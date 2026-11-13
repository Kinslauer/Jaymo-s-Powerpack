package com.jaymoskingdoms.client.mixin.occult;

import com.jaymoskingdoms.client.OccultClient;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exposure, Initiated: what walks unseen shows to you as a pale see-through shape ({@link OccultClient#ethereal}); and,
 * Unveiled, the hidden cursed are outlined in violet ({@link OccultClient#unveiled}; a body glowing for any other reason
 * keeps its own colour).
 */
@Mixin(LivingEntityRenderer.class)
abstract class EtherealSightMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$etherealSight(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.isInvisibleToPlayer && OccultClient.ethereal(entity)) {
			state.isInvisibleToPlayer = false;
		}
		if (state.outlineColor != 0 && !entity.isCurrentlyGlowing() && OccultClient.unveiled(entity)) {
			state.outlineColor = OccultClient.UNVEILED_COLOR;
		}
	}
}
