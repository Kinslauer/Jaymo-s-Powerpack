"""Alchemy track E (essence enchanting and the artificer's gear): textures, item models and definitions, equipment layers,
crafting / crucible / infusion recipes, tags, and the lang part (assets/jaymospowers/lang/parts/alchemy_gear.json).

Textures are painted as pixel grids after docs/Vanilla-Style.md: 16x16, no black outlines (the outline is the darkest
shade of what it wraps), light from the top-left, 5 to 18 colours each. Drawn from nothing; no vanilla or other mod's
pixels are read. Each icon is a small ASCII mask: every letter names a colour in that icon's legend.

Run from the repository root: python3 -I tools/gen_alchemy_gear.py
"""
import json
import os
import random

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers/"
DATA = "src/main/resources/data/jaymospowers/"
ITEM_TEX = ASSETS + "textures/item/alchemy/"
EQUIP_TEX = ASSETS + "textures/entity/equipment/"
random.seed(2605)


def hexc(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


CLEAR = (0, 0, 0, 0)
# dark to light
ICHOR = [hexc(c) for c in ("4a1f08", "6e2f0b", "a8581a", "d68222", "f2b430", "ffd95e", "fff3a6")]
VIOLET = [hexc(c) for c in ("2b1a4a", "432a78", "6b4fb0", "9b7ae0", "c9b8ff")]
WOOD = [hexc(c) for c in ("3b2410", "5c3a18", "7d5226", "a06c36")]
CLOTH = [hexc(c) for c in ("3a2e5c", "54417f", "7459a8", "9a7fd0", "c4aef0")]
TRIM = [hexc(c) for c in ("8a5a10", "c48a1c", "f2b430", "ffd95e")]
IRON = [hexc(c) for c in ("3d3d45", "5a5a64", "7b7b86", "a1a1ad", "c9c9d3")]
GLASS = [hexc(c) for c in ("6d7f8f", "93a8ba", "bcd0e0", "e6f3fb")]
FLAME = [hexc(c) for c in ("e2572b", "ff9b21", "ffd95e", "fff7ad")]
WHITE = [hexc(c) for c in ("b9b2a2", "d6d0c2", "ece8de", "fbf9f3")]
SALT = [hexc(c) for c in ("9aa7bd", "c3cddc", "e1e8f1", "ffffff")]


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def paint(rows, legend):
    """An icon from 16 strings of 16 letters; '.' is clear."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), rows
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = legend[ch]
    return img


def ichor_legend():
    # a: highlight ... f: darkest outline; g: tip glint
    return {"a": ICHOR[6], "b": ICHOR[5], "c": ICHOR[4], "d": ICHOR[3], "e": ICHOR[2], "f": ICHOR[1], "g": ICHOR[0],
            "w": WOOD[2], "x": WOOD[1], "y": WOOD[3], "z": WOOD[0], "t": TRIM[1], "u": TRIM[0], "v": TRIM[2]}


# ---------------------------------------------------------------- ichorium
def ichorium_ingot():
    rows = [
        "................",
        "................",
        "................",
        "................",
        "..........gggg..",
        "........ggbbbbg.",
        "......ggbbaabcdg",
        "....ggbbbbbbcddg",
        "...gbbbbbbcddeeg",
        "...gcccccddeeeg.",
        "...gddddddeeefg.",
        "....gggggggggg..",
        "................",
        "................",
        "................",
        "................",
    ]
    save(paint(rows, ichor_legend()), ITEM_TEX + "ichorium_ingot.png")


def ichorium_sword():
    rows = [
        "..............ga",
        ".............gba",
        "............gbca",
        "...........gbcd.",
        "..........gbcd..",
        ".........gbcd...",
        "..g.....gbcd....",
        "..tg...gbcd.....",
        "...tgugbcd......",
        "....utbcd.......",
        "....ttuvg.......",
        "...wxut.........",
        "..wxz...........",
        ".yxz............",
        ".zz.............",
        "................",
    ]
    save(paint(rows, ichor_legend()), ITEM_TEX + "ichorium_sword.png")


def ichorium_pickaxe():
    rows = [
        "................",
        "......gggggg....",
        "....ggbbbccdfg..",
        "...gbbaccddeefg.",
        "..gcb.gg..gddefg",
        "..gc.gwxg..gdefg",
        "......gwxg..gdfg",
        ".....gwxg....gfg",
        "....gwxg.....gg.",
        "...gwxg.........",
        "..gwxg..........",
        ".gwxg...........",
        ".yxz............",
        ".zz.............",
        "................",
        "................",
    ]
    save(paint(rows, ichor_legend()), ITEM_TEX + "ichorium_pickaxe.png")


def ichorium_axe():
    rows = [
        "................",
        "........ggg.....",
        "......ggbbcg....",
        ".....gbbaccdg...",
        ".....gbcgcddeg..",
        ".....gccgwdeefg.",
        "......gdgwxeefg.",
        ".......gwxgeffg.",
        "......gwxg.gfg..",
        ".....gwxg..gg...",
        "....gwxg........",
        "...gwxg.........",
        "..gwxg..........",
        ".yxzg...........",
        ".zz.............",
        "................",
    ]
    save(paint(rows, ichor_legend()), ITEM_TEX + "ichorium_axe.png")


# ---------------------------------------------------------------- ichorcloth icons
def cloth_legend():
    return {"a": CLOTH[4], "b": CLOTH[3], "c": CLOTH[2], "d": CLOTH[1], "e": CLOTH[0], "t": TRIM[2], "u": TRIM[1], "v": TRIM[0],
            "w": ICHOR[5]}


def cloth_hood():
    rows = [
        "................",
        "................",
        ".....eeeeee.....",
        "....ebbbbbce....",
        "...ebbaabbbce...",
        "...ebabbbbbce...",
        "..ebabeeeebcce..",
        "..ebbe....ebce..",
        "..ebce....ecde..",
        "..ebce....ecde..",
        "..ebcte..etcde..",
        "..ecdue..eudde..",
        "..eddvee.evdde..",
        "...eeeee.eeeee..",
        "................",
        "................",
    ]
    save(paint(rows, cloth_legend()), ITEM_TEX + "ichorcloth_hood.png")


def cloth_robe():
    rows = [
        "................",
        "..eee......eee..",
        ".ebbce.tt.ebbce.",
        ".ebccbeuuebccde.",
        ".ebcbbbewebbcde.",
        ".ecbbbbbbbbbcde.",
        ".ecdbbbbbbbbcde.",
        ".ecdcbbbbbbddde.",
        ".eddcbbtttbcdde.",
        "..eecbbbwbbcee..",
        "...ecbbbbbbcde..",
        "...ecbbbbbbcde..",
        "...ecdbbbbbdde..",
        "...eddcbbbcdde..",
        "...eeddcccddee..",
        ".....eeeeeee....",
    ]
    save(paint(rows, cloth_legend()), ITEM_TEX + "ichorcloth_robe.png")


def cloth_leggings():
    rows = [
        "................",
        "..eeeeeeeeeeee..",
        "..ebbbbbbbbbce..",
        "..ebcttttttcce..",
        "..ebbbbbbbbbce..",
        "..ecbbbeebbcde..",
        "..ecbbe..ebcde..",
        "..ecbce..ecdde..",
        "..ecbce..ecdde..",
        "..ecdce..ecdde..",
        "..ecdce..eddde..",
        "..ecdce..eddde..",
        "..eddce..eddee..",
        "..eedee..eeee...",
        "................",
        "................",
    ]
    save(paint(rows, cloth_legend()), ITEM_TEX + "ichorcloth_leggings.png")


def cloth_boots():
    rows = [
        "................",
        "................",
        "................",
        "................",
        "..eeee....eeee..",
        "..ebbce..ebbce..",
        "..ebtce..ebtce..",
        "..ecbce..ecbce..",
        "..ecdce..ecdce..",
        ".eebcdde.eebcdde",
        ".ebbcdde.ebbcdde",
        ".eccddde.eccddde",
        ".eddddee.eddddee",
        ".eeeeee..eeeeee.",
        "................",
        "................",
    ]
    save(paint(rows, cloth_legend()), ITEM_TEX + "ichorcloth_boots.png")


def armour_layers():
    """The worn cloth: 64x32 sheets for the two humanoid layers, painted in broad cloth with gold hems, so every part of
    the body model that is used (head, body, arms, legs) is covered. Parts the model never reads stay clear."""
    for layer in ("humanoid", "humanoid_leggings"):
        img = Image.new("RGBA", (64, 32), CLEAR)
        px = img.load()
        regions = {
            "humanoid": [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)],
            "humanoid_leggings": [(16, 16, 40, 32), (0, 16, 16, 32)],
        }[layer]
        for (x0, y0, x1, y1) in regions:
            for x in range(x0, x1):
                for y in range(y0, y1):
                    # woven: a faint diagonal twill, lit from the top left
                    base = 2 + (1 if (x + y) % 4 == 0 else 0) - (1 if (x - y) % 7 == 0 else 0)
                    if y == y0 and layer == "humanoid":
                        base = 4
                    px[x, y] = CLOTH[max(0, min(4, base))]
            # a gold hem along the bottom of each part, and a thin one above it
            for x in range(x0, x1):
                px[x, y1 - 1] = TRIM[1]
                px[x, y1 - 2] = TRIM[2] if (x % 4) < 2 else TRIM[1]
        if layer == "humanoid":
            # the hood's gold edge round the face, and a rune on the chest
            for x in range(8, 16):
                px[x, 8] = TRIM[2]
            for x, y in ((27, 21), (28, 21), (27, 22), (28, 23), (27, 24)):
                px[x, y] = ICHOR[5]
        save(img, EQUIP_TEX + layer + "/ichorcloth.png")
    write(ASSETS + "equipment/ichorcloth.json", {"layers": {
        "humanoid": [{"texture": "jaymospowers:ichorcloth"}],
        "humanoid_leggings": [{"texture": "jaymospowers:ichorcloth"}]}})


# ---------------------------------------------------------------- rod, foci, bound block, cloth, lantern
def rod_legend():
    return {"a": ICHOR[6], "b": ICHOR[5], "c": ICHOR[4], "d": ICHOR[3], "e": ICHOR[2], "f": ICHOR[1], "g": ICHOR[0],
            "t": TRIM[2], "u": TRIM[1], "v": TRIM[0], "w": WOOD[2], "x": WOOD[1], "y": WOOD[3], "z": WOOD[0],
            "p": VIOLET[3], "q": VIOLET[2], "r": VIOLET[1], "s": VIOLET[4]}


def rod(focused):
    rows = [
        "................",
        "............gg..",
        "...........gbcg.",
        "..........gtucvg",
        "..........gucvg.",
        "........gwuvg...",
        ".......gwxg.....",
        "......gwxg......",
        ".....gwxg.......",
        "....gwxg........",
        "...gwxg.........",
        "..gtwg..........",
        ".gtucg..........",
        ".gvug...........",
        "..gg............",
        "................",
    ]
    if focused:
        # a crystal rides in the claw
        rows[1] = "...........grsg."
        rows[2] = "..........grpsqg"
        rows[3] = "..........gtuuvg"
    save(paint(rows, rod_legend()), ITEM_TEX + ("artificers_rod_focused.png" if focused else "artificers_rod.png"))


FOCUS_RAMPS = {
    "pull": [hexc(c) for c in ("1d4f3f", "2f8a6e", "5fd0a4", "b7f0d0", "e8fff3")],
    "push": [hexc(c) for c in ("4a3418", "7d5a2a", "b88a45", "e0b870", "fff0cf")],
    "lift": [hexc(c) for c in ("2b1a4a", "5a3a9a", "9b7ae0", "c9b8ff", "f1eaff")],
    "ember": [hexc(c) for c in ("6a1a0a", "b83a14", "ff6a1e", "ffb02e", "fff2a0")],
    "frost": [hexc(c) for c in ("1f4a66", "3b86b0", "7fc6e8", "bfe9ff", "f2fbff")],
}


def focus(name):
    """A faceted crystal in a little gold claw, in the colour of what it does."""
    r = FOCUS_RAMPS[name]
    rows = [
        "................",
        "................",
        ".......ee.......",
        "......eddde.....",
        ".....eddcdde....",
        "....edcbbcdde...",
        "....edcabbcde...",
        "....edcbbbcde...",
        "....eddcbccde...",
        ".....eddcccde...",
        "......eddcde....",
        "......tu.dv.....",
        ".......uuv......",
        ".......uv.......",
        "................",
        "................",
    ]
    legend = {"a": r[4], "b": r[3], "c": r[2], "d": r[1], "e": r[0], "t": TRIM[2], "u": TRIM[1], "v": TRIM[0]}
    img = paint(rows, legend)
    px = img.load()
    # a mark in the heart of each crystal tells them apart
    marks = {
        "pull": ((7, 7), (8, 8), (9, 9), (6, 6)),
        "push": ((9, 7), (8, 8), (7, 9), (10, 6)),
        "lift": ((8, 6), (8, 7), (8, 8), (8, 9)),
        "ember": ((8, 6), (7, 8), (9, 8), (8, 7)),
        "frost": ((8, 6), (8, 9), (6, 8), (10, 8)),
    }
    for (x, y) in marks[name]:
        px[x, y] = r[4]
    save(img, ITEM_TEX + f"{name}_focus.png")


def bound_block():
    """A stone cube bound with gold bands and a rune: what a lifted block becomes."""
    rows = [
        "................",
        "......gggggg....",
        "....ggbbbbccg...",
        "..ggbbbbbbccdg..",
        ".gbbbbbbbbcddeg.",
        ".gttttttttttttg.",
        ".gccccccccceeeg.",
        ".gcgcccccgceeeg.",
        ".gtuttttutttteg.",
        ".gcccccccccdeeg.",
        ".gccccrcccdeeeg.",
        ".gccccprcdeeeeg.",
        ".gddddddddeeeg..",
        "..ggggggggggg...",
        "................",
        "................",
    ]
    legend = {"a": IRON[4], "b": IRON[3], "c": IRON[2], "d": IRON[1], "e": IRON[0], "g": IRON[0], "t": TRIM[1], "u": TRIM[2],
              "r": VIOLET[3], "p": VIOLET[4]}
    save(paint(rows, legend), ITEM_TEX + "bound_block.png")


def spellbinding_cloth():
    rows = [
        "................",
        "................",
        "...gggggggggg...",
        "..gbbbbbbbbbbg..",
        "..gbcccccccccg..",
        "..gbcttttttcdg..",
        "..gbcteeeetcdg..",
        "..gbcter.etcdg..",
        "..gbcte.eetcdg..",
        "..gbctttttcddg..",
        "..gbcccccddddg..",
        "..gcddddddddeg..",
        "..gcdd.dd.ddeg..",
        "...gg..gg..gg...",
        "................",
        "................",
    ]
    legend = {"b": WHITE[3], "c": WHITE[2], "d": WHITE[1], "e": WHITE[0], "g": WHITE[0], "t": VIOLET[3], "r": VIOLET[4]}
    save(paint(rows, legend), ITEM_TEX + "spellbinding_cloth.png")


def lantern():
    rows = [
        "................",
        ".....gtttg......",
        "....gt...tg.....",
        "....g.....g.....",
        "...gtttttttg....",
        "...gaaaaaaag....",
        "...gaaFFFaag....",
        "...gaFYYYFag....",
        "...gaFYWYFag....",
        "...gaaFYFaag....",
        "...gaaaFaaag....",
        "...gtttttttg....",
        "....gdddddg.....",
        ".....ggggg......",
        "................",
        "................",
    ]
    legend = {"g": IRON[0], "t": TRIM[1], "d": IRON[1], "a": GLASS[2], "F": FLAME[1], "Y": FLAME[2], "W": FLAME[3]}
    save(paint(rows, legend), ITEM_TEX + "everflame_lantern.png")


# ---------------------------------------------------------------- models, item definitions
def models():
    flat = ["ichorium_ingot", "ichorcloth_hood", "ichorcloth_robe", "ichorcloth_leggings", "ichorcloth_boots", "bound_block",
            "spellbinding_cloth", "everflame_lantern", "pull_focus", "push_focus", "lift_focus", "ember_focus", "frost_focus"]
    held = ["ichorium_sword", "ichorium_pickaxe", "ichorium_axe", "artificers_rod", "artificers_rod_focused"]
    for name in flat:
        write(ASSETS + f"models/item/alchemy/{name}.json", {"parent": "minecraft:item/generated",
                                                              "textures": {"layer0": f"jaymospowers:item/alchemy/{name}"}})
    for name in held:
        write(ASSETS + f"models/item/alchemy/{name}.json", {"parent": "minecraft:item/handheld",
                                                              "textures": {"layer0": f"jaymospowers:item/alchemy/{name}"}})
    for name in flat + held:
        if name == "artificers_rod_focused":
            continue
        if name == "artificers_rod":
            write(ASSETS + "items/artificers_rod.json", {"model": {
                "type": "minecraft:condition", "property": "minecraft:has_component", "component": "jaymospowers:focus",
                "on_false": {"type": "minecraft:model", "model": "jaymospowers:item/alchemy/artificers_rod"},
                "on_true": {"type": "minecraft:model", "model": "jaymospowers:item/alchemy/artificers_rod_focused"}}})
        else:
            write(ASSETS + f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:item/alchemy/{name}"}})


# ---------------------------------------------------------------- recipes and tags
def recipes():
    def shaped(name, key, pattern, result, count=1):
        write(DATA + f"recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "key": key, "pattern": pattern,
                                              "result": {"id": f"jaymospowers:{result}", "count": count}})

    ing = "jaymospowers:ichorium_ingot"
    shaped("ichorium_sword", {"I": ing, "S": "minecraft:stick"}, ["I", "I", "S"], "ichorium_sword")
    shaped("ichorium_pickaxe", {"I": ing, "S": "minecraft:stick"}, ["III", " S ", " S "], "ichorium_pickaxe")
    shaped("ichorium_axe", {"I": ing, "S": "minecraft:stick"}, ["II", "IS", " S"], "ichorium_axe")
    shaped("artificers_rod", {"A": "minecraft:amethyst_shard", "O": "jaymospowers:orichalc_ingot", "S": "minecraft:stick"},
           ["A", "O", "S"], "artificers_rod")
    write(DATA + "recipe/spellbinding_cloth.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["#minecraft:wool", "jaymospowers:alchemical_salt", "jaymospowers:orichalc_nugget"],
        "result": {"id": "jaymospowers:spellbinding_cloth", "count": 1}})
    write(DATA + "recipe/everflame_lantern.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": ["minecraft:lantern", "jaymospowers:everflame", "jaymospowers:orichalc_ingot"],
        "result": {"id": "jaymospowers:everflame_lantern", "count": 1}})

    write(DATA + "alchemy/crucible/ichorium_ingot.json", {
        "catalyst": "jaymospowers:ichor",
        "essences": {"metal": 8, "arcana": 6},
        "result": {"item": "jaymospowers:ichorium_ingot", "count": 2},
        "research": "ichorium"})

    base = DATA + "alchemy/infusion/"

    def infuse(name, centre, comps, essences, instability, result, research, flux):
        write(base + f"{name}.json", {"centre": centre, "components": comps, "essences": essences, "instability": instability,
                                      "result": {"item": f"jaymospowers:{result}", "count": 1}, "research": research, "flux": flux})

    ichor = "jaymospowers:ichor"
    infuse("pull_focus", "minecraft:ender_pearl", [ichor, "minecraft:lead", "minecraft:lead", "jaymospowers:alchemical_salt"],
           {"motion": 8, "air": 6}, 4, "pull_focus", "focus_pull", 3)
    infuse("push_focus", "minecraft:slime_ball", [ichor, "minecraft:piston", "jaymospowers:orichalc_ingot", "jaymospowers:alchemical_salt"],
           {"motion": 8, "earth": 6}, 4, "push_focus", "focus_push", 3)
    infuse("lift_focus", "minecraft:ender_eye", [ichor, "minecraft:chorus_fruit", "minecraft:chorus_fruit", "minecraft:obsidian"],
           {"earth": 10, "spirit": 6, "motion": 6}, 6, "lift_focus", "focus_lift", 4)
    infuse("ember_focus", "minecraft:blaze_powder", [ichor, "jaymospowers:brimstone", "jaymospowers:brimstone", "minecraft:fire_charge"],
           {"fire": 10, "spark": 6}, 5, "ember_focus", "focus_ember", 3)
    infuse("frost_focus", "minecraft:blue_ice", [ichor, "jaymospowers:alchemical_salt", "jaymospowers:alchemical_salt", "minecraft:snowball"],
           {"frost": 10, "water": 6}, 4, "frost_focus", "focus_frost", 3)

    wool = "minecraft:white_wool"
    infuse("ichorcloth_hood", "minecraft:leather_helmet", [ichor, wool, wool, "jaymospowers:alchemical_salt"],
           {"spirit": 8, "salt": 6}, 4, "ichorcloth_hood", "ichorcloth", 3)
    infuse("ichorcloth_robe", "minecraft:leather_chestplate", [ichor, wool, wool, wool, wool, ing, "jaymospowers:alchemical_salt", "jaymospowers:alchemical_salt"],
           {"spirit": 14, "salt": 10, "arcana": 6}, 8, "ichorcloth_robe", "ichorcloth", 5)
    infuse("ichorcloth_leggings", "minecraft:leather_leggings", [ichor, wool, wool, wool, "jaymospowers:alchemical_salt", "jaymospowers:alchemical_salt"],
           {"spirit": 12, "salt": 8}, 6, "ichorcloth_leggings", "ichorcloth", 4)
    infuse("ichorcloth_boots", "minecraft:leather_boots", [ichor, wool, wool, "jaymospowers:alchemical_salt"],
           {"spirit": 8, "salt": 6}, 4, "ichorcloth_boots", "ichorcloth", 3)

    write(DATA + "tags/item/ichorium_tool_materials.json", {"values": ["jaymospowers:ichorium_ingot"]})
    write(DATA + "tags/item/repairs_ichorcloth_armor.json", {"values": ["#minecraft:wool"]})


# ---------------------------------------------------------------- lang
def lang():
    t = {
        "item.jaymospowers.artificers_rod": "Artificer's Rod",
        "item.jaymospowers.pull_focus": "Focus of Pulling",
        "item.jaymospowers.push_focus": "Focus of Pushing",
        "item.jaymospowers.lift_focus": "Focus of Lifting",
        "item.jaymospowers.ember_focus": "Focus of Embers",
        "item.jaymospowers.frost_focus": "Focus of Frost",
        "item.jaymospowers.bound_block": "Bound Block",
        "item.jaymospowers.bound_block.of": "Bound %s",
        "item.jaymospowers.spellbinding_cloth": "Spellbinding Cloth",
        "item.jaymospowers.ichorium_ingot": "Ichorium Ingot",
        "item.jaymospowers.ichorium_sword": "Ichorium Sword",
        "item.jaymospowers.ichorium_pickaxe": "Ichorium Pickaxe",
        "item.jaymospowers.ichorium_axe": "Ichorium Axe",
        "item.jaymospowers.ichorcloth_hood": "Ichorcloth Hood",
        "item.jaymospowers.ichorcloth_robe": "Ichorcloth Robe",
        "item.jaymospowers.ichorcloth_leggings": "Ichorcloth Leggings",
        "item.jaymospowers.ichorcloth_boots": "Ichorcloth Boots",
        "item.jaymospowers.everflame_lantern": "Lantern of Everflame",
        "tooltip.jaymospowers.artificers_rod": "Sneak with a focus in your other hand to set it in. Strike the infusion stone to wake it.",
        "tooltip.jaymospowers.artificers_rod.focus": "Set with: %s",
        "tooltip.jaymospowers.artificers_rod.empty": "No focus is set.",
        "tooltip.jaymospowers.focus": "Set it in an Artificer's Rod.",
        "tooltip.jaymospowers.focus.pull": "Draws what you aim at toward you.",
        "tooltip.jaymospowers.focus.push": "Throws what you aim at away from you.",
        "tooltip.jaymospowers.focus.lift": "Binds the block you aim at, and all it holds, into a carried stone.",
        "tooltip.jaymospowers.focus.ember": "Casts a small bolt of fire.",
        "tooltip.jaymospowers.focus.frost": "Casts a bolt of cold that slows what it strikes.",
        "tooltip.jaymospowers.bound_block": "Use on a face to set it down as it was.",
        "tooltip.jaymospowers.spellbinding_cloth": "Hold an enchanted thing in your other hand and use the cloth to unpick one enchantment into essence.",
        "tooltip.jaymospowers.ichorium": "It hungers.",
        "tooltip.jaymospowers.everflame_lantern": "Use on a block face to set an Everflame there, for a little Light.",
        "message.jaymospowers.gear.not_understood": "You do not yet understand how this is done.",
        "message.jaymospowers.gear.no_focus": "The rod holds no focus.",
        "message.jaymospowers.gear.focus_set": "The rod takes the %s.",
        "message.jaymospowers.gear.nothing_there": "There is nothing there to answer.",
        "message.jaymospowers.gear.wont_lift": "That will not be bound.",
        "message.jaymospowers.gear.no_essence": "You carry too little essence for it.",
        "message.jaymospowers.gear.nothing_to_strip": "There is no enchantment on it to unpick.",
        "gui.jaymospowers.enchanting.essence": "Essence",
        "gui.jaymospowers.enchanting.essence.tip": "Pay with essence from the phials you carry, instead of with items. It leaves flux about the table, and a little taint in you.",
        "gui.jaymospowers.enchanting.essence.carried": "You carry %s",
        "research.jaymospowers.essence_enchanting": "Essence Enchanting",
        "research.jaymospowers.essence_enchanting.text": "Every enchantment is woven of two essences. Give the table those essences from the phials in your pack, a measure of each for every level, and it will weave the enchantment without any of the usual trinkets. It is quick work, but it leaves flux about the table and a little taint in whoever pays.",
        "research.jaymospowers.spellbinding": "Spellbinding",
        "research.jaymospowers.spellbinding.text": "A cloth of white wool, salt and a pinch of the rose metal. Held to an enchanted thing it unpicks the first enchantment and gathers half of what went into it as essence in phials. The cloth frays with each use, and the unpicking leaves flux behind.",
        "research.jaymospowers.artifice": "Artifice",
        "research.jaymospowers.artifice.text": "A slim rod of rose metal and amethyst with a claw at its tip, made to hold a focus. Set a focus in the claw, sneaking with it in your other hand, and the rod casts what the focus knows, drawing the essence from the phials in your pack. Struck against the infusion stone it wakes the altar, as a phial does.",
        "research.jaymospowers.focus_pull": "Focus of Pulling",
        "research.jaymospowers.focus_pull.text": "An ender pearl bathed in motion and air. Aimed at a creature or a dropped thing it drags it to you.",
        "research.jaymospowers.focus_push": "Focus of Pushing",
        "research.jaymospowers.focus_push.text": "A slime ball steeped in motion and earth. Aimed at a creature or a dropped thing it throws it away with great force.",
        "research.jaymospowers.focus_lift": "Focus of Lifting",
        "research.jaymospowers.focus_lift.text": "An eye of the End bathed in earth, spirit and motion. Aimed at a block it binds the block, and all it holds, into a stone you carry. Use the stone on a face to set the block down again, whole.",
        "research.jaymospowers.focus_ember": "Focus of Embers",
        "research.jaymospowers.focus_ember.text": "Blaze powder, brimstone and a fire charge bathed in fire and sparks. It casts a small bolt of fire.",
        "research.jaymospowers.focus_frost": "Focus of Frost",
        "research.jaymospowers.focus_frost.text": "Blue ice and salt bathed in frost and water. It casts a bolt of cold that chills and slows what it strikes.",
        "research.jaymospowers.ichorium": "Ichorium",
        "research.jaymospowers.ichorium.text": "Cast ichor into a crucible rich in metal and arcana and it comes out as two ingots of a deep amber metal. Tools made of it outlast and outcut netherite, but each blow and each block broken feeds something in you.",
        "research.jaymospowers.ichorcloth": "Ichorcloth",
        "research.jaymospowers.ichorcloth.text": "Leather, wool and ichor, worked at the altar into a hood, a robe, leggings and boots of violet cloth. It turns aside magic, and a wearer of the whole set takes up flux's taint only half as fast.",
        "research.jaymospowers.everflame_lantern": "The Lantern of Everflame",
        "research.jaymospowers.everflame_lantern.text": "A lantern, an everflame and a rose metal ingot. Used on the face of a block it sets an everflame beside it, taking a little Light from the phials you carry. It needs a moment between flames.",
    }
    write(ASSETS + "lang/parts/alchemy_gear.json", t)


if __name__ == "__main__":
    ichorium_ingot()
    ichorium_sword()
    ichorium_pickaxe()
    ichorium_axe()
    cloth_hood()
    cloth_robe()
    cloth_leggings()
    cloth_boots()
    armour_layers()
    rod(False)
    rod(True)
    for n in FOCUS_RAMPS:
        focus(n)
    bound_block()
    spellbinding_cloth()
    lantern()
    models()
    recipes()
    lang()
