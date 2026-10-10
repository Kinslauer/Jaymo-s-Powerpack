"""The Void Flintlock's art (jaymospowers:flintlock): an original void-tinted palette (ebony stock, violet gunmetal,
amethyst fittings, a pan lit with void fire), nothing taken from WeaponMod.

- textures/item/flintlock.png, flintlock_spent.png: the 32x32 inventory icons (side view; spent = hammer down, pan
  burnt out).
- textures/item/flintlock_held.png, flintlock_held_spent.png: 16x16 sprites laid out like our held_guns flintlock
  (grip bottom, muzzle top left), voxelised into models/item/flintlock_held.json and flintlock_held_spent.json:
  every opaque pixel becomes a small block, the stock thicker than the metal, so the gun has body in the hand.
- items/flintlock.json: a has_component condition on jaymospowers:spent, each side a display_context select (flat
  icon in the GUI, on the ground, in frames and on shelves; 3D in hand).

Run from the project root:  python -I tools/gen_flintlock.py
"""
import json

from PIL import Image, ImageDraw

ROOT = "src/main/resources/assets/jaymospowers"

# the void palette
EBONY = (42, 22, 52)
EBONY_GRAIN = (82, 44, 98)
GUNMETAL = (58, 50, 82)
GUNMETAL_HI = (128, 110, 170)
AMETHYST = (176, 120, 230)
AMETHYST_DARK = (112, 64, 160)
HAMMER = (34, 30, 44)
PAN_LIT = (236, 110, 255)
PAN_BURNT = (74, 44, 82)
EDGE = (20, 10, 26)

# ---- the 32x32 inventory icons ----


def outline(img):
    out = img.copy()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if img.getpixel((x, y))[3]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < w and 0 <= ny < h and img.getpixel((nx, ny))[3]:
                    out.putpixel((x, y), (*EDGE, 255))
                    break
    return out


def icon(spent):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(6, 12), (27, 12), (27, 15), (6, 15)], fill=GUNMETAL)               # barrel
    d.line([(6, 12), (27, 12)], fill=GUNMETAL_HI)
    for x in range(9, 26, 4):                                                      # faint violet runes on the barrel
        img.putpixel((x, 14), (*AMETHYST_DARK, 255))
    d.rectangle([(26, 11), (28, 16)], fill=AMETHYST)                               # muzzle ring
    d.polygon([(4, 15), (16, 15), (16, 18), (10, 18), (6, 27), (1, 26), (4, 17)], fill=EBONY)  # stock and grip
    d.line([(6, 18), (2, 25)], fill=EBONY_GRAIN)
    d.line([(8, 16), (14, 16)], fill=EBONY_GRAIN)
    d.ellipse([(0, 24), (5, 28)], fill=AMETHYST)                                   # pommel cap
    img.putpixel((2, 25), (*AMETHYST_DARK, 255))
    d.polygon([(9, 18), (13, 18), (12, 22), (10, 22)], outline=AMETHYST)           # trigger guard
    d.line([(11, 18), (11, 20)], fill=HAMMER)                                      # trigger
    if spent:
        d.polygon([(13, 10), (16, 11), (16, 12), (12, 12)], fill=HAMMER)           # hammer down on the pan
        d.rectangle([(15, 12), (17, 12)], fill=PAN_BURNT)
    else:
        d.polygon([(12, 8), (15, 10), (14, 11), (11, 9)], fill=HAMMER)             # hammer cocked
        d.rectangle([(15, 11), (17, 12)], fill=PAN_LIT)                            # the pan, void-lit
    return outline(img)


# ---- the 16x16 held sprites and their voxel models ----
# m muzzle, b/B barrel, o dark edge, w/W stock, h hammer, p pan (lit), q pan (burnt), l lock plate,
# t trigger, c butt cap
HELD = [
    "................",
    "................",
    "....m...........",
    "....oB..........",
    "....bBo.........",
    ".....wob........",
    ".....WwbB.h.....",
    "......wWbpl.....",
    ".......wWl......",
    "......ttwWo.....",
    "........Wwo.....",
    ".......wWwo.....",
    ".......cwWo.....",
    "........cc......",
    "................",
    "................",
]
# spent: the hammer has fallen onto the pan, which has burnt out
HELD_SPENT = list(HELD)
HELD_SPENT[6] = ".....WwbB......."
HELD_SPENT[7] = "......wWbqh....."

COLOURS = {"m": AMETHYST, "b": GUNMETAL, "B": GUNMETAL_HI, "o": EDGE, "w": EBONY, "W": EBONY_GRAIN, "h": HAMMER,
           "p": PAN_LIT, "q": PAN_BURNT, "l": GUNMETAL, "t": AMETHYST, "c": AMETHYST_DARK}
# thickness (pixels) of each kind of pixel, all centred on z = 8
THICKNESS = {"w": 3.0, "W": 3.0, "o": 2.5, "c": 2.5, "b": 2.0, "B": 2.0, "m": 2.0, "l": 2.0, "t": 1.5, "p": 1.5,
             "q": 1.5, "h": 1.5}
# the held_guns flintlock's third-person holds (our own JSON). Loaded, the barrel runs along the arm, which
# ArsenalArmPose raises level with the look; spent, it's held barrel-forward in a lowered hand.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [10, -90, 55], "translation": [0, 2.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [10, 90, -55], "translation": [0, 2.0, 0.5], "scale": [0.85, 0.85, 0.85]},
}
DISPLAY_SPENT = {
    "thirdperson_righthand": {"rotation": [40, -40, 65], "translation": [-2.5, 2.0, 1.2], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [40, 140, -65], "translation": [-2.5, 2.0, 1.2], "scale": [0.85, 0.85, 0.85]},
}


def held_texture(rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), (*COLOURS[ch], 255))
    return img


def voxels(rows):
    elements = []
    for y, row in enumerate(rows):
        x = 0
        while x < 16:
            ch = row[x]
            if ch == ".":
                x += 1
                continue
            start = x
            depth = THICKNESS[ch]
            while x < 16 and row[x] != "." and THICKNESS[row[x]] == depth:
                x += 1
            z0, z1 = 8 - depth / 2, 8 + depth / 2
            faces = {
                "south": {"uv": [start, y, x, y + 1], "texture": "#gun"},
                "north": {"uv": [x, y, start, y + 1], "texture": "#gun"},
                "up": {"uv": [start, y, x, y + 1], "texture": "#gun"},
                "down": {"uv": [start, y, x, y + 1], "texture": "#gun"},
                "west": {"uv": [start, y, start + 1, y + 1], "texture": "#gun"},
                "east": {"uv": [x - 1, y, x, y + 1], "texture": "#gun"},
            }
            elements.append({"from": [start, 16 - (y + 1), z0], "to": [x, 16 - y, z1], "faces": faces})
    return elements


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)


def by_context(flat, solid):
    return {"type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": flat}}],
            "fallback": {"type": "minecraft:model", "model": solid}}


def main():
    for spent, suffix in ((False, ""), (True, "_spent")):
        icon(spent).save(f"{ROOT}/textures/item/flintlock{suffix}.png")
        write_json(f"{ROOT}/models/item/flintlock{suffix}.json",
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": f"jaymospowers:item/flintlock{suffix}"}})
        rows = HELD_SPENT if spent else HELD
        held_texture(rows).save(f"{ROOT}/textures/item/flintlock_held{suffix}.png")
        texture = f"jaymospowers:item/flintlock_held{suffix}"
        write_json(f"{ROOT}/models/item/flintlock_held{suffix}.json", {
            "parent": "minecraft:item/handheld",
            "textures": {"gun": texture, "particle": texture},
            "gui_light": "front",
            "elements": voxels(rows),
            "display": DISPLAY_SPENT if spent else DISPLAY,
        })
    write_json(f"{ROOT}/items/flintlock.json", {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:has_component",
        "component": "jaymospowers:spent",
        "on_false": by_context("jaymospowers:item/flintlock", "jaymospowers:item/flintlock_held"),
        "on_true": by_context("jaymospowers:item/flintlock_spent", "jaymospowers:item/flintlock_held_spent"),
    }})
    print("flintlock drawn:", len(voxels(HELD)), "elements loaded,", len(voxels(HELD_SPENT)), "spent")


main()
