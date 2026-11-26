package com.jaymospowers.client.body;

import com.jaymospowers.body.Wading;
import com.jaymospowers.client.KneeBend;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Body animation of the small kind, laid over whatever the body already does, for every body drawn with the player
 * renderer (players and MCA's villagers alike; the idea of each after Punchy Moves, which is All Rights Reserved: the
 * code is our own). Each is worked out from synced state, so everyone in view sees the same, and none shows while one of
 * our own poses has the body ({@link PoseTakeover}), so any pose wins.
 * <ul>
 * <li><b>Feet turn first:</b> standing, look far to one side and the feet shuffle round under you, the body following
 * (vanilla turns it only past 50 degrees; BodyMotion turns the chest, this turns the legs).</li>
 * <li><b>Lean into turns:</b> sprinting round a corner, or riding a horse round one, the body tips into the turn.</li>
 * <li><b>Wading:</b> in water to the waist or more with the head out ({@link Wading}), the arms come up out of it.</li>
 * <li><b>Ladders:</b> hand over hand, a foot on each rung in turn, the pace of it set by how far you climb.</li>
 * <li><b>Shield arm:</b> a blow taken on a shield (told by ShieldBlockEventMixin) jolts the arm that holds it back.</li>
 * <li><b>Riding:</b> the rider sways with the horse's gait and holds the reins. Icy's Better Horses only seats riders
 * (its mixins set where they sit, none of them touches the rider's limbs), so nothing of its is fought.</li>
 * </ul>
 */
public final class BodyAnim {
	// ===== the knobs ================================================================================================
	/** Feet: the head must be this many degrees off the body before they turn, they follow this share of the rest, at most this many degrees. */
	static final float FOOT_FROM = 20.0F, FOOT_GAIN = 1.1F, FOOT_MAX = 38.0F;
	/** Feet: how fast they ease to where they should be (share a tick), how far the leading foot lifts (radians), the walk speed under which a body stands still. */
	static final float FOOT_EASE = 0.3F, FOOT_LIFT = 0.45F, STILL_BELOW = 0.04F;
	/** Lean: degrees of lean per degree turned a tick, at most this many degrees on foot and on a horse, how fast it eases (share a tick). */
	static final float LEAN_GAIN = 0.6F, LEAN_MAX = 10.0F, LEAN_MAX_RIDING = 7.0F, LEAN_EASE = 0.25F;
	/** Lean on foot needs the walk this fast (walk animation speed); on a horse it grows to full by this many blocks a tick. */
	static final float LEAN_FROM_SPEED = 0.6F, LEAN_HORSE_FULL = 0.25F;
	/** Wading: the arms come up (pitch) and out to the side (radians), and slosh about by this much; how fast it eases. */
	static final float WADE_UP = -0.75F, WADE_OUT = 0.6F, WADE_SLOSH = 0.12F, WADE_EASE = 0.15F;
	/** Ladders: the arms overhead (pitch), how far each reaches up and down (radians), how far out; the legs' lift (radians); radians of the cycle a block climbed. */
	static final float CLIMB_ARM = -2.6F, CLIMB_REACH = 0.4F, CLIMB_OUT = 0.15F, CLIMB_LEG = -0.7F, CLIMB_KNEE = 0.9F, CLIMB_PHASE = 7.0F;
	/** Ladders: how fast the pose eases in and out (share a tick). */
	static final float CLIMB_EASE = 0.3F;
	/** Shield arm: how long the jolt lasts (ticks), how far the arm kicks back (radians) and the body tips back (degrees). */
	static final float JOLT_TICKS = 8.0F, JOLT_ARM = 0.7F, JOLT_TILT = 4.0F;
	/** Riding: the sway (degrees) and bob (blocks) at the gallop, the walk speed that counts as a full gait. */
	static final float RIDE_SWAY = 3.0F, RIDE_BOB = 0.035F, RIDE_FULL = 0.8F;
	/** Reins: the hands' pitch (radians), drawn in towards each other (radians), how far they rise and fall with the gait. */
	static final float REINS_PITCH = -1.0F, REINS_IN = 0.28F, REINS_BOB = 0.07F;
	// ================================================================================================================

	/**
	 * {@code foot}: radians the legs turn, {@code lift}: how far the leading foot is off the floor, {@code leadRight}: which
	 * foot leads; {@code lean}: degrees, positive into a right turn; {@code wade}, {@code climb}: 0-1 of the pose;
	 * {@code climbPhase}: radians of the hand-over-hand cycle; {@code jolt}: 0-1 left of a jolt, {@code joltArm}: the
	 * shield arm; {@code ride}: 0-1 riding a horse, {@code gait}: the horse's walk position, {@code gaitSpeed}: 0-1.
	 */
	public record Anim(float foot, float lift, boolean leadRight, float lean, float wade, float climb, float climbPhase, float jolt,
		HumanoidArm joltArm, float ride, float gait, float gaitSpeed) {
	}

	public static final RenderStateDataKey<Anim> KEY = RenderStateDataKey.create(() -> "jaymospowers:body_anim");

	/** Per entity id: the smoothed feet, lean, wade and climb; the last frame's time. */
	private static final Map<Integer, float[]> EASED = new HashMap<>();
	/** Per entity id: the game time a blocked blow last reached it. */
	private static final Map<Integer, Long> BLOCKED = new HashMap<>();

	private BodyAnim() {
	}

	/** Told by the client when a blow was taken on {@code entity}'s shield. */
	public static void blocked(LivingEntity entity) {
		BLOCKED.put(entity.getId(), entity.level().getGameTime());
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (!(state instanceof AvatarRenderState avatar) || state.deathTime > 0.0F || entity.isSleeping() || avatar.isSpectator
			|| entity instanceof Player player && Downed.isDowned(player)) {
			EASED.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		int id = entity.getId();
		float time = entity.level().getGameTime() + partialTicks;
		// [0] foot, [1] lean, [2] wade, [3] climb, [4] the time of the last frame
		float[] e = EASED.computeIfAbsent(id, i -> new float[] {0.0F, 0.0F, 0.0F, 0.0F, time});
		float dt = Mth.clamp(time - e[4], 0.0F, 3.0F);
		e[4] = time;
		boolean upright = state.pose == Pose.STANDING || state.pose == Pose.CROUCHING;
		Entity vehicle = entity.getVehicle();
		AbstractHorse horse = vehicle instanceof AbstractHorse h && avatar.isPassenger ? h : null;

		// the feet: only when standing still on the ground
		float footTarget = 0.0F;
		if (upright && horse == null && entity.onGround() && state.walkAnimationSpeed < STILL_BELOW && !avatar.isFallFlying && !avatar.isVisuallySwimming) {
			float off = Mth.wrapDegrees(state.yRot);
			if (Math.abs(off) > FOOT_FROM) {
				footTarget = Math.signum(off) * Math.min(FOOT_MAX, (Math.abs(off) - FOOT_FROM) * FOOT_GAIN);
			}
		}
		float ease = Math.min(1.0F, FOOT_EASE * dt);
		e[0] += (footTarget - e[0]) * ease;
		float lift = Mth.clamp(Math.abs(footTarget - e[0]) / 10.0F, 0.0F, 1.0F);

		// the lean into a turn: a sprint round a corner, or a horse round one
		float leanTarget = 0.0F;
		if (horse != null) {
			float turned = Mth.wrapDegrees(horse.getYRot() - horse.yRotO);
			float pace = Mth.clamp((float)horse.getDeltaMovement().horizontalDistance() / LEAN_HORSE_FULL, 0.0F, 1.0F);
			leanTarget = Mth.clamp(turned * LEAN_GAIN * pace, -LEAN_MAX_RIDING, LEAN_MAX_RIDING);
		} else if (upright && entity.isSprinting() && entity.onGround() && state.walkAnimationSpeed >= LEAN_FROM_SPEED) {
			leanTarget = Mth.clamp(Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO) * LEAN_GAIN, -LEAN_MAX, LEAN_MAX);
		}
		e[1] += (leanTarget - e[1]) * Math.min(1.0F, LEAN_EASE * dt);

		float wadeTarget = horse == null && Wading.wading(entity) ? 1.0F : 0.0F;
		e[2] += (wadeTarget - e[2]) * Math.min(1.0F, WADE_EASE * dt);
		float climbTarget = horse == null && !avatar.isPassenger && entity.onClimbable() ? 1.0F : 0.0F;
		e[3] += (climbTarget - e[3]) * Math.min(1.0F, CLIMB_EASE * dt);

		float jolt = 0.0F;
		Long at = BLOCKED.get(id);
		if (at != null) {
			float age = entity.level().getGameTime() + partialTicks - at;
			if (age >= 0.0F && age < JOLT_TICKS) {
				jolt = (1.0F - age / JOLT_TICKS) * (1.0F - age / JOLT_TICKS);
			} else {
				BLOCKED.remove(id);
			}
		}
		HumanoidArm shieldArm = entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();

		float ride = horse != null ? 1.0F : 0.0F;
		float gait = horse != null ? horse.walkAnimation.position(partialTicks) : 0.0F;
		float gaitSpeed = horse != null ? Mth.clamp(horse.walkAnimation.speed(partialTicks) / RIDE_FULL, 0.0F, 1.0F) : 0.0F;

		float y = Mth.lerp(partialTicks, (float)entity.yo, (float)entity.getY());
		float climbPhase = y * CLIMB_PHASE;
		if (Math.abs(e[0]) < 0.5F && Math.abs(e[1]) < 0.1F && e[2] < 0.01F && e[3] < 0.01F && jolt <= 0.0F && ride <= 0.0F) {
			state.setData(KEY, null);
			return;
		}
		// the foot that leads is the one on the side the body is turning towards (a turn to the right is the player's right leg)
		state.setData(KEY, new Anim(e[0] * Mth.DEG_TO_RAD, lift, e[0] >= 0.0F, e[1], e[2], e[3], climbPhase, jolt, shieldArm, ride, gait, gaitSpeed));
	}

	private static @Nullable Anim anim(LivingEntityRenderState state) {
		if (!(state instanceof AvatarRenderState avatar) || state.deathTime > 0.0F || PoseTakeover.posed(avatar)) {
			return null;
		}
		return state.getData(KEY);
	}

	/** After the body faces its way: the lean into a turn, the tip back of a blocked blow, the rider's sway and bob. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Anim a = anim(state);
		if (a == null) {
			return;
		}
		float sway = a.ride() > 0.0F ? Mth.sin(a.gait() * 0.6662F) * RIDE_SWAY * a.gaitSpeed() : 0.0F;
		if (a.ride() > 0.0F) {
			poseStack.translate(0.0F, Math.abs(Mth.cos(a.gait() * 0.6662F)) * RIDE_BOB * a.gaitSpeed(), 0.0F);
		}
		// local +X is the body's right: a negative turn about Z tips the top to the right, a positive turn about X back
		if (Math.abs(a.lean() + sway) > 0.01F) {
			poseStack.rotateDegrees(Axis.ZP, -(a.lean() + sway));
		}
		if (a.jolt() > 0.0F) {
			poseStack.rotateDegrees(Axis.XP, JOLT_TILT * a.jolt());
		}
	}

	/** After the model animates: the feet shuffled round, the arms up out of the water, on the rungs, on the reins, and the shield arm's jolt. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Anim a = anim(living);
		if (a == null) {
			return;
		}
		if (Math.abs(a.foot()) > 0.01F) {
			h.rightLeg.yRot += a.foot();
			h.leftLeg.yRot += a.foot();
			ModelPart lead = a.leadRight() ? h.rightLeg : h.leftLeg;
			lead.xRot -= FOOT_LIFT * a.lift();
		}
		if (a.wade() > 0.01F) {
			float slosh = Mth.sin(((AvatarRenderState)living).ageInTicks * 0.12F) * WADE_SLOSH;
			arm(h.rightArm, WADE_UP + slosh, 0.0F, WADE_OUT, a.wade());
			arm(h.leftArm, WADE_UP - slosh, 0.0F, -WADE_OUT, a.wade());
		}
		if (a.climb() > 0.01F) {
			float p = a.climbPhase();
			arm(h.rightArm, CLIMB_ARM + Mth.sin(p) * CLIMB_REACH, 0.0F, CLIMB_OUT, a.climb());
			arm(h.leftArm, CLIMB_ARM - Mth.sin(p) * CLIMB_REACH, 0.0F, -CLIMB_OUT, a.climb());
			arm(h.rightLeg, CLIMB_LEG * Math.max(0.0F, Mth.sin(p + Mth.HALF_PI)), 0.0F, 0.0F, a.climb());
			arm(h.leftLeg, CLIMB_LEG * Math.max(0.0F, -Mth.sin(p + Mth.HALF_PI)), 0.0F, 0.0F, a.climb());
		}
		if (a.ride() > 0.0F && living instanceof AvatarRenderState avatar && avatar.swingAnimation <= 0.0F && !avatar.isUsingItem) {
			float bob = Mth.sin(a.gait() * 0.6662F + 1.0F) * REINS_BOB * a.gaitSpeed();
			arm(h.rightArm, REINS_PITCH + bob, -REINS_IN, 0.05F, 1.0F);
			arm(h.leftArm, REINS_PITCH - bob, REINS_IN, -0.05F, 1.0F);
		}
		if (a.jolt() > 0.0F) {
			(a.joltArm() == HumanoidArm.RIGHT ? h.rightArm : h.leftArm).xRot += JOLT_ARM * a.jolt();
		}
	}

	/** Last thing before the model is drawn: the knees of the climber's lifted foot. */
	public static void bendLimbs(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Anim a = anim(living);
		if (a == null || a.climb() < 0.01F) {
			return;
		}
		float p = a.climbPhase();
		KneeBend.bend(h.rightLeg, CLIMB_KNEE * Math.max(0.0F, Mth.sin(p + Mth.HALF_PI)) * a.climb());
		KneeBend.bend(h.leftLeg, CLIMB_KNEE * Math.max(0.0F, -Mth.sin(p + Mth.HALF_PI)) * a.climb());
	}

	private static void arm(ModelPart part, float x, float y, float z, float e) {
		part.xRot = Mth.lerp(e, part.xRot, x);
		part.yRot = Mth.lerp(e, part.yRot, y);
		part.zRot = Mth.lerp(e, part.zRot, z);
	}
}
