package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.FangGauntletsClient;
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
 * (0.18.74) First person, Fang Gauntlets in the main hand: the item isn't drawn; both bare arms are shown in a guard with
 * the claws on them (the off hand's while empty). A swing (the game's or Better Combat's) leaves the arm to it.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FangGauntletsFirstPersonMixin {
	@Shadow
	private void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight,
		float attackValue, HumanoidArm arm, PlayerRenderState playerState) {
	}

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$gauntletGuard(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
		int lightCoords, CallbackInfo ci) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (state.isScoping || avatar == null || avatar.isInvisible || !FangGauntletsClient.wornInHand(hand, itemStack)) {
			return;
		}
		poseStack.pushPose();
		FangGauntletsClient.firstPersonGuard(poseStack, avatar, hand, attack, inverseArmHeight);
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? avatar.mainArm : avatar.mainArm.getOpposite();
		this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm, playerState);
		poseStack.popPose();
		ci.cancel();
	}
}
