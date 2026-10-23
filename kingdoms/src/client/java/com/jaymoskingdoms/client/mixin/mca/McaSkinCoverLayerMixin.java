package com.jaymoskingdoms.client.mixin.mca;

import com.jaymoskingdoms.client.McaSkinCover;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.178) Tells {@link McaSkinCover} which model is MCA's bare body (its skin layer's) and what the clothes drawn on
 * each villager cover. Ahead of the other MCA layer mixins, so a disguise that drops the clothes still clears them.
 */
@Mixin(value = VillagerLayer.class, priority = 900)
abstract class McaSkinCoverLayerMixin {
	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
		at = @At("HEAD"))
	private void jaymoskingdoms$skinCoverStart(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state,
		float yRot, float xRot, CallbackInfo ci) {
		Object layer = this;
		if (layer instanceof SkinLayer<?, ?> skin) {
			McaSkinCover.skinModel(skin.model);
		} else if (layer instanceof ClothingLayer<?, ?>) {
			McaSkinCover.reset(state);
		}
	}

	@Inject(method = "renderFinal(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FZZ)V",
		at = @At("HEAD"))
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void jaymoskingdoms$skinCoverClothes(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state,
		float partialTicks, boolean translucent, boolean outline, CallbackInfo ci) {
		Object layer = this;
		if (layer instanceof ClothingLayer clothes) {
			Identifier texture = clothes.getSkin(state);
			if (texture != null && clothes.canUse(texture)) {
				McaSkinCover.clothes(state, texture);
			}
		}
	}
}
