"""The space pod's item icon and its flight screen picture (Andy, 2026-11-25: "the space pod item icon, flight png and in
game model need work"), drawn after the pod as it now stands in game: Alex's Caves' submarine seen from its side, its
copper hull and core, the slate motor behind, a thruster arm under the bow, the periscope, and our glass canopy over the
cockpit with the pilot's head in it (client PodSubmarine, PodCanopy).

The shapes are the model's own boxes in side view (his hull's pixels: z along, front to the right; y down), laid out
here as rectangles, rasterised at each picture's size, and shaded like vanilla's items (vstyle's rules: outline in the
part's darkest shade, lit from the top-left, a bright band inside the lit edge). Copper is vanilla's copper ramp; the
slate and the glass are ramps in the submarine's own colours. No pixels of his textures are used.

Run from the repository root:  python -I tools/vanilla_style/space_pod.py
"""
import os, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from vstyle import MAT, ramp, hx, parts

ICON = 'src/main/resources/assets/jaymospowers/textures/item/space_pod.png'
FLIGHT = 'src/main/resources/assets/jaymospowers/textures/gui/space_pod_flight.png'

COPPER = MAT['copper']
SLATE = ramp('24252f', '363744', '454759', '53556c', '686a82', '8a8ca3', 'b4b6c8')
GLASS = ramp('2c5568', '3f7488', '5c98ae', '7fb8cc', 'a6d5e4', 'cdebf3', 'f4fcff')
SKIN, HAIR = hx('c08a6a'), hx('4a3020')
SCREEN = hx('43e88d')

# (part, ramp, [(z0, y0, z1, y1)...]) in his hull's pixels, drawn in this order (later on top)
PARTS = [
    ('motor', SLATE, [(25, -7, 32, 11), (32, -12, 50, 16)]),
    ('core', COPPER, [(-7, -22, 25, 24), (7, -26, 23, -20)]),
    ('keel', COPPER, [(-30, 15, 21, 30)]),
    ('glass', GLASS, [(-36, -6, -7, 8), (-33, -16, -7, -6), (-28, -21, -7, -16)]),
    ('wall', COPPER, [(-30, 8, -7, 24), (-37, 8, -30, 24)]),
    ('scope', SLATE, [(0, -42, 6, -22), (-7, -42, 0, -36)]),
    ('arm', COPPER, [(-51, 16, 0, 35)]),
    ('fan', SLATE, [(-54, 17, -51, 34), (-57, 23, -54, 28)]),
]
# the pilot's head under the glass, and the glowing panel by the wall's top
HEAD = [(-17, -16, -9, -8)]
HAIR_BOX = [(-17, -16, -9, -14)]


def raster(f, z_back, y_top, w, h):
    """Each part's pixels at f picture pixels to his pixel: x runs from the front (right) back, y down."""
    def px(z, y):
        return int(round((z_back - z) * f)), int(round((y - y_top) * f))
    masks = []
    for name, rmp, rects in PARTS:
        m = set()
        for z0, y0, z1, y1 in rects:
            xa, ya = px(z1, y0)
            xb, yb = px(z0, y1)
            for x in range(max(0, xa), min(w, xb)):
                for y in range(max(0, ya), min(h, yb)):
                    m.add((x, y))
        masks.append((name, rmp, m))
    extra = {}
    for boxes, col in ((HEAD, SKIN), (HAIR_BOX, HAIR)):
        for z0, y0, z1, y1 in boxes:
            xa, ya = px(z1, y0)
            xb, yb = px(z0, y1)
            for x in range(xa, xb):
                for y in range(ya, yb):
                    extra[(x, y)] = col
    return masks, extra, px


def shade_part(m):
    """Levels 0-6 for one part's pixels at any size: vstyle's outline (darkest shade, one step lighter on the lit
    top-left), and inside it the box shading below."""
    out = {}
    for (x, y) in m:
        n = [(x, y - 1), (x - 1, y), (x, y + 1), (x + 1, y)]
        if not all(p in m for p in n):
            lit = (x, y - 1) not in m or (x - 1, y) not in m
            dark = (x, y + 1) not in m or (x + 1, y) not in m
            out[(x, y)] = 1 if lit and not dark else 0
            continue
        # boxes, not blades: a bright band under the lit top and left edges, a shade row over the bottom, and the face
        # between lit above and shaded below
        up = 0
        while (x, y - up - 1) in m:
            up += 1
        down = 0
        while (x, y + down + 1) in m:
            down += 1
        # (vanilla's copper drops sharply below its 4th shade, so the faces keep to the top three)
        if up == 0 or (x - 1, y - 1) not in m and (x - 1, y) not in m:
            out[(x, y)] = 5
        elif down == 0:
            out[(x, y)] = 3
        else:
            out[(x, y)] = 4
    return out


def paint(w, h, f, z_back, y_top, seams):
    img = Image.new('RGBA', (w, h))
    p = img.load()
    masks, extra, at = raster(f, z_back, y_top, w, h)
    for name, rmp, m in masks:
        lv = shade_part(m)
        for (x, y), l in lv.items():
            if name == 'glass' and (x, y) in extra and l >= 2:
                # the pilot behind the glass, tinted by it
                c = extra[(x, y)]
                g = rmp[3]
                p[x, y] = tuple((a + b) // 2 for a, b in zip(c, g)) + (255,)
                continue
            # the periscope is too thin for an outline of its own: it is drawn in its lit shades
            p[x, y] = rmp[l + 3 if name == 'scope' and l < 2 else l] + (255,)
    if seams:
        # panel seams and rivets on the copper, and the cockpit's lit panel, only where there is room for them
        core = [m for n, r, m in masks if n == 'core'][0]
        arm = [m for n, r, m in masks if n == 'arm'][0]
        for m in (core, arm):
            xs = sorted({x for x, y in m})
            ys = sorted({y for x, y in m})
            mid = ys[len(ys) // 2]
            for x in xs[2:-2]:
                if (x, mid) in m and (x, mid - 1) in m and (x, mid + 1) in m:
                    p[x, mid] = COPPER[2] + (255,)
            for x in xs[2:-2:3]:
                for y in (ys[2], ys[-3]):
                    if (x, y) in m:
                        p[x, y] = COPPER[5] + (255,)
        x, y = at(-20, 9)
        p[x, y] = SCREEN + (255,)
    return img


# the icon, the same side view drawn by hand at 16px (his shape squeezed taller to read at that size): m motor, c core,
# k keel, g glass (P the pilot's head in it), w the cockpit's wall, s periscope, a thruster arm, f its propeller
ICON_GRID = """
................
......sss.......
......s.........
....ccccc.......
....ccccc.ggg...
....cccccggggg..
mm..cccccgPPggg.
mmm.cccccgPPggg.
mmmmcccccggggggg
mmm.cccccwwwwwww
mm..cccccwwwwwww
....kkkkkkkkkkk.
.....aaaaaaaaaaf
.....aaaaaaaaaaf
......aaaaaaaaa.
................
"""


def icon():
    ramps = {'m': SLATE, 'c': COPPER, 'k': COPPER, 'g': GLASS, 'w': COPPER, 's': SLATE, 'a': COPPER, 'f': SLATE}
    g = [r for r in ICON_GRID.strip('\n').split('\n')]
    assert len(g) == 16 and all(len(r) == 16 for r in g)
    img = Image.new('RGBA', (16, 16))
    p = img.load()
    # the core, the cockpit's wall and the keel are one copper hull, shaded together
    for part in ('m', 'c', 'g', 's', 'a', 'f'):
        mine = {part, 'P'} if part == 'g' else {'c', 'w', 'k'} if part == 'c' else {part}
        m = {(x, y) for y in range(16) for x in range(16) if g[y][x] in mine}
        for (x, y), l in shade_part(m).items():
            if g[y][x] == 'P':
                p[x, y] = tuple((a + b) // 2 for a, b in zip(SKIN if y > 6 else HAIR, GLASS[3])) + (255,)
            else:
                p[x, y] = ramps[part][l + 3 if part == 's' and l < 2 else l] + (255,)
    # the seam where the cockpit meets the core, and the keel's line under them
    for y in range(9, 11):
        p[9, y] = COPPER[2] + (255,)
    for x in range(5, 14):
        if g[11][x] == 'k' and p[x, 11][3]:
            p[x, 11] = COPPER[2] + (255,)
    return img


def main():
    icon().save(ICON)
    os.makedirs(os.path.dirname(FLIGHT), exist_ok=True)
    paint(48, 34, 0.42, 57, -44, True).save(FLIGHT)


main()
