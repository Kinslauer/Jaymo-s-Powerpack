package com.jaymospowers.mixin;

import com.jaymospowers.combat.Edge;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** {@link Edge}: a blow struck by hand with a dull blade does less damage. After {@link BlowsLivingMixin} (a higher priority goes later). */
@Mixin(value = LivingEntity.class, priority = 950)
abstract class EdgeLivingMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$dullBlade(float damage, @Local(argsOnly = true) DamageSource source) {
		if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity() && attacker != (Object) this) {
			return Edge.cut(attacker, damage);
		}
		return damage;
	}
}
