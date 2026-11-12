/*
 * Adapted from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * specifically net.dries007.tfc.world.river.Flow.
 * Original work Copyright (c) TerraFirmaCraft team, licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Modified for Dynamic Waters by AdamNew, 2026-07-13.
 * As a Derivative Work under the EUPL, this file remains available under the
 * EUPL v1.2, or a licence listed as compatible in the EUPL's Appendix
 * (e.g. GPLv3, LGPLv3, MPL2), for as long as it carries this notice.
 *
 * NOTE: the constructor below intentionally diverges from upstream TFC. In
 * TFC's original, RiverFlow.NONE.getVector() evaluates ordinal() * (1/8f) * PI
 * with ordinal() == 16, which resolves to a full-strength Vec3(1, 0, 0)
 * instead of zero. TFC never calls getVector() on NONE so it never mattered
 * upstream, but we do (ship/entity push force), so NONE is special-cased to
 * Vec3.ZERO here. Keep this special case if you ever re-sync this file from TFC.
 */

package com.AdamNew.dynamicwaters.river;

import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * The 16 compass directions a river's current can flow in, plus NONE for still water.
 * {@link #fromAngle(double)} quantizes a raw angle (e.g. from {@link RiverPathFractal#calculateFlow}) down to
 * the nearest of these 16, and {@link #getVector()} hands back a ready-to-use unit push vector for physics
 * (boats, items, entities, ship mods via SableCompat).
 */
public enum RiverFlow implements StringRepresentable
{
    // Ordered by unit circle angles
    EEE("e"),
    NEE("nee"),
    N_E("ne"),
    NNE("nne"),
    NNN("n"),
    NNW("nnw"),
    N_W("nw"),
    NWW("nww"),
    WWW("w"),
    SWW("sww"),
    S_W("sw"),
    SSW("ssw"),
    SSS("s"),
    SSE("sse"),
    S_E("se"),
    SEE("see"),
    ___("none");

    public static final RiverFlow NONE = ___;

    private static final RiverFlow[] VALUES = values();
    private static final int MODULUS = VALUES.length - 1; // Since when taking modulo, we want to skip NONE

    public static RiverFlow valueOf(int i)
    {
        return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
    }

    /**
     * @param angle An angle in radians, as returned by {@link Mth#atan2}.
     * @return The nearest of the 16 named directions.
     */
    public static RiverFlow fromAngle(double angle)
    {
        int ordinal = (int) Math.round(8 * (angle / Mth.PI));
        if (ordinal < 0)
        {
            ordinal += MODULUS;
        }
        return VALUES[ordinal];
    }

    public static RiverFlow lerp(RiverFlow flow00, RiverFlow flow01, RiverFlow flow10, RiverFlow flow11, float delta0, float delta1)
    {
        final RiverFlow flow0 = lerp(flow00, flow01, delta0);
        final RiverFlow flow1 = lerp(flow10, flow11, delta0);
        return lerp(flow0, flow1, delta1, lerpWeight(flow00, flow01, delta0), lerpWeight(flow10, flow11, delta0));
    }

    public static RiverFlow lerp(RiverFlow left, RiverFlow right, float delta)
    {
        return lerp(left, right, delta, left == NONE ? 0 : 1, right == NONE ? 0 : 1);
    }

    private static RiverFlow lerp(RiverFlow left, RiverFlow right, float delta, float weightLeft, float weightRight)
    {
        if (left == NONE && right == NONE)
        {
            return NONE;
        }
        else if (left == NONE)
        {
            return Mth.lerp(delta, weightLeft, weightRight) < 0.5f ? left : right;
        }
        else if (right == NONE)
        {
            return Mth.lerp(delta, weightLeft, weightRight) >= 0.5f ? left : right;
        }
        else
        {
            return lerpNonEmpty(left, right, delta);
        }
    }

    private static RiverFlow lerpNonEmpty(RiverFlow left, RiverFlow right, float delta)
    {
        final int ordinalDistance = Math.abs(left.ordinal() - right.ordinal());
        if (ordinalDistance == 8)
        {
            return delta == 0.5f ? NONE : (delta < 0.5f ? left : right);
        }
        else if (ordinalDistance < 8)
        {
            return VALUES[lerp(left.ordinal(), right.ordinal(), delta)];
        }
        else
        {
            int leftValue = left.ordinal(), rightValue = right.ordinal();
            if (leftValue < rightValue)
            {
                leftValue += MODULUS;
            }
            else
            {
                rightValue += MODULUS;
            }
            return VALUES[lerp(leftValue, rightValue, delta) % MODULUS];
        }
    }

    private static int lerp(int left, int right, float delta)
    {
        return Math.round(Mth.lerp(delta, left, right));
    }

    private static float lerpWeight(RiverFlow left, RiverFlow right, float delta)
    {
        if (left == NONE && right == NONE) return 0;
        else if (left == NONE) return delta;
        else if (right == NONE) return 1 - delta;
        else return 1;
    }

    private final String name;
    private final Vec3 vector;

    RiverFlow(String name)
    {
        this.name = name;
        if (name.equals("none"))
        {
            this.vector = Vec3.ZERO;
        }
        else
        {
            float angle = ordinal() * (1 / 8f) * Mth.PI;
            this.vector = new Vec3(Mth.cos(angle), 0, -Mth.sin(angle));
        }
    }

    @Override
    public String getSerializedName()
    {
        return name;
    }

    /**
     * @return A ready-to-use unit force vector for pushing entities/ships along this flow direction.
     */
    public Vec3 getVector()
    {
        return vector;
    }
}
