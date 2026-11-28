"""Draws the Cordon's outfits as MCA clothing (Uniforms.cordon): the Cordon are MCA guards now, each in their rank's ash
robes from the old Cordon skins (tools/gen_awakening_assets.py, cordon_skin), with the head left bare for the villager's
own face and hair and the black band of the blindfolded moved onto the hat layer, over their eyes. No skin is painted,
so each villager's own skin shows at the hands. Four of each rank, a shade apart (Andy: "each outfit needs at least 4
variations so they dont all look the same"). Original art.

Run from the repository root: python3 -I kingdoms/tools/gen_cordon_outfits.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import gen_awakening_assets as aw  # noqa: E402

OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "skins", "clothing", "cordon")
RANKS = ("sentinel", "stalker", "enforcer", "overseer", "arbiter")
# each variant's shade of the robe (a share toward black, or toward white when negative)
SHADES = (0.0, 0.08, -0.08, 0.15)
HOODS = {"stalker": (0x3A, 0x37, 0x34), "overseer": (0x6A, 0x22, 0x26), "arbiter": (0x18, 0x16, 0x18)}


def shade(c, s):
    return aw.mix(c, (0, 0, 0), s) if s >= 0 else aw.mix(c, (255, 255, 255), -s)


def outfit(rank, s):
    """{rank}'s robes, shaded {s}, as MCA clothing."""
    skin = aw.cordon_skin(rank)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    src, px = skin.load(), img.load()
    for y in range(64):
        for x in range(64):
            r, g, b, a = src[x, y]
            if a == 0 or y < 16 and x < 32 or (r, g, b) == aw.SKIN:
                continue  # the head (their own face and hair) and any bare skin stay theirs
            px[x, y] = tuple(shade((r, g, b), s)) + (255,)

    # the hat layer: the rank's hood (if it has one), and the black band of the blindfolded round the head
    hood = HOODS.get(rank)

    def hat(face, x, y, w, h):
        if face in ("front", "right", "left", "back") and y in (3, 4):
            return aw.BAND
        if hood is None:
            return None
        if face == "bottom" or face == "front" and y >= 2 and 1 <= x <= w - 2:
            return None
        if face in ("right", "left") and y >= 7:
            return None
        return aw.grain(shade(hood, s), x, y, 8)

    aw.paint(px, "hat", hat)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.new("RGBA", (64 * len(SHADES), 64 * len(RANKS)), (0, 0, 0, 0))
    for row, rank in enumerate(RANKS):
        for i, s in enumerate(SHADES):
            img = outfit(rank, s)
            path = os.path.join(OUT, "%s_%d.png" % (rank, i))
            img.save(path)
            sheet.paste(img, (64 * i, 64 * row))
            print("drew", path)
    os.makedirs(os.path.join(ROOT, "kingdoms", "build"), exist_ok=True)
    sheet.save(os.path.join(ROOT, "kingdoms", "build", "cordon-outfits-sheet.png"))


if __name__ == "__main__":
    main()
