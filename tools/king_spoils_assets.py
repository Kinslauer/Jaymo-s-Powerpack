"""Writes the Havens patch 5 spoils' art and models (our own pixel art, drawn here):

* Powers: the kings' weapons (armory/KingWeapons: Gutting Hook, Warchief's Cleaver, Ogre King's Maul, 32x32 like the
  Armory's) and their iron heads (tinkering/WeaponParts KINGS, 16x16, drawn by weapon_parts_assets.py's DRAWN).
* Kingdoms: the trophies (Spoils: captain's tusk, goblin king's ring, necklace of tusks, Ogre King's hide) and the three
  monster crowns (block models, worn on the head through the "head" display).

    python -I tools/king_spoils_assets.py [preview.png]
"""
import json
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import weapon_parts_assets as wpa  # noqa: E402

POWERS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymospowers")
KINGDOMS = os.path.join(HERE, "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")

KING_HEADS = ["gutting_hook_blade", "warchief_cleaver_blade", "ogre_maul_head"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def dark(c, f):
    return tuple(max(0, int(v * f)) for v in c[:3]) + (255,)


def outline(img, factor=0.35, colours=None):
    """Every opaque pixel that touches a clear one (4 ways) darkens into its outline."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            p = src.getpixel((x, y))
            if p[3] == 0:
                continue
            edge = any(not (0 <= x + dx < w and 0 <= y + dy < h) or src.getpixel((x + dx, y + dy))[3] == 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge:
                img.putpixel((x, y), (colours or {}).get(p[:3], dark(p, factor)))
    return img


def shine(img, colour, light):
    """Pixels of `colour` with clear (or outline) to their upper left catch the light."""
    w, h = img.size
    src = img.copy()
    for y in range(1, h):
        for x in range(1, w):
            if src.getpixel((x, y))[:3] == colour[:3]:
                up = src.getpixel((x - 1, y - 1))
                if up[3] == 0 or sum(up[:3]) < sum(colour[:3]) * 0.6:
                    img.putpixel((x, y), light)


def thick(d, a, b, width, colour):
    d.line([a, b], fill=colour, width=width)


# ---- palettes ----------------------------------------------------------------------------------------------------------
IRON, IRON_LIGHT, IRON_DARK = (168, 168, 168, 255), (216, 216, 216, 255), (110, 110, 116, 255)
RUSTY, RUST = (150, 142, 124, 255), (142, 84, 44, 255)
BLACK_IRON, BLACK_LIGHT = (84, 84, 92, 255), (134, 134, 144, 255)
WOOD, WOOD_LIGHT = (104, 78, 30, 255), (137, 103, 39, 255)
DARK_WOOD = (74, 52, 24, 255)
RAG, RAG_DARK = (92, 120, 58, 255), (62, 84, 40, 255)
BONE, BONE_LIGHT, BONE_DARK = (226, 218, 188, 255), (248, 244, 226, 255), (176, 164, 128, 255)
LEATHER, LEATHER_DARK = (122, 78, 46, 255), (86, 52, 30, 255)
STONE, STONE_LIGHT = (122, 118, 110, 255), (156, 152, 142, 255)
GOLD, GOLD_LIGHT, GOLD_DARK = (230, 186, 50, 255), (255, 240, 140, 255), (172, 116, 22, 255)
EMERALD, EMERALD_LIGHT = (40, 170, 70, 255), (130, 236, 140, 255)
HIDE, HIDE_DARK, HIDE_LIGHT = (120, 112, 92, 255), (86, 78, 62, 255), (150, 142, 118, 255)
BRONZE, BRONZE_LIGHT = (160, 104, 56, 255), (206, 150, 96, 255)
RED_GEM = (178, 34, 34, 255)


# ---- the kings' weapons (32x32) ----------------------------------------------------------------------------------------
def gutting_hook():
    img = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(img)
    thick(d, (3, 28), (13, 18), 3, WOOD)
    for t in (0.25, 0.5, 0.75):
        x, y = 3 + 10 * t, 28 - 10 * t
        d.point([(int(x), int(y)), (int(x) + 1, int(y)), (int(x), int(y) - 1), (int(x) + 1, int(y) + 1)], fill=RAG)
    thick(d, (12, 17), (16, 21), 2, RUSTY)  # a crude guard
    # the blade, and the hook curling back over at its tip
    d.polygon([(12, 18), (15, 20), (25, 10), (24, 7), (20, 7)], fill=RUSTY)
    d.polygon([(20, 7), (23, 3), (27, 3), (30, 6), (30, 10), (27, 13), (26, 10), (27, 8), (26, 6), (24, 6), (24, 8)], fill=RUSTY)
    d.point([(19, 12), (24, 5), (28, 8)], fill=RUST)
    thick(d, (15, 18), (23, 10), 1, IRON_LIGHT)  # the cutting edge
    img = outline(img, 0.3)
    return img


def warchief_cleaver():
    img = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(img)
    thick(d, (4, 27), (12, 19), 3, DARK_WOOD)
    d.ellipse([1, 27, 5, 31], fill=BONE)  # a skull-knob pommel
    for t in (0.3, 0.65):
        x, y = int(4 + 8 * t), int(27 - 8 * t)
        d.point([(x, y), (x + 1, y), (x, y + 1)], fill=LEATHER)
    # a slab of black iron, wide at the end, notched along its back
    d.polygon([(10, 19), (13, 22), (27, 8), (27, 3), (22, 2), (19, 6), (11, 15)], fill=BLACK_IRON)
    for x, y in ((21, 3), (24, 3), (16, 9)):
        d.point([(x, y)], fill=(0, 0, 0, 0))
    thick(d, (13, 21), (27, 7), 1, BLACK_LIGHT)  # its edge
    d.polygon([(18, 7), (21, 2), (19, 7)], fill=BONE)  # bone spikes along the back
    d.polygon([(14, 11), (14, 6), (15, 11)], fill=BONE)
    d.point([(20, 12), (23, 9)], fill=RED_GEM)  # old blood
    img = outline(img, 0.35)
    return img


def ogre_kings_maul():
    img = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(img)
    thick(d, (2, 29), (19, 12), 3, WOOD)
    for t in (0.15, 0.3):
        x, y = int(2 + 17 * t), int(29 - 17 * t)
        d.point([(x, y), (x + 1, y), (x, y - 1), (x + 1, y - 1)], fill=LEATHER)
    # the head: a great block set across the haft, bound in iron
    d.polygon([(13, 11), (21, 3), (29, 11), (21, 19)], fill=STONE)
    thick(d, (15, 9), (23, 17), 2, IRON)
    thick(d, (19, 5), (27, 13), 2, IRON)
    d.point([(17, 11), (21, 7), (25, 11), (21, 15)], fill=STONE_LIGHT)
    d.point([(18, 13), (22, 11), (24, 9)], fill=IRON_DARK)
    img = outline(img, 0.35)
    shine(img, STONE, STONE_LIGHT)
    return img


# ---- the kings' heads (16x16, drawn by weapon_parts_assets.DRAWN) ------------------------------------------------------
def heads():
    for part in KING_HEADS:
        heads_ = wpa.sprites("drawn:" + part, None)
        cases = []
        for metal in ["stone", "iron"]:
            path = wpa.OUT + part + "_" + metal
            os.makedirs(os.path.join(POWERS, "textures", wpa.OUT), exist_ok=True)
            heads_[metal].save(os.path.join(POWERS, "textures", path + ".png"))
            write(os.path.join(POWERS, "models", path + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
            cases.append({"when": metal, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
        # a king's head is iron: that's its fallback too
        write(os.path.join(POWERS, "items", part + ".json"), {"model": wpa.hot({
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[1]["model"]})})


# ---- Kingdoms' trophies (16x16) ----------------------------------------------------------------------------------------
def captains_tusk():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    d.line([(3, 1), (6, 5)], fill=LEATHER)  # the cord, tied round its root
    d.line([(12, 1), (9, 5)], fill=LEATHER)
    d.polygon([(5, 5), (10, 5), (11, 8), (11, 11), (9, 14), (6, 15), (8, 12), (8, 9), (6, 7)], fill=BONE)
    d.line([(6, 5), (9, 5)], fill=LEATHER_DARK)
    d.point([(9, 7), (10, 9), (10, 10)], fill=BONE_LIGHT)
    return outline(img, 0.55)


def goblin_kings_ring():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    d.ellipse([3, 4, 13, 14], outline=GOLD, width=2)
    d.ellipse([6, 1, 10, 6], fill=EMERALD)
    d.point([(7, 2)], fill=EMERALD_LIGHT)
    d.point([(5, 7), (4, 9)], fill=GOLD_LIGHT)
    return outline(img, 0.5)


def necklace_of_tusks():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    cord = [(1, 1), (2, 4), (4, 6), (6, 7), (9, 7), (11, 6), (13, 4), (14, 1)]
    d.line(cord, fill=LEATHER)
    for x, y in ((2, 5), (5, 7), (9, 8), (12, 5)):
        d.polygon([(x, y), (x + 2, y), (x + 2, y + 3), (x + 1, y + 5), (x, y + 3)], fill=BONE)
        d.point([(x + 1, y + 1)], fill=BONE_LIGHT)
    return outline(img, 0.55)


def ogre_kings_hide():
    img = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(img)
    d.polygon([(4, 1), (11, 1), (13, 5), (14, 14), (11, 13), (8, 15), (5, 13), (1, 14), (2, 5)], fill=HIDE)
    d.line([(4, 2), (11, 2)], fill=BONE)  # a bone clasp across the shoulders
    d.point([(7, 2), (8, 2)], fill=BONE_DARK)
    for x, y in ((5, 6), (10, 7), (7, 10), (4, 11), (11, 11)):
        d.point([(x, y)], fill=HIDE_DARK)
    d.line([(3, 5), (3, 9)], fill=HIDE_LIGHT)
    return outline(img, 0.55)


# ---- Kingdoms' crowns (block models; 16x16 textures) -------------------------------------------------------------------
def crown_texture(base, light, gem, darkf=0.55):
    img = Image.new("RGBA", (16, 16), base)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (15, 0)], fill=light)
    d.line([(0, 15), (15, 15)], fill=dark(base, darkf))
    for x in range(1, 16, 4):
        d.rectangle([x, 6, x + 1, 9], fill=gem)
        d.point([(x, 6)], fill=light)
    return img


def crown_model(name, tex, low, high, height, points, point_height, head_scale, head_y):
    """A ring band from low to high (x, z), `height` tall, with `points` (x, z, w) standing `point_height` above it."""
    def box(f, t, texture):
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            if face in ("north", "south"):
                uv = [f[0], 16 - t[1], t[0], 16 - f[1]]
            elif face in ("east", "west"):
                uv = [f[2], 16 - t[1], t[2], 16 - f[1]]
            else:
                uv = [f[0], f[2], t[0], t[2]]
            faces[face] = {"uv": uv, "texture": texture}
        return {"from": f, "to": t, "faces": faces}
    els = [box([low, 0, low], [high, height, low + 1], "#band"), box([low, 0, high - 1], [high, height, high], "#band"),
           box([low, 0, low + 1], [low + 1, height, high - 1], "#band"), box([high - 1, 0, low + 1], [high, height, high - 1], "#band")]
    for x, z, w in points:
        els.append(box([x, height, z], [x + w, height + point_height, z + w], "#point"))
    write(os.path.join(KINGDOMS, "models", "block", name + ".json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex["band"], "band": tex["band"], "point": tex["point"]},
        "elements": els,
        "display": {
            "head": {"rotation": [0, 0, 0], "translation": [0, head_y, 0], "scale": [head_scale] * 3},
            "gui": {"rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        }})
    write(os.path.join(KINGDOMS, "blockstates", name + ".json"), {"variants": {"": {"model": "jaymoskingdoms:block/" + name}}})
    write(os.path.join(KINGDOMS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/" + name}})
    loot = os.path.join(KINGDOMS, "..", "..", "data", "jaymoskingdoms", "loot_table", "blocks", name + ".json")
    write(loot, {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                        "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:" + name}]}],
                 "random_sequence": "jaymoskingdoms:blocks/" + name})


def ring_points(low, high, w):
    """Points at the corners and the middles of each side."""
    mid = (low + high - w) // 2
    spots = [(low, low), (high - w, low), (low, high - w), (high - w, high - w), (mid, low), (mid, high - w), (low, mid), (high - w, mid)]
    return [(x, z, w) for x, z in spots]


def crowns():
    block = os.path.join(KINGDOMS, "textures", "block")
    os.makedirs(block, exist_ok=True)
    crown_texture(GOLD, GOLD_LIGHT, EMERALD).save(os.path.join(block, "goblin_kings_crown.png"))
    crown_texture(BLACK_IRON, BLACK_LIGHT, RED_GEM).save(os.path.join(block, "orc_kings_crown.png"))
    Image.new("RGBA", (16, 16), BONE).save(os.path.join(block, "orc_kings_crown_spike.png"))
    crown_texture(BRONZE, BRONZE_LIGHT, STONE).save(os.path.join(block, "ogre_kings_crown.png"))
    # head displays: the band sits on top of the head, a little wider than it (see Spoils' crowns)
    gob = "jaymoskingdoms:block/goblin_kings_crown"
    crown_model("goblin_kings_crown", {"band": gob, "point": gob}, 3, 13, 3, ring_points(3, 13, 1), 2, 1.35, 16.4)
    orc = "jaymoskingdoms:block/orc_kings_crown"
    crown_model("orc_kings_crown", {"band": orc, "point": orc + "_spike"}, 3, 13, 3,
                [(x, z, w) for x, z, w in ring_points(3, 13, 1)][:4] + [(7, 3, 2), (7, 11, 2)], 4, 1.35, 16.4)
    ogre = "jaymoskingdoms:block/ogre_kings_crown"
    crown_model("ogre_kings_crown", {"band": ogre, "point": ogre}, 2, 14, 4, ring_points(2, 14, 2), 2, 1.15, 14.8)


def main(preview=None):
    armory = os.path.join(POWERS, "textures", "item", "armory")
    weapons = {"gutting_hook": gutting_hook(), "warchief_cleaver": warchief_cleaver(), "ogre_kings_maul": ogre_kings_maul()}
    for name, img in weapons.items():
        img.save(os.path.join(armory, name + ".png"))
        write(os.path.join(POWERS, "models", "item", "armory", name + ".json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/armory/" + name}})
        write(os.path.join(POWERS, "items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": "jaymospowers:item/armory/" + name}})
    heads()
    item = os.path.join(KINGDOMS, "textures", "item")
    trophies = {"captains_tusk": captains_tusk(), "goblin_kings_ring": goblin_kings_ring(),
                "necklace_of_tusks": necklace_of_tusks(), "ogre_kings_hide": ogre_kings_hide()}
    for name, img in trophies.items():
        img.save(os.path.join(item, name + ".png"))
        write(os.path.join(KINGDOMS, "models", "item", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
        write(os.path.join(KINGDOMS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}})
    crowns()
    if preview:
        sheet = Image.new("RGBA", (32 * 10, 32 * 2), (60, 90, 120, 255))
        for i, img in enumerate(weapons.values()):
            sheet.alpha_composite(img, (i * 32, 0))
        for i, part in enumerate(KING_HEADS):
            sheet.alpha_composite(Image.open(os.path.join(POWERS, "textures", wpa.OUT + part + "_iron.png")), (96 + i * 32, 0))
        for i, img in enumerate(trophies.values()):
            sheet.alpha_composite(img, (i * 32, 32))
        for i, n in enumerate(("goblin_kings_crown", "orc_kings_crown", "ogre_kings_crown")):
            sheet.alpha_composite(Image.open(os.path.join(KINGDOMS, "textures", "block", n + ".png")), (128 + i * 32, 32))
        sheet.resize((sheet.width * 6, sheet.height * 6), Image.NEAREST).save(preview)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
