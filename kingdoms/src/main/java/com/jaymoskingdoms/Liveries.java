package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Each realm's livery (Andy, 2026-11-22: on the life story's realm page five realms wore the same armour, and realms
 * should never match): the marks a realm's smiths and tailors put on everything they issue, so no two realms' guards
 * dress alike even when both can only afford the levy's leather and mail. A livery is one number standing for two
 * things: the trim its smiths work into the mail, leather, bronze and iron they issue (one of {@link Outfits}' trim
 * patterns, in the metal nearest the realm's colour), and which of the levy's cloth pieces its guards are given for
 * nothing ({@link #HOOD}, {@link #TASSETS}, {@link #CUISSES}).
 * <p>
 * No two realms of the same colour share one: the realms are taken in the order they were founded, and each takes its
 * own pick (from its id) or, if an older realm of its colour already has that, the next one free. Realms of different
 * colours already differ in their tabards and cloth. A fiefdom wears its overlord's colour but has a livery of its own,
 * so it is told from its overlord and from its overlord's other fiefdoms. Fallen realms keep theirs, so a livery never
 * moves to another realm while the first is remembered.
 */
public final class Liveries {
	/** The levy's cloth a livery gives (bits of {@link #cloth}): a hood in the realm's colour, leather tassets, padded cuisses. */
	static final int HOOD = 1, TASSETS = 2, CUISSES = 4, CLOTH = 8;

	private static String known = "";
	private static Map<Integer, Integer> liveries = Map.of();

	private Liveries() {
	}

	/** How many liveries there are: every trim pattern with every mix of the levy's cloth. */
	static int count() {
		return Outfits.PATTERN_COUNT * CLOTH;
	}

	/** {@code f}'s livery (0 to {@link #count}): its own among the world's realms. */
	static synchronized int of(MinecraftServer server, Faction f) {
		List<Faction> realms = new ArrayList<>();
		for (Faction r : Factions.all(server)) {
			if (r.type().isRealm()) {
				realms.add(r);
			}
		}
		realms.sort(Comparator.comparingInt(Faction::id));
		int[] ids = new int[realms.size()], colours = new int[realms.size()];
		StringBuilder key = new StringBuilder();
		for (int i = 0; i < realms.size(); i++) {
			ids[i] = realms.get(i).id();
			colours[i] = realms.get(i).styleColor(server).getId();
			key.append(ids[i]).append(':').append(colours[i]).append(';');
		}
		if (!known.contentEquals(key)) {
			liveries = assign(ids, colours, count());
			known = key.toString();
			check(ids, colours, liveries);
		}
		Integer own = liveries.get(f.id());
		return own != null ? own : pick(f.id(), count());
	}

	/** The trim pattern a livery's smiths work in (an index of {@link Outfits}' patterns). */
	static int pattern(int livery) {
		return livery % Outfits.PATTERN_COUNT;
	}

	/** The levy's cloth a livery gives ({@link #HOOD}, {@link #TASSETS}, {@link #CUISSES} added together). */
	static int cloth(int livery) {
		return livery / Outfits.PATTERN_COUNT % CLOTH;
	}

	/**
	 * The liveries of realms {@code ids} (oldest first) of colours {@code colours}: each its own pick or, taken by an older
	 * realm of its colour, the next free one, stepping by one pattern and one cloth so the next differs in both.
	 */
	static Map<Integer, Integer> assign(int[] ids, int[] colours, int count) {
		Map<Integer, Integer> out = new HashMap<>();
		Set<Long> taken = new HashSet<>();
		int stride = gcd(Outfits.PATTERN_COUNT + 1, count) == 1 ? Outfits.PATTERN_COUNT + 1 : 1;
		for (int i = 0; i < ids.length; i++) {
			int start = pick(ids[i], count), chosen = start;
			for (int step = 0; step < count; step++) {
				int at = (int)((start + (long)step * stride) % count);
				if (!taken.contains(colours[i] * (long)count + at)) {
					chosen = at;
					break;
				}
			}
			taken.add(colours[i] * (long)count + chosen);
			out.put(ids[i], chosen);
		}
		return out;
	}

	/**
	 * Two realms of one colour with one livery would dress alike: in the dev game that stops the game, so it's seen; in
	 * a real one it's logged (it takes more realms of one colour than there are liveries).
	 */
	static void check(int[] ids, int[] colours, Map<Integer, Integer> liveries) {
		Map<Long, Integer> seen = new HashMap<>();
		for (int i = 0; i < ids.length; i++) {
			long look = colours[i] * (long)count() + liveries.get(ids[i]);
			Integer other = seen.putIfAbsent(look, ids[i]);
			if (other != null) {
				String msg = "Realms " + other + " and " + ids[i] + " have the same colour and livery, so their guards dress alike";
				if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
					throw new IllegalStateException(msg);
				}
				JaymosKingdoms.LOGGER.warn(msg);
			}
		}
	}

	private static int pick(int id, int count) {
		long z = id * 0x9E3779B97F4A7C15L + 0x7F4A7C15F39CC060L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return (int)Math.floorMod(z ^ (z >>> 31), (long)count);
	}

	private static int gcd(int a, int b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
