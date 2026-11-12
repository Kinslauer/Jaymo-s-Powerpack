package com.AdamNew.dynamicwaters.shore;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.mixin.SurfaceSystemAccessor;
import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import com.AdamNew.dynamicwaters.platform.ModTags;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public final class Boulders {
    private static final double FAST_SLOPE = 0.04;
    private static final double TORRENT_SLOPE = 0.12;
    private static final double CHANCE = 0.001;

    private static final double LOWLAND_CHANCE = 0.001;
    private static final double LOWLAND_RADIUS_MIN = 2.4;
    private static final double LOWLAND_RADIUS_MAX = 3.4;
    private static final double LOWLAND_LUMP = 0.12;
    private static final double LOWLAND_SINK = 0.9;
    private static final int LOWLAND_DEPTH = 4;
    private static final int LOWLAND_HEIGHT = 7;

    private static final double MIN_HALF_WIDTH = 2.5;

    private static final int MAX_DEPTH = 3;
    private static final int SINGLE_DEPTH = 2;

    private static final int SINGLE_HEIGHT = 3;
    private static final int BIG_HEIGHT = 4;

    private static final int FALL = 3;

    private static final double SINGLE = 0.55;
    private static final double SQUARE = 0.85;
    private static final int SALT = 0xB01D;

    private enum Palette { BADLANDS, DESERT, COLD, DRY, LUSH, TEMPERATE }

    private Boulders() {
    }

    public static void place(ChunkAccess chunk, HydrologyManager.RiverCarving[] carvings, BitSet[] protectedMap,
            int minH, int startX, int startZ, BiomeSource biomeSource, RandomState randomState) {
        if (!Config.RIVER_BOULDERS.get()) return;
        final Climate.Sampler sampler = com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState);
        final SurfaceSystemAccessor bands = (SurfaceSystemAccessor) randomState.surfaceSystem();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final Map<Long, Holder<Biome>> lands = new HashMap<>();

        final int[] surface = new int[256];
        final double[] fast = new double[256];
        final boolean[] lowland = new boolean[256];
        Arrays.fill(surface, Integer.MIN_VALUE);
        for (int i = 0; i < 256; i++) {
            final HydrologyManager.RiverCarving c = carvings[i];
            if (c == null || !c.modifies || c.isWatershedWall) continue;
            final int wx = startX + (i & 15), wz = startZ + (i >> 4);
            final int y = c.waterLevel;
            if (!water(chunk.getBlockState(pos.set(wx, y, wz))) || !chunk.getBlockState(pos.set(wx, y + 1, wz)).isAir()) continue;
            surface[i] = y;
            if (c.isLake) continue;
            if (c.isMainRiverWater) {
                lowland[i] = !c.deltaWater;
            } else if (c.isMountainRiver && c.channelWidth >= MIN_HALF_WIDTH && c.distanceFromCenter <= c.channelWidth - 1.0) {
                fast[i] = Mth.clampedMap(c.channelSlope, FAST_SLOPE, TORRENT_SLOPE, 0.0, 1.0);
            }
        }

        for (int i = 0; i < 256; i++) {
            if (!lowland[i] && fast[i] <= 0.0 || fallNear(i, surface)) continue;

            final int wx = startX + (i & 15), wz = startZ + (i >> 4);
            int bed = surface[i];
            while (bed > minH && water(chunk.getBlockState(pos.set(wx, bed, wz)))) bed--;
            if (surface[i] - bed > (lowland[i] ? LOWLAND_DEPTH : MAX_DEPTH)) continue;
            WatershedStats.fastColumns.increment();
            final int top = lowland[i] ? roundTop(wx, wz, surface[i], bed) : rockTop(wx, wz, surface[i], bed, fast[i]);
            if (top <= bed) continue;
            WatershedStats.rockColumns.increment();

            pos.set(wx, surface[i], wz);
            final Holder<Biome> here = chunk.getNoiseBiome(wx >> 2, surface[i] >> 2, wz >> 2);
            final Palette palette = palette(ShoreSkin.landBiome(here, wx >> 2, surface[i] >> 2, wz >> 2,
                    biomeSource, sampler, lands), pos);
            final BitSet bits = protectedMap[i];
            for (int ky = bed + 1; ky <= top; ky++) {
                if (bits != null && ky >= minH && bits.get(ky - minH)) break;
                chunk.setBlockState(pos.set(wx, ky, wz), stone(wx, ky, wz, palette, ky == top, bands), 3);
            }
        }
    }

    private static int rockTop(int x, int z, int level, int bed, double fast) {
        int top = Integer.MIN_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int sx = x - dx, sz = z - dz;
                if (Patches.hash01(sx, SALT, sz) >= CHANCE * fast) continue;
                final double shape = Patches.hash01(sx, SALT + 1, sz);
                final int rise = rise(sx, sz);
                final boolean own = dx == 0 && dz == 0;
                int here = Integer.MIN_VALUE, tall = SINGLE_HEIGHT;
                if (shape < SINGLE) {
                    if (own && level - bed <= SINGLE_DEPTH) here = rise;
                } else if (shape < SQUARE) {
                    if (dx >= 0 && dz >= 0) {
                        final boolean far = dx == 1 && dz == 1;
                        here = far ? rise - 1 : rise;
                        tall = far ? SINGLE_HEIGHT : BIG_HEIGHT;
                    }
                } else if (own) {
                    here = rise;
                    tall = BIG_HEIGHT;
                } else if (dx == 0 || dz == 0) {
                    here = rise - 1;
                }
                if (here != Integer.MIN_VALUE) top = Math.max(top, Math.min(level + here, bed + tall));
            }
        }
        return top;
    }

    private static int roundTop(int x, int z, int level, int bed) {
        int top = Integer.MIN_VALUE;
        final int reach = (int) Math.ceil(LOWLAND_RADIUS_MAX * (1.0 + LOWLAND_LUMP));
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                final int sx = x - dx, sz = z - dz;
                if (Patches.hash01(sx, SALT + 10, sz) >= LOWLAND_CHANCE) continue;
                final double r = Mth.lerp(Patches.hash01(sx, SALT + 11, sz), LOWLAND_RADIUS_MIN, LOWLAND_RADIUS_MAX);

                final double edge = r * (1.0 + LOWLAND_LUMP
                        * Math.sin(3.0 * Math.atan2(dz, dx) + Patches.hash01(sx, SALT + 12, sz) * Math.PI * 2.0));
                final double d = Math.sqrt(dx * dx + dz * dz) / edge;
                if (d > 1.0) continue;

                final double sink = LOWLAND_SINK * (1.0 + Patches.hash01(sx, SALT + 13, sz));
                final int ball = (int) Math.round(level - sink + r * Math.sqrt(1.0 - d * d));
                top = Math.max(top, Math.min(ball, bed + LOWLAND_HEIGHT));
            }
        }
        return top;
    }

    private static int rise(int x, int z) {
        final double u = Patches.hash01(x, SALT + 2, z);
        return u < 0.6 ? 1 : u < 0.9 ? 0 : -1;
    }

    private static BlockState stone(int x, int y, int z, Palette palette, boolean crown, SurfaceSystemAccessor bands) {
        final double u = Patches.hash01(x + y * 7919, SALT + 3, z);
        return switch (palette) {
            case BADLANDS -> bands.dynamicwaters$getBand(x, y, z);
            case DESERT -> u < 0.6 ? Blocks.SANDSTONE.defaultBlockState()
                    : u < 0.8 ? Blocks.STONE.defaultBlockState()
                    : Blocks.ANDESITE.defaultBlockState();
            case COLD -> u < 0.45 ? Blocks.STONE.defaultBlockState()
                    : u < 0.8 ? Blocks.ANDESITE.defaultBlockState()
                    : Blocks.COBBLESTONE.defaultBlockState();
            case DRY -> u < 0.4 ? Blocks.STONE.defaultBlockState()
                    : u < 0.75 ? Blocks.ANDESITE.defaultBlockState()
                    : Blocks.GRANITE.defaultBlockState();
            case LUSH -> u < (crown ? 0.8 : 0.5) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : u < 0.7 ? Blocks.STONE.defaultBlockState()
                    : u < 0.85 ? Blocks.ANDESITE.defaultBlockState()
                    : Blocks.COBBLESTONE.defaultBlockState();
            case TEMPERATE -> u < (crown ? 0.6 : 0.3) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                    : u < 0.65 ? Blocks.STONE.defaultBlockState()
                    : u < 0.88 ? Blocks.ANDESITE.defaultBlockState()
                    : Blocks.COBBLESTONE.defaultBlockState();
        };
    }

    private static Palette palette(Holder<Biome> land, BlockPos pos) {
        if (land.is(ModTags.IS_BADLANDS)) return Palette.BADLANDS;
        if (land.is(ModTags.IS_DESERT)) return Palette.DESERT;
        final Biome biome = land.value();
        if (biome.coldEnoughToSnow(pos, com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel()) || land.is(ModTags.IS_SNOWY) || land.is(ModTags.IS_ICY)) return Palette.COLD;
        if (land.is(ModTags.IS_JUNGLE) || land.is(ModTags.IS_SWAMP) || land.is(ModTags.IS_LUSH)) return Palette.LUSH;
        if (!biome.hasPrecipitation() || land.is(ModTags.IS_SAVANNA) || land.is(ModTags.IS_DRY_OVERWORLD)) return Palette.DRY;
        return Palette.TEMPERATE;
    }

    private static boolean fallNear(int i, int[] surface) {
        final int x = i & 15, z = i >> 4, level = surface[i];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int nx = x + dx, nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx > 15 || nz > 15) continue;
                final int n = surface[nx + nz * 16];
                if (n != Integer.MIN_VALUE && Math.abs(n - level) >= FALL) return true;
            }
        }
        return false;
    }

    private static boolean water(BlockState s) {
        return !s.getFluidState().isEmpty();
    }
}
