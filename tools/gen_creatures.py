#!/usr/bin/env python3
"""
Patch 9 "Creatures": draws the stand-in art for the new creatures, blocks and items (our own, Pillow-drawn, nothing copied).

One source for the shapes and the paint: each creature is a list of boxes (name, parent, pivot, box, anim, style). This script
packs each box's unfolded faces into a texture sheet, paints them in the creature's styles, and writes the shapes as
assets/jaymoskingdoms/creature_models/<name>.json, which the client builds its model from (CreatureModel.java). Re-run it after
changing a shape or a colour:  python3 tools/gen_creatures.py
"""
import json
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
MODELS = os.path.join(ROOT, "creature_models")
TEX = os.path.join(ROOT, "textures", "entity", "creatures")
ITEMS = os.path.join(ROOT, "textures", "item")
BLOCKS = os.path.join(ROOT, "textures", "block")
for d in (MODELS, TEX, ITEMS, BLOCKS):
    os.makedirs(d, exist_ok=True)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def P(name, pivot, box, anim="", style="skin", parent=None, eyes=None, rot=None, mirror=False):
    return dict(name=name, parent=parent, pivot=pivot, box=box, anim=anim, style=style, eyes=eyes, rot=rot)


# ---- the creatures ---------------------------------------------------------------------------------------------------------
# Ground is y=24; boxes are [x, y, z, width, height, depth] from the pivot; a child's pivot is from its parent's pivot.
CREATURES = {}

CREATURES["troll"] = dict(scale=1.2, translucent=False, palette=("#5f7448", "#4a5e38"), parts=[
    P("body", [0, 12, 0], [-7, -16, -4, 14, 16, 8], style="troll"),
    P("head", [0, -16, 0], [-5, -9, -5, 10, 9, 9], "head", "troll", "body", eyes=dict(c="#f2d34a", at=[2, 3], s=[2, 1])),
    P("tuskL", [-3, -3, -5], [-1, -2, -1, 2, 4, 2], "", "bone", "head"),
    P("tuskR", [3, -3, -5], [-1, -2, -1, 2, 4, 2], "", "bone", "head"),
    P("armR", [-9, -14, 0], [-3, 0, -3, 5, 17, 5], "armR", "troll", "body"),
    P("armL", [9, -14, 0], [-2, 0, -3, 5, 17, 5], "armL", "troll", "body"),
    P("legR", [-3.5, 12, 0], [-3, 0, -3, 6, 12, 6], "legR", "troll"),
    P("legL", [3.5, 12, 0], [-3, 0, -3, 6, 12, 6], "legL", "troll"),
])
CREATURES["troll_limb"] = dict(scale=0.8, translucent=False, palette=("#5f7448", "#4a5e38"), parts=[
    P("arm", [0, 16, 0], [-3, -16, -3, 5, 17, 5], "crawl", "troll"),
    P("hand", [0, -16, 0], [-3, -3, -3, 7, 4, 5], "", "troll", "arm"),
])
CREATURES["rust_beast"] = dict(scale=1.0, translucent=False, palette=("#a3532a", "#6e3a1e"), parts=[
    P("body", [0, 14, 0], [-6, -7, -10, 12, 10, 20], style="rust"),
    P("head", [0, 10, -10], [-4, -4, -8, 8, 7, 8], "head", "rust", eyes=dict(c="#ffd070", at=[2, 2], s=[1, 1])),
    P("feelerL", [2, -2, -8], [-1, -1, -10, 2, 2, 10], "feeler", "feeler", "head"),
    P("feelerR", [-2, -2, -8], [-1, -1, -10, 2, 2, 10], "feeler", "feeler", "head"),
    P("tail", [0, 0, 10], [-2, -2, 0, 4, 3, 8], "tail", "rust", "body"),
    P("legFR", [-4.5, 17, -6], [-2, 0, -2, 4, 7, 4], "legR", "rust"),
    P("legFL", [4.5, 17, -6], [-2, 0, -2, 4, 7, 4], "legL", "rust"),
    P("legBR", [-4.5, 17, 6], [-2, 0, -2, 4, 7, 4], "legL", "rust"),
    P("legBL", [4.5, 17, 6], [-2, 0, -2, 4, 7, 4], "legR", "rust"),
])
CREATURES["basilisk"] = dict(scale=0.95, translucent=False, palette=("#5a6b34", "#3d4a22"), parts=[
    P("body", [0, 16, 0], [-5, -4, -12, 10, 8, 24], style="scale"),
    P("head", [0, -1, -12], [-4, -3, -9, 8, 7, 9], "head", "scale", "body", eyes=dict(c="#ffe040", at=[2, 1], s=[2, 2])),
    P("crest1", [0, -3, -2], [-1, -3, -1, 2, 3, 2], "", "bone", "head"),
    P("crest2", [0, -3, -6], [-1, -3, -1, 2, 3, 2], "", "bone", "head"),
    P("tail1", [0, 0, 12], [-3.5, -3, 0, 7, 6, 10], "tail", "scale", "body"),
    P("tail2", [0, 0, 10], [-2.5, -2, 0, 5, 4, 10], "tail", "scale", "tail1"),
    P("tail3", [0, 0, 10], [-1.5, -1.5, 0, 3, 3, 9], "tail", "scale", "tail2"),
    P("legFR", [-6, 18, -8], [-2, 0, -2, 4, 6, 4], "legR", "scale"),
    P("legFL", [6, 18, -8], [-2, 0, -2, 4, 6, 4], "legL", "scale"),
    P("legBR", [-6, 18, 8], [-2, 0, -2, 4, 6, 4], "legL", "scale"),
    P("legBL", [6, 18, 8], [-2, 0, -2, 4, 6, 4], "legR", "scale"),
])
CREATURES["clear_cube"] = dict(scale=1.0, translucent=True, palette=("#9fd8c4", "#6ab59a"), parts=[
    P("body", [0, 24, 0], [-15, -30, -15, 30, 30, 30], "wobble", "glass"),
    P("nucleus", [0, 24, 0], [-3, -17, -3, 6, 6, 6], "", "nucleus"),
])
CREATURES["piercer"] = dict(scale=1.0, translucent=False, palette=("#7b766b", "#55514a"), parts=[
    P("top", [0, 24, 0], [-4, -16, -4, 8, 5, 8], "", "rock"),
    P("mid", [0, 24, 0], [-3, -11, -3, 6, 5, 6], "", "rock"),
    P("tip", [0, 24, 0], [-1, -6, -1, 2, 6, 2], "", "rock"),
])
CREATURES["cloak_flier"] = dict(scale=1.0, translucent=False, palette=("#25222b", "#15131a"), parts=[
    P("body", [0, 20, 0], [-4, -1, -7, 8, 2, 14], "", "cloth", eyes=dict(c="#ff6a3a", at=[2, 0], s=[1, 1])),
    P("wingL", [4, 20, 0], [0, -1, -7, 14, 1, 14], "wingL", "cloth"),
    P("wingR", [-4, 20, 0], [-14, -1, -7, 14, 1, 14], "wingR", "cloth"),
    P("tail", [0, 20, 7], [-2, -1, 0, 4, 1, 9], "tail", "cloth"),
])
CREATURES["harpy"] = dict(scale=1.0, translucent=False, palette=("#d2a98a", "#8a5f3f"), parts=[
    P("body", [0, 18, 0], [-4, -12, -2, 8, 12, 5], style="feather"),
    P("head", [0, -12, 0], [-3, -7, -3, 6, 7, 6], "head", "skin", "body", eyes=dict(c="#2a7a2a", at=[1, 3], s=[1, 1])),
    P("hair", [0, -7, 1], [-4, 0, 0, 8, 10, 3], "", "hair", "head"),
    P("wingL", [4, -10, 1], [0, 0, -3, 16, 2, 6], "wingL", "feather", "body"),
    P("wingR", [-4, -10, 1], [-16, 0, -3, 16, 2, 6], "wingR", "feather", "body"),
    P("tail", [0, -1, 2], [-3, 0, 0, 6, 1, 9], "", "feather", "body"),
    P("legR", [-2.5, 18, 0], [-1, 0, -1, 2, 6, 2], "legR", "talon"),
    P("legL", [2.5, 18, 0], [-1, 0, -1, 2, 6, 2], "legL", "talon"),
])
CREATURES["digger"] = dict(scale=0.77, translucent=False, palette=("#9a5a34", "#6b3a22"), parts=[
    P("body", [0, 16, 0], [-3, -8, -2, 6, 8, 4], style="kobold"),
    P("head", [0, -8, 0], [-3, -6, -4, 6, 6, 7], "head", "kobold", "body", eyes=dict(c="#ffe040", at=[1, 2], s=[1, 1])),
    P("snout", [0, -2, -4], [-1, -1, -3, 2, 2, 3], "", "kobold", "head"),
    P("hornL", [2, -6, -1], [-1, -3, -1, 1, 3, 1], "", "bone", "head"),
    P("hornR", [-2, -6, -1], [0, -3, -1, 1, 3, 1], "", "bone", "head"),
    P("armR", [-4, -7, 0], [-1, 0, -1, 2, 7, 2], "armR", "kobold", "body"),
    P("armL", [4, -7, 0], [-1, 0, -1, 2, 7, 2], "armL", "kobold", "body"),
    P("tail", [0, -1, 2], [-1, -1, 0, 2, 2, 9], "tail", "kobold", "body"),
    P("legR", [-1.5, 16, 0], [-1, 0, -1, 3, 8, 3], "legR", "kobold"),
    P("legL", [1.5, 16, 0], [-1, 0, -1, 3, 8, 3], "legL", "kobold"),
])
CREATURES["shadow"] = dict(scale=1.0, translucent=True, palette=("#101018", "#06060c"), parts=[
    P("body", [0, 13, 0], [-3, -12, -2, 7, 12, 3], style="shade"),
    P("head", [0, -12, 0], [-3, -7, -3, 6, 7, 6], "head", "shade", "body", eyes=dict(c="#e8f0ff", at=[1, 3], s=[1, 1])),
    P("armR", [-4, -11, 0], [-1, 0, -1, 2, 15, 2], "armR", "shade", "body"),
    P("armL", [5, -11, 0], [-1, 0, -1, 2, 15, 2], "armL", "shade", "body"),
    P("legR", [-1.5, 13, 0], [-1, 0, -1, 3, 11, 3], "legR", "shade"),
    P("legL", [2.5, 13, 0], [-1, 0, -1, 3, 11, 3], "legL", "shade"),
])
CREATURES["shapeshifter"] = dict(scale=0.98, translucent=False, palette=("#8d8f88", "#5e605a"), parts=[
    P("body", [0, 12, 0], [-4, -12, -2, 8, 12, 4], style="grey"),
    P("head", [0, -12, 0], [-4, -8, -4, 8, 8, 8], "head", "grey", "body", eyes=dict(c="#0a0a0a", at=[2, 3], s=[2, 2])),
    P("armR", [-6, -10, 0], [-2, 0, -2, 4, 15, 4], "armR", "grey", "body"),
    P("armL", [6, -10, 0], [-2, 0, -2, 4, 15, 4], "armL", "grey", "body"),
    P("legR", [-2, 12, 0], [-2, 0, -2, 4, 12, 4], "legR", "grey"),
    P("legL", [2, 12, 0], [-2, 0, -2, 4, 12, 4], "legL", "grey"),
])
CREATURES["bog_light"] = dict(scale=1.0, translucent=False, palette=("#d8f08a", "#9ac04a"), parts=[
    P("core", [0, 20, 0], [-2, -2, -2, 4, 4, 4], "wobble", "glow"),
])


# ---- painting ---------------------------------------------------------------------------------------------------------------

def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return tuple(clamp(x * f) for x in c)


def jitter(c, rng, amount=14):
    d = rng.randint(-amount, amount)
    return tuple(clamp(x + d) for x in c)


def pixel(style, face, fx, fy, w, h, pal, rng):
    """One pixel of a face: fx, fy are 0..1 across the face. Returns (r, g, b, a)."""
    base = hexc(pal[0])
    dark = hexc(pal[1])
    top = face == "top"
    bottom = face == "bottom"
    f = 1.12 if top else 0.72 if bottom else 1.0 - 0.18 * fy
    if style == "troll":
        c = jitter(base, rng, 10)
        if rng.random() < 0.13:
            c = jitter((88, 118, 60), rng, 12)  # moss
        if rng.random() < 0.06:
            c = shade(dark, 0.8)  # warts
        return shade(c, f) + (255,)
    if style == "rust":
        c = jitter(base, rng, 14)
        if rng.random() < 0.2:
            c = jitter(dark, rng, 10)
        if rng.random() < 0.05:
            c = (220, 150, 70)
        return shade(c, f) + (255,)
    if style == "feeler":
        return shade(jitter((226, 216, 190), rng, 8), f) + (255,)
    if style == "scale":
        cell = (int(fx * w) // 2 + int(fy * h) // 2) % 2
        c = jitter(base if cell else shade(base, 0.82), rng, 8)
        if face == "bottom" or fy > 0.8 and face not in ("top",):
            c = jitter((156, 158, 100), rng, 8)
        return shade(c, f) + (255,)
    if style == "kobold":
        c = jitter(base, rng, 10)
        if (int(fx * w) + int(fy * h)) % 3 == 0:
            c = shade(c, 0.88)
        return shade(c, f) + (255,)
    if style == "rock":
        c = jitter(hexc(pal[0]), rng, 10)
        if int(fy * h) % 3 == 0:
            c = shade(c, 0.82)
        if rng.random() < 0.05:
            c = shade(c, 0.6)
        return shade(c, f) + (255,)
    if style == "cloth":
        c = jitter(base, rng, 6)
        if int(fx * w) % 4 == 0:
            c = shade(c, 1.35)
        edge = fx < 0.08 or fx > 0.92 or fy < 0.08 or fy > 0.92
        if edge and rng.random() < 0.45:
            return (0, 0, 0, 0)
        return shade(c, f) + (255,)
    if style == "glass":
        edge = fx < 0.06 or fx > 0.94 or fy < 0.06 or fy > 0.94
        c = jitter(base, rng, 6)
        if edge:
            return shade(c, 1.15) + (150,)
        return c + (64,)
    if style == "nucleus":
        return jitter((72, 130, 70), rng, 10) + (220,)
    if style == "shade":
        c = jitter((16, 16, 26), rng, 6)
        edge = fx < 0.12 or fx > 0.88
        if edge:
            c = (40, 52, 90)
        return c + (205,)
    if style == "grey":
        c = jitter(base, rng, 9)
        if rng.random() < 0.08:
            c = shade(c, 0.7)
        return shade(c, f) + (255,)
    if style == "feather":
        c = jitter(hexc("#8a5f3f"), rng, 10)
        if int(fy * h) % 3 == 0:
            c = shade(c, 0.8)
        if rng.random() < 0.07:
            c = (230, 220, 200)
        return shade(c, f) + (255,)
    if style == "skin":
        return shade(jitter(hexc("#d9b08e"), rng, 8), f) + (255,)
    if style == "hair":
        c = jitter(hexc("#243a30"), rng, 8)
        if int(fx * w) % 2 == 0:
            c = shade(c, 1.4)
        return shade(c, f) + (255,)
    if style == "talon":
        return shade(jitter((196, 160, 70), rng, 10), f) + (255,)
    if style == "bone":
        return shade(jitter((226, 220, 196), rng, 8), f) + (255,)
    if style == "glow":
        r = ((fx - 0.5) ** 2 + (fy - 0.5) ** 2) ** 0.5
        return (255, 255, clamp(210 - r * 120), 255) if r < 0.3 else (190, 240, 120, 255)
    return jitter(base, rng, 10) + (255,)


def pack(parts):
    """Shelf-packs each box's unfolded faces; returns (width, height) and sets part['uv']."""
    for size in (64, 128, 256):
        x = y = rowh = 0
        ok = True
        for p in sorted(parts, key=lambda p: -(p["box"][5] + p["box"][4])):
            w, h, d = (int(abs(v)) for v in p["box"][3:6])
            fw, fh = 2 * (w + d), d + h
            if x + fw > size:
                x, y, rowh = 0, y + rowh, 0
            if y + fh > size:
                ok = False
                break
            p["uv"] = [x, y]
            x += fw
            rowh = max(rowh, fh)
        if ok:
            return size
    raise SystemExit("does not fit")


def paint_part(img, p, pal, rng):
    w, h, d = (int(abs(v)) for v in p["box"][3:6])
    u, v = p["uv"]
    style = p["style"]
    faces = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }
    for face, (fx0, fy0, fw, fh) in faces.items():
        for iy in range(fh):
            for ix in range(fw):
                img.putpixel((fx0 + ix, fy0 + iy), pixel(style, face, (ix + 0.5) / fw, (iy + 0.5) / fh, fw, fh, pal, rng))
    eyes = p.get("eyes")
    if eyes:
        fx0, fy0, fw, fh = faces["front"]
        ex, ey = eyes["at"]
        ew, eh = eyes["s"]
        cx = fw // 2
        for side in (-1, 1):
            for iy in range(eh):
                for ix in range(ew):
                    x = cx + (ex if side > 0 else -ex - ew + 1) + ix - (0 if side > 0 else 0)
                    px, py = fx0 + max(0, min(fw - 1, x)), fy0 + max(0, min(fh - 1, ey + iy))
                    img.putpixel((px, py), hexc(eyes["c"]) + (255,))


def build(name, spec):
    rng = random.Random(name)
    parts = spec["parts"]
    size = pack(parts)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for p in parts:
        paint_part(img, p, spec["palette"], rng)
    img.save(os.path.join(TEX, name + ".png"))
    out = dict(width=size, height=size, scale=spec["scale"], translucent=spec["translucent"], parts=[
        dict(name=p["name"], parent=p["parent"], pivot=p["pivot"], box=p["box"], uv=p["uv"], anim=p["anim"], rot=p["rot"]) for p in parts])
    with open(os.path.join(MODELS, name + ".json"), "w") as f:
        json.dump(out, f, indent=1)


# ---- icons and blocks ------------------------------------------------------------------------------------------------------------

def egg(name, a, b):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((3, 1, 12, 14), fill=hexc(a) + (255,), outline=shade(hexc(a), 0.6) + (255,))
    rng = random.Random(name)
    for _ in range(9):
        x, y = rng.randint(4, 11), rng.randint(3, 12)
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), hexc(b) + (255,))
    img.save(os.path.join(ITEMS, name + "_spawn_egg.png"))


def mirror_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((7, 9, 8, 14), fill=(120, 84, 48, 255))
    d.ellipse((3, 1, 12, 10), fill=(196, 150, 70, 255))
    d.ellipse((4, 2, 11, 9), fill=(196, 226, 238, 255))
    d.line((5, 7, 8, 3), fill=(255, 255, 255, 255))
    img.save(os.path.join(ITEMS, "hand_mirror.png"))


def wax_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((4, 5, 11, 12), fill=(214, 170, 80, 255), outline=(150, 110, 40, 255))
    d.ellipse((5, 6, 8, 8), fill=(244, 214, 130, 255))
    img.save(os.path.join(ITEMS, "ear_wax.png"))


def quicksand():
    rng = random.Random("quicksand")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            c = jitter((178, 156, 104), rng, 9)
            if (x + y * 2) % 7 < 2:
                c = shade(c, 0.88)
            img.putpixel((x, y), c + (255,))
    for _ in range(5):
        x, y = rng.randint(1, 13), rng.randint(1, 13)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            img.putpixel((x + dx, y + dy), (120, 100, 64, 255))
    img.save(os.path.join(BLOCKS, "quicksand.png"))


def trap_cover():
    rng = random.Random("trap_cover")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for y in range(16):
        for x in range(16):
            c = jitter((98, 140, 62), rng, 14)
            if rng.random() < 0.12:
                c = jitter((112, 84, 48), rng, 10)
            img.putpixel((x, y), c + (255,))
    d = ImageDraw.Draw(img)
    for _ in range(5):
        x, y = rng.randint(0, 10), rng.randint(0, 14)
        d.line((x, y, x + rng.randint(3, 5), y + rng.randint(-1, 1)), fill=(104, 78, 44, 255))
    img.save(os.path.join(BLOCKS, "trap_cover.png"))


def blood_bat():
    """The blood bat: our vampire bat's sheet (same hand) warmed toward a dry brown-red."""
    src = os.path.join(ROOT, "textures", "entity", "nightfall", "vampire_bat.png")
    img = Image.open(src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            px[x, y] = (clamp(r * 1.25 + 24), clamp(g * 0.8), clamp(b * 0.75), a)
    img.save(os.path.join(TEX, "blood_bat.png"))


def main():
    blood_bat()
    for name, spec in CREATURES.items():
        build(name, spec)
    eggs = dict(troll=("#5f7448", "#d8c040"), rust_beast=("#a3532a", "#f0b070"), basilisk=("#5a6b34", "#ffe040"),
                clear_cube=("#9fd8c4", "#ffffff"), piercer=("#7b766b", "#cfc9bb"), cloak_flier=("#25222b", "#ff6a3a"), blood_bat=("#6a1010", "#e03030"),
                bog_light=("#d8f08a", "#ffffff"), harpy=("#d2a98a", "#243a30"), digger=("#9a5a34", "#ffe040"), shadow=("#101018", "#e8f0ff"),
                shapeshifter=("#8d8f88", "#0a0a0a"))
    for name, (a, b) in eggs.items():
        egg(name, a, b)
    mirror_icon()
    wax_icon()
    quicksand()
    trap_cover()
    print("wrote", len(CREATURES), "creature sheets")


if __name__ == "__main__":
    main()
