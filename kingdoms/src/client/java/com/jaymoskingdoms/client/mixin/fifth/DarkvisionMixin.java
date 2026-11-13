package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.client.DarkvisionClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A blood that sees in the dark wears the darkvision screen ({@link DarkvisionClient}). */
@Mixin(GameRenderer.class)
abstract class DarkvisionMixin {
	@Shadow
	public abstract java.util.List<Identifier> getRequestedPostEffects();

	@Inject(method = "update", at = @At("TAIL"))
	private void jaymoskingdoms$darkvision(DeltaTracker delta, CallbackInfo ci) {
		Identifier effect = DarkvisionClient.effect();
		if (effect != null && !this.getRequestedPostEffects().contains(effect)) {
			this.getRequestedPostEffects().add(effect);
		}
	}
}
