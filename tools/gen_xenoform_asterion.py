"""Asterion's body and skins: Kingdoms' minotaur ("Minotaur" by eanncrod, Blockbench Workshop, CC BY 4.0; credit and
changes in art/xenoform_asterion-CREDITS.md), turned alien.

- Bakes kingdoms/art/minotaur.bbmodel (cubes, pivots and its eight animations, unchanged) with tools/bbmodel_bake.py into
  assets/jaymospowers/bbmodels/xenoform_asterion.json, which client/power/xenoform/AsterionModel loads.
- Repaints the minotaur's own texture as the alien's skin, textures/entity/xenoform_asterion.png (same UV layout):
  the brown hide turned a dark slate, the gold trim and red eyes turned the band's green (its exact colours, so the band's
  glow repaints them, client XenoLooks), the horns and grey plate kept, and the xeno insignia on its chest.
- Paints its fallback Ultimate's plated skin and seams from that skin, the way tools/gen_xenoform.py does every alien's.

Run from the project root:  python tools/gen_xenoform_asterion.py [render.png]
(with a path, also renders the baked body in its new skin there, for checking; art/xenoform_asterion-render.png is one).
"""
import colorsys
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_xenoform as gx  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
SOURCE = REPO / "kingdoms" / "art" / "minotaur.bbmodel"
NAME = "xenoform_asterion"
# its colour (as XenoForm's 0x8A78E0): the Ultimate's seams
COLOR = (138, 120, 224)
# the hide: its brightness carried onto this cool slate (per channel, times the source's luma)
HIDE_TINT = (0.80, 0.86, 1.30)
# the grey plate pulled this far toward a blue steel
STEEL, STEEL_PULL = (120, 128, 152), 0.25
# the horns (the head's four horn cubes, every face): their bone kept as it is
HORNS = ((82, 22, 94, 36), (44, 75, 56, 89), (128, 27, 160, 39))
# the chest plate's front face (the torso's small front cube): the insignia, on a dark rim
PLATE = (131, 113, 137, 117)


def bake():
    """The minotaur baked for our loader; returns its texture (the source of the skin)."""
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([sys.executable, "-I", str(REPO / "tools" / "bbmodel_bake.py"), str(SOURCE), tmp, NAME], check=True)
        out = gx.ROOT / "bbmodels" / f"{NAME}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(Path(tmp) / "bbmodels" / f"{NAME}.json", out)
        return Image.open(Path(tmp) / "textures" / "entity" / "bb" / f"{NAME}.png").convert("RGBA").copy()


def inside(x, y, rect):
    return rect[0] <= x < rect[2] and rect[1] <= y < rect[3]


def recolour(rgb, horn):
    r, g, b = rgb
    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    deg = h * 360.0
    if horn and s <= 0.45 and v > 0.7:
        # bone: as it is
        return rgb
    if 30.0 <= deg <= 75.0 and (s > 0.45 and v > 0.35 or s > 0.2 and v > 0.6):
        # gold trim: the band's green, its highlights pale
        return gx.PALE_GREEN[:3] if v > 0.8 else gx.GREEN[:3]
    if (deg < 15.0 or deg > 345.0) and s > 0.6 and v > 0.5:
        # the red eyes
        return gx.PALE_GREEN[:3] if v > 0.8 else gx.GREEN[:3]
    if s < 0.12:
        # grey plate: a little toward blue steel
        return gx.lerp(rgb, STEEL, STEEL_PULL)[:3]
    luma = 0.3 * r + 0.59 * g + 0.11 * b
    return tuple(max(0, min(255, int(luma * k))) for k in HIDE_TINT)


def skin(src):
    out = src.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = recolour((r, g, b), any(inside(x, y, h) for h in HORNS)) + (a,)
    plate(out)
    return out


def plate(img):
    """The insignia on the chest plate's 6x4 front: the hourglass squeezed to four rows (3, 1, 1, 3), a pale heart."""
    x0, y0, x1, y1 = PLATE
    gx.rect(img, PLATE, gx.DEEP_GREEN)
    cx = (x0 + x1) // 2 - 1
    for i, w in enumerate((3, 1, 1, 3)):
        gx.rect(img, (cx - w // 2, y0 + i, cx - w // 2 + w, y0 + i + 1), gx.GREEN)
    img.putpixel((cx, y0 + 1), gx.PALE_GREEN)


def main():
    src = bake()
    gx.save(skin(src), f"entity/{NAME}.png")
    gx.ULTIMATE_COLORS["asterion"] = COLOR
    gx.ultimate("asterion")
    print("Asterion written under", gx.ROOT)
    if len(sys.argv) > 1:
        assets = gx.ROOT
        texture = assets / "textures" / "entity" / f"{NAME}.png"
        subprocess.run([sys.executable, "-I", str(REPO / "tools" / "bbmodel_preview.py"), str(assets), NAME, sys.argv[1], "idle", "0.5", str(texture)],
                       check=True)


if __name__ == "__main__":
    main()
