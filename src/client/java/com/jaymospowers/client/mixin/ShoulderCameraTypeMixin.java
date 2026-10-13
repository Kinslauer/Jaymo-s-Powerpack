package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CameraPreset;
import com.jaymospowers.client.ShoulderCamera;
import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** F5 with Web or Gravity: first person and the shoulder view only (no front-facing view). */
@Mixin(CameraType.class)
abstract class ShoulderCameraTypeMixin {
	@Inject(method = "cycle", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$shoulderCycle(CallbackInfoReturnable<CameraType> cir) {
		// not while a Camera Utils view is on: its own cycle hook has to run to switch that view off
		if (ShoulderCamera.active() && !CameraPreset.viewActive()) {
			cir.setReturnValue((Object)this == CameraType.FIRST_PERSON ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
		}
	}
}
