"""Writes the assets for fuel grades and breath (0.18.48, power/zfighter/fuel and power/zfighter/breath): the 16x16 icons of
Pod Spirit, the Cinder Ember, the Hide Air Skin and the Sealed Helm, the two 64x32 layers the air skin and the helm are drawn
with when worn, their item models, item definitions and equipment assets, and the Helm Seal blank (the WeaponParts kind
helm_seal: stone and iron heads, shown hot while heated). Everything is our own art in the vanilla style: a one-pixel dark
outline, three tones lit from the top left.

    python3 -I tools/gen_fuel_breath.py

26.3 item models name their faces through texture variables (layer0), never raw sprite ids (they render purple).
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import weapon_parts_assets as wp  # noqa: E402  (its helpers: drawn heads, stone recolour, the hot-tinted item definition)

ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
NS = "jaymospowers"

OUTLINE = (30, 26, 36, 255)
IRON = ((196, 198, 206, 255), (140, 143, 152, 255), (92, 95, 104, 255))  # light, mid, dark
LEATHER = ((190, 140, 86, 255), (150, 100, 58, 255), (104, 66, 38, 255))
GLASS = ((214, 238, 250, 255), (120, 190, 230, 255), (70, 130, 190, 255))
SPIRIT = ((236, 228, 255, 255), (201, 184, 255, 255), (146, 118, 226, 255))
BRASS = ((236, 200, 110, 255), (196, 150, 62, 255), (140, 98, 38, 255))
EMBER_GLOW = ((255, 236, 120, 255), (255, 150, 40, 255), (200, 70, 20, 255))
CHAR = ((74, 62, 62, 255), (52, 42, 44, 255), (34, 28, 30, 255))


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, color)


def lit_rect(img, x0, y0, x1, y1, tones):
    """A box lit from the top left: light on its top and left edge, dark on its bottom and right, mid between."""
    light, mid, dark = tones
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            color = mid
            if y == y0 or x == x0:
                color = light
            if y == y1 or x == x1:
                color = dark
            put(img, x, y, color)


def lit_disc(img, cx, cy, r, tones):
    """A disc lit from the top left."""
    light, mid, dark = tones
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = (dx * dx + dy * dy) ** 0.5
            if d <= r:
                lean = (dx + dy) / max(r, 1.0)
                put(img, x, y, light if lean < -0.7 else dark if lean > 0.55 else mid)


def outline(img, color):
    """Every empty pixel touching a painted one (up, down, left, right) becomes the outline."""
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] != 0:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img.putpixel((x, y), color)


def blank16():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


# ---------------------------------------------------------------- the four icons

def pod_spirit():
    """A small stoppered phial of pale violet spirit."""
    img = blank16()
    lit_rect(img, 6, 1, 9, 3, LEATHER)  # the stopper
    lit_rect(img, 6, 4, 9, 6, GLASS)  # the neck
    lit_disc(img, 8, 10.2, 4.6, SPIRIT)  # the flask
    for x in range(5, 12):
        put(img, x, 8, SPIRIT[1])
    put(img, 6, 9, (255, 255, 255, 255))
    put(img, 6, 10, SPIRIT[0])
    put(img, 10, 13, SPIRIT[2])
    put(img, 9, 12, SPIRIT[2])
    outline(img, OUTLINE)
    return img


def cinder_ember():
    """A cooling lump with a glowing seam."""
    img = blank16()
    rng = random.Random("cinder_ember")
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8.0, y + 0.5 - 8.5
            if (dx * dx + dy * dy) ** 0.5 <= 5.4 + rng.uniform(-1.0, 0.6):
                lean = (dx + dy) / 6.0
                put(img, x, y, CHAR[0] if lean < -0.5 else CHAR[2] if lean > 0.45 else CHAR[1])
    seam = [(5, 7), (6, 8), (7, 8), (8, 9), (9, 9), (10, 10), (8, 10), (7, 11), (9, 7), (10, 6)]
    for i, (x, y) in enumerate(seam):
        if img.getpixel((x, y))[3]:
            put(img, x, y, EMBER_GLOW[i % 3 if i % 3 else 1])
    for x, y in ((8, 9), (7, 8)):
        if img.getpixel((x, y))[3]:
            put(img, x, y, EMBER_GLOW[0])
    outline(img, OUTLINE)
    return img


def air_skin():
    """A tied leather bag with a flap and a brass buckle."""
    img = blank16()
    lit_rect(img, 3, 5, 12, 14, LEATHER)
    lit_rect(img, 3, 5, 12, 8, (LEATHER[1], LEATHER[2], LEATHER[2]))  # the flap
    put(img, 3, 5, LEATHER[0])
    for x in range(4, 12):
        put(img, x, 9, LEATHER[2])
    # the drawstring over the top and the tied knot
    rect(img, 6, 3, 9, 4, LEATHER[1])
    put(img, 6, 3, LEATHER[0])
    put(img, 7, 3, (230, 226, 210, 255))
    put(img, 8, 3, (230, 226, 210, 255))
    put(img, 7, 4, (200, 196, 180, 255))
    # the buckle
    rect(img, 7, 8, 8, 10, BRASS[1])
    put(img, 7, 8, BRASS[0])
    put(img, 8, 10, BRASS[2])
    # a seam
    for y in range(11, 14):
        put(img, 5, y, LEATHER[2])
        put(img, 10, y, LEATHER[0])
    outline(img, OUTLINE)
    return img


def sealed_helm():
    """An iron dome with a glass visor and rivets."""
    img = blank16()
    lit_disc(img, 8, 8.6, 6.0, IRON)
    lit_rect(img, 2, 9, 13, 13, IRON)  # the collar
    lit_rect(img, 4, 6, 11, 10, GLASS)  # the visor
    for x in range(5, 11):
        put(img, x, 9, GLASS[2])
    put(img, 5, 7, (255, 255, 255, 255))
    put(img, 6, 7, GLASS[0])
    put(img, 5, 8, GLASS[0])
    for x in (3, 12):
        put(img, x, 11, IRON[0])
    put(img, 8, 3, IRON[0])
    outline(img, OUTLINE)
    return img


# ---------------------------------------------------------------- the worn layers (64x32, the vanilla armour layout)

def noise_fill(img, x0, y0, x1, y1, tones, seed):
    rng = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            roll = rng.random()
            put(img, x, y, tones[0] if roll < 0.12 else tones[2] if roll < 0.28 else tones[1])


def helm_worn():
    """The head layer: an iron dome all round, a glass visor across the front, rivets along the brow."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # the head's faces: right 0-7, front 8-15, left 16-23, back 24-31 (rows 8-15); top 8-15 and bottom 16-23 (rows 0-7)
    noise_fill(img, 0, 8, 31, 15, IRON, "helm_sides")
    noise_fill(img, 8, 0, 15, 7, IRON, "helm_top")
    noise_fill(img, 16, 0, 23, 7, IRON, "helm_bottom")
    # the visor across the front (and carried round to both sides a little)
    rect(img, 9, 10, 14, 13, GLASS[1])
    rect(img, 9, 13, 14, 13, GLASS[2])
    rect(img, 9, 10, 10, 10, GLASS[0])
    put(img, 9, 11, GLASS[0])
    rect(img, 6, 11, 7, 12, GLASS[1])
    rect(img, 16, 11, 17, 12, GLASS[1])
    # the brim and rivets
    for x in range(0, 32):
        put(img, x, 9, IRON[2])
    for x in range(0, 32, 4):
        put(img, x + 1, 8, IRON[0])
    # the seam of the collar
    for x in range(0, 32):
        put(img, x, 15, IRON[2])
    return img


def skin_worn():
    """The chest layer: leather straps over the shoulders and chest, and the bag itself on the back."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # body: front 20-27, back 32-39 (rows 20-31), right 16-19, left 28-31, top 20-27 (rows 16-19)
    # the back: the bag, with a flap and a buckle
    lit_rect(img, 32, 20, 39, 31, LEATHER)
    rect(img, 33, 21, 38, 24, LEATHER[1])
    for x in range(33, 39):
        put(img, x, 25, LEATHER[2])
    rect(img, 35, 24, 36, 26, BRASS[1])
    put(img, 35, 24, BRASS[0])
    rng = random.Random("skin_back")
    for _ in range(14):
        put(img, rng.randint(33, 38), rng.randint(26, 30), LEATHER[rng.choice((0, 2))])
    # the front: two straps down the chest
    for y in range(20, 32):
        for x in (21, 22, 25, 26):
            put(img, x, y, LEATHER[1] if x in (22, 25) else LEATHER[0])
    # the straps cross the top of the shoulders and the sides of the body
    for x in (21, 22, 25, 26):
        for y in range(16, 20):
            put(img, x, y, LEATHER[1])
    for side_x in (16, 17, 18, 19, 28, 29, 30, 31):
        for y in range(20, 26):
            put(img, side_x, y, LEATHER[2] if side_x in (17, 18, 29, 30) else LEATHER[1])
    return img


# ---------------------------------------------------------------- the Helm Seal blank (WeaponParts kind helm_seal)

SEAL_ROWS = [
    "................",
    "................",
    ".....######.....",
    "....#oooooox#...",
    "...#ooxxxxxxx#..",
    "..#oxxxxxxxxxx#.",
    "..#oxxxxxxxxxx#.",
    "..#oxx######xx#.",
    "..#oxx#....#xx#.",
    "..#oxx######xx#.",
    "..#oxxxxxxxxxx#.",
    "..#xxxxxxxxxxx#.",
    "...############.",
    "................",
    "................",
    "................",
]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def simple_item(name, img):
    save(img, "item/" + name)
    write(os.path.join(ASSETS, "models", "item", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def worn_gear(name, layer):
    save(layer, "entity/equipment/humanoid/" + name)
    write(os.path.join(ASSETS, "equipment", name + ".json"), {"layers": {"humanoid": [{"texture": f"{NS}:{name}"}]}})


def seal_blank():
    part = "helm_seal"
    # the visor slot of the drawing is the shell's glass: '#' is the dark outline colour in the head palettes
    assert len(SEAL_ROWS) == 16 and all(len(r) == 16 for r in SEAL_ROWS), "helm_seal rows must be 16x16"
    wp.DRAWN[part] = SEAL_ROWS
    heads = {"stone": wp.as_stone(wp.drawn(part, "iron")), "iron": wp.drawn(part, "iron")}
    cases = []
    for metal, img in heads.items():
        rel = wp.OUT + part + "_" + metal
        save(img, rel)
        write(os.path.join(ASSETS, "models", rel + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:{rel}"}})
        cases.append({"when": metal, "model": {"type": "minecraft:model", "model": f"{NS}:{rel}"}})
    write(os.path.join(ASSETS, "items", part + ".json"), {"model": wp.hot({
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": cases[0]["model"]})})


def main():
    simple_item("pod_spirit", pod_spirit())
    simple_item("cinder_ember", cinder_ember())
    simple_item("air_skin", air_skin())
    simple_item("sealed_helm", sealed_helm())
    worn_gear("air_skin", skin_worn())
    worn_gear("sealed_helm", helm_worn())
    seal_blank()
    print("fuel and breath assets written")


if __name__ == "__main__":
    main()
