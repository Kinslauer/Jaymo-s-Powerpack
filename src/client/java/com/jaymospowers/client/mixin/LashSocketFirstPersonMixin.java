package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.LashSockets;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.16.8) Your own main-hand flail or whip in first person: where its handle's tip is drawn (LashSockets). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class LashSocketFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void jaymospowers$lashSocket(CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) InteractionHand hand,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack) {
		if (hand == InteractionHand.MAIN_HAND) {
			LashSockets.firstPerson(stack);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void jaymospowers$lashSocketDone(CallbackInfo ci) {
		LashSockets.done();
	}
}
