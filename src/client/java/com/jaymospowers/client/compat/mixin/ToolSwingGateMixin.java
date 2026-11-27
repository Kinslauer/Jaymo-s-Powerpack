package com.jaymospowers.client.compat.mixin;

import com.jaymospowers.combat.RegionSwings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.AttackInteractor;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A work tool ({@link RegionSwings#tool}: an axe, pick, shovel or hoe not made for war) swings Better Combat's way only
 * while you're locked on: otherwise Better Combat sees no weapon in the hand when you click, and the plain swing and
 * hit go ahead. Balkon's flail never swings Better Combat's way: its left click is its own wind-up and jab (armory/Lash,
 * client/armory/FlailSwingClient). In Combo Clash's combat mode nothing does: the click is its 4-hit string
 * ({@code client.compat.ComboClashClient}).
 */
@Mixin(value = AttackInteractor.class, remap = false)
abstract class ToolSwingGateMixin {
	@WrapOperation(method = {"onDoAttack", "onHandleBlockBreaking"}, at = @At(value = "INVOKE",
		target = "Lnet/bettercombat/logic/WeaponRegistry;getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;"))
	private WeaponAttributes jaymospowers$toolsOnlyLocked(ItemStack stack, Operation<WeaponAttributes> original) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && (RegionSwings.tool(stack) && !RegionSwings.lockedOn(player) || com.jaymospowers.armory.Lash.isBallFlail(stack)
			|| com.jaymospowers.client.compat.ComboClashClient.active())) {
			return null;
		}
		return original.call(stack);
	}
}
