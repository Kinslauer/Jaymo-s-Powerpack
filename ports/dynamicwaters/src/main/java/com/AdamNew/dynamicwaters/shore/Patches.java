package com.AdamNew.dynamicwaters.shore;

import java.util.Arrays;

public final class Patches {
    private static final double[] QUANTILES = quantiles();

    private Patches() {
    }

    public static double blob(int x, int z, int size, int salt) {
        if (size <= 1) return hash01(x, salt, z);
        final double v = raw(x, z, size, salt);
        int lo = 0, hi = QUANTILES.length - 1;
        if (v <= QUANTILES[lo]) return 0.0;
        if (v >= QUANTILES[hi]) return 0.9999999;
        while (hi - lo > 1) {
            final int mid = (lo + hi) >>> 1;
            if (QUANTILES[mid] <= v) lo = mid;
            else hi = mid;
        }
        final double span = QUANTILES[hi] - QUANTILES[lo];
        final double t = span > 0.0 ? (v - QUANTILES[lo]) / span : 0.0;
        return (lo + t) / (QUANTILES.length - 1);
    }

    public static int spot(int[] weights, int total, int x, int z, int size, int salt) {
        int ground = 0;
        for (int k = 1; k < weights.length; k++) {
            if (weights[k] > weights[ground]) ground = k;
        }
        double left = total;
        for (int k = 0; k < weights.length; k++) {
            if (k == ground) continue;

            final double share = weights[k] / left;
            if (blob(x, z, size, salt + 0x3D * (k + 1)) >= 1.0 - share) return k;
            left -= weights[k];
        }
        return ground;
    }

    public static double smooth(int x, int z, int size, int salt) {
        final int gx = Math.floorDiv(x, size), gz = Math.floorDiv(z, size);
        final double tx = (double) Math.floorMod(x, size) / size, tz = (double) Math.floorMod(z, size) / size;
        final double sx = tx * tx * (3.0 - 2.0 * tx), sz = tz * tz * (3.0 - 2.0 * tz);
        final double a = hash01(gx, salt, gz), b = hash01(gx + 1, salt, gz);
        final double c = hash01(gx, salt, gz + 1), d = hash01(gx + 1, salt, gz + 1);
        final double top = a + (b - a) * sx, bottom = c + (d - c) * sx;
        return top + (bottom - top) * sz;
    }

    public static double hash01(int a, int b, int c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static double raw(int x, int z, int size, int salt) {
        return 0.7 * smooth(x, z, size, salt) + 0.3 * smooth(x, z, Math.max(2, size / 2), salt ^ 0x2C1B);
    }

    private static double[] quantiles() {
        final int n = 1 << 16;
        final double[] s = new double[n];
        for (int i = 0; i < n; i++) {
            final int x = (int) (hash01(i, 11, 3) * 200000.0) - 100000;
            final int z = (int) (hash01(i, 17, 5) * 200000.0) - 100000;
            s[i] = raw(x, z, 4 + (i & 7), 0x5A5A + ((i >>> 3) & 63));
        }
        Arrays.sort(s);
        final double[] q = new double[1025];
        for (int k = 0; k < q.length; k++) q[k] = s[(int) ((long) k * (n - 1) / (q.length - 1))];
        return q;
    }
}
