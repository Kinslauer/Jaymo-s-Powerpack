package com.jaymoskingdoms.seas;

import net.minecraft.core.BlockPos;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Sea life lives out at sea (Andy's ocean pick-list: "most sea life spawn further out"): fish, squid, dolphins, turtles'
 * fellows and whatever else the sea spawns on its own come up thin along the coast and thick far out, so a walk on the
 * shore is quiet and a voyage is where the life is. Rivers, lakes and caves are left as they were; only the sea's own
 * spawns ({@link #keep}, asked by {@code SeaLifeSpawnMixin}) are thinned, by how far out they'd be ({@link Offshore#far}).
 * The sea's spawn limits stay the same, so what the shore doesn't take goes further out.
 */
public final class SeaLife {
	/** The share of the sea's spawns kept right at the coast; out past {@link Offshore#OPEN}, all of them. */
	static final float COAST_KEEP = 0.15F;

	/**
	 * Open sea: this far out ({@link Offshore#far}) or more. Andy (2026-11-19, crossing an ocean: "only saw dolphins and
	 * abandoned ships") wanted the sea full of life, after (2026-11-07) "still too many mobs spawning at once in the water":
	 * so the low water caps ({@code MonsterCapMixin}) and small groups ({@code WaterClusterMixin}) hold along coasts, rivers
	 * and lakes, and open sea chunks get their own, higher ones.
	 */
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

	/** Whether a natural spawn of {@code mob} at its place goes ahead. */
	public static boolean keep(ServerLevel level, Mob mob) {
		MobCategory category = mob.getType().getCategory();
		if (category != MobCategory.WATER_CREATURE && category != MobCategory.WATER_AMBIENT || level.dimension() != Level.OVERWORLD) {
			return true;
		}
		BlockPos pos = mob.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_BEACH)) {
			return true;
		}
		float keep = Mth.lerp(Offshore.far(level, pos.getX(), pos.getZ()), COAST_KEEP, 1.0F);
		return keep >= 1.0F || level.getRandom().nextFloat() < keep;
	}
}
