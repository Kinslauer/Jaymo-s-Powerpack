package com.jaymospowers.mixin;

import com.jaymospowers.core.Wrestle;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob brought down by a takedown thinks nothing and stops walking for a moment, but still falls and can be shoved (see Wrestle). */
@Mixin(Mob.class)
abstract class WrestleMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$wrestled(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (Wrestle.stunned(self)) {
			self.getNavigation().stop();
			self.setXxa(0.0F);
			self.setYya(0.0F);
			self.setZza(0.0F);
			self.setJumping(false);
			ci.cancel();
		}
	}
}
