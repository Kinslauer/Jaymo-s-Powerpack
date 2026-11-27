package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.ThrallRideClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The thrall's view, to the camera, is where its rider looks ({@link ThrallRideClient#pitch}). */
@Mixin(Entity.class)
abstract class RideViewPitchMixin {
	@Inject(method = "getViewXRot(F)F", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$riderPitch(float partial, CallbackInfoReturnable<Float> cir) {
		if (ThrallRideClient.looksThrough((Entity) (Object) this)) {
			cir.setReturnValue(ThrallRideClient.pitch());
		}
	}
}
