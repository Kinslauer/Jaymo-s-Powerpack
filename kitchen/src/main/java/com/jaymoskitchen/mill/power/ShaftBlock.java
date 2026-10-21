package com.jaymoskitchen.mill.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wooden shaft (Better With Mods' axle, MIT; our own code): a thin log laid along x, y or z, placed like a log.
 * A run of them carries a windmill's or waterwheel's turn to a machine; while it turns, it shows it.
 */
public class ShaftBlock extends RotatedPillarBlock {
	/** Being turned by a windmill or waterwheel (set by the source; only changes the look). */
	public static final BooleanProperty TURNING = BooleanProperty.create("turning");

	private static final VoxelShape X = Block.box(0, 5, 5, 16, 11, 11);
	private static final VoxelShape Y = Block.box(5, 0, 5, 11, 16, 11);
	private static final VoxelShape Z = Block.box(5, 5, 0, 11, 11, 16);

	public ShaftBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Y).setValue(TURNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(TURNING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(AXIS)) {
			case X -> X;
			case Y -> Y;
			case Z -> Z;
		};
	}
}
