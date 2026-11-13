package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoTimeStop;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A boss that shrugged off a Time Stop ({@link ChronoTimeStop#free}) isn't held by the server's tick freeze. */
@Mixin(TickRateManager.class)
abstract class TimeStopFreezeMixin {
	@Inject(method = "isEntityFrozen", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$bossWalksOn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ChronoTimeStop.free(entity)) {
			cir.setReturnValue(false);
		}
	}
}
