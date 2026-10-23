package com.jaymospowers.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.42) Shared easing for our procedural body poses, so nothing snaps on or off. Each pose owns a {@link Channel}; each
 * entity drawn with it gets a {@link Track} (by entity id) holding:
 * <ul>
 * <li>a <b>weight</b> (0-1) on a slightly underdamped spring: it eases the pose in and out and settles with a small
 * overshoot. The pose is laid over the vanilla angles by this weight ({@code vanilla + weight * offset}).</li>
 * <li>per-part <b>springs</b> on the pose's offsets from the vanilla angles (a {@link Frame}): a pose that jumps between
 * keys (a wind-up, a strike) is followed through with a little lag and overshoot, as the preview's springs did. Offsets,
 * not angles, so vanilla's own walk passes straight through a part the pose leaves alone.</li>
 * </ul>
 * Everything runs on real time ({@link System#nanoTime}, small fixed substeps), so it looks the same at any frame rate,
 * and a pose applied twice in a frame (the layers pass, the final draw, each armour piece) moves its springs by nothing
 * the second time. When a pose ends, its last offsets are held while the weight eases out. Tracks go when they have
 * eased out, or when their entity hasn't been drawn for {@link #FORGET_NANOS}. Render thread only.
 */
public final class PoseBlend {
	/** The weight's spring: stiffness (per second squared) and damping ratio (under 1 overshoots a little). */
	static final float WEIGHT_STIFFNESS = 110.0F, WEIGHT_DAMPING = 0.72F;
	/** The most the weight may overshoot past 1 (and never below 0). */
	static final float WEIGHT_MAX = 1.12F;
	/** The parts' springs: stiffness per part (head, body, arms, legs, the bend at the hips) and the damping ratio. */
	static final float HEAD_STIFFNESS = 90.0F, BODY_STIFFNESS = 170.0F, ARM_STIFFNESS = 240.0F, LEG_STIFFNESS = 200.0F,
		HIP_STIFFNESS = 150.0F;
	static final float PART_DAMPING = 0.62F;
	/** Integration step (seconds) and the longest gap taken in one go (a frame after a stall doesn't fling the pose). */
	static final float SUBSTEP = 0.008F, MAX_DT = 0.1F;
	/** Under this, a weight easing out (and its speed) is done with. */
	static final float SETTLED = 0.003F;
	/** A track whose entity hasn't been drawn this long is dropped; how often that's looked for. */
	static final long FORGET_NANOS = 3_000_000_000L, SWEEP_NANOS = 1_000_000_000L;
	/** The hips, in model pixels below the neck: the upper body bends and turns about them. */
	static final float HIP_Y = 12.0F;

	// ---- the slots of a frame: each part's x, y, z rotation, then the bend at the hips and the drop

	public static final int HEAD = 0, BODY = 3, RIGHT_ARM = 6, LEFT_ARM = 9, RIGHT_LEG = 12, LEFT_LEG = 15;
	/**
	 * The upper body bent at the hips: forward (x, + leans forward), turned (y, + draws the right shoulder back, as
	 * vanilla's swing does first; - drives it forward), tipped (z).
	 */
	public static final int HIP_PITCH = 18, HIP_YAW = 19, HIP_ROLL = 20;
	/** The upper body lowered (model pixels, + is down) and moved forward (pixels, + is ahead). */
	public static final int DROP = 21, FORWARD = 22;
	static final int SLOTS = 23;

	private static final float[] STIFFNESS = new float[SLOTS];

	static {
		for (int i = 0; i < SLOTS; i++) {
			STIFFNESS[i] = i < BODY ? HEAD_STIFFNESS : i < RIGHT_ARM ? BODY_STIFFNESS : i < RIGHT_LEG ? ARM_STIFFNESS
				: i < HIP_PITCH ? LEG_STIFFNESS : HIP_STIFFNESS;
		}
	}

	private static final List<Channel> CHANNELS = new ArrayList<>();
	private static long lastSweep;

	private PoseBlend() {
	}

	/** A pose's own easing, made once (a static final in the pose's class). */
	public static Channel channel(String name) {
		Channel channel = new Channel(name);
		CHANNELS.add(channel);
		return channel;
	}

	/** One pose's tracks, by entity id. */
	public static final class Channel {
		final String name;
		final Map<Integer, Track> tracks = new HashMap<>();

		Channel(String name) {
			this.name = name;
		}

		/**
		 * Steps (by the real time since last asked) and returns {@code id}'s track: {@code on} whether the pose is wanted
		 * now. Null when it's off and has eased out (or never was on): draw nothing then.
		 */
		public @Nullable Track track(int id, boolean on) {
			return this.track(id, on, 0.0F, 1.0F);
		}

		/**
		 * As {@link #track(int, boolean)}, the weight held up {@code linger} seconds after the pose ends (a set-down, a
		 * recovery played out first), and its spring {@code speed} times as quick.
		 */
		public @Nullable Track track(int id, boolean on, float linger, float speed) {
			long now = System.nanoTime();
			sweep(now);
			Track track = this.tracks.get(id);
			if (track == null) {
				if (!on) {
					return null;
				}
				track = new Track(now);
				this.tracks.put(id, track);
			}
			float dt = Mth.clamp((now - track.last) / 1.0E9F, 0.0F, MAX_DT);
			track.last = now;
			track.dt = dt;
			track.framed = false;
			if (on != track.on) {
				track.on = on;
				track.changed = now;
			}
			boolean wanted = on || (now - track.changed) / 1.0E9F < linger;
			float stiffness = WEIGHT_STIFFNESS * speed * speed;
			float damping = 2.0F * WEIGHT_DAMPING * Mth.sqrt(stiffness);
			float target = wanted ? 1.0F : 0.0F;
			int steps = Math.max(1, Mth.ceil(dt / SUBSTEP));
			float h = dt / steps;
			for (int i = 0; i < steps; i++) {
				track.velocity += (stiffness * (target - track.weight) - damping * track.velocity) * h;
				track.weight += track.velocity * h;
			}
			if (track.weight > WEIGHT_MAX) {
				track.weight = WEIGHT_MAX;
				track.velocity = Math.min(0.0F, track.velocity);
			}
			if (track.weight < 0.0F) {
				track.weight = 0.0F;
				track.velocity = Math.max(0.0F, track.velocity);
			}
			if (!wanted && track.weight < SETTLED && Math.abs(track.velocity) < SETTLED * 10.0F) {
				this.tracks.remove(id);
				return null;
			}
			return track;
		}

		/** {@code id}'s track as it stands, not stepped (null if none). */
		public @Nullable Track peek(int id) {
			return this.tracks.get(id);
		}
	}

	/** One entity's easing for one pose. */
	public static final class Track {
		float weight, velocity;
		boolean on;
		long last, changed, born;
		float dt;
		/** Whether the springs have had a frame yet (the first one is taken as it is: the weight eases it in). */
		boolean primed, framed;
		final float[] value = new float[SLOTS], speed = new float[SLOTS];
		/** Anything the pose wants kept with the track (a carry's last hold, a throw's start). */
		public @Nullable Object memo;
		public float memoTime = -1.0F;

		Track(long now) {
			this.last = now;
			this.changed = now;
			this.born = now;
		}

		/** The weight now (0 to a little over 1). */
		public float weight() {
			return this.weight;
		}

		/** The weight clamped to 0-1 (for anything that mustn't overshoot). */
		public float amount() {
			return Mth.clamp(this.weight, 0.0F, 1.0F);
		}

		/** Whether the pose is wanted now. */
		public boolean on() {
			return this.on;
		}

		/** Seconds since the pose last came on (or went off, when it's off). */
		public float age() {
			return (this.last - this.changed) / 1.0E9F;
		}

		/** This step's real time (seconds). */
		public float dt() {
			return this.dt;
		}

		/** The springs' current value of {@code slot} (an offset from vanilla, before the weight). */
		public float held(int slot) {
			return this.value[slot];
		}

		/**
		 * Moves the springs toward {@code frame}'s offsets (null: the pose is off, hold them as they are) and lays them over
		 * the model by the weight. {@code stiffness} scales every spring (above 1 is snappier). Call once per application.
		 */
		public void apply(HumanoidModel<?> h, @Nullable Frame frame, float stiffness) {
			if (frame != null && !this.framed) {
				this.framed = true;
				if (!this.primed) {
					this.primed = true;
					System.arraycopy(frame.off, 0, this.value, 0, SLOTS);
				} else {
					int steps = Math.max(1, Mth.ceil(this.dt / SUBSTEP));
					float step = this.dt / steps;
					for (int i = 0; i < SLOTS; i++) {
						float k = STIFFNESS[i] * stiffness;
						float c = 2.0F * PART_DAMPING * Mth.sqrt(k);
						float v = this.speed[i], x = this.value[i], goal = frame.off[i];
						for (int n = 0; n < steps; n++) {
							v += (k * (goal - x) - c * v) * step;
							x += v * step;
						}
						this.speed[i] = v;
						this.value[i] = x;
					}
				}
			}
			if (frame == null) {
				lay(h, this.value, this.weight);
				return;
			}
			// what's shaken on top (a tremble, a breath too quick for the springs) goes on as it is
			float[] laid = new float[SLOTS];
			for (int i = 0; i < SLOTS; i++) {
				laid[i] = this.value[i] + frame.raw[i];
			}
			lay(h, laid, this.weight);
		}

		/** As {@link #apply(HumanoidModel, Frame, float)} at the springs' own stiffness. */
		public void apply(HumanoidModel<?> h, @Nullable Frame frame) {
			this.apply(h, frame, 1.0F);
		}
	}

	/**
	 * A pose's target for this frame, as offsets from the model's vanilla angles (read when it's made): set parts outright
	 * ({@link #set}) or nudge them ({@link #add}). The hips' bend and the drop start at nothing.
	 */
	public static final class Frame {
		final float[] base = new float[SLOTS];
		final float[] off = new float[SLOTS];
		final float[] raw = new float[SLOTS];

		/** A frame over {@code h} as it's posed now (vanilla's angles, and whatever earlier poses laid on). */
		public Frame(HumanoidModel<?> h) {
			read(h, this.base);
		}

		/** (0.15.142) A frame over the six parts of a model that isn't a HumanoidModel ({@link #parts}: an illager's). */
		public Frame(ModelPart[] parts) {
			for (int i = 0; i < parts.length; i++) {
				readPart(parts[i], this.base, i * 3);
			}
		}

		/**
		 * (0.15.142) Lays this frame straight onto {@code parts} ({@link #parts}) by {@code w}: no springs, and no bend at
		 * the hips (a model that isn't a HumanoidModel has no track).
		 */
		public void put(ModelPart[] parts, float w) {
			for (int i = 0; i < parts.length; i++) {
				ModelPart part = parts[i];
				part.xRot += (this.off[i * 3] + this.raw[i * 3]) * w;
				part.yRot += (this.off[i * 3 + 1] + this.raw[i * 3 + 1]) * w;
				part.zRot += (this.off[i * 3 + 2] + this.raw[i * 3 + 2]) * w;
			}
		}

		/** Sets {@code part}'s rotations (radians) outright. */
		public Frame set(int part, float x, float y, float z) {
			this.off[part] = x - this.base[part];
			this.off[part + 1] = y - this.base[part + 1];
			this.off[part + 2] = z - this.base[part + 2];
			return this;
		}

		/** Sets one slot outright (a rotation, or the hips' bend or the drop). */
		public Frame set(int slot, float value) {
			this.off[slot] = value - this.base[slot];
			return this;
		}

		/** Adds to one slot. */
		public Frame add(int slot, float delta) {
			this.off[slot] += delta;
			return this;
		}

		/** Scales everything set so far (the pose only part way on). */
		public Frame scale(float amount) {
			for (int i = 0; i < SLOTS; i++) {
				this.off[i] *= amount;
				this.raw[i] *= amount;
			}
			return this;
		}

		/** Adds to one slot past the springs (a tremble or a quick shake the springs would smooth away). */
		public Frame shake(int slot, float delta) {
			this.raw[slot] += delta;
			return this;
		}

		/** The slot's angle as the pose stands now (vanilla plus what's been set). */
		public float get(int slot) {
			return this.base[slot] + this.off[slot];
		}

		/** The slot's vanilla angle. */
		public float vanilla(int slot) {
			return this.base[slot];
		}

		/** Mixes {@code part} toward the given rotations by {@code t} (0 leaves it, 1 sets it). */
		public Frame mix(int part, float x, float y, float z, float t) {
			this.off[part] = Mth.lerp(t, this.off[part], x - this.base[part]);
			this.off[part + 1] = Mth.lerp(t, this.off[part + 1], y - this.base[part + 1]);
			this.off[part + 2] = Mth.lerp(t, this.off[part + 2], z - this.base[part + 2]);
			return this;
		}

		/**
		 * Takes the model's angles as they've been posed (by an older pose's code, since this frame was made) as this
		 * frame's target, and puts the model back as it was, for {@link Track#apply} to lay them on eased.
		 */
		public Frame take(HumanoidModel<?> h) {
			float[] now = new float[SLOTS];
			read(h, now);
			for (int i = 0; i < HIP_PITCH; i++) {
				this.off[i] = now[i] - this.base[i];
			}
			write(h, this.base);
			return this;
		}
	}

	/** The parts a frame poses, by their names in a model's root, in slot order. */
	private static final String[] PART_NAMES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
	private static final Map<Model<?>, ModelPart[]> PARTS = new WeakHashMap<>();
	private static final ModelPart[] NO_PARTS = new ModelPart[0];

	/**
	 * (0.15.142) A model that isn't a HumanoidModel but is built like one (an illager's): its head, body, arms and legs,
	 * found by name, for a {@link Frame#Frame(ModelPart[])}. Null if it lacks any of them.
	 */
	public static ModelPart @Nullable [] parts(Model<?> model) {
		ModelPart[] parts = PARTS.computeIfAbsent(model, m -> {
			ModelPart root = m.root();
			ModelPart[] found = new ModelPart[PART_NAMES.length];
			for (int i = 0; i < found.length; i++) {
				if (!root.hasChild(PART_NAMES[i])) {
					return NO_PARTS;
				}
				found[i] = root.getChild(PART_NAMES[i]);
			}
			return found;
		});
		return parts.length == 0 ? null : parts;
	}

	private static void read(HumanoidModel<?> h, float[] into) {
		readPart(h.head, into, HEAD);
		readPart(h.body, into, BODY);
		readPart(h.rightArm, into, RIGHT_ARM);
		readPart(h.leftArm, into, LEFT_ARM);
		readPart(h.rightLeg, into, RIGHT_LEG);
		readPart(h.leftLeg, into, LEFT_LEG);
	}

	private static void write(HumanoidModel<?> h, float[] from) {
		writePart(h.head, from, HEAD);
		writePart(h.body, from, BODY);
		writePart(h.rightArm, from, RIGHT_ARM);
		writePart(h.leftArm, from, LEFT_ARM);
		writePart(h.rightLeg, from, RIGHT_LEG);
		writePart(h.leftLeg, from, LEFT_LEG);
	}

	private static void writePart(ModelPart part, float[] from, int at) {
		part.xRot = from[at];
		part.yRot = from[at + 1];
		part.zRot = from[at + 2];
	}

	private static void readPart(ModelPart part, float[] into, int at) {
		into[at] = part.xRot;
		into[at + 1] = part.yRot;
		into[at + 2] = part.zRot;
	}

	/** Lays {@code off} (offsets from the model's angles now) over the model by {@code w}. */
	static void lay(HumanoidModel<?> h, float[] off, float w) {
		if (w <= 0.0F || PosedCopy.of(h)) {
			// (0.15.44) nothing to lay, or the model copied a body that has it already (MCA's layers)
			return;
		}
		addPart(h.head, off, HEAD, w);
		addPart(h.body, off, BODY, w);
		addPart(h.rightArm, off, RIGHT_ARM, w);
		addPart(h.leftArm, off, LEFT_ARM, w);
		addPart(h.rightLeg, off, RIGHT_LEG, w);
		addPart(h.leftLeg, off, LEFT_LEG, w);
		bend(h, off[HIP_PITCH] * w, off[HIP_YAW] * w, off[HIP_ROLL] * w, off[DROP] * w, off[FORWARD] * w);
	}

	private static void addPart(ModelPart part, float[] off, int at, float w) {
		part.xRot += off[at] * w;
		part.yRot += off[at + 1] * w;
		part.zRot += off[at + 2] * w;
	}

	/**
	 * The upper body (body, head and arms) bent forward ({@code pitch}), turned ({@code yaw}) and tipped ({@code roll})
	 * about the hips, and lowered by {@code drop} and moved ahead by {@code forward} (model pixels); the legs stay planted.
	 * The parts are moved where they hang from and turned with it, as ZFighterComboPose's turn does.
	 */
	public static void bend(HumanoidModel<?> h, float pitch, float yaw, float roll, float drop, float forward) {
		if (PosedCopy.of(h)) {
			// (0.15.44) bent already, in the body it copied (MCA's layers)
			return;
		}
		if (Math.abs(pitch) > 1.0E-4F || Math.abs(yaw) > 1.0E-4F || Math.abs(roll) > 1.0E-4F) {
			Quaternionf q = new Quaternionf().rotationZYX(roll, yaw, pitch);
			Vector3f hip = new Vector3f(0.0F, HIP_Y, 0.0F);
			Vector3f at = new Vector3f();
			Vector3f angles = new Vector3f();
			for (ModelPart part : new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm}) {
				q.transform(at.set(part.x, part.y, part.z).sub(hip)).add(hip);
				part.x = at.x;
				part.y = at.y;
				part.z = at.z;
				new Quaternionf(q).mul(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot)).getEulerAnglesZYX(angles);
				part.xRot = angles.x;
				part.yRot = angles.y;
				part.zRot = angles.z;
			}
		}
		if (Math.abs(drop) > 1.0E-4F || Math.abs(forward) > 1.0E-4F) {
			for (ModelPart part : new ModelPart[] {h.body, h.head, h.rightArm, h.leftArm}) {
				part.y += drop;
				part.z -= forward;
			}
		}
	}

	/** Drops the tracks of entities not drawn for a while (gone, or out of sight while a pose was on). */
	private static void sweep(long now) {
		if (now - lastSweep < SWEEP_NANOS) {
			return;
		}
		lastSweep = now;
		for (Channel channel : CHANNELS) {
			channel.tracks.entrySet().removeIf(entry -> now - entry.getValue().last > FORGET_NANOS);
		}
	}

	// ---- shaping helpers for the poses

	/** 0 before {@code a}, 1 after {@code b}, eased between. */
	public static float seg(float t, float a, float b) {
		float x = Mth.clamp((t - a) / (b - a), 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** A bump: 0 at {@code a}, up to 1 by {@code b}, held to {@code c}, back to 0 by {@code d}. */
	public static float bump(float t, float a, float b, float c, float d) {
		return seg(t, a, b) * (1.0F - seg(t, c, d));
	}

	/** Eased 0-1. */
	public static float smooth(float t) {
		float x = Mth.clamp(t, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** A slow, uneven wander (about -1 to 1) on its own beat, for tremble and idle shifts. */
	public static float wander(float time, float phase) {
		return Mth.sin(time * 0.7F + phase) * 0.6F + Mth.sin(time * 1.33F + phase * 1.9F) * 0.4F;
	}
}
