package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.nightfall.NightfallClient;
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

/** The Nightfall's miasma ({@link NightfallClient}): a pale fog that closes the night in to a few dozen blocks. */
@Mixin(FogRenderer.class)
abstract class MiasmaFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void jaymoskingdoms$miasma(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
		ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		float thick = NightfallClient.miasma;
		if (thick <= 0.001F) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float t = thick * thick * (3.0F - 2.0F * thick);
		float sight = NightfallClient.SIGHT;
		fog.environmentalStart = Mth.lerp(t, fog.environmentalStart, 0.0F);
		fog.environmentalEnd = Mth.lerp(t, fog.environmentalEnd, Math.min(fog.environmentalEnd, sight));
		fog.skyEnd = Mth.lerp(t, fog.skyEnd, Math.min(fog.skyEnd, sight * 1.5F));
		fog.cloudEnd = Mth.lerp(t, fog.cloudEnd, Math.min(fog.cloudEnd, sight * 1.5F));
		// pale, but dim: it's night
		float light = 0.32F;
		float[] c = NightfallClient.COLOUR;
		fog.color.set(Mth.lerp(t, fog.color.x(), c[0] * light), Mth.lerp(t, fog.color.y(), c[1] * light), Mth.lerp(t, fog.color.z(), c[2] * light),
			fog.color.w());
	}
}
