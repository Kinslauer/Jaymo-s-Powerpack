package com.jaymospowers.mixin;

import com.jaymospowers.mortal.PvpCap;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The server's cap on what one player's abilities do to another in one hit ({@link PvpCap}; off unless set). */
@Mixin(LivingEntity.class)
abstract class PvpCapMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymospowers$pvpCap(float damage, @Local(argsOnly = true) DamageSource source) {
		return PvpCap.capped((LivingEntity) (Object) this, source, damage);
	}
}
