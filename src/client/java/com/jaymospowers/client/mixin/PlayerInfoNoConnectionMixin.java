package com.jaymospowers.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player's info asked for before the client has a connection answers "none yet" (it's looked up again later), instead
 * of throwing. {@code Minecraft.getConnection()} is null until the local player is set, and StrawberryLib (Inferno's
 * library) asks whether a player is a spectator while the local player is still being made, so every login failed
 * (2026-11-18 3:48 AM: "Packet handling error" on ClientboundLoginPacket).
 */
@Mixin(AbstractClientPlayer.class)
abstract class PlayerInfoNoConnectionMixin {
	@Inject(method = "getPlayerInfo", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noConnectionYet(CallbackInfoReturnable<PlayerInfo> cir) {
		if (Minecraft.getInstance().getConnection() == null) {
			cir.setReturnValue(null);
		}
	}
}
