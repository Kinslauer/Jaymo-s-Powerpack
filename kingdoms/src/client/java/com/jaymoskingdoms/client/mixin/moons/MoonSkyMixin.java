package com.jaymoskingdoms.client.mixin.moons;

import com.jaymoskingdoms.Moons;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** (0.6.6) Tonight's moon in its colour and size ({@link Moons#clientMoonTint}, {@link Moons#clientMoonScale}). */
@Mixin(SkyRenderer.class)
abstract class MoonSkyMixin {
	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
		index = 1)
	private Vector4f jaymoskingdoms$moonColour(Vector4f colour) {
		if (Moons.clientFade <= 0.0F) {
			return colour;
		}
		Vec3 tint = Moons.clientMoonTint();
		return new Vector4f(colour.x * (float)tint.x, colour.y * (float)tint.y, colour.z * (float)tint.z, colour.w);
	}

	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"),
		index = 2)
	private float jaymoskingdoms$moonSize(float scale) {
		return Moons.clientFade <= 0.0F ? scale : scale * Moons.clientMoonScale();
	}
}
