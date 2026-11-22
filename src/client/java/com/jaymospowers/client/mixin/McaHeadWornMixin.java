package com.jaymospowers.client.mixin;

import com.jaymospowers.client.McaPlayerHeads;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player's head worn (a captain's trophy, a head put on): drawn as the villager MCA draws that player as (McaPlayerHeads). */
@Mixin(CustomHeadLayer.class)
abstract class McaHeadWornMixin {
	@WrapOperation(method = "submit", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void jaymospowers$mcaHead(float animation, PoseStack poseStack, SubmitNodeCollector collector, int light, SkullModelBase model,
		RenderType renderType, int outline, ModelFeatureRenderer.CrumblingOverlay breakProgress, Operation<Void> original,
		@Local(argsOnly = true) LivingEntityRenderState state) {
		if (state.wornHeadType != SkullBlock.Types.PLAYER
			|| !McaPlayerHeads.submit(McaPlayerHeads.id(state.wornHeadProfile), poseStack, collector, light, outline)) {
			original.call(animation, poseStack, collector, light, model, renderType, outline, breakProgress);
		}
	}
}
