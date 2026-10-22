package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Wounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.15.94) Wounds first: a hit vanilla is about to take from the health lands on the parts instead, and the health
 * left is what the parts' hearts come to ({@link Wounds#takeHit}). Players take theirs in {@link WoundsPlayerHurtMixin}.
 */
@Mixin(LivingEntity.class)
abstract class WoundsHurtMixin {
	@WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"))
	private void jaymospowers$woundsFirst(LivingEntity self, float health, Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
		original.call(self, Wounds.takeHit(self, source, self.getHealth() - health, health));
	}
}
