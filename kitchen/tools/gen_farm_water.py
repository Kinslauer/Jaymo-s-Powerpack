"""Farm, field and water (medieval deco patch 5): textures, models, blockstates, item models, loot tables, recipes, tags.
Original pixel art drawn here with Pillow (vanilla style: no outline, lit from the top-left); the blocks otherwise
reuse vanilla block textures by id. Run from anywhere: python -I kitchen/tools/gen_farm_water.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "jaymoskitchen"
DATA = ROOT / "data" / "jaymoskitchen"
NS = "jaymoskitchen"


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def save(img, rel):
    path = ASSETS / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


# ---------------------------------------------------------------- textures
RIM = [(150, 112, 66, 255), (136, 100, 58, 255), (120, 88, 50, 255)]
INNER_EMPTY = (58, 40, 26, 255)
WATER = [(58, 98, 190, 255), (70, 112, 205, 255), (88, 130, 214, 255)]


def barrel_top(inner):
    """A barrel top seen from above: a wooden rim round an open middle filled by {@code inner(x, y, rnd)}."""
    rnd = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge < 2:
                img.putpixel((x, y), RIM[(x + y + rnd.randint(0, 1)) % 3])
            else:
                img.putpixel((x, y), inner(x, y, rnd))
    # the hoops' dark seam between rim and middle
    for i in range(2, 14):
        for p in ((i, 2), (i, 13), (2, i), (13, i)):
            r, g, b, a = img.getpixel(p)
            img.putpixel(p, (int(r * 0.8), int(g * 0.8), int(b * 0.8), 255))
    return img


for level in range(4):
    if level == 0:
        save(barrel_top(lambda x, y, r: INNER_EMPTY if (x + y) % 5 else (72, 50, 32, 255)), f"block/water_barrel_top_{level}.png")
    else:
        # deeper water, darker and bluer; a glint on the lit side
        base = WATER[level - 1]
        save(barrel_top(lambda x, y, r, b=base: tuple(max(0, min(255, c + (14 if (x * 3 + y * 5) % 9 == 0 else r.randint(-4, 4)))) for c in b[:3]) + (255,)),
             f"block/water_barrel_top_{level}.png")

FISH = [(168, 178, 190, 255), (140, 150, 164, 255), (214, 120, 98, 255), (112, 124, 140, 255)]


def fish_top(count):
    def inner(x, y, rnd):
        # packed salt-grey fish backs, more of them the fuller the barrel
        if count == 0:
            return INNER_EMPTY
        if count == 1 and (x * 7 + y * 3) % 11 > 4:
            return INNER_EMPTY
        return FISH[(x // 2 + y + (x * y) % 3) % len(FISH)] if (x + y) % 6 else (236, 240, 244, 255)
    return barrel_top(inner)


for n in range(3):
    save(fish_top(n), f"block/fish_barrel_top_{n}.png")


def wicker():
    rnd = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            weave = ((x // 2) + (y // 2)) % 2
            base = (196, 160, 92) if weave else (166, 130, 72)
            if x % 2 == 0 and y % 2 == 0:
                base = (128, 98, 52)
            j = rnd.randint(-6, 6)
            img.putpixel((x, y), tuple(max(0, min(255, c + j)) for c in base) + (255,))
    return img


save(wicker(), "block/fish_trap.png")


def bait_patch():
    rnd = random.Random(9)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rnd.choice([(150, 84, 72), (184, 112, 94), (112, 58, 52), (222, 192, 98)])
            img.putpixel((x, y), c + (255,))
    return img


save(bait_patch(), "block/fish_trap_bait.png")

CAN = {
    ".": (0, 0, 0, 0),
    "d": (92, 98, 108, 255),    # body, shade
    "m": (136, 144, 156, 255),  # body
    "l": (186, 194, 206, 255),  # body, lit
    "h": (226, 232, 240, 255),  # glint
    "w": (88, 140, 224, 255),   # water in the mouth
    "s": (72, 78, 88, 255),     # spout dark
}
CAN_ROWS = [
    "................",
    "..........ss....",
    "..........sms...",
    ".....lllll.sm...",
    "....lmmmmmm.sm..",
    "...ld.wwww.dsm..",
    "..lld.....dd.sm.",
    "..lmmmmmmmmdd.d.",
    "..lmmmmmmmmmdd..",
    "..lhmmmmmmmmdd..",
    "..lhmmmmmmmmdd..",
    "..lmmmmmmmmmdd..",
    "..lmmmmmmmmmdd..",
    "...dmmmmmmmdd...",
    "....dddddddd....",
    "................",
]
can = Image.new("RGBA", (16, 16))
for y, row in enumerate(CAN_ROWS):
    for x, ch in enumerate(row):
        can.putpixel((x, y), CAN[ch])
save(can, "item/watering_can.png")

# ---------------------------------------------------------------- models
OAK = "minecraft:block/oak_planks"


def box(frm, to, tex, faces=("down", "up", "north", "south", "west", "east")):
    return {"from": frm, "to": to, "faces": {f: {"texture": tex} for f in faces}}


def block_model(name, obj):
    write_json(ASSETS / "models" / "block" / f"{name}.json", obj)


def barrel(name, top):
    block_model(name, {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {"top": top, "side": "minecraft:block/barrel_side", "bottom": "minecraft:block/barrel_bottom",
                     "particle": "minecraft:block/barrel_side"},
    })


for n in range(4):
    barrel(f"water_barrel_{n}", f"{NS}:block/water_barrel_top_{n}")
for n in range(3):
    barrel(f"fish_barrel_{n}", f"{NS}:block/fish_barrel_top_{n}")

for n in range(4):
    els = [
        box([1, 0, 1], [15, 1, 15], "#planks"),
        box([1, 1, 1], [15, 7, 2], "#planks"),
        box([1, 1, 14], [15, 7, 15], "#planks"),
        box([1, 1, 2], [2, 7, 14], "#planks"),
        box([14, 1, 2], [15, 7, 14], "#planks"),
    ]
    if n:
        els.append(box([2, 1, 2], [14, 1 + 2 * n, 14], "#hay"))
    block_model(f"feeding_trough_{n}", {
        "textures": {"planks": OAK, "hay": "minecraft:block/hay_block_top", "particle": OAK}, "elements": els})

trap = [box([3, 0, 3], [13, 10, 13], "#wicker"), box([5, 10, 5], [11, 13, 11], "#wicker")]
block_model("fish_trap", {"textures": {"wicker": f"{NS}:block/fish_trap", "particle": f"{NS}:block/fish_trap"}, "elements": trap})
block_model("fish_trap_baited", {
    "textures": {"wicker": f"{NS}:block/fish_trap", "bait": f"{NS}:block/fish_trap_bait", "particle": f"{NS}:block/fish_trap"},
    "elements": trap + [box([4, 10, 4], [12, 10.5, 12], "#bait", ("up",))]})

post = [box([7, 0, 7], [9, 14, 9], "#log"), box([0, 8, 7], [16, 10, 9], "#hay"), box([5, 3, 5], [11, 12, 11], "#hay")]
hay_tex = {"log": "minecraft:block/oak_log", "hay": "minecraft:block/hay_block_side", "top": "minecraft:block/hay_block_top",
           "particle": "minecraft:block/hay_block_side"}
block_model("scarecrow", {"textures": hay_tex, "elements": post + [box([4, 12, 4], [12, 16, 12], "#hay")]})
head = {"from": [4, 12, 4], "to": [12, 16, 12], "faces": {
    "north": {"texture": "#face"}, "south": {"texture": "#pside"}, "west": {"texture": "#pside"}, "east": {"texture": "#pside"},
    "up": {"texture": "#ptop"}, "down": {"texture": "#ptop"}}}
block_model("scarecrow_pumpkin", {
    "textures": dict(hay_tex, face="minecraft:block/carved_pumpkin", pside="minecraft:block/pumpkin_side", ptop="minecraft:block/pumpkin_top"),
    "elements": post + [head]})

write_json(ASSETS / "models" / "item" / "watering_can.json",
           {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/watering_can"}})

# ---------------------------------------------------------------- blockstates
def blockstate(name, variants):
    write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


blockstate("water_barrel", {f"level={n}": {"model": f"{NS}:block/water_barrel_{n}"} for n in range(4)})
blockstate("fish_barrel", {f"catch={n}": {"model": f"{NS}:block/fish_barrel_{n}"} for n in range(3)})
blockstate("feeding_trough", {f"fill={n}": {"model": f"{NS}:block/feeding_trough_{n}"} for n in range(4)})
blockstate("fish_trap", {f"baited={b},waterlogged={w}": {"model": f"{NS}:block/fish_trap{'_baited' if b == 'true' else ''}"}
                         for b in ("false", "true") for w in ("false", "true")})
scare = {}
for pumpkin in ("false", "true"):
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        entry = {"model": f"{NS}:block/scarecrow{'_pumpkin' if pumpkin == 'true' else ''}"}
        if y:
            entry["y"] = y
        scare[f"facing={facing},pumpkin={pumpkin}"] = entry
blockstate("scarecrow", scare)

# ---------------------------------------------------------------- item definitions
for item, model in (("water_barrel", "block/water_barrel_3"), ("fish_barrel", "block/fish_barrel_1"), ("feeding_trough", "block/feeding_trough_2"),
                    ("fish_trap", "block/fish_trap"), ("scarecrow", "block/scarecrow_pumpkin"), ("watering_can", "item/watering_can")):
    write_json(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:{model}"}})

# ---------------------------------------------------------------- loot tables
for block in ("water_barrel", "fish_barrel", "feeding_trough", "fish_trap"):
    write_json(DATA / "loot_table" / "blocks" / f"{block}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}]}]})
write_json(DATA / "loot_table" / "blocks" / "scarecrow.json", {
    "type": "minecraft:block",
    "pools": [
        {"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
         "entries": [{"type": "minecraft:item", "name": f"{NS}:scarecrow"}]},
        {"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"},
                                    {"condition": "minecraft:block_state_property", "block": f"{NS}:scarecrow", "properties": {"pumpkin": "true"}}],
         "entries": [{"type": "minecraft:item", "name": "minecraft:carved_pumpkin"}]}]})

# ---------------------------------------------------------------- recipes
def recipe(name, obj):
    write_json(DATA / "recipe" / f"{name}.json", obj)


recipe("water_barrel", {"type": "minecraft:crafting_shaped", "category": "misc",
                        "key": {"N": "minecraft:iron_nugget", "B": "minecraft:barrel"},
                        "pattern": ["N N", "NBN", "N N"], "result": {"id": f"{NS}:water_barrel"}})
recipe("fish_barrel", {"type": "minecraft:crafting_shapeless", "category": "misc",
                       "ingredients": ["minecraft:barrel", f"{NS}:salt", f"{NS}:salt"], "result": {"id": f"{NS}:fish_barrel"}})
recipe("feeding_trough", {"type": "minecraft:crafting_shaped", "category": "misc",
                          "key": {"P": "#minecraft:planks"}, "pattern": ["P P", "PPP"], "result": {"id": f"{NS}:feeding_trough"}})
recipe("watering_can", {"type": "minecraft:crafting_shaped", "category": "equipment",
                        "key": {"I": "minecraft:iron_ingot"}, "pattern": ["I I", "III"], "result": {"id": f"{NS}:watering_can"}})
recipe("fish_trap", {"type": "minecraft:crafting_shaped", "category": "misc",
                     "key": {"S": "minecraft:stick", "T": "minecraft:string"},
                     "pattern": ["SSS", "STS", "SSS"], "result": {"id": f"{NS}:fish_trap"}})
recipe("scarecrow", {"type": "minecraft:crafting_shaped", "category": "misc",
                     "key": {"H": "minecraft:hay_block", "S": "minecraft:stick"},
                     "pattern": [" H ", "SHS", " S "], "result": {"id": f"{NS}:scarecrow"}})

# ---------------------------------------------------------------- tags
write_json(DATA / "tags" / "item" / "trough_feed.json", {"values": [
    "minecraft:hay_block", "minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot", "minecraft:beetroot"]})
write_json(DATA / "tags" / "entity_type" / "crop_eaters.json", {"values": [
    "minecraft:rabbit", {"id": f"{NS}:rat", "required": False}]})
print("done")
