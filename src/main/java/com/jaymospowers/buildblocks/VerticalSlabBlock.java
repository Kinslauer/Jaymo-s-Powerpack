package com.jaymospowers.buildblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A vertical slab: half a block (16 x 16 x {@link #THICKNESS} px) standing on the side {@link #FACING} names, and two of
 * them (the second placed on the empty half, as slabs are) make the full block. Built like the block it is cut from
 * ({@link BuildBlocks}). Waterlogs until it is double.
 */
public class VerticalSlabBlock extends Block implements SimpleWaterloggedBlock {
	/** How thick the slab is, in pixels. */
	public static final int THICKNESS = 8;
	/** A click on the top or bottom face this close (blocks) to the block's middle on both axes picks the side by where the player looks. */
	private static final double MIDDLE_DEAD_ZONE = 0.05;
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty DOUBLE = BooleanProperty.create("double");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, THICKNESS);
	private static final VoxelShape SOUTH = Block.box(0, 0, 16 - THICKNESS, 16, 16, 16);
	private static final VoxelShape WEST = Block.box(0, 0, 0, THICKNESS, 16, 16);
	private static final VoxelShape EAST = Block.box(16 - THICKNESS, 0, 0, 16, 16, 16);

	public VerticalSlabBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DOUBLE, false).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, DOUBLE, WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(DOUBLE)) {
			return Shapes.block();
		}
		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/** Whether the empty half of {@code state} (a single slab) is the one this click is aimed at. */
	private static boolean aimedAtEmptyHalf(BlockState state, BlockPlaceContext context) {
		Direction empty = state.getValue(FACING).getOpposite();
		Direction face = context.getClickedFace();
		if (face == empty) {
			return true;
		}
		if (face == state.getValue(FACING)) {
			return false;
		}
		// from the side or the top and bottom: the cursor decides, as it does for a slab
		Direction.Axis axis = empty.getAxis();
		boolean positive = context.getClickLocation().get(axis) - context.getClickedPos().get(axis) > 0.5;
		return positive == (empty.getAxisDirection() == Direction.AxisDirection.POSITIVE);
	}

	/** Only the same slab completes it, onto its empty half. */
	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		if (state.getValue(DOUBLE) || !context.getItemInHand().is(asItem())) {
			return false;
		}
		return !context.replacingClickedOnBlock() || aimedAtEmptyHalf(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		if (there.is(this)) {
			return there.setValue(DOUBLE, true).setValue(WATERLOGGED, false);
		}
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		return defaultBlockState().setValue(FACING, sideFor(context)).setValue(WATERLOGGED, water);
	}

	/** The side it sits on: against the wall it was put on, or, from above and below, toward the edge the cursor is nearest. */
	private static Direction sideFor(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		if (face.getAxis().isHorizontal()) {
			return face.getOpposite();
		}
		Vec3 hit = context.getClickLocation();
		BlockPos pos = context.getClickedPos();
		double dx = hit.x - pos.getX() - 0.5, dz = hit.z - pos.getZ() - 0.5;
		if (Math.abs(dx) <= MIDDLE_DEAD_ZONE && Math.abs(dz) <= MIDDLE_DEAD_ZONE) {
			return context.getHorizontalDirection().getOpposite();
		}
		return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public boolean canPlaceLiquid(LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
		return !state.getValue(DOUBLE) && SimpleWaterloggedBlock.super.canPlaceLiquid(user, level, pos, state, fluid);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
