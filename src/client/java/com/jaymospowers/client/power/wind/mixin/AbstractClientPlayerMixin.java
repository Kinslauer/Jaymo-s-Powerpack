package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindCamera;
import com.jaymospowers.client.power.wind.WindPowerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Widens the view with cruise speed (scaled by the FOV-effects slider, like sprinting). */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$windSpeedFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if ((Object)this != minecraft.player || !WindPowerClient.cameraEffectsActive(minecraft)) {
			return;
		}
		float boost = WindCamera.fovMultiplier();
		if (boost > 1.0001F && cir.getReturnValue() > 0.2F) {
			cir.setReturnValue(cir.getReturnValue() * Mth.lerp(effectScale, 1.0F, boost));
		}
	}
}
