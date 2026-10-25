"""Generates thirst and body-heat assets for Jaymo's Kitchen (0.5.25): the waterskin, clean water's item model and
the thirst bar's droplets. Hand-drawn pixel grids (one char per pixel), so no vanilla or other mod's art is copied.
Run from the kitchen root:  python -I tools/gen_climate.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskitchen"

PALETTE = {
    ".": (0, 0, 0, 0),
    "k": (44, 26, 14, 255),     # outline
    "l": (150, 96, 52, 255),    # leather
    "L": (112, 68, 34, 255),    # leather shadow
    "h": (186, 130, 78, 255),   # leather highlight
    "s": (214, 196, 160, 255),  # stitching / cord
    "c": (120, 84, 50, 255),    # cork
    "C": (88, 58, 32, 255),     # cork shadow
    # droplets
    "o": (20, 34, 64, 255),     # droplet outline
    "b": (64, 140, 230, 255),   # water
    "B": (40, 100, 190, 255),   # water shadow
    "w": (200, 236, 255, 255),  # shine
    "e": (40, 44, 52, 200),     # empty inside
    "m": (128, 120, 72, 255),   # murky water
    "M": (96, 88, 50, 255),     # murky shadow
}

WATERSKIN = [
    "................",
    "......kk........",
    ".....kcck.......",
    ".....kCck.......",
    "......kk..sss...",
    ".....kssk....s..",
    "....kllllk....s.",
    "...klhhllLk...s.",
    "..klhlllllLk..s.",
    "..klhllllllLks..",
    ".klhlllllllLLk..",
    ".kllllllllllLk..",
    ".kLlllsslllLLk..",
    "..kLLlllllLLk...",
    "...kkLLLLLkk....",
    ".....kkkkk......",
]

# 9x9 droplets for the thirst bar (two halves of one drop are drawn from the same grid)
DROP_FULL = [
    "....o....",
    "...obo...",
    "..obbbo..",
    ".obwbbbo.",
    ".obwbbbo.",
    "obbbbbBBo",
    "obbbbBBBo",
    ".oBBBBBo.",
    "..ooooo..",
]
DROP_EMPTY = [
    "....o....",
    "...oeo...",
    "..oeeeo..",
    ".oeeeeeo.",
    ".oeeeeeo.",
    "oeeeeeeeo",
    "oeeeeeeeo",
    ".oeeeeeo.",
    "..ooooo..",
]
DROP_MURKY = [row.replace("b", "m").replace("B", "M").replace("w", "m") for row in DROP_FULL]


def render(rows):
    img = Image.new("RGBA", (len(rows[0]), len(rows)))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def half(full, empty):
    """The left half of a full drop over an empty one: half a point of water."""
    img = render(empty)
    left = render(full).crop((0, 0, 5, 9))
    img.paste(left, (0, 0))
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    item_dir = ROOT / "textures" / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
    render(WATERSKIN).save(item_dir / "waterskin.png")
    write_json(ROOT / "models" / "item" / "waterskin.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskitchen:item/waterskin"}})
    write_json(ROOT / "items" / "waterskin.json",
               {"model": {"type": "minecraft:model", "model": "jaymoskitchen:item/waterskin"}})
    # clean water: vanilla's potion bottle (so it follows resource packs) with its water a clear pale blue
    write_json(ROOT / "items" / "clean_water.json", {"model": {"type": "minecraft:model", "model": "minecraft:item/potion",
                                                               "tints": [{"type": "minecraft:constant", "value": 0xFF9AD8FF - (1 << 32)}]}})

    hud = ROOT / "textures" / "gui" / "sprites" / "hud"
    hud.mkdir(parents=True, exist_ok=True)
    render(DROP_FULL).save(hud / "thirst_full.png")
    render(DROP_EMPTY).save(hud / "thirst_empty.png")
    half(DROP_FULL, DROP_EMPTY).save(hud / "thirst_half.png")
    render(DROP_MURKY).save(hud / "thirst_murky_full.png")
    half(DROP_MURKY, DROP_EMPTY).save(hud / "thirst_murky_half.png")
    print(f"wrote climate assets to {ROOT}")


if __name__ == "__main__":
    main()
