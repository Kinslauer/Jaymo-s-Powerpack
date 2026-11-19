package com.jaymospowers.mixin;

import com.jaymospowers.combat.Shields;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A scythe, bill or gutting hook reaches round a raised shield: it is asked to stop only part of the blow ({@link Shields#hookedBlock}). */
@Mixin(LivingEntity.class)
abstract class ShieldHookMixin {
	@ModifyVariable(method = "applyItemBlocking", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$hookRoundShield(float damage, @Local(argsOnly = true) DamageSource source) {
		return Shields.hookedBlock((LivingEntity) (Object) this, source, damage);
	}
}
