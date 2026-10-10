package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoFlow;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Time Flow's stun: a stunned mob thinks nothing (no goals, no path, no move control) and its own walking stops, but it
 * still falls and can still be thrown.
 */
@Mixin(Mob.class)
abstract class ChronoMobStunMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chronoStun(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (ChronoFlow.stunned(self)) {
			self.getNavigation().stop();
			self.setXxa(0.0F);
			self.setYya(0.0F);
			self.setZza(0.0F);
			self.setJumping(false);
			ci.cancel();
		}
	}
}
