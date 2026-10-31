"""(0.18.121, Xeno-Band patch 6) Art and data for earning aliens: star tablets, probe circuits, the wreck core, Bulwark's
dusts, the star wreck block, the probe's skin, their models, and the dusts' smelting and blasting recipes.

The probe's skin uses only the shape (alpha) of the vanilla vex texture as its layout, from the 26.3 client jar; every
colour is our own. Run: python3 tools/gen_xeno_finds.py [path to 26.3.jar]"""
import io
import json
import random
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = ROOT / "assets" / "jaymospowers"
DATA = ROOT / "data" / "jaymospowers"
JAR = sys.argv[1] if len(sys.argv) > 1 else "/home/user/cc21/jars/26.3.jar"
GREEN = (92, 240, 122, 255)
CLEAR = (0, 0, 0, 0)

rng = random.Random(6120)


def put(img, x, y, c):
    if 0 <= x < img.size[0] and 0 <= y < img.size[1]:
        img.putpixel((x, y), c)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def save(img, rel):
    path = ASSETS / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def item_json(name):
    write(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{name}"}})
    write(ASSETS / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymospowers:item/{name}"}})


def tablet():
    """A slab of dark star-stone with chipped corners and green glyph lines cut into it."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(2, 14):
        for x in range(3, 13):
            if (x, y) in ((3, 2), (12, 2), (3, 13), (12, 13)):
                continue
            put(img, x, y, shade((54, 58, 66, 255), 0.9 + rng.random() * 0.2))
    for x in range(3, 13):
        put(img, x, 13, (34, 36, 42, 255))
    for y in range(2, 14):
        put(img, 12, y, (40, 42, 48, 255))
    for y, x0, x1 in ((4, 5, 10), (6, 5, 8), (8, 6, 11), (10, 5, 9)):
        for x in range(x0, x1):
            put(img, x, y, GREEN if (x + y) % 3 else (60, 180, 90, 255))
    put(img, 9, 6, GREEN)
    put(img, 9, 7, GREEN)
    save(img, "item/star_tablet.png")
    item_json("star_tablet")


def circuit():
    """A small green board with copper traces and a dark chip."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(4, 12):
        for x in range(2, 14):
            put(img, x, y, shade((40, 110, 60, 255), 0.9 + rng.random() * 0.2))
    for x in range(2, 14):
        put(img, x, 11, (28, 80, 44, 255))
    for x in range(3, 13, 2):
        put(img, x, 5, (200, 140, 70, 255))
        put(img, x, 10, (200, 140, 70, 255))
    for y in range(6, 10):
        for x in range(6, 10):
            put(img, x, y, (30, 30, 34, 255))
    put(img, 7, 7, GREEN)
    for x in range(3, 6):
        put(img, x, 8, (220, 170, 90, 255))
    for x in range(10, 13):
        put(img, x, 7, (220, 170, 90, 255))
    save(img, "item/probe_circuit.png")
    item_json("probe_circuit")


def wreck_core():
    """A fist-sized ball of black hull metal, cracked, green light in the cracks."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 5.8:
                k = 1.25 - (x + y) / 30.0
                put(img, x, y, shade((60, 62, 70, 255), k))
    for x, y in ((5, 6), (6, 7), (7, 7), (8, 8), (9, 8), (10, 9), (7, 4), (7, 5), (6, 10), (5, 11)):
        put(img, x, y, GREEN)
    put(img, 8, 7, (200, 255, 210, 255))
    save(img, "item/wreck_core.png")
    item_json("wreck_core")


def dust(name, base):
    """A small heap of ground ore."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(7, 14):
        half = (y - 6) + 1
        for x in range(8 - half, 8 + half):
            if 2 <= x <= 13:
                put(img, x, y, shade(base, 0.8 + rng.random() * 0.4 - (y - 7) * 0.02))
    for _ in range(6):
        put(img, rng.randint(5, 11), rng.randint(9, 13), shade(base, 1.35))
    save(img, f"item/{name}.png")
    item_json(name)


def wreck_block():
    """Black hull plating: riveted seams, a dented panel, green running lights."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade((58, 60, 68, 255), 0.88 + rng.random() * 0.2))
    for i in range(16):
        img.putpixel((i, 0), (82, 86, 96, 255))
        img.putpixel((i, 8), (36, 38, 44, 255))
        img.putpixel((0, i), (82, 86, 96, 255))
        img.putpixel((8, i), (36, 38, 44, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13), (5, 5), (10, 10)):
        img.putpixel((x, y), (120, 124, 132, 255))
    for x in range(10, 14):
        img.putpixel((x, 4), GREEN)
    img.putpixel((3, 11), GREEN)
    img.putpixel((4, 11), (60, 180, 90, 255))
    save(img, "block/star_wreck.png")
    write(ASSETS / "blockstates" / "star_wreck.json", {"variants": {"": {"model": "jaymospowers:block/star_wreck"}}})
    write(ASSETS / "models" / "block" / "star_wreck.json", {"parent": "minecraft:block/cube_all", "textures": {"all": "jaymospowers:block/star_wreck"}})
    write(ASSETS / "items" / "star_wreck.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/star_wreck"}})
    write(ASSETS / "models" / "item" / "star_wreck.json", {"parent": "jaymospowers:block/star_wreck"})


def probe():
    """The probe: the vex's layout (its alpha only) painted as grey metal, a green visor across the face."""
    with zipfile.ZipFile(JAR) as jar:
        shape = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/entity/illager/vex.png"))).convert("RGBA")
    img = Image.new("RGBA", shape.size, CLEAR)
    w, h = shape.size
    for y in range(h):
        for x in range(w):
            if shape.getpixel((x, y))[3] == 0:
                continue
            base = (150, 156, 166, 255) if y < 16 else (110, 116, 126, 255)
            if x >= 16 and y >= 14:
                # the wings: thin green-lit fins
                base = (70, 140, 96, 200)
            img.putpixel((x, y), shade(base, 0.85 + rng.random() * 0.25))
    # the head's front face (8..16, 8..16): a dark face plate and a green visor
    for y in range(9, 15):
        for x in range(9, 15):
            img.putpixel((x, y), (54, 58, 66, 255))
    for x in range(9, 15):
        img.putpixel((x, 11), GREEN)
    img.putpixel((11, 12), (200, 255, 210, 255))
    save(img, "entity/star_probe.png")


def recipes():
    for metal, ingot in (("iron", "minecraft:iron_ingot"), ("copper", "minecraft:copper_ingot"), ("gold", "minecraft:gold_ingot")):
        for kind, time in (("smelting", 200), ("blasting", 100)):
            write(DATA / "recipe" / f"{metal}_dust_{kind}.json", {"type": f"minecraft:{kind}", "category": "misc",
                "ingredient": f"jaymospowers:{metal}_dust", "result": {"id": ingot}, "experience": 0.35, "cookingtime": time})


if __name__ == "__main__":
    tablet()
    circuit()
    wreck_core()
    dust("iron_dust", (196, 190, 184, 255))
    dust("copper_dust", (206, 120, 78, 255))
    dust("gold_dust", (240, 206, 80, 255))
    wreck_block()
    probe()
    recipes()
    print("xeno finds written under", ROOT)
