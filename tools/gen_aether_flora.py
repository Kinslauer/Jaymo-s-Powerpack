"""Writes the Aether II woods, leaves, saplings and plants (Powers, Track N2 of docs/aether.md): the 16x16 textures (painted
here, vanilla style: flat shade ramps and light noise, nothing copied from The Aether II or anyone else), and the
blockstates, models, item definitions, loot tables, recipes, tags and lang that go with them.

    python -I tools/gen_aether_flora.py

Needs only Pillow. Every texture is seeded from its own name, so a rerun changes nothing. The ids must match
com.jaymospowers.aether.AetherFlora. It borrows the painting and file helpers from gen_aether.py (importing it writes
nothing). Existing tag files are merged into, never replaced. Run after gen_aether.py (that one rewrites aether_logs).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_aether as G  # noqa: E402

hx, ramp, new, seeded = G.hx, G.ramp, G.new, G.seeded
jp, mc, write, merge_tag = G.jp, G.mc, G.write, G.merge_tag
ASSETS, DATA, MCDATA, SUB, NS = G.ASSETS, G.DATA, G.MCDATA, G.SUB, G.NS


def tex(name, kind="block"):
    return "%s:%s/%s/%s" % (NS, kind, SUB, name)


def bmodel(name):
    return "%s:block/%s/%s" % (NS, SUB, name)


def imodel(name):
    return "%s:item/%s/%s" % (NS, SUB, name)


# ---------------------------------------------------------------- ramps (dark, shade, mid, light unless said)

GREAT_BARK = ramp("4a3524", "3e2c1d", "57402c", "33241a")
GREAT_RING = ramp("86633f", "725332", "5e442a")
GREAT_PLANKS = ramp("6b4c32", "7a5a3c", "5c4029")
GREAT_SEAM = hx("46301f")
GREAT_STRIP = ramp("7d5d3f", "8b6a49", "6e5035", "946f4c")
WISP_BARK = ramp("9a94a6", "8b8598", "a8a2b3", "7a7488")
WISP_RING = ramp("cdc7d8", "bbb5c8", "a59fb6")
WISP_PLANKS = ramp("b7b1c4", "c4bed0", "a8a2b6")
WISP_SEAM = hx("857f94")
WISP_STRIP = ramp("bdb7c9", "cac5d5", "aea8bb", "d4cfe0")
AMBER_BARK2 = ramp("8a5a24", "7a4d1c", "9b6a2e", "683f16")
AMBER_RING2 = ramp("dcaa52", "c98f36", "ae7422")
AMBER_PLANKS = ramp("c08a40", "cf9a4e", "b07a34")
AMBER_SEAM = hx("8a5c26")
AMBER_STRIP = ramp("d19a4a", "dfaa5a", "c28a3c", "e8b866")
AMBER_CHUNK = ramp("a85f0a", "dc8f16", "f7b934", "ffe38a")
SKY_PLANKS = ramp("b3bea3", "a3af92", "c3cdb3")
SKY_SEAM = hx("86917a")
MOSS_GREEN = ramp("2f7a3f", "3f9a4f", "56b865", "7fd68a")

LEAF = {
    "skyplane_leaves": ramp("a6d9a0", "bce8b4", "8cc786", "cdf2c6"),
    "skybirch_leaves": ramp("b5d44e", "c9e668", "9bbd38", "dcf08a"),
    "skypine_leaves": ramp("1f6b66", "2a827b", "175651", "3b9a91"),
    "wisproot_leaves": ramp("c8a8e8", "d8bcf2", "b090d6", "e6d2fa"),
    "wisptop_leaves": ramp("8a5cc8", "9f74da", "7447ae", "e8d8ff"),
    "greatroot_leaves": ramp("2f7a3a", "3b8f47", "24652f", "4da65a"),
    "greatoak_leaves": ramp("4aa84a", "5fc05a", "3a8f3c", "7bd470"),
    "greatboa_leaves": ramp("7fc4b8", "98d8cc", "63ab9f", "c4ece6"),
    "amberoot_leaves": ramp("e8b030", "f6c850", "cf9820", "ffe085"),
    "irradiated_leaves": ramp("8a9a2a", "9eae3a", "728018", "b8c85a"),
}
LEAF_DARK = {"skyplane_leaves": "6fa86a", "skybirch_leaves": "7c9a28", "skypine_leaves": "0f3f3c", "wisproot_leaves": "8f6fb8",
             "wisptop_leaves": "5a3590", "greatroot_leaves": "174a22", "greatoak_leaves": "2a6a2c", "greatboa_leaves": "44857a",
             "amberoot_leaves": "a06f12", "irradiated_leaves": "525c10"}
SAPLING_STEM = {"skyplane": ramp("8a7a63", "a89880"), "skybirch": ramp("b8b090", "d8d0b0"), "skypine": ramp("5a4a38", "7a6650"),
                "wisproot": ramp("8a8498", "a8a2b8"), "wisptop": ramp("6a5a88", "8a7aa8"), "greatroot": ramp("4a3524", "6a4d30"),
                "greatoak": ramp("6a4d30", "8a6a44"), "greatboa": ramp("7a8a88", "9aaaa8"), "amberoot": ramp("8c5818", "b57828")}
GREY = ramp("d2d2d2", "e4e4e4", "bcbcbc", "f2f2f2")   # tinted plants: the biome's grass colour multiplies these
LEAFY = ramp("2f7a57", "3f9a6c", "57b888", "7fd6a6")
STEM_GREEN = ramp("3f8f4f", "56b36a", "2f6f3c")


# ---------------------------------------------------------------- painting helpers

def put(px, pts, color):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = color + (255,)


def line(x0, y0, x1, y1):
    pts = []
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        pts.append((round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)))
    return pts


def vstem(px, x, y0, y1, colors):
    """A stem two pixels wide: lit left column, shaded right."""
    for y in range(y0, y1 + 1):
        px[x, y] = colors[0] + (255,)
        px[x + 1, y] = colors[2 if len(colors) > 2 else 1] + (255,)


def make_bark(name, colors, flecks=None, count=0):
    im = G.bark(name, colors)
    if flecks:
        rng = seeded(name + ":flecks")
        px = im.load()
        for _ in range(count):
            x, y = rng.randrange(16), rng.randrange(16)
            px[x, y] = rng.choice(flecks) + (255,)
            if rng.random() < 0.5 and y < 15:
                px[x, y + 1] = rng.choice(flecks) + (255,)
    return im


def mossy_bark(name, colors):
    im = G.bark(name, colors)
    rng = seeded(name + ":moss")
    px = im.load()
    for _ in range(11):
        x, y = rng.randrange(1, 15), rng.randrange(0, 15)
        for dx, dy in rng.choice([[(0, 0), (1, 0)], [(0, 0), (0, 1)], [(0, 0), (1, 0), (0, 1)], [(0, 0)]]):
            if x + dx < 16 and y + dy < 16:
                px[x + dx, y + dy] = rng.choice(MOSS_GREEN) + (255,)
    return im


def deposit_bark(name, colors):
    """Bark with chunks of golden amber set in it."""
    im = G.bark(name, colors)
    rng = seeded(name + ":chunks")
    px = im.load()
    for cx, cy in [(3, 3), (10, 5), (5, 10), (12, 12)]:
        cx += rng.randint(-1, 1)
        cy += rng.randint(-1, 1)
        mask = G.ellipse(cx + 0.5, cy + 0.5, 2.2, 2.2)
        G.paint_mask(im, mask, [AMBER_CHUNK[0], AMBER_CHUNK[1], AMBER_CHUNK[2], AMBER_CHUNK[3]], rng)
    return im


def door_tex(name, colors, seam, top):
    """One half of a door: boards, a darker frame, and either panels (bottom) or a window row over a panel (top)."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = colors[rng.choices([0, 1, 2], [5, 3, 2])[0]]
            if x in (0, 15) or y == (0 if top else 15):
                c = seam
            px[x, y] = c + (255,)
    boxes = [(3, 9, 12, 13)] if top else [(3, 2, 12, 6), (3, 9, 12, 13)]
    for x0, y0, x1, y1 in boxes:
        for x in range(x0, x1 + 1):
            px[x, y0] = seam + (255,)
            px[x, y1] = seam + (255,)
        for y in range(y0, y1 + 1):
            px[x0, y] = seam + (255,)
            px[x1, y] = seam + (255,)
    if top:
        dark = tuple(max(0, v - 70) for v in seam)
        for y in range(2, 7):
            for x in range(3, 13):
                px[x, y] = (dark if (x + y) % 5 else seam) + (255,)
    else:
        px[12, 1] = hx("e8d9a0") + (255,)
        px[12, 2] = hx("b89f60") + (255,)
    return im


def trapdoor_tex(name, colors, seam):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = colors[rng.choices([0, 1, 2], [5, 3, 2])[0]]
            if x in (0, 15) or y in (0, 15):
                c = seam
            px[x, y] = c + (255,)
    for y in list(range(3, 7)) + list(range(9, 13)):
        for x in list(range(3, 7)) + list(range(9, 13)):
            px[x, y] = (0, 0, 0, 0)
    return im


def door_item(name, colors, seam):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(1, 15):
        for x in range(4, 12):
            c = colors[rng.choices([0, 1, 2], [5, 3, 2])[0]]
            if x in (4, 11) or y in (1, 14):
                c = seam
            px[x, y] = c + (255,)
    for y in (3, 4):
        for x in range(6, 10):
            px[x, y] = tuple(max(0, v - 70) for v in seam) + (255,)
    for x0, y0, x1, y1 in [(6, 6, 9, 8), (6, 10, 9, 12)]:
        for x in range(x0, x1 + 1):
            px[x, y0] = seam + (255,)
            px[x, y1] = seam + (255,)
        for y in range(y0, y1 + 1):
            px[x0, y] = seam + (255,)
            px[x1, y] = seam + (255,)
    px[10, 8] = hx("e8d9a0") + (255,)
    return im


def tuft(name, light, blades, hmin, hmax, lean_chance=0.35):
    """A grass tuft: blades from the bottom edge, leaning a little near their tips, lighter at the tip."""
    rng = seeded(name)
    im = new()
    px = im.load()
    for x in sorted(rng.sample(range(1, 15), blades)):
        h = rng.randint(hmin, hmax)
        lean = rng.choice([-1, 1]) if rng.random() < lean_chance else 0
        for i in range(h):
            xx = x + (lean if i >= h * 0.6 else 0)
            if 0 <= xx < 16:
                c = light[3] if i >= h - 2 else light[rng.choice([0, 1, 2])]
                px[xx, 15 - i] = c + (255,)
    return im


def fern(name, light):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(3, 16):
        px[8, y] = light[2] + (255,)
    for i, y in enumerate(range(13, 3, -2)):
        reach = max(1, 6 - i)
        for s in (-1, 1):
            for k in range(1, reach + 1):
                x, yy = 8 + s * k, y - (k + 1) // 2
                if 0 <= x < 16 and 0 <= yy < 16:
                    px[x, yy] = (light[3] if k == reach else light[rng.choice([0, 1])]) + (255,)
    px[8, 2] = light[3] + (255,)
    px[8, 3] = light[3] + (255,)
    return im


def shrub(name):
    rng = seeded(name)
    im = G.leaves(name, LEAFY, 0.05, dark=hx("236048"))
    px = im.load()
    mask = G.ellipse(8, 8, 8.5, 8.5)
    for y in range(16):
        for x in range(16):
            if (x, y) not in mask:
                px[x, y] = (0, 0, 0, 0)
    for _ in range(6):
        x, y = rng.choice(sorted(mask))
        if px[x, y][3]:
            px[x, y] = hx("a6ecc4") + (255,)
    return im


def twig(name):
    im = new()
    px = im.load()
    bark = ramp("7b8b7a", "6b7b6a", "8b9b89", "5c6a5b")
    for i, (x, y) in enumerate(line(2, 12, 13, 4)):
        put(px, [(x, y)], bark[2] if i % 3 else bark[0])
        put(px, [(x, y + 1)], bark[3])
    for i, (x, y) in enumerate(line(5, 5, 11, 12)):
        put(px, [(x, y)], bark[0] if i % 2 else bark[2])
        put(px, [(x + 1, y)], bark[3])
    for i, (x, y) in enumerate(line(7, 9, 3, 13)):
        put(px, [(x, y)], bark[1])
    return im


def bloom_flower(name, petals, centre):
    return G.flower(name, petals, centre)


def tarabloom_pair(name):
    blue = ramp("1f3f9a", "3a63cf", "5b88ee", "a9c8ff")
    bottom = new()
    px = bottom.load()
    for y in range(0, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
        px[8, y] = STEM_GREEN[2] + (255,)
    for x, y in [(6, 12), (5, 11), (4, 10), (9, 8), (10, 7), (11, 6), (6, 5), (5, 4), (9, 13), (10, 14)]:
        px[x, y] = STEM_GREEN[1] + (255,)
    top = new()
    px = top.load()
    for y in range(7, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
        px[8, y] = STEM_GREEN[2] + (255,)
    mask = G.ellipse(8, 5, 6, 4.5) | G.ellipse(5, 7, 3, 3) | G.ellipse(11, 7, 3, 3) | G.ellipse(8, 2.5, 3, 2.5)
    rng = seeded(name)
    G.paint_mask(top, mask, [blue[0], blue[1], blue[2], blue[3]], rng)
    for x, y in [(7, 5), (8, 5), (7, 4), (8, 4)]:
        px[x, y] = hx("ffe9a0") + (255,)
    return bottom, top


def pluracian(name):
    im = new()
    px = im.load()
    pur = ramp("4b2a8a", "6f45b8", "9368d8", "c4a4f2")
    for y in range(7, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
    put(px, line(7, 10, 4, 6), STEM_GREEN[0])
    put(px, line(8, 9, 11, 5), STEM_GREEN[0])
    put(px, [(6, 13), (5, 12), (9, 12), (10, 11)], STEM_GREEN[1])
    rng = seeded(name)
    for cx, cy in [(7, 3), (4, 5), (11, 4), (9, 7)]:
        G.paint_mask(im, G.ellipse(cx + 0.5, cy + 0.5, 2.3, 2.3), [pur[0], pur[1], pur[2], pur[3]], rng)
        px[cx, cy] = hx("fff0b0") + (255,)
    return im


def lilichime(name):
    im = new()
    px = im.load()
    white = ramp("a9b4c4", "d9e0ea", "f4f7fb", "ffffff")
    for y in range(6, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
    put(px, line(7, 6, 10, 3), STEM_GREEN[0])
    put(px, line(7, 8, 4, 5), STEM_GREEN[0])
    put(px, [(6, 13), (5, 12), (9, 14), (10, 13)], STEM_GREEN[1])
    rng = seeded(name)
    for cx, cy in [(10, 5), (4, 7), (7, 5)]:
        mask = G.ellipse(cx + 0.5, cy + 1.0, 2.0, 2.6)
        G.paint_mask(im, mask, [white[0], white[1], white[2], white[3]], rng)
        px[cx, cy + 3] = white[0] + (255,)
        px[cx + 1, cy + 3] = hx("f3dc6a") + (255,)
    return im


def poasprout(name):
    im = new()
    px = im.load()
    g = ramp("1f6b3a", "3f9a52", "6fcb7e", "b8f2c0")
    for y in range(8, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
    put(px, line(7, 9, 3, 6), STEM_GREEN[0])
    put(px, line(8, 10, 12, 6), STEM_GREEN[0])
    rng = seeded(name)
    for cx, cy in [(7, 4), (3, 4), (12, 4)]:
        G.paint_mask(im, G.ellipse(cx + 0.5, cy + 1.0, 2.0, 2.8), [g[0], g[1], g[2], g[3]], rng)
        px[cx, cy - 1] = g[3] + (255,)
    put(px, [(6, 13), (5, 14), (9, 12), (10, 11)], STEM_GREEN[1])
    return im


def blade_poa(name):
    im = tuft(name, ramp("5d8f4a", "79b05f", "4a7a3c", "a6d985"), 6, 8, 13)
    px = im.load()
    for y in range(1, 6):
        px[8, y] = hx("c9d68a") + (255,)
        if y % 2:
            px[7, y] = hx("e6efb8") + (255,)
            px[9, y] = hx("e6efb8") + (255,)
    for y in range(6, 14):
        px[8, y] = hx("79b05f") + (255,)
    return im


def satival_shoot(name):
    im = new()
    px = im.load()
    for y in range(4, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
        px[8, y] = STEM_GREEN[2] + (255,)
    for x, y in line(6, 9, 2, 5) + line(9, 7, 13, 3) + line(6, 12, 3, 10) + line(9, 11, 12, 9):
        px[x, y] = STEM_GREEN[1] + (255,)
    rng = seeded(name)
    G.paint_mask(im, G.ellipse(8, 3.5, 2.4, 3.0), [hx("8a8a3a"), hx("b0b04a"), hx("d8d86a"), hx("f4f4a0")], rng)
    return im


def valkyrie_sprout(name):
    im = new()
    px = im.load()
    for y in range(8, 16):
        px[7, y] = STEM_GREEN[0] + (255,)
    rng = seeded(name)
    for pts, sh in ((line(7, 9, 2, 4), 0), (line(7, 9, 12, 4), 1), (line(7, 11, 4, 8), 1), (line(7, 11, 10, 8), 0)):
        for i, (x, y) in enumerate(pts):
            put(px, [(x, y)], hx("f4f6fa") if i % 2 else hx("d6dce8"))
            put(px, [(x, y + 1)], hx("ffe28a") if i == len(pts) - 1 else hx("b4bcc8"))
    G.paint_mask(im, G.ellipse(7.5, 7, 1.8, 1.8), [hx("c9a030"), hx("e0b840"), hx("f4d060"), hx("fff0a0")], rng)
    return im


def brettl_plant(name):
    im = new()
    px = im.load()
    g = ramp("3a7a4a", "4f9a5e", "6fbf80", "a6e8b0")
    rng = seeded(name)
    G.paint_mask(im, G.ellipse(8, 9.5, 3.4, 5.5), [g[0], g[1], g[2], g[3]], rng)
    G.paint_mask(im, G.ellipse(4.5, 11.5, 2.2, 3.4), [g[0], g[1], g[2], g[3]], rng)
    G.paint_mask(im, G.ellipse(11.5, 11.0, 2.2, 3.8), [g[0], g[1], g[2], g[3]], rng)
    for y in range(7, 15, 2):
        px[8, y] = g[0] + (255,)
    for x, y in [(8, 3), (7, 4), (9, 4), (8, 4)]:
        px[x, y] = hx("f27fb0") + (255,)
    px[8, 3] = hx("ffd0e4") + (255,)
    return im


def sapling_tex(tree):
    name = tree + "_leaves"
    return G.sapling(tree + "_sapling", SAPLING_STEM[tree], LEAF[name])


def leaf_tex(name):
    colors = LEAF[name]
    dark = hx(LEAF_DARK[name])
    if name == "skypine_leaves":
        im = G.leaves(name, colors, 0.2, dark=dark)
        rng = seeded(name + ":needles")
        px = im.load()
        for _ in range(14):
            x, y = rng.randrange(15), rng.randrange(14)
            if px[x, y][3]:
                px[x + 1, y + 1] = colors[3] + (255,)
        return im
    if name == "greatboa_leaves":
        im = G.leaves(name, colors, 0.14, dark=dark)
        rng = seeded(name + ":frost")
        px = im.load()
        for _ in range(12):
            x, y = rng.randrange(16), rng.randrange(16)
            if px[x, y][3]:
                px[x, y] = hx("f2fbfa") + (255,)
        return im
    if name == "irradiated_leaves":
        im = G.leaves(name, colors, 0.18, dark=dark)
        rng = seeded(name + ":rad")
        px = im.load()
        for _ in range(10):
            x, y = rng.randrange(16), rng.randrange(16)
            if px[x, y][3]:
                px[x, y] = hx("e8f06a") + (255,)
        return im
    if name == "amberoot_leaves":
        return G.leaves(name, colors, 0.16, dark=dark)
    return G.leaves(name, colors, 0.15, dark=dark)


def orange_stage(age, upper):
    """The orange tree at one age; lower half has the trunk, upper half the crown."""
    rng = seeded("orange_tree:%d:%s" % (age, "up" if upper else "low"))
    im = new()
    px = im.load()
    leaf = ramp("1f6b3a", "2f8a4a", "45a65c", "6fc87e")
    leaf_c = [leaf[0], leaf[1], leaf[2], leaf[3]]
    trunk = (hx("5a3c20"), hx("6e4a28"))
    if upper:
        masks = [G.ellipse(8, 14, 2.5, 2.0), G.ellipse(8, 12.5, 4.5, 4.0), G.ellipse(8, 10, 6.0, 6.5), G.ellipse(8, 10, 6.5, 6.8)]
        for x, y in masks[age]:
            pass
        G.paint_mask(im, masks[age], leaf_c, rng)
        fruit_spots = [(5, 8), (10, 6), (7, 12), (11, 11)]
        if age == 2:
            for x, y in fruit_spots:
                put(px, [(x, y)], hx("ffffff"))
                put(px, [(x + 1, y)], hx("f4f0c8"))
        if age == 3:
            for x, y in fruit_spots:
                G.paint_mask(im, G.ellipse(x + 1, y + 1, 1.7, 1.7), [hx("a84a0a"), hx("dc7a14"), hx("f59a24"), hx("ffc060")], rng)
    else:
        for y in range(8 if age else 12, 16):
            px[7, y] = trunk[0] + (255,)
            px[8, y] = trunk[1] + (255,)
        masks = [G.ellipse(8, 12.5, 3.0, 2.5), G.ellipse(8, 9, 6.0, 6.0), G.ellipse(8, 7.5, 7.5, 7.0), G.ellipse(8, 7.5, 7.5, 7.0)]
        G.paint_mask(im, masks[age], leaf_c, rng)
        put(px, [(7, y) for y in range(13, 16)], trunk[0])
        put(px, [(8, y) for y in range(13, 16)], trunk[1])
        fruit_spots = [(3, 6), (10, 4), (6, 9), (11, 10), (4, 11)]
        if age == 2:
            for x, y in fruit_spots:
                put(px, [(x, y)], hx("ffffff"))
                put(px, [(x + 1, y)], hx("f4f0c8"))
        if age == 3:
            for x, y in fruit_spots:
                G.paint_mask(im, G.ellipse(x + 1, y + 1, 1.7, 1.7), [hx("a84a0a"), hx("dc7a14"), hx("f59a24"), hx("ffc060")], rng)
    return im


def arilum(name, bloom):
    rng = seeded(name)
    im = new()
    teal = ramp("1c6b6b", "2a8b86", "3fa9a0", "7fd6c8")
    mask = G.ellipse(8, 8, 7.4, 7.4)
    px = im.load()
    for x, y in list(mask):
        if x > 8 and abs(y - 9) <= (x - 8) // 2:
            mask.discard((x, y))
    G.paint_mask(im, mask, [teal[0], teal[1], teal[2], teal[3]], rng)
    px = im.load()
    for ang in [(1, 0), (0, 1), (-1, 0), (0, -1), (1, 1), (-1, 1), (-1, -1)]:
        for k in range(2, 6):
            x, y = 8 + ang[0] * k, 8 + ang[1] * k
            if (x, y) in mask and px[x, y][3]:
                px[x, y] = teal[3] + (255,)
    if bloom:
        pink = ramp("b0407a", "e87db4", "ffb0d6", "ffffff")
        G.paint_mask(im, G.ellipse(8, 8, 3.2, 3.2), [pink[0], pink[1], pink[2], pink[3]], rng)
        px[8, 8] = hx("fff6a0") + (255,)
        px[7, 7] = pink[3] + (255,)
    return im


def moss_block(name, colors):
    rng = seeded(name)
    im = G.speckle(rng, colors, [4, 4, 2, 1])
    G.blobs(im, rng, colors[3], 6, (1, 2))
    G.blobs(im, rng, colors[0], 5, (1, 3))
    return im


def vines_tex(name, colors):
    return G.leaves(name, colors[:3] + [colors[3]], 0.5, dark=colors[0])


def shroom_plant(name, cap, stem, spots):
    rng = seeded(name)
    im = new()
    px = im.load()
    for y in range(9, 16):
        px[7, y] = stem[0] + (255,)
        px[8, y] = stem[1] + (255,)
    G.paint_mask(im, G.ellipse(8, 7, 5.5, 3.8) | G.ellipse(8, 8.5, 5.0, 2.2), [cap[0], cap[1], cap[2], cap[3]], rng)
    for x, y in spots:
        put(px, [(x, y)], cap[3])
    return im


def shroom_block(name, colors, spots, spot_color):
    rng = seeded(name)
    im = G.speckle(rng, colors, [4, 3, 3, 1])
    for cx, cy in spots:
        G.paint_mask(im, G.ellipse(cx + 0.5, cy + 0.5, 1.6, 1.6), [spot_color[0], spot_color[1], spot_color[2], spot_color[3]], rng)
    return im


def shroom_stem(name, colors):
    rng = seeded(name)
    im = new()
    px = im.load()
    lean = [rng.randrange(3) for _ in range(16)]
    for x in range(16):
        for y in range(16):
            px[x, y] = colors[lean[x] if rng.random() < 0.8 else rng.randrange(3)] + (255,)
    return im


def weave(name, top):
    """Woven sticks: crossing sticks two pixels wide (top) or stacked stick ends (side)."""
    rng = seeded(name)
    wood = ramp("5e4a30", "7a6240", "9a8052", "b89c68")
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if top:
                horizontal = ((y // 2) + (x // 4)) % 2 == 0
                lane = (y % 2) if horizontal else (x % 2)
                base = wood[1] if lane == 0 else wood[2]
                if (horizontal and y % 2 == 1 and x % 4 == 3) or ((not horizontal) and x % 2 == 1 and y % 4 == 3):
                    base = wood[0]
            else:
                base = wood[2 if (y // 2) % 2 else 1]
                if y % 2 == 1:
                    base = wood[0] if rng.random() < 0.5 else wood[1]
            if rng.random() < 0.1:
                base = wood[3]
            px[x, y] = base + (255,)
    return im


def bundle(name, top):
    rng = seeded(name)
    g = ramp("5f9a4a", "7bb85e", "98d276", "bfe89a")
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if top:
                c = g[rng.choice([0, 1, 2])]
                if (x + y) % 4 == 0:
                    c = g[3]
            else:
                c = g[(x * 7 + (y // 5)) % 3 if rng.random() < 0.85 else 3]
                if y in (6, 7, 8):
                    c = hx("8a6a3a") if y != 7 else hx("a58a52")
            px[x, y] = c + (255,)
    return im


def item_orange():
    rng = seeded("orange")
    im = new()
    G.paint_mask(im, G.ellipse(8, 9, 6, 6), [hx("a84a0a"), hx("dc7a14"), hx("f59a24"), hx("ffc060")], rng)
    px = im.load()
    put(px, [(5, 6), (4, 7)], hx("ffe0a0"))
    put(px, [(8, 3), (8, 2)], hx("5a3c20"))
    put(px, [(9, 3), (10, 2), (11, 3), (10, 3)], hx("45a65c"))
    return im


def item_wyndberry():
    rng = seeded("wyndberry")
    im = new()
    vio = [hx("2c1456"), hx("5a3a9a"), hx("8a68d0"), hx("c8b0f8")]
    for cx, cy in [(5, 10), (10.5, 10), (8, 6)]:
        G.paint_mask(im, G.ellipse(cx, cy, 2.8, 2.8), vio, rng)
    px = im.load()
    put(px, [(4, 9), (9, 9), (7, 5)], hx("efe4ff"))
    put(px, [(8, 2), (8, 3), (9, 1), (10, 1)], hx("45a65c"))
    return im


def item_arilum_bulb():
    rng = seeded("arilum_bulb")
    im = new()
    G.paint_mask(im, G.ellipse(8, 10, 5, 5) | G.polygon([(8, 1), (11, 7), (5, 7)]), [hx("1c6b6b"), hx("2a8b86"), hx("4fc4b6"), hx("b8f4ea")], rng)
    px = im.load()
    put(px, [(6, 9), (6, 10), (7, 8)], hx("e8fffa"))
    return im


def item_valkyrie_wings():
    rng = seeded("valkyrie_wings")
    im = new()
    col = [hx("8a94a8"), hx("c6cedc"), hx("e8edf5"), hx("ffffff")]
    G.paint_mask(im, G.polygon([(7, 13), (1, 8), (1, 3), (6, 6), (7, 9)]), col, rng)
    G.paint_mask(im, G.polygon([(8, 13), (14, 8), (14, 3), (9, 6), (8, 9)]), col, rng)
    px = im.load()
    put(px, [(7, 13), (8, 13), (7, 12), (8, 12)], hx("e0b840"))
    put(px, [(1, 3), (14, 3), (1, 4), (14, 4)], hx("f4d060"))
    return im


def item_brettl_grass():
    im = new()
    px = im.load()
    g = ramp("4a8a3a", "6aae52", "8acb6c", "b6ec94")
    for i, (x0, x1) in enumerate([(3, 1), (5, 4), (8, 8), (10, 12), (12, 14)]):
        pts = line(x0, 14, x1, 2 + (i % 2) * 2)
        for j, (x, y) in enumerate(pts):
            put(px, [(x, y)], g[3] if j > len(pts) - 3 else g[i % 3])
    for x in range(4, 13):
        put(px, [(x, 11)], hx("8a6a3a"))
        put(px, [(x, 12)], hx("6a4e28"))
    return im


def item_satival_bulb():
    rng = seeded("satival_bulb")
    im = new()
    G.paint_mask(im, G.ellipse(8, 9, 5, 5.5), [hx("6a6a2a"), hx("a0a040"), hx("d0d066"), hx("f4f4a8")], rng)
    px = im.load()
    put(px, [(6, 14), (7, 15), (9, 14), (10, 15)], hx("8a7a50"))
    put(px, [(8, 3), (8, 2), (7, 1), (9, 1)], hx("5aa05a"))
    put(px, [(5, 7), (5, 8)], hx("ffffd0"))
    return im


# ---------------------------------------------------------------- the lists (ids match AetherFlora)

WOODS = {
    # name: (planks colours, seam, bark colours, ring, stripped colours, new logs?)
    "skyroot": dict(planks=SKY_PLANKS, seam=SKY_SEAM, new=False),
    "greatroot": dict(planks=GREAT_PLANKS, seam=GREAT_SEAM, bark=GREAT_BARK, ring=GREAT_RING, strip=GREAT_STRIP, new=True),
    "wisproot": dict(planks=WISP_PLANKS, seam=WISP_SEAM, bark=WISP_BARK, ring=WISP_RING, strip=WISP_STRIP, new=True),
    "amberoot": dict(planks=AMBER_PLANKS, seam=AMBER_SEAM, bark=AMBER_BARK2, ring=AMBER_RING2, strip=AMBER_STRIP, new=True),
}
SHAPES = ["stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate"]
TREES = ["skyplane", "skybirch", "skypine", "wisproot", "wisptop", "greatroot", "greatoak", "greatboa", "amberoot"]
LEAVES = [t + "_leaves" for t in TREES] + ["irradiated_leaves"]
SAPLINGS = [t + "_sapling" for t in TREES]
GRASSES = ["aether_short_grass", "aether_medium_grass", "aether_long_grass", "bush_fern"]  # tinted
SMALL_FLOWERS = ["hesperose", "poasprout", "lilichime", "pluracian"]
CROSS_PLANTS = ["blade_poa", "satival_shoot", "valkyrie_sprout", "brettl_plant", "magnetic_shroom", "rotshroom"]
MOSSES = [("bryalinn", MOSS_GREEN, True), ("shayelinn", ramp("2a9a9a", "36b3b0", "228282", "66d6d0"), True),
          ("ambrelinn", ramp("c8b030", "dcc844", "b09a22", "f2e270"), False)]
POTTED = SAPLINGS + SMALL_FLOWERS + ["bush_fern", "magnetic_shroom", "rotshroom"]
ITEMS = ["orange", "wyndberry", "arilum_bulb", "valkyrie_wings", "brettl_grass", "satival_bulb"]

# every block id with an item, in registration order of AetherFlora (the creative tab)
WOOD_LOGS = {}   # wood -> its log-like block ids
for w, spec in WOODS.items():
    if spec["new"]:
        WOOD_LOGS[w] = [w + "_log", w + "_wood", "stripped_" + w + "_log", "stripped_" + w + "_wood"]
WOOD_LOGS["wisproot"].append("mossy_wisproot_log")
WOOD_LOGS["amberoot"].append("amberoot_deposit")


def wood_ids(w):
    """Every block id a wood set owns (skyroot: only the shaped ones)."""
    out = []
    if WOODS[w]["new"]:
        out += [w + "_log", w + "_wood", "stripped_" + w + "_log", "stripped_" + w + "_wood", w + "_planks"]
    out += [w + "_" + s for s in SHAPES]
    return out


ITEM_BLOCKS = []     # block ids that have a block item
for w in WOODS:
    ITEM_BLOCKS += wood_ids(w)
ITEM_BLOCKS += ["mossy_wisproot_log", "amberoot_deposit"]
ITEM_BLOCKS += LEAVES + SAPLINGS
ITEM_BLOCKS += ["aether_short_grass", "aether_medium_grass", "aether_long_grass", "bush_fern", "aether_bush", "holystone_twig"]
ITEM_BLOCKS += SMALL_FLOWERS + ["blade_poa", "satival_shoot", "valkyrie_sprout", "brettl_plant", "tarabloom", "brettl_grass_bundle", "orange_tree",
                                "arilum", "blooming_arilum"]
for m, _, vines in MOSSES:
    ITEM_BLOCKS += [m + "_moss_block", m + "_moss_carpet"] + ([m + "_vines"] if vines else [])
ITEM_BLOCKS += ["magnetic_shroom", "magnetic_shroom_block", "magnetic_shroom_stem", "rotshroom", "rotshroom_block",
                "woven_skyroot_sticks", "skyroot_leaf_pile"]
POTTED_BLOCKS = ["potted_" + p for p in POTTED]
ALL_BLOCKS = ITEM_BLOCKS + POTTED_BLOCKS

LOGLIKE = {}   # block id -> (side texture, end texture)
for w, spec in WOODS.items():
    if not spec["new"]:
        continue
    LOGLIKE[w + "_log"] = (w + "_log", w + "_log_top")
    LOGLIKE[w + "_wood"] = (w + "_log", w + "_log")
    LOGLIKE["stripped_" + w + "_log"] = ("stripped_" + w + "_log", "stripped_" + w + "_log_top")
    LOGLIKE["stripped_" + w + "_wood"] = ("stripped_" + w + "_log", "stripped_" + w + "_log")
LOGLIKE["mossy_wisproot_log"] = ("mossy_wisproot_log", "wisproot_log_top")
LOGLIKE["amberoot_deposit"] = ("amberoot_deposit", "amberoot_log_top")
LOGLIKE["brettl_grass_bundle"] = ("brettl_grass_bundle", "brettl_grass_bundle_top")


def planks_tex_of(w):
    return "skyroot_planks" if w == "skyroot" else w + "_planks"


def make_textures():
    t, items = {}, {}
    for w, spec in WOODS.items():
        if spec["new"]:
            t[w + "_log"] = make_bark(w + "_log", spec["bark"], [AMBER_CHUNK[2], AMBER_CHUNK[3]] if w == "amberoot" else None, 9)
            t[w + "_log_top"] = G.log_top(w + "_log_top", spec["bark"], spec["ring"])
            t["stripped_" + w + "_log"] = G.bark("stripped_" + w + "_log", spec["strip"])
            t["stripped_" + w + "_log_top"] = G.log_top("stripped_" + w + "_log_top", spec["strip"], spec["ring"])
            t[w + "_planks"] = G.planks(w + "_planks", spec["planks"], spec["seam"])
        t[w + "_door_bottom"] = door_tex(w + "_door_bottom", spec["planks"], spec["seam"], False)
        t[w + "_door_top"] = door_tex(w + "_door_top", spec["planks"], spec["seam"], True)
        t[w + "_trapdoor"] = trapdoor_tex(w + "_trapdoor", spec["planks"], spec["seam"])
        items[w + "_door"] = door_item(w + "_door", spec["planks"], spec["seam"])
    t["mossy_wisproot_log"] = mossy_bark("mossy_wisproot_log", WISP_BARK)
    t["amberoot_deposit"] = deposit_bark("amberoot_deposit", AMBER_BARK2)
    for name in LEAVES:
        t[name] = leaf_tex(name)
    for tree in TREES:
        t[tree + "_sapling"] = sapling_tex(tree)
    t["aether_short_grass"] = tuft("aether_short_grass", GREY, 7, 4, 8)
    t["aether_medium_grass"] = tuft("aether_medium_grass", GREY, 9, 7, 12)
    t["aether_long_grass"] = tuft("aether_long_grass", GREY, 11, 10, 15)
    t["bush_fern"] = fern("bush_fern", GREY)
    t["aether_bush"] = shrub("aether_bush")
    t["holystone_twig"] = twig("holystone_twig")
    t["hesperose"] = bloom_flower("hesperose", ramp("b8407f", "d65fa0", "ee86bd", "ffb3d8"), hx("ffe27a"))
    t["poasprout"] = poasprout("poasprout")
    t["lilichime"] = lilichime("lilichime")
    t["pluracian"] = pluracian("pluracian")
    t["blade_poa"] = blade_poa("blade_poa")
    t["satival_shoot"] = satival_shoot("satival_shoot")
    t["valkyrie_sprout"] = valkyrie_sprout("valkyrie_sprout")
    t["brettl_plant"] = brettl_plant("brettl_plant")
    t["tarabloom_bottom"], t["tarabloom_top"] = tarabloom_pair("tarabloom")
    t["brettl_grass_bundle"] = bundle("brettl_grass_bundle", False)
    t["brettl_grass_bundle_top"] = bundle("brettl_grass_bundle_top", True)
    for age in range(4):
        t["orange_tree_lower_%d" % age] = orange_stage(age, False)
        t["orange_tree_upper_%d" % age] = orange_stage(age, True)
    t["arilum"] = arilum("arilum", False)
    t["blooming_arilum"] = arilum("blooming_arilum", True)
    for m, colors, vines in MOSSES:
        t[m + "_moss_block"] = moss_block(m + "_moss_block", colors)
        if vines:
            t[m + "_vines"] = vines_tex(m + "_vines", colors)
    blue = ramp("3d5a78", "5c86ad", "7fb0d8", "bfe6ff")
    brown = ramp("4a2e1c", "6b4429", "8a5b36", "b58353")
    t["magnetic_shroom"] = shroom_plant("magnetic_shroom", blue, ramp("aab4c0", "8a94a2"), [(6, 6), (9, 5), (10, 8)])
    t["rotshroom"] = shroom_plant("rotshroom", brown, ramp("d8c8a8", "b8a888"), [(6, 6), (9, 5), (10, 8)])
    t["magnetic_shroom_block"] = shroom_block("magnetic_shroom_block", blue, [(3, 3), (10, 5), (6, 11), (12, 12)], ramp("bfe6ff", "d8f0ff", "eaf8ff", "ffffff"))
    t["magnetic_shroom_stem"] = shroom_stem("magnetic_shroom_stem", ramp("c2cad4", "aab4c0", "d8dee6"))
    t["rotshroom_block"] = shroom_block("rotshroom_block", brown, [(4, 4), (11, 3), (7, 10), (12, 11)], ramp("d8c8a8", "e8dcc0", "f2e8d0", "fff8e8"))
    t["woven_skyroot_sticks_top"] = weave("woven_skyroot_sticks_top", True)
    t["woven_skyroot_sticks_side"] = weave("woven_skyroot_sticks_side", False)
    items["orange"] = item_orange()
    items["wyndberry"] = item_wyndberry()
    items["arilum_bulb"] = item_arilum_bulb()
    items["valkyrie_wings"] = item_valkyrie_wings()
    items["brettl_grass"] = item_brettl_grass()
    items["satival_bulb"] = item_satival_bulb()
    return t, items


def textures():
    t, items = make_textures()
    for name, im in t.items():
        path = os.path.join(ASSETS, "textures", "block", SUB, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)
    for name, im in items.items():
        path = os.path.join(ASSETS, "textures", "item", SUB, name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save(path)


# ---------------------------------------------------------------- names

NAMES = {}
for _b in ITEM_BLOCKS:
    NAMES[_b] = _b.replace("_", " ").title()
for _p in POTTED:
    NAMES["potted_" + _p] = "Potted " + _p.replace("_", " ").title()
ITEM_NAMES = {i: i.replace("_", " ").title() for i in ITEMS}


# ---------------------------------------------------------------- models, blockstates, item definitions

GRASS_TINT = [{"type": "minecraft:grass", "downfall": 1.0, "temperature": 0.5}]


def bm(name, data):
    write(os.path.join(ASSETS, "models", "block", SUB, name + ".json"), data)


def im_(name, data):
    write(os.path.join(ASSETS, "models", "item", SUB, name + ".json"), data)


def bs(name, data):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), data)


def idef(name, model, tints=None):
    m = {"type": "minecraft:model", "model": model}
    if tints:
        m["tints"] = tints
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": m})


def state(model, **rot):
    out = {"model": bmodel(model)}
    out.update(rot)
    return out


def spin(y):
    """A turn about y with no uvlock (doors, trapdoors)."""
    return {"y": y % 360} if y % 360 else {}


def rotation(x=0, y=0, lock=False):
    x, y = x % 360, y % 360
    out = {}
    if lock or x or y:
        out["uvlock"] = True
    if x:
        out["x"] = x
    if y:
        out["y"] = y
    return out


def flat_item(name, texture_name, kind="block", tints=None):
    """A flat item (plants, doors): drawn from its own texture."""
    im_(name, {"parent": mc("item/generated"), "textures": {"layer0": tex(texture_name, kind)}})
    idef(name, imodel(name), tints)


FACES = ["east", "south", "west", "north"]
BASE_Y = {"east": 0, "south": 90, "west": 180, "north": 270}


def stairs_states(w):
    out = {}
    for f in FACES:
        base = BASE_Y[f]
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = w + "_stairs" + ("" if shape == "straight" else "_" + shape.split("_")[0])
                left = shape.endswith("left")
                if half == "bottom":
                    y = base - 90 if left else base
                    x = 0
                else:
                    x = 180
                    y = base if (left or shape == "straight") else base + 90
                out["facing=%s,half=%s,shape=%s" % (f, half, shape)] = state(model, **rotation(x, y))
    return out


def gate_states(w):
    out = {}
    gy = {"south": 0, "west": 90, "north": 180, "east": 270}
    for f, y in gy.items():
        for wall in (False, True):
            for opened in (False, True):
                model = w + "_fence_gate" + ("_wall" if wall else "") + ("_open" if opened else "")
                out["facing=%s,in_wall=%s,open=%s" % (f, str(wall).lower(), str(opened).lower())] = state(model, **rotation(0, y, True))
    return out


def door_states(w):
    out = {}
    for f in FACES:
        base = BASE_Y[f]
        for half, hname in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    model = "%s_door_%s_%s%s" % (w, hname, hinge, "_open" if opened else "")
                    y = base if not opened else base + (90 if hinge == "left" else 270)
                    out["facing=%s,half=%s,hinge=%s,open=%s" % (f, half, hinge, str(opened).lower())] = state(model, **spin(y))
    return out


def trapdoor_states(w):
    out = {}
    oy = {"east": 90, "north": 0, "south": 180, "west": 270}
    for f in FACES:
        for half in ("bottom", "top"):
            out["facing=%s,half=%s,open=false" % (f, half)] = state(w + "_trapdoor_" + half)
            out["facing=%s,half=%s,open=true" % (f, half)] = state(w + "_trapdoor_open", **spin(oy[f]))
    return out


def button_states(w):
    out = {}
    fy = {"east": 90, "north": 0, "south": 180, "west": 270}
    cy = {"east": 270, "north": 180, "south": 0, "west": 90}
    for f in FACES:
        for powered in (False, True):
            m = w + "_button" + ("_pressed" if powered else "")
            p = str(powered).lower()
            out["face=floor,facing=%s,powered=%s" % (f, p)] = state(m, **({"y": fy[f]} if fy[f] else {}))
            out["face=ceiling,facing=%s,powered=%s" % (f, p)] = state(m, x=180, **({"y": cy[f]} if cy[f] else {}))
            out["face=wall,facing=%s,powered=%s" % (f, p)] = state(m, **rotation(90, fy[f], True))
    return out


def wood_assets(w):
    spec = WOODS[w]
    planks = planks_tex_of(w)
    pt = tex(planks)
    if spec["new"]:
        bm(planks, {"parent": mc("block/cube_all"), "textures": {"all": pt}})
        bs(planks, {"variants": {"": state(planks)}})
        idef(planks, bmodel(planks))
    three = {"bottom": pt, "side": pt, "top": pt}
    bm(w + "_stairs", {"parent": mc("block/stairs"), "textures": three})
    bm(w + "_stairs_inner", {"parent": mc("block/inner_stairs"), "textures": three})
    bm(w + "_stairs_outer", {"parent": mc("block/outer_stairs"), "textures": three})
    bs(w + "_stairs", {"variants": stairs_states(w)})
    idef(w + "_stairs", bmodel(w + "_stairs"))
    bm(w + "_slab", {"parent": mc("block/slab"), "textures": three})
    bm(w + "_slab_top", {"parent": mc("block/slab_top"), "textures": three})
    bs(w + "_slab", {"variants": {"type=bottom": state(w + "_slab"), "type=top": state(w + "_slab_top"), "type=double": state(planks)}})
    idef(w + "_slab", bmodel(w + "_slab"))
    one = {"texture": pt}
    bm(w + "_fence_post", {"parent": mc("block/fence_post"), "textures": one})
    bm(w + "_fence_side", {"parent": mc("block/fence_side"), "textures": one})
    bm(w + "_fence_inventory", {"parent": mc("block/fence_inventory"), "textures": one})
    sides = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
    bs(w + "_fence", {"multipart": [{"apply": state(w + "_fence_post")}] + [
        {"apply": state(w + "_fence_side", uvlock=True, **({"y": y} if y else {})), "when": {d: "true"}} for d, y in sides]})
    idef(w + "_fence", bmodel(w + "_fence_inventory"))
    for suffix, parent in (("", "template_fence_gate"), ("_open", "template_fence_gate_open"), ("_wall", "template_fence_gate_wall"),
                           ("_wall_open", "template_fence_gate_wall_open")):
        bm(w + "_fence_gate" + suffix, {"parent": mc("block/" + parent), "textures": one})
    bs(w + "_fence_gate", {"variants": gate_states(w)})
    idef(w + "_fence_gate", bmodel(w + "_fence_gate"))
    dt = {"bottom": tex(w + "_door_bottom"), "top": tex(w + "_door_top")}
    for half in ("bottom", "top"):
        for hinge in ("left", "right"):
            for suffix in ("", "_open"):
                bm("%s_door_%s_%s%s" % (w, half, hinge, suffix), {"parent": mc("block/door_%s_%s%s" % (half, hinge, suffix)), "textures": dt})
    bs(w + "_door", {"variants": door_states(w)})
    flat_item(w + "_door", w + "_door", "item")
    tt = {"texture": tex(w + "_trapdoor")}
    for half, parent in (("bottom", "template_trapdoor_bottom"), ("top", "template_trapdoor_top"), ("open", "template_trapdoor_open")):
        bm(w + "_trapdoor_" + half, {"parent": mc("block/" + parent), "textures": tt})
    bs(w + "_trapdoor", {"variants": trapdoor_states(w)})
    idef(w + "_trapdoor", bmodel(w + "_trapdoor_bottom"))
    bm(w + "_button", {"parent": mc("block/button"), "textures": one})
    bm(w + "_button_pressed", {"parent": mc("block/button_pressed"), "textures": one})
    bm(w + "_button_inventory", {"parent": mc("block/button_inventory"), "textures": one})
    bs(w + "_button", {"variants": button_states(w)})
    idef(w + "_button", bmodel(w + "_button_inventory"))
    bm(w + "_pressure_plate", {"parent": mc("block/pressure_plate_up"), "textures": one})
    bm(w + "_pressure_plate_down", {"parent": mc("block/pressure_plate_down"), "textures": one})
    bs(w + "_pressure_plate", {"variants": {"powered=false": state(w + "_pressure_plate"), "powered=true": state(w + "_pressure_plate_down")}})
    idef(w + "_pressure_plate", bmodel(w + "_pressure_plate"))


def log_assets(b, side, end):
    bm(b, {"parent": mc("block/cube_column"), "textures": {"end": tex(end), "side": tex(side)}})
    bm(b + "_horizontal", {"parent": mc("block/cube_column_horizontal"), "textures": {"end": tex(end), "side": tex(side)}})
    bs(b, {"variants": {"axis=x": state(b + "_horizontal", x=90, y=90), "axis=y": state(b), "axis=z": state(b + "_horizontal", x=90)}})
    idef(b, bmodel(b))


def vine_states(b):
    parts = []
    for when, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270})):
        apply = state(b, **dict({"uvlock": True} if rot else {}, **rot))
        parts.append({"apply": apply, "when": {when: "true"}})
        parts.append({"apply": apply, "when": {"east": "false", "north": "false", "south": "false", "up": "false", "west": "false"}})
    return {"multipart": parts}


def shroom_states(b):
    parts = []
    faces = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})]
    for f, rot in faces:
        parts.append({"apply": state(b, **dict({"uvlock": True} if rot else {}, **rot)), "when": {f: "true"}})
    for f, rot in faces:
        parts.append({"apply": dict({"model": mc("block/mushroom_block_inside")}, **rot), "when": {f: "false"}})
    return {"multipart": parts}


def assets():
    for w in WOODS:
        wood_assets(w)
    for b, (side, end) in LOGLIKE.items():
        log_assets(b, side, end)
    for b in LEAVES:
        bm(b, {"parent": mc("block/leaves"), "textures": {"all": tex(b)}})
        bs(b, {"variants": {"": state(b)}})
        idef(b, bmodel(b))
    # crosses: saplings, flowers, shoots, mushrooms (flat items); the grasses and fern are tinted
    for b in SAPLINGS + SMALL_FLOWERS + CROSS_PLANTS:
        bm(b, {"parent": mc("block/cross"), "textures": {"cross": tex(b)}})
        bs(b, {"variants": {"": state(b)}})
        flat_item(b, b)
    for b in GRASSES:
        bm(b, {"parent": mc("block/tinted_cross"), "textures": {"cross": tex(b)}})
        bs(b, {"variants": {"": state(b)}})
        flat_item(b, b, tints=GRASS_TINT)
    # the shrub: a squat leafy block (cutout holes from the texture)
    face = lambda: {"uv": [0, 0, 16, 16], "texture": "#all"}
    bm("aether_bush", {"textures": {"particle": "#all", "all": tex("aether_bush")}, "elements": [
        {"from": [2, 0, 2], "to": [14, 13, 14], "faces": {d: face() for d in ("down", "up", "north", "south", "west", "east")}}]})
    bs("aether_bush", {"variants": {"": state("aether_bush")}})
    idef("aether_bush", bmodel("aether_bush"))
    # the twig lies flat, turned any of four ways
    bm("holystone_twig", {"ambientocclusion": False, "textures": {"particle": tex("holystone_twig"), "texture": tex("holystone_twig")}, "elements": [
        {"from": [0, 0.1, 0], "to": [16, 0.1, 16], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                                                              "down": {"uv": [0, 16, 16, 0], "texture": "#texture"}}}]})
    bs("holystone_twig", {"variants": {"": [state("holystone_twig"), state("holystone_twig", y=90), state("holystone_twig", y=180),
                                            state("holystone_twig", y=270)]}})
    flat_item("holystone_twig", "holystone_twig")
    # tarabloom: two crosses
    for half in ("bottom", "top"):
        bm("tarabloom_" + half, {"parent": mc("block/cross"), "textures": {"cross": tex("tarabloom_" + half)}})
    bs("tarabloom", {"variants": {"half=lower": state("tarabloom_bottom"), "half=upper": state("tarabloom_top")}})
    flat_item("tarabloom", "tarabloom_top")
    # the orange tree: a lower and an upper cross for each age
    variants = {}
    for age in range(4):
        for half, name in (("lower", "orange_tree_lower_%d"), ("upper", "orange_tree_upper_%d")):
            m = name % age
            bm(m, {"parent": mc("block/cross"), "textures": {"cross": tex(m)}})
            variants["age=%d,half=%s" % (age, half)] = state(m)
    bs("orange_tree", {"variants": variants})
    flat_item("orange_tree", "orange_tree_upper_3")
    # water plants: flat pads, turned any of four ways
    for b in ("arilum", "blooming_arilum"):
        bm(b, {"parent": mc("block/lily_pad"), "textures": {"particle": tex(b), "texture": tex(b)}})
        bs(b, {"variants": {"": [state(b), state(b, y=90), state(b, y=180), state(b, y=270)]}})
        flat_item(b, b)
    # mosses
    for m, _, vines in MOSSES:
        blk = m + "_moss_block"
        bm(blk, {"parent": mc("block/cube_all"), "textures": {"all": tex(blk)}})
        bs(blk, {"variants": {"": state(blk)}})
        idef(blk, bmodel(blk))
        car = m + "_moss_carpet"
        bm(car, {"parent": mc("block/carpet"), "textures": {"wool": tex(blk)}})
        bs(car, {"variants": {"": state(car)}})
        idef(car, bmodel(car))
        if vines:
            v = m + "_vines"
            bm(v, {"parent": mc("block/vine"), "textures": {"particle": tex(v), "vine": tex(v)}})
            bs(v, vine_states(v))
            flat_item(v, v)
    # huge mushroom blocks
    for b in ("magnetic_shroom_block", "magnetic_shroom_stem", "rotshroom_block"):
        bm(b, {"parent": mc("block/template_single_face"), "textures": {"texture": tex(b)}})
        bm(b + "_inventory", {"parent": mc("block/cube_all"), "textures": {"all": tex(b)}})
        bs(b, shroom_states(b))
        idef(b, bmodel(b + "_inventory"))
    # woven sticks: an 8-pixel slab of weave
    top, side = tex("woven_skyroot_sticks_top"), tex("woven_skyroot_sticks_side")
    sf = {"uv": [0, 8, 16, 16], "texture": "#side"}
    bm("woven_skyroot_sticks", {"textures": {"particle": side, "top": top, "side": side}, "elements": [
        {"from": [0, 0, 0], "to": [16, 8, 16], "faces": {"down": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "down"},
                                                         "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                                                         "north": dict(sf), "south": dict(sf), "west": dict(sf), "east": dict(sf)}}]})
    bs("woven_skyroot_sticks", {"variants": {"": state("woven_skyroot_sticks")}})
    idef("woven_skyroot_sticks", bmodel("woven_skyroot_sticks"))
    # the leaf pile: a carpet of skyroot leaves (Aether I's texture, painted by gen_aether.py)
    bm("skyroot_leaf_pile", {"parent": mc("block/carpet"), "textures": {"wool": tex("skyroot_leaves")}})
    bs("skyroot_leaf_pile", {"variants": {"": state("skyroot_leaf_pile")}})
    idef("skyroot_leaf_pile", bmodel("skyroot_leaf_pile"))
    # potted plants (no item)
    for p in POTTED:
        parent = "tinted_flower_pot_cross" if p == "bush_fern" else "flower_pot_cross"
        bm("potted_" + p, {"parent": mc("block/" + parent), "textures": {"plant": tex(p)}})
        bs("potted_" + p, {"variants": {"": state("potted_" + p)}})
    # items
    for i in ITEMS:
        flat_item(i, i, "item")
    # lang
    lang = {}
    for b, n in NAMES.items():
        lang["block.jaymospowers." + b] = n
    for i, n in ITEM_NAMES.items():
        lang["item.jaymospowers." + i] = n
    write(os.path.join(ASSETS, "lang", "parts", "aether_flora.json"), lang)


# ---------------------------------------------------------------- loot

SILK, SHEAR, SURVIVES, table, item_entry = G.SILK, G.SHEAR, G.SURVIVES, G.table, G.item_entry
EXPLODE = {"type": "minecraft:explosion_decay"}


def match(block, **st):
    return {"type": "minecraft:match_block", "blocks": jp(block), "state": st}


def count(lo, hi):
    return {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}


def loot():
    out = os.path.join(DATA, "loot_table", "blocks")
    tables = {}
    selfs = [b for b in ITEM_BLOCKS if b not in LEAVES]
    for b in selfs:
        tables[b] = G.self_table(b)
    for w in WOODS:
        s = w + "_slab"
        tables[s] = table(s, [{"rolls": 1, "entries": [item_entry(jp(s), modifier=[
            {"type": "minecraft:set_count", "condition": match(s, type="double"), "count": 2}, EXPLODE])]}])
        for d in (w + "_door",):
            tables[d] = table(d, [{"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp(d), condition=match(d, half="lower"))]}])
    tables["tarabloom"] = table("tarabloom", [{"rolls": 1, "condition": SURVIVES, "entries": [
        item_entry(jp("tarabloom"), condition=match("tarabloom", half="lower"))]}])
    # leaves drop their own sapling (irradiated leaves none)
    for tree in TREES:
        tables[tree + "_leaves"] = G.leaves_table(tree + "_leaves", sapling=tree + "_sapling")
    tables["irradiated_leaves"] = G.leaves_table("irradiated_leaves")
    # the grasses and the fern: shears, or now and then wheat seeds
    seeds = item_entry(mc("wheat_seeds"), condition={"type": "minecraft:random_chance", "chance": 0.125}, modifier=[
        {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
         "parameters": {"bonusMultiplier": 2}}, EXPLODE])
    for b in GRASSES:
        tables[b] = table(b, [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [item_entry(jp(b), condition=SHEAR), seeds]}]}])
    for v in ("bryalinn_vines", "shayelinn_vines"):
        tables[v] = table(v, [{"rolls": 1, "condition": SHEAR, "entries": [item_entry(jp(v))]}])
    tables["valkyrie_sprout"] = G.silk_else("valkyrie_sprout", jp("valkyrie_wings"), [count(1, 2), EXPLODE])
    tables["satival_shoot"] = G.silk_else("satival_shoot", jp("satival_bulb"), [EXPLODE])
    tables["brettl_plant"] = G.silk_else("brettl_plant", jp("brettl_grass"), [count(1, 2), EXPLODE])
    tables["blooming_arilum"] = G.silk_else("blooming_arilum", jp("arilum_bulb"), [count(1, 2), EXPLODE])
    for b, small in (("magnetic_shroom_block", "magnetic_shroom"), ("rotshroom_block", "rotshroom")):
        tables[b] = G.silk_else(b, jp(small), [count(0, 2), EXPLODE])
    tables["magnetic_shroom_stem"] = table("magnetic_shroom_stem", [{"rolls": 1, "condition": SILK, "entries": [item_entry(jp("magnetic_shroom_stem"))]}])
    tables["amberoot_deposit"] = table("amberoot_deposit", [
        {"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp("amberoot_deposit"))]},
        {"rolls": 1, "condition": {"type": "minecraft:match_tool", "predicate": {"items": "#minecraft:axes"}},
         "entries": [item_entry(jp("golden_amber"), modifier=[count(1, 2)])]}])
    tables["orange_tree"] = table("orange_tree", [
        {"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp("orange_tree"), condition=match("orange_tree", half="lower"))]},
        {"rolls": 1, "condition": match("orange_tree", half="lower", age="3"), "entries": [item_entry(jp("orange"), modifier=[
            count(1, 3), {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:uniform_bonus_count",
                          "parameters": {"bonusMultiplier": 1}}, EXPLODE])]}])
    for p in POTTED:
        b = "potted_" + p
        tables[b] = table(b, [{"rolls": 1, "condition": SURVIVES, "entries": [item_entry(mc("flower_pot"))]},
                              {"rolls": 1, "condition": SURVIVES, "entries": [item_entry(jp(p))]}])
    missing = [b for b in ALL_BLOCKS if b not in tables]
    assert not missing, missing
    for b, t in tables.items():
        write(os.path.join(out, b + ".json"), t)


# ---------------------------------------------------------------- recipes

def recipes():
    out = os.path.join(DATA, "recipe", SUB, "flora")
    shaped = lambda key, pattern, result, count=1, group=None, cat="building": dict(
        {"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
         "result": {"id": result, "count": count} if count > 1 else {"id": result}}, **({"group": group} if group else {}))
    shapeless = lambda ings, result, count=1, group=None, cat="misc": dict(
        {"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": ings,
         "result": {"id": result, "count": count} if count > 1 else {"id": result}}, **({"group": group} if group else {}))
    r = {}
    for w, spec in WOODS.items():
        p = jp(planks_tex_of(w))
        if spec["new"]:
            r[w + "_planks"] = shapeless(["#" + jp(w + "_logs")], p, 4, "planks", "building")
            r[w + "_wood"] = shaped({"#": jp(w + "_log")}, ["##", "##"], jp(w + "_wood"), 3, "bark")
            r["stripped_" + w + "_wood"] = shaped({"#": jp("stripped_" + w + "_log")}, ["##", "##"], jp("stripped_" + w + "_wood"), 3, "bark")
        r[w + "_stairs"] = shaped({"#": p}, ["#  ", "## ", "###"], jp(w + "_stairs"), 4, "wooden_stairs")
        r[w + "_slab"] = shaped({"#": p}, ["###"], jp(w + "_slab"), 6, "wooden_slab")
        r[w + "_fence"] = shaped({"#": mc("stick"), "W": p}, ["W#W", "W#W"], jp(w + "_fence"), 3, "wooden_fence")
        r[w + "_fence_gate"] = shaped({"#": mc("stick"), "W": p}, ["#W#", "#W#"], jp(w + "_fence_gate"), 1, "wooden_fence_gate", "redstone")
        r[w + "_door"] = shaped({"#": p}, ["##", "##", "##"], jp(w + "_door"), 3, "wooden_door", "redstone")
        r[w + "_trapdoor"] = shaped({"#": p}, ["###", "###"], jp(w + "_trapdoor"), 2, "wooden_trapdoor", "redstone")
        r[w + "_button"] = shapeless([p], jp(w + "_button"), 1, "wooden_button", "redstone")
        r[w + "_pressure_plate"] = shaped({"#": p}, ["##"], jp(w + "_pressure_plate"), 1, "wooden_pressure_plate", "redstone")
    r["mossy_wisproot_log"] = shapeless([jp("wisproot_log"), jp("bryalinn_moss_block")], jp("mossy_wisproot_log"), 1, "mossy_log", "building")
    r["brettl_grass_bundle"] = shaped({"#": jp("brettl_grass")}, ["###", "###", "###"], jp("brettl_grass_bundle"))
    r["brettl_grass_from_bundle"] = shapeless([jp("brettl_grass_bundle")], jp("brettl_grass"), 9, "brettl_grass")
    for m, _, _ in MOSSES:
        r[m + "_moss_carpet"] = shaped({"#": jp(m + "_moss_block")}, ["##"], jp(m + "_moss_carpet"), 3, "carpet")
    # suspicious stew: hesperose regeneration 8 s, poasprout saturation, lilichime night vision 10 s, pluracian jump boost 6 s
    for flower, effect, ticks in (("hesperose", "minecraft:regeneration", 160), ("poasprout", "minecraft:saturation", 7),
                                  ("lilichime", "minecraft:night_vision", 200), ("pluracian", "minecraft:jump_boost", 120)):
        r["suspicious_stew_from_" + flower] = {
            "type": "minecraft:crafting_shapeless", "category": "misc", "group": "suspicious_stew",
            "ingredients": [mc("bowl"), "#minecraft:mushrooms", "#minecraft:mushrooms", jp(flower)],
            "result": {"id": mc("suspicious_stew"), "components": {"minecraft:suspicious_stew_effects": [{"duration": ticks, "id": effect}]}}}
    for name, data in r.items():
        write(os.path.join(out, name + ".json"), data)


# ---------------------------------------------------------------- tags

def tags():
    b = os.path.join(MCDATA, "tags", "block")
    i = os.path.join(MCDATA, "tags", "item")
    jb = os.path.join(DATA, "tags", "block")
    ji = os.path.join(DATA, "tags", "item")
    ids = lambda names: [jp(n) for n in names]
    # the logs of each new wood, and one tag over all of them (aether_logs is left alone: skyroot planks are made from it)
    flora_logs = []
    for w, blocks in WOOD_LOGS.items():
        for root in (jb, ji):
            write(os.path.join(root, w + "_logs.json"), {"values": ids(blocks)})
        flora_logs.append("#" + jp(w + "_logs"))
    for root in (jb, ji):
        write(os.path.join(root, "aether_flora_logs.json"), {"values": flora_logs})
    new_planks = [planks_tex_of(w) for w in WOODS if WOODS[w]["new"]]
    kinds = {"wooden_stairs": "stairs", "wooden_slabs": "slab", "wooden_fences": "fence", "fence_gates": "fence_gate", "wooden_doors": "door",
             "wooden_trapdoors": "trapdoor", "wooden_buttons": "button", "wooden_pressure_plates": "pressure_plate"}
    for root in (b, i):
        merge_tag(os.path.join(root, "logs.json"), ["#" + jp("aether_flora_logs")])
        merge_tag(os.path.join(root, "logs_that_burn.json"), ["#" + jp("aether_flora_logs")])
        merge_tag(os.path.join(root, "planks.json"), ids(new_planks))
        merge_tag(os.path.join(root, "leaves.json"), ids(LEAVES))
        merge_tag(os.path.join(root, "saplings.json"), ids(SAPLINGS))
        merge_tag(os.path.join(root, "small_flowers.json"), ids(SMALL_FLOWERS))
        merge_tag(os.path.join(root, "flowers.json"), ids(["tarabloom", "blade_poa"]))
        for tag, shape in kinds.items():
            merge_tag(os.path.join(root, tag + ".json"), ids([w + "_" + shape for w in WOODS]))
    grass_like = ["aether_short_grass", "aether_medium_grass", "aether_long_grass", "bush_fern"]
    merge_tag(os.path.join(b, "replaceable.json"), ids(grass_like + ["bryalinn_vines", "shayelinn_vines"]))
    merge_tag(os.path.join(b, "replaceable_by_trees.json"), ids(grass_like + ["holystone_twig"]))
    merge_tag(os.path.join(b, "climbable.json"), ids(["bryalinn_vines", "shayelinn_vines"]))
    merge_tag(os.path.join(b, "flower_pots.json"), ids(POTTED_BLOCKS))
    merge_tag(os.path.join(b, "supports_vegetation.json"), ids([m + "_moss_block" for m, _, _ in MOSSES]))
    axe = ["#" + jp("aether_flora_logs")] + ids(new_planks + [w + "_" + s for w in WOODS for s in SHAPES if s not in ("button", "pressure_plate")]
                                                + ["magnetic_shroom_block", "magnetic_shroom_stem", "rotshroom_block", "woven_skyroot_sticks",
                                                   "bryalinn_vines", "shayelinn_vines"])
    merge_tag(os.path.join(b, "mineable", "axe.json"), axe + ids([w + "_button" for w in WOODS] + [w + "_pressure_plate" for w in WOODS]))
    hoe = ids(LEAVES + [m + "_moss_block" for m, _, _ in MOSSES] + [m + "_moss_carpet" for m, _, _ in MOSSES]
              + ["skyroot_leaf_pile", "aether_bush", "brettl_grass_bundle"])
    merge_tag(os.path.join(b, "mineable", "hoe.json"), hoe)


if __name__ == "__main__":
    textures()
    assets()
    loot()
    recipes()
    tags()
    print("Aether flora assets and data written.")
