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

/** (0.7.113) First person: an empty off hand draws no arm, but one with its hidden blade out shows it (see HiddenBladeClient). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class HiddenBladeOffHandMixin {
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm, PlayerRenderState playerState) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$bladeOffArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (hand != InteractionHand.OFF_HAND || !itemStack.isEmpty() || state.isScoping || avatar == null || avatar.isInvisible
			|| !HiddenBladeClient.showOffArm()) {
			return;
		}
		poseStack.pushPose();
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, avatar.mainArm.getOpposite(), playerState);
		poseStack.popPose();
		ci.cancel();
	}
}
