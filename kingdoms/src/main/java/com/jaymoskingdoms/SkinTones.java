package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * (0.5.38) Andy: villagers in hot, dry lands should tend darker-skinned, those in the snow lighter. MCA already leans its
 * villagers' melanin on the biome's temperature, but one in five is an "immigrant" drawn from anywhere, so a snow town
 * kept turning up dark faces and a desert town pale ones. {@code mixin/mca/McaGeneticsMixin} re-rolls the melanin of
 * every newly spawned MCA villager here: a firm lean on the land's heat, a natural spread, and newcomers from the
 * neighbouring climates only. Children still inherit from their parents (MCA doesn't randomize them), and villagers
 * already in the world keep their skin. Our own folk (guards, lords, kings, builders) wear climate faces already
 * ({@link Climate#skinGroup}).
 */
public final class SkinTones {
	/** Melanin at a biome temperature of 0 (the snow), and how much each degree of heat adds (the desert is 2). */
	static final float BASE = 0.06F, PER_HEAT = 0.42F;
	/** Biome temperatures are read within these bounds (frozen peaks are -0.7, deserts, savannas and badlands 2). */
	static final float MIN_HEAT = -0.5F, MAX_HEAT = 2.0F;
	/** Natural spread: melanin varies this much either side of the land's lean. */
	static final float SPREAD = 0.15F;
	/** The chance a villager's family came from elsewhere, and how far that elsewhere is (in biome temperature). */
	static final float NEWCOMER_CHANCE = 0.15F, NEWCOMER_SHIFT = 0.9F;
	/** Highlanders are a little paler: melanin lost per 128 blocks above sea level (MCA's own is 0.2). */
	static final float PER_ALTITUDE = 0.15F;

	private SkinTones() {
	}

	/** A melanin gene (0 pale .. 1 dark) for someone born at {@code pos}. */
	public static float melanin(Level level, BlockPos pos, RandomSource random) {
		float heat = Mth.clamp(level.getBiome(pos).value().getBaseTemperature(), MIN_HEAT, MAX_HEAT);
		if (random.nextFloat() < NEWCOMER_CHANCE) {
			heat = Mth.clamp(heat + (random.nextFloat() * 2 - 1) * NEWCOMER_SHIFT, MIN_HEAT, MAX_HEAT);
		}
		float altitude = (pos.getY() - level.getSeaLevel()) / 128.0F;
		float melanin = BASE + heat * PER_HEAT + (random.nextFloat() * 2 - 1) * SPREAD - altitude * PER_ALTITUDE;
		return Mth.clamp(melanin, 0, 1);
	}
}
