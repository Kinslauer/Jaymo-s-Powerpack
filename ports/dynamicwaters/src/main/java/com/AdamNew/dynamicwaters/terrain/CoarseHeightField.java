package com.AdamNew.dynamicwaters.terrain;

import com.AdamNew.dynamicwaters.FastConcurrentCache;
import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoarseHeightField {
    public static final int CELL_SHIFT = 3;
    public static final int CELL_SIZE = 1 << CELL_SHIFT;
    public static final int BLOCK_SHIFT = 4;
    public static final int BLOCK_CELLS = 1 << BLOCK_SHIFT;
    private static final int BLOCK_MASK = BLOCK_CELLS - 1;
    private static final int BLOCK_AREA = BLOCK_CELLS * BLOCK_CELLS;

    private static final int CACHE_BLOCKS = 16384;

    private static volatile FastConcurrentCache<short[]> blocks = new FastConcurrentCache<>(CACHE_BLOCKS);

    private static final int HEIGHT_THREADS = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));

    private static final ExecutorService HEIGHT_POOL = Executors.newFixedThreadPool(HEIGHT_THREADS, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "DynamicWaters-HeightSampler-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    private CoarseHeightField() {
    }

    public static void reset() {
        blocks = new FastConcurrentCache<>(CACHE_BLOCKS);
    }

    public static int sample(int cellX, int cellZ, ChunkGenerator generator, RandomState randomState) {
        short[] b = block(cellX >> BLOCK_SHIFT, cellZ >> BLOCK_SHIFT, generator, randomState);
        return b[((cellZ & BLOCK_MASK) << BLOCK_SHIFT) | (cellX & BLOCK_MASK)];
    }

    public static void fillWindow(int cx0, int cz0, int w, int h, float[] out,
            ChunkGenerator generator, RandomState randomState) {
        final int bx0 = cx0 >> BLOCK_SHIFT, bx1 = (cx0 + w - 1) >> BLOCK_SHIFT;
        final int bz0 = cz0 >> BLOCK_SHIFT, bz1 = (cz0 + h - 1) >> BLOCK_SHIFT;
        final FastConcurrentCache<short[]> cache = blocks;

        List<long[]> missing = new ArrayList<>();
        for (int bz = bz0; bz <= bz1; bz++) {
            for (int bx = bx0; bx <= bx1; bx++) {
                if (cache.getIfPresent(bx, bz) == null) {
                    missing.add(new long[] { bx, bz });
                }
            }
        }

        if (!missing.isEmpty()) {
            final int workers = Math.min(missing.size(), HEIGHT_THREADS + 1);
            final int per = (missing.size() + workers - 1) / workers;
            List<Future<?>> futures = new ArrayList<>(workers);
            for (int s = 1; s < workers; s++) {
                final int from = s * per, to = Math.min(missing.size(), from + per);
                if (from >= to) break;
                futures.add(HEIGHT_POOL.submit(() -> fillSlice(missing, from, to, cache, generator, randomState)));
            }
            fillSlice(missing, 0, Math.min(missing.size(), per), cache, generator, randomState);
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (java.util.concurrent.ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                }
            }
        }

        for (int bz = bz0; bz <= bz1; bz++) {
            final int cellZStart = Math.max(cz0, bz << BLOCK_SHIFT);
            final int cellZEnd = Math.min(cz0 + h, (bz + 1) << BLOCK_SHIFT);
            for (int bx = bx0; bx <= bx1; bx++) {
                short[] b = cache.getIfPresent(bx, bz);
                if (b == null) {
                    b = compute(bx, bz, generator, randomState);
                    cache.set(bx, bz, b);
                }
                final int cellXStart = Math.max(cx0, bx << BLOCK_SHIFT);
                final int cellXEnd = Math.min(cx0 + w, (bx + 1) << BLOCK_SHIFT);
                for (int cz = cellZStart; cz < cellZEnd; cz++) {
                    final int srcRow = (cz & BLOCK_MASK) << BLOCK_SHIFT;
                    final int dstRow = (cz - cz0) * w;
                    for (int cx = cellXStart; cx < cellXEnd; cx++) {
                        out[dstRow + (cx - cx0)] = b[srcRow | (cx & BLOCK_MASK)];
                    }
                }
            }
        }
    }

    public static void forEachParallel(int count, java.util.function.IntConsumer task) {
        final boolean generating = HydrologyManager.isGeneratingRivers();
        final int workers = Math.min(count, HEIGHT_THREADS + 1);
        if (workers <= 1) {
            for (int k = 0; k < count; k++) task.accept(k);
            return;
        }
        final int per = (count + workers - 1) / workers;
        final List<Future<?>> futures = new ArrayList<>(workers);
        for (int s = 1; s < workers; s++) {
            final int from = s * per, to = Math.min(count, from + per);
            if (from >= to) break;
            futures.add(HEIGHT_POOL.submit(() -> {
                final boolean was = HydrologyManager.isGeneratingRivers();
                HydrologyManager.setGeneratingRivers(generating);
                try {
                    for (int k = from; k < to; k++) task.accept(k);
                } finally {
                    HydrologyManager.setGeneratingRivers(was);
                }
            }));
        }
        for (int k = 0; k < Math.min(count, per); k++) task.accept(k);
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private static void fillSlice(List<long[]> missing, int from, int to, FastConcurrentCache<short[]> cache,
            ChunkGenerator generator, RandomState randomState) {
        for (int k = from; k < to; k++) {
            long[] key = missing.get(k);
            int bx = (int) key[0], bz = (int) key[1];
            if (cache.getIfPresent(bx, bz) != null) continue;
            cache.set(bx, bz, compute(bx, bz, generator, randomState));
        }
    }

    private static short[] block(int bx, int bz, ChunkGenerator generator, RandomState randomState) {
        final FastConcurrentCache<short[]> cache = blocks;
        short[] b = cache.getIfPresent(bx, bz);
        if (b != null) return b;
        b = compute(bx, bz, generator, randomState);
        cache.set(bx, bz, b);
        return b;
    }

    private static short[] compute(int bx, int bz, ChunkGenerator generator, RandomState randomState) {
        final short[] b = new short[BLOCK_AREA];
        if (generator == null || randomState == null) {
            java.util.Arrays.fill(b, (short) HydrologyManager.getSeaLevel());
            return b;
        }
        final int minY = generator.getMinY();
        final int maxY = minY + generator.getGenDepth();
        final LevelHeightAccessor accessor = LevelHeightAccessor.create(minY, generator.getGenDepth());

        final NoiseRouter router = generator instanceof NoiseBasedChunkGenerator ? WiredRouter.of(randomState) : null;
        final int cellX0 = bx << BLOCK_SHIFT, cellZ0 = bz << BLOCK_SHIFT;
        final boolean was = HydrologyManager.isGeneratingRivers();
        HydrologyManager.setGeneratingRivers(true);
        final long t0 = System.nanoTime();
        try {
            for (int j = 0; j < BLOCK_CELLS; j++) {
                final int blockZ = (cellZ0 + j) << CELL_SHIFT;
                final int row = j << BLOCK_SHIFT;
                for (int i = 0; i < BLOCK_CELLS; i++) {
                    final int blockX = (cellX0 + i) << CELL_SHIFT;
                    int y = router != null
                            ? surfaceFromDensity(randomState, router, blockX, blockZ, minY, maxY, CELL_HEIGHT)
                            : generator.getBaseHeight(blockX, blockZ, Heightmap.Types.OCEAN_FLOOR_WG, accessor, randomState);
                    b[row | i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
                }
            }
        } finally {
            HydrologyManager.setGeneratingRivers(was);
            WatershedStats.baseHeightCalls.add(BLOCK_AREA);
            WatershedStats.baseHeightNanos.add(System.nanoTime() - t0);
            WatershedStats.heightBlocksBuilt.increment();
        }
        return b;
    }

    public static int surfaceAt(int x, int z, ChunkGenerator generator, RandomState randomState) {
        if (generator == null || randomState == null) return HydrologyManager.getSeaLevel();
        final int minY = generator.getMinY();
        final int maxY = minY + generator.getGenDepth();
        final boolean was = HydrologyManager.isGeneratingRivers();
        HydrologyManager.setGeneratingRivers(true);
        final long t0 = System.nanoTime();
        try {
            if (generator instanceof NoiseBasedChunkGenerator) {
                return surfaceFromDensity(randomState, WiredRouter.of(randomState), x, z, minY, maxY, CELL_HEIGHT);
            }
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                    LevelHeightAccessor.create(minY, generator.getGenDepth()), randomState);
        } finally {
            HydrologyManager.setGeneratingRivers(was);
            WatershedStats.baseHeightCalls.increment();
            WatershedStats.baseHeightNanos.add(System.nanoTime() - t0);
        }
    }

    /**
     * Jaymo's Powers 26.3 port. 1.21.1 rebuilt the terrain's interpolated / flat_cache / cache_2d markers by hand so a
     * single-point read matched the chunk generator. 26.3's compiled density samplers already interpolate a single
     * point the way the generator fills a chunk ({@code InterpolatedFunction.Sampler#sampleValue}), so the samplers are
     * used as they come. The preliminary bracket (1.21.1: the first y from the top where
     * initialDensityWithoutJaggedness &gt; 0.390625) is 26.3's router {@code chunk_surface_level}, which is that search.
     */
    public static final int CELL_HEIGHT = 8;

    static int surfaceFromDensity(RandomState randomState, NoiseRouter router, int x, int z, int minY, int maxY, int cellHeight) {
        final DensitySampler preliminary = randomState.getSampler(router.chunkSurfaceLevel());
        final DensitySampler density = randomState.getSampler(router.finalDensity());
        final SamplerContext ctx = SamplerContext.EMPTY_UNCACHED;

        int bracket = Math.max(minY, Math.min(maxY - 1, Mth.floor(preliminary.sampleValue(ctx, x, 0, z))));

        final int top = maxY - 1;
        int y = Math.floorDiv(Math.min(top, bracket + 24), cellHeight) * cellHeight;
        while (y + cellHeight <= top && density.sampleValue(ctx, x, y, z) > 0.0F) {
            y += cellHeight;
        }
        for (; y >= minY; y -= cellHeight) {
            if (density.sampleValue(ctx, x, y, z) > 0.0F) {
                for (int yy = Math.min(top, y + cellHeight - 1); yy > y; yy--) {
                    if (density.sampleValue(ctx, x, yy, z) > 0.0F) {
                        return yy + 1;
                    }
                }
                return y + 1;
            }
        }
        return minY;
    }
}
