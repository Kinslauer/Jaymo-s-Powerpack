package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * The stealth kill (Jaymo's Powers' {@code Finishers}: sneak + hold attack behind someone on their feet who never saw you,
 * a weapon in hand) is everyone's; the Rogue's Arts on Stealth's fourth page make it better. Powers reads Quick Work (a
 * shorter hold), Silent Snap (bare hands) and Bigger Game (bigger foes) by key; what's left is here: each kill counts as an
 * assassination for Stealth experience ({@link Skills#assassination}), and with Into the Dark the one who did it is seen
 * far less for a while after ({@link #sight}, from {@link Senses}), long enough to slip away or reach the next.
 */
public final class StealthKills {
	/** The tag Powers puts on the killer for each stealth kill ({@code Finishers.STEALTH_KILL_TAG}); taken off as it's read. */
	static final String TAG = "jaymospowers_stealth_kill";
	/** Into the Dark: how long you're hard to see after a stealth kill (ticks), and how far mobs see you meanwhile (a share). */
	static final long DARK_TICKS = 160L;
	static final double DARK_SIGHT = 0.35;

	/** Players in the dark after a kill, and until when (game time). */
	private static final Map<UUID, Long> DARK = new HashMap<>();

	private StealthKills() {
	}

	/** The server stopped (from {@link Skills}' own). */
	static void clear() {
		DARK.clear();
	}

	/** {@code player} made a stealth kill just now. */
	static void killed(ServerPlayer player) {
		Skills.assassination(player);
		if (Skills.rank(player, Skills.Perk.INTO_THE_DARK) > 0) {
			DARK.put(player.getUUID(), player.level().getGameTime() + DARK_TICKS);
		}
	}

	/** How far mobs see {@code player} for Into the Dark: {@link #DARK_SIGHT} after a stealth kill, else all of it. */
	static double sight(ServerPlayer player) {
		Long until = DARK.get(player.getUUID());
		if (until == null) {
			return 1.0;
		}
		if (player.level().getGameTime() >= until) {
			DARK.remove(player.getUUID());
			return 1.0;
		}
		return DARK_SIGHT;
	}
}
