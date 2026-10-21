package com.jaymoskingdoms.client.mixin.shouldersurfing;

import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.113) Under free rein Shoulder Surfing's camera looks where the free rein camera looks ({@link FreeReinClient}):
 * the mouse turns it, and it no longer swings round with the horse. Skipped when Shoulder Surfing isn't there.
 */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfingCamera", remap = false)
abstract class ShoulderSurfingCameraMixin {
	@Shadow
	public abstract void setXRot(float pitch);

	@Shadow
	public abstract void setYRot(float yaw);

	@Inject(method = "tick", at = @At("TAIL"), require = 0)
	private void jaymoskingdoms$freeReinTick(CallbackInfo ci) {
		jaymoskingdoms$follow();
	}

	@Inject(method = "renderTick", at = @At("HEAD"), require = 0)
	private void jaymoskingdoms$freeReinFrame(CallbackInfo ci) {
		jaymoskingdoms$follow();
	}

	private void jaymoskingdoms$follow() {
		if (FreeReinClient.on()) {
			setXRot(FreeReinClient.pitch());
			setYRot(FreeReinClient.yaw());
		}
	}
}
