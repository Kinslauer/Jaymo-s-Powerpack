/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.region.RiverEdge.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 *
 * DIFFERENCES FROM UPSTREAM:
 *  - TFC works in an abstract "grid" coordinate space (1 grid unit = several
 *    blocks, see their Units.java) because TFC generates continents/oceans
 *    itself and needs a coarse space to do that cheaply. Dynamic Waters does
 *    not generate terrain, it overlays an existing world, so this version
 *    works directly in block coordinates - there is no grid<->block
 *    conversion layer.
 *  - The partition bounding box (minPartX/Z, maxPartX/Z) is sized off
 *    RiverNetworkBuilder.RIVER_STEP_LENGTH instead of AddRiversAndLakes.RIVER_LENGTH.
 *  - widthSq(x, z) fixes a small upstream bug where the interpolation call
 *    passes drain().y() twice instead of drain().x(), drain().y(), and grows
 *    an edge by at most one WIDTH_STEP toward its drain's width (drainWidth).
 */

package com.AdamNew.dynamicwaters.river;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a single {@link RiverInstance.Edge} (one segment of the macro-graph) with everything the rest of the pipeline
 * needs but the raw graph builder doesn't know about: its course (a {@link RiverPathFractal} at first, then the
 * smooth meandering curve {@link RiverShaper} lays through the whole river), the interpolated channel width, and the
 * link to whichever edge is immediately downstream of it.
 * <p>
 * One of these is created per {@link RiverInstance.Edge} once, right after {@link RiverInstance.MultiParallelBuilder#build} runs
 * for a region (see {@link RiverNetworkBuilder}), and is then immutable except for the width/link fields, which
 * {@link RiverNetworkBuilder} fills in during its downstream annotation pass.
 */
public final class RiverGraphEdge
{
    /**
     * Narrowest a headwater stream will render, and widest a main river - a measure, not blocks:
     * the water's radius is HydrologyManager.normalRiverWaterRadius of it, 12.8 to 26 blocks, a
     * river 26 to 52 across. (They were 6 and 14, 18 to 38 across: thin for a lowland river; then
     * 12 and 28, 33 to 72 across: too thick.) Everything that asks how wide a lowland river is -
     * its banks, its current, the trees kept off them, its biome, the routing's sinks - goes by
     * this, so it all grows together.
     */
    public static final int MIN_WIDTH = 9;
    public static final int MAX_WIDTH = 20;
    /**
     * How much wider a river is one edge further down its course: full width four edges from a
     * source. See RiverNetworkBuilder's annotation and {@link #widthAt}.
     */
    public static final int WIDTH_STEP = (MAX_WIDTH - MIN_WIDTH + 3) / 4;

    /**
     * How far (in blocks) from an edge's midpoint it could possibly still affect terrain: half the
     * step, the meanders (RiverShaper.MAX_SWING), and the widest river's banks or a gorge's walls.
     */
    static final int MAX_AFFECTING_BLOCK_DISTANCE = 1 + Mth.ceil(1.5f * RiverNetworkBuilder.RIVER_STEP_LENGTH) + 60;

    public int width = MIN_WIDTH;
    /**
     * One of a delta's channels - see RiverShaper. Its water is {@link #deltaWaterRadius} of its
     * width, not a river's: a delta's channels are narrow, down to creeks three blocks across. An
     * arm leaves the river - or the arm - it divides from at that one's water radius,
     * {@code flareRadius}, and narrows to its own over the first {@code flareLength} blocks; a
     * creek that ends in the flats comes to a point over the last {@code tipLength}. See
     * {@link #deltaRadius}. {@code deltaMain}: one of the arms the river itself divides into at
     * the apex - the river going on, with its current; the rest of a delta's channels have none.
     */
    private boolean delta, deltaMain;
    private double flareRadius, flareLength, tipLength;

    private final RiverInstance.Vertex source, drain;
    /** The course itself: the builder's fractal at first, then the shaped curve - see RiverShaper. */
    private RiverPathFractal fractal;

    final int minPartX, minPartZ, maxPartX, maxPartZ;

    private boolean sourceEdge;
    private @Nullable RiverGraphEdge drainEdge;

    public RiverGraphEdge(RiverInstance.Edge edge, RandomSource random)
    {
        this(edge, random, 4, MIN_WIDTH, false, null);
    }

    private int cachedDrainWidth;

    public RiverGraphEdge(RiverInstance.Edge edge, RandomSource random, int bisections, int width, boolean sourceEdge, @Nullable RiverGraphEdge drainEdge)
    {
        this.source = edge.source();
        this.drain = edge.drain();
        this.fractal = edge.fractal(random, bisections);
        this.width = width;
        this.cachedDrainWidth = width;

        final int centerX = (int) Math.round(0.5 * (edge.source().x() + edge.drain().x()));
        final int centerZ = (int) Math.round(0.5 * (edge.source().y() + edge.drain().y()));

        this.minPartX = RiverPartition.blockToCell(centerX - MAX_AFFECTING_BLOCK_DISTANCE);
        this.minPartZ = RiverPartition.blockToCell(centerZ - MAX_AFFECTING_BLOCK_DISTANCE);
        this.maxPartX = RiverPartition.blockToCell(centerX + MAX_AFFECTING_BLOCK_DISTANCE);
        this.maxPartZ = RiverPartition.blockToCell(centerZ + MAX_AFFECTING_BLOCK_DISTANCE);

        this.sourceEdge = sourceEdge;
        this.drainEdge = drainEdge;
    }

    /**
     * An edge whose course is already known, source first: one of a delta's channels - see
     * {@link #delta} and RiverShaper. Its partition box is centred on the course's middle point.
     */
    RiverGraphEdge(RiverInstance.Vertex source, RiverInstance.Vertex drain, double[] points, int width,
                   double flareRadius, double flareLength, double tipLength, boolean deltaMain)
    {
        this.source = source;
        this.drain = drain;
        this.fractal = RiverPathFractal.ofPoints(points);
        this.width = width;
        this.delta = true;
        this.deltaMain = deltaMain;
        this.flareRadius = flareRadius;
        this.flareLength = flareLength;
        this.tipLength = tipLength;
        this.cachedDrainWidth = width;
        final int mid = (points.length / 4) * 2;
        final int centerX = (int) Math.round(points[mid]);
        final int centerZ = (int) Math.round(points[mid + 1]);
        this.minPartX = RiverPartition.blockToCell(centerX - MAX_AFFECTING_BLOCK_DISTANCE);
        this.minPartZ = RiverPartition.blockToCell(centerZ - MAX_AFFECTING_BLOCK_DISTANCE);
        this.maxPartX = RiverPartition.blockToCell(centerX + MAX_AFFECTING_BLOCK_DISTANCE);
        this.maxPartZ = RiverPartition.blockToCell(centerZ + MAX_AFFECTING_BLOCK_DISTANCE);
        this.sourceEdge = true;
        this.drainEdge = null;
    }

    /** Replaces the course with a shaped one, source first. Before the edge is published only. */
    void reshape(double[] points)
    {
        this.fractal = RiverPathFractal.ofPoints(points);
    }

    // Client-side constructor for networking
    public RiverGraphEdge(int width, boolean sourceEdge, int cachedDrainWidth, boolean delta, boolean deltaMain,
                          double flareRadius, double flareLength, double tipLength, double sourceX, double sourceZ,
                          double drainX, double drainZ, double[] fractalSegments, double fractalNorm)
    {
        this.source = new RiverInstance.Vertex(sourceX, sourceZ, 0, 0, 0); // Fake vertex
        this.drain = new RiverInstance.Vertex(drainX, drainZ, 0, 0, 0); // Fake vertex
        this.fractal = new RiverPathFractal(fractalSegments, fractalNorm);
        this.width = width;
        this.delta = delta;
        this.deltaMain = deltaMain;
        this.flareRadius = flareRadius;
        this.flareLength = flareLength;
        this.tipLength = tipLength;
        this.cachedDrainWidth = cachedDrainWidth;
        this.sourceEdge = sourceEdge;
        this.drainEdge = null; // Doesn't matter for flow calculation on client

        final int centerX = (int) Math.round(0.5 * (sourceX + drainX));
        final int centerZ = (int) Math.round(0.5 * (sourceZ + drainZ));

        this.minPartX = RiverPartition.blockToCell(centerX - MAX_AFFECTING_BLOCK_DISTANCE);
        this.minPartZ = RiverPartition.blockToCell(centerZ - MAX_AFFECTING_BLOCK_DISTANCE);
        this.maxPartX = RiverPartition.blockToCell(centerX + MAX_AFFECTING_BLOCK_DISTANCE);
        this.maxPartZ = RiverPartition.blockToCell(centerZ + MAX_AFFECTING_BLOCK_DISTANCE);
    }

    public RiverInstance.Vertex source()
    {
        return source;
    }

    /** @return {@code true} if some other edge's drain links into this edge's source, i.e. this is a headwater. */
    public boolean sourceEdge()
    {
        return sourceEdge;
    }

    public RiverInstance.Vertex drain()
    {
        return drain;
    }

    /** @return The edge immediately downstream of this one, or {@code null} if this edge ends at a mouth. */
    @Nullable
    public RiverGraphEdge drainEdge()
    {
        return drainEdge;
    }

    public RiverPathFractal fractal()
    {
        return fractal;
    }

    public int widthSq()
    {
        return width * width;
    }

    /**
     * The channel width at a given (block) position: this edge's own width at its source, growing
     * along the edge to {@link #drainWidth} at its drain, so a river widens evenly down its course.
     * (Every consumer but the carve went by this; the carve took each edge's width whole, and the
     * river stepped a few blocks wider or narrower at every vertex.)
     */
    public double widthAt(double exactX, double exactZ)
    {
        final double lerpFac = RiverMathUtils.projectAlongLine(
            source().x(), source().y(),
            drain().x(), drain().y(),
            exactX, exactZ
        );
        return Mth.lerp(lerpFac, width, drainWidth());
    }

    /** The water radius of a delta's channel {@code width} wide - see {@link #delta}. */
    public static double deltaWaterRadius(int width)
    {
        return 1.5 + width;
    }

    /**
     * A delta channel's water radius at a (block) position: its own, eased out of its flare at the
     * source and into its point at a blind end - see {@link #delta}.
     */
    public double deltaRadius(double exactX, double exactZ)
    {
        double r = deltaWaterRadius(width);
        if (flareLength > 0.0 && flareRadius > r) {
            final double t = Math.hypot(exactX - source.x(), exactZ - source.y()) / flareLength;
            if (t < 1.0) r = Mth.lerp(t * t * (3.0 - 2.0 * t), flareRadius, r);
        }
        if (tipLength > 0.0) {
            final double t = Math.hypot(exactX - drain.x(), exactZ - drain.y()) / tipLength;
            if (t < 1.0) r *= Math.sqrt(t * t * (3.0 - 2.0 * t));
        }
        return r;
    }

    /** The widest this edge's water is anywhere, in blocks: at its drain, or at a delta channel's flare. */
    public double maxWaterRadius()
    {
        if (delta) return Math.max(flareRadius, deltaWaterRadius(width));
        return com.AdamNew.dynamicwaters.HydrologyManager.normalRiverWaterRadius(drainWidth());
    }

    /** See {@link #delta}. */
    public boolean delta()
    {
        return delta;
    }

    /** See {@link #delta}: one of the river's own arms through its delta, which keep its current. */
    public boolean deltaMain()
    {
        return deltaMain;
    }

    public double flareRadius()
    {
        return flareRadius;
    }

    public double flareLength()
    {
        return flareLength;
    }

    public double tipLength()
    {
        return tipLength;
    }

    /**
     * The width this edge has grown to at its drain, and the widest it is anywhere: the drain
     * edge's width, but never more than a step past its own - a tributary is not drawn out to the
     * width of the river it joins (upstream's funnel at every confluence) - nor less than its own,
     * so a delta's distributary does not thin out ahead of its split.
     */
    public int drainWidth()
    {
        final int down = drainEdge == null ? cachedDrainWidth : drainEdge.width;
        return Mth.clamp(down, width, width + WIDTH_STEP);
    }

    /**
     * @return The interpolated channel width, squared, at a given (block) position - see {@link #widthAt}.
     */
    public double widthSq(double exactX, double exactZ)
    {
        final double realWidth = widthAt(exactX, exactZ);
        return realWidth * realWidth;
    }

    /** Undoes linkToDrain and the widening, for the network to be linked and widened again (RiverNetworkBuilder). */
    void unlink()
    {
        this.drainEdge = null;
        this.sourceEdge = false;
        this.width = MIN_WIDTH;
    }

    /**
     * Links this edge to the provided drain edge via {@code this --> edge}.
     */
    public void linkToDrain(@Nullable RiverGraphEdge edge)
    {
        this.drainEdge = edge;
        if (edge != null)
        {
            edge.sourceEdge = true;
        }
    }
}
