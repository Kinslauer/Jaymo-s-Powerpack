package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Downed;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.70) A downed guard ({@link Downed}) is no one's foe, and dies with nothing to loot. */
@Mixin(LivingEntity.class)
abstract class DownedMixin {
	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$downedNoFoe(CallbackInfoReturnable<Boolean> cir) {
		if (Downed.isDowned((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$downedNoLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
		if (Downed.isDowned((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
