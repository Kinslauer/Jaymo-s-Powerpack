"""The blossom land's building blocks (com.jaymoskingdoms.blossomland.BlossomBlocks; docs/BlossomLands-Plan.md, patch 3):
shoji panels, tatami, plaster, red and black lacquered planks, the paper lantern, the stone lantern, thatch (block, slab,
stairs) and the ward rope; and what stands inside a house (blossomland.BlossomFurnishings): the tansu, the futon, the
sunken hearth, the sliding door, and the bowl of lacquer sap. Draws their textures (original, 16 px) and writes block and item models, blockstates, loot
tables, recipes, the mineable tags and the lang part. Safe to run again. Run from the kingdoms project root:
python -I tools/gen_blossom_blocks.py"""
from PIL import Image
import json
import os
import random

NS = "jaymoskingdoms"
RES = "src/main/resources/"
ASSETS = RES + "assets/" + NS + "/"
DATA = RES + "data/" + NS + "/"

NAMES = {
    "shoji": "Shoji Panel",
    "tatami": "Tatami",
    "plaster": "Plaster",
    "red_lacquered_planks": "Red Lacquered Planks",
    "black_lacquered_planks": "Black Lacquered Planks",
    "paper_lantern": "Paper Lantern",
    "stone_lantern": "Stone Lantern",
    "thatch": "Thatch",
    "thatch_slab": "Thatch Slab",
    "thatch_stairs": "Thatch Stairs",
    "ward_rope": "Ward Rope",
    "tansu": "Tansu",
    "futon": "Futon",
    "sunken_hearth": "Sunken Hearth",
    "sliding_door": "Sliding Door",
}
AXE = ["shoji", "red_lacquered_planks", "black_lacquered_planks", "paper_lantern", "tansu", "sunken_hearth", "sliding_door"]
PICKAXE = ["plaster", "stone_lantern"]
HOE = ["tatami", "thatch", "thatch_slab", "thatch_stairs"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def tex(name):
    return NS + ":block/" + name


def mdl(name):
    return NS + ":block/" + name


# --- textures ----------------------------------------------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, k):
    return tuple(clamp(v * k) for v in c)


def canvas(fill=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), fill)


def put(img, x, y, c, a=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), tuple(c[:3]) + (a,))


def save(img, name):
    path = ASSETS + "textures/block/" + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


WOOD = [(0x3b, 0x2a, 0x1a), (0x52, 0x3a, 0x22), (0x6a, 0x4c, 0x2d), (0x82, 0x60, 0x3a)]
PAPER = [(0xd9, 0xd2, 0xbc), (0xe6, 0xe0, 0xcc), (0xf0, 0xeb, 0xda), (0xf7, 0xf3, 0xe6)]
STRAW = [(0x7a, 0x62, 0x2a), (0x96, 0x7a, 0x34), (0xb3, 0x94, 0x42), (0xcc, 0xae, 0x55), (0xdd, 0xc4, 0x6e)]
RUSH = [(0x6f, 0x7a, 0x3a), (0x86, 0x91, 0x48), (0x9b, 0xa6, 0x57), (0xb0, 0xb9, 0x6a)]
STONE = [(0x55, 0x55, 0x57), (0x6b, 0x6b, 0x6d), (0x7f, 0x7f, 0x81), (0x93, 0x93, 0x95)]
GLOW = [(0xe8, 0x9a, 0x3c), (0xf6, 0xc0, 0x5a), (0xff, 0xe0, 0x8a), (0xff, 0xf2, 0xc0)]


def draw_shoji():
    """paper behind a wooden lattice: a frame, one upright and three rails"""
    rng = random.Random(11)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, PAPER[rng.choice([1, 2, 2, 2, 3])])
    for i in range(16):
        for edge in (0, 15):
            put(img, edge, i, WOOD[1 if i % 5 else 2])
            put(img, i, edge, WOOD[1 if i % 4 else 0])
        put(img, 7, i, WOOD[2])
        put(img, 8, i, WOOD[1])
        for rail in (4, 8, 12):
            put(img, i, rail, WOOD[2 if i % 6 else 3])
    # the paper a shade darker under each rail
    for rail in (4, 8, 12):
        for x in range(1, 15):
            if x not in (7, 8):
                put(img, x, rail + 1, PAPER[0])
    save(img, "shoji")


def draw_tatami():
    """woven rush: fine rows running one way, a dark cloth border down two edges (top), and the mat's edge (side)"""
    rng = random.Random(12)
    top = canvas()
    for y in range(16):
        for x in range(16):
            c = RUSH[2 if y % 2 else 1]
            if rng.random() < 0.12:
                c = RUSH[3 if y % 2 else 0]
            put(top, x, y, c)
    for y in range(16):
        for x in (0, 1, 14, 15):
            put(top, x, y, shade((0x2c, 0x33, 0x3a), 1.0 if (y + x) % 3 else 1.25))
    save(top, "tatami_top")
    side = canvas()
    for y in range(16):
        for x in range(16):
            c = STRAW[1 if (x + y // 3) % 2 else 2]
            if rng.random() < 0.1:
                c = STRAW[0]
            put(side, x, y, c)
    for x in range(16):
        put(side, x, 0, RUSH[2 if x % 2 else 1])
        put(side, x, 1, RUSH[0])
        put(side, x, 15, STRAW[0])
    save(side, "tatami_side")


def draw_plaster():
    """lime plaster: off-white, trowelled, a few faint hairline marks"""
    rng = random.Random(13)
    img = canvas()
    base = (0xe9, 0xe5, 0xd8)
    for y in range(16):
        for x in range(16):
            put(img, x, y, shade(base, 1.0 + rng.uniform(-0.035, 0.03)))
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for i in range(rng.randrange(3, 6)):
            put(img, (x + i) % 16, (y + (i // 3)) % 16, shade(base, 0.93))
    save(img, "plaster")


def draw_lacquer(name, pal):
    """planks under lacquer: four boards, hard seams, a long highlight on each"""
    rng = random.Random(len(name))
    img = canvas()
    for y in range(16):
        board = y // 4
        for x in range(16):
            c = pal[1]
            if y % 4 == 3:
                c = pal[0]
            elif y % 4 == 0:
                c = pal[2]
            if (x + board * 5) % 16 == 0:
                c = pal[0]
            put(img, x, y, shade(c, 1.0 + rng.uniform(-0.03, 0.03)))
        start = (board * 7 + 3) % 10
        for x in range(start, start + 4):
            put(img, x % 16, board * 4 + 1, pal[3])
    save(img, name)


def draw_paper_lantern():
    """the lantern's sheet: paper ribs lit from inside on the upper 10 rows, the dark caps on the rows below"""
    img = canvas()
    for y in range(10):
        for x in range(16):
            c = GLOW[2] if 2 <= y <= 7 else GLOW[1]
            if x % 4 == 0:
                c = GLOW[0]
            if y in (0, 9):
                c = shade(GLOW[0], 0.8)
            put(img, x, y, c)
    for x in range(5, 11):
        put(img, x, 4, GLOW[3])
        put(img, x, 5, GLOW[3])
    for y in range(10, 16):
        for x in range(16):
            put(img, x, y, WOOD[0] if (x + y) % 4 else WOOD[1])
    save(img, "paper_lantern")


def draw_stone_lantern():
    """the lantern's sheet: dressed stone on the upper 10 rows, its lit window on the rows below"""
    rng = random.Random(15)
    img = canvas()
    for y in range(10):
        for x in range(16):
            c = STONE[rng.choice([1, 1, 2, 2, 2, 3])]
            if y in (0, 9) or x in (0, 15):
                c = STONE[0]
            put(img, x, y, c)
    for y in range(10, 16):
        for x in range(16):
            c = STONE[1]
            if 3 <= x <= 12 and 11 <= y <= 14:
                c = GLOW[2] if x not in (7, 8) else STONE[0]
                if y in (12, 13) and x in (5, 6, 9, 10):
                    c = GLOW[3]
            put(img, x, y, c)
    save(img, "stone_lantern")


def draw_thatch():
    """bundled straw laid in courses: long stalks, each course's butt ends in shadow"""
    rng = random.Random(16)
    img = canvas()
    for y in range(16):
        for x in range(16):
            col = (x * 7 + 3) % 5
            c = STRAW[1 + (col + (y // 8)) % 3]
            if rng.random() < 0.18:
                c = STRAW[rng.choice([0, 4])]
            if y % 8 == 7:
                c = STRAW[0]
            elif y % 8 == 0:
                c = STRAW[4] if x % 3 else STRAW[3]
            put(img, x, y, c)
    save(img, "thatch")


def draw_ward_rope():
    """a twisted straw rope along the top, three folded paper streamers hanging from it; the rest is air"""
    img = canvas()
    for x in range(16):
        put(img, x, 1, STRAW[3 if x % 3 else 4])
        put(img, x, 2, STRAW[2 if (x + 1) % 3 else 1])
        put(img, x, 3, STRAW[0 if x % 3 else 1])
    for sx in (2, 7, 12):
        zig = [(0, 4), (0, 5), (1, 5), (1, 6), (0, 6), (0, 7), (-1, 7), (-1, 8), (0, 8), (0, 9), (1, 9), (1, 10), (0, 10), (0, 11)]
        for dx, y in zig:
            put(img, sx + dx + 1, y, PAPER[3 if (y + dx) % 2 else 2])
    save(img, "ward_rope")


DARK = [(0x24, 0x18, 0x10), (0x33, 0x23, 0x17), (0x44, 0x30, 0x1f), (0x58, 0x40, 0x2a)]
IRON = [(0x2a, 0x2a, 0x2e), (0x45, 0x45, 0x4b), (0x6c, 0x6c, 0x74)]
QUILT = [(0x2c, 0x3c, 0x66), (0x38, 0x4c, 0x7e), (0x46, 0x5d, 0x94), (0xc9, 0xd0, 0xe2)]
ASH = [(0x6e, 0x6a, 0x66), (0x86, 0x82, 0x7d), (0x9c, 0x98, 0x93), (0xb2, 0xae, 0xa8)]
COAL = [(0x1a, 0x17, 0x16), (0x2c, 0x26, 0x24)]


def save_item(img, name):
    path = ASSETS + "textures/item/" + name + ".png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def boards(img, rng, pal, x0=0, y0=0, x1=16, y1=16):
    """dark boards running across, a seam every four rows"""
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(img, x, y, pal[0] if y % 4 == 3 else pal[rng.choice([1, 2, 2, 2, 3])])


def draw_tansu():
    """a chest of drawers in dark wood: iron corners, three drawers with ring pulls; the top drawer drawn out when open"""
    rng = random.Random(21)
    for name in ("tansu_front", "tansu_front_open", "tansu_side", "tansu_top"):
        img = canvas()
        boards(img, rng, DARK)
        for i in range(16):
            for edge in (0, 15):
                put(img, edge, i, DARK[0])
                put(img, i, edge, DARK[0])
        for cx, cy in ((0, 0), (15, 0), (0, 15), (15, 15)):
            for k in range(3):
                put(img, cx + (k if cx == 0 else -k), cy, IRON[1])
                put(img, cx, cy + (k if cy == 0 else -k), IRON[1])
        if name.startswith("tansu_front"):
            for top in (1, 6, 11):
                drawn = name.endswith("open") and top == 1
                for x in range(1, 15):
                    for y in range(top, top + 4):
                        put(img, x, y, COAL[0] if drawn and y < top + 3 else DARK[2 if (x + y) % 5 else 3])
                    put(img, x, top + 4, DARK[0])
                if drawn:
                    for x in range(1, 15):
                        put(img, x, top + 3, DARK[3])
                for x in (4, 11):
                    put(img, x, top + 1 + (2 if drawn else 0), IRON[2])
                    if not drawn:
                        put(img, x, top + 2, IRON[1])
        save(img, name)


def draw_futon():
    """a quilt seen from above (stitched squares), the head end a pale sheet folded back, and a pillow"""
    rng = random.Random(22)
    for name in ("futon_head", "futon_foot"):
        img = canvas()
        for y in range(16):
            for x in range(16):
                seam = x % 4 == 0 or y % 4 == 0
                put(img, x, y, QUILT[0] if seam else QUILT[rng.choice([1, 1, 2])])
        if name == "futon_head":
            for y in range(0, 9):
                for x in range(16):
                    put(img, x, y, PAPER[rng.choice([1, 2, 2, 3])] if y < 8 else PAPER[0])
        for i in range(16):
            put(img, 0, i, QUILT[0])
            put(img, 15, i, QUILT[0])
        save(img, name)
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, PAPER[rng.choice([1, 2, 2, 3])] if 0 < x < 15 and 0 < y < 15 else PAPER[0])
    save(img, "futon_pillow")
    # the item: a futon rolled and tied
    icon = canvas()
    for y in range(4, 13):
        for x in range(2, 14):
            if (x in (2, 13)) and (y in (4, 12)):
                continue
            edge = x in (2, 13) or y in (4, 12)
            put(icon, x, y, QUILT[0] if edge else QUILT[1 + (x + y) % 2])
    for y in range(4, 13):
        for x in (5, 10):
            put(icon, x, y, STRAW[3])
    for y in range(6, 11):
        put(icon, 13, y, PAPER[2])
    save_item(icon, "futon")


def draw_hearth():
    """a square of ash in a wooden frame, a few coals; lit, the coals glow"""
    rng = random.Random(23)
    for name in ("sunken_hearth_top", "sunken_hearth_top_lit"):
        img = canvas()
        for y in range(16):
            for x in range(16):
                frame = x < 3 or x > 12 or y < 3 or y > 12
                if frame:
                    put(img, x, y, WOOD[0] if x in (0, 15) or y in (0, 15) or x in (2, 13) and 2 <= y <= 13 or y in (2, 13) and 2 <= x <= 13
                        else WOOD[rng.choice([1, 2, 2])])
                else:
                    put(img, x, y, ASH[rng.choice([0, 1, 1, 2, 2, 3])])
        for x, y in ((6, 6), (7, 6), (8, 7), (9, 8), (7, 8), (6, 9), (8, 9), (9, 6), (7, 7)):
            lit = name.endswith("lit")
            put(img, x, y, GLOW[rng.choice([0, 0, 1, 2])] if lit and (x + y) % 3 else COAL[rng.choice([0, 1])])
        save(img, name)
    img = canvas()
    boards(img, rng, WOOD)
    save(img, "sunken_hearth_side")


def draw_sliding_door():
    """a shoji door: paper in a fine lattice above, a wooden panel at the foot, a finger pull; and its item"""
    rng = random.Random(24)
    top, bottom = canvas(), canvas()
    for img in (top, bottom):
        for y in range(16):
            for x in range(16):
                put(img, x, y, PAPER[rng.choice([1, 2, 2, 2, 3])])
        for i in range(16):
            put(img, 0, i, WOOD[1])
            put(img, 15, i, WOOD[1])
            for bar in (5, 10):
                put(img, bar, i, WOOD[2])
            for rail in (3, 7, 11, 15):
                put(img, i, rail, WOOD[2 if i % 6 else 3])
    for i in range(16):
        put(top, i, 0, WOOD[0])
        put(top, i, 1, WOOD[1])
        put(bottom, i, 15, WOOD[0])
    for y in range(8, 15):
        for x in range(1, 15):
            put(bottom, x, y, WOOD[0] if y == 8 else WOOD[rng.choice([1, 2, 2, 3])])
    put(bottom, 13, 2, WOOD[0])
    put(bottom, 13, 3, WOOD[0])
    save(top, "sliding_door_top")
    save(bottom, "sliding_door_bottom")
    icon = canvas()
    for y in range(16):
        for x in range(4, 12):
            src = top if y < 8 else bottom
            icon.putpixel((x, y), src.getpixel(((x - 4) * 2, (y % 8) * 2)))
    for y in range(16):
        put(icon, 4, y, WOOD[1])
        put(icon, 11, y, WOOD[1])
    for x in range(4, 12):
        put(icon, x, 0, WOOD[0])
        put(icon, x, 15, WOOD[0])
        for y in range(11, 15):
            put(icon, x, y, WOOD[2] if 4 < x < 11 else WOOD[1])
    save_item(icon, "sliding_door")


def draw_sap():
    """a wooden bowl of dark, amber-edged sap"""
    img = canvas()
    sap = [(0x3a, 0x22, 0x12), (0x5a, 0x34, 0x16), (0x8a, 0x55, 0x1e), (0xc9, 0x8a, 0x33)]
    rows = {7: (3, 12), 8: (2, 13), 9: (2, 13), 10: (3, 12), 11: (4, 11), 12: (5, 10)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            put(img, x, y, WOOD[0] if x in (a, b) or y == 12 else WOOD[2 if y < 10 else 1])
    for x in range(3, 13):
        put(img, x, 7, sap[1 if x % 3 else 2])
    for x in range(4, 12):
        put(img, x, 8, sap[0 if x % 4 else 1])
    put(img, 5, 7, sap[3])
    put(img, 6, 7, sap[3])
    for x in range(2, 14):
        put(img, x, 6, WOOD[3] if x in (2, 13) else WOOD[0])
    for x in range(3, 13):
        put(img, x, 6, sap[2 if x % 2 else 1])
    put(img, 2, 6, WOOD[2])
    put(img, 13, 6, WOOD[2])
    save_item(img, "lacquer_sap")


LEAF = [(0x2f, 0x5a, 0x2a), (0x3f, 0x76, 0x34), (0x55, 0x92, 0x40), (0x7a, 0xb0, 0x58)]
FIBRE = [(0xb9, 0xb0, 0x8e), (0xd2, 0xca, 0xa8), (0xe6, 0xdf, 0xc2), (0xf3, 0xee, 0xdc)]


def draw_ramie():
    """four stages of a nettle-like stand: thin stalks, broad toothed leaves pale underneath, taller each stage"""
    rng = random.Random(31)
    for stage, height in enumerate((4, 8, 12, 15)):
        img = canvas()
        for stalk in (3, 7, 11, 13):
            top = 16 - height + rng.choice([0, 1, 2])
            for y in range(top, 16):
                put(img, stalk, y, LEAF[0] if y % 3 else LEAF[1])
            for y in range(top, 15, 3):
                side = 1 if (y + stalk) % 2 else -1
                put(img, stalk + side, y, LEAF[2])
                put(img, stalk + 2 * side, y, LEAF[3 if stage == 3 else 2])
                put(img, stalk + side, y + 1, LEAF[1])
                put(img, stalk - side, y + 1, LEAF[2])
        if stage == 3:
            for stalk in (3, 7, 11, 13):
                put(img, stalk, 16 - height, FIBRE[1])
                put(img, stalk, 17 - height, FIBRE[0])
        save(img, "ramie_stage%d" % stage)
    seeds = canvas()
    for x, y in ((5, 6), (9, 5), (7, 9), (11, 9), (4, 11), (9, 12), (6, 13)):
        put(seeds, x, y, (0x6a, 0x58, 0x3a))
        put(seeds, x + 1, y, (0x8a, 0x76, 0x50))
        put(seeds, x, y + 1, (0x4c, 0x3e, 0x28))
    save_item(seeds, "ramie_seeds")
    hank = canvas()
    for y in range(2, 14):
        for x in range(5, 11):
            wave = (y // 2) % 2
            put(hank, x + wave, y, FIBRE[(x + y) % 3 + (1 if x in (7, 8) else 0)])
    for x in range(5, 12):
        put(hank, x, 7, WOOD[2])
    save_item(hank, "ramie_fibre")


def draw_dress():
    """the basket hat's weave with its eye slit, the straw cape's icon, and the cape as worn (the humanoid layer, 64 x 32)"""
    rng = random.Random(32)
    basket = canvas()
    for y in range(16):
        for x in range(16):
            put(basket, x, y, STRAW[1 + ((x // 2 + y // 2) % 2) * 2] if (x + y) % 4 else STRAW[0])
    for x in range(3, 13):
        put(basket, x, 9, COAL[0] if x % 2 else STRAW[0])
    save(basket, "basket_hat")
    icon = canvas()
    for y in range(2, 15):
        half = 2 + (y - 2) * 5 // 12
        for x in range(8 - half, 8 + half):
            put(icon, x, y, STRAW[0] if y in (5, 9, 13) else STRAW[rng.choice([1, 2, 2, 3, 4])])
    for x in range(6, 10):
        put(icon, x, 2, WOOD[1])
    save_item(icon, "straw_cape")
    worn = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    def straw(x0, y0, w, h):
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                row = (y - y0) % 4
                worn.putpixel((x, y), tuple(STRAW[0] if row == 3 else STRAW[rng.choice([1, 2, 2, 3, 4])]) + (255,))
    straw(16, 16, 24, 4)      # the body's top and bottom
    straw(16, 20, 24, 12)     # round the body
    straw(40, 16, 16, 4)      # the shoulders
    straw(40, 20, 16, 7)      # the upper arms
    path = ASSETS + "textures/entity/equipment/humanoid/straw_cape.png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    worn.save(path)


def textures():
    draw_ramie()
    draw_dress()
    draw_shoji()
    draw_tatami()
    draw_plaster()
    draw_lacquer("red_lacquered_planks", [(0x5c, 0x10, 0x12), (0x8e, 0x1b, 0x1c), (0xa6, 0x26, 0x24), (0xd8, 0x5a, 0x4c)])
    draw_lacquer("black_lacquered_planks", [(0x0e, 0x0d, 0x10), (0x1d, 0x1b, 0x20), (0x2a, 0x28, 0x2f), (0x62, 0x5f, 0x6c)])
    draw_paper_lantern()
    draw_stone_lantern()
    draw_thatch()
    draw_ward_rope()
    draw_tansu()
    draw_futon()
    draw_hearth()
    draw_sliding_door()
    draw_sap()


# --- models and blockstates ---------------------------------------------------------------------------------------

def item(name, model=None):
    write(ASSETS + "items/" + name + ".json", {"model": {"type": "minecraft:model", "model": model or mdl(name)}})


def cube(name):
    write(ASSETS + "models/block/" + name + ".json", {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}})
    write(ASSETS + "blockstates/" + name + ".json", {"variants": {"": {"model": mdl(name)}}})
    item(name)


BARS = ["post", "post_ends", "cap", "cap_alt", "side", "side_alt"]


def bars(name):
    t = tex(name)
    for part in BARS:
        write(ASSETS + "models/block/" + name + "_" + part + ".json", {
            "parent": "minecraft:block/template_bars_" + part, "textures": {"bars": t, "edge": t, "particle": t}})
    m = lambda part: mdl(name + "_" + part)
    f = {"north": "false", "east": "false", "south": "false", "west": "false"}
    only = lambda side: dict(f, **{side: "true"})
    write(ASSETS + "blockstates/" + name + ".json", {"multipart": [
        {"apply": {"model": m("post_ends")}},
        {"apply": {"model": m("post")}, "when": f},
        {"apply": {"model": m("cap")}, "when": only("north")},
        {"apply": {"model": m("cap"), "y": 90}, "when": only("east")},
        {"apply": {"model": m("cap_alt")}, "when": only("south")},
        {"apply": {"model": m("cap_alt"), "y": 90}, "when": only("west")},
        {"apply": {"model": m("side")}, "when": {"north": "true"}},
        {"apply": {"model": m("side"), "y": 90}, "when": {"east": "true"}},
        {"apply": {"model": m("side_alt")}, "when": {"south": "true"}},
        {"apply": {"model": m("side_alt"), "y": 90}, "when": {"west": "true"}},
    ]})
    write(ASSETS + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": t}})
    item(name, NS + ":item/" + name)


def box(lo, hi, faces):
    """an element whose faces take explicit uv rectangles from #sheet: faces = {side: [u0, v0, u1, v1]}"""
    return {"from": list(lo), "to": list(hi), "faces": {side: {"uv": uv, "texture": "#sheet"} for side, uv in faces.items()}}


def all_faces(side_uv, end_uv):
    return {"north": side_uv, "south": side_uv, "east": side_uv, "west": side_uv, "up": end_uv, "down": end_uv}


def tatami():
    write(ASSETS + "models/block/tatami.json", {"parent": "minecraft:block/cube_column",
        "textures": {"end": tex("tatami_top"), "side": tex("tatami_side")}})
    # the mat lies flat whichever way it runs: axis only turns its weave
    write(ASSETS + "blockstates/tatami.json", {"variants": {
        "axis=y": {"model": mdl("tatami")},
        "axis=x": {"model": mdl("tatami"), "y": 90},
        "axis=z": {"model": mdl("tatami")}}})
    item("tatami")


def paper_lantern():
    paper, cap = [0, 0, 16, 10], [0, 10, 16, 16]
    standing = [
        box((5, 0, 5), (11, 1, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
        box((4, 1, 4), (12, 11, 12), all_faces(paper, [0, 0, 16, 2])),
        box((5, 11, 5), (11, 12, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
    ]
    hanging = [
        box((5, 3, 5), (11, 4, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
        box((4, 4, 4), (12, 14, 12), all_faces(paper, [0, 0, 16, 2])),
        box((5, 14, 5), (11, 15, 11), all_faces([0, 10, 12, 12], [0, 10, 12, 16])),
        box((7.5, 15, 7.5), (8.5, 16, 8.5), all_faces([0, 14, 2, 16], [0, 14, 2, 16])),
    ]
    base = {"parent": "minecraft:block/block", "textures": {"sheet": tex("paper_lantern"), "particle": tex("paper_lantern")}}
    write(ASSETS + "models/block/paper_lantern.json", dict(base, elements=standing))
    write(ASSETS + "models/block/paper_lantern_hanging.json", dict(base, elements=hanging))
    write(ASSETS + "blockstates/paper_lantern.json", {"variants": {
        "hanging=false": {"model": mdl("paper_lantern")}, "hanging=true": {"model": mdl("paper_lantern_hanging")}}})
    item("paper_lantern")


def stone_lantern():
    stone, window = [0, 0, 16, 10], [0, 10, 16, 16]
    elements = [
        box((3, 0, 3), (13, 2, 13), all_faces([0, 0, 16, 4], [0, 0, 16, 10])),
        box((6, 2, 6), (10, 7, 10), all_faces([4, 0, 12, 10], [4, 2, 12, 8])),
        box((4, 7, 4), (12, 12, 12), all_faces(window, [0, 0, 16, 10])),
        box((2, 12, 2), (14, 14, 14), all_faces([0, 0, 16, 4], [0, 0, 16, 10])),
        box((6, 14, 6), (10, 16, 10), all_faces([4, 0, 12, 4], [4, 2, 12, 8])),
    ]
    write(ASSETS + "models/block/stone_lantern.json", {"parent": "minecraft:block/block",
        "textures": {"sheet": tex("stone_lantern"), "particle": tex("stone_lantern")}, "elements": elements})
    write(ASSETS + "blockstates/stone_lantern.json", {"variants": {"": {"model": mdl("stone_lantern")}}})
    item("stone_lantern")


def thatch_shapes():
    t = {"bottom": tex("thatch"), "top": tex("thatch"), "side": tex("thatch")}
    write(ASSETS + "models/block/thatch_slab.json", {"parent": "minecraft:block/slab", "textures": t})
    write(ASSETS + "models/block/thatch_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": t})
    write(ASSETS + "blockstates/thatch_slab.json", {"variants": {
        "type=bottom": {"model": mdl("thatch_slab")}, "type=top": {"model": mdl("thatch_slab_top")}, "type=double": {"model": mdl("thatch")}}})
    item("thatch_slab")
    for part, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(ASSETS + "models/block/thatch_stairs" + part + ".json", {"parent": "minecraft:block/" + parent, "textures": t})
    turns = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, y in turns.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = mdl("thatch_stairs" + ("" if shape == "straight" else "_" + shape.split("_")[0]))
                if shape == "straight":
                    turn = y
                elif shape.endswith("right"):
                    turn = y if half == "bottom" else y + 90
                else:
                    turn = y + 270 if half == "bottom" else y
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                if turn % 360:
                    v["y"] = turn % 360
                if len(v) > 1:
                    v["uvlock"] = True
                variants["facing=%s,half=%s,shape=%s" % (facing, half, shape)] = v
    write(ASSETS + "blockstates/thatch_stairs.json", {"variants": variants})
    item("thatch_stairs")


def flat_item(name):
    write(ASSETS + "models/item/" + name + ".json", {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + name}})
    item(name, NS + ":item/" + name)


def tansu():
    for suffix in ("", "_open"):
        write(ASSETS + "models/block/tansu" + suffix + ".json", {"parent": "minecraft:block/orientable", "textures": {
            "top": tex("tansu_top"), "front": tex("tansu_front" + suffix), "side": tex("tansu_side")}})
    turn = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    variants = {}
    for facing, rot in turn.items():
        for is_open in ("false", "true"):
            variants["facing=%s,open=%s" % (facing, is_open)] = dict({"model": mdl("tansu" + ("_open" if is_open == "true" else ""))}, **rot)
    write(ASSETS + "blockstates/tansu.json", {"variants": variants})
    item("tansu")


def face(uv, texture):
    return {"uv": uv, "texture": texture}


def futon():
    """head toward north (the bed states' facing), as the infirmary cot's models are turned"""
    for part in ("head", "foot"):
        t = "#quilt"
        edge = [0, 12, 16, 15]
        elements = [{"from": [1, 0, 0], "to": [15, 3, 16], "faces": {
            "up": face([0, 0, 16, 16], t), "down": face([0, 0, 16, 16], t), "north": face(edge, t), "south": face(edge, t),
            "east": face(edge, t), "west": face(edge, t)}}]
        if part == "head":
            p = "#pillow"
            elements.append({"from": [4, 3, 1], "to": [12, 5, 6], "faces": {
                "up": face([0, 0, 16, 10], p), "north": face([0, 0, 16, 4], p), "south": face([0, 0, 16, 4], p),
                "east": face([0, 0, 10, 4], p), "west": face([0, 0, 10, 4], p)}})
        write(ASSETS + "models/block/futon_" + part + ".json", {"parent": "minecraft:block/block", "textures": {
            "particle": tex("futon_foot"), "quilt": tex("futon_" + part), "pillow": tex("futon_pillow")}, "elements": elements})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for part in ("head", "foot"):
            v = {"model": mdl("futon_" + part)}
            if y:
                v["y"] = y
            variants["facing=%s,part=%s" % (facing, part)] = v
    write(ASSETS + "blockstates/futon.json", {"variants": variants})
    flat_item("futon")


def hearth():
    side = lambda w: face([0, 12, w, 16], "#side")
    for suffix in ("", "_lit"):
        frame = []
        for lo, hi in (([0, 0, 0], [16, 4, 3]), ([0, 0, 13], [16, 4, 16]), ([0, 0, 3], [3, 4, 13]), ([13, 0, 3], [16, 4, 13])):
            frame.append({"from": lo, "to": hi, "faces": {
                "up": face([lo[0], lo[2], hi[0], hi[2]], "#top"), "down": face([lo[0], lo[2], hi[0], hi[2]], "#side"),
                "north": side(hi[0] - lo[0]), "south": side(hi[0] - lo[0]), "east": side(hi[2] - lo[2]), "west": side(hi[2] - lo[2])}})
        frame.append({"from": [3, 0, 3], "to": [13, 2, 13], "faces": {"up": face([3, 3, 13, 13], "#top"), "down": face([3, 3, 13, 13], "#side")}})
        textures = {"particle": tex("sunken_hearth_side"), "side": tex("sunken_hearth_side"), "top": tex("sunken_hearth_top" + suffix)}
        if suffix:
            textures["fire"] = "minecraft:block/campfire_fire"
            for angle in (45, -45):
                frame.append({"from": [4, 2, 8], "to": [12, 10, 8], "shade": False,
                    "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle},
                    "faces": {"north": face([0, 0, 16, 16], "#fire"), "south": face([0, 0, 16, 16], "#fire")}})
        write(ASSETS + "models/block/sunken_hearth" + suffix + ".json", {"parent": "minecraft:block/block", "textures": textures,
            "elements": frame})
    write(ASSETS + "blockstates/sunken_hearth.json", {"variants": {
        "lit=false": {"model": mdl("sunken_hearth")}, "lit=true": {"model": mdl("sunken_hearth_lit")}}})
    item("sunken_hearth", mdl("sunken_hearth"))


def sliding_door():
    """the panel as a vanilla door's lies facing east (x 0..3); open, it has run 13 along the wall toward its hinge side"""
    slides = {"": 0, "_open_left": -13, "_open_right": 13}
    for half in ("bottom", "top"):
        for suffix, dz in slides.items():
            t = "#door"
            write(ASSETS + "models/block/sliding_door_" + half + suffix + ".json", {"ambientocclusion": False,
                "textures": {"particle": tex("sliding_door_" + half), "door": tex("sliding_door_" + half)},
                "elements": [{"from": [0, 0, dz], "to": [3, 16, 16 + dz], "faces": {
                    "west": face([0, 0, 16, 16], t), "east": face([16, 0, 0, 16], t), "north": face([0, 0, 1, 16], t),
                    "south": face([15, 0, 16, 16], t), "up": face([0, 0, 1, 16], t), "down": face([0, 0, 1, 16], t)}}]})
    variants = {}
    for facing, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
        for half, name in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for is_open in ("false", "true"):
                    v = {"model": mdl("sliding_door_" + name + ("_open_" + hinge if is_open == "true" else ""))}
                    if y:
                        v["y"] = y
                    variants["facing=%s,half=%s,hinge=%s,open=%s" % (facing, half, hinge, is_open)] = v
    write(ASSETS + "blockstates/sliding_door.json", {"variants": variants})
    flat_item("sliding_door")


GUI = {"gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
       "ground": {"translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]},
       "fixed": {"rotation": [0, 180, 0], "scale": [0.5, 0.5, 0.5]},
       "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0, 1], "scale": [0.3, 0.3, 0.3]},
       "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.3, 0.3, 0.3]}}


def hat(name, texture, boxes):
    """a hat worn from its own model: the head's middle is the model's (8, 8, 8), its crown near y 14"""
    elements = []
    for lo, hi in boxes:
        f = face([0, 0, 16, 16], "#hat")
        thin = face([0, 0, 16, max(1, min(16, hi[1] - lo[1]))], "#hat")
        elements.append({"from": lo, "to": hi, "faces": {"up": f, "down": f, "north": thin, "south": thin, "east": thin, "west": thin}})
    write(ASSETS + "models/item/" + name + ".json", {"textures": {"hat": texture, "particle": texture}, "elements": elements, "display": GUI})
    item(name, NS + ":item/" + name)


def dress():
    hat("straw_hat", tex("thatch"), [([-4, 12, -4], [20, 13, 20]), ([-1, 13, -1], [17, 15, 17]), ([2, 15, 2], [14, 17, 14]),
        ([5, 17, 5], [11, 19, 11]), ([7, 19, 7], [9, 20, 9])])
    hat("basket_hat", tex("basket_hat"), [([0.5, 1, 0.5], [15.5, 17, 15.5])])
    for name in ("straw_cape", "ramie_seeds", "ramie_fibre"):
        flat_item(name)
    write(ASSETS + "equipment/straw_cape.json", {"layers": {"humanoid": [{"texture": NS + ":straw_cape"}]}})
    variants = {}
    for age in range(8):
        stage = (0, 0, 1, 1, 2, 2, 2, 3)[age]
        variants["age=%d" % age] = {"model": mdl("ramie_stage%d" % stage)}
    for stage in range(4):
        write(ASSETS + "models/block/ramie_stage%d.json" % stage, {"parent": "minecraft:block/crop",
            "textures": {"crop": tex("ramie_stage%d" % stage)}})
    write(ASSETS + "blockstates/ramie.json", {"variants": variants})
    grown = {"type": "minecraft:match_block", "blocks": NS + ":ramie", "state": {"age": "7"}}
    write(DATA + "loot_table/blocks/ramie.json", {"type": "minecraft:block", "modifier": {"type": "minecraft:explosion_decay"}, "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":ramie_seeds"}]},
        {"rolls": 1, "condition": grown, "entries": [{"type": "minecraft:item", "name": NS + ":ramie_seeds"}]},
        {"rolls": 1, "condition": grown, "entries": [{"type": "minecraft:item", "name": NS + ":ramie_fibre"}]}],
        "random_sequence": NS + ":blocks/ramie"})


def furnishings():
    dress()
    tansu()
    futon()
    hearth()
    sliding_door()
    write(ASSETS + "models/item/lacquer_sap.json", {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/lacquer_sap"}})
    item("lacquer_sap", NS + ":item/lacquer_sap")


def assets():
    furnishings()
    cube("plaster")
    cube("red_lacquered_planks")
    cube("black_lacquered_planks")
    cube("thatch")
    bars("shoji")
    bars("ward_rope")
    tatami()
    paper_lantern()
    stone_lantern()
    thatch_shapes()


# --- loot, recipes, tags, lang ------------------------------------------------------------------------------------

def loot(name):
    entry = {"type": "minecraft:item", "name": NS + ":" + name}
    if name.endswith("_slab"):
        # a double slab drops two (26.3's loot format: modifier, type, one condition)
        entry["modifier"] = [{"type": "minecraft:set_count", "count": 2, "condition": {
            "type": "minecraft:match_block", "blocks": NS + ":" + name, "state": {"type": "double"}}},
            {"type": "minecraft:explosion_decay"}]
        pool = {"entries": [entry], "rolls": 1}
    else:
        # a bed drops from its head and a door from its lower half, so the pair gives one
        half = {"futon": {"part": "head"}, "sliding_door": {"half": "lower"}}.get(name)
        if half:
            entry["condition"] = {"type": "minecraft:match_block", "blocks": NS + ":" + name, "state": half}
        pool = {"condition": {"type": "minecraft:survives_explosion"}, "entries": [entry], "rolls": 1}
    write(DATA + "loot_table/blocks/" + name + ".json", {"type": "minecraft:block", "pools": [pool],
        "random_sequence": NS + ":blocks/" + name})


def shaped(name, pattern, key, count, category="building"):
    write(DATA + "recipe/" + name + ".json", {"type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": key, "result": {"count": count, "id": NS + ":" + name}})


def shapeless(name, ingredients, count, category="building"):
    write(DATA + "recipe/" + name + ".json", {"type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": ingredients, "result": {"count": count, "id": NS + ":" + name}})


def recipes():
    mc = lambda n: "minecraft:" + n
    own = lambda n: NS + ":" + n
    shaped("shoji", ["SPS", "SPS"], {"S": mc("stick"), "P": mc("paper")}, 6)
    shaped("thatch", ["WW", "WW"], {"W": mc("wheat")}, 2)
    shaped("thatch_slab", ["TTT"], {"T": own("thatch")}, 6)
    shaped("thatch_stairs", ["T  ", "TT ", "TTT"], {"T": own("thatch")}, 4)
    shaped("tatami", ["SS", "TT"], {"S": mc("string"), "T": own("thatch")}, 2)
    shapeless("plaster", [mc("clay_ball"), mc("clay_ball"), mc("bone_meal"), mc("sand")], 4)
    for colour in ("red", "black"):
        shapeless(colour + "_lacquered_planks", ["#minecraft:planks"] * 4 + [mc(colour + "_dye"), own("lacquer_sap")], 4)
    shaped("paper_lantern", ["SPS", "PTP", "SPS"], {"S": mc("stick"), "P": mc("paper"), "T": mc("torch")}, 2, "misc")
    shaped("stone_lantern", ["B", "T", "W"], {"B": mc("stone_brick_slab"), "T": mc("torch"), "W": mc("stone_brick_wall")}, 1, "misc")
    shaped("ward_rope", ["SSS", "P P"], {"S": mc("string"), "P": mc("paper")}, 3, "misc")
    shaped("tansu", ["LLL", "NBN", "LLL"], {"L": own("black_lacquered_planks"), "N": mc("iron_nugget"), "B": mc("barrel")}, 1, "misc")
    shaped("futon", ["WWW", "TTT"], {"W": "#minecraft:wool", "T": own("thatch")}, 1, "misc")
    shaped("sunken_hearth", ["PSP", "PCP", "PPP"], {"P": "#minecraft:planks", "S": mc("sand"), "C": "#minecraft:coals"}, 1, "misc")
    shaped("sliding_door", ["SS", "SS", "SS"], {"S": own("shoji")}, 3, "misc")
    shaped("straw_hat", ["TTT", "T T"], {"T": own("thatch")}, 1, "equipment")
    shaped("basket_hat", ["MMM", "M M", "M M"], {"M": own("tatami")}, 1, "equipment")
    shaped("straw_cape", ["T T", "TTT", "TTT"], {"T": own("thatch")}, 1, "equipment")
    write(DATA + "recipe/string_from_ramie_fibre.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [own("ramie_fibre")] * 3, "result": {"count": 2, "id": "minecraft:string"}})


def merge_tag(path, values):
    have = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path) as f:
            have = json.load(f)
    for v in values:
        if v not in have["values"]:
            have["values"].append(v)
    write(path, have)


def tags():
    own = lambda names: [NS + ":" + n for n in names]
    merge_tag(RES + "data/minecraft/tags/block/mineable/axe.json", own(AXE))
    merge_tag(RES + "data/minecraft/tags/block/mineable/pickaxe.json", own(PICKAXE))
    merge_tag(RES + "data/minecraft/tags/block/mineable/hoe.json", own(HOE))
    merge_tag(RES + "data/minecraft/tags/block/slabs.json", own(["thatch_slab"]))
    merge_tag(RES + "data/minecraft/tags/block/stairs.json", own(["thatch_stairs"]))
    merge_tag(RES + "data/minecraft/tags/item/slabs.json", own(["thatch_slab"]))
    merge_tag(RES + "data/minecraft/tags/item/stairs.json", own(["thatch_stairs"]))
    merge_tag(RES + "data/minecraft/tags/item/planks.json", own(["red_lacquered_planks", "black_lacquered_planks"]))
    merge_tag(RES + "data/minecraft/tags/block/planks.json", own(["red_lacquered_planks", "black_lacquered_planks"]))
    merge_tag(RES + "data/minecraft/tags/block/beds.json", own(["futon"]))
    merge_tag(RES + "data/minecraft/tags/item/beds.json", own(["futon"]))
    merge_tag(RES + "data/minecraft/tags/block/campfires.json", own(["sunken_hearth"]))
    merge_tag(RES + "data/minecraft/tags/block/wooden_doors.json", own(["sliding_door"]))
    merge_tag(RES + "data/minecraft/tags/item/wooden_doors.json", own(["sliding_door"]))
    merge_tag(RES + "data/minecraft/tags/block/crops.json", own(["ramie"]))
    merge_tag(RES + "data/minecraft/tags/block/maintains_farmland.json", own(["ramie"]))


def lang():
    path = ASSETS + "lang/parts/blossom_blocks.json"
    names = {"block." + NS + "." + k: v for k, v in NAMES.items()}
    names["item." + NS + ".lacquer_sap"] = "Bowl of Lacquer Sap"
    names["container." + NS + ".tansu"] = "Tansu"
    names.update({"item." + NS + ".straw_hat": "Straw Hat", "item." + NS + ".basket_hat": "Basket Hat",
        "item." + NS + ".straw_cape": "Straw Cape", "item." + NS + ".ramie_seeds": "Ramie Seeds",
        "item." + NS + ".ramie_fibre": "Ramie Fibre", "block." + NS + ".ramie": "Ramie"})
    write(path, names)


if __name__ == "__main__":
    textures()
    assets()
    for n in NAMES:
        loot(n)
    recipes()
    tags()
    lang()
    print("blossom blocks: %d" % len(NAMES))
