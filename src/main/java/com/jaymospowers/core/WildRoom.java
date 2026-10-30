package com.jaymospowers.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.83) A small walled room built on open ground in the wild, for the starts that wake you in somebody's workshop
 * (the alchemist's lab, the Mad Tinkerer's bench). {@link #find} looks for flat, dry ground; {@link #shell} raises the
 * walls, floor, roof and a doorway big enough for a golem; the caller then furnishes it with {@link #set}.
 * Coordinates are offsets from the room's corner {@code origin}: x across, z deep (the doorway is on the far z side), y up
 * from the first air block over the floor.
 */
public final class WildRoom {
	/** Spots tried for a site, and the most the ground may rise or fall under the room (blocks). */
	static final int TRIES = 28, MAX_SLOPE = 3;
	/** How far the foundation reaches down at most (blocks), and how much sky is cleared over the roof. */
	static final int FOUNDATION = 8, CLEAR_ABOVE = 4;

	/** What a room is made of: its walls, the worn stones among them, the floor, the roof and what it stands on. */
	public record Style(BlockState wall, BlockState worn, BlockState floor, BlockState roof, BlockState foundation) {
	}

	private WildRoom() {
	}

	/**
	 * Flat, dry open ground {@code min}-{@code max} blocks from {@code near} for a room {@code w} wide, {@code d} deep and
	 * {@code h} high; the corner of it (y: the first air layer over the ground), or null if none was found.
	 */
	public static @Nullable BlockPos find(ServerLevel level, BlockPos near, int min, int max, int w, int d, int h, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = min + random.nextInt(Math.max(1, max - min + 1));
			int x0 = near.getX() + Mth.floor(Math.cos(angle) * dist), z0 = near.getZ() + Mth.floor(Math.sin(angle) * dist);
			int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
			boolean dry = true;
			for (int sx = 0; sx <= 2 && dry; sx++) {
				for (int sz = 0; sz <= 2; sz++) {
					int x = x0 + (w - 1) * sx / 2, z = z0 + (d - 1) * sz / 2;
					level.getChunk(x >> 4, z >> 4);
					BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
					BlockState ground = level.getBlockState(top.below());
					if (!ground.getFluidState().isEmpty() || !level.getBlockState(top).getFluidState().isEmpty()) {
						dry = false;
						break;
					}
					lowest = Math.min(lowest, top.getY());
					highest = Math.max(highest, top.getY());
				}
			}
			if (!dry || highest - lowest > MAX_SLOPE || highest <= level.getMinY() + FOUNDATION + 2 || highest + h + CLEAR_ABOVE >= level.getMaxY()) {
				continue;
			}
			return new BlockPos(x0, highest, z0);
		}
		return null;
	}

	public static void set(ServerLevel level, BlockPos origin, int dx, int dy, int dz, BlockState state) {
		level.setBlock(origin.offset(dx, dy, dz), state, Block.UPDATE_CLIENTS);
	}

	/**
	 * Raises the room: a foundation under it down to solid ground, the floor (a layer under {@code origin}), walls {@code h}
	 * high round an empty inside, the roof over them, a doorway {@code doorW} wide at {@code doorX} in the wall on the far z
	 * side (three high), and clear air over the roof.
	 */
	public static void shell(ServerLevel level, BlockPos origin, int w, int d, int h, Style style, int doorX, int doorW, RandomSource random) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
				for (int y = -2; y >= -FOUNDATION; y--) {
					BlockPos at = origin.offset(x, y, z);
					BlockState state = level.getBlockState(at);
					if (!state.isAir() && state.getFluidState().isEmpty() && state.isSolidRender()) {
						break;
					}
					level.setBlock(at, style.foundation(), Block.UPDATE_CLIENTS);
				}
				set(level, origin, x, -1, z, style.floor());
				for (int y = 0; y < h; y++) {
					set(level, origin, x, y, z, edge ? (random.nextInt(4) == 0 ? style.worn() : style.wall()) : air);
				}
				set(level, origin, x, h, z, style.roof());
				for (int y = h + 1; y <= h + CLEAR_ABOVE; y++) {
					set(level, origin, x, y, z, air);
				}
			}
		}
		for (int x = doorX; x < doorX + doorW; x++) {
			for (int y = 0; y < 3; y++) {
				set(level, origin, x, y, d - 1, air);
			}
		}
	}
}
