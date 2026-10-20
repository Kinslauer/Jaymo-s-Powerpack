"""Writes the Aether's passive animals' art and data (Powers 0.13.36, track C): the entity skins (painted here, vanilla
style, our own: nothing copied from The Aether mod or anyone else), the spawn egg and moa egg item textures, item
definitions and models, the loot tables, and the entity/egg names in lang/parts/aether.json.

    python -I tools/gen_aether_mobs.py

Needs only Pillow. The skins are painted onto the boxes the models declare: this script READS the
`part(parent, "name", u, v, x, y, z, w, h, d, ...)` lines in src/client/java/com/jaymospowers/client/aether/mob/*Model.java
for each part's texture offset and size, so a model change and a repaint stay in step (rerun after editing a model).
Every texture is seeded from its own name, so a rerun changes nothing. Run tools/gen_aether.py first if you run both:
it rewrites lang/parts/aether.json from scratch, and this script then adds its own block back at the end.
"""
import json
import os
import random
import re

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MODELS = os.path.join(ROOT, "src", "client", "java", "com", "jaymospowers", "client", "aether", "mob")
NS = "jaymospowers"
SUB = "aether"

PART = re.compile(r'part\(\w+, "(\w+)", (\d+), (\d+), (-?[\d.]+)F, (-?[\d.]+)F, (-?[\d.]+)F, (\d+), (\d+), (\d+)')


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*s):
    return [hx(c) for c in s]


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


# ---------------------------------------------------------------- reading the models

def parts_of(java):
    """{part name: dict(u, v, w, h, d)} from a model file's part(...) lines."""
    out = {}
    with open(os.path.join(MODELS, java), encoding="utf-8") as f:
        for line in f:
            m = PART.search(line)
            if m:
                out[m.group(1)] = dict(u=int(m.group(2)), v=int(m.group(3)), w=int(m.group(7)), h=int(m.group(8)), d=int(m.group(9)))
    return out


def faces(p):
    """The six faces of a part's texture net as (x, y, w, h). 'front' looks toward -z, where the mob faces."""
    u, v, w, h, d = p["u"], p["v"], p["w"], p["h"], p["d"]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def check_layout(name, size, parts):
    """Every part's net must lie inside the texture and overlap no other part's (parts that share a net are one)."""
    nets = {}
    for pname, p in parts.items():
        nets.setdefault((p["u"], p["v"], p["w"], p["h"], p["d"]), []).append(pname)
    rects = []
    for (u, v, w, h, d), names in nets.items():
        r = (u, v, u + 2 * d + 2 * w, v + d + h)
        assert r[2] <= size[0] and r[3] <= size[1], "%s: %s outside the %s texture" % (name, names, size)
        for other, onames in rects:
            assert r[2] <= other[0] or other[2] <= r[0] or r[3] <= other[1] or other[3] <= r[1], \
                "%s: %s overlaps %s" % (name, names, onames)
        rects.append((r, names))


class Skin:
    """A texture being painted, with helpers that work in a face's own coordinates."""

    def __init__(self, name, size, parts):
        check_layout(name, size, parts)
        self.im = Image.new("RGBA", size, (0, 0, 0, 0))
        self.px = self.im.load()
        self.rng = random.Random("aether-mob:" + name)
        self.parts = parts

    def box(self, name, side, top=None, bottom=None, weights=(5, 3, 2, 1), only=None):
        """Fill every face of a part: `side` is a colour ramp (light to dark); the top is lit, the bottom shaded."""
        p = self.parts[name]
        for face, (x0, y0, w, h) in faces(p).items():
            if only and face not in only:
                continue
            colors = top if face == "top" and top else bottom if face == "bottom" and bottom else side
            if face == "top" and not top:
                colors = [shade(c, 1.12) for c in side]
            if face == "bottom" and not bottom:
                colors = [shade(c, 0.78) for c in side]
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    self.px[x, y] = self.rng.choices(colors, list(weights)[:len(colors)])[0] + (255,)

    def at(self, name, face, x, y, color):
        x0, y0, w, h = faces(self.parts[name])[face]
        if 0 <= x < w and 0 <= y < h:
            self.px[x0 + x, y0 + y] = color + (255,)

    def rect(self, name, face, x, y, w, h, color):
        for dy in range(h):
            for dx in range(w):
                self.at(name, face, x + dx, y + dy, color)

    def patches(self, name, color, count, size=(2, 4), only=None, chance=0.9):
        """Blotches of one colour over the faces (cow patches, speckles)."""
        for face, (x0, y0, w, h) in faces(self.parts[name]).items():
            if only and face not in only:
                continue
            for _ in range(count):
                cx, cy = self.rng.randrange(w), self.rng.randrange(h)
                bw, bh = self.rng.randint(*size), self.rng.randint(*size)
                for dy in range(bh):
                    for dx in range(bw):
                        if self.rng.random() < chance:
                            self.at(name, face, cx + dx, cy + dy, color)

    def speckle(self, name, colors, count, only=None):
        for face, (x0, y0, w, h) in faces(self.parts[name]).items():
            if only and face not in only:
                continue
            for _ in range(count):
                self.at(name, face, self.rng.randrange(w), self.rng.randrange(h), self.rng.choice(colors))

    def outline(self, name, faces_, color):
        """A one-pixel border round the given faces' edges (stitching, rims)."""
        for face in faces_:
            x0, y0, w, h = faces(self.parts[name])[face]
            for x in range(w):
                self.at(name, face, x, 0, color)
                self.at(name, face, x, h - 1, color)
            for y in range(h):
                self.at(name, face, 0, y, color)
                self.at(name, face, w - 1, y, color)

    def finish(self, path):
        save(self.im, path)


# ---------------------------------------------------------------- shared bits

LEATHER = ramp("9a6a3a", "8a5a30", "a8764a", "6e4524")
STITCH = hx("4a2e16")
BUCKLE = hx("e0c050")
DARK = hx("23232e")
WHITE = hx("ffffff")
FEATHERS = ramp("ffffff", "f0f4f8", "e2e8f0", "d0d9e4")
FEATHER_TIP = ramp("d9e0ea", "c5cfdc", "b3bfce")


def saddle(sk, name="saddle"):
    sk.box(name, LEATHER)
    sk.outline(name, ["top", "left", "right", "front", "back"], STITCH)
    sk.rect(name, "top", 3, 2, 2, 2, BUCKLE)


def wing(sk, name, tip_high):
    """A feathered wing: the broad faces are rows of feathers, getting greyer toward the tip, the edges plain."""
    p = sk.parts[name]
    for face, (x0, y0, w, h) in faces(p).items():
        for y in range(h):
            for x in range(w):
                dist = (x / max(1, w - 1)) if tip_high else 1.0 - x / max(1, w - 1)
                if face in ("top", "bottom"):
                    base = FEATHERS if dist < 0.6 else FEATHER_TIP
                    c = base[2] if (y // 2) % 2 else base[0]
                    if sk.rng.random() < 0.15:
                        c = base[1]
                else:
                    c = FEATHERS[2]
                sk.px[x0 + x, y0 + y] = c + (255,)


# ---------------------------------------------------------------- the skins

def phyg():
    sk = Skin("phyg", (128, 64), winged()["phyg"])
    pink = ramp("f2a6b0", "e8909c", "f8bcc4", "d97c8c")
    for n in ("head", "body", "leg_fr", "leg_fl", "leg_br", "leg_bl", "tail"):
        sk.box(n, pink)
    sk.box("snout", ramp("e07a8a", "d56b7c", "e98c9a"))
    sk.box("ear_l", ramp("e8909c", "d97c8c"))
    sk.box("ear_r", ramp("e8909c", "d97c8c"))
    # a pale belly and darker hooves
    sk.rect("body", "bottom", 0, 0, 8, 14, hx("f6c4cb"))
    for n in ("leg_fr", "leg_fl", "leg_br", "leg_bl"):
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, h - 1, w, 1, hx("b8606e"))
    # face: eyes with a white corner, nostrils
    sk.rect("head", "front", 1, 3, 1, 2, WHITE)
    sk.rect("head", "front", 2, 3, 1, 2, DARK)
    sk.rect("head", "front", 5, 3, 1, 2, DARK)
    sk.rect("head", "front", 6, 3, 1, 2, WHITE)
    sk.rect("snout", "front", 1, 1, 1, 1, hx("8a3a48"))
    sk.rect("snout", "front", 2, 1, 1, 1, hx("8a3a48"))
    sk.speckle("body", [hx("f8bcc4"), hx("e08492")], 18, only=["top", "right", "left"])
    wing(sk, "wing_l", True)
    wing(sk, "wing_r", False)
    saddle(sk)
    return sk


def cow():
    sk = Skin("flying_cow", (128, 80), winged()["cow"])
    brown = ramp("7a4a2a", "8c5934", "6a3e22", "9a6a44")
    white = ramp("f2f0ea", "e4e0d6", "d9d6cc")
    for n in ("head", "body", "leg_fr", "leg_fl", "leg_br", "leg_bl"):
        sk.box(n, brown)
    sk.patches("body", white[0], 5, (3, 6))
    sk.patches("body", white[1], 2, (1, 3))
    sk.patches("head", white[0], 1, (2, 4), only=["front", "top"])
    for n in ("leg_fr", "leg_fl", "leg_br", "leg_bl"):
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, h - 2, w, 2, white[1])
    sk.box("muzzle", ramp("d9b8a0", "caa68c", "e4c8b0"))
    sk.rect("muzzle", "front", 1, 1, 1, 1, hx("7a4a3a"))
    sk.rect("muzzle", "front", 4, 1, 1, 1, hx("7a4a3a"))
    sk.box("horn_l", ramp("e8e2cc", "d6cfb4"))
    sk.box("horn_r", ramp("e8e2cc", "d6cfb4"))
    sk.box("udder", ramp("f0b0b0", "e69a9c", "f8c4c4"))
    sk.box("tail", brown)
    sk.rect("tail", "front", 0, 7, 2, 2, hx("3a2412"))
    sk.rect("head", "front", 1, 3, 1, 2, DARK)
    sk.rect("head", "front", 6, 3, 1, 2, DARK)
    sk.rect("head", "front", 2, 3, 1, 2, WHITE)
    sk.rect("head", "front", 5, 3, 1, 2, WHITE)
    wing(sk, "wing_l", True)
    wing(sk, "wing_r", False)
    saddle(sk)
    return sk


# the winged model's two layouts are in one file: split its part lines by layer
def winged():
    with open(os.path.join(MODELS, "WingedAnimalModel.java"), encoding="utf-8") as f:
        text = f.read()
    cut = text.index("private static LayerDefinition cowLayer")
    out = {}
    for key, chunk in (("phyg", text[:cut]), ("cow", text[cut:])):
        parts = {}
        for line in chunk.splitlines():
            m = PART.search(line)
            if m:
                parts[m.group(1)] = dict(u=int(m.group(2)), v=int(m.group(3)), w=int(m.group(7)), h=int(m.group(8)), d=int(m.group(9)))
        out[key] = parts
    return out


WOOL = {
    "white": ramp("f4f6f8", "e3e8ee", "ffffff", "d3dae2"),
    "light_blue": ramp("b5dcf2", "9ccbea", "cde9f8", "86b8d8"),
    "cyan": ramp("5ec5cf", "49b0bb", "7fd8e0", "3a98a3"),
    "lime": ramp("a8de5a", "92c846", "bded7c", "7bb034"),
    "pink": ramp("f2a6c8", "e690b8", "f8c0d8", "d67aa4"),
    "purple": ramp("a77ad8", "9264c4", "bf98e8", "7d52ab"),
}


def sheepuff(color):
    sk = Skin("sheepuff_" + color, (128, 64), parts_of("SheepuffModel.java"))
    wool = WOOL[color]
    skin = ramp("e0cfb4", "cdb897", "f0e4cc", "bfa886")
    legs = ramp("bfa886", "a89070", "cdb897")
    sk.box("head", skin)
    sk.box("body", skin)
    for n in ("leg_fr", "leg_fl", "leg_br", "leg_bl"):
        sk.box(n, legs)
    sk.box("ear_l", skin)
    sk.box("ear_r", skin)
    # wool: a curly crowd of lighter and darker tufts
    for n in ("wool", "head_wool"):
        sk.box(n, wool, weights=(4, 3, 3, 2))
        sk.patches(n, wool[2], 6, (1, 3))
        sk.patches(n, wool[3], 4, (1, 2), chance=0.7)
    sk.rect("head", "front", 1, 2, 1, 2, DARK)
    sk.rect("head", "front", 4, 2, 1, 2, DARK)
    sk.rect("head", "front", 2, 4, 2, 1, hx("a8806a"))
    return sk


def aerbunny():
    sk = Skin("aerbunny", (64, 32), parts_of("AerbunnyModel.java"))
    fur = ramp("f6f8fb", "e4ebf3", "ffffff", "d2dcea")
    for n in ("head", "body", "leg_bl", "leg_br", "leg_fl", "leg_fr"):
        sk.box(n, fur)
    for n in ("cheek_l", "cheek_r", "tail"):
        sk.box(n, ramp("ffffff", "f0f5fb", "e4ebf3"))
        sk.speckle(n, [hx("d2dcea"), hx("c8d6ec")], 3)
    for n in ("ear_l", "ear_r"):
        sk.box(n, fur)
        sk.rect(n, "front", 0, 0, 1, 3, hx("f2b8c8"))
    sk.rect("head", "front", 0, 1, 1, 2, DARK)
    sk.rect("head", "front", 3, 1, 1, 2, DARK)
    sk.rect("head", "front", 1, 2, 2, 1, hx("e88aa0"))
    sk.speckle("body", [hx("c8d6ec"), hx("ffffff")], 10)
    return sk


MOA = {
    "blue": dict(body=ramp("4f7fc4", "3f6aa8", "6a9ad8", "335a94"), belly=hx("a8c4ec"), crest=ramp("2f5a9a", "26478a"),
                 wing=ramp("3a62a0", "2f5288", "5a86c8"), beak=ramp("e8b84a", "c99a2e", "f2cc66"), leg=ramp("e0a83c", "c08a28", "f0be5c")),
    "white": dict(body=ramp("f2ecde", "e0d8c4", "fffaf0", "cfc6b0"), belly=hx("fffaf0"), crest=ramp("e8dcc0", "d6c8a4"),
                  wing=ramp("e0d8c4", "cfc6b0", "f2ecde"), beak=ramp("e8a84a", "c98a2e", "f2c066"), leg=ramp("d8a04a", "b88432", "e8b868")),
    "black": dict(body=ramp("3a3a44", "2a2a32", "4c4c58", "1e1e26"), belly=hx("55556a"), crest=ramp("55556a", "44445a"),
                  wing=ramp("2a2a32", "1e1e26", "44444e"), beak=ramp("d8a238", "b0821e", "e8ba50"), leg=ramp("b08a30", "8e6c1e", "c8a044")),
}


def moa(kind):
    sk = Skin("moa_" + kind, (128, 64), parts_of("MoaModel.java"))
    c = MOA[kind]
    for n in ("body", "neck", "head", "tail"):
        sk.box(n, c["body"])
    sk.rect("body", "bottom", 0, 0, 8, 12, c["belly"])
    sk.speckle("body", [c["body"][2], c["body"][3]], 24, only=["top", "right", "left", "back"])
    sk.speckle("neck", [c["body"][2], c["body"][3]], 6)
    sk.box("crest", c["crest"])
    sk.box("beak", c["beak"])
    sk.rect("beak", "front", 0, 1, 2, 1, shade(c["beak"][1], 0.7))
    for n in ("wing_l", "wing_r"):
        sk.box(n, c["wing"])
        sk.speckle(n, [c["wing"][2]], 6)
    sk.box("tail", c["wing"])
    for n in ("leg_l", "leg_r"):
        sk.box(n, c["leg"])
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            for y in range(1, h, 3):
                sk.rect(n, face, 0, y, w, 1, c["leg"][1])
    for n in ("foot_l", "foot_r"):
        sk.box(n, c["leg"])
    # eyes on the sides of the head, near the beak: a ring of yellow round a dark pupil
    sk.at("head", "right", 3, 1, DARK)
    sk.at("head", "right", 2, 1, hx("f2d04a"))
    sk.at("head", "left", 1, 1, DARK)
    sk.at("head", "left", 2, 1, hx("f2d04a"))
    saddle(sk)
    return sk


# ---------------------------------------------------------------- items

def egg_image(base, spot, outline, seed, speckles=6, ring=False):
    """A 16x16 egg lit from the upper left, spotted; `ring` gives a spawn egg's band."""
    rng = random.Random("aether-egg:" + seed)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    cx, cy, rx, ry = 7.5, 8.0, 5.0, 6.6
    inside = {}
    for y in range(16):
        for x in range(16):
            # eggs are narrower at the top
            squeeze = 1.0 - 0.22 * max(0.0, (cy - y) / ry)
            dx, dy = (x - cx) / (rx * squeeze), (y - cy) / ry
            inside[(x, y)] = dx * dx + dy * dy <= 1.0
    for (x, y), ok in inside.items():
        if not ok:
            continue
        edge = any(not inside.get((x + ox, y + oy), False) for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = outline + (255,)
            continue
        lit = 1.12 if x + y < 14 else 0.88 if x + y > 17 else 1.0
        px[x, y] = shade(base, lit) + (255,)
    cells = [k for k, ok in inside.items() if ok and px[k[0], k[1]][:3] != outline]
    for _ in range(speckles):
        x, y = rng.choice(cells)
        px[x, y] = spot + (255,)
        if rng.random() < 0.5 and (x + 1, y) in cells:
            px[x + 1, y] = spot + (255,)
    if ring:
        for x in range(16):
            if inside[(x, 8)] and px[x, 8][:3] != outline:
                px[x, 8] = shade(spot, 0.95) + (255,)
    return im


SPAWN_EGGS = {
    "phyg_spawn_egg": (hx("f0a0aa"), hx("ffffff"), hx("b8606e")),
    "flying_cow_spawn_egg": (hx("7a4a2a"), hx("f2f0ea"), hx("3a2412")),
    "sheepuff_spawn_egg": (hx("f4f6f8"), hx("9ccbea"), hx("8a96a4")),
    "aerbunny_spawn_egg": (hx("f6f8fb"), hx("f2b8c8"), hx("8a96b0")),
    "moa_spawn_egg": (hx("4f7fc4"), hx("e8b84a"), hx("23406e")),
}
MOA_EGGS = {
    "blue_moa_egg": (hx("6a9ad8"), hx("e8f2ff"), hx("335a94")),
    "white_moa_egg": (hx("f2ecde"), hx("b89868"), hx("a89878")),
    "black_moa_egg": (hx("3a3a44"), hx("8a8a9c"), hx("14141a")),
}
ITEM_NAMES = {
    "phyg_spawn_egg": "Phyg Spawn Egg", "flying_cow_spawn_egg": "Flying Cow Spawn Egg", "sheepuff_spawn_egg": "Sheepuff Spawn Egg",
    "aerbunny_spawn_egg": "Aerbunny Spawn Egg", "moa_spawn_egg": "Moa Spawn Egg",
    "blue_moa_egg": "Blue Moa Egg", "white_moa_egg": "White Moa Egg", "black_moa_egg": "Black Moa Egg",
}
ENTITY_NAMES = {"phyg": "Phyg", "flying_cow": "Flying Cow", "sheepuff": "Sheepuff", "aerbunny": "Aerbunny", "moa": "Moa"}


def items():
    for name, (base, spot, outline) in SPAWN_EGGS.items():
        save(egg_image(base, spot, outline, name, 8, ring=True), os.path.join(ASSETS, "textures", "item", SUB, name + ".png"))
    for name, (base, spot, outline) in MOA_EGGS.items():
        save(egg_image(base, spot, outline, name, 7), os.path.join(ASSETS, "textures", "item", SUB, name + ".png"))
    for name in ITEM_NAMES:
        write(os.path.join(ASSETS, "models", "item", SUB, name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s/%s" % (NS, SUB, name)}})
        write(os.path.join(ASSETS, "items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, SUB, name)}})


# ---------------------------------------------------------------- loot (the 26.3 format: "modifier" on entries)

def drop(item, lo, hi, looting=True):
    mods = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        mods.append({"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
                     "enchantment": "minecraft:looting"})
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": mods}]}


def loot():
    tables = {
        "phyg": [drop("minecraft:porkchop", 1, 3), drop("minecraft:feather", 0, 1)],
        "flying_cow": [drop("minecraft:leather", 0, 2), drop("minecraft:beef", 1, 3)],
        # a sheepuff's wool is dropped by the entity itself (its colour); a sheared one has none
        "sheepuff": [drop("minecraft:mutton", 1, 2)],
        "aerbunny": [drop("minecraft:string", 0, 2)],
        "moa": [drop("minecraft:feather", 2, 3)],
    }
    for name, pools in tables.items():
        write(os.path.join(DATA, "loot_table", "entities", name + ".json"),
              {"type": "minecraft:entity", "pools": pools, "random_sequence": "%s:entities/%s" % (NS, name)})


# ---------------------------------------------------------------- lang

def lang():
    path = os.path.join(ASSETS, "lang", "parts", "aether.json")
    cur = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            cur = json.load(f)
    mine = {"entity.jaymospowers." + k: v for k, v in ENTITY_NAMES.items()}
    mine.update({"item.jaymospowers." + k: v for k, v in ITEM_NAMES.items()})
    for k in mine:
        cur.pop(k, None)
    cur.update(mine)  # one block at the end
    write(path, cur)


def main():
    base = os.path.join(ASSETS, "textures", "entity", SUB)
    phyg().finish(os.path.join(base, "phyg.png"))
    cow().finish(os.path.join(base, "flying_cow.png"))
    for color in WOOL:
        sheepuff(color).finish(os.path.join(base, "sheepuff_%s.png" % color))
    aerbunny().finish(os.path.join(base, "aerbunny.png"))
    for kind in MOA:
        moa(kind).finish(os.path.join(base, "moa_%s.png" % kind))
    items()
    loot()
    lang()
    print("Aether passive mob art and data written.")


if __name__ == "__main__":
    main()
