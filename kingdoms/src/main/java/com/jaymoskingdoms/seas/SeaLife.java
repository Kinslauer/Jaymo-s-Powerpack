package com.jaymoskingdoms.seas;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Open sea, for the sea's spawn caps. Coasts, rivers and lakes spawn their water life as the game does (Andy 2026-11-26:
 * the shore's thinning and low water caps came out; the "too many in the water" of 2026-11-07 was one mob overspawning);
 * open sea chunks ask the open sea's caps ({@code MonsterCapMixin}, through {@code SeaCapsMixin}), with more room for
 * squid, dolphins, sharks and whales.
 */
public final class SeaLife {
	/** Open sea: this far out ({@link Offshore#far}) or more. Andy (2026-11-19, crossing an ocean: "only saw dolphins"). */
	public static final float OPEN_FAR = 0.4F;
	/** Whether each chunk is open sea, per world (it never changes); dropped when it grows past {@link #CACHE_MOST}. */
	private static final Map<ServerLevel, Long2BooleanOpenHashMap> OPEN = new WeakHashMap<>();
	private static final int CACHE_MOST = 65536;
	/** Set while the spawner asks a cap for open sea (the open caps apply), on the server thread only. */
	private static final ThreadLocal<Boolean> ASKING_OPEN = ThreadLocal.withInitial(() -> false);

	private SeaLife() {
	}

	/** Whether chunk {@code pos} of {@code level} is open sea (only ever in the Overworld). */
	public static boolean openSea(ServerLevel level, ChunkPos pos) {
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		Long2BooleanOpenHashMap known = OPEN.computeIfAbsent(level, l -> new Long2BooleanOpenHashMap());
		long key = pos.pack();
		if (known.containsKey(key)) {
			return known.get(key);
		}
		if (known.size() >= CACHE_MOST) {
			known.clear();
		}
		boolean open = Offshore.far(level, pos.getMiddleBlockX(), pos.getMiddleBlockZ()) >= OPEN_FAR;
		known.put(key, open);
		return open;
	}

	/** Whether the cap being asked for now is an open sea chunk's (see {@link #askingOpen(boolean)}). */
	public static boolean askingOpen() {
		return ASKING_OPEN.get();
	}

	/** Marks the caps asked for next as open sea's ({@code true}) or the coast's; set back to false after. */
	public static void askingOpen(boolean open) {
		ASKING_OPEN.set(open);
	}

	/** Whether {@code category} is one of the sea's (fish and water creatures). */
	public static boolean seaCategory(MobCategory category) {
		return category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT;
	}
}
