package com.AdamNew.dynamicwaters.shore;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

public final class ShoreSkin {
    private static final int SCAN_BELOW_WATER = 24;

    private static final int FLATS_TOP = 3;
    private static final int FLATS_PATCH = 12;
    private static final double FLATS_SHORE = 3.5;
    private static final double FLATS_BAR = 0.8;

    private static final int SHORE_WAVE = 7;

    private static final int BIOME_WANDER = 3;
    private static final int BIOME_WANDER_SIZE = 9;

    private static final double WIDE_RIVER = 10.0;

    private static final double TORRENT_SLOPE = 0.08;

    private static final int SORT_SALT = 0x50F7, PLACE_SALT = 0x51A7;

    private ShoreSkin() {
    }

    public static void apply(ChunkAccess chunk, HydrologyManager.RiverCarving[] carvings, int[] originalY, boolean[] repaired,
            BitSet[] protectedMap, int minH, int startX, int startZ, BiomeSource biomeSource, Climate.Sampler sampler) {
        if (!Config.RIVERBANK_PALETTES.get()) return;

        final int[] waterAt = new int[256];
        for (int i = 0; i < 256; i++) {
            final HydrologyManager.RiverCarving c = carvings[i];
            waterAt[i] = c == null ? Integer.MIN_VALUE
                    : c.modifies && c.skinLevel != Integer.MIN_VALUE ? c.skinLevel
                    : !c.modifies && repaired[i] ? c.waterLevel : Integer.MIN_VALUE;
        }

        final int[] surface = new int[256];
        final boolean[] wet = new boolean[256];
        Arrays.fill(surface, Integer.MIN_VALUE);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (int i = 0; i < 256; i++) {
            final HydrologyManager.RiverCarving c = carvings[i];
            if (waterAt[i] == Integer.MIN_VALUE) continue;
            final int wx = startX + (i & 15), wz = startZ + (i >> 4);
            int top = Math.max(originalY[i], c.targetY) + 1;
            if (c.cavernRoofY > -999 && c.cavernRoofY < top) top = c.cavernRoofY;
            final int bottom = Math.max(minH + 1, waterAt[i] - SCAN_BELOW_WATER);
            for (int y = top; y >= bottom; y--) {
                if (solid(chunk.getBlockState(pos.set(wx, y, wz)))) {
                    surface[i] = y;
                    wet[i] = !chunk.getBlockState(pos.set(wx, y + 1, wz)).getFluidState().isEmpty();
                    any = true;
                    break;
                }
            }
        }
        if (!any) return;

        final Map<Long, Holder<Biome>> landBiomes = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            final int sy = surface[i];
            if (sy == Integer.MIN_VALUE) continue;
            final HydrologyManager.RiverCarving c = carvings[i];
            final int x = i & 15, z = i >> 4, wx = startX + x, wz = startZ + z;
            final int ws = waterAt[i];

            final boolean cut = sy < originalY[i];
            final boolean built = c.isWatershedWall && sy > originalY[i];

            final boolean flat = c.deltaFlat && !wet[i] && sy <= ws + FLATS_TOP;
            if (!wet[i] && !cut && !built && !flat) continue;
            if (flat && !bare(wx, wz, c.deltaShore)) continue;

            final int jx = Mth.clamp(x + wander(wx, wz, 0x51), 0, 15);
            final int jz = Mth.clamp(z + wander(wx, wz, 0x7D), 0, 15);

            final int by = Math.max(sy, ws);
            Holder<Biome> biome = landBiome(chunk.getNoiseBiome(jx >> 2, by >> 2, jz >> 2),
                    (startX + jx) >> 2, by >> 2, (startZ + jz) >> 2, biomeSource, sampler, landBiomes);
            final int ground = originalY[i] + 1;
            if (ground > by && (c.cavernRoofY <= -999 || c.cavernRoofY >= originalY[i])
                    && biome.is(com.AdamNew.dynamicwaters.platform.ModTags.IS_UNDERGROUND)) {
                biome = landBiome(chunk.getNoiseBiome(jx >> 2, ground >> 2, jz >> 2),
                        (startX + jx) >> 2, ground >> 2, (startZ + jz) >> 2, biomeSource, sampler, landBiomes);
            }
            final RiverbankPalette style = RiverbankPalettes.styleFor(biome);
            if (style == null || !style.enabled) continue;

            BlockState skin = null;
            if (flat) {
                skin = pick(style, RiverbankPalette.FLATS, wx, wz);
            } else if (wet[i]) {
                if (ws - sy <= 1 && !c.pointBar) skin = pick(style, RiverbankPalette.SHORE, wx, wz);
                if (skin == null) {
                    final double run = energy(c, ws - sy, stepNear(i, ws, surface, wet, waterAt)) * (c.pointBar ? 0.5 : 1.0);
                    skin = pick(style, sortDraw(wx, wz, style.patchSize) < run ? RiverbankPalette.RAPIDS : RiverbankPalette.SILT, wx, wz);
                }
            } else {
                final int h = sy - ws;
                if (c.pointBar && h >= -1) {
                    skin = pick(style, RiverbankPalette.BARS, wx, wz);
                } else if (h < -1) {
                    continue;
                } else {
                    skin = pick(style, h <= style.shoreHeight + shoreWaver(wx, wz) ? RiverbankPalette.SHORE : RiverbankPalette.SLOPE, wx, wz);
                }
            }
            if (skin == null) continue;

            final BitSet bits = protectedMap[i];
            if (isProtected(bits, sy, minH)) continue;
            final BlockState current = chunk.getBlockState(pos.set(wx, sy, wz));
            if (current.is(Blocks.BEDROCK)) continue;
            final boolean loose = skin.getBlock() instanceof FallingBlock;
            if (loose && !solid(chunk.getBlockState(pos.set(wx, sy - 1, wz)))) continue;
            if (!skin.equals(current)) chunk.setBlockState(pos.set(wx, sy, wz), skin, 3);
            WatershedStats.bankSkins.increment();
            if (loose) continue;

            int exposure = 0;
            for (int k = 0; k < 4; k++) {
                final int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx > 15 || nz > 15) continue;
                final int ni = nx + nz * 16;
                final int ns = surface[ni] != Integer.MIN_VALUE ? surface[ni]
                        : chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, nx, nz);
                exposure = Math.max(exposure, sy - ns);
            }
            final int depth = Mth.clamp(exposure, 2, 8);
            for (int d = 1; d <= depth; d++) {
                final int y = sy - d;
                final BlockState below = chunk.getBlockState(pos.set(wx, y, wz));
                if (below.isAir() || !below.getFluidState().isEmpty() || below.is(Blocks.BEDROCK)) break;
                if (isProtected(bits, y, minH)) continue;
                if (!below.equals(skin)) chunk.setBlockState(pos.set(wx, y, wz), skin, 3);
            }
        }
    }

    private static BlockState pick(RiverbankPalette style, int place, int x, int z) {
        final RiverbankPalette.Mix mix = style.place(place);
        return mix == null ? null : mix.pick(x, z, spotSize(style.patchSize, place), placeSalt(place));
    }

    private static double energy(HydrologyManager.RiverCarving c, int depth, boolean step) {
        if (step) return 1.0;
        if (c.isLake) return lakeRun(depth);
        if (c.isMountainRiver) {
            final double t = c.channelWidth > 0.0 ? Math.min(1.0, c.distanceFromCenter / c.channelWidth) : 1.0;
            final double slack = lowlandRun(t, c.channelWidth >= WIDE_RIVER);
            return slack + (streamRun(depth) - slack) * Math.min(1.0, c.channelSlope / TORRENT_SLOPE);
        }
        if (c.isMainRiverWater) {
            if (c.deltaWater) return 0.1;
            final double t = c.waterRadius > 0.0 ? Math.min(1.0, c.distanceFromCenter / c.waterRadius) : 1.0;
            return lowlandRun(t, c.waterRadius >= WIDE_RIVER);
        }
        return 0.3;
    }

    public static double lowlandRun(double t, boolean wide) {
        final double run = 0.12 + 0.38 * (1.0 - t * t);
        return wide ? run * 0.7 : run;
    }

    public static double streamRun(int depth) {
        return depth >= 4 ? 0.55 : 0.8;
    }

    public static double lakeRun(int depth) {
        return depth <= 1 ? 0.35 : 0.08;
    }

    private static boolean stepNear(int i, int level, int[] surface, boolean[] wet, int[] waterAt) {
        final int x = i & 15, z = i >> 4;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                final int nx = x + dx, nz = z + dz;
                if ((dx == 0 && dz == 0) || nx < 0 || nz < 0 || nx > 15 || nz > 15) continue;
                final int ni = nx + nz * 16;
                if (!wet[ni] || surface[ni] == Integer.MIN_VALUE || waterAt[ni] == Integer.MIN_VALUE) continue;
                if (waterAt[ni] != level) return true;
            }
        }
        return false;
    }

    public static double sortDraw(int x, int z, int patchSize) {
        return Patches.blob(x, z, patchSize + patchSize / 2, SORT_SALT);
    }

    public static int spotSize(int p, int place) {
        return switch (place) {
            case RiverbankPalette.RAPIDS -> Math.max(2, (p * 2 + 2) / 3);
            case RiverbankPalette.SILT -> p + p / 2;
            case RiverbankPalette.FLATS -> 2 * p;
            default -> p;
        };
    }

    public static int placeSalt(int place) {
        return PLACE_SALT + place * 0x9E3;
    }

    public static int shoreWaver(int x, int z) {
        return (int) Math.round((Patches.smooth(x, z, SHORE_WAVE, 0x5E0) - 0.5) * 2.6);
    }

    private static int wander(int x, int z, int salt) {
        return (int) Math.round((Patches.smooth(x, z, BIOME_WANDER_SIZE, salt) - 0.5) * 2.0 * BIOME_WANDER);
    }

    static Holder<Biome> landBiome(Holder<Biome> here, int qx, int qy, int qz,
            BiomeSource biomeSource, Climate.Sampler sampler, Map<Long, Holder<Biome>> cache) {
        if (biomeSource == null || sampler == null || !(here.is(Biomes.RIVER) || here.is(Biomes.FROZEN_RIVER))) return here;
        final long key = ((long) qx & 0x3FFFFFL) | (((long) qz & 0x3FFFFFL) << 22) | (((long) qy & 0xFFFFFL) << 44);
        Holder<Biome> land = cache.get(key);
        if (land == null) {
            final boolean was = HydrologyManager.isGeneratingRivers();
            HydrologyManager.setGeneratingRivers(true);
            try {
                land = com.AdamNew.dynamicwaters.ClimateAccess.biome(biomeSource, qx, qy, qz, sampler);
            } finally {
                HydrologyManager.setGeneratingRivers(was);
            }
            cache.put(key, land);
        }
        return land;
    }

    private static boolean solid(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && s.canOcclude();
    }

    private static boolean isProtected(BitSet bits, int y, int minH) {
        return bits != null && y >= minH && bits.get(y - minH);
    }

    private static boolean bare(int x, int z, double shore) {
        final double n = Patches.smooth(x, z, FLATS_PATCH, 0x6F);
        return shore <= FLATS_SHORE * (0.4 + 1.2 * n) || n > FLATS_BAR;
    }
}
