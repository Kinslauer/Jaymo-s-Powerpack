package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rotten floor over a pit, laid in old ruined towers and forts ({@link RuinTraps}): it looks like a plank floor, and gives way
 * a moment after something steps on it, taking the boards beside it down too.
 */
public final class WeakFloorBlock extends Block {
	/** Ticks from a step to the fall, and to the boards beside it. */
	static final int GIVE = 8, SPREAD = 3;

	public WeakFloorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
		if (!level.isClientSide() && entity instanceof LivingEntity && !entity.isSteppingCarefully()) {
			level.scheduleTick(pos, this, GIVE);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
		level.destroyBlock(pos, false);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(d)).is(this)) {
				level.scheduleTick(pos.relative(d), this, SPREAD);
			}
		}
	}
}
