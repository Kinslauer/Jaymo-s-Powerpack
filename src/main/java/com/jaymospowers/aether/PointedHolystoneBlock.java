package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A spike of holystone standing on the ground (a thin stepped cone). It hurts like a stalagmite when landed on: the
 * fall counts double and is dealt as stalagmite damage. Pops off when what it stands on goes.
 */
public class PointedHolystoneBlock extends Block {
	// ---- tuning ----
	/** Fall damage multiplier when landing on the spike. */
	public static final float FALL_MULTIPLIER = 2.0F;

	/** The cone, from the foot up (matches the model). */
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3.0, 0.0, 3.0, 13.0, 4.0, 13.0), Block.box(4.0, 4.0, 4.0, 12.0, 8.0, 12.0),
		Block.box(5.5, 8.0, 5.5, 10.5, 12.0, 10.5), Block.box(7.0, 12.0, 7.0, 9.0, 16.0, 9.0));

	public PointedHolystoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		entity.causeFallDamage(fallDistance, FALL_MULTIPLIER, level.damageSources().stalagmite());
	}
}
