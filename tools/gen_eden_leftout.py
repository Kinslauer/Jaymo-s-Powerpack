"""Writes what the Verdant World's first pass left out (Powers, com.jaymospowers.eden): the signs, chests, barrels, bookshelves
and ladders of the four woods, the limphium sapling and pressed-leaf painting, and the lang part left_out_eden.json. Every
texture is painted here from the wood's own planks colour (nothing copied from anyone). Run from the repository root:

    python3 -I tools/gen_eden_leftout.py

Needs only Pillow. Seeded from the names, so a rerun changes nothing. The ids must match EdenWoodwork and EdenLimphium.
"""
import json
import os
import random
import sys

sys.dont_write_bytecode = True
from PIL import Image, ImageStat

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)

NS = "jaymospowers"
ASSETS = f"src/main/resources/assets/{NS}"
DATA = f"src/main/resources/data/{NS}"
MC = "src/main/resources/data/minecraft"
WOODS = ["balloon_mushroom", "pulse_tree", "brain_tree", "sunroot"]
LANG = {}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tag(path, values):
    old = json.load(open(path))["values"] if os.path.exists(path) else []
    write(path, {"values": old + [v for v in values if v not in old]})


def title(s):
    return " ".join(w.capitalize() for w in s.split("_"))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


def planks_colour(w):
    im = Image.open(f"{ASSETS}/textures/block/eden/{w}_planks.png").convert("RGB")
    r, g, b = ImageStat.Stat(im).mean
    return (int(r), int(g), int(b), 255)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------- painting

def boards(size_w, size_h, base, rng, rows=4):
    """Horizontal planks with grain noise."""
    img = Image.new("RGBA", (size_w, size_h), base)
    px = img.load()
    row = max(1, size_h // rows)
    for y in range(size_h):
        for x in range(size_w):
            f = 1.0 + rng.uniform(-0.07, 0.07)
            if y % row == 0:
                f *= 0.78
            elif (y // row) % 2 == 0 and x % 7 == 3:
                f *= 0.94
            px[x, y] = shade(base, f)
    return img


def rect(img, x0, y0, x1, y1, c):
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < img.width and 0 <= y < img.height:
                px[x, y] = c


def sign_texture(base, rng, hanging):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    img.alpha_composite(boards(32, 32, base, rng, 8))
    dark = shade(base, 0.55)
    for x in range(0, 25):
        for y in (2, 13, 16, 27):
            img.putpixel((x, y), dark)
    for y in list(range(2, 14)) + list(range(16, 28)):
        for x in (0, 24):
            img.putpixel((x, y), dark)
    if hanging:
        chain = (92, 92, 100, 255)
        for y in range(0, 32, 2):
            for x in (28, 29):
                img.putpixel((x, y), chain)
    return img


def sign_item(base, rng, hanging):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    k = shade(base, 0.45)
    if hanging:
        for y in range(1, 5):
            for x in (4, 11):
                img.putpixel((x, y), (92, 92, 100, 255))
        rect(img, 2, 5, 13, 13, k)
        rect(img, 3, 6, 12, 12, base)
    else:
        rect(img, 1, 2, 14, 9, k)
        rect(img, 2, 3, 13, 8, base)
        rect(img, 7, 10, 8, 14, shade(base, 0.7))
    return img


def barrel_textures(base, rng):
    hoop = (88, 88, 96, 255)
    side = Image.new("RGBA", (16, 16), base)
    px = side.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(base, (0.8 if x % 4 == 0 else 1.0) + rng.uniform(-0.05, 0.05))
    rect(side, 0, 2, 15, 3, hoop)
    rect(side, 0, 12, 15, 13, hoop)
    top = boards(16, 16, base, rng, 4)
    rect(top, 0, 0, 15, 0, shade(base, 0.6))
    rect(top, 0, 15, 15, 15, shade(base, 0.6))
    rect(top, 0, 0, 0, 15, shade(base, 0.6))
    rect(top, 15, 0, 15, 15, shade(base, 0.6))
    rect(top, 3, 3, 12, 12, shade(base, 0.85))
    open_ = top.copy()
    rect(open_, 3, 3, 12, 12, shade(base, 0.32))
    return side, top, open_, boards(16, 16, shade(base, 0.9), rng, 4)


def bookshelf_texture(base, rng):
    img = boards(16, 16, base, rng, 8)
    palette = [(150, 40, 40, 255), (40, 90, 150, 255), (60, 130, 70, 255), (190, 160, 60, 255), (120, 70, 140, 255), (200, 200, 190, 255)]
    for y0, y1 in ((2, 6), (9, 13)):
        x = 0
        while x < 16:
            w = rng.choice((2, 3))
            c = rng.choice(palette)
            for xx in range(x, min(16, x + w)):
                for y in range(y0, y1 + 1):
                    img.putpixel((xx, y), shade(c, 1.0 if xx == x else 0.85))
            x += w
    return img


def ladder_texture(base):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rail = shade(base, 0.8)
    rect(img, 2, 0, 3, 15, rail)
    rect(img, 12, 0, 13, 15, rail)
    for y in (1, 5, 9, 13):
        rect(img, 4, y, 11, y + 1, shade(base, 1.05))
    return img


def chest_texture(base, rng):
    """The 64x64 chest sheet: lid (top 19 rows), base below, and the lock; the layout vanilla's chest model reads."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    edge = shade(base, 0.5)
    band = shade(base, 0.72)
    iron = (96, 96, 104, 255)

    def part(x0, y0, w, d, h):
        # six faces of a w x h x d box with the vanilla layout: top, bottom beside each other above the four sides
        faces = {"top": (d, 0, w, d), "bottom": (d + w, 0, w, d), "right": (0, d, d, h), "front": (d, d, w, h), "left": (d + w, d, d, h),
                 "back": (d + w + d, d, w, h)}
        for fx, fy, fw, fh in faces.values():
            tile = boards(fw, fh, base, rng, max(2, fh // 3))
            img.alpha_composite(tile, (x0 + fx, y0 + fy))
            rect(img, x0 + fx, y0 + fy, x0 + fx + fw - 1, y0 + fy, edge)
            rect(img, x0 + fx, y0 + fy + fh - 1, x0 + fx + fw - 1, y0 + fy + fh - 1, edge)
            rect(img, x0 + fx, y0 + fy, x0 + fx, y0 + fy + fh - 1, edge)
            rect(img, x0 + fx + fw - 1, y0 + fy, x0 + fx + fw - 1, y0 + fy + fh - 1, edge)
        return faces

    part(0, 0, 14, 14, 5)
    part(0, 19, 14, 14, 10)
    # a band across the front, sides and back of the base
    for fx in (0, 14, 28, 42):
        rect(img, fx, 19 + 14 + 6, fx + 13, 19 + 14 + 6, band)
    # the lock: 2x4 at (1,1) on the sheet
    rect(img, 1, 1, 2, 4, iron)
    rect(img, 1, 1, 2, 1, shade(iron, 1.3))
    return img


def limphium_sapling(rng):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    stem = (70, 120, 60, 255)
    leaf = (110, 170, 90, 255)
    leaf_hi = (150, 205, 120, 255)
    rect(img, 7, 8, 8, 15, stem)
    for (x0, y0, x1, y1) in ((3, 6, 6, 8), (9, 6, 12, 8), (5, 3, 6, 6), (9, 3, 10, 6), (7, 1, 8, 4)):
        rect(img, x0, y0, x1, y1, leaf)
    for x, y in ((4, 6), (10, 6), (5, 3), (7, 1)):
        img.putpixel((x, y), leaf_hi)
    return img


def painting_art(rng):
    """The pressed-leaf painting itself, 16x16 blocks per pixel at 1 block: a 16x16 sheet."""
    img = Image.new("RGBA", (16, 16), (232, 224, 190, 255))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade((232, 224, 190, 255), 1.0 + rng.uniform(-0.03, 0.03))
    leaf = (92, 150, 70, 255)
    for (x0, y0, x1, y1) in ((6, 2, 9, 13), (3, 5, 12, 9), (4, 4, 5, 6), (10, 4, 11, 6)):
        rect(img, x0, y0, x1, y1, leaf)
    rect(img, 7, 3, 8, 13, (140, 195, 110, 255))
    rect(img, 7, 13, 8, 15, (120, 90, 55, 255))
    return img


# ---------------------------------------------------------------- the wood sets

def block_json(block_id, name):
    LANG[f"block.{NS}.{block_id}"] = name


def loot_self(block_id, item_id=None):
    write(f"{DATA}/loot_table/blocks/{block_id}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {
        "type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": f"{NS}:{item_id or block_id}"}]}],
        "random_sequence": f"{NS}:blocks/{block_id}"})


def item_def(item_id, model):
    write(f"{ASSETS}/items/{item_id}.json", {"model": {"type": "minecraft:model", "model": model}})


def flat_item(item_id, texture):
    write(f"{ASSETS}/models/item/eden/{item_id}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
    item_def(item_id, f"{NS}:item/eden/{item_id}")


def shaped(name, key, pattern, result, count=1, group=None, cat="building"):
    r = {"id": f"{NS}:{result}"}
    if count > 1:
        r["count"] = count
    body = {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern, "result": r}
    if group:
        body["group"] = group
    write(f"{DATA}/recipe/eden/{name}.json", body)


def wood_set(w):
    rng = random.Random("leftout-" + w)
    base = planks_colour(w)
    planks = f"{NS}:{w}_planks"
    tex = f"{NS}:block/eden/"

    # signs
    for hanging in (False, True):
        kind = "hanging_sign" if hanging else "sign"
        save(sign_texture(base, rng, hanging), f"{ASSETS}/textures/block/eden/{w}_{kind}.png")
        save(sign_item(base, rng, hanging), f"{ASSETS}/textures/item/eden/{w}_{kind}.png")
        flat_item(f"{w}_{kind}", f"{NS}:item/eden/{w}_{kind}")
        particle = f"{NS}:block/eden/{w}_planks"
        states = {}
        if hanging:
            for r in range(16):
                for attached in (False, True):
                    model = f"{NS}:block/eden/{w}_hanging_sign{'_attached' if attached else ''}_rot_{r % 4}"
                    entry = {"model": model}
                    if r // 4:
                        entry["y"] = 90 * (r // 4)
                    states[f"attached={'true' if attached else 'false'},rotation={r}"] = entry
            for r in range(4):
                for attached in (False, True):
                    tpl = "template_attached_hanging_sign" if attached else "template_hanging_sign"
                    write(f"{ASSETS}/models/block/eden/{w}_hanging_sign{'_attached' if attached else ''}_rot_{r}.json",
                          {"parent": f"minecraft:block/{tpl}_rot_{r}", "textures": {"all": tex + f"{w}_hanging_sign", "particle": particle}})
            write(f"{ASSETS}/models/block/eden/{w}_wall_hanging_sign.json",
                  {"parent": "minecraft:block/template_wall_hanging_sign", "textures": {"all": tex + f"{w}_hanging_sign", "particle": particle}})
        else:
            for r in range(16):
                entry = {"model": f"{NS}:block/eden/{w}_sign_rot_{r % 4}"}
                if r // 4:
                    entry["y"] = 90 * (r // 4)
                states[f"rotation={r}"] = entry
            for r in range(4):
                write(f"{ASSETS}/models/block/eden/{w}_sign_rot_{r}.json",
                      {"parent": f"minecraft:block/template_sign_rot_{r}", "textures": {"all": tex + f"{w}_sign", "particle": particle}})
            write(f"{ASSETS}/models/block/eden/{w}_wall_sign.json",
                  {"parent": "minecraft:block/template_wall_sign", "textures": {"all": tex + f"{w}_sign", "particle": particle}})
        write(f"{ASSETS}/blockstates/{w}_{kind}.json", {"variants": states})
        wall = f"{w}_wall_{kind}"
        write(f"{ASSETS}/blockstates/{wall}.json", {"variants": {
            "facing=south": {"model": f"{NS}:block/eden/{wall}"}, "facing=west": {"model": f"{NS}:block/eden/{wall}", "y": 90},
            "facing=north": {"model": f"{NS}:block/eden/{wall}", "y": 180}, "facing=east": {"model": f"{NS}:block/eden/{wall}", "y": 270}}})
        loot_self(f"{w}_{kind}")
        loot_self(wall, f"{w}_{kind}")
        block_json(f"{w}_{kind}", title(f"{w}_{kind}"))
        block_json(wall, title(f"{w}_wall_{kind}"))
    shaped(f"{w}_sign", {"#": planks, "X": "minecraft:stick"}, ["###", "###", " X "], f"{w}_sign", 3, "wooden_sign")
    shaped(f"{w}_hanging_sign", {"#": planks, "X": "minecraft:iron_chain"}, ["X X", "###", "###"], f"{w}_hanging_sign", 6, "wooden_hanging_sign")

    # chest
    save(chest_texture(base, rng), f"{ASSETS}/textures/entity/chest/eden/{w}.png")
    write(f"{ASSETS}/models/block/eden/{w}_chest.json", {"textures": {"particle": tex + f"{w}_planks"}})
    write(f"{ASSETS}/blockstates/{w}_chest.json", {"variants": {"": {"model": f"{NS}:block/eden/{w}_chest"}}})
    write(f"{ASSETS}/items/{w}_chest.json", {"model": {
        "type": "minecraft:special", "base": "minecraft:item/chest", "model": {"type": "minecraft:chest", "texture": f"{NS}:eden/{w}"}}})
    loot_self(f"{w}_chest")
    block_json(f"{w}_chest", title(f"{w}_chest"))
    shaped(f"{w}_chest", {"#": planks}, ["###", "# #", "###"], f"{w}_chest", 1, "chest")

    # barrel
    side, top, top_open, bottom = barrel_textures(base, rng)
    save(side, f"{ASSETS}/textures/block/eden/{w}_barrel_side.png")
    save(top, f"{ASSETS}/textures/block/eden/{w}_barrel_top.png")
    save(top_open, f"{ASSETS}/textures/block/eden/{w}_barrel_top_open.png")
    save(bottom, f"{ASSETS}/textures/block/eden/{w}_barrel_bottom.png")
    for suffix, t in (("", "top"), ("_open", "top_open")):
        write(f"{ASSETS}/models/block/eden/{w}_barrel{suffix}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "bottom": tex + f"{w}_barrel_bottom", "side": tex + f"{w}_barrel_side", "top": tex + f"{w}_barrel_{t}"}})
    rot = {"down": {"x": 180}, "up": {}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
    states = {}
    for facing, r in rot.items():
        for open_ in ("false", "true"):
            entry = {"model": f"{NS}:block/eden/{w}_barrel{'_open' if open_ == 'true' else ''}"}
            entry.update(r)
            states[f"facing={facing},open={open_}"] = entry
    write(f"{ASSETS}/blockstates/{w}_barrel.json", {"variants": states})
    item_def(f"{w}_barrel", f"{NS}:block/eden/{w}_barrel")
    loot_self(f"{w}_barrel")
    block_json(f"{w}_barrel", title(f"{w}_barrel"))
    shaped(f"{w}_barrel", {"P": planks, "S": f"{NS}:{w}_slab"}, ["PSP", "P P", "PSP"], f"{w}_barrel", 1, "barrel")

    # bookshelf
    save(bookshelf_texture(base, rng), f"{ASSETS}/textures/block/eden/{w}_bookshelf.png")
    write(f"{ASSETS}/models/block/eden/{w}_bookshelf.json", {"parent": "minecraft:block/cube_column", "textures": {
        "end": tex + f"{w}_planks", "side": tex + f"{w}_bookshelf"}})
    write(f"{ASSETS}/blockstates/{w}_bookshelf.json", {"variants": {"": {"model": f"{NS}:block/eden/{w}_bookshelf"}}})
    item_def(f"{w}_bookshelf", f"{NS}:block/eden/{w}_bookshelf")
    write(f"{DATA}/loot_table/blocks/{w}_bookshelf.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": f"{NS}:{w}_bookshelf"},
            {"type": "minecraft:item", "name": "minecraft:book", "modifier": [{"type": "minecraft:set_count", "count": 3},
                                                                              {"type": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"{NS}:blocks/{w}_bookshelf"})
    block_json(f"{w}_bookshelf", title(f"{w}_bookshelf"))
    shaped(f"{w}_bookshelf", {"#": planks, "X": "minecraft:book"}, ["###", "XXX", "###"], f"{w}_bookshelf", 1, "bookshelf")

    # ladder
    save(ladder_texture(base), f"{ASSETS}/textures/block/eden/{w}_ladder.png")
    write(f"{ASSETS}/models/block/eden/{w}_ladder.json", {
        "ambientocclusion": False, "textures": {"particle": tex + f"{w}_ladder", "texture": tex + f"{w}_ladder"},
        "elements": [{"from": [0, 0, 15.2], "to": [16, 16, 15.2], "shade_direction_override": "up", "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#texture"}, "south": {"uv": [16, 0, 0, 16], "texture": "#texture"}}}]})
    write(f"{ASSETS}/blockstates/{w}_ladder.json", {"variants": {
        "facing=north": {"model": f"{NS}:block/eden/{w}_ladder"}, "facing=east": {"model": f"{NS}:block/eden/{w}_ladder", "y": 90},
        "facing=south": {"model": f"{NS}:block/eden/{w}_ladder", "y": 180}, "facing=west": {"model": f"{NS}:block/eden/{w}_ladder", "y": 270}}})
    flat_item(f"{w}_ladder", tex + f"{w}_ladder")
    loot_self(f"{w}_ladder")
    block_json(f"{w}_ladder", title(f"{w}_ladder"))
    shaped(f"{w}_ladder", {"#": "minecraft:stick"}, ["# #", "###", "# #"], f"{w}_ladder", 3, "ladder")


def tags():
    def names(*shapes):
        return [f"{NS}:{w}_{s}" for w in WOODS for s in shapes]
    tag(f"{MC}/tags/block/standing_signs.json", names("sign"))
    tag(f"{MC}/tags/block/wall_signs.json", names("wall_sign"))
    tag(f"{MC}/tags/block/ceiling_hanging_signs.json", names("hanging_sign"))
    tag(f"{MC}/tags/block/wall_hanging_signs.json", names("wall_hanging_sign"))
    tag(f"{MC}/tags/item/signs.json", names("sign"))
    tag(f"{MC}/tags/item/hanging_signs.json", names("hanging_sign"))
    tag(f"{MC}/tags/block/climbable.json", names("ladder"))
    tag(f"{MC}/tags/block/enchantment_power_provider.json", names("bookshelf"))
    tag(f"{MC}/tags/block/mineable/axe.json", names("sign", "wall_sign", "hanging_sign", "wall_hanging_sign", "chest", "barrel", "bookshelf", "ladder"))


# ---------------------------------------------------------------- limphium

def limphium():
    rng = random.Random("leftout-limphium")
    save(limphium_sapling(rng), f"{ASSETS}/textures/block/eden/limphium_sapling.png")
    write(f"{ASSETS}/models/block/eden/limphium_sapling.json", {"parent": "minecraft:block/cross", "textures": {
        "cross": f"{NS}:block/eden/limphium_sapling"}})
    write(f"{ASSETS}/blockstates/limphium_sapling.json", {"variants": {"": {"model": f"{NS}:block/eden/limphium_sapling"}}})
    flat_item("limphium_sapling", f"{NS}:block/eden/limphium_sapling")
    loot_self("limphium_sapling")
    block_json("limphium_sapling", "Limphium Sapling")
    shaped("limphium_sapling", {"L": f"{NS}:limphium_leaf"}, ["LL", "LL"], "limphium_sapling", 1, None, "misc")
    # the pressed-leaf painting: a painting variant (hung at random like the vanilla ones) and a recipe for it on purpose
    save(painting_art(rng), f"{ASSETS}/textures/painting/pressed_leaf.png")
    LANG[f"painting.{NS}.pressed_leaf.title"] = "Pressed Leaf"
    LANG[f"painting.{NS}.pressed_leaf.author"] = "The Verdant World"
    write(f"{DATA}/painting_variant/pressed_leaf.json", {
        "asset_id": f"{NS}:pressed_leaf", "width": 1, "height": 1,
        "title": {"color": "yellow", "translate": f"painting.{NS}.pressed_leaf.title"},
        "author": {"color": "gray", "translate": f"painting.{NS}.pressed_leaf.author"}})
    tag(f"{MC}/tags/painting_variant/placeable.json", [f"{NS}:pressed_leaf"])
    write(f"{DATA}/recipe/eden/pressed_leaf_painting.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [f"{NS}:dried_limphium_leaf", "minecraft:painting"],
        "result": {"id": "minecraft:painting", "components": {"minecraft:painting/variant": f"{NS}:pressed_leaf"}}})


ASPECTS = {
    "balloon_mushroom_small": {"plant": 3, "air": 2, "mind": 1},
    "mycotic_grass": {"plant": 2, "spirit": 1},
    "violum": {"plant": 3, "light": 2, "spirit": 1},
    "lonlix": {"plant": 3, "water": 1, "motion": 1},
    "parignum": {"plant": 2, "light": 1, "life": 1},
    "limphium_leaf": {"plant": 3, "air": 2},
    "dried_limphium_leaf": {"plant": 3, "air": 3, "motion": 1},
    "limphium_sapling": {"plant": 4, "air": 2, "life": 1},
    "limphium": {"plant": 4, "air": 2},
    "eden_vine": {"plant": 3, "life": 1},
    "eden_moss": {"plant": 2, "water": 2, "earth": 1},
    "symbiotic_mold": {"rot": 2, "plant": 2, "life": 2},
    "symbiotic_mold_emissive": {"rot": 2, "plant": 2, "life": 2, "light": 1},
    "copper_grass": {"plant": 2, "metal": 3},
    "iron_grass": {"plant": 2, "metal": 4},
    "gold_grass": {"plant": 2, "wealth": 4},
    "gravilite_shards": {"crystal": 6, "motion": 3, "earth": 3},
    "gravilite_block": {"crystal": 24, "motion": 12, "earth": 12},
    "pulse_tree_sapling": {"plant": 4, "light": 2, "life": 2},
    "sunroot_sapling": {"plant": 4, "light": 3, "life": 1},
    "aquatus_sapling": {"plant": 4, "water": 3},
    "gas_vent": {"venom": 4, "earth": 2, "death": 1},
    "volvox_block": {"water": 3, "life": 2, "air": 1},
    "volvox_block_dense": {"water": 4, "life": 3, "light": 2},
}


def alchemy():
    """The essences of the new plants and gravilite, read by the Scrying Lens and the crucible."""
    write(f"{DATA}/alchemy/essences/verdant_world_plants.json", {"items": {f"{NS}:{k}": v for k, v in ASPECTS.items()}})


def gas_vent_texture(rng):
    """Pale green-grey stone pocked with dark vents."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    base = (118, 132, 104, 255)
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(base, 1.0 + rng.uniform(-0.12, 0.12))
    for _ in range(7):
        x, y = rng.randrange(1, 14), rng.randrange(1, 14)
        rect(img, x, y, x + 1, y + 1, (44, 58, 38, 255))
        img.putpixel((x, y), (143, 191, 74, 255))
    return img


def gas_vent():
    rng = random.Random("leftout-gas-vent")
    save(gas_vent_texture(rng), f"{ASSETS}/textures/block/eden/gas_vent.png")
    write(f"{ASSETS}/models/block/eden/gas_vent.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/eden/gas_vent"}})
    write(f"{ASSETS}/blockstates/gas_vent.json", {"variants": {"": {"model": f"{NS}:block/eden/gas_vent"}}})
    item_def("gas_vent", f"{NS}:block/eden/gas_vent")
    loot_self("gas_vent")
    block_json("gas_vent", "Gas Vent")
    tag(f"{MC}/tags/block/mineable/pickaxe.json", [f"{NS}:gas_vent"])
    write(f"{DATA}/worldgen/feature/island_caves.json", {"type": f"{NS}:eden", "kind": "island_caves"})
    write(f"{DATA}/worldgen/placed_feature/island_caves.json", {"feature": f"{NS}:island_caves", "placement": [
        {"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})


def main():
    for w in WOODS:
        wood_set(w)
    tags()
    limphium()
    gas_vent()
    alchemy()
    write(f"{ASSETS}/lang/parts/left_out_eden.json", LANG)
    print(f"left out eden: {len(LANG)} names")


main()
