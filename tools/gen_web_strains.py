"""Generates the symbiote strains' art (0.13.0): the white strain and the red strain.

Freshly painted here, pixel by pixel from code (nothing copied from any other mod or from vanilla):
  textures/entity/web_symbiote_suit_white.png  a white suit, ragged black patches over the shoulders and the face,
                                               black-rimmed white eyes
  textures/entity/web_symbiote_suit_red.png    a red suit coursed with black veins, jagged black edges at the neck,
                                               wrists and ankles, white slit eyes
  textures/block/web_symbiote_red_goo.png      Red Ooze, animated (+ .mcmeta, the block model and blockstate)

Recoloured from our own symbiote art (tools/gen_web_hulk.py, gen_web_tendril.py, gen_web_veins.py and gen_web.py's
goo; the inputs are read from the textures folder, so run those first if they change):
  web_symbiote_hulk_*, web_symbiote_flesh_*, web_symbiote_mouth_*, web_symbiote_goo_*, web_symbiote_tendril_*,
  web_symbiote_veins_{0..3}_*  (each _white and _red). Eye whites are kept (rimmed in black on the white strain); bone
  and the red of the tongue are kept; the black becomes white (with noise-shaped black patches) or red (with black
  veins drawn over it).

And the red craters' worldgen (worldgen/feature/web_red_crater.json, placed_feature/web_red_crater.json).

The client picks the files by name (client WebStrainLook.texture: <black name>_white.png / _red.png).
Run from the project root:  python3 -I tools/gen_web_strains.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent
ASSETS = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers"
DATA = PROJECT / "src" / "main" / "resources" / "data" / "jaymospowers"
ENTITY = ASSETS / "textures" / "entity"


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# ---- the player-skin layout (the same regions the black suit uses: tools/gen_web.py) --------------------------------
SKIN_PARTS = [
    ((0, 0, 32, 16), (32, 0, 64, 16)),     # head -> hat
    ((16, 16, 40, 32), (16, 32, 40, 48)),  # body -> jacket
    ((40, 16, 56, 32), (40, 32, 56, 48)),  # right arm -> sleeve
    ((0, 16, 16, 32), (0, 32, 16, 48)),    # right leg -> trousers
    ((16, 48, 32, 64), (0, 48, 16, 64)),   # left leg -> trousers
    ((32, 48, 48, 64), (48, 48, 64, 64)),  # left arm -> sleeve
]
SKIN_BOXES = [(8, 8, 8), (8, 12, 4), (4, 12, 4), (4, 12, 4), (4, 12, 4), (4, 12, 4)]
HEAD, BODY, RIGHT_ARM, RIGHT_LEG, LEFT_LEG, LEFT_ARM = range(6)


def faces(region, size):
    """Yields (x, y, face, u, v) for every pixel of a box's skin region: face is 'top', 'bottom' or 'side'; on the
    sides u runs round the box (0 at the front's left edge, negative on the right side) and v from 0 (top) to 1."""
    (x0, y0, x1, y1), (w, h, d) = region, size
    for y in range(y0, y1):
        for x in range(x0, x1):
            if y < y0 + d:
                face = "top" if x < x0 + d + w else "bottom"
                yield x, y, face, x - x0 - d, (y - y0) / d
            else:
                u = (x - x0) % (2 * (w + d)) - d
                yield x, y, "side", u, (y - y0 - d + 0.5) / h


def copy_outer(img):
    """The outer layer matches the base, so a skin's own hat, jacket and sleeves are covered too."""
    for (bx0, by0, bx1, by1), (ox0, oy0, _, _) in SKIN_PARTS:
        for y in range(by0, by1):
            for x in range(bx0, bx1):
                img.putpixel((ox0 + x - bx0, oy0 + y - by0), img.getpixel((x, y)))


def put_face(img, grid, colours):
    for fy, row in enumerate(grid):
        for fx, ch in enumerate(row):
            if ch in colours:
                img.putpixel((8 + fx, 8 + fy), colours[ch] + (255,))


# ---- the white suit -------------------------------------------------------------------------------------------------
W_DEEP = (184, 182, 174)
W_BASE = (236, 235, 229)
W_LIGHT = (250, 249, 244)
W_GOLD = (232, 214, 150)
PATCH = (17, 16, 20)
PATCH_DEEP = (8, 8, 10)
PATCH_FRAY = (58, 56, 62)

# the face (8x8): a ragged black mask over the eyes, and in it two big round-cornered white eyes rimmed in black.
# W white, g soft edge, k black (the mask and the rim), . the white suit
WHITE_FACE_LEFT = [
    "k...",
    "kk.k",
    "kkkk",
    "kWWk",
    "WWWk",
    "gWWk",
    "kkk.",
    ".k..",
]
WHITE_FACE = [row + row[::-1] for row in WHITE_FACE_LEFT]


def white_suit():
    rnd = random.Random(7331)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for part, ((region, _), size) in enumerate(zip(SKIN_PARTS, SKIN_BOXES)):
        w, h, d = size
        for x, y, face, u, v in faces(region, size):
            c = lerp(W_DEEP, W_BASE, 0.7 + rnd.random() * 0.3)
            if face == "top":
                c = lerp(c, W_LIGHT, 0.6)
            elif face == "side":
                glow = math.exp(-((v - 0.12) / 0.18) ** 2) * 0.5
                c = lerp(c, W_LIGHT, glow)
                # a warm, pale gold sheen low on each face, like light through it
                c = lerp(c, W_GOLD, 0.18 * max(0.0, v - 0.55))
            black = False
            if part in (RIGHT_ARM, LEFT_ARM):
                # over the shoulders: the whole top, and ragged down the upper arm
                reach = 0.32 + 0.1 * math.sin(u * 1.9 + part) + rnd.random() * 0.12
                black = face == "top" or face == "side" and v < reach
            elif part == BODY:
                # the shoulders' black runs onto the top of the chest and back at the outer edges
                # how far in from the outer edge of the front or the back (the sides are all edge)
                edge = min(u, w - 1 - u) if 0 <= u < w else min(u - w - d, 2 * w + d - 1 - u) if u >= w + d else 0
                if face == "top":
                    black = rnd.random() < 0.85
                elif face == "side":
                    reach = 0.22 - 0.06 * edge + rnd.random() * 0.08
                    black = v < reach
            elif part == HEAD:
                # streaks of black back over the head from the mask
                if face == "side" and not (0 <= u < w):
                    black = abs(v - 0.45) < 0.16 + rnd.random() * 0.1
                elif face == "top":
                    black = abs(u - 3.5) < 1.2 and rnd.random() < 0.7
            if black:
                c = lerp(PATCH_DEEP, PATCH, 0.6 + rnd.random() * 0.4)
            img.putpixel((x, y), c + (255,))
    # frayed edges: a grey thread where black meets white, now and then
    base = img.copy()
    for y in range(64):
        for x in range(64):
            p = base.getpixel((x, y))
            if p[3] == 0 or sum(p[:3]) > 200:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = base.getpixel(((x + dx) % 64, (y + dy) % 64))
                if q[3] and sum(q[:3]) > 400 and rnd.random() < 0.25:
                    img.putpixel((x, y), PATCH_FRAY + (255,))
                    break
    put_face(img, WHITE_FACE, {"W": W_LIGHT, "g": (206, 208, 214), "k": PATCH_DEEP})
    copy_outer(img)
    return img


# ---- the red suit ---------------------------------------------------------------------------------------------------
R_DEEP = (92, 8, 14)
R_BASE = (156, 18, 26)
R_LIGHT = (204, 52, 56)
VEIN = (14, 6, 8)
VEIN_EDGE = (52, 6, 10)
SLIT = (246, 244, 240)

# the face (8x8): white slits slanting down to the nose, in black, under a black brow
RED_FACE_LEFT = [
    "....",
    "....",
    "kk..",
    "WWk.",
    "kWWk",
    ".kkk",
    "....",
    "....",
]
RED_FACE = [row + row[::-1] for row in RED_FACE_LEFT]


def walk_veins(rnd, rx, ry, rw, rh, count):
    veins = []
    for _ in range(count):
        x, y = rx + rnd.uniform(0, rw), ry + rnd.uniform(0, rh)
        angle = rnd.uniform(0, math.tau)
        path = []
        for _ in range(rnd.randint(6, 16)):
            path.append((int(x), int(y)))
            angle += rnd.uniform(-0.7, 0.7)
            x += math.cos(angle)
            y += math.sin(angle)
            if not (rx <= x < rx + rw and ry <= y < ry + rh):
                break
            if rnd.random() < 0.15:
                bx, by, ba = x, y, angle + rnd.choice((-1, 1)) * rnd.uniform(0.6, 1.3)
                branch = []
                for _ in range(rnd.randint(2, 5)):
                    branch.append((int(bx), int(by)))
                    bx += math.cos(ba)
                    by += math.sin(ba)
                    if not (rx <= bx < rx + rw and ry <= by < ry + rh):
                        break
                veins.append(branch)
        veins.append(path)
    return veins


def red_suit():
    rnd = random.Random(6660)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for part, ((region, _), size) in enumerate(zip(SKIN_PARTS, SKIN_BOXES)):
        w, h, d = size
        x0, y0, x1, y1 = region
        for x, y, face, u, v in faces(region, size):
            c = lerp(R_DEEP, R_BASE, 0.7 + rnd.random() * 0.3)
            if face == "top":
                c = lerp(c, R_LIGHT, 0.4)
            elif face == "side":
                c = lerp(c, R_LIGHT, math.exp(-((v - 0.12) / 0.16) ** 2) * 0.45)
                c = lerp(c, R_DEEP, 0.4 * max(0.0, v - 0.6))
                # wiry, jagged black edges: teeth up from the wrists and ankles, down from the neck
                tooth = (2, 1, 3, 0, 2, 1)[(x * 7 + part) % 6]
                rows_from_bottom = (1.0 - v) * h
                if part in (RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG) and rows_from_bottom < tooth + 0.5:
                    c = lerp(VEIN, VEIN_EDGE, rnd.random() * 0.4)
                if part == BODY and v * h < tooth * 0.7:
                    c = lerp(VEIN, VEIN_EDGE, rnd.random() * 0.4)
            img.putpixel((x, y), c + (255,))
        # black veins coursing over every face of it
        for path in walk_veins(rnd, x0, y0, x1 - x0, y1 - y0, max(2, (x1 - x0) * (y1 - y0) // 90)):
            for i, (vx, vy) in enumerate(path):
                if x0 <= vx < x1 and y0 <= vy < y1:
                    img.putpixel((vx, vy), (VEIN if i % 4 else VEIN_EDGE) + (255,))
    # (drawn last, over any vein that crossed the face)
    put_face(img, RED_FACE, {"W": SLIT, "k": VEIN})
    copy_outer(img)
    return img


# ---- recolouring our own black symbiote art ------------------------------------------------------------------------
def classify(p):
    """'clear', 'eye' (a cold white), 'bone' (a warm white), 'red' (tongue, gums, throat), 'vein' or 'black'."""
    r, g, b, a = p
    if a == 0:
        return "clear"
    lum = 0.3 * r + 0.59 * g + 0.11 * b
    if lum > 150 and b >= r - 2:
        return "eye"
    if lum > 120:
        return "bone"
    if r > g + 40 and r > b + 10 and r > 70:
        return "red"
    return "black"


def value_noise(seed, w, h, cell):
    rnd = random.Random(seed)
    gw, gh = w // cell + 2, h // cell + 2
    grid = [[rnd.random() for _ in range(gw)] for _ in range(gh)]

    def at(x, y):
        fx, fy = x / cell, y / cell
        ix, iy = int(fx), int(fy)
        tx, ty = fx - ix, fy - iy
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        top = grid[iy][ix] + (grid[iy][ix + 1] - grid[iy][ix]) * tx
        bot = grid[iy + 1][ix] + (grid[iy + 1][ix + 1] - grid[iy + 1][ix]) * tx
        return top + (bot - top) * ty
    return at


def recolour(src, strain, seed, patches=True):
    """The black symbiote's texture in the white or red strain's colours (see the module notes)."""
    img = src.convert("RGBA")
    w, h = img.size
    px = img.load()
    kinds = [[classify(px[x, y]) for x in range(w)] for y in range(h)]
    lums = sorted(0.3 * px[x, y][0] + 0.59 * px[x, y][1] + 0.11 * px[x, y][2]
                  for y in range(h) for x in range(w) if kinds[y][x] == "black")
    lo = lums[len(lums) // 20] if lums else 0.0
    hi = lums[len(lums) * 19 // 20] if lums else 1.0
    span = max(1.0, hi - lo)
    noise = value_noise(seed, w, h, max(3, w // 12))
    rnd = random.Random(seed + 1)
    out = Image.new("RGBA", (w, h))
    o = out.load()
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            kind = kinds[y][x]
            if kind in ("clear", "bone", "eye"):
                o[x, y] = p
                continue
            if kind == "red":
                o[x, y] = p if strain == "red" else lerp(p[:3], (196, 70, 80), 0.3) + (p[3],)
                continue
            t = ((0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]) - lo) / span
            bluish = p[2] > p[0] + 8
            if strain == "white":
                c = lerp((176, 174, 166), (250, 249, 244), 0.25 + 0.75 * t)
                if bluish and t > 0.5:
                    c = lerp(c, (236, 220, 160), 0.2)
                if patches and noise(x, y) > 0.68 + rnd.random() * 0.04:
                    c = lerp((8, 8, 10), (30, 29, 34), t)
            else:
                c = lerp((70, 6, 10), (204, 44, 48), 0.2 + 0.8 * t)
                if bluish and t > 0.5:
                    c = lerp(c, (230, 80, 80), 0.25)
            o[x, y] = c + (p[3],)
    if strain == "white":
        # eyes keep their white, rimmed in black
        for y in range(h):
            for x in range(w):
                if kinds[y][x] != "black":
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and kinds[ny][nx] == "eye":
                        o[x, y] = (6, 6, 8, px[x, y][3])
                        break
    elif patches:
        # black veins over the red
        count = max(3, w * h // 160)
        for path in walk_veins(rnd, 0, 0, w, h, count):
            for i, (vx, vy) in enumerate(path):
                if 0 <= vx < w and 0 <= vy < h and kinds[vy][vx] == "black":
                    o[vx, vy] = ((14, 6, 8) if i % 3 else (48, 6, 10)) + (px[vx, vy][3],)
    return out


VEIN_COLOURS = {
    # (dark, lit, core) for the pulsing veins: the white strain's are a pale gold, the red one's black with a red core
    "white": ((150, 140, 110), (214, 196, 130), (255, 246, 206)),
    "red": ((18, 4, 6), (64, 8, 12), (255, 72, 60)),
}


def recolour_veins(src, strain):
    """The pulsing veins (gen_web_veins.py: dark, lit and core purples) in the strain's colours, frame for frame."""
    img = src.convert("RGBA")
    dark, lit, core = VEIN_COLOURS[strain]
    out = Image.new("RGBA", img.size)
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            lum = 0.3 * r + 0.59 * g + 0.11 * b
            c = core if lum > 130 else lit if lum > 50 else dark
            out.putpixel((x, y), c + (a,))
    return out


# ---- Red Ooze (the block) -------------------------------------------------------------------------------------------
GOO_FRAMES = 8
RED_GOO_BASE = (118, 12, 20)
RED_GOO_DEEP = (58, 5, 9)
RED_GOO_SHEEN = (16, 4, 6)
RED_GOO_GLINT = (236, 120, 110)


def red_goo_texture():
    """An animated strip of glossy red goo: slow black sheen bands drift across it, with a few wet glints."""
    rnd = random.Random(1500)
    lumps = [[rnd.random() for _ in range(16)] for _ in range(16)]
    glints = [(rnd.randrange(16), rnd.randrange(16), rnd.random() * math.tau) for _ in range(5)]
    img = Image.new("RGBA", (16, 16 * GOO_FRAMES))
    for f in range(GOO_FRAMES):
        phase = f / GOO_FRAMES * math.tau
        for y in range(16):
            for x in range(16):
                depth = (lumps[y][x] + lumps[(y + 1) % 16][x] + lumps[y][(x + 1) % 16]) / 3.0
                c = lerp(RED_GOO_DEEP, RED_GOO_BASE, depth)
                band = math.sin(x * 0.5 - y * 0.4 + 1.1 * math.sin(y * 0.6 + x * 0.3) - phase)
                if band > 0.72:
                    c = lerp(c, RED_GOO_SHEEN, (band - 0.72) / 0.28 * 0.8)
                img.putpixel((x, f * 16 + y), c + (255,))
        for gx, gy, gp in glints:
            s = (math.sin(phase + gp) + 1.0) / 2.0
            if s > 0.55:
                img.putpixel((gx, f * 16 + gy), lerp(RED_GOO_BASE, RED_GOO_GLINT, (s - 0.55) / 0.45) + (255,))
    return img


def cube(frm, to):
    faces_ = {}
    for face in ("down", "up", "north", "south", "west", "east"):
        if face in ("up", "down"):
            uv = [frm[0], frm[2], to[0], to[2]]
        elif face in ("north", "south"):
            uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
        else:
            uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        entry = {"uv": uv, "texture": "#goo"}
        if face == "down" and frm[1] == 0:
            entry["cullface"] = "down"
        faces_[face] = entry
    return {"from": frm, "to": to, "faces": faces_}


def red_goo_block():
    name = "web_symbiote_red_goo"
    tex = ASSETS / "textures" / "block" / f"{name}.png"
    red_goo_texture().save(tex)
    (tex.parent / f"{name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 5, "interpolate": True}}, indent=2) + "\n")
    # a lumpier, spikier heap than the black one: a base, a mound, and three jagged points
    model = {
        "parent": "minecraft:block/block",
        "textures": {"goo": f"jaymospowers:block/{name}", "particle": f"jaymospowers:block/{name}"},
        "elements": [
            cube([2, 0, 2], [14, 3, 14]),
            cube([1, 0, 5], [15, 2, 11]),
            cube([5, 0, 1], [11, 2, 15]),
            cube([4, 3, 4], [12, 5, 12]),
            cube([5, 5, 5], [8, 8, 8]),
            cube([9, 5, 8], [11, 9, 10]),
            cube([6, 5, 9], [8, 7, 11]),
            cube([13, 0, 1], [15, 1, 3]),
        ],
    }
    (ASSETS / "models" / "block" / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n")
    (ASSETS / "blockstates" / f"{name}.json").write_text(json.dumps({"variants": {"": {"model": f"jaymospowers:block/{name}"}}}, indent=2) + "\n")


# ---- red craters -----------------------------------------------------------------------------------------------------
# One attempt per RED_CRATER_RARITY chunks in the same overworld land biomes as the black craters (WebSymbioteStrains).
RED_CRATER_RARITY = 1500


def red_crater_worldgen():
    feature = DATA / "worldgen" / "feature" / "web_red_crater.json"
    feature.write_text(json.dumps({"type": "jaymospowers:web_red_crater"}, indent=2) + "\n")
    placed = DATA / "worldgen" / "placed_feature" / "web_red_crater.json"
    placed.write_text(json.dumps({
        "feature": "jaymospowers:web_red_crater",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": RED_CRATER_RARITY},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"},
        ],
    }, indent=2) + "\n")


def main():
    white_suit().save(ENTITY / "web_symbiote_suit_white.png")
    red_suit().save(ENTITY / "web_symbiote_suit_red.png")
    for i, name in enumerate(("hulk", "flesh", "mouth", "goo", "tendril")):
        src = Image.open(ENTITY / f"web_symbiote_{name}.png")
        # the flesh swatches and the tendrils are too small for patches to read; they stay plain
        patches = name in ("hulk", "mouth", "goo")
        for j, strain in enumerate(("white", "red")):
            recolour(src, strain, 900 + i * 10 + j, patches).save(ENTITY / f"web_symbiote_{name}_{strain}.png")
    for frame in range(4):
        src = Image.open(ENTITY / f"web_symbiote_veins_{frame}.png")
        for strain in ("white", "red"):
            recolour_veins(src, strain).save(ENTITY / f"web_symbiote_veins_{frame}_{strain}.png")
    red_goo_block()
    red_crater_worldgen()
    print("strain assets written")


if __name__ == "__main__":
    main()
