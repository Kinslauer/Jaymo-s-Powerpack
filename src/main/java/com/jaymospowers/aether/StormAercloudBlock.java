package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storm aercloud: a dark cloud that crackles. Whoever is inside takes {@link #DAMAGE} lightning damage every
 * {@link #INTERVAL} ticks; there is still no fall damage (it is a cloud).
 */
public class StormAercloudBlock extends AercloudBlock {
	// ---- tuning ----
	/** Lightning damage dealt, and ticks between strikes on the same entity. */
	public static final float DAMAGE = 1.0F;
	public static final int INTERVAL = 40;

	public StormAercloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
		this.settle(entity);
		if (level.isClientSide()) {
			if (level.getRandom().nextInt(3) == 0) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, entity.getRandomX(0.6), entity.getRandomY(), entity.getRandomZ(0.6), 0.0, 0.0, 0.0);
			}
		} else if (level instanceof ServerLevel server && entity instanceof LivingEntity && entity.tickCount % INTERVAL == 0) {
			entity.hurtServer(server, server.damageSources().lightningBolt(), DAMAGE);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
				pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
		}
	}
}
