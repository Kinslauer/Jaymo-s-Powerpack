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
 * The smoker's curing minigame. The heat drifts down on its own and gusts of wind knock it about; holding the
 * fan raises it. Time spent with the heat inside the band cures the meat; running hot scorches it.
 * Like the dough prep, the moment-to-moment simulation runs on the client (it's all about holding a key), the
 * client reports how it went, and both sides judge it with {@link #judge}.
 */
public final class Smoke {
	/** Heat band that cures: [BAND_MIN, BAND_MAX]. Above SCORCH the meat scorches. */
	public static final float BAND_MIN = 0.46F, BAND_MAX = 0.62F, SCORCH = 0.78F;
	/** Heat change per tick: natural cooling (each session picks a rate in this range), and fanning. */
	public static final float COOLING_MIN = 0.0035F, COOLING_MAX = 0.0065F, FANNING = 0.012F;
	/** Gusts: strength per tick, how long they blow, and the wait between them (all random within range). */
	public static final float GUST_MIN = 0.008F, GUST_MAX = 0.017F;
	public static final int GUST_TICKS_MIN = 8, GUST_TICKS_MAX = 22, GUST_GAP_MIN = 20, GUST_GAP_MAX = 70;
	/** Ticks in the band to finish curing, and the total time allowed. */
	public static final int CURE_TICKS = 220, TIME_LIMIT = 900;
	/** Scorch ticks that make it Burnt, and Charred (which also ends the cook early). */
	public static final int SCORCH_BURNT = 25, SCORCH_CHARRED = 60;
	/** At most this much scorching and cooling-off (after curing has begun) for a Perfect. */
	public static final int PERFECT_SCORCH = 5, PERFECT_COLD = 30;

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

	private Smoke() {
	}

	public static Quality judge(int scorchTicks, int coldTicks, boolean cured) {
		if (scorchTicks >= SCORCH_CHARRED) {
			return Quality.CHARRED;
		}
		if (scorchTicks >= SCORCH_BURNT) {
			return Quality.BURNT;
		}
		if (!cured) {
			return Quality.UNDERCOOKED;
		}
		return scorchTicks <= PERFECT_SCORCH && coldTicks <= PERFECT_COLD ? Quality.PERFECT : Quality.GOOD;
	}

	public static boolean isSmoking(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	static void start(ServerPlayer player, Dish dish, BlockPos pos) {
		SESSIONS.put(player.getUUID(), new Session(dish, pos.immutable(), player.level().getServer().getTickCount()));
		player.level().playSound(null, pos, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 1.0F, 1.0F);
		ServerPlayNetworking.send(player, new SimUpdate(dish.ordinal(), SimUpdate.STATUS_STARTED, 0));
	}

	public static void finish(ServerPlayer player, int scorchTicks, int coldTicks, boolean cured) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		Quality quality = judge(Math.max(0, scorchTicks), Math.max(0, coldTicks), cured);
		int elapsed = player.level().getServer().getTickCount() - session.startTick;
		if (quality == Quality.PERFECT || quality == Quality.GOOD) {
			// curing can't have taken less than the time in the band
			if (elapsed < CURE_TICKS - 10) {
				return;
			}
		}
		SESSIONS.remove(player.getUUID());
		award(player, session, quality);
	}

	/** Walking away leaves it in the smoke: it comes out burnt. */
	public static void abandon(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session != null) {
			award(player, session, Quality.BURNT);
		}
	}

	private static void award(ServerPlayer player, Session session, Quality quality) {
		Kitchen.award(player, session.dish, session.pos, quality);
		ServerPlayNetworking.send(player, new SimUpdate(session.dish.ordinal(), SimUpdate.STATUS_FINISHED, quality.ordinal()));
	}
}
