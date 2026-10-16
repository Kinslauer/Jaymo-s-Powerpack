package com.jaymospowers.power.web;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The red symbiote's crater: a meteor crater like {@link WebCraterFeature} (the same bowl, the same site rules), with Red
 * Ooze ({@code WebPower.RED_GOO}) in the middle instead of the black. Far rarer: one in
 * {@link WebSymbioteStrains#RED_CRATER_RARITY} chunks (see {@code worldgen/placed_feature/web_red_crater.json}).
 */
public record WebRedCraterFeature() implements Feature {
	public static final MapCodec<WebRedCraterFeature> CODEC = MapCodec.unit(WebRedCraterFeature::new);

	@Override
	public MapCodec<WebRedCraterFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		return WebPower.RED_GOO != null && WebCraterFeature.carve(level, random, origin, true, WebPower.RED_GOO);
	}
}
