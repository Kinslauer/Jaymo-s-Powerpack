package com.jaymospowers.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * Dry, gentle ground far off, picked without making a single chunk, so a player can be put a long way from where they fell
 * and only that one spot is loaded to be sure ({@link com.jaymospowers.core.wounds.Mortality}'s wakes). Candidates are random
 * columns in a ring round a centre, scored from the generator alone (the ground height it would make, the biome at that
 * height, the lie of the land round it), cheapest test first, stopping at the first {@link #WANT} that pass; the gentlest of
 * them that stands clear of a village is the answer. Works in the Overworld's own band of a One Dimension world, as
 * {@link Scout} does.
 */
public final class FarLand {
	private FarLand() {
	}

	// ---- tuning ----
	/** Columns looked at at most (each a height read, and a biome read for the ones that stand above the sea). */
	static final int TRIES = 48;
	/** Candidates that pass before the search stops. */
	static final int WANT = 3;
	/** How far (blocks) from a candidate the lie of the land is read, and the steepest it may be (height difference to any of the four). */
	static final int SLOPE_STEP = 6, MAX_SLOPE = 5;
	/** No village point of interest this near (blocks) the one picked; only the finalists are asked. */
	static final int TOWN_CLEAR = 48;

	/**
	 * A column {@code min} to {@code max} blocks from {@code centre} on dry, gentle land (y is the ground the generator would
	 * make, in the level's heights), or null: none of {@link #TRIES} columns qualified (a sea, a steep country, a world border
	 * in the way). The chunk is not made or checked: the caller loads that one spot and tests it.
	 */
	public static @Nullable BlockPos pick(ServerLevel level, BlockPos centre, int min, int max, RandomSource random) {
		return com.jaymospowers.compat.Stacked.searchingOverworld(level, () -> pickHere(level, centre, min, max, random));
	}

	private record Spot(BlockPos at, int slope) {
	}

	private static @Nullable BlockPos pickHere(ServerLevel level, BlockPos centre, int min, int max, RandomSource random) {
		com.jaymospowers.compat.Stacked.Ground ground = com.jaymospowers.compat.Stacked.overworldGround(level);
		ChunkGenerator generator = ground.generator();
		RandomState state = ground.randomState();
		List<Spot> passed = new ArrayList<>();
		for (int i = 0; i < TRIES && passed.size() < WANT; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = min + random.nextDouble() * (max - min);
			int x = (int)Math.floor(centre.getX() + Math.cos(angle) * r);
			int z = (int)Math.floor(centre.getZ() + Math.sin(angle) * r);
			if (!level.getWorldBorder().isWithinBounds(x, z)) {
				continue;
			}
			int y = height(generator, ground, state, x, z);
			// (the water's surface counts as ground here: at or under the sea is a shore or a sea floor)
			if (y <= generator.getSeaLevel()) {
				continue;
			}
			Holder<Biome> biome = level.getUncachedNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(y + ground.shift()),
				net.minecraft.core.QuartPos.fromBlock(z));
			if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
				continue;
			}
			int slope = 0;
			for (int[] d : new int[][] {{SLOPE_STEP, 0}, {-SLOPE_STEP, 0}, {0, SLOPE_STEP}, {0, -SLOPE_STEP}}) {
				slope = Math.max(slope, Math.abs(height(generator, ground, state, x + d[0], z + d[1]) - y));
			}
			if (slope <= MAX_SLOPE) {
				passed.add(new Spot(new BlockPos(x, y + ground.shift(), z), slope));
			}
		}
		passed.sort(Comparator.comparingInt(Spot::slope));
		for (Spot spot : passed) {
			// (a village's points of interest, read from the world's records: no chunk is made for it)
			if (level.getPoiManager().findClosest(h -> h.is(PoiTypeTags.VILLAGE), spot.at(), TOWN_CLEAR, PoiManager.Occupancy.ANY).isEmpty()) {
				return spot.at();
			}
		}
		return null;
	}

	private static int height(ChunkGenerator generator, com.jaymospowers.compat.Stacked.Ground ground, RandomState state, int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ground.heights(), state);
	}
}
