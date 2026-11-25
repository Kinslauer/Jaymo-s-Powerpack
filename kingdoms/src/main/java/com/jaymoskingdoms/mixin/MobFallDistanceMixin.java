package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Stranded;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob stranded on a roof or a ledge ({@link Stranded}) may plan a deeper drop, to climb down. */
@Mixin(Mob.class)
abstract class MobFallDistanceMixin {
	@Inject(method = "getMaxFallDistance", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$stranded(CallbackInfoReturnable<Integer> cir) {
		int was = cir.getReturnValueI();
		int fall = Stranded.maxFall((Mob) (Object) this, was);
		if (fall != was) {
			cir.setReturnValue(fall);
		}
	}
}
