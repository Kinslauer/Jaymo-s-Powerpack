package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterScanClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The scouter's lens zooms a little while its scan key is held ({@link ZFighterScanClient#fovFactor}). */
@Mixin(Camera.class)
abstract class ZFighterScanZoomMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$scanZoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		float factor = ZFighterScanClient.fovFactor();
		if (factor < 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * factor);
		}
	}
}
