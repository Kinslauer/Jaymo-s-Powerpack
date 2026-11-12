package com.jaymospowers.power.web;

import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Perch (server side): a slinger who has settled on a narrow top (a fence post, a wall, a ledge's edge, a webline plank)
 * crouches there balanced, arms out, for everyone to see ({@link WebPower#PERCH}); the client holds them fast until they
 * move. From a perch Spider-Sense reaches {@link #SENSE_SCALE} times as far.
 */
public final class WebPerch {
	/** How much further Spider-Sense reaches from a perch. */
	public static final double SENSE_SCALE = 1.5;

	private WebPerch() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(WebNet.Perch.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (payload.on() && Powers.has(player, WebPower.INSTANCE) && player.isAlive() && !player.isSpectator() && player.onGround()) {
				player.setAttached(WebPower.PERCH, true);
			} else {
				player.removeAttached(WebPower.PERCH);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (perched(player) && (!player.onGround() || !player.isAlive() || !Powers.has(player, WebPower.INSTANCE))) {
					player.removeAttached(WebPower.PERCH);
				}
			}
		});
	}

	/** Whether {@code entity} is settled on a perch right now. */
	public static boolean perched(Entity entity) {
		return entity.getAttached(WebPower.PERCH) != null;
	}
}
