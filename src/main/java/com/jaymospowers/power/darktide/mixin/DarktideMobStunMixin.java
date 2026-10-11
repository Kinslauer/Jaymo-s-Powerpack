package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktideStatus;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Darktide's stun: a stunned mob thinks nothing and stops walking, but still falls and can still be shoved. */
@Mixin(Mob.class)
abstract class DarktideMobStunMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$darktideStun(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (DarktideStatus.stunned(self)) {
			self.getNavigation().stop();
			self.setXxa(0.0F);
			self.setYya(0.0F);
			self.setZza(0.0F);
			self.setJumping(false);
			ci.cancel();
		}
	}
}
