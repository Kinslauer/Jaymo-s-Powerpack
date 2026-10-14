package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.BloodVision;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A vampire player's screen wears MCA's Sirben trait effect ({@link BloodVision#SIRBEN}). */
@Mixin(GameRenderer.class)
abstract class SirbenMixin {
	@Shadow
	public abstract java.util.List<Identifier> getRequestedPostEffects();

	@Inject(method = "update", at = @At("TAIL"))
	private void jaymoskingdoms$sirben(DeltaTracker delta, CallbackInfo ci) {
		Identifier effect = BloodVision.sirben();
		if (effect != null && !this.getRequestedPostEffects().contains(effect)) {
			this.getRequestedPostEffects().add(effect);
		}
	}
}
