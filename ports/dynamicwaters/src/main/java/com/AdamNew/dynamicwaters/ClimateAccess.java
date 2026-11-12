package com.AdamNew.dynamicwaters;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Jaymo's Powers 26.3 port: 1.21.1's {@code RandomState#sampler()} (one shared climate sampler) and
 * {@code BiomeSource#getNoiseBiome(x, y, z, sampler)} are gone. 26.3 builds a climate sampler for a sampler context and
 * picks biomes through a {@code BiomeResolver}. This keeps one uncached sampler per random state, as 1.21.1 did.
 */
public final class ClimateAccess {
    private record Cached(RandomState state, Climate.Sampler sampler) {
    }

    private static volatile Cached cached;

    private ClimateAccess() {
    }

    public static Climate.Sampler sampler(RandomState randomState) {
        Cached c = cached;
        if (c == null || c.state() != randomState) {
            c = new Cached(randomState, randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED));
            cached = c;
        }
        return c.sampler();
    }

    public static Holder<Biome> biome(BiomeSource source, int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return source.createResolver(sampler).getNoiseBiome(quartX, quartY, quartZ);
    }
}
