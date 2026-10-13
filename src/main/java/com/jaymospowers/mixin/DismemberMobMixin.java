package com.jaymospowers.mixin;

import com.jaymospowers.core.Dismember;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A beheaded mob staggering about has no mind left to run its AI (see Dismember#stagger). */
@Mixin(Mob.class)
abstract class DismemberMobMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$stagger(CallbackInfo ci) {
		if (Dismember.stagger((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
