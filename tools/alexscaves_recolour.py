"""Recolours Alex's Caves' scarlet and azure neodymium textures into the Aether's magnetic palette (Andy, 2026-11-24:
"yes bring it back with our theme"). Alex's Caves Continued is LGPL-3.0 (COPYING and COPYING.LESSER in its jar, no
separate art licence); these recoloured files are published here with the rest of the pack's source.

Each pixel keeps its alpha and its brightness. Coloured pixels take the matching shade of our ramp: scarlet becomes
the rust of ferrosite sand, azure the slate blue of the magnetic shrooms. Grey pixels (Alex's galena) take ferrosite's
greys. Run with the Alex's Caves jar:  python3 tools/alexscaves_recolour.py alexscaves-1.1.3-fabric+26.3.jar
"""
import colorsys
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/alexscaves/textures"
# darkest to lightest, from our textures (block/aether/ferrosite_sand, magnetic_shroom, ferrosite)
RUST = ["#3a1c0e", "#5e2f18", "#7d3f20", "#974e2a", "#a95c33", "#b96a3d", "#c97b4c", "#dca070", "#f0c79a"]
SLATE = ["#141e29", "#1b2836", "#243548", "#2b4058", "#3d5a78", "#4d7192", "#5c86ad", "#7fb0d8", "#bfe6ff"]
GREY = ["#2e2723", "#463c37", "#574c46", "#6d615b", "#7f726b", "#8f8179", "#9f9189", "#b8aaa2", "#d3c7bf"]
# a pixel this saturated or more counts as coloured
COLOURED = 0.22


def ramp(hexes):
    return [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes]


def shade(r, light):
    t = max(0.0, min(1.0, light)) * (len(r) - 1)
    i = min(int(t), len(r) - 2)
    f = t - i
    return tuple(round(r[i][k] + (r[i + 1][k] - r[i][k]) * f) for k in range(3))


def coloured(r, g, b):
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    # very dark pixels are the outlines of the coloured parts
    return s >= COLOURED or l < 0.12


def recolour(im, colour):
    """Brightness is stretched over the texture's own darkest and lightest pixels of each kind, so the shading keeps its contrast."""
    im = im.convert("RGBA")
    px = im.load()
    light = {}
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a:
                light[x, y] = (coloured(r, g, b), colorsys.rgb_to_hls(r / 255, g / 255, b / 255)[1])
    span = {}
    for kind in (True, False):
        ls = [l for c, l in light.values() if c == kind]
        span[kind] = (min(ls), max(ls)) if ls else (0.0, 1.0)
    for (x, y), (c, l) in light.items():
        lo, hi = span[c]
        t = (l - lo) / (hi - lo) if hi > lo else 0.5
        px[x, y] = shade(colour if c else GREY_R, (0.04 + 0.72 * t) if c else (0.15 + 0.6 * t)) + (px[x, y][3],)
    return im


RUST_R, SLATE_R, GREY_R = ramp(RUST), ramp(SLATE), ramp(GREY)


def main(jar):
    done = 0
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if not name.startswith("assets/alexscaves/textures/") or not name.endswith(".png"):
                continue
            base = name.rsplit("/", 1)[1]
            if "scarlet" in base:
                colour = RUST_R
            elif "azure" in base:
                colour = SLATE_R
            else:
                continue
            out = OUT / name[len("assets/alexscaves/textures/"):]
            out.parent.mkdir(parents=True, exist_ok=True)
            recolour(Image.open(io.BytesIO(z.read(name))), colour).save(out)
            meta = name + ".mcmeta"
            if meta in z.namelist():
                (out.parent / (out.name + ".mcmeta")).write_bytes(z.read(meta))
            done += 1
    print(f"recoloured {done} textures into {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
