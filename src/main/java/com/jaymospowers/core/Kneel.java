package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.7) Andy: "shift should now animate a crouch, the players gets low, hands out to the side, swaying as they move, ready
 * to draw a weapon and fire more comfortably on one knee when they stop moving". Crouched and still on the ground for
 * {@link #KNEEL_AFTER} ticks, a player is down on one knee (drawn by the client's {@code CrouchPose}, which reads the same
 * rule off what it sees). On one knee a bow or crossbow shot is steadier: its spread × {@link #STEADY}
 * ({@code KneelShotMixin}).
 */
public final class Kneel {
	/** Crouched and still this long (ticks) and you're on one knee. */
	public static final int KNEEL_AFTER = 8;
	/** Moving less than this a tick (blocks, across the ground) counts as still. */
	public static final double STILL = 0.03;
	/** A bow or crossbow's spread on one knee, as a share of its usual. */
	public static final float STEADY = 0.3F;

	/** Player → since when they've been crouched and still (game time). */
	private static final Map<UUID, Long> STILL_SINCE = new HashMap<>();
	/** Player → where they were last tick. */
	private static final Map<UUID, Vec3> LAST = new HashMap<>();

	private Kneel() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Kneel::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STILL_SINCE.clear();
			LAST.clear();
		});
	}

	/** Server: whether {@code player} is down on one knee just now. */
	public static boolean kneeling(Player player) {
		Long since = STILL_SINCE.get(player.getUUID());
		return since != null && player.level().getGameTime() - since >= KNEEL_AFTER;
	}

	/** Whether this player could be on one knee at all: crouched on the ground, on their own feet. */
	public static boolean crouched(Player player) {
		return player.isShiftKeyDown() && player.onGround() && !player.isPassenger() && !player.isSwimming() && !player.isFallFlying()
			&& !player.isSpectator() && !Carry.isCarried(player);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Vec3 here = player.position();
			Vec3 last = LAST.put(id, here);
			boolean still = last != null && here.subtract(last).horizontalDistanceSqr() < STILL * STILL;
			if (crouched(player) && still) {
				STILL_SINCE.putIfAbsent(id, player.level().getGameTime());
			} else {
				STILL_SINCE.remove(id);
			}
		}
		if (LAST.size() > server.getPlayerList().getPlayerCount() * 2 + 8) {
			LAST.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			STILL_SINCE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}
}
