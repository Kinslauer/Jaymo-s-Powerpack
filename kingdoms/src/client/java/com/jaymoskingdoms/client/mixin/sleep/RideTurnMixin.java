package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.ThrallRideClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Riding a thrall by day: the mouse turns the thrall's view, and leaves the sleeper's alone ({@link ThrallRideClient#turn}). */
@Mixin(Entity.class)
abstract class RideTurnMixin {
	@Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$turnThrall(double dx, double dy, CallbackInfo ci) {
		if (ThrallRideClient.riding() && (Object) this == Minecraft.getInstance().player) {
			ThrallRideClient.turn(dx, dy);
			ci.cancel();
		}
	}
}
