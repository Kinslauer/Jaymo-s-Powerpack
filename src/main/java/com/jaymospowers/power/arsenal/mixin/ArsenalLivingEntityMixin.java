package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.jaymospowers.power.arsenal.VoidCrown;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Summoned weapons in corrupted hands cut deeper (see ArsenalVoid#modifyDamage); the Void Crown picks its heads. */
@Mixin(LivingEntity.class)
abstract class ArsenalLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$arsenalVoidEdge(float damage, @Local(argsOnly = true) DamageSource source) {
		return ArsenalVoid.modifyDamage((LivingEntity) (Object) this, source, damage);
	}

	/** The Void Crown won't go on a head the void hasn't taken (armour slot clicks and shift-clicks, both sides). */
	@Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$voidCrownRefuses(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
		if (VoidCrown.refuses((LivingEntity) (Object) this, stack, slot)) {
			cir.setReturnValue(false);
		}
	}
}
