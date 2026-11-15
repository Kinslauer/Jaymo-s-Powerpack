package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.Herds;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crowded pens ({@link Herds#crowded}): a crowded farm animal won't take to breeding, and now and then butts another. */
@Mixin(Animal.class)
abstract class CrowdedPenMixin {
	@Inject(method = "canFallInLove", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$crowdedPen(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Herds.crowded((Animal) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void jaymoskitchen$jostle(CallbackInfo ci) {
		Herds.tick((Animal) (Object) this);
	}
}
