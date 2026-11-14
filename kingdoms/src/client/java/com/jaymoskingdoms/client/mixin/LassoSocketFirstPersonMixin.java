package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.forms.LassoSockets;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own held lasso in first person: where the top of its coil is drawn (LassoSockets), and the hand raised to whirl it. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class LassoSocketFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void jaymoskingdoms$lassoSocket(CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) InteractionHand hand,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack) {
		if (hand == InteractionHand.MAIN_HAND) {
			LassoSockets.firstPerson(stack);
		}
	}

	/** Whirling the lasso: your hand goes up over your head, circling with the loop (LassoPose). */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0,
		shift = At.Shift.AFTER))
	private void jaymoskingdoms$lassoWhirl(CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) InteractionHand hand,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true, ordinal = 0) float partialTicks,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) com.mojang.blaze3d.vertex.PoseStack poseStack) {
		if (stack.is(com.jaymoskingdoms.forms.Lasso.ITEM)) {
			float t = com.jaymoskingdoms.client.forms.LassoPose.firstPersonWhirl(partialTicks);
			net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
			if (t >= 0.0F && player != null && player.getUsedItemHand() == hand) {
				com.jaymoskingdoms.client.forms.LassoPose.firstPersonHand(poseStack, t);
			}
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void jaymoskingdoms$lassoSocketDone(CallbackInfo ci) {
		LassoSockets.done();
	}
}
