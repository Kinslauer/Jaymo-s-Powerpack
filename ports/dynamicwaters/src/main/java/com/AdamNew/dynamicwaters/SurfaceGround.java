package com.AdamNew.dynamicwaters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Asks the world's material rules what the top block of a river cut should be. In 26.3 the surface rules became
 * material rules; the context is opened through the access widener instead of reflection.
 */
public final class SurfaceGround {
    private final NoiseBasedChunkGenerator generator;
    private final WorldGenRegion level;
    private final ChunkAccess chunk;
    private final RandomState random;
    private final Supplier<NoiseChunk> noise;

    private MaterialRuleContext context;
    private RuleEvaluator rule;
    private boolean failed;

    public SurfaceGround(NoiseBasedChunkGenerator generator, WorldGenRegion level, ChunkAccess chunk, RandomState random,
            Supplier<NoiseChunk> noise) {
        this.generator = generator;
        this.level = level;
        this.chunk = chunk;
        this.random = random;
        this.noise = noise;
    }

    public BlockState top(int x, int y, int z) {
        if (failed) return null;
        try {
            if (rule == null) {
                final NoiseChunk noiseChunk = noise.get();
                final Function<BlockPos, Holder<Biome>> biomes = level.getBiomeManager()::getBiome;
                context = new MaterialRuleContext(random.surfaceSystem(), random, noiseChunk.volume(),
                        noiseChunk.cachingSamplers(), biomes, new WorldGenerationContext(generator, level), null);
                rule = generator.generatorSettings().value().materialRule().value().compile(context);
            }
            final int lx = x & 15, lz = z & 15;
            context.updateXZ(x, z, gradient(lx, lz, 1, 0), gradient(lx, lz, 0, 1));
            context.updateY(1, 1, Integer.MIN_VALUE, y);
            final BlockState laid = rule.tryApply(x, y, z);
            return laid != null ? laid : generator.generatorSettings().value().defaultBlock();
        } catch (Throwable t) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[Dynamic Waters] Surface rule failed ({}); river cuts here are dressed in grass.", t.toString());
            return null;
        }
    }

    private int gradient(int lx, int lz, int dx, int dz) {
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, Math.min(lx + dx, 15), Math.min(lz + dz, 15))
                - chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, Math.max(lx - dx, 0), Math.max(lz - dz, 0));
    }

    public boolean rock(BlockState state) {
        return state == generator.generatorSettings().value().defaultBlock() || state.is(Blocks.DEEPSLATE);
    }
}
