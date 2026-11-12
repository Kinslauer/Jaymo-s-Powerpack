package com.AdamNew.dynamicwaters.terrain;

import net.minecraft.util.Mth;

import java.util.Arrays;

public final class WatershedLake {
    public final int waterY;

    public final int minCellX, minCellZ, cellsW, cellsH;

    public final long[] bits;

    public final short[] coarseH;

    public final double minX, maxX, minZ, maxZ;

    public final int dig;

    public WatershedLake(int waterY, int minCellX, int minCellZ, int cellsW, int cellsH, long[] bits, short[] coarseH) {
        this(waterY, minCellX, minCellZ, cellsW, cellsH, bits, coarseH, SHAVE);
    }

    public WatershedLake(int waterY, int minCellX, int minCellZ, int cellsW, int cellsH, long[] bits, short[] coarseH,
            int dig) {
        this.dig = dig;
        this.waterY = waterY;
        this.minCellX = minCellX;
        this.minCellZ = minCellZ;
        this.cellsW = cellsW;
        this.cellsH = cellsH;
        this.bits = bits;
        this.coarseH = coarseH;
        final int c = CoarseHeightField.CELL_SIZE;
        this.minX = (double) (minCellX - 1) * c;
        this.maxX = (double) (minCellX + cellsW + 1) * c;
        this.minZ = (double) (minCellZ - 1) * c;
        this.maxZ = (double) (minCellZ + cellsH + 1) * c;
    }

    public static long[] newBits(int cellsW, int cellsH) {
        return new long[(cellsW * cellsH + 63) >>> 6];
    }

    public static void setBit(long[] bits, int cellsW, int lx, int lz) {
        int i = lz * cellsW + lx;
        bits[i >>> 6] |= 1L << (i & 63);
    }

    private static final double WOBBLE = 8.0;
    private static final double WOBBLE_WAVE = 20.0;
    private static final double SLOPE_SPAN = 4.0;

    private static final double GRADE_RUN = 6.0;
    private static final double GRADE_LEAD = 2.0;
    private static final double GRADE_SPREAD = 10.0;
    private static final double GRADE_WAVE = 16.0;

    private static final double[] GRAD_X = new double[16], GRAD_Z = new double[16];
    static {
        for (int k = 0; k < 16; k++) {
            GRAD_X[k] = Math.cos(k * Math.PI / 8.0);
            GRAD_Z[k] = Math.sin(k * Math.PI / 8.0);
        }
    }

    public static final int FLOOR_SLACK = 4;

    private int floorY = Integer.MIN_VALUE;

    public int floorY() {
        int f = floorY;
        if (f == Integer.MIN_VALUE) {
            f = waterY;
            final int gw = cellsW + 2;
            for (int lz = 0; lz < cellsH; lz++) {
                for (int lx = 0; lx < cellsW; lx++) {
                    if (!bit(lx, lz)) continue;
                    final int h = coarseH[(lz + 1) * gw + (lx + 1)];
                    if (h < f) f = h;
                }
            }
            floorY = f;
        }
        return f;
    }

    public boolean holdsColumn(int columnY) {
        return columnY <= waterY && columnY >= floorY() - FLOOR_SLACK;
    }

    public static final int SHAVE = 3;

    public boolean bedColumn(int columnY) {
        return columnY <= waterY + dig && columnY >= floorY() - FLOOR_SLACK;
    }

    private double shoreHeight(double blockX, double blockZ) {
        final double gx = floorSurface(blockX + SLOPE_SPAN, blockZ) - floorSurface(blockX - SLOPE_SPAN, blockZ);
        final double gz = floorSurface(blockX, blockZ + SLOPE_SPAN) - floorSurface(blockX, blockZ - SLOPE_SPAN);
        final double g = Math.sqrt(gx * gx + gz * gz);
        if (g < 1.0E-6) return floorSurface(blockX, blockZ);
        final double step = WOBBLE * gradientNoise(blockX / WOBBLE_WAVE, blockZ / WOBBLE_WAVE, 0) / g;
        return floorSurface(blockX - step * gx, blockZ - step * gz);
    }

    public double shoreGrade(int x, int z) {
        double lead = GRADE_LEAD + GRADE_SPREAD * gradientNoise(x / GRADE_WAVE, z / GRADE_WAVE, 1);

        if (dig > SHAVE) lead = Math.min(lead, GRADE_LEAD);
        final double t = Mth.clamp((waterDistance(x, z) + lead) / GRADE_RUN, 0.0, 1.0);
        return 1.0 - Math.cos(t * Math.PI / 2.0);
    }

    private double gradientNoise(double x, double z, int salt) {
        final int ix = Mth.floor(x), iz = Mth.floor(z);
        final double fx = x - ix, fz = z - iz;
        final double a = latticeSlope(ix, iz, salt, fx, fz);
        final double b = latticeSlope(ix + 1, iz, salt, fx - 1.0, fz);
        final double c = latticeSlope(ix, iz + 1, salt, fx, fz - 1.0);
        final double d = latticeSlope(ix + 1, iz + 1, salt, fx - 1.0, fz - 1.0);
        final double u = fade(fx), v = fade(fz);
        final double near = a + (b - a) * u, far = c + (d - c) * u;
        return near + (far - near) * v;
    }

    private double latticeSlope(int ix, int iz, int salt, double dx, double dz) {
        long h = (long) minCellX * 0x2545F4914F6CDD1DL ^ (long) minCellZ * 0x5851F42D4C957F2DL
                ^ ix * 0xD1B54A32D192ED03L ^ iz * 0xABC98388FB8FAC03L ^ salt * 0x8CB92BA72F3D8DD7L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        final int k = (int) ((h ^ (h >>> 31)) >>> 60);
        return GRAD_X[k] * dx + GRAD_Z[k] * dz;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private boolean bit(int lx, int lz) {
        if (lx < 0 || lz < 0 || lx >= cellsW || lz >= cellsH) return false;
        int i = lz * cellsW + lx;
        return (bits[i >>> 6] & (1L << (i & 63))) != 0;
    }

    public boolean covers(double blockX, double blockZ) {
        if (blockX < minX || blockX >= maxX || blockZ < minZ || blockZ >= maxZ) return false;
        final int lx = (Mth.floor(blockX) >> CoarseHeightField.CELL_SHIFT) - minCellX;
        final int lz = (Mth.floor(blockZ) >> CoarseHeightField.CELL_SHIFT) - minCellZ;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (bit(lx + dx, lz + dz)) return true;
            }
        }
        return false;
    }

    public boolean near(double blockX, double blockZ, int cells) {
        final double margin = (cells - 1) * (double) CoarseHeightField.CELL_SIZE;
        if (blockX < minX - margin || blockX >= maxX + margin || blockZ < minZ - margin || blockZ >= maxZ + margin) {
            return false;
        }
        final int lx = (Mth.floor(blockX) >> CoarseHeightField.CELL_SHIFT) - minCellX;
        final int lz = (Mth.floor(blockZ) >> CoarseHeightField.CELL_SHIFT) - minCellZ;
        for (int dz = -cells; dz <= cells; dz++) {
            for (int dx = -cells; dx <= cells; dx++) {
                if (bit(lx + dx, lz + dz)) return true;
            }
        }
        return false;
    }

    public boolean touchesInside(double blockX, double blockZ) {
        return inside(blockX + 1.0, blockZ) || inside(blockX - 1.0, blockZ)
                || inside(blockX, blockZ + 1.0) || inside(blockX, blockZ - 1.0);
    }

    public static final double SHORE_BAND = 3.5;

    public boolean shoreBand(double blockX, double blockZ) {
        return covers(blockX, blockZ) && shoreHeight(blockX, blockZ) <= waterY + SHORE_BAND;
    }

    public boolean isLakeCell(int cellX, int cellZ) {
        return bit(cellX - minCellX, cellZ - minCellZ);
    }

    public double floorSurface(double blockX, double blockZ) {
        final int gw = cellsW + 2, gh = cellsH + 2;

        final double u = blockX / CoarseHeightField.CELL_SIZE - (minCellX - 1);
        final double v = blockZ / CoarseHeightField.CELL_SIZE - (minCellZ - 1);
        final int i0 = Mth.clamp(Mth.floor(u), 0, gw - 2);
        final int j0 = Mth.clamp(Mth.floor(v), 0, gh - 2);
        final double fx = Mth.clamp(u - i0, 0.0, 1.0), fz = Mth.clamp(v - j0, 0.0, 1.0);
        final int b = j0 * gw + i0;
        final double top = coarseH[b] * (1 - fx) + coarseH[b + 1] * fx;
        final double bot = coarseH[b + gw] * (1 - fx) + coarseH[b + gw + 1] * fx;
        return top * (1 - fz) + bot * fz;
    }

    public boolean inside(double blockX, double blockZ) {
        if (!covers(blockX, blockZ)) return false;

        return shoreHeight(blockX, blockZ) <= waterY + 0.5;
    }

    public static final int SHORE_REACH = 14;

    private static final int WINDOW_MARGIN = SHORE_REACH + 1;
    private static final int WINDOW = 16 + 2 * WINDOW_MARGIN;

    private static final class ShoreWindow {
        WatershedLake lake;
        int chunkX, chunkZ;
        final boolean[] in = new boolean[WINDOW * WINDOW];

        final float[] dist = new float[16 * 16];
    }

    private static final class ShoreWindows {
        final ShoreWindow[] slots = { new ShoreWindow(), new ShoreWindow(), new ShoreWindow() };
        int next;
    }

    private static final ThreadLocal<ShoreWindows> SHORE_WINDOWS = ThreadLocal.withInitial(ShoreWindows::new);

    private ShoreWindow shoreWindow(int cx, int cz) {
        final ShoreWindows ws = SHORE_WINDOWS.get();
        for (ShoreWindow w : ws.slots) {
            if (w.lake == this && w.chunkX == cx && w.chunkZ == cz) return w;
        }
        final ShoreWindow w = ws.slots[ws.next];
        ws.next = (ws.next + 1) % ws.slots.length;
        final int ox = (cx << 4) - WINDOW_MARGIN, oz = (cz << 4) - WINDOW_MARGIN;
        for (int j = 0; j < WINDOW; j++) {
            for (int i = 0; i < WINDOW; i++) {
                w.in[j * WINDOW + i] = inside(ox + i, oz + j);
            }
        }
        Arrays.fill(w.dist, Float.NaN);
        w.lake = this;
        w.chunkX = cx;
        w.chunkZ = cz;
        return w;
    }

    public double shoreDistance(int x, int z) {
        final ShoreWindow w = shoreWindow(x >> 4, z >> 4);
        final int lx = x & 15, lz = z & 15;
        return w.in[(lz + WINDOW_MARGIN) * WINDOW + lx + WINDOW_MARGIN] ? acrossContour(w, lx, lz) : 0.0;
    }

    public double waterDistance(int x, int z) {
        final ShoreWindow w = shoreWindow(x >> 4, z >> 4);
        final int lx = x & 15, lz = z & 15;
        return w.in[(lz + WINDOW_MARGIN) * WINDOW + lx + WINDOW_MARGIN] ? 0.0 : acrossContour(w, lx, lz);
    }

    private static float acrossContour(ShoreWindow w, int lx, int lz) {
        float d = w.dist[lz * 16 + lx];
        if (Float.isNaN(d)) {
            final int ci = lx + WINDOW_MARGIN, cj = lz + WINDOW_MARGIN;
            final boolean own = w.in[cj * WINDOW + ci];
            int best = Integer.MAX_VALUE;
            for (int dj = -WINDOW_MARGIN; dj <= WINDOW_MARGIN; dj++) {
                final int dj2 = dj * dj;
                if (dj2 >= best) continue;
                final int row = (cj + dj) * WINDOW + ci;
                for (int di = -WINDOW_MARGIN; di <= WINDOW_MARGIN; di++) {
                    final int d2 = di * di + dj2;
                    if (d2 < best && w.in[row + di] != own) best = d2;
                }
            }
            d = best == Integer.MAX_VALUE ? SHORE_REACH : (float) Math.min(SHORE_REACH, Math.sqrt(best) - 0.5);
            w.dist[lz * 16 + lx] = d;
        }
        return d;
    }

    public int cellCount() {
        int n = 0;
        for (long w : bits) n += Long.bitCount(w);
        return n;
    }
}
