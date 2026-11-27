package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.ThrallRideClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The thrall's view, to the camera, is where its rider looks ({@link ThrallRideClient#yaw}). */
@Mixin(LivingEntity.class)
abstract class RideViewYawMixin {
	@Inject(method = "getViewYRot(F)F", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$riderYaw(float partial, CallbackInfoReturnable<Float> cir) {
		if (ThrallRideClient.looksThrough((LivingEntity) (Object) this)) {
			cir.setReturnValue(ThrallRideClient.yaw());
		}
	}
}
