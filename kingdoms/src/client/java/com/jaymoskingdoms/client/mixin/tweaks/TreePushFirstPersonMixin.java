package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.TreePushPose;
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

/** First person, pushing a fallen tree: both bare hands out flat on the log (TreePushPose), whatever they held. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class TreePushFirstPersonMixin {
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm, PlayerRenderState playerState) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$treePush(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (state.isScoping || avatar == null) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
		poseStack.pushPose();
		if (TreePushPose.firstPerson(poseStack, arm, partialTicks) <= 0.0F) {
			poseStack.popPose();
			return;
		}
		// no swing, no lowering as the held item changes, and nothing in the hand: both palms stay on the log
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, 0.0F, 0.0F, arm, playerState);
		poseStack.popPose();
		ci.cancel();
	}
}
