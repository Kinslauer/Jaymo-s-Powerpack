package com.jaymospowers.client.power.web.mixin;

import com.geckolib.renderer.GeoEntityRenderer;
import com.jaymospowers.client.power.web.WebbedGeoLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every GeckoLib entity renderer draws a webbed-up body wrapped too ({@link WebbedGeoLayer}). */
@Mixin(value = GeoEntityRenderer.class, remap = false)
abstract class WebbedGeoRendererMixin {
	/** (one constructor hands on to another, and each returns through here: the layer goes on once) */
	@Unique
	private boolean jaymospowers$webbed;

	@Inject(method = "<init>*", at = @At("RETURN"))
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void jaymospowers$webbedLayer(CallbackInfo ci) {
		if (this.jaymospowers$webbed) {
			return;
		}
		this.jaymospowers$webbed = true;
		((GeoEntityRenderer) (Object) this).withRenderLayer(new WebbedGeoLayer((GeoEntityRenderer) (Object) this));
	}
}
