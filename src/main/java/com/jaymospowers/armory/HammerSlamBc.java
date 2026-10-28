package com.jaymospowers.armory;

import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.network.Packets;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** The Better Combat calls behind {@link HammerSlam}; only loaded when Better Combat is. */
final class HammerSlamBc {
	private HammerSlamBc() {
	}

	/** Everyone who can see {@code player} watches the slam (the swinger plays it themselves, client-side). */
	static void broadcast(ServerPlayer player) {
		Packets.AttackAnimation packet = new Packets.AttackAnimation(player.getId(), AnimatedHand.TWO_HANDED, HammerSlam.ANIMATION,
			HammerSlam.LENGTH, HammerSlam.UPSWING, 0.0F, HammerSlam.IMPACT, Packets.SwingParticles.EMPTY);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			if (viewer != player && ServerPlayNetworking.canSend(viewer, Packets.AttackAnimation.PACKET_ID)) {
				ServerPlayNetworking.send(viewer, packet);
			}
		}
	}
}
