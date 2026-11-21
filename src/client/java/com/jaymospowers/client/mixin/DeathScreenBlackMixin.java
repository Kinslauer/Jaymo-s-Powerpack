package com.jaymospowers.client.mixin;

import com.jaymospowers.core.wounds.Mortality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.94) Andy: "the red overlay on the respawn screen should be black as well, the screen should cut to black whenever
 * you die, and the slow entry of either the reincarnate screen or the scenario will load". The death screen's red is black,
 * and what's on it (the cause, the buttons) comes slowly up out of the black over {@link #FADE_TICKS}.
 * <p>
 * Andy (2026-11-19): "deaths like this should cut straight to black ... only hearing nothing and the long wait for the
 * respawn screen", then "the cut to black is only for a final death". For a death that ends the character
 * ({@link Mortality#FINAL}) the screen stays all black and silent ({@code BlackoutHush}) for {@link #HOLD_TICKS} first, its
 * buttons not yet there to be clicked, and only then comes up. Any other death comes up as before.
 */
// (after the other death-screen mixins, so its black is drawn over what they add: nothing shows through the hold)
@Mixin(value = DeathScreen.class, priority = 1100)
abstract class DeathScreenBlackMixin {
	/** How long (ticks) the death screen takes to come up out of the black. */
	@Unique
	private static final float FADE_TICKS = 50.0F;
	/** How long (ticks) the death screen is only black, before it starts to come up. */
	@Unique
	private static final int HOLD_TICKS = 100;

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
		float left = 1.0F - Math.clamp((jaymospowers$age - jaymospowers$hold() + delta) / FADE_TICKS, 0.0F, 1.0F);
		if (left > 0.0F) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(left * 255.0F) << 24);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$holdClicks(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (jaymospowers$age < jaymospowers$hold()) {
			cir.setReturnValue(false);
		}
	}

	/** How long this death screen is only black: {@link #HOLD_TICKS} for a final death, none for any other. */
	@Unique
	private static int jaymospowers$hold() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && Mortality.isFinal(minecraft.player) ? HOLD_TICKS : 0;
	}
}
