package com.AdamNew.dynamicwaters.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import com.AdamNew.dynamicwaters.HydrologyManager;

import net.minecraft.util.Mth;

public final class RiverShaper {
    private static final double STEP = 4.0;

    private static final double FINE = 1.5;
    private static final double WAVELENGTH = 6.5;
    private static final double AMPLITUDE = 0.4;
    private static final double CLEARANCE_SHARE = 0.4;
    private static final double CURVATURE_SHARE = 0.6;

    private static final double MAX_SWING = 60.0;

    private static final double JOIN_PULL = 0.9;

    private static final double GRID_SAMPLE = 8.0;
    private static final int GRID_CELL = 32;
    private static final int CLEARANCE_CELLS = 5;

    static final int DELTA_MIN_WIDTH = 11;
    private static final float DELTA_CHANCE = 0.95f;
    private static final double DELTA_REACH = 9.0;
    private static final double DELTA_ABOVE = 4.0;

    public static final double DELTA_FLATS = 55.0;

    private static final double DELTA_ARM_SHARE = 0.75;
    private static final double DELTA_SPLIT_SHARE = 0.72;
    private static final double DELTA_CREEK_SHARE = 0.3;
    private static final int DELTA_CREEK_WIDTH = 3;
    private static final double DELTA_FAN = 1.2;
    private static final double DELTA_FAN_MORE = 0.3;
    private static final double DELTA_SPLIT_EVERY = 56.0;
    private static final double DELTA_SPLIT_TURN = 0.5;
    private static final double DELTA_SPLIT_SPREAD = 0.8;
    private static final int DELTA_LEVELS = 4;
    private static final int DELTA_BIG_MOUTH = 18;
    private static final int DELTA_MIN_ARM = 2;
    private static final double DELTA_CREEK_EVERY = 14.0;
    private static final double DELTA_CREEK_TURN = 0.85;
    private static final double DELTA_CREEK_LENGTH = 22.0;
    private static final double DELTA_CREEK_GROWTH = 14.0;
    private static final int DELTA_CREEK_DEPTH = 4;

    private static final double DELTA_SPREAD_MAX = 1.0;
    private static final double DELTA_COAST_FAR = 2.0;
    private static final double DELTA_COAST_SLACK = 16.0;

    private static final double[] DELTA_EASE = { 1.0, 0.75, 0.5, 0.25, 0.0 };
    private static final double DELTA_OUT = 24.0;
    private static final double DELTA_GAP = 4.0;
    private static final double DELTA_SWING = 0.85;
    private static final double DELTA_CREEK_SWING = 1.3;
    private static final double DELTA_WAVE_WIDTHS = 5.5;
    private static final double DELTA_WAVE_MIN = 16.0;

    private static final double DELTA_MAX_ARM = 480.0;
    private static final int DELTA_MAX_CHANNELS = 300;
    private static final double DELTA_STEP = 2.5;

    private static final double DELTA_TAPER = 1.5;

    private static final double DELTA_RIVER_GAP = 8.0;

    private record Sprout(double x, double z, double heading, int width, double parentRadius, int level, boolean arm,
            double bearing, int parent) {
    }

    private record Channel(double[] pts, int width, double flareRadius, double flareLength, double tipLength,
            boolean main) {
    }

    private static final class Occupied {
        private static final int DELTA_CELL = 16;
        private final Map<Long, List<double[]>> cells = new HashMap<>();

        void add(double x, double z, double r, int id) {
            cells.computeIfAbsent(key(Math.floorDiv((long) Math.floor(x), DELTA_CELL), Math.floorDiv((long) Math.floor(z), DELTA_CELL)),
                    k -> new ArrayList<>()).add(new double[] { x, z, r, id });
        }

        int hit(double x, double z, double r, double gap, int self, int skip, double sx, double sz, double clear,
                double[] at) {
            final long cx = Math.floorDiv((long) Math.floor(x), DELTA_CELL), cz = Math.floorDiv((long) Math.floor(z), DELTA_CELL);
            for (long i = cx - 2; i <= cx + 2; i++) {
                for (long j = cz - 2; j <= cz + 2; j++) {
                    final List<double[]> list = cells.get(key(i, j));
                    if (list == null) continue;
                    for (double[] p : list) {
                        final int id = (int) p[3];
                        if (id == self || id == skip) continue;
                        if (clear > 0.0 && (p[0] - sx) * (p[0] - sx) + (p[1] - sz) * (p[1] - sz) < clear * clear) continue;
                        final double reach = r + p[2] + gap;
                        final double dx = p[0] - x, dz = p[1] - z;
                        if (dx * dx + dz * dz < reach * reach) {
                            at[0] = p[0];
                            at[1] = p[1];
                            return id;
                        }
                    }
                }
            }
            return NONE;
        }

        private static long key(long i, long j) {
            return (i << 32) ^ (j & 0xFFFFFFFFL);
        }
    }

    private static final class Rivers {
        private static final int CELL = 32;

        static final int BESIDE = -2;
        private final Map<Long, List<double[]>> cells = new HashMap<>();

        private final Map<Integer, Integer> cutBelow = new HashMap<>();
        private double widest;

        private double[] mouths = new double[0];

        void add(double x, double z, double r, int river, int chain, int at) {
            cells.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(new double[] { x, z, r, river, chain, at });
            widest = Math.max(widest, r);
        }

        void addCourse(double[] pts, double r, int river) {
            for (int i = 0; i + 3 < pts.length; i += 2) {
                final double dx = pts[i + 2] - pts[i], dz = pts[i + 3] - pts[i + 1];
                final int n = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / 3.0));
                for (int k = 0; k < n; k++) add(pts[i] + dx * k / n, pts[i + 1] + dz * k / n, r, river, -1, -1);
            }
            if (pts.length >= 2) add(pts[pts.length - 2], pts[pts.length - 1], r, river, -1, -1);
        }

        void cut(int chain, int at) {
            cutBelow.put(chain, at);
        }

        boolean theirs(double x, double z, double r, double mx, double mz) {
            final double own = Math.hypot(x - mx, z - mz) + DELTA_RIVER_GAP + 2.0 * r;
            for (int i = 0; i < mouths.length; i += 2) {
                if (Math.hypot(x - mouths[i], z - mouths[i + 1]) < own) return true;
            }
            return false;
        }

        boolean hit(double x, double z, double r, int own) {
            final int span = (int) Math.ceil((r + widest + DELTA_RIVER_GAP) / CELL);
            final long cx = Math.floorDiv((long) Math.floor(x), CELL), cz = Math.floorDiv((long) Math.floor(z), CELL);
            for (long i = cx - span; i <= cx + span; i++) {
                for (long j = cz - span; j <= cz + span; j++) {
                    final List<double[]> list = cells.get((i << 32) ^ (j & 0xFFFFFFFFL));
                    if (list == null) continue;
                    for (double[] p : list) {
                        if ((int) p[3] == own) continue;
                        final Integer cut = cutBelow.get((int) p[4]);
                        if (cut != null && p[5] >= 0 && p[5] < cut) continue;
                        final double reach = r + p[2] + DELTA_RIVER_GAP, dx = p[0] - x, dz = p[1] - z;
                        if (dx * dx + dz * dz < reach * reach) return true;
                    }
                }
            }
            return false;
        }

        private static long key(double x, double z) {
            final long i = Math.floorDiv((long) Math.floor(x), CELL), j = Math.floorDiv((long) Math.floor(z), CELL);
            return (i << 32) ^ (j & 0xFFFFFFFFL);
        }
    }

    private static double besideRadius(int width) {
        final double r = HydrologyManager.normalRiverWaterRadius(width);
        return r + Math.min(MAX_SWING, AMPLITUDE * WAVELENGTH * 2.0 * r);
    }

    private static final int NONE = Integer.MIN_VALUE, TRUNK = -1;

    private RiverShaper() {
    }

    private static final class Chain {
        final List<RiverGraphEdge> edges = new ArrayList<>();
        final Chain parent;

        final int parentVertex;
        int id;

        double[] qx, qz, qr;
        int[] vertexAt;

        Chain(Chain parent, int parentVertex) {
            this.parent = parent;
            this.parentVertex = parentVertex;
        }

        Chain root() {
            Chain c = this;
            while (c.parent != null) c = c.parent;
            return c;
        }
    }

    public interface Land {
        boolean isLand(double x, double z);

        default double height(double x, double z) {
            return Double.NaN;
        }
    }

    private static boolean flat(Land land, double x, double z) {
        return !(land.height(x, z) > DELTA_FLATS);
    }

    public static List<RiverGraphEdge> shape(List<RiverGraphEdge> edges, long seed, Land land, double[][] beside,
            int[] besideWidths, double[] besideMouths) {
        if (edges.isEmpty()) return edges;
        final Map<RiverGraphEdge, List<RiverGraphEdge>> upstream = new IdentityHashMap<>();
        for (RiverGraphEdge e : edges) upstream.put(e, new ArrayList<>());
        for (RiverGraphEdge e : edges) {
            final RiverGraphEdge d = e.drainEdge();
            if (d != null && upstream.containsKey(d)) upstream.get(d).add(e);
        }
        final Map<RiverGraphEdge, Integer> size = new IdentityHashMap<>();
        for (RiverGraphEdge e : edges) upstreamSize(e, upstream, size);

        final List<Chain> chains = new ArrayList<>();
        final Map<RiverGraphEdge, Chain> chainOf = new IdentityHashMap<>();
        final ArrayDeque<Chain> todo = new ArrayDeque<>();
        final ArrayDeque<RiverGraphEdge> starts = new ArrayDeque<>();
        for (RiverGraphEdge e : edges) {
            final RiverGraphEdge d = e.drainEdge();
            if (d == null || !upstream.containsKey(d)) {
                todo.add(new Chain(null, -1));
                starts.add(e);
            }
        }
        while (!todo.isEmpty()) {
            final Chain c = todo.poll();
            RiverGraphEdge e = starts.poll();
            c.id = chains.size();
            chains.add(c);
            while (e != null) {
                c.edges.add(e);
                chainOf.put(e, c);
                RiverGraphEdge main = null;
                for (RiverGraphEdge u : upstream.get(e)) {
                    if (main == null || u.width > main.width
                            || (u.width == main.width && size.get(u) > size.get(main))) main = u;
                }
                for (RiverGraphEdge u : upstream.get(e)) {
                    if (u == main) continue;
                    todo.add(new Chain(c, c.edges.size()));
                    starts.add(u);
                }
                e = main;
            }
        }

        final Map<Long, List<double[]>> grid = new HashMap<>();
        for (RiverGraphEdge e : edges) {
            final double sx = e.source().x(), sz = e.source().y(), dx = e.drain().x() - sx, dz = e.drain().y() - sz;
            final int n = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / GRID_SAMPLE));
            final double id = chainOf.get(e).id;
            for (int k = 0; k <= n; k++) {
                final double x = sx + dx * k / n, z = sz + dz * k / n;
                grid.computeIfAbsent(cell(x, z), q -> new ArrayList<>()).add(new double[] { x, z, id });
            }
        }

        for (Chain c : chains) shapeChain(c, grid, seed);

        final Map<Chain, boolean[]> joined = new IdentityHashMap<>();
        for (Chain c : chains) joined.put(c, new boolean[c.edges.size() + 1]);
        for (Chain c : chains) {
            if (c.parent != null) joined.get(c.parent)[c.parentVertex] = true;
        }

        final Rivers rivers = new Rivers();
        for (Chain c : chains) {
            final int river = c.root().id;
            for (int k = 0; k < c.qx.length; k += 2) rivers.add(c.qx[k], c.qz[k], c.qr[k], river, c.id, k);
        }
        for (int i = 0; i < beside.length; i++) rivers.addCourse(beside[i], besideRadius(besideWidths[i]), Rivers.BESIDE);
        rivers.mouths = besideMouths;

        final List<RiverGraphEdge> out = new ArrayList<>(edges);
        for (Chain c : chains) {
            if (c.parent != null) continue;
            final RiverGraphEdge mouth = c.edges.get(0);
            if (mouth.drainEdge() != null || mouth.width < DELTA_MIN_WIDTH) continue;
            final long h = mix(seed ^ 0x4F1BBCDCBFA53E0BL, Math.round(mouth.drain().x()), Math.round(mouth.drain().y()));
            if (unit(h) >= DELTA_CHANCE) continue;
            delta(c, out, h, land, joined.get(c), rivers);
        }
        return out;
    }

    private static int upstreamSize(RiverGraphEdge e, Map<RiverGraphEdge, List<RiverGraphEdge>> upstream,
            Map<RiverGraphEdge, Integer> size) {
        final Integer known = size.get(e);
        if (known != null) return known;
        int s = 1;
        for (RiverGraphEdge u : upstream.get(e)) s += upstreamSize(u, upstream, size);
        size.put(e, s);
        return s;
    }

    private static void shapeChain(Chain c, Map<Long, List<double[]>> grid, long seed) {
        final int nv = c.edges.size() + 1;
        final double[] px = new double[nv], pz = new double[nv];
        px[0] = c.edges.get(0).drain().x();
        pz[0] = c.edges.get(0).drain().y();
        for (int i = 0; i < c.edges.size(); i++) {
            px[i + 1] = c.edges.get(i).source().x();
            pz[i + 1] = c.edges.get(i).source().y();
        }

        double bx = 2 * px[0] - px[1], bz = 2 * pz[0] - pz[1];
        if (c.parent != null) {
            final Chain p = c.parent;
            final int at = p.vertexAt[c.parentVertex];
            px[0] = p.qx[at];
            pz[0] = p.qz[at];
            final int down = Math.max(0, at - 6);
            double fx = p.qx[down] - p.qx[at], fz = p.qz[down] - p.qz[at];
            final double fl = Math.hypot(fx, fz);
            if (fl > 1.0E-6) {
                final double len = Math.hypot(px[1] - px[0], pz[1] - pz[0]) * JOIN_PULL;
                bx = px[0] + fx / fl * len;
                bz = pz[0] + fz / fl * len;
            }
        }
        final double ax = 2 * px[nv - 1] - px[nv - 2], az = 2 * pz[nv - 1] - pz[nv - 2];

        final List<double[]> pts = new ArrayList<>();
        final int[] vertexAt = new int[nv];
        final double[] q = new double[2];
        for (int i = 0; i < nv - 1; i++) {
            final double x0 = i == 0 ? bx : px[i - 1], z0 = i == 0 ? bz : pz[i - 1];
            final double x3 = i + 2 < nv ? px[i + 2] : ax, z3 = i + 2 < nv ? pz[i + 2] : az;
            final int n = Math.max(2, (int) Math.ceil(Math.hypot(px[i + 1] - px[i], pz[i + 1] - pz[i]) / FINE));
            vertexAt[i] = pts.size();
            for (int j = 0; j < n; j++) {
                catmullRom(x0, z0, px[i], pz[i], px[i + 1], pz[i + 1], x3, z3, (double) j / n, q);
                pts.add(new double[] { q[0], q[1] });
            }
        }
        vertexAt[nv - 1] = pts.size();
        pts.add(new double[] { px[nv - 1], pz[nv - 1] });
        final int m = pts.size();

        final double[] s = new double[m], nx = new double[m], nz = new double[m], full = new double[m];
        for (int k = 1; k < m; k++) {
            s[k] = s[k - 1] + Math.hypot(pts.get(k)[0] - pts.get(k - 1)[0], pts.get(k)[1] - pts.get(k - 1)[1]);
        }
        for (int k = 0; k < m; k++) {
            final double[] a = pts.get(Math.max(0, k - 1)), b = pts.get(Math.min(m - 1, k + 1));
            final double tx = b[0] - a[0], tz = b[1] - a[1], tl = Math.max(1.0E-9, Math.hypot(tx, tz));
            nx[k] = -tz / tl;
            nz[k] = tx / tl;
        }
        for (int i = 0; i < nv - 1; i++) {
            final RiverGraphEdge e = c.edges.get(i);
            final int downWidth = i == 0 ? e.width : c.edges.get(i - 1).width;
            for (int k = vertexAt[i]; k <= vertexAt[i + 1]; k++) {
                final double t = (double) (k - vertexAt[i]) / Math.max(1, vertexAt[i + 1] - vertexAt[i]);
                full[k] = 2.0 * HydrologyManager.normalRiverWaterRadius(Mth.lerp(t, downWidth, e.width));
            }
        }
        final double total = s[m - 1];

        final double[] room = new double[m];
        final int every = Math.max(1, (int) Math.round(GRID_SAMPLE / FINE));
        for (int k = 0; k < m; k += every) {
            final double[] p = pts.get(k);
            final double clear = clearance(grid, p[0], p[1], c.id);
            final double bend = bendRadius(pts, k, (int) Math.round(10.0 / FINE));
            final double r = Math.min(CLEARANCE_SHARE * Math.max(0.0, clear - full[k] * 0.5 - 6.0), CURVATURE_SHARE * bend);
            for (int j = k; j < Math.min(m, k + every); j++) room[j] = r;
        }

        final double[] eased = erodeAndEase(room, (int) Math.round(24.0 / FINE));

        final long h = mix(seed, Math.round(px[nv - 1]), Math.round(pz[nv - 1]));
        double phase1 = unit(h) * Math.PI * 2.0, phase2 = unit(mix(h, 1, 2)) * Math.PI * 2.0;
        final double phase3 = unit(mix(h, 3, 4)) * Math.PI * 2.0;
        final double[] qx = new double[m], qz = new double[m];
        for (int k = 0; k < m; k++) {
            final double lambda = WAVELENGTH * full[k];
            if (k > 0) {
                final double ds = s[k] - s[k - 1];
                phase1 += 2.0 * Math.PI * ds / lambda;
                phase2 += 2.0 * Math.PI * ds / (0.47 * lambda);
            }
            final double strength = 0.65 + 0.35 * Math.sin(2.0 * Math.PI * s[k] / (4.3 * lambda) + phase3);
            final double taper = smooth(s[k] / (0.6 * lambda)) * smooth((total - s[k]) / (0.6 * lambda));
            final double amplitude = Math.min(Math.min(AMPLITUDE * lambda * strength, eased[k]), MAX_SWING) * taper;
            final double d = amplitude * (0.72 * Math.sin(phase1) + 0.28 * Math.sin(phase2));
            qx[k] = pts.get(k)[0] + nx[k] * d;
            qz[k] = pts.get(k)[1] + nz[k] * d;
        }
        c.qx = qx;
        c.qz = qz;
        c.qr = new double[m];
        for (int k = 0; k < m; k++) c.qr[k] = 0.5 * full[k];
        c.vertexAt = vertexAt;

        final int keep = Math.max(1, (int) Math.round(STEP / FINE));
        for (int i = 0; i < nv - 1; i++) {
            final int from = vertexAt[i], to = vertexAt[i + 1];
            final List<double[]> part = new ArrayList<>();
            for (int k = to; k > from; k -= keep) part.add(new double[] { qx[k], qz[k] });
            part.add(new double[] { qx[from], qz[from] });
            final double[] points = new double[part.size() * 2];
            for (int k = 0; k < part.size(); k++) {
                points[2 * k] = part.get(k)[0];
                points[2 * k + 1] = part.get(k)[1];
            }
            c.edges.get(i).reshape(points);
        }
    }

    private static void delta(Chain c, List<RiverGraphEdge> out, long h, Land land, boolean[] joined, Rivers rivers) {
        final RiverGraphEdge mouth = c.edges.get(0);
        final int nv = c.edges.size() + 1;

        int free = 1;
        while (free < nv - 1 && !joined[free]) free++;
        final double[] qx = c.qx, qz = c.qz;
        final int top = c.vertexAt[free], head = c.vertexAt[nv - 1];
        if (top < 8) return;
        final double[] s = new double[head + 1];
        for (int k = 1; k <= head; k++) s[k] = s[k - 1] + Math.hypot(qx[k] - qx[k - 1], qz[k] - qz[k - 1]);
        final double full = 2.0 * HydrologyManager.normalRiverWaterRadius(mouth.width);

        int coastAt = 0;
        while (coastAt < top && !land.isLand(qx[coastAt], qz[coastAt])) coastAt++;
        final double highest = Math.min(0.85 * s[top], s[head] - DELTA_ABOVE * full);
        if (highest < 90.0) return;

        final int inland = com.AdamNew.dynamicwaters.Config.DELTA_INLAND_REACH.get();
        if (inland <= 0) return;
        final double back = Mth.clamp(s[coastAt] + Math.min(DELTA_REACH * full, Math.max(90.0, inland)), 90.0, highest);
        int a = 0;
        while (a < top && s[a] < back) a++;

        while (a > coastAt && !flat(land, qx[a], qz[a])) a--;
        if (s[a] - s[coastAt] < full) return;
        if (a < 4 || a >= top) return;
        final double apexX = qx[a], apexZ = qz[a];

        double fx = qx[a - 4] - apexX, fz = qz[a - 4] - apexZ;
        double sx = qx[0] - apexX, sz = qz[0] - apexZ;
        final double fl = Math.hypot(fx, fz), sl = Math.hypot(sx, sz);
        if (fl < 1.0E-6 || sl < 1.0E-6) return;
        fx /= fl;
        fz /= fl;
        sx /= sl;
        sz /= sl;

        int k = 0;
        while (k + 1 < free && c.vertexAt[k + 1] <= a) k++;
        final RiverGraphEdge held = c.edges.get(k);

        final SplittableRandom rnd = new SplittableRandom(h);
        final int count = 2;
        final double fan = DELTA_FAN + DELTA_FAN_MORE * rnd.nextDouble();
        final double skew = 0.1 * (rnd.nextDouble() - 0.5);
        final double sea = Math.atan2(sz, sx), current = Math.atan2(fz, fx);
        final double straight = coast(apexX, apexZ, sx, sz, land);
        if (Double.isNaN(straight)) return;
        final int levels = mouth.width >= DELTA_BIG_MOUTH ? DELTA_LEVELS : DELTA_LEVELS - 1;
        final double trunk = HydrologyManager.normalRiverWaterRadius(held.width);
        final int width = Math.max(DELTA_MIN_ARM + 1, deltaWidthFor(trunk * DELTA_ARM_SHARE));

        final Occupied occupied = new Occupied();
        for (int j = a; j <= top && s[j] - s[a] <= 6.0 * trunk; j++) occupied.add(qx[j], qz[j], trunk, TRUNK);
        final ArrayDeque<Sprout> todo = new ArrayDeque<>();
        final List<Channel> channels = new ArrayList<>();
        int arms = 0;
        for (int b = 0; b < count; b++) {
            final double middle = skew - fan + fan * (2 * b + 1) / count;
            for (double ease : DELTA_EASE) {
                final double bearing = sea + middle * ease;
                final double far = coast(apexX, apexZ, Math.cos(bearing), Math.sin(bearing), land);
                if (Double.isNaN(far) || far > DELTA_COAST_FAR * straight + DELTA_COAST_SLACK) continue;

                final double toward = Math.atan2(Math.sin(bearing - current), Math.cos(bearing - current));
                final Channel ch = grow(new Sprout(apexX, apexZ, current + 0.6 * toward, width, trunk, 0, true, bearing, TRUNK),
                        channels.size(), levels, sea, occupied, rivers, c.id, mouth, land, rnd, todo);
                if (ch == null) continue;
                channels.add(ch);
                arms++;
                break;
            }
        }
        if (arms < 2) return;
        while (!todo.isEmpty() && channels.size() < DELTA_MAX_CHANNELS) {
            final Channel ch = grow(todo.poll(), channels.size(), levels, sea, occupied, rivers, c.id, mouth, land, rnd, todo);
            if (ch != null) channels.add(ch);
        }

        final int keep = Math.max(1, (int) Math.round(STEP / FINE));
        final List<double[]> part = new ArrayList<>();
        for (int j = c.vertexAt[k + 1]; j > a; j -= keep) part.add(new double[] { qx[j], qz[j] });
        part.add(new double[] { apexX, apexZ });
        if (part.size() < 2) return;
        final double[] points = new double[part.size() * 2];
        for (int j = 0; j < part.size(); j++) {
            points[2 * j] = part.get(j)[0];
            points[2 * j + 1] = part.get(j)[1];
        }
        held.reshape(points);
        held.linkToDrain(null);
        final java.util.Set<RiverGraphEdge> gone = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int j = 0; j < k; j++) gone.add(c.edges.get(j));
        out.removeIf(gone::contains);
        for (Channel ch : channels) out.add(edgeOf(ch, mouth.source().distance()));

        rivers.cut(c.id, a);
        for (Channel ch : channels) rivers.addCourse(ch.pts(), RiverGraphEdge.deltaWaterRadius(ch.width()), c.id);
    }

    private static Channel grow(Sprout sp, int id, int levels, double sea, Occupied occupied, Rivers rivers, int river,
            RiverGraphEdge mouth, Land land, SplittableRandom rnd, ArrayDeque<Sprout> todo) {
        final double r = RiverGraphEdge.deltaWaterRadius(sp.width());
        final double lambda = Math.max(DELTA_WAVE_MIN, DELTA_WAVE_WIDTHS * 2.0 * r) * (0.8 + 0.4 * rnd.nextDouble());
        final double swing = (sp.arm() ? DELTA_SWING : DELTA_CREEK_SWING) * (0.75 + 0.5 * rnd.nextDouble());
        final double phase = rnd.nextDouble() * Math.PI * 2.0;
        final double length = sp.arm() ? DELTA_MAX_ARM
                : (DELTA_CREEK_LENGTH + DELTA_CREEK_GROWTH * sp.width()) * (0.6 + 0.8 * rnd.nextDouble());

        final double free = 1.6 * (sp.parentRadius() + r + DELTA_GAP) + DELTA_STEP;
        final List<Sprout> sent = new ArrayList<>();
        final double[] at = new double[2];
        double[] pts = new double[64];
        int n = 0;
        pts[n++] = sp.x();
        pts[n++] = sp.z();
        double x = sp.x(), z = sp.z(), heading = sp.heading(), s = 0.0, out = -1.0;
        double nextArm = DELTA_SPLIT_EVERY * (0.6 + 0.8 * rnd.nextDouble());
        double nextCreek = free + DELTA_CREEK_EVERY * (0.3 + 0.7 * rnd.nextDouble());
        int side = rnd.nextBoolean() ? 1 : -1;
        boolean blind = true;
        int joined = NONE;
        while (s < length) {
            if (sp.arm()) {
                final double off = sp.bearing() - heading;
                heading += 0.06 * Math.atan2(Math.sin(off), Math.cos(off));
            }

            final double h = heading + swing * smooth(s / (0.5 * lambda)) * Math.sin(2.0 * Math.PI * s / lambda + phase);
            final double nx = x + Math.cos(h) * DELTA_STEP, nz = z + Math.sin(h) * DELTA_STEP;
            if (out < 0.0 && !land.isLand(nx, nz)) {
                blind = false;
                if (!sp.arm()) break;
                out = 0.0;
            }
            if (out >= 0.0) {
                out += DELTA_STEP;
                if (out > DELTA_OUT) break;
            } else {
                if (!flat(land, nx, nz) || rivers.hit(nx, nz, r, river)
                        || rivers.theirs(nx, nz, r, mouth.drain().x(), mouth.drain().y())) {
                    break;
                }
                final boolean leaving = s < free;
                final int other = occupied.hit(nx, nz, r, DELTA_GAP, id, leaving ? sp.parent() : NONE, sp.x(), sp.z(),
                        leaving ? free : 0.0, at);
                if (other != NONE) {
                    if (sp.arm()) {
                        pts = add(pts, n, at[0], at[1]);
                        n += 2;
                        blind = false;
                        joined = other;
                    }
                    break;
                }
            }
            x = nx;
            z = nz;
            s += DELTA_STEP;
            pts = add(pts, n, x, z);
            n += 2;
            if (out >= 0.0) continue;
            if (sp.arm() && sp.level() < levels && sp.width() > DELTA_MIN_ARM && s >= nextArm) {
                final double turn = side * DELTA_SPLIT_TURN * (0.75 + 0.5 * rnd.nextDouble());
                final int narrower = Math.max(DELTA_MIN_ARM, (int) Math.round(sp.width() * DELTA_SPLIT_SHARE));
                sent.add(new Sprout(x, z, h + turn, narrower, r, sp.level() + 1, true,
                        seaward(x, z, sp.bearing() + DELTA_SPLIT_SPREAD * turn, sea, land), id));
                side = -side;
                nextArm = s + DELTA_SPLIT_EVERY * (0.7 + 0.6 * rnd.nextDouble());
            }
            if (s >= nextCreek) {
                if (sp.level() < DELTA_CREEK_DEPTH) {
                    final int w = sp.arm() ? Math.min(DELTA_CREEK_WIDTH, (int) Math.round(sp.width() * DELTA_CREEK_SHARE))
                            : Math.max(0, sp.width() - 1);
                    sent.add(new Sprout(x, z, h + side * DELTA_CREEK_TURN * (0.8 + 0.6 * rnd.nextDouble()), w, r,
                            sp.level() + 1, false, Double.NaN, id));
                    if (rnd.nextDouble() < 0.75) side = -side;
                }
                nextCreek = s + DELTA_CREEK_EVERY * (sp.arm() ? 1.0 : 0.75) * (0.6 + 0.8 * rnd.nextDouble());
            }
        }
        if (n < 8) return null;

        if (sp.arm() && sp.level() == 0 && out < 0.0 && (joined == NONE || joined == TRUNK)) return null;
        final double[] course = Arrays.copyOf(pts, n);
        for (int k = 0; k < n; k += 2) occupied.add(course[k], course[k + 1], r, id);
        todo.addAll(sent);
        return new Channel(course, sp.width(), sp.arm() ? sp.parentRadius() : 0.0,
                sp.arm() ? DELTA_TAPER * sp.parentRadius() : 0.0, blind ? Math.min(0.45 * s, 6.0 + 3.0 * sp.width()) : 0.0,
                sp.arm() && sp.level() == 0);
    }

    private static int deltaWidthFor(double radius) {
        return Math.max(0, (int) Math.round(radius - RiverGraphEdge.deltaWaterRadius(0)));
    }

    private static double[] add(double[] a, int n, double x, double z) {
        final double[] b = n + 2 > a.length ? Arrays.copyOf(a, a.length * 2) : a;
        b[n] = x;
        b[n + 1] = z;
        return b;
    }

    private static double seaward(double x, double z, double bearing, double sea, Land land) {
        final double off = Mth.clamp(Math.atan2(Math.sin(bearing - sea), Math.cos(bearing - sea)),
                -DELTA_SPREAD_MAX, DELTA_SPREAD_MAX);
        final double straight = coast(x, z, Math.cos(sea), Math.sin(sea), land);
        if (Double.isNaN(straight)) return sea + off;
        for (double ease : DELTA_EASE) {
            final double b = sea + off * ease;
            final double far = coast(x, z, Math.cos(b), Math.sin(b), land);
            if (!Double.isNaN(far) && far <= DELTA_COAST_FAR * straight + DELTA_COAST_SLACK) return b;
        }
        return sea;
    }

    private static double coast(double x, double z, double bx, double bz, Land land) {
        for (double d = 8.0; d <= DELTA_MAX_ARM; d += 8.0) {
            if (!land.isLand(x + bx * d, z + bz * d)) return d;
        }
        return Double.NaN;
    }

    private static RiverGraphEdge edgeOf(Channel ch, int distance) {
        final double[] p = ch.pts();
        final int n = p.length / 2;
        final RiverInstance.Vertex from = new RiverInstance.Vertex(p[0], p[1], Math.atan2(p[3] - p[1], p[2] - p[0]), 0.0,
                distance);
        final RiverInstance.Vertex to = new RiverInstance.Vertex(p[2 * n - 2], p[2 * n - 1],
                Math.atan2(p[2 * n - 1] - p[2 * n - 3], p[2 * n - 2] - p[2 * n - 4]), 0.0, distance);
        return new RiverGraphEdge(from, to, p, ch.width(), ch.flareRadius(), ch.flareLength(), ch.tipLength(), ch.main());
    }

    private static long cell(double x, double z) {
        final long cx = Math.floorDiv((long) Math.floor(x), GRID_CELL), cz = Math.floorDiv((long) Math.floor(z), GRID_CELL);
        return (cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static double clearance(Map<Long, List<double[]>> grid, double x, double z, int own) {
        final long cx = Math.floorDiv((long) Math.floor(x), GRID_CELL), cz = Math.floorDiv((long) Math.floor(z), GRID_CELL);
        final double far = (CLEARANCE_CELLS + 1) * GRID_CELL;
        double best = far * far;
        for (long i = cx - CLEARANCE_CELLS; i <= cx + CLEARANCE_CELLS; i++) {
            for (long j = cz - CLEARANCE_CELLS; j <= cz + CLEARANCE_CELLS; j++) {
                final List<double[]> list = grid.get((i << 32) ^ (j & 0xFFFFFFFFL));
                if (list == null) continue;
                for (double[] p : list) {
                    if ((int) p[2] == own) continue;
                    final double dx = p[0] - x, dz = p[1] - z, d = dx * dx + dz * dz;
                    if (d < best) best = d;
                }
            }
        }
        return Math.sqrt(best);
    }

    private static double bendRadius(List<double[]> pts, int k, int half) {
        final int a = Math.max(0, k - half), b = Math.min(pts.size() - 1, k + half);
        if (b - a < 2) return 1.0E9;
        final double[] pa = pts.get(a), pk = pts.get(k), pb = pts.get(b);
        final double t1 = Math.atan2(pk[1] - pa[1], pk[0] - pa[0]), t2 = Math.atan2(pb[1] - pk[1], pb[0] - pk[0]);
        double turn = Math.abs(t2 - t1);
        if (turn > Math.PI) turn = 2.0 * Math.PI - turn;
        final double arc = Math.hypot(pk[0] - pa[0], pk[1] - pa[1]) + Math.hypot(pb[0] - pk[0], pb[1] - pk[1]);
        return turn < 1.0E-6 ? 1.0E9 : arc / turn;
    }

    private static double[] erodeAndEase(double[] v, int r) {
        final int m = v.length;
        final double[] lo = new double[m], out = new double[m];
        for (int k = 0; k < m; k++) {
            double x = v[k];
            for (int j = Math.max(0, k - r); j <= Math.min(m - 1, k + r); j++) x = Math.min(x, v[j]);
            lo[k] = x;
        }
        for (int k = 0; k < m; k++) {
            double sum = 0.0;
            int cnt = 0;
            for (int j = Math.max(0, k - r); j <= Math.min(m - 1, k + r); j++) {
                sum += lo[j];
                cnt++;
            }
            out[k] = Math.min(lo[k] * 1.5, sum / cnt);
        }
        return out;
    }

    private static void catmullRom(double x0, double z0, double x1, double z1, double x2, double z2, double x3, double z3,
            double t, double[] out) {
        final double t0 = 0.0;
        final double t1 = t0 + Math.max(1.0E-4, Math.sqrt(Math.hypot(x1 - x0, z1 - z0)));
        final double t2 = t1 + Math.max(1.0E-4, Math.sqrt(Math.hypot(x2 - x1, z2 - z1)));
        final double t3 = t2 + Math.max(1.0E-4, Math.sqrt(Math.hypot(x3 - x2, z3 - z2)));
        final double u = t1 + t * (t2 - t1);
        final double a1x = ((t1 - u) * x0 + (u - t0) * x1) / (t1 - t0), a1z = ((t1 - u) * z0 + (u - t0) * z1) / (t1 - t0);
        final double a2x = ((t2 - u) * x1 + (u - t1) * x2) / (t2 - t1), a2z = ((t2 - u) * z1 + (u - t1) * z2) / (t2 - t1);
        final double a3x = ((t3 - u) * x2 + (u - t2) * x3) / (t3 - t2), a3z = ((t3 - u) * z2 + (u - t2) * z3) / (t3 - t2);
        final double b1x = ((t2 - u) * a1x + (u - t0) * a2x) / (t2 - t0), b1z = ((t2 - u) * a1z + (u - t0) * a2z) / (t2 - t0);
        final double b2x = ((t3 - u) * a2x + (u - t1) * a3x) / (t3 - t1), b2z = ((t3 - u) * a2z + (u - t1) * a3z) / (t3 - t1);
        out[0] = ((t2 - u) * b1x + (u - t1) * b2x) / (t2 - t1);
        out[1] = ((t2 - u) * b1z + (u - t1) * b2z) / (t2 - t1);
    }

    private static double smooth(double t) {
        final double c = Mth.clamp(t, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
