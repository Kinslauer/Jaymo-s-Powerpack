"""Generates the art of the medieval picks' Patch 1, Webbed up (all our own, drawn here; nothing copied).

Run from the project root:  python -I tools/gen_webbed.py

Jaymo's Powers (assets/jaymospowers/textures/):
  entity/webbed/web_sparse.png, web_medium.png, web_dense.png  the web skin drawn over a webbed body (64x64, tiling:
      it is laid over whatever model it wraps by that model's own UVs, so any part of it must read as web)
  entity/webbed/web_tuft.png  a tuft of web standing out of the body (32x32)
  gui/webbed/strands_thin.png, strands_thick.png, strands_dense.png  web across your own view, one sheet a layer
      (320x180, stretched over the screen: clear in the middle, strands clinging to the edges and corners)
Jaymo's Kingdoms (assets/jaymoskingdoms/textures/):
  block/mote_lantern.png, item/mote_lantern.png  a lantern with a court wizard's everburning mote in it, made from
      Kingdoms' own burnt lantern art, its glass filled with a pale glint instead of a flame
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
POWERS = ROOT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures"
KINGDOMS = ROOT / "kingdoms" / "src" / "main" / "resources" / "assets" / "jaymoskingdoms" / "textures"

SILK = [(236, 236, 240), (222, 222, 228), (206, 206, 214), (246, 246, 248)]


def silk(rng):
    return rng.choice(SILK) + (255,)


def tiled_lines(size, rng, strands, knots, sheets):
    """A tiling web: strands drawn on a 3x3 canvas and the middle cut out, so they run off one edge onto the other."""
    big = Image.new("RGBA", (size * 3, size * 3), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    # matted sheets first (the dense web's): small irregular blobs of silk
    for _ in range(sheets):
        cx, cy = rng.uniform(size, size * 2), rng.uniform(size, size * 2)
        r = rng.uniform(2.5, 5.5)
        pts = [(cx + math.cos(a) * r * rng.uniform(0.6, 1.2), cy + math.sin(a) * r * rng.uniform(0.6, 1.2))
               for a in [i * math.tau / 7 for i in range(7)]]
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d.polygon([(x + ox, y + oy) for x, y in pts], fill=(214, 214, 222, 255))
    for _ in range(strands):
        x0, y0 = rng.uniform(size, size * 2), rng.uniform(size, size * 2)
        a = rng.uniform(0, math.tau)
        length = rng.uniform(size * 0.4, size * 1.1)
        sag = rng.uniform(-4, 4)
        pts = []
        for i in range(9):
            t = i / 8
            x = x0 + math.cos(a) * length * t - math.sin(a) * sag * math.sin(t * math.pi)
            y = y0 + math.sin(a) * length * t + math.cos(a) * sag * math.sin(t * math.pi)
            pts.append((x, y))
        c = silk(rng)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d.line([(x + ox, y + oy) for x, y in pts], fill=c, width=1)
    for _ in range(knots):
        x, y = rng.randrange(size, size * 2), rng.randrange(size, size * 2)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d.rectangle([x + ox, y + oy, x + ox + 1, y + oy + 1], fill=(250, 250, 252, 255))
    return big.crop((size, size, size * 2, size * 2))


def skins():
    out = POWERS / "entity" / "webbed"
    out.mkdir(parents=True, exist_ok=True)
    for name, seed, strands, knots, sheets in (("web_sparse", 11, 9, 4, 0), ("web_medium", 23, 20, 9, 2), ("web_dense", 37, 38, 16, 7)):
        tiled_lines(64, random.Random(seed), strands, knots, sheets).save(out / f"{name}.png")


def tuft():
    """A tuft: threads splayed from a hub low on one side, sagging cross threads between them, ragged ends."""
    rng = random.Random(5)
    s = 4
    big = Image.new("RGBA", (32 * s, 32 * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    hub = (11 * s, 21 * s)
    angles = sorted(rng.uniform(-2.6, 0.9) for _ in range(7))
    ends = []
    for a in angles:
        r = rng.uniform(12, 19) * s
        end = (hub[0] + math.cos(a) * r, hub[1] + math.sin(a) * r)
        ends.append((a, r))
        d.line([hub, end], fill=(238, 238, 242, 255), width=s)
    for ring in (0.35, 0.6, 0.85):
        for (a0, r0), (a1, r1) in zip(ends, ends[1:]):
            p0 = (hub[0] + math.cos(a0) * r0 * ring, hub[1] + math.sin(a0) * r0 * ring)
            p1 = (hub[0] + math.cos(a1) * r1 * ring, hub[1] + math.sin(a1) * r1 * ring)
            mid = ((p0[0] + p1[0]) / 2 - (p1[1] - p0[1]) * 0.12, (p0[1] + p1[1]) / 2 + abs(p1[0] - p0[0]) * 0.12 + s)
            d.line([p0, mid, p1], fill=(222, 222, 230, 255), width=s)
    # a few loose wisps
    for _ in range(4):
        x, y = rng.uniform(4, 28) * s, rng.uniform(4, 28) * s
        d.line([(x, y), (x + rng.uniform(-5, 5) * s, y + rng.uniform(2, 6) * s)], fill=(210, 210, 218, 255), width=s)
    small = big.resize((32, 32), Image.Resampling.BOX)
    # hard edges for the cutout: keep what's mostly there
    px = small.load()
    for y in range(32):
        for x in range(32):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 90 else (0, 0, 0, 0)
    small.save(POWERS / "entity" / "webbed" / "web_tuft.png")


def edge_strands(w, h, rng, count, reach, alpha, matte):
    """Strands clinging round the edges of the view, reaching no further in than {reach} of the shorter side."""
    s = 2
    big = Image.new("RGBA", (w * s, h * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    R = min(w, h) * reach * s
    corners = [(0, 0), (w * s, 0), (0, h * s), (w * s, h * s)]
    if matte:
        # a soft matted rim of silk in the corners
        for cx, cy in corners:
            for i in range(10):
                r = R * 0.5 * (1 - i / 10)
                d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(235, 235, 240, int(14 + i * 2)))
    for _ in range(count):
        # anchored on an edge (mostly near a corner), sagging in and back out to the same or the next edge
        cx, cy = rng.choice(corners)
        a0 = rng.uniform(0, R * 1.6)
        a1 = rng.uniform(0, R * 1.6)
        if rng.random() < 0.5:
            p0 = (abs(cx - a0), cy)
            p1 = (cx, abs(cy - a1))
        else:
            along = rng.uniform(0, 1)
            if rng.random() < 0.5:
                x = along * w * s
                p0, p1 = (x, cy), (x + rng.uniform(-R, R), cy)
            else:
                y = along * h * s
                p0, p1 = (cx, y), (cx, y + rng.uniform(-R, R))
        mx, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
        # sag toward the middle of the screen
        tx, ty = w * s / 2 - mx, h * s / 2 - my
        tl = math.hypot(tx, ty) or 1
        sag = rng.uniform(0.25, 0.6) * R
        ctrl = (mx + tx / tl * sag, my + ty / tl * sag)
        pts = []
        for i in range(17):
            t = i / 16
            x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * ctrl[0] + t * t * p1[0]
            y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * ctrl[1] + t * t * p1[1]
            pts.append((x, y))
        a = int(alpha * rng.uniform(0.55, 1.0))
        d.line(pts, fill=(240, 240, 244, a), width=rng.choice((1, 2, 2, 3)))
        # a thread or two strung off it back to the edge
        if rng.random() < 0.6:
            k = pts[rng.randrange(4, 13)]
            d.line([k, (k[0] + (cx - k[0]) * 0.8, k[1] + (cy - k[1]) * 0.8)], fill=(232, 232, 238, int(a * 0.8)), width=1)
    return big.resize((w, h), Image.Resampling.LANCZOS)


def view():
    out = POWERS / "gui" / "webbed"
    out.mkdir(parents=True, exist_ok=True)
    for name, seed, count, reach, alpha, matte in (("strands_thin", 3, 16, 0.22, 210, False), ("strands_thick", 9, 28, 0.32, 210, False),
                                                   ("strands_dense", 17, 46, 0.42, 225, True)):
        edge_strands(320, 180, random.Random(seed), count, reach, alpha, matte).save(out / f"{name}.png")


MOTE = [  # a four-point glint: h heart, b bright, p pale
    "..p..",
    ".pbp.",
    "pbhbp",
    ".pbp.",
    "..p..",
]
COLOURS = {"h": (255, 255, 240, 255), "b": (255, 238, 170, 255), "p": (226, 206, 140, 255)}
GLASS = (70, 78, 96, 255)


def mote_lantern():
    burnt = Image.open(KINGDOMS / "block" / "burnt_lantern.png").convert("RGBA")
    block = burnt.copy()
    px = block.load()
    # the glass on the lantern's sides (rows 3 to 7 of its 6 wide side strip): dark glass, the mote in the middle
    for y in range(3, 8):
        for x in range(0, 6):
            px[x, y] = GLASS
    for dy, row in enumerate(MOTE):
        for dx, ch in enumerate(row):
            if ch != ".":
                x, y = dx + 0, dy + 3
                if 0 <= x < 6:
                    px[x, y] = COLOURS[ch]
    block.save(KINGDOMS / "block" / "mote_lantern.png")
    item = Image.open(KINGDOMS / "item" / "burnt_lantern.png").convert("RGBA")
    ip = item.load()
    for y in range(8, 13):
        for x in range(5, 11):
            ip[x, y] = GLASS
    for dy, row in enumerate(MOTE):
        for dx, ch in enumerate(row):
            if ch != ".":
                ip[dx + 5, dy + 8] = COLOURS[ch]
    item.save(KINGDOMS / "item" / "mote_lantern.png")


if __name__ == "__main__":
    skins()
    tuft()
    view()
    mote_lantern()
    print("webbed art written")
