/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.river.RiverHelpers.
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

import net.minecraft.util.Mth;

public final class RiverMathUtils
{
    /**
     * @return The shortest square euclidean distance between the point (px, py), and the line segment described by (vx, vy) - (wx, wy).
     */
    public static double distancePointToLineSq(double vx, double vy, double wx, double wy, double px, double py)
    {
        double t = projectAlongLine(vx, vy, wx, wy, px, py);
        double x0 = vx + t * (wx - vx);
        double y0 = vy + t * (wy - vy);
        return norm2(x0 - px, y0 - py);
    }

    public static double projectAlongLine(double vx, double vy, double wx, double wy, double px, double py)
    {
        // Return minimum distance between line segment vw and point p, i.e. |w - v|^2
        double l2 = norm2(vx - wx, vy - wy);
        if (l2 == 0)
        {
            return l2;
        }
        // Consider the line extending the segment, parameterized as v + t (w - v).
        // We find projection of point p onto the line.
        // It falls where t = [(p-v) . (w-v)] / |w-v|^2
        // We clamp t from [0,1] to handle points outside the segment vw.
        return Mth.clamp((((px - vx) * (wx - vx)) + ((py - vy) * (wy - vy))) / l2, 0, 1);
    }

    /**
     * @return The euclidean norm of the vector (x, y).
     */
    public static double norm2(double x, double y)
    {
        return x * x + y * y;
    }

    /**
     * @return The infinity norm of the vector (x, y).
     */
    public static double normInf(double x, double y)
    {
        return Math.max(Math.abs(x), Math.abs(y));
    }

    /**
     * Packs a coordinate pair into a long based on their respective grid positions.
     */
    public static long pack(double x, double y)
    {
        return pack(floor(x), floor(y));
    }

    /**
     * Packs a coordinate pair into a long.
     */
    public static long pack(int x, int y)
    {
        return (x & 0xffffffffL) | (((long) y) << 32);
    }

    /**
     * Unpacks the x coordinate from a packed long.
     */
    public static int unpackX(long key)
    {
        return (int) key;
    }

    /**
     * Unpacks the y coordinate from a packed long.
     */
    public static int unpackZ(long key)
    {
        return (int) (key >> 32);
    }

    /**
     * @return The greatest integer x s.t. x < f.
     */
    public static int floor(double f)
    {
        return f >= 0 ? (int) f : (int) f - 1;
    }
}
