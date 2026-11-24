package com.jaymospowers.client.air.mixin;

import com.jaymospowers.client.air.LightTint;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Light in its block's colour ({@link LightTint}): the block light's colour the lightmap is made with, from the lights round the camera. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class LightTintMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void jaymospowers$tint(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.needsUpdate && state.blockLightTint != null) {
			state.blockLightTint = LightTint.tint(state.blockLightTint);
		}
	}
}
