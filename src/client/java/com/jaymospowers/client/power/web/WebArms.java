package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebLine;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Web-slinger body physics, built the same way as Jaymo's Kitchen's corpse ragdolls (Limp / Jolt): a little
 * spring-and-damper state per player, ticked here, put on the player's render state, and applied to the model's
 * parts by name right after the model animates (see the web client mixins).
 * <ul>
 * <li>Each arm with a web out reaches along its web: it springs toward the anchor with a bit of lag and overshoot, so a
 * fresh web snaps the arm up and a swing tugs it around.</li>
 * <li>A Web Zip fires from both hands at the one target; then, as the web reels you in, both hands haul back on it
 * and trail behind you while you fly forward.</li>
 * <li>While hanging from a web the legs go limp: they trail behind the swing and flop sideways with it, settling as
 * the swing slows.</li>
 * <li>Swinging along on a web, the slinger drives the swing instead: legs back on the drop, knees tucked through the
 * bottom of the arc, kicked out ahead on the upswing; the free arm flares out for balance, and the body banks into
 * turns.</li>
 * <li>On a Yank web (the Yank, its lead, and every Web Whip), the weight on the line swings the upper body: it twists
 * after the catch, bends forward into a slam and back under a haul overhead, and leans into it as it goes round,
 * lagging and overshooting like something heavy, while the legs plant a wide, staggered stance.</li>
 * </ul>
 * The web line is drawn from the posed hand ({@link #handPos}), so it really comes out of the arm.
 */
public final class WebArms {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_arms");

	/** Arm aim spring: pull toward the web per tick, and velocity kept per tick (lower = more damped). */
	private static final double ARM_SPRING = 0.35;
	private static final double ARM_DAMPING = 0.55;
	/** Leg ragdoll spring, damping, and how far the swing speed throws them. */
	private static final float LEG_SPRING = 0.22F;
	private static final float LEG_DAMPING = 0.78F;
	private static final float LEG_TRAIL_PER_SPEED = 0.9F;
	private static final float LEG_TRAIL_MAX = 1.1F;
	/** How fast the whole pose eases in and out, per tick. */
	private static final float EASE = 0.25F;
	/** Shoulder pivot above the feet and out to the side, and the shoulder-to-hand length (model units / 16). */
	private static final double SHOULDER_HEIGHT = 22.0 / 16.0;
	private static final double SHOULDER_OUT = 5.0 / 16.0;
	private static final double ARM_LENGTH = 10.0 / 16.0;
	/** Both webs on one catch (a Web Whip): the hands meet on the one line this far out from the chest. */
	private static final double GRIP_REACH = 8.0 / 16.0;

	/** This frame's pose: an arm's aim is null when that hand has no web out. Angles are model-part radians. */
	public record Pose(float amount, float @Nullable [] mainArm, float @Nullable [] offArm, float legTrail, float legSide, float wings, float lean,
		float brake, float dive, float swayPitch, float swayRoll, float armSway, float legSway, float limbRoll, boolean airborne,
		float drive, float pump, float tuck, float heave, float torsoX, float torsoY, float torsoZ) {
	}

	/**
	 * Web Zip: both arms aim at the target for {@code ZIP_SHOOT_TICKS}, then sweep back over {@code ZIP_SWEEP_TICKS}
	 * until they trail behind the flight, angled this far down ({@code ZIP_TRAIL_DROP}, as a share of the back sweep).
	 */
	private static final int ZIP_SHOOT_TICKS = 5, ZIP_SWEEP_TICKS = 6;
	private static final double ZIP_TRAIL_DROP = 0.45;

	/** Gliding: the body leans this far forward (degrees), easing in over {@code LEAN_TICKS} of the glide. */
	private static final float GLIDE_LEAN = 70.0F;
	private static final float LEAN_TICKS = 30.0F;

	/** Spread wings: how far the arms are raised out from the sides (radians from hanging down). */
	private static final float WING_SPREAD = 1.35F;
	/**
	 * Airbrake (S while gliding): the body rears up out of the glide, leaning back this far (degrees), the arms
	 * swept up and out ({@code BRAKE_SPREAD}, radians from hanging) and beating forward, between {@code BRAKE_REACH}
	 * and {@code BRAKE_REACH + BRAKE_BEAT} radians ahead, {@code BRAKE_BEAT_SPEED} radians of the stroke a tick. The
	 * legs tuck up ({@code BRAKE_TUCK}).
	 */
	private static final float BRAKE_LEAN_BACK = 15.0F;
	private static final float BRAKE_SPREAD = 2.2F;
	private static final float BRAKE_REACH = 0.25F;
	private static final float BRAKE_BEAT = 0.55F;
	private static final float BRAKE_BEAT_SPEED = 0.7F;
	private static final float BRAKE_TUCK = 0.9F;
	/**
	 * Braked to a stop, the freedive: the body tips head-first toward {@code DIVE_PITCH} (degrees, as Wind's dive) as the
	 * fall passes {@code DIVE_FROM} blocks/tick, all the way by {@code DIVE_FULL}; the wings stay out but sweep back
	 * ({@code DIVE_SWEEP} radians behind, {@code DIVE_SPREAD} out from the sides), the legs together.
	 */
	private static final float DIVE_PITCH = 150.0F;
	private static final float DIVE_FROM = 0.3F, DIVE_FULL = 1.0F;
	private static final float DIVE_SWEEP = 0.55F, DIVE_SPREAD = 0.95F;
	/**
	 * Hanging from a web, the body swings from the shoulders like a pendulum (as a hovering Wind flyer's does): moving
	 * one way, the legs trail the other. Lean per block/tick of motion, the most it leans, the spring and damping.
	 */
	private static final float SWAY_PER_SPEED = 0.9F;
	private static final float SWAY_PER_RISE = 0.4F;
	private static final float SWAY_MAX = 0.6F;
	private static final float SWAY_SPRING = 0.16F;
	private static final float SWAY_DAMPING = 0.86F;
	/** The swing pivots at about the shoulders (fraction of the body's height); the glide lean about its middle. */
	private static final float SWAY_PIVOT = 0.85F;
	/**
	 * The limbs not on a web hang limp, as in the Wind hover: they chase the body's sway on softer springs of their own
	 * ({@code LIMB_SPRING}, {@code LIMB_DAMPING}), so they trail a swing that's starting and run on as it stops, and drift
	 * a little ({@code LIMB_DRIFT}, radians) so they never hang dead still.
	 */
	private static final float LIMB_SPRING = 0.1F;
	private static final float LIMB_DAMPING = 0.82F;
	private static final float LIMB_DRIFT = 0.07F;
	private static final float LEAN_PIVOT = 0.5F;

	// ===== active swing: the knobs to tweak after playtesting =========================================================
	/**
	 * Swinging along on a swing web (not a zip, yank or whip), the body drives the swing, fully once it moves this fast
	 * (blocks/tick) and mostly the way it faces; slower, or going backwards, it hangs limp as before.
	 */
	private static final float DRIVE_SPEED = 0.45F;
	/**
	 * The legs through the arc (thigh angle, radians; negative = forward): back and straight on the drop ({@code LEGS_DROP}),
	 * tucked up through the bottom ({@code LEGS_TUCK}), kicked out ahead on the upswing ({@code LEGS_KICK}). The bottom is
	 * where the climb or fall is under {@code BOTTOM_BAND} blocks/tick; the drop and the upswing are full at
	 * {@code DROP_FULL} / {@code RISE_FULL}.
	 */
	private static final float LEGS_DROP = 0.3F, LEGS_TUCK = -1.2F, LEGS_KICK = -1.65F;
	private static final float BOTTOM_BAND = 0.18F, DROP_FULL = 0.35F, RISE_FULL = 0.3F;
	/** The legs follow on a quick spring, so the kick whips. Tucked, the knees part this far (radians each). */
	private static final float PUMP_SPRING = 0.32F, PUMP_DAMPING = 0.68F;
	private static final float TUCK_SPLAY = 0.22F;
	/** Kicking, the legs scissor: the lead leg this much further ahead than the other (radians). */
	private static final float KICK_SCISSOR = 0.3F;
	/** Swinging from one hand, the free arm flares out to the side ({@code FLARE_OUT}) and a little back ({@code FLARE_BACK}). */
	private static final float FLARE_OUT = 1.25F, FLARE_BACK = 0.35F;
	/** Banking into a turn: roll per (radians turned per tick x blocks/tick), and the most it banks (radians). */
	private static final float BANK_PER_TURN = 9.0F, BANK_MAX = 0.55F;

	// ===== the heave: the upper body on a Yank or Web Whip line ======================================================
	/**
	 * The torso turns at the waist after the catch: twisting toward it ({@code HEAVE_TWIST} of its bearing, at most
	 * {@code HEAVE_TWIST_MAX}), bending forward as it's in front ({@code HEAVE_BEND}) and further into a slam below
	 * ({@code HEAVE_SLAM}), back under it overhead ({@code HEAVE_BACK}), and leaning toward its side ({@code HEAVE_LEAN}).
	 * All radians.
	 */
	private static final float HEAVE_TWIST = 0.75F, HEAVE_TWIST_MAX = 1.0F;
	private static final float HEAVE_BEND = 0.4F, HEAVE_SLAM = 0.35F, HEAVE_BACK = 0.5F, HEAVE_LEAN = 0.38F;
	/** A Yank hauling something in leans back against it, this far (radians) per block/tick it comes, at most {@code HAUL_MAX}. */
	private static final float HAUL_LEAN = 0.5F, HAUL_MAX = 0.35F;
	/**
	 * The faster the catch flies (full at {@code HEAVE_FAST} blocks/tick), the harder it drags the torso: from
	 * {@code HEAVE_SLOW} of the angles above up to all of them.
	 */
	private static final float HEAVE_FAST = 1.4F, HEAVE_SLOW = 0.6F;
	/** The torso's spring and damping: soft and underdamped, so it lags the catch and swings past it, like weight. */
	private static final float HEAVE_SPRING = 0.16F, HEAVE_DAMPING = 0.8F;
	/** How far the torso may bend (forward or back), lean and twist at most (radians). */
	private static final float HEAVE_BEND_MAX = 0.75F, HEAVE_LEAN_MAX = 0.5F;
	/** The waist the torso turns about: this far below the neck (model units). */
	private static final float WAIST = 12.0F;
	/**
	 * The planted stance (thigh angles, radians): the leg on the main-arm side back ({@code STANCE_BACK}), the other
	 * forward ({@code STANCE_FORWARD}), feet apart ({@code STANCE_SPREAD}) and turned out ({@code STANCE_TURN}). Walking,
	 * it gives way to the walk, all of it by {@code STANCE_WALK} walk speed.
	 */
	private static final float STANCE_BACK = 0.32F, STANCE_FORWARD = -0.28F, STANCE_SPREAD = 0.14F, STANCE_TURN = 0.18F;
	private static final float STANCE_WALK = 0.6F;

	/** A player's springy state. Directions are world-space unit vectors (from the shoulder toward the web). */
	private static final class Body {
		final Vec3[] dir = new Vec3[2];
		final Vec3[] prevDir = new Vec3[2];
		final Vec3[] dirVel = {Vec3.ZERO, Vec3.ZERO};
		float trail, trailVel, prevTrail;
		float side, sideVel, prevSide;
		float amount, prevAmount;
		float wings, prevWings;
		float brake, prevBrake;
		float dive, prevDive;
		int wingsAge;
		float lean, prevLean;
		float swayPitch, swayPitchVel, prevSwayPitch;
		float swayRoll, swayRollVel, prevSwayRoll;
		float armSway, armSwayVel, prevArmSway;
		float legSway, legSwayVel, prevLegSway;
		float limbRoll, limbRollVel, prevLimbRoll;
		/** Active swing: how much the body drives it (0-1), the legs' angle through the arc, and how tucked they are. */
		float drive, prevDrive;
		float pump, pumpVel, prevPump;
		float tuck, prevTuck;
		/** Last tick's motion, for the turn. */
		Vec3 lastMotion = Vec3.ZERO;
		/** Ticks into the current Web Zip (0 = not zipping). */
		int zipAge;
		/** On a Yank line (0-1); the torso's turn at the waist (model-part radians); where the catch was last tick. */
		float heave, prevHeave;
		float torsoX, torsoXVel, prevTorsoX;
		float torsoY, torsoYVel, prevTorsoY;
		float torsoZ, torsoZVel, prevTorsoZ;
		@Nullable Vec3 lastCatch;
	}

	private static final Map<Integer, Body> BODIES = new HashMap<>();

	private WebArms() {
	}

	// ---- ticking --------------------------------------------------------------------------------------------------

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			BODIES.clear();
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			Vec3 main = lineEnd(minecraft, level, player, WebNet.MAIN, 1.0F);
			Vec3 off = lineEnd(minecraft, level, player, WebNet.OFF, 1.0F);
			boolean zipping = zipping(minecraft, player);
			boolean winged = isWinged(minecraft, player);
			boolean braking = winged && isBraking(minecraft, player);
			Body body = BODIES.get(player.getId());
			if (body == null) {
				if (main == null && off == null && !winged && !zipping) {
					continue;
				}
				body = new Body();
				BODIES.put(player.getId(), body);
			}
			body.prevWings = body.wings;
			body.prevBrake = body.brake;
			body.brake = braking ? Math.min(1.0F, body.brake + 0.25F) : Math.max(0.0F, body.brake - 0.2F);
			// braked and dropping: tip over into the dive as the fall picks up
			body.prevDive = body.dive;
			float fall = (float)(player.yo - player.getY());
			float dive = braking ? Mth.clamp((fall - DIVE_FROM) / (DIVE_FULL - DIVE_FROM), 0.0F, 1.0F) : 0.0F;
			body.dive = dive > body.dive ? Math.min(dive, body.dive + 0.08F) : Math.max(dive, body.dive - 0.15F);
			body.wings = winged ? Math.min(1.0F, body.wings + 0.3F) : Math.max(0.0F, body.wings - 0.2F);
			// the lean comes on gradually through the glide (smoothstep), and comes back up quickly when the wings fold
			body.wingsAge = winged ? body.wingsAge + 1 : 0;
			body.prevLean = body.lean;
			float t = Math.min(1.0F, body.wingsAge / LEAN_TICKS);
			body.lean = winged ? t * t * (3.0F - 2.0F * t) : Math.max(0.0F, body.lean - 0.15F);
			body.zipAge = zipping ? body.zipAge + 1 : 0;
			boolean swinging = !zipping && !winged && (swingLine(minecraft, player, WebNet.MAIN) || swingLine(minecraft, player, WebNet.OFF))
				&& !twoHanded(player);
			tickBody(player, body, main, off, swinging);
			tickHeave(player, body, yanking(player) ? (main != null ? main : off) : null);
		}
		BODIES.entrySet().removeIf(entry -> entry.getValue().amount <= 0.0F && entry.getValue().prevAmount <= 0.0F
			&& entry.getValue().wings <= 0.0F && entry.getValue().prevWings <= 0.0F || level.getEntity(entry.getKey()) == null);
	}

	/** This hand's web is a swing line (stuck to the world or a thing, swung from), for any player. */
	private static boolean swingLine(Minecraft minecraft, Player player, int hand) {
		if (player == minecraft.player && WebPowerClient.isActive(minecraft.player)) {
			return WebPowerClient.INSTANCE.swing.strands[hand].attached;
		}
		WebLine line = player.getAttached(WebPower.line(hand));
		return line != null && line.kind() == WebLine.SWING;
	}

	/**
	 * On a Web Zip's line right now. The local player: while the reel-in lasts. Others: a zip-style line out while off
	 * the ground (a webline being strung from where you stand draws the same line, but that's one hand).
	 */
	static boolean zipping(Minecraft minecraft, Player player) {
		WebLine line = player.getAttached(WebPower.LINE);
		if (line == null || line.kind() != WebLine.ZIP) {
			return false;
		}
		if (player == minecraft.player) {
			return WebPowerClient.isActive(minecraft.player) && WebPowerClient.INSTANCE.swing.pull != null;
		}
		return !player.onGround();
	}

	private static boolean isWinged(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WebPowerClient.isActive(minecraft.player) && WebPowerClient.INSTANCE.swing.wingsTicks > 0;
		}
		return player.getAttached(WebPower.WINGS) != null;
	}

	private static boolean isBraking(Minecraft minecraft, Player player) {
		if (player == minecraft.player) {
			return WebPowerClient.INSTANCE.swing.braking;
		}
		return player.getAttached(WebPower.WINGS_BRAKE) != null;
	}

	/**
	 * The glide lean (degrees, tipping the top forward), easing back past upright as the wings flare to brake, and over
	 * head-first in the dive.
	 */
	private static float leanDegrees(float lean, float brake, float dive) {
		return Mth.lerp(dive, GLIDE_LEAN * lean * (1.0F - brake) - BRAKE_LEAN_BACK * brake, DIVE_PITCH);
	}

	/** The airbrake's forward arm beat (radians ahead of the spread), for a player's age in ticks. */
	private static float brakeReach(float ageInTicks) {
		return BRAKE_REACH + BRAKE_BEAT * (0.5F + 0.5F * Mth.sin(ageInTicks * BRAKE_BEAT_SPEED));
	}

	/** How far a player's wings are flared to brake this frame (0-1). */
	static float brake(Player player, float partialTick) {
		Body body = BODIES.get(player.getId());
		return body == null ? 0.0F : Mth.lerp(partialTick, body.prevBrake, body.brake);
	}

	/** How spread a player's wings are this frame (0 = folded). */
	static float wings(Player player, float partialTick) {
		Body body = BODIES.get(player.getId());
		return body == null ? 0.0F : Mth.lerp(partialTick, body.prevWings, body.wings);
	}

	// ---- the posed body ---------------------------------------------------------------------------------------------

	/** This frame's glide lean (radians, tipping the top forward) and swing sway (radians), as drawn. */
	private static float leanAngle(Body body, float partialTick) {
		return leanDegrees(Mth.lerp(partialTick, body.prevLean, body.lean), Mth.lerp(partialTick, body.prevBrake, body.brake),
			Mth.lerp(partialTick, body.prevDive, body.dive)) * Mth.DEG_TO_RAD;
	}

	private static float swayPitch(Body body, float partialTick) {
		return Mth.lerp(partialTick, body.prevSwayPitch, body.swayPitch) * Mth.lerp(partialTick, body.prevAmount, body.amount);
	}

	private static float swayRoll(Body body, float partialTick) {
		return Mth.lerp(partialTick, body.prevSwayRoll, body.swayRoll) * Mth.lerp(partialTick, body.prevAmount, body.amount);
	}

	/** Rotates {@code v} about +X (right-handed). */
	private static Vec3 rotX(Vec3 v, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return new Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c);
	}

	/** Rotates {@code v} about +Z (right-handed). */
	private static Vec3 rotZ(Vec3 v, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle);
		return new Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z);
	}

	/**
	 * A point on the upright body moved to where the posed body has it, the same way {@link #rotate} turns the model:
	 * first the sway about the shoulders, then the glide lean about the middle. Body-local axes: x right, y up, z back.
	 */
	static Vec3 posed(Player player, Vec3 world, float partialTick) {
		Body body = BODIES.get(player.getId());
		if (body == null) {
			return world;
		}
		float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		Vec3 feet = player.getPosition(partialTick);
		Vec3 d = world.subtract(feet);
		Vec3 local = new Vec3(d.dot(right), d.y, d.dot(back));
		double height = player.getBbHeight();
		Vec3 swayPivot = new Vec3(0.0, height * SWAY_PIVOT, 0.0);
		local = rotX(rotZ(local.subtract(swayPivot), -swayRoll(body, partialTick)), -swayPitch(body, partialTick)).add(swayPivot);
		Vec3 leanPivot = new Vec3(0.0, height * LEAN_PIVOT, 0.0);
		local = rotX(local.subtract(leanPivot), -leanAngle(body, partialTick)).add(leanPivot);
		return feet.add(right.scale(local.x)).add(0.0, local.y, 0.0).add(back.scale(local.z));
	}

	/** A world direction as the posed body sees it (undoing the lean and sway), for aiming the arms within it. */
	private static Vec3 unposedDir(Player player, Body body, Vec3 dir, float partialTick) {
		float yaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Vec3 back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		Vec3 local = new Vec3(dir.dot(right), dir.y, dir.dot(back));
		local = rotX(local, leanAngle(body, partialTick));
		local = rotZ(rotX(local, swayPitch(body, partialTick)), swayRoll(body, partialTick));
		return right.scale(local.x).add(0.0, local.y, 0.0).add(back.scale(local.z));
	}

	/** A spread wing's tip (the hand, out to the side) for the body's right ({@code side} 1) or left (-1). */
	static Vec3 wingTip(Player player, int side, float spread, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		double scale = player.getScale();
		Vec3 shoulder = player.getPosition(partialTick).add(right.scale(side * SHOULDER_OUT * scale)).add(0.0, SHOULDER_HEIGHT * scale, 0.0);
		Body body = BODIES.get(player.getId());
		float brake = body == null ? 0.0F : Mth.lerp(partialTick, body.prevBrake, body.brake);
		float dive = body == null ? 0.0F : Mth.lerp(partialTick, body.prevDive, body.dive);
		// the arm swings forward (reach) then out to the side (angle), as the model turns it
		double angle = Mth.lerp(dive, Mth.lerp(brake, WING_SPREAD, BRAKE_SPREAD), DIVE_SPREAD) * spread;
		double reach = Mth.lerp(dive, brake * brakeReach(player.tickCount + partialTick), -DIVE_SWEEP) * spread;
		Vec3 back = new Vec3(Mth.sin(bodyYaw), 0.0, -Mth.cos(bodyYaw));
		double length = ARM_LENGTH * 1.25 * scale;
		return posed(player, shoulder.add(right.scale(side * Math.sin(angle) * Math.cos(reach) * length))
			.add(0.0, -Math.cos(angle) * Math.cos(reach) * length, 0.0).add(back.scale(-Math.sin(reach) * length)), partialTick);
	}

	/** Where a wing's web meets the body: the hip on that side, and the shoulder. */
	static Vec3 wingHip(Player player, int side, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		return posed(player, player.getPosition(partialTick).add(right.scale(side * 0.22 * player.getScale())).add(0.0, 0.7 * player.getScale(), 0.0),
			partialTick);
	}

	static Vec3 wingShoulder(Player player, int side, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
		return posed(player, player.getPosition(partialTick).add(right.scale(side * SHOULDER_OUT * player.getScale()))
			.add(0.0, SHOULDER_HEIGHT * player.getScale(), 0.0), partialTick);
	}

	private static void tickBody(Player player, Body body, @Nullable Vec3 main, @Nullable Vec3 off, boolean swinging) {
		boolean hanging = main != null || off != null;
		body.prevAmount = body.amount;
		body.amount = hanging ? Math.min(1.0F, body.amount + EASE) : Math.max(0.0F, body.amount - EASE);
		Vec3[] targets = {main, off};
		// both webs on the one catch: both hands grip the one line, together in front of the chest
		Vec3 grip = null;
		if (main != null && off != null && twoHanded(player)) {
			Vec3 chest = shoulder(player, WebNet.MAIN, 1.0F).lerp(shoulder(player, WebNet.OFF, 1.0F), 0.5);
			Vec3 out = main.subtract(chest);
			grip = out.lengthSqr() < 1.0E-6 ? null : chest.add(out.normalize().scale(GRIP_REACH * player.getScale()));
		}
		for (int hand = 0; hand < 2; hand++) {
			Vec3 target = grip != null ? grip : targets[hand];
			if (target == null) {
				body.dir[hand] = null;
				body.prevDir[hand] = null;
				body.dirVel[hand] = Vec3.ZERO;
				continue;
			}
			Vec3 shoulder = shoulder(player, hand, 1.0F);
			Vec3 want = target.subtract(shoulder);
			want = want.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : want.normalize();
			if (body.zipAge > ZIP_SHOOT_TICKS) {
				// the zip has caught: haul back on it, the hands trailing behind as the body flies on
				float sweep = Math.min(1.0F, (body.zipAge - ZIP_SHOOT_TICKS) / (float)ZIP_SWEEP_TICKS);
				sweep = sweep * sweep * (3.0F - 2.0F * sweep);
				want = want.lerp(trail(player), sweep).normalize();
			}
			if (body.dir[hand] == null) {
				// a fresh web: the arm starts from hanging at the side and snaps up to it
				body.dir[hand] = new Vec3(0.0, -1.0, 0.0);
				body.dirVel[hand] = Vec3.ZERO;
			}
			body.prevDir[hand] = body.dir[hand];
			Vec3 vel = body.dirVel[hand].add(want.subtract(body.dir[hand]).scale(ARM_SPRING)).scale(ARM_DAMPING);
			Vec3 next = body.dir[hand].add(vel);
			body.dirVel[hand] = vel;
			body.dir[hand] = next.lengthSqr() < 1.0E-6 ? want : next.normalize();
		}
		// legs: trail behind the body's motion (in its own frame) and flop sideways, like a hanging ragdoll
		Vec3 v = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double forwardSpeed = -Mth.sin(yaw) * v.x + Mth.cos(yaw) * v.z;
		double sideSpeed = Mth.cos(yaw) * v.x + Mth.sin(yaw) * v.z;
		boolean airborne = hanging && !player.onGround();
		// and the whole body swings from the shoulders, opposite to the motion
		double rightSpeed = -sideSpeed;
		float pitchWant = airborne ? Mth.clamp((float)(forwardSpeed * SWAY_PER_SPEED - v.y * SWAY_PER_RISE), -SWAY_MAX, SWAY_MAX) : 0.0F;
		float rollWant = airborne ? Mth.clamp((float)(rightSpeed * SWAY_PER_SPEED), -SWAY_MAX, SWAY_MAX) : 0.0F;
		tickDrive(body, v, forwardSpeed, airborne && swinging);
		// driving the swing, the body banks into a turn (toward its inside, as the rope pulls it round)
		rollWant += bank(body.lastMotion, v) * body.drive;
		body.lastMotion = v;
		body.prevSwayPitch = body.swayPitch;
		body.swayPitchVel = (body.swayPitchVel + (pitchWant - body.swayPitch) * SWAY_SPRING) * SWAY_DAMPING;
		body.swayPitch += body.swayPitchVel;
		body.prevSwayRoll = body.swayRoll;
		body.swayRollVel = (body.swayRollVel + (rollWant - body.swayRoll) * SWAY_SPRING) * SWAY_DAMPING;
		body.swayRoll += body.swayRollVel;
		// the free limbs chase the sway, not its target, so they settle a beat after the body does
		body.prevArmSway = body.armSway;
		body.armSwayVel = (body.armSwayVel + (body.swayPitch - body.armSway) * LIMB_SPRING) * LIMB_DAMPING;
		body.armSway += body.armSwayVel;
		body.prevLegSway = body.legSway;
		body.legSwayVel = (body.legSwayVel + (body.swayPitch - body.legSway) * LIMB_SPRING * 0.8F) * LIMB_DAMPING;
		body.legSway += body.legSwayVel;
		body.prevLimbRoll = body.limbRoll;
		body.limbRollVel = (body.limbRollVel + (body.swayRoll - body.limbRoll) * LIMB_SPRING) * LIMB_DAMPING;
		body.limbRoll += body.limbRollVel;
		float trailWant = airborne ? Mth.clamp((float)(forwardSpeed - v.y * 0.35) * LEG_TRAIL_PER_SPEED, -LEG_TRAIL_MAX, LEG_TRAIL_MAX) : 0.0F;
		float sideWant = airborne ? Mth.clamp((float)sideSpeed * 0.8F, -0.6F, 0.6F) : 0.0F;
		body.prevTrail = body.trail;
		body.trailVel = (body.trailVel + (trailWant - body.trail) * LEG_SPRING) * LEG_DAMPING;
		body.trail += body.trailVel;
		body.prevSide = body.side;
		body.sideVel = (body.sideVel + (sideWant - body.side) * LEG_SPRING) * LEG_DAMPING;
		body.side += body.sideVel;
	}

	/**
	 * The active swing's legs: where in the arc the body is, from its climb or fall, sets how far back, tucked or kicked
	 * out they want to be, and they chase that on a quick spring.
	 */
	private static void tickDrive(Body body, Vec3 v, double forwardSpeed, boolean swinging) {
		double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
		float ahead = horizontal < 1.0E-3 ? 0.0F : (float)(forwardSpeed / horizontal);
		float driveWant = swinging ? Mth.clamp((float)horizontal / DRIVE_SPEED, 0.0F, 1.0F) * Mth.clamp((ahead + 0.2F) / 0.8F, 0.0F, 1.0F) : 0.0F;
		body.prevDrive = body.drive;
		body.drive += (driveWant - body.drive) * 0.2F;
		float drop = Mth.clamp((float)-v.y / DROP_FULL, 0.0F, 1.0F);
		float rise = Mth.clamp((float)v.y / RISE_FULL, 0.0F, 1.0F);
		float bottom = Mth.clamp(1.0F - (float)Math.abs(v.y) / BOTTOM_BAND, 0.0F, 1.0F);
		float total = drop + rise + bottom;
		float want = total < 1.0E-3 ? 0.0F : (drop * LEGS_DROP + bottom * LEGS_TUCK + rise * LEGS_KICK) / total;
		body.prevPump = body.pump;
		body.pumpVel = (body.pumpVel + (want - body.pump) * PUMP_SPRING) * PUMP_DAMPING;
		body.pump += body.pumpVel;
		body.prevTuck = body.tuck;
		body.tuck += ((total < 1.0E-3 ? 0.0F : bottom / total) - body.tuck) * 0.3F;
	}

	/** On a Yank web: a Yank line out from either hand (the Yank, a Web Whip), or holding something on its lead. */
	private static boolean yanking(Player player) {
		for (int hand = 0; hand < 2; hand++) {
			WebLine line = player.getAttached(WebPower.line(hand));
			if (line != null && line.kind() == WebLine.YANK) {
				return true;
			}
		}
		return player.getAttached(WebPower.TETHER) != null;
	}

	/**
	 * The heave: the torso chases where the catch is (and how hard it's moving) on a soft spring, so it trails behind
	 * the whirl and swings past it at each turn, as if the weight were pulling it round.
	 */
	private static void tickHeave(Player player, Body body, @Nullable Vec3 caught) {
		body.prevHeave = body.heave;
		body.heave = caught != null ? Math.min(1.0F, body.heave + 0.2F) : Math.max(0.0F, body.heave - 0.15F);
		float xWant = 0.0F, yWant = 0.0F, zWant = 0.0F;
		if (caught != null) {
			Vec3 chest = player.getPosition(1.0F).add(0.0, SHOULDER_HEIGHT * player.getScale(), 0.0);
			Vec3 d = caught.subtract(chest);
			Vec3 motion = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
			Vec3 moved = body.lastCatch == null ? Vec3.ZERO : caught.subtract(body.lastCatch).subtract(motion);
			if (d.lengthSqr() > 1.0E-4) {
				d = d.normalize();
				float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
				// the catch in the body's frame: ahead, to the right, above
				float f = (float)(-Mth.sin(yaw) * d.x + Mth.cos(yaw) * d.z);
				float r = (float)(-Mth.cos(yaw) * d.x - Mth.sin(yaw) * d.z);
				float u = (float)d.y;
				float level = Mth.sqrt(f * f + r * r);
				float pull = HEAVE_SLOW + (1.0F - HEAVE_SLOW) * Mth.clamp((float)moved.length() / HEAVE_FAST, 0.0F, 1.0F);
				// twist after it (+ turns the chest to the right), less as it goes over the top and its bearing means little
				yWant = Mth.clamp((float)Mth.atan2(r, f) * HEAVE_TWIST, -HEAVE_TWIST_MAX, HEAVE_TWIST_MAX) * Math.min(1.0F, level * 1.5F);
				// + bends the top forward: into it in front (deeper into a slam), back under it overhead and behind
				xWant = HEAVE_BEND * f + HEAVE_SLAM * Math.max(0.0F, -u) * Math.max(0.0F, f) - HEAVE_BACK * Math.max(0.0F, u);
				// + leans the top to the left: lean toward its side
				zWant = -HEAVE_LEAN * r;
				if (!twoHanded(player)) {
					// a Yank hauling it in: lean back against the line as it comes
					double coming = -moved.dot(d);
					xWant -= Mth.clamp((float)coming * HAUL_LEAN, 0.0F, HAUL_MAX);
				}
				xWant = Mth.clamp(xWant * pull, -HEAVE_BEND_MAX, HEAVE_BEND_MAX);
				yWant *= pull;
				zWant = Mth.clamp(zWant * pull, -HEAVE_LEAN_MAX, HEAVE_LEAN_MAX);
			}
		}
		body.lastCatch = caught;
		body.prevTorsoX = body.torsoX;
		body.torsoXVel = (body.torsoXVel + (xWant - body.torsoX) * HEAVE_SPRING) * HEAVE_DAMPING;
		body.torsoX += body.torsoXVel;
		body.prevTorsoY = body.torsoY;
		body.torsoYVel = (body.torsoYVel + (yWant - body.torsoY) * HEAVE_SPRING) * HEAVE_DAMPING;
		body.torsoY += body.torsoYVel;
		body.prevTorsoZ = body.torsoZ;
		body.torsoZVel = (body.torsoZVel + (zWant - body.torsoZ) * HEAVE_SPRING) * HEAVE_DAMPING;
		body.torsoZ += body.torsoZVel;
	}

	/** The torso's turn at the waist this frame, as drawn ({x, y, z} model-part radians), or null when it isn't turned. */
	private static float @Nullable [] torso(Body body, float partialTick) {
		float w = Mth.lerp(partialTick, body.prevHeave, body.heave) * Mth.lerp(partialTick, body.prevAmount, body.amount);
		if (w <= 0.001F) {
			return null;
		}
		return new float[] {Mth.lerp(partialTick, body.prevTorsoX, body.torsoX) * w, Mth.lerp(partialTick, body.prevTorsoY, body.torsoY) * w,
			Mth.lerp(partialTick, body.prevTorsoZ, body.torsoZ) * w};
	}

	/** A point of the upper body (model units) moved with the torso's turn about the waist under {@code neck}. */
	private static Vector3f turned(Quaternionf turn, float neckY, float x, float y, float z) {
		Vector3f p = new Vector3f(x, y - neckY - WAIST, z);
		turn.transform(p);
		return p.add(0.0F, neckY + WAIST, 0.0F);
	}

	/** How far to bank (radians, + = toward the right) for turning from {@code before} to {@code now}. */
	private static float bank(Vec3 before, Vec3 now) {
		double was = Math.sqrt(before.x * before.x + before.z * before.z), is = Math.sqrt(now.x * now.x + now.z * now.z);
		if (was < 0.05 || is < 0.05) {
			return 0.0F;
		}
		// the sine of the turn, + to the right of the way it was going (the right of heading (x, z) is (-z, x))
		double turn = (now.x * -before.z + now.z * before.x) / (was * is);
		return Mth.clamp((float)(turn * is) * BANK_PER_TURN, -BANK_MAX, BANK_MAX);
	}

	/** Straight back from the way the body is flying, and a little down: where a zipping slinger's hands trail. */
	private static Vec3 trail(Player player) {
		Vec3 v = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
		Vec3 back;
		if (v.lengthSqr() > 1.0E-4) {
			back = v.normalize().scale(-1.0);
		} else {
			float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
			back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		}
		return back.add(0.0, -ZIP_TRAIL_DROP, 0.0).normalize();
	}

	/** Where the hand's web ends for any player: the local player's own state, else the synced lines. */
	static @Nullable Vec3 lineEnd(Minecraft minecraft, ClientLevel level, Player player, int hand, float partialTick) {
		Vec3 end;
		if (player == minecraft.player && WebPowerClient.isActive(minecraft.player)) {
			end = WebPowerClient.INSTANCE.swing.lineEnd(hand, partialTick);
			if (end == null && (hand == WebNet.MAIN || twoHanded(player))) {
				// a line the server put out from the main hand (a webline being strung, a yank) draws like any other, and
				// so do both of a Web Whip's (the off hand's too, or only one arm would follow the whirl)
				end = syncedEnd(level, player, hand, partialTick);
			}
		} else {
			end = syncedEnd(level, player, hand, partialTick);
		}
		if (end == null && hand == WebNet.MAIN) {
			// the Yank web holding something, like a lead
			Integer held = player.getAttached(WebPower.TETHER);
			Entity entity = held == null ? null : level.getEntity(held);
			if (entity != null) {
				end = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
			}
		}
		if (end == null && hand == WebNet.OFF && zipping(minecraft, player)) {
			// a Web Zip fires from both hands at the one target
			end = lineEnd(minecraft, level, player, WebNet.MAIN, partialTick);
		}
		return end;
	}

	/**
	 * Both hands' webs are on the same thing (a Web Whip with both webs on its catch): drawn as one web held in both
	 * hands, not two.
	 */
	static boolean twoHanded(Player player) {
		WebLine main = player.getAttached(WebPower.line(WebNet.MAIN));
		WebLine off = player.getAttached(WebPower.line(WebNet.OFF));
		return main != null && off != null && main.kind() == WebLine.YANK && off.kind() == WebLine.YANK
			&& main.entityId() >= 0 && main.entityId() == off.entityId();
	}

	private static @Nullable Vec3 syncedEnd(ClientLevel level, Player player, int hand, float partialTick) {
		WebLine line = player.getAttached(WebPower.line(hand));
		if (line == null) {
			return null;
		}
		if (line.entityId() >= 0) {
			Entity entity = level.getEntity(line.entityId());
			if (entity != null) {
				return entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.5, 0.0);
			}
		}
		return line.anchor();
	}

	// ---- geometry -------------------------------------------------------------------------------------------------

	/** +1 for the right side of the body, -1 for the left, for a hand. */
	private static int side(Player player, int hand) {
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		boolean right = hand == WebNet.MAIN ? mainRight : !mainRight;
		return right ? 1 : -1;
	}

	/** The shoulder joint in the world. */
	static Vec3 shoulder(Player player, int hand, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double scale = player.getScale();
		double out = side(player, hand) * SHOULDER_OUT * scale;
		double up = SHOULDER_HEIGHT * scale, back = 0.0;
		Body body = BODIES.get(player.getId());
		float[] torso = body == null ? null : torso(body, partialTick);
		if (torso != null) {
			// turned with the torso about the waist (model units: x to the left, y down from the neck's height, z back)
			Vector3f p = turned(new Quaternionf().rotationZYX(torso[2], torso[1], torso[0]), 0.0F, (float)(-out / scale * 16.0),
				(float)(24.0 - SHOULDER_HEIGHT * 16.0), 0.0F);
			out = -p.x / 16.0 * scale;
			up = (24.0 - p.y) / 16.0 * scale;
			back = p.z / 16.0 * scale;
		}
		// the body's right is (-cos, -sin) in x/z for Minecraft's yaw, its back (sin, -cos); then wherever the lean and
		// sway have moved it
		return posed(player, player.getPosition(partialTick).add(-Mth.cos(bodyYaw) * out + Mth.sin(bodyYaw) * back, up,
			-Mth.sin(bodyYaw) * out - Mth.cos(bodyYaw) * back), partialTick);
	}

	/** Where the posed hand is (for the web line), or null if this player's arm isn't being posed for that hand. */
	static @Nullable Vec3 handPos(Player player, int hand, float partialTick) {
		Body body = BODIES.get(player.getId());
		if (body == null || body.dir[hand] == null) {
			return null;
		}
		Vec3 prev = body.prevDir[hand] == null ? body.dir[hand] : body.prevDir[hand];
		Vec3 dir = prev.lerp(body.dir[hand], partialTick);
		return shoulder(player, hand, partialTick).add(dir.scale(ARM_LENGTH * player.getScale()));
	}

	// ---- rendering ------------------------------------------------------------------------------------------------

	/** From the avatar renderer's extractRenderState: this frame's pose, in model-part angles. */
	public static void extract(Entity entity, AvatarRenderState state, float partialTicks) {
		Body body = BODIES.get(entity.getId());
		if (body == null || !(entity instanceof Player player) || state.isFallFlying || state.isVisuallySwimming) {
			state.setData(KEY, null);
			return;
		}
		float amount = Mth.lerp(partialTicks, body.prevAmount, body.amount);
		float wings = Mth.lerp(partialTicks, body.prevWings, body.wings);
		if (amount <= 0.001F && wings <= 0.001F) {
			state.setData(KEY, null);
			return;
		}
		if (wings > 0.001F) {
			// Sneak while gliding flares the wings to brake, as S does: the glide pose has the body, not a crouch
			state.isCrouching = false;
		}
		float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot);
		float[] main = aim(player, body, WebNet.MAIN, bodyYaw, partialTicks);
		float[] off = aim(player, body, WebNet.OFF, bodyYaw, partialTicks);
		state.setData(KEY, new Pose(amount, main, off,
			Mth.lerp(partialTicks, body.prevTrail, body.trail), Mth.lerp(partialTicks, body.prevSide, body.side), wings,
			Mth.lerp(partialTicks, body.prevLean, body.lean), Mth.lerp(partialTicks, body.prevBrake, body.brake),
			Mth.lerp(partialTicks, body.prevDive, body.dive), swayPitch(body, partialTicks),
			swayRoll(body, partialTicks), Mth.lerp(partialTicks, body.prevArmSway, body.armSway) * amount,
			Mth.lerp(partialTicks, body.prevLegSway, body.legSway) * amount, Mth.lerp(partialTicks, body.prevLimbRoll, body.limbRoll) * amount,
			!player.onGround() && !player.isInWater(), Mth.lerp(partialTicks, body.prevDrive, body.drive) * amount,
			Mth.lerp(partialTicks, body.prevPump, body.pump), Mth.lerp(partialTicks, body.prevTuck, body.tuck),
			Mth.lerp(partialTicks, body.prevHeave, body.heave) * amount, Mth.lerp(partialTicks, body.prevTorsoX, body.torsoX),
			Mth.lerp(partialTicks, body.prevTorsoY, body.torsoY), Mth.lerp(partialTicks, body.prevTorsoZ, body.torsoZ)));
	}

	/** An arm's {xRot, yRot} pointing it along its web, relative to the body (as the bow-aiming pose does it). */
	private static float @Nullable [] aim(Player player, Body body, int hand, float bodyYawDegrees, float partialTicks) {
		if (body.dir[hand] == null) {
			return null;
		}
		Vec3 prev = body.prevDir[hand] == null ? body.dir[hand] : body.prevDir[hand];
		Vec3 dir = prev.lerp(body.dir[hand], partialTicks);
		if (dir.lengthSqr() < 1.0E-6) {
			return null;
		}
		// aim within the swinging, leaning body, so the arm still points along its web
		dir = unposedDir(player, body, dir.normalize(), partialTicks).normalize();
		float elevation = (float)Math.asin(Mth.clamp(dir.y, -1.0, 1.0));
		float yaw = (float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
		float relative = Mth.wrapDegrees(yaw - bodyYawDegrees) * Mth.DEG_TO_RAD;
		return new float[] {-Mth.HALF_PI - elevation, relative};
	}

	/**
	 * After the body has been turned to face its way: a glider leans forward into the flying pose (about its middle),
	 * and a swinger sways from the shoulders. {@link #posed} moves points the same way.
	 */
	public static void rotate(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, float entityScale) {
		Pose pose = state.getData(KEY);
		if (pose == null || !(state instanceof AvatarRenderState) || state.deathTime > 0.0F) {
			return;
		}
		float height = state.boundingBoxHeight / Math.max(entityScale, 0.01F);
		float lean = leanDegrees(pose.lean(), pose.brake(), pose.dive());
		if (Math.abs(lean) > 0.05F) {
			poseStack.translate(0.0F, height * LEAN_PIVOT, 0.0F);
			poseStack.rotateDegrees(com.mojang.math.Axis.XP, -lean);
			poseStack.translate(0.0F, -height * LEAN_PIVOT, 0.0F);
		}
		if (Math.abs(pose.swayPitch()) > 1.0E-4F || Math.abs(pose.swayRoll()) > 1.0E-4F) {
			// local +Z is the body's back and +X its right: a negative turn about X sends the feet back, about Z left
			poseStack.translate(0.0F, height * SWAY_PIVOT, 0.0F);
			poseStack.rotateDegrees(com.mojang.math.Axis.XP, -pose.swayPitch() * Mth.RAD_TO_DEG);
			poseStack.rotateDegrees(com.mojang.math.Axis.ZP, -pose.swayRoll() * Mth.RAD_TO_DEG);
			poseStack.translate(0.0F, -height * SWAY_PIVOT, 0.0F);
		}
	}

	/** After the model animates (body and armour alike): the arms along their webs, the legs limp. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		if (pose.wings() > 0.001F) {
			// wings spread: arms straight out to the sides, legs together behind
			float w = pose.wings();
			float flutter = Mth.sin(avatar.ageInTicks * 0.8F) * 0.04F;
			set(humanoid.rightArm, 0.1F, 0.0F, WING_SPREAD + flutter, w);
			set(humanoid.leftArm, 0.1F, 0.0F, -WING_SPREAD - flutter, w);
			set(humanoid.rightLeg, 0.15F, 0.0F, 0.05F, w);
			set(humanoid.leftLeg, 0.15F, 0.0F, -0.05F, w);
			// leaning into the glide: head up to look ahead
			set(humanoid.head, -1.1F * pose.lean() * (1.0F - pose.brake()), humanoid.head.yRot, 0.0F, w);
			if (pose.brake() > 0.001F) {
				// airbrake: arms swept up and out, beating forward against the air, legs tucked up under
				float b = pose.brake() * w;
				float reach = brakeReach(avatar.ageInTicks);
				set(humanoid.rightArm, -reach, 0.0F, BRAKE_SPREAD, b);
				set(humanoid.leftArm, -reach, 0.0F, -BRAKE_SPREAD, b);
				set(humanoid.rightLeg, -BRAKE_TUCK, 0.0F, 0.12F, b);
				set(humanoid.leftLeg, -BRAKE_TUCK * 0.85F, 0.0F, -0.12F, b);
			}
			if (pose.dive() > 0.001F) {
				// the freedive: wings swept back along the body, legs straight and together, head tucked to see the ground
				float d = pose.dive() * w;
				set(humanoid.rightArm, DIVE_SWEEP, 0.0F, DIVE_SPREAD, d);
				set(humanoid.leftArm, DIVE_SWEEP, 0.0F, -DIVE_SPREAD, d);
				set(humanoid.rightLeg, 0.0F, 0.0F, 0.04F, d);
				set(humanoid.leftLeg, 0.0F, 0.0F, -0.04F, d);
				set(humanoid.head, -0.9F, humanoid.head.yRot, 0.0F, d);
			}
		}
		float amount = pose.amount();
		boolean mainRight = avatar.mainArm != HumanoidArm.LEFT;
		ModelPart mainArm = mainRight ? humanoid.rightArm : humanoid.leftArm;
		ModelPart offArm = mainRight ? humanoid.leftArm : humanoid.rightArm;
		if (pose.mainArm() != null) {
			set(mainArm, pose.mainArm()[0], pose.mainArm()[1], 0.0F, amount);
		}
		if (pose.offArm() != null) {
			set(offArm, pose.offArm()[0], pose.offArm()[1], 0.0F, amount);
		}
		// the limbs not on a web hang limp like a ragdoll's: trailing the sway, a little out from the body, drifting
		float age = avatar.ageInTicks;
		float armLag = pose.armSway() - pose.swayPitch();
		float legLag = pose.legSway() - pose.swayPitch();
		float spread = Math.abs(pose.limbRoll()) * 0.25F;
		float drive = pose.drive();
		if (pose.wings() <= 0.001F && pose.airborne()) {
			// driving a swing, the free arm flares out for balance; hanging, it dangles
			if (pose.mainArm() == null) {
				float sign = mainRight ? 1.0F : -1.0F;
				set(mainArm, Mth.lerp(drive, pose.armSway() * 0.5F + armLag * 0.8F, FLARE_BACK + armLag * 0.4F) + drift(age, 0.0F), 0.0F,
					sign * Mth.lerp(drive, 0.12F + spread, FLARE_OUT) + pose.limbRoll() * 0.3F + drift(age, 1.7F) * 0.4F, amount);
			}
			if (pose.offArm() == null) {
				float sign = mainRight ? -1.0F : 1.0F;
				set(offArm, Mth.lerp(drive, pose.armSway() * 0.5F + armLag * 0.8F, FLARE_BACK + armLag * 0.4F) + drift(age, 3.1F), 0.0F,
					sign * Mth.lerp(drive, 0.12F + spread, FLARE_OUT) + pose.limbRoll() * 0.3F + drift(age, 4.4F) * 0.4F, amount);
			}
		}
		// limp legs, each a little out of step so they don't move as one
		float flop = Mth.sin(avatar.ageInTicks * 0.35F) * 0.04F * Math.abs(pose.legTrail());
		// driving the swing they pump instead: back on the drop, knees tucked and apart at the bottom, kicked out ahead
		// on the upswing with the lead (main-side) leg a little further
		float kick = Mth.clamp((pose.pump() - LEGS_TUCK) / (LEGS_KICK - LEGS_TUCK), 0.0F, 1.0F) * KICK_SCISSOR;
		float splay = pose.tuck() * TUCK_SPLAY;
		float rightLead = mainRight ? -kick * 0.5F : kick * 0.5F;
		set(humanoid.rightLeg, Mth.lerp(drive, pose.legTrail() + 0.12F + flop + legLag * 0.9F, pose.pump() + rightLead + legLag * 0.3F)
			+ drift(age, 2.3F), 0.0F, Mth.lerp(drive, 0.08F + pose.legSide() * 0.9F, 0.06F + splay) + pose.limbRoll() * 0.15F, amount);
		set(humanoid.leftLeg, Mth.lerp(drive, pose.legTrail() * 0.8F - 0.05F - flop + legLag * 0.9F, pose.pump() - rightLead + legLag * 0.3F)
			+ drift(age, 5.9F), 0.0F, Mth.lerp(drive, -0.08F + pose.legSide() * 0.7F, -0.06F - splay) + pose.limbRoll() * 0.15F, amount);
		heave(humanoid, avatar, pose, mainRight);
	}

	/**
	 * On a Yank line: the torso turns at the waist with the weight (the head and shoulders carried round with it, the
	 * arms still reaching along their webs, a free arm swinging with the chest) over legs planted in a wide stance.
	 */
	private static void heave(HumanoidModel<?> h, AvatarRenderState avatar, Pose pose, boolean mainRight) {
		float w = pose.heave();
		if (w <= 0.001F || avatar.isCrouching) {
			return;
		}
		float tx = pose.torsoX() * w, ty = pose.torsoY() * w, tz = pose.torsoZ() * w;
		Quaternionf turn = new Quaternionf().rotationZYX(tz, ty, tx);
		float neckY = h.body.getInitialPose().y();
		Vector3f neck = turned(turn, neckY, h.body.getInitialPose().x(), neckY, h.body.getInitialPose().z());
		h.body.x = neck.x;
		h.body.y = neck.y;
		h.body.z = neck.z;
		h.body.xRot = tx;
		h.body.yRot = ty;
		h.body.zRot = tz;
		// the head rides on the neck, still looking where it looks
		h.head.x = neck.x;
		h.head.y = neck.y;
		h.head.z = neck.z;
		for (ModelPart arm : new ModelPart[] {h.rightArm, h.leftArm}) {
			net.minecraft.client.model.geom.PartPose rest = arm.getInitialPose();
			Vector3f shoulder = turned(turn, neckY, rest.x(), rest.y(), rest.z());
			arm.x = shoulder.x;
			arm.y = shoulder.y;
			arm.z = shoulder.z;
		}
		// an arm with no web out goes round with the chest (set, not added: this pass can run twice a frame)
		ModelPart mainArm = mainRight ? h.rightArm : h.leftArm;
		ModelPart offArm = mainRight ? h.leftArm : h.rightArm;
		for (ModelPart arm : new ModelPart[] {pose.mainArm() == null ? mainArm : null, pose.offArm() == null ? offArm : null}) {
			if (arm != null) {
				arm.yRot = Mth.lerp(w, arm.yRot, ty);
				arm.zRot = Mth.lerp(w, arm.zRot, tz + (arm == h.rightArm ? 0.1F : -0.1F));
			}
		}
		// the legs planted: the main side's back, the other forward, apart and turned out (on the ground, unless walking)
		float stance = w * (pose.airborne() ? 0.6F : 1.0F - Mth.clamp(avatar.walkAnimationSpeed / STANCE_WALK, 0.0F, 1.0F));
		float back = mainRight ? STANCE_BACK : STANCE_FORWARD, forward = mainRight ? STANCE_FORWARD : STANCE_BACK;
		set(h.rightLeg, back, STANCE_TURN, STANCE_SPREAD, stance);
		set(h.leftLeg, forward, -STANCE_TURN, -STANCE_SPREAD, stance);
	}

	/** A limb's slow wander (radians), out of step with the others by {@code phase}. */
	private static float drift(float age, float phase) {
		return LIMB_DRIFT * (Mth.sin(age * 0.07F + phase) * 0.6F + Mth.sin(age * 0.13F + phase * 1.9F) * 0.4F);
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}
}
