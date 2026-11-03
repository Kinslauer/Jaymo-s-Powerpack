package com.jaymoskitchen.butchery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.5.51) A meat hook: a short chain and an iron hook, hung under a beam (any block with something solid to it above:
 * a log, a fence, a chain). Right-click it while carrying a carcass, or dragging one near, to hang the carcass on it by
 * its hind legs ({@code corpse/Hanging}).
 */
public class MeatHookBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(6.0, 2.0, 6.0, 10.0, 16.0, 10.0);
	/** Where a hung carcass's legs are held: this far up the hook's block. */
	public static final double HOLD_AT = 0.2;

	public MeatHookBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		return !level.getBlockState(above).getCollisionShape(level, above).isEmpty();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return direction == Direction.UP && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}
}
