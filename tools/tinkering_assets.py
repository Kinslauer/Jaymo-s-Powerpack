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
    ("flint", "Flint", "all"), ("bone", "Bone", "all"), ("seared_stone", "Seared Stone", "all"),
    ("copper", "Copper", "all"), ("iron", "Iron", "all"), ("silver", "Silver", "all"), ("rose_gold", "Rose Gold", "all"),
    ("amethyst_bronze", "Amethyst Bronze", "all"), ("pig_iron", "Pig Iron", "all"), ("slimesteel", "Slimesteel", "all"),
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
    # (0.11.77) for swords' weapon heads (tinkering/WeaponParts), drawn from Tinkers' sword guard
    ("crossguard", "Crossguard", "handle", "sword/guard", (3, -3)),
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
    "searing": ("Searing", "Hits harder on anything that can burn."),
    "enhanced": ("Enhanced", "One more modifier slot."),
    "crumbling": ("Crumbling", "Digs faster through blocks that need no tool."),
    "tasty": ("Tasty", "When you're hungry you sometimes take a bite of it."),
    "overslime": ("Overslime", "Slowly mends itself while you carry it."),
}

# modifier id, name, what it does (the items are in com.jaymospowers.tinkering.Modifier)
MODIFIERS = [
    ("haste", "Haste", "Mines faster (redstone block)."),
    ("sharpness", "Sharpness", "Hits harder (quartz block)."),
    ("luck", "Luck", "Fortune on tools, looting on blades (lapis block)."),
    ("silky", "Silky", "Silk touch (cobweb and gold block)."),
    ("fiery", "Fiery", "Sets what it hits alight (blaze rod)."),
    ("knockback", "Knockback", "Knocks what it hits back (piston)."),
    ("reinforced", "Reinforced", "Wears more slowly (obsidian)."),
    ("diamond", "Diamond", "+500 durability, a little more attack, mines like diamond."),
    ("emerald", "Emerald", "+50% durability, mines at least like iron."),
    ("netherite", "Netherite", "+20% durability, more attack, mines like netherite, won't burn (needs Diamond)."),
]

# molten id, name, Tinkers' fluid folder (under textures/fluid), or None for a tinted plain molten texture
MOLTEN = [
    ("iron", "Molten Iron", "molten/ore/iron"), ("gold", "Molten Gold", "molten/ore/gold"),
    ("copper", "Molten Copper", "molten/ore/copper"), ("silver", "Molten Silver", "molten/compat_ore/silver"),
    ("amethyst", "Molten Amethyst", "molten/ore/amethyst"),
    ("rose_gold", "Molten Rose Gold", "molten/alloy/rose_gold"),
    ("amethyst_bronze", "Molten Amethyst Bronze", "molten/alloy/amethyst_bronze"),
    ("pig_iron", "Molten Pig Iron", "molten/alloy/pig_iron"), ("slimesteel", "Molten Slimesteel", "molten/alloy/slimesteel"),
    ("slime", "Liquid Slime", "slime/earth"), ("honey", "Honey", "food/honey"), ("blood", "Blood", None),
]
BLOOD_TINT = (120, 8, 8)
# plain fluids tinted: molten id to (r, g, b)
TINTS = {"blood": BLOOD_TINT}

# alloy ingots: our id, Tinkers' texture
INGOTS = [("rose_gold_ingot", "Rose Gold Ingot", "rose_gold_ingot"),
          ("amethyst_bronze_ingot", "Amethyst Bronze Ingot", "amethyst_bronze_ingot"),
          ("pig_iron_ingot", "Pig Iron Ingot", "pigiron_ingot"), ("slimesteel_ingot", "Slimesteel Ingot", "slimesteel_ingot")]
CASTS = ["ingot", "nugget"] + [p[0] for p in PARTS]
# casts Tinkers has no sprite for
CAST_SPRITE = {"crossguard": "tool_binding"}

# (0.11.77) weapon casts (tinkering/WeaponParts)
WEAPON_CAST_LANG = {
    "item.jaymospowers.weapon_cast": "Weapon Cast",
    "tooltip.jaymospowers.tinkering.weapon_cast.makes": "Casts: %s",
    "tooltip.jaymospowers.tinkering.weapon_cast.blank": "Pour gold over a weapon head on a casting table to make its cast.",
}

SMELTERY_LANG = {
    "block.jaymospowers.grout": "Grout",
    "block.jaymospowers.seared_bricks": "Seared Bricks",
    "block.jaymospowers.seared_glass": "Seared Glass",
    "block.jaymospowers.smeltery_controller": "Smeltery Controller",
    "block.jaymospowers.seared_drain": "Seared Drain",
    "block.jaymospowers.seared_faucet": "Seared Faucet",
    "block.jaymospowers.casting_table": "Casting Table",
    "block.jaymospowers.casting_basin": "Casting Basin",
    "item.jaymospowers.seared_brick": "Seared Brick",
    "container.jaymospowers.smeltery": "Smeltery",
    "message.jaymospowers.smeltery.unformed": "The smeltery isn't complete yet.",
    "gui.jaymospowers.smeltery.unformed": "Not a smeltery yet: it needs a floor of seared bricks and seared walls all round an open space, with this controller in the wall.",
    "gui.jaymospowers.smeltery.to_bottom": "Click to pour this one first",
    "gui.jaymospowers.smeltery.capacity": "%s of %s",
    "gui.jaymospowers.smeltery.fuel": "Lava: %s / %s mB",
    "gui.jaymospowers.smeltery.fuel_hint": "Put lava buckets in the slot above",
    "molten.jaymospowers.mb": "%s mB",
    "molten.jaymospowers.ingot": "%s ingot",
    "molten.jaymospowers.ingots": "%s ingots",
    "molten.jaymospowers.and": "%s, %s",
    "tooltip.jaymospowers.tinkering.cast": "Set it on a casting table and pour metal over it.",
    "gui.jaymospowers.part_builder.cast": "%s parts are cast: melt it in a smeltery and pour it over a cast.",
    "gui.jaymospowers.tool_station.modify": "Adding %s (%s slots left after)",
    "gui.jaymospowers.tool_station.max": "%s is as high as it goes",
    "gui.jaymospowers.tool_station.no_slots": "No modifier slots free: level the tool up to earn more",
    "gui.jaymospowers.tool_station.clash": "%s and %s don't go together",
    "gui.jaymospowers.tool_station.needs": "Needs %s first",
    "gui.jaymospowers.tool_station.weapon": "%s doesn't go on this weapon",
    "gui.jaymospowers.tool_station.embed": "Setting diamonds along its edge",
    "tooltip.jaymospowers.tinkering.level": "Level %s (%s / %s xp)",
    "tooltip.jaymospowers.tinkering.level_max": "Level %s (top level)",
    "tooltip.jaymospowers.tinkering.slots": "Modifier slots: %s of %s free",
    "message.jaymospowers.tinkering.level_up": "Your %s reached level %s: one more modifier slot!",
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
        elif icon == "crossguard":
            # (0.11.77) Tinkers has no pattern icon for it: the guard's outline in the icons' grey
            guard = shifted(sprite(tcon, "sword/guard", "iron"), (3, -3))
            out = Image.new("RGBA", guard.size, (0, 0, 0, 0))
            for y in range(guard.size[1]):
                for x in range(guard.size[0]):
                    if guard.getpixel((x, y))[3] > 0:
                        out.putpixel((x, y), (50, 50, 50, 255))
            dest = os.path.join(ASSETS, "textures", "gui", "tinkering", "icon", "crossguard.png")
            out.save(dest)
        else:
            print("no icon for", icon)
    shutil.copyfile(os.path.join(os.path.dirname(os.path.abspath(__file__)), "tinkers_license.txt"),
                    os.path.join(ASSETS, "textures", "item", "tinkering", "license_tinkers_construct.txt"))


def smeltery(tcon):
    tex = os.path.join(tcon, "assets", "tconstruct", "textures")
    models_dir = os.path.join(tcon, "assets", "tconstruct", "models", "block", "template")
    shutil.rmtree(os.path.join(ASSETS, "textures", "block", "tinkering", "molten"), ignore_errors=True)
    t = NS + ":block/tinkering/"
    block_tex = {
        "grout": "block/grout", "seared_bricks": "block/smeltery/seared/bricks", "seared_stone": "block/smeltery/seared/stone",
        "seared_glass": "block/smeltery/seared_glass", "smeltery_inactive": "block/smeltery/controller/smeltery_inactive",
        "smeltery_active": "block/smeltery/controller/smeltery_active", "drain": "block/smeltery/io/drain_front_active",
        "table_top": "block/smeltery/casting/table_top", "table_side": "block/smeltery/casting/table_side",
        "table_bottom": "block/smeltery/casting/table_bottom", "basin_top": "block/smeltery/casting/basin_top",
        "basin_side": "block/smeltery/casting/basin_side", "basin_bottom": "block/smeltery/casting/basin_bottom",
    }
    for name, rel in block_tex.items():
        copy_png(os.path.join(tex, rel + ".png"), "block/tinkering/" + name)

    def block_model(name, data):
        write(os.path.join(ASSETS, "models", "block", "tinkering", name + ".json"), data)

    def simple(block, model_name=None):
        model_name = model_name or block
        write(os.path.join(ASSETS, "blockstates", block + ".json"), {"variants": {"": {"model": t + model_name}}})
        write(os.path.join(ASSETS, "items", block + ".json"), {"model": ref("block/tinkering/" + model_name)})

    for block in ("grout", "seared_bricks", "seared_glass"):
        block_model(block, {"parent": "minecraft:block/cube_all", "textures": {"all": t + block}})
        simple(block)
    rotations = {"north": 0, "east": 90, "south": 180, "west": 270}
    for lit in ("inactive", "active"):
        block_model("smeltery_" + lit, {"parent": "minecraft:block/orientable", "textures": {
            "top": t + "seared_bricks", "side": t + "seared_bricks", "front": t + "smeltery_" + lit}})
    write(os.path.join(ASSETS, "blockstates", "smeltery_controller.json"), {"variants": {
        "facing=%s,lit=%s" % (f, str(lit).lower()): {"model": t + ("smeltery_active" if lit else "smeltery_inactive"), "y": r}
        for f, r in rotations.items() for lit in (False, True)}})
    write(os.path.join(ASSETS, "items", "smeltery_controller.json"), {"model": ref("block/tinkering/smeltery_inactive")})
    block_model("seared_drain", {"parent": "minecraft:block/orientable", "textures": {
        "top": t + "seared_bricks", "side": t + "seared_bricks", "front": t + "drain"}})
    write(os.path.join(ASSETS, "blockstates", "seared_drain.json"), {"variants": {
        "facing=" + f: {"model": t + "seared_drain", "y": r} for f, r in rotations.items()}})
    write(os.path.join(ASSETS, "items", "seared_drain.json"), {"model": ref("block/tinkering/seared_drain")})
    # Tinkers' own models for the faucet, table and basin (its loader keys dropped; 26.3 picks cutout by itself)
    for template, name, textures in (("faucet", "seared_faucet", {"tex": t + "seared_stone"}),
                                     ("table", "casting_table", {"top": t + "table_top", "side": t + "table_side", "bottom": t + "table_bottom"}),
                                     ("basin", "casting_basin", {"top": t + "basin_top", "side": t + "basin_side", "bottom": t + "basin_bottom"})):
        tpl = json.load(open(os.path.join(models_dir, template + ".json")))
        tpl.pop("render_type", None)
        tpl.pop("comment", None)
        tpl["parent"] = "minecraft:block/block"
        tpl["textures"] = dict(tpl.get("textures", {}))
        tpl["textures"].update(textures)
        if "particle" in tpl["textures"] and tpl["textures"]["particle"].startswith("#"):
            tpl["textures"]["particle"] = textures[tpl["textures"]["particle"][1:]]
        block_model(name, tpl)
    # the faucet's model faces north (the drain behind it, to the south)
    write(os.path.join(ASSETS, "blockstates", "seared_faucet.json"), {"variants": {
        "facing=%s,powered=%s" % (f, p): {"model": t + "seared_faucet", "y": r}
        for f, r in rotations.items() for p in ("false", "true")}})
    write(os.path.join(ASSETS, "items", "seared_faucet.json"), {"model": ref("block/tinkering/seared_faucet")})
    for block in ("casting_table", "casting_basin"):
        simple(block)

    # items: seared brick, alloy ingots, casts
    copy_png(os.path.join(tex, "item", "materials", "seared_brick.png"), "item/tinkering/seared_brick")
    model("item/tinkering/seared_brick", "minecraft:item/generated", "item/tinkering/seared_brick")
    write(os.path.join(ASSETS, "items", "seared_brick.json"), {"model": ref("item/tinkering/seared_brick")})
    for item, _, src in INGOTS:
        copy_png(os.path.join(tex, "item", "materials", src + ".png"), "item/tinkering/" + item)
        model("item/tinkering/" + item, "minecraft:item/generated", "item/tinkering/" + item)
        write(os.path.join(ASSETS, "items", item + ".json"), {"model": ref("item/tinkering/" + item)})
    for cast in CASTS:
        copy_png(os.path.join(tex, "item", "cast", CAST_SPRITE.get(cast, cast) + ".png"), "item/tinkering/cast/" + cast)
        model("item/tinkering/cast/" + cast, "minecraft:item/generated", "item/tinkering/cast/" + cast)
        write(os.path.join(ASSETS, "items", cast + "_cast.json"), {"model": ref("item/tinkering/cast/" + cast)})
    # (0.11.77) the weapon cast looks like the small blade's (weapon heads: tools/weapon_parts_assets.py)
    write(os.path.join(ASSETS, "items", "weapon_cast.json"), {"model": ref("item/tinkering/cast/small_blade")})

    # molten metal: animated in the world (block atlas), its first frame for the screen
    def save_molten(im, mcmeta, mid):
        dest = os.path.join(ASSETS, "textures", "block", "tinkering", "molten", mid + ".png")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        im.save(dest)
        write(dest + ".mcmeta", mcmeta)
        gui = os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", mid + ".png")
        os.makedirs(os.path.dirname(gui), exist_ok=True)
        im.crop((0, 0, 16, 16)).save(gui)

    for mid, _, folder in MOLTEN:
        src = os.path.join(tex, "fluid", folder if folder else "molten", "still.png")
        im = Image.open(src).convert("RGBA")
        if mid in TINTS:
            tint = TINTS[mid]
            px = im.load()
            for y in range(im.size[1]):
                for x in range(im.size[0]):
                    r, g, b, a = px[x, y]
                    px[x, y] = (min(255, r * tint[0] // 255), min(255, g * tint[1] // 255 + 4), min(255, b * tint[2] // 255 + 4), a)
        save_molten(im, open(src + ".mcmeta").read(), mid)
    lava = os.path.join(tex, "fluid", "molten", "blaze", "still.png")
    Image.open(lava).convert("RGBA").crop((0, 0, 16, 16)).save(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", "lava.png"))
    copy_png(os.path.join(tex, "gui", "heating_structure.png"), "gui/tinkering/smeltery")


def shaped(name, pattern, key, result, count=1):
    write(os.path.join(DATA, "recipe", "tinkering", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "key": key, "pattern": pattern,
        "result": {"id": result, "count": count}})


def smeltery_data():
    b = {"b": NS + ":seared_brick"}
    write(os.path.join(DATA, "recipe", "tinkering", "grout.json"), {
        "type": "minecraft:crafting_shapeless", "category": "building",
        "ingredients": ["minecraft:clay_ball", "#minecraft:sand", "minecraft:gravel"], "result": {"id": NS + ":grout", "count": 2}})
    write(os.path.join(DATA, "recipe", "tinkering", "seared_brick.json"), {
        "type": "minecraft:smelting", "category": "misc", "ingredient": NS + ":grout",
        "result": {"id": NS + ":seared_brick"}, "experience": 0.3, "cookingtime": 200})
    shaped("seared_bricks", ["bb", "bb"], b, NS + ":seared_bricks")
    shaped("seared_glass", [" b ", "bgb", " b "], dict(b, g="minecraft:glass"), NS + ":seared_glass")
    shaped("smeltery_controller", ["bbb", "b b", "bbb"], b, NS + ":smeltery_controller")
    shaped("seared_drain", ["b b", "b b", "b b"], b, NS + ":seared_drain")
    shaped("seared_faucet", ["b b", " b "], b, NS + ":seared_faucet", 2)
    shaped("casting_table", ["bbb", "b b", "b b"], b, NS + ":casting_table")
    shaped("casting_basin", ["b b", "b b", "bbb"], b, NS + ":casting_basin")
    blocks = ["grout", "seared_bricks", "seared_glass", "smeltery_controller", "seared_drain", "seared_faucet",
              "casting_table", "casting_basin"]
    for block in blocks:
        write(os.path.join(DATA, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "random_sequence": NS + ":blocks/" + block,
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + block}],
                       "condition": {"type": "minecraft:survives_explosion"}}]})
    for tag, values in (("pickaxe", [NS + ":" + x for x in blocks if x != "grout"]), ("shovel", [NS + ":grout"])):
        path = os.path.join(MC_DATA, "tags", "block", "mineable", tag + ".json")
        old = json.load(open(path)) if os.path.exists(path) else {"values": []}
        write(path, {"values": [v for v in old["values"] if v not in values] + values})


# (0.8.76) Stage 3: smiths sell parts and casts (vanilla trade data, so kingdoms' smiths and MCA's sell them too).
# (profession, level, material or None for a cast, part or cast shape, emeralds, max uses, xp)
TRADES = [
    ("toolsmith", 2, "iron", "pick_head", 4, 6, 10),
    ("toolsmith", 2, None, "ingot", 6, 3, 10),
    ("toolsmith", 3, "rose_gold", "pick_head", 6, 4, 15),
    ("toolsmith", 3, None, "pick_head", 10, 3, 15),
    ("toolsmith", 4, "amethyst_bronze", "hammer_head", 14, 3, 20),
    ("toolsmith", 4, "pig_iron", "tough_handle", 10, 3, 20),
    ("toolsmith", 5, "slimesteel", "large_plate", 16, 2, 30),
    ("weaponsmith", 2, "iron", "small_blade", 4, 6, 10),
    ("weaponsmith", 3, "rose_gold", "tool_binding", 5, 4, 15),
    ("weaponsmith", 3, None, "small_blade", 10, 3, 15),
    ("weaponsmith", 4, "pig_iron", "broad_blade", 14, 3, 20),
    ("weaponsmith", 5, "slimesteel", "tough_handle", 14, 2, 30),
    ("armorer", 3, None, "large_plate", 12, 3, 15),
    ("mason", 2, None, "seared_bricks", 1, 12, 10),
]


def trades_data():
    tags = {}
    for prof, level, material, shape, price, uses, xp in TRADES:
        if shape == "seared_bricks":
            name, gives = "seared_bricks", {"id": NS + ":seared_bricks", "count": 4}
        elif material is None:
            name, gives = shape + "_cast", {"id": NS + ":" + shape + "_cast"}
        else:
            name = material + "_" + shape
            gives = {"id": NS + ":" + shape, "components": {
                NS + ":part_material": material,
                "minecraft:custom_model_data": {"strings": [material]},
                "minecraft:item_name": {"translate": "jaymospowers.tinkering.named", "with": [
                    {"translate": "material.jaymospowers." + material}, {"translate": "item.jaymospowers." + shape}]}}}
        trade = NS + ":tinkering/" + prof + "/" + name
        write(os.path.join(DATA, "villager_trade", "tinkering", prof, name + ".json"), {
            "gives": gives, "max_uses": uses, "reputation_discount": 0.05,
            "wants": {"id": "minecraft:emerald", "count": price}, "xp": xp})
        tags.setdefault((prof, level), []).append(trade)
    for (prof, level), values in tags.items():
        write(os.path.join(MC_DATA, "tags", "villager_trade", prof, "level_%d.json" % level), {"values": values})


# Andy (2026-10-13): if Tinkering can make it, the vanilla recipe goes, and gold and diamond tools and armour too
# (Tinkering's Diamond modifier gives the diamond tier). Its materials cover wood, stone, copper and iron, and its tools
# the pickaxe, axe (hand axe), shovel and hoe (mattock: digs and tills) and sword. Fabric's false load condition drops
# vanilla's file of the same id; their recipe-book advancements go too.
OFF_TIERS = ("wooden", "stone", "copper", "iron", "golden", "diamond")
OFF_TOOLS = ("pickaxe", "axe", "shovel", "hoe", "sword")
# Andy: gold and diamond armour go too (diamond tools come from Tinkering's Diamond modifier)
OFF_ARMOUR = ("golden", "diamond")
ARMOUR = ("helmet", "chestplate", "leggings", "boots")
# Andy: no craftable diamond weapons at all (Armory's skip their recipe in tools/armory_assets.py, WeaponMod's are
# dropped by tinkering/Uncraftable). (0.11.77) Andy: all weapons are forged, so stone and metal spears are put together
# at the Tool Station from a Spear Tip (tinkering/WeaponParts)
OFF_WEAPONS = ("diamond_spear", "stone_spear", "copper_spear", "iron_spear", "golden_spear")
# (0.15.36) Andy: "All tools need crafting routes through jaymos": these are forged at the Tool Station from cast heads
# too (tinkering/WeaponParts)
OFF_FORGED_TOOLS = ("fishing_rod", "brush")


def vanilla_off():
    off = {"fabric:load_conditions": [{"condition": "fabric:false"}]}
    for folder in ("recipe", os.path.join("advancement", "recipes", "tools"), os.path.join("advancement", "recipes", "combat")):
        shutil.rmtree(os.path.join(MC_DATA, folder), ignore_errors=True)
    for tier in OFF_TIERS:
        for tool in OFF_TOOLS:
            item = tier + "_" + tool
            write(os.path.join(MC_DATA, "recipe", item + ".json"), off)
            folder = "combat" if tool == "sword" else "tools"
            write(os.path.join(MC_DATA, "advancement", "recipes", folder, item + ".json"), off)
    for tier in OFF_ARMOUR:
        for piece in ARMOUR:
            item = tier + "_" + piece
            write(os.path.join(MC_DATA, "recipe", item + ".json"), off)
            write(os.path.join(MC_DATA, "advancement", "recipes", "combat", item + ".json"), off)
    for item in OFF_WEAPONS:
        write(os.path.join(MC_DATA, "recipe", item + ".json"), off)
        write(os.path.join(MC_DATA, "advancement", "recipes", "combat", item + ".json"), off)
    for item in OFF_FORGED_TOOLS:
        write(os.path.join(MC_DATA, "recipe", item + ".json"), off)
        write(os.path.join(MC_DATA, "advancement", "recipes", "tools", item + ".json"), off)

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
    out.update(SMELTERY_LANG)
    for mid, name, _ in MOLTEN:
        out["molten.jaymospowers." + mid] = name
    for item, name, _ in INGOTS:
        out["item.jaymospowers." + item] = name
    for cast in CASTS:
        base = {"ingot": "Ingot", "nugget": "Nugget"}.get(cast) or next(p[1] for p in PARTS if p[0] == cast)
        out["item.jaymospowers." + cast + "_cast"] = base + " Cast"
    out.update(WEAPON_CAST_LANG)
    for mid, name, desc in MODIFIERS:
        out["modifier.jaymospowers." + mid] = name
        out["modifier.jaymospowers." + mid + ".desc"] = desc
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
    smeltery(tcon)
    data()
    smeltery_data()
    trades_data()
    vanilla_off()
    lang()


if __name__ == "__main__":
    main()
    # (0.9.0) the cleaver redrawn short, over Tinkers' great blade
    import gen_tinker_cleaver
    gen_tinker_cleaver.main()
