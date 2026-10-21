package com.jaymoskitchen.mill.power;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A shaft corner: a plain right-angle turn (wooden pegs on two wheels, no gearbox) joining a level shaft to an
 * upright one, so a windmill's level shaft can turn down into the top of a mill. One arm points {@link #FACING} (a
 * side), the other up or down. Only one in a run.
 */
public class ShaftCornerBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	/** The upright arm points up (else down). */
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty TURNING = ShaftBlock.TURNING;

	private static final VoxelShape CORE = Block.box(4, 4, 4, 12, 12, 12);
	private static final VoxelShape DOWN_ARM = Block.box(5, 0, 5, 11, 4, 11);
	private static final VoxelShape UP_ARM = Block.box(5, 12, 5, 11, 16, 11);
	private static final Map<Direction, VoxelShape> SIDE_ARMS = new EnumMap<>(Map.of(
		Direction.NORTH, Block.box(5, 5, 0, 11, 11, 4),
		Direction.SOUTH, Block.box(5, 5, 12, 11, 11, 16),
		Direction.WEST, Block.box(0, 5, 5, 4, 11, 11),
		Direction.EAST, Block.box(12, 5, 5, 16, 11, 11)));

	public ShaftCornerBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(UP, false).setValue(TURNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, UP, TURNING);
	}

	/**
	 * Placed against the side of a block (the end of a level shaft), that side arm points back at it and the other
	 * arm down (sneak: up); placed on top of or under a block (an upright shaft), the upright arm points at it and the
	 * side arm the way you're looking.
	 */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockState state = defaultBlockState();
		if (face.getAxis().isHorizontal()) {
			return state.setValue(FACING, face.getOpposite()).setValue(UP, context.isSecondaryUseActive());
		}
		return state.setValue(FACING, context.getHorizontalDirection()).setValue(UP, face == Direction.DOWN);
	}

	/** Coming in through {@code in} (one arm), the way out through the other arm; null if {@code in} isn't an arm. */
	public static @Nullable Direction otherArm(BlockState state, Direction in) {
		Direction side = state.getValue(FACING);
		Direction upright = state.getValue(UP) ? Direction.UP : Direction.DOWN;
		if (in == side) {
			return upright;
		}
		return in == upright ? side : null;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.or(CORE, state.getValue(UP) ? UP_ARM : DOWN_ARM, SIDE_ARMS.get(state.getValue(FACING)));
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
