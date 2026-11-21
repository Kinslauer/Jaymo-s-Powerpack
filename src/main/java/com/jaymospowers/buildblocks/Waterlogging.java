package com.jaymospowers.buildblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Doors, fence gates and pressure plates (vanilla's and any mod's that extend them) can be waterlogged: the mixins in
 * {@code buildblocks.mixin} add vanilla's {@code waterlogged} property to them and this is what they do with it. A state
 * without the property (a mod's block that builds its own state list) is left as it was.
 */
public final class Waterlogging {
	/** Whether the space above the door being placed held water, noted before its upper half replaces it. */
	private static boolean upperWet;

	private Waterlogging() {
	}

	/** Whether {@code state}'s block is one of the kinds that waterlog. */
	public static boolean covers(BlockState state) {
		return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof BasePressurePlateBlock;
	}

	/** A new default state starts dry (a boolean property's first value is true, which would put every door under water). */
	public static BlockState dry(BlockState state) {
		return covers(state) && state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
	}

	/** The placed state, wet if it goes into water. */
	public static @Nullable BlockState placed(@Nullable BlockState state, BlockPlaceContext context) {
		if (state == null || !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			return state;
		}
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		return state.setValue(BlockStateProperties.WATERLOGGED, water);
	}

	/** A door's upper half goes where the water may be different from the lower half's: remember it first. */
	public static void noteUpper(LevelReader level, BlockPos lower) {
		upperWet = level.getFluidState(lower.above()).getType() == Fluids.WATER;
	}

	/** And then fix the upper half, which was set from the lower one. */
	public static void applyUpper(Level level, BlockPos lower) {
		BlockState upper = level.getBlockState(lower.above());
		if (upper.hasProperty(BlockStateProperties.WATERLOGGED) && upper.getValue(BlockStateProperties.WATERLOGGED) != upperWet) {
			level.setBlock(lower.above(), upper.setValue(BlockStateProperties.WATERLOGGED, upperWet), Block.UPDATE_CLIENTS);
		}
	}

	/** A wet block keeps its water flowing. */
	public static void tick(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos) {
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
	}

	public static FluidState fluid(BlockState state, FluidState otherwise) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
			? Fluids.WATER.getSource(false) : otherwise;
	}

	/** Water may go in, as into a waterloggable block: dry, and only water. */
	public static boolean canPlace(BlockState state, Fluid fluid) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED) && fluid == Fluids.WATER;
	}
}
