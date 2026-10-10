package com.jaymoskitchen.cooking;

import com.jaymoskitchen.net.Payloads.SimUpdate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The blast furnace's searing minigame. A marker sweeps back and forth across the heat bar, a little faster
 * every round; press when it's inside the target (placed somewhere new each round) to sear the meat.
 * The sweeping and pressing run on the client; it reports how the rounds went and both sides judge with
 * {@link #judge}.
 */
public final class Sear {
	public static final int ROUNDS = 5;
	/** Target zone and its perfect core, as fractions of the bar. */
	public static final float ZONE_WIDTH = 0.16F, CORE_WIDTH = 0.05F;
	/** Marker speed (bar widths per tick) in the first round, the extra per round, and the random wobble. */
	public static final float SPEED = 0.022F, SPEED_STEP = 0.007F, SPEED_JITTER = 0.006F;
	/** A round with no press after this many ticks counts as a miss. */
	public static final int ROUND_TICKS = 140;
	/** Every round takes at least this long (the marker has to get somewhere). */
	private static final int MIN_TICKS_PER_ROUND = 6;

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/** The dishes whose sessions here are running at {@code pos} in {@code level} (for the oven's cookware locks). */
	static void dishesAt(net.minecraft.server.level.ServerLevel level, BlockPos pos, java.util.List<Dish> out) {
		for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
			if (entry.getValue().pos().equals(pos)) {
				net.minecraft.server.level.ServerPlayer cook = level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (cook != null && cook.level() == level) {
					out.add(entry.getValue().dish());
				}
			}
		}
	}

	private record Session(Dish dish, BlockPos pos, int startTick) {
	}

	private Sear() {
	}

	public static Quality judge(int perfects, int goods, int misses) {
		if (misses >= 3) {
			return Quality.CHARRED;
		}
		if (misses == 2) {
			return Quality.BURNT;
		}
		if (misses == 0 && perfects >= ROUNDS - 1) {
			return Quality.PERFECT;
		}
		return Quality.GOOD;
	}

	public static boolean isSearing(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	static void start(ServerPlayer player, Dish dish, BlockPos pos) {
		SESSIONS.put(player.getUUID(), new Session(dish, pos.immutable(), player.level().getServer().getTickCount()));
		player.level().playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
		ServerPlayNetworking.send(player, new SimUpdate(dish.ordinal(), SimUpdate.STATUS_STARTED, 0));
	}

	public static void finish(ServerPlayer player, int perfects, int goods, int misses) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || perfects < 0 || goods < 0 || misses < 0 || perfects + goods + misses != ROUNDS) {
			return;
		}
		int elapsed = player.level().getServer().getTickCount() - session.startTick;
		if (elapsed < ROUNDS * MIN_TICKS_PER_ROUND) {
			return;
		}
		SESSIONS.remove(player.getUUID());
		award(player, session, judge(perfects, goods, misses));
	}

	/** Walking away from a blast furnace with meat in it: charred. */
	public static void abandon(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session != null) {
			award(player, session, Quality.CHARRED);
		}
	}

	private static void award(ServerPlayer player, Session session, Quality quality) {
		Kitchen.award(player, session.dish, session.pos, quality);
		ServerPlayNetworking.send(player, new SimUpdate(session.dish.ordinal(), SimUpdate.STATUS_FINISHED, quality.ordinal()));
	}
}
