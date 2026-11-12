package com.AdamNew.dynamicwaters.shore;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public final class WaterPlants {
    private static final double LOWLAND_CALM = 0.7;

    private static final double CALM_SLOPE = 0.02;
    private static final double MOUNTAIN_CALM = 0.5;

    private static final int ANY_DEPTH = 64;

    private static final int SURFACE_SCAN = 32;

    private WaterPlants() {
    }

    public static void plant(ChunkAccess chunk, HydrologyManager.RiverCarving[] carvings, int[] originalY,
            BitSet[] protectedMap, int minH, int startX, int startZ, BiomeSource biomeSource, Climate.Sampler sampler) {
        if (!Config.WATER_PLANTS.get()) return;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        final int[] kind = new int[256];
        final WaterPlantSet[] sets = new WaterPlantSet[256];
        Arrays.fill(kind, -1);
        final Map<Long, Holder<Biome>> landBiomes = new HashMap<>();
        boolean any = false;
        for (int i = 0; i < 256; i++) {
            final HydrologyManager.RiverCarving c = carvings[i];
            if (c == null) continue;
            final int k = kindOf(c);
            if (k < 0) continue;
            final WaterPlantSet set = WaterPlantSets.setFor(biomeAt(chunk, i, c, originalY[i], startX, startZ, biomeSource, sampler, landBiomes));
            if (set == null || !set.enabled) continue;
            kind[i] = k;
            sets[i] = set;
            any = true;
        }
        if (!any) return;

        for (int i = 0; i < 256; i++) {
            if (kind[i] < 0 || carvings[i].isWatershedWall) continue;
            for (WaterPlant p : sets[i].plants(kind[i])) {
                if (p.at == WaterPlant.At.BED && onBed(chunk, carvings[i], p, startX + (i & 15), startZ + (i >> 4),
                        protectedMap[i], minH, pos)) {
                    break;
                }
            }
        }

        final int[] surface = new int[256];
        final int[] depth = new int[256];
        Arrays.fill(surface, Integer.MIN_VALUE);
        for (int i = 0; i < 256; i++) {
            final HydrologyManager.RiverCarving c = carvings[i];
            if (c == null || !c.modifies || c.isWatershedWall) continue;
            final int wx = startX + (i & 15), wz = startZ + (i >> 4);
            final int y = c.waterLevel;
            if (!still(chunk.getBlockState(pos.set(wx, y, wz))) || !chunk.getBlockState(pos.set(wx, y + 1, wz)).isAir()) continue;
            int d = 1;
            while (d <= SURFACE_SCAN && water(chunk.getBlockState(pos.set(wx, y - d, wz)))) d++;
            surface[i] = y;
            depth[i] = d;
        }
        final int[] calmSurface = surface.clone();
        final int[] anySurface = surface.clone();
        for (int i = 0; i < 256; i++) {
            if (calmSurface[i] != Integer.MIN_VALUE && (!calm(carvings[i]) || stepNear(i, calmSurface))) calmSurface[i] = Integer.MIN_VALUE;
            if (anySurface[i] != Integer.MIN_VALUE && stepNear(i, anySurface)) anySurface[i] = Integer.MIN_VALUE;
        }

        for (int i = 0; i < 256; i++) {
            if (kind[i] < 0) continue;
            final int wx = startX + (i & 15), wz = startZ + (i >> 4);
            for (WaterPlant p : sets[i].plants(kind[i])) {
                if (p.at != WaterPlant.At.SURFACE) continue;
                final int y = (p.calm ? calmSurface : anySurface)[i];
                if (y == Integer.MIN_VALUE || !inDepth(p, depth[i]) || !drawn(p, wx, wz)) continue;
                if (frozen(chunk, pos.set(wx, y + 1, wz)) || isProtected(protectedMap[i], y + 1, minH)) continue;
                chunk.setBlockState(pos.set(wx, y + 1, wz), dry(p.state), 3);
                WatershedStats.waterPlants.increment();
                break;
            }
        }

        for (int i = 0; i < 256; i++) {
            if (carvings[i] == null || calmSurface[i] != Integer.MIN_VALUE) continue;
            final int x = i & 15, z = i >> 4, wx = startX + x, wz = startZ + z;

            int beside = -1;
            for (int k = 0; k < 4; k++) {
                final int n = neighbour(x, z, k);
                if (n < 0 || kind[n] < 0) continue;
                final int level = Math.max(calmSurface[n], anySurface[n]);
                if (level != Integer.MIN_VALUE && (beside < 0 || level > Math.max(calmSurface[beside], anySurface[beside]))) beside = n;
            }
            if (beside < 0) continue;
            for (WaterPlant p : sets[beside].plants(kind[beside])) {
                if (p.at != WaterPlant.At.SHORE) continue;
                final int[] waters = p.calm ? calmSurface : anySurface;
                int level = Integer.MIN_VALUE;
                for (int k = 0; k < 4; k++) {
                    final int n = neighbour(x, z, k);
                    if (n >= 0) level = Math.max(level, waters[n]);
                }
                if (level == Integer.MIN_VALUE || !drawn(p, wx, wz)) continue;
                if (onBank(chunk, p, wx, wz, level, protectedMap[i], minH, pos)) break;
            }
        }
    }

    private static boolean onBed(ChunkAccess chunk, HydrologyManager.RiverCarving c, WaterPlant p, int wx, int wz,
            BitSet bits, int minH, BlockPos.MutableBlockPos pos) {
        if (!drawn(p, wx, wz) || p.calm && !calm(c)) return false;
        int y = c.waterLevel;
        if (!still(chunk.getBlockState(pos.set(wx, y, wz)))) return false;
        while (y > minH && (still(chunk.getBlockState(pos.set(wx, y - 1, wz))) || seagrass(chunk.getBlockState(pos.set(wx, y - 1, wz))))) {
            y--;
        }
        final int bed = y - 1;
        final int depth = c.waterLevel - bed;
        if (!inDepth(p, depth)) return false;
        if (!chunk.getBlockState(pos.set(wx, bed, wz)).isFaceSturdy(chunk, pos, Direction.UP)) return false;

        int placed = 0;
        if (p.isKelp()) {
            final BlockState stalk = Blocks.KELP_PLANT.defaultBlockState();
            final BlockState tip = Blocks.KELP.defaultBlockState().setValue(BlockStateProperties.AGE_25, 25);
            final double u = Patches.hash01(wz, p.salt + 2, wx);
            final int height = Math.max(3, depth - 1 - (int) (u * u * (depth - 3)));
            for (int k = 1; k <= height; k++) {
                final int ky = bed + k;
                if (isProtected(bits, ky, minH)) break;
                chunk.setBlockState(pos.set(wx, ky, wz), k == height ? tip : stalk, 3);
                placed++;
            }
        } else if (p.isDouble()) {
            if (depth < 2 || isProtected(bits, bed + 1, minH) || isProtected(bits, bed + 2, minH)) return false;
            final BlockState lower = wet(p.state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
            final BlockState upper = wet(p.state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            chunk.setBlockState(pos.set(wx, bed + 1, wz), lower, 3);
            chunk.setBlockState(pos.set(wx, bed + 2, wz), upper, 3);
            placed = 2;
        } else {
            BlockState s = wet(p.state);
            if (p.isPickles()) s = s.setValue(SeaPickleBlock.PICKLES, 1 + (int) (Patches.hash01(wx, p.salt + 3, wz) * 4.0));

            final int tall = Math.min(height(p, wx, wz), depth);
            for (int k = 1; k <= tall; k++) {
                final int ky = bed + k;
                if (isProtected(bits, ky, minH)) break;
                chunk.setBlockState(pos.set(wx, ky, wz), s, 3);
                placed++;
            }
        }
        if (placed > 0) WatershedStats.waterPlants.increment();
        return placed > 0;
    }

    private static boolean onBank(ChunkAccess chunk, WaterPlant p, int wx, int wz, int level, BitSet bits, int minH,
            BlockPos.MutableBlockPos pos) {
        final BlockState ground = chunk.getBlockState(pos.set(wx, level, wz));
        if (!ground.is(BlockTags.DIRT) && !ground.is(BlockTags.SAND)) return false;
        if (frozen(chunk, pos.set(wx, level + 1, wz))) return false;
        final BlockState s = dry(p.state);
        final int tall = height(p, wx, wz);
        int placed = 0;
        for (int k = 1; k <= tall; k++) {
            final int ky = level + k;
            if (!chunk.getBlockState(pos.set(wx, ky, wz)).isAir() || isProtected(bits, ky, minH)) break;
            chunk.setBlockState(pos, s, 3);
            placed++;
        }
        if (placed > 0) WatershedStats.waterPlants.increment();
        return placed > 0;
    }

    private static boolean drawn(WaterPlant p, int x, int z) {
        if (p.patchSize > 0 && Patches.blob(x, z, p.patchSize, p.salt) < 1.0 - p.patchShare) return false;
        final double own = Patches.hash01(x, p.salt + 1, z);
        if (own >= p.chance) return false;
        if (p.alone) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx != 0 || dz != 0) && Patches.hash01(x + dx, p.salt + 1, z + dz) < Math.min(own, p.chance)) return false;
                }
            }
        }
        return true;
    }

    private static boolean inDepth(WaterPlant p, int depth) {
        return depth >= p.minDepth && (depth <= p.maxDepth || p.maxDepth >= ANY_DEPTH);
    }

    private static int height(WaterPlant p, int x, int z) {
        return p.minHeight + (int) (Patches.hash01(z, p.salt + 2, x) * (p.maxHeight - p.minHeight + 1));
    }

    static int kindOf(HydrologyManager.RiverCarving c) {
        if (c.isLake) return WaterPlantSet.LAKE;
        if (c.isMountainRiver || c.isWatershed) return WaterPlantSet.MOUNTAIN;
        if (c.isMainRiverWater) return WaterPlantSet.LOWLAND;
        return -1;
    }

    private static boolean calm(HydrologyManager.RiverCarving c) {
        if (c.isLake) return true;
        if (c.isMountainRiver) {
            return c.channelSlope <= CALM_SLOPE && c.channelWidth > 0.0 && c.distanceFromCenter >= MOUNTAIN_CALM * c.channelWidth;
        }
        if (c.isMainRiverWater) {
            return c.deltaWater || c.waterRadius > 0.0 && c.distanceFromCenter >= LOWLAND_CALM * c.waterRadius;
        }
        return false;
    }

    private static Holder<Biome> biomeAt(ChunkAccess chunk, int i, HydrologyManager.RiverCarving c, int originalY,
            int startX, int startZ, BiomeSource biomeSource, Climate.Sampler sampler, Map<Long, Holder<Biome>> cache) {
        final int x = i & 15, z = i >> 4, ws = c.waterLevel;
        Holder<Biome> biome = ShoreSkin.landBiome(chunk.getNoiseBiome(x >> 2, ws >> 2, z >> 2),
                (startX + x) >> 2, ws >> 2, (startZ + z) >> 2, biomeSource, sampler, cache);
        final int ground = originalY + 1;
        if (ground > ws && biome.is(com.AdamNew.dynamicwaters.platform.ModTags.IS_UNDERGROUND)) {
            biome = ShoreSkin.landBiome(chunk.getNoiseBiome(x >> 2, ground >> 2, z >> 2),
                    (startX + x) >> 2, ground >> 2, (startZ + z) >> 2, biomeSource, sampler, cache);
        }
        return biome;
    }

    private static int neighbour(int x, int z, int k) {
        final int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
        return nx < 0 || nz < 0 || nx > 15 || nz > 15 ? -1 : nx + nz * 16;
    }

    private static boolean stepNear(int i, int[] surface) {
        final int x = i & 15, z = i >> 4, level = surface[i];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int nx = x + dx, nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx > 15 || nz > 15) continue;
                final int n = surface[nx + nz * 16];
                if (n != Integer.MIN_VALUE && n != level) return true;
            }
        }
        return false;
    }

    private static BlockState wet(BlockState s) {
        return s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, true) : s;
    }

    private static BlockState dry(BlockState s) {
        return s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, false) : s;
    }

    private static boolean frozen(ChunkAccess chunk, BlockPos pos) {
        return chunk.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2).value().coldEnoughToSnow(pos, com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel());
    }

    private static boolean still(BlockState s) {
        return s.is(Blocks.WATER) && s.getFluidState().isSource();
    }

    private static boolean water(BlockState s) {
        return !s.getFluidState().isEmpty();
    }

    private static boolean seagrass(BlockState s) {
        return s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS);
    }

    private static boolean isProtected(BitSet bits, int y, int minH) {
        return bits != null && y >= minH && bits.get(y - minH);
    }
}
