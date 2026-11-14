package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A seared channel: an open trough that joins to the channels beside it and opens downward over anything that takes metal
 * (a casting table or basin, a tank, another channel). Pour into it from a faucet above.
 */
public class ChannelBlock extends MoltenBlock {
	public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST,
		SOUTH = BlockStateProperties.SOUTH, WEST = BlockStateProperties.WEST, DOWN = BlockStateProperties.DOWN;
	/** By which sides are joined (bit 0 north, 1 east, 2 south, 3 west). */
	private static final VoxelShape[] SHAPES = new VoxelShape[16];

	static {
		VoxelShape[] arms = {Block.box(4, 4, 0, 12, 9, 4), Block.box(12, 4, 4, 16, 9, 12), Block.box(4, 4, 12, 12, 9, 16), Block.box(0, 4, 4, 4, 9, 12)};
		for (int i = 0; i < 16; i++) {
			VoxelShape shape = Block.box(4, 4, 4, 12, 9, 12);
			for (int arm = 0; arm < 4; arm++) {
				if ((i & 1 << arm) != 0) {
					shape = Shapes.or(shape, arms[arm]);
				}
			}
			SHAPES[i] = shape;
		}
	}

	public ChannelBlock(Block.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
			.setValue(DOWN, false));
	}

	/** The property of a side (horizontal ones only). */
	public static BooleanProperty side(Direction side) {
		return switch (side) {
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	/** Whether a channel opens downward over this. */
	private static boolean takesFromAbove(BlockState below) {
		return below.getBlock() instanceof CastingBlock || below.getBlock() instanceof SearedTankBlock || below.getBlock() instanceof ChannelBlock;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, DOWN);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[(state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0)];
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = defaultBlockState().setValue(DOWN, takesFromAbove(context.getLevel().getBlockState(context.getClickedPos().below())));
		for (Direction side : Direction.Plane.HORIZONTAL) {
			state = state.setValue(side(side), context.getLevel().getBlockState(context.getClickedPos().relative(side)).getBlock() instanceof ChannelBlock);
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.DOWN) {
			return state.setValue(DOWN, takesFromAbove(neighbourState));
		}
		return direction == Direction.UP ? state : state.setValue(side(direction), neighbourState.getBlock() instanceof ChannelBlock);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChannelEntity(pos, state);
	}
}
