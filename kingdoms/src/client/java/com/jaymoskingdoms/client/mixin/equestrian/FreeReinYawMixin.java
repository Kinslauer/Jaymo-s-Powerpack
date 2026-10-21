package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.107) Under free rein your view turns with the camera ({@code FreeReinViewMixin} for the pitch). */
@Mixin(LocalPlayer.class)
abstract class FreeReinYawMixin {
	@Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$yaw(float a, CallbackInfoReturnable<Float> cir) {
		if (FreeReinClient.on() && (Object)this == Minecraft.getInstance().player) {
			cir.setReturnValue(FreeReinClient.yaw());
		}
	}
}
