package com.jaymospowers.compat.mixin;

import com.jaymospowers.combat.IncomingBlows;
import net.bettercombat.network.Packets;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's Better Combat swing starts (its attack animation, sent by the swinger's client before the upswing): the one
 * it's aimed at is warned ({@link IncomingBlows#swing}). Its stop signal (a cancelled swing) warns nobody.
 */
@Mixin(value = ServerNetwork.class, remap = false)
abstract class IncomingSwingMixin {
	@Inject(method = "handleAttackAnimation", at = @At("HEAD"))
	private static void jaymospowers$warn(Packets.AttackAnimation packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
		if (packet.animationName() == null || packet.animationName().equals(Packets.AttackAnimation.StopSymbol) || packet.length() <= 0.0F) {
			return;
		}
		int upswing = packet.upswingTicks();
		double range = packet.weaponRange();
		server.execute(() -> IncomingBlows.swing(player, upswing, range));
	}
}
