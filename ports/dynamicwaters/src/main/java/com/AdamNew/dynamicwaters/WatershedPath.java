package com.AdamNew.dynamicwaters;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class WatershedPath {
    public static final double NODE_SPACING = com.AdamNew.dynamicwaters.terrain.BasinTile.NODE_SPACING;

    public static final double MAX_INFLUENCE = 70.0;

    public static final int SHEER_DROP = 3;
    public static final int CLIFF_DROP = 6;

    public static boolean isFall(int[] ys, int i) {
        final int rise = ys[i + 1] - ys[i];
        return rise >= CLIFF_DROP || (rise >= SHEER_DROP && i > 0 && ys[i] == ys[i - 1]);
    }

    public static final double FALL_FACE_T = 0.8;

    public static final double FALL_BANK_BLOCKS = 4.0;
    public static final double FALL_BANK_MAX = 10.0;

    public static double fallWing(int low, int high) {
        return Mth.clamp(high - low, FALL_BANK_BLOCKS, FALL_BANK_MAX);
    }

    public static int fallRamp(int low, int high, double below) {
        final double s = 1.0 - Mth.clamp(below / fallWing(low, high), 0.0, 1.0);
        return low + (int) Math.round((high - low) * s * s * (3.0 - 2.0 * s));
    }

    public final double[] segmentsX;
    public final double[] segmentsZ;
    public final int[] segmentsY;
    public final int[] segmentsTerrainY;
    public final double[] segmentWidths;
    public boolean hitMainRiver;
    public int nodeCount;
    public int validNodeCount;
    public boolean intersectionsResolved = true;
    public final double minX, maxX, minZ, maxZ;
    private final double queryMargin;

    private final double[] curvature;
    private final double[] slope;

    volatile HydrologyManager.Ravine ravine;

    volatile HydrologyManager.Ravine feeds;

    volatile HydrologyManager.Ravine below;

    volatile byte gorgeEntry;

    volatile byte leftDry;

    volatile byte deltaFed;

    public WatershedPath(int nodeCount, int validNodeCount, boolean hitMainRiver, double[] segmentsX,
            double[] segmentsZ, int[] segmentsY, int[] segmentsTerrainY, double[] segmentWidths) {
        this.nodeCount = nodeCount;
        this.validNodeCount = validNodeCount;
        this.hitMainRiver = hitMainRiver;
        this.segmentsX = segmentsX;
        this.segmentsZ = segmentsZ;
        this.segmentsY = segmentsY;
        this.segmentsTerrainY = segmentsTerrainY;
        this.segmentWidths = segmentWidths;

        double txMin = Double.MAX_VALUE, txMax = -Double.MAX_VALUE;
        double tzMin = Double.MAX_VALUE, tzMax = -Double.MAX_VALUE;
        double maxWidth = 0.0;
        for (int i = 0; i < nodeCount; i++) {
            if (segmentsX[i] < txMin) txMin = segmentsX[i];
            if (segmentsX[i] > txMax) txMax = segmentsX[i];
            if (segmentsZ[i] < tzMin) tzMin = segmentsZ[i];
            if (segmentsZ[i] > tzMax) tzMax = segmentsZ[i];
            if (segmentWidths[i] > maxWidth) maxWidth = segmentWidths[i];
        }
        this.minX = txMin;
        this.maxX = txMax;
        this.minZ = tzMin;
        this.maxZ = tzMax;
        this.queryMargin = maxWidth + MAX_INFLUENCE;

        this.curvature = new double[nodeCount];
        this.slope = new double[nodeCount];
        for (int j = 0; j < nodeCount; j++) {
            final int up = Math.min(nodeCount - 1, j + 2), dn = Math.max(0, j - 2);
            if (up == dn) continue;

            final double ax = segmentsX[j] - segmentsX[up], az = segmentsZ[j] - segmentsZ[up];
            final double bx = segmentsX[dn] - segmentsX[j], bz = segmentsZ[dn] - segmentsZ[j];
            final double la = Math.sqrt(ax * ax + az * az), lb = Math.sqrt(bx * bx + bz * bz);
            if (la > 1.0E-6 && lb > 1.0E-6) {
                curvature[j] = Math.atan2(ax * bz - az * bx, ax * bx + az * bz) / (0.5 * (la + lb));
            }
            if (la + lb > 1.0E-6) {
                slope[j] = Math.max(0, segmentsY[up] - segmentsY[dn]) / (la + lb);
            }
        }
    }

    public double influenceMinX() {
        return minX - queryMargin;
    }

    public double influenceMaxX() {
        return maxX + queryMargin;
    }

    public double influenceMinZ() {
        return minZ - queryMargin;
    }

    public double influenceMaxZ() {
        return maxZ + queryMargin;
    }

    public RiverNodeData getClosestNodeData(double x, double z) {
        if (x < minX - queryMargin || x > maxX + queryMargin || z < minZ - queryMargin || z > maxZ + queryMargin) {
            return new RiverNodeData(x, z, 0, 0, 0, Double.MAX_VALUE, Vec3.ZERO, 0);
        }
        if (validNodeCount <= 0) {
            return new RiverNodeData(x, z, 0, 0, 0, Double.MAX_VALUE, Vec3.ZERO, 0);
        }
        return closest(x, z, 0, validNodeCount - 1);
    }

    public RiverNodeData getClosestNodeDataNear(double x, double z, int node, int span) {
        if (validNodeCount <= 0) {
            return new RiverNodeData(x, z, 0, 0, 0, Double.MAX_VALUE, Vec3.ZERO, 0);
        }
        final int lo = Mth.clamp(node - span, 0, validNodeCount - 1);
        return closest(x, z, lo, Mth.clamp(node + span, lo, validNodeCount - 1));
    }

    private RiverNodeData closest(double x, double z, int lo, int hi) {
        final int searchCount = validNodeCount;

        double minDistSq = Double.MAX_VALUE;
        int bestIdx = lo;
        double bestT = 0.0, bestRawT = 0.0;
        boolean bestIsPoint = false;

        for (int i = lo; i < hi; i++) {
            final double sx = segmentsX[i], sz = segmentsZ[i];
            final double dx = segmentsX[i + 1] - sx, dz = segmentsZ[i + 1] - sz;
            final double len2 = dx * dx + dz * dz;
            double t = (len2 == 0) ? 0 : ((x - sx) * dx + (z - sz) * dz) / len2;
            final double rawT = t;
            if (t < 0) t = 0;
            else if (t > 1) t = 1;
            final double px = x - (sx + t * dx), pz = z - (sz + t * dz);
            final double dSq = px * px + pz * pz;
            if (dSq < minDistSq) {
                minDistSq = dSq;
                bestIdx = i;
                bestT = t;
                bestRawT = rawT;
            }
        }
        {
            final int last = hi;
            final double px = x - segmentsX[last], pz = z - segmentsZ[last];
            final double dSq = px * px + pz * pz;
            if (dSq < minDistSq) {
                minDistSq = dSq;
                bestIdx = last;
                bestIsPoint = true;
            }
        }

        double projY, projTerrainY, projWidth, projCurv, projSlope;

        double sideSign = 1.0;
        if (bestIsPoint || bestIdx >= searchCount - 1) {
            projY = segmentsY[bestIdx];
            projTerrainY = segmentsTerrainY[bestIdx];
            projWidth = segmentWidths[bestIdx];
            projCurv = curvature[bestIdx];
            projSlope = slope[bestIdx];
            if (bestIdx > 0) {
                final double fx = segmentsX[bestIdx - 1] - segmentsX[bestIdx];
                final double fz = segmentsZ[bestIdx - 1] - segmentsZ[bestIdx];
                sideSign = fx * (z - segmentsZ[bestIdx]) - fz * (x - segmentsX[bestIdx]) >= 0.0 ? 1.0 : -1.0;
            }
        } else {
            final int s = bestIdx;
            final double fx = segmentsX[s] - segmentsX[s + 1], fz = segmentsZ[s] - segmentsZ[s + 1];
            final double px = x - (segmentsX[s] - bestT * fx), pz = z - (segmentsZ[s] - bestT * fz);
            sideSign = fx * pz - fz * px >= 0.0 ? 1.0 : -1.0;
            projCurv = curvature[s] + bestT * (curvature[s + 1] - curvature[s]);
            projSlope = slope[s] + bestT * (slope[s + 1] - slope[s]);
            final int i = bestIdx;
            final double t = bestT;
            final int yDiff = segmentsY[i + 1] - segmentsY[i];
            if (isFall(segmentsY, i)) {
                if (t > FALL_FACE_T) {
                    projY = segmentsY[i + 1];
                    projTerrainY = segmentsTerrainY[i + 1];
                } else {
                    projY = segmentsY[i];
                    projTerrainY = segmentsTerrainY[i];
                }
            } else {
                projY = segmentsY[i] + t * yDiff;
                projTerrainY = segmentsTerrainY[i] + t * (segmentsTerrainY[i + 1] - segmentsTerrainY[i]);
            }
            projWidth = segmentWidths[i] + t * (segmentWidths[i + 1] - segmentWidths[i]);
        }

        Vec3 flowDir;
        if (bestIdx > 0) {
            double fdx = segmentsX[bestIdx - 1] - segmentsX[bestIdx];
            double fdz = segmentsZ[bestIdx - 1] - segmentsZ[bestIdx];
            double flen = Math.sqrt(fdx * fdx + fdz * fdz);
            flowDir = flen > 0.01 ? new Vec3(fdx / flen, 0, fdz / flen) : Vec3.ZERO;
        } else if (searchCount > 1) {
            double fdx = segmentsX[0] - segmentsX[1];
            double fdz = segmentsZ[0] - segmentsZ[1];
            double flen = Math.sqrt(fdx * fdx + fdz * fdz);
            flowDir = flen > 0.01 ? new Vec3(fdx / flen, 0, fdz / flen) : Vec3.ZERO;
        } else {
            flowDir = Vec3.ZERO;
        }

        int bankFloor = Integer.MIN_VALUE;
        {
            final boolean onSegment = !bestIsPoint && bestIdx < searchCount - 1;
            final double along = (bestIdx + (onSegment ? bestT : 0.0)) * NODE_SPACING;
            final int span = (int) Math.ceil(FALL_BANK_MAX / NODE_SPACING) + 1;
            for (int f = bestIdx, fEnd = Math.min(searchCount - 2, bestIdx + span); f <= fEnd; f++) {
                if (!isFall(segmentsY, f)) continue;
                final double d = (f + FALL_FACE_T) * NODE_SPACING - along;
                if (d < 0.0) continue;
                if (d <= fallWing(segmentsY[f], segmentsY[f + 1])) bankFloor = fallRamp(segmentsY[f], segmentsY[f + 1], d) + 1;
                break;
            }
        }

        final boolean beyondEnd = lo == 0 && bestIdx == 0 && !bestIsPoint && bestRawT < 0.0
                || hi == validNodeCount - 1 && (bestIsPoint || bestIdx == hi - 1 && bestRawT > 1.0);
        final double dist = Math.sqrt(minDistSq);
        return new RiverNodeData(
                segmentsX[bestIdx], segmentsZ[bestIdx], (int) Math.round(projY), (int) Math.round(projTerrainY),
                projWidth, dist, flowDir, bestIdx, dist * sideSign, projCurv, projSlope, bankFloor, beyondEnd);
    }

    public static class RiverNodeData {
        public final double x, z;
        public final int y;
        public final int terrainY;
        public final double width;
        public final double distance;
        public final Vec3 flowDir;
        public final int nodeIndex;

        public final double lateral;

        public final double curvature;

        public final double slope;

        public final int bankFloor;

        public final boolean beyondEnd;

        public RiverNodeData(double x, double z, int y, int terrainY, double width, double distance, Vec3 flowDir,
                int nodeIndex) {
            this(x, z, y, terrainY, width, distance, flowDir, nodeIndex, distance, 0.0, 0.0, Integer.MIN_VALUE, false);
        }

        public RiverNodeData(double x, double z, int y, int terrainY, double width, double distance, Vec3 flowDir,
                int nodeIndex, double lateral, double curvature, double slope, int bankFloor, boolean beyondEnd) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.terrainY = terrainY;
            this.width = width;
            this.distance = distance;
            this.flowDir = flowDir;
            this.nodeIndex = nodeIndex;
            this.lateral = lateral;
            this.curvature = curvature;
            this.slope = slope;
            this.bankFloor = bankFloor;
            this.beyondEnd = beyondEnd;
        }

        public RiverNodeData atLevel(int y) {
            return new RiverNodeData(x, z, y, terrainY, width, distance, flowDir, nodeIndex, lateral, curvature, slope,
                    bankFloor, beyondEnd);
        }
    }
}
