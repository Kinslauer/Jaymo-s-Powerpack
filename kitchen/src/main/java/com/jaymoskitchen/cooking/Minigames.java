package com.jaymoskitchen.cooking;

import com.jaymoskitchen.net.Payloads.SimUpdate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The plain larder's three minigames (0.4.38). Like {@link Sear}, each runs on the client, which reports how it went;
 * both sides judge it with the same code here and the server checks the numbers add up and enough time passed.
 * <ul>
 * <li>{@link Dish.Method#CHOP}: the vegetables slide under the knife, faster after every cut; press when a mark is
 * under the blade. A mark that slips past uncut is a miss.</li>
 * <li>{@link Dish.Method#ROAST}: a spit over the fire with four sides; only the side facing down cooks, and the fire
 * surges and dies back. Turn it a quarter at a time and take it off when every side is golden.</li>
 * <li>{@link Dish.Method#STIR}: circle the mouse round the pot. Too slow and it sticks and scorches, too fast and it
 * slops over; now and then it boils up and wants stirring faster.</li>
 * </ul>
 */
public final class Minigames {
	// --- chop ---
	public static final int CUTS = 8;
	/** How far apart the marks are, in vegetable lengths (the whole run is CUTS marks long). */
	public static final float MARK_GAP = 1.0F / CUTS;
	/** Slide speed (vegetable lengths per tick) at the first cut, and the extra after each cut. */
	public static final float CHOP_SPEED = 0.0045F, CHOP_SPEED_STEP = 0.0007F;
	/** How far off a mark a cut can land: perfect, then good (else ragged). */
	public static final float CHOP_PERFECT = 0.012F, CHOP_GOOD = 0.03F;
	private static final int MIN_TICKS_PER_CUT = 6;

	// --- roast ---
	public static final int SIDES = 4;
	/** Ticks the turning animation takes; the spit can't be turned again until it's done. */
	public static final int TURN_TICKS = 8;
	/** The fire's strength re-rolls between these every 30-80 ticks. */
	public static final float ROAST_HEAT_MIN = 0.7F, ROAST_HEAT_MAX = 1.4F;
	public static final int HEAT_GAP_MIN = 30, HEAT_GAP_MAX = 80;
	/** Doneness bands: golden from GOLDEN to BURNT_AT; under RAW_BELOW it's undercooked; past END it ends itself. */
	public static final float GOLDEN = 0.8F, BURNT_AT = 1.15F, RAW_BELOW = 0.6F, ROAST_END = 1.6F;
	/** The sides either side of the one in the fire cook this much as fast. */
	public static final float ROAST_SIDE_SPILL = 0.15F;

	// --- stir ---
	/** Ticks of stirring in the band to finish, and the time limit. */
	public static final int STIR_TICKS = 240, STIR_LIMIT = 900;
	/** The band of stirring speed (radians a tick round the pot), and the band while it's boiling up. */
	public static final float STIR_MIN = 0.12F, STIR_MAX = 0.36F, BOIL_MIN = 0.26F, BOIL_MAX = 0.55F;
	/** How long a boil-up lasts and how long between them. */
	public static final int BOIL_TICKS = 40, BOIL_GAP_MIN = 70, BOIL_GAP_MAX = 140;
	/** Sticking past this many ticks chars the pot (it ends there). */
	public static final int STUCK_CHARRED = 120;

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private record Session(Dish dish, BlockPos pos, int startTick) {
	}

	private Minigames() {
	}

	public static boolean handles(Dish dish) {
		return dish.method() == Dish.Method.CHOP || dish.method() == Dish.Method.ROAST || dish.method() == Dish.Method.STIR;
	}

	/** Chopping: rounds are cuts; 2+ ragged cuts burn it (a mangled mess), 4+ char it. */
	public static Quality judgeChop(int perfects, int goods, int misses) {
		if (misses >= 4) {
			return Quality.CHARRED;
		}
		if (misses >= 2) {
			return Quality.BURNT;
		}
		if (misses == 0 && perfects >= CUTS - 2) {
			return Quality.PERFECT;
		}
		return Quality.GOOD;
	}

	/** Roasting: by each side's doneness (1.0 = just cooked through). */
	public static Quality judgeRoast(float[] done) {
		int burnt = 0;
		boolean raw = false;
		boolean golden = true;
		for (float side : done) {
			burnt += side > BURNT_AT ? 1 : 0;
			raw |= side < RAW_BELOW;
			golden &= side >= GOLDEN && side <= BURNT_AT;
		}
		if (burnt >= 2) {
			return Quality.CHARRED;
		}
		if (burnt == 1) {
			return Quality.BURNT;
		}
		if (raw) {
			return Quality.UNDERCOOKED;
		}
		return golden ? Quality.PERFECT : Quality.GOOD;
	}

	/** Stirring: ticks it stuck (too slow), ticks it slopped over (too fast), and whether it was stirred through. */
	public static Quality judgeStir(int stuck, int spilled, boolean done) {
		if (stuck >= STUCK_CHARRED) {
			return Quality.CHARRED;
		}
		if (!done) {
			return Quality.UNDERCOOKED;
		}
		if (stuck >= 50 || spilled >= 70) {
			return Quality.BURNT;
		}
		if (stuck <= 15 && spilled <= 20) {
			return Quality.PERFECT;
		}
		return Quality.GOOD;
	}

	/** The dishes whose sessions here are running at {@code pos} in {@code level} (for the oven's cookware locks). */
	static void dishesAt(ServerLevel level, BlockPos pos, List<Dish> out) {
		for (Map.Entry<UUID, Session> entry : SESSIONS.entrySet()) {
			if (entry.getValue().pos().equals(pos)) {
				ServerPlayer cook = level.getServer().getPlayerList().getPlayer(entry.getKey());
				if (cook != null && cook.level() == level) {
					out.add(entry.getValue().dish());
				}
			}
		}
	}

	public static boolean isPlaying(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	static void start(ServerPlayer player, Dish dish, BlockPos pos) {
		SESSIONS.put(player.getUUID(), new Session(dish, pos.immutable(), player.level().getServer().getTickCount()));
		var sound = switch (dish.method()) {
			case ROAST -> SoundEvents.CAMPFIRE_CRACKLE;
			case STIR -> SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT;
			default -> SoundEvents.WOOD_HIT;
		};
		player.level().playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
		ServerPlayNetworking.send(player, new SimUpdate(dish.ordinal(), SimUpdate.STATUS_STARTED, 0));
	}

	/**
	 * The client's report. Chop: perfects, goods, misses. Roast: each side's doneness in thousandths. Stir: ticks stuck,
	 * ticks spilled, 1 if stirred through.
	 */
	public static void finish(ServerPlayer player, int a, int b, int c, int d) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || a < 0 || b < 0 || c < 0 || d < 0) {
			return;
		}
		int elapsed = player.level().getServer().getTickCount() - session.startTick;
		Quality quality;
		switch (session.dish.method()) {
			case CHOP -> {
				if (a + b + c != CUTS || elapsed < CUTS * MIN_TICKS_PER_CUT) {
					return;
				}
				quality = judgeChop(a, b, c);
			}
			case ROAST -> {
				float[] done = {a / 1000.0F, b / 1000.0F, c / 1000.0F, d / 1000.0F};
				// the hottest fire can't have cooked that much any quicker
				float total = 0.0F;
				for (float side : done) {
					total += Math.min(side, ROAST_END);
				}
				if (elapsed < total * session.dish.grillTicks() / (ROAST_HEAT_MAX * (1.0F + 2.0F * ROAST_SIDE_SPILL)) * 0.9F) {
					return;
				}
				quality = judgeRoast(done);
			}
			case STIR -> {
				boolean done = c == 1;
				if (elapsed < (done ? STIR_TICKS : Math.min(a, STUCK_CHARRED))) {
					return;
				}
				quality = judgeStir(a, b, done);
			}
			default -> {
				return;
			}
		}
		SESSIONS.remove(player.getUUID());
		award(player, session, quality);
	}

	/** Walking away mid-cook: the pot or spit is left on the fire. Chopping left half-done is a mess. */
	public static void abandon(ServerPlayer player) {
		Session session = SESSIONS.remove(player.getUUID());
		if (session != null) {
			award(player, session, session.dish.method() == Dish.Method.CHOP ? Quality.BURNT : Quality.CHARRED);
		}
	}

	private static void award(ServerPlayer player, Session session, Quality quality) {
		Kitchen.award(player, session.dish, session.pos, quality);
		ServerPlayNetworking.send(player, new SimUpdate(session.dish.ordinal(), SimUpdate.STATUS_FINISHED, quality.ordinal()));
	}
}
