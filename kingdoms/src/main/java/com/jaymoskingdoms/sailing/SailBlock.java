package com.jaymoskingdoms.sailing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.7) Sailcloth hung across the ship, bellied out the way it faces (the way the ship sails). Sails side by side and
 * one above another make one sheet: the top row hangs from a yard, the cloth bellies out most in the middle and less at
 * its edges, and a mast beside it (a fence, wall or upright log) is crossed by the sheet and its yard. Each one aboard
 * drives the ship a little faster ({@link Hull#sails}); while the ship lies furled they're drawn rolled up on the yard.
 */
public final class SailBlock extends Block {
	/** What's beside a sail, to its left or right (seen from behind it, looking the way it faces). */
	public enum Side implements StringRepresentable {
		NONE, SAIL, MAST;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Side> LEFT = EnumProperty.create("left", Side.class), RIGHT = EnumProperty.create("right", Side.class);
	/** Sailcloth above and below (the sheet goes on). */
	public static final BooleanProperty UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;
	/** Drawn rolled up on its yard (only ever set on a ship's own copy, while its sails are furled). */
	public static final BooleanProperty FURLED = BooleanProperty.create("furled");
	/** Its cloth hangs at the front of its block, the side it faces (and bellies out beyond it). */
	private static final java.util.Map<Direction, VoxelShape> SHAPES = java.util.Map.of(
		Direction.NORTH, Block.box(0, 0, 0, 16, 16, 2), Direction.SOUTH, Block.box(0, 0, 14, 16, 16, 16),
		Direction.WEST, Block.box(0, 0, 0, 2, 16, 16), Direction.EAST, Block.box(14, 0, 0, 16, 16, 16));

	public SailBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, Side.NONE).setValue(RIGHT, Side.NONE)
			.setValue(UP, false).setValue(DOWN, false).setValue(FURLED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LEFT, RIGHT, UP, DOWN, FURLED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return connect(defaultBlockState().setValue(FACING, context.getHorizontalDirection()), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbour,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return connect(state, level, pos);
	}

	/** Its sides as the blocks round it have them. */
	static BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
		Direction facing = state.getValue(FACING);
		return state.setValue(LEFT, side(state, level.getBlockState(pos.relative(facing.getCounterClockWise()))))
			.setValue(RIGHT, side(state, level.getBlockState(pos.relative(facing.getClockWise()))))
			.setValue(UP, sameSheet(state, level.getBlockState(pos.above())))
			.setValue(DOWN, sameSheet(state, level.getBlockState(pos.below())));
	}

	private static Side side(BlockState sail, BlockState beside) {
		if (sameSheet(sail, beside)) {
			return Side.SAIL;
		}
		return mast(beside) ? Side.MAST : Side.NONE;
	}

	private static boolean sameSheet(BlockState sail, BlockState other) {
		return other.getBlock() instanceof SailBlock && other.getValue(FACING).getAxis() == sail.getValue(FACING).getAxis();
	}

	/** A mast: a fence, a wall, or a log standing upright. */
	static boolean mast(BlockState state) {
		return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
			|| state.is(BlockTags.LOGS) && state.getOptionalValue(BlockStateProperties.AXIS).orElse(Direction.Axis.Y) == Direction.Axis.Y;
	}

	/**
	 * Turned to belly out toward {@code forward}, if it hangs across that way (a sail facing aft, set so by whoever hung
	 * it, would belly backward against the wind). Its left and right change places as it turns about.
	 */
	static BlockState toward(BlockState state, Direction forward) {
		if (state.getValue(FACING) != forward.getOpposite()) {
			return state;
		}
		return state.setValue(FACING, forward).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState turned = state.rotate(mirror.getRotation(state.getValue(FACING)));
		return mirror == Mirror.NONE ? turned : turned.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
	}
}
