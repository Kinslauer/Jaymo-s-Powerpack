"""Writes the assets for reading the sky (0.18.23, power/zfighter/sky):

* the Star-Sight Bench (block `star_bench`): three 16x16 textures (stone base, a dull lens, a lit lens), two block models (asleep, and
  awake with an emissive lens), the blockstate on its `awake` property, its item definition, loot table and grid recipe
  (two stone slabs and an amethyst shard: furniture, not a metal part);
* the star chart (item `star_chart`, one item with a world component): six 16x16 textures (`star_chart_<world>`), an item model
  each, and the item definition that picks one by the stack's custom_model_data string (the world's key).

    python3 -I tools/gen_star_sky.py

Model faces reference #variables, never a raw sprite id (26.3 draws a raw id purple). The boxes in BENCH must match
StarBench.SHAPE (pixels of 16). Vanilla style: flat colours, a darker edge, a few flecks, fixed seeds so reruns match.
"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
DATA = os.path.join(ROOT, "data", "jaymospowers")

FACES = ("north", "east", "south", "west", "up", "down")
# the bench: a low plinth, a post, and the lens on it ([x1, y1, z1, x2, y2, z2, texture variable])
BENCH = [(2, 0, 2, 14, 4, 14, "base"), (6, 4, 6, 10, 9, 10, "base"), (5, 9, 5, 11, 12, 11, "lens")]
# the six worlds of the pod, in the order of the destinations, with the colour of each one's light
WORLDS = {"moon": (230, 235, 255), "rustfall": (255, 122, 74), "glacia": (159, 224, 255), "cinder": (255, 176, 80), "maw": (176, 112, 255),
          "verdant": (124, 255, 138)}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(img, *parts):
    path = os.path.join(ASSETS, "textures", *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def shade(colour, amount):
    return tuple(max(0, min(255, c + amount)) for c in colour)


def put(img, x, y, colour):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), colour if len(colour) == 4 else colour + (255,))


# ---------------------------------------------------------------- the bench

def base_texture():
    """Grey-blue worked stone: darker edge, light flecks, a few tiny gilt specks like stars."""
    rng = random.Random("star_bench_base")
    base = (118, 122, 138)
    img = Image.new("RGBA", (16, 16), base + (255,))
    for y in range(16):
        for x in range(16):
            amount = rng.randint(-8, 8)
            if x in (0, 15) or y in (0, 15):
                amount -= 24
            put(img, x, y, shade(base, amount))
    for _ in range(6):
        put(img, rng.randint(1, 14), rng.randint(1, 14), (236, 214, 150))
    return img


def lens_texture(lit):
    """The lens: a dull dark violet glass, or the same lit through with a pale star-blue and a white heart."""
    rng = random.Random("star_bench_lens_%s" % lit)
    base = (214, 228, 255) if lit else (66, 56, 92)
    img = Image.new("RGBA", (16, 16), base + (255,))
    for y in range(16):
        for x in range(16):
            amount = rng.randint(-8, 8)
            if x in (0, 15) or y in (0, 15):
                amount -= 30 if not lit else 40
            put(img, x, y, shade(base, amount))
    # the glint of a four-rayed star
    glint = (255, 255, 255) if lit else (150, 140, 190)
    for i in range(-3, 4):
        put(img, 8 + i, 8, glint)
        put(img, 8, 8 + i, glint)
    return img


def bench_model(awake):
    elements = []
    for b in BENCH:
        el = {"from": list(b[:3]), "to": list(b[3:6]), "faces": {f: {"texture": "#" + b[6]} for f in FACES}}
        if awake and b[6] == "lens":
            el["light_emission"] = 15
        elements.append(el)
    return {"textures": {"base": "jaymospowers:block/star_bench_base",
                         "lens": "jaymospowers:block/star_bench_lens_" + ("lit" if awake else "dull"),
                         "particle": "jaymospowers:block/star_bench_base"},
            "elements": elements}


def bench():
    save(base_texture(), "block", "star_bench_base.png")
    save(lens_texture(False), "block", "star_bench_lens_dull.png")
    save(lens_texture(True), "block", "star_bench_lens_lit.png")
    write(os.path.join(ASSETS, "models", "block", "star_bench.json"), bench_model(False))
    write(os.path.join(ASSETS, "models", "block", "star_bench_awake.json"), bench_model(True))
    write(os.path.join(ASSETS, "blockstates", "star_bench.json"), {"variants": {
        "awake=false": {"model": "jaymospowers:block/star_bench"},
        "awake=true": {"model": "jaymospowers:block/star_bench_awake"}}})
    write(os.path.join(ASSETS, "items", "star_bench.json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:block/star_bench"}})
    write(os.path.join(DATA, "loot_table", "blocks", "star_bench.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                   "entries": [{"type": "minecraft:item", "name": "jaymospowers:star_bench"}]}],
        "random_sequence": "jaymospowers:blocks/star_bench"})
    write(os.path.join(DATA, "recipe", "star_bench.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"S": "minecraft:stone_slab", "A": "minecraft:amethyst_shard"},
        "pattern": ["SAS"],
        "result": {"id": "jaymospowers:star_bench"}})


# ---------------------------------------------------------------- the charts

# where each world's mark sits on its chart (pixels), so the six read differently at a glance
MARKS = {"moon": (11, 5), "rustfall": (4, 10), "glacia": (10, 11), "cinder": (5, 5), "maw": (8, 8), "verdant": (11, 9)}


def chart_texture(world):
    """A folded sheet of old paper, dark-blue border, a few pale stars, and the world's own coloured mark."""
    rng = random.Random("star_chart_" + world)
    paper, edge, ink = (206, 190, 154), (58, 52, 78), (62, 54, 96)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(1, 15):
        for x in range(2, 14):
            colour = shade(paper, rng.randint(-8, 8))
            if x in (2, 13) or y in (1, 14):
                colour = edge
            elif x == 3 or y == 2:
                colour = shade(paper, 14)
            put(img, x, y, colour)
    # the fold
    for y in range(2, 14):
        put(img, 8, y, shade(paper, -26))
    # pale stars, and a line or two of ink
    for _ in range(5):
        put(img, rng.randint(4, 11), rng.randint(3, 12), ink)
    for x in range(4, 8):
        put(img, x, 12, ink)
    # the world's mark: a four-rayed star in its colour
    colour = WORLDS[world]
    mx, my = MARKS[world]
    put(img, mx, my, colour)
    put(img, mx - 1, my, shade(colour, -60))
    put(img, mx + 1, my, shade(colour, -60))
    put(img, mx, my - 1, shade(colour, -60))
    put(img, mx, my + 1, shade(colour, -60))
    return img


def charts():
    cases = []
    for world in WORLDS:
        save(chart_texture(world), "item", "star_chart_%s.png" % world)
        write(os.path.join(ASSETS, "models", "item", "star_chart_%s.json" % world),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/star_chart_" + world}})
        cases.append({"when": world, "model": {"type": "minecraft:model", "model": "jaymospowers:item/star_chart_" + world}})
    write(os.path.join(ASSETS, "items", "star_chart.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": {"type": "minecraft:model", "model": "jaymospowers:item/star_chart_moon"}}})


if __name__ == "__main__":
    bench()
    charts()
