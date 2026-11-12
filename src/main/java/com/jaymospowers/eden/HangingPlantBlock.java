package com.jaymospowers.eden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plant that hangs: under a solid underside, or (a vine) under more of itself. {@link #BOTTOM} is true on the lowest
 * block of a string, which is drawn as its tip.
 */
public class HangingPlantBlock extends Block {
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

	private final boolean chain;

	/** @param chain whether it hangs under itself too */
	public HangingPlantBlock(boolean chain, BlockBehaviour.Properties properties) {
		super(properties);
		this.chain = chain;
		this.registerDefaultState(this.stateDefinition.any().setValue(BOTTOM, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BOTTOM);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState holder = level.getBlockState(above);
		return this.chain && holder.is(this) || holder.isFaceSturdy(level, above, Direction.DOWN);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (direction == Direction.UP && !this.canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (direction == Direction.DOWN) {
			return state.setValue(BOTTOM, !neighborState.is(this));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}
}
