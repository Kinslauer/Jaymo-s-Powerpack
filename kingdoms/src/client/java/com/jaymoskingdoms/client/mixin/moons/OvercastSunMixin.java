package com.jaymoskingdoms.client.mixin.moons;

import com.jaymoskingdoms.HordeDays;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** On a horde day the Sun is hidden behind the grey ({@link HordeDays}): it fades out as the overcast comes in. */
@Mixin(SkyRenderer.class)
abstract class OvercastSunMixin {
	@ModifyArg(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSun(Lcom/mojang/renderpearl/api/commands/RenderPass;FLcom/mojang/blaze3d/vertex/PoseStack;)V"),
		index = 1)
	private float jaymoskingdoms$overcastSun(float alpha) {
		return HordeDays.clientFade <= 0.0F ? alpha : alpha * (1.0F - HordeDays.clientFade);
	}
}
