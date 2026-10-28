package com.jaymospowers.layers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.17.114) Andy: "a layer block, that works like snow layers ... built from any wooden or stone block, using a saw or a
 * stone cutter. you can place these anywhere, and they stack up to 8, making a full block incrementally." Each layer is
 * {@link #PIXELS_PER_LAYER} px; placing the same layer on top of a stack adds one, and at {@link #MAX_LAYERS} it is the
 * full block. Unlike snow, what it holds up stands on its true height (snow lets you sink one layer), and it needs
 * nothing under it. Waterlogs like a slab until it is full.
 */
public class LayerBlock extends Block implements SimpleWaterloggedBlock {
	public static final int MAX_LAYERS = 8;
	public static final int PIXELS_PER_LAYER = 16 / MAX_LAYERS;
	public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape[] SHAPES = new VoxelShape[MAX_LAYERS + 1];

	static {
		for (int n = 1; n <= MAX_LAYERS; n++) {
			SHAPES[n] = Block.box(0, 0, 0, 16, n * PIXELS_PER_LAYER, 16);
		}
	}

	public LayerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LAYERS, 1).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LAYERS, WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(LAYERS)];
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/** Only the same layer stacks onto it, and from above (or into its own space when clicked from the side). */
	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		if (!context.getItemInHand().is(asItem()) || state.getValue(LAYERS) >= MAX_LAYERS) {
			return false;
		}
		return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		if (there.is(this)) {
			int layers = Math.min(MAX_LAYERS, there.getValue(LAYERS) + 1);
			return there.setValue(LAYERS, layers).setValue(WATERLOGGED, layers < MAX_LAYERS && there.getValue(WATERLOGGED));
		}
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		return defaultBlockState().setValue(WATERLOGGED, water);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public boolean canPlaceLiquid(LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
		return state.getValue(LAYERS) < MAX_LAYERS && SimpleWaterloggedBlock.super.canPlaceLiquid(user, level, pos, state, fluid);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}
}
