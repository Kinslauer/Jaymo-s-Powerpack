package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindAirSense;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Jung) The airflow sense's focus: the world drains to grey and white, as far as the focus has come in (see WindAirSense). */
@Mixin(GameRenderer.class)
abstract class WindSenseGameRendererMixin {
	@Shadow
	public abstract List<Identifier> getRequestedPostEffects();

	@Inject(method = "update", at = @At("TAIL"))
	private void jaymospowers$senseGrey(DeltaTracker delta, CallbackInfo ci) {
		Identifier effect = WindAirSense.greyEffect();
		if (effect != null && !this.getRequestedPostEffects().contains(effect)) {
			this.getRequestedPostEffects().add(effect);
		}
	}
}
