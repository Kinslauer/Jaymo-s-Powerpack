package com.jaymoskingdoms.glowwood;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Huge Inkcap (a shaggy mane): a tall, thin stem running up inside a tall bell. The bell is rounded at the tip,
 * hollow, and flares out one block at its bottom edge, where the ink drips.
 * Ported from Spore & Stem (Apache-2.0).
 */
public class HugeInkcapFeature extends HugeFungusFeature {

	public static final MapCodec<HugeInkcapFeature> CODEC = HugeFungusConfiguration.CODEC.xmap(HugeInkcapFeature::new, HugeFungusFeature::config);

	public HugeInkcapFeature(HugeFungusConfiguration config) {
		super(config);
	}

	@Override
	public MapCodec<HugeInkcapFeature> codec() {
		return CODEC;
	}

	@Override
	protected void shape(FungusPlan plan, RandomSource random, int height, int radius) {
		int bell = 5 + (height >= 10 ? 1 : 0) + (radius >= 3 ? 1 : 0);
		int top = height + 1;
		int inner = radius - 1;
		// A rounded tip one step narrower than the bell, then the solid crown at full width.
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (inDisc(dx, dz, radius - 1, 0.5)) {
					plan.cap(dx, top, dz);
				}
				if (inDisc(dx, dz, radius, 0.5)) {
					plan.cap(dx, top - 1, dz);
				}
			}
		}
		// The hollow bell.
		for (int i = 2; i < bell; i++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (inDisc(dx, dz, radius, 1.0) && !inDisc(dx, dz, inner, 1.0)) {
						plan.cap(dx, top - i, dz);
					}
				}
			}
		}
		// The flared edge, a little ragged.
		int flare = radius + 1;
		for (int dx = -flare; dx <= flare; dx++) {
			for (int dz = -flare; dz <= flare; dz++) {
				if (inDisc(dx, dz, flare, 0.5) && !inDisc(dx, dz, radius, 1.0) && random.nextFloat() > 0.15F) {
					plan.cap(dx, top - bell, dz);
				}
			}
		}
		for (int y = 0; y < top - 1; y++) {
			plan.stem(0, y, 0);
		}
	}

	@Override
	protected BlockState stem() {
		return GlowWoods.INKCAP_STEM.defaultBlockState();
	}

	@Override
	protected BlockState cap() {
		return GlowWoods.INKCAP_CAP.defaultBlockState();
	}
}
