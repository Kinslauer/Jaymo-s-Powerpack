package com.jaymospowers.power.zfighter;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.71) Where a tournament is fought. It used to be any spot {@code RING_OFFSET} from the seat at whatever height
 * the ground was: a hillside, a cliff's edge, a roof or the river, so fighters were set down inside the hill or above a
 * drop and fell out of the ring. Now the land round the seat is searched ({@link #DISTANCES} out, {@link #ANGLES} ways)
 * for the flattest stretch of plain ground (no water, lava, houses or roads in it, at most {@link #MAX_RISE} from its
 * lowest to its highest), and a floor is laid there: smooth stone, edged in stone bricks at the ring's line, the dips
 * under it filled and what grows over it (grass, trees, snow) cleared to {@link #CLEAR_HIGH} above.
 */
final class ZFighterArena {
	/** How far from the seat to look (blocks), and in how many directions at each. */
	static final int[] DISTANCES = {18, 24, 30, 36, 44};
	static final int ANGLES = 16;
	/** The floor's radius (the ring's, plus a block of standing room past its line). */
	static final int FLOOR = (int)ZFighterTournaments.RADIUS + 1;
	/** The most the ground under a floor may rise from its lowest to its highest point (blocks). */
	static final int MAX_RISE = 4;
	/** Room kept clear over the floor (blocks); higher up, only trees are cleared, to {@code CLEAR_HIGH}. */
	static final int HEADROOM = 5, CLEAR_HIGH = 10;

	private ZFighterArena() {
	}

	/** Lays the arena near {@code seat} and returns the spot fighters stand on at its middle, or null if none would do. */
	static @Nullable BlockPos build(ServerLevel level, BlockPos seat) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (int distance : DISTANCES) {
			for (int i = 0; i < ANGLES; i++) {
				double angle = i * Math.PI * 2.0 / ANGLES;
				int x = seat.getX() + (int)Math.round(Math.cos(angle) * distance);
				int z = seat.getZ() + (int)Math.round(Math.sin(angle) * distance);
				int[] heights = surface(level, x, z);
				if (heights == null) {
					continue;
				}
				int[] sorted = heights.clone();
				Arrays.sort(sorted);
				int rise = sorted[sorted.length - 1] - sorted[0];
				int floor = sorted[sorted.length / 2];
				if (rise > MAX_RISE || !clear(level, x, z, floor)) {
					continue;
				}
				// flattest first; nearer the seat breaks a tie
				double score = rise * 10.0 + distance * 0.1;
				if (score < bestScore) {
					bestScore = score;
					best = new BlockPos(x, floor, z);
				}
			}
		}
		if (best == null) {
			return null;
		}
		lay(level, best);
		return best.above();
	}

	/** The top of the ground in each column of the floor (y of the ground block), or null if any of it won't do. */
	private static int @Nullable [] surface(ServerLevel level, int cx, int cz) {
		int[] heights = new int[(FLOOR * 2 + 1) * (FLOOR * 2 + 1)];
		int n = 0;
		for (int dx = -FLOOR; dx <= FLOOR; dx++) {
			for (int dz = -FLOOR; dz <= FLOOR; dz++) {
				if (dx * dx + dz * dz > FLOOR * FLOOR) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
				BlockPos ground = new BlockPos(cx + dx, y, cz + dz);
				BlockState state = level.getBlockState(ground);
				// trees stand on ground: look under the trunk
				while (state.is(BlockTags.LOGS) && y > level.getMinY()) {
					y--;
					ground = ground.below();
					state = level.getBlockState(ground);
				}
				if (!state.getFluidState().isEmpty() || !natural(state)) {
					return null;
				}
				heights[n++] = y;
			}
		}
		return Arrays.copyOf(heights, n);
	}

	/** Plain ground, not anything built: dirt, grass, sand, stone, gravel, clay, terracotta, snow. */
	private static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SNOW)
			|| state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MOSS_BLOCK);
	}

	/** What may be cleared off the floor: air, plants, snow, and trees. */
	private static boolean clearable(BlockState state) {
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
			|| state.is(BlockTags.FLOWERS) || state.is(Blocks.SNOW);
	}

	/** Nothing built (or any water) in the room over a floor at {@code floor}; the ground above it is fine (it's cut). */
	private static boolean clear(ServerLevel level, int cx, int cz, int floor) {
		for (int dx = -FLOOR; dx <= FLOOR; dx++) {
			for (int dz = -FLOOR; dz <= FLOOR; dz++) {
				if (dx * dx + dz * dz > FLOOR * FLOOR) {
					continue;
				}
				for (int dy = 1; dy <= HEADROOM; dy++) {
					BlockState state = level.getBlockState(new BlockPos(cx + dx, floor + dy, cz + dz));
					if (!state.getFluidState().isEmpty() || !(clearable(state) || natural(state))) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** The floor at {@code center}: smooth stone, the ring's line in stone bricks, filled under, cleared over. */
	private static void lay(ServerLevel level, BlockPos center) {
		double line = ZFighterTournaments.RADIUS;
		for (int dx = -FLOOR; dx <= FLOOR; dx++) {
			for (int dz = -FLOOR; dz <= FLOOR; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > FLOOR) {
					continue;
				}
				BlockPos at = center.offset(dx, 0, dz);
				level.setBlock(at, Math.abs(r - line) < 0.6 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState(), 3);
				// a dip under it filled, down to the ground
				for (int dy = 1; dy <= MAX_RISE; dy++) {
					BlockPos below = at.below(dy);
					BlockState state = level.getBlockState(below);
					if (!clearable(state)) {
						break;
					}
					level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
				}
				// the room over it cleared: anything in the way to HEADROOM, then trees above that
				for (int dy = 1; dy <= CLEAR_HIGH; dy++) {
					BlockPos above = at.above(dy);
					BlockState state = level.getBlockState(above);
					if (state.isAir()) {
						continue;
					}
					if (dy <= HEADROOM || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
						level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}
}
