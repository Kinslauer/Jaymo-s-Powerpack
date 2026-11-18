package com.jaymospowers.client.mixin;

import com.jaymospowers.client.DreamView;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a dream call comes ({@link DreamView}), the sleeper's view tilts up off the pillow. Not when another free view already holds
 * the camera (Jaymo's Kingdoms' night camera, which runs first and detaches it).
 */
@Mixin(value = Camera.class, priority = 1100)
abstract class DreamCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$dreamLift(float partialTicks, CallbackInfo ci) {
		float lift = DreamView.pitchLift(partialTicks);
		if (lift > 0.0F && !this.detached) {
			this.setRotation(this.yRot, this.xRot - lift);
		}
	}
}
