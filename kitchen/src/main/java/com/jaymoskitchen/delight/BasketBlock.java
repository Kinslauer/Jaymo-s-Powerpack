package com.jaymoskitchen.delight;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's baskets, MIT; their models and art, our own code.) An open-topped basket (wicker or bamboo) that
 * scoops up the items lying in the space its open side faces and holds 27 stacks (see {@link BasketBlockEntity}).
 * Place it facing the way you look; a redstone signal shuts it.
 */
public class BasketBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

	private static final VoxelShape OUT_SHAPE = Shapes.block();
	private static final VoxelShape RENDER_SHAPE = Block.box(1.0, 1.0, 1.0, 15.0, 15.0, 15.0);
	private static final ImmutableMap<Direction, VoxelShape> SHAPES = Maps.immutableEnumMap(ImmutableMap.<Direction, VoxelShape>builder()
		.put(Direction.DOWN, hollow(Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0)))
		.put(Direction.UP, hollow(Block.box(2.0, 2.0, 2.0, 14.0, 16.0, 14.0)))
		.put(Direction.NORTH, hollow(Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 14.0)))
		.put(Direction.SOUTH, hollow(Block.box(2.0, 2.0, 2.0, 14.0, 14.0, 16.0)))
		.put(Direction.WEST, hollow(Block.box(0.0, 2.0, 2.0, 14.0, 14.0, 14.0)))
		.put(Direction.EAST, hollow(Block.box(2.0, 2.0, 2.0, 16.0, 14.0, 14.0)))
		.build());

	private static VoxelShape hollow(VoxelShape cutout) {
		return Shapes.joinUnoptimized(OUT_SHAPE, cutout, BooleanOp.ONLY_FIRST).optimize();
	}

	public BasketBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ENABLED, true).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ENABLED, WATERLOGGED);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof BasketBlockEntity basket) {
			player.openMenu(basket);
		}
		return InteractionResult.CONSUME;
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
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
		boolean enabled = !level.hasNeighborSignal(pos);
		if (enabled != state.getValue(ENABLED)) {
			level.setBlock(pos, state.setValue(ENABLED, enabled), 4);
		}
		super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite())
			.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return RENDER_SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return OUT_SHAPE;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BasketBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DelightHome.BASKET_ENTITY, BasketBlockEntity::pushItemsTick);
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
