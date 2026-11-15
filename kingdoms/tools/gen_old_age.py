"""The old age's peoples (kingdoms/src/main/java/com/jaymoskingdoms/oldage/): their art and data.

- Skins (64x64, the player layout): textures/entity/old_age/ruler_<people>.png and guard_<people>.png for the four
  peoples: the Sunstep folk of the sands (linen gone yellow, a striped head-cloth, gold), the Pillared folk of the plains
  (a white robe with a red hem, bronze, a leaf crown), the Mound-kin of the north (fur and blue wool, an iron helm, a
  beard), the Walled Court of Blossom (jade and plum silk, a black topknot, gold pins). All dead a long time: skin gone
  to grey parchment, eyes two sparks in dark sockets.
- Icons (16x16): the relics (sun_scarab, marble_bust, barrow_brooch, jade_seal) and the spawn eggs (old_ruler,
  tomb_guard); blocks: tomb_seal, old_altar (top lit and cold, side, bottom).
- Models, block states and item definitions for all of them.
- Data: the chests' loot (chests/old_age/<people>_house and _tomb), the seal's drop, the cities (worldgen structure,
  structure set, biome tags, the old_ruins structure tag), the shafts' tag (old_shafts) and the seal's pickaxe tag.

Seeded, so reruns give the same art. Run from the project root: python3 kingdoms/tools/gen_old_age.py  (needs Pillow)
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data")
NS = "jaymoskingdoms"
PEOPLES = ["sunstep", "pillared", "mound", "court"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c[:3])


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


# ---- the skins ----------------------------------------------------------------------------------------------------

DEAD = (150, 140, 120)
SOCKET = (30, 24, 22)
# people: skin, robe, robe dark, trim, metal, head-cloth/hair, spark (eyes)
LOOKS = {
    "sunstep": dict(skin=(150, 120, 86), robe=(214, 196, 150), dark=(170, 150, 108), trim=(40, 70, 150), metal=(214, 170, 60),
                    hair=(40, 70, 150), hair2=(214, 170, 60), spark=(255, 200, 80)),
    "pillared": dict(skin=(160, 154, 140), robe=(226, 222, 210), dark=(180, 176, 166), trim=(150, 40, 36), metal=(176, 120, 60),
                     hair=(70, 110, 50), hair2=(90, 70, 50), spark=(255, 120, 90)),
    "mound": dict(skin=(128, 138, 140), robe=(60, 80, 120), dark=(92, 70, 50), trim=(150, 140, 120), metal=(120, 124, 130),
                  hair=(170, 168, 160), hair2=(120, 124, 130), spark=(170, 220, 255)),
    "court": dict(skin=(176, 166, 150), robe=(60, 130, 100), dark=(90, 50, 90), trim=(200, 160, 60), metal=(200, 160, 60),
                  hair=(24, 22, 26), hair2=(200, 160, 60), spark=(200, 140, 255)),
}


def skin(people, ruler, seed):
    r = random.Random(seed)
    L = LOOKS[people]
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def put(x, y, c, a=255):
        img.putpixel((x, y), tuple(c[:3]) + (a,))

    def tex(c, face):
        k = 0.86 + r.random() * 0.2
        if face in ("left", "right"):
            k *= 0.9
        elif face == "back":
            k *= 0.94
        elif face == "bottom":
            k *= 0.75
        return shade(c, k)

    def paint(u, v, w, h, d, colour, alpha=None):
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            for i in range(fw):
                for j in range(fh):
                    y = -1 if face == "top" else h if face == "bottom" else j
                    c = colour(face, i, y, fw)
                    if c is not None:
                        put(fx + i, fy + j, tex(c, face), 255 if alpha is None else alpha)

    flesh = mix(L["skin"], DEAD, 0.35)
    armoured = not ruler

    # the head: dead flesh, sockets with a spark, the people's hair or head-cloth over it
    def head(face, x, y, fw):
        if face == "front":
            if y in (3, 4) and x in (1, 2, 5, 6):
                return L["spark"] if (y == 4 and x in (2, 5)) else SOCKET
            if y == 6 and 2 <= x <= 5:
                return shade(SOCKET, 1.6)
            if people == "mound" and y >= 5:
                return shade(L["hair"], 0.9) if (y >= 6 or x in (0, 1, 6, 7)) else flesh
            if people == "court" and y <= 1:
                return L["hair"]
            if people == "sunstep" and y <= 1:
                return L["hair"] if (x + y) % 2 == 0 else L["hair2"]
            return flesh
        if face in ("top", "back") or y <= 2:
            if people == "sunstep":
                return L["hair"] if (y + (x if face == "top" else 0)) % 2 == 0 else L["hair2"]
            return L["hair"] if people != "pillared" else mix(L["hair2"], DEAD, 0.3)
        if people == "sunstep" and face in ("left", "right"):
            return L["hair"] if y % 2 == 0 else L["hair2"]
        if people == "court" and y <= 4:
            return L["hair"]
        if people == "mound" and y >= 5:
            return shade(L["hair"], 0.85)
        return flesh
    paint(0, 0, 8, 8, 8, head)

    # the hat layer: crown, helm, leaf crown, pins
    def hat(face, x, y, fw):
        if armoured or people == "mound":
            if people == "mound":
                if y <= 2 or face == "top":
                    return L["metal"]
                if face == "front" and x in (3, 4) and y <= 5:
                    return L["metal"]
                if face in ("left", "right") and y <= 5 and armoured:
                    return shade(L["metal"], 0.85)
                return None
            if people == "pillared":
                if face == "top" or y <= 1:
                    return L["metal"]
                if face == "top" and x in (3, 4):
                    return L["trim"]
                if face in ("left", "right", "back") and y <= 5:
                    return L["metal"]
                return None
            if people == "court":
                if face == "top" or y <= 1:
                    return shade(L["metal"], 0.8)
                if face in ("left", "right") and 2 <= y <= 4:
                    return shade(L["dark"], 1.1)
                return None
            # sunstep guard: a striped cloth over the head
            if face == "front" and y >= 2:
                return None
            if face == "bottom":
                return None
            return L["hair"] if y % 2 == 0 else L["hair2"]
        # rulers
        if people == "sunstep":
            if face == "front" and y >= 2:
                return L["metal"] if y == 2 else None
            if face == "bottom":
                return None
            if face == "top":
                return L["hair2"] if (x + y) % 3 == 0 else L["hair"]
            return L["metal"] if y == 2 else (L["hair"] if y % 2 == 0 else L["hair2"])
        if people == "pillared":
            if face != "bottom" and face != "top" and y == 2 and (x % 2 == 0 or face == "front"):
                return L["hair"]
            return None
        if people == "court":
            if face == "top" and 2 <= x <= 5 and 2 <= y <= 5:
                return L["hair"]
            if face != "bottom" and face != "top" and y == 0 and x % 2 == 0:
                return L["metal"]
            return None
        return None
    paint(32, 0, 8, 8, 8, hat)

    # the body: robe or tunic, a collar or cuirass, a belt
    def body(face, x, y, fw):
        if y < 0:
            return L["robe"]
        if people == "pillared" and armoured and y <= 7:
            return L["metal"] if (x + y) % 4 else shade(L["metal"], 0.8)
        if people == "mound" and armoured and y <= 8:
            return L["metal"] if (x + 2 * y) % 3 else shade(L["metal"], 0.75)
        if y <= 1:
            if people in ("sunstep", "court") or ruler:
                return L["metal"] if (x % 2 == 0 or people == "sunstep") else L["trim"]
        if y == 7 or y == 8:
            return L["dark"] if people != "court" else L["trim"]
        if people == "court" and face == "front" and x in (3, 4):
            return L["dark"]
        if people == "pillared" and ruler and face == "front" and (x + y) % 5 == 0:
            return L["trim"]
        if y >= 11:
            return L["trim"]
        return L["robe"] if not (people == "mound" and ruler) or face != "back" else L["dark"]
    paint(16, 16, 8, 12, 4, body)

    # a fur cloak for the Mound-kin's king, on the jacket layer behind
    if people == "mound" and ruler:
        def cloak(face, x, y, fw):
            if face in ("back", "left", "right") and y >= 0 and (face == "back" or y >= 0):
                return shade(L["dark"], 0.9 + 0.1 * ((x + y) % 2))
            if face == "top":
                return L["dark"]
            return None
        paint(16, 32, 8, 12, 4, cloak)

    # arms: sleeves to the elbow (or bare, wrapped), hands of dead flesh, bracers on the guards
    def arm(face, x, y, fw):
        if y < 0:
            return L["robe"]
        if y >= 10:
            return flesh if people != "sunstep" else mix(flesh, (220, 210, 180), 0.4)
        if armoured and 6 <= y <= 9:
            return L["metal"]
        if people == "sunstep" and y >= 4:
            return mix(flesh, (220, 210, 180), 0.4) if y % 3 else shade(flesh, 0.8)
        if people == "pillared" and y >= 4:
            return flesh
        if y == 0 and ruler:
            return L["metal"]
        return L["robe"] if people != "court" else (L["robe"] if y < 8 else L["dark"])
    paint(40, 16, 4, 12, 4, arm)
    paint(32, 48, 4, 12, 4, arm)

    # legs: a skirt and wraps, sandals, boots
    def leg(face, x, y, fw):
        if y < 0:
            return L["robe"]
        if people == "court":
            return L["dark"] if y < 10 else shade(L["hair"], 1.4)
        if people == "mound":
            if y >= 9:
                return L["dark"]
            return L["robe"] if y < 4 else (shade(L["trim"], 0.8) if (y + x) % 2 else L["trim"])
        if y <= 3:
            return L["robe"] if people == "sunstep" or ruler else L["trim"]
        if y >= 11:
            return shade(L["dark"], 0.7)
        if armoured and 6 <= y <= 9 and people == "pillared":
            return L["metal"]
        return flesh if people == "pillared" else mix(flesh, (220, 210, 180), 0.4) if y % 2 else flesh
    paint(0, 16, 4, 12, 4, leg)
    paint(16, 48, 4, 12, 4, leg)
    return img


# ---- icons ----------------------------------------------------------------------------------------------------------

def icon(draw, seed):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            c = draw(x, y)
            if c is not None:
                img.putpixel((x, y), shade(c, 0.9 + r.random() * 0.15) + (255,))
    return img


def scarab(x, y):
    dx, dy = (x - 7.5) / 5.0, (y - 8.5) / 6.0
    if dx * dx + dy * dy <= 1.0:
        if abs(x - 7.5) < 0.6 and y > 5:
            return (120, 80, 30)
        if y <= 4:
            return (60, 100, 180)
        return (220, 170, 60) if (x + y) % 5 else (255, 220, 120)
    if (x in (2, 13) and 6 <= y <= 11 and y % 2 == 0) or (y == 2 and 6 <= x <= 9):
        return (170, 120, 40)
    return None


def bust(x, y):
    if 12 <= y <= 14 and 3 <= x <= 12:
        return (150, 140, 130)
    if 9 <= y <= 11 and 4 <= x <= 11:
        return (228, 224, 214)
    dx, dy = (x - 7.5) / 3.2, (y - 5.0) / 4.0
    if dx * dx + dy * dy <= 1.0:
        if y == 5 and x in (6, 9):
            return (150, 146, 138)
        return (236, 232, 222) if x < 9 else (206, 202, 192)
    if y == 2 and 4 <= x <= 11 and x % 2:
        return (90, 140, 70)
    return None


def brooch(x, y):
    d = ((x - 7.5) ** 2 + (y - 8.0) ** 2) ** 0.5
    if 3.6 <= d <= 5.4:
        return (190, 196, 206) if (x + y) % 3 else (130, 136, 146)
    if abs((x - 7.5) - (y - 8.0) * -0.2) < 0.8 and 2 <= y <= 14:
        return (160, 120, 60)
    if d <= 1.2:
        return (60, 90, 160)
    return None


def seal(x, y):
    if 3 <= x <= 12 and 6 <= y <= 14:
        if 5 <= x <= 10 and 8 <= y <= 12 and (x == 5 or x == 10 or y == 8 or y == 12 or x == 7 and y == 10):
            return (40, 90, 60)
        return (80, 170, 110) if (x * 3 + y) % 7 else (130, 210, 150)
    if 6 <= x <= 9 and 2 <= y <= 5:
        return (70, 150, 100)
    return None


def egg(shell, spot, seed):
    r = random.Random(seed)

    def draw(x, y):
        dx, dy = (x - 7.5) / 5.2, (y - 8.0) / 7.2
        if dx * dx * (1.0 + 0.3 * (8.0 - y) / 7.2) + dy * dy <= 1.0:
            return spot if r.random() < 0.18 else shell
        return None
    return icon(draw, seed)


# ---- blocks ---------------------------------------------------------------------------------------------------------

def stone_tex(seed, base, mark=None, lit=None):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(base, 0.85 + r.random() * 0.2)
            if x in (0, 15) or y in (0, 15):
                c = shade(base, 0.7)
            if mark and mark(x, y):
                c = shade(base, 0.45)
            if lit is not None:
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d <= 5.5:
                    c = shade(base, 0.55) if d > 4.5 else (lit(x, y, r) if lit else c)
            img.putpixel((x, y), c + (255,))
    return img


def ring_mark(x, y):
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
    return 4.2 <= d <= 5.0 or (abs(x - 7.5) < 0.6 and 3 <= y <= 12) or (abs(y - 7.5) < 0.6 and 3 <= x <= 12)


def band_mark(x, y):
    return y in (3, 12) or (4 <= y <= 11 and x % 4 == 1 and y % 4 == 0)


def embers(x, y, r):
    return (255, 150 + r.randrange(80), 40) if r.random() < 0.5 else (200, 70, 20)


def ash(x, y, r):
    return (70, 66, 62) if r.random() < 0.6 else (110, 104, 98)


# ---- data -----------------------------------------------------------------------------------------------------------

BIOMES = {
    "sunstep": ["minecraft:desert", {"id": "biomesoplenty:dune_beach", "required": False}, {"id": "natures_spirit:desert_oasis", "required": False}],
    "pillared": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", {"id": "biomesoplenty:grassland", "required": False},
                 {"id": "biomesoplenty:prairie", "required": False}],
    "mound": ["minecraft:taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:old_growth_spruce_taiga",
              "minecraft:old_growth_pine_taiga", {"id": "biomesoplenty:tundra", "required": False}],
    "court": ["minecraft:cherry_grove", {"id": "biomesoplenty:cherry_blossom_grove", "required": False}],
}
RELIC = {"sunstep": "sun_scarab", "pillared": "marble_bust", "mound": "barrow_brooch", "court": "jade_seal"}
TRIM = {"sunstep": "minecraft:chiseled_sandstone", "pillared": "minecraft:calcite", "mound": "minecraft:stripped_spruce_log",
        "court": "minecraft:red_terracotta"}


def item(name, weight, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi > 1:
        e["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def pool(lo, hi, entries):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "entries": entries}


def loot(people, tomb):
    relic = NS + ":" + RELIC[people]
    if tomb:
        pools = [
            pool(1, 1, [item("minecraft:emerald", 1, 4, 12)]),
            pool(1, 1, [item(relic, 1)]),
            pool(2, 5, [item("minecraft:gold_ingot", 12, 1, 4), item("minecraft:gold_nugget", 14, 3, 9), item("minecraft:emerald", 10, 1, 4),
                        item("minecraft:bone", 10, 2, 6), item(TRIM[people], 8, 2, 6), item("minecraft:diamond", 2),
                        item("minecraft:book", 4), item("minecraft:golden_apple", 2), item(relic, 2)]),
        ]
    else:
        pools = [
            pool(1, 2, [item("minecraft:emerald", 1, 1, 3)]),
            pool(1, 4, [item("minecraft:bone", 12, 1, 3), item("minecraft:bread", 6, 1, 3), item("minecraft:iron_nugget", 10, 2, 6),
                        item("minecraft:gold_nugget", 8, 1, 4), item("minecraft:string", 8, 1, 4), item("minecraft:flower_pot", 4),
                        item("minecraft:brick", 6, 1, 4), item(TRIM[people], 6, 1, 3), item(relic, 1)]),
        ]
    return {"type": "minecraft:chest", "pools": pools, "random_sequence": "%s:chests/old_age/%s_%s" % (NS, people, "tomb" if tomb else "house")}


def merge_tag(path, values):
    tag = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            tag = json.load(f)
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write(path, tag)


def main():
    tex = os.path.join(ASSETS, "textures")
    os.makedirs(os.path.join(tex, "entity", "old_age"), exist_ok=True)
    os.makedirs(os.path.join(tex, "item"), exist_ok=True)
    os.makedirs(os.path.join(tex, "block"), exist_ok=True)
    for i, p in enumerate(PEOPLES):
        skin(p, True, 1100 + i).save(os.path.join(tex, "entity", "old_age", "ruler_%s.png" % p))
        skin(p, False, 1200 + i).save(os.path.join(tex, "entity", "old_age", "guard_%s.png" % p))
    for i, (name, draw) in enumerate([("sun_scarab", scarab), ("marble_bust", bust), ("barrow_brooch", brooch), ("jade_seal", seal)]):
        icon(draw, 1300 + i).save(os.path.join(tex, "item", name + ".png"))
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        write(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    for name, shell, spot, seed in (("old_ruler_spawn_egg", (200, 170, 90), (70, 50, 40), 1400), ("tomb_guard_spawn_egg", (150, 140, 120), (150, 50, 40), 1401)):
        egg(shell, spot, seed).save(os.path.join(tex, "item", name + ".png"))
        write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "%s:item/%s" % (NS, name)}})
        write(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s" % (NS, name)}})
    # the seal and the altar
    stone_tex(1500, (96, 90, 86), ring_mark).save(os.path.join(tex, "block", "tomb_seal.png"))
    stone_tex(1501, (120, 112, 104), band_mark).save(os.path.join(tex, "block", "old_altar_side.png"))
    stone_tex(1502, (100, 94, 88)).save(os.path.join(tex, "block", "old_altar_bottom.png"))
    stone_tex(1503, (128, 120, 110), lit=embers).save(os.path.join(tex, "block", "old_altar_top_lit.png"))
    stone_tex(1503, (128, 120, 110), lit=ash).save(os.path.join(tex, "block", "old_altar_top.png"))
    write(os.path.join(ASSETS, "blockstates", "tomb_seal.json"), {"variants": {"": {"model": NS + ":block/tomb_seal"}}})
    write(os.path.join(ASSETS, "models", "block", "tomb_seal.json"), {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/tomb_seal"}})
    write(os.path.join(ASSETS, "items", "tomb_seal.json"), {"model": {"type": "minecraft:model", "model": NS + ":block/tomb_seal"}})
    write(os.path.join(ASSETS, "blockstates", "old_altar.json"), {"variants": {
        "lit=true": {"model": NS + ":block/old_altar_lit"}, "lit=false": {"model": NS + ":block/old_altar"}}})
    for name, top in (("old_altar", "old_altar_top"), ("old_altar_lit", "old_altar_top_lit")):
        write(os.path.join(ASSETS, "models", "block", name + ".json"), {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": NS + ":block/" + top, "side": NS + ":block/old_altar_side", "bottom": NS + ":block/old_altar_bottom"}})
    write(os.path.join(ASSETS, "items", "old_altar.json"), {"model": {"type": "minecraft:model", "model": NS + ":block/old_altar_lit"}})

    # data
    d = os.path.join(DATA, NS)
    write(os.path.join(d, "loot_table", "blocks", "tomb_seal.json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": NS + ":tomb_seal"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": NS + ":blocks/tomb_seal"})
    for p in PEOPLES:
        write(os.path.join(d, "loot_table", "chests", "old_age", p + "_house.json"), loot(p, False))
        write(os.path.join(d, "loot_table", "chests", "old_age", p + "_tomb.json"), loot(p, True))
        write(os.path.join(d, "worldgen", "structure", p + "_ruins.json"), {
            "type": NS + ":old_ruins", "people": p, "biomes": "#%s:has_structure/%s_ruins" % (NS, p), "step": "surface_structures",
            "terrain_adaptation": "none", "spawn_overrides": {}})
        write(os.path.join(d, "tags", "worldgen", "biome", "has_structure", p + "_ruins.json"), {"replace": False, "values": BIOMES[p]})
    write(os.path.join(d, "worldgen", "structure_set", "old_ruins.json"), {
        "structures": [{"structure": "%s:%s_ruins" % (NS, p), "weight": 1} for p in PEOPLES],
        "placement": {"type": "minecraft:random_spread", "spacing": 40, "separation": 14, "salt": 731905117}})
    write(os.path.join(d, "tags", "worldgen", "structure", "old_ruins.json"), {"replace": False, "values": ["%s:%s_ruins" % (NS, p) for p in PEOPLES]})
    shafts = ["minecraft:mineshaft", "minecraft:mineshaft_mesa"]
    for s in ("mineshaft", "dark_oak_mineshaft", "desert_mineshaft", "jungle_mineshaft", "mesa_mineshaft", "mushroom_mineshaft", "snowy_mineshaft"):
        shafts.append({"id": "mmr:" + s, "required": False})
        shafts.append({"id": "mmr:" + s + "_underground", "required": False})
    shafts.append({"id": "mmr:deep_dark_mineshaft_underground", "required": False})
    write(os.path.join(d, "tags", "worldgen", "structure", "old_shafts.json"), {"replace": False, "values": shafts})
    merge_tag(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json"), [NS + ":tomb_seal"])
    print("wrote the old age's art and data")


if __name__ == "__main__":
    main()
