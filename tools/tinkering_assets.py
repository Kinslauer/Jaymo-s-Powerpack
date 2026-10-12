"""Writes Jaymo's Tinkering's assets and data: tool parts, tools and stations after Tinkers' Construct (SlimeKnights, MIT).

Tinkers' textures (code, textures and binaries are all MIT) are reused; see
assets/jaymospowers/textures/item/tinkering/license_tinkers_construct.txt. Point it at the unpacked Tinkers' Construct
1.20.1 jar (the folder that holds assets/tconstruct):

    python -I tools/tinkering_assets.py <unpacked TConstruct jar>

The material, part and tool ids must match com.jaymospowers.tinkering (TinkerMaterial, PartType, ToolType).
"""
import json
import os
import shutil
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MC_DATA = os.path.join(RES, "data", "minecraft")
NS = "jaymospowers"

# material id (= Tinkers' texture suffix), name, which parts it makes: head (and handle, binding) or binding only
MATERIALS = [
    ("wood", "Wooden", "all"), ("wood_crimson", "Crimson", "all"), ("wood_warped", "Warped", "all"),
    ("rock_stone", "Stone", "all"), ("rock_granite", "Granite", "all"), ("rock_diorite", "Diorite", "all"),
    ("rock_blackstone", "Blackstone", "all"),
    ("flint", "Flint", "all"), ("bone", "Bone", "all"), ("copper", "Copper", "all"), ("iron", "Iron", "all"),
    ("leather", "Leather", "binding"), ("string", "String", "binding"), ("vine", "Vine", "binding"),
]
HEADS = [m for m, _, use in MATERIALS if use == "all"]
# how much bigger than a vanilla tool the sledge hammer and cleaver look in hand
LARGE_IN_HAND = 1.5
ALL = [m for m, _, _ in MATERIALS]

# part id, name, kind, Tinkers' sprite (folder/name), offset to centre it as an item
PARTS = [
    ("pick_head", "Pick Head", "head", "pickaxe/head", (-2, 1)),
    ("small_axe_head", "Small Axe Head", "head", "hand_axe/head", (-2, 3)),
    ("adze_head", "Adze Head", "head", "pickadze/adze", (-5, 1)),
    ("small_blade", "Small Blade", "head", "parts/small_blade", (0, 0)),
    ("hammer_head", "Hammer Head", "head", "sledge_hammer/head", (-3, 3)),
    ("broad_blade", "Broad Blade", "head", "cleaver/head", (-1, 1)),
    ("large_plate", "Large Plate", "head", "parts/large_plate", (0, 0)),
    ("tool_handle", "Tool Handle", "handle", "parts/tool_handle", (0, 0)),
    ("tough_handle", "Tough Handle", "handle", "parts/tough_handle", (0, 0)),
    ("tool_binding", "Tool Binding", "binding", "parts/tool_binding", (0, 0)),
]
PART_KIND = {p[0]: p[2] for p in PARTS}
# shorter names for the Tool Station's recipe list
SHORT = {"small_axe_head": "Axe Head", "small_blade": "Blade", "large_plate": "Plate", "tool_handle": "Handle",
         "tool_binding": "Binding"}

# tool id, name, its parts (in order), its layers bottom to top (name, Tinkers' sprite, which part), large in hand,
# Better Combat preset, vanilla item tags
TOOLS = [
    ("pickaxe", "Pickaxe", ["pick_head", "tool_handle", "tool_binding"],
     [("handle", "pickaxe/handle", 1), ("head", "pickaxe/head", 0), ("binding", "pickaxe/binding", 2)],
     False, "pickaxe", ["pickaxes"]),
    ("hand_axe", "Hand Axe", ["small_axe_head", "tool_handle", "tool_binding"],
     [("handle", "pickaxe/handle", 1), ("head", "hand_axe/head", 0), ("binding", "hand_axe/binding", 2)],
     False, "axe", ["axes"]),
    ("mattock", "Mattock", ["small_axe_head", "tool_handle", "adze_head"],
     [("handle", "pickaxe/handle", 1), ("axe", "mattock/axe", 0), ("pick", "mattock/pick", 2)],
     False, "axe", ["axes", "shovels", "hoes"]),
    ("sword", "Sword", ["small_blade", "tool_handle", "tool_handle"],
     [("handle", "sword/handle", 1), ("head", "sword/blade", 0), ("guard", "sword/guard", 2)],
     False, "sword", ["swords"]),
    ("sledge_hammer", "Sledge Hammer", ["hammer_head", "tough_handle", "large_plate", "large_plate"],
     [("handle", "sledge_hammer/handle", 1), ("head", "sledge_hammer/head", 0), ("back", "sledge_hammer/back", 2),
      ("front", "sledge_hammer/front", 3)],
     True, "hammer", ["pickaxes"]),
    ("cleaver", "Cleaver", ["broad_blade", "tough_handle", "tough_handle", "large_plate"],
     [("handle", "cleaver/handle", 1), ("head", "cleaver/head", 0), ("shield", "cleaver/shield", 3),
      ("guard", "cleaver/guard", 2)],
     True, "claymore", ["swords"]),
]

TRAITS = {
    "cultivated": ("Cultivated", "Repairs go 50% further."),
    "stonebound": ("Stonebound", "Mines faster and hits softer the more worn it is."),
    "jagged": ("Jagged", "Hits harder the more worn it is."),
    "pierce": ("Piercing", "Part of each hit goes straight through armour."),
    "dwarven": ("Dwarven", "Mines faster the deeper you are."),
    "magnetic": ("Magnetic", "Pulls in nearby drops after you mine or kill."),
    "tanned": ("Tanned", "Never loses more than 1 durability at a time."),
    "stringy": ("Stringy", "Sometimes takes a knock without wearing."),
    "solar_powered": ("Solar Powered", "In sunlight it often takes a knock without wearing."),
    "smite": ("Smite", "Crushes the undead."),
    "severing": ("Severing", "Sometimes takes a head off what it kills."),
    "stripping": ("Stripping", "Strips logs like an axe."),
    "tilling": ("Tilling", "Tills soil like a hoe."),
    "sweeping": ("Sweeping", "Its swings cut through a crowd."),
    "area": ("Wide Swing", "Breaks a 3 by 3 face at once."),
}

STAT_LANG = {
    "tooltip.jaymospowers.tinkering.materials": "Materials: %s",
    "tooltip.jaymospowers.tinkering.durability": "Durability: %s / %s",
    "tooltip.jaymospowers.tinkering.broken": "Broken! Repair it at a Tool Station.",
    "tooltip.jaymospowers.tinkering.mining_speed": "Mining Speed: %s",
    "tooltip.jaymospowers.tinkering.mining_tier": "Mining Tier: %s",
    "tooltip.jaymospowers.tinkering.attack": "Attack Damage: %s",
    "tooltip.jaymospowers.tinkering.attack_speed": "Attack Speed: %s",
    "tooltip.jaymospowers.tinkering.handle_durability": "Durability: %s",
    "tooltip.jaymospowers.tinkering.handle_speed": "Mining Speed: %s",
    "tooltip.jaymospowers.tinkering.handle_attack": "Attack Damage: %s",
    "tooltip.jaymospowers.tinkering.handle_attack_speed": "Attack Speed: %s",
    "tooltip.jaymospowers.tinkering.binding_only": "Its trait is all a binding gives.",
    "tooltip.jaymospowers.tinkering.trait": "%s: %s",
    "tooltip.jaymospowers.tinkering.cost": "Costs %s material",
    "tooltip.jaymospowers.tinkering.shift": "Hold Shift to see what its traits do",
    "tooltip.jaymospowers.tinkering.pattern": "Shape tool parts from it at a Part Builder.",
    "tooltip.jaymospowers.tinkering.part_of": "Used in: %s",
    "tier.jaymospowers.wood": "Wood", "tier.jaymospowers.stone": "Stone", "tier.jaymospowers.iron": "Iron",
    "tier.jaymospowers.diamond": "Diamond", "tier.jaymospowers.netherite": "Netherite",
    "gui.jaymospowers.part_builder.pick": "Pick a part to shape",
    "gui.jaymospowers.part_builder.material": "Put in a material",
    "gui.jaymospowers.part_builder.pattern": "Put in a pattern",
    "gui.jaymospowers.part_builder.cannot": "%s can't make a %s",
    "gui.jaymospowers.part_builder.need": "Needs %s %s (you have %s)",
    "gui.jaymospowers.part_builder.value": "%s: %s per item",
    "gui.jaymospowers.part_builder.unknown": "That isn't a tool material",
    "gui.jaymospowers.tool_station.recipes": "Put in the parts for:",
    "gui.jaymospowers.tool_station.repair": "Repairing with %s",
    "gui.jaymospowers.tool_station.swap": "Swapping in a new %s",
    "gui.jaymospowers.tool_station.recipe": "%s: %s",
    "message.jaymospowers.tinkering.busy": "Someone else is using this table.",
    "container.jaymospowers.part_builder": "Part Builder",
    "container.jaymospowers.tool_station": "Tool Station",
    "block.jaymospowers.part_builder": "Part Builder",
    "block.jaymospowers.tool_station": "Tool Station",
    "item.jaymospowers.pattern": "Pattern",
    "itemGroup.jaymospowers.tinkering": "Jaymo's Tinkering",
    "jaymospowers.tinkering.named": "%s %s",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        if isinstance(data, str):
            f.write(data)
        else:
            json.dump(data, f, indent=2)
            f.write("\n")


def sprite(tcon, rel, mat):
    """Tinkers' sprite for a material, or None when it has none."""
    path = os.path.join(tcon, "assets", "tconstruct", "textures", "item", "tool", rel + "_tconstruct_" + mat + ".png")
    return path if os.path.exists(path) else None


def copy_png(src, dest_rel):
    dest = os.path.join(ASSETS, "textures", dest_rel + ".png")
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    shutil.copyfile(src, dest)
    meta = src + ".mcmeta"
    if os.path.exists(meta):
        shutil.copyfile(meta, dest + ".mcmeta")


def model(name, parent, texture):
    write(os.path.join(ASSETS, "models", name + ".json"), {"parent": parent, "textures": {"layer0": NS + ":" + texture}})


def ref(name):
    return {"type": "minecraft:model", "model": NS + ":" + name}


def by_material(index, cases, fallback):
    return {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": index,
            "cases": [{"when": m, "model": c} for m, c in cases], "fallback": fallback}


def materials_for(kind):
    return HEADS if kind != "binding" else ALL


def tools(tcon):
    missing = []
    for tool, _, parts, layers, large, _, _ in TOOLS:
        for size in (["small", "large"] if large else ["small"]):
            folder = "item/tinkering/" + tool + ("/large" if size == "large" else "")
            parent = "minecraft:item/handheld" if size == "small" else NS + ":item/tinkering/large_tool"
            for layer, rel, index in layers:
                src_rel = rel if size == "small" else rel.replace("/", "/large/", 1)
                for state in ("", "_broken"):
                    base = layer + state
                    if state and not sprite(tcon, src_rel + "_broken", "iron"):
                        continue
                    for mat in materials_for(PART_KIND[parts[index]]):
                        src = sprite(tcon, src_rel + state, mat)
                        if src is None:
                            missing.append(src_rel + state + "_" + mat)
                            continue
                        copy_png(src, folder + "/" + base + "_" + mat)
                        model(folder + "/" + base + "_" + mat, parent, folder + "/" + base + "_" + mat)
        # the item definition: layers by their part's material, broken heads when broken, big in hand for large tools
        def composite(size):
            folder = "item/tinkering/" + tool + ("/large" if size == "large" else "")
            out = []
            for layer, rel, index in layers:
                src_rel = rel if size == "small" else rel.replace("/", "/large/", 1)
                mats = [m for m in materials_for(PART_KIND[parts[index]]) if sprite(tcon, src_rel, m)]
                whole = by_material(index, [(m, ref(folder + "/" + layer + "_" + m)) for m in mats],
                                    ref(folder + "/" + layer + "_" + mats[0]))
                if sprite(tcon, src_rel + "_broken", "iron"):
                    bmats = [m for m in mats if sprite(tcon, src_rel + "_broken", m)]
                    broken = by_material(index, [(m, ref(folder + "/" + layer + "_broken_" + m)) for m in bmats],
                                         ref(folder + "/" + layer + "_broken_" + bmats[0]))
                    whole = {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
                             "on_true": broken, "on_false": whole}
                out.append(whole)
            return {"type": "minecraft:composite", "models": out}
        definition = composite("small")
        if large:
            definition = {"type": "minecraft:select", "property": "minecraft:display_context",
                          "cases": [{"when": ["gui", "ground", "fixed"], "model": definition}],
                          "fallback": composite("large")}
        write(os.path.join(ASSETS, "items", "tinker_" + tool + ".json"), {"model": definition})
    if missing:
        print("no Tinkers sprite for:", ", ".join(sorted(set(missing))))
    # big tools in hand: their 32px sprites held like a vanilla tool, half again as big (Tinkers' own doubled transforms
    # assume its baked large quads and come out far too big here)
    big = LARGE_IN_HAND
    write(os.path.join(ASSETS, "models", "item", "tinkering", "large_tool.json"), {
        "parent": "minecraft:item/handheld",
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 5.0, 0.5], "scale": [0.85 * big, 0.85 * big, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 5.0, 0.5], "scale": [0.85 * big, 0.85 * big, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.6, 1.13], "scale": [0.68 * big, 0.68 * big, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.6, 1.13], "scale": [0.68 * big, 0.68 * big, 0.68]},
        }})


def shifted(src, offset):
    im = Image.open(src).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, offset, im)
    return out


def parts(tcon):
    for part, _, kind, rel, offset in PARTS:
        cases = []
        for mat in materials_for(kind):
            src = sprite(tcon, rel, mat)
            if src is None:
                print("no part sprite for", part, mat)
                continue
            name = "item/tinkering/part/" + part + "_" + mat
            dest = os.path.join(ASSETS, "textures", name + ".png")
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shifted(src, offset).save(dest)
            if os.path.exists(src + ".mcmeta"):
                shutil.copyfile(src + ".mcmeta", dest + ".mcmeta")
            model(name, "minecraft:item/generated", name)
            cases.append((mat, ref(name)))
        write(os.path.join(ASSETS, "items", part + ".json"), {"model": by_material(0, cases, cases[0][1])})


def stations(tcon):
    tex = os.path.join(tcon, "assets", "tconstruct", "textures")
    for name in ("part_builder_top", "tinker_station_top", "side", "bottom"):
        copy_png(os.path.join(tex, "block", "table", name + ".png"), "block/tinkering/" + name)
    table = json.load(open(os.path.join(tcon, "assets", "tconstruct", "models", "block", "table", "table.json")))
    write(os.path.join(ASSETS, "models", "block", "tinkering", "table.json"), table)
    for block, top in (("part_builder", "part_builder_top"), ("tool_station", "tinker_station_top")):
        t = NS + ":block/tinkering/"
        write(os.path.join(ASSETS, "models", "block", "tinkering", block + ".json"), {
            "parent": NS + ":block/tinkering/table",
            "textures": {"particle": t + top, "top": t + top, "side": t + "side", "leg": t + "side",
                         "bottom": t + "bottom", "legBottom": t + "bottom"}})
        write(os.path.join(ASSETS, "blockstates", block + ".json"),
              {"variants": {"": {"model": NS + ":block/tinkering/" + block}}})
        write(os.path.join(ASSETS, "items", block + ".json"), {"model": ref("block/tinkering/" + block)})
    copy_png(os.path.join(tex, "item", "materials", "pattern.png"), "item/tinkering/pattern")
    model("item/tinkering/pattern", "minecraft:item/generated", "item/tinkering/pattern")
    write(os.path.join(ASSETS, "items", "pattern.json"), {"model": ref("item/tinkering/pattern")})
    # the screens: the Part Builder's as it is; the Tool Station's without the armour column (our part slots move)
    copy_png(os.path.join(tex, "gui", "part_builder.png"), "gui/tinkering/part_builder")
    station = Image.open(os.path.join(tex, "gui", "tinker.png")).convert("RGBA")
    fill = station.getpixel((60, 10))
    for x in range(130, 170):
        for y in range(18, 92):
            station.putpixel((x, y), fill)
    # one slot frame to draw under each part slot, kept off to the side of the sheet
    slot = station.crop((7, 101, 25, 119))
    station.paste(slot, (0, 238))
    dest = os.path.join(ASSETS, "textures", "gui", "tinkering", "tool_station.png")
    station.save(dest)
    for icon in [p[0] for p in PARTS] + [t[0] for t in TOOLS] + ["pattern"]:
        src = os.path.join(tex, "gui", "tinker_pattern", icon + ".png")
        if os.path.exists(src):
            copy_png(src, "gui/tinkering/icon/" + icon)
        else:
            print("no icon for", icon)
    shutil.copyfile(os.path.join(os.path.dirname(os.path.abspath(__file__)), "tinkers_license.txt"),
                    os.path.join(ASSETS, "textures", "item", "tinkering", "license_tinkers_construct.txt"))


def data():
    write(os.path.join(DATA, "recipe", "tinkering", "pattern.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"p": "#minecraft:planks", "s": "minecraft:stick"}, "pattern": ["ps", "sp"],
        "result": {"id": NS + ":pattern", "count": 6}})
    write(os.path.join(DATA, "recipe", "tinkering", "part_builder.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"p": NS + ":pattern", "w": "#minecraft:logs"}, "pattern": ["pp", "ww"],
        "result": {"id": NS + ":part_builder"}})
    write(os.path.join(DATA, "recipe", "tinkering", "tool_station.json"), {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [NS + ":pattern", "minecraft:crafting_table"],
        "result": {"id": NS + ":tool_station"}})
    for block in ("part_builder", "tool_station"):
        write(os.path.join(DATA, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "random_sequence": NS + ":blocks/" + block,
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + block}],
                       "condition": {"type": "minecraft:survives_explosion"}}]})
    write(os.path.join(MC_DATA, "tags", "block", "mineable", "axe.json"),
          {"values": [NS + ":part_builder", NS + ":tool_station"]})
    tags = {}
    for tool, _, _, _, _, preset, vanilla in TOOLS:
        write(os.path.join(DATA, "weapon_attributes", "tinker_" + tool + ".json"), {"parent": "bettercombat:" + preset})
        for tag in vanilla:
            tags.setdefault(tag, []).append(NS + ":tinker_" + tool)
    for tag, items in tags.items():
        path = os.path.join(MC_DATA, "tags", "item", tag + ".json")
        old = json.load(open(path)) if os.path.exists(path) else {"values": []}
        values = [v for v in old["values"] if ":tinker_" not in v] + items
        write(path, {"values": values})


def lang():
    out = {}
    for mat, name, _ in MATERIALS:
        out["material.jaymospowers." + mat] = name
    for part, name, _, _, _ in PARTS:
        out["item.jaymospowers." + part] = name
        out["item.jaymospowers." + part + ".short"] = SHORT.get(part, name)
    for tool, name, *_ in TOOLS:
        out["item.jaymospowers.tinker_" + tool] = name
    for trait, (name, desc) in TRAITS.items():
        out["trait.jaymospowers." + trait] = name
        out["trait.jaymospowers." + trait + ".desc"] = desc
    out.update(STAT_LANG)
    write(os.path.join(ASSETS, "lang", "parts", "tinkering.json"), out)


def main():
    if len(sys.argv) != 2 or not os.path.isdir(os.path.join(sys.argv[1], "assets", "tconstruct")):
        sys.exit(__doc__)
    tcon = sys.argv[1]
    for old in (os.path.join(ASSETS, "textures", "item", "tinkering"), os.path.join(ASSETS, "models", "item", "tinkering")):
        shutil.rmtree(old, ignore_errors=True)
    tools(tcon)
    parts(tcon)
    stations(tcon)
    data()
    lang()


if __name__ == "__main__":
    main()
