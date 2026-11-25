package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoSlowLook;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Chrono's slowed or stopped world drains to old gold (see ChronoSlowLook). */
@Mixin(GameRenderer.class)
abstract class ChronoSlowGameRendererMixin {
	@Shadow
	public abstract List<Identifier> getRequestedPostEffects();

	@Inject(method = "update", at = @At("TAIL"))
	private void jaymospowers$chronoSlowGold(DeltaTracker delta, CallbackInfo ci) {
		Identifier effect = ChronoSlowLook.effect();
		if (effect != null && !this.getRequestedPostEffects().contains(effect)) {
			this.getRequestedPostEffects().add(effect);
		}
	}
}
