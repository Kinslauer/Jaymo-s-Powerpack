package com.jaymospowers.client.mixin;

import com.jaymospowers.client.UltimateCinematic;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.93) Andy: "remove any FOV modifiers for the cutscene duration, (I was an overclocked chrono and it was way zoomed
 * out)". While your own film runs ({@link UltimateCinematic#filming}) the camera's field of view is your plain FOV setting:
 * no sprint or speed widening (Overclock, Speed, a road's pace), no zoom, no under-water or dying squeeze. The modifier
 * itself keeps easing underneath, so the view is back as it was when the film ends. A late priority, after other mods' FOV.
 */
@Mixin(value = Camera.class, priority = 2000)
abstract class UltimateCinematicFovMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$plainFilmFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (UltimateCinematic.filming()) {
			cir.setReturnValue((float)this.minecraft.options.fov().get());
		}
	}
}
