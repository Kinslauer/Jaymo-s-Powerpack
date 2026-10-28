package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.Whalers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.307) A whaleboat her crew are pulling ({@link Whalers#CREWED}) is steered by nobody aboard, so the server rows
 * it as her captain orders: a player who gets in only throws irons (Andy: "When you join on a harpoon boat the ai
 * should retain control"). The game gives a boat to any player in its first seat, and puts players first. Both sides.
 */
@Mixin(AbstractBoat.class)
abstract class BoatCrewedMixin {
	@Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$crewed(CallbackInfoReturnable<LivingEntity> cir) {
		if (((AbstractBoat) (Object) this).hasAttached(Whalers.CREWED)) {
			cir.setReturnValue(null);
		}
	}
}
