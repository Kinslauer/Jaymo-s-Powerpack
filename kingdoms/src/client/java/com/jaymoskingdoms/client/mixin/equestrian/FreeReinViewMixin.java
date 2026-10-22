package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.110) Under free rein your view is the camera's ({@code FreeReinClient}): the camera, the crosshair's target and
 * what you aim at follow it, while your body and horse turn after it.
 */
@Mixin(Entity.class)
abstract class FreeReinViewMixin {
	@Inject(method = "getViewXRot", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$pitch(float a, CallbackInfoReturnable<Float> cir) {
		if (FreeReinClient.on() && (Object)this == Minecraft.getInstance().player) {
			cir.setReturnValue(FreeReinClient.pitch());
		}
	}
}
