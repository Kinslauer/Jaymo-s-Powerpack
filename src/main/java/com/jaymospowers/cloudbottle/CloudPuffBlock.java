package com.jaymospowers.cloudbottle;

import com.jaymospowers.aether.cloudsea.CloudbankBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A puff of bottled cloud: soft like Cloudbank (you sink into a single layer and stand in it, a fall onto it is cut and
 * forgotten, daylight passes), but it thins away by itself. Each block of a puff has its own scheduled tick, {@link #LIFE}
 * ticks (about 20 seconds) give or take {@link #SPREAD}, so a puff frays out rather than vanishing all at once. The scheduled
 * tick is saved with the chunk, so a puff left behind by a restart still goes. It drops nothing.
 */
public class CloudPuffBlock extends CloudbankBlock {
	// ---- tuning ----
	/** How long a puff block lasts (ticks), and how far that varies either way. */
	public static final int LIFE = 400, SPREAD = 60;

	public CloudPuffBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this) && !level.isClientSide()) {
			level.scheduleTick(pos, this, LIFE - SPREAD + level.getRandom().nextInt(2 * SPREAD + 1));
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.01);
	}
}
