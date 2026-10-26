package com.jaymoskingdoms.spirits;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.236) What a move decides during the cast that is just happening, per keeper: the pose the arms take, the cooldown
 * that starts and the mana it costs, when they differ from the move's usual ones (a grab's first tap starts no cooldown so
 * the second can throw; a throw shows the throwing pose; a juggle costs per item lifted). {@link Spirits} clears them before
 * {@link Move#cast} and reads them once after it through {@link Move#pose(ServerPlayer)}, {@link Move#cooldown(ServerPlayer)}
 * and {@link Move#cost(ServerPlayer)}. Kept per player (not on the move, which is one object shared by every keeper and, in
 * singleplayer, by the client's HUD).
 */
final class CastHints {
	private static final Map<UUID, Hints> HINTS = new HashMap<>();

	private static final class Hints {
		int pose = -1, cooldown = -1;
		float cost = -1.0F;
	}

	private CastHints() {
	}

	static void setPose(ServerPlayer player, int pose) {
		HINTS.computeIfAbsent(player.getUUID(), k -> new Hints()).pose = pose;
	}

	static void setCooldown(ServerPlayer player, int ticks) {
		HINTS.computeIfAbsent(player.getUUID(), k -> new Hints()).cooldown = ticks;
	}

	static void setCost(ServerPlayer player, float cost) {
		HINTS.computeIfAbsent(player.getUUID(), k -> new Hints()).cost = cost;
	}

	/** The pose set for this cast, or {@code usual}. */
	static int pose(ServerPlayer player, int usual) {
		Hints h = HINTS.get(player.getUUID());
		return h == null || h.pose < 0 ? usual : h.pose;
	}

	/** The cooldown set for this cast, or {@code usual}. */
	static int cooldown(ServerPlayer player, int usual) {
		Hints h = HINTS.get(player.getUUID());
		return h == null || h.cooldown < 0 ? usual : h.cooldown;
	}

	/** The cost set for this cast, or {@code usual}. */
	static float cost(ServerPlayer player, float usual) {
		Hints h = HINTS.get(player.getUUID());
		return h == null || h.cost < 0.0F ? usual : h.cost;
	}

	/** Before a cast: nothing decided yet. */
	static void clear(ServerPlayer player) {
		HINTS.remove(player.getUUID());
	}

	static void forget(UUID id) {
		HINTS.remove(id);
	}
}
