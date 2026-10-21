package com.jaymospowers.aether;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * A crystal island: a small floating islet of holystone (aether grass on top, a layer of dirt, a rounded underside)
 * with one crystal tree on it. The tree is a straight skyroot trunk with a ring of four logs out of its sides at two
 * heights and a diamond-shaped crown of crystal leaves, one in five of them crystal fruit leaves.
 *
 * <p>Only made in open sky: if anything is in the way (another island, a cloud) it stands down.
 */
public record CrystalIslandFeature() implements Feature {
	public static final MapCodec<CrystalIslandFeature> CODEC = MapCodec.unit(CrystalIslandFeature::new);

	/** The islet's widest radius is MIN_RADIUS to MAX_RADIUS blocks, and its underside hangs up to DEPTH below the top. */
	public static final int MIN_RADIUS = 3, MAX_RADIUS = 5, DEPTH = 3;
	/** How ragged the rim is (fraction of the radius, squared). */
	private static final double RIM_WOBBLE = 0.3;
	/** The tree: trunk height, the trunk heights the four side logs come out at, and the crown's radius row by row from CROWN_FROM up. */
	public static final int TRUNK = 7, CROWN_FROM = 3;
	private static final int[] SIDE_LOGS = {2, 5};
	private static final int[] CROWN_RADII = {2, 3, 3, 3, 2, 1};
	/** One crown leaf in this many is a fruit leaf. */
	public static final int FRUIT_ONE_IN = 5;

	@Override
	public MapCodec<CrystalIslandFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		if (origin.getY() - DEPTH <= level.getMinY() || origin.getY() + TRUNK + CROWN_RADII.length >= level.getMaxY()) {
			return false;
		}
		int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
		// the islet's blocks, top layer first: the deeper a column, the nearer its middle
		List<BlockPos> ground = new ArrayList<>();
		List<Integer> layer = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double d = (dx * dx + dz * dz) / (double)(radius * radius) + (random.nextDouble() - 0.5) * RIM_WOBBLE;
				if (d > 1.0 && !(dx == 0 && dz == 0)) {
					continue;
				}
				int depth = (int)Math.floor(DEPTH * (1.0 - Math.max(d, 0.0)));
				for (int k = 0; k <= depth; k++) {
					ground.add(origin.offset(dx, -k, dz));
					layer.add(k);
				}
			}
		}
		for (BlockPos at : ground) {
			if (!AetherFeatures.isAir(level, at)) {
				return false;
			}
		}
		for (int y = 1; y <= TRUNK; y++) {
			if (!AetherFeatures.isAir(level, origin.above(y))) {
				return false;
			}
		}
		for (int i = 0; i < ground.size(); i++) {
			int k = layer.get(i);
			BlockState state = k == 0 ? AetherBlocks.AETHER_GRASS_BLOCK.defaultBlockState()
				: k == 1 ? AetherBlocks.AETHER_DIRT.defaultBlockState() : AetherBlocks.HOLYSTONE.defaultBlockState();
			AetherFeatures.put(level, ground.get(i), state);
		}
		tree(level, random, origin.above());
		return true;
	}

	/** The crystal tree with its foot at {@code foot}. */
	private static void tree(WorldGenLevel level, RandomSource random, BlockPos foot) {
		BlockState log = AetherBlocks.SKYROOT_LOG.defaultBlockState();
		Set<BlockPos> logs = new HashSet<>();
		for (int y = 0; y < TRUNK; y++) {
			BlockPos at = foot.above(y);
			AetherFeatures.put(level, at, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			logs.add(at);
		}
		for (int y : SIDE_LOGS) {
			for (Direction way : Direction.Plane.HORIZONTAL) {
				BlockPos at = foot.above(y).relative(way);
				if (AetherFeatures.isFree(level, at)) {
					AetherFeatures.put(level, at, log.setValue(RotatedPillarBlock.AXIS, way.getAxis()));
					logs.add(at);
				}
			}
		}
		Map<BlockPos, BlockState> leaves = new HashMap<>();
		BlockState plain = AetherBlocks.CRYSTAL_LEAVES.defaultBlockState();
		BlockState fruit = AetherBlocks.CRYSTAL_FRUIT_LEAVES.defaultBlockState();
		for (int row = 0; row < CROWN_RADII.length; row++) {
			int reach = CROWN_RADII[row];
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					if (Math.abs(dx) + Math.abs(dz) <= reach) {
						leaves.put(foot.offset(dx, CROWN_FROM + row, dz), random.nextInt(FRUIT_ONE_IN) == 0 ? fruit : plain);
					}
				}
			}
		}
		AetherFeatures.placeLeaves(level, logs, leaves);
	}
}
