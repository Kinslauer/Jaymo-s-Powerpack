package com.jaymoskingdoms.client.mixin.spirits;

import com.jaymoskingdoms.client.spirits.GrueClient;
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
 * The Long Dark's sky ({@link GrueClient#sky}; Andy asked why it did not change the sky itself): the fog goes black and the
 * sky and clouds are drawn into it, so the whole dome is dark. The ground's own fog is only pulled in to {@link #SIGHT}.
 */
@Mixin(FogRenderer.class)
abstract class GrueSkyFogMixin {
	/** How far the land can still be seen under it (blocks), and how near the sky and clouds are swallowed. */
	private static final float SIGHT = 64.0F, SKY_SIGHT = 8.0F;

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void jaymoskingdoms$grueSky(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
		ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		float t = GrueClient.sky();
		if (t <= 0.001F) {
			return;
		}
		FogData fog = cir.getReturnValue();
		fog.color.set(Mth.lerp(t, fog.color.x(), 0.0F), Mth.lerp(t, fog.color.y(), 0.0F), Mth.lerp(t, fog.color.z(), 0.0F), fog.color.w());
		fog.environmentalEnd = Mth.lerp(t, fog.environmentalEnd, Math.min(fog.environmentalEnd, SIGHT));
		fog.skyEnd = Mth.lerp(t, fog.skyEnd, Math.min(fog.skyEnd, SKY_SIGHT));
		fog.cloudEnd = Mth.lerp(t, fog.cloudEnd, Math.min(fog.cloudEnd, SKY_SIGHT));
	}
}
