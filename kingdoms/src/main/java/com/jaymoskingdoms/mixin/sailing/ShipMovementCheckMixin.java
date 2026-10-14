package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.Ships;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.7) A player walking a ship's deck: their own game carries them with it, and the server's copy of the ship lies a
 * little apart from theirs, so the server takes their word for where they are aboard (no "moved wrongly"), and a deck in
 * open water isn't floating in the air (no flying kick).
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ShipMovementCheckMixin {
	@WrapOperation(method = "handlePlayerPositionChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isInPostImpulseGraceTime()Z"))
	private boolean jaymoskingdoms$aboard(ServerPlayer player, Operation<Boolean> original) {
		return original.call(player) || Ships.aboard(player) != null;
	}

	@Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$deckBelow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Ships.aboard(entity) != null) {
			cir.setReturnValue(false);
		}
	}
}
