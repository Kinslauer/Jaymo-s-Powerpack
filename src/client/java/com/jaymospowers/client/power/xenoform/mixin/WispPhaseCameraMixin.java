package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.WispPhaseClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While you phase as Wisp the world is drawn without cave culling, as it is for a spectator inside a block: from inside a
 * wall the culling would otherwise find nothing to see. Sodium reads the same flag.
 */
@Mixin(Camera.class)
abstract class WispPhaseCameraMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void jaymospowers$wispPhaseCull(CameraRenderState state, DeltaTracker delta, CallbackInfo ci) {
		if (WispPhaseClient.local()) {
			state.smartCull = false;
		}
	}
}
