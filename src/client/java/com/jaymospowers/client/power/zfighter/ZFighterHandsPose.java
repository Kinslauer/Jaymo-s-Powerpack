package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.power.zfighter.ZFighterHands;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterNet;
import com.jaymospowers.power.zfighter.ZFighterWaveBeam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.60) A Z Fighter's ki made in the hands, for players and NPCs alike ({@link ZFighterHands} on the server): the wave
 * gathers between hands cupped at the lead hip, the Frost Needle at one pointed finger, the barrage's balls in both palms
 * held out; each blast is thrown with a thrust of its arm (a tapped one drawn back first), the hands taking turns in a
 * barrage; a bomb (and an NPC's wave) is pushed out with both. The fired wave's push is {@link ZFighterFiringPose}.
 * <p>
 * The hands are also where the ki is drawn: each frame the posed model's hands are read back ({@link #capture}) and the
 * gathering orbs ({@link ZFighterOrbs}) and a travelling beam's root ({@link ZFighterBeams}) are put in them, so nothing
 * hangs in the air in front of the fighter. In your own first-person view they sit where FirstPersonArms draws your hands.
 */
public final class ZFighterHandsPose {
	/** This frame's hands on one body: whose, the held pose, the aim (radians, + down), the main arm's side, the time. */
	public record Hands(int id, int pose, float pitch, boolean mainLeft, float time) {
	}

	public static final RenderStateDataKey<Hands> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_hands");

	// ---- tuning (radians; arm x: - raises it forward; y: - turns it in; z: - swings it in across the body). Right-arm values,
	// mirrored for the left.

	/** Arms straight out along the aim, and how much of the look's pitch they follow. */
	static final float AIM = -Mth.HALF_PI, AIM_SHARE = 0.9F;
	/** The wave's cup at the lead hip: the lead arm, and the other reaching across to it; the shoulders turned with it. */
	static final float[] CUP_LEAD = {-0.35F, 0.0F, -0.3F};
	static final float[] CUP_OTHER = {-0.55F, 0.0F, -0.85F};
	static final float CUP_TWIST = 0.3F, CUP_LEAN = 0.1F;
	/** The charge trembling in the cup (radians, a tick). */
	static final float CUP_SHAKE = 0.04F, CUP_BEAT = 2.3F;
	/** The barrage: palms held out while the balls form (above the aim), and the guard between throws. */
	static final float PALMS_RAISE = 0.35F, PALMS_IN = -0.15F, GUARD_RAISE = 0.55F, GUARD_IN = -0.1F;
	/** A tapped blast's arm drawn back while the ball forms in the palm. */
	static final float[] COCKED = {0.55F, 0.0F, 0.15F};
	/** A thrown arm turned in toward the aim; both pushed out, turned in until the heels of the hands meet. */
	static final float THRUST_IN = -0.12F, PUSH_IN = -0.3F;
	/** The throws' timing (ticks): out, held, back. */
	static final float JAB_OUT = 1.0F, JAB_HOLD = 1.5F, JAB_BACK = 4.5F;
	static final float THROW_OUT = 1.5F, THROW_HOLD = 4.0F, THROW_BACK = 8.0F;
	static final float PUSH_OUT = 1.5F, PUSH_HOLD = 7.0F, PUSH_BACK = 13.0F;
	/** A throw is forgotten this long after it began (ticks). */
	static final int THROW_LIFE = 20;
	/** How quick the pose comes on (the weight's spring, times its usual), and the arms' springs while throwing (snappier). */
	static final float SPEED = 1.5F, THROW_STIFFNESS = 2.5F;
	/** The hand on the model (pixels from the shoulder's pivot, along the arm), and a fingertip. */
	static final float HAND = 10.0F, TIP = 12.0F;
	/** A player's model is drawn this much smaller than its box (as the avatar renderer's own scale). */
	static final float AVATAR_SCALE = 0.9375F;
	/** A read-back older than this is not trusted (seconds): the body is off screen. */
	static final float STALE = 0.25F;

	// ---- first person: right, up, forward of the eyes (blocks), to put the ki where FirstPersonArms draws the hands

	static final double[] FP_CUP = {0.12, -0.32, 0.5};
	static final double[] FP_POINT = {0.22, -0.2, 0.72};
	static final double[] FP_PALM = {0.3, -0.24, 0.52};
	static final double[] FP_BEAM = {0.0, -0.2, 0.72};
	/** How far a thrown palm comes forward and in, and a drawn-back one back and down (blocks, at full). */
	static final double FP_THRUST_AHEAD = 0.25, FP_THRUST_IN = 0.12, FP_COCK_BACK = 0.12, FP_COCK_DOWN = 0.08;

	private static final PoseBlend.Channel BLEND = PoseBlend.channel("zfighter_hands");

	/** A throw: which hand, what kind, and when (the entity's tick count). */
	private record Throw(int hand, int kind, float start) {
	}

	private static final Map<Integer, List<Throw>> THROWS = new HashMap<>();

	/** One body's hands as last drawn: right and left palm, right and left fingertip (model pixels), and when. */
	private record Capture(Vector3f right, Vector3f left, Vector3f rightTip, Vector3f leftTip, long nanos) {
	}

	private static final Map<Integer, Capture> CAPTURES = new HashMap<>();

	private ZFighterHandsPose() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterNet.HandThrow.TYPE, (fx, context) -> {
			Minecraft minecraft = Minecraft.getInstance();
			Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(fx.entity());
			if (entity != null) {
				THROWS.computeIfAbsent(fx.entity(), id -> new ArrayList<>()).add(new Throw(fx.hand(), fx.kind(), entity.tickCount));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				THROWS.clear();
				CAPTURES.clear();
				return;
			}
			THROWS.entrySet().removeIf(entry -> {
				Entity entity = minecraft.level.getEntity(entry.getKey());
				if (entity == null) {
					return true;
				}
				entry.getValue().removeIf(t -> entity.tickCount - t.start() > THROW_LIFE);
				return entry.getValue().isEmpty();
			});
			long now = System.nanoTime();
			CAPTURES.values().removeIf(c -> (now - c.nanos()) / 1.0E9F > STALE * 8.0F);
		});
	}

	// ---------------------------------------------------------------- state

	/** The held pose on {@code entity} (ZFighterHands.NONE when none). */
	public static int heldPose(Entity entity) {
		Integer pose = entity.getAttached(ZFighterHands.POSE);
		return pose == null ? ZFighterHands.NONE : pose;
	}

	private static boolean firing(Entity entity) {
		return entity instanceof Player player && ZFighterWaveBeam.out(player) && !ZFighterLineage.ape(player);
	}

	private static boolean throwing(int id) {
		List<Throw> throws_ = THROWS.get(id);
		return throws_ != null && !throws_.isEmpty();
	}

	/**
	 * One arm's throws at {@code time} (the entity's ticks): how far it's drawn back (0-1), how far thrown out (0-1), and
	 * whether that's both hands pushing (1) or one thrown (0).
	 */
	public static float[] arm(int id, boolean right, float time) {
		float[] out = new float[3];
		List<Throw> throws_ = THROWS.get(id);
		if (throws_ == null) {
			return out;
		}
		for (Throw t : throws_) {
			boolean mine = t.hand() == ZFighterHands.BOTH || (t.hand() == ZFighterHands.RIGHT) == right;
			if (!mine) {
				continue;
			}
			float a = time - t.start();
			float cock = 0.0F, ext;
			switch (t.kind()) {
				case ZFighterHands.THROW -> {
					float c = ZFighterHands.THROW_COCK;
					cock = PoseBlend.seg(a, 0.0F, c * 0.6F) * (1.0F - PoseBlend.seg(a, c, c + 1.0F));
					ext = PoseBlend.seg(a, c, c + THROW_OUT) * (1.0F - PoseBlend.seg(a, c + THROW_HOLD, c + THROW_BACK));
				}
				case ZFighterHands.PUSH -> ext = PoseBlend.seg(a, 0.0F, PUSH_OUT) * (1.0F - PoseBlend.seg(a, PUSH_HOLD, PUSH_BACK));
				default -> ext = PoseBlend.seg(a, 0.0F, JAB_OUT) * (1.0F - PoseBlend.seg(a, JAB_HOLD, JAB_BACK));
			}
			out[0] = Math.max(out[0], cock);
			if (ext > out[1]) {
				out[1] = ext;
				out[2] = t.kind() == ZFighterHands.PUSH ? 1.0F : 0.0F;
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- third person

	/** From LivingEntityRenderer's extractRenderState (players and mobs). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		int pose = heldPose(entity);
		if (pose == ZFighterHands.NONE && !throwing(entity.getId()) && !firing(entity) && BLEND.peek(entity.getId()) == null) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Hands(entity.getId(), pose, entity.getXRot(partialTicks) * Mth.DEG_TO_RAD, entity.getMainArm() == HumanoidArm.LEFT,
			entity.tickCount + partialTicks));
	}

	/** Right after a humanoid model animates (before the fired wave's push, which takes over while the beam is out). */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h) || living.deathTime > 0.0F) {
			return;
		}
		Hands d = living.getData(KEY);
		if (d == null) {
			return;
		}
		boolean throwing = throwing(d.id());
		boolean on = d.pose() != ZFighterHands.NONE || throwing;
		PoseBlend.Track track = BLEND.track(d.id(), on, 0.0F, SPEED);
		if (track == null) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		float aim = AIM + Mth.clamp(d.pitch(), -1.2F, 1.2F) * AIM_SHARE;
		arm(f, d, true, aim);
		arm(f, d, false, aim);
		if (d.pose() == ZFighterHands.CUP || d.pose() == ZFighterHands.ART) {
			// the lead shoulder drawn back with the cup, leaning into it a little
			f.set(PoseBlend.HIP_YAW, d.mainLeft() ? -CUP_TWIST : CUP_TWIST);
			f.set(PoseBlend.HIP_PITCH, CUP_LEAN);
		}
		track.apply(h, f, throwing ? THROW_STIFFNESS : 1.0F);
	}

	/** One arm's frame: the held pose, then any throw laid over it. */
	private static void arm(PoseBlend.Frame f, Hands d, boolean right, float aim) {
		int slot = right ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM;
		boolean lead = right != d.mainLeft();
		float[] base = switch (d.pose()) {
			case ZFighterHands.CUP, ZFighterHands.ART -> (lead ? CUP_LEAD : CUP_OTHER).clone();
			case ZFighterHands.POINT -> lead ? new float[] {aim, 0.0F, 0.0F} : null;
			case ZFighterHands.PALMS -> new float[] {aim + PALMS_RAISE, PALMS_IN, 0.0F};
			case ZFighterHands.VOLLEY -> new float[] {aim + GUARD_RAISE, GUARD_IN, 0.0F};
			default -> null;
		};
		float[] t = arm(d.id(), right, d.time());
		if (base == null && t[0] <= 0.0F && t[1] <= 0.0F) {
			return;
		}
		float side = right ? 1.0F : -1.0F;
		float[] key = base != null ? base : new float[] {f.vanilla(slot), side * f.vanilla(slot + 1), side * f.vanilla(slot + 2)};
		key = lerp(t[0], key, COCKED);
		key = lerp(t[1], key, new float[] {aim, t[2] > 0.0F ? PUSH_IN : THRUST_IN, 0.0F});
		f.set(slot, key[0], side * key[1], side * key[2]);
		if (d.pose() == ZFighterHands.CUP && t[1] <= 0.0F) {
			float shake = Mth.sin(d.time() * CUP_BEAT + (right ? 0.0F : 1.3F)) * CUP_SHAKE;
			f.shake(slot, shake);
			f.shake(slot + 2, shake * 0.6F);
		}
	}

	/** At the end of the model's posing (every pose laid on): reads back where its hands are drawn. */
	static void capture(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Hands d = living.getData(KEY);
		if (d == null) {
			return;
		}
		CAPTURES.put(d.id(), new Capture(point(h.rightArm, -1.0F, HAND), point(h.leftArm, 1.0F, HAND), point(h.rightArm, -1.0F, TIP),
			point(h.leftArm, 1.0F, TIP), System.nanoTime()));
	}

	/** A point on an arm (model pixels): {@code across} and {@code along} it from its pivot, turned as it's posed. */
	private static Vector3f point(ModelPart arm, float across, float along) {
		Vector3f p = new Vector3f(across, along, 0.0F);
		new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(p);
		return p.add(arm.x, arm.y, arm.z);
	}

	/** Model pixels on {@code entity} to the world, as the living renderer draws it. */
	private static Vec3 world(LivingEntity entity, Vector3f model, float partial) {
		float scale = entity.getScale() * (entity instanceof Avatar ? AVATAR_SCALE : 1.0F);
		Vector3f p = new Vector3f(model).div(16.0F);
		// translate(0, -1.501, 0), the avatar's scale, scale(-1, -1, 1), then turned to the body's facing
		p.set(-p.x * scale, -(p.y - 1.501F) * scale, p.z * scale);
		float body = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
		new Quaternionf().rotationY((180.0F - body) * Mth.DEG_TO_RAD).transform(p);
		return entity.getPosition(partial).add(p.x, p.y, p.z);
	}

	// ---------------------------------------------------------------- where the ki is drawn

	/** Which of a fighter's hands: the right palm, the left, between both, the main fingertip. */
	static final int PALM_RIGHT = 1, PALM_LEFT = 2, CUPPED = 3, FINGER = 4;

	/**
	 * Where a gathering orb belongs, by its id ({@code -(entity * 4 + slot)}: 1 and 2 the right and left palm, 3 the
	 * cupped hands or the fingertip), or null to leave it where the server put it.
	 */
	static @Nullable Vec3 orb(int orbId, float partial) {
		if (orbId >= 0) {
			return null;
		}
		int n = -orbId;
		int slot = n % 4;
		Entity entity = level(n / 4);
		if (!(entity instanceof LivingEntity living) || slot == 0) {
			return null;
		}
		if (slot == 3) {
			int pose = heldPose(living);
			int which = pose == ZFighterHands.CUP ? CUPPED : pose == ZFighterHands.POINT ? FINGER : -1;
			return which < 0 ? null : hand(living, which, partial);
		}
		if (heldPose(living) == ZFighterHands.NONE && !throwing(living.getId())) {
			return null;
		}
		return hand(living, slot == 1 ? PALM_RIGHT : PALM_LEFT, partial);
	}

	/** Where a travelling beam leaves its fighter: between the pushed hands (null: as the server says). */
	static @Nullable Vec3 beamRoot(int entityId, float partial) {
		Entity entity = level(entityId);
		if (!(entity instanceof LivingEntity living) || !firing(living)) {
			return null;
		}
		return hand(living, -1, partial);
	}

	private static @Nullable Entity level(int id) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level == null ? null : minecraft.level.getEntity(id);
	}

	/** One of {@code entity}'s hands ({@link #PALM_RIGHT} ..., -1 the beam's root) in the world, or null if not known. */
	private static @Nullable Vec3 hand(LivingEntity entity, int which, float partial) {
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson() && !FirstPersonCompat.bodyShown()) {
			return firstPerson(entity, which, partial);
		}
		Capture c = CAPTURES.get(entity.getId());
		if (c == null || (System.nanoTime() - c.nanos()) / 1.0E9F > STALE) {
			return null;
		}
		boolean mainLeft = entity.getMainArm() == HumanoidArm.LEFT;
		Vector3f p = switch (which) {
			case PALM_RIGHT -> c.right();
			case PALM_LEFT -> c.left();
			case FINGER -> mainLeft ? c.leftTip() : c.rightTip();
			default -> new Vector3f(c.right()).add(c.left()).mul(0.5F);
		};
		return world(entity, p, partial);
	}

	/** In your own first-person view: where FirstPersonArms draws the hand. */
	private static Vec3 firstPerson(LivingEntity self, int which, float partial) {
		Vec3 eye = self.getEyePosition(partial);
		Vec3 look = self.getViewVector(partial);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-6) {
			float yaw = self.getYRot(partial) * Mth.DEG_TO_RAD;
			right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		}
		right = right.normalize();
		Vec3 up = right.cross(look).normalize();
		double lead = self.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
		double[] at;
		double side;
		if (which == -1) {
			at = FP_BEAM;
			side = 1.0;
		} else if (which == CUPPED) {
			at = FP_CUP;
			side = lead;
		} else if (which == FINGER) {
			at = FP_POINT;
			side = lead;
		} else {
			boolean r = which == PALM_RIGHT;
			float[] t = arm(self.getId(), r, self.tickCount + partial);
			side = r ? 1.0 : -1.0;
			at = new double[] {FP_PALM[0] - FP_THRUST_IN * t[1], FP_PALM[1] - FP_COCK_DOWN * t[0],
				FP_PALM[2] + FP_THRUST_AHEAD * t[1] - FP_COCK_BACK * t[0]};
		}
		return eye.add(right.scale(at[0] * side)).add(up.scale(at[1])).add(look.scale(at[2]));
	}

	private static float[] lerp(float t, float[] a, float[] b) {
		if (t <= 0.0F) {
			return a;
		}
		float[] out = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			out[i] = Mth.lerp(t, a[i], b[i]);
		}
		return out;
	}
}
