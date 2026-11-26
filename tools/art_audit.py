#!/usr/bin/env python3
"""The Reason look's art check: finds textures that look script-made instead of painted (docs/Vanilla-Style.md).

    python3 tools/art_audit.py [paths...]      check these PNGs (pr_checks.py passes the ones a PR adds or changes)
    python3 tools/art_audit.py --all           count every texture of the three mods and the Reason look pack

What it measures, against vanilla 26.3's own icons and blocks (2026-11-26 scan, project files
art-overhaul/pack-art-scan/): vanilla items use 5 to 18 colours (23 at the 99th percentile) and almost never step
from one pixel to the next by a hair; blocks stay under about 40 colours. Script-made art fills shapes with per-pixel
noise or soft gradients, so it has far more colours and many tiny steps. Every redraw in the Reason look pack passes.

Fails a 16 px item (scaled for bigger ones) or block when:
  - it has more colours than vanilla ever uses (items 36, blocks 80), or
  - a quarter of an item's neighbouring pixels differ by a hair (a gradient), or
  - an item has half-transparent pixels.
Mob skins and worn armour are only reported, not failed: numbers judge them less well.
Glows, effects, particles and screens are skipped (SKIP); a deliberate exception goes in tools/pr_checks_allow.txt.
Pure Python (zlib), so the PR workflow needs nothing installed.
"""
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ["src/main/resources", "kingdoms/src/main/resources", "kitchen/src/main/resources"]
SKIP = ("glow", "emissive", "_fx", "aura", "beam", "flame", "halo", "_eyes", "wisp", "/particle/", "/gui/",
        "/mob_effect/", "/environment/", "/misc/", "/font/", "overlay", "_e.png", "spark", "flash", "portal")
ITEM_COLOURS, BLOCK_COLOURS, GRADIENT, SEMI = 36, 80, 0.25, 0.02


def read_png(path):
    """(width, height, rows of (r, g, b, a)) for 8-bit PNGs, or None for anything it can't read."""
    data = Path(path).read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    pos, idat, plte, trns = 8, b"", None, None
    while pos + 8 <= len(data):
        n, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            w, h, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            plte = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + n
    if depth != 8 or interlace or ctype not in (0, 2, 3, 4, 6):
        return None
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw, stride, rows, prev = zlib.decompress(idat), w * bpp, [], bytearray(w * bpp)
    for y in range(h):
        f, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b, c = prev[i], prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        px = []
        for x in range(w):
            v = line[x * bpp:(x + 1) * bpp]
            if ctype == 6:
                px.append(tuple(v))
            elif ctype == 2:
                px.append((v[0], v[1], v[2], 255))
            elif ctype == 0:
                px.append((v[0], v[0], v[0], 255))
            elif ctype == 4:
                px.append((v[0], v[0], v[0], v[1]))
            else:
                r, g, b2 = plte[v[0]]
                px.append((r, g, b2, trns[v[0]] if trns and v[0] < len(trns) else 255))
        rows.append(px)
    return w, h, rows


def kind_of(path):
    p = str(path).replace("\\", "/")
    for k in ("item", "block", "entity"):
        if f"/textures/{k}/" in p:
            return k
    return None


def faults(path):
    """The reasons this texture looks script-made ([] when it passes, None when it isn't judged)."""
    p = str(path).replace("\\", "/")
    kind = kind_of(p)
    if kind is None or any(s in p for s in SKIP):
        return None
    try:
        png = read_png(path)
    except (struct.error, zlib.error, NameError, IndexError):  # a truncated or broken PNG isn't judged
        png = None
    if png is None:
        return None
    w, h, rows = png
    if h > w and h % w == 0:  # animation strip: its first frame
        rows, h = rows[:w], w
    seen = [(x, y) for y in range(h) for x in range(w) if rows[y][x][3] > 0]
    if len(seen) < 8:
        return []
    lum = lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
    colours = len({rows[y][x][:3] for x, y in seen})
    steps = tiny = 0
    for x, y in seen:
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if nx < w and ny < h and rows[ny][nx][3] > 0:
                d = abs(lum(rows[y][x]) - lum(rows[ny][nx]))
                steps += 1
                tiny += 0 < d < 4
    semi = sum(1 for x, y in seen if rows[y][x][3] < 255) / len(seen)
    scale = max(1.0, (w / 16) ** 2)
    out = []
    if kind == "entity":
        if colours > 140 and len(seen) > 500:
            out.append(f"{colours} colours")
        elif steps and tiny / steps > 0.55 and len(seen) > 500:
            out.append("soft gradients")
        return out
    limit = ITEM_COLOURS if kind == "item" else BLOCK_COLOURS
    if colours > limit * scale:
        out.append(f"{colours} colours (vanilla stays under {int(limit * scale)})")
    if kind == "item" and steps and tiny / steps > GRADIENT:
        out.append("soft gradients")
    if kind == "item" and semi > SEMI:
        out.append("half-transparent pixels")
    return out


def check(paths, allow=()):
    """(failures, notes): failures for items and blocks, notes for mob skins."""
    fails, notes = [], []
    for p in paths:
        rel = str(Path(p).resolve().relative_to(ROOT)) if Path(p).is_absolute() else str(p)
        if rel in allow:
            continue
        f = faults(ROOT / rel)
        if f:
            (notes if kind_of(rel) == "entity" else fails).append(f"{rel}: {', '.join(f)}")
    return fails, notes


def main(argv):
    if argv[:1] == ["--all"]:
        counts = {}
        for src in SOURCES:
            for f in (ROOT / src).rglob("*.png"):
                if "resourcepacks/" in str(f) and "resourcepacks/vanilla_style" not in str(f):
                    continue
                r = faults(f)
                if r is None:
                    continue
                key = (src.split("/")[0] if src != SOURCES[0] else "powers") + " " + kind_of(f)
                if "resourcepacks/vanilla_style" in str(f):
                    key = "reason look " + kind_of(f)
                tot, bad = counts.get(key, (0, 0))
                counts[key] = (tot + 1, bad + bool(r))
        for k, (tot, bad) in sorted(counts.items()):
            print(f"{k:24} {bad:5} of {tot:5} look script-made")
        return 0
    fails, notes = check(argv)
    for n in notes:
        print("note (mob skin): " + n)
    for f in fails:
        print("FAIL: " + f)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
