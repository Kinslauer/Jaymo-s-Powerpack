package com.jaymospowers.client.mixin;

import com.jaymospowers.client.ShoulderCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The crosshair stays up in the shoulder view (see ShoulderCamera), so abilities can be aimed from it. */
@Mixin(Hud.class)
abstract class ShoulderCrosshairMixin {
	@Redirect(method = "extractCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
	private boolean jaymospowers$shoulderCrosshair(CameraType type) {
		return type.isFirstPerson() || type == CameraType.THIRD_PERSON_BACK && ShoulderCamera.active();
	}
}
