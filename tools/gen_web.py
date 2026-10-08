"""Generates the Web Slinger assets: the web ball item texture (also what the Web Shot projectile looks like) and its
item model files. Hand-drawn 16x16 pixel grid, one char per pixel, so no vanilla art is copied.
Run from the project root:  python tools/gen_web.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"

PALETTE = {
    ".": (0, 0, 0, 0),
    "w": (250, 250, 250, 255),  # bright strand
    "s": (214, 216, 222, 255),  # strand
    "g": (168, 172, 182, 255),  # shadowed strand
    "d": (120, 124, 136, 255),  # deep shadow / outline
    "f": (236, 238, 244, 170),  # translucent film between strands
}

# a clump of web: a ball of crossing strands with a few loose threads trailing off
WEB_BALL = [
    "................",
    ".......s........",
    "...s..dsd...s...",
    "....sdgwsgd.s...",
    "...dfswfwsfd....",
    "..dgwffsffwgd...",
    ".sswsfwswfswss..",
    "..dfsfswsfsfd...",
    "..gwffwswffwg.s.",
    "..dgsfswsfsgd...",
    "...dgwfsfwgd....",
    "....ddgwgdd.....",
    "...s..dsd..s....",
    "..s....s....s...",
    "................",
    "................",
]


def draw(grid):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(grid):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def item(name, grid):
    tex = ROOT / "textures" / "item" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    draw(grid).save(tex)
    model = ROOT / "models" / "item" / f"{name}.json"
    model.parent.mkdir(parents=True, exist_ok=True)
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymospowers:item/{name}"}}, indent=2) + "\n")
    definition = ROOT / "items" / f"{name}.json"
    definition.parent.mkdir(parents=True, exist_ok=True)
    definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{name}"}}, indent=2) + "\n")


# ---- the symbiote (0.2.0) -------------------------------------------------------------------------------------------
# Everything below is generated from code (noise, random walks, hand-made 8x8 grids); nothing is copied from anywhere.

DATA = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "jaymospowers"

GOO_FRAMES = 8
GOO_BASE = (14, 12, 18)
GOO_DEEP = (6, 5, 9)
GOO_SHEEN = (74, 42, 102)
GOO_GLINT = (190, 176, 214)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def goo_texture():
    """An animated strip of glossy black goo: slow purple sheen bands drift across it, with a few wet glints."""
    import math
    import random

    rnd = random.Random(1987)
    # fixed lumpy darkness, so the surface has some depth
    lumps = [[rnd.random() for _ in range(16)] for _ in range(16)]
    glints = [(rnd.randrange(16), rnd.randrange(16), rnd.random() * math.tau) for _ in range(5)]
    img = Image.new("RGBA", (16, 16 * GOO_FRAMES))
    for f in range(GOO_FRAMES):
        phase = f / GOO_FRAMES * math.tau
        for y in range(16):
            for x in range(16):
                depth = (lumps[y][x] + lumps[(y + 1) % 16][x] + lumps[y][(x + 1) % 16]) / 3.0
                c = lerp(GOO_DEEP, GOO_BASE, depth)
                # a diagonal sheen band that sweeps across over the animation
                band = math.sin(x * 0.55 + y * 0.35 + 1.3 * math.sin(y * 0.7 + x * 0.25) - phase)
                if band > 0.75:
                    c = lerp(c, GOO_SHEEN, (band - 0.75) / 0.25 * 0.85)
                img.putpixel((x, f * 16 + y), c + (255,))
        for gx, gy, gp in glints:
            # each glint swells and fades on its own beat
            s = (math.sin(phase + gp) + 1.0) / 2.0
            if s > 0.55:
                img.putpixel((gx, f * 16 + gy), lerp(GOO_SHEEN, GOO_GLINT, (s - 0.55) / 0.45) + (255,))
    return img


def goo_block():
    tex = ROOT / "textures" / "block" / "web_symbiote_goo.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    goo_texture().save(tex)
    (tex.parent / "web_symbiote_goo.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 5, "interpolate": True}}, indent=2) + "\n")

    def cube(frm, to):
        faces = {}
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
            faces[face] = entry
        return {"from": frm, "to": to, "faces": faces}

    # a low, lumpy blob: a wide base, a mound on it, a little crown, and two drips to one side
    model = {
        "parent": "minecraft:block/block",
        "textures": {"goo": "jaymospowers:block/web_symbiote_goo", "particle": "jaymospowers:block/web_symbiote_goo"},
        "elements": [
            cube([2, 0, 2], [14, 3, 14]),
            cube([1, 0, 4], [15, 2, 12]),
            cube([4, 0, 1], [12, 2, 15]),
            cube([4, 3, 4], [12, 5, 12]),
            cube([5, 5, 6], [10, 7, 11]),
            cube([12, 0, 12], [14, 1, 15]),
            cube([0, 0, 6], [2, 1, 8]),
        ],
    }
    model_path = ROOT / "models" / "block" / "web_symbiote_goo.json"
    model_path.parent.mkdir(parents=True, exist_ok=True)
    model_path.write_text(json.dumps(model, indent=2) + "\n")
    blockstate = ROOT / "blockstates" / "web_symbiote_goo.json"
    blockstate.parent.mkdir(parents=True, exist_ok=True)
    blockstate.write_text(json.dumps({"variants": {"": {"model": "jaymospowers:block/web_symbiote_goo"}}}, indent=2) + "\n")


SUIT_BLACK = (16, 14, 20)
SUIT_DEEP = (8, 7, 11)
SUIT_VEIN = (44, 28, 60)
SUIT_VEIN_HI = (78, 50, 106)
EYE_WHITE = (238, 238, 244)
EYE_EDGE = (168, 166, 182)

# the face (8x8, front of the head): two angular white eye-patches, mirrored. W = white, g = soft edge
FACE_LEFT = [
    "....",
    "g...",
    "WWg.",
    "WWW.",
    "gWW.",
    "..W.",
    "....",
    "....",
]
# the right eye mirrors the left exactly
FACE = [row + row[::-1] for row in FACE_LEFT]

# regions of a 64x64 player skin: (x0, y0, x1, y1), base layer → its outer layer
SKIN_PARTS = [
    ((0, 0, 32, 16), (32, 0, 64, 16)),     # head → hat
    ((16, 16, 40, 32), (16, 32, 40, 48)),  # body → jacket
    ((40, 16, 56, 32), (40, 32, 56, 48)),  # right arm → sleeve
    ((0, 16, 16, 32), (0, 32, 16, 48)),    # right leg → trousers
    ((16, 48, 32, 64), (0, 48, 16, 64)),   # left leg → trousers
    ((32, 48, 48, 64), (48, 48, 64, 64)),  # left arm → sleeve
]


def suit_texture():
    """The black suit over the whole player skin: mottled black, a web of dark purple veins, white eye-patches."""
    import random

    rnd = random.Random(31337)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    inside = set()
    for (x0, y0, x1, y1), _ in SKIN_PARTS:
        for y in range(y0, y1):
            for x in range(x0, x1):
                inside.add((x, y))
                img.putpixel((x, y), lerp(SUIT_DEEP, SUIT_BLACK, 0.55 + rnd.random() * 0.45) + (255,))
    # veins: wandering random walks that branch, kept inside the skin's used areas
    for _ in range(26):
        x, y = rnd.choice(sorted(inside))
        dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        for step in range(rnd.randint(6, 16)):
            if (x, y) not in inside:
                break
            img.putpixel((x, y), (SUIT_VEIN_HI if rnd.random() < 0.25 else SUIT_VEIN) + (255,))
            if rnd.random() < 0.35:
                dx, dy = rnd.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
            x, y = x + dx, y + dy
    # the face: head front is (8, 8)-(16, 16)
    for fy, row in enumerate(FACE):
        for fx, ch in enumerate(row):
            if ch == "W":
                img.putpixel((8 + fx, 8 + fy), EYE_WHITE + (255,))
            elif ch == "g":
                img.putpixel((8 + fx, 8 + fy), EYE_EDGE + (255,))
    # the outer layer matches the base, so a skin's own hat/jacket/sleeves are covered too
    for (bx0, by0, bx1, by1), (ox0, oy0, _, _) in SKIN_PARTS:
        for y in range(by0, by1):
            for x in range(bx0, bx1):
                img.putpixel((ox0 + x - bx0, oy0 + y - by0), img.getpixel((x, y)))
    return img


def suit():
    tex = ROOT / "textures" / "entity" / "web_symbiote_suit.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    suit_texture().save(tex)


# Meteor craters: ~1 attempt per CRATER_RARITY chunks in overworld land biomes. Many attempts are refused (water,
# slopes, trees, structures), so this is a bit below the ~600 target to land near it.
CRATER_RARITY = 500


def crater_worldgen():
    feature = DATA / "worldgen" / "feature" / "web_crater.json"
    feature.parent.mkdir(parents=True, exist_ok=True)
    feature.write_text(json.dumps({"type": "jaymospowers:web_crater"}, indent=2) + "\n")
    placed = DATA / "worldgen" / "placed_feature" / "web_crater.json"
    placed.parent.mkdir(parents=True, exist_ok=True)
    placed.write_text(json.dumps({
        "feature": "jaymospowers:web_crater",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": CRATER_RARITY},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"},
        ],
    }, indent=2) + "\n")


if __name__ == "__main__":
    item("web_ball", WEB_BALL)
    goo_block()
    suit()
    crater_worldgen()
    print("web assets written")
