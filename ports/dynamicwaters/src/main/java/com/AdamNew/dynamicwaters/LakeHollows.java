package com.AdamNew.dynamicwaters;

import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import com.AdamNew.dynamicwaters.terrain.WatershedLake;
import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

public final class LakeHollows {
    public enum Kind {
        NONE,

        FLOODED,

        OPEN
    }

    public static final int LAKE_HOLLOW_COLUMNS = 128;

    public static final int LAKE_HOLLOW_REACH = 3;

    public static final int LAKE_HOLD_DEPTH = 8;

    private static final int[] DX4 = { 1, -1, 0, 0 };
    private static final int[] DZ4 = { 0, 0, 1, -1 };

    private static final int MEMO_LIMIT = 1 << 16;

    private LakeHollows() {
    }

    private static final class Ground {
        int startX, startZ;
        int[] heights;
        long generation = -1;
        final Long2IntOpenHashMap sampled = new Long2IntOpenHashMap();
        final Long2ObjectOpenHashMap<Verdict> verdicts = new Long2ObjectOpenHashMap<>();
    }

    private record Verdict(WatershedLake lake, Kind kind) {
    }

    private static final ThreadLocal<Ground> GROUND = ThreadLocal.withInitial(Ground::new);

    public static void setChunkGround(int startX, int startZ, int[] heights) {
        final Ground g = GROUND.get();
        g.startX = startX;
        g.startZ = startZ;
        g.heights = heights;
    }

    public static void clearChunkGround() {
        GROUND.get().heights = null;
    }

    private static Ground ground() {
        final Ground g = GROUND.get();
        final long generation = HydrologyManager.regionGeneration();
        if (g.generation != generation) {
            g.sampled.clear();
            g.verdicts.clear();
            g.generation = generation;
        }
        return g;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int groundAt(int x, int z, ChunkGenerator generator, RandomState randomState) {
        final Ground g = ground();
        final int[] hs = g.heights;
        if (hs != null) {
            final int lx = x - g.startX, lz = z - g.startZ;
            if (lx >= 0 && lz >= 0 && lx < 16 && lz < 16) return hs[lx + lz * 16];
        }
        final long key = pack(x, z);
        int v = g.sampled.getOrDefault(key, Integer.MIN_VALUE);
        if (v == Integer.MIN_VALUE) {
            v = CoarseHeightField.surfaceAt(x, z, generator, randomState) - 1;
            if (g.sampled.size() >= MEMO_LIMIT) g.sampled.clear();
            g.sampled.put(key, v);
        }
        return v;
    }

    static Kind kind(WatershedLake lake, int x, int z, int ground, int lipDepth,
            ChunkGenerator generator, RandomState randomState) {
        final Ground g = ground();
        final long start = pack(x, z);
        final Verdict known = g.verdicts.get(start);
        if (known != null && known.lake() == lake) return known.kind();

        final int waterY = lake.waterY;
        final LongArrayList members = new LongArrayList();
        final LongOpenHashSet seen = new LongOpenHashSet();
        members.add(start);
        seen.add(start);
        boolean touches = false, open = !lake.near(x, z, LAKE_HOLLOW_REACH);
        boolean beyondLip = ground < waterY - lipDepth || !lake.covers(x, z);
        for (int k = 0; k < members.size() && !open; k++) {
            final long m = members.getLong(k);
            final int mx = (int) (m >> 32), mz = (int) m;
            for (int d = 0; d < 4; d++) {
                final int nx = mx + DX4[d], nz = mz + DZ4[d];
                final long nk = pack(nx, nz);
                if (!seen.add(nk)) continue;
                final int gn = groundAt(nx, nz, generator, randomState);
                if (gn >= waterY) continue;
                if (HydrologyManager.lakeWaterAt(lake, nx, nz, gn, generator, randomState)) {
                    touches = true;
                    continue;
                }
                if (members.size() >= LAKE_HOLLOW_COLUMNS || !lake.near(nx, nz, LAKE_HOLLOW_REACH)) {
                    open = true;
                    break;
                }
                if (gn < waterY - lipDepth || !lake.covers(nx, nz)) beyondLip = true;
                members.add(nk);
            }
        }
        final Kind kind = open ? Kind.OPEN : touches && beyondLip ? Kind.FLOODED : Kind.NONE;
        if (g.verdicts.size() + members.size() > MEMO_LIMIT) g.verdicts.clear();
        final Verdict verdict = new Verdict(lake, kind);
        for (int k = 0; k < members.size(); k++) g.verdicts.put(members.getLong(k), verdict);
        return kind;
    }

    static boolean besideWater(WatershedLake lake, int x, int z, ChunkGenerator generator, RandomState randomState) {
        for (int d = 0; d < 4; d++) {
            final int nx = x + DX4[d], nz = z + DZ4[d];
            final int gn = groundAt(nx, nz, generator, randomState);
            if (gn < lake.waterY && HydrologyManager.lakeWaterAt(lake, nx, nz, gn, generator, randomState)) return true;
        }
        return false;
    }

    static void counted(Kind kind, boolean held) {
        if (kind == Kind.FLOODED) WatershedStats.lakeHollowsFlooded.increment();
        else if (held) WatershedStats.lakeHollowsHeld.increment();
    }
}
