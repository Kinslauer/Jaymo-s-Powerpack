package com.jaymoskingdoms.glowwood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The blocks of one huge fungus, collected before anything is placed so the whole shape can be checked for room
 * first. Offsets are relative to the base of the stem; y points up.
 * Ported from Spore & Stem (Apache-2.0).
 */
final class FungusPlan {
	private final BlockPos origin;
	private final Set<BlockPos> stem = new LinkedHashSet<>();
	private final Set<BlockPos> cap = new LinkedHashSet<>();
	/** Hanging columns (top first) of an extra block, such as Glowcap Gills. */
	private final List<List<BlockPos>> hangers = new ArrayList<>();

	FungusPlan(BlockPos origin) {
		this.origin = origin;
	}

	void stem(int x, int y, int z) {
		BlockPos pos = origin.offset(x, y, z);
		cap.remove(pos);
		stem.add(pos);
	}

	void cap(int x, int y, int z) {
		BlockPos pos = origin.offset(x, y, z);
		if (!stem.contains(pos)) {
			cap.add(pos);
		}
	}

	boolean isCap(int x, int y, int z) {
		return cap.contains(origin.offset(x, y, z));
	}

	boolean isStem(int x, int y, int z) {
		return stem.contains(origin.offset(x, y, z));
	}

	void hanger(int x, int topY, int z, int length) {
		List<BlockPos> column = new ArrayList<>();
		for (int i = 0; i < length; i++) {
			column.add(origin.offset(x, topY - i, z));
		}
		hangers.add(column);
	}

	/** True when every block of the shape is free (air, plants, leaves; never fluids) and inside the world. */
	boolean fits(WorldGenLevel level) {
		for (BlockPos pos : stem) {
			if (!isFree(level, pos)) {
				return false;
			}
		}
		for (BlockPos pos : cap) {
			if (!isFree(level, pos)) {
				return false;
			}
		}
		return true;
	}

	static boolean isFree(WorldGenLevel level, BlockPos pos) {
		if (level.isOutsideBuildHeight(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.isAir() || (state.getFluidState().isEmpty()
				&& (state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES)));
	}

	/**
	 * Places stems (axis y), then caps with their faces set like vanilla huge mushrooms: the cap skin shows where a
	 * face meets the open air, the inside (gills, pores) where it touches the fungus itself and everywhere underneath.
	 * Hanging columns go last, only into air, with the lowest block marked as the tip.
	 */
	void place(WorldGenLevel level, BlockState stemState, BlockState capState, @Nullable BlockState hangerState,
			   @Nullable BooleanProperty hangerTip) {
		BlockState stemY = stemState.hasProperty(RotatedPillarBlock.AXIS)
				? stemState.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : stemState;
		for (BlockPos pos : stem) {
			level.setBlock(pos, stemY, 2);
		}
		for (BlockPos pos : cap) {
			BlockState state = capState;
			for (Direction direction : Direction.values()) {
				BooleanProperty face = faceProperty(direction);
				if (!state.hasProperty(face)) {
					continue;
				}
				BlockPos neighbour = pos.relative(direction);
				boolean exposed = direction != Direction.DOWN && !cap.contains(neighbour) && !stem.contains(neighbour);
				state = state.setValue(face, exposed);
			}
			level.setBlock(pos, state, 2);
		}
		if (hangerState == null) {
			return;
		}
		for (List<BlockPos> column : hangers) {
			int placed = 0;
			for (BlockPos pos : column) {
				if (!level.getBlockState(pos).isAir()) {
					break;
				}
				placed++;
			}
			for (int i = 0; i < placed; i++) {
				BlockState state = hangerTip != null ? hangerState.setValue(hangerTip, i == placed - 1) : hangerState;
				level.setBlock(column.get(i), state, 2);
			}
		}
	}

	private static BooleanProperty faceProperty(Direction direction) {
		return switch (direction) {
			case UP -> HugeMushroomBlock.UP;
			case DOWN -> HugeMushroomBlock.DOWN;
			case NORTH -> HugeMushroomBlock.NORTH;
			case SOUTH -> HugeMushroomBlock.SOUTH;
			case EAST -> HugeMushroomBlock.EAST;
			case WEST -> HugeMushroomBlock.WEST;
		};
	}
}
