"""Generates the hide-tanning assets for Jaymo's Kitchen (0.5.27): hide, raw/salted/dried/wet tanned hide, salt and
resin icons, the drying rack's models, its recipes, tags and loot table. Hand-drawn pixel grids (one char per pixel), so
no other mod's art is copied. Run from the kitchen root:  python -I tools/gen_hides.py
"""
import json
from pathlib import Path

from PIL import Image

RES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = RES / "assets" / "jaymoskitchen"
DATA = RES / "data"

# a stretched pelt: o outline, a body, b shadow, c highlight, d spot (fur tufts, salt grains, gloss)
PELT = [
    "................",
    "..oo........oo..",
    ".oaao......oaao.",
    ".oacaoooooooaao.",
    "..oaccaaaaaaabo.",
    "..oacaadaaaaabo.",
    "...oaaaaaadaabo.",
    "...oaaadaaaaabo.",
    "...oacaaaaadabo.",
    "...oaaaadaaaabo.",
    "..oaaaaaaaaaabo.",
    "..oacaadaaaabbo.",
    ".oaabooooooobbao",
    ".oaao......oaao.",
    "..oo........oo..",
    "................",
]

PELTS = {
    # fur still on
    "hide": {"o": (52, 34, 20), "a": (124, 84, 50), "b": (92, 60, 34), "c": (156, 112, 72), "d": (70, 46, 28)},
    # scraped, pink-tan flesh side
    "raw_hide": {"o": (88, 46, 40), "a": (204, 150, 132), "b": (170, 112, 98), "c": (226, 182, 164), "d": (186, 128, 112)},
    # flesh side dusted with salt
    "salted_hide": {"o": (88, 50, 44), "a": (206, 160, 146), "b": (172, 122, 108), "c": (240, 236, 230), "d": (250, 250, 248)},
    # stiff and pale
    "dried_hide": {"o": (96, 72, 46), "a": (212, 190, 146), "b": (180, 156, 112), "c": (230, 214, 178), "d": (194, 170, 124)},
    # dark, soaked with resin
    "wet_tanned_hide": {"o": (40, 24, 12), "a": (110, 62, 30), "b": (78, 42, 20), "c": (150, 96, 52), "d": (176, 124, 70)},
}

SALT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......w........",
    "......wsw.......",
    ".....wsssw..w...",
    "....wsssggw.ws..",
    "...wssggggswsgw.",
    "..wsssgggggsggw.",
    ".wsssggggggggggw",
    ".kggggggggggggk.",
    "..kkkkkkkkkkkk..",
    "................",
    "................",
]
SALT_PALETTE = {"w": (255, 255, 255), "s": (238, 236, 234), "g": (204, 200, 198), "k": (150, 146, 144)}

RESIN = [
    "................",
    "................",
    "......kkk.......",
    ".....kyyok......",
    "....kyhyook.....",
    "....kyhyyook....",
    "...kyyyyyyok....",
    "...kyyyyyyook...",
    "..kyyyyyyyyook..",
    "..kyyyyyyyyyok..",
    "..kooyyyyyyook..",
    "...koooooooook..",
    "....kkkkkkkkk...",
    "................",
    "................",
    "................",
]
RESIN_PALETTE = {"k": (90, 42, 8), "y": (226, 150, 40), "o": (176, 98, 20), "h": (255, 224, 150)}


def render(rows, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch] + (255,))
    return img


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def flat_item(name):
    write_json(ASSETS / "models" / "item" / f"{name}.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{name}"}})
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"}})


def drying_rack():
    post = "minecraft:block/stripped_spruce_log"
    pole = "minecraft:block/spruce_log"

    def cube(fr, to, tex):
        return {"from": fr, "to": to, "faces": {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}

    write_json(ASSETS / "models" / "block" / "drying_rack.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": post, "post": post, "pole": pole},
        "elements": [
            cube([0, 0, 7], [2, 16, 9], "#post"),
            cube([14, 0, 7], [16, 16, 9], "#post"),
            cube([2, 13, 7], [14, 15, 9], "#pole"),
            # feet, so it stands
            cube([0, 0, 5], [2, 1, 11], "#post"),
            cube([14, 0, 5], [16, 1, 11], "#post"),
        ],
    })
    write_json(ASSETS / "blockstates" / "drying_rack.json", {"variants": {
        f"facing={facing}": ({"model": "jaymoskitchen:block/drying_rack", "y": y} if y else {"model": "jaymoskitchen:block/drying_rack"})
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    write_json(ASSETS / "items" / "drying_rack.json", {"model": {"type": "minecraft:model", "model": "jaymoskitchen:block/drying_rack"}})
    write_json(DATA / "jaymoskitchen" / "loot_table" / "blocks" / "drying_rack.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "jaymoskitchen:drying_rack"}], "rolls": 1}],
        "random_sequence": "jaymoskitchen:blocks/drying_rack"})


def recipe(name, data):
    write_json(DATA / "jaymoskitchen" / "recipe" / f"{name}.json", data)


def blade(name, ingredients, result, count=1):
    recipe(name, {"type": "jaymoskitchen:blade_crafting", "category": "misc", "ingredients": ingredients,
                  "result": {"id": result, "count": count}})


def recipes():
    blade("raw_hide", ["jaymoskitchen:hide", "#jaymoskitchen:work_blades"], "jaymoskitchen:raw_hide", 4)
    blade("raw_hide_from_rabbit_hide", ["minecraft:rabbit_hide", "#jaymoskitchen:work_blades"], "jaymoskitchen:raw_hide")
    blade("salted_hide", ["jaymoskitchen:raw_hide", "jaymoskitchen:raw_hide", "jaymoskitchen:salt", "#jaymoskitchen:tanning_water"],
          "jaymoskitchen:salted_hide")
    blade("wet_tanned_hide", ["jaymoskitchen:dried_hide", "jaymoskitchen:dried_hide", "jaymoskitchen:resin", "#jaymoskitchen:tanning_water"],
          "jaymoskitchen:wet_tanned_hide", 2)
    # fleece: wool cut up with a blade
    blade("string_from_wool", ["#minecraft:wool", "#jaymoskitchen:work_blades"], "minecraft:string", 2)
    recipe("drying_rack", {"type": "minecraft:crafting_shaped", "category": "misc",
                           "pattern": ["SCS", "S S", "S S"],
                           "key": {"S": "minecraft:stick", "C": "#jaymoskitchen:cords"},
                           "result": {"id": "jaymoskitchen:drying_rack"}})
    # a stone-age waterskin from raw hide, so the first leather can be made before there's any
    recipe("waterskin_from_raw_hide", {"type": "minecraft:crafting_shaped", "category": "misc",
                                       "pattern": [" s ", "r r", " r "],
                                       "key": {"s": "#jaymoskitchen:cords", "r": "jaymoskitchen:raw_hide"},
                                       "result": {"id": "jaymoskitchen:waterskin"}})
    # rabbit hides go through the tanning too
    write_json(DATA / "minecraft" / "recipe" / "leather.json", {"fabric:load_conditions": [{"condition": "fabric:false"}]})


def tags():
    tag = DATA / "jaymoskitchen" / "tags" / "item"
    write_json(tag / "work_blades.json", {"values": [
        {"id": "jaymospowers:primitive_knife", "required": False},
        {"id": "jaymospowers:bone_knife", "required": False},
        {"id": "#jaymospowers:fiber_knives", "required": False},
        {"id": "#c:tools/knife", "required": False}]})
    write_json(tag / "tanning_water.json", {"values": ["jaymoskitchen:waterskin", "minecraft:water_bucket"]})
    write_json(tag / "cords.json", {"values": ["minecraft:string", {"id": "jaymospowers:plant_string", "required": False}]})


def main():
    item_dir = ASSETS / "textures" / "item"
    item_dir.mkdir(parents=True, exist_ok=True)
    for name, palette in PELTS.items():
        render(PELT, palette).save(item_dir / f"{name}.png")
        flat_item(name)
    render(SALT, SALT_PALETTE).save(item_dir / "salt.png")
    flat_item("salt")
    render(RESIN, RESIN_PALETTE).save(item_dir / "resin.png")
    flat_item("resin")
    drying_rack()
    recipes()
    tags()
    print(f"wrote hide assets to {RES}")


if __name__ == "__main__":
    main()
