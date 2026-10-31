package com.jaymospowers.client.mixin;

import com.jaymospowers.client.armory.MusketSights;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.18.70) The first-person musket: no bow pose while it's held ready, raised to the sights instead ({@link MusketSights}). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class MusketSightsMixin {
	@ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
	private ItemUseAnimation jaymospowers$noBowPose(ItemUseAnimation animation, @Local(argsOnly = true) ItemStack stack) {
		return MusketSights.isGun(stack) ? ItemUseAnimation.NONE : animation;
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$raiseToSights(CallbackInfo ci, @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack,
		@Local(argsOnly = true) PoseStack poseStack) {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		if (!MusketSights.isGun(stack)) {
			// (Motion patch 4) shears, compass, clock, lantern, flint and steel
			HumanoidArm owner = Minecraft.getInstance().player.getMainArm();
			com.jaymospowers.client.armory.HeldItems.firstPerson(poseStack, hand == InteractionHand.MAIN_HAND ? owner : owner.getOpposite(), stack);
			return;
		}
		HumanoidArm main = Minecraft.getInstance().player.getMainArm();
		MusketSights.raise(poseStack, hand == InteractionHand.MAIN_HAND ? main : main.getOpposite(), stack);
	}
}
