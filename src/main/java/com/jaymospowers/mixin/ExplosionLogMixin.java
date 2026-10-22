package com.jaymospowers.mixin;

import com.jaymospowers.core.ExplosionLog;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (BWM port) Logs each blast a player is behind as it goes off ({@link ExplosionLog}). */
@Mixin(ServerExplosion.class)
abstract class ExplosionLogMixin {
	@Inject(method = "explode", at = @At("HEAD"))
	private void jaymospowers$log(CallbackInfoReturnable<Integer> cir) {
		ExplosionLog.explosion((ServerExplosion) (Object) this);
	}
}
