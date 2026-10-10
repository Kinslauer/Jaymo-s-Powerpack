package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.archery.LegShots;
import com.jaymoskitchen.client.corpse.CrawlPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A creature hit in the legs is drawn crawling (see CrawlPose). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$crawl(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		CrawlPose.extract(state, LegShots.crawlAmount(entity, partialTicks));
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymoskitchen$layDown(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CrawlPose.rotate(state, poseStack, entityScale);
	}
}
