"""Generates the art for a part the symbiote has corded back (WebSymbioteMend): it stays the symbiote's colour, then
fades back to the skin (client WebSymbioteMendLayer).

Cut from our own suit art (tools/gen_web.py, gen_web_strains.py; run those first if they change), nothing else copied:
  textures/entity/web_symbiote_mend_<right_arm|left_arm|right_leg|left_leg>.png  just that limb's skin regions (base
                                               and outer layer) of web_symbiote_suit.png, the rest clear
  ..._white.png / _red.png                     the same, from the white and red suits (client WebStrainLook.texture)
And painted here:
  textures/entity/web_symbiote_mend_eyes.png (+ _white, _red)  solid eyes in the stock eye spots (face row 4), with a
                                               wet highlight: for a player MCA doesn't draw (MCA's own eye pixels
                                               are used where it does).
Run from the project root:  python3 -I tools/gen_web_mend.py
"""
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
ENTITY = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"

# the player-skin regions (64x64): base, then outer layer
LIMBS = {
    "right_arm": ((40, 16, 56, 32), (40, 32, 56, 48)),
    "left_arm": ((32, 48, 48, 64), (48, 48, 64, 64)),
    "right_leg": ((0, 16, 16, 32), (0, 32, 16, 48)),
    "left_leg": ((16, 48, 32, 64), (0, 48, 16, 64)),
}
STRAINS = {"": "", "_white": "_white", "_red": "_red"}
# eye colour, highlight per strain
EYES = {"": ((14, 12, 16), (70, 66, 78)), "_white": ((232, 229, 220), (255, 255, 255)), "_red": ((110, 12, 20), (190, 60, 64))}
# the stock eye spots: two pixels wide each, at face row 4 (y 12), with a row above and below
EYE_X = (9, 10, 13, 14)


def limbs():
    for suffix in STRAINS:
        suit = Image.open(ENTITY / f"web_symbiote_suit{suffix}.png").convert("RGBA")
        for name, regions in LIMBS.items():
            out = Image.new("RGBA", suit.size, (0, 0, 0, 0))
            for x0, y0, x1, y1 in regions:
                out.paste(suit.crop((x0, y0, x1, y1)), (x0, y0))
            out.save(ENTITY / f"web_symbiote_mend_{name}{suffix}.png")


def eyes():
    for suffix, (colour, shine) in EYES.items():
        out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for x in EYE_X:
            out.putpixel((x, 11), colour + (200,))
            out.putpixel((x, 12), colour + (255,))
            out.putpixel((x, 13), colour + (150,))
        # a wet glint in the upper outer corner of each
        out.putpixel((9, 11), shine + (220,))
        out.putpixel((14, 11), shine + (220,))
        out.save(ENTITY / f"web_symbiote_mend_eyes{suffix}.png")


if __name__ == "__main__":
    limbs()
    eyes()
