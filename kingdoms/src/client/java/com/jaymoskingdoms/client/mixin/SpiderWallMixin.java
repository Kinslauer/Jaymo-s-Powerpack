package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.SpiderWalls;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A climbing spider is drawn flat on its wall ({@link SpiderWalls}). */
@Mixin(LivingEntityRenderer.class)
abstract class SpiderWallMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$spiderWall(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity instanceof Spider spider) {
			SpiderWalls.extract(spider, state, partialTicks);
		}
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymoskingdoms$onTheWall(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		SpiderWalls.rotate(state, poseStack);
	}
}
