"""Umbrellas, lace parasols, Mohknee paper parasols and the umbrella stand: sprites, textures, models, item definitions,
block state and models. Our own art, drawn here.

An umbrella's look rides in its custom model data (see umbrella/Umbrellas.java): flag 0 = open, colour 0 = canopy,
colour 1 = panel, string 0 = the parasol's panel style "0".."3". Open, the held model is a tall pole with a canopy over the
holder's head; furled, a flat sprite. Stand models are pre-coloured (a block model cannot be tinted by item data).
Run from the repository root: python -I tools/gen_umbrellas.py
"""
import json
import math
import os
from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
TEX = ASSETS + "textures/"
CLEAR = (0, 0, 0, 0)

UMBRELLA_CANOPY = 0x3A5F8F
UMBRELLA_PANEL = 0xE8E0C8
PARASOL_CANOPY = 0xF1E4DC
PARASOL_PANEL = 0xB03A48
PAPER_CANOPY = 0xF2E6C4
PAPER_PANEL = 0xFFFFFF


def argb(rgb):
    v = 0xFF000000 | rgb
    return v - (1 << 32) if v >= (1 << 31) else v


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def tint(rgb, grey):
    return tuple(int(((rgb >> s) & 255) * grey / 255) for s in (16, 8, 0))


# ---- open canopy textures (grey, so a tint colours them) ----

def canopy_tex(ribs=True, dark=0):
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            ang = math.atan2(y - 7.5, x - 7.5)
            gore = int((ang + math.pi) / (2 * math.pi) * 8) % 8
            g = 232 - dark - (14 if gore % 2 else 0)
            img.putpixel((x, y), (g, g, g, 255))
    if ribs:
        for i in range(8):
            a = i * math.pi / 4
            for r in range(0, 11):
                x, y = int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))
                if 0 <= x < 16 and 0 <= y < 16:
                    img.putpixel((x, y), (120 - dark // 2,) * 3 + (255,))
    return img


def lace_tex():
    img = Image.new("RGBA", (16, 16), CLEAR)
    for x in range(16):
        for y in range(16):
            # a scalloped edge hanging off the top, and an eyelet row
            scallop = 5 + int(2 * abs(math.sin((x + 0.5) / 16 * math.pi * 4)))
            if y < scallop:
                eyelet = y == 2 and x % 4 == 1
                img.putpixel((x, y), CLEAR if eyelet else (250, 248, 244, 255))
    return img


def panel_tex(style):
    img = Image.new("RGBA", (16, 16), CLEAR)
    w = (255, 255, 255, 255)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            on = False
            if style == 0:  # a straight cross
                on = abs(dx) < 1.6 or abs(dy) < 1.6
            elif style == 1:  # a saltire
                on = abs(abs(dx) - abs(dy)) < 1.8
            elif style == 2:  # a border ring and a boss
                r = math.hypot(dx, dy)
                on = 5.0 < r < 6.8 or r < 2.0
            else:  # alternate gores
                ang = math.atan2(dy, dx)
                on = int((ang + math.pi) / (2 * math.pi) * 8) % 2 == 0 and math.hypot(dx, dy) < 7.5
            if on:
                img.putpixel((x, y), w)
    return img


def blossoms_tex():
    img = Image.new("RGBA", (16, 16), CLEAR)
    branch = (92, 62, 44, 255)
    for i in range(14):
        img.putpixel((1 + i, 12 - i // 2), branch)
    for cx, cy in ((4, 10), (8, 6), (12, 4), (6, 3), (11, 9)):
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            x, y = cx + dx, cy + dy
            if 0 <= x < 16 and 0 <= y < 16:
                img.putpixel((x, y), (250, 190, 205, 255) if (dx, dy) != (0, 0) else (255, 236, 120, 255))
    return img


def pole_tex(bamboo=False):
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            if bamboo:
                c = (168, 176, 92) if y % 8 else (112, 120, 56)
            else:
                c = (110, 74, 44) if x % 4 else (84, 56, 34)
            img.putpixel((x, y), c + (255,))
    return img


# ---- furled sprites ----

def furled(cloth, band, handle):
    """Three layers: cloth (tint 0), band (tint 1), handle and tip (untinted)."""
    c, b, h = (Image.new("RGBA", (16, 16), CLEAR) for _ in range(3))
    for i in range(10):
        x, y = 4 + i, 11 - i
        for ox, oy in ((0, 0), (1, 0), (0, 1)):
            shade = 235 - 20 * (ox + oy)
            c.putpixel((x + ox, y + oy), (shade,) * 3 + (255,))
    for i in (4, 5):
        x, y = 4 + i, 11 - i
        for ox, oy in ((0, 0), (1, 0), (0, 1)):
            b.putpixel((x + ox, y + oy), (255, 255, 255, 255))
    for i in range(4):
        h.putpixel((3 - i // 2, 12 + i // 2 + (1 if i else 0)), handle + (255,))
    h.putpixel((2, 13), handle + (255,))
    h.putpixel((1, 14), handle + (255,))
    h.putpixel((1, 15), handle + (255,))
    h.putpixel((2, 15), handle + (255,))
    h.putpixel((14, 1), (200, 200, 205, 255))
    h.putpixel((13, 2), (200, 200, 205, 255))
    return c, b, h


def stand_tex(rgb, name):
    img = canopy_tex(ribs=True)
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = tuple(int(((rgb >> s) & 255) * r / 255) for s in (16, 8, 0)) + (a,)
    save(img, TEX + "block/umbrella_stand_" + name + ".png")


# ---- models ----

def box(frm, to, tex, tintindex=None, faces="neswud"):
    names = {"n": "north", "e": "east", "s": "south", "w": "west", "u": "up", "d": "down"}
    out = {}
    for f in faces:
        face = {"texture": tex}
        if tintindex is not None:
            face["tintindex"] = tintindex
        out[names[f]] = face
    return {"from": frm, "to": to, "faces": out}


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2, 0.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [3, -3, 2], "scale": [0.8, 0.8, 0.8]},
    "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [-3, -3, 2], "scale": [0.8, 0.8, 0.8]},
    "gui": {"rotation": [30, 45, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
}


def open_model(canopy, pole, lace=False, panel=None, blossoms=False):
    textures = {"canopy": canopy, "pole": pole, "particle": canopy}
    el = [
        box([7.4, -6, 7.4], [8.6, 26, 8.6], "#pole"),
        box([-4, 21, -4], [20, 22, 20], "#canopy", 0),
        box([-3, 22, -3], [19, 24, 19], "#canopy", 0, "neswd"),
        box([-3, 24, -3], [19, 24.01, 19], "#canopy", 0, "u"),
        box([5, 24, 5], [11, 26, 11], "#pole"),
        box([7.4, 26, 7.4], [8.6, 29, 8.6], "#pole"),
    ]
    if lace:
        textures["lace"] = "jaymospowers:item/umbrella/lace"
        el.append(box([-4.02, 20.98, -4.02], [20.02, 22.02, 20.02], "#lace", None, "newsd"))
    if panel is not None:
        textures["panel"] = "jaymospowers:item/umbrella/panel_%d" % panel
        el.append(box([-3, 24.02, -3], [19, 24.03, 19], "#panel", 1, "u"))
    if blossoms:
        textures["blossoms"] = "jaymospowers:item/umbrella/blossoms"
        el.append(box([-3, 24.02, -3], [19, 24.03, 19], "#blossoms", None, "u"))
    return {"textures": textures, "elements": el, "display": DISPLAY}


def furled_model(name):
    return {"parent": "minecraft:item/generated", "textures": {
        "layer0": "jaymospowers:item/umbrella/%s_cloth" % name,
        "layer1": "jaymospowers:item/umbrella/%s_band" % name,
        "layer2": "jaymospowers:item/umbrella/%s_handle" % name}}


def tints(canopy, panel):
    return [{"type": "minecraft:custom_model_data", "index": 0, "default": argb(canopy)},
            {"type": "minecraft:custom_model_data", "index": 1, "default": argb(panel)}]


def model_ref(path, canopy, panel):
    return {"type": "minecraft:model", "model": "jaymospowers:item/umbrella/" + path, "tints": tints(canopy, panel)}


def item_def(opened, closed):
    return {"model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
                      "on_true": opened, "on_false": closed}}


def stand_models():
    base = [box([4, 0, 4], [12, 1, 12], "#base"), box([4, 1, 4], [12, 6, 5], "#base", None, "nesw"),
            box([4, 1, 11], [12, 6, 12], "#base", None, "nesw"), box([4, 1, 5], [5, 6, 11], "#base", None, "nesw"),
            box([11, 1, 5], [12, 6, 11], "#base", None, "nesw")]
    t = {"base": "minecraft:block/iron_block", "particle": "minecraft:block/iron_block"}
    write(ASSETS + "models/block/umbrella_stand_empty.json", {"textures": t, "elements": base})
    for i, (kind, pole) in enumerate((("umbrella", False), ("parasol", False), ("paper", True))):
        tt = dict(t, canopy="jaymospowers:block/umbrella_stand_" + kind,
                  pole="jaymospowers:item/umbrella/" + ("bamboo" if pole else "pole"))
        write(ASSETS + "models/block/umbrella_stand_closed_%d.json" % i, {"textures": tt, "elements": base + [
            box([6.5, 1, 6.5], [9.5, 20, 9.5], "#canopy"), box([7.3, 20, 7.3], [8.7, 25, 8.7], "#pole")]})
        write(ASSETS + "models/block/umbrella_stand_open_%d.json" % i, {"textures": tt, "elements": base + [
            box([7.4, 1, 7.4], [8.6, 28, 8.6], "#pole"), box([-4, 24, -4], [20, 25, 20], "#canopy"),
            box([-3, 25, -3], [19, 27, 19], "#canopy"), box([7.4, 27, 7.4], [8.6, 30, 8.6], "#pole")]})


def main():
    save(canopy_tex(), TEX + "item/umbrella/canopy.png")
    save(canopy_tex(dark=40), TEX + "item/umbrella/canopy_paper.png")
    save(lace_tex(), TEX + "item/umbrella/lace.png")
    for s in range(4):
        save(panel_tex(s), TEX + "item/umbrella/panel_%d.png" % s)
    save(blossoms_tex(), TEX + "item/umbrella/blossoms.png")
    save(pole_tex(), TEX + "item/umbrella/pole.png")
    save(pole_tex(True), TEX + "item/umbrella/bamboo.png")
    for name, handle in (("umbrella", (110, 74, 44)), ("parasol", (230, 224, 210)), ("paper_parasol", (168, 176, 92))):
        c, b, h = furled(None, None, handle)
        save(c, TEX + "item/umbrella/%s_cloth.png" % name)
        save(b, TEX + "item/umbrella/%s_band.png" % name)
        save(h, TEX + "item/umbrella/%s_handle.png" % name)
        write(ASSETS + "models/item/umbrella/%s_closed.json" % name, furled_model(name))
    stand_tex(UMBRELLA_CANOPY, "umbrella")
    stand_tex(0xE9C9C4, "parasol")
    stand_tex(0xF2E6C4, "paper")
    stand_models()

    write(ASSETS + "models/item/umbrella/umbrella_open.json", open_model(
        "jaymospowers:item/umbrella/canopy", "jaymospowers:item/umbrella/pole"))
    write(ASSETS + "models/item/umbrella/paper_parasol_open.json", open_model(
        "jaymospowers:item/umbrella/canopy_paper", "jaymospowers:item/umbrella/bamboo", blossoms=True))
    for s in range(4):
        write(ASSETS + "models/item/umbrella/parasol_open_%d.json" % s, open_model(
            "jaymospowers:item/umbrella/canopy", "jaymospowers:item/umbrella/pole", lace=True, panel=s))

    write(ASSETS + "items/umbrella.json", item_def(
        model_ref("umbrella_open", UMBRELLA_CANOPY, UMBRELLA_PANEL), model_ref("umbrella_closed", UMBRELLA_CANOPY, UMBRELLA_PANEL)))
    write(ASSETS + "items/paper_parasol.json", item_def(
        model_ref("paper_parasol_open", PAPER_CANOPY, PAPER_PANEL), model_ref("paper_parasol_closed", PAPER_CANOPY, PAPER_PANEL)))
    cases = [{"when": str(s), "model": model_ref("parasol_open_%d" % s, PARASOL_CANOPY, PARASOL_PANEL)} for s in range(4)]
    write(ASSETS + "items/parasol.json", item_def(
        {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
         "fallback": model_ref("parasol_open_0", PARASOL_CANOPY, PARASOL_PANEL)},
        model_ref("parasol_closed", PARASOL_CANOPY, PARASOL_PANEL)))
    write(ASSETS + "items/umbrella_stand.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:block/umbrella_stand_empty"}})

    variants = {}
    for contents in ("empty", "closed", "open"):
        for k in range(3):
            model = "umbrella_stand_empty" if contents == "empty" else "umbrella_stand_%s_%d" % (contents, k)
            variants["contents=%s,kind=%d" % (contents, k)] = {"model": "jaymospowers:block/" + model}
    write(ASSETS + "blockstates/umbrella_stand.json", {"variants": variants})

    data = "src/main/resources/data/jaymospowers/"
    write(data + "tags/item/umbrellas.json", {"values": ["jaymospowers:umbrella", "jaymospowers:parasol", "jaymospowers:paper_parasol"]})
    write(data + "loot_table/blocks/umbrella_stand.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "jaymospowers:umbrella_stand"}]}],
        "random_sequence": "jaymospowers:blocks/umbrella_stand"})

    def shaped(name, key, pattern, result):
        write(data + "recipe/umbrella/%s.json" % name, {"type": "minecraft:crafting_shaped", "category": "equipment", "key": key,
                                                         "pattern": pattern, "result": {"id": "jaymospowers:" + result}})

    shaped("umbrella", {"W": "minecraft:white_wool", "S": "minecraft:stick"}, ["WWW", " S ", " S "], "umbrella")
    shaped("parasol", {"T": "minecraft:string", "C": "minecraft:white_carpet", "S": "minecraft:stick"}, ["TTT", "CCC", " S "], "parasol")
    shaped("paper_parasol", {"P": "minecraft:paper", "H": "minecraft:honeycomb", "B": "minecraft:bamboo"}, ["PPP", "PHP", " B "], "paper_parasol")
    shaped("umbrella_stand", {"S": "minecraft:stick", "I": "minecraft:iron_ingot"}, ["S S", " S ", "III"], "umbrella_stand")

    lang = {
        "item.jaymospowers.umbrella": "Umbrella",
        "item.jaymospowers.umbrella.hint": "Use to open or furl. Open, it keeps the rain off you and slows a fall; over campfire smoke it lifts you. It wears.",
        "item.jaymospowers.parasol": "Lace Parasol",
        "item.jaymospowers.parasol.hint": "Use to open or furl. Keeps the sun and rain off. Too frail to fall with: it turns inside out.",
        "item.jaymospowers.paper_parasol": "Paper Parasol",
        "item.jaymospowers.paper_parasol.hint": "Oiled paper over bamboo ribs, painted with blossom. Keeps the sun and rain off; tears in a fall.",
        "block.jaymospowers.umbrella_stand": "Umbrella Stand",
    }
    write(ASSETS + "lang/parts/umbrella.json", lang)


if __name__ == "__main__":
    main()
