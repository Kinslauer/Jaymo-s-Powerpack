"""Space pod texture and cube table (0.18.9, Andy): the entity's 128x128 skin, and the cubes in PodModel.

The pod is built from slots (PodPart): a keel (iron cross at the base), three fins (a slab with a foot), a thruster bell,
a brass tank with a red band, four bone-white hull plates with rivets (a porthole on the front one, a hatch on the right),
a stepped glass canopy and a mast with a brass cap. Each cube below belongs to a named part of the model; PodRenderer
draws the parts whose slots are filled. A damaged plate also draws its gash; a damaged canopy only its lower step.

Sizes are pixels (16 to the block). Heights are above the pod's feet; x is the pod's right, z its front. The script packs
every cube's six faces onto the sheet, paints them, and rewrites the table between the markers in PodModel.java (so
change the shape here). Run from the repository root: python -I tools/gen_pod.py
"""
import random
from PIL import Image, ImageDraw

OUT = "src/main/resources/assets/jaymospowers/textures/entity/space_pod.png"
JAVA = "src/client/java/com/jaymospowers/client/power/zfighter/pod/PodModel.java"
random.seed(1809)

IRON = (74, 78, 86)
BRASS = (176, 134, 62)
BONE = (226, 222, 210)
GLASS = (120, 190, 230)
RED = (170, 46, 40)
SCORCH = (58, 46, 40)

# (part, material, x, h0, z, w, h, d)
CUBES = [
    ("keel", "iron", -12, 0, -2, 24, 3, 4),
    ("keel", "iron", -2, 0, -12, 4, 3, 24),
    ("keel", "iron", -4, 3, -4, 8, 3, 8),
    ("fin_r", "iron", 11, 3, -4, 2, 16, 8),
    ("fin_r", "iron", 11, 0, -4, 8, 3, 8),
    ("fin_l", "iron", -13, 3, -4, 2, 16, 8),
    ("fin_l", "iron", -19, 0, -4, 8, 3, 8),
    ("fin_b", "iron", -4, 3, -13, 8, 16, 2),
    ("fin_b", "iron", -4, 0, -19, 8, 3, 8),
    ("bell", "brass", -3, 6, -3, 6, 5, 6),
    ("bell", "brass", -5, 6, -5, 10, 2, 10),
    ("tank", "brass_band", -8, 11, -8, 16, 18, 16),
    ("plate_f", "bone_front", -10, 10, 10, 20, 20, 2),
    ("plate_r", "bone_right", 10, 10, -10, 2, 20, 20),
    ("plate_b", "bone", -10, 10, -12, 20, 20, 2),
    ("plate_l", "bone", -12, 10, -10, 2, 20, 20),
    ("gash_f", "scorch", -4, 15, 11.5, 6, 8, 1),
    ("gash_b", "scorch", 0, 17, -12.5, 6, 8, 1),
    ("gash_r", "scorch", 11.5, 14, -3, 1, 8, 6),
    ("gash_l", "scorch", -12.5, 16, 0, 1, 8, 6),
    ("canopy_low", "glass", -8, 30, -8, 16, 6, 16),
    ("canopy", "glass", -8, 30, -8, 16, 6, 16),
    ("canopy", "glass", -5, 36, -5, 10, 5, 10),
    ("mast", "iron", -1, 41, -1, 2, 7, 2),
    ("mast", "brass", -2, 48, -2, 4, 2, 4),
]


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def noisy(c, spread=0.06):
    return shade(c, random.uniform(1 - spread, 1 + spread)) + (255,)


def face(img, x, y, w, h, c, edge=True):
    for i in range(w):
        for j in range(h):
            col = noisy(c)
            if edge and (i == 0 or j == 0 or i == w - 1 or j == h - 1):
                col = shade(c, 0.78) + (255,)
            img.putpixel((x + i, y + j), col)


MATERIALS = {
    "iron": IRON, "brass": BRASS, "brass_band": BRASS, "bone": BONE, "bone_front": BONE, "bone_right": BONE,
    "glass": GLASS, "scorch": SCORCH,
}


def ceil(v):
    return int(-(-v // 1))


def footprint(cube):
    _, _, _, _, _, w, h, d = cube
    return 2 * ceil(d) + 2 * ceil(w), ceil(d) + ceil(h)


def family(mat):
    return "bone" if mat.startswith("bone") else mat


def key_of(c):
    """Cubes of the same size and look share one area of the sheet (the left fin is the right fin, the back plate the front)."""
    return (family(c[1]), ceil(c[5]), ceil(c[6]), ceil(c[7]))


def pack():
    spots = {}
    x = y = shelf = 0
    for c in sorted(CUBES, key=lambda c: -footprint(c)[1]):
        key = key_of(c)
        if key in spots:
            continue
        fw, fh = footprint(c)
        if x + fw > 128:
            x, y, shelf = 0, y + shelf, 0
        spots[key] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= 128, y + shelf
    return spots


def paint():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    spots = pack()
    groups = {}
    for c in CUBES:
        best = groups.get(key_of(c))
        if best is None or c[1] in ("bone_front", "bone_right"):
            groups[key_of(c)] = c
    dr = ImageDraw.Draw(img)
    for key, c in groups.items():
        u, v = spots[key]
        mat = c[1]
        w, h, d = key[1], key[2], key[3]
        base = MATERIALS[mat]
        top = (u + d, v, w, d)
        bottom = (u + d + w, v, w, d)
        west = (u, v + d, d, h)
        north = (u + d, v + d, w, h)
        east = (u + d + w, v + d, d, h)
        south = (u + 2 * d + w, v + d, w, h)
        if mat == "glass":
            for f in (top, bottom, west, north, east, south):
                face(img, *f, base)
                fx, fy, fw, fh = f
                for i in range(1, fw - 1):
                    for j in range(1, fh - 1):
                        img.putpixel((fx + i, fy + j), noisy(base, 0.04)[:3] + (190,))
            continue
        face(img, *top, shade(base, 1.1))
        face(img, *bottom, shade(base, 0.7))
        face(img, *west, shade(base, 0.9))
        face(img, *north, shade(base, 0.85))
        face(img, *east, shade(base, 0.9))
        face(img, *south, base)
        if mat == "brass_band":
            for f in (west, north, east, south):
                fx, fy, fw, fh = f
                for j in range(fh // 2 - 2, fh // 2 + 2):
                    for i in range(fw):
                        img.putpixel((fx + i, fy + j), noisy(RED))
        if family(mat) == "bone":
            outers = (south, north) if w > d else (east, west)
            for ox, oy, ow, oh in outers:
                for rx in range(ox + 2, ox + ow - 1, 4):
                    for ry in (oy + 2, oy + oh - 3):
                        img.putpixel((rx, ry), shade(IRON, 1.5) + (255,))
            if mat == "bone_front":
                ox, oy, ow, oh = south
                cx, cy = ox + ow // 2, oy + oh // 2
                dr.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=shade(IRON, 1.2) + (255,))
                dr.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=shade(GLASS, 0.9) + (255,))
                dr.point((cx - 1, cy - 1), fill=(240, 250, 255, 255))
            if mat == "bone_right":
                ox, oy, ow, oh = east
                cx, cy = ox + ow // 2, oy + oh // 2
                dr.rectangle((cx - 4, cy - 6, cx + 4, cy + 6), outline=shade(IRON, 1.2) + (255,))
                dr.rectangle((cx + 2, cy - 1, cx + 3, cy + 1), fill=BRASS + (255,))
    return img, spots


def table(spots):
    lines = []
    for c in CUBES:
        part, mat, x, h0, z, w, h, d = c
        u, v = spots[key_of(c)]
        y = -(h0 + h)
        lines.append('\t\t{"%s", %d, %d, %sF, %sF, %sF, %sF, %sF, %sF},' % (part, u, v, x, y, z, w, h, d))
    return "\n".join(lines)


def main():
    img, spots = paint()
    img.save(OUT)
    src = open(JAVA).read()
    a = src.index("// cubes:start") + len("// cubes:start\n")
    b = src.index("\t\t// cubes:end")
    open(JAVA, "w").write(src[:a] + table(spots) + "\n" + src[b:])


main()
