package com.jaymospowers.mixin;

import com.jaymospowers.core.Carry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A carried body shares its carrier's spot: they don't shove each other apart, and it's out of the way of crosshairs
 * and projectiles (the carrier's own arrows would hit it first), and it doesn't suffocate in a low ceiling.
 */
@Mixin(LivingEntity.class)
abstract class CarryLivingEntityMixin {
	@Inject(method = "doPush", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carryNoShove(Entity other, CallbackInfo ci) {
		if (Carry.linked((Entity)(Object)this, other)) {
			ci.cancel();
		}
	}

	/** A carried body is drawn in the hold, not at the carrier's feet where it really is: it never chokes in a wall there. */
	@Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$carriedNotInWall(CallbackInfoReturnable<Boolean> cir) {
		if (Carry.isCarried((Entity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPickable", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$carriedNotPickable(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Carry.isCarried((Entity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
