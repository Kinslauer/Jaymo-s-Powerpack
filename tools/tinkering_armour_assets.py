"""Writes the Tool Station armour's assets: plate armour and travelers' gear after Tinkers' Construct (SlimeKnights, MIT).

Tinkers' textures (code, textures and binaries are all MIT) are reused; see
assets/jaymospowers/textures/item/tinker_armour/license_tinkers_construct.txt. Point it at the unpacked Tinkers' Construct
1.20.1 jar (the folder that holds assets/tconstruct):

    python -I tools/tinkering_armour_assets.py <unpacked TConstruct jar>

It writes into its own folders (item/tinker_armour, entity/equipment/.../tinker_plate and tinker_travelers), so
tools/tinkering_assets.py, which clears item/tinkering, leaves them alone. The ids must match com.jaymospowers.tinkering
(TinkerMaterial.plating(), PartType's platings and maille, ToolType HELMET to BOOTS, ToolStats.applyArmour).
"""
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tinkering_assets as ta  # noqa: E402

ASSETS = ta.ASSETS
NS = ta.NS
# the metals with plating stats (TinkerMaterial.plating()) and their Tinkers sprite names
METALS = ["copper", "iron", "silver", "rose_gold", "amethyst_bronze", "pig_iron", "slimesteel", "cobalt", "manyullyn",
          "queen_slime", "hepatizon", "steel"]
TC = {"queen_slime": "queens_slime"}
ROCKS = ["rock_stone", "rock_granite", "rock_diorite", "rock_blackstone"]
# armour piece (our item), Tinkers' plate folder, Tinkers' travelers folder
PIECES = [("helmet", "helmet", "goggles"), ("chestplate", "chestplate", "vest"), ("leggings", "leggings", "pants"),
          ("boots", "boots", "boots")]
PLATINGS = ["helmet_plating", "chestplate_plating", "leggings_plating", "boots_plating"]
# the colour a hot part glows (smithing_assets.py)
HOT = -26040
ITEM = "item/tinker_armour"

NAMES = {
    "helmet": ("Plate Helmet", "Travelers' Goggles"), "chestplate": ("Plate Chestplate", "Travelers' Vest"),
    "leggings": ("Plate Leggings", "Travelers' Pants"), "boots": ("Plate Boots", "Travelers' Boots"),
}
LANG = {
    "item.jaymospowers.helmet_plating": "Helmet Plating", "item.jaymospowers.chestplate_plating": "Chestplate Plating",
    "item.jaymospowers.leggings_plating": "Leggings Plating", "item.jaymospowers.boots_plating": "Boots Plating",
    "item.jaymospowers.maille": "Maille",
    "item.jaymospowers.helmet_plating.short": "Helmet", "item.jaymospowers.chestplate_plating.short": "Chestplate",
    "item.jaymospowers.leggings_plating.short": "Leggings", "item.jaymospowers.boots_plating.short": "Boots",
    "item.jaymospowers.maille.short": "Maille",
    "item.jaymospowers.helmet_plating_cast": "Helmet Plating Cast", "item.jaymospowers.chestplate_plating_cast": "Chestplate Plating Cast",
    "item.jaymospowers.leggings_plating_cast": "Leggings Plating Cast", "item.jaymospowers.boots_plating_cast": "Boots Plating Cast",
    "item.jaymospowers.maille_cast": "Maille Cast",
    "tooltip.jaymospowers.tinkering.armor": "Armor: %s",
    "tooltip.jaymospowers.tinkering.toughness": "Toughness: %s",
    "tooltip.jaymospowers.tinkering.knockback_resistance": "Knockback Resistance: %s",
    "tooltip.jaymospowers.tinkering.dummy_plating": "Only good for pouring a cast over",
    "tooltip.jaymospowers.tinkering.cuirass": "A leather cuirass: makes travelers' gear",
    "gui.jaymospowers.tool_station.dummy_plating": "A stone plating is only a mould for its cast",
    "gui.jaymospowers.tool_station.armour": "Armour takes no modifiers",
    "trait.jaymospowers.depth_protection": "Depth Protection",
    "trait.jaymospowers.depth_protection.desc": "Turns aside more harm the deeper underground you are",
    "trait.jaymospowers.projectile_protection": "Projectile Protection",
    "trait.jaymospowers.projectile_protection.desc": "Turns aside arrows and other things shot or thrown",
    "trait.jaymospowers.consecrated": "Consecrated",
    "trait.jaymospowers.consecrated.desc": "Turns aside blows from the undead",
    "trait.jaymospowers.melee_protection": "Melee Protection",
    "trait.jaymospowers.melee_protection.desc": "Turns aside blows struck up close",
    "trait.jaymospowers.warded": "Warded",
    "trait.jaymospowers.warded.desc": "Turns aside magic",
    "trait.jaymospowers.recurrent_protection": "Recurrent Protection",
    "trait.jaymospowers.recurrent_protection.desc": "Turns aside harm for a few seconds after you are hurt",
}


def tc(mat):
    return TC.get(mat, mat)


def src(tcon, *rel):
    return os.path.join(tcon, "assets", "tconstruct", "textures", *rel)


def texture(path, dest_rel):
    """Copies a Tinkers texture to textures/<dest_rel>.png and gives it an item model; returns the model's ref."""
    ta.copy_png(path, dest_rel)
    ta.model(dest_rel, "minecraft:item/generated", dest_rel)
    return ta.ref(dest_rel)


def recolour(template, colours, dest_rel):
    """A part template in a material's colours: each pixel's brightness, stretched over the template's darkest to
    lightest, picks the colour the material gives that grey (Tinkers colours its parts by brightness the same way)."""
    im = Image.open(template).convert("RGBA")
    pixels = [(x, y) for y in range(im.size[1]) for x in range(im.size[0]) if im.getpixel((x, y))[3]]
    light = {xy: sum(im.getpixel(xy)[:3]) / 3 for xy in pixels}
    low, high = min(light.values()), max(light.values())
    for xy in pixels:
        grey = ta.GREYS[0] + (light[xy] - low) / max(1, high - low) * (ta.GREYS[-1] - ta.GREYS[0])
        below = max(g for g in ta.GREYS if g <= grey)
        above = min(g for g in ta.GREYS if g >= grey)
        f = 0 if above == below else (grey - below) / (above - below)
        c = tuple(round(colours[below][i] + (colours[above][i] - colours[below][i]) * f) for i in range(3))
        im.putpixel(xy, c + (im.getpixel(xy)[3],))
    dest = os.path.join(ASSETS, "textures", dest_rel + ".png")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    im.save(dest)
    ta.model(dest_rel, "minecraft:item/generated", dest_rel)
    return ta.ref(dest_rel)


def heated(cases, fallback):
    """A part's model: by material, glowing while it's hot from the forge (like every other part)."""
    def hot(model):
        return dict(model, tints=[{"type": "minecraft:constant", "value": HOT}])
    return {"type": "minecraft:condition", "property": "minecraft:has_component", "component": NS + ":heated_at",
            "on_true": ta.by_material(0, [(m, hot(c)) for m, c in cases], hot(fallback)),
            "on_false": ta.by_material(0, cases, fallback)}


def parts(tcon):
    for i, part in enumerate(PLATINGS):
        template = src(tcon, "item", "tool", "parts", "plating_" + PIECES[i][1] + ".png")
        cases = []
        for mat in METALS + ROCKS:
            colours = ta.palette(tcon, tc(mat), "head")
            if colours is None:
                print("no colours for", mat)
                continue
            cases.append((mat, recolour(template, colours, ITEM + "/part/" + part + "_" + mat)))
        ta.write(os.path.join(ASSETS, "items", part + ".json"), {"model": heated(cases, cases[1][1])})
    cases = []
    for mat in METALS + ["leather"]:
        cases.append((mat, texture(src(tcon, "item", "tool", "parts", "maille_tconstruct_" + tc(mat) + ".png"), ITEM + "/part/maille_" + mat)))
    ta.write(os.path.join(ASSETS, "items", "maille.json"), {"model": heated(cases, cases[1][1])})
    for part in PLATINGS + ["maille"]:
        ref = texture(src(tcon, "item", "cast", part + ".png"), ITEM + "/cast/" + part)
        ta.write(os.path.join(ASSETS, "items", part + "_cast.json"), {"model": ref})
        # the Part Builder's button for it
        icon = os.path.join(ASSETS, "textures", "gui", "tinkering", "icon", part + ".png")
        os.makedirs(os.path.dirname(icon), exist_ok=True)
        shutil.copyfile(src(tcon, "gui", "tinker_pattern", part + ".png"), icon)


def pieces(tcon):
    for piece, plate, trav in PIECES:
        looks = {}
        for broken in ("", "_broken"):
            base = ["item", "tool", "armor", "plate", plate]
            plating = [(m, texture(src(tcon, *base, "plating" + broken + "_tconstruct_" + tc(m) + ".png"),
                                   ITEM + "/" + piece + "/plating" + broken + "_" + m)) for m in METALS]
            maille = [(m, texture(src(tcon, *base, "maille" + broken + "_tconstruct_" + tc(m) + ".png"),
                                  ITEM + "/" + piece + "/maille" + broken + "_" + m)) for m in METALS]
            plate_look = ta.composite(ta.by_material(0, plating, plating[1][1]), ta.by_material(1, maille, maille[1][1]))
            base = ["item", "tool", "armor", "travelers", trav]
            metal = [(m, texture(src(tcon, *base, "metal" + broken + "_tconstruct_" + tc(m) + ".png"),
                                 ITEM + "/" + piece + "/travelers_metal" + broken + "_" + m)) for m in METALS]
            cuirass = texture(src(tcon, *base, "cuirass" + broken + "_tconstruct_leather.png"), ITEM + "/" + piece + "/cuirass" + broken)
            goggles = None
            if trav == "goggles":
                goggles = texture(src(tcon, *base, "base" + broken + ".png"), ITEM + "/" + piece + "/goggles" + broken)
            travelers = ta.composite(goggles, cuirass, ta.by_material(0, metal, metal[1][1]))
            # a leather maille makes it the travelers' piece
            looks[broken] = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1,
                             "cases": [{"when": "leather", "model": travelers}], "fallback": plate_look}
        ta.write(os.path.join(ASSETS, "items", "tinker_" + piece + ".json"), {"model": ta.broken_or(looks["_broken"], looks[""])})


def worn(tcon):
    """The worn look: one equipment asset per plating and maille, and one per travelers' plating."""
    eq = os.path.join(ASSETS, "textures", "entity", "equipment")

    def copy(path, layer, name):
        dest = os.path.join(eq, layer, name + ".png")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        shutil.copyfile(path, dest)

    for m in METALS:
        for kind in ("plating", "maille"):
            copy(src(tcon, "tinker_armor", "plate", kind + "_armor_tconstruct_" + tc(m) + ".png"), "humanoid", "tinker_plate/" + kind + "_" + m)
            copy(src(tcon, "tinker_armor", "plate", kind + "_leggings_tconstruct_" + tc(m) + ".png"), "humanoid_leggings",
                 "tinker_plate/" + kind + "_" + m)
        copy(src(tcon, "tinker_armor", "travelers", "metal_armor_tconstruct_" + tc(m) + ".png"), "humanoid", "tinker_travelers/metal_" + m)
        copy(src(tcon, "tinker_armor", "travelers", "metal_leggings_tconstruct_" + tc(m) + ".png"), "humanoid_leggings",
             "tinker_travelers/metal_" + m)
    copy(src(tcon, "tinker_armor", "travelers", "cuirass_armor_tconstruct_leather.png"), "humanoid", "tinker_travelers/cuirass")
    copy(src(tcon, "tinker_armor", "travelers", "cuirass_leggings_tconstruct_leather.png"), "humanoid_leggings", "tinker_travelers/cuirass")
    copy(src(tcon, "tinker_armor", "travelers", "base_armor.png"), "humanoid", "tinker_travelers/goggles")

    def layers(*names):
        return [{"texture": NS + ":" + n} for n in names]

    for p in METALS:
        for m in METALS:
            look = ["tinker_plate/maille_" + m, "tinker_plate/plating_" + p]
            ta.write(os.path.join(ASSETS, "equipment", "tinker_plate", p + "__" + m + ".json"),
                     {"layers": {"humanoid": layers(*look), "humanoid_leggings": layers(*look)}})
        ta.write(os.path.join(ASSETS, "equipment", "tinker_travelers", p + ".json"), {"layers": {
            "humanoid": layers("tinker_travelers/cuirass", "tinker_travelers/metal_" + p, "tinker_travelers/goggles"),
            "humanoid_leggings": layers("tinker_travelers/cuirass", "tinker_travelers/metal_" + p)}})


def lang():
    out = dict(LANG)
    for piece, (plate, travelers) in NAMES.items():
        out["item.jaymospowers.tinker_" + piece] = plate
        out["item.jaymospowers.tinker_" + piece + ".travelers"] = travelers
    ta.write(os.path.join(ASSETS, "lang", "parts", "tinker_armour.json"), out)


def main():
    if len(sys.argv) != 2 or not os.path.isdir(os.path.join(sys.argv[1], "assets", "tconstruct")):
        sys.exit(__doc__)
    tcon = sys.argv[1]
    for old in (os.path.join(ASSETS, "textures", ITEM), os.path.join(ASSETS, "models", ITEM)):
        shutil.rmtree(old, ignore_errors=True)
    parts(tcon)
    pieces(tcon)
    worn(tcon)
    lang()
    shutil.copyfile(os.path.join(ASSETS, "textures", "item", "tinkering", "license_tinkers_construct.txt"),
                    os.path.join(ASSETS, "textures", ITEM, "license_tinkers_construct.txt"))


if __name__ == "__main__":
    main()
