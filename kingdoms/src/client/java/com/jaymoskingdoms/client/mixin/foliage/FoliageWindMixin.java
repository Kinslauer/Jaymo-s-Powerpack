package com.jaymoskingdoms.client.mixin.foliage;

import com.jaymoskingdoms.client.disasters.WindSigns;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Grass and plants in our wind (Andy's survival picks, "wind sway"). The pack's Interactive Foliage (by KartO, CC BY-NC-SA
 * 4.0, shipped unchanged) already sways plants on the GPU, cheaply enough for the Steam Deck, and leans them harder in
 * rain and storms: its shader reads the rain and thunder from {@code GpuFoliageRenderer#weather}. Here our wind
 * ({@link WindSigns#plantWind}, {@link WindSigns#plantGale}) counts as rain and thunder there when it blows harder, so
 * plants bend and gust with it whatever the sky. Its shader blows them one way (west) at all times; that isn't ours to
 * change. Only while its "weather wind" setting is on (its own default). Nothing happens without the mod.
 */
@Pseudo
@Mixin(targets = "net.karto.mc2.mc2_interactivefoliage.gpu.GpuFoliageRenderer", remap = false)
abstract class FoliageWindMixin {
	@Inject(method = "weather", at = @At("RETURN"), require = 0)
	private static void jaymoskingdoms$ourWind(CallbackInfoReturnable<Vector4f> cir) {
		Vector4f weather = cir.getReturnValue();
		if (weather != null && weather.z > 0.0F) {
			weather.x = Math.max(weather.x, WindSigns.plantWind);
			weather.y = Math.max(weather.y, WindSigns.plantGale);
		}
	}
}
