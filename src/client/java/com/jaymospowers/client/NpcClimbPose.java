package com.jaymospowers.client;

import com.jaymospowers.freerun.moves.NpcClimb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;

/**
 * An NPC pulling itself up over an edge ({@link NpcClimb}), for everyone: squared to the wall and leaning in over the
 * edge as a player's climb is, and on a body built like a player's ({@link HumanoidModel}: villagers of Minecraft Comes
 * Alive, zombies, skeletons, piglins) the player's own mantle clip on the arms and legs ({@link LedgePose#pose}). Others
 * (an illager's crossed-arm body) only lean in.
 */
public final class NpcClimbPose {
	/** How far the body leans in over the edge at the most (degrees), as the player's climb. */
	static final float LEAN_DEGREES = LedgePose.CLIMB_LEAN_DEGREES;

	private static final RenderStateDataKey<LedgePose.State> KEY = RenderStateDataKey.create(() -> "jaymospowers:npc_climb");

	private NpcClimbPose() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		if (entity instanceof Avatar) {
			return;
		}
		NpcClimb.Pull pull = entity.getAttached(NpcClimb.PULL);
		if (pull == null) {
			state.setData(KEY, null);
			return;
		}
		float climb = Mth.clamp((entity.level().getGameTime() - pull.start() + partialTicks) / Math.max(1, pull.ticks()), 0.0F, 1.0F);
		float head = state.bodyRot + state.yRot;
		state.bodyRot = pull.direction().toYRot();
		state.yRot = Mth.clamp(Mth.wrapDegrees(head - state.bodyRot), -70.0F, 70.0F);
		state.walkAnimationSpeed = 0.0F;
		state.setData(KEY, new LedgePose.State(climb, entity.tickCount + partialTicks, false, 0.0F));
	}

	/** After the body faces its way: the lean in over the edge, most as the knee comes up, upright again to stand. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack) {
		if (state instanceof AvatarRenderState) {
			return;
		}
		LedgePose.State climb = state.getData(KEY);
		if (climb == null) {
			return;
		}
		float lean = Mth.sin(Mth.PI * Math.min(1.0F, climb.climb() / 0.9F));
		poseStack.translate(0.0F, 0.9F, 0.0F);
		poseStack.rotateDegrees(Axis.XP, -LEAN_DEGREES * lean);
		poseStack.translate(0.0F, -0.9F, 0.0F);
	}

	/** Right after the model animates: the player's mantle on the arms and legs. */
	public static void applyModel(Model<?> model, Object renderState) {
		if (renderState instanceof AvatarRenderState || !(renderState instanceof LivingEntityRenderState state)
			|| !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		LedgePose.State climb = state.getData(KEY);
		if (climb != null) {
			LedgePose.pose(h, climb, 1.0F);
		}
	}
}
