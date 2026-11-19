package com.jaymospowers.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Darkness feeds corruption (Andy's pick): a power's corruption meter ({@link Fate#addCorruption}) climbs a little faster
 * for whoever it has already begun to take: {@link #HIT} for a hit taken (at most one every {@link #HIT_GAP} ticks),
 * {@link #KILL} for a kill, and {@link #DARK} every {@link #DARK_EVERY} ticks spent in full darkness (light 0).
 * It only feeds a meter that has started ({@link #FROM} or more), so it never begins corruption in someone clean, and it
 * stops at {@link #CEILING}, short of the top: the last of it is the power's own doing. It adds to what each power does
 * already (Smog, the Storm, the Void Arsenal, Cryokinesis' kills, the Z Fighter's lessons all go through the same meter).
 */
public final class DarkFeed {
	/** The least meter it feeds, and the most it takes it to. */
	public static final int FROM = 1, CEILING = 90;
	/** Meter points for a hit taken, for a kill, and for each {@link #DARK_EVERY} ticks in full darkness. */
	public static final int HIT = 1, KILL = 1, DARK = 1;
	public static final int HIT_GAP = 40, DARK_EVERY = 20 * 10;

	private static final Map<UUID, Long> LAST_HIT = new HashMap<>();

	private DarkFeed() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && !blocked) {
				long now = player.level().getGameTime();
				if (now - LAST_HIT.getOrDefault(player.getUUID(), (long)-HIT_GAP) >= HIT_GAP && feed(player, HIT)) {
					LAST_HIT.put(player.getUUID(), now);
				}
			}
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killed, source) -> {
			if (entity instanceof ServerPlayer player) {
				feed(player, KILL);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % DARK_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerLevel level = player.level();
				if (player.isAlive() && level.getMaxLocalRawBrightness(player.blockPosition()) == 0) {
					feed(player, DARK);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_HIT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_HIT.clear());
	}

	/** Adds {@code amount} to a meter that has begun and is under the ceiling. True if it did. */
	private static boolean feed(ServerPlayer player, int amount) {
		if (!Fate.canCorrupt(player) || player.isCreative()) {
			return false;
		}
		int meter = Fate.corruption(player);
		if (meter < FROM || meter >= CEILING) {
			return false;
		}
		Fate.addCorruption(player, amount);
		return true;
	}
}
