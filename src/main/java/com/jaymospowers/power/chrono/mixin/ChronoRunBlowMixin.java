package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoCalculation;
import com.jaymospowers.power.chrono.ChronoRapid;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A melee blow landed at a sprint by a speedster hits harder the faster it goes (see ChronoRapid#blowBonus). */
@Mixin(LivingEntity.class)
abstract class ChronoRunBlowMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$runningBlow(float damage, @Local(argsOnly = true) DamageSource source) {
		float bonus = ChronoRapid.blowBonus(source) + ChronoCalculation.weakBonus(source);
		return bonus > 0.0F ? damage * (1.0F + bonus) : damage;
	}
}
