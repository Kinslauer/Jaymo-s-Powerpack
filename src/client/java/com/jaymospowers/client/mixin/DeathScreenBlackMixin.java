package com.jaymospowers.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.94) Andy: "the red overlay on the respawn screen should be black as well, the screen should cut to black whenever
 * you die, and the slow entry of either the reincarnate screen or the scenario will load". The death screen's red is black,
 * and what's on it (the cause, the buttons) comes slowly up out of the black over {@link #FADE_TICKS}.
 */
@Mixin(DeathScreen.class)
abstract class DeathScreenBlackMixin {
	/** How long (ticks) the death screen takes to come up out of the black. */
	@Unique
	private static final float FADE_TICKS = 50.0F;

	@Unique
	private int jaymospowers$age;

	@Inject(method = "extractDeathBackground", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$black(GuiGraphicsExtractor graphics, int width, int height, CallbackInfo ci) {
		graphics.fill(0, 0, width, height, 0xFF000000);
		ci.cancel();
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void jaymospowers$age(CallbackInfo ci) {
		jaymospowers$age++;
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void jaymospowers$fadeIn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		float left = 1.0F - Math.min(1.0F, (jaymospowers$age + delta) / FADE_TICKS);
		if (left > 0.0F) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(left * 255.0F) << 24);
		}
	}
}
