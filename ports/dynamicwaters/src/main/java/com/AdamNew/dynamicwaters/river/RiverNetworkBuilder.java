/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.region.AddRiversAndLakes.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 *
 * WHY THIS LOOKS DIFFERENT FROM UPSTREAM:
 * TFC generates its own continents and oceans before it ever thinks about
 * rivers, so by the time AddRiversAndLakes runs, every Region.Point already
 * carries a precomputed land/ocean flag and a distanceToOcean field from
 * earlier passes (AddContinentsAndSetOceanDepths, AnnotateDistanceToOcean).
 * Dynamic Waters has no such luxury - it overlays terrain that already
 * exists (vanilla, or Tectonic/Lithosphere/Larion), so there is no
 * precomputed field to read. OceanField below recreates just enough of one,
 * on demand, per region: it samples the underlying chunk generator's base
 * height on a coarse grid (reusing HydrologyManager's cached height query,
 * the same primitive already used for mountain rivers) and runs a small
 * multi-source BFS to get a local distance-to-ocean value. This is the
 * direct counterpart of TFC's precomputed field, just derived instead of
 * authored, and computed lazily for one region (+ a halo) at a time instead
 * of for the whole world up front.
 *
 * Everything downstream of that field - findBestStartingAngle, the
 * MultiParallelBuilder + isLegal subclass, and the width/link annotation
 * pass - mirrors upstream's algorithm closely, because that part doesn't
 * care where the field came from.
 *
 * KNOWN LIMITATION (shared with upstream): a region's rivers are built
 * independently of its neighbours. A real drainage basin that crosses a
 * region seam will be generated as two separate graphs that happen to
 * line up, because both regions sample the same real coastline - there is
 * no cross-region edge stitching. Lakes and rainfall annotation are also
 * intentionally not ported yet; they weren't part of the requested plan.
 */

package com.AdamNew.dynamicwaters.river;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.AdamNew.dynamicwaters.client.LoadingProgressTracker;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import com.AdamNew.dynamicwaters.HydrologyManager;

public final class RiverNetworkBuilder {
    /**
     * Length, in blocks, of one macro-graph step. Analogous to upstream's
     * AddRiversAndLakes.RIVER_LENGTH.
     */
    public static final float RIVER_STEP_LENGTH = 180f;
    /**
     * Max steps in a river's initial branch before it must have found the
     * sea-facing direction and committed.
     */
    public static final int RIVER_DEPTH = 20;
    /**
     * Two growing branches within this many blocks of each other merge instead of
     * crossing. (70 when the rivers were two thirds as wide: two side by side then ran bank to
     * bank. 110 for rivers a third wider than now.)
     */
    public static final float RIVER_FEATHER = 90f;

    /**
     * Spacing, in blocks, of the coarse land/ocean sampling grid. Increased to 32
     * for performance.
     */
    private static final int SAMPLE_STEP = 32;
    /**
     * How far past the region's own border we sample, purely so isLegal() has real
     * data near the seam.
     */
    private static final int HALO_BLOCKS = 8 * SAMPLE_STEP;

    /**
     * A region's rivers as they grow from its own coast, linked and widened, with nothing of its
     * neighbours' weighed against them: the seed and the ground decide them alone. See generate.
     */
    public static final class Raw {
        public final int rx, rz;
        /** The region's land field and rivers, for its own build alone, which shapes the rivers in place. */
        private OceanField field;
        private List<RiverGraphEdge> edges;
        /**
         * Every river's course, width, middle and box as it grew, and how many of the region's
         * rivers its water comes down - itself and all above it: the region's own build and the
         * neighbours' go by these, never by the rivers as they are cut and shaped.
         */
        private final double[][] courses;
        private final int[] widths;
        private final double[] middles;
        private final double[] boxes;
        private final int[] reaches;
        /** Each river's drain point, x, z, and the river it runs into, -1 at a mouth: see RiverShaper.DELTA_RIVER_GAP. */
        private final double[] ends;
        private final int[] drains;
        private final long nanos;

        private Raw(int rx, int rz, OceanField field, List<RiverGraphEdge> edges, long nanos) {
            this.rx = rx;
            this.rz = rz;
            this.field = field;
            this.edges = edges;
            this.nanos = nanos;
            final int n = edges.size();
            courses = new double[n][];
            widths = new int[n];
            middles = new double[2 * n];
            boxes = new double[4 * n];
            reaches = new int[n];
            final Map<RiverGraphEdge, Integer> index = new java.util.IdentityHashMap<>();
            for (int i = 0; i < n; i++) {
                final RiverGraphEdge e = edges.get(i);
                index.put(e, i);
                courses[i] = e.fractal().segments.clone();
                widths[i] = e.width;
                middles[2 * i] = (e.source().x() + e.drain().x()) * 0.5;
                middles[2 * i + 1] = (e.source().y() + e.drain().y()) * 0.5;
                box(courses[i], boxes, 4 * i);
            }
            for (RiverGraphEdge e : edges) {
                for (RiverGraphEdge d = e; d != null; d = d.drainEdge()) {
                    final Integer at = index.get(d);
                    if (at == null) break;
                    reaches[at]++;
                }
            }
            ends = new double[2 * n];
            drains = new int[n];
            for (int i = 0; i < n; i++) {
                final RiverGraphEdge e = edges.get(i);
                ends[2 * i] = e.drain().x();
                ends[2 * i + 1] = e.drain().y();
                final Integer d = e.drainEdge() == null ? null : index.get(e.drainEdge());
                drains[i] = d == null ? -1 : d;
            }
        }
    }

    /**
     * Grows a region's rivers - see Raw. Safe to call from any thread; MainRivers keeps every
     * region's, and builds each once.
     */
    public static Raw grow(int rx, int rz, long worldSeed, int regionSize,
            ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState) {
        final long started = System.nanoTime();
        long regionKey = (((long) rx & 0xFFFFFFFFL) | (((long) rz & 0xFFFFFFFFL) << 32));
        final int minX = rx * regionSize, minZ = rz * regionSize;
        final OceanField field = OceanField.sample(regionKey, minX - HALO_BLOCKS, minZ - HALO_BLOCKS,
                regionSize + 2 * HALO_BLOCKS, regionSize + 2 * HALO_BLOCKS, generator, level, randomState);
        field.computeDistanceToOcean(regionKey);

        final long regionSeed = regionSeed(rx, rz, worldSeed);
        final RandomSource random = new XoroshiroRandomSource(regionSeed);

        final RegionRiverGenerator riverGenerator = new RegionRiverGenerator(field);
        createInitialDrains(regionKey, riverGenerator, field, random, minX, minZ, regionSize);

        LoadingProgressTracker.setProgress(regionKey, 75, "Carving river beds...");
        final List<RiverGraphEdge> edges = new java.util.ArrayList<>(
                riverGenerator.build(e -> new RiverGraphEdge(e, random)));
        if (!edges.isEmpty()) {
            annotateNetwork(edges);
        }
        com.AdamNew.dynamicwaters.terrain.WatershedStats.lowlandGrown.increment();
        return new Raw(rx, rz, field, edges, System.nanoTime() - started);
    }

    private static long regionSeed(int rx, int rz, long worldSeed) {
        return (worldSeed + rx * 341873128712L + rz * 132897987541L) ^ 84213987123L;
    }

    /**
     * Builds the river macro-graph for a single region from its grown rivers ({@code own}) and its
     * eight neighbours' ({@code around}): see NEIGHBOUR_ROOM. A pure function of those - so of the
     * seed and the ground - and each region's to be built once (MainRivers), as it takes
     * {@code own}'s rivers and shapes them in place.
     */
    public static List<RiverGraphEdge> generate(int rx, int rz, long worldSeed,
            ChunkGenerator generator, RandomState randomState, Raw own, Raw[] around) {
        final long started = System.nanoTime();
        long regionKey = (((long) rx & 0xFFFFFFFFL) | (((long) rz & 0xFFFFFFFFL) << 32));
        final OceanField field = own.field;
        final List<RiverGraphEdge> edges = own.edges;
        own.field = null;
        own.edges = null;
        final long regionSeed = regionSeed(rx, rz, worldSeed);
        // See NEIGHBOUR_ROOM.
        int yielded = 0;
        for (int i = edges.size() - 1; i >= 0; i--) {
            if (yields(own, i, around)) {
                edges.remove(i);
                yielded++;
            }
        }
        com.AdamNew.dynamicwaters.terrain.WatershedStats.lowlandYielded.add(yielded);
        // What lay upstream of an edge taken out above has lost its way to the sea: it went, with
        // the rest of its branch. Left in, it was a river running from nowhere to nowhere.
        boolean pruned = true;
        while (pruned) {
            final java.util.Set<RiverGraphEdge> live = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            live.addAll(edges);
            pruned = edges.removeIf(e -> e.drainEdge() != null && !live.contains(e.drainEdge()));
        }
        // A river that lost its upper course begins where it now begins: a head (sourceEdge) that
        // tapers in, and from there it widens as any river does. Left as it grew, it began at the
        // width its lost course had given it, square across, where the course was cut.
        if (yielded > 0) {
            for (RiverGraphEdge e : edges) e.unlink();
            annotateNetwork(edges);
        }
        final long shaping = System.nanoTime();
        // The neighbours' rivers as they grew, for the deltas to keep clear of (RiverShaper.DELTA_RIVER_GAP),
        // but not those that yield to this region's in their own build, nor the branches above them:
        // they are not there. (They stood in the way of the deltas of the rivers they yielded to.)
        final List<double[]> beside = new java.util.ArrayList<>();
        final List<Integer> besideWidths = new java.util.ArrayList<>();
        final List<Double> besideMouths = new java.util.ArrayList<>();
        final Raw[] self = { own };
        for (Raw n : around) {
            final int m = n.widths.length;
            final boolean[] gone = new boolean[m];
            for (int j = 0; j < m; j++) gone[j] = yields(n, j, self);
            for (boolean more = true; more; ) {
                more = false;
                for (int j = 0; j < m; j++) {
                    if (!gone[j] && n.drains[j] >= 0 && gone[n.drains[j]]) gone[j] = more = true;
                }
            }
            for (int j = 0; j < m; j++) {
                if (gone[j]) continue;
                beside.add(n.courses[j]);
                besideWidths.add(n.widths[j]);
                if (n.drains[j] < 0 && n.widths[j] >= RiverShaper.DELTA_MIN_WIDTH) {
                    besideMouths.add(n.ends[2 * j]);
                    besideMouths.add(n.ends[2 * j + 1]);
                }
            }
        }
        // The ground's height for RiverShaper.DELTA_FLATS: the coarse height field, before any river.
        final int seaLevel = generator != null ? generator.getSeaLevel() : 63;
        final List<RiverGraphEdge> shaped = RiverShaper.shape(edges, regionSeed, new RiverShaper.Land() {
            @Override
            public boolean isLand(double x, double z) {
                return field.isLand((int) Math.round(x), (int) Math.round(z));
            }

            @Override
            public double height(double x, double z) {
                if (generator == null || randomState == null) return Double.NaN;
                final int cell = com.AdamNew.dynamicwaters.terrain.CoarseHeightField.CELL_SIZE;
                return com.AdamNew.dynamicwaters.terrain.CoarseHeightField.sample(
                        Math.floorDiv((int) Math.round(x), cell), Math.floorDiv((int) Math.round(z), cell),
                        generator, randomState) - seaLevel;
            }
        }, beside.toArray(new double[0][]), besideWidths.stream().mapToInt(Integer::intValue).toArray(),
                besideMouths.stream().mapToDouble(Double::doubleValue).toArray());
        final long done = System.nanoTime();
        com.AdamNew.dynamicwaters.terrain.WatershedStats.lowlandRegions.increment();
        com.AdamNew.dynamicwaters.terrain.WatershedStats.lowlandRegionNanos.add(done - started + own.nanos);
        com.AdamNew.dynamicwaters.terrain.WatershedStats.lowlandShapeNanos.add(done - shaping);
        LoadingProgressTracker.completeRegion(regionKey);
        labDump(rx, rz, field, shaped);
        return shaped;
    }

    /**
     * NEIGHBOUR_ROOM: where a region's river and a neighbour's come within this many of their
     * widths and a smallest river's of each other - the one's course to the other's middle - one
     * of them goes, and with it all of its branch above (the prune in generate): the one with
     * less of its region's rivers above it (Raw.reaches), so the least is lost; of two alike, the
     * narrower; of two as wide, the river of the region first in (rx, rz) order. Both regions
     * come to the same answer, on the rivers as they grew, so a region is the same whatever was
     * built before it. (It went by whatever neighbours happened to be built already, as they had
     * been cut and shaped: a region came out with 177 rivers, or 248, or none - built again with
     * itself in the cache - and a river or a delta built in one world session was not there in
     * the next. Going first by which region's ground a river was in, a river whose mouth lay
     * over the seam - its drain is put out to sea, maybe past it - lost its whole tree to a
     * stream of the region next door - twenty kilometres of river, in one world.)
     */
    private static final double NEIGHBOUR_ROOM = 3.0;

    private static boolean yields(Raw own, int i, Raw[] around) {
        final double mx = own.middles[2 * i], mz = own.middles[2 * i + 1];
        final int width = own.widths[i], reach = own.reaches[i];
        for (Raw n : around) {
            for (int j = 0; j < n.widths.length; j++) {
                final double room = (n.widths[j] + width + RiverGraphEdge.MIN_WIDTH) * NEIGHBOUR_ROOM;
                final double nx = n.middles[2 * j], nz = n.middles[2 * j + 1];
                final boolean meets = inBox(n.boxes, 4 * j, mx, mz, room)
                        && distanceSq(n.courses[j], mx, mz) < room * room
                        || inBox(own.boxes, 4 * i, nx, nz, room) && distanceSq(own.courses[i], nx, nz) < room * room;
                if (!meets) continue;
                if (n.reaches[j] != reach) {
                    if (n.reaches[j] > reach) return true;
                    continue;
                }
                if (n.widths[j] != width) {
                    if (n.widths[j] > width) return true;
                    continue;
                }
                if (n.rx < own.rx || n.rx == own.rx && n.rz < own.rz) return true;
            }
        }
        return false;
    }

    /** A course's bounding box into {@code out} at {@code at}: min x, max x, min z, max z. */
    private static void box(double[] s, double[] out, int at) {
        double mnx = Double.MAX_VALUE, mxx = -Double.MAX_VALUE, mnz = Double.MAX_VALUE, mxz = -Double.MAX_VALUE;
        for (int k = 0; k < s.length; k += 2) {
            mnx = Math.min(mnx, s[k]);
            mxx = Math.max(mxx, s[k]);
            mnz = Math.min(mnz, s[k + 1]);
            mxz = Math.max(mxz, s[k + 1]);
        }
        out[at] = mnx;
        out[at + 1] = mxx;
        out[at + 2] = mnz;
        out[at + 3] = mxz;
    }

    private static boolean inBox(double[] b, int at, double x, double z, double reach) {
        return x >= b[at] - reach && x <= b[at + 1] + reach && z >= b[at + 2] - reach && z <= b[at + 3] + reach;
    }

    private static double distanceSq(double[] s, double x, double z) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < s.length - 2; i += 2) {
            min = Math.min(min, RiverMathUtils.distancePointToLineSq(s[i], s[i + 1], s[i + 2], s[i + 3], x, z));
        }
        return min;
    }

    /** LAB ONLY (-Ddynamicwaters.labDumpRivers=<file>): the region's land field and edges, for drawing. */
    private static void labDump(int rx, int rz, OceanField field, List<RiverGraphEdge> edges) {
        final String file = System.getProperty("dynamicwaters.labDumpRivers");
        if (file == null) return;
        final StringBuilder sb = new StringBuilder();
        sb.append("L ").append(rx).append(' ').append(rz).append(' ').append(field.originX).append(' ')
                .append(field.originZ).append(' ').append(field.cols).append(' ').append(field.rows).append(' ')
                .append(SAMPLE_STEP).append(' ');
        for (boolean b : field.land) sb.append(b ? '1' : '0');
        sb.append('\n');
        final Map<RiverGraphEdge, Integer> index = new HashMap<>();
        for (int i = 0; i < edges.size(); i++) index.put(edges.get(i), i);
        for (int i = 0; i < edges.size(); i++) {
            final RiverGraphEdge e = edges.get(i);
            final Integer d = e.drainEdge() == null ? null : index.get(e.drainEdge());
            sb.append("E ").append(rx).append(' ').append(rz).append(' ').append(i).append(' ').append(e.width)
                    .append(' ').append(e.sourceEdge() ? 1 : 0).append(' ').append(d == null ? -1 : d);
            for (double v : e.fractal().segments) sb.append(' ').append(Math.round(v * 10) / 10.0);
            sb.append('\n');
            if (e.delta()) {
                sb.append("D ").append(rx).append(' ').append(rz).append(' ').append(i).append(' ')
                        .append(Math.round(e.flareRadius() * 10) / 10.0).append(' ')
                        .append(Math.round(e.flareLength() * 10) / 10.0).append(' ')
                        .append(Math.round(e.tipLength() * 10) / 10.0).append(' ')
                        .append(e.deltaMain() ? 1 : 0).append('\n');
            }
        }
        synchronized (RiverNetworkBuilder.class) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of(file), sb, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    /**
     * The side of the square of coast one river mouth is looked for on, per density level - see
     * {@code Config.NORMAL_RIVER_DENSITY}. What counts is how many squares fit along a 2048-block
     * region (a narrow leftover is skipped): 1, 2, 3, 4, 5, 8 - so 1, 4, 9, 16, 25, 64 mouths a
     * region. HIGH is the spacing the mod has always used. MEDIUM, between 4 and 9, is HIGH's
     * squares with a share of them left out - see {@link #drainKeep}.
     */
    public static int drainSpacing(com.AdamNew.dynamicwaters.Config.DensityLevel level) {
        return switch (level) {
            case VERY_LOW -> 2048;
            case LOW -> 1024;
            case MEDIUM, HIGH -> 704;
            case ULTRA -> 512;
            case EXTREME -> 410;
            case INSANE -> 256;
        };
    }

    /** A number in [0, 1) fixed by the seed and the square. */
    private static float unitHash(long seed, int x, int z) {
        final long h = it.unimi.dsi.fastutil.HashCommon.mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL));
        return (h >>> 40) * 0x1.0p-24f;
    }

    /** Share of the squares that get a river mouth looked for at all. */
    static float drainKeep(com.AdamNew.dynamicwaters.Config.DensityLevel level) {
        return level == com.AdamNew.dynamicwaters.Config.DensityLevel.MEDIUM ? 0.65f : 1.0f;
    }

    private static void createInitialDrains(long regionKey, RegionRiverGenerator riverGenerator, OceanField field,
            RandomSource random, int minX, int minZ, int regionSize) {
        LoadingProgressTracker.setProgress(regionKey, 50, "Searching for river drains...");
        // Deliberately restricted to the region's own bounds (not the halo) - the halo
        // exists only so
        // isLegal() has real neighbours to check near the border, not so we spawn
        // duplicate river mouths
        // in what's really the neighbouring region's territory.
        final com.AdamNew.dynamicwaters.Config.DensityLevel density = com.AdamNew.dynamicwaters.Config.NORMAL_RIVER_DENSITY.get();
        final int DRAIN_STEP = drainSpacing(density);
        final float keep = drainKeep(density);
        // Drawn only when some squares are left out, so the other levels' rivers - HIGH's above
        // all - come out of the region's random exactly as they always have.
        final long thinSeed = keep < 1.0f ? random.nextLong() : 0L;
        for (int x = minX; x < minX + regionSize; x += DRAIN_STEP) {
            // A spacing that does not divide the region leaves a narrow last square. At under half
            // the spacing it is skipped, so each level has a whole number of squares a region.
            if (minX + regionSize - x < DRAIN_STEP / 2) continue;
            for (int z = minZ; z < minZ + regionSize; z += DRAIN_STEP) {
                if (minZ + regionSize - z < DRAIN_STEP / 2) continue;
                if (keep < 1.0f && unitHash(thinSeed, x, z) >= keep) continue;
                int bestShoreX = -1;
                int bestShoreZ = -1;
                for (int dx = 0; dx < DRAIN_STEP; dx += SAMPLE_STEP) {
                    for (int dz = 0; dz < DRAIN_STEP; dz += SAMPLE_STEP) {
                        if (field.isShore(x + dx, z + dz)) {
                            bestShoreX = x + dx;
                            bestShoreZ = z + dz;
                            break;
                        }
                    }
                    if (bestShoreX != -1)
                        break;
                }

                // NEW: Ocean start fallback. Rivers can start from the ocean!
                if (bestShoreX == -1) {
                    for (int dx = 0; dx < DRAIN_STEP; dx += SAMPLE_STEP) {
                        for (int dz = 0; dz < DRAIN_STEP; dz += SAMPLE_STEP) {
                            if (!field.isLand(x + dx, z + dz) && random.nextFloat() < 0.05f) { // 5% chance
                                bestShoreX = x + dx;
                                bestShoreZ = z + dz;
                                break;
                            }
                        }
                        if (bestShoreX != -1)
                            break;
                    }
                }

                if (bestShoreX != -1) {
                    final float bestAngle = findBestStartingAngle(field, random, bestShoreX, bestShoreZ);
                    if (!Float.isNaN(bestAngle)) {
                        final RandomSource rng = new XoroshiroRandomSource(random.nextLong());
                        double extension = 60.0; // Increased to push drain further into ocean
                        double startX = bestShoreX + 0.5 - Math.cos(bestAngle) * extension;
                        double startZ = bestShoreZ + 0.5 - Math.sin(bestAngle) * extension;
                        riverGenerator.add(new RiverInstance.Builder(rng, startX, startZ, bestAngle,
                                RIVER_STEP_LENGTH, RIVER_DEPTH, RIVER_FEATHER));
                    }
                }
            }
        }
    }

    /**
     * Same algorithm as upstream's findBestStartingAngle: look at the 8
     * neighbouring sample cells, score each
     * by how much further from the ocean it is (favouring the one that also isn't
     * just diagonal padding), and
     * head that way with a little jitter so rivers don't all start on exact
     * 45-degree angles.
     */
    private static float findBestStartingAngle(OceanField field, RandomSource random, int x, int z) {
        float bestMetric = Float.MIN_VALUE;
        int bestCount = 0;
        float bestAngle = Float.NaN;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0)
                    continue;

                final int nx = x + dx * SAMPLE_STEP, nz = z + dz * SAMPLE_STEP;
                if (field.isLand(nx, nz)) {
                    final float metric = field.distanceToOcean(nx, nz) - Math.abs(dx) - Math.abs(dz);
                    if (metric > bestMetric || (metric == bestMetric && random.nextInt(1 + bestCount) == 0)) {
                        if (metric > bestMetric) {
                            bestMetric = metric;
                            bestCount = 0;
                        }
                        bestCount += 1;
                        bestAngle = (float) Math.atan2(dz, dx);
                    }
                }
            }
        }
        if (!Float.isNaN(bestAngle)) {
            bestAngle += random.nextFloat() * 0.2f - 0.1f;
        } else {
            // If started deep in the ocean and no land is nearby, pick a random angle!
            bestAngle = random.nextFloat() * (float) Math.PI * 2.0f;
        }
        return bestAngle;
    }

    /**
     * Links every edge to whatever's immediately downstream of it, then walks each
     * headwater-to-mouth chain
     * once to taper width up as tributaries accumulate. Same two-pass approach as
     * upstream; lake placement and
     * rainfall annotation are not ported (out of scope for the macro-graph step).
     */
    private static void annotateNetwork(List<RiverGraphEdge> edges) {
        final Map<RiverInstance.Vertex, RiverGraphEdge> bySource = new HashMap<>();
        for (RiverGraphEdge edge : edges) {
            bySource.put(edge.source(), edge);
        }
        for (RiverGraphEdge edge : edges) {
            edge.linkToDrain(bySource.get(edge.drain()));
        }

        for (RiverGraphEdge edge : edges) {
            if (!edge.sourceEdge()) {
                int width = RiverGraphEdge.MIN_WIDTH;
                RiverGraphEdge cursor = edge;
                while (cursor != null) {
                    cursor.width = Math.max(cursor.width, width);
                    cursor = cursor.drainEdge();
                    // Full width four steps from a source, as it always was.
                    width = Math.min(width + RiverGraphEdge.WIDTH_STEP, RiverGraphEdge.MAX_WIDTH);
                }
            }
        }
    }

    /**
     * MultiParallelBuilder subclass whose legality check is "on land, and
     * monotonically further from the ocean".
     */
    private static final class RegionRiverGenerator extends RiverInstance.MultiParallelBuilder {
        private final OceanField field;

        RegionRiverGenerator(OceanField field) {
            this.field = field;
        }

        @Override
        protected boolean isLegal(RiverInstance.Vertex prev, RiverInstance.Vertex vertex) {
            if (vertex.distance() > 3) {
                if (!field.isLand(round(prev.x()), round(prev.y()))) {
                    return false;
                }
            }

            // Nothing of the neighbouring regions' rivers: a region's rivers grow alike whatever is
            // built round it (Raw), and meet the neighbours' in generate - see NEIGHBOUR_ROOM.
            if (vertex.distance() > 3) {
                if (!field.isLand(round(vertex.x()), round(vertex.y()))) {
                    return true;
                }
                final int prevDist = field.distanceToOcean(round(prev.x()), round(prev.y()));
                final int nextDist = field.distanceToOcean(round(vertex.x()), round(vertex.y()));
                return nextDist >= prevDist || nextDist >= 12;
            }

            return true;
        }

        private static int round(double v) {
            return (int) Math.round(v);
        }
    }

    /**
     * A coarse, on-demand land/ocean/distance-to-ocean field over a rectangular
     * block-space window, sampled by
     * probing the chunk generator's base height. This is the derived stand-in for
     * TFC's precomputed Region.Point
     * data described in the class-level comment above.
     */
    private static final class OceanField {
        static OceanField sample(long regionKey, int originX, int originZ, int width, int depth,
                ChunkGenerator generator, LevelHeightAccessor level, RandomState randomState) {
            LoadingProgressTracker.setProgress(regionKey, 0, "Scanning terrain (Biomes)...");
            LoadingProgressTracker.isGenerating = true;
            final int cols = (width + SAMPLE_STEP - 1) / SAMPLE_STEP + 1;
            final int rows = (depth + SAMPLE_STEP - 1) / SAMPLE_STEP + 1;
            final boolean[] land = new boolean[cols * rows];

            // Land is what the biome there is not an ocean. Column by column over the height
            // sampler's threads: under a heavy terrain generator every biome is costly - on
            // Lithosphere a region's six thousand took minutes on one thread, and every chunk
            // waited on it.
            com.AdamNew.dynamicwaters.terrain.CoarseHeightField.forEachParallel(cols, i -> {
                for (int j = 0; j < rows; j++) {
                    final int x = originX + i * SAMPLE_STEP, z = originZ + j * SAMPLE_STEP;
                    land[j * cols + i] = !HydrologyManager.isOceanBiomeAt(generator, x, z, randomState);
                }
            });
            return new OceanField(originX, originZ, cols, rows, land);
        }

        private final int originX, originZ, cols, rows;
        private final boolean[] land;
        private int[] distanceToOcean;

        private OceanField(int originX, int originZ, int cols, int rows, boolean[] land) {
            this.originX = originX;
            this.originZ = originZ;
            this.cols = cols;
            this.rows = rows;
            this.land = land;
        }

        boolean isLand(int blockX, int blockZ) {
            final int idx = index(blockX, blockZ);
            return idx >= 0 && land[idx];
        }

        boolean isShore(int blockX, int blockZ) {
            if (!isLand(blockX, blockZ))
                return false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0)
                        continue;
                    if (!isLand(blockX + dx * SAMPLE_STEP, blockZ + dz * SAMPLE_STEP))
                        return true;
                }
            }
            return false;
        }

        /**
         * @return BFS distance in sample-steps to the nearest ocean cell, or -1 if this
         *         cell is out of bounds.
         */
        int distanceToOcean(int blockX, int blockZ) {
            final int idx = index(blockX, blockZ);
            return idx >= 0 ? distanceToOcean[idx] : -1;
        }

        void computeDistanceToOcean(long regionKey) {
            LoadingProgressTracker.setProgress(regionKey, 25, "Calculating distance to oceans...");
            distanceToOcean = new int[cols * rows];
            java.util.Arrays.fill(distanceToOcean, -1);

            final Queue<Integer> queue = new ArrayDeque<>();
            for (int i = 0; i < land.length; i++) {
                if (!land[i]) {
                    distanceToOcean[i] = 0;
                    queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                final int idx = queue.poll();
                final int x = idx % cols, z = idx / cols;
                final int nextDist = distanceToOcean[idx] + 1;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0)
                            continue;
                        final int nx = x + dx, nz = z + dz;
                        if (nx < 0 || nx >= cols || nz < 0 || nz >= rows)
                            continue;
                        final int nIdx = nz * cols + nx;
                        if (distanceToOcean[nIdx] == -1) {
                            distanceToOcean[nIdx] = nextDist;
                            queue.add(nIdx);
                        }
                    }
                }
            }
        }

        private int index(int blockX, int blockZ) {
            final int i = Math.round((float) (blockX - originX) / SAMPLE_STEP);
            final int j = Math.round((float) (blockZ - originZ) / SAMPLE_STEP);
            if (i < 0 || i >= cols || j < 0 || j >= rows)
                return -1;
            return j * cols + i;
        }
    }
}
