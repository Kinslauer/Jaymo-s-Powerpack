package com.jaymoskitchen.mixin;

import com.jaymoskitchen.corpse.Scavengers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Andy (2026-11-23): "all mobs should have reduced visibility while focused on another task, for example if they are
 * eating they wont be as aware of sneaking players". A scavenger at its meal ({@link Scavengers#feeding}) notices anyone
 * at {@link Scavengers#FEEDING_SIGHT} of the distance it would: every goal that looks for someone (a vulture's flight
 * from players, a fox's, a wolf's hunting) asks {@code getVisibilityPercent} through {@code TargetingConditions}.
 */
@Mixin(LivingEntity.class)
abstract class ScavengerSightMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$feeding(ServerLevel level, Entity looker, CallbackInfoReturnable<Double> cir) {
		if (Scavengers.feeding(looker)) {
			cir.setReturnValue(cir.getReturnValueD() * Scavengers.FEEDING_SIGHT);
		}
	}
}
