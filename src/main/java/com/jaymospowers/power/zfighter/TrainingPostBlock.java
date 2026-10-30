package com.jaymospowers.power.zfighter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.71) The Training Post: two blocks tall and shaped like the vanilla mannequin (a player's proportions: legs, a
 * padded body, arms, a sacking head), facing whoever set it. The lower half is the post proper (its item, its drop);
 * the upper half comes with it and goes with it. A lower half set by a structure grows its upper half when there's room.
 */
public class TrainingPostBlock extends HorizontalDirectionalBlock {
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

	// (shapes for a post facing north or south: wide along x, thin along z; east and west swap them)
	private static final VoxelShape LOWER_NS = Shapes.or(Block.box(4, 0, 6, 12, 16, 10), Block.box(0, 12, 6, 16, 16, 10));
	private static final VoxelShape LOWER_EW = Shapes.or(Block.box(6, 0, 4, 10, 16, 12), Block.box(6, 12, 0, 10, 16, 16));
	private static final VoxelShape UPPER_NS = Shapes.or(Block.box(0, 0, 6, 16, 8, 10), Block.box(4, 8, 4, 12, 16, 12));
	private static final VoxelShape UPPER_EW = Shapes.or(Block.box(6, 0, 0, 10, 8, 16), Block.box(4, 8, 4, 12, 16, 12));

	public TrainingPostBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		boolean ns = state.getValue(FACING).getAxis() == Direction.Axis.Z;
		if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
			return ns ? LOWER_NS : LOWER_EW;
		}
		return ns ? UPPER_NS : UPPER_EW;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Level level = context.getLevel();
		if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) {
			return null;
		}
		// it faces the one setting it
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
		level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		// (a post set by a structure, or one from before it stood two blocks tall: its top half comes when there's room)
		if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.LOWER && pos.getY() < level.getMaxY()
			&& level.getBlockState(pos.above()).isAir()) {
			level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
		}
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			BlockState below = level.getBlockState(pos.below());
			return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
		}
		return true;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		DoubleBlockHalf half = state.getValue(HALF);
		if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN && !(neighbour.is(this) && neighbour.getValue(HALF) == DoubleBlockHalf.LOWER)) {
			// the post below is gone: so is its top
			return Blocks.AIR.defaultBlockState();
		}
		if (half == DoubleBlockHalf.LOWER && direction == Direction.UP && neighbour.is(this) && neighbour.getValue(HALF) == DoubleBlockHalf.UPPER
			&& neighbour.getValue(FACING) != state.getValue(FACING)) {
			return state.setValue(FACING, neighbour.getValue(FACING));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
			// broken at the top: the post below goes too, and drops itself (once) unless in creative
			BlockPos below = pos.below();
			BlockState lower = level.getBlockState(below);
			if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
				if (player.isCreative()) {
					level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
				} else {
					level.destroyBlock(below, true, player);
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
