package com.jaymospowers.client.mixin;

import com.jaymospowers.client.SneakZoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Shift + Left Alt zoom ({@link SneakZoom#fovFactor}) narrows the camera's field of view as the very last change to
 * it: a priority later than every other mod's and our own (the films' plain-FOV mixins sit at 2000), so nothing after it
 * can put the plain field of view back, which is what left OK Zoomer's zoom showing nothing in Andy's clip. It multiplies,
 * so the sprint widening, a punch's kick and the rest still show through it.
 */
@Mixin(value = Camera.class, priority = 3000)
abstract class ZoomFovMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$sneakZoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float factor = SneakZoom.fovFactor(partialTicks);
		if (factor < 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * factor);
		}
	}
}
