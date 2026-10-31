package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Herding;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.444, Havens patch 8) A donkey's foal (a donkey, or with a horse a mule) needs a jenny and a jack too, as {@link HorseMixin}. */
@Mixin(Donkey.class)
abstract class DonkeyMixin {
	@Inject(method = "canMate", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$pair(Animal partner, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && partner instanceof AbstractHorse other && !Herding.pair((Donkey)(Object)this, other)) {
			cir.setReturnValue(false);
		}
	}
}
