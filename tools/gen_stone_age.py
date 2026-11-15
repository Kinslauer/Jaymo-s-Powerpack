"""Writes the Stone Age recipes of Andy's GT New Horizons picks, patch 1 (docs/GTNH-Plan.md; Java in
com.jaymospowers.ground.StoneAge): the vanilla recipes switched off, our recipes in their place, gravel's loot, the tags,
the item textures, models and item definitions, the pane cast, molten glass and the lang part. Wooden doors, fences,
gates and slabs are read off vanilla's own recipes, so every wood (poplar too) is covered. The textures are recoloured
from vanilla's own icons and ours (vanilla style), so point it at a 26.3 jar that holds assets/minecraft and
data/minecraft (Loom's client jar):

    python -I tools/gen_stone_age.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

Run it again after tools/tinkering_assets.py (that one wipes the Tinkering textures and models: the pane cast and molten
glass are written here). The numbers are starting values (docs/GTNH-Plan.md: everything starts gentle); change them here
and run it again.
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
NS = "jaymospowers"

# ---- tuning ----
# torch yield by fuel (vanilla: 4 from either). Patch 4 adds coal coke (5) and the creosote-soaked rag (3) here.
TORCH_YIELDS = {"minecraft:coal": 2, "minecraft:charcoal": 2}
# wooden doors: 6 planks and a fitting make one (vanilla: 6 planks, 3 doors); iron doors want a steel ingot
WOOD_DOORS = 1
IRON_DOORS = 3
# the saw: two planks into sticks, three planks into slabs, a wooden slab into fence rails
SAW_STICKS = 8
SAW_SLABS = 8
SAW_RAILS = 2
# fences and gates as vanilla's, rails for sticks
FENCES = 3
GATES = 1
# paper: three pulp and a bucket of water pressed between two stone slabs
PRESS_PULP = 3
PRESS_PAPER = 3
# a brick form and clay: a clay block or four clay balls make four unfired bricks
FORM_BRICKS = 4
# glass dust: sand and flint dust
GLASS_SAND = 4
GLASS_DUST = 4
# bronze dust: three copper to one tin
BRONZE_COPPER = 3
BRONZE_DUST = 4

ITEMS = ["flint_dust", "clay_dust", "bronze_dust", "glass_dust", "sawdust", "cane_chad", "fence_rail", "brick_form",
         "unfired_brick", "unfired_clay_bucket", "clay_bucket", "clay_water_bucket", "clay_milk_bucket"]

LANG = {
    "item.jaymospowers.flint_dust": "Flint Dust",
    "item.jaymospowers.clay_dust": "Clay Dust",
    "item.jaymospowers.bronze_dust": "Bronze Dust",
    "item.jaymospowers.glass_dust": "Glass Dust",
    "item.jaymospowers.sawdust": "Sawdust",
    "item.jaymospowers.cane_chad": "Cane Chad",
    "item.jaymospowers.fence_rail": "Fence Rail",
    "item.jaymospowers.brick_form": "Brick Form",
    "item.jaymospowers.unfired_brick": "Unfired Brick",
    "item.jaymospowers.unfired_clay_bucket": "Unfired Clay Bucket",
    "item.jaymospowers.clay_bucket": "Clay Bucket",
    "item.jaymospowers.clay_water_bucket": "Clay Bucket of Water",
    "item.jaymospowers.clay_milk_bucket": "Clay Bucket of Milk",
    "item.jaymospowers.pane_cast": "Pane Cast",
    "molten.jaymospowers.glass": "Molten Glass",
    "jei.jaymospowers.sieve": "Sifting",
    "jei.jaymospowers.sieve.chance": "%s%% a time",
    "jei.jaymospowers.mortar_grinding": "Mortar Grinding",
    "jei.jaymospowers.mortar_grinding.how": "Up to four at a time, ground with the pestle",
    "jei.jaymospowers.clay_bucket": "Scoops water and milks a cow or goat like a bucket. It wears a little every time it's "
                                    "emptied and cracks in the end. Dip it in lava and it shatters.",
    "jei.jaymospowers.brick_form": "Carved from a plank with a knife. Clay pressed into it in the grid makes unfired bricks, "
                                   "for the brick kiln; the form wears a little each time.",
}

# vanilla recipes switched off (replaced below)
OFF = ["crafting_table", "chest", "iron_door", "torch", "paper", "brick", "glass"]

WOODEN_UNMENDABLE = ["broadsword", "claymore", "cleaver", "cudgel", "dagger", "flanged_mace", "glaive", "hatchet", "lance",
                     "longsword", "machete", "ninjato", "quarterstaff", "rapier", "saber", "skewer", "war_axe", "war_scythe"]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def recipe(name, data):
    write(os.path.join(DATA, NS, "recipe", "stone_age", name + ".json"), data)


def off(name):
    write(os.path.join(DATA, "minecraft", "recipe", name + ".json"), {"fabric:load_conditions": [{"condition": "fabric:false"}]})


def shaped(category, pattern, key, result, count=1, group=None, kind="minecraft:crafting_shaped"):
    data = {"type": kind, "category": category}
    if group:
        data["group"] = group
    data.update({"pattern": pattern, "key": key, "result": {"id": result, "count": count}})
    return data


def shapeless(category, ingredients, result, count=1, kind="minecraft:crafting_shapeless", group=None):
    data = {"type": kind, "category": category}
    if group:
        data["group"] = group
    data.update({"ingredients": ingredients, "result": {"id": result, "count": count}})
    return data


def vanilla_recipes(jar):
    out = {}
    for name in jar.namelist():
        if name.startswith("data/minecraft/recipe/") and name.endswith(".json"):
            out[name[len("data/minecraft/recipe/"):-5]] = json.loads(jar.read(name))
    return out


def planks_key(r):
    """The planks item a vanilla wooden recipe is keyed on, or None."""
    for value in r.get("key", {}).values():
        if isinstance(value, str) and value.endswith("_planks"):
            return value
    return None


def data(jar):
    recipes = vanilla_recipes(jar)
    for name in OFF:
        off(name)
    saw = NS + ":saw"
    rail = NS + ":fence_rail"
    # the first table and chest want flint (the table is made in the 2x2 grid, so three logs and the flint)
    recipe("crafting_table", shaped("misc", ["LF", "LL"], {"L": "#minecraft:logs", "F": "minecraft:flint"}, "minecraft:crafting_table"))
    recipe("chest", shaped("misc", ["LPL", "PFP", "LPL"], {"L": "#minecraft:logs", "P": "#minecraft:planks", "F": "minecraft:flint"},
                           "minecraft:chest"))
    # doors with fittings, fences and gates with rails, slabs with the saw
    for name, r in sorted(recipes.items()):
        planks = planks_key(r)
        if planks is None or r.get("type") != "minecraft:crafting_shaped":
            continue
        result = r["result"]["id"]
        if name.endswith("_door") and result.endswith("_door"):
            off(name)
            recipe(name, shaped("redstone", ["PPN", "PP ", "PP "], {"P": planks, "N": "#" + NS + ":door_fittings"}, result, WOOD_DOORS,
                                "wooden_door"))
        elif name.endswith("_fence") and "minecraft:stick" in r["key"].values():
            off(name)
            recipe(name, shaped("misc", ["W#W", "W#W"], {"W": planks, "#": rail}, result, FENCES, "wooden_fence"))
        elif name.endswith("_fence_gate") and "minecraft:stick" in r["key"].values():
            off(name)
            recipe(name, shaped("redstone", ["#W#", "#W#"], {"W": planks, "#": rail}, result, GATES, "wooden_fence_gate"))
        elif name.endswith("_slab") and r.get("pattern") == ["###"]:
            recipe(name + "_from_saw", shapeless("building", [saw, planks, planks, planks], result, SAW_SLABS, group="wooden_slab"))
    recipe("iron_door", shaped("redstone", ["IIS", "II ", "II "], {"I": "minecraft:iron_ingot", "S": NS + ":steel_ingot"},
                               "minecraft:iron_door", IRON_DOORS))
    recipe("sticks_from_saw", shapeless("misc", [saw, "#minecraft:planks", "#minecraft:planks"], "minecraft:stick", SAW_STICKS))
    recipe("fence_rail", shapeless("misc", [saw, "#minecraft:wooden_slabs"], rail, SAW_RAILS))
    # torches by fuel
    for fuel, count in TORCH_YIELDS.items():
        recipe("torch_from_" + fuel.split(":")[1], shaped("misc", ["X", "#"], {"X": fuel, "#": "minecraft:stick"}, "minecraft:torch", count))
    # tools kept in the grid (ground/GridToolRecipe)
    tool = NS + ":tool_crafting"
    water = ["minecraft:water_bucket", NS + ":clay_water_bucket"]
    recipe("paper", shapeless("misc", ["#" + NS + ":press_slabs", "#" + NS + ":press_slabs"] + ["#" + NS + ":paper_pulp"] * PRESS_PULP
                              + [water], "minecraft:paper", PRESS_PAPER, tool))
    recipe("brick_form", shapeless("misc", ["#" + NS + ":fiber_knives", "#minecraft:planks"], NS + ":brick_form", 1, tool))
    recipe("unfired_bricks", shapeless("misc", [NS + ":brick_form", "minecraft:clay"], NS + ":unfired_brick", FORM_BRICKS, tool))
    recipe("unfired_bricks_from_balls", shapeless("misc", [NS + ":brick_form"] + ["minecraft:clay_ball"] * FORM_BRICKS,
                                                  NS + ":unfired_brick", FORM_BRICKS, tool))
    recipe("unfired_clay_bucket", shaped("misc", ["C C", " C "], {"C": "#" + NS + ":bucket_clay"}, NS + ":unfired_clay_bucket"))
    recipe("glass_dust", shapeless("misc", ["#minecraft:smelts_to_glass"] * GLASS_SAND + [NS + ":flint_dust"], NS + ":glass_dust", GLASS_DUST))
    recipe("bronze_dust", shapeless("misc", [NS + ":copper_dust"] * BRONZE_COPPER + ["#c:dusts/tin"], NS + ":bronze_dust", BRONZE_DUST))

    # gravel never drops flint
    write(os.path.join(DATA, "minecraft", "loot_table", "blocks", "gravel.json"), {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "minecraft:gravel"}], "rolls": 1}],
        "random_sequence": "minecraft:blocks/gravel"})

    tags = os.path.join(DATA, NS, "tags", "item")
    write(os.path.join(tags, "grid_tools.json"), {"values": ["#" + NS + ":fiber_knives", "#" + NS + ":press_slabs", NS + ":brick_form"]})
    write(os.path.join(tags, "press_slabs.json"), {"values": ["minecraft:stone_slab", "minecraft:smooth_stone_slab", "minecraft:cobblestone_slab"]})
    write(os.path.join(tags, "paper_pulp.json"), {"values": [NS + ":sawdust", NS + ":cane_chad"]})
    write(os.path.join(tags, "door_fittings.json"), {"values": ["minecraft:copper_nugget", "minecraft:iron_nugget"]})
    write(os.path.join(tags, "bucket_clay.json"), {"values": ["minecraft:clay_ball", NS + ":clay_dust"]})
    unmendable = ["minecraft:wooden_" + t for t in ("sword", "shovel", "pickaxe", "axe", "hoe", "spear")]
    unmendable += [{"id": NS + ":wooden_" + w, "required": False} for w in WOODEN_UNMENDABLE]
    unmendable += [{"id": NS + ":" + i, "required": False} for i in ("hand_axe", "primitive_knife", "primitive_spear", "throwing_stick")]
    write(os.path.join(tags, "unmendable.json"), {"values": unmendable})
    # tin arrives in patch 4: bronze dust is mixed from whatever joins this tag
    write(os.path.join(DATA, "c", "tags", "item", "dusts", "tin.json"), {"replace": False, "values": []})


# ---------------------------------------------------------------- art

def lum(rgb):
    r, g, b = rgb[:3]
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def ramp(dark, light, t):
    t = max(0.0, min(1.0, t))
    return tuple(round(d + (l - d) * t) for d, l in zip(dark, light))


def recolour(im, dark, light, keep=None, lo=None, hi=None):
    """Every opaque pixel by its brightness on the ramp dark..light (stretched over the image's own range); keep(x, y)
    leaves a pixel as it is."""
    im = im.convert("RGBA")
    values = [lum(im.getpixel((x, y))) for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3]]
    lo = min(values) if lo is None else lo
    hi = max(values) if hi is None else hi
    out = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            px = im.getpixel((x, y))
            if px[3] == 0 or keep and keep(x, y):
                continue
            out.putpixel((x, y), ramp(dark, light, (lum(px) - lo) / max(1e-6, hi - lo)) + (px[3],))
    return out


def png(path, im):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def item_model(name, texture):
    write(os.path.join(ASSETS, "models", "item", "stone_age", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/stone_age/" + texture}})
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/stone_age/" + name}})


# dust colours (dark, light)
DUSTS = {
    "flint_dust": ((38, 38, 44), (128, 128, 136)),
    "clay_dust": ((112, 118, 134), (196, 202, 216)),
    "bronze_dust": ((112, 62, 26), (232, 164, 92)),
    "glass_dust": ((150, 184, 194), (240, 250, 252)),
    "sawdust": ((140, 104, 58), (226, 194, 140)),
    "cane_chad": ((108, 140, 70), (212, 228, 168)),
}
FIRED = ((92, 42, 26), (214, 126, 84))
UNFIRED = ((96, 102, 118), (192, 198, 212))
OAK = [(76, 56, 32), (110, 82, 48), (150, 116, 68), (184, 148, 92)]


def tex(name):
    return os.path.join(ASSETS, "textures", "item", "stone_age", name + ".png")


def art(jar):
    def vanilla(path):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + path))).convert("RGBA")

    base_dust = Image.open(os.path.join(ASSETS, "textures", "item", "iron_dust.png")).convert("RGBA")
    for name, (dark, light) in DUSTS.items():
        png(tex(name), recolour(base_dust, dark, light))

    bucket = vanilla("item/bucket.png")
    png(tex("clay_bucket"), recolour(bucket, *FIRED))
    png(tex("unfired_clay_bucket"), recolour(bucket, *UNFIRED))
    lo = min(lum(bucket.getpixel((x, y))) for y in range(16) for x in range(16) if bucket.getpixel((x, y))[3])
    hi = max(lum(bucket.getpixel((x, y))) for y in range(16) for x in range(16) if bucket.getpixel((x, y))[3])
    for fill in ("water", "milk"):
        full = vanilla("item/" + fill + "_bucket.png")
        same = lambda x, y, full=full: full.getpixel((x, y)) != bucket.getpixel((x, y))
        png(tex("clay_" + fill + "_bucket"), recolour(full, *FIRED, keep=same, lo=lo, hi=hi))

    png(tex("unfired_brick"), recolour(vanilla("item/brick.png"), *UNFIRED))

    # the brick form: an oak frame round a brick's hollow, lit from the top left
    form = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(4, 12):
        for x in range(1, 15):
            if 3 <= x <= 12 and 6 <= y <= 9:
                shade = 1 if y == 6 or x == 3 else 0
            elif y == 4 or x == 1:
                shade = 3
            elif y == 11 or x == 14:
                shade = 1
            else:
                shade = 2
            form.putpixel((x, y), OAK[shade] + (255,))
    png(tex("brick_form"), form)

    # the fence rail: a squared timber, corner to corner, two pixels wide with its end grain
    rail = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for i in range(2, 14):
        x, y = i, 15 - i
        rail.putpixel((x, y), OAK[2 if i % 4 == 0 else 3] + (255,))
        rail.putpixel((x, y - 1), OAK[2] + (255,))
        rail.putpixel((x + 1, y), OAK[1] + (255,))
    rail.putpixel((2, 13), OAK[0] + (255,))
    rail.putpixel((14, 1), OAK[0] + (255,))
    png(tex("fence_rail"), rail)

    for name in ITEMS:
        item_model(name, name)

    # the pane cast: the ingot cast's gold with a window's hollow (four panes and their cross)
    ingot = Image.open(os.path.join(ASSETS, "textures", "item", "tinkering", "cast", "ingot.png")).convert("RGBA")
    golds = sorted({ingot.getpixel((x, y))[:3] for y in range(16) for x in range(16) if ingot.getpixel((x, y))[3]}, key=lum)
    cast = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if 3 <= x <= 12 and 3 <= y <= 12 and x not in (7, 8) and y not in (7, 8):
                continue
            light = y <= 1 or x <= 1 or (x, y) in ((7, 3), (3, 7))
            dark = y >= 14 or x >= 14 or (x == 13 and y >= 3) or (y == 13 and x >= 3)
            colour = golds[-1] if light else golds[0] if dark else golds[len(golds) // 2 + ((x * 3 + y) % 2)]
            cast.putpixel((x, y), colour + (255,))
    png(os.path.join(ASSETS, "textures", "item", "tinkering", "cast", "pane.png"), cast)
    write(os.path.join(ASSETS, "models", "item", "tinkering", "cast", "pane.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/tinkering/cast/pane"}})
    write(os.path.join(ASSETS, "items", "pane_cast.json"), {"model": {"type": "minecraft:model", "model": NS + ":item/tinkering/cast/pane"}})

    # molten glass: honey's moving texture, pale and clear
    for folder in (os.path.join("block", "tinkering", "molten"), os.path.join("gui", "tinkering", "molten")):
        honey = Image.open(os.path.join(ASSETS, "textures", folder, "honey.png")).convert("RGBA")
        png(os.path.join(ASSETS, "textures", folder, "glass.png"), recolour(honey, (150, 196, 206), (238, 250, 252)))
        meta = os.path.join(ASSETS, "textures", folder, "honey.png.mcmeta")
        if os.path.exists(meta):
            with open(meta, encoding="utf-8") as f:
                write(os.path.join(ASSETS, "textures", folder, "glass.png.mcmeta"), json.load(f))

    write(os.path.join(ASSETS, "lang", "parts", "stone_age.json"), LANG)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with zipfile.ZipFile(sys.argv[1]) as jar:
        data(jar)
        art(jar)


if __name__ == "__main__":
    main()
