"""Draws and writes Jaymo's Kingdoms' whaling assets (0.7.206), all original and procedural:

- textures/entity/whale/rorqual.png and sperm.png (128x128): the great whale's two skins, painted face by face onto the
  UVs of GreatWhaleModel's boxes (keep BOXES in step with the model): dark backs fading to pale bellies, throat grooves
  under the rorqual's jaw and barnacle specks on its head, the sperm whale wrinkled and brown-grey all over.
- textures/entity/whale/harpoon.png (64x32): the thrown harpoon's ash shaft and iron head (HarpoonRenderer's boxes).
- textures/item/: harpoon, whale_blubber, whale_meat, cooked_whale_meat, whale_bone, ambergris, great_whale_spawn_egg;
  and their items/ and models/item/ JSON.

Seeded, so every run draws the same pixels. Run from kingdoms/: python3 -I tools/gen_whaling.py"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymoskingdoms")

# GreatWhaleModel's boxes: name -> (u, v, w, h, d)
BOXES = {
    "body": (0, 0, 12, 12, 22),
    "head": (0, 34, 10, 8, 15),
    "jaw": (0, 60, 9, 3, 14),
    "fin": (56, 46, 10, 2, 5),
    "stock": (68, 0, 8, 8, 12),
    "dorsal": (90, 46, 1, 3, 4),
    "tail": (68, 20, 5, 5, 10),
    "flukes": (56, 36, 20, 2, 7),
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def clamp(c):
    return tuple(max(0, min(255, int(round(x)))) for x in c)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, k):
    return tuple(x * k for x in c)


def faces(u, v, w, h, d):
    """The six faces of a box's UV layout: name -> (x0, y0, width, height)."""
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def paint_whale(back, belly, seed, grooves, barnacles, wrinkles):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px = img.load()
    for name, (u, v, w, h, d) in BOXES.items():
        for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    if face == "up":
                        c = back
                    elif face == "down":
                        c = belly if name in ("body", "head", "jaw", "stock") else mix(back, belly, 0.35)
                    else:
                        # sides fade from the dark back (top row) to the pale belly (bottom row)
                        t = (y + 0.5) / fh
                        t = max(0.0, (t - 0.45) / 0.55) if name in ("body", "head", "jaw", "stock") else t * 0.3
                        c = mix(back, belly, t * t)
                    n = rnd.uniform(0.9, 1.08)
                    if wrinkles and rnd.random() < 0.18:
                        n *= 0.85
                    c = shade(c, n)
                    if grooves and name == "jaw" and face == "down" and x % 2 == 0:
                        c = shade(c, 0.7)
                    if barnacles and name == "head" and face in ("up", "north") and rnd.random() < 0.07:
                        c = (205, 200, 185)
                    px[x0 + x, y0 + y] = clamp(c) + (255,)
    return img


def harpoon_texture():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(7)
    ash, iron, rope = (150, 112, 70), (120, 124, 130), (178, 150, 100)

    def fill(u, v, w, h, d, colour, grain):
        for face, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    c = shade(colour, rnd.uniform(0.85, 1.1))
                    if grain and (x + y) % 5 == 0:
                        c = shade(c, 0.85)
                    px[x0 + x, y0 + y] = clamp(c) + (255,)

    fill(0, 0, 1, 1, 24, ash, True)
    fill(0, 25, 3, 1, 5, iron, False)
    fill(20, 25, 2, 2, 2, rope, True)
    return img


def sprite(pattern, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(pattern):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch] + (255,)
    return img


ITEMS = {
    "harpoon": ([
        "............OOH.",
        "...........OOHH.",
        "..........OHHH..",
        ".........WOHO...",
        "........WWO.....",
        ".......WW.......",
        "......WW........",
        ".....WW.........",
        "....WW..........",
        "...WR...........",
        "..RWR...........",
        ".R.RRR..........",
        "R...R.R.........",
        "R..R...R........",
        ".RR....R........",
        "......R.........",
    ], {"O": (110, 114, 122), "H": (175, 180, 188), "W": (140, 100, 60), "R": (190, 160, 105)}),
    "whale_blubber": ([
        "................",
        "................",
        "....DDDDDDD.....",
        "...DGGGGGGGD....",
        "..DCCCCCCCCCD...",
        "..DCYCCCCCYCD...",
        "..DCCCCYCCCCCD..",
        "..DCCCCCCCCYCD..",
        "..DCYCCCCCCCCD..",
        "..DCCCCCYCCCCD..",
        "...DCCCCCCCCD...",
        "....DDDDDDDD....",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (90, 84, 70), "G": (60, 62, 66), "C": (236, 222, 176), "Y": (214, 196, 140)}),
    "whale_meat": ([
        "................",
        "................",
        "...DDDDDDDD.....",
        "..DRRRRRRRRD....",
        ".DRRMRRRRRRRD...",
        ".DRRRRRMRRRRRD..",
        ".DRRRRRRRRMRRD..",
        ".DRMRRRRRRRRRD..",
        ".DRRRRMRRRRRD...",
        "..DRRRRRRRRD....",
        "...DDDDDDDD.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (70, 14, 18), "R": (128, 26, 34), "M": (170, 70, 70)}),
    "cooked_whale_meat": ([
        "................",
        "................",
        "...DDDDDDDD.....",
        "..DBBBBBBBBD....",
        ".DBBLBBBBBBBD...",
        ".DBBBBBLBBBBBD..",
        ".DBBBBBBBBLBBD..",
        ".DBLBBBBBBBBBD..",
        ".DBBBBLBBBBBD...",
        "..DBBBBBBBBD....",
        "...DDDDDDDD.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (52, 28, 16), "B": (110, 62, 34), "L": (150, 96, 56)}),
    "whale_bone": ([
        "..BB............",
        ".BWWB...........",
        ".BWWWB..........",
        "..BWWWB.........",
        "...BWWWB........",
        "....BWWB........",
        ".....BWWB.......",
        "......BWWB......",
        "......BWWB......",
        ".......BWWB.....",
        "........BWWB....",
        ".........BWWB...",
        "..........BWWB..",
        "..........BWWWB.",
        "...........BWWB.",
        "............BB..",
    ], {"B": (160, 150, 128), "W": (232, 226, 206)}),
    "ambergris": ([
        "................",
        "................",
        "................",
        ".....DDDD.......",
        "....DGGGGDD.....",
        "...DGLGGGGGD....",
        "..DGGGGGAGGGD...",
        "..DGGAGGGGLGD...",
        "..DGGGGGGGGGD...",
        "...DGGLGGAGD....",
        "....DDGGGGD.....",
        "......DDDD......",
        "................",
        "................",
        "................",
        "................",
    ], {"D": (96, 88, 72), "G": (168, 160, 132), "L": (210, 202, 170), "A": (196, 160, 80)}),
    "great_whale_spawn_egg": ([
        "......DDDD......",
        ".....DGGGGD.....",
        "....DGGGGGGD....",
        "....DGPGGGGD....",
        "...DGGGGGPGGD...",
        "...DGGGGGGGGD...",
        "..DGPGGGGGGGGD..",
        "..DGGGGGGPGGGD..",
        "..DGGGGGGGGGGD..",
        "..DGGPGGGGGGGD..",
        "..DPPPPPPPPPPD..",
        "..DPPPPPPPPPPD..",
        "...DPPPPPPPPD...",
        "...DPPPPPPPPD...",
        "....DDPPPPDD....",
        "......DDDD......",
    ], {"D": (30, 34, 40), "G": (62, 70, 82), "P": (200, 204, 196)}),
}


def main():
    entity = os.path.join(ASSETS, "textures", "entity", "whale")
    os.makedirs(entity, exist_ok=True)
    paint_whale((52, 60, 70), (206, 208, 200), 1, True, True, False).save(os.path.join(entity, "rorqual.png"))
    paint_whale((70, 64, 60), (120, 112, 104), 2, False, False, True).save(os.path.join(entity, "sperm.png"))
    harpoon_texture().save(os.path.join(entity, "harpoon.png"))
    items = os.path.join(ASSETS, "textures", "item")
    for name, (pattern, palette) in ITEMS.items():
        assert len(pattern) == 16 and all(len(r) == 16 for r in pattern), name
        sprite(pattern, palette).save(os.path.join(items, name + ".png"))
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})
        parent = "minecraft:item/handheld" if name == "harpoon" else "minecraft:item/generated"
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": parent, "textures": {"layer0": "jaymoskingdoms:item/" + name}})


if __name__ == "__main__":
    main()
