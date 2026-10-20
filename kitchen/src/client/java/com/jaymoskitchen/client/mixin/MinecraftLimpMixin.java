package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.ragdoll.LimpController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.0) Lying limp, a click swings at nothing and mines nothing (using is stopped in MinecraftUseMixin). */
@Mixin(value = Minecraft.class, priority = 900)
abstract class MinecraftLimpMixin {
	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limpNoAttack(CallbackInfoReturnable<Boolean> cir) {
		if (LimpController.active()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limpNoMining(boolean down, CallbackInfo ci) {
		if (LimpController.active()) {
			ci.cancel();
		}
	}
}
