package com.jaymospowers.mixin;

import com.jaymospowers.combat.Blows;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link Blows}: a swing's damage answers to its weapon's distance, and a spear's stab is a swing as a player's attack is
 * ({@link BlowsPlayerMixin}). Ahead of the other changes to a hit's damage (a lower priority goes first), so a cap set
 * on it afterwards still holds.
 */
@Mixin(value = LivingEntity.class, priority = 900)
abstract class BlowsLivingMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$blowAtItsDistance(float damage, @Local(argsOnly = true) DamageSource source) {
		return Blows.measure((LivingEntity) (Object) this, source, damage);
	}

	@Inject(method = "stabAttack", at = @At("HEAD"))
	private void jaymospowers$stabBegins(EquipmentSlot slot, Entity target, float damage, boolean dealsDamage, boolean dealsKnockback, boolean dismounts,
		CallbackInfoReturnable<Boolean> cir) {
		Blows.begin((LivingEntity) (Object) this);
	}

	@Inject(method = "stabAttack", at = @At("RETURN"))
	private void jaymospowers$stabEnds(EquipmentSlot slot, Entity target, float damage, boolean dealsDamage, boolean dealsKnockback, boolean dismounts,
		CallbackInfoReturnable<Boolean> cir) {
		Blows.end((LivingEntity) (Object) this);
	}
}
