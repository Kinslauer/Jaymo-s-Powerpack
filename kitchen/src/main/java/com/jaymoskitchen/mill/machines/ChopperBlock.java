package com.jaymoskitchen.mill.machines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The bloody chopper (Better With Mods' saw, MIT; our own code): a banded wooden box with a saw blade sticking out of
 * its front ({@link #FACING}, placed facing you like a piston). A shaft into any other side turns it, and only then
 * does it cut ({@link ChopperEntity}). The blade has no collision, so whatever walks into the front walks into it.
 */
public class ChopperBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;
	/** Being turned by a shaft (set by the chopper; shows the blade turning). */
	public static final BooleanProperty TURNING = BooleanProperty.create("turning");

	/** The box without the 3 pixels the blade sticks out into, by facing. */
	private static final VoxelShape NORTH = Block.box(0, 0, 3, 16, 16, 16), SOUTH = Block.box(0, 0, 0, 16, 16, 13),
		WEST = Block.box(3, 0, 0, 16, 16, 16), EAST = Block.box(0, 0, 0, 13, 16, 16),
		UP = Block.box(0, 0, 0, 16, 13, 16), DOWN = Block.box(0, 3, 0, 16, 16, 16);

	public ChopperBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TURNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TURNING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(FACING)) {
			case NORTH -> NORTH;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			case UP -> UP;
			case DOWN -> DOWN;
		};
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChopperEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, PowerMachines.CHOPPER_ENTITY, ChopperEntity::serverTick);
	}
}
