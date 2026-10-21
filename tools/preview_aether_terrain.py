#!/usr/bin/env python3
"""Draws the Aether's terrain from the shipped data: a vertical slice and top-down maps (height and biome).

It reads src/main/resources/data/jaymospowers/{worldgen/density_function,worldgen/noise,worldgen/noise_settings,dimension}
and evaluates the real density-function JSON (the types the Aether uses: add, sub, mul, min, max, clamp, abs, square, cube,
squeeze, gradient, noise, range_choice, spline, lerp and the cache/interpolated wrappers) on a grid. Interpolated functions
are sampled at their cell corners and blended trilinearly, as the game does. The noise is a stand-in (my own Perlin, scaled
to the game's NormalNoise deviation of about 0.336), so shapes and densities match the data, not the game's exact seed.

Usage (from the repo root):
  python3 -I tools/preview_aether_terrain.py [--seed N] [--z Z] [--x0 X] [--span N] [--out DIR] [--map-x X --map-z Z --map-span N]
Writes aether_slice.png, aether_map_height.png and aether_map_biome.png into DIR, and prints the terrain's numbers.
Needs numpy and Pillow.
"""
import argparse
import json
import math
import os
import sys
import tempfile
import zlib

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DATA = os.path.join(ROOT, 'src/main/resources/data')
SPLIT_Y = 140            # the picture's lowland/upper split
MIN_Y, HEIGHT = 0, 384   # the noise region (noise_settings.noise)
TARGET_STD = 0.336       # what the game's NormalNoise comes to (measured on the real classes)


# ---------------------------------------------------------------- noise

class Perlin:
    def __init__(self, rng):
        p = np.arange(256)
        rng.shuffle(p)
        self.p = np.concatenate([p, p]).astype(np.int64)
        self.off = rng.uniform(0, 256, 3)

    def at(self, x, y, z):
        x, y, z = np.broadcast_arrays(x + self.off[0], y + self.off[1], z + self.off[2])
        xi = np.floor(x)
        yi = np.floor(y)
        zi = np.floor(z)
        fx, fy, fz = x - xi, y - yi, z - zi
        X, Y, Z = xi.astype(np.int64) & 255, yi.astype(np.int64) & 255, zi.astype(np.int64) & 255
        u = fx * fx * fx * (fx * (fx * 6 - 15) + 10)
        v = fy * fy * fy * (fy * (fy * 6 - 15) + 10)
        w = fz * fz * fz * (fz * (fz * 6 - 15) + 10)
        p = self.p
        A = p[X] + Y
        AA = p[A] + Z
        AB = p[A + 1] + Z
        B = p[X + 1] + Y
        BA = p[B] + Z
        BB = p[B + 1] + Z

        def grad(h, a, b, c):
            h = h & 15
            uu = np.where(h < 8, a, b)
            vv = np.where(h < 4, b, np.where((h == 12) | (h == 14), a, c))
            return np.where((h & 1) == 0, uu, -uu) + np.where((h & 2) == 0, vv, -vv)

        def lerp(t, a, b):
            return a + t * (b - a)

        return lerp(w,
                    lerp(v, lerp(u, grad(p[AA], fx, fy, fz), grad(p[BA], fx - 1, fy, fz)),
                         lerp(u, grad(p[AB], fx, fy - 1, fz), grad(p[BB], fx - 1, fy - 1, fz))),
                    lerp(v, lerp(u, grad(p[AA + 1], fx, fy, fz - 1), grad(p[BA + 1], fx - 1, fy, fz - 1)),
                         lerp(u, grad(p[AB + 1], fx, fy - 1, fz - 1), grad(p[BB + 1], fx - 1, fy - 1, fz - 1))))


class NormalNoise:
    """The game's two-layer octave noise: octave i is 2^(first+i) cycles a block, weighted by amplitude * 2^-i."""

    def __init__(self, seed, name, first, amps):
        rng = np.random.RandomState((zlib.crc32(name.encode()) ^ seed) & 0x7fffffff)
        self.first, self.amps = first, amps
        self.a = [Perlin(rng) for _ in amps]
        self.b = [Perlin(rng) for _ in amps]
        self.k = 1.0
        pts = np.random.RandomState(7).uniform(0, 1 << 14, (3, 30000))
        self.k = TARGET_STD / max(1e-9, float(np.std(self.value(pts[0], pts[1], pts[2]))))

    def value(self, x, y, z):
        total = 0.0
        for i, amp in enumerate(self.amps):
            if amp == 0.0:
                continue
            f = 2.0 ** (self.first + i)
            w = amp * 0.5 ** i
            total = total + w * (self.a[i].at(x * f, y * f, z * f) + self.b[i].at(x * f * 1.0181268882175227, y * f * 1.0181268882175227, z * f * 1.0181268882175227))
        return total * self.k


# ---------------------------------------------------------------- density functions

class World:
    def __init__(self, seed):
        self.seed = seed
        self.files = {}
        self.noises = {}

    def load(self, kind, ident):
        key = (kind, ident)
        if key not in self.files:
            ns, _, path = ident.partition(':')
            with open(os.path.join(DATA, ns, 'worldgen', kind, path + '.json'), encoding='utf-8') as f:
                self.files[key] = json.load(f)
        return self.files[key]

    def noise(self, ident):
        if ident not in self.noises:
            try:
                d = self.load('noise', ident)
            except FileNotFoundError:
                # vanilla noises the Aether borrows: only the offset (the shifts) matters here
                d = {'base_octave': -3, 'octave_count': 4, 'amplitude_modifiers': [1.0, 1.0, 1.0, 0.0]}
            amps = d.get('amplitude_modifiers') or [1.0] * d.get('octave_count', 1)
            self.noises[ident] = NormalNoise(self.seed, ident, d['base_octave'], amps)
        return self.noises[ident]


def smooth_spline(spline, v):
    pts = spline['points']
    loc = np.array([p['location'] for p in pts], dtype=float)
    val = np.array([p['value'] for p in pts], dtype=float)
    der = np.array([p['derivative'] for p in pts], dtype=float)
    v = np.asarray(v, dtype=float)
    idx = np.clip(np.searchsorted(loc, v) - 1, 0, len(loc) - 2)
    x0, x1 = loc[idx], loc[idx + 1]
    h = x1 - x0
    t = np.clip((v - x0) / h, 0.0, 1.0)
    y0, y1, d0, d1 = val[idx], val[idx + 1], der[idx] * h, der[idx + 1] * h
    t2, t3 = t * t, t * t * t
    out = (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * d0 + (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * d1
    out = np.where(v < loc[0], val[0] + der[0] * (v - loc[0]), out)
    out = np.where(v > loc[-1], val[-1] + der[-1] * (v - loc[-1]), out)
    return out


class Ctx:
    """A grid: 1-D axes of x, y and z, evaluated as an open mesh (so 2-D parts stay cheap)."""

    def __init__(self, xs, ys, zs):
        self.xs, self.ys, self.zs = np.asarray(xs, float), np.asarray(ys, float), np.asarray(zs, float)
        self.X = self.xs.reshape(-1, 1, 1)
        self.Y = self.ys.reshape(1, -1, 1)
        self.Z = self.zs.reshape(1, 1, -1)
        self.memo = {}


def lerp_axis(arr, axis, coarse, fine):
    """Linear interpolation of arr along axis from the coarse coordinates to the fine ones."""
    step = coarse[1] - coarse[0]
    pos = (fine - coarse[0]) / step
    i0 = np.clip(np.floor(pos).astype(int), 0, len(coarse) - 2)
    f = pos - i0
    a = np.take(arr, i0, axis=axis)
    b = np.take(arr, i0 + 1, axis=axis)
    shape = [1, 1, 1]
    shape[axis] = -1
    f = f.reshape(shape)
    return a * (1 - f) + b * f


def ev(world, node, ctx):
    if isinstance(node, (int, float)):
        return float(node)
    if isinstance(node, str):
        if node == 'minecraft:y':
            return ctx.Y
        if node in ('minecraft:shift_x', 'minecraft:shift_z'):
            n = world.noise('minecraft:offset')
            if node.endswith('x'):
                return n.value(ctx.X * 0.25, 0.0, ctx.Z * 0.25) * 4.0
            return n.value(ctx.Z * 0.25, ctx.X * 0.25, 0.0) * 4.0
        key = ('ref', node)
        if key not in ctx.memo:
            ctx.memo[key] = ev(world, world.load('density_function', node), ctx)
        return ctx.memo[key]
    t = node['type'].split(':')[-1]
    if t in ('add', 'sub', 'mul', 'min', 'max'):
        a, b = ev(world, node['left'], ctx), ev(world, node['right'], ctx)
        return {'add': np.add, 'sub': np.subtract, 'mul': np.multiply, 'min': np.minimum, 'max': np.maximum}[t](a, b)
    if t == 'clamp':
        return np.clip(ev(world, node['input'], ctx), node['min'], node['max'])
    if t == 'abs':
        return np.abs(ev(world, node['input'], ctx))
    if t == 'square':
        return np.square(ev(world, node['input'], ctx))
    if t == 'cube':
        return np.power(ev(world, node['input'], ctx), 3)
    if t == 'half_negative':
        v = ev(world, node['input'], ctx)
        return np.where(v > 0, v, v * 0.5)
    if t == 'quarter_negative':
        v = ev(world, node['input'], ctx)
        return np.where(v > 0, v, v * 0.25)
    if t == 'squeeze':
        c = np.clip(ev(world, node['input'], ctx), -1.0, 1.0)
        return c / 2.0 - c * c * c / 24.0
    if t in ('cache', 'flat_cache', 'cache_once', 'cache_2d', 'blend_density', 'cache_all_in_cell'):
        return ev(world, node['input'], ctx)
    if t in ('beardifier', 'blend_alpha', 'blend_offset'):
        return 0.0 if t != 'blend_alpha' else 1.0
    if t == 'gradient':
        v = ctx.Y
        lo, hi = node['from_coordinate'], node['to_coordinate']
        f = np.clip((v - lo) / (hi - lo), 0.0, 1.0)
        return node['from_value'] + f * (node['to_value'] - node['from_value'])
    if t == 'noise':
        n = world.noise(node['noise'])
        xz, yy = node.get('xz_scale', 1.0), node.get('y_scale', 1.0)
        x = ctx.X * xz
        z = ctx.Z * xz
        if 'shift_x' in node:
            x = x + ev(world, node['shift_x'], ctx)
        if 'shift_z' in node:
            z = z + ev(world, node['shift_z'], ctx)
        y = ctx.Y * yy if yy != 0.0 else 0.0
        return n.value(x, y, z)
    if t == 'range_choice':
        v = ev(world, node['input'], ctx)
        inside = (v >= node['min_inclusive']) & (v < node['max_exclusive'])
        return np.where(inside, ev(world, node['when_in_range'], ctx), ev(world, node['when_out_of_range'], ctx))
    if t == 'lerp':
        a = ev(world, node['alpha'], ctx)
        f, s = ev(world, node['first'], ctx), ev(world, node['second'], ctx)
        return f + a * (s - f)
    if t == 'spline':
        sp = node['spline']
        return smooth_spline(sp, ev(world, sp['coordinate'], ctx))
    if t == 'interpolated':
        cxz, cy = node['cell_size_xz'], node['cell_size_y']
        def gap(a):
            return float(a[1] - a[0]) if len(a) > 1 else float('inf')
        if gap(ctx.xs) >= cxz and gap(ctx.zs) >= cxz and gap(ctx.ys) >= cy:
            return ev(world, node['input'], ctx)     # a coarse picture: sample the cells directly
        cx = np.arange(math.floor(ctx.xs.min() / cxz) * cxz, ctx.xs.max() + cxz + 1, cxz, dtype=float)
        cz = np.arange(math.floor(ctx.zs.min() / cxz) * cxz, ctx.zs.max() + cxz + 1, cxz, dtype=float)
        cyv = np.arange(math.floor(ctx.ys.min() / cy) * cy, ctx.ys.max() + cy + 1, cy, dtype=float)
        cctx = Ctx(cx, cyv, cz)
        c = ev(world, node['input'], cctx)
        c = np.broadcast_to(c, (len(cx), len(cyv), len(cz)))
        c = lerp_axis(c, 0, cx, ctx.xs)
        c = lerp_axis(c, 1, cyv, ctx.ys)
        return lerp_axis(c, 2, cz, ctx.zs)
    raise ValueError('density function type not supported by the preview: ' + t)


def full(arr, ctx):
    return np.broadcast_to(arr, (len(ctx.xs), len(ctx.ys), len(ctx.zs)))


# ---------------------------------------------------------------- the picture

SKY_TOP, SKY_BOTTOM = np.array([150, 190, 255]), np.array([225, 235, 255])
STONE, UNDERSHALE, DIRT, GRASS = (205, 207, 214), (96, 98, 112), (150, 138, 124), (120, 220, 140)


def slice_image(world, final, x0, z0, span, scale):
    xs = np.arange(x0, x0 + span)
    ys = np.arange(MIN_Y, MIN_Y + HEIGHT)
    ctx = Ctx(xs, ys, [z0])
    d = full(ev(world, final, ctx), ctx)[:, :, 0]
    solid = d > 0
    img = np.zeros((HEIGHT, span, 3), dtype=np.uint8)
    sky = (SKY_BOTTOM[None, :] + (SKY_TOP - SKY_BOTTOM)[None, :] * (np.arange(HEIGHT)[:, None] / HEIGHT)).astype(np.uint8)
    img[:] = sky[::-1][:, None, :] if False else sky[:, None, :]
    # colour columns: grass on the top block of each island run, dirt under it, undershale deep down
    for xi in range(span):
        col = solid[xi]
        depth = 0
        for yi in range(HEIGHT - 1, -1, -1):
            if col[yi]:
                if depth == 0:
                    c = GRASS
                elif depth <= 3:
                    c = DIRT
                else:
                    c = UNDERSHALE if yi < 64 else STONE
                img[yi, xi] = c
                depth += 1
            else:
                depth = 0
    img = img[::-1]  # y up
    out = Image.fromarray(img).resize((span * scale, HEIGHT * scale), Image.NEAREST)
    draw = ImageDraw.Draw(out)
    for yv in (0, 64, 128, 192, 256, 320, 384):
        py = (HEIGHT - (yv - MIN_Y)) * scale - 1
        draw.line([(0, py), (6 * scale, py)], fill=(0, 0, 0))
        draw.text((7 * scale, py - 5), 'y%d' % yv, fill=(0, 0, 0))
    return out, d


def surface_heights(d):
    """Top solid block of every column (-1 where there is none), and the mass per 32 blocks of height."""
    solid = d > 0
    any_ = solid.any(axis=1)
    top = np.where(any_, solid.shape[1] - 1 - np.argmax(solid[:, ::-1], axis=1), -1) + MIN_Y
    return top


BIOME_COLOURS = {
    'frozen_lakes': (180, 215, 255), 'sheer_tundra': (225, 235, 245), 'enduring_woodland': (90, 140, 150), 'frigid_sierra': (255, 255, 255),
    'shimmering_basin': (110, 190, 255), 'flourishing_field': (170, 235, 120), 'verdant_woods': (40, 150, 60), 'shrouded_forest': (20, 90, 50),
    'violet_highwoods': (160, 110, 220), 'turquoise_forest': (50, 190, 180), 'glistening_swamp': (80, 120, 90), 'magnetic_scar': (170, 100, 70),
    'battleground_wastes': (210, 190, 90), 'contaminated_jungle': (150, 170, 40), 'hestveil_caverns': (120, 60, 60), 'expanse': (255, 200, 255),
}


def pick_biomes(world, dim, T, H, E, W, D):
    """The multi-noise source's choice: the entry whose parameter box is nearest (sum of squared gaps)."""
    entries = dim['generator']['biome_source']['biomes']
    names = [e['biome'].split(':')[1] for e in entries]
    best = np.full(T.shape, 1e18)
    which = np.zeros(T.shape, dtype=int)
    vals = {'temperature': T, 'humidity': H, 'erosion': E, 'weirdness': W, 'depth': D}
    for i, e in enumerate(entries):
        cost = np.zeros(T.shape)
        for key, arr in vals.items():
            r = e['parameters'][key]
            lo, hi = (r, r) if not isinstance(r, list) else (r[0], r[1])
            gap = np.maximum(0.0, np.maximum(lo - arr, arr - hi))
            cost = cost + gap * gap
        better = cost < best
        best = np.where(better, cost, best)
        which = np.where(better, i, which)
    return names, which


def map_images(world, final, dim, mx0, mz0, span, step):
    xs = np.arange(mx0, mx0 + span, step)
    zs = np.arange(mz0, mz0 + span, step)
    ys = np.arange(MIN_Y, MIN_Y + HEIGHT, 8)
    ctx = Ctx(xs, ys, zs)
    d = full(ev(world, final, ctx), ctx)
    solid = d > 0
    low = ys < SPLIT_Y
    sl = solid[:, low, :]
    any_ = sl.any(axis=1)
    topi = sl.shape[1] - 1 - np.argmax(sl[:, ::-1, :], axis=1)
    top = np.where(any_, ys[low][topi], -1)          # (x, z): the lowland's highest block
    up_cells = solid[:, ~low, :].sum(axis=1)         # how much upper-island mass stands over each column
    # colour by height; columns with no lowland ground stay sky, upper islets are drawn as a shade
    hm = np.zeros(top.shape + (3,), dtype=np.uint8)
    hm[:] = (205, 225, 255)
    pal = [(0, (90, 60, 90)), (40, (120, 120, 150)), (80, (110, 200, 120)), (110, (70, 170, 80)), (125, (240, 240, 180)), (140, (255, 200, 200))]
    for xi in range(top.shape[0]):
        for zi in range(top.shape[1]):
            h = top[xi, zi]
            if h >= 0:
                for k in range(len(pal) - 1):
                    if pal[k][0] <= h <= pal[k + 1][0]:
                        t = (h - pal[k][0]) / (pal[k + 1][0] - pal[k][0])
                        hm[xi, zi] = tuple(int(pal[k][1][c] + t * (pal[k + 1][1][c] - pal[k][1][c])) for c in range(3))
                        break
            if up_cells[xi, zi] > 0:
                shade = max(60, 200 - 12 * int(up_cells[xi, zi]))
                hm[xi, zi] = (shade // 2, shade // 2, 255 - (255 - shade) // 3) if h < 0 else (hm[xi, zi] // 2 + np.array([100, 60, 120])).astype(np.uint8)
    # biomes at each column's surface
    c2 = Ctx(xs, [0], zs)
    T = full(ev(world, 'jaymospowers:aether/climate/temperature', c2), c2)[:, 0, :]
    Hh = full(ev(world, 'jaymospowers:aether/climate/humidity', c2), c2)[:, 0, :]
    E = full(ev(world, 'jaymospowers:aether/climate/erosion', c2), c2)[:, 0, :]
    Wd = full(ev(world, 'jaymospowers:aether/climate/weirdness', c2), c2)[:, 0, :]
    ytop = np.where(top >= 0, top, 100).astype(float)
    # depth at the surface height
    cy = Ctx([0], ytop.ravel(), [0])
    Dd = full(ev(world, 'jaymospowers:aether/climate/depth', cy), cy)[0, :, 0].reshape(top.shape)
    names, which = pick_biomes(world, dim, T, Hh, E, Wd, Dd)
    bm = np.zeros(top.shape + (3,), dtype=np.uint8)
    bm[:] = (230, 240, 255)
    counts = {}
    for xi in range(top.shape[0]):
        for zi in range(top.shape[1]):
            if top[xi, zi] >= 0:
                name = names[which[xi, zi]]
                bm[xi, zi] = BIOME_COLOURS.get(name, (255, 0, 255))
                counts[name] = counts.get(name, 0) + 1
    def up(a, scale):
        return Image.fromarray(np.transpose(a, (1, 0, 2))).resize((a.shape[0] * scale, a.shape[1] * scale), Image.NEAREST)
    return up(hm, 3), up(bm, 3), top, counts, (T, Hh, E, Wd)


def mass_profile(d):
    solid = d > 0
    per_y = solid.sum(axis=(0, 2)).astype(float) if solid.ndim == 3 else solid.sum(axis=0).astype(float)
    return per_y


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--seed', type=int, default=12345)
    ap.add_argument('--z', type=int, default=0)
    ap.add_argument('--x0', type=int, default=0)
    ap.add_argument('--span', type=int, default=640)
    ap.add_argument('--scale', type=int, default=2)
    ap.add_argument('--map-x', type=int, default=-1024)
    ap.add_argument('--map-z', type=int, default=-1024)
    ap.add_argument('--map-span', type=int, default=2048)
    ap.add_argument('--map-step', type=int, default=32)
    ap.add_argument('--out', default=tempfile.gettempdir())
    ap.add_argument('--no-map', action='store_true')
    args = ap.parse_args()
    world = World(args.seed)
    with open(os.path.join(DATA, 'jaymospowers/dimension/aether.json'), encoding='utf-8') as f:
        dim = json.load(f)
    with open(os.path.join(DATA, 'jaymospowers/worldgen/noise_settings/aether.json'), encoding='utf-8') as f:
        settings = json.load(f)
    final = settings['noise_router']['final_density']
    os.makedirs(args.out, exist_ok=True)

    img, d = slice_image(world, final, args.x0, args.z, args.span, args.scale)
    path = os.path.join(args.out, 'aether_slice.png')
    img.save(path)
    print('wrote', path)
    mass = (d > 0).sum(axis=0)
    total = mass.sum()
    print('solid share of the slice: %.1f%%' % (100.0 * total / d.size))
    print('share of the mass per 32 blocks of height:')
    for y0 in range(0, HEIGHT, 32):
        print('  y %3d-%3d  %5.1f%%' % (y0, y0 + 31, 100.0 * mass[y0:y0 + 32].sum() / max(1, total)))
    if not args.no_map:
        hm, bm, top, counts, _ = map_images(world, final, dim, args.map_x, args.map_z, args.map_span, args.map_step)
        hp = os.path.join(args.out, 'aether_map_height.png')
        bp = os.path.join(args.out, 'aether_map_biome.png')
        hm.save(hp)
        bm.save(bp)
        print('wrote', hp)
        print('wrote', bp)
        cover = (top >= 0).mean()
        print('columns with lowland ground (below y140): %.1f%%' % (100.0 * cover))
        total = sum(counts.values()) or 1
        for name, n in sorted(counts.items(), key=lambda kv: -kv[1]):
            print('  %-20s %5.1f%%' % (name, 100.0 * n / total))


if __name__ == '__main__':
    main()
