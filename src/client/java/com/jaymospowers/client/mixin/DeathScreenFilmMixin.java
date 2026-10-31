package com.jaymospowers.client.mixin;

import com.jaymospowers.client.UltimateCinematic;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.18.105) While a film plays after your death ({@link UltimateCinematic#deathFilm}: a Kingdoms captain's moment over
 * your body), the death screen waits: not drawn, its clock and buttons held, so the scene plays before you can respawn.
 */
@Mixin(DeathScreen.class)
abstract class DeathScreenFilmMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$filmFirst(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (UltimateCinematic.deathFilm()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$filmFirstBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (UltimateCinematic.deathFilm()) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$filmHoldsClock(CallbackInfo ci) {
		if (UltimateCinematic.deathFilm()) {
			ci.cancel();
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$filmHoldsButtons(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (UltimateCinematic.deathFilm()) {
			cir.setReturnValue(false);
		}
	}
}
