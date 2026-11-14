package com.jaymoskingdoms.client.mixin.spirits;

import com.jaymoskingdoms.client.spirits.GrueClient;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Under the Long Dark the Sun goes out ({@link GrueClient#sky}). */
@Mixin(SkyRenderer.class)
abstract class GrueSkySunMixin {
	@ModifyArg(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(Lcom/mojang/renderpearl/api/commands/RenderPass;FLcom/mojang/blaze3d/vertex/PoseStack;)V"),
		index = 1)
	private float jaymoskingdoms$grueSun(float alpha) {
		return alpha * (1.0F - GrueClient.sky());
	}
}
