package com.AdamNew.dynamicwaters;

import net.minecraft.world.level.LevelReader;
import com.AdamNew.dynamicwaters.terrain.BasinTile;
import com.AdamNew.dynamicwaters.terrain.ChannelForm;
import com.AdamNew.dynamicwaters.terrain.CoarseHeightField;
import com.AdamNew.dynamicwaters.terrain.WatershedLake;
import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import net.minecraft.util.Mth;

public class HydrologyManager {
    private static net.minecraft.world.level.chunk.ChunkGenerator globalGenerator;
    private static net.minecraft.world.level.levelgen.RandomState globalRandomState;
    private static final net.minecraft.world.level.LevelHeightAccessor dummyAccessor = net.minecraft.world.level.LevelHeightAccessor
            .create(-64, 384);

    private static final ThreadLocal<Boolean> IS_GENERATING_RIVERS = ThreadLocal.withInitial(() -> false);

    public static boolean isGeneratingRivers() {
        return IS_GENERATING_RIVERS.get();
    }

    public static void setGeneratingRivers(boolean val) {
        IS_GENERATING_RIVERS.set(val);
    }

    public static net.minecraft.world.level.chunk.ChunkGenerator getGlobalGenerator() {
        return globalGenerator;
    }

    public static net.minecraft.world.level.levelgen.RandomState getGlobalRandomState() {
        return globalRandomState;
    }

    public static void setGlobalGenerator(net.minecraft.world.level.chunk.ChunkGenerator gen,
            net.minecraft.world.level.levelgen.RandomState rs) {
        globalGenerator = gen;
        globalRandomState = rs;
    }

    public static boolean riversIn(net.minecraft.world.level.Level level) {
        return level != null ? level.dimension() == net.minecraft.world.level.Level.OVERWORLD
                : drawnLevelRivers.getAsBoolean();
    }

    private static volatile java.util.function.BooleanSupplier drawnLevelRivers = () -> true;

    public static void setDrawnLevelRivers(java.util.function.BooleanSupplier rivers) {
        drawnLevelRivers = rivers;
    }

    public static void resetForServer() {
        com.AdamNew.dynamicwaters.compat.OneDimension.reset();
        globalGenerator = null;
        globalRandomState = null;
        riverBiomeHolder = null;
        clearCaches();
        MainRivers.clearCaches();
    }

    public static net.minecraft.world.level.LevelHeightAccessor getDummyAccessor() {
        return dummyAccessor;
    }

    public static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> riverBiomeHolder;

    public static final int REGION_SIZE = 2048;

    public static final java.util.concurrent.ConcurrentHashMap<Long, WatershedRegionData> WATERSHED_REGION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public static final int WATERSHED_REGION_SIZE = 256;

    private static final int WATERSHED_CACHE_LIMIT = 4096;
    private static final java.util.concurrent.atomic.AtomicLong WATERSHED_CLOCK = new java.util.concurrent.atomic.AtomicLong();
    private static final Object EVICTION_LOCK = new Object();

    private static final java.util.concurrent.atomic.AtomicLong REGION_GENERATION = new java.util.concurrent.atomic.AtomicLong();

    public static final int WATERSHED_SCAN_RADIUS = 1;

    private static final int LAKE_SHORE_LIP = 3;

    private static final double LAKE_LIP_RIVER_GAP = 4.0;

    private static final int LAKE_SHORE_CUT = 12;

    private static final int LAKE_DEPTH = 8;

    private static final double NORMAL_RIVER_WIDTH_SCALE = 1.2;
    private static final double NORMAL_RIVER_WIDTH_EXTRA = 2.0;

    private static final double NORMAL_RIVER_DEPTH = 7.0;

    public static double normalRiverWaterRadius(double networkWidth) {
        return networkWidth * NORMAL_RIVER_WIDTH_SCALE + NORMAL_RIVER_WIDTH_EXTRA;
    }

    public static double normalRiverSkinRadius(double networkWidth) {
        final double r = normalRiverWaterRadius(networkWidth);
        return r + bankMeet(r) + 0.15 * r + 1.0;
    }

    private static final double GORGE_NARROW_FROM = 8.0;
    private static final double GORGE_NARROW_TO = 40.0;
    private static final double GORGE_NARROW_MIN = 0.6;

    private static final double STREAM_DEPTH_RATIO = 0.48;

    private static final int MAX_BOWL = 6;

    private static final double TUNNEL_FROM_DEPTH = 8.0;

    private static final int TUNNEL_WAIST = 3;

    private static final int TUNNEL_LOOK_NODES = 8;
    private static final int TUNNEL_CRUST = 3;

    private static final double PORTAL_CRUST = 8.0;
    private static final double PORTAL_FLARE = 5.0;

    private static final double CAVE_SWELL = 2.0;
    private static final double CAVE_ROUGH = 1.0;

    private static final int JUNCTION_CHAMBER = 2;

    private static final double BANK_ZONE_PER_WIDTH = 2.0;
    private static final double BANK_ZONE_MIN = 5.0;

    private static final double BANK_MEET_PER_WIDTH = 1.5;
    private static final double BANK_MEET_MIN = 5.0;

    private static final double BANK_TALUS = 1.0;
    private static final double LEVEE_TALUS = 0.5;

    private static final double LEVEE_CREST = 2.0;
    private static final double LEVEE_SHOULDER = 3.0;

    private static double bankMeet(double halfWidth) {
        return Math.max(BANK_MEET_MIN, BANK_MEET_PER_WIDTH * halfWidth);
    }

    private static int sealDepth(double dOut, double halfWidth) {
        final int deep = Math.max(SEAL_MIN, (int) Math.round(SEAL_PER_WIDTH * halfWidth));
        final double t = Math.min(1.0, Math.max(0.0, dOut) / bankMeet(halfWidth));
        return (int) Math.round(deep - (deep - 1) * t);
    }

    private static int sealJitter(double x, double z) {
        return (int) Math.round(RIVER_BED_NOISE.get(x * 0.4, z * 0.4) * 3.5);
    }

    public static boolean holdsWater(RiverCarving c) {
        return c != null && c.modifies && !c.isWatershedWall && c.waterLevel > c.targetY;
    }

    public static net.minecraft.world.level.block.state.BlockState holdFill(net.minecraft.world.level.BlockGetter getter,
            net.minecraft.core.BlockPos.MutableBlockPos at) {
        final int y = at.getY();
        final net.minecraft.world.level.block.state.BlockState rock = y < 0
                ? net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()
                : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int up = 1; up <= 4; up++) {
            final net.minecraft.world.level.block.state.BlockState s = getter.getBlockState(at.setY(y + up));
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            at.setY(y);
            if (s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || s.is(net.minecraft.world.level.block.Blocks.MYCELIUM)
                    || s.is(net.minecraft.world.level.block.Blocks.PODZOL) || s.is(net.minecraft.world.level.block.Blocks.DIRT)
                    || s.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT) || s.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT)) {
                return net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            }
            if (s.is(net.minecraft.world.level.block.Blocks.SAND) || s.is(net.minecraft.world.level.block.Blocks.SANDSTONE)) {
                return net.minecraft.world.level.block.Blocks.SANDSTONE.defaultBlockState();
            }
            if (s.is(net.minecraft.world.level.block.Blocks.RED_SAND) || s.is(net.minecraft.world.level.block.Blocks.RED_SANDSTONE)) {
                return net.minecraft.world.level.block.Blocks.RED_SANDSTONE.defaultBlockState();
            }
            if (s.is(net.minecraft.world.level.block.Blocks.DEEPSLATE) || s.is(net.minecraft.world.level.block.Blocks.TUFF)
                    || s.is(net.minecraft.world.level.block.Blocks.ANDESITE) || s.is(net.minecraft.world.level.block.Blocks.DIORITE)
                    || s.is(net.minecraft.world.level.block.Blocks.GRANITE) || s.is(net.minecraft.world.level.block.Blocks.CALCITE)
                    || s.is(net.minecraft.world.level.block.Blocks.CLAY) || s.is(net.minecraft.world.level.block.Blocks.MUD)) {
                return s;
            }
            return rock;
        }

        net.minecraft.world.level.block.state.BlockState under = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int down = 1; down <= 8; down++) {
            under = getter.getBlockState(at.setY(y - down));
            if (!under.isAir() && under.getFluidState().isEmpty()) break;
        }
        at.setY(y);
        if (under.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || under.is(net.minecraft.world.level.block.Blocks.DIRT)
                || under.is(net.minecraft.world.level.block.Blocks.COARSE_DIRT) || under.is(net.minecraft.world.level.block.Blocks.PODZOL)
                || under.is(net.minecraft.world.level.block.Blocks.ROOTED_DIRT) || under.is(net.minecraft.world.level.block.Blocks.MYCELIUM)) {
            return net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (under.is(net.minecraft.world.level.block.Blocks.SAND) || under.is(net.minecraft.world.level.block.Blocks.RED_SAND)
                || under.is(net.minecraft.world.level.block.Blocks.GRAVEL) || under.is(net.minecraft.world.level.block.Blocks.CLAY)
                || under.is(net.minecraft.world.level.block.Blocks.MUD)) {
            return under;
        }
        return rock;
    }

    private static boolean underRoof(net.minecraft.world.level.BlockGetter getter, int x, int y, int z) {
        final net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int up = 1; up <= CONTAIN_ROOF_REACH; up++) {
            final net.minecraft.world.level.block.state.BlockState s = getter.getBlockState(at.set(x, y + up, z));
            if (s.isAir() || !s.getFluidState().isEmpty()) continue;
            if (s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(net.minecraft.tags.BlockTags.LOGS)) continue;
            if (s.isSolidRender()) return true;
        }
        return false;
    }

    private static final int CONTAIN_ROOF_REACH = 48;

    public static void containAcrossChunks(net.minecraft.world.level.WorldGenLevel level,
            net.minecraft.world.level.chunk.ChunkAccess chunk,
            net.minecraft.world.level.chunk.ChunkGenerator generator) {
        final net.minecraft.world.level.ChunkPos cp = chunk.getPos();
        final int[] band = takeWaterBand(cp.pack());
        if (band == null) return;
        final int sx = cp.getMinBlockX(), sz = cp.getMinBlockZ();
        final int sea = generator.getSeaLevel();
        final net.minecraft.world.level.levelgen.RandomState random = com.AdamNew.dynamicwaters.compat.OneDimension.randomState(level.getLevel());
        final net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        final net.minecraft.core.BlockPos.MutableBlockPos next = new net.minecraft.core.BlockPos.MutableBlockPos();
        final int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (lx != 0 && lx != 15 && lz != 0 && lz != 15) continue;
                for (int y = band[1]; y >= band[0]; y--) {
                    if (!chunk.getBlockState(at.set(sx + lx, y, sz + lz)).is(net.minecraft.world.level.block.Blocks.WATER)) continue;
                    for (int[] d : sides) {
                        final int nlx = lx + d[0], nlz = lz + d[1];
                        if (nlx >= 0 && nlx <= 15 && nlz >= 0 && nlz <= 15) continue;
                        final int nx = sx + nlx, nz = sz + nlz;
                        if (!level.getBlockState(next.set(nx, y, nz)).isAir()) continue;
                        if (!underRoof(level, nx, y, nz)) continue;
                        final RiverCarving n = calculateRiverCarving(nx, nz,
                                generator.getBaseHeight(nx, nz, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1,
                                sea, generator, level, random);
                        if (holdsWater(n) && y > n.waterLevel) continue;
                        level.setBlock(next, holdFill(level, next), 2);
                    }
                }
            }
        }
    }

    private static final int SEAL_MIN = 5;
    private static final double SEAL_PER_WIDTH = 5.0;

    private static final int MAX_BANK_CUT = 8;

    private static final double STEEP_BANK_TALUS = 3.0;

    private static final double BANK_HOLD_REACH = 1.5;
    private static final int BANK_HOLD_SPAN = 4;

    private static final int BANK_HOLD_OVER = 3;
    private static final double BANK_HOLD_NEAR = 10.0;

    private static final int CAP_YIELD_RISE = 4;
    private static final double CAP_YIELD_EDGE = 2.0;
    private static final int CAP_YIELD_UP = 6;

    private static double channelFullWidth(WatershedPath.RiverNodeData data, double x, double z) {
        final double gorge = data.terrainY - data.y;
        final double narrow = gorge <= GORGE_NARROW_FROM ? 1.0
                : Math.max(GORGE_NARROW_MIN,
                        1.0 - (1.0 - GORGE_NARROW_MIN) * Math.min(1.0,
                                (gorge - GORGE_NARROW_FROM) / (GORGE_NARROW_TO - GORGE_NARROW_FROM)));
        final double pinch = 0.6 * RIVER_BED_NOISE.get(x * 0.007, z * 0.007)
                + 0.4 * RIVER_BED_NOISE.get(x * 0.0018 + 91.0, z * 0.0018 - 37.0);
        return data.width * narrow * ChannelForm.slopeWidth(data.slope) * ChannelForm.pinchWidth(pinch);
    }

    private static int channelBowl(WatershedPath.RiverNodeData data, double chanW) {
        final int centerDepth = Mth.clamp((int) Math.round(STREAM_DEPTH_RATIO * chanW), 1, MAX_BOWL);
        final double lat = Mth.clamp(data.lateral / chanW, -1.0, 1.0);
        final double skew = ChannelForm.bendSkew(data.curvature, chanW);
        return Math.min(MAX_BOWL, (int) Math.round(centerDepth * ChannelForm.bedDepth(lat, skew)));
    }

    private static boolean capYields(WatershedPath self, WatershedPath.RiverNodeData own, double x, double z,
            int colX, int colZ, int homeRx, int homeRz,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        for (int dx = -WATERSHED_SCAN_RADIUS; dx <= WATERSHED_SCAN_RADIUS; dx++) {
            for (int dz = -WATERSHED_SCAN_RADIUS; dz <= WATERSHED_SCAN_RADIUS; dz++) {
                final int mrx = homeRx + dx, mrz = homeRz + dz;
                final WatershedRegionData regionData = getWatershedRegion(mrx, mrz, generator, level, randomState);
                for (WatershedPath other : regionData.grid.getPathsNear(colX, colZ, mrx, mrz)) {
                    if (other == self) continue;
                    final WatershedPath.RiverNodeData d = other.getClosestNodeData(x, z);
                    if (d.beyondEnd || d.nodeIndex < CAP_YIELD_UP || d.y < own.y + CAP_YIELD_RISE) continue;
                    if (d.distance < channelFullWidth(d, x, z) + CAP_YIELD_EDGE) return true;
                }
            }
        }
        return false;
    }

    private static boolean capGoesOn(WatershedPath self, double x, double z, int colX, int colZ, int homeRx, int homeRz,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (self.validNodeCount < 2) return false;
        final int last = self.validNodeCount - 1;
        final boolean atMouth = Math.hypot(x - self.segmentsX[0], z - self.segmentsZ[0])
                <= Math.hypot(x - self.segmentsX[last], z - self.segmentsZ[last]);
        final double ex = atMouth ? self.segmentsX[0] : self.segmentsX[last];
        final double ez = atMouth ? self.segmentsZ[0] : self.segmentsZ[last];

        int mouths = 0, heads = 0;
        boolean onIt = false;
        final java.util.List<WatershedPath> seen = new java.util.ArrayList<>();
        for (int dx = -WATERSHED_SCAN_RADIUS; dx <= WATERSHED_SCAN_RADIUS; dx++) {
            for (int dz = -WATERSHED_SCAN_RADIUS; dz <= WATERSHED_SCAN_RADIUS; dz++) {
                final int mrx = homeRx + dx, mrz = homeRz + dz;
                final WatershedRegionData regionData = getWatershedRegion(mrx, mrz, generator, level, randomState);
                for (WatershedPath other : regionData.grid.getPathsNear(colX, colZ, mrx, mrz)) {
                    if (other == self || other.validNodeCount < 2 || seen.contains(other)) continue;
                    seen.add(other);
                    final int o = other.validNodeCount - 1;
                    final boolean mouth = Math.abs(other.segmentsX[0] - ex) < 1.5 && Math.abs(other.segmentsZ[0] - ez) < 1.5;
                    final boolean head = Math.abs(other.segmentsX[o] - ex) < 1.5 && Math.abs(other.segmentsZ[o] - ez) < 1.5;
                    if (mouth) mouths++;
                    if (head) heads++;
                    if (!(atMouth ? head : mouth) || onIt) continue;
                    final WatershedPath.RiverNodeData d = other.getClosestNodeData(x, z);
                    if (d.beyondEnd) continue;
                    final double w = channelFullWidth(d, x, z);
                    onIt = d.distance < w + Math.max(BANK_ZONE_MIN, BANK_ZONE_PER_WIDTH * w);
                }
            }
        }
        return onIt && (atMouth ? heads == 1 && mouths == 0 : mouths == 1 && heads == 0);
    }

    private static int bankHold(WatershedPath path, int node, int ownY, double x, double z, double chanW,
            Ravine ravine, Ravine fed, boolean overRim, int seaLevel, boolean wetOnly) {
        int hold = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            final double nx = x + (k == 0 ? 1.0 : k == 1 ? -1.0 : 0.0);
            final double nz = z + (k == 2 ? 1.0 : k == 3 ? -1.0 : 0.0);
            final WatershedPath.RiverNodeData nd = path.getClosestNodeDataNear(nx, nz, node, BANK_HOLD_SPAN);
            if (nd.distance >= chanW + 0.5) continue;
            if (wetOnly && channelBowl(nd, chanW) == 0) continue;
            if (ravine != null && (nd.nodeIndex < ravine.start || inRavineWay(ravine, nx, nz, true))) continue;
            if (fed != null && inRavineWay(fed, nx, nz, false)) continue;
            if (overRim && nd.y <= seaLevel + 1) continue;
            hold = Math.max(hold, nd.y > ownY || underStep(path, nd, nx, nz) ? nd.y + 1 : nd.y);
        }
        return hold;
    }

    private static boolean underStep(WatershedPath path, WatershedPath.RiverNodeData nd, double x, double z) {
        if (nd.flowDir.x == 0.0 && nd.flowDir.z == 0.0) return false;
        final WatershedPath.RiverNodeData up = path.getClosestNodeDataNear(x - nd.flowDir.x, z - nd.flowDir.z,
                nd.nodeIndex, 2);
        return up.distance != Double.MAX_VALUE && up.y > nd.y;
    }

    private static boolean inRavineWay(Ravine r, double x, double z, boolean ragged) {
        final double ox = x - r.px, oz = z - r.pz;
        final double along = ox * r.ux + oz * r.uz;
        if (along <= 0.0) return false;
        final double side = ragged ? r.across(x, z, ox, oz, along) : Math.abs(r.lateral(ox, oz, along));
        return side < r.width + RAVINE_SIDE;
    }

    private static int lakeDepth(double shoreDistance) {
        final double s = 1.0 - Math.min(1.0, shoreDistance / WatershedLake.SHORE_REACH);
        return (int) Math.round(LAKE_DEPTH - (LAKE_DEPTH - 1) * s * s);
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<Void>> TILE_BUILDS = new java.util.concurrent.ConcurrentHashMap<>();

    public static class WatershedSpatialGrid {
        private final java.util.List<WatershedPath>[][] grid;
        public static final int CELL_SIZE = 32;
        public static final int GRID_DIM = WATERSHED_REGION_SIZE / CELL_SIZE;

        @SuppressWarnings("unchecked")
        public WatershedSpatialGrid() {
            grid = new java.util.List[GRID_DIM][GRID_DIM];
            for (int i = 0; i < GRID_DIM; i++) {
                for (int j = 0; j < GRID_DIM; j++) {
                    grid[i][j] = new java.util.ArrayList<>();
                }
            }
        }

        public void addPath(WatershedPath path, int rx, int rz) {
            int originX = rx * WATERSHED_REGION_SIZE;
            int originZ = rz * WATERSHED_REGION_SIZE;

            int minCol = Math.max(0,
                    Math.min(GRID_DIM - 1, (int) Math.floor((path.influenceMinX() - originX) / CELL_SIZE)));
            int maxCol = Math.max(0,
                    Math.min(GRID_DIM - 1, (int) Math.floor((path.influenceMaxX() - originX) / CELL_SIZE)));
            int minRow = Math.max(0,
                    Math.min(GRID_DIM - 1, (int) Math.floor((path.influenceMinZ() - originZ) / CELL_SIZE)));
            int maxRow = Math.max(0,
                    Math.min(GRID_DIM - 1, (int) Math.floor((path.influenceMaxZ() - originZ) / CELL_SIZE)));

            for (int c = minCol; c <= maxCol; c++) {
                for (int r = minRow; r <= maxRow; r++) {
                    grid[c][r].add(path);
                }
            }
        }

        public java.util.List<WatershedPath> getPathsNear(int x, int z, int rx, int rz) {
            int originX = rx * WATERSHED_REGION_SIZE;
            int originZ = rz * WATERSHED_REGION_SIZE;
            int c = (int) Math.floor((x - originX) / (double) CELL_SIZE);
            int r = (int) Math.floor((z - originZ) / (double) CELL_SIZE);
            c = Math.max(0, Math.min(GRID_DIM - 1, c));
            r = Math.max(0, Math.min(GRID_DIM - 1, r));
            return grid[c][r];
        }
    }

    public static class WatershedRegionData {
        public final java.util.List<WatershedPath> allPaths;
        public final java.util.List<WatershedLake> lakes;
        public final WatershedSpatialGrid grid;
        public final int rx, rz;

        public long lastUsed;

        public WatershedRegionData(java.util.List<WatershedPath> paths, int rx, int rz) {
            this(paths, java.util.Collections.emptyList(), rx, rz);
        }

        public WatershedRegionData(java.util.List<WatershedPath> paths, java.util.List<WatershedLake> lakes, int rx, int rz) {
            this.allPaths = new java.util.ArrayList<>(paths);
            this.lakes = new java.util.ArrayList<>(lakes);
            this.rx = rx;
            this.rz = rz;
            this.grid = new WatershedSpatialGrid();
            for (WatershedPath p : paths) {
                this.grid.addPath(p, rx, rz);
            }
        }
    }

    public static boolean isOceanBiome(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
        if (biomeHolder.is(net.minecraft.tags.BiomeTags.IS_OCEAN))
            return true;

        if (com.AdamNew.dynamicwaters.Config.isBiomeBlacklisted(biomeHolder))
            return true;

        net.minecraft.resources.Identifier id = biomeHolder.unwrapKey()
                .map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
        if (id != null) {
            String path = id.getPath();
            return path.contains("ocean") || (path.contains("deep_") && path.contains("ocean"));
        }
        return false;
    }

    public static boolean isWaterBiomeAt(net.minecraft.world.level.chunk.ChunkGenerator generator, int x, int z,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || randomState == null)
            return false;
        try {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = com.AdamNew.dynamicwaters.ClimateAccess.biome(generator.getBiomeSource(), x >> 2, HydrologyManager.getSeaLevel() >> 2, z >> 2, com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState));

            if (isOceanBiome(biomeHolder))
                return true;
            if (biomeHolder.is(net.minecraft.tags.BiomeTags.IS_RIVER))
                return true;

            net.minecraft.resources.Identifier id = biomeHolder.unwrapKey()
                    .map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
            if (id != null) {
                String path = id.getPath();
                return path.contains("lake") || path.contains("water");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isMouthBiomeAt(net.minecraft.world.level.chunk.ChunkGenerator generator, int x, int z,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || randomState == null)
            return false;
        try {
            final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = com.AdamNew.dynamicwaters.ClimateAccess.biome(generator.getBiomeSource(), x >> 2, HydrologyManager.getSeaLevel() >> 2, z >> 2, com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState));
            if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                    || biome.is(net.minecraft.world.level.biome.Biomes.RIVER)
                    || biome.is(net.minecraft.world.level.biome.Biomes.FROZEN_RIVER))
                return true;

            final net.minecraft.resources.Identifier id = biome.unwrapKey()
                    .map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
            return id != null && (id.getPath().contains("ocean") || id.getPath().contains("river"));
        } catch (Exception e) {
            return false;
        }
    }

    public static final int POOL_FILL_DEPTH = 8;

    private static final int POOL_SEA_CLEAR = 32;

    private static final int POOL_MAX_CELLS = 64;

    private static final FastConcurrentCache<Boolean> POOL_BODIES = new FastConcurrentCache<>(1 << 16);

    private static final FastConcurrentCache<Boolean> POOL_CLEAR = new FastConcurrentCache<>(1 << 14);

    public static boolean poolFilled(int x, int z, int top, int seaLevel,
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (!com.AdamNew.dynamicwaters.Config.ENABLE_watersheds.get() || generator == null || randomState == null) {
            return false;
        }

        final int cell = CoarseHeightField.CELL_SIZE;
        final int cx = Math.floorDiv(x + cell / 2, cell), cz = Math.floorDiv(z + cell / 2, cell);
        if (poolPoint(cx, cz, seaLevel, generator, randomState)) {
            return poolBody(cx, cz, seaLevel, generator, randomState);
        }
        boolean near = false;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx | dz) == 0 || !poolPoint(cx + dx, cz + dz, seaLevel, generator, randomState)) continue;
                if (!poolBody(cx + dx, cz + dz, seaLevel, generator, randomState)) return false;
                near = true;
            }
        }
        if (near) return true;

        return seaLevel - 1 - top <= POOL_FILL_DEPTH && !keepsStandingWater(biome)
                && poolClear(cx, cz, generator, randomState);
    }

    private static boolean poolPoint(int cx, int cz, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        return CoarseHeightField.sample(cx, cz, generator, randomState) - 1 < seaLevel - 1;
    }

    private static boolean poolBody(int cx, int cz, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final Boolean known = POOL_BODIES.getIfPresent(cx, cz);
        if (known != null) return known;
        final it.unimi.dsi.fastutil.longs.LongOpenHashSet body = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        final it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue open = new it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue();
        body.add(net.minecraft.world.level.ChunkPos.pack(cx, cz));
        open.enqueue(net.minecraft.world.level.ChunkPos.pack(cx, cz));
        boolean fill = true;
        search:
        while (!open.isEmpty()) {
            final long point = open.dequeueLong();
            final int px = net.minecraft.world.level.ChunkPos.getX(point), pz = net.minecraft.world.level.ChunkPos.getZ(point);
            if (seaLevel - CoarseHeightField.sample(px, pz, generator, randomState) > POOL_FILL_DEPTH
                    || keepsStandingWater(poolBiome(px, pz, generator, randomState))) {
                fill = false;
                break;
            }
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx | dz) == 0 || !poolPoint(px + dx, pz + dz, seaLevel, generator, randomState)) continue;
                    final long next = net.minecraft.world.level.ChunkPos.pack(px + dx, pz + dz);
                    if (!body.add(next)) continue;
                    if (body.size() > POOL_MAX_CELLS) {
                        fill = false;
                        break search;
                    }
                    open.enqueue(next);
                }
            }
        }
        final it.unimi.dsi.fastutil.longs.LongIterator points = body.iterator();
        while (points.hasNext()) {
            final long point = points.nextLong();
            POOL_BODIES.set(net.minecraft.world.level.ChunkPos.getX(point), net.minecraft.world.level.ChunkPos.getZ(point), fill);
        }
        return fill;
    }

    private static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> poolBiome(int cx, int cz,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final int cell = CoarseHeightField.CELL_SIZE;
        return com.AdamNew.dynamicwaters.ClimateAccess.biome(generator.getBiomeSource(), net.minecraft.core.QuartPos.fromBlock(cx * cell),
                net.minecraft.core.QuartPos.fromBlock(getSeaLevel()), net.minecraft.core.QuartPos.fromBlock(cz * cell),
                com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState));
    }

    private static boolean poolClear(int cx, int cz, net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final Boolean known = POOL_CLEAR.getIfPresent(cx, cz);
        if (known != null) return known;
        final int x = cx * CoarseHeightField.CELL_SIZE, z = cz * CoarseHeightField.CELL_SIZE;
        boolean clear = true;
        for (int dz = -POOL_SEA_CLEAR; dz <= POOL_SEA_CLEAR && clear; dz += POOL_SEA_CLEAR / 2) {
            for (int dx = -POOL_SEA_CLEAR; dx <= POOL_SEA_CLEAR; dx += POOL_SEA_CLEAR / 2) {
                if (isMouthBiomeAt(generator, x + dx, z + dz, randomState)) {
                    clear = false;
                    break;
                }
            }
        }
        POOL_CLEAR.set(cx, cz, clear);
        return clear;
    }

    public static boolean keepsStandingWater(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                || biome.is(net.minecraft.tags.BiomeTags.IS_BEACH)
                || biome.is(com.AdamNew.dynamicwaters.platform.ModTags.IS_SWAMP)) return true;
        final net.minecraft.resources.Identifier id = biome.unwrapKey()
                .map(net.minecraft.resources.ResourceKey::identifier).orElse(null);
        if (id == null) return true;
        final String p = id.getPath();
        return p.contains("swamp") || p.contains("marsh") || p.contains("bog") || p.contains("fen")
                || p.contains("mangrove") || p.contains("wetland") || p.contains("lake") || p.contains("beach")
                || p.contains("shore") || p.contains("ocean") || p.contains("river");
    }

    public static int fillPool(net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int z, int top, int seaLevel,
            net.minecraft.world.level.block.state.BlockState ground) {
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        int filled = top;
        for (int y = top + 1; y < seaLevel; y++) {
            pos.set(x, y, z);
            if (!chunk.getBlockState(pos).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) break;
            chunk.setBlockState(pos, ground, 3);
            filled = y;
        }
        return filled;
    }

    private static final int CAVE_GUARD_REACH = 8;
    private static final int CAVE_GUARD_DEPTH = 12;

    public static int[] caveGuard(RiverCarving[] carvings) {
        final int[] own = new int[256];
        for (int i = 0; i < 256; i++) {
            final RiverCarving c = carvings[i];
            own[i] = c != null && c.modifies ? c.waterLevel : Integer.MIN_VALUE;
        }
        final int[] rows = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int m = Integer.MIN_VALUE;
                for (int nx = Math.max(0, x - CAVE_GUARD_REACH); nx <= Math.min(15, x + CAVE_GUARD_REACH); nx++) {
                    m = Math.max(m, own[nx + z * 16]);
                }
                rows[x + z * 16] = m;
            }
        }
        final int[] near = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int m = Integer.MIN_VALUE;
                for (int nz = Math.max(0, z - CAVE_GUARD_REACH); nz <= Math.min(15, z + CAVE_GUARD_REACH); nz++) {
                    m = Math.max(m, rows[x + nz * 16]);
                }
                near[x + z * 16] = m;
            }
        }
        return near;
    }

    public static void cancelCaves(net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int z, int top, int level,
            net.minecraft.world.level.block.state.BlockState ground, java.util.BitSet protectedBlocks, int minH) {
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = top - 1; y >= Math.max(minH, level - CAVE_GUARD_DEPTH); y--) {
            if (protectedBlocks != null && protectedBlocks.get(y - minH)) continue;
            final net.minecraft.world.level.block.state.BlockState here = chunk.getBlockState(pos.set(x, y, z));
            if (here.isAir() || !here.getFluidState().isEmpty()) chunk.setBlockState(pos, ground, 3);
        }
    }

    public static final int SHAFT_DEPTH = 3;

    private static final int SHAFT_REACH = 2;

    public static boolean repairsColumn(double x, double z, net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.chunk.ChunkAccess chunk, net.minecraft.world.level.levelgen.RandomState randomState) {
        final WatershedContext mountainCtx = evaluateWatershedContext(x, z, generator, chunk, randomState, false);
        if (mountainCtx.bestPath != null && mountainCtx.distance < mountainCtx.width + 40.0) return true;
        if (isInOrBesideLake(x, z)) return true;
        final com.AdamNew.dynamicwaters.river.RiverContext macroInfo = MainRivers.findNearest(x, z, generator, chunk, randomState);
        if (macroInfo == null) return false;

        final double waterR = normalRiverWaterRadius(Math.sqrt(macroInfo.widthSq()));
        return macroInfo.distSq() < waterR * waterR;
    }

    public static boolean dryHollow(net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int z, int top, int seaLevel) {
        if (top >= seaLevel - 1) return false;
        return chunk.getBlockState(new net.minecraft.core.BlockPos(x, top + 1, z)).isAir();
    }

    public static int shaftFloor(int i, int top, int waterLevel, int[] ground, RiverCarving[] carvings, boolean[] mouths,
            boolean[] filled) {
        final int x = i & 15, z = i >> 4;
        int deepest = -1;
        double shore = Double.MAX_VALUE;
        for (int reach = SHAFT_REACH; deepest < 0 && reach <= 16; reach *= 2) {
            for (int nz = Math.max(0, z - reach); nz <= Math.min(15, z + reach); nz++) {
                for (int nx = Math.max(0, x - reach); nx <= Math.min(15, x + reach); nx++) {
                    final int j = nx + nz * 16;
                    final RiverCarving c = carvings[j];
                    if (j == i || mouths[j] || c == null) continue;
                    final int bed = c.modifies && !filled[j] ? Math.min(c.targetY, ground[j]) : ground[j];
                    final int depth = c.isWatershedWall ? 0 : Math.max(0, c.waterLevel - bed);
                    final double d = Math.sqrt((nx - x) * (nx - x) + (nz - z) * (nz - z));
                    if (depth == 0) {
                        shore = Math.min(shore, d);
                    } else if (d <= reach) {
                        deepest = Math.max(deepest, depth);
                    }
                }
            }
        }
        int depth = deepest >= 0 ? deepest : SHAFT_DEPTH;
        if (shore < Double.MAX_VALUE) depth = Math.min(depth, (int) shore + 1);
        return Math.max(top, waterLevel - Math.max(SHAFT_MIN_DEPTH, depth));
    }

    private static final int SHAFT_MIN_DEPTH = 2;

    public static int digHollow(net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int z, int top, int floor,
            java.util.BitSet protectedBlocks, int minH) {
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        int dug = top;
        for (int y = top; y > floor; y--) {
            if (protectedBlocks != null && y >= minH && protectedBlocks.get(y - minH)) break;
            chunk.setBlockState(pos.set(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            dug = y - 1;
        }
        return dug;
    }

    public static int fillHollow(net.minecraft.world.level.chunk.ChunkAccess chunk, int x, int z, int top, int floor,
            net.minecraft.world.level.block.state.BlockState ground, java.util.BitSet protectedBlocks, int minH) {
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        int filled = top;
        for (int y = top + 1; y <= floor; y++) {
            if (protectedBlocks != null && y >= minH && protectedBlocks.get(y - minH)) break;
            final net.minecraft.world.level.block.state.BlockState here = chunk.getBlockState(pos.set(x, y, z));
            if (!here.isAir() && here.getFluidState().isEmpty()) break;
            chunk.setBlockState(pos, ground, 3);
            filled = y;
        }
        return filled;
    }

    public static boolean isOceanBiomeAt(net.minecraft.world.level.chunk.ChunkGenerator generator, int x, int z,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || randomState == null)
            return false;
        try {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biomeHolder = com.AdamNew.dynamicwaters.ClimateAccess.biome(generator.getBiomeSource(), x >> 2, HydrologyManager.getSeaLevel() >> 2, z >> 2, com.AdamNew.dynamicwaters.ClimateAccess.sampler(randomState));
            return isOceanBiome(biomeHolder);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isOceanAt(LevelReader level, net.minecraft.core.BlockPos pos) {
        if (level == null)
            return false;

        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }

        try {
            return isOceanBiome(level.getBiome(pos));
        } catch (Exception e) {
            return false;
        }
    }

    private static long WORLD_SEED = 0;
    private static volatile int WORLD_SEA_LEVEL = 63;

    public static void setSeed(long seed) {
        if (WORLD_SEED != seed || RIVER_BED_NOISE == null) {
            WORLD_SEED = seed;
            MainRivers.setSeed(seed);
            clearCaches();
            RIVER_BED_NOISE = new net.minecraft.world.level.levelgen.synth.SimplexNoise(
                    new net.minecraft.world.level.levelgen.XoroshiroRandomSource(seed ^ 12345L));
            PILLAR_NOISE = new net.minecraft.world.level.levelgen.synth.SimplexNoise(
                    new net.minecraft.world.level.levelgen.XoroshiroRandomSource(seed ^ 67890L));
            SPELEOTHEM_NOISE = new net.minecraft.world.level.levelgen.synth.SimplexNoise(
                    new net.minecraft.world.level.levelgen.XoroshiroRandomSource(seed ^ 54321L));
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> PRE_CARVE_HEIGHTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void rememberPreCarveHeights(long chunkKey, int[] heights) {
        if (PRE_CARVE_HEIGHTS.size() > 4096) PRE_CARVE_HEIGHTS.clear();
        PRE_CARVE_HEIGHTS.put(chunkKey, heights);
    }

    public static int[] takePreCarveHeights(long chunkKey) {
        return PRE_CARVE_HEIGHTS.remove(chunkKey);
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> WATER_BANDS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void rememberWaterBand(long chunkKey, int low, int high) {
        if (WATER_BANDS.size() > 4096) WATER_BANDS.clear();
        WATER_BANDS.put(chunkKey, new int[] {low, high});
    }

    public static int[] takeWaterBand(long chunkKey) {
        return WATER_BANDS.remove(chunkKey);
    }

    public static void clearCaches() {
        WATERSHED_REGION_CACHE.clear();
        HEAD_FEEDS.clear();
        PRE_CARVE_HEIGHTS.clear();
        WATER_BANDS.clear();
        TILE_BUILDS.clear();
        BasinTile.clearSeamCache();
        CoarseHeightField.reset();
        POOL_BODIES.clear();
        POOL_CLEAR.clear();
        REGION_GENERATION.incrementAndGet();
    }

    private static net.minecraft.world.level.levelgen.synth.SimplexNoise RIVER_BED_NOISE;
    private static net.minecraft.world.level.levelgen.synth.SimplexNoise PILLAR_NOISE;
    private static net.minecraft.world.level.levelgen.synth.SimplexNoise SPELEOTHEM_NOISE;

    public static void setSeaLevel(int seaLevel) {
        WORLD_SEA_LEVEL = seaLevel;
    }

    public static int getSeaLevel() {
        return WORLD_SEA_LEVEL;
    }

    public static long getRegionKey(int rx, int rz) {
        return ((long) rx & 0xFFFFFFFFL) | (((long) rz & 0xFFFFFFFFL) << 32);
    }

    public static WatershedRegionData getWatershedRegion(int rx, int rz,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final long key = getRegionKey(rx, rz);
        WatershedRegionData data = WATERSHED_REGION_CACHE.get(key);
        if (data != null) {
            data.lastUsed = WATERSHED_CLOCK.get();
            return data;
        }
        if (!com.AdamNew.dynamicwaters.Config.ENABLE_watersheds.get()) {
            data = new WatershedRegionData(java.util.Collections.emptyList(), java.util.Collections.emptyList(), rx, rz);
            putRegion(data);
            return data;
        }
        if (generator == null || randomState == null) {
            return new WatershedRegionData(java.util.Collections.emptyList(), java.util.Collections.emptyList(), rx, rz);
        }
        ensureTileBuilt(BasinTile.tileCoord(rx), BasinTile.tileCoord(rz), generator, randomState);
        data = WATERSHED_REGION_CACHE.get(key);
        if (data == null) {
            return new WatershedRegionData(java.util.Collections.emptyList(), java.util.Collections.emptyList(), rx, rz);
        }
        data.lastUsed = WATERSHED_CLOCK.get();
        return data;
    }

    public static java.util.List<WatershedPath> getMountainRegionPaths(int rx, int rz,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        return getWatershedRegion(rx, rz, generator, level, randomState).allPaths;
    }

    public static void putRegion(WatershedRegionData data) {
        data.lastUsed = WATERSHED_CLOCK.incrementAndGet();
        WATERSHED_REGION_CACHE.put(getRegionKey(data.rx, data.rz), data);
        REGION_GENERATION.incrementAndGet();
        if (WATERSHED_REGION_CACHE.size() > WATERSHED_CACHE_LIMIT) {
            evictLeastRecentlyUsed();
        }
    }

    private static void evictLeastRecentlyUsed() {
        synchronized (EVICTION_LOCK) {
            if (WATERSHED_REGION_CACHE.size() <= WATERSHED_CACHE_LIMIT) return;
            long[] stamps = new long[WATERSHED_REGION_CACHE.size()];
            int n = 0;
            for (WatershedRegionData d : WATERSHED_REGION_CACHE.values()) {
                if (n == stamps.length) break;
                stamps[n++] = d.lastUsed;
            }
            java.util.Arrays.sort(stamps, 0, n);
            final long cutoff = stamps[n / 4];
            WATERSHED_REGION_CACHE.values().removeIf(d -> d.lastUsed <= cutoff);
            REGION_GENERATION.incrementAndGet();
        }
    }

    private static void ensureTileBuilt(int tx, int tz,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final long tileKey = BasinTile.tileKey(tx, tz);
        final java.util.concurrent.CompletableFuture<Void> mine = new java.util.concurrent.CompletableFuture<>();
        final java.util.concurrent.CompletableFuture<Void> existing = TILE_BUILDS.putIfAbsent(tileKey, mine);
        if (existing != null) {
            WatershedStats.tileWaits.increment();
            existing.join();
            return;
        }
        try {
            final BasinTile tile = BasinTile.build(tx, tz, generator, randomState, WORLD_SEED, WORLD_SEA_LEVEL,
                    BasinTile.channelThreshold(com.AdamNew.dynamicwaters.Config.MOUNTAIN_RIVER_DENSITY.get()),
                    com.AdamNew.dynamicwaters.Config.ENABLE_MOUNTAIN_LAKES.get());
            for (java.util.Map.Entry<Long, java.util.List<WatershedPath>> e : tile.pathsByRegion.entrySet()) {
                final long key = e.getKey();
                final int rx = (int) key;
                final int rz = (int) (key >> 32);
                final WatershedRegionData data = new WatershedRegionData(e.getValue(), tile.lakesByRegion.get(key), rx, rz);
                putRegion(data);
                com.AdamNew.dynamicwaters.async.MountainRiverScheduler.rememberIfBuilt(rx, rz, data);
            }
        } catch (Throwable t) {
            com.mojang.logging.LogUtils.getLogger().error("[Dynamic Waters] Basin tile {},{} failed to build", tx, tz, t);
        } finally {
            mine.complete(null);
            TILE_BUILDS.remove(tileKey, mine);
        }
    }

    public static void labBuildTiles(String spec, net.minecraft.server.level.ServerLevel level) {
        final String[] p = spec.split(",");
        final int tx0 = Integer.parseInt(p[0].trim()), tz0 = Integer.parseInt(p[1].trim());
        final int tx1 = Integer.parseInt(p[2].trim()), tz1 = Integer.parseInt(p[3].trim());
        final net.minecraft.world.level.chunk.ChunkGenerator generator = com.AdamNew.dynamicwaters.compat.OneDimension.generator(level);
        final net.minecraft.world.level.levelgen.RandomState randomState = com.AdamNew.dynamicwaters.compat.OneDimension.randomState(level);
        setSeed(level.getSeed());
        setSeaLevel(level.getSeaLevel());
        if (getGlobalGenerator() == null) setGlobalGenerator(generator, randomState);
        final Thread t = new Thread(() -> {
            final long t0 = System.nanoTime();
            final java.util.List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
            for (int tz = tz0; tz <= tz1; tz++) {
                for (int tx = tx0; tx <= tx1; tx++) {
                    final int x = tx, z = tz;
                    jobs.add(PREFETCH_POOL.submit(() -> ensureTileBuilt(x, z, generator, randomState)));
                }
            }
            for (java.util.concurrent.Future<?> f : jobs) {
                try {
                    f.get();
                } catch (Exception e) {
                    com.mojang.logging.LogUtils.getLogger().error("[Dynamic Waters] lab tile failed", e);
                }
            }

            if (System.getProperty("dynamicwaters.labDumpRavines") != null) {
                final int per = BasinTile.TILE_SIZE / WATERSHED_REGION_SIZE;
                for (int rz = tz0 * per; rz < (tz1 + 1) * per; rz++) {
                    for (int rx = tx0 * per; rx < (tx1 + 1) * per; rx++) {
                        warmRavines(getWatershedRegion(rx, rz, generator, level, randomState), generator, level, randomState);
                    }
                }
            }

            final String dump = System.getProperty("dynamicwaters.labDumpDryness");
            if (dump != null) {
                final StringBuilder sb = new StringBuilder();
                final int x0 = tx0 * BasinTile.TILE_SIZE, z0 = tz0 * BasinTile.TILE_SIZE;
                final int x1 = (tx1 + 1) * BasinTile.TILE_SIZE, z1 = (tz1 + 1) * BasinTile.TILE_SIZE;
                sb.append(x0).append(' ').append(z0).append(' ').append(32).append(' ')
                        .append((x1 - x0) / 32).append(' ').append((z1 - z0) / 32).append('\n');
                for (int z = z0; z < z1; z += 32) {
                    for (int x = x0; x < x1; x += 32) {
                        sb.append((int) Math.round(9.0 * BasinTile.labDryness(randomState, level.getSeed(), level.getSeaLevel(), x, z)));
                    }
                    sb.append('\n');
                }
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(dump), sb);
                } catch (java.io.IOException e) {
                    com.mojang.logging.LogUtils.getLogger().error("[Dynamic Waters] lab dryness dump failed", e);
                }
            }
            com.mojang.logging.LogUtils.getLogger().info("[Dynamic Waters] lab tiles built: {} in {} ms",
                    jobs.size(), (System.nanoTime() - t0) / 1_000_000L);
        }, "DynamicWaters-LabTiles");
        t.setDaemon(true);
        t.start();
    }

    public static java.util.List<WatershedPath> getMountainRegionPathsIfCached(int rx, int rz) {
        long key = getRegionKey(rx, rz);
        WatershedRegionData data = WATERSHED_REGION_CACHE.get(key);
        return data != null ? data.allPaths : java.util.Collections.emptyList();
    }

    private static final java.util.concurrent.ExecutorService PREFETCH_POOL = java.util.concurrent.Executors
            .newFixedThreadPool(4, new java.util.concurrent.ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    final Thread t = new Thread(r, "DynamicWaters-WatershedPrefetch-" + count.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    private static volatile java.util.concurrent.ConcurrentHashMap.KeySetView<Long, Boolean> inFlightTilePrefetch = java.util.concurrent.ConcurrentHashMap
            .newKeySet();

    public static void prefetchWatershedsAround(int blockX, int blockZ,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (RIVER_BED_NOISE == null) return;
        if (!com.AdamNew.dynamicwaters.Config.ENABLE_watersheds.get() || generator == null || randomState == null)
            return;

        final int homeRx = Math.floorDiv(blockX, WATERSHED_REGION_SIZE);
        final int homeRz = Math.floorDiv(blockZ, WATERSHED_REGION_SIZE);

        for (int drx = -WATERSHED_SCAN_RADIUS; drx <= WATERSHED_SCAN_RADIUS; drx++) {
            for (int drz = -WATERSHED_SCAN_RADIUS; drz <= WATERSHED_SCAN_RADIUS; drz++) {
                final int rx = homeRx + drx, rz = homeRz + drz;
                if (WATERSHED_REGION_CACHE.get(getRegionKey(rx, rz)) != null)
                    continue;
                final int tx = BasinTile.tileCoord(rx), tz = BasinTile.tileCoord(rz);
                final long tileKey = BasinTile.tileKey(tx, tz);
                if (TILE_BUILDS.containsKey(tileKey))
                    continue;
                if (!inFlightTilePrefetch.add(tileKey))
                    continue;

                PREFETCH_POOL.submit(() -> {
                    try {
                        ensureTileBuilt(tx, tz, generator, randomState);
                    } catch (Throwable e) {
                        com.mojang.logging.LogUtils.getLogger().error("[Dynamic Waters] Basin tile prefetch failed", e);
                    } finally {
                        inFlightTilePrefetch.remove(tileKey);
                    }
                });
            }
        }
    }

    public static int getCachedBaseHeight(net.minecraft.world.level.chunk.ChunkGenerator generator, int x, int z,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || randomState == null)
            return WORLD_SEA_LEVEL;
        return CoarseHeightField.sample(x >> CoarseHeightField.CELL_SHIFT, z >> CoarseHeightField.CELL_SHIFT, generator, randomState);
    }

    public static class WatershedContext {
        public double distance;
        public double width;
        public WatershedPath bestPath;
        public int bestY;
        public int bestNodeIndex;
        public net.minecraft.world.phys.Vec3 flowDir;
    }

    private static final ThreadLocal<WatershedContext> MOUNTAIN_RIVER_CONTEXT = ThreadLocal
            .withInitial(WatershedContext::new);

    private static final double PAST_MOUTH_YIELD = 4.0;

    public static WatershedContext evaluateWatershedContext(double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean useCacheOnly) {
        WatershedContext ctx = MOUNTAIN_RIVER_CONTEXT.get();
        ctx.distance = Double.MAX_VALUE;
        ctx.width = 3.0;
        ctx.bestPath = null;
        ctx.bestY = 0;
        ctx.bestNodeIndex = -1;
        ctx.flowDir = net.minecraft.world.phys.Vec3.ZERO;

        final int bx = Mth.floor(x), bz = Mth.floor(z);
        final int mrx = Math.floorDiv(bx, WATERSHED_REGION_SIZE);
        final int mrz = Math.floorDiv(bz, WATERSHED_REGION_SIZE);
        double bestRank = Double.MAX_VALUE;

        for (int dx = -WATERSHED_SCAN_RADIUS; dx <= WATERSHED_SCAN_RADIUS; dx++) {
            for (int dz = -WATERSHED_SCAN_RADIUS; dz <= WATERSHED_SCAN_RADIUS; dz++) {
                final int nrx = mrx + dx;
                final int nrz = mrz + dz;

                WatershedRegionData data;
                if (useCacheOnly) {
                    data = WATERSHED_REGION_CACHE.get(getRegionKey(nrx, nrz));
                } else {
                    data = getWatershedRegion(nrx, nrz, generator, level, randomState);
                }
                if (data != null) {
                    for (WatershedPath mPath : data.grid.getPathsNear(bx, bz, nrx, nrz)) {
                        WatershedPath.RiverNodeData ndata = mPath.getClosestNodeData(x, z);

                        final boolean pastMouth = ndata.beyondEnd && ndata.nodeIndex == 0
                                && (x - ndata.x) * ndata.flowDir.x + (z - ndata.z) * ndata.flowDir.z > 0.0;
                        final double rank = ndata.distance + (pastMouth ? PAST_MOUTH_YIELD : 0.0);
                        if (rank < bestRank) {
                            bestRank = rank;
                            ctx.distance = ndata.distance;
                            ctx.bestPath = mPath;
                            ctx.width = ndata.width;
                            ctx.bestY = ndata.y;
                            ctx.bestNodeIndex = ndata.nodeIndex;
                            ctx.flowDir = ndata.flowDir;
                        }
                    }
                }
            }
        }
        return ctx;
    }

    private static final class ColumnProtection {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        long generation = -1;
        int mountainLo = 1, mountainHi = 0;
        int mainLo = 1, mainHi = 0;
        int lakeLo = 1, lakeHi = 0;

        boolean covers(int y) {
            return (y >= mountainLo && y <= mountainHi) || (y >= mainLo && y <= mainHi) || (y >= lakeLo && y <= lakeHi);
        }
    }

    private static final ThreadLocal<ColumnProtection> COLUMN_PROTECTION = ThreadLocal.withInitial(ColumnProtection::new);

    public static boolean isRiverOrWallBlock(double x, int y, double z) {
        final int bx = Mth.floor(x), bz = Mth.floor(z);
        final ColumnProtection cp = COLUMN_PROTECTION.get();
        final long generation = REGION_GENERATION.get();
        if (cp.x != bx || cp.z != bz || cp.generation != generation) {
            cp.x = bx;
            cp.z = bz;
            cp.generation = generation;
            cp.mountainLo = 1;
            cp.mountainHi = 0;
            cp.mainLo = 1;
            cp.mainHi = 0;
            cp.lakeLo = 1;
            cp.lakeHi = 0;

            WatershedContext mCtx = evaluateWatershedContext(x, z, null, null, null, true);
            if (mCtx.bestPath != null) {
                double baseBankWidth = 6.0;
                double baseSegmentWidth = mCtx.bestPath.segmentWidths.length > 0 ? mCtx.bestPath.segmentWidths[0] : 1.0;
                if (baseSegmentWidth < 0.1)
                    baseSegmentWidth = 1.0;
                double bankWidth = baseBankWidth * (mCtx.width / baseSegmentWidth);
                double minBank = 4.0 * (mCtx.width / baseSegmentWidth);
                if (bankWidth < minBank)
                    bankWidth = minBank;
                double outerBlendWidth = Math.max(15.0, bankWidth * 2.5);
                if (mCtx.distance < mCtx.width + bankWidth + outerBlendWidth) {
                    cp.mountainLo = mCtx.bestY - 60;
                    cp.mountainHi = mCtx.bestY + 60;
                }
            }

            WatershedLake lake = findLake(x, z, null, null, null, true);
            if (lake == null) lake = lakeBeside(x, z, Integer.MIN_VALUE, null, null, null, true);
            if (lake != null) {
                cp.lakeLo = lake.waterY - 12;
                cp.lakeHi = lake.waterY + 3;
            }

            java.util.List<com.AdamNew.dynamicwaters.river.RiverGraphEdge> nearbyRivers = com.AdamNew.dynamicwaters.MainRivers
                    .riversAt(x, z);
            for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : nearbyRivers) {
                final double near = path.width * 1.2 + 25.0;
                if (path.fractal().boxDistanceSq(x, z) >= near * near) continue;
                double rawDistance = Math.sqrt(path.fractal().intersectDistance(x, z));
                double jitter = getRiverJitter(x, z);
                double distance = Math.max(0, rawDistance + jitter * (path.width / 10.0));
                if (distance < path.width + 25.0) {
                    int riverY = WORLD_SEA_LEVEL - 1;
                    cp.mainLo = riverY - 10;
                    cp.mainHi = riverY + 65;
                    break;
                }
            }

            if (inGorgeReach(x, z)) {
                cp.mainLo = WORLD_SEA_LEVEL - 11;
                cp.mainHi = Integer.MAX_VALUE;
            }
        }
        return cp.covers(y);
    }

    public static boolean inRavine(double x, double z) {
        if (Config.NORMAL_RIVER_TUNNELS.get()) return false;
        final int colX = Mth.floor(x), colZ = Mth.floor(z);
        final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE), homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final WatershedRegionData data = WATERSHED_REGION_CACHE.get(getRegionKey(homeRx + dx, homeRz + dz));
                if (data == null) continue;
                for (WatershedPath p : data.grid.getPathsNear(colX, colZ, homeRx + dx, homeRz + dz)) {
                    final Ravine r = p.ravine;
                    if (r == null || r == NO_RAVINE) continue;
                    final double ox = x - r.px, oz = z - r.pz;
                    final double along = ox * r.ux + oz * r.uz;
                    if (along < -RAVINE_RIM || along > r.length + RAVINE_INTO) continue;
                    if (Math.abs(r.lateral(ox, oz, along)) < r.width + RAVINE_SIDE) return true;
                }
            }
        }
        return false;
    }

    private static net.minecraft.world.phys.Vec3 ravineFlow(double x, int y, double z) {
        if (Config.NORMAL_RIVER_TUNNELS.get()) return null;
        final int colX = Mth.floor(x), colZ = Mth.floor(z);
        final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE), homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final WatershedRegionData data = WATERSHED_REGION_CACHE.get(getRegionKey(homeRx + dx, homeRz + dz));
                if (data == null) continue;
                for (WatershedPath p : data.grid.getPathsNear(colX, colZ, homeRx + dx, homeRz + dz)) {
                    final Ravine r = p.ravine;
                    if (r == null || r == NO_RAVINE || y > r.top + 1 || y < WORLD_SEA_LEVEL - 10) continue;
                    final double ox = x - r.px, oz = z - r.pz;
                    final double along = ox * r.ux + oz * r.uz;
                    if (along < 0.0 || along > r.length) continue;
                    if (r.across(x, z, ox, oz, along) >= r.width + 1.0) continue;

                    final double bend = r.centre(along + 0.5) - r.centre(along - 0.5);
                    final double fx = r.ux + r.uz * bend, fz = r.uz - r.ux * bend;
                    final double fl = Math.hypot(fx, fz);
                    return new net.minecraft.world.phys.Vec3(fx / fl, 0.0, fz / fl);
                }
            }
        }
        return null;
    }

    public static void warmRavines(WatershedRegionData data,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (data == null || Config.NORMAL_RIVER_TUNNELS.get() || generator == null || randomState == null) return;
        for (WatershedPath p : data.allPaths) {
            if (p.ravine != null || p.validNodeCount < 2) continue;
            if (entersGorge(p, WORLD_SEA_LEVEL, generator, level, randomState)) {
                ravineOf(p, WORLD_SEA_LEVEL, generator, level, randomState);
            }
        }
    }

    public static boolean inGorgeReach(double x, double z) {
        if (Config.NORMAL_RIVER_TUNNELS.get()) return false;
        final net.minecraft.world.level.chunk.ChunkGenerator gen = getGlobalGenerator();
        final net.minecraft.world.level.levelgen.RandomState rs = getGlobalRandomState();
        if (gen == null || rs == null) return false;
        final double[] onRiver = new double[2];
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : com.AdamNew.dynamicwaters.MainRivers
                .riversAt(x, z)) {
            final double reach = path.maxWaterRadius() + GORGE_REACH + GORGE_SEAL + 4.0;
            if (path.fractal().boxDistanceSq(x, z) >= reach * reach) continue;
            if (path.fractal().nearestPoint(x, z, onRiver) >= reach * reach) continue;
            if (coarseGroundAt(onRiver[0], onRiver[1], gen, rs) <= GORGE_FROM) continue;
            return true;
        }
        return false;
    }

    public static WatershedLake findLake(double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean useCacheOnly) {
        return findLake(x, z, Integer.MIN_VALUE, generator, level, randomState, useCacheOnly);
    }

    private static boolean laysLake(WatershedLake lake, int seaLevel) {
        return lake.waterY > seaLevel - 1 || lake.dig > WatershedLake.SHAVE;
    }

    private static boolean lakeDrainsHere(WatershedLake lake, double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || randomState == null) return true;
        final int ci = Mth.floor(x) >> CoarseHeightField.CELL_SHIFT;
        final int cj = Mth.floor(z) >> CoarseHeightField.CELL_SHIFT;
        for (int dj = 0; dj <= 1; dj++) {
            for (int di = 0; di <= 1; di++) {
                if (lake.isLakeCell(ci + di, cj + dj)) continue;
                if (CoarseHeightField.sample(ci + di, cj + dj, generator, randomState) <= lake.waterY) return true;
            }
        }
        return false;
    }

    public static WatershedLake findLake(double x, double z, int columnY,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean useCacheOnly) {
        return findLake(x, z, columnY, generator, level, randomState, useCacheOnly, false);
    }

    static WatershedLake findLake(double x, double z, int columnY,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean useCacheOnly, boolean dugShores) {
        final int bx = Mth.floor(x), bz = Mth.floor(z);
        final int mrx = Math.floorDiv(bx, WATERSHED_REGION_SIZE);
        final int mrz = Math.floorDiv(bz, WATERSHED_REGION_SIZE);
        final boolean known = columnY != Integer.MIN_VALUE;
        WatershedLake insideAny = null;
        WatershedLake nearby = null;
        WatershedLake dugShore = null;
        for (int dx = -WATERSHED_SCAN_RADIUS; dx <= WATERSHED_SCAN_RADIUS; dx++) {
            for (int dz = -WATERSHED_SCAN_RADIUS; dz <= WATERSHED_SCAN_RADIUS; dz++) {
                final int nrx = mrx + dx, nrz = mrz + dz;
                WatershedRegionData data = useCacheOnly
                        ? WATERSHED_REGION_CACHE.get(getRegionKey(nrx, nrz))
                        : getWatershedRegion(nrx, nrz, generator, level, randomState);
                if (data == null || data.lakes.isEmpty()) continue;
                for (WatershedLake lake : data.lakes) {
                    final boolean holds = !known || lake.holdsColumn(columnY);
                    if (lake.inside(x, z)) {
                        if (holds) return lake;
                        if (insideAny == null) insideAny = lake;
                    } else if (nearby == null && lake.covers(x, z) && holds) {
                        nearby = lake;
                    } else if (dugShores && dugShore == null && known && columnY > lake.waterY
                            && lake.dig > WatershedLake.SHAVE && lake.covers(x, z)) {
                        dugShore = lake;
                    }
                }
            }
        }
        return nearby != null ? nearby : insideAny != null ? insideAny : dugShore;
    }

    static WatershedLake lakeBeside(double x, double z, int columnY,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean useCacheOnly) {
        final int mrx = Math.floorDiv(Mth.floor(x), WATERSHED_REGION_SIZE);
        final int mrz = Math.floorDiv(Mth.floor(z), WATERSHED_REGION_SIZE);
        for (int dx = -WATERSHED_SCAN_RADIUS; dx <= WATERSHED_SCAN_RADIUS; dx++) {
            for (int dz = -WATERSHED_SCAN_RADIUS; dz <= WATERSHED_SCAN_RADIUS; dz++) {
                final WatershedRegionData data = useCacheOnly
                        ? WATERSHED_REGION_CACHE.get(getRegionKey(mrx + dx, mrz + dz))
                        : getWatershedRegion(mrx + dx, mrz + dz, generator, level, randomState);
                if (data == null || data.lakes.isEmpty()) continue;
                for (WatershedLake lake : data.lakes) {
                    if (columnY < lake.waterY && lake.near(x, z, LakeHollows.LAKE_HOLLOW_REACH)) return lake;
                }
            }
        }
        return null;
    }

    static boolean lakeWaterAt(WatershedLake lake, double x, double z, int ground,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (lake.bedColumn(ground) && lake.inside(x, z)) return true;
        if (lake.holdsColumn(ground) && lake.touchesInside(x, z)) return true;
        return ground < lake.waterY && lake.holdsColumn(ground) && lake.shoreBand(x, z)
                && !lake.touchesInside(x, z) && !lakeDrainsHere(lake, x, z, generator, randomState);
    }

    private static int lipDepth(WatershedLake lake, boolean covered, LakeHollows.Kind hollow, double x, double z,
            int ground, net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final int lip = covered ? LAKE_SHORE_LIP : -1;
        if (hollow == LakeHollows.Kind.OPEN && ground < lake.waterY - lip
                && ground >= lake.waterY - LakeHollows.LAKE_HOLD_DEPTH
                && LakeHollows.besideWater(lake, Mth.floor(x), Mth.floor(z), generator, randomState)) {
            return LakeHollows.LAKE_HOLD_DEPTH;
        }
        return lip;
    }

    static long regionGeneration() {
        return REGION_GENERATION.get();
    }

    public static boolean isInLake(double x, double z) {
        return findLake(x, z, null, null, null, true) != null;
    }

    public static boolean isInOrBesideLake(double x, double z) {
        return isInLake(x, z) || lakeBeside(x, z, Integer.MIN_VALUE, null, null, null, true) != null;
    }

    public static int getTrueTerrainHeight(double x, double z, int baseHeight, int seaLevel) {
        RiverCarving carving = calculateRiverCarving(x, z, baseHeight, seaLevel, null, null, null);
        if (carving.modifies && carving.waterLevel > carving.targetY && !carving.isWatershedWall) {
            return carving.waterLevel;
        }
        return carving.targetY;
    }

    private static final double HEAD_TAPER = 100.0;

    private static final double HEAD_WATER_FROM = 20.0;

    private static final double HEAD_TIP_POWER = 1.6;
    private static final double HEAD_DEPTH_PER_RADIUS = 0.9;
    private static final double HEAD_CLOSE = 0.45;
    private static final double HEAD_SLOT = 0.45;

    private static final double DELTA_RAGGED = 0.2;
    private static final double DELTA_RAGGED_SCALE = 32.0;

    private static final double DELTA_PLAIN = 24.0;
    private static final double DELTA_PLAIN_SLOPE = 0.15;

    private static final int DELTA_FLAT_ABOVE = 2;

    public static double waterRadiusAt(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z) {
        return edge.delta() ? edge.deltaRadius(x, z) : normalRiverWaterRadius(edge.widthAt(x, z));
    }

    public static double routeWaterRadius(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z) {
        final double r = waterRadiusAt(edge, x, z);
        if (edge.sourceEdge() || edge.delta()) return r;
        final double dist = Math.hypot(x - edge.source().x(), z - edge.source().y());
        if (dist >= HEAD_TAPER) return r;
        final double taper = Math.max(0.0, (dist - HEAD_WATER_FROM) / (HEAD_TAPER - HEAD_WATER_FROM));
        return r * (Config.NORMAL_RIVER_TUNNELS.get() ? taper : Math.pow(taper, HEAD_TIP_POWER));
    }

    public static boolean inHeadTaper(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z) {
        return !edge.sourceEdge() && !edge.delta() && Math.hypot(x - edge.source().x(), z - edge.source().y()) < HEAD_TAPER;
    }

    private static double deltaRagged(double x, double z) {
        final double n = 0.65 * RIVER_BED_NOISE.get(x / DELTA_RAGGED_SCALE + 311.0, z / DELTA_RAGGED_SCALE - 173.0)
                + 0.35 * RIVER_BED_NOISE.get(x / (0.4 * DELTA_RAGGED_SCALE) - 59.0, z / (0.4 * DELTA_RAGGED_SCALE) + 97.0);
        return 1.0 + DELTA_RAGGED * Mth.clamp(n * 1.6, -1.0, 1.0);
    }

    private static final double HEAD_FEED_TIP = 3.0;
    private static final double HEAD_FEED_TIP_MIN = 10.0;
    private static final double HEAD_FEED_WIDEN = 60.0;

    private static final double HEAD_FEED_MARGIN = 1.15;

    private record HeadFeed(double distToSource, double width) {
    }

    private static final HeadFeed NO_FEED = new HeadFeed(Double.MAX_VALUE, 0.0);

    private static double gorgeWaterRadius(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final double r = routeWaterRadius(edge, x, z);
        if (edge.sourceEdge() || edge.delta()) return r;
        final double dist = Math.hypot(x - edge.source().x(), z - edge.source().y());
        if (dist >= HEAD_TAPER + HEAD_FEED_WIDEN) return r;
        final HeadFeed feed = headFeed(edge, generator, level, randomState);
        return feed == NO_FEED ? r : fedWaterRadius(edge, x, z, dist, feed);
    }

    private static double flowWaterRadius(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z) {
        final double r = routeWaterRadius(edge, x, z);
        if (edge.sourceEdge() || edge.delta() || Config.NORMAL_RIVER_TUNNELS.get()) return r;
        final double dist = Math.hypot(x - edge.source().x(), z - edge.source().y());
        if (dist >= HEAD_TAPER + HEAD_FEED_WIDEN) return r;
        final HeadFeed feed = headFeed(edge, null, null, null, true);
        return feed == NO_FEED ? r : fedWaterRadius(edge, x, z, dist, feed);
    }

    private static double fedWaterRadius(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z,
            double dist, HeadFeed feed) {
        final double maxRadius = waterRadiusAt(edge, x, z);
        final double fedFrom = feed.distToSource() - feed.width();
        final double widen = Math.max(HEAD_FEED_WIDEN, HEAD_TAPER - fedFrom);
        if (dist >= fedFrom + widen) return maxRadius;
        final double least = Math.min(maxRadius, feed.width() * HEAD_FEED_MARGIN);
        if (dist >= fedFrom) return least + (maxRadius - least) * Math.pow((dist - fedFrom) / widen, HEAD_TIP_POWER);
        final double tipLen = Math.max(HEAD_FEED_TIP_MIN, HEAD_FEED_TIP * least);
        return least * Math.pow(Mth.clamp((dist - (fedFrom - tipLen)) / tipLen, 0.0, 1.0), HEAD_TIP_POWER);
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, HeadFeed> HEAD_FEEDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static HeadFeed headFeed(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || randomState == null) return NO_FEED;
        return headFeed(edge, generator, level, randomState, false);
    }

    private static HeadFeed headFeed(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean cacheOnly) {
        final double vx = edge.source().x(), vz = edge.source().y();
        final long key = (((long) Math.round(vx)) << 32) ^ (Math.round(vz) & 0xFFFFFFFFL);
        HeadFeed feed = HEAD_FEEDS.get(key);
        if (feed != null) return feed;

        final double reach = HEAD_TAPER + normalRiverWaterRadius(edge.drainWidth()) + 2.0;
        final int r0x = Math.floorDiv((int) Math.floor(vx - reach), WATERSHED_REGION_SIZE);
        final int r1x = Math.floorDiv((int) Math.ceil(vx + reach), WATERSHED_REGION_SIZE);
        final int r0z = Math.floorDiv((int) Math.floor(vz - reach), WATERSHED_REGION_SIZE);
        final int r1z = Math.floorDiv((int) Math.ceil(vz + reach), WATERSHED_REGION_SIZE);

        if (cacheOnly) {
            for (int rz = r0z; rz <= r1z; rz++) {
                for (int rx = r0x; rx <= r1x; rx++) {
                    if (!WATERSHED_REGION_CACHE.containsKey(getRegionKey(rx, rz))) return NO_FEED;
                }
            }
        }
        double bestD = Double.MAX_VALUE, width = 0.0;
        boolean complete = true;
        for (int rz = r0z; rz <= r1z; rz++) {
            for (int rx = r0x; rx <= r1x; rx++) {
                final WatershedRegionData data = cacheOnly ? WATERSHED_REGION_CACHE.get(getRegionKey(rx, rz))
                        : getWatershedRegion(rx, rz, generator, level, randomState);

                if (!WATERSHED_REGION_CACHE.containsKey(getRegionKey(rx, rz))) complete = false;
                if (data == null) continue;
                for (WatershedPath p : data.allPaths) {
                    if (!p.hitMainRiver || p.nodeCount < 1) continue;
                    final double mx = p.segmentsX[0], mz = p.segmentsZ[0];
                    final double d = Math.hypot(mx - vx, mz - vz);
                    if (d >= HEAD_TAPER) continue;
                    final double stampR = waterRadiusAt(edge, mx, mz) + 2.0;
                    if (edge.fractal().intersectDistance(mx, mz) > stampR * stampR) continue;
                    if (d < bestD) bestD = d;
                    if (p.segmentWidths[0] > width) width = p.segmentWidths[0];
                }
            }
        }
        feed = bestD < Double.MAX_VALUE ? new HeadFeed(bestD, width) : NO_FEED;
        if (complete) HEAD_FEEDS.putIfAbsent(key, feed);
        return feed;
    }

    private static final double RIVER_SPLIT_MIN_LENGTH = 70.0;
    private static final double RIVER_SPLIT_END = 0.08;
    private static final double RIVER_SPLIT_SHOAL = 0.55;
    private static final double RIVER_SPLIT_BAR = 5.0;
    private static final double RIVER_SPLIT_RISE = 0.4;
    private static final int RIVER_SPLIT_TOP = 3;

    private static final double RIVER_SPLIT_ONE = 0.6;
    private static final double RIVER_SPLIT_MANY = 0.46;

    private static final double RIVER_SPLIT_OFF_ONE = 0.1;
    private static final double RIVER_SPLIT_OFF_MANY = 0.34;

    private static final double RIVER_SPLIT_END_MIN = 14.0;

    private static final double RIVER_SPLIT_MANY_FROM = 16.0;

    private static final double RIVER_SPLIT_SMALL = 0.13;
    private static final double RIVER_SPLIT_SMALL_MORE = 0.12;
    private static final double RIVER_SPLIT_OFF_SMALL = 0.1;
    private static final double RIVER_SPLIT_OFF_SMALL_MORE = 0.45;

    private static final double RIVER_SPLIT_LAND_IN = 2.0;
    private static final double RIVER_SPLIT_TREE_IN = 3.0;

    private static final double RIVER_SPLIT_FLOW_REACH = 8.0;
    private static final double RIVER_SPLIT_NONE = -1.0E9;

    private static long riverSplitHash(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge) {
        long h = WORLD_SEED ^ 0x5DA7E1B1L;
        h = it.unimi.dsi.fastutil.HashCommon.mix(h + Math.round(edge.source().x()));
        h = it.unimi.dsi.fastutil.HashCommon.mix(h + Math.round(edge.source().y()));
        h = it.unimi.dsi.fastutil.HashCommon.mix(h + Math.round(edge.drain().x()));
        return it.unimi.dsi.fastutil.HashCommon.mix(h + Math.round(edge.drain().y()));
    }

    private static boolean riverSplits(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge) {
        final double chance = Config.RIVER_SPLITS.get().chance;
        if (chance <= 0.0 || !edge.sourceEdge() || edge.delta()) return false;
        if (Math.hypot(edge.drain().x() - edge.source().x(), edge.drain().y() - edge.source().y()) < RIVER_SPLIT_MIN_LENGTH) {
            return false;
        }
        return (riverSplitHash(edge) >>> 11) * 0x1.0p-53 < chance;
    }

    private static double riverSplitDepth(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge, double x, double z, double radius) {
        final double[] at = new double[6];
        edge.fractal().nearestAlong(x, z, at);

        if (at[4] <= 0.0 || at[4] >= at[5]) return RIVER_SPLIT_NONE;
        final double lateral = (z - at[1]) * at[2] - (x - at[0]) * at[3];
        final double along = at[4] / at[5];
        final long h = riverSplitHash(edge);
        final double end = Math.max(RIVER_SPLIT_END, RIVER_SPLIT_END_MIN / at[5]);
        final double span = 1.0 - 2.0 * end;

        final int kind = (int) ((h >>> 8 & 0xFF) * 10 / 256);
        final boolean scatter = kind >= 7;
        final int islands = scatter ? 4 + (int) (h >>> 17 & 3L)
                : kind < 4 || radius < RIVER_SPLIT_MANY_FROM ? 1 : 2 + (int) (h >>> 16 & 1L);
        final double side = (h & 1L) == 0L ? 1.0 : -1.0;

        final double ragged = 1.0 + 0.25 * RIVER_BED_NOISE.get(x / 14.0 + 41.0, z / 14.0 - 17.0);
        final double wander = 0.08 * radius * RIVER_BED_NOISE.get(x / 40.0 - 71.0, z / 40.0 + 23.0);
        double best = RIVER_SPLIT_NONE;
        for (int i = 0; i < islands; i++) {
            final long k = it.unimi.dsi.fastutil.HashCommon.mix(h + i + 1L);
            final double a = (k >>> 44) * 0x1.0p-20, b = (k >>> 24 & 0xFFFFFL) * 0x1.0p-20, c = (k >>> 4 & 0xFFFFFL) * 0x1.0p-20;
            final double turn = side * (i % 2 == 0 ? 1.0 : -1.0);
            final double middle, half, width, off;
            if (scatter) {
                middle = end + span * (i + 0.5 + 0.7 * (a - 0.5)) / islands;
                half = span / islands * (0.4 + 0.5 * b);
                width = radius * (RIVER_SPLIT_SMALL + RIVER_SPLIT_SMALL_MORE * c);
                off = turn * radius * (RIVER_SPLIT_OFF_SMALL + RIVER_SPLIT_OFF_SMALL_MORE * a);
            } else if (islands == 1) {
                middle = end + span / 2.0;
                half = span / 2.0;
                width = RIVER_SPLIT_ONE * radius;
                off = turn * RIVER_SPLIT_OFF_ONE * radius;
            } else {
                middle = end + span * (i + 0.5) / islands;
                half = span / (2.0 * islands) * 1.5;
                width = RIVER_SPLIT_MANY * radius;
                off = turn * RIVER_SPLIT_OFF_MANY * radius;
            }
            final double u = (along - middle) / half;
            final double across = Math.abs(lateral - off - wander);
            final double depth;
            if (Math.abs(u) >= 1.0) {
                depth = -Math.hypot(across, (Math.abs(u) - 1.0) * half * at[5]);
            } else {
                final double round = 1.0 - u * u;

                depth = width * ragged * (u < 0.0 ? Math.sqrt(round) : Math.pow(round, 1.4)) - across - 1.0;
            }
            best = Math.max(best, depth);
        }
        return best;
    }

    private static boolean onRiverSplit(double x, double z, double in) {
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge edge : MainRivers.riversAt(x, z)) {
            if (riverSplits(edge) && riverSplitDepth(edge, x, z, waterRadiusAt(edge, x, z)) >= in) return true;
        }
        return false;
    }

    public static boolean riverSplitLand(double x, double z) {
        return onRiverSplit(x, z, RIVER_SPLIT_LAND_IN);
    }

    public static boolean riverIsland(double x, double z) {
        return onRiverSplit(x, z, 0.0);
    }

    public static boolean riverSplitHoldsTree(double x, double z) {
        return onRiverSplit(x, z, RIVER_SPLIT_TREE_IN);
    }

    private static net.minecraft.world.phys.Vec3 aroundRiverSplit(double x, double z, net.minecraft.world.phys.Vec3 flow) {
        if (flow.lengthSqr() < 1.0E-6 || Config.RIVER_SPLITS.get().chance <= 0.0) return flow;
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge edge : MainRivers.riversAt(x, z)) {
            if (!riverSplits(edge)) continue;
            final double r = waterRadiusAt(edge, x, z);
            final double depth = riverSplitDepth(edge, x, z, r);
            if (depth <= -RIVER_SPLIT_FLOW_REACH) continue;
            final double gx = riverSplitDepth(edge, x + 1.0, z, r) - riverSplitDepth(edge, x - 1.0, z, r);
            final double gz = riverSplitDepth(edge, x, z + 1.0, r) - riverSplitDepth(edge, x, z - 1.0, r);
            final double gl = Math.hypot(gx, gz);
            if (gl < 1.0E-6) continue;
            final double nx = gx / gl, nz = gz / gl;
            final double into = flow.x * nx + flow.z * nz;
            if (into <= 0.0) continue;
            final double near = Mth.clamp(1.0 + depth / RIVER_SPLIT_FLOW_REACH, 0.0, 1.0);
            final double fx = flow.x - near * into * nx, fz = flow.z - near * into * nz;
            final double fl = Math.hypot(fx, fz);
            if (fl < 1.0E-6) continue;
            final double speed = flow.horizontalDistance();
            return new net.minecraft.world.phys.Vec3(fx / fl * speed, flow.y, fz / fl * speed);
        }
        return flow;
    }

    private static final double GORGE_FROM = 76.0;
    private static final double GORGE_RAMP = 10.0;

    private static final double GORGE_GRADE_SLOT = 5.0;
    private static final double GORGE_GRADE_TERRACED = 2.2;
    private static final double GORGE_BENCH_RISE = 20.0;
    private static final double GORGE_BENCH_MAX = 9.0;
    private static final double GORGE_BENCH_WARP = 6.0;
    private static final int GORGE_BAND_MIN = 3;
    private static final int GORGE_BAND_MAX = 8;
    private static final double GORGE_WARP = 3.0;

    private static final double GORGE_STEEP_RUN = 0.4;

    private static final double GORGE_ROUGH = 1.2;

    private static final double GORGE_STRIP_MAX = 3.0;

    private static final double GORGE_REACH = 48.0;

    private static final double GORGE_HOLD = 4.0;

    private static final double GORGE_SEAL = 2.0;

    private static final class GorgeProfile {
        static final int MAX_BENCHES = 16;
        double floor, floorRise, grade, rough;
        int benches;
        final double[] benchH = new double[MAX_BENCHES], benchW = new double[MAX_BENCHES], benchT = new double[MAX_BENCHES];

        boolean lastSteep;

        double setback(double h) {
            if (h <= 0.0) return 0.0;
            if (h <= floorRise) return floor * h / floorRise;
            double d = floor + (h - floorRise) / grade;
            for (int b = 0; b < benches; b++) {
                final double t = Mth.clamp((h - benchH[b]) / benchT[b] + 0.5, 0.0, 1.0);
                d += benchW[b] * t * t * (3.0 - 2.0 * t);
            }
            return d;
        }
    }

    private static final ThreadLocal<GorgeProfile> GORGE_PROFILE = ThreadLocal.withInitial(GorgeProfile::new);

    private static int gorgeWallY(double x, double z, double dOut, int originalY, int waterY, double depth) {
        final GorgeProfile p = GORGE_PROFILE.get();
        p.lastSteep = false;
        final double style = Mth.clamp(0.65 + 0.9 * RIVER_BED_NOISE.get(x * 0.004 + 1234.5, z * 0.004 - 987.6),
                0.0, 1.0);
        p.rough = GORGE_ROUGH * (0.7 * RIVER_BED_NOISE.get(x * 0.12 + 300.0, z * 0.12 - 300.0)
                + 0.3 * RIVER_BED_NOISE.get(x * 0.33 - 500.0, z * 0.33 + 500.0));
        final double d = dOut + p.rough;
        if (d >= GORGE_REACH) return originalY;

        p.floor = Math.max(1.0, GORGE_STRIP_MAX
                * Mth.clamp(-0.1 + style + 0.4 * RIVER_BED_NOISE.get(x * 0.05 - 40.0, z * 0.05 + 40.0), 0.0, 1.0));
        p.floorRise = 1.0;

        p.benches = 0;
        double benchSum = 0.0, rise = 0.0;
        for (int b = 0; b < GorgeProfile.MAX_BENCHES; b++) {
            rise += GORGE_BENCH_RISE * (0.7 + 0.6 * ((gorgeHash(b, 0x2545F4914F6CDD1DL) >>> 11) * 0x1.0p-53));
            final double h = rise + GORGE_BENCH_WARP * RIVER_BED_NOISE.get(x * 0.015 + b * 41.3, z * 0.015 - b * 23.9);
            if (h >= depth) break;
            final double w = Mth.clamp(style * (1.5 + 6.0 * (0.5 + 0.5
                    * RIVER_BED_NOISE.get(x * 0.025 - b * 17.7, z * 0.025 + b * 29.1))), 0.0, GORGE_BENCH_MAX);
            p.benchH[p.benches] = Math.max(p.floorRise + 2.0, h);
            p.benchW[p.benches] = w;
            p.benchT[p.benches] = 2.5 + 2.5 * style;
            p.benches++;
            benchSum += w;
        }

        final double room = GORGE_REACH - 4.0 - p.floor;
        if (benchSum > 0.5 * room) {
            final double k = 0.5 * room / benchSum;
            for (int b = 0; b < p.benches; b++) p.benchW[b] *= k;
            benchSum *= k;
        }
        p.grade = Math.max(Mth.lerp(style, GORGE_GRADE_SLOT, GORGE_GRADE_TERRACED),
                (depth - p.floorRise) / Math.max(4.0, room - benchSum));

        final int hMax = originalY - waterY;
        int h = 0;
        while (h < hMax && p.setback(h + 1) <= d) h++;
        if (h >= hMax) return originalY;
        p.lastSteep = p.setback(h + 1) - p.setback(h) < GORGE_STEEP_RUN;
        return waterY + h;
    }

    public static final double LOWLAND_CORRIDOR_REACH = GORGE_REACH + GORGE_ROUGH + 1.0;
    public static final double LOWLAND_FALL_MIN = 6.0;

    private static final double GORGE_RIM_OUTLET = 0.5;
    private static final double GORGE_RIM_OUTLET_MIN = 2.5;
    private static final double GORGE_RIM_FUNNEL = 2.0;
    private static final double GORGE_RIM_FUNNEL_MIN = 8.0;

    private static int rimLevel(WatershedPath path, int node, int seaLevel) {
        int k = node;
        while (k < path.nodeCount && path.segmentsY[k] <= seaLevel + 1) k++;
        if (k >= path.nodeCount) return Integer.MIN_VALUE;
        final int lip = path.segmentsY[k];
        return lip >= seaLevel + 1 + LOWLAND_FALL_MIN ? lip : Integer.MIN_VALUE;
    }

    private static double rimDistance(WatershedPath path, int node, double x, double z, int seaLevel, double reach) {
        if (path.segmentsY[node] <= seaLevel + 1) return Double.NaN;
        final int span = (int) Math.ceil(reach / WatershedPath.NODE_SPACING) + 1;
        int k = node;
        while (k > 0 && node - k <= span && path.segmentsY[k - 1] > seaLevel + 1) k--;
        if (k == 0 || node - k > span || path.segmentsY[k] < seaLevel + 1 + LOWLAND_FALL_MIN) return Double.NaN;

        double t = 0.0;
        if (node + 1 < path.validNodeCount) {
            final double dx = path.segmentsX[node + 1] - path.segmentsX[node];
            final double dz = path.segmentsZ[node + 1] - path.segmentsZ[node];
            final double len2 = dx * dx + dz * dz;
            if (len2 > 0.0) t = Mth.clamp(((x - path.segmentsX[node]) * dx + (z - path.segmentsZ[node]) * dz) / len2, 0.0, 1.0);
        }
        final double d = (node - k + t) * WatershedPath.NODE_SPACING;
        return d <= reach ? d : Double.NaN;
    }

    private static double funnelDistance(WatershedPath path, int node, int start, double x, double z, double reach) {
        if (node < start) return Double.NaN;
        double t = 0.0;
        if (node + 1 < path.validNodeCount) {
            final double dx = path.segmentsX[node + 1] - path.segmentsX[node];
            final double dz = path.segmentsZ[node + 1] - path.segmentsZ[node];
            final double len2 = dx * dx + dz * dz;
            if (len2 > 0.0) t = Mth.clamp(((x - path.segmentsX[node]) * dx + (z - path.segmentsZ[node]) * dz) / len2, 0.0, 1.0);
        }
        final double d = (node - start + t) * WatershedPath.NODE_SPACING;
        return d <= reach ? d : Double.NaN;
    }

    private static final double RAVINE_SINK = 4.0;
    private static final int RAVINE_BACK = 15;
    private static final int RAVINE_POOL = 5;
    private static final int RAVINE_FALL_MIN = 3;
    private static final double RAVINE_GRADE = 1.0;
    private static final double RAVINE_FALL_GRADE = 2.5;
    private static final double RAVINE_BANK = 3.0;
    private static final double RAVINE_LEDGE = 2.0;
    private static final double RAVINE_LEDGE_SPREAD = 1.0;
    private static final double RAVINE_SIDE = 30.0;

    private static final int RAVINE_RIM_HOLD = 1;
    private static final double RAVINE_RIM = 3.0;

    private static final double RAVINE_NEAR = 2.0;

    private static final int RAVINE_RIM_BUILD = 3;

    private static final double RAVINE_BEND = 2.0;
    private static final double RAVINE_BEND_IN = 8.0;
    private static final double RAVINE_ROUGH = 1.2;
    private static final double RAVINE_ROUGH_SCALE = 0.08;
    private static final double RAVINE_LIP_ROUGH = 0.5;

    private static final double RAVINE_CURVE = 6.0;
    private static final double RAVINE_CURVE_FULL = 40.0;
    private static final double RAVINE_CURVE_WAVE = 44.0;
    private static final double RAVINE_CURVE_OUT = 16.0;
    private static final double RAVINE_MAX = 64.0;

    private static final double RAVINE_WATER_MIN = 2.0;

    private static final double RAVINE_MAX_PAST_HEAD = 128.0;

    private static final int RAVINE_LAST = 8;
    private static final double RAVINE_INTO = 8.0;

    static final class Ravine {
        final double px, pz, ux, uz, length, width;

        final int start, top;

        final double[] from;
        final int[] level;

        final double curveAmp, curvePhase;

        Ravine(double px, double pz, double ux, double uz, double length, double width, int start, int top,
                double[] from, int[] level, double curveAmp, double curvePhase) {
            this.px = px;
            this.pz = pz;
            this.ux = ux;
            this.uz = uz;
            this.length = length;
            this.width = width;
            this.start = start;
            this.top = top;
            this.from = from;
            this.level = level;
            this.curveAmp = curveAmp;
            this.curvePhase = curvePhase;
        }

        double curve(double along) {
            return ravineCurve(curveAmp, curvePhase, along, length);
        }

        double lateral(double ox, double oz, double along) {
            return ox * uz - oz * ux - curve(along);
        }

        double centre(double along) {
            return curve(along) + RAVINE_BEND * ravineSway(along, length) * RIVER_BED_NOISE.get(
                    (px + ux * along) * 0.06 + 71.0, (pz + uz * along) * 0.06 - 29.0);
        }

        double across(double x, double z, double ox, double oz, double along) {
            final double bend = RAVINE_BEND * ravineSway(along, length) * RIVER_BED_NOISE.get(
                    (px + ux * along) * 0.06 + 71.0, (pz + uz * along) * 0.06 - 29.0);
            return Math.max(0.0, Math.abs(lateral(ox, oz, along) - bend) + RAVINE_ROUGH
                    * RIVER_BED_NOISE.get(x * RAVINE_ROUGH_SCALE - 13.0, z * RAVINE_ROUGH_SCALE + 57.0));
        }

        int poolLevel(double x, double z, double along) {
            return levelAt(along + RAVINE_LIP_ROUGH * RIVER_BED_NOISE.get(x * 0.27 + 31.0, z * 0.27 - 83.0));
        }

        int waterAt(double x, double z) {
            final double ox = x - px, oz = z - pz;
            final double along = ox * ux + oz * uz;
            if (along < -RAVINE_NEAR || along > length + RAVINE_INTO || across(x, z, ox, oz, along) >= width) {
                return Integer.MIN_VALUE;
            }
            return poolLevel(x, z, along);
        }

        int levelAt(double t) {
            int i = from.length - 1;
            while (i > 0 && t < from[i]) i--;
            return level[i];
        }

        double sideLine(double t) {
            int j = from.length - 1;
            while (j > 0 && t < from[j]) j--;
            double line = j == 0 ? top : level[j];
            for (int i = 0; i < j; i++) {
                line = Math.max(line, (i == 0 ? top : level[i]) - RAVINE_FALL_GRADE * (t - from[i + 1]));
            }
            return line;
        }
    }

    private static final Ravine NO_RAVINE = new Ravine(0, 0, 0, 0, 0, 0, 0, 0, new double[] { 0.0 }, new int[] { 0 }, 0, 0);

    private static double ravineCurve(double amp, double phase, double along, double length) {
        if (amp <= 0.0) return 0.0;
        return amp * ravineSway(along, length) * Math.sin(2.0 * Math.PI * along / RAVINE_CURVE_WAVE + phase);
    }

    private static double ravineSway(double along, double length) {
        final double in = Mth.clamp(along / RAVINE_BEND_IN, 0.0, 1.0);
        final double out = Mth.clamp((length - along) / RAVINE_CURVE_OUT, 0.0, 1.0);
        return in * in * (3.0 - 2.0 * in) * out * out * (3.0 - 2.0 * out);
    }

    private static boolean feedsDelta(WatershedPath path,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (path.deltaFed != 0) return path.deltaFed == 2;
        final boolean fed = walksToDelta(path, new java.util.ArrayList<>(), generator, level, randomState);
        path.deltaFed = (byte) (fed ? 2 : 1);
        return fed;
    }

    private static boolean walksToDelta(WatershedPath path, java.util.List<WatershedLake> through,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        WatershedPath p = path;
        for (int guard = 0; guard < 64; guard++) {
            if (p.deltaFed != 0) return p.deltaFed == 2;
            if (p.hitMainRiver) return mouthInDelta(p, generator, level, randomState);
            final WatershedPath next = joins(p, generator, level, randomState);
            if (next == null) return lakeFeedsDelta(p, through, generator, level, randomState);
            p = next;
        }
        return false;
    }

    private static WatershedPath joins(WatershedPath p,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final WatershedPath down = downstreamOf(p, generator, level, randomState);
        if (down != null) return down;
        final double mx = p.segmentsX[0], mz = p.segmentsZ[0];
        final int colX = Mth.floor(mx), colZ = Mth.floor(mz);
        final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE), homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
        WatershedPath best = null, after = null;
        double bestOut = DELTA_FED_JOIN, afterGap = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final WatershedRegionData data = getWatershedRegion(homeRx + dx, homeRz + dz, generator, level, randomState);
                for (WatershedPath q : data.grid.getPathsNear(colX, colZ, homeRx + dx, homeRz + dz)) {
                    if (q == p || drainsInto(q, p) || q.validNodeCount < 1) continue;

                    final double together = 0.5 * (p.segmentWidths[0] + q.segmentWidths[0]) + DELTA_FED_JOIN * 2.0;
                    final double ex = q.segmentsX[0] - mx, ez = q.segmentsZ[0] - mz;
                    if (ex * ex + ez * ez < together * together) continue;

                    final int s = q.validNodeCount - 1;
                    final double gap = Math.hypot(q.segmentsX[s] - mx, q.segmentsZ[s] - mz);
                    if (gap < together && q.segmentsY[s] <= p.segmentsY[0] && gap < afterGap) {
                        afterGap = gap;
                        after = q;
                    }
                    final WatershedPath.RiverNodeData d = q.getClosestNodeData(mx, mz);
                    if (d.distance == Double.MAX_VALUE) continue;
                    final double out = d.distance - 0.5 * d.width;
                    if (out < bestOut) {
                        bestOut = out;
                        best = q;
                    }
                }
            }
        }
        return best != null ? best : after;
    }

    private static final double DELTA_FED_JOIN = 3.0;

    private static boolean drainsInto(WatershedPath q, WatershedPath p) {
        final double qx = q.segmentsX[0], qz = q.segmentsZ[0];
        for (int i = 1; i < p.validNodeCount; i++) {
            final double ex = p.segmentsX[i] - qx, ez = p.segmentsZ[i] - qz;
            if (ex * ex + ez * ez < 2.25) return true;
        }
        return false;
    }

    private static boolean mouthInDelta(WatershedPath p,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final double mx = p.segmentsX[0], mz = p.segmentsZ[0];
        MainRivers.findNearest(mx, mz, generator, level, randomState);
        com.AdamNew.dynamicwaters.river.RiverGraphEdge nearest = null;
        double best = DELTA_FED_REACH;
        final double[] on = new double[2];
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge e : MainRivers.riversAt(mx, mz)) {
            final double out = Math.sqrt(e.fractal().nearestPoint(mx, mz, on)) - waterRadiusAt(e, on[0], on[1]);
            if (out < best) {
                best = out;
                nearest = e;
            }
        }
        return nearest != null && nearest.delta();
    }

    private static final double DELTA_FED_REACH = 16.0;

    private static boolean lakeFeedsDelta(WatershedPath p, java.util.List<WatershedLake> through,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final WatershedLake lake = findLake(p.segmentsX[0], p.segmentsZ[0], generator, level, randomState, false);
        if (lake == null) return false;

        for (WatershedLake l : through) {
            if (l.waterY == lake.waterY && l.minCellX == lake.minCellX && l.minCellZ == lake.minCellZ) return false;
        }
        through.add(lake);
        final int reach = CoarseHeightField.CELL_SIZE;
        final int rx0 = Math.floorDiv(Mth.floor(lake.minX) - reach, WATERSHED_REGION_SIZE);
        final int rx1 = Math.floorDiv(Mth.floor(lake.maxX) + reach, WATERSHED_REGION_SIZE);
        final int rz0 = Math.floorDiv(Mth.floor(lake.minZ) - reach, WATERSHED_REGION_SIZE);
        final int rz1 = Math.floorDiv(Mth.floor(lake.maxZ) + reach, WATERSHED_REGION_SIZE);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                for (WatershedPath q : getWatershedRegion(rx, rz, generator, level, randomState).allPaths) {
                    if (q == p || q.validNodeCount < 2) continue;
                    final int s = q.validNodeCount - 1;
                    if (q.segmentsY[s] > lake.waterY || !lake.covers(q.segmentsX[s], q.segmentsZ[s])) continue;
                    if (walksToDelta(q, through, generator, level, randomState)) return true;
                }
            }
        }
        return false;
    }

    private static WatershedPath downstreamOf(WatershedPath p,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final double mx = p.segmentsX[0], mz = p.segmentsZ[0];
        final int colX = Mth.floor(mx), colZ = Mth.floor(mz);
        final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE), homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final WatershedRegionData data = getWatershedRegion(homeRx + dx, homeRz + dz, generator, level, randomState);
                for (WatershedPath q : data.grid.getPathsNear(colX, colZ, homeRx + dx, homeRz + dz)) {
                    if (q == p) continue;
                    for (int i = 1; i < q.validNodeCount; i++) {
                        final double ex = q.segmentsX[i] - mx, ez = q.segmentsZ[i] - mz;
                        if (ex * ex + ez * ez < 2.25) return q;
                    }
                }
            }
        }
        return null;
    }

    private static Ravine ravineOf(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        Ravine r = path.ravine;
        if (r == null) {
            r = buildRavine(path, seaLevel, generator, level, randomState);
            path.ravine = r;
            labDumpRavine(path, r);
        }
        return r == NO_RAVINE ? null : r;
    }

    private static Ravine buildRavine(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        int k = 0;
        while (k < path.validNodeCount && path.segmentsY[k] <= seaLevel + 1) k++;
        if (k == 0 || k >= path.validNodeCount || path.segmentsY[k] < seaLevel + 1 + LOWLAND_FALL_MIN) return NO_RAVINE;
        final double[] on = new double[2];
        com.AdamNew.dynamicwaters.river.RiverGraphEdge river = nearestRiver(path.segmentsX[k], path.segmentsZ[k],
                generator, level, randomState, on);
        if (river == null) return NO_RAVINE;

        int start = k;
        for (int j = k + 1; j < path.validNodeCount && j <= k + RAVINE_BACK; j++) {
            final double toWater = toRiverWater(river, path.segmentsX[j], path.segmentsZ[j], generator, level, randomState, on);
            if (toWater > RAVINE_MAX - 4.0) break;
            final int a = j - 1, b = Math.min(path.validNodeCount - 1, j + 1);
            final double tx = path.segmentsX[b] - path.segmentsX[a], tz = path.segmentsZ[b] - path.segmentsZ[a];
            final double tl = Math.hypot(tx, tz);
            if (tl < 1.0E-6) break;
            final double half = 1.3 * path.segmentWidths[j] + RAVINE_RIM;
            final double jx = path.segmentsX[j], jz = path.segmentsZ[j], nx = -tz / tl * half, nz = tx / tl * half;
            final double low = Math.min(ravineWall(river, jx, jz, seaLevel, generator, level, randomState, on),
                    Math.min(ravineWall(river, jx + nx, jz + nz, seaLevel, generator, level, randomState, on),
                            ravineWall(river, jx - nx, jz - nz, seaLevel, generator, level, randomState, on)));
            if (low >= path.segmentsY[j] + RAVINE_SINK) break;
            start = j;
        }
        final double px = path.segmentsX[start], pz = path.segmentsZ[start];
        final int lip = path.segmentsY[start];

        river = nearestRiver(px, pz, generator, level, randomState, on);
        if (river == null) return NO_RAVINE;
        final double cx = on[0], cz = on[1];
        final double dc = Math.hypot(cx - px, cz - pz);
        final double out = dc - gorgeWaterRadius(river, cx, cz, generator, level, randomState);
        final boolean pastHead = Math.sqrt(river.fractal().nearestPoint(px, pz, on)) < out;
        if (out < 2.0 || out > (pastHead ? RAVINE_MAX_PAST_HEAD : RAVINE_MAX)) return NO_RAVINE;
        final double ux = (cx - px) / dc, uz = (cz - pz) / dc;

        final double curveAmp = RAVINE_CURVE * Math.min(1.0, out / RAVINE_CURVE_FULL);
        final double curvePhase = 2.0 * Math.PI * (it.unimi.dsi.fastutil.HashCommon.mix(
                Double.doubleToLongBits(px) * 31L + Double.doubleToLongBits(pz)) >>> 11) * 0x1.0p-53;

        final int n = (int) Math.ceil(out);
        final double[] wall = new double[n + 1];

        double stream = 0.0;
        for (int j = k; j <= start; j++) stream = Math.max(stream, path.segmentWidths[j]);
        final double width = Math.max(GORGE_RIM_OUTLET_MIN, stream);
        final double wx = -uz * (width + RAVINE_RIM), wz = ux * (width + RAVINE_RIM);
        for (int i = 0; i <= n; i++) {
            final double bent = ravineCurve(curveAmp, curvePhase, i, out);
            final double qx = px + ux * i + uz * bent, qz = pz + uz * i - ux * bent;
            wall[i] = Math.min(ravineWall(river, qx, qz, seaLevel, generator, level, randomState, on),
                    Math.min(ravineWall(river, qx + wx, qz + wz, seaLevel, generator, level, randomState, on),
                            ravineWall(river, qx - wx, qz - wz, seaLevel, generator, level, randomState, on)));
        }

        final int water = seaLevel - 1;
        for (int i = Math.max(1, n - RAVINE_LAST); i <= n; i++) wall[i] = Math.min(wall[i], water);
        final double[] from = new double[n + 2];
        final int[] lv = new int[n + 2];
        int pools = 1;
        lv[0] = lip;
        int w = lip;
        for (int i = 1; i <= n && w > water; i++) {
            if (wall[i] - RAVINE_SINK >= w) continue;
            double low = Double.MAX_VALUE;
            for (int j = i; j <= Math.min(n, i + RAVINE_POOL); j++) low = Math.min(low, wall[j] - RAVINE_SINK);
            final int next = Math.max(water, Math.min(w - RAVINE_FALL_MIN, Mth.floor(low)));
            if (next >= w) continue;
            from[pools] = i - 0.5;
            lv[pools++] = next;
            w = next;
        }
        if (w > water) {
            from[pools] = out - 0.5;
            lv[pools++] = water;
        }
        int top = lip;
        for (int j = start; j < Math.min(path.validNodeCount, start + 4); j++) top = Math.max(top, path.segmentsY[j]);

        top = feedersTop(px, pz, width + 3.0 * WatershedPath.NODE_SPACING, top, generator, level, randomState);
        return new Ravine(px, pz, ux, uz, out, width, start, top, java.util.Arrays.copyOf(from, pools),
                java.util.Arrays.copyOf(lv, pools), curveAmp, curvePhase);
    }

    private static boolean atRavineRim(WatershedContext ctx, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (generator == null || level == null || randomState == null) return false;

        Ravine r = entersGorge(ctx.bestPath, seaLevel, generator, level, randomState)
                ? ravineOf(ctx.bestPath, seaLevel, generator, level, randomState) : null;
        int start = r != null ? r.start : 0;
        if (r == null && feederRavine(ctx.bestPath, seaLevel, generator, level, randomState) == null) return false;
        return ctx.bestNodeIndex < start + Math.ceil((ctx.width + GORGE_HOLD) / WatershedPath.NODE_SPACING) + 1;
    }

    private static Ravine feederRavine(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        Ravine f = path.feeds;
        if (f == null) {
            f = NO_RAVINE;
            final WatershedPath down = path.hitMainRiver ? null : downstreamOf(path, generator, level, randomState);
            if (down != null) {
                final Ravine r = ravineOf(down, seaLevel, generator, level, randomState);
                if (r != null) {
                    final double mx = path.segmentsX[0], mz = path.segmentsZ[0];
                    for (int j = r.start; j < Math.min(down.validNodeCount, r.start + 3); j++) {
                        final double ex = down.segmentsX[j] - mx, ez = down.segmentsZ[j] - mz;
                        if (ex * ex + ez * ez < 2.25) {
                            f = r;
                            break;
                        }
                    }
                }
            }
            path.feeds = f;
        }
        return f == NO_RAVINE ? null : f;
    }

    private static boolean entersGorge(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (path.hitMainRiver) return true;
        if (path.gorgeEntry == 0) {
            boolean in = false;

            WatershedPath p = path;
            for (int hop = 0; hop < GORGE_ENTRY_HOPS && p.validNodeCount > 0 && p.segmentsY[0] <= seaLevel + 1; hop++) {
                p = downstreamOf(p, generator, level, randomState);
                if (p == null) break;
                if (p.hitMainRiver) {
                    in = true;
                    break;
                }
            }
            path.gorgeEntry = (byte) (in ? 2 : 1);
        }
        return path.gorgeEntry == 2;
    }

    private static final int GORGE_ENTRY_HOPS = 4;

    private static boolean leftDry(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (path.leftDry == 0) {
            path.leftDry = (byte) (drainedByRavines(path, seaLevel, generator, level, randomState) ? 2 : 1);
        }
        return path.leftDry == 2;
    }

    private static boolean drainedByRavines(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        if (path.validNodeCount < 2) return false;
        for (int i = 0; i < path.validNodeCount; i++) {
            if (path.segmentsY[i] > seaLevel + 1) return false;
        }
        final int rx0 = Math.floorDiv(Mth.floor(path.minX), WATERSHED_REGION_SIZE) - 1;
        final int rx1 = Math.floorDiv(Mth.floor(path.maxX), WATERSHED_REGION_SIZE) + 1;
        final int rz0 = Math.floorDiv(Mth.floor(path.minZ), WATERSHED_REGION_SIZE) - 1;
        final int rz1 = Math.floorDiv(Mth.floor(path.maxZ), WATERSHED_REGION_SIZE) + 1;
        boolean fed = false;
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                for (WatershedPath q : getWatershedRegion(rx, rz, generator, level, randomState).allPaths) {
                    if (q == path || q.validNodeCount < 2 || !joins(q, path)) continue;
                    final Ravine r = entersGorge(q, seaLevel, generator, level, randomState)
                            ? ravineOf(q, seaLevel, generator, level, randomState) : null;
                    if (r == null && !leftDry(q, seaLevel, generator, level, randomState)) return false;
                    fed = true;
                }
            }
        }
        return fed;
    }

    private static boolean joins(WatershedPath q, WatershedPath path) {
        final double mx = q.segmentsX[0], mz = q.segmentsZ[0];
        if (mx < path.minX - 2.0 || mx > path.maxX + 2.0 || mz < path.minZ - 2.0 || mz > path.maxZ + 2.0) return false;
        for (int i = 1; i < path.validNodeCount; i++) {
            final double ex = path.segmentsX[i] - mx, ez = path.segmentsZ[i] - mz;
            if (ex * ex + ez * ez < 2.25) return true;
        }
        return false;
    }

    private static Ravine ravineAbove(WatershedPath path, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        Ravine r = path.below;
        if (r == null) {
            r = NO_RAVINE;
            final int head = path.validNodeCount - 1;
            if (head > 0) {
                final double hx = path.segmentsX[head], hz = path.segmentsZ[head];
                final int colX = Mth.floor(hx), colZ = Mth.floor(hz);
                final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE), homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
                search:
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        final WatershedRegionData data = getWatershedRegion(homeRx + dx, homeRz + dz, generator, level, randomState);
                        for (WatershedPath q : data.grid.getPathsNear(colX, colZ, homeRx + dx, homeRz + dz)) {
                            if (q == path || q.validNodeCount < 2) continue;
                            final double ex = q.segmentsX[0] - hx, ez = q.segmentsZ[0] - hz;
                            if (ex * ex + ez * ez > 2.25) continue;
                            final Ravine qr = entersGorge(q, seaLevel, generator, level, randomState)
                                    ? ravineOf(q, seaLevel, generator, level, randomState) : null;
                            if (qr != null) {
                                r = qr;
                                break search;
                            }
                        }
                    }
                }
            }
            path.below = r;
        }
        return r == NO_RAVINE ? null : r;
    }

    private static int feedersTop(double px, double pz, double reach, int top,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final int colX = Mth.floor(px), colZ = Mth.floor(pz);
        final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE), homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final WatershedRegionData data = getWatershedRegion(homeRx + dx, homeRz + dz, generator, level, randomState);
                for (WatershedPath q : data.grid.getPathsNear(colX, colZ, homeRx + dx, homeRz + dz)) {
                    if (q.hitMainRiver || q.validNodeCount < 1) continue;
                    final double ex = q.segmentsX[0] - px, ez = q.segmentsZ[0] - pz;
                    if (ex * ex + ez * ez > reach * reach) continue;
                    for (int j = 0; j < Math.min(q.validNodeCount, 4); j++) top = Math.max(top, q.segmentsY[j]);
                }
            }
        }
        return top;
    }

    private static void labDumpRavine(WatershedPath path, Ravine r) {
        final String file = System.getProperty("dynamicwaters.labDumpRavines");
        if (file == null || r == NO_RAVINE) return;
        final StringBuilder sb = new StringBuilder("R ");
        sb.append(Math.round(path.segmentsX[0])).append(' ').append(Math.round(path.segmentsZ[0])).append(" start ")
                .append(r.start).append(" P ").append(Math.round(r.px)).append(' ').append(Math.round(r.pz))
                .append(" u ").append(Math.round(r.ux * 100) / 100.0).append(' ').append(Math.round(r.uz * 100) / 100.0)
                .append(" len ").append(Math.round(r.length)).append(" w ").append(Math.round(r.width * 10) / 10.0)
                .append(" pools");
        for (int i = 0; i < r.level.length; i++) sb.append(' ').append(Math.round(r.from[i] * 10) / 10.0).append(':').append(r.level[i]);
        sb.append('\n');
        synchronized (HydrologyManager.class) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(file), sb, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    public static void labDumpFlow(net.minecraft.server.level.ServerLevel level) {
        final String file = System.getProperty("dynamicwaters.labDumpFlow");
        final String rect = System.getProperty("dynamicwaters.labFlowRect");
        if (file == null || rect == null) return;
        final String[] r = rect.split(",");
        final int x0 = Integer.parseInt(r[0].trim()), x1 = Integer.parseInt(r[1].trim());
        final int z0 = Integer.parseInt(r[2].trim()), z1 = Integer.parseInt(r[3].trim());
        final StringBuilder sb = new StringBuilder();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                final int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                if (!level.getFluidState(new net.minecraft.core.BlockPos(x, top, z)).is(net.minecraft.tags.FluidTags.WATER)) continue;
                final net.minecraft.world.phys.Vec3 f = getRiverFlow(x, top, z, level);
                sb.append(String.format(java.util.Locale.ROOT, "%d %d %d %.3f %.3f%n", x, z, top, f.x, f.z));
            }
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(file), sb);
        } catch (java.io.IOException ignored) {
        }
    }

    private static com.AdamNew.dynamicwaters.river.RiverGraphEdge nearestRiver(double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, double[] on) {
        MainRivers.findNearest(x, z, generator, level, randomState);
        com.AdamNew.dynamicwaters.river.RiverGraphEdge best = null;
        double out = Double.MAX_VALUE;
        final double[] p = new double[2];
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge e : MainRivers.riversAt(x, z)) {
            final double d = toRiverWater(e, x, z, generator, level, randomState, p);
            if (d < out) {
                out = d;
                best = e;
                on[0] = p[0];
                on[1] = p[1];
            }
        }
        return best;
    }

    private static double toRiverWater(com.AdamNew.dynamicwaters.river.RiverGraphEdge river, double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, double[] on) {
        final double d = Math.sqrt(river.fractal().nearestPoint(x, z, on));
        final double r = gorgeWaterRadius(river, on[0], on[1], generator, level, randomState);
        if (r >= RAVINE_WATER_MIN) return d - r;
        final double[] s = river.fractal().segments;
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < s.length; i += 2) {
            final double ri = gorgeWaterRadius(river, s[i], s[i + 1], generator, level, randomState);
            if (ri < RAVINE_WATER_MIN) continue;
            final double di = Math.hypot(s[i] - x, s[i + 1] - z) - ri;
            if (di < best) {
                best = di;
                on[0] = s[i];
                on[1] = s[i + 1];
            }
        }
        return best;
    }

    private static double ravineWall(com.AdamNew.dynamicwaters.river.RiverGraphEdge river, double x, double z,
            int seaLevel, net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, double[] on) {
        final double d = Math.sqrt(river.fractal().nearestPoint(x, z, on));
        final double r = gorgeWaterRadius(river, on[0], on[1], generator, level, randomState);
        final double out = Math.max(0.0, d + getRiverJitter(x, z) * (r / 10.0)) - r;
        final int ground = CoarseHeightField.surfaceAt(Mth.floor(x), Mth.floor(z), generator, randomState) - 1;
        return lowlandRouteGround(x, z, ground, out, r, coarseGroundAt(on[0], on[1], generator, randomState),
                seaLevel, river.delta());
    }

    public static double gorgeShare(double riverGround) {
        final double t = Mth.clamp((riverGround - GORGE_FROM) / GORGE_RAMP, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double lowlandRouteGround(double x, double z, double ground, double dOut, double waterRadius,
            double riverGround, int seaLevel, boolean delta) {
        final double base = seaLevel - 1;
        if (dOut <= 0.0 || ground <= base) return ground;

        final double bank = Math.min(ground, base + ChannelForm.bankRise(dOut, ground - base,
                delta ? DELTA_PLAIN : bankMeet(waterRadius), delta ? DELTA_PLAIN_SLOPE : BANK_TALUS));
        final double gorge = gorgeShare(riverGround);
        if (gorge < 0.5 || delta) return bank;
        if (dOut >= GORGE_REACH + GORGE_ROUGH) return ground;

        final double depth = Math.max(8.0, (riverGround - base) * 1.1 + 6.0);
        final int wallY = gorgeWallY(x, z, dOut, Mth.floor(ground), (int) base, depth);
        return bank + (Math.min(wallY, ground) - bank) * gorge;
    }

    private static int gorgeStrataBase(double x, double z, int waterY) {
        return waterY + 1
                + (int) Math.round(GORGE_WARP * RIVER_BED_NOISE.get(x * 0.009 - 77.0, z * 0.009 + 41.0));
    }

    public static int gorgeStratum(double x, double z, int y, int waterY) {
        int top = gorgeStrataBase(x, z, waterY);
        if (y <= top) return -1;
        for (int k = 0; k < 96; k++) {
            top += gorgeBand(k);
            if (y <= top) return k;
        }
        return 96;
    }

    private static int gorgeBand(int k) {
        return GORGE_BAND_MIN + (int) Long.remainderUnsigned(gorgeHash(k, 0x5DEECE66DL), GORGE_BAND_MAX - GORGE_BAND_MIN + 1);
    }

    public static long gorgeHash(int k, long salt) {
        long h = (WORLD_SEED ^ salt) + (k + 1) * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static final net.minecraft.world.level.block.Block[] GORGE_BADLANDS_BANDS = {
            net.minecraft.world.level.block.Blocks.TERRACOTTA, net.minecraft.world.level.block.Blocks.TERRACOTTA,
            net.minecraft.world.level.block.Blocks.TERRACOTTA, net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.orange(),
            net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.orange(), net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.red(),
            net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.yellow(), net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.brown(),
            net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.white(), net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.lightGray() };

    public static void gorgeDress(net.minecraft.world.level.chunk.ChunkAccess chunk, int wx, int wz, int topY,
            int waterY, boolean steep, java.util.BitSet protectedBlocks, int minH,
            @org.jetbrains.annotations.Nullable SurfaceGround ground, int groundY) {
        final net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome =
                chunk.getNoiseBiome(wx >> 2, (topY + 1) >> 2, wz >> 2);
        if (biome.is(net.minecraft.world.level.biome.Biomes.STONY_PEAKS)
                || biome.is(net.minecraft.world.level.biome.Biomes.JAGGED_PEAKS)
                || biome.is(net.minecraft.world.level.biome.Biomes.FROZEN_PEAKS)) {
            return;
        }
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        final boolean badlands = biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS);
        final boolean desert = !badlands && (biome.is(net.minecraft.world.level.biome.Biomes.DESERT)
                || biome.is(com.AdamNew.dynamicwaters.platform.ModTags.IS_DESERT));
        if (badlands || desert) {
            int k = Integer.MIN_VALUE;
            net.minecraft.world.level.block.state.BlockState bed = null;
            for (int y = waterY - 2; y <= topY; y++) {
                final int s = gorgeStratum(wx, wz, y, waterY);
                if (s != k) {
                    k = s;
                    final long h = gorgeHash(s, 0x6A09E667F3BCC909L);
                    bed = badlands
                            ? GORGE_BADLANDS_BANDS[(int) Long.remainderUnsigned(h, GORGE_BADLANDS_BANDS.length)].defaultBlockState()
                            : (Long.remainderUnsigned(h, 3) == 0 ? net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE
                                    : net.minecraft.world.level.block.Blocks.SANDSTONE).defaultBlockState();
                }
                gorgeReplaceRock(chunk, pos.set(wx, y, wz), bed, protectedBlocks, minH);
            }

            if (desert ? !steep : topY <= waterY + 2) {
                final net.minecraft.world.level.block.state.BlockState sand = (desert
                        ? net.minecraft.world.level.block.Blocks.SAND
                        : net.minecraft.world.level.block.Blocks.RED_SAND).defaultBlockState();
                final net.minecraft.world.level.block.state.BlockState here = chunk.getBlockState(pos.set(wx, topY, wz));
                if (bed != null && here.is(bed.getBlock())
                        && !(protectedBlocks != null && topY >= minH && protectedBlocks.get(topY - minH))) {
                    chunk.setBlockState(pos, sand, 3);
                }
            }
            return;
        }
        final net.minecraft.world.level.block.state.BlockState top, fill;
        if (biome.is(net.minecraft.world.level.biome.Biomes.MUSHROOM_FIELDS)) {
            top = net.minecraft.world.level.block.Blocks.MYCELIUM.defaultBlockState();
            fill = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
        } else if (biome.is(net.minecraft.world.level.biome.Biomes.SNOWY_SLOPES)
                || biome.is(net.minecraft.world.level.biome.Biomes.GROVE)) {
            if (steep) return;
            top = net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
            fill = net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState();
        } else {
            final net.minecraft.world.level.block.state.BlockState natural =
                    ground != null ? ground.top(wx, groundY, wz) : null;
            if (natural == null || natural.isAir() || !natural.getFluidState().isEmpty()
                    || natural.is(net.minecraft.tags.BlockTags.DIRT)
                    || natural.is(net.minecraft.world.level.block.Blocks.DIRT_PATH)) {
                top = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
                fill = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
            } else if (ground.rock(natural)) {
                return;
            } else if (natural.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
                if (!gorgeRock(chunk.getBlockState(pos.set(wx, topY - 1, wz)))) return;
                gorgeReplaceRock(chunk, pos.set(wx, topY, wz), natural, protectedBlocks, minH);
                return;
            } else {
                top = natural.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                        ? net.minecraft.world.level.block.Blocks.SNOW_BLOCK.defaultBlockState()
                        : natural.is(net.minecraft.world.level.block.Blocks.ICE)
                                ? net.minecraft.world.level.block.Blocks.PACKED_ICE.defaultBlockState() : natural;
                fill = top;
            }
        }

        if (!gorgeReplaceRock(chunk, pos.set(wx, topY, wz), top, protectedBlocks, minH)) return;
        for (int y = topY - 1; y >= Math.max(waterY - 2, topY - (steep ? 1 : 3)); y--) {
            if (!gorgeReplaceRock(chunk, pos.set(wx, y, wz), fill, protectedBlocks, minH)) return;
        }
    }

    public static void gorgeSeal(net.minecraft.world.level.chunk.ChunkAccess chunk, int wx, int wz, int seaLevel,
            int originalY, java.util.BitSet protectedBlocks, int minH) {
        final net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        final net.minecraft.world.level.block.state.BlockState rock = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        for (int y = seaLevel; y <= originalY; y++) {
            if (protectedBlocks != null && y >= minH && protectedBlocks.get(y - minH)) continue;
            final net.minecraft.world.level.block.state.BlockState here = chunk.getBlockState(pos.set(wx, y, wz));
            if (here.isAir() || !here.getFluidState().isEmpty()) chunk.setBlockState(pos, rock, 3);
        }
    }

    private static boolean gorgeReplaceRock(net.minecraft.world.level.chunk.ChunkAccess chunk,
            net.minecraft.core.BlockPos.MutableBlockPos pos, net.minecraft.world.level.block.state.BlockState state,
            java.util.BitSet protectedBlocks, int minH) {
        final int y = pos.getY();
        if (protectedBlocks != null && y >= minH && protectedBlocks.get(y - minH)) return false;
        if (!gorgeRock(chunk.getBlockState(pos))) return false;
        chunk.setBlockState(pos, state, 3);
        return true;
    }

    private static boolean gorgeRock(net.minecraft.world.level.block.state.BlockState here) {
        return here.is(net.minecraft.world.level.block.Blocks.STONE)
                || here.is(net.minecraft.world.level.block.Blocks.DEEPSLATE);
    }

    static double coarseGroundAt(double x, double z,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        final double fx = x / CoarseHeightField.CELL_SIZE, fz = z / CoarseHeightField.CELL_SIZE;
        final int i = Mth.floor(fx), j = Mth.floor(fz);
        final double tx = fx - i, tz = fz - j;
        final double h00 = CoarseHeightField.sample(i, j, generator, randomState);
        final double h10 = CoarseHeightField.sample(i + 1, j, generator, randomState);
        final double h01 = CoarseHeightField.sample(i, j + 1, generator, randomState);
        final double h11 = CoarseHeightField.sample(i + 1, j + 1, generator, randomState);
        return Mth.lerp(tz, Mth.lerp(tx, h00, h10), Mth.lerp(tx, h01, h11));
    }

    private static int vaultRoof(double distance, double vaultR, double vaultH) {
        final double t = Math.min(1.0, distance / vaultR);

        return (int) Math.round(TUNNEL_WAIST + (vaultH - TUNNEL_WAIST) * Math.sqrt(1.0 - t * t));
    }

    private static int vaultTop(WatershedPath path, WatershedPath.RiverNodeData data, double vaultR, double vaultH, int ground) {
        int level = data.y;
        final int last = Math.min(path.validNodeCount - 1, data.nodeIndex + TUNNEL_LOOK_NODES);
        for (int k = data.nodeIndex + 1; k <= last; k++) {
            level = Math.max(level, path.segmentsY[k] - (int) ((k - data.nodeIndex - 1) * WatershedPath.NODE_SPACING));
        }
        final int arch = vaultRoof(data.distance, vaultR, vaultH);
        return Math.max(data.y + arch, Math.min(level + arch, ground - TUNNEL_CRUST));
    }

    public static class RiverCarving {
        public int targetY;
        public int waterLevel;

        public int sealFloorY;
        public int cavernRoofY = -999;
        public boolean modifies;
        public boolean isPillar;
        public boolean isWatershed = false;
        public boolean isMountainRiver = false;
        public boolean isWatershedWall = false;
        public boolean isMainRiverWater = false;
        public boolean isLake = false;
        public int wallTopY;

        public int skinLevel = Integer.MIN_VALUE;

        public boolean pointBar = false;

        public boolean riverSplit = false;

        public boolean deltaFlat = false;
        public double deltaShore = Double.MAX_VALUE;

        public boolean deltaWater = false;

        public boolean dressCut = false;

        public boolean gorgeWall = false;

        public boolean gorgeSteep = false;

        public boolean gorgeSeal = false;
        public double distanceFromCenter = Double.MAX_VALUE;
        public double channelWidth = 0.0;
        public double waterRadius = 0.0;

        public double channelSlope = 0.0;
    }

    public static RiverCarving calculateRiverCarving(double x, double z, int originalY, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        return carve(x, z, originalY, seaLevel, generator, level, randomState, true);
    }

    public static RiverCarving lowlandCut(double x, double z, int ground, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        return carve(x, z, ground, seaLevel, generator, getDummyAccessor(), randomState, false);
    }

    public static boolean nearRiverHead(double x, double z, double margin) {
        final double reach = HEAD_TAPER + (Config.NORMAL_RIVER_TUNNELS.get() ? 0.0 : HEAD_FEED_WIDEN) + margin;
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : MainRivers.riversAt(x, z)) {
            if (path.sourceEdge()) continue;
            final double dx = x - path.source().x(), dz = z - path.source().y();
            if (dx * dx + dz * dz < reach * reach) return true;
        }
        return false;
    }

    public static boolean lowlandMayReach(double x, double z) {
        final boolean gorgeMode = !Config.NORMAL_RIVER_TUNNELS.get();
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : MainRivers.riversAt(x, z)) {
            final double radius = path.maxWaterRadius() * (path.delta() ? 1.0 + DELTA_RAGGED : 1.0);
            double furthest = gorgeMode ? radius + GORGE_REACH + GORGE_ROUGH + 6.0 : radius * 2.0 + 1.0;
            if (path.delta()) furthest = Math.max(furthest, radius + DELTA_PLAIN + 1.0);
            if (path.fractal().boxDistanceSq(x, z) < furthest * furthest) return true;
        }
        return false;
    }

    private static final WatershedContext NO_MOUNTAIN_RIVER = new WatershedContext();

    private static RiverCarving carve(double x, double z, int originalY, int seaLevel,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState, boolean whole) {
        final long carveStart = System.nanoTime();
        RiverCarving result = new RiverCarving();
        result.targetY = originalY;
        result.waterLevel = seaLevel - 1;
        result.sealFloorY = originalY;
        result.cavernRoofY = -999;
        result.modifies = false;
        result.isPillar = false;
        result.isWatershed = false;
        result.isMountainRiver = false;
        result.isWatershedWall = false;
        result.wallTopY = -999;
        result.distanceFromCenter = Double.MAX_VALUE;
        result.channelWidth = 0.0;

        int mountainTargetY = 320;
        int mountainWaterLevel = Integer.MAX_VALUE;

        int mountainWetLevel = Integer.MAX_VALUE;

        int mountainAnyLevel = Integer.MIN_VALUE;

        double riverEdgeGap = Double.MAX_VALUE;
        int riverEdgeY = Integer.MIN_VALUE;

        int riverRim = Integer.MIN_VALUE;

        int mountainLakeBedY = Integer.MAX_VALUE;

        int vaultPaths = 0;

        double jitter = getRiverJitter(x, z);
        double floorNoise = getRiverBedNoise(x, z);

        final WatershedLake foundLake = (whole && generator != null && level != null && randomState != null)
                ? findLake(x, z, originalY, generator, level, randomState, false, true)
                : null;
        final boolean dugShore = foundLake != null && originalY > foundLake.waterY && !foundLake.inside(x, z);
        final WatershedLake columnLake = dugShore ? null : foundLake;

        final long lowlandStart = System.nanoTime();
        {
            MainRivers.findNearest(x, z, generator, level, randomState);

            WatershedContext mCtx = whole
                    ? evaluateWatershedContext(x, z, generator, level, randomState, generator == null)
                    : NO_MOUNTAIN_RIVER;

            final boolean gorgeMode = !Config.NORMAL_RIVER_TUNNELS.get();
            final double[] onRiver = new double[2];

            java.util.List<com.AdamNew.dynamicwaters.river.RiverGraphEdge> nearbyRivers = com.AdamNew.dynamicwaters.MainRivers
                    .riversAt(x, z);

            double ragged = Double.NaN;

            int splitTop = Integer.MAX_VALUE, splitBed = Integer.MIN_VALUE;
            for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : nearbyRivers) {
                double maxRadius = waterRadiusAt(path, x, z);
                if (path.delta()) {
                    if (Double.isNaN(ragged)) ragged = deltaRagged(x, z);
                    maxRadius *= ragged;
                }

                double furthest = gorgeMode ? maxRadius + GORGE_REACH + GORGE_ROUGH + 6.0 : maxRadius * 2.0 + 1.0;
                if (path.delta()) furthest = Math.max(furthest, maxRadius + DELTA_PLAIN + 1.0);
                if (path.fractal().boxDistanceSq(x, z) >= furthest * furthest) continue;
                double rawDistance = Math.sqrt(gorgeMode ? path.fractal().nearestPoint(x, z, onRiver)
                        : path.fractal().intersectDistance(x, z));

                double canyonRadius = maxRadius;
                double waterRadius = maxRadius;

                double pastTip = 0.0;
                double headSlot = 1.0;

                if (!path.sourceEdge()) {
                    double sx = path.source().x();
                    double sz = path.source().y();
                    double distToSource = Math.sqrt((x - sx) * (x - sx) + (z - sz) * (z - sz));

                    if (distToSource < HEAD_TAPER + (gorgeMode ? HEAD_FEED_WIDEN : 0.0)) {
                        final HeadFeed feed = whole ? headFeed(path, generator, level, randomState) : NO_FEED;
                        final double fedFrom = feed.distToSource() - feed.width();
                        final boolean fed = gorgeMode && feed != NO_FEED;
                        final double widen = fed ? Math.max(HEAD_FEED_WIDEN, HEAD_TAPER - fedFrom) : 0.0;
                        if (distToSource < (fed ? fedFrom + widen : HEAD_TAPER)) {
                            canyonRadius = maxRadius * Mth.clamp(distToSource / HEAD_TAPER, 0.01, 1.0);

                            final double taper = Mth.clamp(
                                    (distToSource - HEAD_WATER_FROM) / (HEAD_TAPER - HEAD_WATER_FROM), 0.0, 1.0);
                            waterRadius = maxRadius * (gorgeMode ? Math.pow(taper, HEAD_TIP_POWER) : taper);
                            double tipAt = HEAD_WATER_FROM;
                            final double least = Math.min(maxRadius, feed.width() * HEAD_FEED_MARGIN);
                            if (fed) {
                                final double tipLen = Math.max(HEAD_FEED_TIP_MIN, HEAD_FEED_TIP * least);
                                tipAt = fedFrom - tipLen;
                                waterRadius = distToSource >= fedFrom
                                        ? least + (maxRadius - least)
                                                * Math.pow((distToSource - fedFrom) / widen, HEAD_TIP_POWER)
                                        : least * Math.pow(Mth.clamp((distToSource - tipAt) / tipLen, 0.0, 1.0),
                                                HEAD_TIP_POWER);
                            } else if (distToSource >= fedFrom) {
                                waterRadius = Math.max(waterRadius, least);
                                canyonRadius = Math.max(canyonRadius, least);
                            }
                            if (gorgeMode) {
                                canyonRadius = waterRadius;
                                pastTip = Math.max(0.0, tipAt - distToSource);
                                headSlot = HEAD_SLOT + (1.0 - HEAD_SLOT) * waterRadius / maxRadius;
                            }
                        }
                    }
                }

                double gorge = 0.0;
                double gorgeDepth = 0.0;
                final double gorgeReach = waterRadius + GORGE_REACH + GORGE_ROUGH + 3.0;
                if (gorgeMode && rawDistance < gorgeReach && !path.delta()) {
                    final double riverGround = generator != null && randomState != null
                            ? coarseGroundAt(onRiver[0], onRiver[1], generator, randomState)
                            : originalY;
                    final double t = Mth.clamp((riverGround - GORGE_FROM) / GORGE_RAMP, 0.0, 1.0);
                    gorge = t * t * (3.0 - 2.0 * t);

                    gorgeDepth = Math.max(8.0, (riverGround - (seaLevel - 1)) * 1.1 + 6.0);
                }
                final boolean inReach = rawDistance < (path.delta() ? maxRadius + DELTA_PLAIN : maxRadius * 2.0);
                if (!inReach && !(gorge > 0.0 && rawDistance < gorgeReach)) {
                    continue;
                }
                double distance = Math.max(0, rawDistance + jitter * (canyonRadius / 10.0));

                double baselineY = seaLevel - 1;
                double heightDiffForWidth = originalY - baselineY;
                double widthFactor = Math.min(1.0, Math.max(0.0, (heightDiffForWidth - 3.0) / 10.0));
                double tunnelFactor = gorgeMode ? 0.0
                        : Math.min(1.0, Math.max(0.0, (originalY - Config.NORMAL_RIVER_CANYON_HEIGHT.get()) / 8.0));

                final double split = gorgeMode && rawDistance < waterRadius && riverSplits(path)
                        ? riverSplitDepth(path, x, z, maxRadius) : RIVER_SPLIT_NONE;

                if (!gorgeMode && mCtx.bestPath != null) {
                    double distToWatershed = mCtx.distance - mCtx.width;
                    if (distToWatershed < 25.0 && distToWatershed > -10.0) {
                        double forceTunnel;
                        if (distToWatershed < 15.0) {
                            forceTunnel = 1.0;
                        } else {
                            forceTunnel = 1.0 - ((distToWatershed - 15.0) / 10.0);
                        }

                        double mRiverHeightAboveSea = Math.max(0, mCtx.bestY - (seaLevel - 1));
                        double heightFade = Math.min(1.0, mRiverHeightAboveSea / 8.0);

                        double nodeFade = Math.min(1.0, mCtx.bestNodeIndex / 15.0);
                        double combinedFade = Math.max(heightFade, nodeFade);

                        forceTunnel *= combinedFade;
                        tunnelFactor = Math.max(tunnelFactor, forceTunnel);
                    }
                }

                double bankNoise = RIVER_BED_NOISE.get(x * 0.05, z * 0.05) * 3.0;

                double reqBankWidth;
                double hDiff = Math.max(0.0, originalY - baselineY);

                double oldBaseWidth = 16.0 * (1.0 - widthFactor) + 4.0 * widthFactor;
                if (originalY < 80) {
                    double reqNormSq = hDiff / 5.6;
                    double reqNorm = Math.sqrt(Math.max(1.0, reqNormSq));

                    double valleyBankWidth = (reqNorm - 1.0) * canyonRadius;
                    reqBankWidth = valleyBankWidth * (1.0 - tunnelFactor) + oldBaseWidth * tunnelFactor;
                } else {
                    reqBankWidth = oldBaseWidth;
                }

                double baseWidth = Math.min(70.0, reqBankWidth + 5.0);

                double bankMultiplier = 1.0 - Math.min(1.0, tunnelFactor * 1.5);
                double targetBankWidth = (baseWidth + bankNoise) * bankMultiplier;

                final int tunnelLedge = tunnelFactor > 0.0 ? Config.NORMAL_TUNNEL_LEDGE.get() : 0;
                targetBankWidth = Math.max(targetBankWidth, tunnelLedge * tunnelFactor);

                if (mCtx.bestPath != null) {
                    double distToMouth = mCtx.distance;
                    double mRiverHeightAboveSea = Math.max(0, mCtx.bestY - (seaLevel - 1));

                    if (distToMouth < 25.0 && mRiverHeightAboveSea < 8.0 && mCtx.bestNodeIndex < 12) {
                        double distFade = Math.max(0.0, 1.0 - distToMouth / 25.0);
                        double heightFadeMouth = Math.max(0.0, 1.0 - mRiverHeightAboveSea / 8.0);
                        double mouthNodeFade = Math.max(0.0, 1.0 - mCtx.bestNodeIndex / 12.0);
                        double mouthFlare = distFade * heightFadeMouth * mouthNodeFade;

                        targetBankWidth = Math.max(targetBankWidth, 12.0 * mouthFlare);
                    }
                }

                if (targetBankWidth < 0.0)
                    targetBankWidth = 0.0;

                if (path.delta()) targetBankWidth = Math.max(targetBankWidth, DELTA_PLAIN);

                final double edgeOut = distance - canyonRadius + HEAD_CLOSE * pastTip;
                final boolean inCanyon = inReach && (edgeOut < targetBankWidth || edgeOut < 0.0);
                if (inCanyon || (gorge > 0.0 && distance >= waterRadius)) {
                    double totalWidth = canyonRadius + targetBankWidth;
                    double totalNormDist = (totalWidth > 0) ? Math.min(1.0, distance / totalWidth) : 1.0;

                    double tunnelArch = Math.cos(totalNormDist * Math.PI / 2.0);

                    double t2 = totalNormDist * totalNormDist;
                    double canyonArch = 1.0 - (t2 * t2);

                    double arch = canyonArch * (1.0 - tunnelFactor) + tunnelArch * tunnelFactor;

                    double canyonNoise = Math.max(0.0, RIVER_BED_NOISE.get(x * 0.02, z * 0.02));

                    double openH = Math.max(20.0, heightDiffForWidth + 15.0);
                    double tunnelH = tunnelFactor > 0.0 ? Config.NORMAL_TUNNEL_HEIGHT.get() + 4.0 * canyonNoise : 0.0;
                    double H = openH * (1.0 - tunnelFactor) + tunnelH * tunnelFactor;

                    if (distance < result.distanceFromCenter) {
                        result.distanceFromCenter = distance;
                        result.channelWidth = canyonRadius;
                        result.waterRadius = waterRadius;
                    }

                    int absoluteRoofY = (int) Math.floor(baselineY + H * arch);

                    if (gorge > 0.0) {
                        absoluteRoofY = inCanyon ? (int) Math.round(absoluteRoofY + (320 - absoluteRoofY) * gorge) : 320;
                    }

                    if (split > 0.0 && originalY >= seaLevel - 1) {
                        final int top = Math.min(originalY,
                                seaLevel - 1 + Math.min(RIVER_SPLIT_TOP, (int) (split * RIVER_SPLIT_RISE)));
                        splitTop = Math.min(splitTop, top);
                        result.modifies = true;
                        result.cavernRoofY = Math.max(result.cavernRoofY, absoluteRoofY);
                        result.skinLevel = Math.max(result.skinLevel, seaLevel - 1);
                        result.sealFloorY = Math.min(result.sealFloorY, top - 3);
                        if (split < RIVER_SPLIT_BAR) result.pointBar = true;
                        if (top < originalY - 1) result.dressCut = true;
                    } else if (distance < waterRadius) {
                        result.isMainRiverWater = true;
                        if (path.delta()) result.deltaWater = true;
                        result.modifies = true;

                        if (tunnelFactor >= 0.99 && Config.NORMAL_TUNNEL_PILLARS.get()) {
                            int hash = (int) (x * 31337 + z * 42069);
                            hash ^= (hash >> 16);
                            if (Math.abs(hash % 1000) < 2) {
                                result.isPillar = true;
                            }
                        }

                        double tBed = (waterRadius > 0) ? distance / waterRadius : 1.0;
                        if (tBed > 1.0)
                            tBed = 1.0;
                        double clampedFloorNoise = floorNoise * Math.max(0.0, 1.0 - tBed * 2.0);

                        double bowl = Math.min(NORMAL_RIVER_DEPTH, HEAD_DEPTH_PER_RADIUS * waterRadius)
                                * (1.0 - tBed * tBed);
                        double riverHeight = (seaLevel - 1) - bowl + clampedFloorNoise;
                        int localTargetY = (int) Math.floor(Math.min(riverHeight, originalY));

                        splitBed = Math.max(splitBed, (int) Math.floor(seaLevel - 2 + Math.min(0.0, split) * RIVER_SPLIT_SHOAL));

                        if (absoluteRoofY < originalY - 2 && tunnelFactor > 0.8 && Config.NORMAL_TUNNEL_FORMATIONS.get()) {
                            double sNoise = SPELEOTHEM_NOISE.get(x * 0.4, z * 0.4);
                            if (sNoise > 0.65)
                                localTargetY += (int) ((sNoise - 0.65) * 12.0);
                            if (sNoise < -0.65)
                                absoluteRoofY -= (int) ((Math.abs(sNoise) - 0.65) * 12.0);
                        }

                        result.targetY = Math.min(result.targetY, localTargetY);
                        result.cavernRoofY = Math.max(result.cavernRoofY, absoluteRoofY);

                        result.skinLevel = Math.max(result.skinLevel, seaLevel - 1);
                    } else {
                        if ((inCanyon && targetBankWidth > 0.0) || gorge > 0.0) {
                            int slopeY = originalY;
                            if (inCanyon && targetBankWidth > 0.0) {
                                final double dOut = Math.max(0.0, edgeOut);
                                final double bankReach = path.delta() ? DELTA_PLAIN : bankMeet(canyonRadius);
                                final double talus = path.delta() ? DELTA_PLAIN_SLOPE : BANK_TALUS;
                                slopeY = (int) Math.floor(baselineY
                                        + ChannelForm.bankRise(dOut, originalY - baselineY, bankReach, talus));

                                if (tunnelLedge > 0 && tunnelFactor > 0.8) {
                                    final double s = Math.min(1.0, dOut / targetBankWidth);
                                    slopeY = Math.min(slopeY, seaLevel + (int) Math.round(
                                            TUNNEL_WAIST * (1.0 - Math.cos(s * Math.PI / 2.0))));
                                }

                                slopeY = Math.min(slopeY, originalY);

                                if (path.delta() && slopeY <= baselineY + DELTA_FLAT_ABOVE) {
                                    result.deltaFlat = true;
                                    result.deltaShore = Math.min(result.deltaShore, dOut);
                                }
                            }
                            if (gorge > 0.0) {
                                final double wallOut = (distance - waterRadius + HEAD_CLOSE * pastTip) / headSlot;
                                final int wallY = gorgeWallY(x, z, wallOut, originalY, seaLevel - 1, gorgeDepth);
                                final boolean steep = GORGE_PROFILE.get().lastSteep;
                                slopeY = (int) Math.round(slopeY + (wallY - slopeY) * gorge);

                                int hold = Integer.MIN_VALUE;
                                if (mCtx.bestPath != null && mCtx.distance < mCtx.width + GORGE_HOLD
                                        && !atRavineRim(mCtx, seaLevel, generator, level, randomState)) {
                                    hold = mCtx.bestY + 1;
                                }
                                if (columnLake != null && laysLake(columnLake, seaLevel) && columnLake.covers(x, z)) {
                                    hold = Math.max(hold, columnLake.waterY + 1);
                                }
                                slopeY = Math.min(originalY, Math.max(slopeY, hold));
                                if (gorge >= 0.5) {
                                    if (slopeY < originalY) {
                                        if (!result.gorgeWall || slopeY < result.targetY) result.gorgeSteep = steep;
                                        result.gorgeWall = true;
                                        result.gorgeSeal = true;
                                    } else if (gorgeWallY(x, z, wallOut - GORGE_SEAL, originalY,
                                            seaLevel - 1, gorgeDepth) < originalY) {
                                        result.gorgeSeal = true;
                                    }
                                }
                            }

                            if (inCanyon || slopeY < originalY) {
                                result.modifies = true;
                                result.targetY = Math.min(result.targetY, slopeY);
                                result.cavernRoofY = Math.max(result.cavernRoofY, absoluteRoofY);
                                result.skinLevel = Math.max(result.skinLevel, seaLevel - 1);

                                if (slopeY < originalY - 1 && !(tunnelLedge > 0 && tunnelFactor > 0.8)) result.dressCut = true;

                                result.sealFloorY = Math.min(result.sealFloorY,
                                        originalY - sealDepth(distance - maxRadius, maxRadius) + sealJitter(x, z));

                                if (gorge > 0.0) {
                                    result.sealFloorY = Math.min(result.sealFloorY, slopeY - 3);
                                }
                            }
                        }
                    }
                }
            }
            if (splitTop != Integer.MAX_VALUE) {
                result.isMainRiverWater = false;
                result.riverSplit = true;
                result.targetY = splitTop;
            } else if (splitBed > result.targetY && result.isMainRiverWater) {
                result.targetY = Math.min(splitBed, originalY);
                if (splitBed >= seaLevel - 3) result.pointBar = true;
            }
        }
        if (!whole) return result;
        WatershedStats.lowlandPassNanos.add(System.nanoTime() - lowlandStart);

        final boolean columnInLake = columnLake != null && columnLake.bedColumn(originalY)
                && columnLake.inside(x, z);

        final boolean columnInShore = columnLake != null && !columnInLake
                && originalY < columnLake.waterY
                && columnLake.holdsColumn(originalY)
                && columnLake.shoreBand(x, z)
                && !columnLake.touchesInside(x, z)
                && !lakeDrainsHere(columnLake, x, z, generator, randomState);

        final boolean lakeWater = columnInLake
                || (columnLake != null && columnLake.holdsColumn(originalY) && columnLake.touchesInside(x, z))
                || columnInShore;

        WatershedLake hollowLake = null;
        LakeHollows.Kind hollow = LakeHollows.Kind.NONE;
        if (!lakeWater && generator != null && randomState != null) {
            hollowLake = columnLake != null ? columnLake
                    : lakeBeside(x, z, originalY, generator, level, randomState, false);
            if (hollowLake != null && laysLake(hollowLake, seaLevel) && originalY < hollowLake.waterY) {
                hollow = LakeHollows.kind(hollowLake, Mth.floor(x), Mth.floor(z), originalY, LAKE_SHORE_LIP,
                        generator, randomState);
            }
        }
        final boolean columnIsLakeWater = lakeWater || hollow == LakeHollows.Kind.FLOODED;

        boolean isMountainWater = false;

        final int groundBefore = Math.min(originalY, result.targetY);

        int vaultLevel = Integer.MIN_VALUE;

        int ravineSide = Integer.MAX_VALUE, ravineHold = Integer.MIN_VALUE;

        int heldFor = Integer.MIN_VALUE;
        WatershedPath heldBy = null;
        boolean heldNearMouth = false;
        int lowNearY = Integer.MAX_VALUE, lowNearY2 = Integer.MAX_VALUE;
        WatershedPath lowNearPath = null;
        if (generator != null && level != null && randomState != null && !result.riverSplit) {
            final int colX = Mth.floor(x), colZ = Mth.floor(z);
            final int homeRx = Math.floorDiv(colX, WATERSHED_REGION_SIZE);
            final int homeRz = Math.floorDiv(colZ, WATERSHED_REGION_SIZE);
            for (int dx = -WATERSHED_SCAN_RADIUS; dx <= WATERSHED_SCAN_RADIUS; dx++) {
                for (int dz = -WATERSHED_SCAN_RADIUS; dz <= WATERSHED_SCAN_RADIUS; dz++) {
                    final int mrx = homeRx + dx;
                    final int mrz = homeRz + dz;
                    final WatershedRegionData regionData = getWatershedRegion(mrx, mrz, generator, level, randomState);
                    for (WatershedPath mountainPath : regionData.grid.getPathsNear(colX, colZ, mrx, mrz)) {
                        WatershedPath.RiverNodeData data = mountainPath.getClosestNodeData(x, z);
                        if (data.distance == Double.MAX_VALUE) {
                            continue;
                        }

                        if (feedsDelta(mountainPath, generator, level, randomState)) continue;

                        if (!Config.NORMAL_RIVER_TUNNELS.get() && leftDry(mountainPath, seaLevel, generator, level, randomState)) continue;

                        final boolean overRim = !Config.NORMAL_RIVER_TUNNELS.get() && !result.isMainRiverWater
                                && entersGorge(mountainPath, seaLevel, generator, level, randomState);
                        final Ravine ravine = overRim ? ravineOf(mountainPath, seaLevel, generator, level, randomState) : null;

                        final Ravine fed = ravine == null && !Config.NORMAL_RIVER_TUNNELS.get() && !result.isMainRiverWater
                                ? feederRavine(mountainPath, seaLevel, generator, level, randomState) : null;
                        if (fed != null) {
                            final double ox = x - fed.px, oz = z - fed.pz;
                            final double fedAlong = ox * fed.ux + oz * fed.uz;
                            if (fedAlong > 0.0 && Math.abs(fed.lateral(ox, oz, fedAlong)) < fed.width + RAVINE_SIDE) {
                                continue;
                            }
                        }
                        if (ravine != null) {
                            final double ox = x - ravine.px, oz = z - ravine.pz;
                            final double along = ox * ravine.ux + oz * ravine.uz;

                            final double across = ravine.across(x, z, ox, oz, along);

                            final boolean behind = along < 0.0 && along > -(ravine.width + RAVINE_NEAR)
                                    && data.nodeIndex < ravine.start && data.distance < ravine.width + RAVINE_NEAR;
                            if ((along >= 0.0 || behind) && along <= ravine.length + RAVINE_INTO
                                    && across < ravine.width + RAVINE_SIDE) {
                                int w = ravine.poolLevel(x, z, along);
                                boolean inPool = across < ravine.width;

                                int beside = Integer.MIN_VALUE;
                                if (!inPool && across < ravine.width + RAVINE_NEAR) {
                                    final int east = ravine.waterAt(x + 1.0, z), west = ravine.waterAt(x - 1.0, z);
                                    final int south = ravine.waterAt(x, z + 1.0), north = ravine.waterAt(x, z - 1.0);
                                    beside = Math.max(Math.max(east, west), Math.max(south, north));

                                    if (east != Integer.MIN_VALUE && west != Integer.MIN_VALUE
                                            || south != Integer.MIN_VALUE && north != Integer.MIN_VALUE) {
                                        inPool = true;
                                        w = beside;
                                    }
                                }
                                boolean cut = false;
                                final double u = Math.min(1.0, across / ravine.width);
                                final int bowl = Mth.clamp(
                                        (int) Math.round(STREAM_DEPTH_RATIO * ravine.width * (1.0 - u * u)), 1, MAX_BOWL);
                                if (inPool && w - bowl <= groundBefore) {
                                    isMountainWater = true;
                                    result.isWatershedWall = false;
                                    mountainWaterLevel = Math.min(mountainWaterLevel, w);
                                    mountainWetLevel = Math.min(mountainWetLevel, w);
                                    mountainTargetY = Math.min(mountainTargetY, w - bowl);
                                    if (across < result.distanceFromCenter) {
                                        result.distanceFromCenter = across;
                                        result.channelWidth = ravine.width;
                                        result.channelSlope = 1.0;
                                    }
                                    cut = true;
                                } else if (!inPool) {
                                    final double noise = RIVER_BED_NOISE.get(x * 0.15, z * 0.15) * 1.5;
                                    final double out = across - ravine.width;
                                    final int line = Mth.floor(ravine.sideLine(along)) + 1
                                            + Math.max(0, Mth.floor(out * RAVINE_GRADE + noise));
                                    final int bank = ravine.levelAt(along - RAVINE_LEDGE - RAVINE_LEDGE_SPREAD * out) + 1
                                            + Math.max(0, Mth.floor(out * RAVINE_BANK + noise));
                                    ravineSide = Math.min(ravineSide, Math.min(line, bank));

                                    if (beside > seaLevel - 1) ravineHold = Math.max(ravineHold, beside + RAVINE_RIM_HOLD);
                                }
                                if (cut) {
                                    result.modifies = true;
                                    result.isWatershed = true;
                                    result.isMountainRiver = true;
                                    result.cavernRoofY = 320;
                                    mountainAnyLevel = Math.max(mountainAnyLevel, w);
                                }
                            }

                            final int roundStart = ravine.start + Mth.ceil((ravine.width + RAVINE_NEAR) / WatershedPath.NODE_SPACING);
                            if (data.nodeIndex < ravine.start
                                    || data.nodeIndex <= roundStart && along > 0.0 && across < ravine.width + RAVINE_SIDE) continue;
                        }
                        boolean atRim = false;
                        if (overRim && ravine == null && data.y <= seaLevel + 1) {
                            final int lip = rimLevel(mountainPath, data.nodeIndex, seaLevel);
                            if (lip != Integer.MIN_VALUE) {
                                if (Math.min(originalY, result.targetY) < lip) continue;
                                data = data.atLevel(lip);
                                atRim = true;
                            }

                            final Ravine above = atRim ? null : ravineAbove(mountainPath, seaLevel, generator, level, randomState);
                            if (above != null) {
                                final double ox = x - above.px, oz = z - above.pz;
                                final double along = ox * above.ux + oz * above.uz;
                                if (along > -RAVINE_RIM && along < above.length + RAVINE_INTO
                                        && Math.abs(above.lateral(ox, oz, along)) < above.width + RAVINE_SIDE) continue;
                            }
                        }

                        final double gorge = data.terrainY - data.y;
                        final double fullW = channelFullWidth(data, x, z);

                        final double outletW = ravine != null ? Math.min(fullW, ravine.width)
                                : Math.min(fullW, Math.max(GORGE_RIM_OUTLET_MIN, GORGE_RIM_OUTLET * fullW));
                        double chanW = atRim ? outletW : fullW;
                        if (overRim && !atRim && outletW < fullW) {
                            final double funnel = Math.max(GORGE_RIM_FUNNEL_MIN, GORGE_RIM_FUNNEL * fullW);

                            final double d = ravine != null
                                    ? funnelDistance(mountainPath, data.nodeIndex, ravine.start, x, z, funnel)
                                    : rimDistance(mountainPath, data.nodeIndex, x, z, seaLevel, funnel);
                            if (!Double.isNaN(d)) {
                                final double t = Math.max(0.0, d - (ravine != null ? outletW + 1.0 : 0.0)) / funnel;
                                chanW = Mth.lerp(t * t * (3.0 - 2.0 * t), outletW, fullW);
                            }
                        } else if (fed != null && fed.width < fullW) {
                            final double funnel = Math.max(GORGE_RIM_FUNNEL_MIN, GORGE_RIM_FUNNEL * fullW);
                            final double d = funnelDistance(mountainPath, data.nodeIndex, 0, x, z, funnel);
                            if (!Double.isNaN(d)) {
                                final double t = Math.max(0.0, d - (fed.width + 1.0)) / funnel;
                                chanW = Mth.lerp(t * t * (3.0 - 2.0 * t), fed.width, fullW);
                            }
                        }

                        final boolean tunnel = Config.MOUNTAIN_RIVER_TUNNELS.get() && gorge > TUNNEL_FROM_DEPTH;

                        final double vaultSwell = tunnel
                                ? RIVER_BED_NOISE.get(x * 0.02, z * 0.02) * CAVE_SWELL : 0.0;
                        final double wallRough = tunnel
                                ? RIVER_BED_NOISE.get(x * 0.18, z * 0.18) * CAVE_ROUGH : 0.0;
                        final double roofRough = tunnel
                                ? RIVER_BED_NOISE.get((x + 512.0) * 0.14, (z - 512.0) * 0.14) * CAVE_ROUGH : 0.0;
                        final double vaultH0 = tunnel
                                ? Math.max(Config.MOUNTAIN_TUNNEL_HEIGHT.get(), 1.2 * chanW) + vaultSwell + roofRough : 0.0;
                        final double flare = tunnel
                                ? Mth.clamp(1.0 - (groundBefore - (data.y + vaultH0)) / PORTAL_CRUST, 0.0, 1.0)
                                : 0.0;

                        final double vaultR = chanW + Math.max(1.0,
                                (tunnel ? Config.MOUNTAIN_TUNNEL_LEDGE.get() : 0) + vaultSwell + wallRough + PORTAL_FLARE * flare);
                        final double vaultH = Math.max(TUNNEL_WAIST + 2.0, vaultH0 + PORTAL_FLARE * flare);

                        final boolean atLakeLevel = columnLake != null
                                && columnLake.covers(x, z)
                                && data.y >= columnLake.waterY - 2
                                && data.y <= columnLake.waterY + 1;

                        if (result.isMainRiverWater && data.distance >= chanW) {
                            if (result.cavernRoofY < originalY - 2 || result.targetY <= seaLevel - 1) {
                                continue;
                            }
                        }

                        double distToOutlet = data.nodeIndex * WatershedPath.NODE_SPACING;
                        boolean isNearOutlet = distToOutlet < 24.0;

                        double reach = Math.max(BANK_ZONE_MIN, BANK_ZONE_PER_WIDTH * chanW);

                        if (data.beyondEnd && (data.distance >= chanW || channelBowl(data, chanW) == 0)
                                && data.distance < chanW + Math.max(reach, RAVINE_NEAR)
                                && (capYields(mountainPath, data, x, z, colX, colZ, homeRx, homeRz, generator, level, randomState)
                                || capGoesOn(mountainPath, x, z, colX, colZ, homeRx, homeRz, generator, level, randomState))) {
                            continue;
                        }

                        if (data.distance < chanW + RAVINE_NEAR) riverRim = Math.max(riverRim, data.y);
                        if (data.distance < chanW + reach) {
                            mountainAnyLevel = Math.max(mountainAnyLevel, data.y);
                            if (data.distance - chanW < riverEdgeGap) {
                                riverEdgeGap = data.distance - chanW;
                                riverEdgeY = data.y;
                            }
                            result.modifies = true;
                            result.isWatershed = true;
                            result.isMountainRiver = true;

                            if (data.distance < chanW) {
                                isMountainWater = true;
                                result.isWatershedWall = false;

                                final double lat = Mth.clamp(data.lateral / chanW, -1.0, 1.0);
                                final double skew = ChannelForm.bendSkew(data.curvature, chanW);
                                int bowl = channelBowl(data, chanW);

                                if (!columnInLake && !atLakeLevel && ChannelForm.onBar(lat, skew)) {
                                    result.pointBar = true;
                                }

                                int localTargetY = columnInLake
                                        ? Math.min(originalY - 1, Math.min(columnLake.waterY, data.y) - bowl)
                                        : data.y - bowl;

                                if (!columnInLake && originalY >= seaLevel - 1) {
                                    localTargetY = Math.max(localTargetY, (seaLevel - 1) - bowl);
                                }

                                int targetWaterLevel = data.y;

                                if (isNearOutlet && !result.isMainRiverWater && !atRim && ravine == null) {
                                    int outletY = mountainPath.segmentsY[0];
                                    if (outletY < targetWaterLevel) {
                                        double blend = distToOutlet / 24.0;
                                        targetWaterLevel = (int) Math
                                                .floor(outletY + blend * (targetWaterLevel - outletY));
                                        if (!columnInLake) {
                                            localTargetY = targetWaterLevel - bowl;
                                        }
                                    }
                                }

                                if (result.isMainRiverWater) {
                                    targetWaterLevel = Math.min(targetWaterLevel, seaLevel - 1);
                                }

                                if (data.bankFloor != Integer.MIN_VALUE && localTargetY >= targetWaterLevel
                                        && !columnInLake && !atLakeLevel && !tunnel) {
                                    localTargetY = targetWaterLevel - 1;
                                }

                                if (localTargetY >= targetWaterLevel && !columnInLake && !atLakeLevel && !tunnel && !atRim
                                        && ravine == null && !result.isMainRiverWater) {
                                    final int rimHold = bankHold(mountainPath, data.nodeIndex, data.y, x, z, chanW, null, fed,
                                            overRim, seaLevel, true);
                                    if (rimHold > localTargetY && originalY < rimHold && rimHold - originalY <= BANK_HOLD_OVER) {
                                        localTargetY = rimHold;
                                    }
                                }
                                mountainWaterLevel = Math.min(mountainWaterLevel, targetWaterLevel);
                                if (localTargetY < targetWaterLevel) mountainWetLevel = Math.min(mountainWetLevel, targetWaterLevel);

                                if (atLakeLevel) {
                                    mountainLakeBedY = Math.min(mountainLakeBedY,
                                            Math.min(columnLake.waterY, targetWaterLevel) - bowl);
                                }

                                mountainTargetY = Math.min(mountainTargetY, localTargetY);
                                if (tunnel) {
                                    result.cavernRoofY = Math.max(result.cavernRoofY,
                                            vaultTop(mountainPath, data, vaultR, vaultH, groundBefore));
                                    vaultPaths++;
                                } else {
                                    result.cavernRoofY = 320;
                                }

                                if (data.distance < result.distanceFromCenter) {
                                    result.distanceFromCenter = data.distance;
                                    result.channelWidth = chanW;
                                    result.channelSlope = data.slope;
                                }
                            } else if (tunnel) {
                                if (data.distance >= vaultR) continue;
                                final int roofY = vaultTop(mountainPath, data, vaultR, vaultH, groundBefore);
                                final double s = (data.distance - chanW) / (vaultR - chanW);
                                final int floorY = data.y + (int) Math.round(
                                        TUNNEL_WAIST * (1.0 - Math.cos(s * Math.PI / 2.0)));

                                if (floorY >= roofY || floorY >= groundBefore) continue;
                                if (result.targetY >= originalY || floorY < result.targetY) {
                                    result.targetY = floorY;
                                }
                                result.cavernRoofY = Math.max(result.cavernRoofY, roofY);

                                vaultLevel = vaultLevel == Integer.MIN_VALUE ? data.y : Math.min(vaultLevel, data.y);
                                vaultPaths++;
                            } else {
                                if (columnIsLakeWater || atLakeLevel) {
                                    continue;
                                }
                                double dOut = data.distance - chanW;

                                double extraReach = 6.0;
                                double outerReach = reach + extraReach;

                                if (dOut <= outerReach) {
                                    if (data.y <= seaLevel && originalY <= seaLevel + 1) {
                                        continue;
                                    }

                                    if (originalY <= seaLevel - 1) {
                                        continue;
                                    }

                                    boolean needsBerm = originalY < data.y + 1;
                                    if (isNearOutlet && data.y <= seaLevel) {
                                        needsBerm = false;
                                    }

                                    if (columnLake != null && data.y <= columnLake.waterY + 1) {
                                        double distToHead = (mountainPath.nodeCount - 1 - data.nodeIndex)
                                                * WatershedPath.NODE_SPACING;
                                        if (isNearOutlet || distToHead < 24.0) {
                                            needsBerm = false;
                                        }
                                    }

                                    final int hold = dOut < BANK_HOLD_REACH && !atRim && (needsBerm || originalY >= data.y + 1)
                                            ? bankHold(mountainPath, data.nodeIndex, data.y, x, z, chanW, ravine, fed, overRim, seaLevel, false)
                                            : Integer.MIN_VALUE;
                                    if (hold > heldFor) {
                                        heldFor = hold;
                                        heldBy = mountainPath;
                                        heldNearMouth = isNearOutlet;
                                    }
                                    if (dOut < BANK_HOLD_NEAR) {
                                        if (data.y < lowNearY) {
                                            if (lowNearPath != mountainPath) lowNearY2 = lowNearY;
                                            lowNearY = data.y;
                                            lowNearPath = mountainPath;
                                        } else if (data.y < lowNearY2 && mountainPath != lowNearPath) {
                                            lowNearY2 = data.y;
                                        }
                                    }

                                    final double bankWidth = bankMeet(chanW);
                                    final double tOut = Math.min(1.0, dOut / bankWidth);

                                    final double wallNoise = RIVER_BED_NOISE.get(x * 0.15, z * 0.15) * 0.25;

                                    int finalTargetY = originalY;

                                    if (needsBerm) {
                                        final double dEff = dOut - 1.0;
                                        final int below = data.y - originalY;
                                        final int fall = Mth.clamp((int) Math.floor(ChannelForm.leveeFall(
                                                dEff, below, bankWidth - 1.0, LEVEE_TALUS) + wallNoise), 0, Math.max(0, below));

                                        int bermTop = data.y + 1 - fall;
                                        if (dOut < 1.0) {
                                            bermTop = data.y;
                                        } else if (dEff <= Math.max(LEVEE_CREST, chanW * 2.0 / 3.0)) {
                                            bermTop = Math.max(bermTop, data.y + 1);
                                        } else if (dEff <= LEVEE_SHOULDER) {
                                            bermTop = Math.max(bermTop, data.y);
                                        }

                                        if (data.bankFloor != Integer.MIN_VALUE) {
                                            bermTop = Math.max(bermTop,
                                                    data.bankFloor - (int) Math.floor(Math.max(0.0, dEff) * LEVEE_TALUS));
                                        }

                                        if (dOut > bankWidth) {
                                            double tOuter = Math.min(1.0, (dOut - bankWidth) / extraReach);
                                            double outerBlend = tOuter * tOuter * (3.0 - 2.0 * tOuter);
                                            bermTop = (int) Math.floor(bermTop + outerBlend * (originalY - bermTop));
                                        }

                                        finalTargetY = bermTop;
                                    } else {
                                        int bankTop = (int) Math.floor(data.y
                                                + ChannelForm.bankRise(dOut, originalY - data.y, bankWidth, BANK_TALUS) + wallNoise);

                                        if (originalY <= seaLevel + 1 && bankTop < seaLevel + 1) {
                                            bankTop = Math.max(bankTop, seaLevel + 1);
                                        }

                                        final int maxCut = (int) Math.round(MAX_BANK_CUT * (1.0 - tOut));
                                        final int steepest = (int) Math.floor(data.y + STEEP_BANK_TALUS * dOut + wallNoise);
                                        bankTop = Math.max(bankTop, Math.min(originalY - maxCut, steepest));

                                        if (columnLake != null && data.y < columnLake.waterY
                                                && columnLake.covers(x, z)) {
                                            bankTop = Math.max(bankTop, columnLake.waterY + 1);
                                        }
                                        bankTop = Math.max((int) data.y,
                                                Math.min(bankTop, Math.max((int) data.y, originalY)));

                                        if (data.bankFloor != Integer.MIN_VALUE) {
                                            bankTop = Math.max(bankTop, Math.min(originalY,
                                                    data.bankFloor - (int) Math.floor(Math.max(0.0, dOut - 1.0) * LEVEE_TALUS)));
                                        }

                                        if (dOut > bankWidth) {
                                            double tOuter = Math.min(1.0, (dOut - bankWidth) / extraReach);
                                            double outerBlend = tOuter * tOuter * (3.0 - 2.0 * tOuter);
                                            bankTop = (int) Math.floor(bankTop + outerBlend * (originalY - bankTop));
                                        }

                                        finalTargetY = bankTop;
                                    }

                                    if (hold > finalTargetY) {
                                        finalTargetY = hold;
                                        if (hold > originalY) needsBerm = true;
                                    }

                                    if (needsBerm) {
                                        if (result.targetY < originalY) {
                                        } else {
                                            result.targetY = Math.max(result.targetY, finalTargetY);
                                        }
                                    } else {
                                        if (result.targetY >= originalY || finalTargetY < result.targetY) {
                                            result.targetY = finalTargetY;
                                        }

                                        if (finalTargetY < originalY - 1) result.dressCut = true;
                                    }

                                    if (finalTargetY > originalY || originalY > data.y) {
                                        result.isWatershedWall = true;
                                        result.wallTopY = Math.max(result.wallTopY, finalTargetY);
                                    }
                                    result.cavernRoofY = 320;
                                }

                                result.sealFloorY = Math.min(result.sealFloorY,
                                        originalY - sealDepth(data.distance - chanW, chanW) + sealJitter(x, z));
                            }
                        }
                    }
                }
            }
        }

        if (ravineSide != Integer.MAX_VALUE) {
            final int top = Math.max(Math.max(ravineSide, ravineHold), riverRim + 1);
            if (top < groundBefore) {
                if (result.targetY >= originalY || top < result.targetY) result.targetY = top;
                result.modifies = true;
                result.cavernRoofY = 320;
                result.gorgeWall = true;
                result.gorgeSteep = false;
                result.gorgeSeal = true;
                result.dressCut = true;
            } else if (ravineHold > groundBefore && ravineHold - groundBefore <= RAVINE_RIM_BUILD && !isMountainWater) {
                result.targetY = Math.max(result.targetY, ravineHold);
                result.modifies = true;
                result.cavernRoofY = 320;
                result.isWatershed = true;
                result.isWatershedWall = true;
                result.wallTopY = Math.max(result.wallTopY, ravineHold);
                result.skinLevel = Math.max(result.skinLevel, ravineHold - 2);
            }
        }

        final int lowerBeside = lowNearPath != heldBy ? lowNearY : lowNearY2;
        if (heldFor > result.targetY && !isMountainWater && !result.isMainRiverWater
                && heldFor - originalY <= BANK_HOLD_OVER && (lowerBeside >= heldFor - 1 || !heldNearMouth)) {
            result.targetY = heldFor;
            result.modifies = true;
            result.isWatershed = true;
            result.isWatershedWall = true;
            result.wallTopY = Math.max(result.wallTopY, heldFor);
            result.skinLevel = Math.max(result.skinLevel, mountainAnyLevel);
            result.cavernRoofY = 320;
        }

        if (vaultPaths >= 2 && result.cavernRoofY > -999 && result.cavernRoofY < 320) {
            result.cavernRoofY += JUNCTION_CHAMBER;
        }

        if (isMountainWater) {
            result.isWatershedWall = false;

            result.waterLevel = Math.max(result.waterLevel,
                    mountainWetLevel != Integer.MAX_VALUE ? mountainWetLevel : mountainWaterLevel);

            boolean isOceanFloor = originalY < getSeaLevel() - 2 && mountainTargetY <= getSeaLevel();

            if (result.isMainRiverWater && result.targetY < mountainTargetY) {
            } else if (isOceanFloor) {
                result.isMountainRiver = false;
                if (!result.isMainRiverWater) {
                    result.modifies = false;
                    result.isWatershed = false;
                }
                result.targetY = Math.min(result.targetY, originalY);
                result.waterLevel = Math.max(result.waterLevel, getSeaLevel() - 1);
            } else {
                result.targetY = mountainTargetY;
            }
        } else if (vaultLevel > Integer.MIN_VALUE) {
            result.waterLevel = Math.max(result.waterLevel, vaultLevel);
        }

        if (isMountainWater) {
            result.skinLevel = result.waterLevel;
        } else if (mountainAnyLevel > Integer.MIN_VALUE) {
            result.skinLevel = mountainAnyLevel;
        }

        final WatershedLake lakeHere = columnLake != null ? columnLake
                : hollow != LakeHollows.Kind.NONE ? hollowLake : dugShore ? foundLake : null;
        if (lakeHere != null && laysLake(lakeHere, seaLevel)) {
            final int lakeWaterY = lakeHere.waterY;

            final boolean columnIsWater = columnIsLakeWater;

            if (columnIsWater) {
                final boolean riverAbove = mountainAnyLevel > lakeWaterY + 1;
                if (!result.isWatershedWall || result.wallTopY <= lakeWaterY || !riverAbove) {
                    result.modifies = true;
                    result.isWatershed = true;
                    result.isLake = true;
                    result.isWatershedWall = false;
                    result.cavernRoofY = 320;

                    final boolean channelAbove = isMountainWater && mountainWaterLevel > lakeWaterY + 1;
                    result.waterLevel = channelAbove ? Math.max(result.waterLevel, lakeWaterY) : lakeWaterY;
                    result.skinLevel = channelAbove ? Math.max(result.skinLevel, lakeWaterY) : lakeWaterY;
                    LakeHollows.counted(hollow, false);
                  if (columnInShore || hollow == LakeHollows.Kind.FLOODED
                          && originalY >= lakeHere.floorY() - WatershedLake.FLOOR_SLACK) {
                    result.targetY = Math.min(result.targetY, originalY);
                  } else {
                    final int bed = lakeWaterY - lakeDepth(lakeHere.shoreDistance(Mth.floor(x), Mth.floor(z)));
                    result.targetY = result.isMainRiverWater ? Math.min(result.targetY, bed) : bed;
                  }

                    if (mountainLakeBedY < result.targetY) result.targetY = mountainLakeBedY;
                }
            } else if ((!isMountainWater || result.targetY >= lakeWaterY) && riverEdgeY == lakeWaterY
                    && riverEdgeGap < LAKE_LIP_RIVER_GAP && lakeHere.covers(x, z)
                    && Math.min(originalY, result.modifies ? result.targetY : originalY) <= lakeWaterY
                    && originalY >= lakeWaterY - LAKE_SHORE_LIP) {
                LakeHollows.counted(hollow, false);
                result.modifies = true;
                result.isWatershed = true;
                result.isLake = true;
                result.isWatershedWall = false;
                result.targetY = Math.min(result.targetY, Math.min(originalY, lakeWaterY - 1));
                result.waterLevel = lakeWaterY;
                result.skinLevel = lakeWaterY;
                result.cavernRoofY = 320;
            } else if (originalY <= lakeWaterY && originalY >= lakeWaterY - lipDepth(lakeHere, columnLake != null,
                            hollow, x, z, originalY, generator, randomState)
                    && !isMountainWater
                    && (!result.modifies || result.targetY >= originalY)) {
                LakeHollows.counted(hollow, columnLake == null || originalY < lakeWaterY - LAKE_SHORE_LIP);
                result.modifies = true;
                result.isWatershed = true;
                result.isWatershedWall = true;
                result.targetY = Math.max(result.targetY, lakeWaterY);
                result.wallTopY = Math.max(result.wallTopY, lakeWaterY);
                result.skinLevel = Math.max(result.skinLevel, lakeWaterY);
                result.cavernRoofY = 320;
            } else if (originalY > lakeWaterY && !isMountainWater
                    && (!result.modifies || result.targetY >= originalY)
                    && lakeHere.covers(x, z)) {
                final double grade = lakeHere.shoreGrade(Mth.floor(x), Mth.floor(z));
                int shoreTop = lakeWaterY + (int) Math.round((originalY - lakeWaterY) * grade);
                shoreTop = Math.max(shoreTop, originalY - LAKE_SHORE_CUT);
                if (shoreTop < originalY) {
                    result.modifies = true;
                    result.isWatershed = true;
                    result.isWatershedWall = true;
                    result.targetY = shoreTop;
                    result.wallTopY = Math.max(result.wallTopY, shoreTop);
                    result.skinLevel = Math.max(result.skinLevel, lakeWaterY);
                    result.cavernRoofY = 320;
                }
            }
        }

        WatershedStats.carveCalls.increment();
        WatershedStats.carveNanos.add(System.nanoTime() - carveStart);
        return result;
    }

    public static double getRiverBedNoise(double x, double z) {
        return RIVER_BED_NOISE.get(x * 0.05, z * 0.05) * 1.5;
    }

    public static double getRiverJitter(double x, double z) {
        double jitter = Math.sin(x * 0.04 + z * 0.02) * 0.8;
        jitter += Math.sin(x * -0.02 + z * 0.04) * 0.6;
        return jitter;
    }

    public static net.minecraft.world.phys.Vec3 getRiverFlow(double x, int y, double z) {
        return getRiverFlow(x, y, z, null);
    }

    public static boolean isMountainRiverWater(int x, int y, int z) {
        final double cx = x + 0.5, cz = z + 0.5;
        if (isInLake(cx, cz)) return false;
        if (ravineFlow(cx, y, cz) != null) return true;
        final WatershedContext ctx = evaluateWatershedContext(cx, cz, getGlobalGenerator(), dummyAccessor,
                getGlobalRandomState(), true);
        if (ctx.bestPath == null || ctx.distance >= ctx.width * 1.5) return false;
        final WatershedPath.RiverNodeData data = ctx.bestPath.getClosestNodeData(cx, cz);
        return y >= data.y - 20 && y <= Math.max(data.y + 20, WORLD_SEA_LEVEL);
    }

    private static final double RIVER_BIOME_MIDDLE = 0.75;
    private static final double RIVER_BIOME_MIN = 2.9;

    public static boolean inMountainRiverMiddle(double x, int y, double z) {
        if (isInLake(x, z)) return false;
        if (ravineFlow(x, y, z) != null) return true;
        final WatershedContext ctx = evaluateWatershedContext(x, z, getGlobalGenerator(), dummyAccessor,
                getGlobalRandomState(), false);
        if (ctx.bestPath == null || ctx.distance >= Math.max(ctx.width * RIVER_BIOME_MIDDLE, RIVER_BIOME_MIN)) return false;
        final WatershedPath.RiverNodeData data = ctx.bestPath.getClosestNodeData(x, z);
        return y >= data.y - 20 && y <= Math.max(data.y + 20, WORLD_SEA_LEVEL);
    }

    public static boolean inLowlandWater(double x, double z) {
        final double[] on = new double[2];
        for (com.AdamNew.dynamicwaters.river.RiverGraphEdge edge : MainRivers.riversAt(x, z)) {
            final double d = Math.sqrt(edge.fractal().nearestPoint(x, z, on));
            if (d < flowWaterRadius(edge, on[0], on[1])) return true;
        }
        return false;
    }

    public static java.util.List<double[]> mountainRiverNodesNear(double x, double z, double minDist, double maxDist) {
        final java.util.List<double[]> near = new java.util.ArrayList<>();
        final int rx0 = Math.floorDiv(Mth.floor(x - maxDist), WATERSHED_REGION_SIZE);
        final int rx1 = Math.floorDiv(Mth.floor(x + maxDist), WATERSHED_REGION_SIZE);
        final int rz0 = Math.floorDiv(Mth.floor(z - maxDist), WATERSHED_REGION_SIZE);
        final int rz1 = Math.floorDiv(Mth.floor(z + maxDist), WATERSHED_REGION_SIZE);
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                final WatershedRegionData data = WATERSHED_REGION_CACHE.get(getRegionKey(rx, rz));
                if (data == null) continue;
                for (WatershedPath p : data.allPaths) {
                    if (x < p.minX - maxDist || x > p.maxX + maxDist || z < p.minZ - maxDist || z > p.maxZ + maxDist) continue;
                    for (int i = 0; i < p.validNodeCount; i += 2) {
                        final double d = Math.hypot(p.segmentsX[i] - x, p.segmentsZ[i] - z);
                        if (d >= minDist && d <= maxDist) near.add(new double[] { p.segmentsX[i], p.segmentsZ[i] });
                    }
                }
            }
        }
        return near;
    }

    private static final double FALL_OPEN = 6.0;

    public static boolean nearMountainFall(int x, int y, int z) {
        final double cx = x + 0.5, cz = z + 0.5;
        if (ravineFlow(cx, y, cz) != null) return true;
        final WatershedContext ctx = evaluateWatershedContext(cx, cz, getGlobalGenerator(), dummyAccessor,
                getGlobalRandomState(), true);
        final WatershedPath p = ctx.bestPath;
        if (p == null || ctx.bestNodeIndex < 0) return false;
        final double reach = ctx.width + FALL_OPEN;
        if (ctx.distance >= reach || y < ctx.bestY - 20 || y > ctx.bestY + 2) return false;
        int lo = ctx.bestY, hi = ctx.bestY;
        double s = 0.0;
        for (int i = ctx.bestNodeIndex; i > 0 && s <= reach; i--) {
            s += Math.hypot(p.segmentsX[i] - p.segmentsX[i - 1], p.segmentsZ[i] - p.segmentsZ[i - 1]);
            lo = Math.min(lo, p.segmentsY[i - 1]);
            hi = Math.max(hi, p.segmentsY[i - 1]);
        }
        s = 0.0;
        for (int i = ctx.bestNodeIndex; i + 1 < p.validNodeCount && s <= reach; i++) {
            s += Math.hypot(p.segmentsX[i + 1] - p.segmentsX[i], p.segmentsZ[i + 1] - p.segmentsZ[i]);
            lo = Math.min(lo, p.segmentsY[i + 1]);
            hi = Math.max(hi, p.segmentsY[i + 1]);
        }
        return hi > lo;
    }

    private static boolean stillDelta(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge) {
        return edge.delta() && !edge.deltaMain();
    }

    public static net.minecraft.world.phys.Vec3 getRiverFlow(double x, int y, double z,
            net.minecraft.world.level.Level level) {
        if (level != null) {
            net.minecraft.core.BlockPos scanPos = new net.minecraft.core.BlockPos((int) x, y + 1, (int) z);
            for (int i = 0; i < 16; i++) {
                net.minecraft.world.level.block.state.BlockState scanState = level.getBlockState(scanPos);
                if (scanState.getBlock() instanceof com.AdamNew.dynamicwaters.block.FlowOverrideBlock) {
                    boolean flowEnabled = scanState
                            .getValue(com.AdamNew.dynamicwaters.block.FlowOverrideBlock.FLOW_ENABLED);
                    if (!flowEnabled) {
                        return net.minecraft.world.phys.Vec3.ZERO;
                    }
                    int direction = scanState
                            .getValue(com.AdamNew.dynamicwaters.block.FlowOverrideBlock.FLOW_DIRECTION);
                    return com.AdamNew.dynamicwaters.block.FlowOverrideBlock.directionToVec3(direction);
                }

                if (!scanState.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                    break;
                }
                scanPos = scanPos.above();
            }
        }

        final net.minecraft.world.phys.Vec3 zoneFlow = FlowZones.flowAt(level, x, y, z);
        if (zoneFlow != null) return zoneFlow;

        if (!riversIn(level)) return net.minecraft.world.phys.Vec3.ZERO;

        if (y >= WORLD_SEA_LEVEL - 6 && y <= WORLD_SEA_LEVEL + 1) {
            final net.minecraft.world.phys.Vec3 vanilla = VanillaRiverFlow.flowAt(level, x, y, z);
            if (vanilla != null) return vanilla;
        }

        net.minecraft.world.level.chunk.ChunkGenerator generator = getGlobalGenerator();
        net.minecraft.world.level.levelgen.RandomState randomState = getGlobalRandomState();
        net.minecraft.world.level.LevelHeightAccessor accessor = level != null ? level : dummyAccessor;
        boolean useCacheOnly = (generator == null || randomState == null);

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            generator = com.AdamNew.dynamicwaters.compat.OneDimension.generator(serverLevel);
            randomState = com.AdamNew.dynamicwaters.compat.OneDimension.randomState(serverLevel);
            accessor = level;
            useCacheOnly = true;
        } else if (level != null && level.isClientSide()) {
            useCacheOnly = true;
        }

        double cx = x + 0.5;
        double cz = z + 0.5;

        if (isInLake(cx, cz)) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }

        net.minecraft.world.phys.Vec3 mountainFlow = ravineFlow(cx, y, cz);

        boolean pastLowlandMouth = false;
        if (mountainFlow == null) {
            WatershedContext mountainCtx = evaluateWatershedContext(cx, cz, generator, accessor, randomState,
                    useCacheOnly);

            if (mountainCtx.bestPath != null && mountainCtx.distance < mountainCtx.width * 1.5) {
                WatershedPath.RiverNodeData data = mountainCtx.bestPath.getClosestNodeData(cx, cz);

                int top = Math.max(data.y + 20, WORLD_SEA_LEVEL);
                if (data.y <= WORLD_SEA_LEVEL + 1) {
                    final int lip = rimLevel(mountainCtx.bestPath, data.nodeIndex, WORLD_SEA_LEVEL);
                    if (lip != Integer.MIN_VALUE) top = Math.max(top, lip + 1);
                }
                if (y >= data.y - 20 && y <= top) {
                    mountainFlow = data.flowDir;
                    pastLowlandMouth = mountainCtx.bestPath.hitMainRiver && data.beyondEnd && data.nodeIndex == 0
                            && (cx - data.x) * data.flowDir.x + (cz - data.z) * data.flowDir.z > 0.0;
                }
            }
        }
        if (mountainFlow != null) {
            if (y <= WORLD_SEA_LEVEL + 1) {
                final com.AdamNew.dynamicwaters.river.RiverContext parent = com.AdamNew.dynamicwaters.MainRivers
                        .findNearest(x, z, getGlobalGenerator(), getDummyAccessor(), getGlobalRandomState());
                if (parent != null) {
                    final double into = flowWaterRadius(parent.edge(), x, z) - Math.sqrt(parent.distSq());
                    if (into > 0.0) {
                        final net.minecraft.world.phys.Vec3 lowland = aroundRiverSplit(x, z, lowlandFlow(x, y, z, level));
                        if (into >= MOUTH_FLOW_BLEND) return lowland;
                        if (lowland.lengthSqr() < 1.0E-6) return mountainFlow;
                        final double t = into / MOUTH_FLOW_BLEND;
                        return turnTowards(mountainFlow, lowland, t * t * (3.0 - 2.0 * t));
                    }
                    if (pastLowlandMouth) return intoLowland(parent.edge(), x, z, mountainFlow);
                }
            }
            return mountainFlow;
        }
        return aroundRiverSplit(x, z, lowlandFlow(x, y, z, level));
    }

    private static final double MOUTH_FLOW_LEAD = 12.0;

    private static net.minecraft.world.phys.Vec3 intoLowland(com.AdamNew.dynamicwaters.river.RiverGraphEdge edge,
            double x, double z, net.minecraft.world.phys.Vec3 fallback) {
        final double[] on = new double[2];
        edge.fractal().nearestPoint(x, z, on);
        final net.minecraft.world.phys.Vec3 down = edge.fractal().calculateContinuousFlow(on[0], on[1]);
        final double dx = on[0] + down.x * MOUTH_FLOW_LEAD - x, dz = on[1] + down.z * MOUTH_FLOW_LEAD - z;
        final double len = Math.hypot(dx, dz);
        return len < 1.0E-6 ? fallback : new net.minecraft.world.phys.Vec3(dx / len, 0.0, dz / len);
    }

    private static final double MOUTH_FLOW_BLEND = 6.0;

    private static net.minecraft.world.phys.Vec3 turnTowards(net.minecraft.world.phys.Vec3 from,
            net.minecraft.world.phys.Vec3 to, double t) {
        if (from.lengthSqr() < 1.0E-6) return to;
        final double a = Math.atan2(from.z, from.x);
        double diff = Math.atan2(to.z, to.x) - a;
        while (diff < -Math.PI) diff += Math.PI * 2.0;
        while (diff >= Math.PI) diff -= Math.PI * 2.0;
        final double c = a + diff * t;
        return new net.minecraft.world.phys.Vec3(Math.cos(c), 0.0, Math.sin(c));
    }

    private static net.minecraft.world.phys.Vec3 lowlandFlow(double x, int y, double z,
            net.minecraft.world.level.Level level) {
        if (level != null) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos((int) x, y, (int) z);
            if (isOceanAt(level, pos)) {
                return net.minecraft.world.phys.Vec3.ZERO;
            }
        }

        com.AdamNew.dynamicwaters.river.RiverContext ctx = com.AdamNew.dynamicwaters.MainRivers.findNearest(x, z,
                HydrologyManager.getGlobalGenerator(), HydrologyManager.getDummyAccessor(),
                HydrologyManager.getGlobalRandomState());

        if ((y < WORLD_SEA_LEVEL - 6 || y > WORLD_SEA_LEVEL + 1)) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }

        if (level != null
                && isOceanBiome(level.getBiome(new net.minecraft.core.BlockPos(net.minecraft.util.Mth.floor(x),
                        net.minecraft.util.Mth.floor(y), net.minecraft.util.Mth.floor(z))))) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }

        if (ctx != null && stillDelta(ctx.edge())) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }

        if (ctx != null) {
            double rawDistance = Math.sqrt(ctx.distSq());
            double jitter = getRiverJitter(x, z);
            jitter *= (Math.sqrt(ctx.widthSq()) / 10.0);

            double distance = Math.max(0, rawDistance + jitter);

            if (distance < flowWaterRadius(ctx.edge(), x, z) + 1.0) {
                int rx = (int) Math.floor(x / REGION_SIZE);
                int rz = (int) Math.floor(z / REGION_SIZE);

                double sinSum = 0;
                double cosSum = 0;
                double totalWeight = 0;

                double minRatioFlow = Double.MAX_VALUE;
                java.util.List<com.AdamNew.dynamicwaters.river.RiverGraphEdge> regionPaths = com.AdamNew.dynamicwaters.MainRivers
                        .riversAt(x, z);
                for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : regionPaths) {
                    if (stillDelta(path)) continue;
                    if (path.fractal().boxDistanceSq(x, z) >= (path.width * 2.0) * (path.width * 2.0))
                        continue;
                    double dSq = path.fractal().intersectDistance(x, z);
                    if (dSq >= (path.width * 2.0) * (path.width * 2.0))
                        continue;
                    double ratio = Math.sqrt(dSq) / path.width;
                    if (ratio < minRatioFlow)
                        minRatioFlow = ratio;
                }
                double ratioThreshold = minRatioFlow * 1.3;

                for (com.AdamNew.dynamicwaters.river.RiverGraphEdge path : regionPaths) {
                    if (stillDelta(path)) continue;
                    if (path.fractal().boxDistanceSq(x, z) >= (path.width * 2.0) * (path.width * 2.0))
                        continue;

                    double dSq = path.fractal().intersectDistance(x, z);
                    if (dSq >= (path.width * 2.0) * (path.width * 2.0))
                        continue;

                    double d = Math.sqrt(dSq);
                    double ratio = d / path.width;

                    if (ratio > ratioThreshold)
                        continue;

                    double normDist = Math.min(1.0, d / path.width);
                    double wDist = 1.0 - normDist;
                    double w = wDist * wDist;

                    double drainX = path.fractal().segments[path.fractal().segments.length - 2];
                    double drainZ = path.fractal().segments[path.fractal().segments.length - 1];
                    double srcX = path.fractal().segments[0];
                    double srcZ = path.fractal().segments[1];
                    double totalLenSq = (drainX - srcX) * (drainX - srcX) + (drainZ - srcZ) * (drainZ - srcZ);
                    double toDrainSq = (drainX - x) * (drainX - x) + (drainZ - z) * (drainZ - z);
                    double drainProximity = (totalLenSq > 0) ? Math.sqrt(toDrainSq / totalLenSq) : 1.0;
                    double drainFade = Math.min(1.0, drainProximity / 0.4);
                    w *= drainFade;

                    if (w > 0) {
                        net.minecraft.world.phys.Vec3 flowVec = path.fractal().calculateContinuousFlow(x, z);
                        double angle = Math.atan2(flowVec.z, flowVec.x);
                        cosSum += Math.cos(angle) * w;
                        sinSum += Math.sin(angle) * w;
                        totalWeight += w;
                    }
                }

                net.minecraft.world.phys.Vec3 finalFlow = null;
                if (totalWeight > 0) {
                    double blendedAngle = Math.atan2(sinSum, cosSum);
                    finalFlow = new net.minecraft.world.phys.Vec3(Math.cos(blendedAngle), 0, Math.sin(blendedAngle));
                } else {
                    finalFlow = ctx.edge().fractal().calculateContinuousFlow(x, z);
                }

                return finalFlow;
            }
        }

        return net.minecraft.world.phys.Vec3.ZERO;
    }

    public static net.minecraft.world.phys.Vec3 findNearestRiverNode(double px, double pz) {
        int rx = (int) Math.floor(px / REGION_SIZE);
        int rz = (int) Math.floor(pz / REGION_SIZE);

        double minDist = Double.MAX_VALUE;
        double bestX = px;
        double bestZ = pz;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                java.util.List<com.AdamNew.dynamicwaters.river.RiverGraphEdge> regionPaths = java.util.Collections
                        .emptyList();
                for (com.AdamNew.dynamicwaters.river.RiverGraphEdge fractal : regionPaths) {
                    for (int i = 0; i < fractal.fractal().segments.length - 2; i += 2) {
                        double sx = fractal.fractal().segments[i];
                        double sz = fractal.fractal().segments[i + 1];
                        double ex = fractal.fractal().segments[i + 2];
                        double ez = fractal.fractal().segments[i + 3];

                        double segmentDx = ex - sx;
                        double segmentDz = ez - sz;
                        double len2 = segmentDx * segmentDx + segmentDz * segmentDz;

                        double t = 0;
                        if (len2 != 0) {
                            t = Math.max(0, Math.min(1, ((px - sx) * segmentDx + (pz - sz) * segmentDz) / len2));
                        }
                        double closestX = sx + t * segmentDx;
                        double closestZ = sz + t * segmentDz;

                        double d = (px - closestX) * (px - closestX) + (pz - closestZ) * (pz - closestZ);
                        if (d < minDist) {
                            minDist = d;
                            bestX = closestX;
                            bestZ = closestZ;
                        }
                    }
                }
            }
        }

        return new net.minecraft.world.phys.Vec3((int) bestX, 0, (int) bestZ);
    }

    public static boolean isNearMountainWater(int x, int z, int radius) {
        return isNearWatershed(x, z, radius);
    }

    private static boolean isNearWatershed(int x, int z, int radius) {
        final int reach = radius + WATERSHED_REGION_SIZE;
        final int r0x = Math.floorDiv(x - reach, WATERSHED_REGION_SIZE);
        final int r1x = Math.floorDiv(x + reach, WATERSHED_REGION_SIZE);
        final int r0z = Math.floorDiv(z - reach, WATERSHED_REGION_SIZE);
        final int r1z = Math.floorDiv(z + reach, WATERSHED_REGION_SIZE);
        for (int rz = r0z; rz <= r1z; rz++) {
            for (int rx = r0x; rx <= r1x; rx++) {
                WatershedRegionData data = WATERSHED_REGION_CACHE.get(getRegionKey(rx, rz));
                if (data == null) continue;
                for (WatershedPath p : data.allPaths) {
                    if (x >= p.influenceMinX() - radius && x <= p.influenceMaxX() + radius
                            && z >= p.influenceMinZ() - radius && z <= p.influenceMaxZ() + radius) {
                        return true;
                    }
                }
                for (WatershedLake lake : data.lakes) {
                    if (x >= lake.minX - radius && x <= lake.maxX + radius
                            && z >= lake.minZ - radius && z <= lake.maxZ + radius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isTooCloseToMountainWater(int minX, int maxX, int minZ, int maxZ, int minY, int maxY,
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.LevelHeightAccessor level,
            net.minecraft.world.level.levelgen.RandomState randomState) {
        int[] xs = { minX, maxX, (minX + maxX) / 2 };
        int[] zs = { minZ, maxZ, (minZ + maxZ) / 2 };

        for (int x : xs) {
            for (int z : zs) {
                WatershedContext mountainCtx = evaluateWatershedContext(x, z, generator, level, randomState, true);
                if (mountainCtx.bestPath != null) {
                    double baseBankWidth = 8.0;
                    double baseSegmentWidth = mountainCtx.bestPath.segmentWidths.length > 0
                            ? mountainCtx.bestPath.segmentWidths[0]
                            : 1.0;
                    if (baseSegmentWidth < 0.1)
                        baseSegmentWidth = 1.0;
                    double bankWidth = baseBankWidth * (mountainCtx.width / baseSegmentWidth);
                    double minBank = 5.0 * (mountainCtx.width / baseSegmentWidth);
                    if (bankWidth < minBank)
                        bankWidth = minBank;

                    if (mountainCtx.distance < mountainCtx.width + bankWidth) {
                        int riverY = mountainCtx.bestY;
                        if (Math.abs(minY - riverY) <= 70 || Math.abs(maxY - riverY) <= 70
                                || (minY <= riverY && maxY >= riverY)) {
                            return true;
                        }
                    }
                }

                WatershedLake lake = findLake(x, z, null, null, null, true);
                if (lake != null) {
                    int lakeY = lake.waterY;
                    if (Math.abs(minY - lakeY) <= 40 || Math.abs(maxY - lakeY) <= 40
                            || (minY <= lakeY && maxY >= lakeY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
