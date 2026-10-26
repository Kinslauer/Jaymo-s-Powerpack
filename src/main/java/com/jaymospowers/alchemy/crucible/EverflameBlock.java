package com.jaymospowers.alchemy.crucible;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An Everflame: a small flame that floats in the middle of its block, shedding {@link #LIGHT} light and never burning
 * out. Set it by right-clicking a face like any block; it has no collision and breaks at a touch.
 */
public class EverflameBlock extends Block {
	/** The light it gives (the most there is). */
	public static final int LIGHT = 15;
	private static final VoxelShape SHAPE = Block.box(5, 4, 5, 11, 12, 11);

	public EverflameBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5, y = pos.getY() + 0.55, z = pos.getZ() + 0.5;
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.15, y + random.nextDouble() * 0.2,
				z + (random.nextDouble() - 0.5) * 0.15, 0.0, 0.01, 0.0);
		}
		if (random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.3, y + 0.1, z + (random.nextDouble() - 0.5) * 0.3,
				0.0, 0.01, 0.0);
		}
	}
}
