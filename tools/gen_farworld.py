"""Writes the assets and data of "far worlds restart the stone age" (com.jaymospowers.power.zfighter.farworld, 0.18.53):
the six first metals (their Tinkering material: every part and tool layer recoloured from the iron ones, molten, ingot, hot
ingot), the six worlds' loose rocks, the veiled ore, the field anvil, the patrol's skip writ, and their recipes, loot, tags
and lang. All 16x16 vanilla-style art painted here. Run from the repository root, after tools/tinkering_assets.py and
tools/gen_steel.py (those wipe or write the Tinkering textures this one adds beside):

    python3 -I tools/gen_farworld.py

The ids must match FarWorld / FarWorlds / PatrolWrit and the Molten and TinkerMaterial entries.
"""
import json
import os
import random
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
os.chdir(ROOT)
sys.path.insert(0, HERE)

from PIL import Image  # noqa: E402

import gen_ground_rocks as rocks  # noqa: E402
import gen_steel as steel  # noqa: E402

NS = "jaymospowers"
ASSETS, DATA, RES = steel.ASSETS, steel.DATA, steel.RES
write = steel.write

# world key, first metal id, metal's name, the metal ramp (dark to light), the world's rock (base, fleck), lang name of the world
WORLDS = [
    ("moon", "pale_alloy", "Pale Alloy", [(58, 64, 84), (130, 140, 168), (200, 212, 232), (244, 248, 255)], ((150, 152, 166), (205, 210, 230)), "Moon"),
    ("rustfall", "rust_iron", "Rust Iron", [(60, 26, 16), (128, 56, 30), (188, 92, 48), (226, 140, 84)], ((146, 78, 52), (196, 116, 64)), "Rustfall"),
    ("glacia", "ice_iron", "Ice Iron", [(30, 60, 92), (74, 128, 176), (140, 200, 232), (220, 246, 255)], ((150, 190, 214), (236, 248, 255)), "Glacia"),
    ("cinder", "cinder_bronze", "Cinder Bronze", [(58, 24, 12), (150, 70, 24), (222, 120, 36), (255, 190, 90)], ((66, 40, 38), (232, 112, 30)), "Cinder"),
    ("maw", "void_shard", "Void Shard", [(22, 10, 44), (60, 28, 110), (112, 64, 190), (190, 140, 255)], ((40, 28, 58), (144, 84, 224)), "Maw"),
    ("verdant", "amber_bronze", "Amber Bronze", [(64, 40, 10), (150, 100, 24), (222, 160, 44), (255, 214, 110)], ((92, 126, 70), (198, 172, 62)), "Verdant"),
]
HOSTS = ("stone", "deepslate", "netherrack", "blackstone")
ORES = ("coal", "iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald", "nether_gold", "quartz", "debris", "silver")
# the ores that are far ore (vanilla's, ours: silver)
ORE_BLOCKS = [f"minecraft:{p}coal_ore" for p in ("", "deepslate_")] + [
    f"minecraft:{p}{o}_ore" for o in ("iron", "copper", "gold", "redstone", "lapis", "diamond", "emerald") for p in ("", "deepslate_")] + [
    "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore", "minecraft:ancient_debris", f"{NS}:silver_ore", f"{NS}:deepslate_silver_ore",
    f"{NS}:veiled_ore"]


def ramp_at(ramp, v):
    v = max(0.0, min(1.0, v)) * (len(ramp) - 1)
    i = min(int(v), len(ramp) - 2)
    f = v - i
    return tuple(round(ramp[i][k] + (ramp[i + 1][k] - ramp[i][k]) * f) for k in range(3))


def ramped(im, ramp):
    """Every opaque pixel recoloured through {@code ramp} by its brightness (the shading kept)."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = ramp_at(ramp, (0.299 * r + 0.587 * g + 0.114 * b) / 255) + (a,)
    return im


def png(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def shade(c, f):
    return tuple(max(0, min(255, round(v * f))) for v in c)


def stone(base, fleck, seed):
    """A 16x16 rough stone: blotches of the base colour, a few flecks."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    blot = [[rng.random() for _ in range(8)] for _ in range(8)]
    for y in range(16):
        for x in range(16):
            f = 0.78 + 0.26 * blot[y // 2][x // 2] + 0.12 * (rng.random() - 0.5)
            c = shade(base, f)
            if rng.random() < 0.07:
                c = fleck
            img.putpixel((x, y), c + (255,))
    return img


# ---------------------------------------------------------------- tinkering material

def tinker_material(metal, ramp):
    """Every iron part and tool layer as this metal, its model, and a case beside iron's in each item definition."""
    tex = os.path.join(ASSETS, "textures", "item", "tinkering")
    models = os.path.join(ASSETS, "models", "item", "tinkering")
    count = 0
    for folder in steel.TINKER_DIRS:
        for name in sorted(os.listdir(os.path.join(tex, folder))):
            if not name.endswith("_iron.png"):
                continue
            base = name[:-len("_iron.png")]
            # plain iron only (pig iron, rust iron and ice iron end in "_iron" too)
            if base.endswith(("_pig", "_rust", "_ice")):
                continue
            ramped(Image.open(os.path.join(tex, folder, name)), ramp).save(os.path.join(tex, folder, base + "_" + metal + ".png"))
            model = json.load(open(os.path.join(models, folder, base + "_iron.json")))
            model["textures"]["layer0"] = model["textures"]["layer0"][:-len("_iron")] + "_" + metal
            write(os.path.join(models, folder, base + "_" + metal + ".json"), model)
            count += 1
    print(metal, "sprites:", count)


def add_cases(metals):
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
            if "iron" in whens:
                iron = cases[whens.index("iron")]
                for metal in metals:
                    if metal not in whens:
                        text = json.dumps(iron["model"]).replace('_iron"', '_' + metal + '"')
                        cases.append({"when": metal, "model": json.loads(text)})
        for value in node.values():
            add_case(value)

    for item in steel.TINKER_ITEMS:
        path = os.path.join(ASSETS, "items", item + ".json")
        definition = json.load(open(path))
        add_case(definition)
        write(path, definition)


def molten_and_ingots():
    src = os.path.join(ASSETS, "textures", "block", "tinkering", "molten", "iron.png")
    with open(src + ".mcmeta") as f:
        meta = f.read()
    ref = lambda name: {"model": {"type": "minecraft:model", "model": NS + ":" + name}}
    hot_path = os.path.join(ASSETS, "items", "hot_ingot.json")
    hot = json.load(open(hot_path))
    cases = hot["model"]["cases"]
    template = next(c for c in cases if c["when"] == "amethyst_bronze")
    for _, metal, _, ramp, _, _ in WORLDS:
        bright = [shade(c, 1.15) for c in ramp]
        glow = ramped(Image.open(src), bright)
        dest = os.path.join(os.path.dirname(src), metal + ".png")
        glow.save(dest)
        with open(dest + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
            f.write(meta)
        glow.crop((0, 0, 16, 16)).save(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", metal + ".png"))
        ingot = ramped(Image.open(os.path.join(ASSETS, "textures", "item", "tinkering", "amethyst_bronze_ingot.png")), ramp)
        ingot.save(os.path.join(ASSETS, "textures", "item", "tinkering", metal + "_ingot.png"))
        write(os.path.join(ASSETS, "models", "item", "tinkering", metal + "_ingot.json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/" + metal + "_ingot"}})
        write(os.path.join(ASSETS, "items", metal + "_ingot.json"), ref("item/tinkering/" + metal + "_ingot"))
        if not any(c["when"] == metal for c in cases):
            cases.append({"when": metal, "model": json.loads(json.dumps(template["model"]).replace("amethyst_bronze", metal))})
    write(hot_path, hot)


# ---------------------------------------------------------------- rocks, veiled ore, field anvil, writ

def rock_blocks():
    for i, (key, _, _, _, (base, fleck), _) in enumerate(WORLDS):
        block = key + "_rock"
        png(stone(base, fleck, 100 + i), "block/farworld/" + block)
        rocks.blockstate(block, "rocks", len(rocks.ROCKS), block)
        rocks.models(block, "rocks", len(rocks.ROCKS), NS + ":block/farworld/" + block)
        write(os.path.join(ASSETS, "items", block + ".json"),
              {"model": {"type": "minecraft:model", "model": f"{NS}:block/ground/{block}_1"}})
        rocks.loot(block, f"{NS}:{block}", "rocks", len(rocks.ROCKS))


def veiled_ore():
    for host in HOSTS:
        write(os.path.join(ASSETS, "models", "block", "farworld", "veiled_" + host + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/" + host}})
    write(os.path.join(ASSETS, "blockstates", "veiled_ore.json"),
          {"variants": {f"host={host}": {"model": f"{NS}:block/farworld/veiled_{host}"} for host in HOSTS}})
    write(os.path.join(DATA, "loot_table", "blocks", "veiled_ore.json"), {"type": "minecraft:block", "pools": []})


def field_anvil():
    rng = random.Random(7)
    body = stone((120, 120, 126), (170, 170, 176), 41)
    top = stone((70, 72, 80), (150, 152, 160), 42)
    for y in (4, 5, 10, 11):
        for x in range(16):
            body.putpixel((x, y), shade((96, 98, 108), 1.0 + 0.12 * (rng.random() - 0.5)) + (255,))
    for x in range(16):
        top.putpixel((x, 0), (150, 152, 160, 255))
        top.putpixel((x, 15), (40, 42, 48, 255))
    png(body, "block/farworld/field_anvil_body")
    png(top, "block/farworld/field_anvil_top")
    write(os.path.join(ASSETS, "models", "block", "farworld", "field_anvil.json"), {
        "parent": "minecraft:block/template_anvil",
        "textures": {"particle": NS + ":block/farworld/field_anvil_body", "body": NS + ":block/farworld/field_anvil_body",
                     "top": NS + ":block/farworld/field_anvil_top"}})
    model = NS + ":block/farworld/field_anvil"
    write(os.path.join(ASSETS, "blockstates", "field_anvil.json"), {"variants": {
        "facing=east": {"model": model, "y": 270}, "facing=north": {"model": model, "y": 180},
        "facing=south": {"model": model}, "facing=west": {"model": model, "y": 90}}})
    write(os.path.join(ASSETS, "items", "field_anvil.json"), {"model": {"type": "minecraft:model", "model": model}})
    write(os.path.join(DATA, "loot_table", "blocks", "field_anvil.json"), {
        "type": "minecraft:block", "random_sequence": NS + ":blocks/field_anvil", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":field_anvil"}],
            "condition": {"type": "minecraft:survives_explosion"}}]})


def writ():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    paper, edge, ink, wax, wax_dark = (232, 220, 186), (178, 160, 120), (70, 56, 40), (170, 36, 36), (110, 20, 24)
    for y in range(1, 15):
        for x in range(3, 13):
            img.putpixel((x, y), (edge if x in (3, 12) or y in (1, 14) else paper) + (255,))
    for y in (4, 6, 8):
        for x in range(5, 11 if y != 8 else 8):
            img.putpixel((x, y), ink + (255,))
    for dx, dy, c in ((0, 0, wax), (1, 0, wax), (0, 1, wax), (1, 1, wax_dark), (-1, 0, wax_dark), (0, -1, wax), (2, 1, wax_dark), (1, 2, wax_dark)):
        img.putpixel((9 + dx, 11 + dy), c + (255,))
    png(img, "item/farworld/patrol_writ")
    write(os.path.join(ASSETS, "models", "item", "farworld", "patrol_writ.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/farworld/patrol_writ"}})
    write(os.path.join(ASSETS, "items", "patrol_writ.json"),
          {"model": {"type": "minecraft:model", "model": NS + ":item/farworld/patrol_writ"}})


# ---------------------------------------------------------------- data

def recipes():
    out = os.path.join(DATA, "recipe", "farworld")
    for key, metal, _, _, _, _ in WORLDS:
        # three of the world's rocks in a row and an iron ingot under the middle one: too wide for the pocket grid
        write(os.path.join(out, metal + "_ingot.json"), {
            "type": "minecraft:crafting_shaped", "category": "misc",
            "key": {"R": f"{NS}:{key}_rock", "I": "minecraft:iron_ingot"}, "pattern": ["RRR", " I "],
            "result": {"id": f"{NS}:{metal}_ingot"}})
    write(os.path.join(out, "field_anvil.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"I": "minecraft:iron_ingot", "C": "minecraft:cobblestone"}, "pattern": ["III", " C ", "CCC"],
        "result": {"id": NS + ":field_anvil"}})


def tags():
    write(os.path.join(DATA, "tags", "block", "far_ore.json"), {"values": ORE_BLOCKS})
    mc = os.path.join(RES, "data", "minecraft", "tags", "block")
    for tag, value in (("mineable/pickaxe", NS + ":field_anvil"), ("mineable/pickaxe", NS + ":veiled_ore"), ("needs_stone_tool", NS + ":field_anvil")):
        path = os.path.join(mc, tag + ".json")
        old = json.load(open(path)) if os.path.exists(path) else {"values": []}
        if value not in old["values"]:
            old["values"].append(value)
        write(path, old)


def lang():
    keys = {
        "block.jaymospowers.veiled_ore": "Stone",
        "block.jaymospowers.field_anvil": "Field Anvil",
        "container.jaymospowers.field_anvil": "Field Anvil",
        "item.jaymospowers.patrol_writ": "Patrol Skip Writ",
        "item.jaymospowers.patrol_writ.named": "Skip Writ: %s",
        "tooltip.jaymospowers.patrol_writ": "Read it where a world's captains wait. A Commander of the Patrol takes that fight.",
        "message.jaymospowers.farworld.crumbles": "Your gear only chips it to dust.",
        "message.jaymospowers.patrol_writ.wrong_world": "This writ is for another world.",
        "message.jaymospowers.patrol_writ.champion": "No writ buys a champion's duel.",
        "message.jaymospowers.patrol_writ.no_captains": "There are no captains before you.",
        "message.jaymospowers.patrol_writ.already": "A Commander is already on the way.",
        "message.jaymospowers.patrol_writ.no_one": "No Commander answers from here.",
        "message.jaymospowers.patrol_writ.no_room": "There is no open ground for a pod near you.",
        "message.jaymospowers.patrol_writ.called": "The writ goes up in a flash of light. A pod is coming down.",
        "message.jaymospowers.patrol_writ.landed": "%s steps out of the pod. \"Stand back. This is Patrol business.\"",
        "message.jaymospowers.patrol_writ.farewell": "%s: \"Captains are down. The rest is yours.\"",
        "message.jaymospowers.patrol_writ.returned": "The Commander could not come. Your writ is returned.",
        "talk.jaymospowers.patrol.opt.writ": "Ask for a skip writ",
        "talk.jaymospowers.patrol.opt.writ_for": "A writ for %s",
        "talk.jaymospowers.patrol.writ_not_yet": "\"A writ is for sergeants and better. Make more arrests.\"",
        "talk.jaymospowers.patrol.writ_offer": "\"Sign here. One writ for each world, and the Patrol will not do it twice.\"",
        "talk.jaymospowers.patrol.writ_given": "\"There. A Commander will come when you read it. The champion and the boss are still yours.\"",
    }
    for i, name in enumerate(("Commander Vael", "Commander Orrin", "Commander Teska", "Commander Brandt")):
        keys[f"entity.jaymospowers.patrol_commander.{i}"] = name
    for key, metal, name, _, _, world in WORLDS:
        keys[f"block.jaymospowers.{key}_rock"] = world + " Rock"
        keys[f"item.jaymospowers.{metal}_ingot"] = name + ("" if metal == "void_shard" else " Ingot")
        keys[f"material.jaymospowers.{metal}"] = name
        keys[f"molten.jaymospowers.{metal}"] = "Molten " + name
    write(os.path.join(ASSETS, "lang", "parts", "zfighter_farworld.json"), keys)


def main():
    for _, metal, _, ramp, _, _ in WORLDS:
        tinker_material(metal, ramp)
    add_cases([w[1] for w in WORLDS])
    molten_and_ingots()
    rock_blocks()
    veiled_ore()
    field_anvil()
    writ()
    recipes()
    tags()
    lang()


if __name__ == "__main__":
    main()
