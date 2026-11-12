package com.jaymoskingdoms.client.mixin.disasters;

import com.jaymoskingdoms.client.disasters.SkiesClient;
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
 * The everyday weather's fog ({@link SkiesClient}): morning mist pales and shortens the view, a downpour closes it in
 * to a grey few dozen blocks. Dimmed by night like the sky's own fog, as a sandstorm's is ({@link CalamityFogMixin}).
 */
@Mixin(FogRenderer.class)
abstract class SkiesFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void jaymoskingdoms$skiesFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
		ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		FogData fog = cir.getReturnValue();
		jaymoskingdoms$veil(fog, SkiesClient.mist * SkiesClient.MIST_FOG, SkiesClient.MIST_SIGHT, SkiesClient.MIST_COLOUR);
		jaymoskingdoms$veil(fog, SkiesClient.downpour, SkiesClient.DOWNPOUR_SIGHT, SkiesClient.DOWNPOUR_COLOUR);
	}

	private static void jaymoskingdoms$veil(FogData fog, float thick, float sight, float[] colour) {
		if (thick <= 0.001F) {
			return;
		}
		float t = thick * thick * (3.0F - 2.0F * thick);
		fog.environmentalStart = Mth.lerp(t, fog.environmentalStart, 0.0F);
		fog.environmentalEnd = Mth.lerp(t, fog.environmentalEnd, Math.min(fog.environmentalEnd, sight));
		fog.skyEnd = Mth.lerp(t, fog.skyEnd, Math.min(fog.skyEnd, sight * 2.0F));
		fog.cloudEnd = Mth.lerp(t, fog.cloudEnd, Math.min(fog.cloudEnd, sight * 2.0F));
		float light = Mth.clamp(Math.max(fog.color.x(), Math.max(fog.color.y(), fog.color.z())) * 1.25F, 0.12F, 1.0F);
		fog.color.set(Mth.lerp(t, fog.color.x(), colour[0] * light), Mth.lerp(t, fog.color.y(), colour[1] * light),
			Mth.lerp(t, fog.color.z(), colour[2] * light), fog.color.w());
	}
}
