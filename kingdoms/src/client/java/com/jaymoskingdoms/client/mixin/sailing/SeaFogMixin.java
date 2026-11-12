package com.jaymoskingdoms.client.mixin.sailing;

import com.jaymoskingdoms.client.SeaEventsClient;
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
 * A fog bank at sea ({@link SeaEventsClient}): the world closes in to a couple of dozen blocks of wet grey, sky and
 * clouds too, dimmed at night like the sky's own fog.
 */
@Mixin(FogRenderer.class)
abstract class SeaFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void jaymoskingdoms$seaFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
		ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		float thick = SeaEventsClient.fog;
		if (thick <= 0.001F) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float sight = SeaEventsClient.FOG_SIGHT;
		float[] colour = SeaEventsClient.FOG_COLOUR;
		float t = thick * thick * (3.0F - 2.0F * thick);
		fog.environmentalStart = Mth.lerp(t, fog.environmentalStart, 0.0F);
		fog.environmentalEnd = Mth.lerp(t, fog.environmentalEnd, Math.min(fog.environmentalEnd, sight));
		fog.skyEnd = Mth.lerp(t, fog.skyEnd, Math.min(fog.skyEnd, sight));
		fog.cloudEnd = Mth.lerp(t, fog.cloudEnd, Math.min(fog.cloudEnd, sight));
		float light = Mth.clamp(Math.max(fog.color.x(), Math.max(fog.color.y(), fog.color.z())) * 1.25F, 0.1F, 1.0F);
		fog.color.set(Mth.lerp(t, fog.color.x(), colour[0] * light), Mth.lerp(t, fog.color.y(), colour[1] * light),
			Mth.lerp(t, fog.color.z(), colour[2] * light), fog.color.w());
	}
}
