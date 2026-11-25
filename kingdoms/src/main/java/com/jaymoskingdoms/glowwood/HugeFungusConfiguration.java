package com.jaymoskingdoms.glowwood;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;

/**
 * Size of a huge fungus. {@code height} is the stem height, {@code radius} the cap radius. {@code any_ground} lets it
 * stand on any solid block (cave floors); otherwise it needs dirt-like ground, mycelium, moss or Mycelial Soil.
 * Ported from Spore & Stem (Apache-2.0).
 */
public record HugeFungusConfiguration(IntProvider height, IntProvider radius, boolean anyGround) {
	public static final MapCodec<HugeFungusConfiguration> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			net.minecraft.util.valueproviders.IntProviders.codec(0, 32).fieldOf("height").forGetter(HugeFungusConfiguration::height),
			net.minecraft.util.valueproviders.IntProviders.codec(1, 6).fieldOf("radius").forGetter(HugeFungusConfiguration::radius),
			Codec.BOOL.optionalFieldOf("any_ground", false).forGetter(HugeFungusConfiguration::anyGround)
	).apply(instance, HugeFungusConfiguration::new));
}
