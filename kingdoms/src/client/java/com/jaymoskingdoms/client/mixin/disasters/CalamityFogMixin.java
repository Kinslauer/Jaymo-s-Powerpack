package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.CalamitiesClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.31) A sandstorm's or blizzard's fog ({@link CalamitiesClient}): out in one, the world closes in to a few dozen
 * blocks of orange dust or white snow (dimmed by night like the sky's own fog), sky and clouds too.
 */
@Mixin(FogRenderer.class)
abstract class CalamityFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void jaymoskingdoms$calamityFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
		ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		float sand = CalamitiesClient.sand, snow = CalamitiesClient.snow;
		float thick = Math.max(sand, snow);
		if (thick <= 0.001F) {
			return;
		}
		FogData fog = cir.getReturnValue();
		boolean sandy = sand >= snow;
		float sight = sandy ? CalamitiesClient.SAND_SIGHT : CalamitiesClient.SNOW_SIGHT;
		float[] colour = sandy ? CalamitiesClient.SAND_COLOUR : CalamitiesClient.SNOW_COLOUR;
		// eased in: a little thickening reads as haze, the full storm a wall
		float t = thick * thick * (3.0F - 2.0F * thick);
		fog.environmentalStart = Mth.lerp(t, fog.environmentalStart, 0.0F);
		fog.environmentalEnd = Mth.lerp(t, fog.environmentalEnd, Math.min(fog.environmentalEnd, sight));
		fog.skyEnd = Mth.lerp(t, fog.skyEnd, Math.min(fog.skyEnd, sight));
		fog.cloudEnd = Mth.lerp(t, fog.cloudEnd, Math.min(fog.cloudEnd, sight));
		float light = Mth.clamp(Math.max(fog.color.x(), Math.max(fog.color.y(), fog.color.z())) * 1.25F, 0.12F, 1.0F);
		fog.color.set(Mth.lerp(t, fog.color.x(), colour[0] * light), Mth.lerp(t, fog.color.y(), colour[1] * light),
			Mth.lerp(t, fog.color.z(), colour[2] * light), fog.color.w());
	}
}
