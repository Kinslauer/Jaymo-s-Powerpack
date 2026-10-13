package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.ClassPerks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** (0.6.0) What a class's weapons do to whatever they hit ({@link ClassPerks#dealt}): an Archer's arrows hit harder. */
@Mixin(LivingEntity.class)
abstract class LivingEntityHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$classDamage(float amount, ServerLevel level, DamageSource source) {
		return ClassPerks.dealt(source, amount);
	}
}
