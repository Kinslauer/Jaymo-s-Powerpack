package com.jaymoskingdoms.glowwood;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A fairy ring: {@code fungus} picks the one species of the whole ring, {@code radius} its size.
 * Ported from Spore & Stem (Apache-2.0).
 */
public record FairyRingConfiguration(BlockStateProvider fungus, IntProvider radius) {
	public static final MapCodec<FairyRingConfiguration> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BlockStateProvider.DIRECT_CODEC.fieldOf("fungus").forGetter(FairyRingConfiguration::fungus),
			net.minecraft.util.valueproviders.IntProviders.codec(2, 7).fieldOf("radius").forGetter(FairyRingConfiguration::radius)
	).apply(instance, FairyRingConfiguration::new));
}
