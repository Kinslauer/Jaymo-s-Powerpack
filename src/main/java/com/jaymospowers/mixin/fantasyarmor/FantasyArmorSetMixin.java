package com.jaymospowers.mixin.fantasyarmor;

import com.jaymospowers.core.ArmourWear;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fantasy Armor (kenddie, DSMSLv2; shipped unchanged, nothing of it copied) gives a whole set's effects while all four
 * pieces are on. Its pieces wear out by {@link ArmourWear} (Andy, 2026-11-15: "nothing breaks, it becomes useless and
 * requires repair"), so a set with a worn-out piece is not whole: its effects stop until that piece is repaired.
 * Skipped when Fantasy Armor isn't there.
 */
@Pseudo
@Mixin(targets = "net.kenddie.fantasyarmor.item.armor.ArmorSetEffects", remap = false)
abstract class FantasyArmorSetMixin {
	@Inject(method = "hasFullSet", at = @At("RETURN"), cancellable = true, require = 0)
	private static void jaymospowers$wornOutIsNotWhole(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (ArmourWear.broken(player.getItemBySlot(slot))) {
				cir.setReturnValue(false);
				return;
			}
		}
	}
}
