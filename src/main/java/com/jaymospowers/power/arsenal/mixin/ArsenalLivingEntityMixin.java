package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.ArsenalVoid;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Summoned weapons in corrupted hands cut deeper (see ArsenalVoid#modifyDamage). */
@Mixin(LivingEntity.class)
abstract class ArsenalLivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$arsenalVoidEdge(float damage, @Local(argsOnly = true) DamageSource source) {
		return ArsenalVoid.modifyDamage((LivingEntity) (Object) this, source, damage);
	}
}
