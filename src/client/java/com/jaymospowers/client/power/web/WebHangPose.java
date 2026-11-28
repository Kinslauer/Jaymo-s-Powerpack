package com.jaymospowers.client.power.web;

import com.jaymospowers.client.KneeBend;
import com.jaymospowers.client.rig.RigOctoClient;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.13.23) Hung from Wall Crawl's tendrils ({@link WebTendrilWalk#hanging}: carried between them off the ground, or on
 * a ceiling): no walking the air. The walk stops and the legs dangle, swinging slowly like a pendulum, the left a
 * little behind the right, and harder (trailing back) for a while after you were moving. A wall climb keeps its walk.
 * <p>Carried on the Rig's Octo Core arm walk ({@link RigOctoClient#carried}), the whole body goes limp the same way
 * (Andy: "the player should ragdoll within the arms the same way their legs hang when using the tendrils"): the legs
 * dangle and swing as above, the arms hang loose at the sides and swing a beat behind them, and the knees and elbows
 * give a little. The head keeps to the look, so first person still follows it. A swing, a drawn bow or anything held up
 * in use keeps the arms.
 */
public final class WebHangPose {
	// ===== the look: knobs to tweak ==================================================================================
	/** The legs at rest, hanging (radians: a touch forward, a touch apart). */
	static final float HANG_LEG_X = -0.08F, HANG_LEG_SPREAD = 0.07F;
	/** The swing: how far each way at rest and how much more just after moving (radians), and how fast (radians a tick). */
	static final float HANG_SWAY = 0.09F, HANG_SWAY_MOVING = 0.42F, HANG_SWAY_RATE = 0.11F;
	/** How far behind the right leg the left one swings (radians of the swing), and the side-to-side swing (radians). */
	static final float HANG_PHASE_GAP = 0.9F, HANG_SIDE_SWAY = 0.04F;
	/** Moving, the legs trail back this far (radians); this speed (blocks/tick) counts as moving flat out. */
	static final float HANG_TRAIL = 0.35F, HANG_SPEED_FULL = 0.25F;
	/** How much of the swing's extra a tick keeps once you stop (it dies down over a few seconds). */
	static final float HANG_SETTLE = 0.97F;
	/** Ticks the pose takes to settle in. */
	static final float HANG_EASE_IN = 4.0F;
	/** Limp in the Octo arms: the arms a touch out from the sides (radians), their swing (a share of the legs') and how far behind the legs' (radians). */
	static final float LIMP_ARM_OUT = 0.1F, LIMP_ARM_SWAY = 0.8F, LIMP_ARM_LAG = 1.4F;
	/** Limp: the knees' and elbows' give (radians), and a little more of it as the swing goes back. */
	static final float LIMP_KNEE = 0.25F, LIMP_ELBOW = 0.2F, LIMP_FOLD_SWING = 0.3F;
	/** The elbow: this far down the arm (model pixels). */
	static final float LIMP_ELBOW_AT = 4.0F;
	// ================================================================================================================

	/**
	 * {@code in}: how settled into the pose (0-1); {@code energy}: how hard it's swinging from moving (0-1); {@code limp}:
	 * the whole body hangs (carried in the Octo arms), not just the legs.
	 */
	public record Hang(float time, float in, float energy, boolean limp) {
	}

	public static final RenderStateDataKey<Hang> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_hang");

	/** Per entity id: when the hang began, the swing's energy, and when that was last worked out. */
	private static final java.util.Map<Integer, float[]> HANGS = new java.util.HashMap<>();

	private WebHangPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		boolean limp = entity instanceof Player p && !WebTendrilWalk.hanging(p) && RigOctoClient.carried(p);
		if (!(entity instanceof Player player) || !limp && !WebTendrilWalk.hanging(player)) {
			HANGS.remove(entity.getId());
			state.setData(KEY, null);
			return;
		}
		if (HANGS.size() > 64) {
			HANGS.keySet().removeIf(id -> id != entity.getId() && entity.level().getEntity(id) == null);
		}
		float time = entity.level().getGameTime() + partialTicks;
		float[] hang = HANGS.computeIfAbsent(entity.getId(), id -> new float[] {time, 0.0F, time});
		float dt = Math.max(0.0F, time - hang[2]);
		hang[2] = time;
		float speed = (float)Math.sqrt(Mth.square(entity.getX() - entity.xo) + Mth.square(entity.getZ() - entity.zo));
		hang[1] = Math.max(Mth.clamp(speed / HANG_SPEED_FULL, 0.0F, 1.0F), hang[1] * (float)Math.pow(HANG_SETTLE, dt));
		float in = Mth.clamp((time - hang[0]) / HANG_EASE_IN, 0.0F, 1.0F);
		// no stepping in mid-air
		state.walkAnimationSpeed = 0.0F;
		state.setData(KEY, new Hang(time, in * in * (3.0F - 2.0F * in), hang[1], limp));
	}

	/** After the model animates: the legs dangle and swing. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Hang hang = living.getData(KEY);
		// (a web's swing has the legs: WebArms)
		if (hang == null || living.getData(WebArms.KEY) != null) {
			return;
		}
		float e = hang.in();
		float phase = hang.time() * HANG_SWAY_RATE;
		float amp = HANG_SWAY + HANG_SWAY_MOVING * hang.energy();
		float trail = HANG_TRAIL * hang.energy();
		float side = Mth.sin(phase * 0.7F) * HANG_SIDE_SWAY;
		h.rightLeg.xRot = Mth.lerp(e, h.rightLeg.xRot, HANG_LEG_X + trail + amp * Mth.sin(phase));
		h.leftLeg.xRot = Mth.lerp(e, h.leftLeg.xRot, HANG_LEG_X + trail + amp * Mth.sin(phase - HANG_PHASE_GAP));
		h.rightLeg.yRot = Mth.lerp(e, h.rightLeg.yRot, 0.0F);
		h.leftLeg.yRot = Mth.lerp(e, h.leftLeg.yRot, 0.0F);
		h.rightLeg.zRot = Mth.lerp(e, h.rightLeg.zRot, HANG_LEG_SPREAD + side);
		h.leftLeg.zRot = Mth.lerp(e, h.leftLeg.zRot, -HANG_LEG_SPREAD + side);
		if (!hang.limp() || !armsFree(living)) {
			return;
		}
		// limp in the arms: the arms hang loose and swing a beat behind the legs
		float armAmp = amp * LIMP_ARM_SWAY;
		h.rightArm.xRot = Mth.lerp(e, h.rightArm.xRot, trail + armAmp * Mth.sin(phase - LIMP_ARM_LAG));
		h.leftArm.xRot = Mth.lerp(e, h.leftArm.xRot, trail + armAmp * Mth.sin(phase - HANG_PHASE_GAP - LIMP_ARM_LAG));
		h.rightArm.yRot = Mth.lerp(e, h.rightArm.yRot, 0.0F);
		h.leftArm.yRot = Mth.lerp(e, h.leftArm.yRot, 0.0F);
		h.rightArm.zRot = Mth.lerp(e, h.rightArm.zRot, LIMP_ARM_OUT + side);
		h.leftArm.zRot = Mth.lerp(e, h.leftArm.zRot, -LIMP_ARM_OUT + side);
	}

	/**
	 * Right before the model is drawn (after {@link #applyModel}): limp in the Octo arms, the knees and elbows give a
	 * little, more as each limb swings back ({@code KneeBend.restore} puts them straight once it's drawn).
	 */
	public static void bendLimbs(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Hang hang = living.getData(KEY);
		if (hang == null || !hang.limp() || living.getData(WebArms.KEY) != null) {
			return;
		}
		float e = hang.in();
		// (KneeBend's + swings the lower half back: a shin goes back on a +, a forearm comes forward on a -)
		KneeBend.bend(h.rightLeg, e * (LIMP_KNEE + LIMP_FOLD_SWING * Math.max(0.0F, h.rightLeg.xRot)));
		KneeBend.bend(h.leftLeg, e * (LIMP_KNEE + LIMP_FOLD_SWING * Math.max(0.0F, h.leftLeg.xRot)));
		if (armsFree(living)) {
			KneeBend.bend(h.rightArm, -e * (LIMP_ELBOW + LIMP_FOLD_SWING * Math.max(0.0F, -h.rightArm.xRot)), LIMP_ELBOW_AT, false);
			KneeBend.bend(h.leftArm, -e * (LIMP_ELBOW + LIMP_FOLD_SWING * Math.max(0.0F, -h.leftArm.xRot)), LIMP_ELBOW_AT, false);
		}
	}

	/** Nothing has the arms: no swing, nothing in use, nothing aimed or raised. */
	private static boolean armsFree(LivingEntityRenderState living) {
		return living instanceof AvatarRenderState a && !a.isUsingItem && a.swingAnimation <= 0.0F && free(a.rightArmPose) && free(a.leftArmPose);
	}

	private static boolean free(HumanoidModel.ArmPose pose) {
		return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
	}
}
