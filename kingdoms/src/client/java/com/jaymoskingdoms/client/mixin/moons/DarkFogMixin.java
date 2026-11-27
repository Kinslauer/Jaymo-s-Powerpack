package com.jaymoskingdoms.client.mixin.moons;

import com.jaymoskingdoms.client.DarkNights;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Nether's and the End's fog, dimmed ({@link DarkNights#dimFog}). */
@Mixin(FogRenderer.class)
abstract class DarkFogMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void jaymoskingdoms$darkFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
		ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		DarkNights.dimFog(cir.getReturnValue().color, camera.getFluidInCamera() != FogType.NONE);
	}
}
