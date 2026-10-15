package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Smoke can't put armour on: the inventory's armour slots (clicks, shift-clicks, hotbar keys) and dispensers refuse
 * it while you're in Vapour Form. Armour you already wear stays on. Right-click equipping is {@link SmokeEquipMixin}.
 */
@Mixin(LivingEntity.class)
abstract class SmokeArmourMixin {
	@Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noArmourSlot(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
		if (slot.isArmor() && (Object) this instanceof Player player && SmokeVapour.active(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canEquipWithDispenser", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noDispensedArmour(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && equippable.slot().isArmor() && (Object) this instanceof Player player && SmokeVapour.active(player)) {
			cir.setReturnValue(false);
		}
	}
}
