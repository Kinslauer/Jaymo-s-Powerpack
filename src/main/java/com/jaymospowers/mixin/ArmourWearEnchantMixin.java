package com.jaymospowers.mixin;

import com.jaymospowers.core.ArmourWear;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.20.68) A broken armour piece's enchantments (Protection and the rest) do nothing until it is repaired ({@link ArmourWear}). */
@Mixin(EnchantmentHelper.class)
abstract class ArmourWearEnchantMixin {
	@WrapOperation(method = "runIterationOnEquipment", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack jaymospowers$brokenArmourQuiet(LivingEntity owner, EquipmentSlot slot, Operation<ItemStack> original) {
		ItemStack stack = original.call(owner, slot);
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && ArmourWear.broken(stack) ? ItemStack.EMPTY : stack;
	}
}
