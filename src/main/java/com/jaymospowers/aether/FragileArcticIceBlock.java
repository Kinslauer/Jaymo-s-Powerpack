package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fragile arctic ice: cracks and breaks {@link #BREAK_DELAY} ticks after a player stands on it, and on one random tick in
 * {@link #RANDOM_ODDS}. It leaves nothing behind (no water).
 */
public class FragileArcticIceBlock extends HalfTransparentBlock {
	// ---- tuning ----
	public static final int BREAK_DELAY = 5, RANDOM_ODDS = 5;

	public FragileArcticIceBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide() && entity instanceof Player && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, BREAK_DELAY);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		shatter(state, level, pos);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(RANDOM_ODDS) == 0) {
			shatter(state, level, pos);
		}
	}

	private static void shatter(BlockState state, ServerLevel level, BlockPos pos) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
		level.removeBlock(pos, false);
	}
}
