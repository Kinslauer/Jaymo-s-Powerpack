package com.jaymoskingdoms.seas;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * How far out to sea a place lies, read from the world's own continent noise (the same number that decided, when the
 * land was made, whether it would be sea, coast or land): so it costs no chunk loading, and it knows an island from a
 * cape before anyone has sailed there.
 */
public final class Offshore {
	/** Continentalness: below this is sea (the game's own line between ocean and coast), below this deep sea. */
	public static final float SEA = -0.19F, DEEP = -0.455F;
	/** Below this it is as far out as it gets (for {@link #far}). */
	public static final float OPEN = -0.6F;
	/** Above this the land is land, not shore (the game's inland line). */
	public static final float LAND = -0.11F;
	/** An island: land here, and sea all round at one of these distances (blocks), checked at this many points. */
	static final int[] ISLAND_RINGS = {96, 160, 256};
	static final int ISLAND_POINTS = 12;

	private Offshore() {
	}

	/** The continentalness at block (x, z) of {@code state}'s world (negative is sea). */
	public static float continentalness(RandomState state, int x, int z) {
		Climate.Sampler sampler = state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		return Climate.unquantizeCoord(sampler.sample(x >> 2, 0, z >> 2).continentalness());
	}

	public static float continentalness(Climate.Sampler sampler, int x, int z) {
		return Climate.unquantizeCoord(sampler.sample(x >> 2, 0, z >> 2).continentalness());
	}

	public static float continentalness(ServerLevel level, int x, int z) {
		return continentalness(level.getChunkSource().randomState(), x, z);
	}

	/** How far out (x, z) is, 0 (at the coast or ashore) to 1 (open ocean). */
	public static float far(ServerLevel level, int x, int z) {
		return far(continentalness(level, x, z));
	}

	/**
	 * Whether (x, z) is on an island: land (or its shore) here, and open sea all the way round at one of
	 * {@link #ISLAND_RINGS}' distances.
	 */
	public static boolean island(Climate.Sampler sampler, int x, int z) {
		if (Climate.unquantizeCoord(sampler.sample(x >> 2, 0, z >> 2).continentalness()) < SEA) {
			return false;
		}
		for (int r : ISLAND_RINGS) {
			boolean round = true;
			for (int i = 0; i < ISLAND_POINTS && round; i++) {
				double a = Math.PI * 2.0 * i / ISLAND_POINTS;
				int px = x + (int)Math.round(Math.cos(a) * r), pz = z + (int)Math.round(Math.sin(a) * r);
				round = Climate.unquantizeCoord(sampler.sample(px >> 2, 0, pz >> 2).continentalness()) < SEA;
			}
			if (round) {
				return true;
			}
		}
		return false;
	}

	public static boolean island(ServerLevel level, int x, int z) {
		return island(level.getChunkSource().randomState(), x, z);
	}

	public static boolean island(RandomState state, int x, int z) {
		return island(state.createClimateSampler(SamplerContext.EMPTY_UNCACHED), x, z);
	}

	/** How far out a continentalness {@code c} is, 0 (coast) to 1 (open ocean). */
	public static float far(float c) {
		return Mth.clamp((SEA - c) / (SEA - OPEN), 0.0F, 1.0F);
	}

	/** The highest continentalness round (x, z) at {@link #ISLAND_RINGS}' middle distance: the nearest the land comes. */
	public static float ringContinentalness(Climate.Sampler sampler, int x, int z) {
		int r = ISLAND_RINGS[1];
		float most = -2.0F;
		for (int i = 0; i < ISLAND_POINTS; i++) {
			double a = Math.PI * 2.0 * i / ISLAND_POINTS;
			int px = x + (int)Math.round(Math.cos(a) * r), pz = z + (int)Math.round(Math.sin(a) * r);
			most = Math.max(most, Climate.unquantizeCoord(sampler.sample(px >> 2, 0, pz >> 2).continentalness()));
		}
		return most;
	}
}
