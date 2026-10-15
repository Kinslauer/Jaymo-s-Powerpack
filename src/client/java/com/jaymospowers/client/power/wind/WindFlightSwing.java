package com.jaymospowers.client.power.wind;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Swinging a weapon in flight. Better Combat's swings are made for someone standing: laid out in the cruise they'd
 * cut the air under the body, and the cruise pose held the arms still anyway. So a cruising flyer swings its own way,
 * timed to the same attack (the hit lands mid-strike, where Better Combat lands it): a horizontal slash sweeps across
 * ahead of the head and rolls the body into it, a slam or uppercut chops through the flight from the sky side, a stab
 * or punch draws back along the body and drives straight ahead, and a spin is a barrel roll with the arms out. A
 * two-handed weapon brings the other hand across to the grip; dual wielding mirrors the swing in the other arm. Plain
 * vanilla swings (no Better Combat weapon) get a quick slash or, empty-handed, a punch.
 * <p>
 * Hovering, the body is upright, so Better Combat's own swing is the right one: the hover's limp arms let go for it.
 * In Player Animation Library's first-person pass the flight leaves the body alone (see {@link CombatAnimCompat}).
 */
public final class WindFlightSwing {
	public static final RenderStateDataKey<Swing> KEY = RenderStateDataKey.create(() -> "jaymospowers:wind_swing");

	/**
	 * The swing's beats, as fractions of the attack: the arm winds up until {@code WINDUP_END}, strikes until
	 * {@code STRIKE_END} (Better Combat lands the hit about halfway), holds the follow-through until {@code HOLD_END},
	 * then eases back into the cruise.
	 */
	private static final float WINDUP_END = 0.35F, STRIKE_END = 0.6F, HOLD_END = 0.72F;
	/** An attack's length in ticks (its attack cooldown), kept within these. */
	private static final float MIN_TICKS = 5.0F, MAX_TICKS = 30.0F;
	/** How far a slash rolls the body into the cut, and a spin's barrel roll (degrees). */
	private static final float SLASH_ROLL = 25.0F, CHOP_ROLL = 8.0F, SPIN_ROLL = 360.0F;
	/** A thrust lunges the body ahead (blocks) and the legs kick back (radians) as the blow lands. */
	private static final float THRUST_LUNGE = 0.25F, LEG_KICK = 0.3F;
	/** Arm length to the fist (model pixels), and the shoulders, for bringing the other hand to a two-handed grip. */
	private static final float ARM_REACH = 10.0F, SHOULDER_X = 5.0F, SHOULDER_Y = 2.0F;

	/** The kinds of swing, picked from the Better Combat animation's name. */
	public enum Style {
		SLASH, CHOP, THRUST, SPIN,
		/**
		 * The Staff Surge: the staff thrust out ahead and spun like a propeller for as long as left mouse is held;
		 * {@code progress} is how far the arm is out (its weight) and {@code sweep} the spin's angle (radians).
		 */
		TWIRL
	}

	/**
	 * A swing under way: how far through (0-1), its style, which way it goes (+1: a slash from the right across to the
	 * left, a chop down from the sky, a spin rolling right; -1 the other way), whether the left arm swings, and the
	 * other hand's part: on the grip of a two-handed weapon, or swinging a mirrored second blade.
	 */
	public record Swing(float progress, Style style, float sweep, boolean leftArm, boolean twoHanded, boolean dual) {
		/** How far into the swing pose (0-1): in over the wind-up, out after the follow-through. */
		public float weight() {
			if (this.style == Style.TWIRL) {
				return this.progress;
			}
			if (this.progress < WINDUP_END) {
				return smooth(this.progress / WINDUP_END);
			}
			return this.progress < HOLD_END ? 1.0F : smooth(Mth.clamp((1.0F - this.progress) / (1.0F - HOLD_END), 0.0F, 1.0F));
		}

		/** How far along the strike's path (0 cocked, 1 followed through). */
		float strike() {
			return smooth(Mth.clamp((this.progress - WINDUP_END) / (STRIKE_END - WINDUP_END), 0.0F, 1.0F));
		}
	}

	private static final class Tracked {
		UUID id;
		String name;
		boolean mirrored;
		float lastTicks;
		float start;
		float duration;
	}

	/** entity id → the Better Combat attack being followed. */
	private static final Map<Integer, Tracked> ATTACKS = new HashMap<>();
	/** entity id → a Staff Surge twirl: {start (game time), let go at (-1 while held), turns a second}. */
	private static final Map<Integer, float[]> TWIRLS = new HashMap<>();

	private WindFlightSwing() {
	}

	static void tick(ClientLevel level) {
		ATTACKS.entrySet().removeIf(entry -> {
			Entity entity = level.getEntity(entry.getKey());
			return entity == null || level.getGameTime() - entry.getValue().start > entry.getValue().duration + 2.0F;
		});
		TWIRLS.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null
			|| entry.getValue()[1] >= 0.0F && level.getGameTime() - entry.getValue()[1] > com.jaymospowers.power.wind.WindStaffSurge.TWIRL_EASE_TICKS + 2.0F);
	}

	/**
	 * The Staff Surge's twirl: {@code held} starts (or keeps) {@code entity}'s staff spinning out ahead; false lets it
	 * drop back.
	 */
	public static void twirl(Entity entity, boolean ascended, boolean held) {
		float now = entity.level().getGameTime();
		float[] was = TWIRLS.get(entity.getId());
		if (held) {
			float rate = ascended ? com.jaymospowers.power.wind.WindStaffSurge.TWIRL_TURNS_PER_SECOND_ASCENDED : com.jaymospowers.power.wind.WindStaffSurge.TWIRL_TURNS_PER_SECOND;
			if (was == null || was[1] >= 0.0F && now - was[1] >= com.jaymospowers.power.wind.WindStaffSurge.TWIRL_EASE_TICKS) {
				TWIRLS.put(entity.getId(), new float[] {now, -1.0F, rate});
			} else {
				// picked up again while still dropping back: carry on spinning
				was[1] = -1.0F;
				was[2] = rate;
			}
		} else if (was != null && was[1] < 0.0F) {
			was[1] = now;
		}
	}

	/** From the avatar renderer: the swing this player is in, if any. */
	static @Nullable Swing extract(Entity entity, AvatarRenderState state, float partialTicks) {
		float now = entity.level().getGameTime() + partialTicks;
		float[] twirl = TWIRLS.get(entity.getId());
		if (twirl != null) {
			// the surge's twirl wins over the swings Better Combat starts while left mouse is held
			float ease = com.jaymospowers.power.wind.WindStaffSurge.TWIRL_EASE_TICKS;
			float weight = Mth.clamp((now - twirl[0]) / ease, 0.0F, 1.0F);
			if (twirl[1] >= 0.0F) {
				weight = Math.min(weight, Mth.clamp(1.0F - (now - twirl[1]) / ease, 0.0F, 1.0F));
			}
			if (weight > 0.0F) {
				float angle = Mth.TWO_PI * twirl[2] * (now - twirl[0]) / 20.0F;
				return new Swing(smooth(weight), Style.TWIRL, angle, state.mainArm == HumanoidArm.LEFT, false, false);
			}
		}
		CombatAnimCompat.Attack attack = CombatAnimCompat.attack(entity);
		Tracked tracked = ATTACKS.get(entity.getId());
		if (attack != null) {
			// a new attack: a different animation, or the same one started over
			if (tracked == null || !attack.id().equals(tracked.id) || attack.ticks() < tracked.lastTicks - 0.5F) {
				tracked = new Tracked();
				tracked.id = attack.id();
				tracked.name = attack.name();
				tracked.mirrored = attack.mirrored();
				tracked.start = now;
				float delay = entity instanceof Player player ? player.getCurrentItemAttackStrengthDelay() : 10.0F;
				tracked.duration = Mth.clamp(delay, MIN_TICKS, MAX_TICKS);
				ATTACKS.put(entity.getId(), tracked);
			}
			tracked.lastTicks = attack.ticks();
		}
		if (tracked != null && now - tracked.start < tracked.duration) {
			// (on to the end even if Better Combat lets go of its animation a little early)
			return fromName(tracked.name, tracked.mirrored, (now - tracked.start) / tracked.duration);
		}
		if (state.swingAnimation > 0.0F) {
			// a plain swing (a pickaxe, a punch): quick, from the hand that swung
			boolean offHand = state.currentSwing != null && state.currentSwing.hand() == InteractionHand.OFF_HAND;
			boolean left = (state.mainArm == HumanoidArm.LEFT) != offHand;
			Style style = state.getMainHandItemStack().isEmpty() && !offHand ? Style.THRUST : Style.SLASH;
			return new Swing(state.swingAnimation, style, left ? -1.0F : 1.0F, left, false, false);
		}
		return null;
	}

	private static Swing fromName(String name, boolean mirrored, float progress) {
		String n = name.toLowerCase(Locale.ROOT);
		Style style = Style.SLASH;
		float sweep = n.contains("left") ? -1.0F : 1.0F;
		if (n.contains("spin") || n.contains("whirl") || n.contains("twirl")) {
			style = Style.SPIN;
		} else if (n.contains("stab") || n.contains("thrust") || n.contains("pierce") || n.contains("punch") || n.contains("jab")
			|| n.contains("lance")) {
			style = Style.THRUST;
		} else if (n.contains("uppercut")) {
			style = Style.CHOP;
			sweep = -1.0F;
		} else if (n.contains("slam") || n.contains("vertical") || n.contains("chop") || n.contains("overhead") || n.contains("smash")) {
			style = Style.CHOP;
			sweep = 1.0F;
		}
		if (mirrored && style != Style.CHOP) {
			sweep = -sweep;
		}
		boolean twoHanded = n.contains("two_handed") || n.contains("staff") || n.contains("polearm") || n.contains("heavy");
		boolean dual = n.contains("dual_handed");
		return new Swing(Mth.clamp(progress, 0.0F, 1.0F), style, sweep, mirrored, twoHanded, dual);
	}

	/** The body's roll into the swing about its own length (degrees), for {@link WindPose}'s tilt. */
	static float roll(@Nullable Swing swing) {
		if (swing == null) {
			return 0.0F;
		}
		float w = swing.weight();
		return switch (swing.style()) {
			case SLASH -> swing.sweep() * SLASH_ROLL * Mth.sin(Mth.PI * Mth.clamp(swing.progress() / HOLD_END, 0.0F, 1.0F)) * w;
			case CHOP -> swing.sweep() * CHOP_ROLL * Mth.sin(Mth.PI * swing.strike());
			case THRUST, TWIRL -> 0.0F;
			// a whole turn through the strike: no weight, so it ends level
			case SPIN -> swing.sweep() * SPIN_ROLL * smooth(Mth.clamp((swing.progress() - 0.15F) / (HOLD_END - 0.15F), 0.0F, 1.0F));
		};
	}

	/** A thrust's lunge along the flight (blocks, in the body's frame: toward the head). */
	static float lunge(@Nullable Swing swing) {
		return swing == null || swing.style() != Style.THRUST ? 0.0F : THRUST_LUNGE * Mth.sin(Mth.PI * swing.strike()) * swing.weight();
	}

	/**
	 * Over the cruise pose: the swinging arm (and the other, for two hands or two blades) and a leg kick. Better
	 * Combat's own swing ran first and may have moved the parts' pivots (its torso bend); they go back to rest.
	 */
	static void applyModel(HumanoidModel<?> h, Swing swing, float amount) {
		float w = swing.weight() * amount;
		if (w <= 0.001F) {
			return;
		}
		h.body.resetPose();
		for (ModelPart part : new ModelPart[] {h.head, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg}) {
			PartPose rest = part.getInitialPose();
			part.x = rest.x();
			part.y = rest.y();
			part.z = rest.z();
		}
		float u = swing.strike();
		Vector3f arm = new Vector3f(), blade = new Vector3f();
		path(swing, u, arm, blade);
		ModelPart main = swing.leftArm() ? h.leftArm : h.rightArm;
		ModelPart other = swing.leftArm() ? h.rightArm : h.leftArm;
		if (swing.leftArm()) {
			mirror(arm);
			mirror(blade);
		}
		aim(main, arm, blade, w);
		if (swing.twoHanded()) {
			// the other hand on the grip: aimed from its shoulder at the swinging fist
			float side = swing.leftArm() ? 1.0F : -1.0F;
			Vector3f fist = new Vector3f(arm).mul(ARM_REACH).add(side * SHOULDER_X, SHOULDER_Y, 0.0F);
			Vector3f reach = fist.sub(-side * SHOULDER_X, SHOULDER_Y, 0.0F).normalize();
			aim(other, reach, blade, w);
		} else if (swing.dual() || swing.style() == Style.SPIN) {
			// the second blade cuts the mirror image (a spin holds both arms out)
			aim(other, mirror(new Vector3f(arm)), mirror(new Vector3f(blade)), w);
		}
		// the legs kick back against the blow (a twirl keeps them trailing)
		float kick = swing.style() == Style.TWIRL ? 0.0F : 1.0F;
		kick *= LEG_KICK * Mth.sin(Mth.PI * u) * w;
		h.rightLeg.xRot += kick;
		h.leftLeg.xRot += kick * 0.7F;
	}

	/**
	 * Where the right arm points along its strike at {@code u}, and its blade (unit vectors, model space). The cruise
	 * lies the body along the flight: model -Y (toward the head) is ahead, -Z (the chest) faces the ground, -X is the
	 * flyer's right.
	 */
	private static void path(Swing swing, float u, Vector3f arm, Vector3f blade) {
		float s = swing.sweep();
		Vector3f motion;
		switch (swing.style()) {
			case SLASH -> {
				// out to the side, across ahead (a little under the head so it doesn't pass through it), round to the far side
				along(u, dir(0.35F, s, 0.25F), dir(1.0F, 0.0F, 0.45F), dir(0.75F, -0.75F * s, 0.55F), arm);
				motion = dir(0.0F, -s, 0.0F);
			}
			case CHOP -> {
				// raised to the sky over the shoulder, through ahead, down toward the ground (an uppercut the other way)
				along(u, dir(0.25F, 0.25F, -s), dir(1.0F, 0.1F, -0.1F * s), dir(0.55F, 0.05F, s), arm);
				motion = dir(0.0F, 0.0F, s);
			}
			case THRUST -> {
				// drawn back along the side, then driven straight ahead
				along(u, dir(-0.6F, 0.45F, 0.5F), dir(0.2F, 0.3F, 0.5F), dir(1.0F, 0.05F, 0.3F), arm);
				motion = dir(1.0F, 0.0F, 0.0F);
			}
			case TWIRL -> {
				// thrust out ahead, the staff (held by its middle) spinning round the forearm like a propeller
				arm.set(dir(1.0F, 0.15F, 0.2F));
				Vector3f across = new Vector3f(dir(0.0F, 1.0F, 0.0F));
				across.sub(new Vector3f(arm).mul(across.dot(arm))).normalize();
				Vector3f up = new Vector3f(arm).cross(across).normalize();
				float angle = s;
				blade.set(across).mul(Mth.cos(angle)).add(new Vector3f(up).mul(Mth.sin(angle))).normalize();
				return;
			}
			default -> {
				// straight out to the side, the body rolling round under it
				arm.set(dir(0.2F, 1.0F, 0.05F));
				motion = dir(0.0F, 0.0F, -s);
			}
		}
		// the blade leads the cut and leans toward the target ahead (always square to the arm, as it's held)
		blade.set(motion).add(dir(0.6F, 0.0F, 0.0F));
		blade.sub(new Vector3f(arm).mul(blade.dot(arm)));
		if (blade.lengthSquared() < 1.0E-4F) {
			blade.set(dir(0.0F, 0.0F, -1.0F));
		}
		blade.normalize();
	}

	/** Through three directions as {@code u} goes 0 to 1. */
	private static void along(float u, Vector3f from, Vector3f mid, Vector3f to, Vector3f out) {
		if (u < 0.5F) {
			out.set(from).lerp(mid, u * 2.0F);
		} else {
			out.set(mid).lerp(to, u * 2.0F - 1.0F);
		}
		out.normalize();
	}

	/** A direction in the flight's terms: ahead, to the flyer's right, toward the ground. */
	private static Vector3f dir(float ahead, float right, float down) {
		return new Vector3f(-right, -ahead, -down).normalize();
	}

	private static Vector3f mirror(Vector3f v) {
		v.x = -v.x;
		return v;
	}

	/**
	 * Turns an arm to point along {@code arm} with its held item along {@code blade}, {@code w} of the way from where
	 * it is. At rest an arm hangs along +Y with the item pointing ahead (-Z); the part turns Z, then Y, then X.
	 */
	private static void aim(ModelPart part, Vector3f arm, Vector3f blade, float w) {
		Vector3f z = new Vector3f(blade).negate();
		Vector3f x = new Vector3f(arm).cross(z);
		Quaternionf target = new Quaternionf().setFromNormalized(new Matrix3f(x, arm, z));
		Quaternionf now = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
		Vector3f angles = now.slerp(target, w).getEulerAnglesZYX(new Vector3f());
		part.xRot = angles.x;
		part.yRot = angles.y;
		part.zRot = angles.z;
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
