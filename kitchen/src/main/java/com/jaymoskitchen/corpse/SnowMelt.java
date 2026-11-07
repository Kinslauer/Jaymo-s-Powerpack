package com.jaymoskitchen.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.41) Andy: "snow golems should melt and not leave corpses." A snow golem leaves no body (it's in the
 * {@code no_corpse} tag) and doesn't tip over and puff away either: it slumps into itself over {@link #MELT_TICKS},
 * shedding snow as it sinks, and leaves a little snow where it stood. The squash itself is drawn on the client
 * ({@code SnowMeltPose}); both sides count its {@code deathTime} up here in place of vanilla's death tick.
 */
public final class SnowMelt {
	/** How long the melt takes (ticks); vanilla's death is 20. */
	public static final int MELT_TICKS = 50;
	/** Snow layers left where it stood (added to a snow layer already there, up to a full block). */
	static final int SNOW_LAYERS = 2;
	/** Snow chunks shed each tick, and snowflakes dripping off it. */
	static final int CHUNKS_PER_TICK = 3, FLAKES_PER_TICK = 2;
	/** A soft crunch every this many ticks while it sinks. */
	static final int CRUNCH_EVERY = 12;

	private SnowMelt() {
	}

	/** Whether this creature melts instead of dying the usual way. */
	public static boolean melts(LivingEntity entity) {
		return entity instanceof SnowGolem;
	}

	/** How far through its melt it is, 0 to 1, for drawing. */
	public static float progress(float deathTime) {
		return Mth.clamp(deathTime / MELT_TICKS, 0.0F, 1.0F);
	}

	/** One tick of melting, in place of {@code LivingEntity#tickDeath} (both sides). */
	public static void tick(LivingEntity golem) {
		golem.deathTime++;
		if (!(golem.level() instanceof ServerLevel level) || golem.isRemoved()) {
			return;
		}
		float p = progress(golem.deathTime);
		double height = golem.getBbHeight() * (1.0F - 0.85F * p);
		double width = golem.getBbWidth() * (0.5 + 0.4 * p);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
			golem.getX(), golem.getY() + height * 0.6, golem.getZ(), CHUNKS_PER_TICK, width * 0.5, height * 0.3, width * 0.5, 0.05);
		level.sendParticles(ParticleTypes.SNOWFLAKE, golem.getX(), golem.getY() + height, golem.getZ(), FLAKES_PER_TICK,
			width * 0.4, 0.05, width * 0.4, 0.01);
		if (golem.deathTime == 1) {
			level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.SNOW_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		} else if (golem.deathTime % CRUNCH_EVERY == 0) {
			level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.POWDER_SNOW_STEP, SoundSource.NEUTRAL, 0.6F,
				0.7F + level.getRandom().nextFloat() * 0.2F);
		}
		if (golem.deathTime >= MELT_TICKS) {
			level.sendParticles(ParticleTypes.ITEM_SNOWBALL, golem.getX(), golem.getY() + 0.2, golem.getZ(), 12, 0.3, 0.1, 0.3, 0.1);
			leaveSnow(level, golem.blockPosition());
			// no puff of smoke (vanilla's event 60): there's nothing left of it but the snow
			golem.remove(Entity.RemovalReason.KILLED);
		}
	}

	/** A little snow where it stood, if snow can lie there. */
	static void leaveSnow(ServerLevel level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		BlockState snow = Blocks.SNOW.defaultBlockState();
		if (here.is(Blocks.SNOW)) {
			int layers = here.getValue(SnowLayerBlock.LAYERS);
			if (layers < 8) {
				level.setBlockAndUpdate(pos, here.setValue(SnowLayerBlock.LAYERS, Math.min(8, layers + SNOW_LAYERS)));
			}
		} else if (here.isAir() && snow.canSurvive(level, pos)) {
			level.setBlockAndUpdate(pos, snow.setValue(SnowLayerBlock.LAYERS, SNOW_LAYERS));
		}
	}
}
