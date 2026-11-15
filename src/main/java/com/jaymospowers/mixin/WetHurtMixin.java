package com.jaymospowers.mixin;

import com.jaymospowers.core.wet.Wet;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fire hurts a wet body less and lightning more ({@link Wet#incoming}). */
@Mixin(LivingEntity.class)
abstract class WetHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$wetHurt(float damage, @Local(argsOnly = true) DamageSource source) {
		return Wet.incoming((LivingEntity) (Object) this, source, damage);
	}
}
