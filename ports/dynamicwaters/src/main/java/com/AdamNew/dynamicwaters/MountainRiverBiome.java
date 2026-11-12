package com.AdamNew.dynamicwaters;

import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MountainRiverBiome {
    private static final int REACH = 8;
    private static final int NO_WATER = Integer.MIN_VALUE;

    private MountainRiverBiome() {
    }

    public static boolean wanted(WorldGenLevel level) {
        return Config.MOUNTAIN_RIVER_BIOME.get() && Config.ENABLE_watersheds.get() && Config.dynamicNormalRivers()
                && HydrologyManager.riversIn(level.getLevel());
    }

    public static void paint(WorldGenLevel level, ChunkAccess chunk, Holder<Biome> river, Holder<Biome> frozen) {
        final long t0 = System.nanoTime();
        final ChunkPos pos = chunk.getPos();
        final int qx0 = QuartPos.fromBlock(pos.getMinBlockX()), qz0 = QuartPos.fromBlock(pos.getMinBlockZ());
        @SuppressWarnings("unchecked")
        final Holder<Biome>[] biome = new Holder[16];
        final int[] from = new int[16], to = new int[16];
        final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE, columns = 0;
        for (int i = 0; i < 16; i++) {
            final int x = pos.getMinBlockX() + (i & 3) * 4 + 2, z = pos.getMinBlockZ() + (i >> 2) * 4 + 2;
            final int y = waterTop(chunk, x, z, at);
            if (y == NO_WATER) continue;
            final Holder<Biome> land = chunk.getNoiseBiome(qx0 + (i & 3), QuartPos.fromBlock(y), qz0 + (i >> 2));
            if (land.is(BiomeTags.IS_RIVER) || land.is(BiomeTags.IS_OCEAN)) continue;
            if (!HydrologyManager.inMountainRiverMiddle(x + 0.5, y, z + 0.5)) continue;
            biome[i] = frozen != null && land.value().coldEnoughToSnow(at.set(x, y, z), com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel()) ? frozen : river;
            from[i] = QuartPos.fromBlock(y - REACH);
            to[i] = QuartPos.fromBlock(y + REACH);
            lowest = Math.min(lowest, from[i]);
            highest = Math.max(highest, to[i]);
            columns++;
        }
        if (columns > 0) {
            final int s0 = Math.max(chunk.getMinSectionY(), QuartPos.toSection(lowest));
            final int s1 = Math.min((chunk.getMaxSectionY() + 1) - 1, QuartPos.toSection(highest));
            for (int s = s0; s <= s1; s++) {
                final LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(s));
                section.fillBiomesFromNoise((qx, qy, qz) -> {
                    final int lx = qx & 3, lz = qz & 3, i = lx + lz * 4;
                    return biome[i] != null && qy >= from[i] && qy <= to[i] ? biome[i] : section.getNoiseBiome(lx, qy & 3, lz);
                }, qx0, QuartPos.fromSection(s), qz0);
            }
            WatershedStats.riverBiomeChunks.increment();
            WatershedStats.riverBiomeColumns.add(columns);
        }
        WatershedStats.riverBiomeNanos.add(System.nanoTime() - t0);
        WatershedStats.riverBiomeAsked.increment();
    }

    private static int waterTop(ChunkAccess chunk, int x, int z, BlockPos.MutableBlockPos at) {
        final int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = top; y >= top - 2; y--) {
            at.set(x, y, z);
            if (chunk.getFluidState(at).is(FluidTags.WATER) || chunk.getBlockState(at).is(Blocks.ICE)) return y;
        }
        return NO_WATER;
    }
}
