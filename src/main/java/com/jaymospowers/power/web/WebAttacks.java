package com.jaymospowers.power.web;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * The web slinger's attacks that run for more than a moment (Legends patch 2): zip strike, riding a launched foe, the two-line dive
 * slam, the rising drop kick, shock lines, the wrecking ball, the web storm and the hunting dive. This is where they are ticked, forgotten
 * and told apart from ordinary movement; each has its own class and its own notes ({@link WebZipStrike}, {@link WebSurf},
 * {@link WebDiveSlam}, {@link WebRisingKick}, {@link WebShock}, {@link WebWreck}, {@link WebStorm}, {@link WebHunt}). The web
 * uptilt, pin to the wall, the five-hit fists and the full-layer cap sit in {@link WebUptilt}, {@link WebPin}, {@link WebFistCombo}
 * and {@link Webbed}. They share no packets of their own: they ride on Zip, Yank, the punch (a {@link WebWhip.Hold}) and the ultimate key.
 */
final class WebAttacks {
	private WebAttacks() {
	}

	static void register() {
		WebGrind.register();
		WebStilts.register();
		WebTempest.register();
		WebLunge.register();
		// every move ends (and gives back what it carries) when its slinger leaves, changes world or the server stops
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer()));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forget(player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				forget(player);
			}
			WebStrikeLines.clearAll();
			WebUptilt.clearAll();
			WebPin.clearAll();
			WebZipStrike.clearAll();
			WebSurf.clearAll();
			WebDiveSlam.clearAll();
			WebRisingKick.clearAll();
			WebShock.clearAll();
			WebWreck.clearAll();
			WebStorm.clearAll();
			WebHunt.clearAll();
			WebGrind.clearAll();
			WebStilts.clearAll();
			WebTempest.clearAll();
			WebLunge.clearAll();
			WebSymbioteSnuff.clearAll();
		});
	}

	/** Every tick, for a web slinger. */
	static void tick(ServerPlayer player) {
		WebUptilt.tick(player);
		WebPin.tick(player);
		WebZipStrike.tick(player);
		WebSurf.tick(player);
		WebDiveSlam.tick(player);
		WebRisingKick.tick(player);
		WebShock.tick(player);
		WebWreck.tick(player);
		WebStorm.tick(player);
		WebHunt.tick(player);
		WebStrikeLines.tick(player);
		WebGrind.tick(player);
		WebStilts.tick(player);
		WebTempest.tick(player);
		WebLunge.tick(player);
	}

	/** The power was lost, or the player left: nothing of theirs goes on. */
	static void forget(ServerPlayer player) {
		WebStrikeLines.clear(player);
		WebStrikeLines.forget(player);
		WebUptilt.forget(player);
		WebPin.forget(player);
		WebZipStrike.forget(player);
		WebSurf.forget(player);
		WebDiveSlam.forget(player);
		WebRisingKick.forget(player);
		WebShock.forget(player);
		WebWreck.forget(player);
		WebStorm.forget(player);
		WebHunt.forget(player);
		WebGrind.forget(player);
		WebStilts.forget(player);
		WebTempest.forget(player);
		WebLunge.forget(player);
		WebSymbioteSnuff.forget(player);
	}

	/** Hauled along by one of these (a dive, a ride, a hunt, a zip at a foe): the movement check leaves them be. */
	static boolean moving(ServerPlayer player) {
		return WebDiveSlam.diving(player) || WebSurf.surfing(player) || WebHunt.hunting(player) || WebZipStrike.flying(player)
			|| WebUptilt.arcing(player) || WebPin.pulling(player) || WebGrind.dragging(player) || WebLunge.flying(player);
	}
}
