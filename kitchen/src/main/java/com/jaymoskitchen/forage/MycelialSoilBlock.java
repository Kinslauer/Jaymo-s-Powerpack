package com.jaymoskitchen.forage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dark loam threaded with mycelium (from Spore &amp; Stem, Apache-2.0; see licenses/spore-and-stem). Any mushroom, vanilla
 * ones too, stands on it in any light, and on random ticks it slowly spreads the mushroom growing on top of it. It
 * keeps no season, so a bed of it fruits all year.
 */
public class MycelialSoilBlock extends Block {
	/** A random tick spreads the mushroom above with chance one in this. */
	private static final int SPREAD_ODDS = 8;
	/** Spore particles drift up one animate tick in this many. */
	private static final int PARTICLE_ODDS = 14;

	public MycelialSoilBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos abovePos = pos.above();
		BlockState above = level.getBlockState(abovePos);
		if (above.getBlock() instanceof MushroomBlock && random.nextInt(SPREAD_ODDS) == 0) {
			ForageMushroomBlock.spread(level, abovePos, above, random);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(PARTICLE_ODDS) == 0 && level.getBlockState(pos.above()).isAir()) {
			level.addParticle(ParticleTypes.MYCELIUM, pos.getX() + random.nextDouble(), pos.getY() + 1.05,
				pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
