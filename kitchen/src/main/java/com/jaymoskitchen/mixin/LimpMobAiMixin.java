package com.jaymoskitchen.mixin;

import com.jaymoskitchen.ragdoll.MobRagdolls;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob knocked limp (see MobRagdolls) has no mind while it's down: no goals, no pathing, no looking about, no attacks. */
@Mixin(Mob.class)
abstract class LimpMobAiMixin {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limp(CallbackInfo ci) {
		if (MobRagdolls.isLimp((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
