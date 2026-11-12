package com.AdamNew.dynamicwaters.terrain;

import net.minecraft.util.Mth;

public final class ChannelForm {
    static final double BEND_SKEW = 0.18;

    static final double BEND_SCOUR = 1.0;

    static final double BAR_SKEW = 0.08;
    static final double BAR_FROM = 0.3;

    static final double SLOPE_WIDTH = 0.15;
    static final double STEEP_SLOPE = 0.12;

    static final double PINCH_WIDTH = 0.09;

    private ChannelForm() {
    }

    public static double bendSkew(double curvature, double halfWidth) {
        final double b = Math.abs(curvature) * halfWidth;
        if (b <= 0.0 || b >= 1.0) return 0.0;
        return -Math.signum(curvature) * BEND_SKEW * Math.sin(Math.PI * b);
    }

    public static double bedDepth(double side, double skew) {
        final double s = Mth.clamp(side, -1.0, 1.0);
        final double u = (s - skew) / (1.0 - skew * s);
        return (1.0 + BEND_SCOUR * Math.abs(skew)) * (1.0 - u * u);
    }

    public static boolean onBar(double side, double skew) {
        return Math.abs(skew) >= BAR_SKEW && side * Math.signum(skew) < -BAR_FROM;
    }

    public static double slopeWidth(double slope) {
        return Mth.clampedLerp(1.0 + SLOPE_WIDTH, 1.0 - SLOPE_WIDTH, slope / STEEP_SLOPE);
    }

    public static double pinchWidth(double pinch) {
        return 1.0 + PINCH_WIDTH * pinch;
    }

    public static double bankRise(double d, double h, double reach, double talus) {
        if (d <= 0.0) return 0.0;
        final double t = Math.min(1.0, d / reach);
        final double rest = Math.max(0.0, h - reach * talus);
        return d * talus + (talus == 1.0 ? rest * t * t * t : rest * Math.pow(t, 2.0 + Math.sqrt(talus)));
    }

    public static double leveeFall(double d, double depth, double reach, double talus) {
        if (d <= 0.0) return 0.0;
        final double t = Math.min(1.0, d / reach);
        return d * talus + Math.max(0.0, depth - reach * talus) * t * t;
    }
}
