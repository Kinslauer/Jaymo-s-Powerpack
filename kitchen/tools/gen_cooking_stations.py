"""Art and data for the new hands-on stations and what they make: the brewing keg, the tea kettle, the sushi mat, a cup
of tea and the sandwich. Original vanilla-style 16x16 textures. Run from the repo root:
    python3 -I kitchen/tools/gen_cooking_stations.py
Writes textures, models, block states, item definitions, loot tables, recipes and the mineable tags under kitchen/src/main/resources.
"""
import json
from pathlib import Path

from PIL import Image

RES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = RES / "assets" / "jaymoskitchen"
DATA = RES / "data" / "jaymoskitchen"

OUT = (26, 18, 12, 255)
WOOD = (150, 104, 58, 255)
WOOD_LIGHT = (176, 128, 74, 255)
WOOD_DARK = (110, 76, 42, 255)
IRON = (150, 154, 162, 255)
IRON_LIGHT = (196, 200, 208, 255)
IRON_DARK = (90, 94, 104, 255)
COPPER = (184, 108, 66, 255)
COPPER_LIGHT = (214, 140, 92, 255)
BAMBOO = (206, 188, 112, 255)
BAMBOO_DARK = (160, 142, 78, 255)
CORD = (150, 60, 50, 255)
BREAD = (214, 164, 88, 255)
BREAD_DARK = (170, 120, 56, 255)
LETTUCE = (96, 160, 64, 255)
TOMATO = (200, 56, 48, 255)
CHEESE = (236, 200, 84, 255)
CUP = (232, 228, 214, 255)
TEA = (150, 96, 44, 255)


def canvas(fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), fill)


def px(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c)


def rect(img, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(img, x, y, c)


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def keg_side():
    img = canvas(WOOD)
    for x in range(0, 16, 4):
        rect(img, x, 0, x, 15, WOOD_DARK)
    for x in range(2, 16, 4):
        rect(img, x, 0, x, 15, WOOD_LIGHT)
    for y in (2, 3, 12, 13):
        rect(img, 0, y, 15, y, IRON)
    for y in (2, 12):
        rect(img, 0, y, 15, y, IRON_LIGHT)
    return img


def keg_top():
    img = canvas(WOOD_DARK)
    rect(img, 1, 1, 14, 14, WOOD)
    for i in range(2, 14):
        px(img, i, 7, WOOD_DARK)
        px(img, i, 8, WOOD_LIGHT)
    rect(img, 6, 6, 9, 9, IRON)
    rect(img, 7, 7, 8, 8, IRON_DARK)
    return img


def kettle_side():
    img = canvas(COPPER)
    for y in range(16):
        for x in range(16):
            if (x + y) % 5 == 0:
                px(img, x, y, COPPER_LIGHT)
    rect(img, 0, 0, 15, 1, IRON)
    rect(img, 0, 14, 15, 15, IRON_DARK)
    return img


def kettle_top():
    img = canvas(IRON_DARK)
    rect(img, 2, 2, 13, 13, (60, 44, 36, 255))
    rect(img, 4, 4, 11, 11, (40, 28, 24, 255))
    return img


def mat():
    img = canvas(BAMBOO)
    for x in range(0, 16, 2):
        rect(img, x, 0, x, 15, BAMBOO_DARK)
    for y in (3, 12):
        rect(img, 0, y, 15, y, CORD)
    return img


def tea_item():
    img = canvas()
    rect(img, 4, 6, 11, 12, CUP)
    rect(img, 5, 7, 10, 8, TEA)
    rect(img, 12, 7, 12, 10, CUP)
    px(img, 13, 8, CUP)
    px(img, 13, 9, CUP)
    rect(img, 4, 13, 11, 13, IRON_LIGHT)
    for x, y in ((6, 3), (7, 2), (9, 3), (8, 4)):
        px(img, x, y, (230, 230, 230, 160))
    return outline(img)


def sandwich_item():
    img = canvas()
    rect(img, 2, 4, 13, 6, BREAD)
    rect(img, 2, 4, 13, 4, BREAD_DARK)
    rect(img, 2, 7, 13, 7, LETTUCE)
    rect(img, 3, 8, 12, 8, TOMATO)
    rect(img, 2, 9, 13, 9, CHEESE)
    rect(img, 2, 10, 13, 12, BREAD)
    rect(img, 2, 12, 13, 12, BREAD_DARK)
    return outline(img)


def outline(img):
    src = img.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        px(img, x, y, OUT)
                        break
    return img


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def block_model(name, parent_elements, textures):
    write_json(ASSETS / "models" / "block" / f"{name}.json", {"parent": "minecraft:block/block", "textures": textures, "elements": parent_elements})
    write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"jaymoskitchen:block/{name}"}}})
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:block/{name}"}})
    write_json(DATA / "loot_table" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": f"jaymoskitchen:{name}"}], "rolls": 1}],
        "random_sequence": f"jaymoskitchen:blocks/{name}",
    })


def cuboid(frm, to, side, top, bottom=None):
    bottom = bottom or top
    return {"from": frm, "to": to, "faces": {
        "north": {"texture": side}, "east": {"texture": side}, "south": {"texture": side}, "west": {"texture": side},
        "up": {"texture": top}, "down": {"texture": bottom}}}


def item_model(name):
    write_json(ASSETS / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskitchen:item/{name}"}})
    write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskitchen:item/{name}"}})


def recipe(name, pattern, key, count=1):
    write_json(DATA / "recipe" / f"{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
                                                  "result": {"id": f"jaymoskitchen:{name}", "count": count}})


def add_tag(tag, ids):
    path = RES / "data" / "minecraft" / "tags" / "block" / "mineable" / f"{tag}.json"
    obj = json.loads(path.read_text())
    for i in ids:
        if i not in obj["values"]:
            obj["values"].append(i)
    write_json(path, obj)


def main():
    tex = ASSETS / "textures"
    save(keg_side(), tex / "block" / "brewing_keg_side.png")
    save(keg_top(), tex / "block" / "brewing_keg_top.png")
    save(kettle_side(), tex / "block" / "tea_kettle_side.png")
    save(kettle_top(), tex / "block" / "tea_kettle_top.png")
    save(mat(), tex / "block" / "sushi_mat.png")
    save(tea_item(), tex / "item" / "tea.png")
    save(sandwich_item(), tex / "item" / "sandwich.png")

    block_model("brewing_keg", [cuboid([2, 0, 2], [14, 14, 14], "#side", "#top")],
                {"side": "jaymoskitchen:block/brewing_keg_side", "top": "jaymoskitchen:block/brewing_keg_top", "particle": "jaymoskitchen:block/brewing_keg_side"})
    block_model("tea_kettle", [
        cuboid([3, 0, 3], [13, 8, 13], "#side", "#top"),
        cuboid([5, 8, 5], [11, 9, 11], "#side", "#top"),
        {"from": [13, 3, 7], "to": [15, 5, 9], "faces": {f: {"texture": "#side"} for f in ("north", "east", "south", "west", "up", "down")}},
        {"from": [7, 9, 7], "to": [9, 11, 9], "faces": {f: {"texture": "#top"} for f in ("north", "east", "south", "west", "up", "down")}},
    ], {"side": "jaymoskitchen:block/tea_kettle_side", "top": "jaymoskitchen:block/tea_kettle_top", "particle": "jaymoskitchen:block/tea_kettle_side"})
    block_model("sushi_mat", [cuboid([1, 0, 1], [15, 1, 15], "#mat", "#mat")],
                {"mat": "jaymoskitchen:block/sushi_mat", "particle": "jaymoskitchen:block/sushi_mat"})
    item_model("tea")
    item_model("sandwich")

    recipe("brewing_keg", ["PNP", "P P", "PNP"], {"P": "#minecraft:planks", "N": "minecraft:iron_nugget"})
    recipe("tea_kettle", [" S ", "I I", "III"], {"S": "minecraft:stick", "I": "minecraft:iron_ingot"})
    recipe("sushi_mat", ["BBB", "BBB"], {"B": "minecraft:bamboo"})
    add_tag("axe", ["jaymoskitchen:brewing_keg", "jaymoskitchen:sushi_mat"])
    add_tag("pickaxe", ["jaymoskitchen:tea_kettle"])


if __name__ == "__main__":
    main()
