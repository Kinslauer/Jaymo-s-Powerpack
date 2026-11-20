package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.FishingRealClient;
import com.jaymospowers.client.fishing.RodTip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Track A) The rod in your own hand bends with the line's strain and draws back while you charge a cast; its tip is kept
 * for the fishing line ({@link RodTip}).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FishingRodFirstPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$bendRod(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
		int lightCoords, CallbackInfo ci) {
		FishingRealClient.bend(Minecraft.getInstance().player, itemStack, poseStack, false, partialTicks);
		RodTip.beginFirstPerson(Minecraft.getInstance().player, hand, itemStack);
	}

	/** The fishing line starts at the rod tip: the rod is drawn, keep where its tip went. */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$rodTip(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
		int lightCoords, CallbackInfo ci) {
		RodTip.endFirstPerson();
	}

	/** The hands' base pose (view rotation undone, bob on), so the rod tip can be put back in the world's view. */
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void jaymospowers$handBase(float frameInterp, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		RodTip.handBase(poseStack.last().pose());
	}
}
