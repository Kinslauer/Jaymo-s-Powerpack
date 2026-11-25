package com.jaymoskitchen.corpse;

import com.jaymoskitchen.KitchenBlood;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A slime (or magma cube) bursts as it dies: goo flies out of it and splats, more of it the bigger it was. It still
 * splits as it always does (that's the game's, as it's removed); this is only the burst. Jaymo's Powers' own blood for
 * it, if it has any, splats too ({@link KitchenBlood}).
 */
public final class Goo {
	/** Flecks of goo a size-1 cube bursts into, and how many more for each size after it, at most. */
	static final int FLECKS = 8, FLECKS_PER_SIZE = 6, FLECKS_MAX = 60;
	/** How far (blocks, per size) and how fast the goo flies. */
	static final double SPREAD = 0.25, SPEED = 0.2;

	private Goo() {
	}

	/** {@code cube} has just started to die: its goo bursts. */
	public static void burst(AbstractCubeMob cube) {
		if (!(cube.level() instanceof ServerLevel level)) {
			return;
		}
		int size = Math.max(1, cube.getSize());
		double width = cube.getBbWidth();
		Item goo = cube.getType() == EntityTypes.MAGMA_CUBE ? Items.MAGMA_CREAM : Items.SLIME_BALL;
		int flecks = Math.min(FLECKS_MAX, FLECKS + FLECKS_PER_SIZE * (size - 1));
		double y = cube.getY() + cube.getBbHeight() * 0.5;
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, goo), cube.getX(), y, cube.getZ(), flecks, width * 0.4,
			cube.getBbHeight() * 0.3, width * 0.4, SPEED);
		KitchenBlood.send(level, cube, cube.getX(), y, cube.getZ(), flecks / 2, width * 0.4, cube.getBbHeight() * 0.3, width * 0.4, SPEED);
		level.playSound(null, cube.getX(), cube.getY(), cube.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 0.5F + 0.2F * size,
			1.2F - 0.1F * Math.min(size, 4));
	}
}
