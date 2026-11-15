package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Slow farms (the GT New Horizons picks, patch 2): crops, saplings, cane, cactus, cocoa, nether wart, berries and fruit
 * trees ({@code #jaymoskitchen:slow_growing}) grow at {@link #GROWTH} of their speed, on top of the seasons
 * ({@code SlowGrowthMixin} drops that share of their random ticks; Kingdoms' growing trees keep their own, slower
 * clock). Bone meal pushes a crop {@link #BONE_MEAL_STAGES} stage ({@code CropBoneMealMixin}, {@code StemBoneMealMixin},
 * the Kitchen's own crops). A Time Bloom (Jaymo's Powers' Chronokinesis, by reflection) runs its random ticks through
 * {@link #hurry}, so nothing it grows is slowed.
 */
public final class SlowFarms {
	/** The share of a plant's random ticks that still grow it. */
	public static final float GROWTH = 0.5F;
	/** Stages one use of bone meal pushes a crop. */
	public static final int BONE_MEAL_STAGES = 1;

	/** Plants that grow at {@link #GROWTH} (data/jaymoskitchen/tags/block/slow_growing.json). */
	public static final TagKey<Block> SLOW_GROWING = TagKey.create(Registries.BLOCK, JaymosKitchen.id("slow_growing"));

	/** Set while a Time Bloom's own tick runs ({@link #hurry}); the server thread only. */
	private static boolean hurried;

	private SlowFarms() {
	}

	/** Whether this random tick of {@code state} is dropped. */
	public static boolean slows(BlockState state, RandomSource random) {
		return !hurried && state.is(SLOW_GROWING) && random.nextFloat() >= GROWTH;
	}

	/**
	 * Jaymo's Powers' Time Bloom: runs {@code tick} (a random tick it gives a plant) without the farms' slowing. Plain
	 * Java types, so it can be reached by reflection.
	 */
	public static void hurry(Runnable tick) {
		boolean was = hurried;
		hurried = true;
		try {
			tick.run();
		} finally {
			hurried = was;
		}
	}
}
