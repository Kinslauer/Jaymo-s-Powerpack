"""The space pod's glass canopy (Andy, 2026-11-25): the cockpit cover drawn over Alex's Caves' submarine (PodSubmarine).

Andy: "you should be sitting in that cockpit, and there needs to be a glass enclosure over it. maybe it animates tilting up
then back down after you enter." The submarine's own cockpit is open; this is our own cover for it, a stepped glass
bubble in slate frames with one riveted rib across it, hinged at the top of the hull behind it so it tilts open.

The boxes are in the submarine model's hull space (pixels, y down, its front -z), measured from the hinge: the top edge
of the hull's core over the cockpit (hull x 0, y -21, z -7). The script packs the boxes onto a 256x128 sheet, paints a
sound and a cracked sheet (a damaged canopy), and rewrites the cube table between the markers in PodCanopy.java, so
change the shape here. Run from the repository root: python -I tools/gen_pod_canopy.py
"""
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/space_pod_canopy.png"
OUT_CRACKED = "src/main/resources/assets/jaymospowers/textures/entity/space_pod_canopy_cracked.png"
JAVA = "src/client/java/com/jaymospowers/client/compat/PodCanopy.java"
SHEET = (256, 128)
HINGE = (0, -21, -7)
random.seed(2511)

# the submarine's own colours (its slate frame and copper plating, read off it in game), and our glass
SLATE = (83, 85, 108)
SLATE_DARK = (64, 66, 83)
SLATE_LIGHT = (104, 106, 127)
COPPER = (214, 123, 91)
GLASS = (176, 214, 232)
GLINT = (236, 248, 253)

# (material, x0, y0, z0, x1, y1, z1) in hull space: the glass sits on the cockpit's side walls (their tops at y 8)
BOXES = [
    ("glass", -19, -6, -36, 19, 8, -7),    # the low step, over the walls and the bow
    ("glass", -16, -16, -33, 16, -6, -7),  # the middle step, round the pilot's shoulders
    ("glass", -11, -21, -28, 11, -16, -7),  # the crown, over the head, flush with the hull's top
    # one slate rib arched over all three steps, a little proud of the glass: up each side, in along each ledge, over the top
    ("rib", -19.5, -6, -23, -17.5, 8, -21), ("rib", 17.5, -6, -23, 19.5, 8, -21),
    ("rib", -19.5, -7, -23, -15.5, -5, -21), ("rib", 15.5, -7, -23, 19.5, -5, -21),
    ("rib", -16.5, -16, -23, -14.5, -6, -21), ("rib", 14.5, -16, -23, 16.5, -6, -21),
    ("rib", -16.5, -17, -23, -10.5, -15, -21), ("rib", 10.5, -17, -23, 16.5, -15, -21),
    ("rib", -11.5, -21, -23, -9.5, -16, -21), ("rib", 9.5, -21, -23, 11.5, -16, -21),
    ("rib", -11.5, -22, -23, 11.5, -20, -21),
    # the sill: a slate lip all round the bottom edge that seals on the walls
    ("rib", -19.5, 6, -37, 19.5, 8, -35),
]


def ceil(v):
    return int(-(-v // 1))


def size(b):
    return ceil(b[4] - b[1]), ceil(b[5] - b[2]), ceil(b[6] - b[3])


def pack():
    spots = []
    x = y = shelf = 0
    for b in BOXES:
        w, h, d = size(b)
        fw, fh = 2 * (w + d), d + h
        if x + fw > SHEET[0]:
            x, y, shelf = 0, y + shelf, 0
        spots.append((x, y))
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= SHEET[1], y + shelf
    return spots


def tone(c, k, a=255):
    return tuple(max(0, min(255, int(v * k))) for v in c) + (a,)


def faces(u, v, w, h, d):
    """The six faces of a box's net: (name, x, y, width, height), the layout Minecraft's cubes read."""
    return [("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d), ("west", u, v + d, d, h),
            ("north", u + d, v + d, w, h), ("east", u + d + w, v + d, d, h), ("south", u + 2 * d + w, v + d, w, h)]


def glass_face(px, name, fx, fy, fw, fh):
    for i in range(fw):
        for j in range(fh):
            edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
            if edge:
                # the frame: lit on its top-left, dark on its bottom-right
                px[fx + i, fy + j] = tone(SLATE_LIGHT if i == 0 or j == 0 else SLATE_DARK, 1.0)
            else:
                px[fx + i, fy + j] = tone(GLASS, random.uniform(0.96, 1.04), 60)
    # a glint across the pane, top-left to bottom-right, two pixels wide
    if name != "bottom" and fw > 4 and fh > 4:
        for t in range(min(fw, fh) - 2):
            for k in (0, 1):
                gx, gy = fx + 2 + t + k, fy + 1 + t
                if fx < gx < fx + fw - 1 and fy < gy < fy + fh - 1 and t % 6 < 3:
                    px[gx, gy] = tone(GLINT, 1.0, 170)


def rib_face(px, name, fx, fy, fw, fh):
    for i in range(fw):
        for j in range(fh):
            c = SLATE_DARK if (i == fw - 1 or j == fh - 1) else SLATE_LIGHT if (i == 0 or j == 0) else SLATE
            px[fx + i, fy + j] = tone(c, random.uniform(0.95, 1.05))
    # copper rivets along the long faces
    if name in ("north", "south", "top", "west", "east") and max(fw, fh) > 6:
        long_w = fw >= fh
        for s in range(2, (fw if long_w else fh) - 2, 5):
            rx, ry = (fx + s, fy + fh // 2) if long_w else (fx + fw // 2, fy + s)
            px[rx, ry] = tone(COPPER, 1.1)


def covered(i):
    """The part of glass box i's top that the next step up sits on: (columns, rows) of its top face, whose rows run
    from the back (+z, row 0) to the front, and columns from its left (-x)."""
    if i + 1 >= len(BOXES) or BOXES[i + 1][0] != "glass":
        return None
    b, n = BOXES[i], BOXES[i + 1]
    return range(ceil(n[1] - b[1]), ceil(n[4] - b[1])), range(0, ceil(n[6] - n[3]))


def paint(cracked):
    img = Image.new("RGBA", SHEET, (0, 0, 0, 0))
    px = img.load()
    for i, (b, (u, v)) in enumerate(zip(BOXES, pack())):
        w, h, d = size(b)
        for name, fx, fy, fw, fh in faces(u, v, w, h, d):
            if b[0] == "glass" and name in ("bottom", "south"):
                # open underneath (the cockpit) and behind (against the hull): left clear, so the glass is one pane thick
                continue
            (glass_face if b[0] == "glass" else rib_face)(px, name, fx, fy, fw, fh)
            hidden = covered(i) if b[0] == "glass" and name == "top" else None
            if hidden:
                for c in hidden[0]:
                    for r in hidden[1]:
                        px[fx + c, fy + r] = (0, 0, 0, 0)
        if cracked and b[0] == "glass":
            # a star of cracks on the front of each step
            for name, fx, fy, fw, fh in faces(u, v, w, h, d):
                if name != "north" or fw < 8 or fh < 6:
                    continue
                cx, cy = fx + fw // 3, fy + fh // 2
                for dx, dy in ((1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (1, -1), (0, -1)):
                    x, y = cx, cy
                    for _ in range(random.randint(3, 7)):
                        x += dx + random.choice((0, 0, 1, -1)) * (dy != 0)
                        y += dy + random.choice((0, 0, 1, -1)) * (dx != 0)
                        if fx < x < fx + fw - 1 and fy < y < fy + fh - 1:
                            px[x, y] = tone(GLINT, 1.0, 230)
    return img


def table():
    lines = []
    for b, (u, v) in zip(BOXES, pack()):
        _, x0, y0, z0, x1, y1, z1 = b
        x, y, z = x0 - HINGE[0], y0 - HINGE[1], z0 - HINGE[2]
        lines.append("\t\t{%d, %d, %sF, %sF, %sF, %sF, %sF, %sF}," % (u, v, x, y, z, x1 - x0, y1 - y0, z1 - z0))
    return "\n".join(lines)


def main():
    paint(False).save(OUT)
    paint(True).save(OUT_CRACKED)
    src = open(JAVA).read()
    a = src.index("// cubes:start") + len("// cubes:start\n")
    b = src.index("\t\t// cubes:end")
    open(JAVA, "w").write(src[:a] + table() + "\n" + src[b:])


main()
