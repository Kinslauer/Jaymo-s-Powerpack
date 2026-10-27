package com.jaymospowers.mixin;

import com.jaymospowers.combat.Guard;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (0.17.67) The blow that answers a parry lands harder (see Guard#riposte). */
@Mixin(LivingEntity.class)
abstract class GuardRiposteMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$riposte(float damage, @Local(argsOnly = true) DamageSource source) {
		return Guard.riposte((LivingEntity) (Object) this, source, damage);
	}
}
