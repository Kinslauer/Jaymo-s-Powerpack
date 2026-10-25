"""Draws the flint hatchet (the Armory's hand_axe, 0.16.17, our own art; 0.16.18 a size smaller) and writes its model. A short stick haft with a
knapped flint head lashed on: the head dark like vanilla's flint, its cutting edge chipped pale along the top-left, the
plant-string binding wound round head and haft in light and shaded turns, the haft's end poking out above the binding.
Painted by hand on a shade grid on vanilla's own ramps (flint, stick, our plant string), lit from the top-left like
vanilla's items. Run it from the root:

    python -I tools/gen_flint_hatchet.py
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NAME = "hand_axe"


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# k-w: the flint, darkest to lightest (vanilla flint.png); 0-3: the haft (vanilla stick.png); s-v: the plant-string
# binding, lightest to darkest
SHADES = dict(zip("kdmnglw", map(rgb, ("0e0e0e", "222020", "2e2d2d", "3d3c3c", "565656", "7f7f7f", "a8a8a8"))))
SHADES.update(zip("0123", map(rgb, ("493615", "281e0b", "896727", "684e1e"))))
SHADES.update(zip("suv", map(rgb, ("c4ba78", "6e6438", "3a3320"))))

GRID = (
    "................",
    "................",
    "......kkk.......",
    ".....kwlnk..01..",
    "....kwgnmdk021..",
    "....kwngmsusv...",
    "...kwlnmsusv....",
    "...klgmsusv.....",
    "....kkd031......",
    "......021.......",
    ".....031........",
    "....021.........",
    "...031..........",
    "...11...........",
    "................",
    "................",
)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(GRID):
        for x, ch in enumerate(row):
            if ch in SHADES:
                im.putpixel((x, y), SHADES[ch] + (255,))
    im.save(os.path.join(ASSETS, "textures", "item", "armory", NAME + ".png"))
    write(os.path.join(ASSETS, "models", "item", "armory", NAME + ".json"),
          {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/armory/" + NAME}})


if __name__ == "__main__":
    main()
