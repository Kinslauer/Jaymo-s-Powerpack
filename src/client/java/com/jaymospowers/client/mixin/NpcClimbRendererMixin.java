package com.jaymospowers.client.mixin;

import com.jaymospowers.client.NpcClimbPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An NPC pulling itself up over an edge (see NpcClimbPose): read onto the render state, squared to the wall; the lean after the body turns. */
@Mixin(LivingEntityRenderer.class)
abstract class NpcClimbRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$npcClimbState(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		NpcClimbPose.extract(entity, state, partialTicks);
	}

	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void jaymospowers$npcClimbLean(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		NpcClimbPose.rotate(state, poseStack);
	}
}
