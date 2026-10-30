package com.jaymospowers.client.mixin;

import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.armory.KnifeGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.17.98) A warhammer held up is drawn as what it is ({@link HammerSlam#pose}): raised overhead charging the smash, or
 * a guard while the smash cools down. The item alone can't tell which, so the arm pose asks about the one holding it.
 * (0.17.114) Pinned to the (Avatar, ItemStack, InteractionHand) overload: {@code AvatarRenderer} has two {@code getArmPose}s,
 * and the bare name picked the (Avatar, HumanoidArm) one, which never calls {@code getUseAnimation}, so the client crashed on start.
 */
@Mixin(AvatarRenderer.class)
abstract class HammerPoseMixin {
	@WrapOperation(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
	private static ItemUseAnimation jaymospowers$hammerPose(ItemStack stack, Operation<ItemUseAnimation> original, Avatar avatar, ItemStack held,
		InteractionHand hand) {
		if (KnifeGuard.isKnife(stack.getItem()) && KnifeGuard.pose(avatar) != null) {
			// (0.18.64) a WeaponMod knife held up as a guard rather than drawn back to throw
			return ItemUseAnimation.BLOCK;
		}
		return HammerSlam.isWarhammer(stack.getItem()) ? HammerSlam.pose(avatar) : original.call(stack);
	}
}
