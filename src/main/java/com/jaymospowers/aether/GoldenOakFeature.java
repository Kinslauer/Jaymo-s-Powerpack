package com.jaymospowers.aether;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A golden oak: a straight golden-barked trunk {@link #TRUNK} tall with short branches thrown out of it part way up
 * and a round, ragged crown of golden leaves. The rare tree of the Aether (a hundredth of tree rolls, see
 * {@code worldgen/feature/trees_verdant_woods.json}); the golden oak sapling grows the same tree.
 *
 * <p>Needs soil under its foot and a clear trunk; leaves only take the room they find.
 */
public record GoldenOakFeature() implements Feature {
	public static final MapCodec<GoldenOakFeature> CODEC = MapCodec.unit(GoldenOakFeature::new);

	/** Trunk height in logs. */
	public static final int TRUNK = 10;
	/** Branches grow out of these trunk heights (from the foot, inclusive), two rows in three, 1 to 2 logs long. */
	public static final int BRANCH_FROM = 5, BRANCH_TO = 8, BRANCH_MAX_LENGTH = 2;
	/** The crown: a sphere of this radius, in rows from CROWN_ROWS_BELOW under its centre to the same above it. */
	public static final int CROWN_RADIUS = 4, CROWN_ROWS_BELOW = 3;
	/** The crown's centre, in blocks above the foot. */
	public static final int CROWN_CENTRE = TRUNK - 3;
	/** A leaf stays when its squared distance from the centre is at most this plus a random 0 to CROWN_RAGGED (ragged edge). */
	public static final int CROWN_SQUARED = 12, CROWN_RAGGED = 4;

	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	@Override
	public MapCodec<GoldenOakFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		if (!AetherFeatures.isSoil(level.getBlockState(origin.below())) || origin.getY() + CROWN_CENTRE + CROWN_RADIUS >= level.getMaxY()) {
			return false;
		}
		for (int y = 0; y < TRUNK; y++) {
			if (!AetherFeatures.isFree(level, origin.above(y))) {
				return false;
			}
		}
		BlockState log = AetherBlocks.GOLDEN_OAK_LOG.defaultBlockState();
		Set<BlockPos> logs = new HashSet<>();
		AetherFeatures.put(level, origin.below(), AetherBlocks.AETHER_DIRT.defaultBlockState());
		for (int y = 0; y < TRUNK; y++) {
			BlockPos at = origin.above(y);
			AetherFeatures.put(level, at, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			logs.add(at);
		}
		for (int y = BRANCH_FROM; y <= BRANCH_TO; y++) {
			if (random.nextInt(3) == 0) {
				continue;
			}
			Direction way = SIDES[random.nextInt(SIDES.length)];
			int length = 1 + random.nextInt(BRANCH_MAX_LENGTH);
			for (int i = 1; i <= length; i++) {
				BlockPos at = origin.above(y).relative(way, i);
				if (!AetherFeatures.isFree(level, at)) {
					break;
				}
				AetherFeatures.put(level, at, log.setValue(RotatedPillarBlock.AXIS, way.getAxis()));
				logs.add(at);
			}
		}
		Map<BlockPos, BlockState> leaves = new HashMap<>();
		BlockState leaf = AetherBlocks.GOLDEN_OAK_LEAVES.defaultBlockState();
		for (int dy = -CROWN_ROWS_BELOW; dy <= CROWN_ROWS_BELOW; dy++) {
			for (int dx = -CROWN_RADIUS; dx <= CROWN_RADIUS; dx++) {
				for (int dz = -CROWN_RADIUS; dz <= CROWN_RADIUS; dz++) {
					if (dx * dx + dy * dy + dz * dz <= CROWN_SQUARED + random.nextInt(CROWN_RAGGED + 1)) {
						leaves.put(origin.offset(dx, CROWN_CENTRE + dy, dz), leaf);
					}
				}
			}
		}
		AetherFeatures.placeLeaves(level, logs, leaves);
		return true;
	}
}
