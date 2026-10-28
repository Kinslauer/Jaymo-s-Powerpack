package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Herding;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.305) A foal needs a mare and a stallion ({@link Herding#pair}). */
@Mixin(Horse.class)
abstract class HorseMixin {
	@Inject(method = "canMate", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$pair(Animal partner, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && partner instanceof AbstractHorse other && !Herding.pair((Horse)(Object)this, other)) {
			cir.setReturnValue(false);
		}
	}
}
