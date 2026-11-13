"""Writes the assets and data of what the great ones leave (com.jaymospowers.tinkering.GreatParts, baubles.ClingingCoil):
the four dragon bones and the deep tooth as Tinkering materials (every bone part and tool layer recoloured, a case beside
bone's in each item definition), the bone and hide icons, the dragonhide round shield (the iron round shield's model,
re-skinned), its recipe, the dragon bones tag and the lang. Run from the repository root, after tools/tinkering_assets.py:

    python3 -I tools/gen_great_parts.py

The ids must match GreatParts and the TinkerMaterial entries.
"""
import json
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
os.chdir(ROOT)
sys.path.insert(0, HERE)

from PIL import Image  # noqa: E402

import gen_steel as steel  # noqa: E402

NS = "jaymospowers"
ASSETS, DATA = steel.ASSETS, steel.DATA
write = steel.write

# material id, lang name, ramp (dark to light), trait id, trait name, trait text
MATERIALS = [
    ("ember_dragon_bone", "Ember Dragon Bone", [(58, 22, 14), (140, 62, 34), (214, 150, 104), (250, 226, 190)],
     "ember_marrow", "Ember Marrow", "Sets what it strikes alight."),
    ("rime_dragon_bone", "Rime Dragon Bone", [(40, 62, 92), (98, 140, 178), (176, 214, 232), (238, 250, 255)],
     "rime_marrow", "Rime Marrow", "Slows and chills what it strikes."),
    ("venom_dragon_bone", "Venom Dragon Bone", [(34, 52, 20), (92, 118, 48), (176, 196, 112), (236, 244, 196)],
     "venom_marrow", "Venom Marrow", "Poisons what it strikes."),
    ("storm_dragon_bone", "Storm Dragon Bone", [(44, 40, 70), (104, 100, 150), (184, 186, 220), (246, 246, 255)],
     "storm_marrow", "Storm Marrow", "Now and then a hit shocks what it strikes."),
    ("deep_tooth", "Deep Tooth", [(70, 52, 44), (150, 134, 108), (214, 204, 176), (246, 242, 226)],
     "hooked", "Hooked", "Hits harder on anything in water."),
]
HIDE = [(40, 20, 18), (96, 40, 34), (150, 70, 52), (204, 118, 80)]


def ramp_at(ramp, v):
    v = max(0.0, min(1.0, v)) * (len(ramp) - 1)
    i = min(int(v), len(ramp) - 2)
    f = v - i
    return tuple(round(ramp[i][k] + (ramp[i + 1][k] - ramp[i][k]) * f) for k in range(3))


def ramped(im, ramp, stretch=True):
    """Every opaque pixel recoloured through the ramp by its brightness (stretched over the sprite's own range)."""
    im = im.convert("RGBA")
    px = im.load()
    lum = [(0.299 * r + 0.587 * g + 0.114 * b) / 255 for r, g, b, a in list(im.getdata()) if a]
    lo, hi = (min(lum), max(lum)) if lum and stretch else (0.0, 1.0)
    span = max(0.05, hi - lo)
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = ramp_at(ramp, ((0.299 * r + 0.587 * g + 0.114 * b) / 255 - lo) / span) + (a,)
    return im


def png(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def tinker_material(material, ramp):
    """Every bone part and tool layer as this material, its model."""
    tex = os.path.join(ASSETS, "textures", "item", "tinkering")
    models = os.path.join(ASSETS, "models", "item", "tinkering")
    count = 0
    for folder in steel.TINKER_DIRS:
        for name in sorted(os.listdir(os.path.join(tex, folder))):
            if not name.endswith("_bone.png"):
                continue
            base = name[:-len("_bone.png")]
            # plain bone only (the dragon bones end in "_bone" too)
            if base.endswith("_dragon"):
                continue
            ramped(Image.open(os.path.join(tex, folder, name)), ramp).save(os.path.join(tex, folder, base + "_" + material + ".png"))
            model = json.load(open(os.path.join(models, folder, base + "_bone.json")))
            model["textures"]["layer0"] = model["textures"]["layer0"][:-len("_bone")] + "_" + material
            write(os.path.join(models, folder, base + "_" + material + ".json"), model)
            count += 1
    print(material, "sprites:", count)


def add_cases(materials):
    def add_case(node):
        if isinstance(node, list):
            for child in node:
                add_case(child)
            return
        if not isinstance(node, dict):
            return
        cases = node.get("cases")
        if node.get("property") == "minecraft:custom_model_data" and isinstance(cases, list):
            whens = [c.get("when") for c in cases]
            if "bone" in whens:
                bone = cases[whens.index("bone")]
                for material in materials:
                    if material not in whens:
                        text = json.dumps(bone["model"]).replace('_bone"', '_' + material + '"')
                        cases.append({"when": material, "model": json.loads(text)})
        for value in node.values():
            add_case(value)

    for item in steel.TINKER_ITEMS:
        path = os.path.join(ASSETS, "items", item + ".json")
        definition = json.load(open(path))
        add_case(definition)
        write(path, definition)


BONE = [
    "................",
    "...........cb...",
    "..........cbbdc.",
    "..........bbbbb.",
    ".........cbbbd..",
    "........cbbdc...",
    ".......cbbd.....",
    "......cbbd......",
    ".....cbbd.......",
    "....cbbd........",
    "..ccbbd.........",
    ".cbbbbd.........",
    ".bbbbd..........",
    ".dbdbc..........",
    "..c.c...........",
    "................",
]
HIDE_ART = [
    "................",
    "..dd......dd....",
    ".dccd....dccd...",
    ".dcbcddddcbcd...",
    "..dcbccccbcd....",
    "..dcbcbbcbcd....",
    "...dcbccbcd.....",
    "...dccbbccd.....",
    "...dcbccbcd.....",
    "..dcbcbbcbcd....",
    "..dcbccccbcd....",
    ".dcbcddddcbcd...",
    ".dccd....dccd...",
    "..dd......dd....",
    "................",
    "................",
]


def art(rows, ramp):
    """b = light, c = mid, d = dark, through the ramp."""
    at = {"b": 0.95, "c": 0.6, "d": 0.2}
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), ramp_at(ramp, at[ch]) + (255,))
    return im


def flat_item(item, texture):
    write(os.path.join(ASSETS, "items", item + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/" + item}})
    write(os.path.join(ASSETS, "models", "item", item + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":" + texture}})


def items():
    for material, _, ramp, *_ in MATERIALS[:4]:
        png(art(BONE, ramp), "item/" + material)
        flat_item(material, "item/" + material)
    png(art(HIDE_ART, HIDE), "item/dragon_hide")
    flat_item("dragon_hide", "item/dragon_hide")


def shield():
    armory = os.path.join(ASSETS, "models", "item", "armory")
    png(ramped(Image.open(os.path.join(ASSETS, "textures", "item", "armory", "iron_round_shield.png")), HIDE), "item/armory/dragonhide_round_shield")
    for suffix in ("", "_blocking"):
        text = open(os.path.join(armory, "iron_round_shield" + suffix + ".json")).read().replace("iron_round_shield", "dragonhide_round_shield")
        write(os.path.join(armory, "dragonhide_round_shield" + suffix + ".json"), json.loads(text))
    text = open(os.path.join(ASSETS, "items", "iron_round_shield.json")).read().replace("iron_round_shield", "dragonhide_round_shield")
    write(os.path.join(ASSETS, "items", "dragonhide_round_shield.json"), json.loads(text))
    write(os.path.join(DATA, "recipe", "great_parts", "dragonhide_round_shield.json"), {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "key": {"H": NS + ":dragon_hide", "B": "#" + NS + ":dragon_bones"},
        "pattern": ["HBH", "HHH", " H "], "result": {"id": NS + ":dragonhide_round_shield"}})
    write(os.path.join(DATA, "tags", "item", "dragon_bones.json"), {"values": [NS + ":" + m[0] for m in MATERIALS[:4]]})


def lang():
    out = {}
    for material, name, _, trait, trait_name, trait_text in MATERIALS:
        out["material." + NS + "." + material] = name
        out["trait." + NS + "." + trait] = trait_name
        out["trait." + NS + "." + trait + ".desc"] = trait_text
    for material, name, *_ in MATERIALS[:4]:
        out["item." + NS + "." + material] = name
        out["jei." + NS + "." + material] = ("A bone from a wild dragon of that breath. Shape it at the Part Builder like bone: "
                                             "diamond-tier heads, handles, bindings and bow parts that keep the breath.")
    out["item." + NS + ".dragon_hide"] = "Dragon Hide"
    out["jei." + NS + ".dragon_hide"] = "Cut from any wild dragon. Six hides and a dragon bone make a dragonhide round shield."
    out["item." + NS + ".dragonhide_round_shield"] = "Dragonhide Round Shield"
    out["jei." + NS + ".dragonhide_round_shield"] = ("A round shield that cannot burn. While it is raised no fire gets through, "
                                                     "from any side, and you are put out.")
    write(os.path.join(ASSETS, "lang", "parts", "great_parts.json"), out)


def main():
    for material, _, ramp, *_ in MATERIALS:
        tinker_material(material, ramp)
    add_cases([m[0] for m in MATERIALS])
    items()
    shield()
    lang()


if __name__ == "__main__":
    main()
