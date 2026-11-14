"""Paints the skins for the Aether mobs whose shapes come from The Aether II's model code (LGPL, see the file headers
under src/client/java/com/jaymospowers/client/aether). The mod's textures are All Rights Reserved and are NOT used or
read here: every pixel is painted by this script onto the boxes the Java models declare.

    python -I tools/gen_aether2_skins.py            writes textures/entity/aether/*.png
    python -I tools/gen_aether2_skins.py --preview DIR   also renders each mob to DIR (needs numpy)

Needs Pillow. Each skin is seeded from its own name, so a rerun changes nothing.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "aether2"))
import a2parse  # noqa: E402
import a2paint  # noqa: E402
from a2paint import hx, mix, ramp_from, side_eyes, front_eyes, tip, plume, plumes, clear_except, fan, shape  # noqa: E402

ROOT = os.path.dirname(HERE)
MODELS = os.path.join(ROOT, "src", "client", "java", "com", "jaymospowers", "client", "aether")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "aether")

DYES = dict(white="f4f4f0", light_gray="a8a8a4", gray="5a5e62", black="26262c", brown="7a5234", red="b03a30", orange="e8862a",
            yellow="f0cc3c", lime="86c832", green="587a26", cyan="2a94a0", light_blue="62b8e4", blue="4654b4", purple="8a40b8",
            magenta="c456bc", pink="f29ab6")

SKINS = {}   # name -> (model file, painter function, hidden-in-preview parts)


def skin(name, java, hide=()):
    def deco(fn):
        SKINS[name] = (java, fn, hide)
        return fn
    return deco


def model(java):
    return a2parse.parse(os.path.join(MODELS, java))


def box(m, part, i=0):
    return m["parts"][part]["boxes"][i]


# ---------------------------------------------------------------- moa

MOA = dict(blue=("4f86d2", "e9f2ff", "e2b65a"), white=("eeeae0", "b9d3ee", "d99a4a"), black=("34343e", "8d6ad0", "9a9aa8"))


def moa(kind):
    feather, accent, keratin = MOA[kind]

    def paint(m, seed):
        f = ramp_from(feather, dark=0.6, light=1.18); acc = ramp_from(accent, dark=0.7, light=1.1)
        k = ramp_from(keratin, dark=0.55, light=1.15); mouth = ramp_from("7a2f3a"); teeth = ramp_from("efe9d8", dark=0.8, light=1.05)
        leather = ramp_from("7a4a2c"); cloth = ramp_from("b8392f")
        p = a2paint.paint(m, seed, [
            ("stirrup", leather, "smooth"), ("handle", leather, "horn"), ("rear_r1", cloth, "smooth"), ("moa_saddle", leather, "smooth"),
            ("jaw_teeth", teeth, "smooth"), ("beak_int", mouth, "smooth"),
            ("beak|jaw|head_front", k, "horn"), ("toe|foot|leg_\\w+_[23]", k, "scale"),
            ("feather", f, "plume"), ("brow", ramp_from(mix(hx(feather), (0, 0, 0), 0.25)), "feather"),
            ("", f, "feather")])
        for name in m["order"]:
            if "feather" in name:
                for b in m["parts"][name]["boxes"]:
                    a2paint.plume(p, b, f, acc)
        # the saddle's blanket trim
        sb = box(m, "moa_saddle", 0)
        for face in ("west", "east", "north", "south"):
            tip(p, sb, face, cloth, "y", "hi", 1)
        side_eyes(p, box(m, "head_main"), 2, 3, 2, 2, white=(250, 238, 190), pupil=(24, 20, 30))
        return p
    return paint


for _k in MOA:
    skin("moa_" + _k, "mob/MoaModel.java", hide=())(moa(_k))


# ---------------------------------------------------------------- sheepuff

def sheepuff(color):
    def paint(m, seed):
        wool = ramp_from(DYES[color], dark=0.72, light=1.1); sk = ramp_from("6a5560", dark=0.6); nose = ramp_from("e3a7a0")
        p = a2paint.paint(m, seed, [("wool|crest|tail", wool, "wool", 0.8), ("ear", sk, "smooth"), ("", sk, "plain", 0.8)])
        head = box(m, "head", 0)
        front_eyes(p, head, 0, 2, 2, 2)
        snout = box(m, "head", 1)
        p.rect(snout, "north", 1, 1, 2, 1, nose[2])
        for leg in ("leg_front_right", "leg_front_left", "leg_back_left", "leg_back_right"):
            for face in ("west", "east", "north", "south"):
                tip(p, box(m, leg), face, ramp_from("2a2630"), "y", "hi", 2)
        return p
    return paint


for _c in DYES:
    skin("sheepuff_" + _c, "mob/SheepuffModel.java")(sheepuff(_c))


# ---------------------------------------------------------------- cockatrice

def cockatrice(glow):
    def paint(m, seed):
        hide_ = ramp_from("3d5a44", dark=0.55, light=1.2); belly = ramp_from("8a9a6a"); k = ramp_from("c9b27a", dark=0.5)
        f = ramp_from("2f4a4e", dark=0.55); lit = ramp_from("b35ce0", dark=0.7, light=1.3); claw = ramp_from("e8e2d0", dark=0.6, light=1.05)
        p = a2paint.paint(m, seed, [("talon", claw, "horn"), ("jaw", k, "horn"), ("foot|hand|leg_\\w+_2|leg_\\w+_3", k, "scale"),
                                    ("spines", f, "plain"), ("", hide_, "feather")])
        plumes(p, m, "feather", f, lit, acc_from=0.45)
        plumes(p, m, "spines", f, lit, acc_from=0.6)
        head = box(m, "head")
        side_eyes(p, head, 2, 2, 2, 2, white=(255, 210, 90), pupil=(40, 10, 10))
        if glow:
            clear_except(p, m, "emissive")
            # only the lit part of each feather glows
            for y in range(p.im.height):
                for x in range(p.im.width):
                    c = p.px[x, y]
                    if c[3] and not (c[0] > c[1] + 20 and c[2] > c[1] + 20):
                        p.px[x, y] = (0, 0, 0, 0)
            side_eyes(p, head, 2, 2, 2, 2, white=(255, 210, 90), pupil=(255, 150, 60))
        return p
    return paint


skin("cockatrice", "monster/CockatriceModel.java")(cockatrice(False))
skin("cockatrice_glow", "monster/CockatriceModel.java")(cockatrice(True))


# ---------------------------------------------------------------- zephyr

@skin("zephyr", "monster/ZephyrModel.java")
def zephyr(m, seed):
    cloud = ramp_from("eef3f8", dark=0.74, light=1.04, cool=(90, 120, 170)); under = ramp_from("c9d6e6", dark=0.75, light=1.05, cool=(80, 110, 160))
    mouth = ramp_from("5a6f96"); cheek = ramp_from("f3c9d2", dark=0.8, light=1.05)
    p = a2paint.paint(m, seed, [("mouth", mouth, "smooth"), ("cheek", cloud, "wool", 0.6), ("bottom", under, "wool", 0.6), ("", cloud, "wool", 0.6)])
    plumes(p, m, "wisp", cloud, under, acc_from=0.6, taper_from=0.3, blunt=0.15)
    body = box(m, "body")
    front_eyes(p, body, 2, 2, 2, 3, white=(40, 52, 84), pupil=(20, 26, 46))
    for part in ("cheek_left", "cheek_right"):
        b = box(m, part)
        p.rect(b, "north", 1, 2, 2, 2, cheek[2])
    return p


# ---------------------------------------------------------------- phyg, flying cow

def boxes(m, word):
    return [b for n in m["order"] if word in n for b in m["parts"][n]["boxes"]]


def hooves(p, m, word, rmp, n=2):
    for b in boxes(m, word):
        for face in ("west", "east", "north", "south"):
            tip(p, b, face, rmp, "y", "hi", n)


SADDLE = [("saddle", ramp_from("8a5230"), "smooth")]


@skin("phyg", "mob/PhygModel.java")
def phyg(m, seed):
    pink = ramp_from("eaa3a8", dark=0.7, light=1.1, cool=(120, 60, 90)); wing = ramp_from("f6f1e6", dark=0.78, light=1.04)
    tusk = ramp_from("f2ead2", dark=0.75, light=1.04); dark = ramp_from("a8646c")
    p = a2paint.paint(m, seed, SADDLE + [("wing", wing, "feather"), ("tusk", tusk, "smooth"), ("spines|tail_2", dark, "plain"), ("", pink, "plain", 0.7)])
    plumes(p, m, "feather_", wing, ramp_from("cfdcf0"))
    for b in boxes(m, "spines") + boxes(m, "tail_2"):
        plume(p, b, dark, taper_from=0.2)
    for b in boxes(m, "tusk"):
        plume(p, b, tusk, taper_from=0.0, blunt=0.4)
    head = box(m, "head", 1)
    front_eyes(p, head, 1, 2, 2, 2)
    snout = box(m, "head", 0)
    p.rect(snout, "north", 1, 1, 1, 1, dark[1]); p.rect(snout, "north", 3, 1, 1, 1, dark[1])
    hooves(p, m, "leg_", ramp_from("6a4a4a"))
    return p


@skin("flying_cow", "mob/FlyingCowModel.java")
def flying_cow(m, seed):
    hide_ = ramp_from("f1ede4", dark=0.75, light=1.05); patch = ramp_from("54a0c8", dark=0.65); wing = ramp_from("f6f1e6", dark=0.78, light=1.04)
    nose = ramp_from("e8b0b4"); horn = ramp_from("d8d0b8", dark=0.7)
    p = a2paint.paint(m, seed, SADDLE + [("wing", wing, "feather"), ("", hide_, "plain", 0.6)])
    # blue patches, the same blotch on both flanks
    for name in ("body", "head", "leg_front_left", "leg_back_right"):
        for b in m["parts"][name]["boxes"][:1]:
            for face, (u, v, fw, fh) in a2parse.faces(b).items():
                for j in range(fh):
                    for i in range(fw):
                        if p.n(u + i, v + j, 5) > 0.25 and p.px[u + i, v + j][3]:
                            old = p.px[u + i, v + j]; lum = (old[0] + old[1] + old[2]) / 765.0
                            p.put(u + i, v + j, patch[max(0, min(4, int(lum * 5.5) - 1))])
    plumes(p, m, "feather_", wing, ramp_from("cfdcf0"))
    for b in m["parts"]["wing_left"]["boxes"][1:] + m["parts"]["wing_right"]["boxes"][1:]:
        plume(p, b, wing)
    for b in boxes(m, "ear_"):
        plume(p, b, hide_, taper_from=0.3, blunt=0.5)
    for b in boxes(m, "tail_r1"):
        plume(p, b, hide_, patch, acc_from=0.75, taper_from=0.0, blunt=0.8)
    hb = m["parts"]["head"]["boxes"]
    front_eyes(p, hb[0], 1, 2, 2, 2)
    p.box(hb[1], nose, "smooth")
    p.rect(hb[1], "north", 1, 1, 1, 1, nose[0]); p.rect(hb[1], "north", 4, 1, 1, 1, nose[0])
    for b in hb[3:]:
        plume(p, b, horn, taper_from=0.1, blunt=0.3)
    hooves(p, m, "leg_", ramp_from("4a4046"))
    return p


# ---------------------------------------------------------------- aerbunny

@skin("aerbunny", "mob/AerbunnyModel.java")
def aerbunny(m, seed):
    fur = ramp_from("f4f0ea", dark=0.76, light=1.04); puff = ramp_from("eaf4fb", dark=0.84, light=1.04, cool=(110, 150, 200)); ear = ramp_from("f0b8c0")
    p = a2paint.paint(m, seed, [("cloudpuff", puff, "wool", 0.6), ("", fur, "plain", 0.6)])
    for b in boxes(m, "whisker"):
        shape(p, b, lambda pt, lo, size, x, y: (150, 150, 158) if int(pt[1] - lo[1]) == 1 else None)
    for b in boxes(m, "ear_"):
        p.rect(b, "north", 0, 1, 1, 2, ear[2])
    head = box(m, "head", 0)
    front_eyes(p, head, 0, 1, 1, 1, white=(150, 40, 60), pupil=(150, 40, 60))
    p.box(box(m, "head", 1), ear, "smooth")
    return p


# ---------------------------------------------------------------- kirrid

ZONE_HIDE = dict(highfields="8a6e58", magnetic="6f7486", arctic="dfe4ea")
KIRRID_WOOL = dict(DYES, undyed="e8dfc8")


def kirrid(zone, color):
    def paint(m, seed):
        hide_ = ramp_from(ZONE_HIDE[zone], dark=0.62); wool = ramp_from(KIRRID_WOOL[color], dark=0.72, light=1.1)
        plate = ramp_from("d9cfb4", dark=0.6, light=1.08); hoof = ramp_from("2e2a30")
        p = a2paint.paint(m, seed, [("wool", wool, "wool", 0.8), ("plate|horn", plate, "horn"), ("ear", hide_, "smooth"), ("", hide_, "fur", 0.8)])
        for b in boxes(m, "horn_"):
            if min(b["w"], b["h"], b["d"]) == 0:
                plume(p, b, plate, taper_from=0.2)
        head = box(m, "head_main_r1")
        side_eyes(p, head, 1, 3, 2, 2, white=(232, 196, 96), pupil=(30, 24, 20))
        tip(p, head, "north", ramp_from("3a3038"), "y", "hi", 2)
        hooves(p, m, "_2", hoof)
        return p
    return paint


for _z in ZONE_HIDE:
    for _c in KIRRID_WOOL:
        skin("kirrid_%s_%s" % (_z, _c), "mob/%sKirridModel.java" % _z.capitalize())(kirrid(_z, _c))


# ---------------------------------------------------------------- burrukai, taegore

ZONE_FUR = dict(highfields=("7a5a3e", "c8b48a"), magnetic=("5a6278", "a8b4c8"), arctic=("e4e8ee", "9ab4d0"))


def burrukai(zone):
    def paint(m, seed):
        fur_c, plate_c = ZONE_FUR[zone]
        fur = ramp_from(fur_c, dark=0.58); plate = ramp_from(plate_c, dark=0.6, light=1.1); antler = ramp_from("e6dcc0", dark=0.6, light=1.05)
        p = a2paint.paint(m, seed, [("antler", antler, "horn"), ("plate", plate, "horn"), ("mouth", ramp_from("3a2a2e"), "smooth"),
                                    ("ear", fur, "smooth"), ("lower", ramp_from(mix(hx(fur_c), (0, 0, 0), 0.3)), "fur", 0.8), ("", fur, "fur", 0.9)])
        def antlerf(pt, lo, size, x, y):
            a = (abs(pt[0]) - 5.0) / 10.0                 # 0 at the skull, 1 at the tip
            up = (lo[1] + size[1] - pt[1]) / size[1]      # 0 at the bottom edge, 1 at the top
            beam = abs(up - (0.15 + a * 0.35)) < 0.13
            tine = any(abs(a - ta) < 0.07 and (0.15 + ta * 0.35) < up < th for ta, th in ((0.3, 0.75), (0.6, 0.95), (0.9, 0.9)))
            if not (beam or tine):
                return None
            return antler[max(0, min(4, 2 + (1 if up > 0.6 else 0) - (1 if p.n(x, y, 2) > 0.4 else 0)))]
        for b in boxes(m, "antler"):
            shape(p, b, antlerf)
        side_eyes(p, box(m, "head_main"), 1, 1, 2, 2, white=(240, 220, 150), pupil=(30, 20, 20))
        hooves(p, m, "_lower", ramp_from("26222a"), 3)
        return p
    return paint


def taegore(zone):
    def paint(m, seed):
        fur_c, plate_c = ZONE_FUR[zone]
        hide_ = ramp_from(mix(hx(fur_c), (120, 110, 100), 0.25), dark=0.6); plate = ramp_from(plate_c, dark=0.6, light=1.1)
        p = a2paint.paint(m, seed, [("plate|crest", plate, "horn"), ("spines", plate, "horn"), ("snout", ramp_from("c89a94"), "smooth"),
                                    ("ear", hide_, "smooth"), ("lower", ramp_from(mix(hx(fur_c), (0, 0, 0), 0.35)), "plain", 0.8), ("", hide_, "fur", 0.8)])
        for b in boxes(m, "tail"):
            plume(p, b, hide_, plate, acc_from=0.7, taper_from=0.1, blunt=0.5)
        sn = m["parts"]["snout"]["boxes"]
        plume(p, sn[1], ramp_from("f0e8d0", dark=0.7), taper_from=0.0, blunt=0.6)
        side_eyes(p, box(m, "head_main"), 2, 2, 2, 2, white=(240, 220, 150), pupil=(30, 20, 20))
        hooves(p, m, "_lower", ramp_from("26222a"), 2)
        return p
    return paint


for _z in ZONE_FUR:
    skin("burrukai_" + _z, "mob/%sBurrukaiModel.java" % ("Arctic" if _z == "arctic" else "Plains"))(burrukai(_z))
    skin("taegore_" + _z, "mob/TaegoreModel.java")(taegore(_z))


# ---------------------------------------------------------------- skyroot lizard

LIZARD = dict(skyroot=("7fc7a0", "d8f0d0"), amberoot=("d9a43a", "f4e0a0"), greatboa=("4f7a52", "b8d890"), greatoak=("5a8a3a", "c8e088"),
              greatroot=("3f6a5a", "9ad0b0"), skybirch=("b8c8a0", "f0f0d8"), skypine=("3a6a6a", "88c0c0"), skyplane=("8a9a4a", "e0e090"),
              wisproot=("7a8ad0", "d0d8f8"), wisptop=("a87ac8", "ecd0f4"))


def lizard(kind):
    def paint(m, seed):
        leaf_c, pale_c = LIZARD[kind]
        skin_ = ramp_from(mix(hx(leaf_c), (90, 80, 60), 0.45), dark=0.62); leaf = ramp_from(leaf_c, dark=0.62, light=1.15); pale = ramp_from(pale_c)
        p = a2paint.paint(m, seed, [("tongue", ramp_from("d8607a"), "smooth"), ("leg", skin_, "scale", 0.7), ("", skin_, "scale", 0.8)])
        for name in m["order"]:
            if "flaps" in name or name == "earflaps":
                for b in m["parts"][name]["boxes"]:
                    fan(p, b, leaf, pale)
        for b in boxes(m, "tongue"):
            plume(p, b, ramp_from("d8607a"), taper_from=0.0, blunt=0.5)

        def limb(pt, lo, size, x, y):
            t = abs(pt[0]) / 12.0; c = pt[2] - (lo[2] + size[2] / 2.0)
            if t < 0.72:
                return skin_[2 if int(abs(pt[0])) % 3 else 1] if abs(c) < 1.0 else None
            return skin_[1] if (abs(c) < 0.5 or abs(abs(c) - 2.0) < 0.5 or (t < 0.82 and abs(c) < 2.5)) else None
        for b in boxes(m, "leg_"):
            shape(p, b, limb)
        side_eyes(p, box(m, "head_2"), 1, 0, 1, 1, white=(20, 20, 24), pupil=(20, 20, 24))
        return p
    return paint


for _k in LIZARD:
    skin("skyroot_lizard_" + _k, "mob/SkyrootLizardModel.java")(lizard(_k))


# ---------------------------------------------------------------- prismallard

@skin("prismallard", "mob/PrismallardModel.java")
def prismallard(m, seed):
    body = ramp_from("5a7ad8", dark=0.6, light=1.15); head = ramp_from("2f9a7a", dark=0.6, light=1.2); bill = ramp_from("f0b83a", dark=0.65)
    leg = ramp_from("e8862a", dark=0.65); gem = ramp_from("e86ad8", dark=0.7, light=1.3)
    p = a2paint.paint(m, seed, [("jaw", bill, "smooth"), ("leg", leg, "scale", 0.6), ("head|neck", head, "feather", 0.8), ("wing", ramp_from("3a5ab0"), "feather"),
                                ("", body, "feather", 0.8)])
    hb = m["parts"]["head"]["boxes"]
    p.box(hb[1], bill, "smooth")
    for b in m["parts"]["head_detail"]["boxes"]:
        p.box(b, gem, "smooth")
    side_eyes(p, hb[0], 1, 1, 1, 1, white=(20, 20, 24), pupil=(20, 20, 24))
    plumes(p, m, "_tail", body, gem, acc_from=0.6)
    return p


# ---------------------------------------------------------------- glitterwing, shroudwing (flat insects)

GLITTER = dict(indigo=("4a48c8", "c8d0ff", "moth"), dragonfly=("3aa8d8", "e0f6ff", "dragon"), drapewing=("b84a8a", "f8d0e8", "drape"),
               leaf_insect=("5aa83a", "d8f0a0", "leaf"), quicksoil_moth=("e0c060", "fff4c0", "moth"), sunset_dragonfly=("e8743a", "ffe0a0", "dragon"),
               orange_mimic=("f08a2a", "2a2020", "moth"), amber_moth=("d89a2a", "fff0b0", "moth"), spotted=("f0f0e8", "3a3a48", "moth"),
               blightfly=("7a5ab0", "c8f060", "dragon"), phantomfly=("d8e8f8", "88a0d8", "drape"))


def glitterwing(kind):
    main_c, mark_c, form = GLITTER[kind]

    def paint(m, seed):
        main = ramp_from(main_c, dark=0.6, light=1.15); mark = ramp_from(mark_c); dark = ramp_from("2a2630")
        p = a2paint.Painter(m, seed)

        def wing(pt, lo, size, x, y):
            ax = abs(pt[0]) / 13.0                      # 0 at the body, 1 at the wing tip
            z = (pt[2] - lo[2]) / size[2]               # 0 front, 1 back
            if form == "dragon":                        # two narrow pairs
                band = min(abs(z - 0.33), abs(z - 0.52)); inside = band < 0.07 and ax < 0.98
            elif form == "leaf":
                inside = abs(z - 0.5) < 0.36 * (1 - ax ** 2) + 0.02 and ax < 0.9
            elif form == "drape":                       # long trailing hind wings
                inside = (abs(z - 0.32) < 0.2 * (1 - ax ** 1.5) and ax < 0.95) or (0.4 < z < 0.95 - ax * 0.5 and ax < 0.55)
            else:                                       # moth / butterfly: a big fore wing and a rounder hind wing
                inside = (((ax - 0.5) / 0.5) ** 2 + ((z - 0.33) / 0.2) ** 2 < 1) or (((ax - 0.36) / 0.36) ** 2 + ((z - 0.58) / 0.15) ** 2 < 1)
            if not inside or ax < 0.06:
                return None
            v = p.n(x, y, 2) * 0.3 + (0.4 - ax) * 0.8
            spot = (int(ax * 13) + int(z * 26)) % 5 == 0 and 0.35 < ax < 0.9
            r = mark if (spot or ax > 0.82) else main
            return r[max(0, min(4, int(round(2 + v * 1.4))))]

        def bodyf(pt, lo, size, x, y):
            z = (pt[2] - lo[2]) / size[2]
            if abs(pt[0]) > (1.0 if z > 0.45 else 1.5) or not 0.22 < z < (0.85 if form in ("dragon", "leaf") else 0.7):
                # feelers
                if 0.12 < z <= 0.22 and abs(abs(pt[0]) - (1.5 + (0.22 - z) * 12)) < 0.5:
                    return dark[1]
                return None
            return dark[1 + (int(z * 26) % 2)] if z > 0.3 else dark[2]

        for name in ("right_wing", "left_wing"):
            shape(p, box(m, name), wing)
        shape(p, box(m, "glitterwing"), bodyf)
        return p
    return paint


for _k in GLITTER:
    skin("glitterwing_" + _k, "mob/GlitterwingModel.java")(glitterwing(_k))

SHROUD = dict(scarab=("2a7a5a", "e8c84a"), arctic=("c8dcec", "6a9ad0"), fire=("c8482a", "f8c84a"), moss=("5a7a3a", "a8c860"), ore=("5a5a66", "58d8c8"),
              purple=("6a3a9a", "d8a0f0"))


def shroudwing(kind):
    def paint(m, seed):
        shell_c, mark_c = SHROUD[kind]
        shell = ramp_from(shell_c, dark=0.55, light=1.25); mark = ramp_from(mark_c); dark = ramp_from("26222c")
        p = a2paint.paint(m, seed, [("case", shell, "smooth"), ("", dark, "plain", 0.6)])
        for b in boxes(m, "wing_case"):
            tip(p, b, "top", mark, "y", "lo", 1)
        for name in ("right_wing_r1", "left_wing_r1"):
            for b in m["parts"][name]["boxes"]:
                plume(p, b, ramp_from("dfe8f4", dark=0.8, light=1.04), taper_from=0.3, blunt=0.5)
        sb = m["parts"]["shroudwing"]["boxes"]
        shape(p, sb[2], lambda pt, lo, size, x, y: dark[1] if int(pt[0] - lo[0]) % 2 == 0 else None)       # legs
        return p
    return paint


for _k in SHROUD:
    skin("shroudwing_" + _k, "mob/ShroudwingModel.java")(shroudwing(_k))


# ---------------------------------------------------------------- aerwhale

@skin("aerwhale", "mob/AerwhaleModel.java")
def aerwhale(m, seed):
    back = ramp_from("5f8fc4", dark=0.62, light=1.15, cool=(30, 40, 90)); belly = ramp_from("e6eef6", dark=0.78, light=1.04, cool=(110, 140, 190))
    p = a2paint.paint(m, seed, [("Jaw", belly, "smooth", 0.7), ("Fin", back, "smooth", 0.8), ("", back, "stone", 0.7)])
    # the pale belly: the underside and the lower third of the flanks
    for name in ("Head", "Body", "Tail"):
        for b in m["parts"][name]["boxes"]:
            p.face(a2parse.faces(b)["bottom"], "bottom", belly, "smooth", 0.7)
            for face in ("west", "east", "north", "south"):
                u, v, fw, fh = a2parse.faces(b)[face]
                cut = fh * 2 // 3
                for j in range(cut, fh):
                    for i in range(fw):
                        if j > cut or (i + u) % 3 != 0:
                            p.put(u + i, v + j, belly[max(0, min(4, 3 - (j - cut) // 2 + (1 if p.n(u + i, v + j, 2) > 0.3 else 0)))])
    # throat grooves on the jaw
    jb = box(m, "Jaw_r1")
    u, v, fw, fh = a2parse.faces(jb)["bottom"]
    for j in range(fh):
        for i in range(2, fw - 2, 3):
            p.put(u + i, v + j, belly[1])
    for name in ("TailFin", "LeftFin_r1", "RightFin_r1"):
        for b in m["parts"][name]["boxes"]:
            p.face(a2parse.faces(b)["bottom"], "bottom", belly, "smooth", 0.7)
    for b in boxes(m, "DorsalFin") + m["parts"]["Tail"]["boxes"][:1]:
        plume(p, b, back, taper_from=0.1, blunt=0.35)
    side_eyes(p, box(m, "Head"), 22, 8, 2, 2, white=(20, 24, 40), pupil=(20, 24, 40))
    return p


# ---------------------------------------------------------------- run

def main():
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    only = [a for a in sys.argv[1:] if not a.startswith("--") and a != preview]
    for name, (java, fn, hide) in SKINS.items():
        if only and not any(o in name for o in only):
            continue
        m = model(java)
        p = fn(m, name)
        os.makedirs(OUT, exist_ok=True)
        p.im.save(os.path.join(OUT, name + ".png"))
        if preview:
            import a2render
            os.makedirs(preview, exist_ok=True)
            a2render.sheet(m, p.im, hide=hide).save(os.path.join(preview, name + ".png"))
    print("painted", len(SKINS) if not only else "selected", "skins")


if __name__ == "__main__":
    main()
