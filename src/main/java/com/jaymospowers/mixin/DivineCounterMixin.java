package com.jaymospowers.mixin;

import com.jaymospowers.divine.DivineSense;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A Divine-born's blow on the one whose attack they have just slipped lands harder (DivineSense#counter), and their trained bare hands do too (DivineSense#fists). */
@Mixin(LivingEntity.class)
abstract class DivineCounterMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$divineCounter(float damage, @Local(argsOnly = true) DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		return DivineSense.fists(self, source, DivineSense.counter(self, source, damage));
	}
}
