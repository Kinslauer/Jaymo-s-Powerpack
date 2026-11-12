package com.AdamNew.dynamicwaters.terrain;

import com.AdamNew.dynamicwaters.Config;
import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.MainRivers;
import com.AdamNew.dynamicwaters.WatershedPath;
import com.AdamNew.dynamicwaters.client.LoadingProgressTracker;
import com.AdamNew.dynamicwaters.river.RiverGraphEdge;
import com.AdamNew.dynamicwaters.river.RiverMathUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BasinTile {
    public static final int TILE_SHIFT = 10;
    public static final int TILE_SIZE = 1 << TILE_SHIFT;
    public static final int REGION_SIZE = HydrologyManager.WATERSHED_REGION_SIZE;
    public static final int REGIONS_PER_SIDE = TILE_SIZE / REGION_SIZE;

    public static final int ALGORITHM_VERSION = 94;

    static final int CS = CoarseHeightField.CELL_SHIFT;
    static final int CELL = CoarseHeightField.CELL_SIZE;
    static final int CORE = TILE_SIZE >> CS;
    static final int HALO = 512 >> CS;
    static final int W = CORE + 2 * HALO;
    static final int W_SHIFT = Integer.numberOfTrailingZeros(W);
    static final int W_MASK = W - 1;
    static final int N = W * W;
    static final int REGION_CELLS = REGION_SIZE >> CS;
    static final int REGION_SHIFT = Integer.numberOfTrailingZeros(REGION_CELLS);
    static final int HALO_REGIONS = HALO / REGION_CELLS;
    static final int WINDOW_REGIONS = W / REGION_CELLS;

    static final int CELL_KEY_BITS = 20;
    static final long CELL_KEY_MASK = (1L << CELL_KEY_BITS) - 1;

    public static final double NODE_SPACING = 3.0;

    static final double MIN_WIDTH = 2.5;
    static final double MAX_WIDTH = 20.0;
    static final double WIDTH_K = 0.564;
    static final double WIDTH_POWER = 0.42;

    static double riverWidth(double rain) {
        return Mth.clamp(WIDTH_K * Math.pow(rain, WIDTH_POWER), MIN_WIDTH, MAX_WIDTH);
    }
    static final float EPS = 1e-3f;

    static final float MAX_FILL = 2.0f;

    static final float MOUTH_RAMP_PER_CELL = 8.0f;

    static final float UNDER_RUN = 0.25f;
    static final float UNDER_DIVE = 6.0f;

    static final int UNDER_COVER = 12;
    static final int UNDER_MIN_CELLS = 4;

    static final int UNDER_GAP = 12;

    static final int UNDER_CLEAR = 6;

    static final int UNDER_SEAM = 6;

    static final int UNDER_DIVE_CELLS = 32;

    static final float CAVE_SPIKE = 5.0f;

    static final float CAVE_DEEP = 20.0f;

    static final float TROUGH_MARGIN = 2.0f;

    static final int CAPTURE_RATIO = 4;

    static final int FUSE_OWN_CELLS = 24;

    static final double FUSE_WIDE_GAP = 6.0;

    static final int JOIN_ALONGSIDE_CELLS = 3;

    static final int CHANNEL_CONVERGENCE = 3;

    static final double MAIN_JOIN_IN = 0.45;

    static final double MAIN_JOIN_CONTACT = 2.0;
    static final double MAIN_JOIN_CONTACT_MARGIN = 4.0;

    static final int MAIN_MOUTH_RAMP = 1;
    static final int MAIN_MOUTH_CUT = 3;
    static final int MAIN_MOUTH_RAPIDS = 4;
    static final int MAIN_MOUTH_GORGE_CELLS = 3;
    static final int JOIN_SCAN_CELLS = 16;

    static final float SEA_JOIN_RISE = 4.0f;

    static final int SEA_SHORE_CELLS = 2;

    static final double MAIN_JOIN_WIDTH = 0.75;
    static final double MAIN_JOIN_TAPER = 64.0;

    static final int GORGE_STAT_DEPTH = 12;

    static final float LAKE_MIN_DEPTH = 3.0f;
    static final float LAKE_MIN_DEPTH_FED = 2.0f;

    static final int LAKE_MIN_CELLS = 24;

    static final int LAKE_MIN_CELLS_FED = 20;

    static final double LAKE_MIN_SHAPE = 8.0;
    static final int LAKE_ROUND_CELLS = 200;

    static final int LAKE_ABOVE_SEA = 4;

    static final double LAKE_DEEP_SHARE = 0.7;

    static final float POND_MIN_DEPTH = 6.0f;

    static final float LAKE_MAX_DEPTH = 16.0f;

    static final float LAKE_SHAFT_RATIO = 0.5f;
    static final int LAKE_MAX_CELLS = 3600;

    static final int LAKE_MAX_EXTENT = 60;

    static final int LAKE_MAX_GROWTH = 4000;

    static final int LAKE_CHAIN_CELLS = 3;
    static final int LAKE_CHAIN_STEP = 3;

    static final int LAKE_UNDERCUT_ROUNDS = 4;

    static final float LAKE_CLOSED_RISE = 8.0f;

    static final float LAKE_WIDE_DEPTH = 8.0f;

    static final int LAKE_RIVER_GAP = 3;

    static final double FALL_FLOOR_SLOPE = 0.06;

    static final double RIM_CUT_REACH = 6.0;
    static final double RIM_CUT_DEPTH = 3.0;
    static final int WATERFALL_MIN_DROP = 7;

    static final int CLIFF_SLOT_DEPTH = 12;

    static final int STEP_MIN_FALL = WatershedPath.SHEER_DROP;
    static final int STEP_MIN_POOL_NODES = 2;
    static final int STEP_MAX_POOL_NODES = 8;
    static final int STEP_MAX_HOLD = 5;
    static final int STEP_MAX_GORGE = 7;
    static final int STEP_END_NODES = 3;

    static final double JUNCTION_HEAD_MARGIN = 3.0;
    static final int JUNCTION_HEAD_GROUND = 2;

    static final double ALONGSIDE_GAP = 4.0;
    static final int ALONGSIDE_DROP = 4;

    static final float ROUTE_NOISE = 0.05f;

    static final double ROUTE_WANDER = 1.6;
    static final double ROUTE_WANDER_SLOPE = 0.4;
    static final double ROUTE_WANDER_CELLS = 10.0;

    static final int SPLINE_STRIDE = 3;

    static final double[] JUNCTION_TURN = { 1.0, 0.55, 0.22 };

    static final double JUNCTION_Y = 60.0;
    static final double JUNCTION_APART = 65.0;
    static final double JUNCTION_MAX_TURN = 35.0;

    static final double MEANDER_WAVELENGTH = 6.5;
    static final double MEANDER_AMPLITUDE = 0.4;
    static final double MEANDER_STRENGTH_SPAN = 4.3;
    static final double MEANDER_MAX_SWING = 60.0;

    static final double MEANDER_WAVE_SPREAD = 0.4;
    static final double MEANDER_WAVE_CELLS = 12.0;

    static final double MEANDER_ROOM_RISE = 4.0;
    static final double MEANDER_ROOM_PER_WIDTH = 0.25;
    static final double MEANDER_EDGE_SHARE = 1.0;

    static final double MEANDER_ROOM_DROP = 2.0;

    static final double MEANDER_BEND_SHARE = 0.6;

    static final double MEANDER_FLAT_SLOPE = 0.02;
    static final double MEANDER_STEEP_SLOPE = 0.15;
    static final double MEANDER_STEEP_SHARE = 0.4;

    static final double MEANDER_MOUTH_FADE = 32.0;
    static final double MEANDER_SOURCE_FADE = 24.0;

    static final int MEANDER_KEEP_CELLS = 3;

    static final double MEANDER_SEAM_MARGIN = 2.0;

    static final int MEANDER_KEEP_MAIN_CELLS = 4;
    static final int MEANDER_MAIN_GAP_CELLS = 2;

    static final double MEANDER_RIVER_GAP = 4.0;
    static final int MEANDER_JOIN_CELLS = 6;

    static final double MEANDER_JOIN_LEVEL = 1.0;

    static final double MEANDER_GAP_PER_BLOCK = 2.0;
    static final double MEANDER_GAP_MAX = 8.0;

    static final double MEANDER_RIVER_APART = 4.0;

    static final double MEANDER_JOIN_BLEND = 24.0;
    static final double MEANDER_JOIN_PER_BLOCK = 2.5;

    static final double MEANDER_SPIKE = 3.0;

    static final double MEANDER_NECK_SHARE = 1.0;
    static final double MEANDER_NECK_MIN = 8.0;
    static final double MEANDER_NECK_ARC = 2.5;
    static final double MEANDER_NECK_SCALE = 0.7;
    static final int MEANDER_NECK_ROUNDS = 8;
    static final int MEANDER_NECK_EDGE = 8;

    static final double NECK_TOUCH = 1.1;
    static final double NECK_DETOUR = 1.5;
    static final int NECK_EDGE = 2;

    static final double BEND_FOLD = 2.0;

    static final double BEND_MIN_RADIUS = 8.0;
    static final double BEND_SPAN = 6.0;
    static final double BEND_SPAN_WIDTHS = 1.0;
    static final int BEND_EASE_ROUNDS = 24;

    static final double SOURCE_TAPER = 36.0;
    static final double SOURCE_TIP_POWER = 1.6;

    static final double DENSITY_WET = 3.6;
    static final double DENSITY_DRY = 30.0;

    static final double MAIN_FEED_SHARE = 0.5;
    static final double MAIN_FEED_CONVERGENCE = 2.5;
    static final double MAIN_FEED_FROM = 3.0;
    static final double MAIN_FEED_FULL = 7.0;
    static final double MAIN_FEED_HOLD = 18.0;
    static final double MAIN_FEED_REACH = 36.0;
    static final double DENSITY_TURN = 0.2;
    static final double DENSITY_LAKE_WET = 1.0;
    static final double DENSITY_LAKE_DRY = 10.0;
    static final double DENSITY_NOISE_SCALE = 1700.0;
    static final int DENSITY_GRID = 4;

    static final int RAIN_UNITS = 64;

    static final double DENSITY_HUMIDITY_SPREAD = 0.4;
    static final double DENSITY_NOISE_SPREAD = 0.35;
    static final double DENSITY_WET_SPAN = 3.0;

    static final int MAX_REACH_CELLS = 96;

    static final int LAKE_SILL_NODES = 8;

    static final int MOUTH_EXTRA_CELLS = 2;

    static final int LAKE_LEAD_CELLS = 2;

    static final int LAKE_BORDER_MARGIN = 4;

    static final int MOUTH_WALK_BLOCKS = 24;
    static final int MOUTH_OVERSHOOT_BLOCKS = 8;

    static final int MOUTH_AIM_CELLS = 6;

    static final int CHANNEL_INCISION = 2;

    static final int[] DX = { 1, 1, 0, -1, -1, -1, 0, 1 };
    static final int[] DZ = { 0, 1, 1, 1, 0, -1, -1, -1 };
    static final float[] DIST = { 1f, 1.41421356f, 1f, 1.41421356f, 1f, 1.41421356f, 1f, 1.41421356f };

    private static final double[] DIR_ANGLE = { 0.0, 0.25 * Math.PI, 0.5 * Math.PI, 0.75 * Math.PI, Math.PI, -0.75 * Math.PI,
            -0.5 * Math.PI, -0.25 * Math.PI };

    public static int tileCoord(int regionCoord) {
        return Math.floorDiv(regionCoord, REGIONS_PER_SIDE);
    }

    public static long tileKey(int tx, int tz) {
        return HydrologyManager.getRegionKey(tx, tz);
    }

    public static int channelThreshold(Config.DensityLevel level) {
        switch (level) {
            case VERY_LOW: return 320;
            case LOW: return 160;
            case MEDIUM: return 80;
            case HIGH: return 40;
            case ULTRA: return 24;
            case EXTREME: return 14;
            case INSANE: return 8;
            default: return 40;
        }
    }

    private record SeamExit(int fromX, int fromZ, int cellX, int cellZ, int acc, int wet, int flowLen, boolean channel,
            float level) {
    }

    private record TileSeams(List<SeamExit> exits, short[] exitOf, boolean[] taken) {
    }

    private record SeamKey(int tileX, int tileZ, long seed, int seaLevel, int threshold, boolean lakes,
            boolean tunnels, boolean dynamicRivers, double canyonHeight, boolean normalTunnels) {
    }

    private static final java.util.concurrent.ConcurrentHashMap<SeamKey, TileSeams> SEAM_EXITS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final int SEAM_CACHE_LIMIT = 256;

    private static final int[][] SEAM_NEIGHBOURS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private static final int SEAM_BOUNCE_PASSES = 4;

    static final float SEAM_FLAT_RELIEF = 1.0f;
    static final int SEAM_BAND = 16;
    static final int SEAM_SEGMENT = 16;

    static final int SEAM_POOL_SHARED = HALO - 16;
    static final int SEAM_POOL_BAND = 2;
    static final float SEAM_POOL_FILL = 0.5f;

    static final int SEAM_CYCLE_BAND = 40;
    static final int SEAM_CYCLE_REACH = 48;
    static final float SEAM_CYCLE_CLIMB = 2.0f;

    static final float SEAM_CYCLE_WANDER = 1.0f;

    static final int SEAM_CYCLE_INSET = 3;

    static final int SEAM_CORNER_HOPS = 16;

    static final float SEAM_CYCLE_RISE = 1.0f;

    static final float SEAM_CYCLE_HEIGHT = 8.0f;

    static final int PASS_THROUGH_CELLS = 5;

    static final int PASS_CLIMB_CELLS = 10;

    private static final int[] LAB_LOG_CYCLES = labRect(System.getProperty("dynamicwaters.labLogCycles"));

    private static int[] labRect(String rect) {
        if (rect == null) return null;
        final String[] p = rect.split(",");
        return new int[] { Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                Integer.parseInt(p[3].trim()) };
    }

    public static void clearSeamCache() {
        SEAM_EXITS.clear();
    }

    private static double plainDryness(net.minecraft.world.level.levelgen.densityfunction.DensitySampler humidity, SimplexNoise noise, int bx, int bz, int seaLevel) {
        final double hum = humidity.sampleValue(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED, bx, seaLevel, bz);
        final double local = noise.get(bx / DENSITY_NOISE_SCALE, bz / DENSITY_NOISE_SCALE);
        final double wet = 0.75 * Mth.clamp(hum / DENSITY_HUMIDITY_SPREAD, -2.5, 2.5)
                + 0.66 * Mth.clamp(local / DENSITY_NOISE_SPREAD, -2.5, 2.5);
        return Mth.clamp(0.5 - wet / DENSITY_WET_SPAN, 0.0, 1.0);
    }

    private static double turned(double plain) {
        final double t = Mth.clamp((plain - 0.5) / DENSITY_TURN + 0.5, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double labDryness(RandomState randomState, long seed, int seaLevel, int x, int z) {
        return turned(plainDryness(randomState.getSampler(WiredRouter.of(randomState).vegetation()), densityNoise(seed), x, z, seaLevel));
    }

    private record DensityNoise(long seed, SimplexNoise noise) {
    }

    private static volatile DensityNoise densityNoise;

    private static SimplexNoise densityNoise(long seed) {
        DensityNoise cur = densityNoise;
        if (cur == null || cur.seed() != seed) {
            cur = new DensityNoise(seed, new SimplexNoise(new XoroshiroRandomSource(seed ^ 0x6A09E667F3BCC909L)));
            densityNoise = cur;
        }
        return cur.noise();
    }

    private static SeamKey seamKey(int tileX, int tileZ, long seed, int seaLevel, int threshold, boolean lakes) {
        return new SeamKey(tileX, tileZ, seed, seaLevel, threshold, lakes, Config.MOUNTAIN_RIVER_TUNNELS.get(),
                Config.dynamicNormalRivers(), Config.NORMAL_RIVER_CANYON_HEIGHT.get(), Config.NORMAL_RIVER_TUNNELS.get());
    }

    private static void rememberSeams(SeamKey key, TileSeams seams) {
        if (SEAM_EXITS.size() >= SEAM_CACHE_LIMIT) SEAM_EXITS.clear();
        SEAM_EXITS.put(key, seams);
    }

    private static TileSeams seamsOf(int tileX, int tileZ, ChunkGenerator generator, RandomState randomState,
            long seed, int seaLevel, int threshold, boolean lakesEnabled) {
        final SeamKey key = seamKey(tileX, tileZ, seed, seaLevel, threshold, lakesEnabled);
        final TileSeams cached = SEAM_EXITS.get(key);
        if (cached != null) return cached;
        final Builder b = new Builder(new BasinTile(tileX, tileZ), generator, randomState, seed, seaLevel,
                threshold, lakesEnabled, 0L);
        b.neighbour = true;
        WatershedStats.seamNeighbourRoutes.increment();
        b.route();
        final TileSeams seams = b.seams();
        rememberSeams(key, seams);
        return seams;
    }

    public final int tileX, tileZ;
    public final Map<Long, List<WatershedPath>> pathsByRegion = new HashMap<>();
    public final Map<Long, List<WatershedLake>> lakesByRegion = new HashMap<>();

    private BasinTile(int tileX, int tileZ) {
        this.tileX = tileX;
        this.tileZ = tileZ;
        for (int rz = 0; rz < REGIONS_PER_SIDE; rz++) {
            for (int rx = 0; rx < REGIONS_PER_SIDE; rx++) {
                long key = HydrologyManager.getRegionKey(tileX * REGIONS_PER_SIDE + rx, tileZ * REGIONS_PER_SIDE + rz);
                pathsByRegion.put(key, new ArrayList<>());
                lakesByRegion.put(key, new ArrayList<>());
            }
        }
    }

    public static BasinTile build(int tileX, int tileZ, ChunkGenerator generator, RandomState randomState,
            long seed, int seaLevel, int channelThreshold, boolean lakesEnabled) {
        final long t0 = System.nanoTime();
        final long progressKey = tileKey(tileX, tileZ) ^ 0x4000000000000000L;
        LoadingProgressTracker.setProgress(progressKey, 0, "Sampling terrain for mountain rivers...");
        BasinTile tile = new BasinTile(tileX, tileZ);
        try {
            new Builder(tile, generator, randomState, seed, seaLevel, channelThreshold, lakesEnabled, progressKey).run();
        } finally {
            LoadingProgressTracker.completeRegion(progressKey);
            WatershedStats.tileBuilds.increment();
            WatershedStats.tileNanos.add(System.nanoTime() - t0);
        }
        return tile;
    }

    private static final class Builder {
        final BasinTile tile;
        final ChunkGenerator generator;
        final RandomState randomState;
        final long seed;
        final int seaLevel;
        final int threshold;

        final int thresholdSetting;
        final boolean lakesEnabled;
        final long progressKey;

        boolean neighbour;

        final Config.WaterfallLevel waterfalls = Config.waterfalls();

        private int underCount;

        private boolean[] underCells;

        final int cx0, cz0;

        final float[] h = new float[N];

        final float[] wanderWay = new float[N], wanderPull = new float[N];
        final float[] filled = new float[N];
        final float[] breached = new float[N];
        final byte[] dir = new byte[N];
        final int[] acc = new int[N];

        final int[] flowLen = new int[N];
        final boolean[] sink = new boolean[N];
        final boolean[] mainRiver = new boolean[N];

        final boolean[] nearRiver = new boolean[N];

        final boolean[] cut = new boolean[N];

        float[] ground0;
        final boolean[] pushed = new boolean[N];
        final int[] order = new int[N];
        final int[] lakeId = new int[N];
        final boolean[] chan = new boolean[N];
        final byte[] upCount = new byte[N];
        final boolean[] upLake = new boolean[N];
        final boolean[] sameRegionUp = new boolean[N];

        final boolean[] inflow = new boolean[N];

        float[] seamLevel;

        int[] inAcc, inWet, inLen;

        int[] wetAlone;

        final TileSeams[] around = new TileSeams[SEAM_NEIGHBOURS.length];

        boolean[] takenOver;

        TileSeams ownSeams;

        private final java.util.HashMap<Long, TileSeams> farSeams = new java.util.HashMap<>();

        final boolean[] dropped = new boolean[N];

        boolean[][] withdrawn;
        short[][] wayOut;

        boolean seamsImported;

        byte[] labDir0;
        int[] labAcc0;

        final int[] lakeFloorAt = new int[N];
        final long[] heap = new long[N + 1];
        int heapSize;
        int orderCount;

        final List<int[]> lakeCells = new ArrayList<>();
        final List<Integer> lakeWaterY = new ArrayList<>();

        boolean[] lakeClosed = new boolean[0];

        boolean closedChanged;

        final int[] lakeShore = new int[N];

        boolean lakesFound;
        final List<int[]> lakeShoreCells = new ArrayList<>();

        WatershedLake[] packaged = new WatershedLake[0];

        final int[] rain = new int[N];
        final float[] dryness = new float[N];

        final int[] wet = new int[N];

        final int streamRain;

        final int[] needRain = new int[N];

        final float[] needConvergence = new float[N];

        float[] bendKeep, bendNx, bendNz, bendK, bendPhase, bendFlat;

        boolean[] bendCrossing;

        final double[] swayNormal = new double[2];

        Builder(BasinTile tile, ChunkGenerator generator, RandomState randomState, long seed, int seaLevel,
                int threshold, boolean lakesEnabled, long progressKey) {
            this.tile = tile;
            this.generator = generator;
            this.randomState = randomState;
            this.seed = seed;
            this.seaLevel = seaLevel;
            this.threshold = Math.max(4, threshold);
            this.thresholdSetting = threshold;
            this.streamRain = (int) Math.round(this.threshold * DENSITY_WET * RAIN_UNITS);
            this.lakesEnabled = lakesEnabled;
            this.progressKey = progressKey;
            this.cx0 = (tile.tileX << TILE_SHIFT >> CS) - HALO;
            this.cz0 = (tile.tileZ << TILE_SHIFT >> CS) - HALO;
        }

        void run() {
            route();

            ownSeams = seams();
            rememberSeams(seamKey(tile.tileX, tile.tileZ, seed, seaLevel, thresholdSetting, lakesEnabled), ownSeams);
            if (System.getProperty("dynamicwaters.labDumpTile") != null) {
                labDir0 = dir.clone();
                labAcc0 = acc.clone();
            }
            LoadingProgressTracker.setProgress(progressKey, 80, "Joining mountain rivers to the neighbouring land...");
            importSeams();
            fuseWide();
            final long t = System.nanoTime();
            for (int pass = 0; ; pass++) {
                breach();
                computeChannels();
                for (int round = 0; round < LAKE_UNDERCUT_ROUNDS && lowerUndercutLakes(); round++) {
                    breach();
                    computeChannels();
                }
                if (pass == SEAM_BOUNCE_PASSES || !bounce()) break;
                accumulateCore();
            }
            deadEndLakes();

            if (seamsImported && !deadEndLakeIds.isEmpty()) accumulateCore();
            widenAsTold();
            carryWidths();

            if (lakesEnabled) packageLakes();
            lakeFloors();
            goUnderground();
            traceChannels();
            lowerAlongside();
            WatershedStats.tileExtractNanos.add(System.nanoTime() - t);
            labDumpTile();
        }

        private void goUnderground() {
            if (!Config.MOUNTAIN_RIVER_TUNNELS.get()) return;
            final double chance = Config.MOUNTAIN_TUNNEL_FREQUENCY.get().chance;
            final int longest = Config.MOUNTAIN_TUNNEL_LENGTH.get() >> CS;
            final float deepest = Config.MOUNTAIN_TUNNEL_DEPTH.get();
            final boolean[] taken = new boolean[N];
            final int[] cells = new int[N];
            final float[] levels = new float[N];
            underCells = new boolean[N];

            for (int k = 0; k < orderCount; k++) {
                final int foot = order[k];
                if (!chan[foot] || taken[foot] || !mayGoUnder(foot) || !clearBelow(foot)) continue;
                goUnder(foot, longest, deepest, chance, taken, cells, levels);
            }
        }

        private boolean mayGoUnder(int c) {
            final int i = (c & W_MASK) - HALO, j = (c >> W_SHIFT) - HALO;
            return i >= UNDER_SEAM && j >= UNDER_SEAM && i < CORE - UNDER_SEAM && j < CORE - UNDER_SEAM
                    && !sink[c] && !mainRiver[c] && !cut[c] && !nearRiver[c] && !upLake[c] && !inflow[c]
                    && lakeId[c] < 0 && lakeShore[c] < 0

                    && (ground0 == null || ground0[c] <= h[c] + 1.0f);
        }

        private boolean clearBelow(int foot) {
            int c = foot;
            for (int k = 0; k < UNDER_CLEAR; k++) {
                c = downstream(c);
                if (c < 0 || !mayGoUnder(c)) return false;
            }
            return true;
        }

        private void goUnder(int foot, int longest, float deepest, double chance, boolean[] taken, int[] cells, float[] levels) {
            int reach = 0;
            for (int c = foot; reach < longest + UNDER_DIVE_CELLS; ) {
                final int u = mainUp(c);
                if (u < 0 || !chan[u] || taken[u] || !mayGoUnder(u)) break;
                cells[reach++] = u;
                c = u;
            }

            int bestRun = 0;
            for (int run = Math.min(longest, reach); run > 0 && bestRun == 0; run--) {
                if (underLevels(foot, run, deepest, reach, cells, levels) >= UNDER_MIN_CELLS) bestRun = run;
            }
            if (bestRun == 0) return;
            final long hash = it.unimi.dsi.fastutil.HashCommon.mix(seed ^ 0x7E57ADA5L
                    ^ ((long) (cx0 + (foot & W_MASK)) * 341873128712L) ^ ((long) (cz0 + (foot >> W_SHIFT)) * 132897987541L));
            if ((hash >>> 11) * 0x1.0p-53 >= chance) return;

            underLevels(foot, bestRun, deepest, reach, cells, levels);
            int count = underCount;
            final int stem = count, head = cells[count];
            for (int k = -1; k < count; k++) {
                final int d = k < 0 ? foot : cells[k];
                final float level = k < 0 ? breached[foot] : levels[k];
                final int i = d & W_MASK, j = d >> W_SHIFT;
                for (int n = 0; n < 8; n++) {
                    final int ni = i + DX[n], nj = j + DZ[n];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    final int v = (nj << W_SHIFT) | ni;
                    if (!chan[v] || downstream(v) != d) continue;

                    if (k + 1 < stem && v == cells[k + 1] || v == head) continue;
                    final float to = level + UNDER_DIVE;
                    if (breached[v] <= to) continue;
                    if (taken[v] || !mayGoUnder(v) || to - CHANNEL_INCISION < lakeFloorAt[v]) return;
                    cells[count] = v;
                    levels[count++] = to;
                }
            }
            for (int k = 0; k < count; k++) {
                breached[cells[k]] = levels[k];
                taken[cells[k]] = true;
                underCells[cells[k]] = true;
                WatershedStats.underCells.increment();
            }
            WatershedStats.underStretches.increment();

            taken[foot] = true;
            int up = head, down = foot;
            for (int k = 0; k < UNDER_GAP; k++) {
                if (up >= 0) {
                    taken[up] = true;
                    up = mainUp(up);
                }
                if (down >= 0) {
                    taken[down] = true;
                    down = downstream(down);
                }
            }
        }

        private int underLevels(int foot, int run, float deepest, int reach, int[] cells, float[] levels) {
            float water = breached[foot];
            boolean running = true;
            int covered = 0;
            for (int k = 0; k < reach; k++) {
                final int u = cells[k];
                water += running ? UNDER_RUN : UNDER_DIVE;
                if (water >= breached[u]) {
                    underCount = k;
                    return covered;
                }

                if (water - CHANNEL_INCISION < lakeFloorAt[u]) return -1;
                levels[k] = water;
                if (h[u] - (water - CHANNEL_INCISION) >= UNDER_COVER) covered++;
                if (breached[u] - water >= deepest || k + 1 >= run) running = false;
            }
            return -1;
        }

        private void labDumpTile() {
            final String file = System.getProperty("dynamicwaters.labDumpTile");
            final String at = System.getProperty("dynamicwaters.labDumpTileAt");
            if (file == null || at == null || neighbour) return;
            boolean mine = false;
            for (String one : at.split(";")) {
                final String[] t = one.split(",");
                if (tile.tileX == Integer.parseInt(t[0].trim()) && tile.tileZ == Integer.parseInt(t[1].trim())) mine = true;
            }
            if (!mine) return;
            final String out = at.contains(";") ? file.replace(".txt", "_" + tile.tileX + "_" + tile.tileZ + ".txt") : file;
            final StringBuilder sb = new StringBuilder();
            sb.append("# cx0 ").append(cx0).append(" cz0 ").append(cz0).append(" cell ").append(CELL).append('\n');
            for (int c = 0; c < N; c++) {
                sb.append(c & W_MASK).append(' ').append(c >> W_SHIFT).append(' ').append(h[c]).append(' ').append(filled[c])
                        .append(' ').append(breached[c]).append(' ').append(lakeId[c]).append(' ').append(lakeShore[c])
                        .append(' ').append(dir[c]).append(' ').append(chan[c] ? 1 : 0)
                        .append(' ').append(takenOver != null && takenOver[c] ? 1 : 0).append(' ').append(inflow[c] ? 1 : 0)
                        .append(' ').append(seamLevel != null ? seamLevel[c] : Float.POSITIVE_INFINITY)
                        .append(' ').append(acc[c]).append(' ').append(inAcc != null ? inAcc[c] : 0)
                        .append(' ').append(labDir0 != null ? labDir0[c] : -2).append(' ').append(labAcc0 != null ? labAcc0[c] : -1)
                        .append('\n');
            }
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(out), sb);
            } catch (java.io.IOException ignored) {
            }
        }

        void route() {
            long t = System.nanoTime();
            CoarseHeightField.fillWindow(cx0, cz0, W, W, h, generator, randomState);
            despike();
            routeNoise();
            wanderField();
            densityField();
            WatershedStats.tileSampleNanos.add(System.nanoTime() - t);
            if (!neighbour) LoadingProgressTracker.setProgress(progressKey, 40, "Routing mountain water...");

            t = System.nanoTime();
            stampSinks();
            feedField();
            flood();
            computeD8();
            accumulate();
            capture();
            fuse();
            fuseWide();
            joinAlongside();
            WatershedStats.tileFloodNanos.add(System.nanoTime() - t);
            if (!neighbour) LoadingProgressTracker.setProgress(progressKey, 70, "Carving mountain streams...");

            t = System.nanoTime();
            Arrays.fill(lakeId, -1);
            Arrays.fill(lakeShore, -1);
            lakesFound = true;
            if (lakesEnabled) {
                extractLakes();
                settleLakeChains();
                if (closeLakes()) reroute();
            }
            breach();
            computeChannels();

            for (int round = 0; round < LAKE_UNDERCUT_ROUNDS && lowerUndercutLakes(); round++) {
                if (closeLakes()) reroute();
                breach();
                computeChannels();
            }
            WatershedStats.tileExtractNanos.add(System.nanoTime() - t);
        }

        private static boolean inCore(int c) {
            final int i = c & W_MASK, j = c >> W_SHIFT;
            return i >= HALO && j >= HALO && i < HALO + CORE && j < HALO + CORE;
        }

        private float toldOver(int c, int d) {
            final short x = ownSeams.exitOf()[coreIndex(c)];
            if (x < 0) return Float.NaN;
            final SeamExit e = ownSeams.exits().get(x);
            return e.cellX() == cx0 + (d & W_MASK) && e.cellZ() == cz0 + (d >> W_SHIFT) ? e.level() : Float.NaN;
        }

        private static int coreIndex(int c) {
            return ((c >> W_SHIFT) - HALO) * CORE + ((c & W_MASK) - HALO);
        }

        TileSeams seams() {
            final List<SeamExit> exits = new ArrayList<>();
            final short[] exitOf = new short[CORE * CORE];

            for (int k = 0; k < orderCount; k++) {
                final int c = order[k];
                if (!inCore(c)) continue;
                final int d = downstream(c);
                short exit = -1;

                if (d >= 0 && !sink[d]) {
                    if (inCore(d)) {
                        exit = exitOf[coreIndex(d)];
                    } else {
                        exit = (short) exits.size();
                        exits.add(new SeamExit(cx0 + (c & W_MASK), cz0 + (c >> W_SHIFT), cx0 + (d & W_MASK),
                                cz0 + (d >> W_SHIFT), acc[c], wet[c], flowLen[c], chan[c], breached[d]));
                    }
                }
                exitOf[coreIndex(c)] = exit;
            }
            final boolean[] taken = new boolean[SEAM_NEIGHBOURS.length];
            for (int n = 0; n < taken.length; n++) taken[n] = seamFlat(n) || seamPooled(n);
            return new TileSeams(exits, exitOf, taken);
        }

        void importSeams() {
            inAcc = new int[N];
            inWet = new int[N];
            inLen = new int[N];
            seamLevel = new float[N];
            Arrays.fill(seamLevel, Float.POSITIVE_INFINITY);
            withdrawn = new boolean[SEAM_NEIGHBOURS.length][];
            wayOut = new short[SEAM_NEIGHBOURS.length][];
            takenOver = new boolean[N];
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (!ownSeams.taken()[n]) {
                    WatershedStats.seamSidesKept.increment();
                    continue;
                }
                if (!seamFlat(n)) WatershedStats.seamSidesPooled.increment();
                WatershedStats.seamSidesTaken.increment();
                around[n] = seamsOf(tile.tileX + SEAM_NEIGHBOURS[n][0], tile.tileZ + SEAM_NEIGHBOURS[n][1], generator,
                        randomState, seed, seaLevel, thresholdSetting, lakesEnabled);
                withdrawn[n] = new boolean[around[n].exits().size()];
                wayOut[n] = new short[withdrawn[n].length];
                Arrays.fill(wayOut[n], (short) -1);
                markTakenOver(n);
            }
            breakSeamCycles(withdrawn);
            dropPassingThrough(withdrawn);
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (around[n] == null) continue;
                final List<SeamExit> exits = around[n].exits();
                for (int x = 0; x < exits.size(); x++) {
                    if (withdrawn[n][x]) continue;
                    final SeamExit e = exits.get(x);
                    final int i = e.cellX() - cx0, j = e.cellZ() - cz0;
                    if (i < HALO || j < HALO || i >= HALO + CORE || j >= HALO + CORE) continue;
                    final int c = (j << W_SHIFT) | i;
                    inAcc[c] += e.acc();
                    inWet[c] += e.wet();
                    inLen[c] = Math.max(inLen[c], e.flowLen() + 1);
                    seamLevel[c] = Math.min(seamLevel[c], e.level());
                    if (e.channel()) inflow[c] = true;
                }
            }
            wetAlone = new int[CORE * CORE];
            for (int c = 0; c < N; c++) if (inCore(c)) wetAlone[coreIndex(c)] = wet[c];
            accumulateCore();
            seamsImported = true;
        }

        static final double DEAD_END_LAKE_SPAN = 0.7;
        static final int DEAD_END_LAKE_MIN = 5;
        static final int DEAD_END_LAKE_MAX = 14;

        static final int DEAD_END_LAKE_LAST = 3;

        static final int DEAD_END_LAKE_EDGE = 0;
        static final int DEAD_END_LAKE_GAP = 2;
        static final double DEAD_END_LAKE_REACH = 1.6;

        static final int DEAD_END_LAKE_RIM_WOBBLE = 3;

        static final int DEAD_END_LAKE_JOIN = 8;

        static final int DEAD_END_LAKE_JOIN_NEAR = 3;

        static final int DEAD_END_LAKE_DIG = 7;

        final java.util.Set<Integer> deadEndLakeIds = new java.util.HashSet<>();

        static final int SEAM_LAKE_RUN_GAP = 8;

        static final double SEAM_LAKE_SPAN = 0.5;
        static final int SEAM_LAKE_MIN = 8;
        static final int SEAM_LAKE_MAX = 22;

        static final int SEAM_LAKE_RUN_MAX = 64;

        private record SeamLake(int[] cells, int waterY) {
        }
        final List<SeamLake> seamLakes = new ArrayList<>();

        static final int DEAD_END_LAKE_SEARCH = 48;

        static final double DEAD_END_LAKE_FULL = 0.6;

        static final double DEAD_END_LAKE_WOBBLE = 0.45;
        static final double DEAD_END_LAKE_WAVE = 6.0;

        static final double DEAD_END_LAKE_GROUND = 1.0;

        int[] floodMark;
        int floodStamp;

        void deadEndLakes() {
            if (!lakesEnabled) return;
            for (SeamLake s : seamLakes) makeSeamLake(s);
            final boolean[] riverAlone = new boolean[N];
            for (SeamExit e : ownSeams.exits()) {
                final int c = windowCell(e.fromX(), e.fromZ());
                if (c >= 0 && e.channel()) riverAlone[c] = true;
            }

            final long[] ends = new long[5 * CORE];
            int endCount = 0;
            for (int j = HALO; j < HALO + CORE; j++) {
                final boolean rimRow = j == HALO || j == HALO + CORE - 1;
                for (int i = HALO; i < HALO + CORE; i++) {
                    if (!rimRow && i != HALO && i != HALO + CORE - 1) continue;
                    final int c = (j << W_SHIFT) | i;
                    if (!chan[c]) continue;
                    final int d = downstream(c);
                    if (d < 0 || inCore(d) || sink[d]) continue;

                    final boolean corner = neighbourOf(cx0 + (d & W_MASK), cz0 + (d >> W_SHIFT)) < 0;
                    if (riverAlone[c] && !dropped[c] && !corner) continue;

                    if (lakeId[d] >= 0 && lakeWaterY.get(lakeId[d]) <= riverLevel(c)) continue;

                    if (!dropped[c] && comesBack(c, d)) continue;
                    ends[endCount++] = ((long) (Integer.MAX_VALUE - wet[c]) << CELL_KEY_BITS) | c;
                }
            }

            for (int c = 0; c < N && endCount < ends.length; c++) {
                if (!chan[c] || !inCore(c)) continue;
                final int d = downstream(c);
                final int lake = d < 0 || !inCore(d) ? -1 : lakeId[d] >= 0 ? lakeId[d] : lakeShore[d];
                if (lake < 0 || !sharedLake(lake) || !heldUnder(c, lakeWaterY.get(lake))) continue;
                ends[endCount++] = ((long) (Integer.MAX_VALUE - wet[c]) << CELL_KEY_BITS) | c;
                WatershedStats.sharedLakeUndercuts.increment();
            }
            Arrays.sort(ends, 0, endCount);
            final int[] streams = new int[N];
            for (int e = 0; e < endCount; e++) {
                final int c = (int) (ends[e] & ((1L << CELL_KEY_BITS) - 1));
                if (!chan[c]) continue;

                int count = endInLake(c, streams, 0);

                if (count < 0 && fromLake(c) && dryUp(c)) {
                    WatershedStats.deadEndOutletsClosed.increment();
                    continue;
                }
                (count >= 0 ? WatershedStats.deadEndLakes : WatershedStats.deadEndsLeft).increment();
                if (count < 0 && LAB_LOG_CYCLES != null) labLog(String.format("tile %d,%d no room for a dead-end lake: river at cell (%d,%d) w %.1f%s%n",
                        tile.tileX, tile.tileZ, cx0 + (c & W_MASK), cz0 + (c >> W_SHIFT), cellWidth(c), dropped[c] ? " (dropped cycle)" : ""),
                        cx0 + (c & W_MASK), cz0 + (c >> W_SHIFT));
                for (int k = 0; k < count; k++) {
                    final int s = streams[k];
                    if (!chan[s] || reopenBelow(s, false)) continue;
                    final int more = endInLake(s, streams, count);
                    if (more >= 0) {
                        WatershedStats.deadEndLakes.increment();
                        count = more;
                    } else if (dryUp(s)) {
                        WatershedStats.deadEndStreams.increment();
                    } else {
                        reopenBelow(s, true);
                        WatershedStats.deadEndsLeft.increment();
                    }
                }
            }
        }

        private boolean comesBack(int c, int d) {
            final int wx = cx0 + (d & W_MASK), wz = cz0 + (d >> W_SHIFT);
            final int n = neighbourOf(wx, wz);
            if (n < 0 || around[n] == null) return false;
            short exit = exitOfIn(n, wx, wz);
            if (exit >= 0 && withdrawn[n][exit]) exit = wayOut[n][exit];
            if (exit < 0) return false;
            final SeamExit e = around[n].exits().get(exit);
            final int back = windowCell(e.cellX(), e.cellZ());
            return back >= 0 && inCore(back) && inflow[back];
        }

        private boolean fromLake(int c) {
            for (int p = c, steps = 0; steps < N; steps++) {
                final int u = mainUp(p);
                if (u < 0) return false;
                if (lakeId[u] >= 0) return inCore(u);
                if (!chan[u] || !inCore(u)) return false;
                p = u;
            }
            return false;
        }

        private int rimDistance(int c) {
            final int i = (c & W_MASK) - HALO, j = (c >> W_SHIFT) - HALO;
            return Math.min(Math.min(i, CORE - 1 - i), Math.min(j, CORE - 1 - j));
        }

        private int endInLake(int c, int[] streams, int from) {
            final int[] chain = new int[2 * (DEAD_END_LAKE_SEARCH + 1)];
            int length = 0;
            for (int p = c; length < chain.length; ) {
                chain[length++] = p;
                final int u = mainUp(p);
                if (u < 0 || !inCore(u) || !chan[u] || lakeId[u] >= 0) break;
                p = u;
            }
            final int full = length;
            length = Math.min(length, DEAD_END_LAKE_SEARCH + 1);
            final int[] reach = chainReach(chain, length);
            final boolean[] apart = keptApart(chain, length, reach);
            final int joined = joinLake(chain, length, reach, apart, false);
            if (joined >= 0) return streamsBelow(chain, joined, streams, from);
            final int want = Mth.clamp((int) Math.round(cellWidth(c) * DEAD_END_LAKE_SPAN),
                    DEAD_END_LAKE_MIN, DEAD_END_LAKE_MAX);
            final int[] cells = new int[N];
            for (int r = want; ; r = Math.max(DEAD_END_LAKE_LAST, r * 4 / 5)) {
                final int made = growInto(chain, length, full, r, reach, apart, cells, false, streams, from);
                if (made >= 0) return made;
                if (r == DEAD_END_LAKE_LAST) break;
            }

            final int edge = joinLake(chain, length, reach, apart, true);
            if (edge >= 0) return streamsBelow(chain, edge, streams, from);
            return growInto(chain, length, full, DEAD_END_LAKE_LAST, reach, apart, cells, true, streams, from);
        }

        private int growInto(int[] chain, int length, int full, int r, int[] reach, boolean[] apart, int[] cells,
                boolean toSeam, int[] streams, int from) {
            final int room = (int) Math.round(Math.PI * r * r);

            final int inset = r + (toSeam ? 1 : DEAD_END_LAKE_GAP);
            final double reachOut = DEAD_END_LAKE_REACH * r;
            for (int m = 0; m < length; m++) {
                final int ci = Mth.clamp(chain[m] & W_MASK, HALO + inset, HALO + CORE - 1 - inset);
                final int cj = Mth.clamp(chain[m] >> W_SHIFT, HALO + inset, HALO + CORE - 1 - inset);

                int top = m;
                while (top + 1 < length && Math.hypot((chain[top + 1] & W_MASK) - ci,
                        (chain[top + 1] >> W_SHIFT) - cj) <= reachOut) {
                    top++;
                }

                if (toSeam) top = Math.max(top, Math.min(length - 1, m + DEAD_END_LAKE_GAP + 2));
                final int waterY = riverLevel(chain[m]);
                final int count = growLake(chain[m], ci, cj, r, room, waterY, top, reach, apart, cells, toSeam);
                if (count < DEAD_END_LAKE_FULL * room) continue;

                int inlet = m + 1;
                for (int k = m + 1; k < full; k++) {
                    if (floodMark[chain[k]] == floodStamp) inlet = k + 1;
                }
                makeLake(chain, inlet, Arrays.copyOf(cells, count), waterY);
                return streamsBelow(chain, inlet, streams, from);
            }
            return -1;
        }

        private int[] chainReach(int[] chain, int length) {
            final int[] reach = new int[N];
            Arrays.fill(reach, -2);
            for (int k = 0; k < length; k++) reach[chain[k]] = k;
            final int[] walk = new int[N];
            for (int s = 0; s < N; s++) {
                if (reach[s] != -2) continue;
                int count = 0, q = s;
                while (q >= 0 && reach[q] == -2) {
                    reach[q] = -3;
                    walk[count++] = q;
                    q = sink[q] || lakeId[q] >= 0 ? -1 : downstream(q);
                }
                final int to = q >= 0 && reach[q] >= 0 ? reach[q] : -1;
                while (count > 0) reach[walk[--count]] = to;
            }
            return reach;
        }

        private boolean[] keptApart(int[] chain, int length, int[] reach) {
            final boolean[] apart = new boolean[N];
            int loI = W, hiI = -1, loJ = W, hiJ = -1;
            for (int k = 0; k < length; k++) {
                loI = Math.min(loI, chain[k] & W_MASK);
                hiI = Math.max(hiI, chain[k] & W_MASK);
                loJ = Math.min(loJ, chain[k] >> W_SHIFT);
                hiJ = Math.max(hiJ, chain[k] >> W_SHIFT);
            }
            final int reachOut = 3 * DEAD_END_LAKE_MAX + DEAD_END_LAKE_GAP + 4;
            final int i0 = Math.max(0, loI - reachOut), i1 = Math.min(W - 1, hiI + reachOut);
            final int j0 = Math.max(0, loJ - reachOut), j1 = Math.min(W - 1, hiJ + reachOut);
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    final int q = (j << W_SHIFT) | i;

                    final int r = sink[q] ? 2 : chan[q] && reach[q] < 0 && inCore(q) ? 1 + (int) (cellWidth(q) / 16.0) : -1;
                    if (r < 0) continue;
                    for (int dj = -r; dj <= r; dj++) {
                        for (int di = -r; di <= r; di++) {
                            final int ni = i + di, nj = j + dj;
                            if (ni >= 0 && nj >= 0 && ni < W && nj < W) apart[(nj << W_SHIFT) | ni] = true;
                        }
                    }
                }
            }
            return apart;
        }

        private int growLake(int p, int ci, int cj, int r, int room, int waterY, int top, int[] reach,
                boolean[] apart, int[] out, boolean toSeam) {
            if (floodMark == null) floodMark = new int[N];
            final int stamp = ++floodStamp;
            final int edge = toSeam ? DEAD_END_LAKE_EDGE : 1;
            if (apart[p] || rimDistance(p) < edge || h[p] > waterY + DEAD_END_LAKE_DIG - 1) return 0;
            int count = 0;
            int qi = p & W_MASK, qj = p >> W_SHIFT;
            int loI = qi, hiI = qi, loJ = qj, hiJ = qj;

            while (true) {
                final int q = (qj << W_SHIFT) | qi;
                if (count > 0 && !lakeMayTake(q, waterY, top, reach, apart, true, -1, edge)) return 0;
                floodMark[q] = stamp;
                out[count++] = q;
                if (qi == ci && qj == cj) break;

                if (toSeam && qi != ci && qj != cj) {
                    qi += Integer.signum(ci - qi);
                    qj += Integer.signum(cj - qj);
                } else if (Math.abs(ci - qi) >= Math.abs(cj - qj)) {
                    qi += Integer.signum(ci - qi);
                } else {
                    qj += Integer.signum(cj - qj);
                }
            }
            loI = Math.min(loI, ci);
            hiI = Math.max(hiI, ci);
            loJ = Math.min(loJ, cj);
            hiJ = Math.max(hiJ, cj);
            final double reachOut = DEAD_END_LAKE_REACH * r;
            heapSize = 0;
            for (int k = 0; k < count; k++) queueAround(out[k], ci, cj, r, reachOut, waterY, top, reach, apart, edge);
            while (count < room && heapSize > 0) {
                final int q = (int) (pop() & ((1L << CELL_KEY_BITS) - 1));
                out[count++] = q;
                loI = Math.min(loI, q & W_MASK);
                hiI = Math.max(hiI, q & W_MASK);
                loJ = Math.min(loJ, q >> W_SHIFT);
                hiJ = Math.max(hiJ, q >> W_SHIFT);
                queueAround(q, ci, cj, r, reachOut, waterY, top, reach, apart, edge);
            }

            while (heapSize > 0) floodMark[(int) (pop() & ((1L << CELL_KEY_BITS) - 1))] = 0;

            final int pi = p & W_MASK, pj = p >> W_SHIFT, span = top - reach[p] + 1;
            for (boolean grew = toSeam; grew; ) {
                grew = false;
                for (int dj = -span; dj <= span && count < out.length; dj++) {
                    final int nj = pj + dj;
                    if (nj < 1 || nj >= W - 1) continue;
                    for (int di = -span; di <= span; di++) {
                        final int ni = pi + di;
                        if (ni < 1 || ni >= W - 1) continue;
                        final int q = (nj << W_SHIFT) | ni;
                        if (floodMark[q] == stamp || !chan[q] || reach[q] <= reach[p] || reach[q] > top) continue;
                        if (floodMark[q + 1] != stamp && floodMark[q - 1] != stamp && floodMark[q + W] != stamp
                                && floodMark[q - W] != stamp) continue;
                        if (!lakeMayTake(q, waterY, top, reach, apart, false, -1, edge)) continue;
                        floodMark[q] = stamp;
                        out[count++] = q;
                        loI = Math.min(loI, ni);
                        hiI = Math.max(hiI, ni);
                        loJ = Math.min(loJ, nj);
                        hiJ = Math.max(hiJ, nj);
                        grew = true;
                    }
                }
            }

            for (int j = Math.max(1, loJ); j <= Math.min(W - 2, hiJ); j++) {
                for (int i = Math.max(1, loI); i <= Math.min(W - 2, hiI); i++) {
                    final int q = (j << W_SHIFT) | i;
                    if (floodMark[q] == stamp) continue;
                    final int around = (floodMark[q + 1] == stamp ? 1 : 0) + (floodMark[q - 1] == stamp ? 1 : 0)
                            + (floodMark[q + W] == stamp ? 1 : 0) + (floodMark[q - W] == stamp ? 1 : 0);
                    if (around >= 3 && lakeMayTake(q, waterY, top, reach, apart, false, -1, edge)) {
                        floodMark[q] = stamp;
                        out[count++] = q;
                    }
                }
            }
            return count;
        }

        private void queueAround(int q, int ci, int cj, int r, double reachOut, int waterY, int top, int[] reach,
                boolean[] apart, int edge) {
            final int qi = q & W_MASK, qj = q >> W_SHIFT;
            for (int k = 0; k < 8; k += 2) {
                final int ni = qi + DX[k], nj = qj + DZ[k];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                final int nq = (nj << W_SHIFT) | ni;
                if (floodMark[nq] == floodStamp) continue;
                final double far = Math.hypot(ni - ci, nj - cj);
                if (far > reachOut || !lakeMayTake(nq, waterY, top, reach, apart, false, -1, edge)) continue;
                floodMark[nq] = floodStamp;

                double ground = 0.0;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) ground += h[((nj + dj) << W_SHIFT) | (ni + di)];
                }
                final double turn = far + DEAD_END_LAKE_GROUND * (ground / 9.0 - waterY)
                        + DEAD_END_LAKE_WOBBLE * r * lakeNoise(nq);
                push(heapKeyFor((float) turn, nq));
            }
        }

        private int joinLake(int[] chain, int length, int[] reach, boolean[] apart, boolean toSeam) {
            if (lakeCells.isEmpty()) return -1;
            if (floodMark == null) floodMark = new int[N];
            final int[] queue = new int[N], parent = new int[N], steps = new int[N];
            int bestId = -1, bestAt = -1;
            long bestScore = Long.MIN_VALUE;
            int[] bestParent = null;

            int loI = W, hiI = -1, loJ = W, hiJ = -1;
            for (int k = 0; k < length; k++) {
                loI = Math.min(loI, chain[k] & W_MASK);
                hiI = Math.max(hiI, chain[k] & W_MASK);
                loJ = Math.min(loJ, chain[k] >> W_SHIFT);
                hiJ = Math.max(hiJ, chain[k] >> W_SHIFT);
            }
            final int near = DEAD_END_LAKE_JOIN + 1;
            for (int id = 0; id < lakeCells.size(); id++) {
                final int[] own = lakeCells.get(id);
                if (own == null || own.length == 0 || lakeId[own[0]] != id) continue;
                boolean inReach = false, inCore = false;
                for (int q : own) {
                    final int qi = q & W_MASK, qj = q >> W_SHIFT;
                    if (qi >= loI - near && qi <= hiI + near && qj >= loJ - near && qj <= hiJ + near) inReach = true;
                    if (inCore(q)) inCore = true;
                }
                if (!inReach || !inCore) continue;
                final int waterY = lakeWaterY.get(id);
                final int stamp = ++floodStamp;
                int head = 0, tail = 0;
                for (int q : lakeCells.get(id)) {
                    floodMark[q] = stamp;
                    parent[q] = -1;
                    steps[q] = 0;
                    queue[tail++] = q;
                }
                int at = -1;
                long atScore = Long.MIN_VALUE;
                while (head < tail) {
                    final int q = queue[head++];
                    if (steps[q] >= DEAD_END_LAKE_JOIN) continue;
                    final int qi = q & W_MASK, qj = q >> W_SHIFT;
                    for (int k = 0; k < 8; k += 2) {
                        final int nq = ((qj + DZ[k]) << W_SHIFT) | (qi + DX[k]);
                        if (floodMark[nq] == stamp) continue;
                        floodMark[nq] = stamp;

                        final int on = chan[nq] ? reach[nq] : -1;
                        if (on >= 0 && chain[on] == nq) {
                            if (rimDistance(nq) >= (toSeam ? DEAD_END_LAKE_EDGE : 1) && !apart[nq] && h[nq] <= waterY + DEAD_END_LAKE_DIG - 1
                                    && riverLevel(nq) >= waterY) {
                                parent[nq] = q;
                                steps[nq] = steps[q] + 1;

                                final long score = steps[nq] <= DEAD_END_LAKE_JOIN_NEAR ? (1L << 40) + on
                                        : ((long) (DEAD_END_LAKE_JOIN - steps[nq]) << 20) + on;
                                if (score > atScore) {
                                    atScore = score;
                                    at = nq;
                                }
                            }
                            continue;
                        }
                        if (!lakeMayTake(nq, waterY, length - 1, reach, apart, toSeam, id, toSeam ? DEAD_END_LAKE_EDGE : 1)) continue;
                        parent[nq] = q;
                        steps[nq] = steps[q] + 1;
                        queue[tail++] = nq;
                    }
                }
                if (at >= 0 && atScore > bestScore) {
                    bestId = id;
                    bestAt = at;
                    bestScore = atScore;
                    bestParent = parent.clone();
                }
            }
            if (bestId < 0) return -1;
            final int inlet = reach[bestAt] + 1;
            final int waterY = lakeWaterY.get(bestId);
            final int[] old = lakeCells.get(bestId);
            final int[] way = new int[DEAD_END_LAKE_JOIN + 1];
            int count = 0;
            for (int q = bestAt; q >= 0 && lakeId[q] != bestId; q = bestParent[q]) way[count++] = q;
            final int stamp = ++floodStamp;

            final int side = Math.max(1, (int) Math.ceil(cellWidth(bestAt) / CELL));
            final int[] grown = Arrays.copyOf(old, old.length + count * (2 * side + 1) * (2 * side + 1));
            int n = old.length;
            for (int k = 0; k < count; k++) {
                final int q = way[k];
                if (floodMark[q] != stamp) {
                    floodMark[q] = stamp;
                    grown[n++] = q;
                }
                final int qi = q & W_MASK, qj = q >> W_SHIFT;
                for (int dj = -side; dj <= side; dj++) {
                    for (int di = -side; di <= side; di++) {
                        if (di * di + dj * dj > side * side) continue;
                        final int ni = qi + di, nj = qj + dj;
                        if (ni < 1 || nj < 1 || ni >= W - 1 || nj >= W - 1) continue;
                        final int nq = (nj << W_SHIFT) | ni;
                        if (floodMark[nq] == stamp || !lakeMayTake(nq, waterY, inlet - 1, reach, apart, toSeam, bestId,
                                toSeam ? DEAD_END_LAKE_EDGE : 1)) continue;
                        floodMark[nq] = stamp;
                        grown[n++] = nq;
                    }
                }
            }
            final int[] cells = Arrays.copyOf(grown, n);
            lakeCells.set(bestId, cells);
            for (int k = old.length; k < n; k++) {
                final int q = cells[k];
                lakeId[q] = bestId;
                lakeShore[q] = -1;
                chan[q] = false;
                h[q] = Math.min(h[q], waterY - 1);
                breached[q] = waterY;
            }
            for (int k = 0; k < inlet; k++) chan[chain[k]] = false;
            inflowsIntoLake(chain, inlet, bestId);

            deadEndLakeIds.add(bestId);
            return inlet;
        }

        private boolean lakeMayTake(int q, int waterY, int top, int[] reach, boolean[] apart, boolean wayIn, int joinId,
                int edge) {
            if (!inCore(q) || apart[q] || lakeId[q] >= 0 || h[q] > waterY + DEAD_END_LAKE_DIG - 1) return false;
            final int qi = q & W_MASK, qj = q >> W_SHIFT;
            for (int k = -1; k < 8; k++) {
                final int c = k < 0 ? q : ((qj + DZ[k]) << W_SHIFT) | (qi + DX[k]);
                if (chan[c] && reach[c] > top || lakeId[c] >= 0 && lakeId[c] != joinId
                        || lakeShore[c] >= 0 && lakeShore[c] != joinId) return false;
            }
            if (chan[q] && reach[q] >= 0) return rimDistance(q) >= edge;

            if (wayIn || edge < 1) return rimDistance(q) >= 1;
            final double x = (cx0 + qi) / 4.0, z = (cz0 + qj) / 4.0;
            final int wobble = Math.min(DEAD_END_LAKE_RIM_WOBBLE,
                    (int) ((DEAD_END_LAKE_RIM_WOBBLE + 1) * (0.5 + 0.5 * valueNoise(x, z, 2))));
            return rimDistance(q) >= DEAD_END_LAKE_GAP + wobble;
        }

        private double lakeNoise(int c) {
            final double x = (cx0 + (c & W_MASK)) / DEAD_END_LAKE_WAVE, z = (cz0 + (c >> W_SHIFT)) / DEAD_END_LAKE_WAVE;
            return 0.7 * valueNoise(x, z, 0) + 0.3 * valueNoise(x * 2.3, z * 2.3, 1);
        }

        private double valueNoise(double x, double z, int salt) {
            final int ix = Mth.floor(x), iz = Mth.floor(z);
            final double u = smoothstep(x - ix), v = smoothstep(z - iz);
            return Mth.lerp(v, Mth.lerp(u, lattice(ix, iz, salt), lattice(ix + 1, iz, salt)),
                    Mth.lerp(u, lattice(ix, iz + 1, salt), lattice(ix + 1, iz + 1, salt)));
        }

        private double lattice(int ix, int iz, int salt) {
            final long hsh = it.unimi.dsi.fastutil.HashCommon.mix(
                    seed ^ ix * 0x9E3779B97F4A7C15L ^ iz * 0xC2B2AE3D27D4EB4FL ^ (salt + 1) * 0x632BE59BD9B4E019L);
            return (hsh >>> 11) * 0x1.0p-53 * 2.0 - 1.0;
        }

        private int riverLevel(int p) {
            float level = breached[p];
            for (int q = p, steps = 0; steps < N; steps++) {
                final int u = mainUp(q);
                if (u < 0 || !chan[u] || lakeId[u] >= 0) break;
                level = Math.min(level, breached[u]);
                if (!inCore(u)) break;
                q = u;
            }
            return Math.max(Math.round(level) - CHANNEL_INCISION, seaLevel - 1);
        }

        private void makeSeamLake(SeamLake s) {
            final int[] cells = Arrays.stream(s.cells()).filter(q -> lakeId[q] < 0).toArray();
            if (cells.length == 0) return;
            final int id = lakeCells.size();
            lakeCells.add(cells);
            lakeShoreCells.add(new int[0]);
            lakeWaterY.add(s.waterY());
            deadEndLakeIds.add(id);
            if (lakeClosed.length <= id) lakeClosed = Arrays.copyOf(lakeClosed, id + 1);
            lakeClosed[id] = true;
            for (int q : cells) {
                lakeId[q] = id;
                lakeShore[q] = -1;
                chan[q] = false;
                h[q] = Math.min(h[q], s.waterY() - 1);
                breached[q] = s.waterY();
            }
            WatershedStats.seamLakes.increment();
        }

        private void makeLake(int[] chain, int inlet, int[] cells, int waterY) {
            final int id = lakeCells.size();
            lakeCells.add(cells);
            lakeShoreCells.add(new int[0]);
            lakeWaterY.add(waterY);
            deadEndLakeIds.add(id);
            if (lakeClosed.length <= id) lakeClosed = Arrays.copyOf(lakeClosed, id + 1);
            lakeClosed[id] = true;
            for (int q : cells) {
                lakeId[q] = id;
                chan[q] = false;

                h[q] = Math.min(h[q], waterY - 1);
                breached[q] = waterY;
            }
            for (int k = 0; k < inlet; k++) chan[chain[k]] = false;

            final int over = inlet > 0 ? downstream(chain[0]) : -1;
            if (over >= 0 && !inCore(over) && chan[over]) {
                boolean fed = false;
                final int oi = over & W_MASK, oj = over >> W_SHIFT;
                for (int a = 0; a < 8 && !fed; a++) {
                    final int ni = oi + DX[a], nj = oj + DZ[a];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    final int u = (nj << W_SHIFT) | ni;
                    fed = chan[u] && downstream(u) == over;
                }
                if (!fed) chan[over] = false;
            }
            inflowsIntoLake(chain, inlet, id);
        }

        private void inflowsIntoLake(int[] chain, int inlet, int id) {
            for (int k = 0; k < inlet; k++) {
                final int c = chain[k];
                if (!inflow[c] || lakeId[c] >= 0) continue;
                final int ci = c & W_MASK, cj = c >> W_SHIFT;
                for (int a = 0; a < 8; a++) {
                    final int nc = ((cj + DZ[a]) << W_SHIFT) | (ci + DX[a]);
                    if (lakeId[nc] != id) continue;
                    dir[c] = (byte) a;
                    chan[c] = true;
                    break;
                }
            }
        }

        private int streamsBelow(int[] chain, int inlet, int[] streams, int from) {
            int count = from;
            for (int k = 0; k < inlet; k++) {
                final int d = chain[k];
                if (lakeId[d] >= 0) continue;
                if (inflow[d] && !chan[d] && inCore(d) && count < streams.length) {
                    chan[d] = true;
                    streams[count++] = d;
                }
                final int di = d & W_MASK, dj = d >> W_SHIFT;
                for (int a = 0; a < 8; a++) {
                    final int u = ((dj + DZ[a]) << W_SHIFT) | (di + DX[a]);
                    if (chan[u] && inCore(u) && downstream(u) == d && count < streams.length) streams[count++] = u;
                }
            }
            return count;
        }

        private boolean reopenBelow(int s, boolean toSeam) {
            final int[] way = new int[CORE * CORE];
            final int level = Math.round(breached[s]);
            int count = 0;
            for (int c = s; count < way.length; ) {
                final int into = lakeBeside(c);
                if (into >= 0) {
                    dir[c] = (byte) into;
                    for (int k = 0; k < count; k++) chan[way[k]] = true;
                    return true;
                }
                final int q = downstream(c);
                if (q < 0) return false;
                if (lakeId[q] >= 0 && lakeWaterY.get(lakeId[q]) > level - CHANNEL_INCISION
                        || chan[q] && !sink[q] && Math.round(breached[q]) > level) return false;

                final boolean water = lakeId[q] >= 0 || chan[q] && inCore(q) || sink[q];
                if (water || !inCore(q)) {
                    if (!water && !toSeam) return false;
                    for (int k = 0; k < count; k++) chan[way[k]] = true;
                    return true;
                }
                way[count++] = q;
                c = q;
            }
            return false;
        }

        private int lakeBeside(int c) {
            final int ci = c & W_MASK, cj = c >> W_SHIFT;
            int level = Integer.MIN_VALUE;
            for (int a = 0; a < 8; a++) {
                final int ni = ci + DX[a], nj = cj + DZ[a];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                final int nc = (nj << W_SHIFT) | ni;
                if (lakeId[nc] < 0 || !inCore(nc)) continue;
                if (level == Integer.MIN_VALUE) level = riverLevel(c);
                if (lakeWaterY.get(lakeId[nc]) <= level) return a;
            }
            return -1;
        }

        private boolean dryUp(int s) {
            final int[] tree = new int[N];
            final boolean[] seen = new boolean[N];
            int count = 0;
            tree[count++] = s;
            seen[s] = true;
            for (int k = 0; k < count; k++) {
                final int d = tree[k];
                if (inflow[d] || !inCore(d)) return false;
                final int di = d & W_MASK, dj = d >> W_SHIFT;
                for (int a = 0; a < 8; a++) {
                    final int ni = di + DX[a], nj = dj + DZ[a];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    final int u = (nj << W_SHIFT) | ni;
                    if (!seen[u] && chan[u] && downstream(u) == d) {
                        seen[u] = true;
                        tree[count++] = u;
                    }
                }
            }
            for (int k = 0; k < count; k++) chan[tree[k]] = false;
            return true;
        }

        void breakSeamCycles(boolean[][] withdrawn) {
            final List<SeamExit> own = ownSeams.exits();
            boolean turned = false;
            CycleScratch scratch = null;
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (around[n] == null) continue;
                final List<SeamExit> theirs = around[n].exits();
                final int e0 = own.size(), total = e0 + theirs.size();
                final int ntx = tile.tileX + SEAM_NEIGHBOURS[n][0], ntz = tile.tileZ + SEAM_NEIGHBOURS[n][1];

                final int[] succ = new int[total];
                final boolean[] pair = new boolean[total];
                for (int x = 0; x < e0; x++) {
                    final SeamExit e = own.get(x);
                    succ[x] = -1;
                    if (!inTile(ntx, ntz, e.cellX(), e.cellZ())) continue;
                    pair[x] = true;
                    final short y = exitOfIn(n, e.cellX(), e.cellZ());
                    if (y >= 0 && inTile(tile.tileX, tile.tileZ, theirs.get(y).cellX(), theirs.get(y).cellZ())) succ[x] = e0 + y;
                }
                for (int y = 0; y < theirs.size(); y++) {
                    final SeamExit e = theirs.get(y);
                    succ[e0 + y] = -1;
                    if (!inTile(tile.tileX, tile.tileZ, e.cellX(), e.cellZ())) continue;
                    pair[e0 + y] = true;
                    final short x = ownExitAt(e.cellX(), e.cellZ());
                    if (x >= 0 && inTile(ntx, ntz, own.get(x).cellX(), own.get(x).cellZ())) succ[e0 + y] = x;
                }

                final byte[] state = new byte[total];
                final boolean[] cyclic = new boolean[total];
                final int[] cycleOf = new int[total];
                final List<int[]> cycles = new ArrayList<>();
                final int[] walk = new int[total];
                for (int s = 0; s < total; s++) {
                    if (!pair[s] || state[s] != 0) continue;
                    int len = 0, cur = s;
                    while (cur >= 0 && state[cur] == 0) {
                        state[cur] = 1;
                        walk[len++] = cur;
                        cur = succ[cur];
                    }
                    int into = cur >= 0 && cyclic[cur] ? cycleOf[cur] : -1;
                    if (cur >= 0 && state[cur] == 1) {
                        int from = 0;
                        while (walk[from] != cur) from++;
                        into = cycles.size();
                        cycles.add(Arrays.copyOfRange(walk, from, len));
                    }
                    for (int k = 0; k < len; k++) {
                        state[walk[k]] = 2;
                        if (into >= 0) {
                            cyclic[walk[k]] = true;
                            cycleOf[walk[k]] = into;
                        }
                    }
                }

                final float[] cycleLevel = new float[cycles.size()];
                Arrays.fill(cycleLevel, Float.POSITIVE_INFINITY);
                for (int m = 0; m < total; m++) {
                    if (!cyclic[m]) continue;
                    final float lv = (m < e0 ? own.get(m) : theirs.get(m - e0)).level();
                    cycleLevel[cycleOf[m]] = Math.min(cycleLevel[cycleOf[m]], lv);
                }
                if (!cycles.isEmpty() && scratch == null) scratch = new CycleScratch();

                final boolean[] dead = new boolean[total];
                for (int ci = 0; ci < cycles.size(); ci++) {
                    final int[] cycle = cycles.get(ci);
                    final Plan mine = planCycle(n, cycle, true, e0, pair, cyclic, cycleLevel[ci], scratch);
                    final Plan other = planCycle(n, cycle, false, e0, pair, cyclic, cycleLevel[ci], scratch);
                    if (LAB_LOG_CYCLES != null) {
                        labLogCycle(n, cycle, e0, theirs, mine, other);
                        final SeamExit first = cycle[0] < e0 ? own.get(cycle[0]) : theirs.get(cycle[0] - e0);
                        labLog(String.format("   water into it as low as %.2f; %d crossings lead in%n", cycleLevel[ci],
                                countInto(cycleOf, cyclic, ci)), first.fromX(), first.fromZ());
                    }
                    if (mine == null && other == null) {
                        if (!neighbour) WatershedStats.seamCyclesLeft.increment();

                        int ownWater = 0, theirWater = 0;
                        for (int m : cycle) {
                            if (m < e0) ownWater = Math.max(ownWater, own.get(m).acc());
                            else theirWater = Math.max(theirWater, theirs.get(m - e0).acc());
                        }
                        final boolean mineStand = ownWater > theirWater || ownWater == theirWater
                                && (tile.tileX < ntx || tile.tileX == ntx && tile.tileZ < ntz);
                        for (int m : cycle) {
                            if (m >= e0) {
                                if (mineStand) withdrawn[n][m - e0] = true;
                            } else if (!mineStand) {
                                final SeamExit e = own.get(m);
                                dropped[((e.fromZ() - cz0) << W_SHIFT) | (e.fromX() - cx0)] = true;
                            }
                        }

                        for (int m = 0; m < total; m++) {
                            if (!cyclic[m] || cycleOf[m] != ci) continue;
                            dead[m] = true;
                            if (inCycle(cycle, m)) continue;
                            if (m >= e0) {
                                withdrawn[n][m - e0] = true;
                            } else {
                                final SeamExit e = own.get(m);
                                dropped[((e.fromZ() - cz0) << W_SHIFT) | (e.fromX() - cx0)] = true;
                            }
                        }
                        continue;
                    }
                    final boolean mineCarries = other == null || mine != null && (mine.cost() < other.cost()
                            || mine.cost() == other.cost() && (tile.tileX < ntx || tile.tileX == ntx && tile.tileZ < ntz));
                    if (!neighbour) WatershedStats.seamCyclesBroken.increment();
                    if (mineCarries) {
                        for (int k = 0; k < mine.cells().length; k++) dir[mine.cells()[k]] = mine.dirs()[k];
                        turned = true;

                        for (int t : mine.cells()) {
                            final int y = ownSeams.exitOf()[coreIndex(t)];
                            if (y < 0 || !pair[y] || inCycle(cycle, y)) continue;
                            final SeamExit e = own.get(y);
                            dropped[((e.fromZ() - cz0) << W_SHIFT) | (e.fromX() - cx0)] = true;
                        }
                        continue;
                    }

                    for (int k = 0; k < other.crossings().length; k++) {
                        final int y = other.crossings()[k];
                        withdrawn[n][y] = true;
                        redirect(n, y, other.leaveBy()[k], theirs, cycleLevel[ci]);
                    }

                    for (int k = 0; k < other.cells().length; k++) {
                        final int t = other.cells()[k];
                        final int y = exitOfIn(n, cx0 + (t & W_MASK), cz0 + (t >> W_SHIFT));
                        if (y < 0 || !pair[e0 + y] || withdrawn[n][y]) continue;
                        withdrawn[n][y] = true;
                        redirect(n, y, other.cellLeave()[k], theirs, cycleLevel[ci]);
                    }
                }
                if (lakesEnabled && !neighbour) planSeamLakes(n, e0, theirs, dead);
            }
            if (turned) reorder();
        }

        private void planSeamLakes(int n, int e0, List<SeamExit> theirs, boolean[] dead) {
            final List<SeamExit> own = ownSeams.exits();
            final boolean acrossX = SEAM_NEIGHBOURS[n][0] != 0;
            final long[] sorted = new long[dead.length];
            int count = 0;
            for (int m = 0; m < dead.length; m++) {
                if (!dead[m]) continue;
                final SeamExit e = m < e0 ? own.get(m) : theirs.get(m - e0);
                sorted[count++] = ((long) (acrossX ? e.fromZ() : e.fromX()) << 32) | m;
            }
            Arrays.sort(sorted, 0, count);
            for (int k = 0; k < count; ) {
                int end = k;
                while (end + 1 < count && (int) (sorted[end + 1] >> 32) - (int) (sorted[end] >> 32) <= SEAM_LAKE_RUN_GAP) end++;
                planSeamLake(n, e0, theirs, sorted, k, end);
                k = end + 1;
            }
        }

        private void planSeamLake(int n, int e0, List<SeamExit> theirs, long[] sorted, int from, int to) {
            final List<SeamExit> own = ownSeams.exits();
            final int dx = SEAM_NEIGHBOURS[n][0], dz = SEAM_NEIGHBOURS[n][1];
            final boolean acrossX = dx != 0;
            final int ntx = tile.tileX + dx, ntz = tile.tileZ + dz;
            final int seam = acrossX ? (dx > 0 ? tile.tileX + 1 : tile.tileX) * CORE : (dz > 0 ? tile.tileZ + 1 : tile.tileZ) * CORE;
            int a0 = (int) (sorted[from] >> 32), a1 = (int) (sorted[to] >> 32);
            float level = Float.POSITIVE_INFINITY;
            int biggest = -1, biggestAt = a0;
            for (int t = from; t <= to; t++) {
                final int m = (int) sorted[t];
                final SeamExit e = m < e0 ? own.get(m) : theirs.get(m - e0);
                if (!e.channel()) continue;
                level = Math.min(level, e.level());
                if (e.acc() > biggest) {
                    biggest = e.acc();
                    biggestAt = (int) (sorted[t] >> 32);
                }
            }
            if (biggest < 0) return;
            if (a1 - a0 >= SEAM_LAKE_RUN_MAX) {
                a0 = Math.max(a0, biggestAt - SEAM_LAKE_RUN_MAX / 2);
                a1 = Math.min(a1, a0 + SEAM_LAKE_RUN_MAX - 1);
            }
            final int waterY = Math.max(Math.round(level) - CHANNEL_INCISION, seaLevel - 1);
            final int r = Mth.clamp((int) Math.round(SEAM_LAKE_SPAN * (a1 - a0 + 1)), SEAM_LAKE_MIN, SEAM_LAKE_MAX);
            final int reach = (int) Math.ceil(DEAD_END_LAKE_REACH * r);

            final boolean[] may = new boolean[N], take = new boolean[N];
            for (int b = a0 - reach; b <= a1 + reach; b++) {
                final double along = b < a0 ? a0 - b : b > a1 ? b - a1 : 0.0;
                for (int a = seam - reach; a < seam + reach; a++) {
                    final int wx = acrossX ? a : b, wz = acrossX ? b : a;
                    final int c = windowCell(wx, wz);
                    if (c < 0 || !seamLakeMay(c, wx, wz, b, ntx, ntz, waterY)) continue;
                    may[c] = true;
                    double ground = 0.0;
                    for (int dj = -1; dj <= 1; dj++) {
                        for (int di = -1; di <= 1; di++) ground += h[c + dj * W + di];
                    }
                    final double far = Math.hypot(a >= seam ? a - seam + 0.5 : seam - a - 0.5, along);
                    take[c] = far + DEAD_END_LAKE_GROUND * (ground / 9.0 - waterY) + DEAD_END_LAKE_WOBBLE * r * lakeNoise(c) <= r;
                }
            }

            final int[] cells = new int[N];
            final boolean[] reached = new boolean[N];
            int count = 0;
            for (int t = from; t <= to; t++) {
                final int m = (int) sorted[t];
                final SeamExit e = m < e0 ? own.get(m) : theirs.get(m - e0);
                final int c = windowCell(e.fromX(), e.fromZ());
                if (c < 0 || !take[c] || reached[c]) continue;
                reached[c] = true;
                cells[count++] = c;
            }
            for (int head = 0; head < count; head++) {
                final int q = cells[head];
                for (int k = 0; k < 8; k += 2) {
                    final int nq = q + DZ[k] * W + DX[k];
                    if (nq < 0 || nq >= N || !take[nq] || reached[nq]) continue;
                    reached[nq] = true;
                    cells[count++] = nq;
                }
            }
            if (count < 2 * SEAM_LAKE_MIN * SEAM_LAKE_MIN) return;

            for (int b = a0 - reach; b <= a1 + reach; b++) {
                for (int a = seam - reach; a < seam + reach; a++) {
                    final int c = windowCell(acrossX ? a : b, acrossX ? b : a);
                    if (c < 0 || !may[c] || reached[c]) continue;
                    final int round = (reached[c + 1] ? 1 : 0) + (reached[c - 1] ? 1 : 0) + (reached[c + W] ? 1 : 0) + (reached[c - W] ? 1 : 0);
                    if (round < 3) continue;
                    reached[c] = true;
                    cells[count++] = c;
                }
            }
            seamLakes.add(new SeamLake(Arrays.copyOf(cells, count), waterY));
            if (LAB_LOG_CYCLES != null) labLog(String.format("tile %d,%d side %d seam lake: along %d..%d level %.2f water %d radius %d, %d cells%n",
                    tile.tileX, tile.tileZ, n, a0, a1, level, waterY, r, count), acrossX ? seam : a0, acrossX ? a0 : seam);
        }

        private boolean seamLakeMay(int c, int wx, int wz, int along, int ntx, int ntz, int waterY) {
            final int i = c & W_MASK, j = c >> W_SHIFT;
            if (i < 2 || j < 2 || i >= W - 2 || j >= W - 2 || h[c] > waterY + DEAD_END_LAKE_DIG - 1) return false;
            if (!inTile(tile.tileX, tile.tileZ, wx, wz) && !inTile(ntx, ntz, wx, wz)) return false;

            final int off = Math.floorMod(along, CORE);
            final int wobble = Math.min(DEAD_END_LAKE_RIM_WOBBLE,
                    (int) ((DEAD_END_LAKE_RIM_WOBBLE + 1) * (0.5 + 0.5 * valueNoise(wx / 4.0, wz / 4.0, 2))));
            if (Math.min(off, CORE - 1 - off) < DEAD_END_LAKE_GAP + wobble) return false;
            for (int dj = -2; dj <= 2; dj++) {
                for (int di = -2; di <= 2; di++) {
                    final int q = c + dj * W + di;
                    if (sink[q] || Math.abs(di) <= 1 && Math.abs(dj) <= 1 && (lakeId[q] >= 0 || lakeShore[q] >= 0)) return false;
                }
            }
            return true;
        }

        private void dropPassingThrough(boolean[][] withdrawn) {
            final List<SeamExit> own = ownSeams.exits();
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (around[n] == null) continue;
                final int ntx = tile.tileX + SEAM_NEIGHBOURS[n][0], ntz = tile.tileZ + SEAM_NEIGHBOURS[n][1];
                for (SeamExit x : own) {
                    if (!x.channel() || !inTile(ntx, ntz, x.cellX(), x.cellZ())) continue;
                    final short e = exitOfIn(n, x.cellX(), x.cellZ());
                    if (e < 0) continue;
                    final SeamExit beyond = around[n].exits().get(e);
                    final boolean dies = diesBeyond(x, beyond);
                    if (!dies && !climbsBeyond(x, beyond)) continue;
                    final int c = windowCell(x.fromX(), x.fromZ());
                    if (c < 0 || dropped[c]) continue;
                    dropped[c] = true;
                    if (!neighbour) (dies ? WatershedStats.seamPassingThrough : WatershedStats.seamPassingClimb).increment();
                }
                final List<SeamExit> theirs = around[n].exits();
                for (int y = 0; y < theirs.size(); y++) {
                    final SeamExit x = theirs.get(y);
                    if (withdrawn[n][y] || !x.channel() || !inTile(tile.tileX, tile.tileZ, x.cellX(), x.cellZ())) continue;
                    final short e = ownExitAt(x.cellX(), x.cellZ());
                    if (e >= 0 && (diesBeyond(x, own.get(e)) || climbsBeyond(x, own.get(e)))) withdrawn[n][y] = true;
                }
            }
        }

        private static boolean climbsBeyond(SeamExit x, SeamExit e) {
            if (!e.channel() || Math.round(e.level()) <= Math.round(x.level())) return false;
            if (Math.max(Math.abs(e.fromX() - x.cellX()), Math.abs(e.fromZ() - x.cellZ())) > PASS_CLIMB_CELLS) return false;
            return Math.floorDiv(e.cellX(), CORE) != Math.floorDiv(x.fromX(), CORE)
                    || Math.floorDiv(e.cellZ(), CORE) != Math.floorDiv(x.fromZ(), CORE);
        }

        private static boolean diesBeyond(SeamExit x, SeamExit e) {
            if (e.channel()) return false;
            if (Math.max(Math.abs(e.fromX() - x.cellX()), Math.abs(e.fromZ() - x.cellZ())) > PASS_THROUGH_CELLS) return false;

            return Math.floorDiv(e.cellX(), CORE) != Math.floorDiv(x.fromX(), CORE)
                    || Math.floorDiv(e.cellZ(), CORE) != Math.floorDiv(x.fromZ(), CORE);
        }

        private void redirect(int n, int y, int out, List<SeamExit> theirs, float level) {
            wayOut[n][y] = (short) out;
            if (out < 0) return;
            final SeamExit e = theirs.get(y);
            final SeamExit to = theirs.get(out);
            if (!inTile(tile.tileX, tile.tileZ, to.cellX(), to.cellZ())) return;
            final int c = ((to.cellZ() - cz0) << W_SHIFT) | (to.cellX() - cx0);
            seamLevel[c] = Math.min(seamLevel[c], level);
            inAcc[c] += e.acc();
            inWet[c] += e.wet();
            inLen[c] = Math.max(inLen[c], e.flowLen() + 1);
            if (e.channel()) inflow[c] = true;
        }

        private static boolean inCycle(int[] cycle, int x) {
            for (int m : cycle) if (m == x) return true;
            return false;
        }

        private void labLogCycle(int n, int[] cycle, int e0, List<SeamExit> theirs, Plan mine, Plan other) {
            final List<SeamExit> own = ownSeams.exits();
            boolean in = false;
            final StringBuilder sb = new StringBuilder();
            sb.append("tile ").append(tile.tileX).append(',').append(tile.tileZ).append(" side ").append(n).append(" cycle:");
            for (int m : cycle) {
                final SeamExit e = m < e0 ? own.get(m) : theirs.get(m - e0);
                final int bx = e.fromX() * CELL, bz = e.fromZ() * CELL;
                if (bx >= LAB_LOG_CYCLES[0] && bz >= LAB_LOG_CYCLES[1] && bx <= LAB_LOG_CYCLES[2] && bz <= LAB_LOG_CYCLES[3]) in = true;
                sb.append(String.format(" [%s (%d,%d)->(%d,%d) acc %d lvl %.2f%s]", m < e0 ? "own" : "theirs", e.fromX(), e.fromZ(),
                        e.cellX(), e.cellZ(), e.acc(), e.level(), e.channel() ? " ch" : ""));
            }
            if (!in) return;
            for (int side = 0; side < 2; side++) {
                final Plan p = side == 0 ? mine : other;
                final List<SeamExit> exits = side == 0 ? own : theirs;
                sb.append(side == 0 ? "\n   mine: " : "\n   theirs: ");
                if (p == null) {
                    sb.append("none");
                    continue;
                }
                sb.append(String.format("cost %.1f, %d cells turned from (%d,%d)", p.cost(), p.cells().length,
                        cx0 + (p.cells()[p.cells().length - 1] & W_MASK), cz0 + (p.cells()[p.cells().length - 1] >> W_SHIFT)));
                for (int k = 0; k < p.leaveBy().length; k++) {
                    final int out = p.leaveBy()[k];
                    if (out < 0) {
                        sb.append("; stays");
                        continue;
                    }
                    final SeamExit e = exits.get(out);
                    sb.append(String.format("; leaves (%d,%d)->(%d,%d) acc %d lvl %.2f%s", e.fromX(), e.fromZ(), e.cellX(), e.cellZ(),
                            e.acc(), e.level(), e.channel() ? " ch" : ""));
                }
            }
            sb.append('\n');
            appendLabLog(sb);
        }

        private static int countInto(int[] cycleOf, boolean[] cyclic, int ci) {
            int k = 0;
            for (int m = 0; m < cycleOf.length; m++) if (cyclic[m] && cycleOf[m] == ci) k++;
            return k;
        }

        private void labLog(String line, int wx, int wz) {
            final int bx = wx * CELL, bz = wz * CELL;
            if (bx >= LAB_LOG_CYCLES[0] && bz >= LAB_LOG_CYCLES[1] && bx <= LAB_LOG_CYCLES[2] && bz <= LAB_LOG_CYCLES[3]) appendLabLog(line);
        }

        private static void appendLabLog(CharSequence text) {
            synchronized (BasinTile.class) {
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of("dynamicwaters-cycles.log"), text,
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (java.io.IOException ignored) {
                }
            }
        }

        private record Plan(float cost, int[] cells, byte[] dirs, int[] crossings, int[] leaveBy, int[] cellLeave) {
        }

        private static final class CycleScratch {
            final float[] cost = new float[N];
            final int[] prev = new int[N];
            final int[] steps = new int[N];
            final boolean[] seen = new boolean[N];
        }

        private Plan planCycle(int n, int[] cycle, boolean mine, int e0, boolean[] pair, boolean[] cyclic, float level,
                CycleScratch scratch) {
            final List<SeamExit> exits = mine ? ownSeams.exits() : around[n].exits();
            final int base = mine ? 0 : e0;
            final int tx = mine ? tile.tileX : tile.tileX + SEAM_NEIGHBOURS[n][0];
            final int tz = mine ? tile.tileZ : tile.tileZ + SEAM_NEIGHBOURS[n][1];
            int count = 0;
            for (int m : cycle) if ((m >= e0) != mine) count++;
            final int[] crossings = new int[count];
            count = 0;
            for (int m : cycle) if ((m >= e0) != mine) crossings[count++] = m - base;

            final Integer[] sorted = new Integer[count];
            for (int k = 0; k < count; k++) sorted[k] = crossings[k];
            Arrays.sort(sorted, (a, b) -> {
                final SeamExit ea = exits.get(a), eb = exits.get(b);
                return ea.fromZ() != eb.fromZ() ? Integer.compare(ea.fromZ(), eb.fromZ()) : Integer.compare(ea.fromX(), eb.fromX());
            });
            final int[] leaveBy = new int[count];

            int water = 0;
            long place = Long.MAX_VALUE;
            for (int m : cycle) {
                final SeamExit crossing = m < e0 ? ownSeams.exits().get(m) : around[n].exits().get(m - e0);
                water = Math.max(water, crossing.acc());
                place = Math.min(place, worldPlace(crossing));
            }
            boolean refused = false;
            final java.util.HashMap<Integer, Integer> laid = new java.util.HashMap<>();
            final IntArrayLike cells = new IntArrayLike(), cellLeave = new IntArrayLike();
            final java.io.ByteArrayOutputStream dirs = new java.io.ByteArrayOutputStream();
            float total = 0f;
            final float[] cost = scratch.cost;
            final int[] prev = scratch.prev;
            final int[] steps = scratch.steps;
            final boolean[] seen = scratch.seen;
            for (int k = 0; k < count; k++) {
                final int x = sorted[k];
                crossings[k] = x;
                final SeamExit start = exits.get(x);
                final int s = windowCell(start.fromX(), start.fromZ());
                if (s < 0) return null;
                Arrays.fill(seen, false);
                final java.util.PriorityQueue<long[]> queue = new java.util.PriorityQueue<>(
                        (a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
                long order = 0;
                cost[s] = 0f;
                steps[s] = 0;
                prev[s] = -1;
                seen[s] = true;
                queue.add(new long[] { Float.floatToIntBits(0f), order++, s });
                int target = -1, targetExit = -1;
                while (!queue.isEmpty()) {
                    final long[] top = queue.poll();
                    final int c = (int) top[2];
                    if (Float.floatToIntBits(cost[c]) != top[0]) continue;
                    if (c != s) {
                        final Integer viaWay = laid.get(c);
                        final int e = viaWay != null ? viaWay : exitAt(mine, n, c);

                        final boolean low = e < 0 || exits.get(e).level() <= level + SEAM_CYCLE_RISE;
                        boolean out = viaWay != null || e < 0 || low && pair[base + e] && !cyclic[base + e];
                        if (!out && low && !pair[base + e] && Math.round(exits.get(e).level()) <= Math.round(level)) {
                            final SeamExit third = exits.get(e);
                            if (third.channel()) {
                                out = !comesRound(n, mine, e, water, place, e0, pair, cyclic, laid);
                                if (!out && !refused) {
                                    refused = true;
                                    WatershedStats.seamCycleRoundTrips.increment();
                                }
                            }
                        }
                        if (out) {
                            target = c;
                            targetExit = e;
                            break;
                        }
                    }
                    if (steps[c] >= SEAM_CYCLE_REACH) continue;
                    final int i = c & W_MASK, j = c >> W_SHIFT;
                    for (int d = 0; d < 8; d++) {
                        final int ni = i + DX[d], nj = j + DZ[d];
                        if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                        final int nc = (nj << W_SHIFT) | ni;
                        final int wx = cx0 + ni, wz = cz0 + nj;
                        if (!inTile(tx, tz, wx, wz) || seamDistance(n, wx, wz) > SEAM_CYCLE_BAND) continue;

                        if (steps[c] >= SEAM_CYCLE_INSET && seamDistance(n, wx, wz) < SEAM_CYCLE_INSET) continue;

                        if (h[nc] > level + SEAM_CYCLE_HEIGHT) continue;

                        final float step = DIST[d] * (1f + SEAM_CYCLE_CLIMB * Math.max(0f, h[nc] - h[s]))
                                * (1f + SEAM_CYCLE_WANDER * wanderPull[nc] * 0.5f * (1f - (float) Math.cos(DIR_ANGLE[d] - wanderWay[nc])));
                        final float to = cost[c] + step;
                        if (seen[nc] && to >= cost[nc]) continue;
                        seen[nc] = true;
                        cost[nc] = to;
                        prev[nc] = c;
                        steps[nc] = steps[c] + 1;
                        queue.add(new long[] { Float.floatToIntBits(to), order++, nc });
                    }
                }
                if (target < 0) return null;
                total += cost[target];
                leaveBy[k] = targetExit;
                for (int c = prev[target], next = target; c >= 0; next = c, c = prev[c]) {
                    final int di = (next & W_MASK) - (c & W_MASK), dj = (next >> W_SHIFT) - (c >> W_SHIFT);
                    byte d = -1;
                    for (int q = 0; q < 8; q++) if (DX[q] == di && DZ[q] == dj) d = (byte) q;
                    cells.add(c);
                    dirs.write(d);
                    cellLeave.add(targetExit);
                    laid.put(c, targetExit);
                }
            }
            return new Plan(total, cells.toArray(), dirs.toByteArray(), crossings, leaveBy, cellLeave.toArray());
        }

        private static final class IntArrayLike {
            private int[] a = new int[16];
            private int size;

            void add(int v) {
                if (size == a.length) a = Arrays.copyOf(a, size * 2);
                a[size++] = v;
            }

            int[] toArray() {
                return Arrays.copyOf(a, size);
            }
        }

        private int exitAt(boolean mine, int n, int c) {
            final int wx = cx0 + (c & W_MASK), wz = cz0 + (c >> W_SHIFT);
            return mine ? ownSeams.exitOf()[coreIndex(c)] : exitOfIn(n, wx, wz);
        }

        private boolean comesRound(int n, boolean mine, int out, int water, long place, int e0, boolean[] pair,
                boolean[] cyclic, java.util.Map<Integer, Integer> laid) {
            final int ntx = tile.tileX + SEAM_NEIGHBOURS[n][0], ntz = tile.tileZ + SEAM_NEIGHBOURS[n][1];
            TileSeams from = mine ? ownSeams : around[n];
            int ftx = mine ? tile.tileX : ntx, ftz = mine ? tile.tileZ : ntz;
            SeamExit e = from.exits().get(out);
            final int btx = Math.floorDiv(e.cellX(), CORE), btz = Math.floorDiv(e.cellZ(), CORE);
            final int x0 = Math.min(Math.min(tile.tileX, ntx), btx), x1 = Math.max(Math.max(tile.tileX, ntx), btx);
            final int z0 = Math.min(Math.min(tile.tileZ, ntz), btz), z1 = Math.max(Math.max(tile.tileZ, ntz), btz);

            final int[] byTx = new int[SEAM_CORNER_HOPS + 1], byTz = new int[SEAM_CORNER_HOPS + 1], byX = new int[SEAM_CORNER_HOPS + 1];
            final SeamExit[] byExit = new SeamExit[SEAM_CORNER_HOPS + 1];
            byTx[0] = ftx;
            byTz[0] = ftz;
            byX[0] = out;
            byExit[0] = e;
            int by = 1;
            for (int hop = 0; hop < SEAM_CORNER_HOPS; hop++) {
                final int ttx = Math.floorDiv(e.cellX(), CORE), ttz = Math.floorDiv(e.cellZ(), CORE);
                if (ttx < x0 || ttx > x1 || ttz < z0 || ttz > z1) return false;
                final int side = sideTo(ttx - ftx, ttz - ftz);
                if (side < 0 || !from.taken()[side]) return false;
                final TileSeams to = seamsAt(ttx, ttz);
                final int x = to.exitOf()[(e.cellZ() - ttz * CORE) * CORE + (e.cellX() - ttx * CORE)];
                if (x < 0) return false;
                final boolean here = ttx == tile.tileX && ttz == tile.tileZ, there = ttx == ntx && ttz == ntz;
                if (mine ? here : there) {
                    final int c = windowCell(e.cellX(), e.cellZ());
                    if (c >= 0 && laid.containsKey(c)) return false;
                }
                final int m = here ? x : there ? e0 + x : -1;
                if (m >= 0 && pair[m] && cyclic[m]) return true;
                for (int k = 0; k < by; k++) {
                    if (byTx[k] != ttx || byTz[k] != ttz || byX[k] != x) continue;

                    int loopWater = 0;
                    long loopPlace = Long.MAX_VALUE;
                    boolean two = true;
                    for (int q = k; q < by; q++) {
                        loopWater = Math.max(loopWater, byExit[q].acc());
                        loopPlace = Math.min(loopPlace, worldPlace(byExit[q]));
                        if ((byTx[q] != byTx[k] || byTz[q] != byTz[k]) && (byTx[q] != byTx[k + 1] || byTz[q] != byTz[k + 1])) two = false;
                    }
                    return !two || loopWater < water || loopWater == water && loopPlace > place;
                }
                from = to;
                ftx = ttx;
                ftz = ttz;
                e = to.exits().get(x);
                byTx[by] = ttx;
                byTz[by] = ttz;
                byX[by] = x;
                byExit[by++] = e;
            }
            return false;
        }

        private static long worldPlace(SeamExit e) {
            return ((long) e.fromZ() << 32) | (e.fromX() & 0xFFFFFFFFL);
        }

        private TileSeams seamsAt(int tx, int tz) {
            if (tx == tile.tileX && tz == tile.tileZ) return ownSeams;
            final int n = sideTo(tx - tile.tileX, tz - tile.tileZ);
            if (n >= 0 && around[n] != null) return around[n];
            return farSeams.computeIfAbsent(tileKey(tx, tz),
                    k -> seamsOf(tx, tz, generator, randomState, seed, seaLevel, thresholdSetting, lakesEnabled));
        }

        private static int sideTo(int dx, int dz) {
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (SEAM_NEIGHBOURS[n][0] == dx && SEAM_NEIGHBOURS[n][1] == dz) return n;
            }
            return -1;
        }

        private short ownExitAt(int wx, int wz) {
            return ownSeams.exitOf()[coreIndex(((wz - cz0) << W_SHIFT) | (wx - cx0))];
        }

        private int windowCell(int wx, int wz) {
            final int i = wx - cx0, j = wz - cz0;
            return i < 0 || j < 0 || i >= W || j >= W ? -1 : (j << W_SHIFT) | i;
        }

        private static boolean inTile(int tx, int tz, int wx, int wz) {
            return Math.floorDiv(wx, CORE) == tx && Math.floorDiv(wz, CORE) == tz;
        }

        private int seamDistance(int n, int wx, int wz) {
            final int dx = SEAM_NEIGHBOURS[n][0], dz = SEAM_NEIGHBOURS[n][1];
            if (dx != 0) {
                final int sx = (dx > 0 ? tile.tileX + 1 : tile.tileX) * CORE;
                return wx >= sx ? wx - sx : sx - 1 - wx;
            }
            final int sz = (dz > 0 ? tile.tileZ + 1 : tile.tileZ) * CORE;
            return wz >= sz ? wz - sz : sz - 1 - wz;
        }

        private void reorder() {
            final int[] head = new int[N], next = new int[N], out = new int[N];
            Arrays.fill(head, -1);
            int count = 0;
            for (int c = 0; c < N; c++) {
                final int d = downstream(c);
                if (d < 0) {
                    out[count++] = c;
                } else {
                    next[c] = head[d];
                    head[d] = c;
                }
            }
            for (int k = 0; k < count; k++) {
                for (int u = head[out[k]]; u >= 0; u = next[u]) out[count++] = u;
            }
            if (count != N) {
                if (!neighbour) WatershedStats.seamFlowLoops.increment();
                return;
            }
            System.arraycopy(out, 0, order, 0, N);
            orderCount = N;
        }

        private boolean seamFlat(int n) {
            final int dx = SEAM_NEIGHBOURS[n][0], dz = SEAM_NEIGHBOURS[n][1];
            final int seam = dx + dz > 0 ? HALO + CORE : HALO;
            for (int s = 0; s < CORE / SEAM_SEGMENT; s++) {
                final int along0 = HALO + s * SEAM_SEGMENT, along1 = along0 + SEAM_SEGMENT;
                final int i0 = dx != 0 ? seam - SEAM_BAND : along0, i1 = dx != 0 ? seam + SEAM_BAND : along1;
                final int j0 = dz != 0 ? seam - SEAM_BAND : along0, j1 = dz != 0 ? seam + SEAM_BAND : along1;
                double sum = 0.0;
                int pairs = 0;
                for (int j = j0; j < j1; j++) {
                    for (int i = i0; i < i1; i++) {
                        final int c = (j << W_SHIFT) | i;
                        if (i + 1 < i1) {
                            sum += Math.abs(h[c] - h[c + 1]);
                            pairs++;
                        }
                        if (j + 1 < j1) {
                            sum += Math.abs(h[c] - h[c + W]);
                            pairs++;
                        }
                    }
                }
                if (sum < SEAM_FLAT_RELIEF * pairs) return true;
            }
            return false;
        }

        private boolean seamPooled(int n) {
            final int dx = SEAM_NEIGHBOURS[n][0], dz = SEAM_NEIGHBOURS[n][1];
            final int seam = dx + dz > 0 ? HALO + CORE : HALO;
            final int i0 = dx != 0 ? seam - SEAM_POOL_SHARED : 0, j0 = dz != 0 ? seam - SEAM_POOL_SHARED : 0;
            final int sw = dx != 0 ? 2 * SEAM_POOL_SHARED : W, sh = dz != 0 ? 2 * SEAM_POOL_SHARED : W;
            final int sn = sw * sh;
            final float[] fill = new float[sn];
            final boolean[] seen = new boolean[sn];
            final int[] popped = new int[sn];
            int count = 0;
            heapSize = 0;
            for (int lj = 0; lj < sh; lj++) {
                for (int li = 0; li < sw; li++) {
                    final int l = lj * sw + li, c = ((j0 + lj) << W_SHIFT) | (i0 + li);
                    if (li == 0 || lj == 0 || li == sw - 1 || lj == sh - 1 || sink[c]) {
                        fill[l] = mainRiver[c] ? Math.min(h[c], seaLevel - 1) : h[c];
                        seen[l] = true;
                        push(heapKeyFor(fill[l], l));
                    }
                }
            }
            while (heapSize > 0) {
                final int l = (int) (pop() & CELL_KEY_MASK);
                popped[count++] = l;
                final int li = l % sw, lj = l / sw;
                final float base = fill[l] + EPS;
                for (int k = 0; k < 8; k++) {
                    final int ni = li + DX[k], nj = lj + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= sw || nj >= sh) continue;
                    final int nl = nj * sw + ni;
                    if (seen[nl]) continue;
                    seen[nl] = true;
                    fill[nl] = Math.max(h[((j0 + nj) << W_SHIFT) | (i0 + ni)], base);
                    push(heapKeyFor(fill[nl], nl));
                }
            }

            final boolean[] across = new boolean[sn];
            final int[] to = new int[sn];
            Arrays.fill(to, -1);
            for (int s = 0; s < count; s++) {
                final int l = popped[s];
                final int li = l % sw, lj = l / sw;
                if (li == 0 || lj == 0 || li == sw - 1 || lj == sh - 1) {
                    across[l] = dx != 0 ? li == 0 || li == sw - 1 : lj == 0 || lj == sh - 1;
                    continue;
                }
                if (sink[((j0 + lj) << W_SHIFT) | (i0 + li)]) continue;
                int best = -1;
                float bestSlope = 0f;
                for (int k = 0; k < 8; k++) {
                    final float slope = (fill[l] - fill[(lj + DZ[k]) * sw + li + DX[k]]) / DIST[k];
                    if (slope > bestSlope) {
                        bestSlope = slope;
                        best = k;
                    }
                }
                if (best >= 0) {
                    to[l] = (lj + DZ[best]) * sw + li + DX[best];
                    across[l] = across[to[l]];
                }
            }

            final int[] drains = new int[sn];
            for (int s = count - 1; s >= 0; s--) {
                final int l = popped[s];
                drains[l]++;
                if (to[l] >= 0) drains[to[l]] += drains[l];
            }

            for (int lj = 0; lj < sh; lj++) {
                for (int li = 0; li < sw; li++) {
                    final int off = (dx != 0 ? li : lj) - SEAM_POOL_SHARED;
                    if (off < -SEAM_POOL_BAND || off >= SEAM_POOL_BAND) continue;
                    final int l = lj * sw + li;
                    if (across[l] && drains[l] >= threshold
                            && fill[l] - h[((j0 + lj) << W_SHIFT) | (i0 + li)] >= SEAM_POOL_FILL) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void markTakenOver(int n) {
            final int dx = SEAM_NEIGHBOURS[n][0], dz = SEAM_NEIGHBOURS[n][1];
            final int i0 = dx > 0 ? HALO + CORE : dx < 0 ? 0 : HALO, i1 = dx > 0 ? W : dx < 0 ? HALO : HALO + CORE;
            final int j0 = dz > 0 ? HALO + CORE : dz < 0 ? 0 : HALO, j1 = dz > 0 ? W : dz < 0 ? HALO : HALO + CORE;
            for (int j = j0; j < j1; j++) {
                for (int i = i0; i < i1; i++) {
                    takenOver[(j << W_SHIFT) | i] = true;
                }
            }
        }

        private int neighbourOf(int wx, int wz) {
            final int tx = Math.floorDiv(wx, CORE), tz = Math.floorDiv(wz, CORE);
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (tile.tileX + SEAM_NEIGHBOURS[n][0] == tx && tile.tileZ + SEAM_NEIGHBOURS[n][1] == tz) return n;
            }
            return -1;
        }

        private short exitOfIn(int n, int wx, int wz) {
            final int tx = tile.tileX + SEAM_NEIGHBOURS[n][0], tz = tile.tileZ + SEAM_NEIGHBOURS[n][1];
            return around[n].exitOf()[(wz - tz * CORE) * CORE + (wx - tx * CORE)];
        }

        private int leavesBy(int c) {
            for (int steps = 0; steps < N && c >= 0; steps++) {
                if (sink[c]) return -1;
                final int d = downstream(c);
                if (d < 0) return -1;
                if (!inCore(d)) return c;
                c = d;
            }
            return -1;
        }

        boolean bounce() {
            boolean added = false;
            for (int j = HALO; j < HALO + CORE; j++) {
                final boolean rimRow = j == HALO || j == HALO + CORE - 1;
                for (int i = HALO; i < HALO + CORE; i++) {
                    if (!rimRow && i != HALO && i != HALO + CORE - 1) continue;
                    final int c = (j << W_SHIFT) | i;
                    if (!chan[c] || dropped[c]) continue;
                    final int d = downstream(c);
                    if (d < 0 || inCore(d) || sink[d] || lakeId[d] >= 0) continue;

                    final int wx = cx0 + (d & W_MASK), wz = cz0 + (d >> W_SHIFT);
                    final int n = neighbourOf(wx, wz);
                    if (n < 0 || around[n] == null) continue;
                    short exit = exitOfIn(n, wx, wz);

                    if (exit >= 0 && withdrawn[n][exit]) exit = wayOut[n][exit];
                    if (exit < 0) continue;
                    final SeamExit e = around[n].exits().get(exit);
                    final int bi = e.cellX() - cx0, bj = e.cellZ() - cz0;
                    if (bi < HALO || bj < HALO || bi >= HALO + CORE || bj >= HALO + CORE) continue;
                    final int back = (bj << W_SHIFT) | bi;
                    if (lakeId[back] >= 0 || sink[back]) continue;

                    if (leavesBy(back) == c) continue;

                    final short told = ownSeams.exitOf()[coreIndex(c)];
                    final float level = Math.min(Math.min(breached[c], e.level()),
                            told >= 0 ? ownSeams.exits().get(told).level() : Float.POSITIVE_INFINITY);
                    if (level < seamLevel[back]) {
                        seamLevel[back] = level;
                        added = true;
                    }

                    if (inflow[back]) continue;
                    inflow[back] = true;
                    inAcc[back] += acc[c];
                    inWet[back] += wet[c];
                    inLen[back] = Math.max(inLen[back], flowLen[c] + 2);
                    added = true;
                }
            }
            return added;
        }

        void accumulateCore() {
            final int[] acc2 = new int[N];
            final int[] wet2 = new int[N];
            final int[] len2 = new int[N];
            for (int c = 0; c < N; c++) {
                if (!inCore(c)) continue;
                acc2[c] = 1 + inAcc[c];
                wet2[c] = rain[c] + inWet[c];
                len2[c] = Math.max(1, inLen[c]);
            }
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                final int d = downstream(c);
                if (d < 0 || !inCore(d)) continue;
                if (inCore(c)) {
                    acc2[d] += acc2[c];
                    wet2[d] += wet2[c];
                    if (len2[c] + 1 > len2[d]) len2[d] = len2[c] + 1;
                } else if (!takenOver[c]) {
                    acc2[d] += acc[c];
                    wet2[d] += wet[c];
                    if (flowLen[c] + 1 > len2[d]) len2[d] = flowLen[c] + 1;
                }
            }
            for (int c = 0; c < N; c++) {
                if (!inCore(c)) continue;
                acc[c] = acc2[c];
                wet[c] = wet2[c];
                flowLen[c] = len2[c];
            }
        }

        void widenAsTold() {
            final List<SeamExit> own = ownSeams.exits();
            final short[] exitOf = ownSeams.exitOf();
            for (int n = 0; n < SEAM_NEIGHBOURS.length; n++) {
                if (around[n] == null) continue;
                final int ntx = tile.tileX + SEAM_NEIGHBOURS[n][0], ntz = tile.tileZ + SEAM_NEIGHBOURS[n][1];
                for (int x = 0; x < own.size(); x++) {
                    final SeamExit e = own.get(x);
                    if (!e.channel() || !inTile(ntx, ntz, e.cellX(), e.cellZ())) continue;
                    final int f = windowCell(e.fromX(), e.fromZ());
                    if (f < 0 || dropped[f] || !chan[f] || downstream(f) != windowCell(e.cellX(), e.cellZ())) continue;
                    for (int c = f; c >= 0 && inCore(c) && chan[c]; c = mainUp(c)) {
                        final int ci = coreIndex(c);
                        if (exitOf[ci] != x || wetAlone[ci] <= wet[c]) break;
                        wet[c] = wetAlone[ci];
                        WatershedStats.seamWidenedAsTold.increment();
                    }
                }
            }
        }

        void carryWidths() {
            for (int c = 0; c < N; c++) {
                final int d = chan[c] && inCore(c) ? downstream(c) : -1;
                if (d >= 0 && !inCore(d) && chan[d]) wet[d] = Integer.MIN_VALUE;
            }
            for (int c = 0; c < N; c++) {
                final int d = chan[c] && inCore(c) ? downstream(c) : -1;
                if (d >= 0 && !inCore(d) && chan[d]) wet[d] = Math.max(wet[d], wet[c]);
            }
        }

        void despike() {
            final float[] src = h.clone();
            final float[] nb = new float[8];
            for (int c = 0; c < N; c++) {
                final int i = c & W_MASK, j = c >> W_SHIFT;
                int n = 0;
                for (int k = 0; k < 8; k++) {
                    final int ni = i + DX[k], nj = j + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    nb[n++] = src[(nj << W_SHIFT) | ni];
                }
                if (n < 5) continue;
                Arrays.sort(nb, 0, n);
                final float median = (n & 1) == 1 ? nb[n / 2] : 0.5f * (nb[n / 2 - 1] + nb[n / 2]);
                if (src[c] < median - CAVE_SPIKE) {
                    if (src[c] > median - CAVE_DEEP && inTrough(src, i, j, median - TROUGH_MARGIN)) continue;
                    h[c] = median;
                    WatershedStats.caveSpikesFilled.increment();
                }
            }
        }

        private static boolean inTrough(float[] src, int i, int j, float below) {
            for (int k = 0; k < 4; k++) {
                final int ai = i + DX[k], aj = j + DZ[k];
                final int bi = i + DX[k + 4], bj = j + DZ[k + 4];
                if (ai < 0 || aj < 0 || ai >= W || aj >= W || bi < 0 || bj < 0 || bi >= W || bj >= W) continue;
                if (src[(aj << W_SHIFT) | ai] < below && src[(bj << W_SHIFT) | bi] < below) return true;
            }
            return false;
        }

        void densityField() {
            final int g = DENSITY_GRID;
            final int gi0 = Math.floorDiv(cx0, g), gj0 = Math.floorDiv(cz0, g);
            final int gw = Math.floorDiv(cx0 + W - 1, g) - gi0 + 2;
            final int gh = Math.floorDiv(cz0 + W - 1, g) - gj0 + 2;
            final double[] dry = new double[gw * gh];
            final net.minecraft.world.level.levelgen.densityfunction.DensitySampler humidity = randomState.getSampler(WiredRouter.of(randomState).vegetation());
            final SimplexNoise noise = densityNoise(seed);
            for (int b = 0; b < gh; b++) {
                for (int a = 0; a < gw; a++) {
                    dry[b * gw + a] = plainDryness(humidity, noise, ((gi0 + a) * g) << CS, ((gj0 + b) * g) << CS, seaLevel);
                }
            }
            for (int c = 0; c < N; c++) {
                final int wx = cx0 + (c & W_MASK), wz = cz0 + (c >> W_SHIFT);
                final int a = Math.floorDiv(wx, g) - gi0, b = Math.floorDiv(wz, g) - gj0;
                final double tx = (double) Math.floorMod(wx, g) / g, tz = (double) Math.floorMod(wz, g) / g;
                final int k = b * gw + a;
                final double d = turned(Mth.lerp(tz, Mth.lerp(tx, dry[k], dry[k + 1]), Mth.lerp(tx, dry[k + gw], dry[k + gw + 1])));
                dryness[c] = (float) d;
                rain[c] = (int) Math.round(RAIN_UNITS * Math.pow(DENSITY_WET / DENSITY_DRY, d));
            }
        }

        private int lakeMinCells(int c, boolean fed) {
            final double k = DENSITY_LAKE_WET * Math.pow(DENSITY_LAKE_DRY / DENSITY_LAKE_WET, dryness[c]);
            return (int) Math.ceil((fed ? LAKE_MIN_CELLS_FED : LAKE_MIN_CELLS) * k);
        }

        void wanderField() {
            for (int c = 0; c < N; c++) {
                final double x = (cx0 + (c & W_MASK)) / ROUTE_WANDER_CELLS, z = (cz0 + (c >> W_SHIFT)) / ROUTE_WANDER_CELLS;
                final double vx = valueNoise(x, z, 5) + 0.4 * valueNoise(x * 2.3, z * 2.3, 7);
                final double vz = valueNoise(x, z, 6) + 0.4 * valueNoise(x * 2.3, z * 2.3, 8);
                wanderWay[c] = (float) Math.atan2(vz, vx);
                wanderPull[c] = (float) Math.min(1.0, 2.0 * Math.hypot(vx, vz));
            }
        }

        void routeNoise() {
            for (int c = 0; c < N; c++) {
                final long wx = cx0 + (c & W_MASK), wz = cz0 + (c >> W_SHIFT);
                long hsh = seed ^ (wx * 0x9E3779B97F4A7C15L) ^ (wz * 0xC2B2AE3D27D4EB4FL);
                hsh ^= hsh >>> 33;
                hsh *= 0xFF51AFD7ED558CCDL;
                hsh ^= hsh >>> 33;
                h[c] += ((hsh >>> 11) * 0x1.0p-53f - 0.5f) * 2.0f * ROUTE_NOISE;
            }
        }

        void stampSinks() {
            for (int c = 0; c < N; c++) {
                if (h[c] >= seaLevel) continue;
                final int x = (cx0 + (c & W_MASK)) << CS, z = (cz0 + (c >> W_SHIFT)) << CS;
                if (HydrologyManager.isMouthBiomeAt(generator, x, z, randomState)) sink[c] = true;
            }
            if (!Config.dynamicNormalRivers()) return;

            final double canyonHeight = Config.NORMAL_RIVER_CANYON_HEIGHT.get();

            final boolean tunnelIsSink = Config.MOUNTAIN_RIVER_TUNNELS.get() || !Config.NORMAL_RIVER_TUNNELS.get();
            final int bx0 = cx0 << CS, bz0 = cz0 << CS;
            final int span = W << CS;
            final int margin = 512;
            final int r0x = Math.floorDiv(bx0 - margin, MainRivers.REGION_SIZE);
            final int r1x = Math.floorDiv(bx0 + span + margin, MainRivers.REGION_SIZE);
            final int r0z = Math.floorDiv(bz0 - margin, MainRivers.REGION_SIZE);
            final int r1z = Math.floorDiv(bz0 + span + margin, MainRivers.REGION_SIZE);
            for (int rz = r0z; rz <= r1z; rz++) {
                for (int rx = r0x; rx <= r1x; rx++) {
                    MainRivers.getRegion(rx, rz, generator, HydrologyManager.getDummyAccessor(), randomState);
                }
            }

            final boolean corridor = !Config.NORMAL_RIVER_TUNNELS.get();
            final double reachOut = corridor ? HydrologyManager.LOWLAND_CORRIDOR_REACH : 0.0;
            final double[] onRiver = new double[2];

            ground0 = corridor ? h.clone() : null;
            final int segCell = com.AdamNew.dynamicwaters.river.RiverPartition.CELL_SIZE;
            final int cellsPerSeg = segCell >> CS;
            for (int sz = bz0; sz < bz0 + span; sz += segCell) {
                for (int sx = bx0; sx < bx0 + span; sx += segCell) {
                    List<RiverGraphEdge> edges = MainRivers.getNearbyRivers(sx + segCell / 2.0, sz + segCell / 2.0);
                    if (edges == null || edges.isEmpty()) continue;
                    final int ne = edges.size();
                    final double[] bb = new double[ne * 4];
                    final double[] radSq = new double[ne];
                    for (int e = 0; e < ne; e++) {
                        RiverGraphEdge edge = edges.get(e);
                        double[] s = edge.fractal().segments;
                        double mnx = Double.MAX_VALUE, mxx = -Double.MAX_VALUE, mnz = Double.MAX_VALUE, mxz = -Double.MAX_VALUE;
                        for (int k = 0; k < s.length; k += 2) {
                            if (s[k] < mnx) mnx = s[k];
                            if (s[k] > mxx) mxx = s[k];
                            if (s[k + 1] < mnz) mnz = s[k + 1];
                            if (s[k + 1] > mxz) mxz = s[k + 1];
                        }

                        double r = edge.maxWaterRadius() + 2.0 + reachOut;
                        bb[e * 4] = mnx - r;
                        bb[e * 4 + 1] = mxx + r;
                        bb[e * 4 + 2] = mnz - r;
                        bb[e * 4 + 3] = mxz + r;
                        radSq[e] = r * r;
                    }
                    final int i0 = (sx - bx0) >> CS, j0 = (sz - bz0) >> CS;
                    for (int j = j0; j < j0 + cellsPerSeg; j++) {
                        final double z = ((cz0 + j) << CS) + CELL * 0.5;
                        for (int i = i0; i < i0 + cellsPerSeg; i++) {
                            final int c = (j << W_SHIFT) | i;
                            if (h[c] >= canyonHeight && !tunnelIsSink) continue;
                            final double x = ((cx0 + i) << CS) + CELL * 0.5;

                            double bestOut = Double.MAX_VALUE, bestR = 0.0, bestX = 0.0, bestZ = 0.0;
                            boolean bestDelta = false;
                            for (int e = 0; e < ne; e++) {
                                if (x < bb[e * 4] || x > bb[e * 4 + 1] || z < bb[e * 4 + 2] || z > bb[e * 4 + 3]) continue;
                                final RiverGraphEdge edge = edges.get(e);
                                final double dSq = corridor ? edge.fractal().nearestPoint(x, z, onRiver)
                                        : edge.fractal().intersectDistance(x, z);
                                if (dSq > radSq[e]) continue;

                                final double waterR = HydrologyManager.routeWaterRadius(edge, x, z);
                                final double rc = waterR + 2.0;
                                if (dSq <= rc * rc) {
                                    mainRiver[c] = true;
                                    sink[c] = true;
                                    if (corridor) h[c] = Math.min(h[c], seaLevel);
                                    break;
                                }

                                if (sink[c] && !mainRiver[c] && HydrologyManager.inHeadTaper(edge, x, z)) {
                                    final double whole = HydrologyManager.waterRadiusAt(edge, x, z) + 2.0;
                                    if (dSq <= whole * whole) {
                                        mainRiver[c] = true;
                                        break;
                                    }
                                }
                                if (corridor) {
                                    final double out = Math.sqrt(dSq) - waterR;
                                    if (out < bestOut) {
                                        bestOut = out;
                                        bestR = waterR;
                                        bestX = onRiver[0];
                                        bestZ = onRiver[1];
                                        bestDelta = edge.delta();
                                    }
                                }
                            }
                            if (corridor && !sink[c] && bestOut < reachOut) {
                                final double riverGround = groundAt(ground0, bestX, bestZ);
                                final double ground = HydrologyManager.lowlandRouteGround(x, z, h[c], bestOut, bestR,
                                        riverGround, seaLevel, bestDelta);
                                double routed = ground;
                                if (HydrologyManager.gorgeShare(riverGround) >= 0.5) {
                                    cut[c] = HydrologyManager.lowlandRouteGround(x, z, h[c], bestOut - RIM_CUT_REACH, bestR,
                                            riverGround, seaLevel, bestDelta) < h[c] - RIM_CUT_DEPTH;

                                    if (cut[c]) routed = Math.min(routed, seaLevel + FALL_FLOOR_SLOPE * bestOut);
                                }
                                if (routed < h[c]) h[c] = (float) routed;
                            }
                        }
                    }
                }
            }
            nearRiverMask();
        }

        private double groundAt(float[] ground, double x, double z) {
            final double fx = x / CELL - cx0, fz = z / CELL - cz0;
            final int i = Mth.floor(fx), j = Mth.floor(fz);
            if (i < 0 || j < 0 || i >= W - 1 || j >= W - 1) {
                return CoarseHeightField.sample(Mth.floor(x / CELL), Mth.floor(z / CELL), generator, randomState);
            }
            final double tx = fx - i, tz = fz - j;
            final int c = (j << W_SHIFT) | i;
            return Mth.lerp(tz, Mth.lerp(tx, ground[c], ground[c + 1]), Mth.lerp(tx, ground[c + W], ground[c + W + 1]));
        }

        private void nearRiverMask() {
            final boolean[] rows = new boolean[N];
            for (int j = 0; j < W; j++) {
                int last = Integer.MIN_VALUE / 2;
                for (int i = 0; i < W; i++) {
                    if (mainRiver[(j << W_SHIFT) | i]) last = i;
                    if (i - last <= LAKE_RIVER_GAP) rows[(j << W_SHIFT) | i] = true;
                }
                last = Integer.MAX_VALUE / 2;
                for (int i = W - 1; i >= 0; i--) {
                    if (mainRiver[(j << W_SHIFT) | i]) last = i;
                    if (last - i <= LAKE_RIVER_GAP) rows[(j << W_SHIFT) | i] = true;
                }
            }
            for (int i = 0; i < W; i++) {
                int last = Integer.MIN_VALUE / 2;
                for (int j = 0; j < W; j++) {
                    if (rows[(j << W_SHIFT) | i]) last = j;
                    if (j - last <= LAKE_RIVER_GAP) nearRiver[(j << W_SHIFT) | i] = true;
                }
                last = Integer.MAX_VALUE / 2;
                for (int j = W - 1; j >= 0; j--) {
                    if (rows[(j << W_SHIFT) | i]) last = j;
                    if (last - j <= LAKE_RIVER_GAP) nearRiver[(j << W_SHIFT) | i] = true;
                }
            }
        }

        private long heapKeyFor(float value, int c) {
            int s = Float.floatToRawIntBits(value);
            s ^= (s >> 31) | 0x80000000;
            return (((long) s & 0xFFFFFFFFL) << CELL_KEY_BITS) | c;
        }

        private long heapKey(int c) {
            int s = Float.floatToRawIntBits(filled[c]);
            s ^= (s >> 31) | 0x80000000;
            return (((long) s & 0xFFFFFFFFL) << CELL_KEY_BITS) | c;
        }

        private void push(long key) {
            int i = heapSize++;
            while (i > 0) {
                int p = (i - 1) >> 1;
                if (heap[p] <= key) break;
                heap[i] = heap[p];
                i = p;
            }
            heap[i] = key;
        }

        private long pop() {
            long top = heap[0];
            long last = heap[--heapSize];
            if (heapSize > 0) {
                int i = 0;
                while (true) {
                    int l = 2 * i + 1;
                    if (l >= heapSize) break;
                    int r = l + 1;
                    int m = (r < heapSize && heap[r] < heap[l]) ? r : l;
                    if (heap[m] >= last) break;
                    heap[i] = heap[m];
                    i = m;
                }
                heap[i] = last;
            }
            return top;
        }

        void flood() {
            Arrays.fill(filled, Float.POSITIVE_INFINITY);
            Arrays.fill(pushed, false);
            heapSize = 0;
            for (int c = 0; c < N; c++) {
                int i = c & W_MASK, j = c >> W_SHIFT;
                boolean border = i == 0 || j == 0 || i == W - 1 || j == W - 1;

                final boolean closedWater = lakeId[c] >= 0 && closedLake(lakeId[c]);
                if (border || sink[c] || closedWater) {
                    filled[c] = closedWater ? lakeWaterY.get(lakeId[c]) : mainRiver[c] ? Math.min(h[c], seaLevel - 1) : h[c];
                    pushed[c] = true;
                    push(heapKey(c));
                }
            }
            orderCount = 0;
            while (heapSize > 0) {
                int c = (int) (pop() & CELL_KEY_MASK);
                order[orderCount++] = c;
                int i = c & W_MASK, j = c >> W_SHIFT;
                float base = filled[c] + EPS;
                for (int k = 0; k < 8; k++) {
                    int ni = i + DX[k], nj = j + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    int nc = (nj << W_SHIFT) | ni;
                    if (pushed[nc] || overCorner(i, j, k)) continue;
                    filled[nc] = Math.max(h[nc], base);
                    pushed[nc] = true;
                    push(heapKey(nc));
                }
            }
        }

        void computeD8() {
            for (int c = 0; c < N; c++) {
                if (sink[c]) {
                    dir[c] = -1;
                    continue;
                }
                int i = c & W_MASK, j = c >> W_SHIFT;
                float fc = filled[c];
                int best = -1;
                float bestSlope = 0f;
                for (int k = 0; k < 8; k++) {
                    int ni = i + DX[k], nj = j + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W || overCorner(i, j, k)) continue;
                    float slope = (fc - filled[(nj << W_SHIFT) | ni]) / DIST[k];
                    if (slope > bestSlope) {
                        bestSlope = slope;
                        best = k;
                    }
                }

                final double lean = best < 0 ? 0.0
                        : ROUTE_WANDER * wanderPull[c] * Mth.clamp(2.0 - bestSlope / ROUTE_WANDER_SLOPE, 0.0, 1.0);
                if (lean > 0.0) {
                    final double theta = wanderWay[c];
                    final int steepest = best;
                    final int tx = Math.floorDiv(cx0 + i, CORE), tz = Math.floorDiv(cz0 + j, CORE);
                    double bestScore = 0.0;
                    for (int k = 0; k < 8; k++) {
                        int ni = i + DX[k], nj = j + DZ[k];
                        if (ni < 0 || nj < 0 || ni >= W || nj >= W || overCorner(i, j, k)) continue;
                        final float slope = (fc - filled[(nj << W_SHIFT) | ni]) / DIST[k];
                        if (slope <= 0f) continue;

                        if (k != steepest && (Math.floorDiv(cx0 + ni, CORE) != tx || Math.floorDiv(cz0 + nj, CORE) != tz)) continue;
                        final double score = slope * Math.exp(lean * Math.cos(DIR_ANGLE[k] - theta));
                        if (score > bestScore) {
                            bestScore = score;
                            best = k;
                        }
                    }
                }
                dir[c] = (byte) best;
            }
        }

        private boolean overCorner(int i, int j, int k) {
            if (DX[k] == 0 || DZ[k] == 0) return false;
            final int mx = Math.floorMod(cx0 + i, CORE), mz = Math.floorMod(cz0 + j, CORE);
            return (DX[k] > 0 ? mx == CORE - 1 : mx == 0) && (DZ[k] > 0 ? mz == CORE - 1 : mz == 0);
        }

        private boolean overCorner(int p, int q) {
            return Math.floorDiv(cx0 + (p & W_MASK), CORE) != Math.floorDiv(cx0 + (q & W_MASK), CORE)
                    && Math.floorDiv(cz0 + (p >> W_SHIFT), CORE) != Math.floorDiv(cz0 + (q >> W_SHIFT), CORE);
        }

        private int downstream(int c) {
            int d = dir[c];
            if (d < 0) return -1;

            if (lakeId[c] >= 0 && closedLake(lakeId[c])) return -1;
            return ((c >> W_SHIFT) + DZ[d]) << W_SHIFT | ((c & W_MASK) + DX[d]);
        }

        private boolean closedLake(int a) {
            return a < lakeClosed.length && lakeClosed[a];
        }

        private boolean closeLakes() {
            final int n = lakeCells.size();
            if (lakeClosed.length < n) lakeClosed = Arrays.copyOf(lakeClosed, n);
            boolean any = closedChanged;
            closedChanged = false;
            for (int a = 0; a < n; a++) {
                final int[] cells = lakeCells.get(a);
                if (lakeClosed[a] || cells.length == 0) continue;

                float spill = Float.POSITIVE_INFINITY;
                for (int c : cells) spill = Math.min(spill, filled[c]);
                if (spill - lakeWaterY.get(a) > LAKE_CLOSED_RISE) {
                    lakeClosed[a] = true;
                    if (!neighbour) WatershedStats.lakesClosed.increment();
                    any = true;
                }
            }
            return any;
        }

        private void reroute() {
            flood();
            computeD8();
            accumulate();
            capture();
            fuse();
            fuseWide();
            joinAlongside();
        }

        void feedField() {
            final float far = 1.0e9f;
            final float diag = (float) Math.sqrt(2.0);
            final float[] dist = new float[N];
            boolean any = false;
            for (int c = 0; c < N; c++) {
                dist[c] = mainRiver[c] ? 0.0f : far;
                any |= mainRiver[c];
            }
            if (!any) {
                Arrays.fill(needRain, streamRain);
                Arrays.fill(needConvergence, CHANNEL_CONVERGENCE);
                return;
            }
            for (int j = 0; j < W; j++) {
                for (int i = 0; i < W; i++) {
                    final int c = (j << W_SHIFT) | i;
                    float d = dist[c];
                    if (i > 0) d = Math.min(d, dist[c - 1] + 1.0f);
                    if (j > 0) {
                        d = Math.min(d, dist[c - W] + 1.0f);
                        if (i > 0) d = Math.min(d, dist[c - W - 1] + diag);
                        if (i < W - 1) d = Math.min(d, dist[c - W + 1] + diag);
                    }
                    dist[c] = d;
                }
            }
            for (int j = W - 1; j >= 0; j--) {
                for (int i = W - 1; i >= 0; i--) {
                    final int c = (j << W_SHIFT) | i;
                    float d = dist[c];
                    if (i < W - 1) d = Math.min(d, dist[c + 1] + 1.0f);
                    if (j < W - 1) {
                        d = Math.min(d, dist[c + W] + 1.0f);
                        if (i < W - 1) d = Math.min(d, dist[c + W + 1] + diag);
                        if (i > 0) d = Math.min(d, dist[c + W - 1] + diag);
                    }
                    dist[c] = d;
                }
            }
            for (int c = 0; c < N; c++) {
                final double d = dist[c];
                final double feed = smoothstep((d - MAIN_FEED_FROM) / (MAIN_FEED_FULL - MAIN_FEED_FROM))
                        * (1.0 - smoothstep((d - MAIN_FEED_HOLD) / (MAIN_FEED_REACH - MAIN_FEED_HOLD)));

                final double dry = 1.0 - feed * (1.0 - (double) rain[c] / RAIN_UNITS);
                needRain[c] = (int) Math.round(streamRain * (1.0 - (1.0 - MAIN_FEED_SHARE) * feed) * dry);
                needConvergence[c] = (float) (CHANNEL_CONVERGENCE - (CHANNEL_CONVERGENCE - MAIN_FEED_CONVERGENCE) * feed);
            }
        }

        void accumulate() {
            Arrays.fill(acc, 1);
            Arrays.fill(flowLen, 1);
            System.arraycopy(rain, 0, wet, 0, N);
            for (int k = orderCount - 1; k >= 0; k--) {
                int c = order[k];
                int d = downstream(c);
                if (d >= 0) {
                    acc[d] += acc[c];
                    wet[d] += wet[c];
                    if (flowLen[c] + 1 > flowLen[d]) flowLen[d] = flowLen[c] + 1;
                }
            }
        }

        void capture() {
            boolean changed = false;
            for (int c = 0; c < N; c++) {
                if (sink[c] || wet[c] < needRain[c]) continue;
                final int d = downstream(c);
                if (d < 0 || acc[d] >= acc[c] * CAPTURE_RATIO) continue;
                final int i = c & W_MASK, j = c >> W_SHIFT;
                int best = -1;
                int bestAcc = acc[c] * CAPTURE_RATIO;
                for (int k = 0; k < 8; k++) {
                    final int ni = i + DX[k], nj = j + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W || overCorner(i, j, k)) continue;
                    final int nc = (nj << W_SHIFT) | ni;
                    if (nc == d) continue;
                    if (filled[nc] >= filled[c]) continue;
                    if (acc[nc] > bestAcc) {
                        bestAcc = acc[nc];
                        best = k;
                    }
                }
                if (best >= 0) {
                    dir[c] = (byte) best;
                    changed = true;
                }
            }
            if (changed) accumulate();
        }

        void fuse() {
            final int[] end = new int[N];
            for (int k = 0; k < orderCount; k++) {
                final int c = order[k];
                final int d = downstream(c);
                end[c] = d < 0 ? c : end[d];
            }

            final boolean[] stream = new boolean[N];
            final byte[] ins = new byte[N];
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                if (sink[c]) continue;
                if (!stream[c]) stream[c] = startsStream(c, 0, 0);
                final int d = downstream(c);
                if (stream[c] && d >= 0 && !sink[d]) {
                    stream[d] = true;
                    ins[d]++;
                }
            }
            boolean changed = false;
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                if (!stream[c]) continue;
                final int d = downstream(c);
                if (d < 0 || sink[d]) continue;
                final int i = c & W_MASK, j = c >> W_SHIFT;
                final int fi = (d & W_MASK) - i, fj = (d >> W_SHIFT) - j;
                int best = -1;
                float bestH = filled[c];
                for (int e = 0; e < 8; e++) {
                    final int ni = i + DX[e], nj = j + DZ[e];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W || overCorner(i, j, e)) continue;
                    final int nc = (nj << W_SHIFT) | ni;
                    if (nc == d || !stream[nc] || sink[nc] || end[nc] != end[c] || filled[nc] >= bestH) continue;
                    if (DX[e] * fi + DZ[e] * fj < 0) continue;
                    final int nd = downstream(nc);
                    if (nd < 0 || ((nd & W_MASK) - ni) * fi + ((nd >> W_SHIFT) - nj) * fj < 0) continue;
                    if (onCourse(d, nc, FUSE_OWN_CELLS)) continue;
                    best = e;
                    bestH = filled[nc];
                }
                int via = -1;
                if (best < 0) {
                    for (int b = -2; b <= 2; b++) {
                        for (int a = -2; a <= 2; a++) {
                            if (Math.max(Math.abs(a), Math.abs(b)) != 2) continue;
                            final int ni = i + a, nj = j + b;
                            if (ni < 0 || nj < 0 || ni >= W || nj >= W || a * fi + b * fj < 0) continue;
                            final int nc = (nj << W_SHIFT) | ni;
                            if (!stream[nc] || sink[nc] || end[nc] != end[c] || filled[nc] >= bestH) continue;
                            final int nd = downstream(nc);
                            if (nd < 0 || ((nd & W_MASK) - ni) * fi + ((nd >> W_SHIFT) - nj) * fj < 0) continue;
                            if (onCourse(d, nc, FUSE_OWN_CELLS)) continue;
                            for (int e = 0; e < 8; e++) {
                                final int mi = i + DX[e], mj = j + DZ[e];
                                if (Math.abs(ni - mi) > 1 || Math.abs(nj - mj) > 1 || DX[e] * fi + DZ[e] * fj < 0) continue;
                                final int mc = (mj << W_SHIFT) | mi;
                                if (stream[mc] || sink[mc] || filled[mc] >= filled[c] || filled[mc] <= filled[nc]) continue;
                                if (overCorner(c, mc) || overCorner(mc, nc)) continue;
                                best = e;
                                via = nc;
                                bestH = filled[nc];
                                break;
                            }
                        }
                    }
                }
                if (best < 0) continue;
                final int n = ((j + DZ[best]) << W_SHIFT) | (i + DX[best]);
                dir[c] = (byte) best;
                if (via >= 0) {
                    final int vi = (via & W_MASK) - (n & W_MASK), vj = (via >> W_SHIFT) - (n >> W_SHIFT);
                    for (int e = 0; e < 8; e++) {
                        if (DX[e] == vi && DZ[e] == vj) dir[n] = (byte) e;
                    }
                    stream[n] = true;
                    ins[via]++;
                }
                ins[n]++;
                changed = true;

                for (int e = d; e >= 0 && !sink[e] && stream[e]; e = downstream(e)) {
                    if (--ins[e] > 0 || startsStream(e, wet[c], acc[c])) break;
                    stream[e] = false;
                }
            }
            if (changed) accumulate();
        }

        void fuseWide() {
            final boolean fin = seamsImported;
            final int[] end = new int[N];

            final int[] out = fin ? new int[N] : null;
            for (int k = 0; k < orderCount; k++) {
                final int c = order[k];
                final int d = downstream(c);
                end[c] = d < 0 ? c : end[d];
                if (fin && inCore(c)) out[c] = d < 0 || sink[d] ? -1 : inCore(d) ? out[d] : c;
            }
            final boolean[] stream = new boolean[N];
            final byte[] ins = new byte[N];
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                if (sink[c]) continue;
                if (!stream[c]) stream[c] = startsStream(c, 0, 0) || fin && inflow[c];
                final int d = downstream(c);
                if (stream[c] && d >= 0 && !sink[d]) {
                    stream[d] = true;
                    ins[d]++;
                }
            }
            final float[] width = new float[N];
            for (int c = 0; c < N; c++) if (stream[c]) width[c] = (float) riverWidth((double) wet[c] / RAIN_UNITS);

            final int[] own = new int[N];
            final int[] path = new int[64];
            int stamp = 0;
            boolean changed = false;
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                if (!stream[c] || fin && !inCore(c) || standing(c)) continue;
                final int d = downstream(c);
                if (d < 0 || sink[d]) continue;
                final int i = c & W_MASK, j = c >> W_SHIFT;
                final int tx = Math.floorDiv(cx0 + i, CORE), tz = Math.floorDiv(cz0 + j, CORE);
                final int fi = (d & W_MASK) - i, fj = (d >> W_SHIFT) - j;
                final int reach = (int) Math.ceil((width[c] + MAX_WIDTH + FUSE_WIDE_GAP) / CELL);
                stamp++;
                own[c] = stamp;
                for (int s = 0, e = d; s < FUSE_OWN_CELLS && e >= 0; s++, e = downstream(e)) own[e] = stamp;
                int target = -1;
                double bestGap = FUSE_WIDE_GAP;
                for (int b = -reach; b <= reach; b++) {
                    final int nj = j + b;
                    if (nj < 0 || nj >= W) continue;
                    for (int a = -reach; a <= reach; a++) {
                        final int ni = i + a;
                        if (ni < 0 || ni >= W) continue;
                        final int t = (nj << W_SHIFT) | ni;
                        if (!stream[t] || own[t] == stamp || sink[t] || end[t] != end[c] || filled[t] >= filled[c]
                                || wet[t] < wet[c] || standing(t)) continue;
                        final double gap = Math.hypot(a, b) * CELL - width[c] - width[t];
                        if (gap >= bestGap) continue;
                        if (Math.floorDiv(cx0 + ni, CORE) != tx || Math.floorDiv(cz0 + nj, CORE) != tz) continue;
                        if (fin && out[t] != out[c]) continue;
                        final int td = downstream(t);
                        if (td < 0 || ((td & W_MASK) - ni) * fi + ((td >> W_SHIFT) - nj) * fj < 0) continue;
                        bestGap = gap;
                        target = t;
                    }
                }
                if (target < 0) continue;

                final int ti = target & W_MASK, tj = target >> W_SHIFT;
                int len = 0, cur = c;
                path[len++] = c;
                boolean reached = false;
                while (len < path.length) {
                    final int ci = cur & W_MASK, cj = cur >> W_SHIFT;
                    final double from = Math.hypot(ti - ci, tj - cj);
                    int next = -1;
                    double nearest = from;
                    for (int e = 0; e < 8; e++) {
                        final int ni = ci + DX[e], nj = cj + DZ[e];
                        if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                        final int nc = (nj << W_SHIFT) | ni;
                        if (sink[nc] || filled[nc] >= filled[cur] || standing(nc)) continue;
                        if (Math.floorDiv(cx0 + ni, CORE) != tx || Math.floorDiv(cz0 + nj, CORE) != tz) continue;
                        final double to = Math.hypot(ti - ni, tj - nj);
                        if (to < nearest) {
                            nearest = to;
                            next = nc;
                        }
                    }
                    if (next < 0 || own[next] == stamp) break;
                    path[len++] = next;
                    if (stream[next]) {
                        reached = end[next] == end[c] && (!fin || out[next] == out[c]);
                        break;
                    }
                    cur = next;
                }
                if (!reached) continue;
                if (fin) {
                    boolean loop = false;
                    for (int s = 0, e = path[len - 1]; s < N && e >= 0 && !loop; s++, e = downstream(e)) {
                        for (int q = 0; q < len - 1; q++) if (path[q] == e) loop = true;
                    }
                    if (loop) continue;
                }
                for (int s = 0; s < len - 1; s++) {
                    final int p = path[s], q = path[s + 1];
                    final int di = (q & W_MASK) - (p & W_MASK), dj = (q >> W_SHIFT) - (p >> W_SHIFT);
                    for (int e = 0; e < 8; e++) if (DX[e] == di && DZ[e] == dj) dir[p] = (byte) e;
                    if (s > 0) stream[p] = true;
                }
                ins[path[1]]++;
                for (int s = 2; s < len; s++) ins[path[s]]++;
                changed = true;
                if (!neighbour) WatershedStats.fusesWide.increment();

                for (int e = d; e >= 0 && !sink[e] && stream[e]; e = downstream(e)) {
                    if (--ins[e] > 0 || startsStream(e, wet[c], acc[c])) break;
                    stream[e] = false;
                }
            }
            if (!changed) return;
            if (fin) {
                reorder();
                accumulateCore();
            } else {
                accumulate();
            }
        }

        private boolean standing(int c) {
            return lakesFound && (lakeId[c] >= 0 || lakeShore[c] >= 0);
        }

        void joinAlongside() {
            final byte[] ins = new byte[N];
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                final int d = downstream(c);
                if (d >= 0 && !sink[c] && startsStreamOrFed(c, ins)) ins[d]++;
            }
            boolean changed = false;
            for (int k = orderCount - 1; k >= 0; k--) {
                final int c = order[k];
                if (sink[c] || ins[c] == 0 && !startsStream(c, 0, 0)) continue;
                final int i = c & W_MASK, j = c >> W_SHIFT;
                final int first = downstream(c);
                boolean confluence = false;
                int e = first;
                for (int s = 1; s <= JOIN_ALONGSIDE_CELLS && e >= 0 && !sink[e]; s++) {
                    if (ins[e] >= 2) confluence = true;
                    final int next = downstream(e);
                    if (next < 0) break;
                    if (confluence && Math.abs((next & W_MASK) - i) <= 1 && Math.abs((next >> W_SHIFT) - j) <= 1 && !overCorner(c, next)) {
                        for (int a = 0; a < 8; a++) {
                            if (i + DX[a] == (next & W_MASK) && j + DZ[a] == (next >> W_SHIFT)) dir[c] = (byte) a;
                        }

                        ins[first]--;
                        ins[next]++;
                        changed = true;
                        WatershedStats.joinsAlongside.increment();
                        break;
                    }
                    e = next;
                }
            }
            if (changed) accumulate();
        }

        private boolean startsStreamOrFed(int c, byte[] ins) {
            return ins[c] > 0 || startsStream(c, 0, 0);
        }

        private boolean startsStream(int c, int lessWet, int lessAcc) {
            return !cut[c] && wet[c] - lessWet >= needRain[c] && acc[c] - lessAcc >= needConvergence[c] * flowLen[c];
        }

        private boolean onCourse(int from, int target, int cells) {
            for (int s = 0, e = from; s <= cells && e >= 0; s++, e = downstream(e)) {
                if (e == target) return true;
            }
            return false;
        }

        void breach() {
            settle(null);
            if (seamsImported) {
                final float[] floor = holdExits();
                if (floor != null) settle(floor);
            }

            final boolean gorgeWalls = !Config.NORMAL_RIVER_TUNNELS.get();
            if (Config.MOUNTAIN_RIVER_TUNNELS.get() || gorgeWalls) {
                final boolean[] ramped = new boolean[N];
                for (int k = 0; k < orderCount; k++) {
                    final int c = order[k];
                    final int d = downstream(c);
                    if (d < 0 || lakeId[c] >= 0 || lakeShore[c] >= 0) continue;
                    if (!sink[d] && !ramped[d]) continue;
                    if (gorgeWalls && mainRiver[d]) continue;
                    final float limit = breached[d] + MOUTH_RAMP_PER_CELL;
                    if (breached[c] > limit) {
                        breached[c] = limit;
                        ramped[c] = true;
                    }
                }
            }
        }

        private void settle(float[] floor) {
            for (int c = 0; c < N; c++) {
                breached[c] = lakeId[c] >= 0
                        ? lakeWaterY.get(lakeId[c])

                        : sink[c] ? seaLevel - 1

                        : lakeShore[c] >= 0 && !closedLake(lakeShore[c]) ? lakeWaterY.get(lakeShore[c])

                        : wet[c] < needRain[c] && filled[c] - h[c] >= POND_MIN_DEPTH ? filled[c]
                        : Math.min(filled[c], h[c] + MAX_FILL);

                if (seamsImported && seamLevel[c] < breached[c] && lakeId[c] < 0 && lakeShore[c] < 0 && !sink[c]) {
                    breached[c] = seamLevel[c];
                }
                if (floor != null && breached[c] < floor[c]) breached[c] = floor[c];
            }

            for (int k = orderCount - 1; k >= 0; k--) {
                int c = order[k];
                int d = downstream(c);

                if (d < 0 || lakeId[d] >= 0 || lakeShore[d] >= 0 || sink[d]) continue;

                if (seamsImported && takenOver[c] && inCore(d)) continue;
                if (breached[c] < breached[d]) breached[d] = breached[c];
            }
        }

        private float[] holdExits() {
            boolean[] seen = null;
            int[] stack = null;
            float[] held = null;
            for (SeamExit e : ownSeams.exits()) {
                final int c = windowCell(e.fromX(), e.fromZ());
                if (c < 0 || dropped[c] || lakeId[c] >= 0) continue;
                final int d = downstream(c);
                if (d < 0 || inCore(d)) continue;
                final float floor = Math.round(e.level()) - 0.5f;
                if (breached[c] >= floor) continue;
                if (seen == null) {
                    seen = new boolean[N];
                    stack = new int[N];
                }
                int top = 0, count = 0;
                stack[top++] = c;
                seen[c] = true;
                boolean hold = true;

                while (count < top) {
                    final int u = stack[count++];
                    if (!inCore(u) || lakeId[u] >= 0 || lakeShore[u] >= 0 || sink[u] || inflow[u] && seamLevel[u] < floor
                            || floor > h[u] + MAX_FILL) {
                        hold = false;
                        break;
                    }
                    final int i = u & W_MASK, j = u >> W_SHIFT;
                    for (int k = 0; k < 8; k++) {
                        final int ni = i + DX[k], nj = j + DZ[k];
                        if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                        final int v = (nj << W_SHIFT) | ni;
                        if (seen[v] || breached[v] >= floor || downstream(v) != u) continue;

                        if (takenOver[v] && !inCore(v)) continue;
                        seen[v] = true;
                        stack[top++] = v;
                    }
                }
                if (hold) {
                    if (held == null) {
                        held = new float[N];
                        Arrays.fill(held, Float.NEGATIVE_INFINITY);
                    }
                    for (int k = 0; k < top; k++) held[stack[k]] = floor;
                    WatershedStats.seamExitsHeld.increment();
                }
                for (int k = 0; k < top; k++) seen[stack[k]] = false;
            }
            return held;
        }

        void lakeFloors() {
            Arrays.fill(lakeFloorAt, Integer.MIN_VALUE);
            for (int c = 0; c < N; c++) {
                if (lakeId[c] >= 0) continue;
                final int water = Math.round(breached[c]);
                int f = Integer.MIN_VALUE;
                if (!lakeWaterY.isEmpty()) {
                    f = lakeLevelBeside(c, cellWidth(c), water);
                }
                final int d = downstream(c);
                if ((sink[c] || d >= 0 && sink[d]) && water >= seaLevel - 1) f = Math.max(f, seaLevel - 1);
                lakeFloorAt[c] = f;
            }

            for (int k = 0; k < orderCount; k++) {
                final int c = order[k];
                final int d = downstream(c);
                if (d < 0 || lakeId[c] >= 0) continue;
                final int f = lakeFloorAt[d];
                if (f > lakeFloorAt[c] && f <= Math.round(breached[c])) lakeFloorAt[c] = f;
            }
        }

        private int lakeLevelBeside(int c, double width, int water) {
            final int r = 1 + (int) (width / CELL);
            final int ci = c & W_MASK, cj = c >> W_SHIFT;
            int best = Integer.MIN_VALUE;
            for (int dj = -r; dj <= r; dj++) {
                final int nj = cj + dj;
                if (nj < 0 || nj >= W) continue;
                for (int di = -r; di <= r; di++) {
                    final int ni = ci + di;
                    if (ni < 0 || ni >= W) continue;
                    final int id = lakeId[(nj << W_SHIFT) | ni];
                    if (id < 0) continue;
                    final int y = lakeWaterY.get(id);
                    if (y <= water && y > best) best = y;
                }
            }
            return best;
        }

        void extractLakes() {
            final long[] pits = new long[N];
            int pitCount = 0;
            for (int c = 0; c < N; c++) {
                if (sink[c] || filled[c] - h[c] < LAKE_MIN_DEPTH_FED) continue;
                final int i = c & W_MASK, j = c >> W_SHIFT;
                boolean minimum = true;
                for (int k = 0; k < 8; k++) {
                    final int ni = i + DX[k], nj = j + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) {
                        minimum = false;
                        break;
                    }
                    final int nc = (nj << W_SHIFT) | ni;

                    if (h[nc] < h[c] || (h[nc] == h[c] && nc < c)) {
                        minimum = false;
                        break;
                    }
                }
                if (minimum) pits[pitCount++] = heapKeyFor(h[c], c);
            }
            Arrays.sort(pits, 0, pitCount);

            final int[] stamp = new int[N];

            final int[] water = new int[N];
            final int[] region = new int[LAKE_MAX_GROWTH];

            final int[] marked = new int[LAKE_MAX_GROWTH * 8 + 8];
            final int[] markedWas = new int[marked.length];
            int growth = 0;
            for (int p = 0; p < pitCount; p++) {
                final int pit = (int) (pits[p] & CELL_KEY_MASK);

                if (lakeId[pit] != -1 || stamp[pit] != 0) continue;

                growth++;
                heapSize = 0;
                push(heapKeyFor(h[pit], pit));
                stamp[pit] = growth;
                int marks = 0;
                markedWas[marks] = 0;
                marked[marks++] = pit;
                int size = 0;
                float rim = h[pit];
                boolean escaped = false;

                boolean capped = false;

                int probeFrom = -1, probeMarks = 0, lastRise = 0;
                int loI = W, hiI = -1, loJ = W, hiJ = -1;
                while (heapSize > 0) {
                    final int r = (int) (pop() & CELL_KEY_MASK);
                    final int i = r & W_MASK, j = r >> W_SHIFT;

                    if (probeFrom >= 0 && h[r] >= rim) probeFrom = -1;
                    final boolean border = i < LAKE_BORDER_MARGIN || j < LAKE_BORDER_MARGIN
                            || i >= W - LAKE_BORDER_MARGIN || j >= W - LAKE_BORDER_MARGIN;
                    final boolean wide = Math.max(hiI, i) - Math.min(loI, i) >= LAKE_MAX_EXTENT
                            || Math.max(hiJ, j) - Math.min(loJ, j) >= LAKE_MAX_EXTENT;
                    if (sink[r] || border || wide || size >= LAKE_MAX_GROWTH) {
                        if (probeFrom >= 0) {
                            for (int k = probeMarks; k < marks; k++) stamp[marked[k]] = markedWas[k];
                            size = probeFrom;
                        } else if (!sink[r]) {
                            if (!border && Mth.floor(rim) - 1 - h[pit] >= LAKE_WIDE_DEPTH) capped = true;
                            else escaped = true;
                        }
                        break;
                    }
                    if (probeFrom < 0) {
                        if (h[r] < rim) {
                            probeFrom = size;
                            probeMarks = lastRise;
                        } else {
                            rim = h[r];
                        }
                    }
                    region[size++] = r;
                    if (i < loI) loI = i;
                    if (i > hiI) hiI = i;
                    if (j < loJ) loJ = j;
                    if (j > hiJ) hiJ = j;

                    if (probeFrom < 0) lastRise = marks;
                    for (int k = 0; k < 8; k++) {
                        final int ni = i + DX[k], nj = j + DZ[k];
                        if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                        final int nc = (nj << W_SHIFT) | ni;
                        if (stamp[nc] == growth) continue;
                        markedWas[marks] = stamp[nc];
                        stamp[nc] = growth;
                        marked[marks++] = nc;
                        push(heapKeyFor(h[nc], nc));
                    }
                }

                final int waterY = Mth.floor(rim) - 1;
                final float depth = rim - h[pit];

                int count = 0, deep = 0;
                boolean fed = false;

                boolean byRiver = false;
                float deepest = rim;
                int lo_i = W, hi_i = -1, lo_j = W, hi_j = -1;
                for (int k = 0; k < size; k++) {
                    final int c = region[k];

                    final int i = c & W_MASK, j = c >> W_SHIFT;
                    if (i < lo_i) lo_i = i;
                    if (i > hi_i) hi_i = i;
                    if (j < lo_j) lo_j = j;
                    if (j > hi_j) hi_j = j;
                    if (h[c] > waterY) continue;
                    count++;

                    if (h[c] < waterY - 0.5f) deep++;
                    if (h[c] < deepest) deepest = h[c];
                    if (wet[c] >= streamRain) fed = true;
                    if (nearRiver[c]) byRiver = true;
                }
                final boolean tooWide = hi_i - lo_i >= LAKE_MAX_EXTENT || hi_j - lo_j >= LAKE_MAX_EXTENT;

                final int minCells = lakeMinCells(pit, fed);

                final float width = (float) Math.sqrt(count) * CELL;
                final boolean bottomless = waterY - deepest > Math.max(LAKE_MAX_DEPTH, LAKE_SHAFT_RATIO * width);
                final float minDepth = fed ? LAKE_MIN_DEPTH_FED : LAKE_MIN_DEPTH;

                final boolean pond = fed && depth >= POND_MIN_DEPTH;

                if (escaped || tooWide || byRiver || (bottomless && !pond) || (count < minCells && !pond)
                        || count > LAKE_MAX_CELLS || depth < minDepth || waterY < seaLevel - 1 + LAKE_ABOVE_SEA
                        || deep < LAKE_DEEP_SHARE * count) {
                    continue;
                }

                if (!pond && count < LAKE_ROUND_CELLS) {
                    for (int k = 0; k < size; k++) {
                        if (h[region[k]] <= waterY) water[region[k]] = growth;
                    }
                    int edges = 0;
                    for (int k = 0; k < size; k++) {
                        final int c = region[k];
                        if (h[c] > waterY) continue;
                        final int i = c & W_MASK, j = c >> W_SHIFT;
                        for (int e = 0; e < 8; e += 2) {
                            final int ni = i + DX[e], nj = j + DZ[e];
                            if (ni < 0 || nj < 0 || ni >= W || nj >= W || water[(nj << W_SHIFT) | ni] != growth) edges++;
                        }
                    }
                    if (edges < LAKE_MIN_SHAPE * Math.sqrt(count)) continue;
                }

                final int id = lakeCells.size();
                final int[] cells = new int[count];
                int n = 0;
                for (int k = 0; k < size; k++) {
                    final int c = region[k];
                    if (h[c] > waterY) continue;
                    lakeId[c] = id;
                    cells[n++] = c;
                }
                lakeCells.add(cells);

                final int[] shore = new int[size - count];
                int m = 0;
                for (int k = 0; k < size; k++) {
                    final int c = region[k];
                    if (lakeId[c] >= 0 || lakeShore[c] >= 0) continue;
                    lakeShore[c] = id;
                    shore[m++] = c;
                }
                lakeShoreCells.add(Arrays.copyOf(shore, m));
                lakeWaterY.add(waterY);
                if (capped && !neighbour) WatershedStats.lakesCapped.increment();
            }
        }

        void settleLakeChains() {
            final int n = lakeCells.size();
            if (n < 2) return;
            final int[] parent = new int[n];
            final int[] members = new int[n];
            final boolean[] seen = new boolean[n];
            for (int round = 0; round < 16; round++) {
                for (int a = 0; a < n; a++) parent[a] = a;
                boolean anyPair = false;
                for (int a = 0; a < n; a++) {
                    final int ya = lakeWaterY.get(a);
                    for (int c : lakeCells.get(a)) {
                        final int i = c & W_MASK, j = c >> W_SHIFT;
                        final int i0 = Math.max(0, i - LAKE_CHAIN_CELLS), i1 = Math.min(W - 1, i + LAKE_CHAIN_CELLS);
                        final int j0 = Math.max(0, j - LAKE_CHAIN_CELLS), j1 = Math.min(W - 1, j + LAKE_CHAIN_CELLS);
                        for (int nj = j0; nj <= j1; nj++) {
                            for (int ni = i0; ni <= i1; ni++) {
                                final int b = lakeId[(nj << W_SHIFT) | ni];
                                if (b <= a || Math.abs(lakeWaterY.get(b) - ya) > LAKE_CHAIN_STEP) continue;
                                anyPair = true;
                                final int ra = root(parent, a), rb = root(parent, b);
                                if (ra != rb) parent[Math.max(ra, rb)] = Math.min(ra, rb);
                            }
                        }
                    }
                }
                if (!anyPair) return;

                boolean changed = false;
                Arrays.fill(seen, false);
                for (int a = 0; a < n; a++) {
                    final int g = root(parent, a);
                    if (seen[g]) continue;
                    seen[g] = true;
                    int count = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                    for (int b = a; b < n; b++) {
                        if (root(parent, b) != g) continue;
                        members[count++] = b;
                        final int y = lakeWaterY.get(b);
                        if (y < lo) lo = y;
                        if (y > hi) hi = y;
                    }
                    if (lo == hi) continue;

                    int bestY = lo;
                    long bestKept = -1;
                    for (int y = lo; y <= hi; y++) {
                        boolean candidate = false;
                        for (int k = 0; k < count && !candidate; k++) candidate = lakeWaterY.get(members[k]) == y;
                        if (!candidate) continue;
                        long kept = 0;
                        for (int k = 0; k < count; k++) kept += keptAt(members[k], y);
                        if (kept > bestKept) {
                            bestKept = kept;
                            bestY = y;
                        }
                    }
                    for (int k = 0; k < count; k++) {
                        if (settleLake(members[k], bestY)) changed = true;
                    }
                }
                if (!changed) return;
            }
        }

        private static int root(int[] parent, int a) {
            while (parent[a] != a) {
                parent[a] = parent[parent[a]];
                a = parent[a];
            }
            return a;
        }

        private int keptAt(int a, int y) {
            final int own = lakeWaterY.get(a);
            final int[] cells = lakeCells.get(a);
            if (y == own) return cells.length;
            if (y > own) return 0;
            int kept = 0, deep = 0;
            float deepest = Float.POSITIVE_INFINITY;
            for (int c : cells) {
                if (h[c] <= y) {
                    kept++;

                    if (h[c] < y - 0.5f) deep++;
                    if (h[c] < deepest) deepest = h[c];
                }
            }
            return kept >= LAKE_MIN_CELLS_FED && y + 1 - deepest >= LAKE_MIN_DEPTH_FED
                    && deep >= LAKE_DEEP_SHARE * kept ? kept : 0;
        }

        private boolean settleLake(int a, int y) {
            final int own = lakeWaterY.get(a);
            final int[] cells = lakeCells.get(a);
            if (y == own || cells.length == 0) return false;
            final int kept = keptAt(a, y);
            final int[] next = new int[kept];
            int k = 0;
            for (int c : cells) {
                if (kept > 0 && h[c] <= y) next[k++] = c;
                else lakeId[c] = -1;
            }
            lakeCells.set(a, next);

            for (int c : lakeShoreCells.get(a)) {
                if (lakeShore[c] == a) lakeShore[c] = -1;
            }
            lakeShoreCells.set(a, new int[0]);

            if (closedLake(a)) closedChanged = true;
            if (kept > 0) {
                lakeWaterY.set(a, y);
                WatershedStats.lakesSettled.increment();
            } else {
                WatershedStats.lakesDropped.increment();
            }
            return true;
        }

        private boolean lowerUndercutLakes() {
            final int n = lakeCells.size();
            if (n == 0) return false;
            final int[] under = new int[n];
            Arrays.fill(under, Integer.MAX_VALUE);
            for (int c = 0; c < N; c++) {
                final int own = lakeId[c] >= 0 ? lakeId[c] : lakeShore[c];
                if (own >= 0) {
                    if (seamsImported && inflow[c]) under[own] = Math.min(under[own], underLevel(own, seamLevel[c]));
                    continue;
                }
                final int d = chan[c] ? downstream(c) : -1;
                final int lake = d < 0 ? -1 : lakeId[d] >= 0 ? lakeId[d] : lakeShore[d];
                if (lake >= 0 && heldUnder(c, lakeWaterY.get(lake))) {
                    under[lake] = Math.min(under[lake], underLevel(lake, breached[c]));
                }
            }
            boolean changed = false;
            for (int a = 0; a < n; a++) {
                if (seamsImported && closedLake(a)) continue;

                if (sharedLake(a)) continue;
                if (under[a] < lakeWaterY.get(a) && settleLake(a, under[a])) {
                    WatershedStats.lakesUndercut.increment();
                    changed = true;
                }
            }
            return changed;
        }

        private boolean sharedLake(int a) {
            final int[] cells = lakeCells.get(a);
            if (cells.length == 0) return false;
            final int tx = Math.floorDiv(cx0 + (cells[0] & W_MASK), CORE), tz = Math.floorDiv(cz0 + (cells[0] >> W_SHIFT), CORE);
            for (int c : cells) {
                if (Math.floorDiv(cx0 + (c & W_MASK), CORE) != tx || Math.floorDiv(cz0 + (c >> W_SHIFT), CORE) != tz) return true;
            }
            return false;
        }

        private int underLevel(int a, float level) {
            final int routed = Math.round(level);
            return routed < lakeWaterY.get(a) ? routed - CHANNEL_INCISION : Integer.MAX_VALUE;
        }

        private boolean heldUnder(int c, int level) {
            if (Math.round(breached[c]) >= level) return false;
            final boolean[] seen = new boolean[N];
            final int[] stack = new int[N];
            int sp = 0;
            stack[sp++] = c;
            seen[c] = true;
            while (sp > 0) {
                final int e = stack[--sp];
                if (seamsImported && inflow[e] && Math.round(seamLevel[e]) < level) return true;
                final int i = e & W_MASK, j = e >> W_SHIFT;
                int ups = 0;
                for (int k = 0; k < 8; k++) {
                    final int ni = i + DX[k], nj = j + DZ[k];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    final int nc = (nj << W_SHIFT) | ni;
                    if (!chan[nc] || downstream(nc) != e) continue;
                    if (++ups >= 2 || regionIndex(nc) != regionIndex(e)) return true;
                    if (seen[nc] || Math.round(breached[nc]) >= level) continue;
                    seen[nc] = true;
                    stack[sp++] = nc;
                }
            }
            return false;
        }

        void packageLakes() {
            final int nr = WINDOW_REGIONS * WINDOW_REGIONS;
            final boolean[] touched = new boolean[nr];
            packaged = new WatershedLake[lakeCells.size()];
            for (int id = 0; id < lakeCells.size(); id++) {
                final int[] cells = lakeCells.get(id);
                if (cells.length == 0) continue;
                final int waterY = lakeWaterY.get(id);
                int loI = W, hiI = -1, loJ = W, hiJ = -1;
                Arrays.fill(touched, false);
                for (int c : cells) {
                    final int i = c & W_MASK, j = c >> W_SHIFT;
                    if (i < loI) loI = i;
                    if (i > hiI) hiI = i;
                    if (j < loJ) loJ = j;
                    if (j > hiJ) hiJ = j;
                    touched[regionIndex(c)] = true;
                }
                final int w = hiI - loI + 1, hh = hiJ - loJ + 1;
                final long[] bits = WatershedLake.newBits(w, hh);
                for (int c : cells) {
                    WatershedLake.setBit(bits, w, (c & W_MASK) - loI, (c >> W_SHIFT) - loJ);
                }

                final int gw = w + 2, gh = hh + 2;
                final short[] coarse = new short[gw * gh];
                for (int lz = 0; lz < gh; lz++) {
                    final int j = Mth.clamp(loJ - 1 + lz, 0, W - 1);
                    for (int lx = 0; lx < gw; lx++) {
                        final int i = Mth.clamp(loI - 1 + lx, 0, W - 1);
                        final int c = (j << W_SHIFT) | i;
                        int ground = Math.round(h[c]);

                        if (lakeId[c] != id && ground <= waterY) ground = waterY + 1;
                        coarse[lz * gw + lx] = (short) ground;
                    }
                }
                final WatershedLake lake = new WatershedLake(waterY, cx0 + loI, cz0 + loJ, w, hh, bits, coarse,
                        deadEndLakeIds.contains(id) ? DEAD_END_LAKE_DIG : WatershedLake.SHAVE);
                packaged[id] = lake;
                boolean emitted = false;
                for (int r = 0; r < nr; r++) {
                    if (!touched[r] || !isCoreRegion(r)) continue;
                    tile.lakesByRegion.get(regionKeyOf(r)).add(lake);
                    emitted = true;
                }
                if (emitted) WatershedStats.lakesEmitted.increment();
            }
        }

        private int regionIndex(int c) {
            return ((c >> W_SHIFT) >> REGION_SHIFT) * WINDOW_REGIONS + ((c & W_MASK) >> REGION_SHIFT);
        }

        private boolean isCoreRegion(int r) {
            int ri = r % WINDOW_REGIONS, rj = r / WINDOW_REGIONS;
            return ri >= HALO_REGIONS && ri < HALO_REGIONS + REGIONS_PER_SIDE
                    && rj >= HALO_REGIONS && rj < HALO_REGIONS + REGIONS_PER_SIDE;
        }

        private long regionKeyOf(int r) {
            int ri = r % WINDOW_REGIONS, rj = r / WINDOW_REGIONS;
            return HydrologyManager.getRegionKey(tile.tileX * REGIONS_PER_SIDE + ri - HALO_REGIONS,
                    tile.tileZ * REGIONS_PER_SIDE + rj - HALO_REGIONS);
        }

        private boolean startsReach(int s) {
            if (!chan[s]) return false;
            return upCount[s] != 1 || upLake[s] || !sameRegionUp[s] || inflow[s];
        }

        private int aimIntoLake(int prev, int c, int n) {
            final int lake = lakeId[n];
            final int ci = c & W_MASK, cj = c >> W_SHIFT;
            final int ai = prev >= 0 ? ci - (prev & W_MASK) : (n & W_MASK) - ci;
            final int aj = prev >= 0 ? cj - (prev >> W_SHIFT) : (n >> W_SHIFT) - cj;
            int best = n, most = waterAhead(c, (n & W_MASK) - ci, (n >> W_SHIFT) - cj, lake);
            for (int k = 0; k < 8; k++) {
                if (DX[k] * ai + DZ[k] * aj < 0) continue;
                final int ni = ci + DX[k], nj = cj + DZ[k];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                final int m = (nj << W_SHIFT) | ni;
                if (m == n || lakeId[m] != lake) continue;
                final int run = waterAhead(c, DX[k], DZ[k], lake);
                if (run > most) {
                    most = run;
                    best = m;
                }
            }
            return best;
        }

        private int waterAhead(int c, int di, int dj, int lake) {
            int i = c & W_MASK, j = c >> W_SHIFT, run = 0;
            while (run < MOUTH_AIM_CELLS) {
                i += di;
                j += dj;
                if (i < 0 || j < 0 || i >= W || j >= W || lakeId[(j << W_SHIFT) | i] != lake) break;
                run++;
            }
            return run;
        }

        private int extendInto(int[] buf, int len, int from, int into, int lake) {
            int di = (into & W_MASK) - (from & W_MASK);
            int dj = (into >> W_SHIFT) - (from >> W_SHIFT);
            final int ei = di, ej = dj;
            int i = into & W_MASK, j = into >> W_SHIFT;
            for (int k = 0; k < MOUTH_EXTRA_CELLS; k++) {
                if (lake >= 0) {
                    final int at = (j << W_SHIFT) | i;
                    int most = waterAhead(at, di, dj, lake), bi = di, bj = dj;
                    for (int d = 0; d < 8; d++) {
                        if (DX[d] * di + DZ[d] * dj < 0 || DX[d] * ei + DZ[d] * ej < 0 || DX[d] == di && DZ[d] == dj) continue;
                        final int run = waterAhead(at, DX[d], DZ[d], lake);
                        if (run > most) {
                            most = run;
                            bi = DX[d];
                            bj = DZ[d];
                        }
                    }
                    di = bi;
                    dj = bj;
                }
                i += di;
                j += dj;
                if (i < 0 || j < 0 || i >= W || j >= W) break;
                final int c = (j << W_SHIFT) | i;
                final boolean stillWater = lake < 0 ? (sink[c] && !mainRiver[c]) : lakeId[c] == lake;

                if (!stillWater || lake >= 0 && inCore(into) && rimDistance(c) < 1) break;
                buf[len++] = c;
            }
            return len;
        }

        private int leadFromLake(int[] buf, int s) {
            int from = -1;
            final int si = s & W_MASK, sj = s >> W_SHIFT;
            for (int k = 0; k < 8; k++) {
                final int ni = si + DX[k], nj = sj + DZ[k];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                final int nc = (nj << W_SHIFT) | ni;
                if (lakeId[nc] >= 0 && downstream(nc) == s) {
                    from = nc;
                    break;
                }
            }
            if (from < 0) return 0;

            final int lake = lakeId[from];
            final int di = (from & W_MASK) - si, dj = (from >> W_SHIFT) - sj;
            int n = 1;
            int i = from & W_MASK, j = from >> W_SHIFT;
            for (int k = 1; k < LAKE_LEAD_CELLS; k++) {
                i += di;
                j += dj;
                if (i < 0 || j < 0 || i >= W || j >= W) break;
                if (lakeId[(j << W_SHIFT) | i] != lake) break;
                n++;
            }

            i = from & W_MASK;
            j = from >> W_SHIFT;
            for (int k = 0; k < n; k++) {
                buf[n - 1 - k] = (j << W_SHIFT) | i;
                i += di;
                j += dj;
            }
            return n;
        }

        void computeChannels() {
            for (int c = 0; c < N; c++) {
                chan[c] = !sink[c] && lakeId[c] < 0 && !(seamsImported && takenOver[c])
                        && (seamsImported && inflow[c]
                                || !cut[c] && wet[c] >= needRain[c] && acc[c] >= needConvergence[c] * flowLen[c]);
            }
            for (int c = 0; c < N; c++) {
                if (!chan[c]) continue;
                int d = downstream(c);
                while (d >= 0 && !chan[d] && !sink[d] && lakeId[d] < 0) {
                    chan[d] = true;
                    if (seamsImported && takenOver[d]) break;
                    d = downstream(d);
                }
            }
        }

        void prepareBends() {
            bendCrossing = new boolean[N];
            for (int c = 0; c < N; c++) {
                if (inflow[c]) bendCrossing[c] = true;
                if (!chan[c]) continue;
                final int d = downstream(c);
                if (d >= 0 && inCore(c) != inCore(d)) {
                    bendCrossing[c] = true;
                    bendCrossing[d] = true;
                }
            }
            bendKeep = new float[N];
            bendNx = new float[N];
            bendNz = new float[N];
            bendK = new float[N];
            bendPhase = new float[N];
            bendFlat = new float[N];

            final boolean[] known = new boolean[N];
            final int[] stack = new int[N];
            for (int s = 0; s < N; s++) {
                if (known[s]) continue;
                int sp = 0, c = s;
                while (c >= 0 && !known[c] && sp < N) {
                    stack[sp++] = c;
                    c = downstream(c);
                }
                int k = sp - 1;
                double phase;
                if (c >= 0 && known[c]) {
                    phase = bendPhase[c];
                } else {
                    final int e = stack[k--];
                    final long wx = cx0 + (e & W_MASK), wz = cz0 + (e >> W_SHIFT);
                    phase = Math.PI * 2.0 * (it.unimi.dsi.fastutil.HashCommon.mix(
                            seed ^ wx * 0x9E3779B97F4A7C15L ^ wz * 0xC2B2AE3D27D4EB4FL) >>> 11) * 0x1.0p-53;
                    bendPhase[e] = (float) phase;
                    known[e] = true;
                }
                for (; k >= 0; k--) {
                    final int cc = stack[k];
                    final int d = dir[cc];

                    final double rate = 1.0 + MEANDER_WAVE_SPREAD * valueNoise((cx0 + (cc & W_MASK)) / MEANDER_WAVE_CELLS,
                            (cz0 + (cc >> W_SHIFT)) / MEANDER_WAVE_CELLS, 9);
                    phase += Math.PI * 2.0 * (d < 0 ? 1.0f : DIST[d]) * CELL / (MEANDER_WAVELENGTH * 2.0 * cellWidth(cc)) * rate;
                    bendPhase[cc] = (float) phase;
                    known[cc] = true;
                }
            }
            for (int c = 0; c < N; c++) {
                if (!chan[c]) continue;

                final int up = mainUp(c), up2 = up >= 0 ? mainUp(up) : -1;
                final int dn = downstream(c), dn2 = dn >= 0 ? downstream(dn) : -1;
                final int a = up2 >= 0 ? up2 : up >= 0 ? up : c;
                final int b = dn2 >= 0 ? dn2 : dn >= 0 ? dn : c;
                final double ax = (c & W_MASK) - (a & W_MASK), az = (c >> W_SHIFT) - (a >> W_SHIFT);
                final double bx = (b & W_MASK) - (c & W_MASK), bz = (b >> W_SHIFT) - (c >> W_SHIFT);
                final double al = Math.hypot(ax, az), bl = Math.hypot(bx, bz);
                double fx = 0.0, fz = 0.0;
                if (al > 0.0) {
                    fx += ax / al;
                    fz += az / al;
                }
                if (bl > 0.0) {
                    fx += bx / bl;
                    fz += bz / bl;
                }
                final double fl = Math.hypot(fx, fz);
                if (fl < 1.0E-6) continue;
                fx /= fl;
                fz /= fl;
                bendNx[c] = (float) -fz;
                bendNz[c] = (float) fx;
                if (al > 0.0 && bl > 0.0) {
                    final double turn = Math.acos(Mth.clamp((ax * bx + az * bz) / (al * bl), -1.0, 1.0));
                    final double side = (bx / bl - ax / al) * -fz + (bz / bl - az / al) * fx;
                    bendK[c] = (float) (Math.signum(side) * turn / (0.5 * (al + bl) * CELL));
                }
                final double run = (al + bl) * CELL;
                final double slope = run > 0.0 ? Math.max(0.0, breached[a] - breached[b]) / run : 0.0;
                bendFlat[c] = (float) Mth.clampedMap(slope, MEANDER_FLAT_SLOPE, MEANDER_STEEP_SLOPE, 1.0, MEANDER_STEEP_SHARE);
                bendKeep[c] = (float) keepAt(c);
            }
        }

        private double cellWidth(int c) {
            return riverWidth((double) wet[c] / RAIN_UNITS);
        }

        private int mainUp(int c) {
            final int i = c & W_MASK, j = c >> W_SHIFT;
            int best = -1, bestWet = 0;
            for (int k = 0; k < 8; k++) {
                final int ni = i + DX[k], nj = j + DZ[k];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                final int nc = (nj << W_SHIFT) | ni;
                if (wet[nc] > bestWet && downstream(nc) == c) {
                    bestWet = wet[nc];
                    best = nc;
                }
            }
            return best;
        }

        private boolean noSway(int c) {
            return sink[c] || cut[c] || lakeId[c] >= 0 || lakeShore[c] >= 0;
        }

        private double keepAt(int c) {
            final int i = c & W_MASK, j = c >> W_SHIFT;
            int near = MEANDER_KEEP_CELLS;
            int nearMain = MEANDER_KEEP_MAIN_CELLS;
            for (int r = 0; r < Math.max(near, nearMain); r++) {
                boolean hit = false, main = false;
                for (int dj = -r; dj <= r; dj++) {
                    final int nj = j + dj;
                    if (nj < 0 || nj >= W) continue;
                    for (int di = -r; di <= r; di++) {
                        if (Math.max(Math.abs(di), Math.abs(dj)) != r) continue;
                        final int ni = i + di;
                        if (ni < 0 || ni >= W) continue;
                        final int nc = (nj << W_SHIFT) | ni;
                        if (noSway(nc) || bendCrossing[nc]) hit = true;
                        if (mainRiver[nc]) main = true;
                    }
                }
                if (hit && r < near) near = r;
                if (main && r < nearMain) nearMain = r;
            }
            return Math.min(smoothstep((double) near / MEANDER_KEEP_CELLS),
                    smoothstep((double) nearMain / MEANDER_KEEP_MAIN_CELLS));
        }

        private boolean besideMain(int c) {
            final int i = c & W_MASK, j = c >> W_SHIFT;
            for (int dj = -MEANDER_MAIN_GAP_CELLS; dj <= MEANDER_MAIN_GAP_CELLS; dj++) {
                final int nj = j + dj;
                if (nj < 0 || nj >= W) continue;
                for (int di = -MEANDER_MAIN_GAP_CELLS; di <= MEANDER_MAIN_GAP_CELLS; di++) {
                    final int ni = i + di;
                    if (ni >= 0 && ni < W && mainRiver[(nj << W_SHIFT) | ni]) return true;
                }
            }
            return false;
        }

        private double sway(double x, double z, int c0, int c1, double t, double tn, double ends) {
            swayNormal[0] = 0.0;
            swayNormal[1] = 0.0;
            final double keep = Mth.lerp(t, bendKeep[c0], bendKeep[c1]) * ends;
            if (keep <= 0.0) return 0.0;

            final double tp = bendKeep[c0] <= 0.0f ? 1.0 : bendKeep[c1] <= 0.0f ? 0.0 : t;
            final double tq = bendKeep[c0] <= 0.0f ? 1.0 : bendKeep[c1] <= 0.0f ? 0.0 : tn;
            double nx = Mth.lerp(tq, bendNx[c0], bendNx[c1]), nz = Mth.lerp(tq, bendNz[c0], bendNz[c1]);
            final double nl = Math.hypot(nx, nz);
            if (nl < 1.0E-6) return 0.0;
            nx /= nl;
            nz /= nl;
            swayNormal[0] = nx;
            swayNormal[1] = nz;
            final double r = Mth.lerp(tp, cellWidth(c0), cellWidth(c1));
            final double phase = Mth.lerp(tp, bendPhase[c0], bendPhase[c1]);
            final double strength = 0.65 + 0.35 * Math.sin(phase / MEANDER_STRENGTH_SPAN + 1.3);
            final double wave = 0.72 * Math.sin(phase) + 0.28 * Math.sin(phase / 0.47 + 0.9);
            final double lambda = MEANDER_WAVELENGTH * 2.0 * r;
            double d = Math.min(MEANDER_MAX_SWING, MEANDER_AMPLITUDE * lambda * strength)
                    * Mth.lerp(tp, bendFlat[c0], bendFlat[c1]) * keep * wave;

            final double bend = Mth.lerp(tq, bendK[c0], bendK[c1]);
            if (d * bend > 0.0) d = Math.signum(d) * Math.min(Math.abs(d), MEANDER_BEND_SHARE / Math.abs(bend));

            final double level = Mth.lerp(tp, breached[c0], breached[c1]);
            d += keep * 0.5 * (lacksBeside(x, z, -nx, -nz, r, level, c0, c1) - lacksBeside(x, z, nx, nz, r, level, c0, c1));
            if (Math.abs(d) < 0.05) return 0.0;

            final double edge = MEANDER_EDGE_SHARE * r;
            final double s = Math.signum(d);
            final double room = valleyRoom(x, z, s * nx, s * nz, level, Math.abs(d) + edge, r, c0, c1) - edge;
            return s * Math.max(0.0, Math.min(Math.min(Math.abs(d), room), seamRoom(x, z, s * nx, s * nz, r)));
        }

        private double lacksBeside(double x, double z, double ux, double uz, double width, double level, int c0, int c1) {
            final int up = mainUp(c0), down = downstream(c1);
            final double reach = width + MAX_WIDTH + MEANDER_RIVER_APART + MEANDER_GAP_MAX;
            for (double s = 2.0; s <= reach; s += 2.0) {
                final int ci = (int) Math.round((x + ux * s) / CELL) - cx0, cj = (int) Math.round((z + uz * s) / CELL) - cz0;
                if (ci < 0 || cj < 0 || ci >= W || cj >= W) return 0.0;
                final int c = (cj << W_SHIFT) | ci;
                if (!chan[c] || c == c0 || c == c1 || c == up || c == down) continue;
                final double rise = Math.abs(breached[c] - level);
                if (meetsSoon(c, c1) && rise < MEANDER_JOIN_LEVEL) return 0.0;
                return Math.max(0.0, width + cellWidth(c) + MEANDER_RIVER_APART + Math.min(MEANDER_GAP_MAX, MEANDER_GAP_PER_BLOCK * rise) - s);
            }
            return 0.0;
        }

        private double seamRoom(double x, double z, double ux, double uz, double width) {
            final double x0 = (cx0 + HALO) << CS, x1 = (cx0 + HALO + CORE) << CS;
            final double z0 = (cz0 + HALO) << CS, z1 = (cz0 + HALO + CORE) << CS;
            final double keep = 0.5 * width + MEANDER_SEAM_MARGIN;
            return Math.min(Math.min(edgeRoom(x - x0, -ux, keep), edgeRoom(x1 - x, ux, keep)),
                    Math.min(edgeRoom(z - z0, -uz, keep), edgeRoom(z1 - z, uz, keep)));
        }

        private static double edgeRoom(double inside, double rate, double keep) {
            if (rate <= 1.0E-9) return Double.POSITIVE_INFINITY;
            return Math.max(0.0, (inside - Math.min(inside, keep)) / rate);
        }

        private boolean roomFor(double x, double z, double level, double width) {
            final double g = groundAt(h, x, z);
            return g <= level + MEANDER_ROOM_PER_WIDTH * width + MEANDER_ROOM_RISE && g >= level - MEANDER_ROOM_DROP;
        }

        private double valleyRoom(double x, double z, double ux, double uz, double level, double limit, double width,
                int c0, int c1) {
            final double top = level + MEANDER_ROOM_PER_WIDTH * width + MEANDER_ROOM_RISE;
            final double bottom = level - MEANDER_ROOM_DROP;
            final int bi = (int) Math.round(x / CELL) - cx0, bj = (int) Math.round(z / CELL) - cz0;
            final int up = mainUp(c0), down = downstream(c1);
            double room = 0.0, before = groundAt(h, x, z);
            for (double d = 2.0; ; d += 2.0) {
                final double s = Math.min(d, limit);
                final double px = x + ux * s, pz = z + uz * s;
                final int ci = (int) Math.round(px / CELL) - cx0, cj = (int) Math.round(pz / CELL) - cz0;
                if (ci < 0 || cj < 0 || ci >= W || cj >= W) return room;
                final int c = (cj << W_SHIFT) | ci;
                if (noSway(c) || besideMain(c)) return room;
                if (chan[c] && c != c0 && c != c1 && c != up && c != down) {
                    if (!meetsSoon(c, c1) || Math.abs(breached[c] - level) >= MEANDER_JOIN_LEVEL) {
                        return Math.min(room, halfWay(s, width, c, level));
                    }
                    if (Math.max(Math.abs(ci - bi), Math.abs(cj - bj)) >= 2) return room;
                }
                final double g = groundAt(h, px, pz);
                if (g > top) {
                    return g > before ? room + (s - room) * Mth.clamp((top - before) / (g - before), 0.0, 1.0) : room;
                }
                if (g < bottom) {
                    return g < before ? room + (s - room) * Mth.clamp((before - bottom) / (before - g), 0.0, 1.0) : room;
                }
                room = s;
                before = g;
                if (s >= limit) break;
            }

            final double far = limit + MAX_WIDTH + MEANDER_RIVER_GAP + MEANDER_GAP_MAX;
            for (double s = limit + 2.0; s <= far; s += 2.0) {
                final int ci = (int) Math.round((x + ux * s) / CELL) - cx0, cj = (int) Math.round((z + uz * s) / CELL) - cz0;
                if (ci < 0 || cj < 0 || ci >= W || cj >= W) break;
                final int c = (cj << W_SHIFT) | ci;
                if (!chan[c] || c == c0 || c == c1 || c == up || c == down) continue;
                if (meetsSoon(c, c1) && Math.abs(breached[c] - level) < MEANDER_JOIN_LEVEL) break;
                return Math.min(room, halfWay(s, width, c, level));
            }
            return room;
        }

        private double halfWay(double s, double width, int c, double level) {
            final double gap = MEANDER_RIVER_GAP + Math.min(MEANDER_GAP_MAX, MEANDER_GAP_PER_BLOCK * Math.abs(breached[c] - level));
            return Math.max(0.0, 0.5 * (s + width - cellWidth(c) - gap));
        }

        private boolean meetsSoon(int a, int b) {
            final int[] down = new int[MEANDER_JOIN_CELLS + 1];
            int n = 0;
            for (int c = b; c >= 0 && n <= MEANDER_JOIN_CELLS; c = downstream(c)) down[n++] = c;
            for (int k = 0, c = a; c >= 0 && k <= MEANDER_JOIN_CELLS; k++, c = downstream(c)) {
                for (int i = 0; i < n; i++) {
                    if (down[i] == c) return true;
                }
            }
            return false;
        }

        private void turnIntoConfluence(double[] pts, int m, int prev, int jc, int dn, int main) {
            final double mainIn = heading(main, jc);
            final double current = mainIn + 0.5 * Mth.wrapDegrees(heading(jc, dn) - mainIn);

            final double off0 = Mth.wrapDegrees(heading(prev, jc) - mainIn);
            final double turns = Mth.wrapDegrees(current - mainIn);
            final double side = Math.abs(off0) > 179.0 && turns != 0.0 ? Math.signum(turns) : off0 < 0.0 ? -1.0 : 1.0;
            final double off = side * off0 >= 0.0 ? side * off0 : side * off0 + 360.0;
            final double along = side * Mth.wrapDegrees(current - mainIn);
            final double lo = Math.max(JUNCTION_APART, along - JUNCTION_Y), hi = along + JUNCTION_Y;
            final double want = lo <= hi ? Mth.clamp(off, lo, hi) : 0.5 * (lo + hi);
            final double turn = side * Mth.clamp(want - off, -JUNCTION_MAX_TURN, JUNCTION_MAX_TURN);
            if (turn == 0.0) return;
            final double ox = pts[(m - 1) * 3], oz = pts[(m - 1) * 3 + 1];
            for (int j = 0; j < JUNCTION_TURN.length; j++) {
                final int p = m - 2 - j;
                if (p < 1) break;
                final double a = Math.toRadians(turn * JUNCTION_TURN[j]), cos = Math.cos(a), sin = Math.sin(a);
                final double dx = pts[p * 3] - ox, dz = pts[p * 3 + 1] - oz;
                pts[p * 3] = ox + dx * cos - dz * sin;
                pts[p * 3 + 1] = oz + dx * sin + dz * cos;
            }
        }

        private boolean gorgeAtMouth(int[] buf, int from, int to) {
            for (int k = to; k >= Math.max(from, to - MAIN_MOUTH_GORGE_CELLS); k--) {
                if (cut[buf[k]]) return true;
            }
            return false;
        }

        private static void mouthRamp(int[] ys, boolean[] fixed, int inWater, int lip) {
            final int n = ys.length, river = ys[0];
            int edge = Math.min(inWater, n - 1);
            while (edge + 1 < n && ys[edge + 1] <= river) edge++;
            if (edge + 1 >= n || ys[edge + 1] >= lip) return;

            for (int k = 1; k <= edge; k++) {
                if (!fixed[k]) ys[k] = river;
            }
            for (int k = edge + 1; k < n && !fixed[k]; k++) {
                final int cut = MAIN_MOUTH_CUT - (k - edge - 1);
                final int cap = Math.max(river + (k - edge) * MAIN_MOUTH_RAMP, ys[k] - cut);
                if (cut <= 0 || ys[k] <= cap) break;
                ys[k] = cap;
            }

            for (int k = edge + 1; k <= Math.min(n - 1, edge + MAIN_MOUTH_RAPIDS); k++) fixed[k] = true;
        }

        private static int nodesInMain(double[] xs, double[] zs) {
            int k = 0;
            while (k + 1 < xs.length) {
                final double x = xs[k + 1], z = zs[k + 1];
                final MainRiverPoint p = nearestMainRiver(x, z);
                if (p == null) break;
                final double r = p.waterRadius();
                if (Math.hypot(x - p.x(), z - p.z()) + HydrologyManager.getRiverJitter(x, z) * r / 10.0 >= r) break;
                k++;
            }
            return k;
        }

        private static double heading(int a, int b) {
            return Math.toDegrees(Math.atan2((b >> W_SHIFT) - (a >> W_SHIFT), (b & W_MASK) - (a & W_MASK)));
        }

        void traceChannels() {
            prepareBends();
            for (int c = 0; c < N; c++) {
                final boolean fromLake = lakeId[c] >= 0;
                if (!chan[c] && !fromLake) continue;
                final int d = downstream(c);
                if (d < 0) continue;
                if (fromLake) {
                    upLake[d] = true;
                } else {
                    if (upCount[d] < 100) upCount[d]++;
                    if (regionIndex(c) == regionIndex(d)) sameRegionUp[d] = true;
                }
            }
            final int[] buf = new int[N + 4];
            for (int s = 0; s < N; s++) {
                if (!startsReach(s)) continue;
                final int ownerRegion = regionIndex(s);
                if (!isCoreRegion(ownerRegion)) continue;

                int len = upLake[s] ? leadFromLake(buf, s) : 0;
                int c = s;
                boolean endIsMouth = false;
                boolean hitMain = false;
                int inletLakeY = Integer.MIN_VALUE;
                while (true) {
                    buf[len++] = c;
                    final int n = downstream(c);
                    if (n < 0) {
                        WatershedStats.endNowhere.increment();
                        break;
                    }
                    if (sink[n]) {
                        buf[len++] = n;
                        endIsMouth = true;
                        hitMain = mainRiver[n];
                        (hitMain ? WatershedStats.endMainRiver : WatershedStats.endSea).increment();

                        if (!hitMain) len = extendInto(buf, len, c, n, -1);
                        break;
                    }
                    if (lakeId[n] >= 0) {
                        final int into = aimIntoLake(len >= 2 ? buf[len - 2] : -1, c, n);
                        buf[len++] = into;
                        endIsMouth = true;
                        inletLakeY = lakeWaterY.get(lakeId[n]);
                        WatershedStats.endLake.increment();

                        len = extendInto(buf, len, c, into, lakeId[n]);
                        break;
                    }
                    if (!chan[n]) {
                        WatershedStats.endNowhere.increment();
                        break;
                    }
                    if (startsReach(n)) {
                        buf[len++] = n;
                        WatershedStats.endJunction.increment();
                        break;
                    }
                    c = n;
                }
                if (len < 2) continue;

                if (hitMain) len = joinOnContact(buf, len);
                else if (endIsMouth && inletLakeY == Integer.MIN_VALUE) len = seaOnContact(buf, len);

                final boolean isTrueSource = upCount[s] == 0 && !upLake[s] && !inflow[s];

                MainRiverPoint mainAt = null;
                if (hitMain) {
                    final int sc = buf[len - 1];
                    mainAt = nearestMainRiver((cx0 + (sc & W_MASK)) << CS, (cz0 + (sc >> W_SHIFT)) << CS);
                }

                int from = 0;
                while (from < len - 1) {
                    final int to = Math.min(len - 1, from + MAX_REACH_CELLS - 1);
                    final boolean last = to == len - 1;
                    emit(buf, from, to, ownerRegion,
                            from == 0 && isTrueSource,
                            from == 0 && upLake[s],
                            from == 0 && !upLake[s] && upCount[s] >= 2,
                            last && endIsMouth,
                            last && hitMain,
                            last ? inletLakeY : Integer.MIN_VALUE,
                            mainAt, mainAt == null ? 0.0 : chainBlocks(buf, to, len - 1));
                    from = to;
                }
            }
        }

        private void lowerAlongside() {
            final List<WatershedPath> all = new ArrayList<>();
            for (List<WatershedPath> list : tile.pathsByRegion.values()) all.addAll(list);
            final Map<Long, List<WatershedPath>> byMouth = new HashMap<>(), byHead = new HashMap<>();
            for (WatershedPath p : all) {
                if (p.validNodeCount < 2) continue;
                byMouth.computeIfAbsent(nodeKey(p, 0), k -> new ArrayList<>()).add(p);
                byHead.computeIfAbsent(nodeKey(p, p.validNodeCount - 1), k -> new ArrayList<>()).add(p);
            }

            final java.util.ArrayDeque<WatershedPath> work = new java.util.ArrayDeque<>();
            final java.util.IdentityHashMap<WatershedPath, Integer> startAt = new java.util.IdentityHashMap<>();
            for (WatershedPath t : all) {
                if (t.validNodeCount < 2) continue;
                final long mouth = nodeKey(t, 0);
                final List<WatershedPath> out = byHead.get(mouth);
                if (out != null) {
                    final List<WatershedPath> in = byMouth.get(mouth);
                    if (in.size() < 2 || mainInto(in, out.get(0)) == t) continue;
                } else if (!mouthInWater(t, all)) {
                    continue;
                }
                startAt.put(t, t.segmentsY[0]);
                work.add(t);
            }
            final java.util.IdentityHashMap<WatershedPath, int[]> lowered = new java.util.IdentityHashMap<>();
            final java.util.IdentityHashMap<WatershedPath, Integer> doneAt = new java.util.IdentityHashMap<>();
            while (!work.isEmpty()) {
                final WatershedPath t = work.poll();
                final int start = startAt.get(t);
                final Integer done = doneAt.get(t);
                if (done != null && done <= start) continue;
                doneAt.put(t, start);
                final int n = t.validNodeCount;
                final int[] ys = t.segmentsY.clone();
                ys[0] = Math.min(ys[0], start);
                int k = 1;
                for (; k < n; k++) {
                    final int beside = levelAlongside(t, k, all, lowered);
                    if (beside == Integer.MAX_VALUE || t.segmentsY[k] - beside > ALONGSIDE_DROP) break;
                    ys[k] = Math.min(ys[k], Math.max(beside, ys[k - 1]));
                }
                lowered.put(t, ys);
                if (k < n) continue;

                final List<WatershedPath> up = byMouth.get(nodeKey(t, n - 1));
                if (up == null) continue;
                for (WatershedPath u : up) {
                    if (u == t) continue;
                    startAt.merge(u, ys[n - 1], Math::min);
                    work.add(u);
                }
            }
            for (List<WatershedPath> list : tile.pathsByRegion.values()) {
                for (int i = 0; i < list.size(); i++) {
                    final WatershedPath p = list.get(i);
                    final int[] ys = lowered.get(p);
                    if (ys == null || Arrays.equals(ys, p.segmentsY)) continue;
                    list.set(i, new WatershedPath(p.nodeCount, p.validNodeCount, p.hitMainRiver, p.segmentsX, p.segmentsZ,
                            ys, p.segmentsTerrainY, p.segmentWidths));
                    if (!neighbour) WatershedStats.loweredAlongside.increment();
                }
            }
        }

        private static long nodeKey(WatershedPath p, int i) {
            return ((long) Math.round(p.segmentsX[i]) << 32) ^ (Math.round(p.segmentsZ[i]) & 0xFFFFFFFFL);
        }

        private static int levelAlongside(WatershedPath t, int k, List<WatershedPath> all,
                java.util.IdentityHashMap<WatershedPath, int[]> lowered) {
            final double x = t.segmentsX[k], z = t.segmentsZ[k], w = t.segmentWidths[k];
            final double reach = w + MAX_WIDTH + ALONGSIDE_GAP;
            int lowest = Integer.MAX_VALUE;
            for (WatershedPath m : all) {
                if (m == t) continue;
                if (x < m.minX - reach || x > m.maxX + reach || z < m.minZ - reach || z > m.maxZ + reach) continue;
                final WatershedPath.RiverNodeData d = m.getClosestNodeData(x, z);
                if (d.distance >= w + d.width + ALONGSIDE_GAP) continue;
                final int[] down = lowered.get(m);
                final int y = down == null ? d.y
                        : Math.min(down[d.nodeIndex], down[Math.min(d.nodeIndex + 1, m.validNodeCount - 1)]);
                if (y <= t.segmentsY[k]) lowest = Math.min(lowest, y);
            }
            return lowest;
        }

        private static boolean mouthInWater(WatershedPath t, List<WatershedPath> all) {
            final double x = t.segmentsX[0], z = t.segmentsZ[0];
            for (WatershedPath m : all) {
                if (m == t) continue;
                if (x < m.minX - MAX_WIDTH || x > m.maxX + MAX_WIDTH || z < m.minZ - MAX_WIDTH || z > m.maxZ + MAX_WIDTH) continue;
                final WatershedPath.RiverNodeData d = m.getClosestNodeData(x, z);
                if (d.distance < d.width && d.y <= t.segmentsY[0]) return true;
            }
            return false;
        }

        private static WatershedPath mainInto(List<WatershedPath> in, WatershedPath out) {
            final int oh = out.validNodeCount - 1;
            final double ox = out.segmentsX[oh - 1] - out.segmentsX[oh], oz = out.segmentsZ[oh - 1] - out.segmentsZ[oh];
            final double ol = Math.hypot(ox, oz);
            WatershedPath best = null;
            double bestWidth = -1.0, bestAlign = -2.0;
            for (WatershedPath p : in) {
                double width = 0.0;
                for (int i = Math.min(2, p.validNodeCount - 1); i <= Math.min(8, p.validNodeCount - 1); i++) {
                    width = Math.max(width, p.segmentWidths[i]);
                }
                final double ix = p.segmentsX[0] - p.segmentsX[1], iz = p.segmentsZ[0] - p.segmentsZ[1];
                final double il = Math.hypot(ix, iz);
                final double align = il > 1.0E-6 && ol > 1.0E-6 ? (ix * ox + iz * oz) / (il * ol) : 0.0;
                if (width > bestWidth + 0.5 || Math.abs(width - bestWidth) <= 0.5 && align > bestAlign) {
                    best = p;
                    bestWidth = width;
                    bestAlign = align;
                }
            }
            return best;
        }

        private void emit(int[] buf, int from, int to, int ownerRegion, boolean tapersAtSource, boolean startsAtLake,
                boolean opensAtConfluence, boolean endIsMouth,
                boolean hitMain, int inletLakeY, MainRiverPoint mainAt, double mouthOffset) {
            final int m = to - from + 1;
            if (m < 2 || !isCoreRegion(ownerRegion)) return;

            double[] pts = new double[m * 3];
            for (int k = 0; k < m; k++) {
                int c = buf[from + k];
                pts[k * 3] = (cx0 + (c & W_MASK)) << CS;
                pts[k * 3 + 1] = (cz0 + (c >> W_SHIFT)) << CS;
                pts[k * 3 + 2] = k;
            }
            if (hitMain) {
                if (mainAt != null) pts = joinSquare(pts, m, mainAt);
            } else if (endIsMouth) {
                final int lake = inletLakeY == Integer.MIN_VALUE ? -1 : lakeId[buf[to]];
                walkMouthIntoWater(pts, buf[to], lake);
            } else if (upCount[buf[to]] >= 2) {
                final int jc = buf[to], dn = downstream(jc), main = mainUp(jc);
                if (dn >= 0 && main >= 0 && main != buf[to - 1]) turnIntoConfluence(pts, m, buf[to - 1], jc, dn, main);
            }

            pts = spline(pts, SPLINE_STRIDE);
            final double[] course = resample(pts, NODE_SPACING);
            final int nc = course.length / 3;
            if (nc < 2) return;

            final double total = (nc - 1) * NODE_SPACING;
            final double mouthFade = Math.min(MEANDER_MOUTH_FADE, 0.5 * total);
            final double sourceFade = Math.min(MEANDER_SOURCE_FADE, 0.5 * total);

            final boolean joins = !endIsMouth && upCount[buf[to]] >= 2;
            final double[] sx = new double[nc], sz = new double[nc];
            for (int k = 0; k < nc; k++) {
                final double u = Mth.clamp(course[k * 3 + 2], 0.0, m - 1);
                final int i0 = Math.min(m - 2, (int) u);
                final double t = u - i0;
                final double ends = (endIsMouth ? smoothstep((total - k * NODE_SPACING) / mouthFade) : 1.0)
                        * (tapersAtSource ? smoothstep(k * NODE_SPACING / sourceFade) : 1.0);

                final double tj = joins && i0 == m - 2 ? 0.0 : t;
                final double d = sway(course[k * 3], course[k * 3 + 1], buf[from + i0], buf[from + i0 + 1], tj, tj, ends);
                sx[k] = d * swayNormal[0];
                sz[k] = d * swayNormal[1];
            }
            if (joins) {
                final int jc = buf[to], on = downstream(jc);
                final double d = on >= 0 ? sway(course[(nc - 1) * 3], course[(nc - 1) * 3 + 1], jc, on, 0.0, 0.0, 1.0)
                        : sway(course[(nc - 1) * 3], course[(nc - 1) * 3 + 1], buf[to - 1], buf[to], 1.0, 1.0, 1.0);
                final double ex = d * swayNormal[0], ez = d * swayNormal[1];
                final double gap = Math.hypot(ex - sx[nc - 1], ez - sz[nc - 1]);
                final double want = MEANDER_JOIN_BLEND + MEANDER_JOIN_PER_BLOCK * gap;

                final boolean whole = tapersAtSource && want > total;
                final double blend = Math.max(NODE_SPACING, Math.min(want, total));
                for (int k = 0; k < nc; k++) {
                    final double w = whole ? 1.0 : smoothstep(1.0 - (total - k * NODE_SPACING) / blend);
                    final double mx = (ex - sx[k]) * w, mz = (ez - sz[k]) * w;

                    double f = 1.0;
                    final double len = Math.hypot(mx, mz);
                    if (k < nc - 1 && len > 1.0) {
                        final double u = Mth.clamp(course[k * 3 + 2], 0.0, m - 1);
                        final int i0 = Math.min(m - 2, (int) u);
                        final int a = buf[from + i0], b = buf[from + i0 + 1];
                        final double level = Mth.lerp(u - i0, breached[a], breached[b]);
                        final double width = Mth.lerp(u - i0, cellWidth(a), cellWidth(b));
                        final double px = course[k * 3] + sx[k], pz = course[k * 3 + 1] + sz[k];
                        for (double s = 2.0; ; s += 2.0) {
                            final double q = Math.min(1.0, s / len);
                            if (!roomFor(px + mx * q, pz + mz * q, level, width)) {
                                f = (s - 2.0) / len;
                                break;
                            }
                            if (q >= 1.0) break;
                        }
                    }
                    sx[k] += mx * f;
                    sz[k] += mz * f;
                }
            }

            final double[] radius = new double[nc];
            for (int k = 0; k < nc; k++) {
                final double u = Mth.clamp(course[k * 3 + 2], 0.0, m - 1);
                final int i0 = Math.min(m - 2, (int) u);
                radius[k] = cellWidth(u - i0 < 0.5 ? buf[from + i0] : buf[from + i0 + 1]);
            }

            for (int k = 1; k < nc - 1; k++) {
                final double here = Math.hypot(sx[k], sz[k]);
                final double next = Math.max(Math.hypot(sx[k - 1], sz[k - 1]), Math.hypot(sx[k + 1], sz[k + 1]));
                if (here - next > MEANDER_SPIKE) {
                    final double f = next / here;
                    sx[k] *= f;
                    sz[k] *= f;
                }
            }
            untangle(course, sx, sz, radius, nc);

            final double[] nodes = cutNecks(course, sx, sz, radius, nc);
            final int n = nodes.length / 3;
            double[] xs = new double[n], zs = new double[n];
            for (int k = 0; k < n; k++) {
                xs[k] = nodes[k * 3];
                zs[k] = nodes[k * 3 + 1];
            }

            int[] ys = new int[n], tys = new int[n];
            double[] ws = new double[n];

            boolean[] fixed = new boolean[n];

            final boolean[] underNode = new boolean[n];
            float upWater = Float.POSITIVE_INFINITY;

            final float overLevel = !inCore(buf[to]) && inCore(buf[to - 1]) ? toldOver(buf[to - 1], buf[to]) : Float.NaN;
            for (int k = 0; k < n; k++) {
                final double u = Mth.clamp(nodes[k * 3 + 2], 0.0, m - 1);
                final int i0 = Math.min(m - 2, (int) u);
                final double t = u - i0;
                final int c0 = buf[from + i0], c1 = buf[from + i0 + 1];
                final int cc = t < 0.5 ? c0 : c1;

                final double tw = cut[c0] != cut[c1] ? (t < 0.5 ? 0.0 : 1.0) : t;
                final float b1 = c1 == buf[to] && !Float.isNaN(overLevel) ? Math.max(breached[c1], overLevel) : breached[c1];
                final float water = Math.min(upWater, (float) (breached[c0] * (1.0 - tw) + b1 * tw));
                final float terrain = (float) (h[c0] * (1.0 - tw) + h[c1] * tw);
                upWater = water;
                double width = cellWidth(cc);

                boolean inLake = lakeId[cc] >= 0 || lakeShore[cc] >= 0;
                final boolean lake0 = lakeId[c0] >= 0 || lakeShore[c0] >= 0, lake1 = lakeId[c1] >= 0 || lakeShore[c1] >= 0;
                double incision = Mth.lerp(tw, lake0 ? 0 : CHANNEL_INCISION, lake1 ? 0 : CHANNEL_INCISION);

                if (startsAtLake && k < LAKE_SILL_NODES) {
                    incision = Math.floor(incision * k / LAKE_SILL_NODES);
                }
                fixed[n - 1 - k] = inLake || (startsAtLake && k < LAKE_SILL_NODES);
                ys[k] = (int) Math.round(water - incision);
                if (underCells != null && underCells[cc]) underNode[n - 1 - k] = true;

                if (ys[k] < lakeFloorAt[cc]) {
                    ys[k] = lakeFloorAt[cc];
                    fixed[n - 1 - k] = true;
                }

                if (ys[k] < seaLevel - 1) ys[k] = seaLevel - 1;
                tys[k] = Math.round(terrain);
                ws[k] = width;
            }

            reverse(xs);
            reverse(zs);
            reverse(ys);
            reverse(tys);
            reverse(ws);

            if (endIsMouth) {
                if (inletLakeY != Integer.MIN_VALUE) {
                    ys[0] = inletLakeY;
                } else {
                    ys[0] = seaLevel - 1;
                }
            }

            for (int k = 1; k < n - 1; k++) {
                if (tys[k] - tys[k - 1] >= WATERFALL_MIN_DROP) {
                    ys[k] = Math.max(ys[k], Math.min(tys[k] - 1, ys[k + 1]));
                }
            }
            for (int k = 1; k < n; k++) {
                if (ys[k] < ys[k - 1]) ys[k] = ys[k - 1];
            }
            collapseCliffs(ys, tys, fixed);

            if (hitMain) {
                mouthRamp(ys, fixed, nodesInMain(xs, zs), gorgeAtMouth(buf, from, to)
                        ? seaLevel + 1 + (int) HydrologyManager.LOWLAND_FALL_MIN : Integer.MAX_VALUE);
            }

            if (opensAtConfluence) {
                junctionHead(ys, tys, fixed, (int) Math.ceil((cellWidth(buf[from]) + JUNCTION_HEAD_MARGIN) / NODE_SPACING));
            }
            final int headCell = buf[from];

            int[] poolGround = tys;
            for (int k = 0; k < n; k++) {
                if (!underNode[k]) continue;
                if (poolGround == tys) poolGround = tys.clone();
                poolGround[k] = ys[k];
            }
            stepPools(ys, poolGround, fixed, seed ^ ((long) (cx0 + (headCell & W_MASK)) * 341873128712L)
                    ^ ((long) (cz0 + (headCell >> W_SHIFT)) * 132897987541L) ^ 0x5DEECE66DL, waterfalls,
                    opensAtConfluence);

            double[] sm = new double[n];
            for (int k = 0; k < n; k++) {
                double a = ws[Math.max(0, k - 1)], b = ws[k], c = ws[Math.min(n - 1, k + 1)];
                sm[k] = (a + b + c) / 3.0;
            }
            for (int k = n - 2; k >= 0; k--) {
                if (sm[k] < sm[k + 1]) sm[k] = sm[k + 1];
            }

            if (tapersAtSource) {
                final double taper = Math.min(SOURCE_TAPER, 0.5 * (n - 1) * NODE_SPACING);
                for (int k = n - 1; k >= 0; k--) {
                    final double s = (n - 1 - k) * NODE_SPACING;
                    if (s >= taper) break;
                    sm[k] *= Math.pow(s / taper, SOURCE_TIP_POWER);
                }
            }

            if (mainAt != null) {
                final double cap0 = MAIN_JOIN_WIDTH * HydrologyManager.normalRiverWaterRadius(mainAt.width());
                for (int k = 0; k < n; k++) {
                    final double cap = cap0 * (1.0 + (mouthOffset + k * NODE_SPACING) / MAIN_JOIN_TAPER);
                    if (sm[k] > cap) sm[k] = cap;
                }
            }

            WatershedPath path = new WatershedPath(n, n, endIsMouth && hitMain, xs, zs, ys, tys, sm);
            tile.pathsByRegion.get(regionKeyOf(ownerRegion)).add(path);
            WatershedStats.pathsEmitted.increment();
            for (int k = 0; k < n; k++) {
                final int gorge = tys[k] - ys[k];
                if (gorge > GORGE_STAT_DEPTH) {
                    WatershedStats.gorgeNodes.increment();
                    WatershedStats.maxGorge.accumulateAndGet(gorge, Math::max);
                }
            }
        }

        private double coreEdgeDistance(double x, double z) {
            final double x0 = (cx0 + HALO) << CS, x1 = (cx0 + HALO + CORE) << CS;
            final double z0 = (cz0 + HALO) << CS, z1 = (cz0 + HALO + CORE) << CS;
            return Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
        }

        private void walkMouthIntoWater(double[] pts, int mouthCell, int lake) {
            final int m = pts.length / 3;
            if (m < 2) return;
            final int last = (m - 1) * 3;
            final double x0 = pts[last], z0 = pts[last + 1];
            double dx = x0 - pts[last - 3], dz = z0 - pts[last - 2];
            final double dl = Math.hypot(dx, dz);
            if (dl < 1.0E-6) return;
            dx /= dl;
            dz /= dl;

            final double keep = lake >= 0 ? cellWidth(mouthCell) + 2.0 : 0.0;
            final double edge0 = coreEdgeDistance(x0, z0);
            double firstWet = Double.NaN, lastWet = Double.NaN;
            for (int s = 0; s <= MOUTH_WALK_BLOCKS + 2 * MOUTH_OVERSHOOT_BLOCKS; s++) {
                final double x = x0 + dx * s, z = z0 + dz * s;
                if (s > 0 && keep > 0.0 && coreEdgeDistance(x, z) < Math.min(keep, edge0)) break;
                final boolean wet = lake >= 0
                        ? lakeWaterAt(x, z, lake)
                        : CoarseHeightField.surfaceAt(Mth.floor(x), Mth.floor(z), generator, randomState) < seaLevel;
                if (wet) {
                    if (Double.isNaN(firstWet)) firstWet = s;
                    lastWet = s;
                    if (s - firstWet >= 2 * MOUTH_OVERSHOOT_BLOCKS) break;
                } else if (!Double.isNaN(firstWet) || s >= MOUTH_WALK_BLOCKS) {
                    break;
                }
            }
            if (!Double.isNaN(firstWet)) {
                final double in = firstWet + Math.min(MOUTH_OVERSHOOT_BLOCKS, (lastWet - firstWet) / 2.0);
                pts[last] = x0 + dx * in;
                pts[last + 1] = z0 + dz * in;
                WatershedStats.mouthsWalked.increment();
            } else {
                pts[last] = (cx0 + (mouthCell & W_MASK)) << CS;
                pts[last + 1] = (cz0 + (mouthCell >> W_SHIFT)) << CS;
                WatershedStats.mouthsFellBack.increment();
            }
        }

        private boolean lakeWaterAt(double x, double z, int lake) {
            if (lake < packaged.length && packaged[lake] != null) return packaged[lake].inside(x, z);
            final int ci = (Mth.floor(x) >> CS) - cx0, cj = (Mth.floor(z) >> CS) - cz0;
            if (ci < 0 || cj < 0 || ci >= W || cj >= W) return false;
            return lakeId[(cj << W_SHIFT) | ci] == lake;
        }

        private static MainRiverPoint nearestMainRiver(double x, double z) {
            List<RiverGraphEdge> edges = MainRivers.getNearbyRivers(x, z);
            if (edges == null || edges.isEmpty()) return null;
            double bestOut = Double.MAX_VALUE, bx = x, bz = z, dx = 0.0, dz = 0.0, bestR = 0.0;
            RiverGraphEdge best = null;
            for (RiverGraphEdge edge : edges) {
                final double maxR = edge.maxWaterRadius();
                double[] s = edge.fractal().segments;
                for (int k = 0; k + 3 < s.length; k += 2) {
                    double t = RiverMathUtils.projectAlongLine(s[k], s[k + 1], s[k + 2], s[k + 3], x, z);
                    double px = s[k] + t * (s[k + 2] - s[k]);
                    double pz = s[k + 1] + t * (s[k + 3] - s[k + 1]);
                    final double d = Math.hypot(px - x, pz - z);
                    if (d - maxR >= bestOut) continue;
                    final double r = HydrologyManager.routeWaterRadius(edge, px, pz);
                    if (d - r < bestOut) {
                        bestOut = d - r;
                        bestR = r;
                        bx = px;
                        bz = pz;
                        dx = s[k + 2] - s[k];
                        dz = s[k + 3] - s[k + 1];
                        best = edge;
                    }
                }
            }
            if (best == null) return null;
            final double width = best.widthAt(bx, bz);
            final double dl = Math.hypot(dx, dz);
            return dl < 1.0E-9 ? new MainRiverPoint(bx, bz, 0.0, 0.0, width, bestR)
                    : new MainRiverPoint(bx, bz, dx / dl, dz / dl, width, bestR);
        }

        private int joinOnContact(int[] buf, int len) {
            if (ground0 == null) return len;
            for (int i = Math.max(0, len - 1 - JOIN_SCAN_CELLS); i < len - 2; i++) {
                final int c = buf[i];
                if (lakeId[c] >= 0) continue;
                final double x = (cx0 + (c & W_MASK)) << CS, z = (cz0 + (c >> W_SHIFT)) << CS;
                final MainRiverPoint p = nearestMainRiver(x, z);
                if (p == null) continue;
                final double toWater = Math.hypot(x - p.x(), z - p.z()) - p.waterRadius();
                if (toWater >= MAIN_JOIN_CONTACT * cellWidth(c) + MAIN_JOIN_CONTACT_MARGIN) continue;
                if (HydrologyManager.gorgeShare(groundAt(ground0, p.x(), p.z())) >= 0.5) return len;
                buf[i + 1] = buf[len - 1];
                return i + 2;
            }
            return len;
        }

        private int seaOnContact(int[] buf, int len) {
            int ground = len;
            for (int k = 0; k < len; k++) {
                if (sink[buf[k]]) {
                    ground = k;
                    break;
                }
            }
            for (int i = Math.max(0, ground - 1 - JOIN_SCAN_CELLS); i < ground - 1; i++) {
                final int c = buf[i];
                if (lakeId[c] >= 0 || lakeShore[c] >= 0) continue;

                final double contact = MAIN_JOIN_CONTACT * cellWidth(c) + MAIN_JOIN_CONTACT_MARGIN + 0.5 * CELL;
                final int r = (int) Math.ceil(contact / CELL);
                final int ci = c & W_MASK, cj = c >> W_SHIFT;
                int sea = -1;
                double nearest = contact;
                for (int dj = -r; dj <= r; dj++) {
                    for (int di = -r; di <= r; di++) {
                        final int ni = ci + di, nj = cj + dj;

                        if (ni < 0 || nj < 0 || ni >= W || nj >= W || (di == 0 && dj == 0)) continue;
                        final int nc = (nj << W_SHIFT) | ni;
                        if (mainRiver[nc] || !sink[nc] && !(h[nc] < seaLevel - 0.5f && byTheSea(nc))) continue;
                        final double d = Math.hypot(di, dj) * CELL;
                        if (d < nearest) {
                            nearest = d;
                            sea = nc;
                        }
                    }
                }
                if (sea < 0) continue;
                final int si = sea & W_MASK, sj = sea >> W_SHIFT;
                final int steps = Math.max(Math.abs(si - ci), Math.abs(sj - cj));
                final float top = breached[c] + SEA_JOIN_RISE;
                boolean clear = true;
                for (int s = 1; s < steps && clear; s++) {
                    final int lc = lineCell(ci, cj, si, sj, s, steps);
                    clear = !sink[lc] && lakeId[lc] < 0 && h[lc] <= top;
                }
                if (!clear) continue;
                int n = i + 1;
                for (int s = 1; s <= steps; s++) buf[n++] = lineCell(ci, cj, si, sj, s, steps);
                return extendInto(buf, n, buf[n - 2], buf[n - 1], -1);
            }
            return len;
        }

        private boolean byTheSea(int c) {
            final int ci = c & W_MASK, cj = c >> W_SHIFT;
            for (int dj = -SEA_SHORE_CELLS; dj <= SEA_SHORE_CELLS; dj++) {
                for (int di = -SEA_SHORE_CELLS; di <= SEA_SHORE_CELLS; di++) {
                    final int ni = ci + di, nj = cj + dj;
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    final int nc = (nj << W_SHIFT) | ni;
                    if (sink[nc] && !mainRiver[nc]) return true;
                }
            }
            return false;
        }

        private static int lineCell(int ci, int cj, int si, int sj, int s, int steps) {
            final int i = ci + (int) Math.round((si - ci) * (double) s / steps);
            final int j = cj + (int) Math.round((sj - cj) * (double) s / steps);
            return (j << W_SHIFT) | i;
        }

        private static double[] joinSquare(double[] pts, int m, MainRiverPoint sink) {
            final int last = (m - 1) * 3, bank = (m - 2) * 3;

            final MainRiverPoint near = nearestMainRiver(pts[bank], pts[bank + 1]);
            final MainRiverPoint at = near != null ? near : sink;
            double nx = pts[bank] - at.x(), nz = pts[bank + 1] - at.z();
            final double nl = Math.hypot(nx, nz);
            if (nl < 1.0E-6) {
                pts[last] = at.x();
                pts[last + 1] = at.z();
                return pts;
            }
            nx /= nl;
            nz /= nl;
            final double r = HydrologyManager.normalRiverWaterRadius(at.width());

            final double in = Math.min(MAIN_JOIN_IN * r, 0.5 * nl);
            final double[] out = Arrays.copyOf(pts, (m + 1) * 3);
            out[last] = at.x() + nx * in;
            out[last + 1] = at.z() + nz * in;
            out[last + 2] = m - 1;
            out[last + 3] = at.x();
            out[last + 4] = at.z();
            out[last + 5] = m - 1;
            return out;
        }

        private static double chainBlocks(int[] buf, int from, int to) {
            double blocks = 0.0;
            for (int k = from; k < to; k++) {
                final int a = buf[k], b = buf[k + 1];
                final boolean diagonal = (a & W_MASK) != (b & W_MASK) && (a >> W_SHIFT) != (b >> W_SHIFT);
                blocks += diagonal ? CELL * 1.41421356 : CELL;
            }
            return blocks;
        }
    }

    private record MainRiverPoint(double x, double z, double dirX, double dirZ, double width, double waterRadius) {
    }

    static void untangle(double[] nodes, double[] sx, double[] sz, double[] radius, int n) {
        if (n < 2 * MEANDER_NECK_EDGE + 2) return;
        final double[] arc = new double[n];
        final double[] near = new double[n];
        for (int round = 0; round < MEANDER_NECK_ROUNDS; round++) {
            for (int k = 1; k < n; k++) {
                arc[k] = arc[k - 1] + Math.hypot(nodes[k * 3] + sx[k] - nodes[(k - 1) * 3] - sx[k - 1],
                        nodes[k * 3 + 1] + sz[k] - nodes[(k - 1) * 3 + 1] - sz[k - 1]);
            }

            Arrays.fill(near, Double.MAX_VALUE);
            boolean any = false;
            for (int i = 0; i < n; i++) {
                final double xi = nodes[i * 3] + sx[i], zi = nodes[i * 3 + 1] + sz[i];
                for (int j = i + 1; j < n; j++) {
                    final double reach = radius[i] + radius[j];
                    final double keep = reach + Math.max(MEANDER_NECK_MIN, MEANDER_NECK_SHARE * reach);
                    if (arc[j] - arc[i] < MEANDER_NECK_ARC * keep) continue;
                    final double dx = nodes[j * 3] + sx[j] - xi, dz = nodes[j * 3 + 1] + sz[j] - zi;
                    if (dx * dx + dz * dz >= keep * keep) continue;
                    any = true;
                    for (int k = i; k <= j; k++) near[k] = 0.0;
                }
            }
            if (!any) return;
            for (int k = 1; k < n; k++) near[k] = Math.min(near[k], near[k - 1] + 1.0);
            for (int k = n - 2; k >= 0; k--) near[k] = Math.min(near[k], near[k + 1] + 1.0);
            for (int k = 0; k < n; k++) {
                final double w = smoothstep(1.0 - near[k] / MEANDER_NECK_EDGE)
                        * smoothstep(Math.min(k, n - 1 - k) / (double) MEANDER_NECK_EDGE);
                if (w <= 0.0) continue;
                final double f = 1.0 - (1.0 - MEANDER_NECK_SCALE) * w;
                sx[k] *= f;
                sz[k] *= f;
            }
        }
    }

    static double[] cutNecks(double[] course, double[] sx, double[] sz, double[] radius, int n) {
        double[] p = new double[n * 3];
        double[] r = Arrays.copyOf(radius, n);
        for (int k = 0; k < n; k++) {
            p[k * 3] = course[k * 3] + sx[k];
            p[k * 3 + 1] = course[k * 3 + 1] + sz[k];
            p[k * 3 + 2] = course[k * 3 + 2];
        }
        int count = n;
        boolean changed = false;

        if (count >= 2 * NECK_EDGE + 3) {
            final double[] arc = new double[count];
            for (int k = 1; k < count; k++) {
                arc[k] = arc[k - 1] + Math.hypot(p[k * 3] - p[(k - 1) * 3], p[k * 3 + 1] - p[(k - 1) * 3 + 1]);
            }
            double[] q = new double[(count + 8) * 3];
            double[] qr = new double[count + 8];
            int o = 0;
            for (int i = 0; i < count; ) {
                int cut = -1;
                if (i >= NECK_EDGE) {
                    final double xi = p[i * 3], zi = p[i * 3 + 1];
                    for (int j = i + 2; j < count - NECK_EDGE; j++) {
                        final double gap = Math.hypot(p[j * 3] - xi, p[j * 3 + 1] - zi);
                        if (gap < NECK_TOUCH * (r[i] + r[j]) && arc[j] - arc[i] > gap + NECK_DETOUR * Math.max(r[i], r[j])) {
                            cut = j;
                        }
                    }
                }
                final int steps = cut < 0 ? 1 : Math.max(1, (int) Math.round(
                        Math.hypot(p[cut * 3] - p[i * 3], p[cut * 3 + 1] - p[i * 3 + 1]) / NODE_SPACING));
                if (o + steps > qr.length) {
                    q = Arrays.copyOf(q, (qr.length + steps + 8) * 6);
                    qr = Arrays.copyOf(qr, (qr.length + steps + 8) * 2);
                }

                for (int s = 0; s < steps; s++) {
                    final double t = (double) s / steps;
                    for (int c = 0; c < 3; c++) {
                        q[o * 3 + c] = cut < 0 ? p[i * 3 + c] : p[i * 3 + c] + t * (p[cut * 3 + c] - p[i * 3 + c]);
                    }
                    qr[o++] = cut < 0 ? r[i] : r[i] + t * (r[cut] - r[i]);
                }
                if (cut < 0) {
                    i++;
                } else {
                    i = cut;
                    changed = true;
                    WatershedStats.necksCut.increment();
                }
            }
            if (changed) {
                p = Arrays.copyOf(q, o * 3);
                r = Arrays.copyOf(qr, o);
                count = o;
            }
        }

        for (int round = 0; round < BEND_EASE_ROUNDS && count >= 2 * NECK_EDGE + 3; round++) {
            final double[] arc = new double[count];
            for (int k = 1; k < count; k++) {
                arc[k] = arc[k - 1] + Math.hypot(p[k * 3] - p[(k - 1) * 3], p[k * 3 + 1] - p[(k - 1) * 3 + 1]);
            }
            double[] e = null;
            for (int k = NECK_EDGE + 1; k < count - NECK_EDGE - 1; k++) {
                final double span = Math.max(BEND_SPAN, BEND_SPAN_WIDTHS * r[k]);
                int a = k, c = k;
                while (a > 0 && arc[k] - arc[a] < span) a--;
                while (c + 1 < count && arc[c] - arc[k] < span) c++;
                if (arc[k] - arc[a] < span || arc[c] - arc[k] < span) continue;
                final double bend = circumradius(p, a, k, c), want = Math.max(BEND_MIN_RADIUS, BEND_FOLD * r[k]);
                if (bend >= want) continue;
                if (e == null) e = p.clone();

                final double f = Math.min(0.5, 1.0 - bend / want);
                e[k * 3] = p[k * 3] + f * (0.5 * (p[a * 3] + p[c * 3]) - p[k * 3]);
                e[k * 3 + 1] = p[k * 3 + 1] + f * (0.5 * (p[a * 3 + 1] + p[c * 3 + 1]) - p[k * 3 + 1]);
            }
            if (e == null) break;
            p = e;
            if (round == 0) WatershedStats.foldsEased.increment();
        }

        return p;
    }

    private static double circumradius(double[] p, int a, int b, int c) {
        final double ax = p[a * 3], az = p[a * 3 + 1], bx = p[b * 3], bz = p[b * 3 + 1], cx = p[c * 3], cz = p[c * 3 + 1];
        final double cross = (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
        if (Math.abs(cross) < 1.0E-9) return Double.POSITIVE_INFINITY;
        return Math.hypot(bx - ax, bz - az) * Math.hypot(cx - bx, cz - bz) * Math.hypot(ax - cx, az - cz)
                / (2.0 * Math.abs(cross));
    }

    static double[] spline(double[] pts, int stride) {
        final int n = pts.length / 3;
        if (n < 3) return pts;
        final int[] idx = new int[n];
        int c = 0;
        for (int k = 0; k < n - 2; k += stride) idx[c++] = k;
        idx[c++] = n - 2;
        idx[c++] = n - 1;
        final double[] q = new double[2];
        double[] out = new double[Math.max(16, n * 24)];
        int o = 0;
        for (int i = 0; i + 1 < c; i++) {
            final int a = idx[i], b = idx[i + 1];
            final double x1 = pts[a * 3], z1 = pts[a * 3 + 1], x2 = pts[b * 3], z2 = pts[b * 3 + 1];
            final double x0 = i > 0 ? pts[idx[i - 1] * 3] : 2 * x1 - x2, z0 = i > 0 ? pts[idx[i - 1] * 3 + 1] : 2 * z1 - z2;
            final double x3 = i + 2 < c ? pts[idx[i + 2] * 3] : 2 * x2 - x1;
            final double z3 = i + 2 < c ? pts[idx[i + 2] * 3 + 1] : 2 * z2 - z1;
            final int steps = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1) / 1.5));
            for (int j = 0; j < steps; j++) {
                final double t = (double) j / steps;
                catmullRom(x0, z0, x1, z1, x2, z2, x3, z3, t, q);
                if (o + 3 > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[o++] = q[0];
                out[o++] = q[1];
                out[o++] = pts[a * 3 + 2] + t * (pts[b * 3 + 2] - pts[a * 3 + 2]);
            }
        }
        if (o + 3 > out.length) out = Arrays.copyOf(out, out.length + 3);
        out[o++] = pts[(n - 1) * 3];
        out[o++] = pts[(n - 1) * 3 + 1];
        out[o++] = pts[(n - 1) * 3 + 2];
        return Arrays.copyOf(out, o);
    }

    private static void catmullRom(double x0, double z0, double x1, double z1, double x2, double z2, double x3, double z3,
            double t, double[] out) {
        final double t0 = 0.0;
        final double t1 = t0 + Math.max(1.0E-4, Math.sqrt(Math.hypot(x1 - x0, z1 - z0)));
        final double t2 = t1 + Math.max(1.0E-4, Math.sqrt(Math.hypot(x2 - x1, z2 - z1)));
        final double t3 = t2 + Math.max(1.0E-4, Math.sqrt(Math.hypot(x3 - x2, z3 - z2)));
        final double u = t1 + t * (t2 - t1);
        final double a1x = ((t1 - u) * x0 + (u - t0) * x1) / (t1 - t0), a1z = ((t1 - u) * z0 + (u - t0) * z1) / (t1 - t0);
        final double a2x = ((t2 - u) * x1 + (u - t1) * x2) / (t2 - t1), a2z = ((t2 - u) * z1 + (u - t1) * z2) / (t2 - t1);
        final double a3x = ((t3 - u) * x2 + (u - t2) * x3) / (t3 - t2), a3z = ((t3 - u) * z2 + (u - t2) * z3) / (t3 - t2);
        final double b1x = ((t2 - u) * a1x + (u - t0) * a2x) / (t2 - t0), b1z = ((t2 - u) * a1z + (u - t0) * a2z) / (t2 - t0);
        final double b2x = ((t3 - u) * a2x + (u - t1) * a3x) / (t3 - t1), b2z = ((t3 - u) * a2z + (u - t1) * a3z) / (t3 - t1);
        out[0] = ((t2 - u) * b1x + (u - t1) * b2x) / (t2 - t1);
        out[1] = ((t2 - u) * b1z + (u - t1) * b2z) / (t2 - t1);
    }

    private static double smoothstep(double t) {
        final double c = Mth.clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    static double[] resample(double[] pts, double spacing) {
        final int n = pts.length / 3;
        if (n < 2) return pts;
        double[] cum = new double[n];
        for (int k = 1; k < n; k++) {
            double dx = pts[k * 3] - pts[(k - 1) * 3], dz = pts[k * 3 + 1] - pts[(k - 1) * 3 + 1];
            cum[k] = cum[k - 1] + Math.sqrt(dx * dx + dz * dz);
        }
        final double total = cum[n - 1];
        final int count = Math.max(2, (int) Math.round(total / spacing) + 1);
        final double step = total / (count - 1);
        double[] out = new double[count * 3];
        int seg = 0;
        for (int k = 0; k < count; k++) {
            double s = Math.min(total, k * step);
            while (seg < n - 2 && cum[seg + 1] < s) seg++;
            double segLen = cum[seg + 1] - cum[seg];
            double t = segLen > 1e-9 ? (s - cum[seg]) / segLen : 0.0;
            for (int c = 0; c < 3; c++) {
                out[k * 3 + c] = pts[seg * 3 + c] + t * (pts[seg * 3 + 3 + c] - pts[seg * 3 + c]);
            }
        }
        return out;
    }

    private static void reverse(double[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            double t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static void reverse(int[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    static void collapseCliffs(int[] ys, int[] tys, boolean[] fixed) {
        final int n = ys.length;
        int k = 0;
        while (k + 2 < n) {
            if (!WatershedPath.isFall(ys, k) || !WatershedPath.isFall(ys, k + 1)) {
                k++;
                continue;
            }
            final int foot = ys[k];
            int i = k + 1;
            while (i + 1 < n && WatershedPath.isFall(ys, i) && !fixed[i] && tys[i] - foot <= CLIFF_SLOT_DEPTH) {
                ys[i] = foot;
                tys[i] = foot + 1;
                i++;
            }
            k = i;
        }
    }

    static void junctionHead(int[] ys, int[] tys, boolean[] fixed, int nodes) {
        final int n = ys.length, head = n - 1, base = ys[head];
        if (n < 3) return;
        if (!fixed[head - 1] && tys[head - 1] >= base + JUNCTION_HEAD_GROUND) {
            for (int k = head - 1; k >= Math.max(1, head - nodes); k--) {
                if (fixed[k] || tys[k] < base + JUNCTION_HEAD_GROUND) return;
                ys[k] = base;
            }
            return;
        }
        int best = -1;
        for (int k = head - STEP_MIN_POOL_NODES; k >= 1 && head - k <= STEP_MAX_POOL_NODES; k--) {
            boolean holds = true;
            for (int i = k + 1; i < head && holds; i++) {
                holds = !fixed[i] && ys[i] - ys[k] <= STEP_MAX_HOLD && tys[i] - ys[k] <= STEP_MAX_GORGE;
            }
            if (!holds) break;
            if (base - ys[k] >= STEP_MIN_FALL) best = k;
        }
        if (best < 0) return;
        for (int i = best + 1; i < head; i++) ys[i] = ys[best];
    }

    static void stepPools(int[] ys, int[] tys, boolean[] fixed, long seed, Config.WaterfallLevel level, boolean openHead) {
        if (level == Config.WaterfallLevel.CLIFFS) return;
        final int n = ys.length;

        final int top = openHead ? n - 1 : n - STEP_END_NODES;
        final int window = stepWindow(level);
        int from = STEP_END_NODES, falls = 0;
        int k = STEP_END_NODES;
        while (k < top) {
            if (WatershedPath.isFall(ys, k)) {
                from = Math.max(from, k + 1 + stepGap(seed, falls++, level));
                k++;
                continue;
            }
            if (k < from) {
                k++;
                continue;
            }

            int bestK = -1, bestJ = -1, bestStep = -1;
            for (int b = k; b < Math.min(top - 1, k + window); b++) {
                if (b > k && WatershedPath.isFall(ys, b)) break;
                final int j = poolHead(ys, tys, fixed, b, top);
                if (j < 0) continue;

                boolean lowers = false;
                for (int i = b + 1; i < j && !lowers; i++) lowers = ys[i] > ys[b];
                if (lowers && ys[j] - ys[j - 1] > bestStep) {
                    bestStep = ys[j] - ys[j - 1];
                    bestK = b;
                    bestJ = j;
                }
            }
            if (bestJ < 0) {
                k++;
                continue;
            }
            for (int i = bestK + 1; i < bestJ; i++) {
                ys[i] = ys[bestK];
            }
            from = bestJ + stepGap(seed, falls++, level);
            k = bestJ;
        }
    }

    private static int poolHead(int[] ys, int[] tys, boolean[] fixed, int k, int top) {
        final int base = ys[k];
        for (int j = k + 1; j <= top && j - k <= STEP_MAX_POOL_NODES; j++) {
            final int rise = ys[j] - base;
            final boolean pooled = j - k >= STEP_MIN_POOL_NODES;
            if (rise > STEP_MAX_HOLD || tys[j] - base > STEP_MAX_GORGE) {
                return pooled && rise >= STEP_MIN_FALL ? j : -1;
            }
            if (pooled && rise >= STEP_MIN_FALL) return j;
            if (fixed[j]) return -1;
        }
        return -1;
    }

    private static int stepWindow(Config.WaterfallLevel level) {
        return switch (level) {
            case FEW -> 6;
            case NORMAL -> 3;
            default -> 1;
        };
    }

    private static int stepGap(long seed, int i, Config.WaterfallLevel level) {
        final int min, jitter;
        switch (level) {
            case FEW -> {
                min = 14;
                jitter = 10;
            }
            case NORMAL -> {
                min = 4;
                jitter = 4;
            }
            default -> {
                return 0;
            }
        }
        final long h = it.unimi.dsi.fastutil.HashCommon.mix(seed + i * 0x9E3779B97F4A7C15L);
        return min + (int) Math.floorMod(h, (long) (jitter + 1));
    }
}
