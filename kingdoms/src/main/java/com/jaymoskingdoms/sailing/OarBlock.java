package com.jaymoskingdoms.sailing;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.18) An oar in its oarlock, set on a ship's gunwale and facing outboard. Each one aboard lets her row (W/S on a ship
 * with no sails, Space on one with them), the faster the more oars she has and the lighter she is ({@link Ship}); while
 * she rows her oars are run out and drawn sweeping through the water, and shipped (laid along the gunwale) when she
 * doesn't. (AlekiShips, MIT, rows its boats with oars in oarlocks; this is our own take on it.)
 */
public final class OarBlock extends Block {
	/** How it's drawn: shipped along the gunwale, its lock alone (the oar run out, drawn by the ship), or the oar alone. */
	public enum Oar implements StringRepresentable {
		SHIPPED, LOCK, LOOM;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Oar> OAR = EnumProperty.create("oar", Oar.class);
	/** A low block of gunwale with the lock on its outboard edge. */
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.NORTH, Block.box(0, 0, 0, 16, 4, 6), Direction.SOUTH, Block.box(0, 0, 10, 16, 4, 16),
		Direction.WEST, Block.box(0, 0, 0, 6, 4, 16), Direction.EAST, Block.box(10, 0, 0, 16, 4, 16));

	public OarBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OAR, Oar.SHIPPED));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OAR);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// it faces the way you look as you set it: outboard, from the deck
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
