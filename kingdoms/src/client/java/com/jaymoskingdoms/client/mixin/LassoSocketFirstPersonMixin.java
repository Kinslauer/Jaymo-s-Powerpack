package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.forms.LassoSockets;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your own held lasso in first person: where the top of its coil is drawn (LassoSockets). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class LassoSocketFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void jaymoskingdoms$lassoSocket(CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) InteractionHand hand,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack) {
		if (hand == InteractionHand.MAIN_HAND) {
			LassoSockets.firstPerson(stack);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void jaymoskingdoms$lassoSocketDone(CallbackInfo ci) {
		LassoSockets.done();
	}
}
