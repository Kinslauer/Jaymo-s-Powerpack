package com.jaymospowers.alchemy.crucible;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The alembic: a small still of copper and glass that stands on a crucible. It has no logic of its own: a hot pot under
 * it ({@link CrucibleEntity}) leads one essence every {@link Crucibles#DISTIL_TICKS} into an essence jar next to the
 * alembic (or next to the pot). It covers the pot's opening, so nothing can be thrown in while it stands there.
 */
public class AlembicBlock extends Block {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 11, 13), Block.box(6, 11, 6, 10, 14, 10),
		Block.box(7, 10, 2, 9, 12, 6), Block.box(7, 10, 10, 9, 12, 14), Block.box(2, 10, 7, 6, 12, 9), Block.box(10, 10, 7, 14, 12, 9));

	public AlembicBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** It is only ever set on a crucible. */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(Crucibles.CRUCIBLE);
	}

	/** A wisp of steam while the pot beneath is hot. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(6) == 0 && level.getBlockEntity(pos.below()) instanceof CrucibleEntity pot && pot.hot() && !pot.essences().isEmpty()) {
			level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
		}
	}
}
