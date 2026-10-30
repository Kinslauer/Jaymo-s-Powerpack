package com.jaymospowers.client.mixin;

import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.armory.KnifeGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.17.98) Your own warhammer in first person: the overhead charge, or (cooling down) the guard ({@link HammerSlam#pose}). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class HammerFirstPersonMixin {
	@WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
	private ItemUseAnimation jaymospowers$hammerPose(ItemStack stack, Operation<ItemUseAnimation> original) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && KnifeGuard.isKnife(stack.getItem()) && KnifeGuard.pose(player) != null) {
			// (0.18.61) your WeaponMod knife held up as a guard
			return ItemUseAnimation.BLOCK;
		}
		return player != null && HammerSlam.isWarhammer(stack.getItem()) ? HammerSlam.pose(player) : original.call(stack);
	}
}
