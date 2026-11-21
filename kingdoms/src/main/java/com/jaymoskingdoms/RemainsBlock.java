package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The gone-before's bones ({@link Remains}): a skeleton lying on the ground in one of three poses, drawn in the colour of the
 * place it fell ({@link #KIND}, an index into {@link Remains#KINDS}). It has no collision, so you walk over it. Everything they
 * carried is worked out from where they lie, so the block holds no data: breaking it with an empty hand searches it, a shovel
 * buries it, silk touch lifts it whole.
 */
public final class RemainsBlock extends Block implements SimpleWaterloggedBlock {
	public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 9);
	public static final IntegerProperty POSE = IntegerProperty.create("pose", 0, 2);
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0);

	public RemainsBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, 6).setValue(POSE, 0).setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, POSE, FACING, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(net.minecraft.tags.FluidTags.WATER);
		return defaultBlockState().setValue(POSE, context.getLevel().getRandom().nextInt(3)).setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(WATERLOGGED, water);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		if (!canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}
}
