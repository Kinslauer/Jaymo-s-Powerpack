package com.jaymospowers.client.mixin;

import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.resources.SplashManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The yellow line beside the title logo always reads the pack's caption, "what's yours", instead of a random splash
 * (and instead of vanilla's Christmas, New Year and Halloween lines).
 */
@Mixin(SplashManager.class)
abstract class ReasonSplashMixin {
	private static final int SPLASH_YELLOW = 0xFFFF00;

	@Inject(method = "getSplash", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$reasonCaption(CallbackInfoReturnable<SplashRenderer> cir) {
		cir.setReturnValue(new SplashRenderer(Component.literal("what's yours").withColor(SPLASH_YELLOW)));
	}
}
