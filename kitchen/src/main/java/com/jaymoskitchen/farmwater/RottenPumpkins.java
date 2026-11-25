package com.jaymoskitchen.farmwater;

import com.jaymoskitchen.season.Season;
import com.jaymoskitchen.season.Seasons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rotten pumpkins: now and then a pumpkin comes up on its vine already bad ({@link #ROT_CHANCE}; more often in autumn, when
 * the vines are old and the damp is in), and the farmer finds it among the good ones. It is useless to eat. The witch's
 * cauldron brews with it ({@code WitchCauldron} in Kingdoms), and so it is the one thing a farmer's carelessness yields
 * that someone wants: a field of pumpkins tended and picked in good time seldom has one. Fed in by
 * {@code mixin.RottenPumpkinMixin}, at the moment a vine sets its fruit.
 */
public final class RottenPumpkins {
	/** The chance a fruit set by a vine is a rotten pumpkin: in autumn, and the rest of the year. */
	public static final float ROT_CHANCE = 0.06F, AUTUMN_ROT_CHANCE = 0.2F;

	private RottenPumpkins() {
	}

	/** A vine at {@code pos} (it was {@code before}) has just ticked: if it set a pumpkin, that one may be rotten. */
	public static void ticked(ServerLevel level, BlockPos pos, BlockState before, RandomSource random) {
		if (before.getValue(StemBlock.AGE) < StemBlock.MAX_AGE || level.getBlockState(pos).is(before.getBlock())) {
			return;
		}
		float chance = Seasons.of(level) == Season.AUTUMN ? AUTUMN_ROT_CHANCE : ROT_CHANCE;
		if (random.nextFloat() >= chance) {
			return;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos fruit = pos.relative(side);
			if (level.getBlockState(fruit).is(Blocks.PUMPKIN)) {
				level.setBlock(fruit, FarmWater.ROTTEN_PUMPKIN.defaultBlockState(), 3);
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, fruit.getX() + 0.5, fruit.getY() + 0.9, fruit.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0.0);
				level.playSound(null, fruit, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.5F, 0.6F);
				return;
			}
		}
	}
}
