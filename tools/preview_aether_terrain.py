"""Draws a side-on slice of the Aether's terrain so the island shapes can be eyeballed without launching the game.

    python -I tools/preview_aether_terrain.py [z] [seed] [out.png]

It reads the real data (worldgen/noise_settings/aether.json and the density functions it names) and evaluates the
final density with a small interpreter, so a change to the JSON shows up here. Only the density function types the
Aether uses are supported (add, mul, lerp, gradient, clamp, squeeze, interpolated, old_blended_noise, constants).

The one stand-in is the noise: the game's old blended noise is rebuilt here from the same recipe (8 main octaves
choosing between two 16-octave limit noises, y smeared in steps of smear_scale_multiplier), but with Perlin noise seeded
by numpy, so islands will not match a given world seed, only their size, density and shape. Needs numpy and Pillow.

Writes a 256x128 block slice at the chosen z (default 0), 4 pixels a block, and prints how much of the solid mass sits
in each 16-block band of height. Solid cells above air are drawn green, the dirt layers under them brown, the rest
holystone grey; the cell grid (8 wide, 4 tall) the game interpolates over is applied, as it is in the game.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
OUT_DIR = "/tmp/claude-0/-home-user-Jaymo-s-Powerpack/2b93d717-3c36-5b65-add9-935a1021d356/scratchpad/aether-study"

WIDTH, HEIGHT = 256, 128
SCALE = 4
DIRT_LAYERS = 3

GRADS = np.array([(1, 1, 0), (-1, 1, 0), (1, -1, 0), (-1, -1, 0), (1, 0, 1), (-1, 0, 1), (1, 0, -1), (-1, 0, -1),
                  (0, 1, 1), (0, -1, 1), (0, 1, -1), (0, -1, -1), (1, 1, 0), (0, -1, 1), (-1, 1, 0), (0, -1, -1)], dtype=float)


class Perlin:
    """Improved Perlin noise with the game's gradient set, plus its y smear (the fractional y snapped to y_scale steps)."""

    def __init__(self, rng):
        self.off = rng.uniform(0, 256, 3)
        self.perm = np.concatenate([rng.permutation(256)] * 2)

    def noise(self, x, y, z, y_scale=0.0):
        x, y, z = x + self.off[0], y + self.off[1], z + self.off[2]
        xi, yi, zi = np.floor(x), np.floor(y), np.floor(z)
        xf, yf, zf = x - xi, y - yi, z - zi
        yr = np.floor(yf / y_scale + 1e-7) * y_scale if y_scale else 0.0
        xi, yi, zi = xi.astype(int) & 255, yi.astype(int) & 255, zi.astype(int) & 255
        p = self.perm

        def corner(dx, dy, dz):
            h = p[p[p[xi + dx] + yi + dy] + zi + dz] & 15
            g = GRADS[h]
            return g[..., 0] * (xf - dx) + g[..., 1] * (yf - yr - dy) + g[..., 2] * (zf - dz)

        def fade(t):
            return t * t * t * (t * (t * 6 - 15) + 10)

        u, v, w = fade(xf), fade(yf), fade(zf)
        lerp = lambda t, a, b: a + t * (b - a)
        return lerp(w,
                    lerp(v, lerp(u, corner(0, 0, 0), corner(1, 0, 0)), lerp(u, corner(0, 1, 0), corner(1, 1, 0))),
                    lerp(v, lerp(u, corner(0, 0, 1), corner(1, 0, 1)), lerp(u, corner(0, 1, 1), corner(1, 1, 1))))


class BlendedNoise:
    def __init__(self, cfg, seed):
        rng = np.random.RandomState(seed)
        self.main = [Perlin(rng) for _ in range(8)]
        self.lo = [Perlin(rng) for _ in range(16)]
        self.hi = [Perlin(rng) for _ in range(16)]
        self.xz_m = 684.412 * cfg["xz_scale"]
        self.y_m = 684.412 * cfg["y_scale"]
        self.xz_f, self.y_f = cfg["xz_factor"], cfg["y_factor"]
        self.smear = cfg["smear_scale_multiplier"]

    def __call__(self, X, Y, Z):
        d0, d1, d2 = X * self.xz_m, Y * self.y_m, Z * self.xz_m
        d3, d4, d5 = d0 / self.xz_f, d1 / self.y_f, d2 / self.xz_f
        d6 = self.y_m * self.smear
        d7 = d6 / self.y_f
        sel, f = 0.0, 1.0
        for p in self.main:
            sel = sel + p.noise(d3 * f, d4 * f, d5 * f, d7 * f) / f
            f /= 2
        t = np.clip((sel / 10.0 + 1.0) / 2.0, 0.0, 1.0)
        lo = hi = 0.0
        f = 1.0
        for a, b in zip(self.lo, self.hi):
            lo = lo + a.noise(d0 * f, d1 * f, d2 * f, d6 * f) / f
            hi = hi + b.noise(d0 * f, d1 * f, d2 * f, d6 * f) / f
            f /= 2
        return (lo / 512.0 + (hi / 512.0 - lo / 512.0) * t) / 128.0


def load_function(ref):
    ns, path = ref.split(":")
    if ns == "minecraft":
        raise SystemExit("preview does not know vanilla density function " + ref)
    with open(os.path.join(DATA, ns, "worldgen", "density_function", path + ".json")) as f:
        return json.load(f)


class Evaluator:
    def __init__(self, seed):
        self.seed = seed
        self.noises = {}

    def ev(self, node, X, Y, Z):
        if isinstance(node, (int, float)):
            return np.full_like(X, float(node))
        if isinstance(node, str):
            return self.ev(load_function(node), X, Y, Z)
        t = node["type"].split(":")[1]
        if t == "add":
            return self.ev(node["left"], X, Y, Z) + self.ev(node["right"], X, Y, Z)
        if t == "mul":
            return self.ev(node["left"], X, Y, Z) * self.ev(node["right"], X, Y, Z)
        if t == "clamp":
            return np.clip(self.ev(node["input"], X, Y, Z), node["min"], node["max"])
        if t == "lerp":
            a = self.ev(node["alpha"], X, Y, Z)
            f, s = self.ev(node["first"], X, Y, Z), self.ev(node["second"], X, Y, Z)
            return f + a * (s - f)
        if t == "gradient":
            u = (Y - node["from_coordinate"]) / (node["to_coordinate"] - node["from_coordinate"])
            return node["from_value"] + np.clip(u, 0, 1) * (node["to_value"] - node["from_value"])
        if t == "squeeze":
            c = np.clip(self.ev(node["input"], X, Y, Z), -1, 1)
            return c / 2 - c * c * c / 24
        if t == "beardifier":
            return np.zeros_like(X)
        if t in ("blend_density", "cache_2d", "cache_once", "flat_cache"):
            return self.ev(node["input"], X, Y, Z)
        if t == "interpolated":
            cx, cy, cz = node["cell_size_xz"], node["cell_size_y"], node["cell_size_xz"]
            x0, y0, z0 = np.floor(X / cx) * cx, np.floor(Y / cy) * cy, np.floor(Z / cz) * cz
            fx, fy, fz = (X - x0) / cx, (Y - y0) / cy, (Z - z0) / cz
            out = 0.0
            for dx in (0, 1):
                for dy in (0, 1):
                    for dz in (0, 1):
                        w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                        out = out + w * self.ev(node["input"], x0 + dx * cx, y0 + dy * cy, z0 + dz * cz)
            return out
        if t == "old_blended_noise":
            key = json.dumps(node, sort_keys=True)
            if key not in self.noises:
                self.noises[key] = BlendedNoise(node, self.seed)
            return self.noises[key](X, Y, Z)
        raise SystemExit("preview does not know density function type " + node["type"])


def main():
    z = float(sys.argv[1]) if len(sys.argv) > 1 else 0.0
    seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    out = sys.argv[3] if len(sys.argv) > 3 else os.path.join(OUT_DIR, "aether_terrain_preview.png")
    with open(os.path.join(DATA, "jaymospowers", "worldgen", "noise_settings", "aether.json")) as f:
        settings = json.load(f)
    final = settings["noise_router"]["final_density"]
    X, Y = np.meshgrid(np.arange(WIDTH, dtype=float), np.arange(HEIGHT, dtype=float))
    solid = Evaluator(seed).ev(final, X, Y, np.full_like(X, z)) > 0  # [y][x]
    print("solid share of the slice: %.1f%%" % (100.0 * solid.mean()))
    total = solid.sum()
    for lo in range(0, HEIGHT, 16):
        share = 100.0 * solid[lo:lo + 16].sum() / max(total, 1)
        print("y %3d-%3d  %5.1f%% of the mass  %s" % (lo, lo + 15, share, "#" * int(share / 2)))
    sky = np.array([192, 192, 255], dtype=np.uint8)
    img = np.empty((HEIGHT, WIDTH, 3), dtype=np.uint8)
    img[:] = sky
    for x in range(WIDTH):
        depth = 0
        for y in range(HEIGHT - 1, -1, -1):
            if not solid[y, x]:
                depth = 0
                continue
            depth += 1
            img[y, x] = (110, 214, 140) if depth == 1 else (140, 110, 90) if depth <= 1 + DIRT_LAYERS else (225, 228, 235)
    img = img[::-1]  # y up
    Image.fromarray(img).resize((WIDTH * SCALE, HEIGHT * SCALE), Image.NEAREST).save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
