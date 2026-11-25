package com.jaymoskingdoms.glowwood;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A Huge Glowcap: a slender stem (sometimes with a gentle lean halfway up) under a wide, flat parasol. The cap has a
 * raised centre and a rim that droops one block lower, and luminous gills hang in curtains beneath it.
 * Ported from Spore & Stem (Apache-2.0).
 */
public class HugeGlowcapFeature extends HugeFungusFeature {

	public static final MapCodec<HugeGlowcapFeature> CODEC = HugeFungusConfiguration.CODEC.xmap(HugeGlowcapFeature::new, HugeFungusFeature::config);

	public HugeGlowcapFeature(HugeFungusConfiguration config) {
		super(config);
	}

	@Override
	public MapCodec<HugeGlowcapFeature> codec() {
		return CODEC;
	}

	@Override
	protected void shape(FungusPlan plan, RandomSource random, int height, int radius) {
		int cx = 0;
		int cz = 0;
		int leanAt = height >= 7 && random.nextFloat() < 0.45F ? height / 2 + random.nextInt(2) : -1;
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (int y = 0; y < height; y++) {
			if (y == leanAt) {
				plan.stem(cx, y, cz);
				cx += lean.getStepX();
				cz += lean.getStepZ();
			}
			plan.stem(cx, y, cz);
		}
		int top = height;
		int reach = radius + 1;
		// The flat parasol.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (inDisc(dx, dz, radius, 0.6)) {
					plan.cap(cx + dx, top, cz + dz);
				}
			}
		}
		// The raised centre (umbo).
		double umbo = radius >= 4 ? 2.0 : 1.2;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx * dx + dz * dz <= umbo * umbo + 0.5) {
					plan.cap(cx + dx, top + 1, cz + dz);
				}
			}
		}
		if (radius >= 4 && random.nextBoolean()) {
			plan.cap(cx, top + 2, cz);
		}
		// The drooping rim, one block lower, all the way round.
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				if (!inDisc(dx, dz, radius, 0.6) && inDisc(dx, dz, reach, 0.4)) {
					plan.cap(cx + dx, top - 1, cz + dz);
				}
			}
		}
		// Gill curtains under the parasol, inside the rim.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int d2 = dx * dx + dz * dz;
				if (d2 >= 2 && inDisc(dx, dz, radius, 0.6) && !plan.isCap(cx + dx, top - 1, cz + dz)
						&& random.nextFloat() < 0.42F) {
					plan.hanger(cx + dx, top - 1, cz + dz, random.nextFloat() < 0.35F ? 2 : 1);
				}
			}
		}
	}

	@Override
	protected BlockState stem() {
		return GlowWoods.GLOWCAP_STEM.defaultBlockState();
	}

	@Override
	protected BlockState cap() {
		return GlowWoods.GLOWCAP_CAP.defaultBlockState();
	}

	@Override
	protected BlockState hanger() {
		return GlowWoods.GLOWCAP_GILLS.defaultBlockState();
	}

	@Override
	protected BooleanProperty hangerTip() {
		return GlowcapGillsBlock.TIP;
	}
}
