package com.jaymospowers.mixin;

import com.jaymospowers.elemental.ElementalCorruption;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Elemental Corruption's wrath: a blow dealt by a player in it is twice as hard (see ElementalCorruption#empower). */
@Mixin(LivingEntity.class)
abstract class ElementalWrathDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$elementalWrath(float damage, @Local(argsOnly = true) DamageSource source) {
		return ElementalCorruption.empower((LivingEntity) (Object) this, source, damage);
	}
}
