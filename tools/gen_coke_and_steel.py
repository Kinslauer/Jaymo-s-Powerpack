"""Writes the assets, data and lang of Andy's GT New Horizons picks, patch 4 (docs/GTNH-Plan.md, "Coke and steel"):

* the coke oven (its bricks, the oven's front lit and unlit with its creosote gauge, creosote as a fluid and in buckets),
  creosote rags, treated planks, the charcoal pit's charred and smouldering logs, the brick blast furnace and its
  firebricks, coal coke and its block, ash;
* tin (ores, raw tin, ingot, nugget, dust, blocks, tin sand, molten tin) and bronze (ingot, nugget, block, molten bronze,
  and bronze as a Tinkering material: every iron part, tool and armour layer recoloured, its models, and a bronze case
  beside iron's in each item definition, its worn armour and equipment assets);
* blockstates, models, loot tables, tags, recipes, burn times, the tin veins and tin sand worldgen, smiths' tin trades;
* the names and JEI pages (lang/parts/coke_and_steel.json).

All art is vanilla's own textures recoloured or drawn over, so point it at the 26.3 client jar. Run after
tools/tinkering_assets.py, tools/tinkering_armour_assets.py, tools/gen_steel.py and tools/gen_stone_age.py:

    python3 -I tools/gen_coke_and_steel.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.tinkering.Tin and com.jaymospowers.tinkering.coke.CokeAndSteel. Safe to run again
(every file is overwritten, every edit to a shared file is idempotent).
"""
import colorsys
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_steel  # noqa: E402
import gen_stone_age as sa  # noqa: E402
import tinkering_armour_assets as taa  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
KITCHEN_DATA = os.path.join(ROOT, "kitchen", "src", "main", "resources", "data")
NS = "jaymospowers"

# ---- tuning ----
# burn times in a furnace (ticks; coal 1600, a coal block 16000, a lava bucket 20000)
BURN_COAL_COKE, BURN_COAL_COKE_BLOCK, BURN_CREOSOTE_BUCKET = 3200, 32000, 4800
# the brick form: clay and sand (or clay and flint dust) pressed into four unfired bricks
FORM_BRICKS = 4
# charred logs (CharcoalPit.CHARCOAL_PER_LOG says the same) and the smouldering log, if it's broken before it's done
CHARCOAL_PER_LOG, CHARCOAL_SMOULDERING = 2, 1
# rags soaked by one bucket of creosote, and planks treated by one
SOAKED_RAGS, TREATED_PLANKS = 4, 8
# tin's great veins: blocks in one, one chunk in RARITY (copper 20, iron 24, gold 48), and how high (mountains only)
TIN_VEIN_SIZE, TIN_RARITY, TIN_LOW, TIN_HIGH = 70, 24, 48, 200
# tin sand in river beds: one patch in RARITY chunks, its radius
SAND_RARITY, SAND_RADIUS_MIN, SAND_RADIUS_MAX = 3, 2, 4
# smiths: tin ingots bought for an emerald (level 2), and tin ingots sold for one (level 3)
TRADE_TIN_WANTED, TRADE_TIN_GIVEN = 6, 3
# Kitchen's kettles in bronze: as many ingots as its iron recipes take
KETTLE_BRONZE = {"cooking_pot": 3, "saucepan": 2}

BLOCKS_COKE = ["coke_oven_bricks", "firebricks", "coal_coke_block", "treated_planks"]
BLOCKS_TIN = ["tin_ore", "deepslate_tin_ore", "raw_tin_block", "tin_block", "bronze_block", "tin_sand"]
ITEMS_COKE = ["creosote_bucket", "clay_creosote_bucket", "creosote_rag", "coal_coke", "ash", "unfired_coke_oven_brick",
              "coke_oven_brick", "unfired_firebrick", "firebrick"]
ITEMS_TIN = ["raw_tin", "tin_ingot", "tin_nugget", "tin_dust", "bronze_nugget"]

LANG = {
    "block.jaymospowers.tin_ore": "Tin Ore",
    "block.jaymospowers.deepslate_tin_ore": "Deepslate Tin Ore",
    "block.jaymospowers.raw_tin_block": "Block of Raw Tin",
    "block.jaymospowers.tin_block": "Block of Tin",
    "block.jaymospowers.bronze_block": "Block of Bronze",
    "block.jaymospowers.tin_sand": "Tin Sand",
    "block.jaymospowers.coke_oven_bricks": "Coke Oven Bricks",
    "block.jaymospowers.coke_oven": "Coke Oven",
    "block.jaymospowers.firebricks": "Firebricks",
    "block.jaymospowers.brick_blast_furnace": "Brick Blast Furnace",
    "block.jaymospowers.coal_coke_block": "Block of Coal Coke",
    "block.jaymospowers.treated_planks": "Treated Planks",
    "block.jaymospowers.charred_log": "Charred Log",
    "block.jaymospowers.smouldering_log": "Smouldering Log",
    "block.jaymospowers.creosote": "Creosote",
    "item.jaymospowers.raw_tin": "Raw Tin",
    "item.jaymospowers.tin_ingot": "Tin Ingot",
    "item.jaymospowers.tin_nugget": "Tin Nugget",
    "item.jaymospowers.tin_dust": "Tin Dust",
    "item.jaymospowers.bronze_ingot": "Bronze Ingot",
    "item.jaymospowers.bronze_nugget": "Bronze Nugget",
    "item.jaymospowers.creosote_bucket": "Creosote Bucket",
    "item.jaymospowers.clay_creosote_bucket": "Clay Creosote Bucket",
    "item.jaymospowers.creosote_rag": "Creosote Rag",
    "item.jaymospowers.coal_coke": "Coal Coke",
    "item.jaymospowers.ash": "Ash",
    "item.jaymospowers.unfired_coke_oven_brick": "Unfired Coke Oven Brick",
    "item.jaymospowers.coke_oven_brick": "Coke Oven Brick",
    "item.jaymospowers.unfired_firebrick": "Unfired Firebrick",
    "item.jaymospowers.firebrick": "Firebrick",
    "material.jaymospowers.bronze": "Bronze",
    "molten.jaymospowers.tin": "Molten Tin",
    "molten.jaymospowers.bronze": "Molten Bronze",
    "ore_kind.jaymospowers.tin": "tin",
    "jei.jaymospowers.coke_oven": "Coke Oven",
    "jei.jaymospowers.coke_oven.time": "%s seconds",
    "jei.jaymospowers.coke_oven.creosote": "+%s mB creosote (holds %s mB)",
    "jei.jaymospowers.coke_oven.draw": "%s mB to a bucket",
    "jei.jaymospowers.coke_oven.full": "A full tank stops the oven",
    "jei.jaymospowers.coke_oven.build.1": "Coke oven: 25 coke oven bricks and the coke oven, 3 wide, 3 deep and 3 tall, hollow.",
    "jei.jaymospowers.coke_oven.build.2": "Bottom layer: 9 coke oven bricks.",
    "jei.jaymospowers.coke_oven.build.3": "Middle layer: 8 round an empty middle. The coke oven is the middle of one side, facing out; the other 7 are coke oven bricks.",
    "jei.jaymospowers.coke_oven.build.4": "Top layer: 9 coke oven bricks. Load it, draw its creosote and take what it bakes at the coke oven's face.",
    "jei.jaymospowers.charcoal_pit": "Charcoal Pit",
    "jei.jaymospowers.charcoal_pit.days": "%s days buried",
    "jei.jaymospowers.charcoal_pit.air": "Let air in and it burns",
    "jei.jaymospowers.charcoal_pit.break": "Break it for charcoal",
    "jei.jaymospowers.charcoal_pit.build.1": "Charcoal pit: stack logs in a heap, each touching another face to face (up to 128).",
    "jei.jaymospowers.charcoal_pit.build.2": "Cover every face of the heap with earth (dirt, grass, coarse dirt, podzol, mud, clay and the like), the ground under it too.",
    "jei.jaymospowers.charcoal_pit.build.3": "Leave one gap beside one log and light that log through it with flint and steel or a fire charge.",
    "jei.jaymospowers.charcoal_pit.build.4": "Leave it buried until every log is charred. Opened early, it flares up and burns.",
    "jei.jaymospowers.brick_blast_furnace": "Brick Blast Furnace",
    "jei.jaymospowers.brick_blast_furnace.time": "%s minutes",
    "jei.jaymospowers.brick_blast_furnace.ash": "+%s ash",
    "jei.jaymospowers.brick_blast_furnace.build.1": "Brick blast furnace: 32 firebricks and the brick blast furnace, 3 wide, 3 deep and 4 tall, hollow and open at the top.",
    "jei.jaymospowers.brick_blast_furnace.build.2": "Layer 1 (bottom): 9 firebricks.",
    "jei.jaymospowers.brick_blast_furnace.build.3": "Layer 2: 8 round an empty middle. The brick blast furnace is the middle of one side, facing out; the other 7 are firebricks.",
    "jei.jaymospowers.brick_blast_furnace.build.4": "Layer 3: 8 firebricks round an empty middle.",
    "jei.jaymospowers.brick_blast_furnace.build.5": "Layer 4: 8 firebricks round an empty middle, open to the sky.",
    "jei.jaymospowers.tin_ore": "Tin Ore",
    "jei.jaymospowers.tin_ore.where": "Great veins high in the mountains",
    "jei.jaymospowers.tin_sand.where": "River beds; pan it for tin",
    "jei.jaymospowers.treated_planks": "Planks soaked in creosote. They never weather, rot or grow moss.",
    "jei.jaymospowers.creosote": "Drawn from a coke oven. It soaks planks and rags, and an iron bucket of it burns in a furnace.",
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


# ---------------------------------------------------------------- colour

def hsv_map(im, fn, keep=None):
    """Each opaque pixel through fn(h, s, v) -> (h, s, v); keep(r, g, b) leaves a pixel as it is."""
    im = im.convert("RGBA")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0 or keep and keep(r, g, b):
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h2, s2, v2 = fn(h, s, v)
            r2, g2, b2 = colorsys.hsv_to_rgb(h2 % 1.0, max(0.0, min(1.0, s2)), max(0.0, min(1.0, v2)))
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    return im


def bronzed(im):
    """Iron's shading kept, turned the warm orange-brown of bronze (paler and yellower in the light)."""
    return sa.recolour(im, *BRONZE)


def tinned(im):
    """Iron's shading kept, turned tin's dull, faintly warm white."""
    return sa.recolour(im, *TIN)


def tin_spots(im):
    """Only an ore's coloured spots: tinstone, brown-black in its shadows and dull white in the light."""
    return hsv_map(im, lambda h, s, v: (0.08, 0.15 + 0.35 * (1 - v), min(1.0, v * 1.02)), keep=lambda r, g, b: colorsys.rgb_to_hsv(
        r / 255, g / 255, b / 255)[1] < 0.18)


def molten(im, dark, light):
    """Molten metal from molten iron's moving frames, on a ramp dark..light."""
    return sa.recolour(im, dark, light)


CREOSOTE = ((16, 10, 6), (88, 58, 28))
BRONZE = ((80, 38, 12), (244, 176, 94))
TIN = ((92, 92, 98), (232, 232, 224))


# ---------------------------------------------------------------- art

def art(jar):
    def vanilla(rel):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

    def mcmeta(rel):
        return json.loads(jar.read("assets/minecraft/textures/" + rel + ".png.mcmeta"))

    block = os.path.join(ASSETS, "textures", "block")
    item = os.path.join(ASSETS, "textures", "item")

    # -- the coke oven: dark, sooty bricks
    oven_bricks = sa.recolour(vanilla("block/bricks"), (34, 22, 18), (122, 78, 56))
    png(os.path.join(block, "coke", "coke_oven_bricks.png"), oven_bricks)
    for lit in (False, True):
        for fill in range(5):
            png(os.path.join(block, "coke", "coke_oven_front" + ("_on" if lit else "") + "_" + str(fill) + ".png"),
                oven_front(oven_bricks, lit, fill))
    # -- the blast furnace: pale firebricks
    fire_bricks = sa.recolour(vanilla("block/bricks"), (118, 96, 70), (230, 214, 178))
    png(os.path.join(block, "coke", "firebricks.png"), fire_bricks)
    for lit in (False, True):
        png(os.path.join(block, "coke", "brick_blast_furnace_front" + ("_on" if lit else "") + ".png"), furnace_front(fire_bricks, lit))
    png(os.path.join(block, "coke", "coal_coke_block.png"), sa.recolour(vanilla("block/coal_block"), (30, 30, 34), (150, 152, 160)))
    png(os.path.join(block, "coke", "treated_planks.png"), sa.recolour(vanilla("block/oak_planks"), (36, 22, 12), (106, 72, 40)))
    # -- the charcoal pit: charred logs, and the one smouldering at its heart
    side = sa.recolour(vanilla("block/oak_log"), (12, 10, 10), (86, 80, 76))
    top = sa.recolour(vanilla("block/oak_log_top"), (12, 10, 10), (92, 86, 80))
    png(os.path.join(block, "coke", "charred_log.png"), side)
    png(os.path.join(block, "coke", "charred_log_top.png"), top)
    png(os.path.join(block, "coke", "smouldering_log.png"), embers(side))
    png(os.path.join(block, "coke", "smouldering_log_top.png"), embers(top))
    # -- creosote, thick and dark; it moves like water
    for kind in ("still", "flow"):
        im = sa.recolour(vanilla("block/water_" + kind), *CREOSOTE)
        im.putalpha(255)
        png(os.path.join(block, "coke", "creosote_" + kind + ".png"), im)
        write(os.path.join(block, "coke", "creosote_" + kind + ".png.mcmeta"), mcmeta("block/water_" + kind))

    # -- tin
    png(os.path.join(block, "tin", "tin_ore.png"), tin_spots(vanilla("block/iron_ore")))
    png(os.path.join(block, "tin", "deepslate_tin_ore.png"), tin_spots(vanilla("block/deepslate_iron_ore")))
    png(os.path.join(block, "tin", "raw_tin_block.png"), tin_spots_all(vanilla("block/raw_iron_block")))
    png(os.path.join(block, "tin", "tin_block.png"), tinned(vanilla("block/iron_block")))
    png(os.path.join(block, "tin", "bronze_block.png"), bronzed(vanilla("block/iron_block")))
    png(os.path.join(block, "tin", "tin_sand.png"), tin_sand(vanilla("block/sand")))

    # -- items
    bucket, water = vanilla("item/bucket"), vanilla("item/water_bucket")
    png(os.path.join(item, "coke", "creosote_bucket.png"), filled(water, bucket, water))
    clay = Image.open(os.path.join(item, "stone_age", "clay_bucket.png")).convert("RGBA")
    clay_water = Image.open(os.path.join(item, "stone_age", "clay_water_bucket.png")).convert("RGBA")
    png(os.path.join(item, "coke", "clay_creosote_bucket.png"), filled(clay_water, clay, clay_water))
    png(os.path.join(item, "coke", "creosote_rag.png"), sa.recolour(Image.open(os.path.join(item, "rag.png")), (30, 20, 10), (124, 88, 48)))
    png(os.path.join(item, "coke", "coal_coke.png"), sa.recolour(vanilla("item/coal"), (38, 38, 44), (178, 180, 190)))
    dust = Image.open(os.path.join(item, "iron_dust.png")).convert("RGBA")
    png(os.path.join(item, "coke", "ash.png"), sa.recolour(dust, (84, 82, 80), (208, 206, 202)))
    brick = vanilla("item/brick")
    png(os.path.join(item, "coke", "unfired_coke_oven_brick.png"), sa.recolour(brick, (104, 98, 90), (200, 192, 176)))
    png(os.path.join(item, "coke", "coke_oven_brick.png"), sa.recolour(brick, (50, 30, 22), (150, 96, 66)))
    png(os.path.join(item, "coke", "unfired_firebrick.png"), sa.recolour(brick, (122, 120, 124), (214, 212, 216)))
    png(os.path.join(item, "coke", "firebrick.png"), sa.recolour(brick, (128, 100, 68), (234, 216, 176)))
    png(os.path.join(item, "tin", "raw_tin.png"), tin_spots_all(vanilla("item/raw_iron")))
    png(os.path.join(item, "tin", "tin_ingot.png"), tinned(vanilla("item/iron_ingot")))
    png(os.path.join(item, "tin", "tin_nugget.png"), tinned(vanilla("item/iron_nugget")))
    png(os.path.join(item, "tin", "tin_dust.png"), sa.recolour(dust, (104, 104, 108), (224, 224, 220)))
    png(os.path.join(item, "tin", "bronze_nugget.png"), bronzed(vanilla("item/iron_nugget")))
    png(os.path.join(item, "tinkering", "bronze_ingot.png"), bronzed(vanilla("item/iron_ingot")))

    # -- molten tin and bronze, from molten iron's frames (the smeltery's block texture and its gauge)
    src = os.path.join(block, "tinkering", "molten", "iron.png")
    with open(src + ".mcmeta", encoding="utf-8") as f:
        meta = json.load(f)
    for mid, dark, light in (("tin", (120, 122, 126), (246, 246, 240)), ("bronze", (128, 58, 16), (255, 196, 108))):
        hot = molten(Image.open(src), dark, light)
        png(os.path.join(block, "tinkering", "molten", mid + ".png"), hot)
        write(os.path.join(block, "tinkering", "molten", mid + ".png.mcmeta"), meta)
        png(os.path.join(ASSETS, "textures", "gui", "tinkering", "molten", mid + ".png"), hot.crop((0, 0, 16, 16)))


def oven_front(bricks, lit, fill):
    """The coke oven's face: an arched mouth (dark, or glowing while it bakes) and, to its right, a gauge of creosote
    rising in four steps as the tank fills."""
    im = bricks.copy()
    mortar = (20, 14, 12, 255)
    for y in range(4, 13):
        for x in range(2, 10):
            arch = y == 4 and x in (2, 9)
            if arch:
                continue
            edge = x in (2, 9) or y in (4, 12) or y == 5 and x in (3, 8)
            if edge:
                im.putpixel((x, y), mortar)
            elif lit:
                t = (y - 5) / 7
                im.putpixel((x, y), sa.ramp((150, 40, 10), (255, 186, 70), t) + (255,))
            else:
                im.putpixel((x, y), (30 + 4 * (y % 2), 22, 18, 255))
    if lit:
        # embers in the bed
        for x in range(3, 9):
            im.putpixel((x, 11), (255, 222, 120, 255) if x % 2 else (232, 120, 40, 255))
    iron = [(40, 40, 44, 255), (88, 88, 96, 255), (130, 130, 138, 255)]
    for y in range(2, 14):
        for x in range(11, 15):
            frame = x in (11, 14) or y in (2, 13)
            im.putpixel((x, y), iron[2 if (x == 11 or y == 2) else 0] if frame else iron[0])
    for step in range(fill):
        for x in (12, 13):
            for y in (12 - 2 * step, 11 - 2 * step):
                im.putpixel((x, y), (168, 110, 44, 255) if x == 12 else (112, 70, 26, 255))
    return im


def furnace_front(bricks, lit):
    """The brick blast furnace's face: a squared mouth behind two iron bars, roaring yellow-white while it's hot."""
    im = bricks.copy()
    for y in range(6, 14):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (6, 13)
            if edge:
                im.putpixel((x, y), (60, 46, 34, 255))
            elif x in (6, 9):
                im.putpixel((x, y), (54, 54, 60, 255) if y % 3 else (110, 110, 118, 255))
            elif lit:
                im.putpixel((x, y), sa.ramp((220, 90, 20), (255, 240, 170), (y - 7) / 5) + (255,))
            else:
                im.putpixel((x, y), (26, 20, 18, 255))
    # the lintel
    for x in range(2, 14):
        im.putpixel((x, 5), (96, 76, 56, 255))
    return im


def embers(im):
    """A charred texture with live embers glowing in its darkest cracks."""
    im = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a and sa.lum((r, g, b)) < 0.09 and (x * 5 + y * 3) % 4 != 0:
                im.putpixel((x, y), (210, 76, 18, a) if (x + y) % 3 else (255, 160, 60, a))
    return im


def tin_spots_all(im):
    """Raw tin: every pixel in tinstone's colours."""
    return hsv_map(im, lambda h, s, v: (0.08, 0.12 + 0.35 * (1 - v), 0.06 + 0.9 * v))


def tin_sand(sand):
    """Sand gone dark grey-brown with tin, flecked with black grains of tinstone."""
    im = sa.recolour(sand, (58, 52, 46), (148, 138, 122))
    for y in range(16):
        for x in range(16):
            if (x * 7 + y * 11) % 13 == 0:
                im.putpixel((x, y), (30, 24, 20, 255))
    return im


def filled(full, empty, shade):
    """{@code empty} with creosote where {@code full} differs from it (by the shading of {@code shade})."""
    vanilla_bucket = full
    out = empty.copy()
    lo, hi = 1.0, 0.0
    diff = []
    for y in range(16):
        for x in range(16):
            if vanilla_bucket.getpixel((x, y)) != empty.getpixel((x, y)) and shade.getpixel((x, y))[3]:
                diff.append((x, y))
                v = sa.lum(shade.getpixel((x, y)))
                lo, hi = min(lo, v), max(hi, v)
    for x, y in diff:
        t = (sa.lum(shade.getpixel((x, y))) - lo) / max(1e-6, hi - lo)
        out.putpixel((x, y), sa.ramp(*CREOSOTE, t) + (255,))
    return out


# ---------------------------------------------------------------- models

def assets():
    blockstates = os.path.join(ASSETS, "blockstates")
    bmodels = os.path.join(ASSETS, "models", "block")
    items = os.path.join(ASSETS, "items")

    def cube(name, folder, texture=None):
        write(os.path.join(bmodels, folder, name + ".json"),
              {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/" + folder + "/" + (texture or name)}})

    def simple(name, folder):
        cube(name, folder)
        write(os.path.join(blockstates, name + ".json"), {"variants": {"": {"model": NS + ":block/" + folder + "/" + name}}})
        write(os.path.join(items, name + ".json"), ref(NS + ":block/" + folder + "/" + name))

    for name in BLOCKS_COKE:
        simple(name, "coke")
    for name in BLOCKS_TIN:
        simple(name, "tin")

    turn = {"north": 0, "east": 90, "south": 180, "west": 270}

    def front(name, front_tex, side_tex):
        write(os.path.join(bmodels, "coke", name + ".json"), {"parent": "minecraft:block/orientable", "textures": {
            "front": NS + ":block/coke/" + front_tex, "side": NS + ":block/coke/" + side_tex, "top": NS + ":block/coke/" + side_tex}})

    variants = {}
    for lit in (False, True):
        for fill in range(5):
            model = "coke_oven" + ("_on" if lit else "") + "_" + str(fill)
            front(model, "coke_oven_front" + ("_on" if lit else "") + "_" + str(fill), "coke_oven_bricks")
            for facing, y in turn.items():
                v = {"model": NS + ":block/coke/" + model}
                if y:
                    v["y"] = y
                variants["facing=" + facing + ",fill=" + str(fill) + ",lit=" + str(lit).lower()] = v
    write(os.path.join(blockstates, "coke_oven.json"), {"variants": variants})
    write(os.path.join(items, "coke_oven.json"), ref(NS + ":block/coke/coke_oven_0"))

    variants = {}
    for lit in (False, True):
        model = "brick_blast_furnace" + ("_on" if lit else "")
        front(model, "brick_blast_furnace_front" + ("_on" if lit else ""), "firebricks")
        for facing, y in turn.items():
            v = {"model": NS + ":block/coke/" + model}
            if y:
                v["y"] = y
            variants["facing=" + facing + ",lit=" + str(lit).lower()] = v
    write(os.path.join(blockstates, "brick_blast_furnace.json"), {"variants": variants})
    write(os.path.join(items, "brick_blast_furnace.json"), ref(NS + ":block/coke/brick_blast_furnace"))

    # the charred log: a log, by its axis
    for name in ("charred_log", "smouldering_log"):
        write(os.path.join(bmodels, "coke", name + ".json"), {"parent": "minecraft:block/cube_column", "textures": {
            "end": NS + ":block/coke/" + name + "_top", "side": NS + ":block/coke/" + name}})
    write(os.path.join(bmodels, "coke", "charred_log_horizontal.json"), {"parent": "minecraft:block/cube_column_horizontal", "textures": {
        "end": NS + ":block/coke/charred_log_top", "side": NS + ":block/coke/charred_log"}})
    log = NS + ":block/coke/charred_log"
    write(os.path.join(blockstates, "charred_log.json"), {"variants": {
        "axis=x": {"model": log + "_horizontal", "x": 90, "y": 90}, "axis=y": {"model": log},
        "axis=z": {"model": log + "_horizontal", "x": 90}}})
    write(os.path.join(items, "charred_log.json"), ref(log))
    write(os.path.join(blockstates, "smouldering_log.json"), {"variants": {"": {"model": NS + ":block/coke/smouldering_log"}}})

    # creosote: a fluid draws itself; the block needs only its particle
    write(os.path.join(bmodels, "coke", "creosote.json"), {"textures": {"particle": NS + ":block/coke/creosote_still"}})
    write(os.path.join(blockstates, "creosote.json"), {"variants": {"": {"model": NS + ":block/coke/creosote"}}})

    for folder, names in (("coke", ITEMS_COKE), ("tin", ITEMS_TIN)):
        for name in names:
            write(os.path.join(ASSETS, "models", "item", folder, name + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + folder + "/" + name}})
            write(os.path.join(items, name + ".json"), ref(NS + ":item/" + folder + "/" + name))
    write(os.path.join(ASSETS, "models", "item", "tinkering", "bronze_ingot.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/bronze_ingot"}})
    write(os.path.join(items, "bronze_ingot.json"), ref(NS + ":item/tinkering/bronze_ingot"))

    # the hot ingot, fresh from the smeltery: tin's and bronze's
    hot_path = os.path.join(items, "hot_ingot.json")
    hot_def = json.load(open(hot_path))
    cases = hot_def["model"]["cases"]
    iron = next(c for c in cases if c["when"] == "iron")
    for mid, model in (("tin", NS + ":item/tin/tin_ingot"), ("bronze", NS + ":item/tinkering/bronze_ingot")):
        if not any(c["when"] == mid for c in cases):
            case = json.loads(json.dumps(iron))
            case["when"] = mid
            case["model"]["model"] = model
            cases.append(case)
    write(hot_path, hot_def)


def bronze_material():
    """Bronze as a Tinkering material: a bronze copy of every iron part, tool and armour sprite, its model and a case in
    each item definition that picks by material (like tools/gen_bog_iron.py)."""
    count = 0

    def iron_sprite(name):
        return name.endswith("_iron.png") and not any(name.endswith(o) for o in ("_pig_iron.png", "_rust_iron.png", "_ice_iron.png", "_bog_iron.png"))

    def copy_folder(tex, models):
        nonlocal count
        for name in sorted(os.listdir(tex)):
            if not iron_sprite(name):
                continue
            base = name[:-len("_iron.png")]
            png(os.path.join(tex, base + "_bronze.png"), bronzed(Image.open(os.path.join(tex, name))))
            model_path = os.path.join(models, base + "_iron.json")
            if os.path.exists(model_path):
                model = json.load(open(model_path))
                model["textures"]["layer0"] = model["textures"]["layer0"][:-len("_iron")] + "_bronze"
                write(os.path.join(models, base + "_bronze.json"), model)
            count += 1

    tink, tink_models = os.path.join(ASSETS, "textures", "item", "tinkering"), os.path.join(ASSETS, "models", "item", "tinkering")
    for folder in gen_steel.TINKER_DIRS:
        copy_folder(os.path.join(tink, folder), os.path.join(tink_models, folder))
    arm, arm_models = os.path.join(ASSETS, "textures", "item", "tinker_armour"), os.path.join(ASSETS, "models", "item", "tinker_armour")
    for folder in sorted(os.listdir(arm)):
        if os.path.isdir(os.path.join(arm, folder)):
            copy_folder(os.path.join(arm, folder), os.path.join(arm_models, folder))

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
            if "iron" in whens and "bronze" not in whens:
                text = json.dumps(cases[whens.index("iron")]["model"]).replace("_iron\"", "_bronze\"")
                cases.append({"when": "bronze", "model": json.loads(text)})
        for value in node.values():
            add_case(value)

    armour_items = taa.PLATINGS + ["maille"] + ["tinker_" + p[0] for p in taa.PIECES]
    for name in gen_steel.TINKER_ITEMS + armour_items:
        path = os.path.join(ASSETS, "items", name + ".json")
        definition = json.load(open(path))
        add_case(definition)
        write(path, definition)

    # worn: bronze plating and maille, travelers' bronze, and every plating with every maille where either is bronze
    eq = os.path.join(ASSETS, "textures", "entity", "equipment")
    for layer in ("humanoid", "humanoid_leggings"):
        for kind in ("plating", "maille"):
            png(os.path.join(eq, layer, "tinker_plate", kind + "_bronze.png"),
                bronzed(Image.open(os.path.join(eq, layer, "tinker_plate", kind + "_iron.png"))))
        png(os.path.join(eq, layer, "tinker_travelers", "metal_bronze.png"),
            bronzed(Image.open(os.path.join(eq, layer, "tinker_travelers", "metal_iron.png"))))

    def layers(*names):
        return [{"texture": NS + ":" + n} for n in names]

    metals = taa.METALS + ["bronze"]
    for p in metals:
        for m in metals:
            if "bronze" not in (p, m):
                continue
            look = ["tinker_plate/maille_" + m, "tinker_plate/plating_" + p]
            write(os.path.join(ASSETS, "equipment", "tinker_plate", p + "__" + m + ".json"),
                  {"layers": {"humanoid": layers(*look), "humanoid_leggings": layers(*look)}})
    write(os.path.join(ASSETS, "equipment", "tinker_travelers", "bronze.json"), {"layers": {
        "humanoid": layers("tinker_travelers/cuirass", "tinker_travelers/metal_bronze", "tinker_travelers/goggles"),
        "humanoid_leggings": layers("tinker_travelers/cuirass", "tinker_travelers/metal_bronze")}})
    print("bronze sprites:", count)


# ---------------------------------------------------------------- data

def recipe(name, data):
    write(os.path.join(DATA, NS, "recipe", "coke_and_steel", name + ".json"), data)


def data():
    tool = NS + ":tool_crafting"
    creosote = [NS + ":creosote_bucket", NS + ":clay_creosote_bucket"]
    # bricks: pressed in the form, fired in the kiln (Kiln.add) or on a campfire
    recipe("unfired_coke_oven_bricks", sa.shapeless("misc", [NS + ":brick_form"] + ["#" + NS + ":bucket_clay"] * 2 + ["#minecraft:sand"] * 2,
                                                    NS + ":unfired_coke_oven_brick", FORM_BRICKS, tool))
    recipe("unfired_firebricks", sa.shapeless("misc", [NS + ":brick_form"] + ["#" + NS + ":bucket_clay"] * 2 + [NS + ":flint_dust"] * 2,
                                              NS + ":unfired_firebrick", FORM_BRICKS, tool))
    for unfired, fired in (("unfired_coke_oven_brick", "coke_oven_brick"), ("unfired_firebrick", "firebrick")):
        recipe(fired + "_from_pit_firing", {"type": "minecraft:campfire_cooking", "category": "misc", "cookingtime": sa.PIT_FIRING_TICKS,
                                            "experience": 0.1, "ingredient": NS + ":" + unfired, "result": {"id": NS + ":" + fired}})
    for brick, block, controller in (("coke_oven_brick", "coke_oven_bricks", "coke_oven"), ("firebrick", "firebricks", "brick_blast_furnace")):
        recipe(block, sa.shaped("building", ["BB", "BB"], {"B": NS + ":" + brick}, NS + ":" + block))
        recipe(controller, sa.shaped("misc", ["BBB", "B B", "BBB"], {"B": NS + ":" + brick}, NS + ":" + controller))
    recipe("coal_coke_block", sa.shaped("building", ["###", "###", "###"], {"#": NS + ":coal_coke"}, NS + ":coal_coke_block"))
    recipe("coal_coke", sa.shapeless("misc", [NS + ":coal_coke_block"], NS + ":coal_coke", 9))
    # creosote soaks rags (torches) and planks
    recipe("creosote_rag", sa.shapeless("misc", [NS + ":rag"] * SOAKED_RAGS + [creosote], NS + ":creosote_rag", SOAKED_RAGS))
    recipe("treated_planks", sa.shaped("building", ["PPP", "PCP", "PPP"], {"P": "#minecraft:planks", "C": creosote}, NS + ":treated_planks",
                                       TREATED_PLANKS))

    # tin and bronze
    ingot, nugget, raw = NS + ":tin_ingot", NS + ":tin_nugget", NS + ":raw_tin"
    for kind, time in (("smelting", 200), ("blasting", 100)):
        for src, suffix in (("#c:ores/tin", ""), (raw, "_raw")):
            recipe("tin_ingot_from_" + kind + suffix, {"type": "minecraft:" + kind, "category": "misc", "cookingtime": time, "experience": 0.7,
                                                       "group": "tin_ingot", "ingredient": src, "result": {"id": ingot}})
    bronze, bronze_nugget = NS + ":bronze_ingot", NS + ":bronze_nugget"
    for small, big, name in ((ingot, NS + ":tin_block", "tin_block"), (nugget, ingot, "tin_ingot_from_nuggets"),
                             (raw, NS + ":raw_tin_block", "raw_tin_block"), (bronze, NS + ":bronze_block", "bronze_block"),
                             (bronze_nugget, bronze, "bronze_ingot_from_nuggets")):
        recipe(name, sa.shaped("misc", ["###", "###", "###"], {"#": small}, big))
    for big, small, name in ((NS + ":tin_block", ingot, "tin_ingot_from_block"), (ingot, nugget, "tin_nugget"),
                             (NS + ":raw_tin_block", raw, "raw_tin"), (NS + ":bronze_block", bronze, "bronze_ingot_from_block"),
                             (bronze, bronze_nugget, "bronze_nugget")):
        recipe(name, sa.shapeless("misc", [big], small, 9))
    # a bell is cast bronze (it melts back to bronze: Melting)
    recipe("bell", sa.shaped("misc", [" S ", "BBB", "B B"], {"S": "minecraft:stick", "B": bronze}, "minecraft:bell"))
    # Kitchen's kettles (the cooking pot and the saucepan) in bronze, beside its iron recipes; only with Kitchen loaded
    for pot, others in (("cooking_pot", ["minecraft:bowl"]), ("saucepan", ["minecraft:stick"])):
        made = sa.shapeless("equipment", others + ["#c:ingots/bronze"] * KETTLE_BRONZE[pot], "jaymoskitchen:" + pot)
        recipe(pot + "_from_bronze", dict({"fabric:load_conditions": [{"condition": "fabric:registry_contains", "registry": "minecraft:item",
                                                                      "values": ["jaymoskitchen:" + pot]}]}, **made))

    # burn times, like vanilla's
    for name, ticks in (("time_coal_coke", BURN_COAL_COKE), ("time_coal_coke_block", BURN_COAL_COKE_BLOCK),
                        ("time_creosote_bucket", BURN_CREOSOTE_BUCKET)):
        write(os.path.join(DATA, NS, "context_int_provider", "cooking", name + ".json"), {
            "type": "minecraft:div", "left": ticks, "right": {
                "type": "minecraft:conditional", "condition": "minecraft:block/fast_cooking",
                "on_false": "minecraft:cooking/normal_burn_time_reduction_factor",
                "on_true": "minecraft:cooking/fast_burn_time_reduction_factor"}})

    loot()
    tags()
    worldgen()
    trades()


def loot():
    def table(block, entries, explosion=True):
        pool = {"rolls": 1, "entries": entries}
        if explosion:
            pool["condition"] = {"type": "minecraft:survives_explosion"}
        return {"type": "minecraft:block", "random_sequence": NS + ":blocks/" + block, "pools": [pool]}

    for block in BLOCKS_COKE + BLOCKS_TIN + ["coke_oven", "brick_blast_furnace"]:
        if block.endswith("_ore"):
            continue
        write(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"), table(block, [{"type": "minecraft:item", "name": NS + ":" + block}]))
    for block in ("tin_ore", "deepslate_tin_ore"):
        write(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"), table(block, [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": NS + ":" + block},
            {"type": "minecraft:item", "name": NS + ":raw_tin", "modifier": [
                {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"type": "minecraft:explosion_decay"}]}]}], explosion=False))
    for block, count in (("charred_log", CHARCOAL_PER_LOG), ("smouldering_log", CHARCOAL_SMOULDERING)):
        write(os.path.join(DATA, NS, "loot_table", "blocks", block + ".json"), table(block, [{"type": "minecraft:item", "name": "minecraft:charcoal", "modifier": [
            {"type": "minecraft:set_count", "count": count}, {"type": "minecraft:explosion_decay"}]}], explosion=False))


def tags():
    def ids(*names):
        return [NS + ":" + n for n in names]

    mc = os.path.join(DATA, "minecraft", "tags", "block")
    merge_tag(os.path.join(mc, "mineable", "pickaxe.json"), ids("tin_ore", "deepslate_tin_ore", "raw_tin_block", "tin_block", "bronze_block",
                                                               "coke_oven_bricks", "coke_oven", "firebricks", "brick_blast_furnace", "coal_coke_block"))
    merge_tag(os.path.join(mc, "needs_stone_tool.json"), ids("tin_ore", "deepslate_tin_ore", "raw_tin_block", "tin_block", "bronze_block"))
    merge_tag(os.path.join(mc, "mineable", "axe.json"), ids("treated_planks", "charred_log", "smouldering_log"))
    merge_tag(os.path.join(mc, "mineable", "shovel.json"), ids("tin_sand"))
    # sand to a pan or a sluice (ground/Panning), and to cactus and cane (the block tag only, so no recipe takes it as sand)
    merge_tag(os.path.join(mc, "sand.json"), ids("tin_sand"))

    c = os.path.join(DATA, "c", "tags")
    ores = ids("tin_ore", "deepslate_tin_ore")
    for kind in ("block", "item"):
        write(os.path.join(c, kind, "ores", "tin.json"), {"values": ores})
        merge_tag(os.path.join(c, kind, "ores.json"), ["#c:ores/tin"])
        write(os.path.join(c, kind, "storage_blocks", "tin.json"), {"values": ids("tin_block")})
        write(os.path.join(c, kind, "storage_blocks", "raw_tin.json"), {"values": ids("raw_tin_block")})
        write(os.path.join(c, kind, "storage_blocks", "bronze.json"), {"values": ids("bronze_block")})
    write(os.path.join(c, "item", "raw_materials", "tin.json"), {"values": ids("raw_tin")})
    write(os.path.join(c, "item", "ingots", "tin.json"), {"values": ids("tin_ingot")})
    write(os.path.join(c, "item", "ingots", "bronze.json"), {"values": ids("bronze_ingot")})
    write(os.path.join(c, "item", "nuggets", "tin.json"), {"values": ids("tin_nugget")})
    write(os.path.join(c, "item", "nuggets", "bronze.json"), {"values": ids("bronze_nugget")})
    merge_tag(os.path.join(c, "item", "dusts", "tin.json"), ids("tin_dust"))

    ours = os.path.join(DATA, NS, "tags")
    merge_tag(os.path.join(ours, "item", "slow_smelt.json"), ores + ids("raw_tin"))
    merge_tag(os.path.join(ours, "block", "far_ore.json"), ores)
    write(os.path.join(ours, "block", "weather_proof.json"), {"values": ids("treated_planks")})
    write(os.path.join(ours, "block", "charcoal_pit_cover.json"), {"values": [
        "#minecraft:dirt", "minecraft:farmland", "minecraft:dirt_path", "minecraft:clay", "minecraft:packed_mud"]})
    # Kitchen's soot: a smouldering pit and a blast furnace count heavy
    write(os.path.join(KITCHEN_DATA, "jaymoskitchen", "tags", "block", "heavy_soot.json"), {"values": [
        {"id": NS + ":smouldering_log", "required": False}, {"id": NS + ":brick_blast_furnace", "required": False}]})


def worldgen():
    wg = os.path.join(DATA, NS, "worldgen")
    write(os.path.join(wg, "feature", "big_vein_tin.json"), {
        "type": NS + ":big_vein", "ore": NS + ":tin_ore", "size": TIN_VEIN_SIZE, "deep": NS + ":deepslate_tin_ore"})
    write(os.path.join(wg, "placed_feature", "big_vein_tin.json"), {"feature": NS + ":big_vein_tin", "placement": [
        {"type": "minecraft:rarity_filter", "chance": TIN_RARITY}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": TIN_LOW},
                                                      "max_inclusive": {"absolute": TIN_HIGH}}},
        {"type": "minecraft:biome"}]})
    write(os.path.join(wg, "feature", "tin_sand.json"), {
        "type": "minecraft:disk", "half_height": 1,
        "radius": {"type": "minecraft:uniform", "min_inclusive": SAND_RADIUS_MIN, "max_inclusive": SAND_RADIUS_MAX},
        "state_provider": {"id": NS + ":tin_sand"},
        "target": {"type": "minecraft:matching_blocks", "blocks": ["minecraft:sand", "minecraft:gravel", "minecraft:dirt", "minecraft:clay"]}})
    write(os.path.join(wg, "placed_feature", "tin_sand.json"), {"feature": NS + ":tin_sand", "placement": [
        {"type": "minecraft:rarity_filter", "chance": SAND_RARITY}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"}},
        {"type": "minecraft:biome"}]})


def trades():
    """Smiths (vanilla's and Kingdoms' MCA smiths, who use the same professions) buy and sell tin."""
    vt = os.path.join(DATA, NS, "villager_trade", "smith")
    write(os.path.join(vt, "tin_ingot_emerald.json"), {
        "gives": {"id": "minecraft:emerald"}, "max_uses": 12, "reputation_discount": 0.05,
        "wants": {"count": TRADE_TIN_WANTED, "id": NS + ":tin_ingot"}, "xp": 10})
    write(os.path.join(vt, "emerald_tin_ingot.json"), {
        "gives": {"count": TRADE_TIN_GIVEN, "id": NS + ":tin_ingot"}, "max_uses": 12, "reputation_discount": 0.05,
        "wants": {"count": 1, "id": "minecraft:emerald"}, "xp": 10})
    tags = os.path.join(DATA, "minecraft", "tags", "villager_trade", "common_smith")
    merge_tag(os.path.join(tags, "level_2.json"), [NS + ":smith/tin_ingot_emerald"])
    merge_tag(os.path.join(tags, "level_3.json"), [NS + ":smith/emerald_tin_ingot"])


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        art(jar)
    assets()
    bronze_material()
    data()
    write(os.path.join(ASSETS, "lang", "parts", "coke_and_steel.json"), LANG)


if __name__ == "__main__":
    main()
