package com.AdamNew.dynamicwaters.river;

import com.AdamNew.dynamicwaters.HydrologyManager;
import net.minecraft.util.Mth;

public final class RiverShapeNoise {
    private static double distFac(RiverContext info, double x, double z, double scale) {
        return info.normDistSq() * scale + HydrologyManager.getRiverBedNoise(x, z) * 0.15;
    }

    public static double wide(RiverContext info, double x, int y, double z, double originalDensity) {
        final int seaLevel = HydrologyManager.getSeaLevel();
        final double distFac = distFac(info, x, z, 0.8);
        final double riverHeight = (seaLevel - 5) + distFac * 7;

        final double ceiling = Math.min(riverHeight, originalDensity > 0 ? y + 64 : riverHeight);
        return carveAgainst(y, ceiling, originalDensity);
    }

    public static double canyon(RiverContext info, double x, int y, double z, double originalDensity) {
        final int seaLevel = HydrologyManager.getSeaLevel();
        final double distFac = distFac(info, x, z, 1.3);

        final double lowFreqCliff = Mth.clamp(
            HydrologyManager.getRiverBedNoise(x * 0.02, z * 0.02) * 0.5 + 0.5, 0, 1);
        final double adjDistFac = distFac > 0.6 ? distFac * 0.4 + 0.8 : distFac;
        final double riverHeight = (seaLevel - 8) + Mth.lerp(lowFreqCliff, distFac, adjDistFac) * 16;

        return carveAgainst(y, riverHeight, originalDensity);
    }

    public static double floodplain(RiverContext info, double x, int y, double z, double originalDensity) {
        final int seaLevel = HydrologyManager.getSeaLevel();
        final double distFac = distFac(info, x, z, 0.8);
        final double width = Math.sqrt(info.widthSq());
        final double depth = Mth.clampedMap(width, RiverGraphEdge.MIN_WIDTH, RiverGraphEdge.MAX_WIDTH, 3, 6);

        final double riverHeight;
        if (distFac < 1) {
            riverHeight = (seaLevel - 2 - depth) + distFac * depth;
        } else if (distFac < 2.0) {
            riverHeight = seaLevel - 2;
        } else {
            riverHeight = seaLevel - 1.5;
        }

        return carveAgainst(y, riverHeight, originalDensity);
    }

    public static double carve(RiverContext info, double x, int y, double z, double originalDensity) {
        if (info.normDistSq() >= 1.0) return originalDensity;

        final double width = Math.sqrt(info.widthSq());
        if (width > 20.0) {
            return floodplain(info, x, y, z, originalDensity);
        } else if (width > 12.0) {
            return wide(info, x, y, z, originalDensity);
        } else {
            return canyon(info, x, y, z, originalDensity);
        }
    }

    private static double carveAgainst(int y, double ceilingY, double originalDensity) {
        if (y > ceilingY) {
            return -1.0;
        }
        return originalDensity;
    }
}
