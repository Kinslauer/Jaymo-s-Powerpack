package com.jaymospowers.mixin;

import com.jaymospowers.core.MountClimb;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player climbs onto a horse, donkey or mule before riding it ({@link MountClimb}). On the client the ride isn't
 * started at all: the server says when it does (or that it doesn't, when the climb is called off or the horse shies).
 * Horseman's "rotate the horse, not the player" starts the ride on the client too, and a client that sat down there
 * while the server's climb was called off stayed on a horse the server never had them on: Sneak did nothing and the
 * horse never threw them.
 */
@Mixin(AbstractHorse.class)
abstract class MountClimbHorseMixin {
	@Inject(method = "doPlayerRide", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$climbOn(Player player, CallbackInfo ci) {
		AbstractHorse horse = (AbstractHorse)(Object)this;
		if (player.level().isClientSide() ? MountClimb.climbable(horse) : MountClimb.begin(player, horse)) {
			ci.cancel();
		}
	}
}
