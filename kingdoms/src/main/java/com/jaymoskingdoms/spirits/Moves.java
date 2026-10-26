package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.237) Every {@link Move} there is, by id, and each player's cooldowns on them. The hands' shared moves
 * ({@link HandMoves}) and each spirit's own register here when the game starts ({@link Spirits#register}).
 */
public final class Moves {
	private static final Map<String, Move> ALL = new LinkedHashMap<>();
	/** Game time each move is ready again, by player. */
	private static final Map<UUID, Map<String, Long>> READY = new HashMap<>();

	private Moves() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}

	/** Adds {@code move} to the list of all moves (a second with the same id is a mistake and is ignored). */
	public static void register(Move move) {
		if (ALL.putIfAbsent(move.id(), move) != null) {
			JaymosKingdoms.LOGGER.warn("two spirit moves called {}", move.id());
		}
	}

	public static @Nullable Move of(String id) {
		return ALL.get(id);
	}

	/** Whether {@code move} is off cooldown for {@code player}; if not, tells them how many seconds are left. */
	public static boolean ready(ServerPlayer player, Move move, long now) {
		long left = readyAt(player, move) - now;
		if (left <= 0L) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.wait", Math.max(1, Math.round(left / 20.0F))).withStyle(ChatFormatting.GRAY));
		return false;
	}

	/** Ticks until {@code move} is ready for {@code player} (0 now): the HUD greys the move by this. */
	public static long wait(ServerPlayer player, Move move, long now) {
		return Math.max(0L, readyAt(player, move) - now);
	}

	/** Starts {@code move}'s cooldown for {@code player}. */
	public static void start(ServerPlayer player, Move move, long now) {
		READY.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(move.id(), now + move.cooldown(player));
	}

	private static long readyAt(ServerPlayer player, Move move) {
		Map<String, Long> mine = READY.get(player.getUUID());
		Long at = mine == null ? null : mine.get(move.id());
		return at == null ? 0L : at;
	}

	static void forget(UUID id) {
		READY.remove(id);
	}
}
