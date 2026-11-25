package com.jaymoskingdoms.glowwood;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A giant bouncecap's cap: springy like a slime block, with no fall damage and its own boing (ported from Spore & Stem, Apache-2.0). */
public class BouncecapCapBlock extends HugeMushroomBlock {
	/** How much of a fall it gives back, and the walking slowdown. */
	private static final float RESTITUTION = 0.92F;
	private static final double SLOW_BASE = 0.4, SLOW_PER_SPEED = 0.2, SLOW_BELOW_SPEED = 0.1;
	/** The fall (blocks) it takes to boing aloud. */
	private static final double BOING_FROM = 1.0;

	public BouncecapCapBlock(Properties properties) {
		super(properties.bounceRestitution(RESTITUTION));
	}

	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		if (entity.isSuppressingBounce()) {
			super.fallOn(level, state, pos, entity, fallDistance);
		} else {
			// the damage multiplier is 0: the cap takes the fall
			entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
			if (fallDistance > BOING_FROM && !level.isClientSide()) {
				level.playSound(null, pos, GlowWoods.BOUNCECAP_BOUNCE, SoundSource.BLOCKS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.3F);
			}
		}
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		double vy = Math.abs(entity.getDeltaMovement().y);
		if (vy < SLOW_BELOW_SPEED && !entity.isSteppingCarefully()) {
			double slow = SLOW_BASE + vy * SLOW_PER_SPEED;
			entity.setDeltaMovement(entity.getDeltaMovement().multiply(slow, 1.0, slow));
		}
		super.stepOn(level, pos, state, entity);
	}
}
