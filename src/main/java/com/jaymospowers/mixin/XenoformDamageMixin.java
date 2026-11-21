package com.jaymospowers.mixin;

import com.jaymospowers.power.xenoform.XenoformPower;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Xenoform: a blow dealt by a player in an alien's body grows with their mastery (see XenoformPower#grow). */
@Mixin(LivingEntity.class)
abstract class XenoformDamageMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$xenoformGrows(float damage, @Local(argsOnly = true) DamageSource source) {
		return XenoformPower.grow((LivingEntity) (Object) this, source, damage);
	}
}
