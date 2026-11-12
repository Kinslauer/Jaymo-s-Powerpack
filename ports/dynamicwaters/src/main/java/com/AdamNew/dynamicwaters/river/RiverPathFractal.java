/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.river.MidpointFractal.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 */

package com.AdamNew.dynamicwaters.river;

import com.google.common.base.Preconditions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class RiverPathFractal
{
    private static final double JITTER_MAX = 0.2f;
    private static final double JITTER_MIN = 0.05f;

    private static final int MAX_BISECTIONS = 10;
    private static final double[] ENCOMPASSING_RANGES = new double[MAX_BISECTIONS];

    static
    {
        // All values are to be interpreted as linear functions of the initial inf-norm (n)
        double sqrt2 = (double) Math.sqrt(2);
        double delta = 0, midpointDelta = 0, prevMidpoint, norm = 1;
        for (int i = 0; i < MAX_BISECTIONS; i++)
        {
            ENCOMPASSING_RANGES[i] = Math.max(delta, midpointDelta);

            // Calculate the new midpoint, then the maximum two deltas will contain the midpoint, and max(delta, oldMidpoint)
            prevMidpoint = midpointDelta;
            midpointDelta = 0.5f * (delta + midpointDelta) + sqrt2 * JITTER_MAX * norm;
            delta = Math.max(prevMidpoint, delta);

            // Worst case reduction in the norm
            norm *= 0.5f + JITTER_MAX;
        }
    }

    /**
     * Performs {@code bisections} iterations of the line-bisection fractal algorithm.
     * The segments, both provided and returned, are a sequence of (x0, y0, x1, y1, ... xN, yN)
     */
    private static double[] bisect(RandomSource random, int bisections, double[] segments)
    {
        for (int i = 0; i < bisections; i++)
        {
            double[] splitSegments = new double[(segments.length << 1) - 2];

            // First point
            splitSegments[0] = segments[0];
            splitSegments[1] = segments[1];

            int splitIndex = 2;
            for (int index = 0; index < segments.length - 2; index += 2)
            {
                // Bisect the segment from [source, source + 1]
                // The first source should already be in the array, we need to set the bisection point and the drain

                double sourceX = segments[index];
                double sourceY = segments[index + 1];
                double drainX = segments[index + 2];
                double drainY = segments[index + 3];

                double norm = RiverMathUtils.normInf(sourceX - drainX, sourceY - drainY);

                // Bisect at the midpoint, plus some variance scaled by the inf-norm of the line segment
                double bisectX = randomJitter(random) * norm + (sourceX + drainX) * 0.5f;
                double bisectY = randomJitter(random) * norm + (sourceY + drainY) * 0.5f;

                // Copy the new split segments
                splitSegments[splitIndex] = bisectX;
                splitSegments[splitIndex + 1] = bisectY;
                splitSegments[splitIndex + 2] = drainX;
                splitSegments[splitIndex + 3] = drainY;

                splitIndex += 4;
            }

            segments = splitSegments;
        }
        return segments;
    }

    /** Return a value x in [-JITTER_MAX, JITTER_MAX] s.t. |x| > JITTER_MIN */
    private static double randomJitter(RandomSource random)
    {
        final double value = 2f * random.nextDouble() - 1; // In [0, 1]
        return (JITTER_MAX - JITTER_MIN) * value + (value < 0 ? -JITTER_MIN : JITTER_MIN);
    }

    public final double[] segments;
    private final double norm;
    /** The box around the path's points, so a column far from it is turned away without a look at them. */
    private final double minX, maxX, minY, maxY;

    // Client-side constructor for networking
    public RiverPathFractal(double[] segments, double norm) {
        this.segments = segments;
        this.norm = norm;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (int i = 0; i < segments.length; i += 2) {
            x0 = Math.min(x0, segments[i]);
            x1 = Math.max(x1, segments[i]);
            y0 = Math.min(y0, segments[i + 1]);
            y1 = Math.max(y1, segments[i + 1]);
        }
        this.minX = x0;
        this.maxX = x1;
        this.minY = y0;
        this.maxY = y1;
    }

    /**
     * A path through the given points (x0, y0, x1, y1, ...), source first - a river's shaped
     * course, see RiverShaper. Its norm is how far the points stray from the straight line between
     * its ends.
     */
    public static RiverPathFractal ofPoints(double[] segments) {
        final int n = segments.length;
        double norm = 0.0;
        for (int i = 0; i < n; i += 2) {
            norm = Math.max(norm, Math.sqrt(RiverMathUtils.distancePointToLineSq(segments[0], segments[1],
                    segments[n - 2], segments[n - 1], segments[i], segments[i + 1])));
        }
        return new RiverPathFractal(segments, norm + 1.0);
    }

    public double getNorm() {
        return norm;
    }

    public RiverPathFractal(RandomSource random, int bisections, double sourceX, double sourceY, double drainX, double drainY)
    {
        this(bisect(checkBisections(random, bisections), bisections, new double[] {sourceX, sourceY, drainX, drainY}),
                ENCOMPASSING_RANGES[bisections] * RiverMathUtils.normInf(sourceX - drainX, sourceY - drainY));
    }

    private static RandomSource checkBisections(RandomSource random, int bisections)
    {
        Preconditions.checkArgument(bisections >= 0 && bisections < MAX_BISECTIONS, "Bisections must be within [0, MAX_BISECTIONS)");
        return random;
    }

    /** Square of the distance from (x, y) to the box around the path's points; 0 inside it. */
    public double boxDistanceSq(double x, double y)
    {
        final double dx = x < minX ? minX - x : (x > maxX ? x - maxX : 0.0);
        final double dy = y < minY ? minY - y : (y > maxY ? y - maxY : 0.0);
        return dx * dx + dy * dy;
    }

    /**
     * Checks if a given point (x, y) comes within a minimum {@code distance} of the bounding box of the fractal, using a heuristic to estimate
     * if this is remotely possible. This is an overestimation vs {@link #intersect(double, double, double)}, and is much faster to compute.
     */
    public boolean maybeIntersect(double x, double y, double distance)
    {
        final double d = RiverMathUtils.distancePointToLineSq(segments[0], segments[1], segments[segments.length - 2], segments[segments.length - 1], x, y);
        final double t = distance + norm;
        return d <= t * t;
    }

    /**
     * @return {@code true} the provided point (x, y) comes within a minimum {@code distance} of the fractal.
     */
    public boolean intersect(double x, double y, double distance)
    {
        return maybeIntersect(x, y, distance) && intersectIndex(x, y, distance * distance) != -1;
    }

    /**
     * @return The square distance to the nearest river segment.
     */
    public double intersectDistance(double x, double y)
    {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < segments.length - 2; i += 2)
        {
            double d = RiverMathUtils.distancePointToLineSq(segments[i], segments[i + 1], segments[i + 2], segments[i + 3], x, y);
            if (d < min)
            {
                min = d;
            }
        }
        return min;
    }

    /**
     * {@link #intersectDistance}, and the nearest point of the river itself written to
     * {@code out} as (x, y) - the foot of the perpendicular on the nearest segment.
     */
    public double nearestPoint(double x, double y, double[] out)
    {
        double min = Double.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < segments.length - 2; i += 2)
        {
            double d = RiverMathUtils.distancePointToLineSq(segments[i], segments[i + 1], segments[i + 2], segments[i + 3], x, y);
            if (d < min)
            {
                min = d;
                minIndex = i;
            }
        }
        if (minIndex >= 0)
        {
            final double ax = segments[minIndex], ay = segments[minIndex + 1];
            final double dx = segments[minIndex + 2] - ax, dy = segments[minIndex + 3] - ay;
            final double lenSq = dx * dx + dy * dy;
            final double t = lenSq > 0 ? Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (y - ay) * dy) / lenSq)) : 0.0;
            out[0] = ax + t * dx;
            out[1] = ay + t * dy;
        }
        return min;
    }

    /**
     * {@link #nearestPoint}, and how that point lies along the river: {@code out} is its (x, y), the
     * unit direction of the river there, how far down the river it is and how long the river is,
     * both in blocks.
     */
    public double nearestAlong(double x, double y, double[] out)
    {
        double min = Double.MAX_VALUE, walked = 0.0, along = 0.0;
        for (int i = 0; i < segments.length - 2; i += 2)
        {
            final double ax = segments[i], ay = segments[i + 1];
            final double dx = segments[i + 2] - ax, dy = segments[i + 3] - ay;
            final double len = Math.sqrt(dx * dx + dy * dy);
            final double d = RiverMathUtils.distancePointToLineSq(ax, ay, segments[i + 2], segments[i + 3], x, y);
            if (d < min && len > 0.0)
            {
                min = d;
                final double t = Math.max(0.0, Math.min(1.0, ((x - ax) * dx + (y - ay) * dy) / (len * len)));
                out[0] = ax + t * dx;
                out[1] = ay + t * dy;
                out[2] = dx / len;
                out[3] = dy / len;
                along = walked + t * len;
            }
            walked += len;
        }
        out[4] = along;
        out[5] = walked;
        return min;
    }

    /**
     * @return The best approximation of the flow near a point to this edge. Note that this will not return {@code RiverFlow.NONE}
     * if a suitable flow cannot be found, rather, it needs to be pre-tested that this edge is the nearest edge to the target point.
     */
    public RiverFlow calculateFlow(double x, double y)
    {
        if (segments.length < 4)
        {
            return RiverFlow.NONE;
        }
        double min = Double.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < segments.length - 2; i += 2)
        {
            double d = RiverMathUtils.distancePointToLineSq(segments[i], segments[i + 1], segments[i + 2], segments[i + 3], x, y);
            if (d < min)
            {
                min = d;
                minIndex = i;
            }
        }
        if (minIndex == -1)
        {
            return RiverFlow.NONE;
        }
        final double sourceX = segments[minIndex], sourceY = segments[minIndex + 1], drainX = segments[minIndex + 2], drainY = segments[minIndex + 3];
        final double angle = Mth.atan2(-(drainY - sourceY), drainX - sourceX);
        return RiverFlow.fromAngle(angle);
    }

    public net.minecraft.world.phys.Vec3 calculateContinuousFlow(double x, double y)
    {
        if (segments.length < 4)
        {
            return net.minecraft.world.phys.Vec3.ZERO;
        }
        double min = Double.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < segments.length - 2; i += 2)
        {
            double d = RiverMathUtils.distancePointToLineSq(segments[i], segments[i + 1], segments[i + 2], segments[i + 3], x, y);
            if (d < min)
            {
                min = d;
                minIndex = i;
            }
        }
        if (minIndex == -1)
        {
            return net.minecraft.world.phys.Vec3.ZERO;
        }

        final double sourceX = segments[minIndex], sourceY = segments[minIndex + 1], drainX = segments[minIndex + 2], drainY = segments[minIndex + 3];
        
        // Calculate projection factor t along the nearest segment
        double dx = drainX - sourceX;
        double dy = drainY - sourceY;
        double lenSq = dx * dx + dy * dy;
        double t = 0.5;
        if (lenSq > 0)
        {
            t = ((x - sourceX) * dx + (y - sourceY) * dy) / lenSq;
            t = Math.max(0.0, Math.min(1.0, t));
        }

        double angle;
        // Interpolate at transition areas to smooth the turns
        if (t < 0.25 && minIndex >= 2)
        {
            double prevAngle = Mth.atan2(-(segments[minIndex + 1] - segments[minIndex - 1]), segments[minIndex] - segments[minIndex - 2]);
            double currAngle = Mth.atan2(-dy, dx);
            double wPrev = 0.5 - 2.0 * t;
            double wCurr = 0.5 + 2.0 * t;
            double vx = wPrev * Mth.cos((float) prevAngle) + wCurr * Mth.cos((float) currAngle);
            double vy = wPrev * Mth.sin((float) prevAngle) + wCurr * Mth.sin((float) currAngle);
            angle = Mth.atan2(vy, vx);
        }
        else if (t > 0.75 && minIndex < segments.length - 4)
        {
            double currAngle = Mth.atan2(-dy, dx);
            double nextAngle = Mth.atan2(-(segments[minIndex + 5] - segments[minIndex + 3]), segments[minIndex + 4] - segments[minIndex + 2]);
            double u = t - 0.75;
            double wCurr = 1.0 - 2.0 * u;
            double wNext = 2.0 * u;
            double vx = wCurr * Mth.cos((float) currAngle) + wNext * Mth.cos((float) nextAngle);
            double vy = wCurr * Mth.sin((float) currAngle) + wNext * Mth.sin((float) nextAngle);
            angle = Mth.atan2(vy, vx);
        }
        else
        {
            angle = Mth.atan2(-dy, dx);
        }

        return new net.minecraft.world.phys.Vec3(Mth.cos((float) angle), 0.0, -Mth.sin((float) angle));
    }

    private int intersectIndex(double x, double y, double distSq)
    {
        for (int i = 0; i < segments.length - 2; i += 2)
        {
            double d = RiverMathUtils.distancePointToLineSq(segments[i], segments[i + 1], segments[i + 2], segments[i + 3], x, y);
            if (d < distSq)
            {
                return i;
            }
        }
        return -1;
    }
}
