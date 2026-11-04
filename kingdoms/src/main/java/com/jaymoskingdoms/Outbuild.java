package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Where a growing camp, cove or hold puts its next outbuilding: on level, dry, clear, unclaimed ground round its
 * centre, a turn of the golden angle further round each time and a ring further out every few ({@link #ring}). Shared by
 * {@link OgreGrowth}, {@link PirateCoves} and {@link IllagerCamps}, which each build their own kind of thing there.
 */
final class Outbuild {
	/** The turn between one outbuilding and the next (the golden angle, radians). */
	static final double GOLDEN = 2.399963229728653;

	private Outbuild() {
	}

	/** The ring (blocks from the centre) outbuilding number {@code n} stands on: {@code start}, {@code step} further every {@code per}, no further than {@code most}. */
	static int ring(int n, int start, int step, int per, int most) {
		return Math.min(most, start + n / per * step);
	}

	/**
	 * The ground for a footprint of {@code half} either side of the column {@code ring} blocks from {@code centre} at
	 * {@code angle}: loaded, dry, not on a tree, within {@code spread} of level across the footprint, within
	 * {@code floorSpread} of {@code refY}, and no other settlement's: land claimed by faction {@code selfId} (the builder's own;
	 * -1 for none) is its own to build on (0.7.490). Its floor (the first free block above the ground), or null.
	 */
	static @Nullable BlockPos ground(ServerLevel level, BlockPos centre, double angle, int ring, int half, int refY, int spread, int floorSpread, int selfId) {
		BlockPos column = centre.offset((int)Math.round(Math.cos(angle) * ring), 0, (int)Math.round(Math.sin(angle) * ring));
		for (int dx = -half - 1; dx <= half + 1; dx += half + 1) {
			for (int dz = -half - 1; dz <= half + 1; dz += half + 1) {
				if (!level.isLoaded(column.offset(dx, 0, dz))) {
					return null;
				}
			}
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!level.getFluidState(ground.below()).isEmpty() || !level.getFluidState(ground).isEmpty() || Math.abs(ground.getY() - refY) > floorSpread) {
			return null;
		}
		BlockState below = level.getBlockState(ground.below());
		if (below.is(BlockTags.LOGS) || below.is(BlockTags.LEAVES) || !below.isSolid()) {
			return null;
		}
		int low = ground.getY(), high = ground.getY();
		for (int dx = -half; dx <= half; dx += half) {
			for (int dz = -half; dz <= half; dz += half) {
				int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.offset(dx, 0, dz)).getY();
				low = Math.min(low, y);
				high = Math.max(high, y);
			}
		}
		if (high - low > spread) {
			return null;
		}
		Faction owner = Banners.owner(level.getServer(), ChunkPos.containing(ground));
		if (owner != null && owner.id() != selfId) {
			return null;
		}
		return ground;
	}

	/** A footprint of {@code half} either side of {@code floor}, {@code height} high, holds nothing but air and plants. */
	static boolean clear(ServerLevel level, BlockPos floor, int half, int height) {
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				for (int y = 0; y < height; y++) {
					BlockState there = level.getBlockState(floor.offset(dx, y, dz));
					if (!there.isAir() && !there.canBeReplaced()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * The first of a few turns ({@code skews}, each a little further round) of {@code angle} whose ground is level and whose
	 * footprint of {@code half} by {@code height} is clear, or null.
	 */
	static @Nullable BlockPos find(ServerLevel level, BlockPos centre, double angle, int skews, int ring, int half, int height, int refY, int spread, int floorSpread, int selfId) {
		for (int skew = 0; skew < skews; skew++) {
			BlockPos at = ground(level, centre, angle + skew * 0.35, ring, half, refY, spread, floorSpread, selfId);
			if (at != null && clear(level, at, half, height)) {
				return at;
			}
		}
		return null;
	}

	static void set(ServerLevel level, BlockPos at, BlockState state) {
		level.setBlock(at, state, Block.UPDATE_ALL);
	}
}
