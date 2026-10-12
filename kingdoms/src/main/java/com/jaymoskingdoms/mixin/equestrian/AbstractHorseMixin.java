package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Equestrian;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.34) Horses ride at their rider's gait, are broken in by their temper, and pass their breed to their foals ({@link Equestrian}). */
@Mixin(AbstractHorse.class)
abstract class AbstractHorseMixin {
	@Inject(method = "getRiddenSpeed", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$gait(Player controller, CallbackInfoReturnable<Float> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			cir.setReturnValue(Equestrian.riddenSpeed(self, controller, cir.getReturnValueF()));
		}
	}

	@Inject(method = "getMaxTemper", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$temper(CallbackInfoReturnable<Integer> cir) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			cir.setReturnValue(Equestrian.maxTemper(self, cir.getReturnValueI()));
		}
	}

	@Inject(method = "setOffspringAttributes", at = @At("TAIL"))
	private void jaymoskingdoms$foal(AgeableMob partner, AbstractHorse baby, CallbackInfo ci) {
		AbstractHorse self = (AbstractHorse)(Object)this;
		if (Equestrian.covered(self)) {
			Equestrian.foal(self, partner, baby);
		}
	}
}
