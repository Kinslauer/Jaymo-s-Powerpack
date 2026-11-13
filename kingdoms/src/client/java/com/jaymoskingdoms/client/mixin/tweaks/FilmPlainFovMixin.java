package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.films.FarmWakeFilmClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy's rule for cutscenes: no FOV modifiers. While the farmhand's wake film runs ({@link FarmWakeFilmClient}) the camera's
 * field of view is the plain FOV setting (no speed widening, zoom or squeeze), as in Powers' films. A late priority, after
 * other mods' FOV.
 */
@Mixin(value = Camera.class, priority = 2000)
abstract class FilmPlainFovMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$plainFilmFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if (FarmWakeFilmClient.running()) {
			cir.setReturnValue((float)this.minecraft.options.fov().get());
		}
	}
}
