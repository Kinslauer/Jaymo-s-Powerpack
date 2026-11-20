package com.jaymospowers.client.fishing.mixin;

import com.jaymospowers.client.fishing.FishingRealClient;
import com.jaymospowers.client.fishing.RodTip;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Track A) A player's rod bends with the line's strain in third person too, and draws back while they charge a cast; its
 * tip is kept for the fishing line ({@link RodTip}).
 */
@Mixin(ItemInHandLayer.class)
abstract class FishingRodThirdPersonMixin {
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$bendRod(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar && Minecraft.getInstance().level != null) {
			Entity holder = Minecraft.getInstance().level.getEntity(avatar.id);
			FishingRealClient.bend(holder, stack, poseStack, true, 0.0F);
			RodTip.beginThirdPerson(holder instanceof Player player ? player : null, arm, stack);
		}
	}

	/** The fishing line starts at the rod tip: the rod is drawn, keep where its tip went. */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$rodTip(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack,
		SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar) {
			RodTip.endThirdPerson(avatar.id, avatar.x, avatar.y, avatar.z, avatar.scale);
		}
	}
}
