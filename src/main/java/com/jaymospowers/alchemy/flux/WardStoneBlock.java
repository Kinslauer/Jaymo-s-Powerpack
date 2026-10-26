package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Flux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Ward Stone: a carved block, faintly aglow, that drinks the flux out of its chunk, {@link FluxEffects#WARD_DRAIN} each
 * {@link FluxEffects#WARD_INTERVAL} ticks. It runs on its own scheduled tick (saved with the chunk), so it keeps working
 * while the chunk is loaded and stops harmlessly when it isn't. Several in one chunk drain several times as fast.
 */
public class WardStoneBlock extends Block {
	public WardStoneBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this) && level instanceof ServerLevel server) {
			server.scheduleTick(pos, this, FluxEffects.WARD_INTERVAL);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (Flux.take(level, pos, FluxEffects.WARD_DRAIN) > 0) {
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.25, 0.1, 0.25, 0.01);
		}
		level.scheduleTick(pos, this, FluxEffects.WARD_INTERVAL);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(24) == 0) {
			level.addParticle(ParticleTypes.END_ROD, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
