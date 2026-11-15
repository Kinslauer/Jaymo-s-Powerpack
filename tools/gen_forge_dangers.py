"""Writes the assets, data and lang of Andy's GT New Horizons picks, patch 5 (docs/GTNH-Plan.md, "The forge's dangers"):

* cracked seared bricks (a blockstate of seared bricks, its own look, and a cracked brick broken out stays cracked);
* cracked bricks, cracked mud bricks and cracked red nether bricks (a kiln's bricks cracked; vanilla has cracked nether
  bricks already), their blocks, items, loot and tags (they still wall a kiln);
* smithing gloves (a wrist bauble), the orb and rod casts, molten ender and molten blaze;
* the tags (hot to hold, forge walls), the gloves' recipe, and the names and JEI pages (lang/parts/forge_dangers.json).

All art is vanilla's own textures recoloured or drawn over (the cracks are vanilla's, lifted off its cracked stone
bricks), so point it at the 26.3 client jar. Run after tools/tinkering_assets.py, tools/gen_stone_age.py and
tools/gen_coke_and_steel.py:

    python3 -I tools/gen_forge_dangers.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.tinkering.dangers.Cracks, SmithingGloves and smeltery.CastItem/Molten. Safe to run
again (every file is overwritten, every edit to a shared file is idempotent).
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_stone_age as sa  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
NS = "jaymospowers"

# ---- tuning ----
# leather and string into one pair of smithing gloves
GLOVE_LEATHER = 3
# how dark a crack is drawn over a brick (its colour times this), and how much darker vanilla's cracked brick must be
CRACK_SHADE, CRACK_EDGE = 0.45, 0.06

CRACKED_KILN = {"cracked_bricks": ("block/bricks", "block/stone_bricks", "block/cracked_stone_bricks"),
                "cracked_mud_bricks": ("block/mud_bricks", "block/stone_bricks", "block/cracked_stone_bricks"),
                "cracked_red_nether_bricks": ("block/red_nether_bricks", "block/nether_bricks", "block/cracked_nether_bricks")}

LANG = {
    "block.jaymospowers.cracked_bricks": "Cracked Bricks",
    "block.jaymospowers.cracked_mud_bricks": "Cracked Mud Bricks",
    "block.jaymospowers.cracked_red_nether_bricks": "Cracked Red Nether Bricks",
    "item.jaymospowers.smithing_gloves": "Smithing Gloves",
    "item.jaymospowers.orb_cast": "Orb Cast",
    "item.jaymospowers.rod_cast": "Rod Cast",
    "molten.jaymospowers.ender": "Molten Ender",
    "molten.jaymospowers.blaze": "Molten Blaze",
    "jei.jaymospowers.creature_melting": "Creature Melting",
    "jei.jaymospowers.creature_melting.burn": "%s %s a burn",
    "jei.jaymospowers.brick_mending": "Mending Bricks",
    "jei.jaymospowers.brick_mending.how": "Hammer on the cracked brick, grout in hand or pack",
    "jei.jaymospowers.smithing_gloves": "Thick leather for the wrist slot. Worn, they take the heat of hot metal, a pot straight off the fire, a lava bucket or a blaze rod for your hands, as tongs do, and wear a little for it.",
    "jei.jaymospowers.grout.mends": "Mends a cracked seared brick or kiln brick: use a hammer on the brick with grout in your other hand or your pack, one grout a brick.",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def png(path, im):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def ref(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def merge_tag(path, values):
    """Adds values to a tag file (ours, appended to, or a new one), keeping what's there."""
    old = json.load(open(path)) if os.path.exists(path) else {"values": []}
    have = old.get("values", [])
    keys = [v if isinstance(v, str) else v.get("id") for v in have]
    for v in values:
        if (v if isinstance(v, str) else v.get("id")) not in keys:
            have.append(v)
    old["values"] = have
    write(path, old)


# ---------------------------------------------------------------- art

def cracked(brick, whole, broken):
    """{@code brick} with vanilla's cracks: wherever its cracked texture is darker than its whole one, darkened."""
    out = brick.convert("RGBA").copy()
    for y in range(16):
        for x in range(16):
            if sa.lum(broken.getpixel((x, y))) < sa.lum(whole.getpixel((x, y))) - CRACK_EDGE:
                r, g, b, a = out.getpixel((x, y))
                out.putpixel((x, y), (round(r * CRACK_SHADE), round(g * CRACK_SHADE), round(b * CRACK_SHADE), a))
    return out


GLOVE = [
    "................",
    "......##.##.....",
    "....##.##.##....",
    "....##.##.##....",
    "....##.##.##.##.",
    "....##.##.##.##.",
    "....###########.",
    ".##.###########.",
    ".###.##########.",
    "..#############.",
    "...###########..",
    "....#########...",
    "....CCCCCCCCC...",
    "....CCCCCCCCC...",
    "....CCCCCCCCC...",
    "................",
]


def gloves(leather):
    """A thick leather glove: the leather's own shading over the glove's shape, a darker cuff, a dark outline."""
    tones = sorted({leather.getpixel((x, y))[:3] for y in range(16) for x in range(16) if leather.getpixel((x, y))[3]}, key=sa.lum)
    dark, light = tones[0], tones[-1]
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and GLOVE[y][x] != "."

    for y in range(16):
        for x in range(16):
            if not solid(x, y):
                continue
            edge = not (solid(x - 1, y) and solid(x + 1, y) and solid(x, y - 1) and solid(x, y + 1))
            px = leather.getpixel((x, y))
            t = (sa.lum(px) - sa.lum(dark)) / max(1e-6, sa.lum(light) - sa.lum(dark)) if px[3] else 0.5
            if GLOVE[y][x] == "C":
                t *= 0.55
            if edge:
                colour = tuple(round(c * 0.55) for c in dark)
            else:
                colour = sa.ramp(dark, light, 0.25 + 0.6 * t)
            out.putpixel((x, y), colour + (255,))
    return out


def cast_plate(golds, hollow):
    """A gold cast's plate (shaded like the ingot cast) with a hollow where {@code hollow(x, y)}."""
    cast = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if hollow(x, y):
                continue
            light = y <= 1 or x <= 1 or hollow(x + 1, y + 1)
            dark = y >= 14 or x >= 14 or hollow(x - 1, y - 1)
            colour = golds[-1] if light else golds[0] if dark else golds[len(golds) // 2 + ((x * 3 + y) % 2)]
            cast.putpixel((x, y), colour + (255,))
    return cast


def art(jar):
    def vanilla(rel):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

    block = os.path.join(ASSETS, "textures", "block")
    stone, stone_cracked = vanilla("block/stone_bricks"), vanilla("block/cracked_stone_bricks")
    seared = Image.open(os.path.join(block, "tinkering", "seared_bricks.png")).convert("RGBA")
    png(os.path.join(block, "tinkering", "cracked_seared_bricks.png"), cracked(seared, stone, stone_cracked))
    for name, (base, whole, broken) in CRACKED_KILN.items():
        png(os.path.join(block, "dangers", name + ".png"), cracked(vanilla(base), vanilla(whole), vanilla(broken)))

    item = os.path.join(ASSETS, "textures", "item")
    png(os.path.join(item, "dangers", "smithing_gloves.png"), gloves(vanilla("item/leather")))

    ingot = Image.open(os.path.join(item, "tinkering", "cast", "ingot.png")).convert("RGBA")
    golds = sorted({ingot.getpixel((x, y))[:3] for y in range(16) for x in range(16) if ingot.getpixel((x, y))[3]}, key=sa.lum)
    png(os.path.join(item, "tinkering", "cast", "orb.png"), cast_plate(golds, lambda x, y: (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 17))
    png(os.path.join(item, "tinkering", "cast", "rod.png"), cast_plate(golds, lambda x, y: 3 <= x <= 12 and 3 <= y <= 12 and abs(x + y - 15) <= 1))

    # molten ender and blaze, from molten iron's moving frames (the smeltery's block texture and its gauge)
    src = os.path.join(block, "tinkering", "molten", "iron.png")
    with open(src + ".mcmeta", encoding="utf-8") as f:
        meta = json.load(f)
    for mid, dark, light in (("ender", (8, 42, 38), (96, 224, 192)), ("blaze", (168, 72, 0), (255, 238, 124))):
        hot = sa.recolour(Image.open(src), dark, light)
        png(os.path.join(block, "tinkering", "molten", mid + ".png"), hot)
        write(os.path.join(block, "tinkering", "molten", mid + ".png.mcmeta"), meta)
        png(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", mid + ".png"), hot.crop((0, 0, 16, 16)))


# ---------------------------------------------------------------- models

def assets():
    blockstates = os.path.join(ASSETS, "blockstates")
    bmodels = os.path.join(ASSETS, "models", "block")
    items = os.path.join(ASSETS, "items")

    # seared bricks, whole and cracked; the item shows cracked when it was broken out cracked
    write(os.path.join(bmodels, "tinkering", "cracked_seared_bricks.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/tinkering/cracked_seared_bricks"}})
    whole, broken = NS + ":block/tinkering/seared_bricks", NS + ":block/tinkering/cracked_seared_bricks"
    write(os.path.join(blockstates, "seared_bricks.json"), {"variants": {"cracked=false": {"model": whole}, "cracked=true": {"model": broken}}})
    write(os.path.join(items, "seared_bricks.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:block_state", "block_state_property": "cracked",
        "cases": [{"when": "true", "model": {"type": "minecraft:model", "model": broken}}],
        "fallback": {"type": "minecraft:model", "model": whole}}})

    for name in CRACKED_KILN:
        model = NS + ":block/dangers/" + name
        write(os.path.join(bmodels, "dangers", name + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": model}})
        write(os.path.join(blockstates, name + ".json"), {"variants": {"": {"model": model}}})
        write(os.path.join(items, name + ".json"), ref(model))

    write(os.path.join(ASSETS, "models", "item", "dangers", "smithing_gloves.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/dangers/smithing_gloves"}})
    write(os.path.join(items, "smithing_gloves.json"), ref(NS + ":item/dangers/smithing_gloves"))
    for shape in ("orb", "rod"):
        write(os.path.join(ASSETS, "models", "item", "tinkering", "cast", shape + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/cast/" + shape}})
        write(os.path.join(items, shape + "_cast.json"), ref(NS + ":item/tinkering/cast/" + shape))


# ---------------------------------------------------------------- data

def data():
    def ids(*names):
        return [NS + ":" + n for n in names]

    def table(block, entry):
        return {"type": "minecraft:block", "random_sequence": NS + ":blocks/" + block, "pools": [
            {"rolls": 1, "entries": [entry], "condition": {"type": "minecraft:survives_explosion"}}]}

    loot = os.path.join(DATA, NS, "loot_table", "blocks")
    # a cracked seared brick broken out stays cracked (its item carries the state, and sets down cracked)
    write(os.path.join(loot, "seared_bricks.json"), table("seared_bricks", {
        "type": "minecraft:item", "name": NS + ":seared_bricks", "modifier": [{
            "type": "minecraft:copy_state", "block": NS + ":seared_bricks", "properties": ["cracked"],
            "condition": {"type": "minecraft:match_block", "blocks": NS + ":seared_bricks", "state": {"cracked": "true"}}}]}))
    for name in CRACKED_KILN:
        write(os.path.join(loot, name + ".json"), table(name, {"type": "minecraft:item", "name": NS + ":" + name}))

    ours = os.path.join(DATA, NS, "tags")
    cracked_kiln = ids(*CRACKED_KILN) + ["minecraft:cracked_nether_bricks"]
    merge_tag(os.path.join(ours, "block", "kiln_walls.json"), cracked_kiln)
    merge_tag(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json"), ids(*CRACKED_KILN))
    write(os.path.join(ours, "block", "forge_walls.json"), {"values": ids(
        "seared_bricks", "seared_glass", "seared_drain", "smeltery_controller", "scorched_bricks", "scorched_glass", "scorched_drain",
        "foundry_controller", "coke_oven_bricks", "coke_oven", "firebricks", "brick_blast_furnace")})
    write(os.path.join(ours, "item", "hot_to_hold.json"), {"values": ["minecraft:lava_bucket", "minecraft:blaze_rod"]})
    merge_tag(os.path.join(ours, "item", "baubles", "wrist.json"), ids("smithing_gloves"))

    write(os.path.join(DATA, NS, "recipe", "dangers", "smithing_gloves.json"),
          sa.shapeless("equipment", ["minecraft:leather"] * GLOVE_LEATHER + ["minecraft:string"], NS + ":smithing_gloves"))


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        art(jar)
    assets()
    data()
    write(os.path.join(ASSETS, "lang", "parts", "forge_dangers.json"), LANG)


if __name__ == "__main__":
    main()
