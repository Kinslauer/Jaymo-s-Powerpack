package com.jaymospowers.client.combat;

import com.jaymospowers.client.KneeBend;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Elbows that Better Combat's animations fold. Andy (2026-11-15) wants combat animations with elbows that bend; the
 * longsword trial's moves (tools/combat_trial) key a fold per arm as the animation's {@code bend}, which Player Animation
 * Library reads but doesn't draw. It's read onto the render state here, drawn with {@link KneeBend}, and the held item
 * turns with the forearm so the sword stays in the fist.
 */
public final class SwingElbows {
	/** The folds (right, left; + brings the forearm forward), or null. */
	private static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:swing_elbows");
	/** Where an arm folds: this many pixels down it from the shoulder's pivot (tools/combat_trial/body.py: ELBOW). */
	static final float ELBOW = 4.0F;

	private SwingElbows() {
	}

	public static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, CombatAnimCompat.elbows(entity));
	}

	/** Last thing before the model is drawn: the arms folded (put back straight by {@code KneeBend.restore}). */
	public static void bendLimbs(Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		float[] elbows = state.getData(KEY);
		if (elbows == null) {
			return;
		}
		// KneeBend's + swings the lower half back
		KneeBend.bend(h.rightArm, -elbows[0], ELBOW, false);
		KneeBend.bend(h.leftArm, -elbows[1], ELBOW, false);
	}

	/** At the end of the player model's {@code translateToHand}: the hand where the folded forearm has carried it. */
	public static void hand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack) {
		float[] elbows = state.getData(KEY);
		if (elbows == null) {
			return;
		}
		float fold = arm == HumanoidArm.RIGHT ? elbows[0] : elbows[1];
		if (fold == 0.0F) {
			return;
		}
		poseStack.rotateAround(Axis.XP.rotation(-fold), 0.0F, ELBOW / 16.0F, 0.0F);
	}
}
