"""Writes the Aether II hostile mobs' art and data (Powers 0.8.x, phase 2 track H): the entity skins (painted here, vanilla
style, our own designs from behaviour and shape notes: nothing copied from The Aether II or anyone else), their glowing
overlays, the drop, shot and spawn egg item textures with their models and definitions, the four effect icons, the loot
tables, and the names in lang/parts/aether_monsters.json.

    python -I tools/gen_aether_monsters.py

Needs only Pillow. Like gen_aether_mobs.py, the skins are painted onto the boxes the models declare: this script READS
the `part(parent, "name", u, v, x, y, z, w, h, d, ...)` lines in
src/client/java/com/jaymospowers/client/aether/monster/*Model.java, so a model change and a repaint stay in step (rerun
after editing a model). Every texture is seeded from its own name, so a rerun changes nothing.
"""
import json
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether_mobs as base  # the skin painter and its helpers (only imported: it writes nothing until main())
from gen_aether_mobs import Skin, faces, hx, ramp, shade, write, save, egg_image

ROOT = base.ROOT
ASSETS = base.ASSETS
DATA = base.DATA
NS = base.NS
SUB = base.SUB
MODELS = os.path.join(ROOT, "src", "client", "java", "com", "jaymospowers", "client", "aether", "monster")
ENTITY_DIR = os.path.join(ASSETS, "textures", "entity", SUB)
ITEM_DIR = os.path.join(ASSETS, "textures", "item", SUB)


def parts_of(java):
    """{part name: dict(u, v, w, h, d)} from one model file's part(...) lines."""
    out = {}
    with open(os.path.join(MODELS, java), encoding="utf-8") as f:
        for line in f:
            m = base.PART.search(line)
            if m:
                out[m.group(1)] = dict(u=int(m.group(2)), v=int(m.group(3)), w=int(m.group(7)), h=int(m.group(8)), d=int(m.group(9)))
    return out


DARK = hx("1c1c26")
WHITE = hx("ffffff")


class MSkin(Skin):
    """A skin that can also paint a glowing overlay (drawn full-bright over the skin by the renderer)."""

    def __init__(self, name, size, java):
        super().__init__(name, size, parts_of(java))
        self.size = size
        self.glow_im = Image.new("RGBA", size, (0, 0, 0, 0))
        self.gpx = self.glow_im.load()
        self.glows = False

    def glow(self, name, face, x, y, color):
        """One pixel that is painted on the skin and glows."""
        x0, y0, w, h = faces(self.parts[name])[face]
        if 0 <= x < w and 0 <= y < h:
            self.px[x0 + x, y0 + y] = color + (255,)
            self.gpx[x0 + x, y0 + y] = color + (255,)
            self.glows = True

    def glow_rect(self, name, face, x, y, w, h, color):
        for dy in range(h):
            for dx in range(w):
                self.glow(name, face, x + dx, y + dy, color)

    def glow_face(self, name, face, color, chance=1.0):
        x0, y0, w, h = faces(self.parts[name])[face]
        for y in range(h):
            for x in range(w):
                if self.rng.random() < chance:
                    self.glow(name, face, x, y, color)

    def finish(self, path):
        save(self.im, path)
        glow_path = path[:-4] + "_glow.png"
        if self.glows:
            save(self.glow_im, glow_path)
        elif os.path.exists(glow_path):
            os.remove(glow_path)


def streaks(sk, name, color, count, length=(2, 5), only=None, vertical=False):
    """Short lines of one colour over the faces (streaks of fur, veins, cracks)."""
    for face, (x0, y0, w, h) in faces(sk.parts[name]).items():
        if only and face not in only:
            continue
        for _ in range(count):
            x, y = sk.rng.randrange(w), sk.rng.randrange(h)
            for i in range(sk.rng.randint(*length)):
                sk.at(name, face, x, y + i, color) if vertical else sk.at(name, face, x + i, y, color)


# ---------------------------------------------------------------- the skins

def zephyr():
    sk = MSkin("zephyr", (128, 64), "ZephyrModel.java")
    cloud = ramp("eef4fb", "dfe9f6", "cddcef", "bccee6")
    sk.box("body", cloud, bottom=ramp("a9bcdc", "9fb2d4", "b8c8e4"))
    streaks(sk, "body", hx("b5c8e8"), 14, (3, 7), only=["top", "left", "right"])
    streaks(sk, "body", hx("ffffff"), 8, (2, 5), only=["top", "left", "right"])
    sk.box("crest", ramp("c4d4f0", "b0c4e8", "d6e2f6"))
    streaks(sk, "crest", hx("8ea8d8"), 4, (2, 4), only=["top"])
    for n in ("cheek_l", "cheek_r"):
        sk.box(n, ramp("f4cfd8", "ecbfca", "fbdce3"))
    # a face on the front of the body: two round dark eyes
    for x in (3, 9):
        sk.rect("body", "front", x, 4, 2, 3, hx("2c3a66"))
        sk.at("body", "front", x, 4, WHITE)
        sk.at("body", "front", x + 1, 6, hx("55689c"))
    sk.rect("body", "front", 6, 8, 2, 1, hx("d79aa8"))
    for n in ("jaw_top", "jaw_bottom"):
        sk.box(n, ramp("e6b4c0", "dba2b0", "f0c6d0"))
    sk.rect("jaw_top", "bottom", 0, 0, 6, 4, hx("7a4254"))
    sk.rect("jaw_bottom", "top", 0, 0, 6, 4, hx("7a4254"))
    # wings: pale and veined like a dragonfly's, nearly white at the tip
    for n, tip_high in (("wing_l", True), ("wing_r", False)):
        for face, (x0, y0, w, h) in faces(sk.parts[n]).items():
            for y in range(h):
                for x in range(w):
                    dist = (x / max(1, w - 1)) if tip_high else 1.0 - x / max(1, w - 1)
                    c = hx("e6eefc") if dist > 0.55 else hx("d3dff6")
                    if face in ("top", "bottom") and (y % 3 == 1 or x % 5 == 0):
                        c = hx("a9b9e2")
                    sk.px[x0 + x, y0 + y] = c + (255,)
    sk.box("tail_a", ramp("dfe9f6", "cddcef", "eef4fb"))
    sk.box("tail_b", ramp("eef4fb", "ffffff", "e4ecf8"))
    return sk


def tempest():
    sk = MSkin("tempest", (64, 64), "TempestModel.java")
    shell = ramp("353d50", "2e3547", "3f4860", "262c3c")
    sk.box("body", shell, bottom=ramp("1f2432", "262c3c"))
    streaks(sk, "body", hx("55607c"), 8, (2, 4), only=["top", "left", "right"])
    sk.box("head", ramp("3a4358", "313950", "454f68"))
    # eyes glow yellow; the jaws are dark
    for x in (0, 3):
        sk.glow_rect("head", "front", x, 1, 2, 2, hx("f4e45c"))
    sk.rect("head", "front", 1, 4, 3, 1, DARK)
    for n in ("plate_l", "plate_r"):
        sk.box(n, ramp("1f2430", "262c3a", "2d3444"))
        # a jagged bolt of light down each plate
        x0, y0, w, h = faces(sk.parts[n])["top"]
        x = 1
        for y in range(h):
            sk.glow(n, "top", x, y, hx("e8d860") if y % 2 == 0 else hx("a8d0f0"))
            if sk.rng.random() < 0.4:
                x = max(0, min(w - 1, x + sk.rng.choice((-1, 1))))
    for n in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        sk.box(n, ramp("262c3c", "1f2430"))
    sk.box("stinger", ramp("e4b02c", "cc9420", "f0c84c"))
    sk.glow_face("stinger", "back", hx("fff08a"))
    sk.glow_face("stinger", "bottom", hx("ffd84a"), 0.7)
    return sk


def cockatrice():
    sk = MSkin("cockatrice", (128, 64), "CockatriceModel.java")
    feathers = ramp("5a7a3a", "4c6a30", "6a8c44", "3e5a28")
    sk.box("body", feathers, bottom=ramp("cdb870", "bfa860", "d8c488"))
    sk.speckle("body", [hx("2f6a5a"), hx("78a050"), hx("3a8a78")], 26, only=["top", "left", "right", "back"])
    # rows of feathers: darker scallops
    for face in ("left", "right", "back"):
        x0, y0, w, h = faces(sk.parts["body"])[face]
        for y in range(2, h, 3):
            for x in range(w):
                if (x + y) % 3 == 0:
                    sk.at("body", face, x, y, hx("3e5a28"))
    sk.box("neck", feathers, bottom=ramp("cdb870", "bfa860"))
    sk.speckle("neck", [hx("2f6a5a"), hx("78a050")], 8)
    sk.box("head", ramp("6a8c44", "5a7a3a", "78a050"), bottom=ramp("cdb870", "bfa860"))
    for x in (1, 4):
        sk.glow_rect("head", "front", x, 1, 1, 2, hx("f08a1c"))
    sk.at("head", "right", 3, 1, hx("f08a1c"))
    sk.at("head", "left", 3, 1, hx("f08a1c"))
    sk.box("beak", ramp("e8a438", "d08a24", "f2b850"))
    sk.rect("beak", "front", 0, 0, 3, 1, hx("7a4a14"))
    sk.box("crest", ramp("c8362a", "b02a22", "dc4a38"))
    for n in ("spine_a", "spine_b", "spine_c"):
        sk.box(n, ramp("2f5a50", "26483f", "3a6e62"))
        sk.rect(n, "top", 0, 0, 1, 2, hx("8a4aa0"))
    sk.box("tail", feathers, bottom=ramp("cdb870", "bfa860"))
    sk.box("tail_tip", ramp("5a3a8a", "4a2e78", "7050a0"))
    for n in ("wing_l", "wing_r"):
        sk.box(n, ramp("4c6a30", "3e5a28", "5a7a3a"))
        sk.rect(n, "left", 0, 4, 7, 2, hx("5a3a8a"))
        sk.rect(n, "right", 0, 4, 7, 2, hx("5a3a8a"))
    for n in ("leg_l", "leg_r"):
        sk.box(n, ramp("b08a3a", "9a7630", "c29c48", "82662a"))
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            for y in range(1, h, 3):
                sk.rect(n, face, 0, y, w, 1, hx("82662a"))
    for n in ("foot_l", "foot_r"):
        sk.box(n, ramp("a07c34", "8a682a", "b4903e"))
    return sk


def swet(golden):
    name = "golden_swet" if golden else "blue_swet"
    sk = MSkin(name, (64, 64), "SwetModel.java")
    if golden:
        gel, core, rim = ramp("f2d060", "ecc448", "f8de80"), ramp("d89e18", "c88c10", "e6b030"), hx("fff0a0")
        mouth, cheek = hx("7a4a08"), hx("f0a030")
    else:
        gel, core, rim = ramp("78b4f0", "6aa6e8", "8cc4f8"), ramp("3a78d0", "306cc4", "4a8ae0"), hx("d8eeff")
        mouth, cheek = hx("142e66"), hx("5aa8f8")
    # the skin is see-through, thick at the edges; the core inside is solid
    sk.box("shell", gel)
    for face, (x0, y0, w, h) in faces(sk.parts["shell"]).items():
        for y in range(h):
            for x in range(w):
                edge = x in (0, w - 1) or y in (0, h - 1)
                alpha = 205 if edge else 130
                r, g, b, _ = sk.im.getpixel((x0 + x, y0 + y))
                sk.px[x0 + x, y0 + y] = (r, g, b, alpha)
    sk.rect("shell", "front", 1, 1, 3, 1, rim)
    sk.box("core", core)
    # the face on the core: two eyes and a wide little mouth
    for x in (2, 8):
        sk.rect("core", "front", x, 3, 2, 3, DARK)
        sk.at("core", "front", x, 3, WHITE)
    sk.rect("core", "front", 4, 8, 4, 1, mouth)
    sk.at("core", "front", 3, 7, mouth)
    sk.at("core", "front", 8, 7, mouth)
    sk.at("core", "front", 1, 6, cheek)
    sk.at("core", "front", 10, 6, cheek)
    return sk


def aechor_plant():
    sk = MSkin("aechor_plant", (64, 32), "AechorPlantModel.java")
    sk.box("stem", ramp("4a8a3a", "3f7a32", "5a9a46"))
    sk.box("bud", ramp("a03a8c", "8a2e78", "b44c9e", "74246a"), bottom=ramp("5a1e52"))
    # the maw: a dark mouth ringed with pale teeth on the front of the bud
    sk.rect("bud", "front", 1, 1, 4, 3, hx("2a0a24"))
    for x in (1, 3):
        sk.at("bud", "front", x, 1, hx("f2e8d8"))
    for x in (2, 4):
        sk.at("bud", "front", x, 3, hx("f2e8d8"))
    sk.speckle("bud", [hx("d070c0"), hx("c85ab0")], 6, only=["top", "left", "right", "back"])
    sk.box("petal_0", ramp("d66ac0", "c85ab0", "e482d0"))
    sk.rect("petal_0", "top", 0, 1, 5, 1, hx("f2a8e0"))
    sk.patches("petal_0", hx("ffd0f0"), 1, (1, 2), only=["top"])
    for n in ("leaf_l", "leaf_r"):
        sk.box(n, ramp("5aa04a", "4a8a3a", "6ab458"))
        sk.rect(n, "top", 0, 1, 4, 1, hx("3a7a2e"))
    return sk


def carrion_sprout():
    sk = MSkin("carrion_sprout", (64, 32), "CarrionSproutModel.java")
    sk.box("stem", ramp("b4c29c", "a4b48c", "c4d0ac", "94a47c"))
    for y in range(0, 8, 2):
        for face in ("front", "back", "left", "right"):
            sk.rect("stem", face, 0, y, 3, 1, hx("8a9a72"))
    sk.box("bud", ramp("c8b8d4", "b8a6c8", "d8cce4"), bottom=ramp("5a1e30"))
    # fang marks on the bud, a red throat underneath
    sk.at("bud", "front", 1, 2, hx("8a3040"))
    sk.at("bud", "front", 2, 2, hx("8a3040"))
    sk.box("leaf_0", ramp("8a7a9c", "7a6a8c", "9a8aac"))
    sk.rect("leaf_0", "top", 0, 1, 6, 1, hx("5a4a6c"))
    sk.box("tip_0", ramp("9a3446", "8a2c3c", "ac4054"))
    return sk


def skephid():
    sk = MSkin("skephid", (64, 32), "SkephidModel.java")
    sk.box("body", ramp("3a3446", "2f2a3a", "443d52", "262230"), bottom=ramp("5a5470", "4c4660"))
    streaks(sk, "body", hx("8a82a8"), 6, (2, 4), only=["top"])
    sk.speckle("body", [hx("5a5470"), hx("b8b0d4")], 6, only=["top"])
    sk.box("head", ramp("4a4258", "3e364c", "564e66"))
    # pale blue eyes that glow, and a dark mouth line below
    for x in (0, 3):
        sk.glow_rect("head", "front", x, 0, 2, 2, hx("b8e0ff"))
    sk.rect("head", "front", 0, 3, 5, 1, hx("120e18"))
    for n in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        sk.box(n, ramp("2a2634", "34303e"))
        sk.rect(n, "top", 2, 0, 1, 1, hx("9a92b8"))
        sk.rect(n, "top", 4, 0, 1, 1, hx("9a92b8"))
    return sk


def arkenium_taluton():
    sk = MSkin("arkenium_taluton", (128, 64), "ArkeniumTalutonModel.java")
    flesh = ramp("5a4a3c", "4e4034", "665444", "42362c")
    steel = ramp("7a8aa0", "6a7a90", "8a9ab2", "56647a")
    for n in ("torso", "head", "leg_l", "leg_r", "arm_l", "arm_r"):
        sk.box(n, flesh)
        sk.speckle(n, [hx("7a6650"), hx("362c24")], 10)
    for n in ("chest", "shoulder_l", "shoulder_r", "fist_l", "fist_r"):
        sk.box(n, steel)
        sk.outline(n, ["top", "front", "back", "left", "right"], hx("46546a"))
        sk.speckle(n, [hx("aebcd2"), hx("5e6c82")], 5)
    for n in ("spine_a", "spine_b", "spine_c"):
        sk.box(n, ramp("8a9ab2", "7a8aa0", "9aaac2"))
        sk.rect(n, "top", 0, 0, 2, 2, hx("c4d0e2"))
    # a steel cap on the head, orange eyes glowing under the brow, a dark mouth
    sk.rect("head", "top", 0, 0, 6, 6, hx("6a7a90"))
    sk.rect("head", "front", 0, 1, 6, 1, hx("46546a"))
    for x in (1, 4):
        sk.glow_rect("head", "front", x, 2, 1, 2, hx("f08a2c"))
    sk.rect("head", "front", 1, 4, 4, 1, DARK)
    # seams of glowing arkenium between the chest plate and the body
    for x in range(0, 10, 2):
        sk.glow("chest", "front", x, 6, hx("e8802a"))
    for n in ("leg_l", "leg_r"):
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, h - 2, w, 2, hx("56647a"))
    return sk


def gravitite_taluton():
    sk = MSkin("gravitite_taluton", (128, 64), "GravititeTalutonModel.java")
    rock = ramp("8a6aa8", "7a5a98", "9a7ab8", "665088")
    dark = ramp("5a4478", "4e3a6a", "66508a")
    for n in ("core", "head", "arm_l", "arm_r", "leg_l", "leg_r"):
        sk.box(n, dark)
        sk.speckle(n, [hx("8a6aa8"), hx("3e2e58")], 10)
    for n in ("plate_front", "plate_back", "shoulder_l", "shoulder_r"):
        sk.box(n, rock)
        sk.outline(n, ["top", "front", "back", "left", "right"], hx("46345e"))
        streaks(sk, n, hx("b49ad0"), 5, (2, 5))
        streaks(sk, n, hx("4a3866"), 5, (2, 4), vertical=True)
    sk.box("chunk_0", rock)  # the other three chunks share its net
    # the core shows through cracks in the front plate: glowing pink
    x = 6
    for y in range(2, 14):
        sk.glow("plate_front", "front", x, y, hx("f070c0"))
        if sk.rng.random() < 0.5:
            x = max(1, min(10, x + sk.rng.choice((-1, 1))))
            sk.glow("plate_front", "front", x, y, hx("f8a0dc"))
    for x in (1, 4):
        sk.glow_rect("head", "front", x, 2, 2, 2, hx("f8a0dc"))
    sk.rect("head", "front", 1, 5, 5, 1, hx("2a1a3e"))
    for n in ("chunk_0",):
        sk.glow_face(n, "bottom", hx("f070c0"), 0.5)
    return sk


def bladeshroom_hunter():
    sk = MSkin("bladeshroom_hunter", (128, 64), "BladeshroomHunterModel.java")
    body = ramp("7a2e2a", "6a2824", "8a3834", "5a201c")
    sk.box("body", body, bottom=ramp("e4d4b4", "d8c8a4", "f0e2c4"))
    sk.speckle("body", [hx("9a4a3e"), hx("4a1816")], 18, only=["top", "left", "right", "back"])
    sk.box("head", ramp("8a3834", "7a2e2a", "9a463e"), bottom=ramp("e4d4b4"))
    for x in (0, 4):
        sk.glow_rect("head", "front", x, 1, 2, 2, hx("f4d24a"))
    sk.rect("head", "front", 1, 4, 4, 1, hx("1c0a0a"))
    for n in ("blade_l", "blade_r"):
        sk.box(n, ramp("e8eaf0", "d4d8e2", "f6f8fc"))
        sk.outline(n, ["top", "bottom"], hx("a4aab8"))
    for n in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        sk.box(n, ramp("e4d4b4", "d0c09c", "efe0c0"))
        for face in ("front", "back", "left", "right"):
            x0, y0, w, h = faces(sk.parts[n])[face]
            sk.rect(n, face, 0, h - 2, w, 2, hx("8a3834"))
    for n in ("stem_a", "stem_b"):
        sk.box(n, ramp("e4d4b4", "d8c8a4", "efe0c0"))
    for n in ("cap_a", "cap_b"):
        sk.box(n, ramp("c8482c", "b83c24", "d85a38", "a0301c"), bottom=ramp("f0e2c4", "e4d4b4"))
        sk.patches(n, hx("f6ecd8"), 3, (1, 2), only=["top"])
        # gills under the cap
        x0, y0, w, h = faces(sk.parts[n])["bottom"]
        for x in range(0, w, 2):
            for y in range(h):
                sk.at(n, "bottom", x, y, hx("d4c098"))
    return sk


# ---------------------------------------------------------------- item sprites (16x16, drawn from text)

def sprite(rows, palette, size=16):
    """An image from rows of characters: '.' is clear, anything else is looked up in the palette ((r, g, b) or (r, g, b, a))."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        assert len(row) == size, (y, row)
        for x, ch in enumerate(row):
            if ch != ".":
                c = palette[ch]
                px[x, y] = tuple(c) + ((255,) if len(c) == 3 else ())
    return im


SPRITES = {}

SPRITES["swet_gel"] = ([
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "...ooBBBBBBoo...",
    "..oBBhhBBBBBBo..",
    "..oBhhBBBBBBBo..",
    ".oBBhBBBBBBBBBo.",
    ".oBBBBBBBBBBDBo.",
    ".oBBBBBBBBBBDDo.",
    ".oBBBBBBBBBDDDo.",
    "..oBBBBBBBDDDo..",
    "..ooBBBBBDDDoo..",
    "....oooooooooo..",
    "................",
    "................",
], {"o": hx("2c5aa8"), "B": hx("5a9ae8"), "h": hx("d4ebff"), "D": hx("3a74c8")})

SPRITES["swet_sugar"] = ([
    "................",
    "................",
    "......oooo......",
    ".....owWWso.....",
    ".....oWWWso.....",
    "..oooossssoooo..",
    ".owWWsoooowWWso.",
    ".oWWWsoyyoWWWso.",
    ".ossssoyyossso..",
    "..ooooowWWsooo..",
    "......oWWWso....",
    "......ossso.....",
    ".......ooo......",
    "................",
    "................",
    "................",
], {"o": hx("a8884a"), "W": hx("fff4cc"), "w": hx("ffffff"), "s": hx("e8d49a"), "y": hx("f8e8b0")})

SPRITES["aechor_petal"] = ([
    "................",
    "................",
    ".........oo.....",
    "........oPPo....",
    ".......oPpPPo...",
    "......oPPpPPPo..",
    ".....oPPPpPPPPo.",
    "....oPPPPpPPPPo.",
    "...oPPPPPpPPPo..",
    "...oPPPPPpPPDo..",
    "..oPPPPPPpPDDo..",
    "..oPPPPPDDDDo...",
    "..oPPPDDDDoo....",
    "...oDDDooo......",
    "....ooo.........",
    "................",
], {"o": hx("5a1e52"), "P": hx("c85ab0"), "p": hx("e89ad8"), "D": hx("9a3a88")})

SPRITES["cockatrice_feather"] = ([
    "..............oo",
    ".............ovo",
    "............ovvo",
    "...........ovGvo",
    "..........ovGGvo",
    ".........ovGGGo.",
    "........ovGGgGo.",
    ".......ovGGgGGo.",
    "......ovGGgGGo..",
    ".....ovGGgGGo...",
    "....ovGGgGoo....",
    "...ovGGgoo......",
    "..osGGoo........",
    ".ossoo..........",
    "oso.............",
    "oo..............",
], {"o": hx("26483f"), "G": hx("5a8a3a"), "g": hx("8ab85a"), "v": hx("8a4aa0"), "s": hx("c8c090")})

SPRITES["cloudtwine"] = ([
    "................",
    "................",
    "....oooo........",
    "...oWWwWoo......",
    "..oWwooowWo.....",
    "..oWo...oWo.....",
    "..oWwoooWWo.....",
    "...oWWwWwoo.....",
    "....ooWwWWoo....",
    "......oowWWWo...",
    ".......oooWwWo..",
    ".........oWowWo.",
    ".........oWwWWo.",
    "..........oWWo..",
    "...........oo...",
    "................",
], {"o": hx("8a94a8"), "W": hx("f0f4fa"), "w": hx("c4ccda")})

SPRITES["zephyr_webbing_ball"] = ([
    "................",
    "................",
    "....oooooooo....",
    "...oWwWoWwWwo...",
    "..oWowWWwWowWo..",
    ".oWwWowWwWowWwo.",
    ".oWWoWwWWwWoWWo.",
    ".owWwWWoWWwWwWo.",
    ".oWowWWoWWwWoWo.",
    ".oWWwWoWwWWwWWo.",
    ".owWoWwWWoWwWwo.",
    ".oWwWowWWwoWWWo.",
    "..oWWwWowWwWWo..",
    "...oWwWoWwWwo...",
    "....oooooooo....",
    "................",
], {"o": hx("8aa0cc"), "W": hx("f4f8ff"), "w": hx("cddcf2")})

SPRITES["skephid_webbing_ball"] = ([
    "................",
    "................",
    "....oooooooo....",
    "...oDdDoDdDdo...",
    "..oDodDDdDodDo..",
    ".oDdDodDdDodDdo.",
    ".oDDoDdDDdDoDDo.",
    ".odDdDDoDDdDdDo.",
    ".oDodDDoDDdDoDo.",
    ".oDDdDoDdDDdDDo.",
    ".odDoDdDDoDdDdo.",
    ".oDdDodDDdoDDDo.",
    "..oDDdDodDdDDo..",
    "...oDdDoDdDdo...",
    "....oooooooo....",
    "................",
], {"o": hx("1a1624"), "D": hx("4a4262"), "d": hx("9a92b8")})

SPRITES["thunderball"] = ([
    "................",
    ".....o..o.......",
    "....oYo.oY......",
    "...o.oooooo.oo..",
    "..oYoWWWWWWoYo..",
    "...oWWYYYYWWo...",
    "..oWWYYwwYYWWo..",
    "o.oWYYwwwwYYWo.o",
    "oYoWYYwwwwYYWoYo",
    "..oWWYYwwYYWWo..",
    "...oWWYYYYWWo...",
    "..oYoWWWWWWoYo..",
    "...o.oooooo.o...",
    "....oYo..oYo....",
    ".....o....o.....",
    "................",
], {"o": hx("4a6ac0"), "W": hx("b8d8ff"), "Y": hx("f4e45c"), "w": hx("ffffff")})

SPRITES["venomous_dart"] = ([
    "................",
    "..............oo",
    ".............ogo",
    "............ogGo",
    "...........oGGo.",
    "..........oGGo..",
    ".........oGGo...",
    "....vv..oGGo....",
    "...vVvvoGGo.....",
    "..vVVvvGGo......",
    ".vVVvvooo.......",
    "..vvvv..........",
    "...vv...........",
    "................",
    "................",
    "................",
], {"o": hx("2a4a1a"), "G": hx("78b83a"), "g": hx("c8f08a"), "V": hx("5a8a3a"), "v": hx("3e6a2a")})

SPRITES["toxic_dart"] = ([
    "................",
    "................",
    "..............o.",
    ".............oP.",
    "............oPo.",
    "...........oPo..",
    "..........oPo...",
    ".........oPo....",
    "........oPo.....",
    ".......oPo......",
    "......oPo.......",
    ".....oPo........",
    "....oPo.........",
    "...opo..........",
    "..oo............",
    "................",
], {"o": hx("3e1a4a"), "P": hx("a04ac8"), "p": hx("d890f0")})

SPRITES["gravitite_debris_shot"] = ([
    "................",
    "................",
    ".....oooo.......",
    "....oRRrRoo.....",
    "...oRrRRRRRoo...",
    "..oRRRrPPRRRRo..",
    "..oRrRPPPPRrRo..",
    ".oRRRRPpPPRRRDo.",
    ".oRrRPPpPPRRRDo.",
    ".oRRRRPPPRRrDDo.",
    "..oRRrRRRRRDDo..",
    "..oDRRRrRRDDo...",
    "...oDDRRDDDo....",
    "....ooDDooo.....",
    "................",
    "................",
], {"o": hx("2e1e44"), "R": hx("8a6aa8"), "r": hx("a888c8"), "D": hx("5a4478"), "P": hx("f070c0"), "p": hx("ffc0e8")})

# 18x18 effect icons: a dark rounded tile with a symbol, like vanilla's
EFFECTS = {}
EFFECTS["webbed"] = ([
    "..................",
    "..oooooooooooooo..",
    ".oDDDDDDDDDDDDDDo.",
    ".oDWDDDDWDDDDWDDo.",
    ".oDDWDDDWDDDWDDDo.",
    ".oDDDWDDWDDWDDDDo.",
    ".oDDDDWDWDWDDDDDo.",
    ".oDWWWWWWWWWWWWDo.",
    ".oDDDDWDWDWDDDDDo.",
    ".oDDDWDDWDDWDDDDo.",
    ".oDDWDDDWDDDWDDDo.",
    ".oDWDDDDWDDDDWDDo.",
    ".oDDDDDDDDDDDDDDo.",
    "..oooooooooooooo..",
    "..................",
    "..................",
    "..................",
    "..................",
], {"o": hx("1a1a22"), "D": hx("3a3a48"), "W": hx("e4e8f4")})
EFFECTS["venom"] = ([
    "..................",
    "..oooooooooooooo..",
    ".oDDDDDDDDDDDDDDo.",
    ".oDDDDDDGGDDDDDDo.",
    ".oDDDDDGGGGDDDDDo.",
    ".oDDDDGGhGGGDDDDo.",
    ".oDDDGGhGGGGGDDDo.",
    ".oDDDGGhGGGGGDDDo.",
    ".oDDDGGGGGGGGDDDo.",
    ".oDDDGGGGGGGGDDDo.",
    ".oDDDDGGGGGGDDDDo.",
    ".oDDDDDGGGGDDDDDo.",
    ".oDDDDDDDDDDDDDDo.",
    "..oooooooooooooo..",
    "..................",
    "..................",
    "..................",
    "..................",
], {"o": hx("162214"), "D": hx("2a4222"), "G": hx("8fd04a"), "h": hx("d8f8a0")})
EFFECTS["toxin"] = ([
    "..................",
    "..oooooooooooooo..",
    ".oDDDDDDDDDDDDDDo.",
    ".oDDDDDDDDDDDDDDo.",
    ".oDDDDDPPPDDDDDDo.",
    ".oDDDDPPhPPDDDDDo.",
    ".oDDDPPhPPPPPDDDo.",
    ".oDDDPPPPPPPPDDDo.",
    ".oDDDPPPPPPPPDDDo.",
    ".oDDDDPPPPPPDDDDo.",
    ".oDDDDDPPPPDDDPDo.",
    ".oDDDDDDDDDDDPPDo.",
    ".oDDDDDDDDDDDDPDo.",
    "..oooooooooooooo..",
    "..................",
    "..................",
    "..................",
    "..................",
], {"o": hx("24122e"), "D": hx("40224e"), "P": hx("b04ad0"), "h": hx("e8a0f8")})
EFFECTS["electric_shock"] = ([
    "..................",
    "..oooooooooooooo..",
    ".oDDDDDDDDDDDDDDo.",
    ".oDDDDDDDDYYYDDDo.",
    ".oDDDDDDDYYYDDDDo.",
    ".oDDDDDDYYYDDDDDo.",
    ".oDDDDDYYYYYYDDDo.",
    ".oDDDDDDDDYYYDDDo.",
    ".oDDDDDDDYYDDDDDo.",
    ".oDDDDDDYYDDDDDDo.",
    ".oDDDDDYYDDDDDDDo.",
    ".oDDDDDYDDDDDDDDo.",
    ".oDDDDDDDDDDDDDDo.",
    "..oooooooooooooo..",
    "..................",
    "..................",
    "..................",
    "..................",
], {"o": hx("2a2810"), "D": hx("4a4620"), "Y": hx("f4e45c")})

# spawn eggs: (base, spots, outline)
EGGS = {
    "zephyr_spawn_egg": (hx("c6d6f0"), hx("8aa0d0"), hx("6a80b0")),
    "tempest_spawn_egg": (hx("2e3446"), hx("f0e060"), hx("141824")),
    "cockatrice_spawn_egg": (hx("5a7a3a"), hx("e0902a"), hx("2e4a1e")),
    "blue_swet_spawn_egg": (hx("5a9ae8"), hx("e8f4ff"), hx("2c5aa8")),
    "golden_swet_spawn_egg": (hx("e8c040"), hx("fff6c0"), hx("a07818")),
    "aechor_plant_spawn_egg": (hx("a03a8c"), hx("4a8a3a"), hx("5a1e52")),
    "carrion_sprout_spawn_egg": (hx("a8b890"), hx("8a3040"), hx("5a6a48")),
    "skephid_spawn_egg": (hx("3a3446"), hx("c0d8ff"), hx("14101c")),
    "arkenium_taluton_spawn_egg": (hx("7a8aa0"), hx("e0802a"), hx("3e4a5e")),
    "gravitite_taluton_spawn_egg": (hx("8a6aa8"), hx("f070c0"), hx("3e2e58")),
    "bladeshroom_hunter_spawn_egg": (hx("a8402a"), hx("f0e8d0"), hx("5a1e14")),
}

MOB_NAMES = {
    "zephyr": "Zephyr", "tempest": "Tempest", "cockatrice": "Cockatrice", "blue_swet": "Blue Swet", "golden_swet": "Golden Swet",
    "aechor_plant": "Aechor Plant", "carrion_sprout": "Carrion Sprout", "skephid": "Skephid", "arkenium_taluton": "Arkenium Taluton",
    "gravitite_taluton": "Gravitite Taluton", "bladeshroom_hunter": "Bladeshroom Hunter",
    "zephyr_webbing_ball": "Zephyr Webbing Ball", "skephid_webbing_ball": "Skephid Webbing Ball", "thunderball": "Thunderball",
    "venomous_dart": "Venomous Dart", "toxic_dart": "Toxic Dart", "gravitite_debris_shot": "Gravitite Debris Shot",
}
ITEM_NAMES = {
    "swet_gel": "Swet Gel", "swet_sugar": "Swet Sugar", "aechor_petal": "Aechor Petal", "cockatrice_feather": "Cockatrice Feather",
    "cloudtwine": "Cloudtwine",
}
ITEM_NAMES.update({k: v for k, v in MOB_NAMES.items() if k.endswith(("_ball", "_dart", "_shot")) or k == "thunderball"})
ITEM_NAMES.update({k: MOB_NAMES[k[:-len("_spawn_egg")]] + " Spawn Egg" for k in EGGS})
ENTITY_NAMES = dict(MOB_NAMES)
EFFECT_NAMES = {"webbed": "Webbed", "venom": "Venom", "toxin": "Toxin", "electric_shock": "Electric Shock"}


def items():
    for name, (rows, palette) in SPRITES.items():
        save(sprite(rows, palette), os.path.join(ITEM_DIR, name + ".png"))
    for name, (base_c, spot, outline) in EGGS.items():
        save(egg_image(base_c, spot, outline, name, 8, ring=True), os.path.join(ITEM_DIR, name + ".png"))
    for name in ITEM_NAMES:
        write(os.path.join(ASSETS, "models", "item", SUB, name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": "%s:item/%s/%s" % (NS, SUB, name)}})
        write(os.path.join(ASSETS, "items", name + ".json"),
              {"model": {"type": "minecraft:model", "model": "%s:item/%s/%s" % (NS, SUB, name)}})
    for name, (rows, palette) in EFFECTS.items():
        save(sprite(rows, palette, 18), os.path.join(ASSETS, "textures", "mob_effect", name + ".png"))


# ---------------------------------------------------------------- loot (the 26.3 format: "modifier" on entries)

def looting_bonus(per_level=(0.0, 1.0)):
    return {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": per_level[0], "max": per_level[1]},
            "enchantment": "minecraft:looting"}


def drop(item, lo, hi, looting=True, only_player=False, chance=None, per_level=(0.0, 1.0)):
    mods = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        mods.append(looting_bonus(per_level))
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "modifier": mods}]}
    conditions = []
    if only_player:
        conditions.append({"condition": "minecraft:killed_by_player"})
    if chance:
        conditions.append({"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                           "unenchanted_chance": chance[0], "enchanted_chance": {"type": "minecraft:linear", "base": chance[1], "per_level_above_first": chance[0]}})
    if conditions:
        pool["conditions"] = conditions
    return pool


def loot():
    tables = {
        # always 1-3 cold aercloud; a blue one 11.1 percent, and 11.1 points more per level of Looting
        "zephyr": [drop(NS + ":cold_aercloud", 1, 3, looting=False), drop(NS + ":blue_aercloud", 1, 1, looting=False, chance=(0.111, 0.222))],
        "tempest": [drop(NS + ":storm_aercloud", 1, 3, looting=False)],
        "cockatrice": [drop(NS + ":cockatrice_feather", 1, 3)],
        "blue_swet": [drop(NS + ":swet_gel", 1, 3), drop(NS + ":swet_sugar", 0, 1)],
        "golden_swet": [drop(NS + ":swet_gel", 1, 3), drop(NS + ":swet_sugar", 2, 3)],
        "aechor_plant": [drop(NS + ":aechor_petal", 1, 3, only_player=True)],
        "carrion_sprout": [drop(NS + ":wyndberry", 1, 1, looting=False, only_player=True)],
        "skephid": [drop(NS + ":cloudtwine", 1, 2, per_level=(1.0, 1.0))],
        "arkenium_taluton": [drop(NS + ":holystone", 0, 2)],
        "gravitite_taluton": [drop(NS + ":holystone", 0, 2)],
        "bladeshroom_hunter": [],
    }
    for name, pools in tables.items():
        write(os.path.join(DATA, "loot_table", "entities", name + ".json"),
              {"type": "minecraft:entity", "pools": pools, "random_sequence": "%s:entities/%s" % (NS, name)})


# ---------------------------------------------------------------- lang

def lang():
    path = os.path.join(ASSETS, "lang", "parts", "aether_monsters.json")
    mine = {}
    mine.update({"entity.%s.%s" % (NS, k): v for k, v in ENTITY_NAMES.items()})
    mine.update({"item.%s.%s" % (NS, k): v for k, v in ITEM_NAMES.items()})
    mine.update({"effect.%s.%s" % (NS, k): v for k, v in EFFECT_NAMES.items()})
    mine["message.jaymospowers.aether.plant_tool"] = "Only a hoe, an axe or shears will cut it."
    write(path, mine)


def main():
    skins = [("zephyr", zephyr()), ("tempest", tempest()), ("cockatrice", cockatrice()), ("blue_swet", swet(False)), ("golden_swet", swet(True)),
             ("aechor_plant", aechor_plant()), ("carrion_sprout", carrion_sprout()), ("skephid", skephid()),
             ("arkenium_taluton", arkenium_taluton()), ("gravitite_taluton", gravitite_taluton()), ("bladeshroom_hunter", bladeshroom_hunter())]
    for name, sk in skins:
        sk.finish(os.path.join(ENTITY_DIR, name + ".png"))
    items()
    loot()
    lang()
    print("Aether hostile mob art and data written.")


if __name__ == "__main__":
    main()
