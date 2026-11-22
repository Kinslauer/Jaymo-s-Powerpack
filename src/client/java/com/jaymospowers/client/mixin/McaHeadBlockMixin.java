package com.jaymospowers.client.mixin;

import com.jaymospowers.client.McaHeadOwner;
import com.jaymospowers.client.McaPlayerHeads;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's head set down as a block: drawn as the villager MCA draws that player as (McaPlayerHeads). */
@Mixin(SkullBlockRenderer.class)
abstract class McaHeadBlockMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/SkullBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
		at = @At("TAIL"))
	private void jaymospowers$headOwner(SkullBlockEntity blockEntity, SkullBlockRenderState state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress, CallbackInfo ci) {
		((McaHeadOwner)state).jaymospowers$setHeadOwner(state.skullType == SkullBlock.Types.PLAYER ? McaPlayerHeads.id(blockEntity.getOwnerProfile()) : null);
	}

	@WrapOperation(
		method = "submit(Lnet/minecraft/client/renderer/blockentity/state/SkullBlockRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/blockentity/SkullBlockRenderer;submitSkull(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/model/object/skull/SkullModelBase;Lnet/minecraft/client/renderer/rendertype/RenderType;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void jaymospowers$mcaHead(float animation, PoseStack poseStack, SubmitNodeCollector collector, int light, SkullModelBase model,
		RenderType renderType, int outline, ModelFeatureRenderer.CrumblingOverlay breakProgress, Operation<Void> original,
		@Local(argsOnly = true) SkullBlockRenderState state) {
		if (!McaPlayerHeads.submit(((McaHeadOwner)state).jaymospowers$headOwner(), poseStack, collector, light, outline)) {
			original.call(animation, poseStack, collector, light, model, renderType, outline, breakProgress);
		}
	}
}
