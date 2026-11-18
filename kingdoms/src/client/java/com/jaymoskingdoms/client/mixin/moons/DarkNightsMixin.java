package com.jaymoskingdoms.client.mixin.moons;

import com.jaymoskingdoms.client.DarkNights;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moonless nights go dark ({@link DarkNights}), each time the lightmap is worked out. */
@Mixin(LightmapRenderStateExtractor.class)
abstract class DarkNightsMixin {
	@Shadow
	private boolean needsUpdate;

	/**
	 * The lightmap is worked out again every client tick, as vanilla does. BadOptimizations' lightmap caching skips the
	 * tick until the sky light factor changes, which it doesn't from morning to dusk: a lightmap worked out mid-change
	 * (after sleeping, /time set or joining, read between the two ticks' values) stayed all day, with the day as dark as
	 * the night it came from. It also never sees the Moon that {@link DarkNights} lights by.
	 */
	@Inject(method = "extract", at = @At("HEAD"))
	private void jaymoskingdoms$everyTick(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (DarkNights.takeTick()) {
			this.needsUpdate = true;
		}
	}

	@Inject(method = "extract", at = @At("TAIL"))
	private void jaymoskingdoms$darkNights(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.needsUpdate) {
			DarkNights.apply(state, partialTicks);
		}
	}
}
