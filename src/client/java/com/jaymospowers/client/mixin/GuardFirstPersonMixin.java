package com.jaymospowers.client.mixin;

import com.jaymospowers.combat.Guard;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.17.67) Your own weapon guard in first person: the blade turned across in front of you (vanilla draws blocking only
 * for shields, so a sword held up looked like nothing happened).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class GuardFirstPersonMixin {
	@Unique
	private static final float SHIFT_X = -0.15F, SHIFT_Y = 0.12F, YAW = 30.0F, PITCH = -70.0F, ROLL = 60.0F;

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void jaymospowers$guardPose(CallbackInfo ci, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) InteractionHand hand,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ItemStack stack, @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) PoseStack pose) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isUsingItem() || player.getUsedItemHand() != hand || !Guard.weaponGuard(stack)) {
			return;
		}
		boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
		float side = right ? 1.0F : -1.0F;
		pose.translate(side * SHIFT_X, SHIFT_Y, 0.0F);
		pose.rotateDegrees(Axis.YP, side * YAW);
		pose.rotateDegrees(Axis.XP, PITCH);
		pose.rotateDegrees(Axis.YP, side * ROLL);
	}
}
