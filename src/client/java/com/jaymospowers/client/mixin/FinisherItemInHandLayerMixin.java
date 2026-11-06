package com.jaymospowers.client.mixin;

import com.jaymospowers.client.finisher.FinisherPose;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.15.98) A finisher turns the weapon in the hand (point down for a plunge, along the arm for a stab: see FinisherPose).
 * (0.21.37) So does the kneel: a sword planted point down, a gun at a low ready (see CrouchPose).
 */
@Mixin(ItemInHandLayer.class)
abstract class FinisherItemInHandLayerMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$finisherGrip(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		FinisherPose.grip(state, arm, poseStack);
		// (0.21.37) and on one knee, the weapon planted or held at a low ready (see CrouchPose)
		com.jaymospowers.client.CrouchPose.grip(state, arm, poseStack);
	}
}
