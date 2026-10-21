package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Senses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.111) Mob senses ({@link Senses#factor}): how far a hostile or neutral mob notices a player (its follow range times
 * this) follows the light, sneaking, standing still, where the mob looks, a hood and glowing. At the return, so vanilla's
 * invisibility and mob heads and Jaymo's Powers' Darktide veil have already had their say and this multiplies on top.
 * Every target goal asks {@code getVisibilityPercent} through {@code TargetingConditions}.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$senses(ServerLevel level, Entity targeting, CallbackInfoReturnable<Double> cir) {
		double factor = Senses.factor((LivingEntity) (Object) this, level, targeting);
		if (factor != 1.0) {
			cir.setReturnValue(cir.getReturnValue() * factor);
		}
	}
}
