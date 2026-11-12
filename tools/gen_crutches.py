"""The Walking Cane and the Crutch (core/wounds/Crutches): icons, item definitions, models, recipes and lang lines.
16x16, vanilla style (docs/Vanilla-Style.md): vanilla's stick browns, the wooden leg's leather for the crutch's pad.

    python -I tools/gen_crutches.py

Both are drawn grip up (top right) and tip down (bottom left), and held in third person the other way round from a
tool: the shaft hangs from the fist to the ground (the display rotation below), the crutch's pad up under the arm.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")

# vanilla's stick, light to dark; the wooden leg's leather
PAL = {
    "a": (0x89, 0x67, 0x27), "b": (0x68, 0x4E, 0x1E), "c": (0x49, 0x36, 0x15), "d": (0x28, 0x1E, 0x0B),
    "s": (0xB0, 0x6A, 0x3C), "t": (0x8E, 0x50, 0x2A), "u": (0x6A, 0x39, 0x1C),
}

CANE = [
    "................",
    ".........ccc....",
    "........cabbd...",
    ".......cab.cbd..",
    ".......cb..cad..",
    ".......dd.cabd..",
    ".........cabd...",
    "........cabd....",
    ".......cabd.....",
    "......cabd......",
    ".....cabd.......",
    "....cabd........",
    "...cabd.........",
    "..cabd..........",
    "..dbd...........",
    "...d............",
]

CRUTCH = [
    "................",
    "..........ss....",
    ".........usst...",
    "..........ustt..",
    "...........cttu.",
    "..........cabuu.",
    ".........cabd...",
    "....cb..cabd....",
    "....dabcabd.....",
    ".....dcabd......",
    ".....cabd.......",
    "....cabd........",
    "...cabd.........",
    "..cabd..........",
    "..dbd...........",
    "...d............",
]

LANG = {
    "item.jaymospowers.walking_cane": "Walking Cane",
    "item.jaymospowers.crutch": "Crutch",
    "tooltip.jaymospowers.walking_cane": "A stout stick with a crook to lean on.",
    "tooltip.jaymospowers.crutch": "Padded under the arm. It takes more of your weight than a cane, so you walk a little faster.",
    "tooltip.jaymospowers.support.hold": "Hold it in either hand to stand and walk on one leg.",
    "message.jaymospowers.legless.one": "You can't stand on one leg. Hold a cane or a crutch, or fit a leg.",
    "message.jaymospowers.legless.both": "You have no legs to stand on. Fit a wooden or iron leg.",
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def paint(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, k in enumerate(row):
            if k != ".":
                px[x, y] = PAL[k] + (255,)
    return im


def model(name, drop, scale):
    """Handheld, but hung from the fist: the grip at the hand, the tip `drop` pixels' worth lower, toward the ground."""
    def hand(sign):
        return {"rotation": [0, -90 * sign, -55 * sign], "translation": [0, 1.0, drop], "scale": [scale, scale, scale]}
    return {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "jaymospowers:item/support/" + name},
        "display": {"thirdperson_righthand": hand(1), "thirdperson_lefthand": hand(-1)},
    }


def main():
    for name, rows, drop, scale in (("walking_cane", CANE, -5.0, 0.8), ("crutch", CRUTCH, -1.5, 1.15)):
        path = os.path.join(ASSETS, "textures", "item", "support", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        paint(rows).save(path)
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "jaymospowers:item/support/" + name}})
        write_json(os.path.join(ASSETS, "models", "item", "support", name + ".json"), model(name, drop, scale))
    write_json(os.path.join(DATA, "recipe", "walking_cane.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"P": "#minecraft:planks", "S": "minecraft:stick"},
        "pattern": ["PS", " S", " S"],
        "result": {"id": "jaymospowers:walking_cane", "count": 1},
    })
    write_json(os.path.join(DATA, "recipe", "crutch.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"L": "minecraft:leather", "S": "minecraft:stick"},
        "pattern": ["SLS", "SSS", " S "],
        "result": {"id": "jaymospowers:crutch", "count": 1},
    })
    write_json(os.path.join(ASSETS, "lang", "parts", "crutches.json"), LANG)


if __name__ == "__main__":
    main()
