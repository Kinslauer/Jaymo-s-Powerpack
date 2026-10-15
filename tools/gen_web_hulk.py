"""The symbiote's big body (the hulk): an original box model built over the player's skeleton (head, body, arms and legs
keep the player model's pivots, so every pose still drives it), and its texture, drawn here pixel by pixel (no art is
copied). Massive traps, shoulders and lats, Popeye forearms, long clawed hands, thick legs, a small head sunk low and
forward with big angular eyes, a hinged lower jaw and rows of fangs (cut out of the texture). Every muscle is its own
part, so the equip animation can lay them on one at a time (client WebSuitGrowth).

Also draws web_symbiote_flesh.png: four swatches (wet black, black with its sheen, bone, the tongue's red) for the
lengths drawn in code (the tongue, the weapon arm, the tendrils that wrap the body; client WebTubes).

Writes:
  src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_hulk.png
  src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_flesh.png
  src/client/java/com/jaymospowers/client/power/web/WebHulkMesh.java  (generated: don't edit by hand)
Run from the project root:  python -I tools/gen_web_hulk.py
"""
import math
import random
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
TEXTURES = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"
JAVA = PROJECT / "src" / "client" / "java" / "com" / "jaymospowers" / "client" / "power" / "web" / "WebHulkMesh.java"

TEX_W, TEX_H = 128, 128

BLACK = (16, 16, 21)
DEEP = (8, 8, 11)
SHEEN = (70, 84, 108)
TEAL = (52, 96, 112)
VEIN = (44, 38, 64)
EYE_WHITE = (240, 240, 246)
EYE_EDGE = (186, 190, 204)
BONE = (226, 222, 206)
BONE_SHADE = (168, 160, 140)
RED = (150, 22, 30)
RED_DEEP = (82, 8, 14)
RED_LIGHT = (196, 62, 70)

# Each part's own pivot (the player model's), and a step in the order the suit lays muscle on (0 first).
# box: (part, name, from (x, y, z) relative to the PART's pivot, size (w, h, d), paint, child pivot relative to the part, order)
BOXES = []


def box(part, name, frm, size, paint, pivot, order):
    BOXES.append(dict(part=part, name=name, frm=frm, size=size, paint=paint, pivot=pivot, order=order))


# ---- the torso (pivot at the neck) ----
box("body", "core", (-6, 0, -3), (12, 13, 7), "muscle", (0, 0, 0), 0)
box("body", "back", (-5.5, 1, 3), (11, 9, 2), "back", (0, 3, 3), 1)
box("body", "lats", (-8, 2, -2), (16, 7, 5), "muscle", (0, 4, 0), 2)
box("body", "pec_r", (-6.5, 1, -4.5), (6, 6, 2), "pec", (-3, 3, -3), 3)
box("body", "pec_l", (0.5, 1, -4.5), (6, 6, 2), "pec", (3, 3, -3), 3)
box("body", "abs", (-4, 7, -4), (8, 5, 1), "abs", (0, 9, -3), 4)
box("body", "traps", (-6, -3, -1), (12, 4, 5), "muscle", (0, 1, 1), 5)

# ---- the arms (right: pivot at the shoulder; the left is the mirror) ----
ARM = [
    ("shoulder", (-5.5, -4, -3.5), (7, 7, 7), "muscle", (-2, 0, 0), 2),
    ("upper", (-4.5, 2, -3), (6, 7, 6), "muscle", (-2, 2, 0), 3),
    ("fore", (-5, 8, -3.5), (7, 6, 7), "fore", (-2, 8, 0), 4),
    ("hand", (-4.5, 14, -2.5), (5, 3, 5), "muscle", (-2, 14, 0), 5),
]
CLAWS = [((-3.5, 17, -2.5), (1, 4, 1)), ((-3.5, 17, -0.5), (1, 4, 1)), ((-3.5, 17, 1.5), (1, 4, 1)), ((-1.5, 17, -2), (1, 3, 1))]
TIPS = [((-3.25, 21, -2.25), (1, 2, 1)), ((-3.25, 21, -0.25), (1, 2, 1)), ((-3.25, 21, 1.75), (1, 2, 1)), ((-1.25, 20, -1.75), (1, 2, 1))]


def mirror(frm, size):
    return (-frm[0] - size[0], frm[1], frm[2])


def mirror_pivot(p):
    return (-p[0], p[1], p[2])


for side, part in (("r", "right_arm"), ("l", "left_arm")):
    for name, frm, size, paint, pivot, order in ARM:
        if side == "l":
            frm, pivot = mirror(frm, size), mirror_pivot(pivot)
        box(part, name, frm, size, paint, pivot, order)
    for i, (frm, size) in enumerate(CLAWS):
        pivot = (-2, 17, 0)
        if side == "l":
            frm, pivot = mirror(frm, size), mirror_pivot(pivot)
        box(part, "claw%d" % i, frm, size, "claw", pivot, 6)
    for i, (frm, size) in enumerate(TIPS):
        pivot = (-2, 17, 0)
        if side == "l":
            frm, pivot = mirror(frm, size), mirror_pivot(pivot)
        box(part, "tip%d" % i, frm, size, "bone", pivot, 6)

# ---- the legs (pivot at the hip) ----
LEG = [
    ("thigh", (-3, -1, -3), (6, 8, 6), "muscle", (0, 0, 0), 1),
    ("calf", (-2.5, 6, -2.5), (5, 5, 5), "muscle", (0, 6, 0), 2),
    ("foot", (-3, 10, -5), (6, 2, 7), "foot", (0, 11, -1), 3),
]
for side, part in (("r", "right_leg"), ("l", "left_leg")):
    for name, frm, size, paint, pivot, order in LEG:
        if side == "l":
            frm, pivot = mirror(frm, size), mirror_pivot(pivot)
        box(part, name, frm, size, paint, pivot, order)
    for i, x in enumerate((-2.5, -0.5, 1.5)):
        frm = (x, 11, -6.5)
        if side == "l":
            frm = mirror(frm, (1, 1, 2))
        box(part, "toe%d" % i, frm, (1, 1, 2), "bone", (0, 11, -5), 4)

# ---- the head (pivot at the neck): a small skull, a heavy muzzle, a hinged jaw, fangs, a red throat ----
box("head", "skull", (-4.5, -8, -4.5), (9, 6, 9), "skull", (0, -2, 0), 7)
box("head", "muzzle", (-4.5, -2, -5.5), (9, 2, 10), "muzzle", (0, -1, 0), 7)
box("head", "fangs", (-4.5, 0, -5.5), (9, 3, 1), "fangs_down", (0, 0, -5), 8)
box("head", "fangs_r", (-4.5, 0, -4.5), (1, 3, 5), "fangs_down", (0, 0, -5), 8)
box("head", "fangs_l", (3.5, 0, -4.5), (1, 3, 5), "fangs_down", (0, 0, -5), 8)
box("head", "throat", (-4, 0, 1), (8, 3, 1), "red", (0, 0, 1), 8)
# the jaw: its own part, hinged at the back (see JAW_PIVOT)
JAW_PIVOT = (0, 0.5, 2)
box("jaw", "chin", (-4.5, 0.5, -5.5), (9, 2, 8), "chin", JAW_PIVOT, 8)
box("jaw", "jaw_fangs", (-4, -2, -5), (8, 3, 1), "fangs_up", JAW_PIVOT, 8)
box("jaw", "jaw_fangs_r", (-4.5, -2, -4), (1, 3, 5), "fangs_up", JAW_PIVOT, 8)
box("jaw", "jaw_fangs_l", (3.5, -2, -4), (1, 3, 5), "fangs_up", JAW_PIVOT, 8)

# where each part's pivot is in the player model (the jaw's is in the head's space)
PART_PIVOTS = {
    "head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
}
EMPTY_CHILD = {"head": "hat", "body": "jacket", "right_arm": "right_sleeve", "left_arm": "left_sleeve",
               "right_leg": "right_pants", "left_leg": "left_pants"}


def footprint(size):
    w, h, d = (math.ceil(v) for v in size)
    return 2 * d + 2 * w, d + h


def pack():
    """Shelf-packs every box's UV net into the texture (biggest first)."""
    order = sorted(range(len(BOXES)), key=lambda i: -footprint(BOXES[i]["size"])[1] * 1000 - footprint(BOXES[i]["size"])[0])
    x = y = shelf = 0
    for i in order:
        fw, fh = footprint(BOXES[i]["size"])
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        BOXES[i]["uv"] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= TEX_H, ("texture too small", y + shelf)


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


EYES = [
    ".........",
    "WWWk.kWWW",
    "eWWWkWWWe",
    ".eWWkWWe.",
    "..eWkWe..",
    "...k.k...",
]

# fangs: per column of a plate, how long the fang is (rows); 0 = gap
FANG_ROWS = [3, 1, 2, 3, 1, 3, 2, 1, 3]


def faces(b):
    """Each face of a box's net: name -> (x0, y0, w, h), in the texture."""
    u, v = b["uv"]
    w, h, d = (math.ceil(s) for s in b["size"])
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def paint(img, rnd):
    for b in BOXES:
        f = faces(b)
        kind = b["paint"]
        for name, (x0, y0, w, h) in f.items():
            for yy in range(h):
                for xx in range(w):
                    c = lerp(DEEP, BLACK, 0.75 + rnd.random() * 0.25)
                    a = 255
                    side = name not in ("top", "bottom")
                    v = (yy + 0.5) / h
                    if side:
                        # wet sheen high on every face, a teal rim at the outer edges
                        glow = math.exp(-((v - 0.18) / 0.22) ** 2) * 0.38
                        edge = min(xx, w - 1 - xx)
                        if edge == 0 and w > 2:
                            c = lerp(c, TEAL, 0.35)
                        c = lerp(c, SHEEN, glow)
                    elif name == "top":
                        c = lerp(c, SHEEN, 0.3)
                    if kind == "pec" and side and yy % 2 == 1 and name == "front":
                        c = lerp(c, DEEP, 0.5)
                    if kind == "abs" and name == "front":
                        if xx == w // 2 or yy in (1, 3):
                            c = lerp(c, DEEP, 0.7)
                        else:
                            c = lerp(c, SHEEN, 0.2)
                    if kind in ("fore", "muscle") and side and (xx + int(yy * 0.4)) % 3 == 0 and rnd.random() < 0.35:
                        c = lerp(c, DEEP, 0.45)
                    if kind == "claw":
                        c = lerp(BLACK, SHEEN, 0.2 + 0.4 * v)
                    if kind == "bone":
                        c = lerp(BONE, BONE_SHADE, v * 0.8 + rnd.random() * 0.1)
                    if kind == "red":
                        c = lerp(RED_DEEP, RED, rnd.random() * 0.6)
                    if kind == "muzzle" and name == "bottom" or kind == "chin" and name == "top":
                        # the roof of the mouth and its floor
                        c = lerp(RED_DEEP, RED, 0.3 + rnd.random() * 0.5)
                        if (xx + yy) % 4 == 0:
                            c = lerp(c, RED_LIGHT, 0.5)
                    if kind.startswith("fangs"):
                        a = 0
                        col = xx % len(FANG_ROWS)
                        length = FANG_ROWS[col]
                        row = yy if kind == "fangs_down" else h - 1 - yy
                        if name in ("top", "bottom"):
                            a = 0
                        elif row < length:
                            a = 255
                            c = lerp(BONE, BONE_SHADE, row / 3.0 + rnd.random() * 0.15)
                    if kind == "skull" and name == "front":
                        ch = EYES[yy][xx] if yy < len(EYES) and xx < len(EYES[yy]) else "."
                        if ch == "W":
                            c = EYE_WHITE
                        elif ch == "e":
                            c = EYE_EDGE
                        elif ch == "k":
                            c = (2, 2, 4)
                    img.putpixel((x0 + xx, y0 + yy), c + (a,))
    # veins: a few dark threads wandering over the bigger faces
    for b in BOXES:
        if b["paint"] not in ("muscle", "fore", "back", "pec"):
            continue
        for name, (x0, y0, w, h) in faces(b).items():
            if w < 4 or h < 4 or rnd.random() < 0.4:
                continue
            px, py = x0 + rnd.randrange(w), y0
            for _ in range(h * 2):
                if x0 <= px < x0 + w and y0 <= py < y0 + h:
                    old = img.getpixel((px, py))
                    img.putpixel((px, py), lerp(old[:3], VEIN, 0.6) + (old[3],))
                px += rnd.choice((-1, 0, 0, 1))
                py += rnd.choice((0, 1, 1))


def flesh():
    """Four 16x16 swatches: wet black, sheen, bone, red."""
    rnd = random.Random(77)
    img = Image.new("RGBA", (32, 32))
    for sy in range(2):
        for sx in range(2):
            swatch = sy * 2 + sx
            for y in range(16):
                for x in range(16):
                    t = rnd.random()
                    if swatch == 0:
                        c = lerp(DEEP, BLACK, 0.6 + t * 0.4)
                        if y % 5 == 0:
                            c = lerp(c, SHEEN, 0.15)
                    elif swatch == 1:
                        c = lerp(BLACK, SHEEN, 0.15 + 0.35 * math.exp(-((y - 4) / 4.0) ** 2) + t * 0.1)
                        if x % 4 == 0:
                            c = lerp(c, TEAL, 0.25)
                    elif swatch == 2:
                        c = lerp(BONE, BONE_SHADE, y / 16.0 * 0.6 + t * 0.15)
                    else:
                        c = lerp(RED_DEEP, RED_LIGHT, 0.35 + 0.35 * math.sin(x * 0.7) * 0.5 + t * 0.2)
                        if x in (7, 8):
                            # the groove down the middle of the tongue
                            c = lerp(c, RED_DEEP, 0.6)
                    img.putpixel((sx * 16 + x, sy * 16 + y), c + (255,))
    return img


def fmt(v):
    return ("%.2f" % v).rstrip("0").rstrip(".") + "F"


def java():
    lines = []
    lines.append("package com.jaymospowers.client.power.web;")
    lines.append("")
    lines.append("import net.minecraft.client.model.geom.PartPose;")
    lines.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    lines.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    lines.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    lines.append("import net.minecraft.client.model.geom.builders.PartDefinition;")
    lines.append("")
    lines.append("/**")
    lines.append(" * GENERATED by {@code tools/gen_web_hulk.py}: don't edit by hand. The big body's boxes over the player model's skeleton")
    lines.append(" * (every muscle its own part, laid on in {@link #ORDER} by WebSuitGrowth), UV-mapped onto")
    lines.append(" * {@code web_symbiote_hulk.png}.")
    lines.append(" */")
    lines.append("final class WebHulkMesh {")
    lines.append("\tstatic final int TEX_W = %d, TEX_H = %d;" % (TEX_W, TEX_H))
    lines.append("\t/** The jaw's hinge in the head's space (pixels). */")
    lines.append("\tstatic final float JAW_X = %s, JAW_Y = %s, JAW_Z = %s;" % tuple(fmt(v) for v in JAW_PIVOT))
    names = []
    for b in BOXES:
        if b["part"] != "jaw":
            names.append((b["part"], b["name"], b["order"]))
    names.append(("head", "jaw", 8))
    lines.append("\t/** Each muscle part (its parent part, its name) and when the suit lays it on (0 first, to {@link #LAST_ORDER}). */")
    lines.append("\tstatic final String[][] PARTS = {")
    for part, name, order in names:
        lines.append("\t\t{\"%s\", \"%s\"}," % (part, name))
    lines.append("\t};")
    lines.append("\tstatic final int[] ORDER = {%s};" % ", ".join(str(o) for _, _, o in names))
    lines.append("\tstatic final int LAST_ORDER = %d;" % max(o for _, _, o in names))
    lines.append("")
    lines.append("\tprivate WebHulkMesh() {")
    lines.append("\t}")
    lines.append("")
    lines.append("\tstatic LayerDefinition create() {")
    lines.append("\t\tMeshDefinition mesh = new MeshDefinition();")
    lines.append("\t\tPartDefinition root = mesh.getRoot();")
    for part, (px, py, pz) in PART_PIVOTS.items():
        var = part.replace("_", "")
        lines.append("\t\tPartDefinition %s = root.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));"
                     % (var, part, fmt(px), fmt(py), fmt(pz)))
        lines.append("\t\t%s.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.ZERO);" % (var, EMPTY_CHILD[part]))
    lines.append("\t\tPartDefinition jaw = head.addOrReplaceChild(\"jaw\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));"
                 % tuple(fmt(v) for v in JAW_PIVOT))
    for b in BOXES:
        var = b["part"].replace("_", "")
        px, py, pz = b["pivot"]
        if b["part"] == "jaw":
            # the jaw's boxes hang straight off it: their pivots are its own
            jx, jy, jz = JAW_PIVOT
            ox, oy, oz = b["frm"][0] - jx, b["frm"][1] - jy, b["frm"][2] - jz
            lines.append("\t\t%s.addOrReplaceChild(\"%s\", CubeListBuilder.create().texOffs(%d, %d).addBox(%s, %s, %s, %d, %d, %d), PartPose.ZERO);"
                         % (var, b["name"], b["uv"][0], b["uv"][1], fmt(ox), fmt(oy), fmt(oz), *(math.ceil(s) for s in b["size"])))
            continue
        ox, oy, oz = b["frm"][0] - px, b["frm"][1] - py, b["frm"][2] - pz
        lines.append("\t\t%s.addOrReplaceChild(\"%s\", CubeListBuilder.create().texOffs(%d, %d).addBox(%s, %s, %s, %d, %d, %d), PartPose.offset(%s, %s, %s));"
                     % (var, b["name"], b["uv"][0], b["uv"][1], fmt(ox), fmt(oy), fmt(oz), *(math.ceil(s) for s in b["size"]), fmt(px), fmt(py), fmt(pz)))
    lines.append("\t\treturn LayerDefinition.create(mesh, TEX_W, TEX_H);")
    lines.append("\t}")
    lines.append("}")
    JAVA.write_text("\n".join(lines) + "\n")


def main():
    pack()
    rnd = random.Random(4242)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    paint(img, rnd)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    img.save(TEXTURES / "web_symbiote_hulk.png")
    flesh().save(TEXTURES / "web_symbiote_flesh.png")
    java()
    print("boxes:", len(BOXES))


if __name__ == "__main__":
    main()
