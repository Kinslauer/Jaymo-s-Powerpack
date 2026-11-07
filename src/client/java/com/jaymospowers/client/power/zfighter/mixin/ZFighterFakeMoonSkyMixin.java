package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterFakeMoonSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Andy, 2026-11-01) A false moon in your sky ({@link ZFighterFakeMoonSky}): always full, bigger, in its own light. */
@Mixin(SkyRenderer.class)
abstract class ZFighterFakeMoonSkyMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void jaymospowers$falseMoonPhase(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
		if (ZFighterFakeMoonSky.fade() > 0.3F) {
			state.moonPhase = MoonPhase.FULL_MOON;
		}
	}

	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"),
		index = 2)
	private float jaymospowers$falseMoonSize(float scale) {
		float fade = ZFighterFakeMoonSky.fade();
		return fade <= 0.0F ? scale : scale * (1.0F + (ZFighterFakeMoonSky.SCALE - 1.0F) * fade);
	}

	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
		index = 1)
	private Vector4f jaymospowers$falseMoonTint(Vector4f colour) {
		if (ZFighterFakeMoonSky.fade() <= 0.0F) {
			return colour;
		}
		float[] tint = ZFighterFakeMoonSky.tint();
		return new Vector4f(colour.x * tint[0], colour.y * tint[1], colour.z * tint[2], colour.w);
	}
}
