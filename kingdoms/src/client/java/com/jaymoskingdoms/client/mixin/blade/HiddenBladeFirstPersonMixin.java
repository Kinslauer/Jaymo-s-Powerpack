package com.jaymoskingdoms.client.mixin.blade;

import com.jaymoskingdoms.client.HiddenBladeClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.135) First person, each empty hand: with its hidden blade springing or out, your bare arm comes up into the guard
 * (the off hand, which the game doesn't draw empty, rises in from below), and a blow is a thrust (see HiddenBladeClient).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class HiddenBladeFirstPersonMixin {
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm, PlayerRenderState playerState) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$bladeGuard(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (!itemStack.isEmpty() || state.isScoping || avatar == null || avatar.isInvisible) {
			return;
		}
		poseStack.pushPose();
		float guard = HiddenBladeClient.firstPersonGuard(poseStack, avatar, hand, attack);
		if (guard <= 0.0F) {
			poseStack.popPose();
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
		// the punch fades out as the guard comes up (the thrust is the guard's own)
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack * (1.0F - guard), arm, playerState);
		poseStack.popPose();
		ci.cancel();
	}
}
