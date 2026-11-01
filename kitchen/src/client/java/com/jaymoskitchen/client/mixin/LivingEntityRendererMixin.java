package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.archery.LegShots;
import com.jaymoskitchen.client.corpse.CrawlPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A creature hit in the legs is drawn crawling (see CrawlPose); a corpse with its eyes shut (see ClosedEyes). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$crawl(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		CrawlPose.extract(state, LegShots.crawlAmount(entity, partialTicks));
		// (0.5.23) the arrows stuck in it (players' are the game's own)
		int arrows = entity instanceof net.minecraft.world.entity.player.Player ? 0
			: com.jaymoskitchen.archery.StuckArrows.total(com.jaymoskitchen.archery.StuckArrows.of(entity));
		state.setData(com.jaymoskitchen.client.archery.StuckArrowsLayer.KEY, arrows > 0 ? new int[] {arrows, entity.getId()} : null);
		// (0.5.41) a snow golem melts down instead of tipping over red
		com.jaymoskitchen.client.corpse.SnowMeltPose.extract(entity, state);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymoskitchen$layDown(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		CrawlPose.rotate(state, poseStack, entityScale);
		com.jaymoskitchen.client.corpse.SnowMeltPose.squash(state, poseStack);
	}

	@ModifyVariable(method = "getRenderType", at = @At("STORE"), ordinal = 0)
	private Identifier jaymoskitchen$closedEyes(Identifier texture, LivingEntityRenderState state) {
		return com.jaymoskitchen.client.corpse.ClosedEyes.texture(((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel(), state, texture);
	}
}
