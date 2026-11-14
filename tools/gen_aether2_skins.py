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
from a2paint import hx, mix, ramp_from, side_eyes, front_eyes, tip, plume, plumes, clear_except  # noqa: E402

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
