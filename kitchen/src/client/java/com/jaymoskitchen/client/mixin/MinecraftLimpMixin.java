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

	// (not used elsewhere in the pack yet, so optional: if 26.3 named it differently the game still starts)
	@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskitchen$limpNoMining(boolean down, CallbackInfo ci) {
		// only while the button's held: letting go still runs, so a half-dug block is let go of
		if (down && LimpController.active()) {
			ci.cancel();
		}
	}
}
