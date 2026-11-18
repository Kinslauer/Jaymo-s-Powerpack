package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What the new classes spend and win back by resting: a cleric's turnings, a druid's shapes, a monk's ki, a warlock's pact slots
 * (back after a short rest, {@link CatchBreath}'s minute by the fire) and a paladin's smites and healing pool (back after a
 * night's sleep). Every pool is a count of what has been spent so far; the class decides how large the pool is.
 */
public final class Rests {
	/** Pools that come back after a short rest (a long rest brings back all of them). */
	public static final String CHANNEL = "channel", SHAPE = "shape", KI = "ki", PACT = "pact", SMITE = "smite", HANDS = "hands";
	private static final java.util.Set<String> SHORT = java.util.Set.of(CHANNEL, SHAPE, KI, PACT, Workshop.QUICK_POOL,
		// (class trees) the once-a-rest extras: Wild Heart, Intercession, Borrowed Time
		FaithExtras.HEART_POOL, FaithExtras.INTER_POOL, ArcaneExtras.TIME_POOL);

	/** Pool → how much is spent. Saved, kept through death. */
	public static final AttachmentType<Map<String, Integer>> SPENT = AttachmentRegistry.create(JaymosKingdoms.id("rest_spent"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private Rests() {
	}

	static void register() {
	}

	/** How much of {@code pool} {@code player} has spent since it last came back. */
	public static int spent(Player player, String pool) {
		return player.getAttachedOrElse(SPENT, Map.<String, Integer>of()).getOrDefault(pool, 0);
	}

	/** How much of a {@code max} pool is left. */
	public static int left(Player player, String pool, int max) {
		return Math.max(0, max - spent(player, pool));
	}

	/** Spends {@code n} of a {@code max} pool; false (nothing spent) if there isn't that much left. Creative players spend nothing. */
	public static boolean spend(ServerPlayer player, String pool, int n, int max) {
		if (player.isCreative()) {
			return true;
		}
		if (left(player, pool, max) < n) {
			return false;
		}
		Map<String, Integer> now = new HashMap<>(player.getAttachedOrElse(SPENT, Map.<String, Integer>of()));
		now.merge(pool, n, Integer::sum);
		player.setAttached(SPENT, now);
		return true;
	}

	/** {@code n} of {@code pool} comes back (class trees: a Monk's Still Mind, a Paladin's Vigil). */
	public static void regain(ServerPlayer player, String pool, int n) {
		Map<String, Integer> now = new HashMap<>(player.getAttachedOrElse(SPENT, Map.<String, Integer>of()));
		int left = now.getOrDefault(pool, 0) - n;
		if (left > 0) {
			now.put(pool, left);
		} else {
			now.remove(pool);
		}
		player.setAttached(SPENT, now);
	}

	/** A short rest: the pools that come back. */
	public static void shortRest(ServerPlayer player) {
		Map<String, Integer> now = new HashMap<>(player.getAttachedOrElse(SPENT, Map.<String, Integer>of()));
		if (now.keySet().removeAll(SHORT)) {
			player.setAttached(SPENT, now);
			Warlocks.rested(player);
		}
		Clerics.rested(player);
		ArcaneExtras.rested(player);
		FeatsTrade.rested(player);
		Paths.rested(player);
	}

	/** A long rest (a night's sleep): everything comes back. */
	public static void longRest(ServerPlayer player) {
		if (!player.getAttachedOrElse(SPENT, Map.<String, Integer>of()).isEmpty()) {
			player.removeAttached(SPENT);
		}
		Paladins.rested(player);
		// (class trees) a Druid's Green Thumb, a Warlock's Hand of the Patron
		FaithExtras.woke(player);
		ArcaneExtras.rested(player);
	}
}
