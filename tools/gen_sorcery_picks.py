"""Draws and writes the art and data for the sorcery picks of Jaymo's Kingdoms
(kingdoms/src/main/java/com/jaymoskingdoms/SorceryPicks.java and monsters/MadeBrute.java):

* the three scrolls' 16x16 item art (Bulwark, Tide Pearl, Harrying Gale), their item models and definitions;
* the skill-bar icons and Spell Engine spell files of the three spells (data/jaymoskingdoms/spell/sorc_<spell>.json);
* the stitched page's item art, model and definition (the item that teaches the rite);
* the made brute's three skins (the ogre's body, recoloured: a villager's, an illager's and a witch's) in
  textures/entity/legend/ogre_made_<n>.png.

Run it from the root:

    python -I tools/gen_sorcery_picks.py
"""
import colorsys
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


OUT = rgb("2e2218")
EARTH = [rgb("d9b46a"), rgb("b08a4e"), rgb("7a5a30")]
WATER = [rgb("bfe0ff"), rgb("3f76e4"), rgb("23418f")]
AIR = [rgb("ffffff"), rgb("dde8f0"), rgb("9fb4c6")]
SPARK = rgb("ffe27a")

# name -> (palette, hue for the scroll art)
SPELLS = {
    "bulwark": (EARTH, 0.09),
    "tide_pearl": (WATER, 0.60),
    "harrying_gale": (AIR, 0.52),
}


def outline(img):
    px = img.load()
    solid = {(x, y) for x in range(16) for y in range(16) if px[x, y][3]}
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in solid:
                px[nx, ny] = OUT


def icon(draw):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(img.load())
    outline(img)
    return img


def bulwark(px):
    # a half-dome of stacked earth with a gate of light in it
    pal = EARTH
    for y in range(4, 13):
        half = int((9 - (12 - y) * 0.0) * 0) + [3, 4, 5, 6, 6, 7, 7, 7, 7][y - 4]
        for x in range(8 - half, 8 + half):
            px[x, y] = pal[0] if y < 7 else (pal[1] if y < 10 else pal[2])
    for x in range(6, 10):
        for y in range(9, 13):
            px[x, y] = pal[2] if (x + y) % 2 else rgb("4a3519")
    for (x, y) in ((7, 3), (8, 3), (4, 5), (11, 5)):
        px[x, y] = SPARK


def tide_pearl(px):
    # a round pearl with a ring of water round its foot
    pal = WATER
    for (x, y) in ((6, 3), (7, 3), (8, 3), (9, 3), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
                   (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (6, 7), (7, 7), (8, 7), (9, 7)):
        px[x, y] = pal[0] if (x + y) % 3 else pal[1]
    px[6, 4] = rgb("ffffff")
    px[7, 4] = rgb("ffffff")
    for x in range(2, 14):
        px[x, 11] = pal[1] if x % 2 else pal[2]
    for x in range(3, 13):
        px[x, 12] = pal[2] if x % 2 else pal[1]
    for x in range(5, 11):
        px[x, 10] = pal[0] if x % 2 else pal[1]


def gale(px):
    # three curling streaks of wind
    pal = AIR
    for i in range(9):
        px[3 + i, 4] = pal[i % 2]
    px[12, 5] = pal[1]
    px[12, 6] = pal[2]
    px[11, 7] = pal[1]
    for i in range(10):
        px[2 + i, 9] = pal[(i + 1) % 2]
    px[12, 10] = pal[1]
    px[12, 11] = pal[2]
    px[11, 12] = pal[1]
    for i in range(6):
        px[5 + i, 13] = pal[i % 2]
    px[4, 12] = pal[2]


DRAW = {"bulwark": bulwark, "tide_pearl": tide_pearl, "harrying_gale": gale}


def tint(img, hue, sat=1.0, val=1.0):
    """Recolours an image's opaque pixels to a hue, keeping their light and shade."""
    out = img.copy()
    px = out.load()
    for x in range(out.width):
        for y in range(out.height):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, min(1.0, max(s, 0.35) * sat), min(1.0, v * val))
            px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), a)
    return out


def scrolls():
    base = Image.open(os.path.join(ASSETS, "textures", "item", "scroll_of_sanctuary.png")).convert("RGBA")
    for name, (pal, hue) in SPELLS.items():
        tint(base, hue, 0.9 if name != "harrying_gale" else 0.35, 1.0).save(os.path.join(ASSETS, "textures", "item", "scroll_of_%s.png" % name))
        write(os.path.join(ASSETS, "models", "item", "scroll_of_%s.json" % name),
              json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/scroll_of_%s" % name}}, indent=2) + "\n")
        write(os.path.join(ASSETS, "items", "scroll_of_%s.json" % name),
              json.dumps({"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/scroll_of_%s" % name}}, indent=2) + "\n")


def spells():
    for name in SPELLS:
        icon(DRAW[name]).save(os.path.join(ASSETS, "textures", "spell", "sorc_%s.png" % name))
        cooldown = {"bulwark": 40, "tide_pearl": 15, "harrying_gale": 20}[name]
        write(os.path.join(DATA, "spell", "sorc_%s.json" % name), json.dumps({
            "school": "spell_power:physical_melee",
            "range": 0.0,
            "tier": 1,
            "group": "sorcerer",
            "type": "ACTIVE",
            "active": {"cast": {"duration": 0.0}},
            "target": {"type": "NONE"},
            "deliver": {"type": "CUSTOM", "custom": {"handler": "jaymoskingdoms:sorcerer_spell"}},
            "cost": {"exhaust": 0.0, "cooldown": {"duration": cooldown}},
        }, indent=2) + "\n")


# the made brute: villager (brown and patched), illager (slate), witch (bruise purple, green at the seams)
BRUTES = [(0.08, 0.55, 0.85), (0.55, 0.30, 0.75), (0.78, 0.60, 0.70)]


def brutes():
    base = Image.open(os.path.join(ASSETS, "textures", "entity", "legend", "ogre_green.png")).convert("RGBA")
    for i, (hue, sat, val) in enumerate(BRUTES):
        skin = tint(base, hue, sat, val)
        px = skin.load()
        rnd = random.Random(1000 + i)
        # stitched, blood-dark seams across the body
        for _ in range(base.width * base.height // 90):
            x, y = rnd.randrange(base.width), rnd.randrange(base.height)
            for k in range(rnd.randint(2, 5)):
                if x + k < base.width and px[x + k, y][3]:
                    px[x + k, y] = (96, 14, 18, 255) if k % 2 == 0 else (58, 10, 12, 255)
        skin.save(os.path.join(ASSETS, "textures", "entity", "legend", "ogre_made_%d.png" % i))


def page():
    """A torn page crossed with red stitching and a dark hand-print."""
    paper = [rgb("e8dcc0"), rgb("c9b894"), rgb("9c8a66")]
    thread = rgb("8a0f14")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for x in range(3, 13):
        for y in range(2, 14):
            px[x, y] = paper[0] if (x * 3 + y) % 7 else paper[1]
    for y in range(2, 14):
        px[12, y] = paper[2]
    for x in range(3, 13):
        px[x, 13] = paper[2]
    # torn corner
    for (x, y) in ((3, 2), (4, 2), (3, 3)):
        px[x, y] = (0, 0, 0, 0)
    # stitches down the middle and across
    for y in range(3, 13, 2):
        px[7, y] = thread
        px[8, y + 1 if y + 1 < 13 else y] = thread
    for x in range(4, 12, 2):
        px[x, 7] = thread
    # a small dark palm
    for (x, y) in ((5, 10), (6, 10), (5, 11), (6, 11), (9, 10), (10, 10), (9, 11)):
        px[x, y] = rgb("4a0508")
    outline(img)
    img.save(os.path.join(ASSETS, "textures", "item", "stitched_page.png"))
    write(os.path.join(ASSETS, "models", "item", "stitched_page.json"),
          json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/stitched_page"}}, indent=2) + "\n")
    write(os.path.join(ASSETS, "items", "stitched_page.json"),
          json.dumps({"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/stitched_page"}}, indent=2) + "\n")


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def main():
    scrolls()
    spells()
    page()
    brutes()


if __name__ == "__main__":
    main()
