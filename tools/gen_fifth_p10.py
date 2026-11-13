"""Writes the assets of patch 10 (Death and the land) for Jaymo's Kingdoms: the 16x16 textures (painted here, flat shade
ramps and light noise, nothing copied), the blockstates, models, item definitions, loot tables and recipes.

    python -I tools/gen_fifth_p10.py

Needs only Pillow. Every texture is seeded from its own name, so a rerun changes nothing. The ids must match
com.jaymoskingdoms.traps.Traps, BodyKeeping, Graves and NewSpells (the three scrolls).
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")
NS = "jaymoskingdoms"


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def put_json(path, obj):
    write(path, json.dumps(obj, indent=2) + "\n")


def noise_tex(name, ramp, kind, extra=None, speckle=None):
    """A 16x16 block texture: a ramp of shades by noise, then the look of its kind."""
    rnd = random.Random(name)
    cols = [hx(c) for c in ramp]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = rnd.random() * 0.6 + 0.4 * (0.5 + 0.5 * ((x * 7 + y * 13) % 5) / 4)
            px[x, y] = cols[min(len(cols) - 1, int(v * len(cols)))] + (255,)
    if speckle:
        sc = hx(speckle[0])
        for _ in range(speckle[1]):
            px[rnd.randrange(16), rnd.randrange(16)] = sc + (255,)
    if extra:
        extra(px, rnd)
    img.save(os.path.join(ASSETS, "textures", "block", name + ".png"))


def frost(px, rnd):
    # white fur on the top rows and in tufts
    for x in range(16):
        for y in range(rnd.randrange(2, 5)):
            px[x, y] = (226, 240, 248, 255) if rnd.random() < 0.7 else (190, 214, 232, 255)
    for _ in range(18):
        x, y = rnd.randrange(16), rnd.randrange(16)
        px[x, y] = (214, 234, 246, 255)


def pustules(color, light):
    def f(px, rnd):
        for _ in range(9):
            cx, cy = rnd.randrange(1, 15), rnd.randrange(1, 15)
            px[cx, cy] = hx(light) + (255,)
            for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1)):
                px[cx + dx, cy + dy] = hx(color) + (255,)
            px[cx, cy] = hx(light) + (255,)
    return f


def bubbles(px, rnd):
    for _ in range(7):
        cx, cy = rnd.randrange(2, 14), rnd.randrange(2, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            px[cx + dx, cy + dy] = (200, 255, 150, 255)
        px[cx, cy] = (240, 255, 215, 255)
    for x in range(16):
        px[x, 15] = (70, 130, 40, 255)


def item_img(name):
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def save_item(img, name):
    img.save(os.path.join(ASSETS, "textures", "item", name + ".png"))


def draw_vinegar():
    img = item_img("vinegar")
    px = img.load()
    glass, amber, dark, hi = hx("cfe7ee"), hx("c07a28"), hx("8a4e18"), hx("f2c46a")
    for y in range(6, 15):
        for x in range(4, 12):
            px[x, y] = amber + (255,)
    for x in range(4, 12):
        px[x, 6] = hi + (255,)
        px[x, 14] = dark + (255,)
    for y in range(6, 15):
        px[4, y] = glass + (255,)
        px[11, y] = glass + (255,)
    for y in range(2, 6):
        for x in (6, 9):
            px[x, y] = glass + (255,)
    px[7, 2] = px[8, 2] = hx("7a5030") + (255,)
    px[7, 3] = px[8, 3] = hx("7a5030") + (255,)
    px[6, 5] = px[9, 5] = glass + (255,)
    px[7, 5] = px[8, 5] = amber + (255,)
    for y in (8, 9):
        px[6, y] = hi + (255,)
    save_item(img, "vinegar")


def draw_wrap():
    img = item_img("salt_herb_wrap")
    px = img.load()
    cloth, shade, salt, herb = hx("e8e0c8"), hx("b9ae8e"), hx("ffffff"), hx("4f8a3a")
    for y in range(4, 13):
        for x in range(2, 14):
            px[x, y] = cloth + (255,)
    for x in range(2, 14):
        px[x, 12] = shade + (255,)
        px[x, 4] = shade + (255,)
    for y in range(4, 13):
        px[2, y] = shade + (255,)
        px[13, y] = shade + (255,)
    for y in range(4, 13):
        px[7, y] = hx("6a5a3a") + (255,)
        px[8, y] = hx("6a5a3a") + (255,)
    for x in range(2, 14):
        px[x, 8] = hx("6a5a3a") + (255,)
    for p in ((4, 6), (5, 7), (10, 6), (11, 7), (4, 10), (11, 10), (10, 11)):
        px[p] = herb + (255,)
    for p in ((5, 5), (10, 5), (3, 9), (12, 9), (5, 11)):
        px[p] = salt + (255,)
    px[7, 3] = px[8, 3] = herb + (255,)
    px[6, 2] = px[9, 2] = herb + (255,)
    save_item(img, "salt_herb_wrap")


def draw_scroll(name, ribbon, mark):
    img = item_img(name)
    px = img.load()
    para, edge, ink = hx("ecdcae"), hx("b79f62"), hx(ribbon)
    for y in range(3, 14):
        for x in range(3, 13):
            px[x, y] = para + (255,)
    for x in range(3, 13):
        px[x, 3] = edge + (255,)
        px[x, 13] = edge + (255,)
    for y in range(3, 14):
        px[3, y] = edge + (255,)
        px[12, y] = edge + (255,)
    for y in range(2, 15):
        px[2, y] = hx("8d6a3a") + (255,)
        px[13, y] = hx("8d6a3a") + (255,)
    for y in range(6, 9):
        for x in range(3, 13):
            px[x, y] = ink + (255,)
    for (x, y) in mark:
        px[x, y] = hx("fff6d0") + (255,)
    save_item(img, name)


def block_assets(name, textures):
    """A cube block: blockstate (a model per texture), item definition, loot table (drops itself)."""
    put_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
    put_json(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}]}],
        "random_sequence": f"{NS}:blocks/{name}"})
    models = {}
    for model, tex in textures.items():
        put_json(os.path.join(ASSETS, "models", "block", model + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{tex}"}})
        models[model] = {"model": f"{NS}:block/{model}"}
    return models


def item_assets(name):
    put_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    put_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})


def main():
    os.makedirs(os.path.join(ASSETS, "textures", "block"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    # --- the three moulds
    noise_tex("cold_mould", ["3b4a58", "4a5d6e", "5b7186", "6d869b"], "cold", frost)
    noise_tex("spore_mould", ["8a8a2a", "a39e34", "b8b345", "cfc95a"], "spore", pustules("6b6a20", "f4efa0"))
    noise_tex("spore_mould_spent", ["4d4a38", "5b5744", "6a6650", "7a755c"], "spent", None, ("383626", 10))
    noise_tex("slime_mould", ["3c8a2c", "4fa13a", "66b84a", "7fd05c"], "slime", bubbles)
    noise_tex("slime_mould_hungry", ["2c5a24", "366b2c", "407c34", "4a8c3c"], "hungry", None, ("1e3d19", 12))
    noise_tex("grave_mound", ["5a3d24", "6b4a2c", "7c5a36", "8d6b42"], "grave", None, ("3f2a19", 14))
    # --- blocks
    cold = block_assets("cold_mould", {"cold_mould": "cold_mould"})
    put_json(os.path.join(ASSETS, "blockstates", "cold_mould.json"), {"variants": {"": cold["cold_mould"]}})
    spore = block_assets("spore_mould", {"spore_mould": "spore_mould", "spore_mould_spent": "spore_mould_spent"})
    put_json(os.path.join(ASSETS, "blockstates", "spore_mould.json"), {"variants": {
        "charge=0": spore["spore_mould_spent"], "charge=1": spore["spore_mould"], "charge=2": spore["spore_mould"], "charge=3": spore["spore_mould"]}})
    slime = block_assets("slime_mould", {"slime_mould": "slime_mould", "slime_mould_hungry": "slime_mould_hungry"})
    put_json(os.path.join(ASSETS, "blockstates", "slime_mould.json"), {"variants": {
        "fed=0": slime["slime_mould_hungry"], "fed=1": slime["slime_mould_hungry"], "fed=2": slime["slime_mould"], "fed=3": slime["slime_mould"]}})
    # grave: a low mound of earth with a stake at its head
    put_json(os.path.join(ASSETS, "models", "block", "grave_mound.json"), {
        "textures": {"mound": f"{NS}:block/grave_mound", "stake": f"{NS}:block/grave_stake", "particle": f"{NS}:block/grave_mound"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 5, 15], "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#mound"} for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [7, 5, 2], "to": [9, 13, 4], "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#stake"} for d in ("north", "south", "east", "west", "up", "down")}},
            {"from": [5, 9, 2], "to": [11, 11, 4], "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#stake"} for d in ("north", "south", "east", "west", "up", "down")}}]})
    put_json(os.path.join(ASSETS, "blockstates", "grave_mound.json"), {"variants": {"": {"model": f"{NS}:block/grave_mound"}}})
    put_json(os.path.join(ASSETS, "items", "grave_mound.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/grave_mound"}})
    noise_tex("grave_stake", ["6a5030", "7a5e3a", "8a6c44", "9a7c52"], "stake", None, ("4a3720", 8))
    # --- items
    draw_vinegar()
    draw_wrap()
    item_assets("vinegar")
    item_assets("salt_herb_wrap")
    draw_scroll("scroll_of_calling_back", "d8b83a", [(7, 4), (8, 4), (7, 5), (8, 5), (7, 10), (8, 10), (7, 11), (8, 11), (6, 7), (9, 7)])
    draw_scroll("scroll_of_keeping", "6fa87a", [(5, 4), (10, 4), (5, 11), (10, 11), (6, 7), (7, 7), (8, 7), (9, 7)])
    draw_scroll("scroll_of_rebirth", "4f9a3a", [(7, 4), (8, 5), (7, 6), (6, 7), (9, 7), (7, 10), (8, 11), (7, 12)])
    for s in ("scroll_of_calling_back", "scroll_of_keeping", "scroll_of_rebirth"):
        item_assets(s)
    # --- recipes
    put_json(os.path.join(DATA, "recipe", "vinegar.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:glass_bottle", "minecraft:apple", "minecraft:apple"],
        "result": {"count": 1, "id": f"{NS}:vinegar"}})
    put_json(os.path.join(DATA, "recipe", "salt_herb_wrap.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:dried_kelp", "minecraft:dried_kelp", "minecraft:fern", "minecraft:paper"],
        "result": {"count": 2, "id": f"{NS}:salt_herb_wrap"}})


if __name__ == "__main__":
    main()
