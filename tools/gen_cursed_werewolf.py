"""Paints the cursed werewolves' coats (Kingdoms 0.7.1): each of the werewolf's three ordinary coats (black, brown,
timber) worn three ways, mangy (bald, sore patches), scarred (old pale wounds) and patchy (piebald, uneven fur), and three
sets of rags (what's left of their clothes after the change: a split shirt and trousers, trousers only, shreds and a
sleeve), greyscale so the game tints them the colour of the clothes they wore. Everything is drawn from our own
werewolf coats, so it fits the model's UV layout exactly:

    python -I tools/gen_cursed_werewolf.py

Part boxes are read from WerewolfModel.createBodyLayer (the textures are twice its 64 x 128 texture size).
"""
import os
import re

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KINGDOMS = os.path.join(ROOT, "kingdoms", "src")
MODEL = os.path.join(KINGDOMS, "client", "java", "com", "jaymoskingdoms", "client", "monsters", "WerewolfModel.java")
TEX = os.path.join(KINGDOMS, "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "legend")
COATS = ["black", "brown", "timber"]
STYLES = ["mangy", "scarred", "patchy"]
SCALE = 2
# parts never touched: teeth, the mouth's inside, the claws
PROTECTED = re.compile(r"teeth_.*|mouth|.finger\d")


def parts():
    """name -> (u, v, w, h, d) in texture pixels."""
    src = open(MODEL, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'addOrReplaceChild\("(\w+)", CubeListBuilder\.create\(\)\.texOffs\((\d+), (\d+)\)\.addBox\([^,]+,[^,]+,[^,]+, (\d+), (\d+), (\d+)\)', src):
        name, u, v, w, h, d = m.group(1), *map(int, m.groups()[1:])
        out[name] = tuple(x * SCALE for x in (u, v, w, h, d))
    return out


def box_mask(shape, part):
    u, v, w, h, d = part
    m = np.zeros(shape, bool)
    m[v:v + d, u + d:u + d + 2 * w] = True  # top and bottom
    m[v + d:v + d + h, u:u + 2 * d + 2 * w] = True  # the four sides
    return m


def noise(rng, shape, cell):
    """Smooth value noise in 0..1, blobs about {cell} pixels across."""
    small = rng.random((shape[0] // cell + 2, shape[1] // cell + 2))
    img = Image.fromarray((small * 255).astype(np.uint8)).resize((shape[1] + 2 * cell, shape[0] + 2 * cell), Image.BICUBIC)
    return np.asarray(img, float)[cell:cell + shape[0], cell:cell + shape[1]] / 255.0


def fur_mask(rgba, ps):
    """Fur pixels: opaque, on an unprotected part, and not an eye, gum or tooth (strong red or near white)."""
    a = rgba[..., 3] > 0
    r, g, b = rgba[..., 0].astype(int), rgba[..., 1].astype(int), rgba[..., 2].astype(int)
    red = (r - np.maximum(g, b) > 50)
    pale = (r + g + b) > 600
    keep = np.zeros(a.shape, bool)
    for name, p in ps.items():
        if PROTECTED.fullmatch(name):
            keep |= box_mask(a.shape, p)
    return a & ~red & ~pale & ~keep


def mix(base, colour, t):
    t = t[..., None]
    return base * (1 - t) + np.asarray(colour, float) * t


SKIN, SORE = (138, 104, 98), (122, 58, 52)


def mangy(rgb, fur, rng):
    n = noise(rng, fur.shape, 9)
    fine = rng.random(fur.shape)
    bald = fur & (n > 0.66)
    thin = fur & (n > 0.56) & ~bald
    skin = mix(np.broadcast_to(np.asarray(SKIN, float), rgb.shape), (90, 66, 62), fine * 0.5)
    out = rgb.copy()
    out[bald] = skin[bald]
    sore = bald & (noise(rng, fur.shape, 4) > 0.72)
    out[sore] = mix(out, SORE, np.full(fur.shape, 0.7))[sore]
    # thinning fur round the bald spots: skin showing through, a few stray hairs left
    out[thin] = mix(rgb, SKIN, np.where(fine > 0.5, 0.45, 0.15))[thin]
    return dull(out, fur, 0.25)


def scarred(rgb, fur, rng):
    out = dull(rgb, fur, 0.1)
    h, w = fur.shape
    ys, xs = np.nonzero(fur)
    for _ in range(46):
        i = rng.integers(len(ys))
        y, x = float(ys[i]), float(xs[i])
        ang = rng.uniform(0, np.pi)
        length = rng.integers(6, 18)
        dy, dx = np.sin(ang), np.cos(ang)
        for s in range(length):
            yy, xx = int(round(y + dy * s)), int(round(x + dx * s + np.sin(s * 0.7) * 0.6))
            # the pale seam, and the puckered, darker skin either side of it
            for oy, colour, t in ((1, (88, 52, 48), 0.6), (-1, (70, 46, 42), 0.6), (0, (196, 150, 140), 1.0)):
                py = yy + oy
                if 0 <= py < h and 0 <= xx < w and fur[py, xx]:
                    out[py, xx] = out[py, xx] * (1 - t) + np.asarray(colour, float) * t
    # the fur never grew back right round them
    n = noise(rng, fur.shape, 5)
    bare = fur & (n > 0.8)
    out[bare] = mix(out, SKIN, np.full(fur.shape, 0.55))[bare]
    return out


def patchy(rgb, fur, rng):
    n = noise(rng, fur.shape, 11)
    m = noise(rng, fur.shape, 6)
    out = rgb.copy()
    light = fur & (n > 0.6)
    dark = fur & (n < 0.33)
    grey = fur & (m > 0.7)
    lum = rgb.mean(axis=2, keepdims=True)
    out[light] = (rgb * 0.45 + np.clip(lum * 1.6 + 70, 0, 235) * 0.55)[light]
    out[dark] = (rgb * 0.55)[dark]
    out[grey] = mix(rgb, (120, 116, 110), np.full(fur.shape, 0.5))[grey]
    bald = fur & (m < 0.2)
    out[bald] = mix(rgb, SKIN, np.full(fur.shape, 0.6))[bald]
    return dull(out, fur, 0.15)


def dull(rgb, fur, amount):
    """Duller and dirtier: towards grey, a little darker."""
    lum = rgb.mean(axis=2, keepdims=True)
    out = rgb.copy()
    out[fur] = (rgb * (1 - amount) + lum * amount * 0.9)[fur]
    return out


# ---- rags ----------------------------------------------------------------------------------------------------------

def ragged(rng, shape, part, top, bottom, holes):
    """Cloth over a part's four sides from {top} to {bottom} (shares of its height, a ragged hem), its top face if it
    starts at 0, with torn holes."""
    u, v, w, h, d = part
    m = np.zeros(shape, bool)
    width = 2 * d + 2 * w
    edge = noise(rng, (4, width + 4), 3)[1, :width]
    for x in range(width):
        lo = int(h * top)
        hi = int(h * bottom + (edge[x] - 0.5) * 6)
        if rng.random() < 0.18:
            hi -= rng.integers(2, 6)  # a strip torn away
        m[v + d + lo:v + d + max(lo, min(h, hi)), u + x] = True
    if top == 0:
        m[v:v + d, u + d:u + d + w] = True
    hole = noise(rng, shape, 4) > 1 - holes
    return m & ~hole


def rags(ps, shape_no, rng, shape):
    m = np.zeros(shape, bool)
    if shape_no == 0:  # a split shirt and trousers
        m |= ragged(rng, shape, ps["chest"], 0, 1, 0.22)
        m |= ragged(rng, shape, ps["abdomen"], 0, 0.55, 0.18)
        m |= ragged(rng, shape, ps["lleg_a"], 0, 0.85, 0.12)
        m |= ragged(rng, shape, ps["rleg_a"], 0, 0.8, 0.12)
    elif shape_no == 1:  # trousers, and a strip of shirt round the waist
        m |= ragged(rng, shape, ps["abdomen"], 0.55, 1, 0.1)
        m |= ragged(rng, shape, ps["lleg_a"], 0, 1, 0.1)
        m |= ragged(rng, shape, ps["rleg_a"], 0, 1, 0.1)
        m |= ragged(rng, shape, ps["rleg_b"], 0, 0.6, 0.15)
    else:  # shreds of a shirt, a sleeve, one trouser leg
        m |= ragged(rng, shape, ps["chest"], 0, 0.7, 0.4)
        m |= ragged(rng, shape, ps["rarm_a"], 0, 1, 0.15)
        m |= ragged(rng, shape, ps["rarm_b"], 0, 0.45, 0.2)
        m |= ragged(rng, shape, ps["lleg_a"], 0, 0.9, 0.15)
    weave = noise(rng, shape, 2)
    dirt = noise(rng, shape, 7)
    val = 205 + (weave - 0.5) * 40 - np.clip(dirt - 0.55, 0, 1) * 160
    # the torn edges darker (frayed, bloodied), the weave's rows showing
    edge = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
    val = np.where(edge, val * 0.62, val)
    val = np.where((np.arange(shape[0])[:, None] % 4) == 0, val * 0.9, val)
    out = np.zeros(shape + (4,), np.uint8)
    v8 = np.clip(val, 0, 255).astype(np.uint8)
    out[..., 0] = out[..., 1] = out[..., 2] = v8
    out[..., 3] = np.where(m, 255, 0)
    blood = m & (noise(rng, shape, 3) > 0.88)
    out[blood, 0] = (v8[blood] * 0.75).astype(np.uint8)
    out[blood, 1] = (v8[blood] * 0.35).astype(np.uint8)
    out[blood, 2] = (v8[blood] * 0.32).astype(np.uint8)
    return out


def main():
    ps = parts()
    for ci, coat in enumerate(COATS):
        src = np.asarray(Image.open(os.path.join(TEX, f"werewolf_{coat}.png")).convert("RGBA")).copy()
        fur = fur_mask(src, ps)
        for si, (style, paint) in enumerate(zip(STYLES, (mangy, scarred, patchy))):
            rng = np.random.default_rng(7001 + ci * 10 + si)
            rgb = paint(src[..., :3].astype(float), fur, rng)
            out = src.copy()
            out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
            Image.fromarray(out, "RGBA").save(os.path.join(TEX, f"werewolf_cursed_{coat}_{style}.png"))
    shape = src.shape[:2]
    for n in range(3):
        Image.fromarray(rags(ps, n, np.random.default_rng(7100 + n), shape), "RGBA").save(os.path.join(TEX, f"werewolf_rags_{n}.png"))
    print("wrote", len(COATS) * len(STYLES), "coats and 3 rags to", TEX)


if __name__ == "__main__":
    main()
