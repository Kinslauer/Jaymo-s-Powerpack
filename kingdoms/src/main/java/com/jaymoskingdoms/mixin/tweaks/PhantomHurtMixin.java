package com.jaymoskingdoms.mixin.tweaks;

import com.jaymoskingdoms.mobs.PhantomCarry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A player a phantom is carrying hurts it twice as much ({@link PhantomCarry#dealt}). */
@Mixin(LivingEntity.class)
abstract class PhantomHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$carriedBlows(float amount, ServerLevel level, DamageSource source) {
		return PhantomCarry.dealt((LivingEntity)(Object)this, source, amount);
	}
}
