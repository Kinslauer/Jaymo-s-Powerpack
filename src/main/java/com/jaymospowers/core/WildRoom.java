package com.jaymospowers.core;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
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
	 * Flat, dry, untouched ground {@code min}-{@code max} blocks from {@code near} for a room {@code w} wide, {@code d} deep and
	 * {@code h} high; the corner of it (y: the first air layer over the ground), or null if none was found. Every column of the
	 * footprint is checked (and the air over it): only natural terrain and plants, no water, logs, buildings or structures,
	 * inside the world border. Chunks that are already generated are tried first.
	 */
	public static @Nullable BlockPos find(ServerLevel level, BlockPos near, int min, int max, int w, int d, int h, RandomSource random) {
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < TRIES; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				int dist = min + random.nextInt(Math.max(1, max - min + 1));
				int x0 = near.getX() + Mth.floor(Math.cos(angle) * dist), z0 = near.getZ() + Mth.floor(Math.sin(angle) * dist);
				if (!level.getWorldBorder().isWithinBounds(new BlockPos(x0 - 2, 0, z0 - 2)) || !level.getWorldBorder().isWithinBounds(new BlockPos(x0 + w + 2, 0, z0 + d + 2))) {
					continue;
				}
				if (pass == 0 && !(level.getChunkSource().hasChunk(x0 >> 4, z0 >> 4) && level.getChunkSource().hasChunk(x0 + w >> 4, z0 + d >> 4))) {
					continue;
				}
				BlockPos origin = site(level, x0, z0, w, d, h);
				if (origin != null) {
					return origin;
				}
			}
		}
		return null;
	}

	private static @Nullable BlockPos site(ServerLevel level, int x0, int z0, int w, int d, int h) {
		for (int cx = x0 >> 4; cx <= x0 + w - 1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z0 + d - 1 >> 4; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
		for (int x = x0; x < x0 + w; x++) {
			for (int z = z0; z < z0 + d; z++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
				lowest = Math.min(lowest, top.getY());
				highest = Math.max(highest, top.getY());
			}
		}
		if (highest - lowest > MAX_SLOPE || highest <= level.getMinY() + FOUNDATION + 2 || highest + h + CLEAR_ABOVE >= level.getMaxY()) {
			return null;
		}
		// every column, from the floor layer up through the walls, the roof and the air over it
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = x0; x < x0 + w; x++) {
			for (int z = z0; z < z0 + d; z++) {
				for (int y = highest - 1; y <= highest + h + CLEAR_ABOVE; y++) {
					if (!natural(level.getBlockState(at.set(x, y, z)))) {
						return null;
					}
				}
				// the ground the foundation will stand on must be dry too
				if (!level.getBlockState(at.set(x, highest - 2, z)).getFluidState().isEmpty()) {
					return null;
				}
			}
		}
		// no structure (village, ruin, camp, dungeon) under it
		BoundingBox box = new BoundingBox(x0, highest - FOUNDATION, z0, x0 + w - 1, highest + h + CLEAR_ABOVE, z0 + d - 1);
		for (int cx = x0 >> 4; cx <= x0 + w - 1 >> 4; cx++) {
			for (int cz = z0 >> 4; cz <= z0 + d - 1 >> 4; cz++) {
				for (StructureStart start : level.structureManager().startsForStructure(cx, cz, structure -> true)) {
					if (start.isValid() && start.getBoundingBox().intersects(box)) {
						return null;
					}
				}
			}
		}
		return new BlockPos(x0, highest, z0);
	}

	/** Nothing but air, plants, leaves and bare earth: no fluid, log, plank, ore or built block. */
	private static boolean natural(BlockState state) {
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
			|| state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SANDSTONE)
			|| state.is(Blocks.CLAY) || state.is(Blocks.RED_SANDSTONE);
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
