package com.jaymoskingdoms.client.mixin.moons;

import com.jaymoskingdoms.client.DarkNights;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moonless nights go dark ({@link DarkNights}), each time the lightmap is worked out. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class DarkNightsMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void jaymoskingdoms$darkNights(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.needsUpdate) {
			DarkNights.apply(state, partialTicks);
		}
	}
}
