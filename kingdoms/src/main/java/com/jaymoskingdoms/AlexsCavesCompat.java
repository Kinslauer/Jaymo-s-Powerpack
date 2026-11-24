package com.jaymoskingdoms;

import com.jaymoskingdoms.seas.Mohknee;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Alex's Caves Continued (LGPL-3.0, shipped unchanged) hides six cave biomes under the Overworld. Andy (2026-11-24):
 * "the cave maps should lead to places like mohknee"; the cave biomes sit mostly under far lands like Mohknee, so its
 * maps send travellers there. Its biomes are laid out in round cells (a Voronoi grid, one biome per cell, about
 * {@code cave_biome_mean_separation} apart); {@code mixin/alexscaves/CaveBiomePlacementMixin} asks {@link #keeps} about
 * each cell before its biome is allowed in:
 * <ul>
 * <li>under Mohknee and the deep sea round it (out to {@link Mohknee#edge()}) every cell keeps its biome, so the
 * Abyssal Chasm lies under the crossing and the other five under the land and its islands, wherever Alex's own climate
 * rules allow them;</li>
 * <li>the home continent keeps none within {@link #HOME} blocks of the world's middle;</li>
 * <li>past that, one cell in {@link #FAR_KEEP} keeps its biome, so the other far reaches of the world have a few.</li>
 * </ul>
 * Its cave maps search a square that grows with {@code cave_map_search_attempts}; the default (128000, about 23000
 * blocks out) falls short of Mohknee's land, so while it is still the default it is raised to {@link #MAP_ATTEMPTS}
 * (about 35000 blocks) once the server is up. The search runs over ticks, as before. Only new chunks are affected.
 */
public final class AlexsCavesCompat {
	public static final String MOD_ID = "alexscaves";
	/** No cave biome on the home continent within this many blocks of the world's middle (Mohknee's reach aside). */
	public static final int HOME = 24000;
	/** Past {@link #HOME}, one cell in this many keeps its cave biome. */
	public static final int FAR_KEEP = 2;
	/** Alex's own default for cave_map_search_attempts, and what it becomes here. */
	public static final int MAP_ATTEMPTS_DEFAULT = 128000, MAP_ATTEMPTS = 300000;

	private AlexsCavesCompat() {
	}

	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return;
		}
		ServerLifecycleEvents.SERVER_STARTED.register(server -> longerMaps());
		BurningBeast.register();
	}

	/**
	 * Whether the cave biome cell whose middle is {@code centre} (quart coordinates, as Alex's gives it) keeps its biome
	 * in the Overworld. {@code biome} is the biome's place in Alex's list, mixed into the one-in-{@link #FAR_KEEP} pick.
	 */
	public static boolean keeps(Vec3 centre, int biome) {
		int x = QuartPos.toBlock(Mth.floor(centre.x)), z = QuartPos.toBlock(Mth.floor(centre.z));
		if (Mohknee.distance(x, z) < Mohknee.edge()) {
			return true;
		}
		if ((double)x * x + (double)z * z < (double)HOME * HOME) {
			return false;
		}
		long h = Mth.getSeed(Mth.floor(centre.x), biome, Mth.floor(centre.z));
		return Math.floorMod(h >>> 16, FAR_KEEP) == 0;
	}

	private static void longerMaps() {
		try {
			var attempts = com.github.alexmodguy.alexscaves.AlexsCaves.COMMON_CONFIG.caveMapSearchAttempts;
			if (attempts.get() == MAP_ATTEMPTS_DEFAULT) {
				attempts.set(MAP_ATTEMPTS);
			}
		} catch (RuntimeException | LinkageError e) {
			JaymosKingdoms.LOGGER.warn("Could not lengthen Alex's Caves' cave map search", e);
		}
	}
}
