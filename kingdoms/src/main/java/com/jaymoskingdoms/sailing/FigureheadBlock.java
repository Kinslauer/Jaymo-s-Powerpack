package com.jaymoskingdoms.sailing;

import java.util.Locale;
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
 * A figurehead: the carving at a ship's prow ({@link Figureheads}), in the wood she is built of. It faces the way the
 * ship goes. The carving is part of the block's state, so the realm's lion, the whaler's whale and a pirate's serpent
 * are the same block in different shapes.
 */
public final class FigureheadBlock extends Block {
	/** What is carved. */
	public enum Carving implements StringRepresentable {
		LION, GULL, STAG, WHALE, SERPENT, MAIDEN;

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Carving> CARVING = EnumProperty.create("carving", Carving.class);
	private static final VoxelShape SHAPE = Block.box(3, 0, 0, 13, 16, 16);

	public FigureheadBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CARVING, Carving.LION));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, CARVING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// it looks the way you look as you set it
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
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
