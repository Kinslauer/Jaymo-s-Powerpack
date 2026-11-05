package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Mounted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.305) Our horses take a second rider, and swim with their riders instead of throwing them in deep water ({@link Mounted}).
 * (0.7.601) No player climbs onto a mount someone else is riding: a villager's, a guard's, a carter's ({@link Mounted#npcRidden}).
 */
@Mixin(Entity.class)
abstract class EntityMountMixin {
	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$pillion(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		if (Mounted.pillion((Entity)(Object)this, passenger)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$notTheirs(Entity vehicle, boolean force, boolean sendEventAndTriggers, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity)(Object)this;
		if (self instanceof Player && !self.level().isClientSide() && Mounted.npcRidden(vehicle)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "dismountsUnderwater", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$swims(CallbackInfoReturnable<Boolean> cir) {
		if (Mounted.swims((Entity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
