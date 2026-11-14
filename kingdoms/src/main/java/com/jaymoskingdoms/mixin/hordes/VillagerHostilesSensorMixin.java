package com.jaymoskingdoms.mixin.hordes;

import com.jaymoskingdoms.reaped.Reaped;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers run from the reaped ({@link Reaped}) as they do from living illagers. */
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
	@Inject(method = "isHostile", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$reapedHostile(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Reaped) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "isClose", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$reapedClose(LivingEntity body, LivingEntity mob, CallbackInfoReturnable<Boolean> cir) {
		if (mob instanceof Reaped) {
			cir.setReturnValue(mob.distanceToSqr(body) <= Reaped.FRIGHT_RANGE * Reaped.FRIGHT_RANGE);
		}
	}
}
