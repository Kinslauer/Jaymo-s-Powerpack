/*
 * Part of Dynamic Waters. Not adapted from any TFC file directly, but glues
 * together river/RiverNetworkBuilder.java, river/RiverPartition.java and
 * FastConcurrentCache.java, all three of which are Derivative Works of
 * TerraFirmaCraft (EUPL v1.2) - see the notices in those files.
 *
 * Written by AdamNew, 2026-07-13.
 *
 * Wired into NoiseChunkGeneratorMixin: prefetchAround() runs at the NOISE stage (fillFromNoise HEAD) to
 * build regions in the background, and findNearest() is queried from applyCarvers. Building a region
 * synchronously on a decoration-stage thread (the old behaviour) is what caused the 0% worldgen freeze -
 * prefetchAround() exists specifically so that almost never happens anymore.
 */

package com.AdamNew.dynamicwaters;

import java.util.List;
import com.AdamNew.dynamicwaters.river.RiverGraphEdge;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

import com.AdamNew.dynamicwaters.river.RiverFlow;

import com.AdamNew.dynamicwaters.river.RiverMathUtils;
import com.AdamNew.dynamicwaters.river.RiverContext;
import com.AdamNew.dynamicwaters.river.RiverPartition;
import com.AdamNew.dynamicwaters.river.RiverNetworkBuilder;

public final class MainRivers
{
    public static final int REGION_SIZE = HydrologyManager.REGION_SIZE;

    private static volatile long worldSeed = 0;

    private static volatile ConcurrentHashMap<Long, List<RiverGraphEdge>> regionCache = new ConcurrentHashMap<>();
    private static volatile RiverPartition partition = new RiverPartition();
    private static final ConcurrentHashMap<Long, Object> REGION_LOCKS = new ConcurrentHashMap<>();

    private static volatile ConcurrentHashMap<Long, CompletableFuture<RiverNetworkBuilder.Raw>> grown = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<Long, CompletableFuture<List<RiverGraphEdge>>> building = new ConcurrentHashMap<>();

    private static final int REGION_QUERY_RADIUS = Math.max(1, Mth.ceil(
        (RiverNetworkBuilder.RIVER_STEP_LENGTH * RiverNetworkBuilder.RIVER_DEPTH) / (float) REGION_SIZE));

    private static final ExecutorService PREFETCH_POOL = Executors.newFixedThreadPool(2, new ThreadFactory()
    {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r)
        {
            final Thread t = new Thread(r, "DynamicWaters-RegionPrefetch-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile KeySetView<Long, Boolean> inFlight = ConcurrentHashMap.newKeySet();

    private MainRivers() {}

    public static void setSeed(long seed)
    {
        if (worldSeed != seed)
        {
            worldSeed = seed;
            clearCaches();
        }
    }

    public static void clearCaches()
    {
        regionCache = new ConcurrentHashMap<>();
        partition = new RiverPartition();
        REGION_LOCKS.clear();
        grown = new ConcurrentHashMap<>();
        building = new ConcurrentHashMap<>();
        inFlight = ConcurrentHashMap.newKeySet();
    }

    public static void prefetchAround(int blockX, int blockZ,
        ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState)
    {
        if (!com.AdamNew.dynamicwaters.Config.dynamicNormalRivers()) return;

        final int homeRx = Math.floorDiv(blockX, REGION_SIZE);
        final int homeRz = Math.floorDiv(blockZ, REGION_SIZE);
        final ConcurrentHashMap<Long, List<RiverGraphEdge>> cache = regionCache;

        for (int drx = -REGION_QUERY_RADIUS; drx <= REGION_QUERY_RADIUS; drx++)
        {
            for (int drz = -REGION_QUERY_RADIUS; drz <= REGION_QUERY_RADIUS; drz++)
            {
                final int rx = homeRx + drx, rz = homeRz + drz;
                final long key = RiverMathUtils.pack(rx, rz);
                if (cache.containsKey(key)) continue;
                if (!inFlight.add(key)) continue;

                PREFETCH_POOL.submit(() -> {
                    try
                    {
                        getRegion(rx, rz, generator, level, randomState);
                    }
                    finally
                    {
                        inFlight.remove(key);
                    }
                });
            }
        }
    }

    public static List<RiverGraphEdge> getRegion(int rx, int rz,
        ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState)
    {
        final ConcurrentHashMap<Long, List<RiverGraphEdge>> cache = regionCache;
        final long key = RiverMathUtils.pack(rx, rz);
        List<RiverGraphEdge> cached = cache.get(key);
        if (cached != null) return cached;

        if (generator == null || randomState == null) return java.util.Collections.emptyList();

        final ConcurrentHashMap<Long, CompletableFuture<List<RiverGraphEdge>>> builds = building;

        final ConcurrentHashMap<Long, CompletableFuture<RiverNetworkBuilder.Raw>> rivers = grown;
        final CompletableFuture<List<RiverGraphEdge>> mine = new CompletableFuture<>();
        final CompletableFuture<List<RiverGraphEdge>> theirs = builds.putIfAbsent(key, mine);
        if (theirs != null) return theirs.join();
        boolean wasGenerating = HydrologyManager.isGeneratingRivers();
        try
        {
            HydrologyManager.setGeneratingRivers(true);
            final RiverNetworkBuilder.Raw own = grow(rivers, rx, rz, generator, level, randomState);
            final RiverNetworkBuilder.Raw[] around = new RiverNetworkBuilder.Raw[8];
            int n = 0;
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx != 0 || dz != 0) around[n++] = grow(rivers, rx + dx, rz + dz, generator, level, randomState);
                }
            }
            final List<RiverGraphEdge> edges = RiverNetworkBuilder.generate(rx, rz, worldSeed,
                generator, randomState, own, around);
            final List<RiverGraphEdge> published = publish(cache, key, edges);
            mine.complete(published);
            builds.remove(key, mine);
            return published;
        }
        catch (Throwable t)
        {
            builds.remove(key, mine);
            mine.completeExceptionally(t);
            throw t;
        }
        finally
        {
            HydrologyManager.setGeneratingRivers(wasGenerating);
        }
    }

    private static RiverNetworkBuilder.Raw grow(ConcurrentHashMap<Long, CompletableFuture<RiverNetworkBuilder.Raw>> all,
        int rx, int rz, ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState)
    {
        final long key = RiverMathUtils.pack(rx, rz);
        final CompletableFuture<RiverNetworkBuilder.Raw> mine = new CompletableFuture<>();
        final CompletableFuture<RiverNetworkBuilder.Raw> theirs = all.putIfAbsent(key, mine);
        if (theirs != null) return theirs.join();
        try
        {
            final RiverNetworkBuilder.Raw raw = RiverNetworkBuilder.grow(rx, rz, worldSeed, REGION_SIZE,
                generator, level, randomState);
            mine.complete(raw);
            return raw;
        }
        catch (Throwable t)
        {
            all.remove(key, mine);
            mine.completeExceptionally(t);
            throw t;
        }
    }

    public static void cacheRegion(int rx, int rz, List<RiverGraphEdge> edges) {
        publish(regionCache, RiverMathUtils.pack(rx, rz), edges);
    }

    private static List<RiverGraphEdge> publish(ConcurrentHashMap<Long, List<RiverGraphEdge>> cache, long key,
        List<RiverGraphEdge> edges)
    {
        final Object lock = REGION_LOCKS.computeIfAbsent(key, k -> new Object());
        synchronized (lock)
        {
            final List<RiverGraphEdge> already = cache.get(key);
            if (already != null) return already;
            final RiverPartition p = partition;
            for (RiverGraphEdge edge : edges)
            {
                p.insert(edge);
            }
            cache.put(key, edges);
            return edges;
        }
    }

    public static List<RiverGraphEdge> riversAt(double x, double z)
    {
        if (!com.AdamNew.dynamicwaters.Config.dynamicNormalRivers()) return java.util.Collections.emptyList();
        return partition.get((int) Math.floor(x), (int) Math.floor(z));
    }

    @Nullable
    public static List<RiverGraphEdge> getNearbyRivers(double x, double z)
    {
        if (!com.AdamNew.dynamicwaters.Config.dynamicNormalRivers()) return java.util.Collections.emptyList();

        final RiverPartition p = partition;
        List<RiverGraphEdge> results = new java.util.ArrayList<>();
        for (int dx = -RiverPartition.CELL_SIZE; dx <= RiverPartition.CELL_SIZE; dx += RiverPartition.CELL_SIZE)
        {
            for (int dz = -RiverPartition.CELL_SIZE; dz <= RiverPartition.CELL_SIZE; dz += RiverPartition.CELL_SIZE)
            {
                for (RiverGraphEdge candidate : p.get((int) Math.floor(x) + dx, (int) Math.floor(z) + dz))
                {
                    if (!results.contains(candidate))
                    {
                        results.add(candidate);
                    }
                }
            }
        }
        return results;
    }

    public static RiverContext findNearest(double x, double z,
        ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState)
    {
        if (!com.AdamNew.dynamicwaters.Config.dynamicNormalRivers()) return null;

        final int homeRx = Math.floorDiv((int) Math.floor(x), REGION_SIZE);
        final int homeRz = Math.floorDiv((int) Math.floor(z), REGION_SIZE);

        for (int drx = -REGION_QUERY_RADIUS; drx <= REGION_QUERY_RADIUS; drx++)
        {
            for (int drz = -REGION_QUERY_RADIUS; drz <= REGION_QUERY_RADIUS; drz++)
            {
                getRegion(homeRx + drx, homeRz + drz, generator, level, randomState);
            }
        }

        RiverGraphEdge nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        final RiverPartition p = partition;

        for (int dx = -RiverPartition.CELL_SIZE; dx <= RiverPartition.CELL_SIZE; dx += RiverPartition.CELL_SIZE)
        {
            for (int dz = -RiverPartition.CELL_SIZE; dz <= RiverPartition.CELL_SIZE; dz += RiverPartition.CELL_SIZE)
            {
                for (RiverGraphEdge candidate : p.get((int) Math.floor(x) + dx, (int) Math.floor(z) + dz))
                {
                    if (candidate.fractal().boxDistanceSq(x, z) >= nearestDistSq) continue;
                    final double distSq = candidate.fractal().intersectDistance(x, z);
                    if (distSq < nearestDistSq)
                    {
                        nearestDistSq = distSq;
                        nearest = candidate;
                    }
                }
            }
        }

        if (nearest == null) return null;

        final RiverFlow flow = nearest.fractal().calculateFlow(x, z);
        final double widthSq = nearest.widthSq(x, z);
        return new RiverContext(nearest, flow, nearestDistSq, widthSq);
    }
}
