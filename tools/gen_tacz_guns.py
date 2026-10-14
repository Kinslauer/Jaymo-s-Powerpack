"""Jaymo's flintlocks as a TaCZ gunpack: a musket, a blunderbuss and a flintlock pistol, all our own work (models,
texture, sounds, animations), so they get TaCZ's first-person arms, aiming, recoil and reloads without using a single
TaCZ asset (its art is CC BY-NC-ND: no changes, no copies). Only TaCZ's built-in default walk/run/draw animations
are used, by name, through "use_default_animation", the way every gunpack does.

Writes src/main/resources/assets/jaymospowers/custom/jaymos_flintlocks/ (a gunpack: assets/ + data/ +
gunpack.meta.json). Jaymo's Powers hands it to TaCZ when TaCZ is installed (armory/TaczGuns), and TaCZ copies it into
.minecraft/tacz/ like its own default pack.

- geo_models/gun/<gun>_geo.json: Bedrock models built from boxes below; units are pixels, the muzzle points at -z.
- textures/gun/uv/flintlocks.png: one 64x64 atlas of material swatches (walnut, steel, brass, flint) shared by all.
- textures/gun/slot|hud/<gun>.png: inventory icon and HUD picture, drawn from the same boxes (side view).
- animations/<gun>.animation.json: idle grip, the shot (hammer falls, frizzen flies open), the muzzle-loading reload
  (muzzle up, powder down the barrel, ramrod drawn, rammed twice, returned, hammer cocked) and an inspect.
- tacz_sounds/flintlock/*.ogg: synthesised (pan flash + boom, ramrod scrape, clicks); needs ffmpeg.
- data: gun stats, two ammo types (musket balls, shot) and Gun Smith Table / Ammo Workbench recipes.
- void_flintlock: Arsenal's Void Armory pistol in the void palette, no recipe, one per hand; it never reloads: Q drops
  the pair and Jaymo's Powers puts a fresh pair in your hands, which come up out of the rift (its draw animation).

- (0.10.85) TaCZ's default gunpack (all modern guns) is never loaded (armory/TaczGuns), so this pack also brings what
  every gun used to borrow from it, all our own: the Lua animation state machine (scripts/flintlock_state_machine.lua),
  third-person poses for Player Animation Library (player_animator/), a muzzle flash, hit/kill/dry-fire sounds and the
  Gunsmith's Bench (TaCZ's gun smith table block, given our model, tabs and recipe filter by data/tacz/index/blocks).

Hand poses are first guesses (arms are placed by the animations); tune the HANDS_* numbers after looking in game.

Run from the project root:  python -I tools/gen_tacz_guns.py
"""
import json
import math
import os
import random
import struct
import subprocess
import tempfile
import wave

from PIL import Image, ImageDraw

NS = "jaymospowers"
PACK = f"src/main/resources/assets/{NS}/custom/jaymos_flintlocks"
A = f"{PACK}/assets/{NS}"
D = f"{PACK}/data/{NS}"

# ---- the atlas ------------------------------------------------------------------------------------------------------
# material -> top-left of its 16x16 swatch
SWATCH = {
    "wood": (0, 0), "wood_dark": (16, 0), "steel": (32, 0), "steel_dark": (48, 0),
    "brass": (0, 16), "flint": (16, 16), "black": (32, 16), "skin": (48, 16),
}
BASE = {
    "wood": (112, 64, 34), "wood_dark": (70, 40, 22), "steel": (104, 106, 112), "steel_dark": (54, 56, 62),
    "brass": (190, 146, 62), "flint": (66, 64, 68), "black": (22, 22, 24), "skin": (200, 160, 130),
}


# the Void Armory's flintlock (Arsenal): ebony, violet gunmetal, amethyst fittings, a flint lit with void fire
VOID = {
    "wood": (42, 22, 52), "wood_dark": (82, 44, 98), "steel": (58, 50, 82), "steel_dark": (34, 30, 44),
    "brass": (176, 120, 230), "flint": (236, 110, 255), "black": (20, 10, 26), "skin": (200, 160, 130),
}


def atlas(base=BASE):
    rnd = random.Random(7)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for mat, (ox, oy) in SWATCH.items():
        r, g, b = base[mat]
        for y in range(16):
            for x in range(16):
                n = rnd.randint(-6, 6)
                if mat.startswith("wood"):
                    # long grain along u (the gun's length), wavy
                    grain = math.sin(y * 1.7 + math.sin(x * 0.45) * 1.3)
                    n += int(grain * 9)
                elif mat in ("steel", "brass"):
                    n += 8 if y in (2, 3) else (-6 if y > 12 else 0)
                img.putpixel((ox + x, oy + y), (clamp(r + n), clamp(g + n), clamp(b + n), 255))
    return img


def clamp(v):
    return max(0, min(255, v))


# ---- models ---------------------------------------------------------------------------------------------------------


class Model:
    def __init__(self, ident):
        self.ident = ident
        self.bones = {}
        self.order = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones[name] = b
        self.order.append(name)
        return b

    def box(self, bone, x0, y0, z0, x1, y1, z1, mat, rot=None, pivot=None):
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        u, v = SWATCH[mat]

        def face(w, h):
            return {"uv": [u + 0.05, v + 0.05], "uv_size": [round(min(w, 15.9), 3), round(min(h, 15.9), 3)]}

        cube = {"origin": [r(x0), r(y0), r(z0)], "size": [r(sx), r(sy), r(sz)], "uv": {
            "north": face(sx, sy), "south": face(sx, sy), "east": face(sz, sy), "west": face(sz, sy),
            "up": face(sx, sz), "down": face(sx, sz)}}
        if rot:
            cube["rotation"] = list(rot)
            cube["pivot"] = list(pivot)
        self.bones[bone].setdefault("cubes", []).append(cube)
        return cube

    def json(self):
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.ident}", "texture_width": 64, "texture_height": 64,
                            "visible_bounds_width": 5, "visible_bounds_height": 10, "visible_bounds_offset": [0, -3, 0]},
            "bones": [self.bones[n] for n in self.order]}]}


def r(v):
    return round(v, 4)


def frame(m, long_gun, muzzle_z, bore_y):
    """The bones TaCZ looks for: root, the gun, hands, camera and views, third-person/ground/frame positions."""
    if long_gun:
        m.bone("root", pivot=(0, 8, 5))
        hand_z = 0
    else:
        m.bone("root", pivot=(0, 7, 6.2))
        hand_z = 8
    m.bone("gun", "root")
    m.bone("constraint", "gun", (0, bore_y + 1, -4))
    for part in ("wood", "steel", "brass"):
        m.bone(part, "gun")
    m.bone("positioning2", "gun")
    m.bone("muzzle_pos", "positioning2", (0, bore_y, muzzle_z))
    m.bone("muzzle_flash", "positioning2", (0, bore_y, muzzle_z))
    m.bone("scope_pos", "positioning2")
    # right hand rides with the gun; the left one is free to go to the muzzle
    m.bone("righthand", "gun", (6, 19, hand_z))
    m.bone("righthand_pos", "righthand", (0, 8, hand_z))
    m.box("righthand_pos", 4, 8, hand_z - 2, 7, 20, hand_z + 2, "skin")
    m.bone("lefthand", "root", (-6, 19, hand_z))
    m.bone("lefthand_pos", "lefthand", (0, 8, hand_z))
    m.box("lefthand_pos", -7, 8, hand_z - 2, -4, 20, hand_z + 2, "skin")
    if long_gun:
        m.bone("camera", pivot=(2.15, 12.74, 12.28))
        m.bone("view")
        m.bone("idle_view", "view", (2.75, 12.5, 12.28))
        m.bone("iron_view", "view", (0, bore_y + 1.6, 15))
        m.bone("refit_view", "view", (32, 9, -8))
        m.bone("positioning", pivot=(0, 0, -8))
        m.bone("thirdperson_hand", "positioning", (0, 7.5, 6.45))
        m.bone("ground", "positioning", (0, -2, -8))
        m.bone("fixed", "positioning", (0.9, 9.3, -5.8), (0, 90, 0))
    else:
        m.bone("camera", pivot=(3.38, 12.16, 11.5))
        m.bone("view")
        m.bone("idle_view", "view", (3.38, 12.16, 11.5))
        m.bone("iron_view", "view", (0, bore_y + 1.2, 8))
        m.bone("refit_view", "view", (20, 9, -2))
        m.bone("positioning", pivot=(0, 0, -8))
        m.bone("thirdperson_hand", "positioning", (0, 6.5, 7.65))
        m.bone("ground", "positioning", (0, -0.9, 0.65))
        m.bone("fixed", "positioning", (0, 9.1, 1.6), (0, 90, 0))


def lock(m, x, y, z, s):
    """The flintlock itself on the right side: plate, pan, frizzen (hinged, its own bone) and the cock with its flint."""
    m.box("steel", x, y - 1.4 * s, z - 1.6 * s, x + 0.2, y, z + 4.6 * s, "steel_dark")
    m.box("steel", x, y, z - 0.6 * s, x + 1.0 * s, y + 0.35 * s, z + 0.8 * s, "steel")
    m.bone("frizzen", "gun", (x + 0.5 * s, y + 0.35 * s, z - 0.6 * s))
    m.box("frizzen", x + 0.15, y + 0.35 * s, z - 0.6 * s, x + 0.85 * s, y + 2.1 * s, z - 0.2 * s, "steel")
    m.box("frizzen", x + 0.15, y + 0.35 * s, z - 0.6 * s, x + 0.85 * s, y + 0.55 * s, z + 0.8 * s, "steel")
    m.bone("hammer", "gun", (x + 0.5 * s, y, z + 2.6 * s))
    m.box("hammer", x + 0.2, y, z + 2.3 * s, x + 0.8 * s, y + 1.8 * s, z + 2.9 * s, "steel_dark")
    m.box("hammer", x + 0.2, y + 1.8 * s, z + 1.6 * s, x + 0.8 * s, y + 2.3 * s, z + 2.9 * s, "steel_dark")
    m.box("hammer", x + 0.25, y + 1.35 * s, z + 1.1 * s, x + 0.75 * s, y + 1.8 * s, z + 1.7 * s, "flint")


def long_gun(ident, barrel_end, bell, brass_barrel):
    """Musket (bell None) or blunderbuss: walnut full stock, barrel bands, ramrod in pipes under the barrel."""
    m = Model(ident)
    bore = 10.5
    muzzle = barrel_end - (4 if bell else 0.5)
    frame(m, True, muzzle - 0.5, bore)
    barrel_mat = "brass" if brass_barrel else "steel"
    # barrel, breech and tang
    m.box("steel", -0.8, bore - 0.8, barrel_end, 0.8, bore + 0.8, 1.5, barrel_mat)
    m.box("steel", -0.6, bore - 0.5, 1.5, 0.6, bore + 0.5, 3.5, "steel_dark")
    if bell:
        m.box("brass", -1.3, bore - 1.3, barrel_end - 3, 1.3, bore + 1.3, barrel_end, "brass")
        m.box("brass", -1.8, bore - 1.8, barrel_end - 4, 1.8, bore + 1.8, barrel_end - 3, "brass")
        m.box("steel", -1.2, bore - 1.2, barrel_end - 4.05, 1.2, bore + 1.2, barrel_end - 3.9, "black")
    else:
        m.box("steel", -0.9, bore - 0.9, barrel_end - 0.5, 0.9, bore + 0.9, barrel_end + 0.5, "steel_dark")
        m.box("steel", -0.4, bore - 0.4, barrel_end - 0.55, 0.4, bore + 0.4, barrel_end - 0.45, "black")
    # sights: brass blade at the muzzle, notched steel block at the breech
    m.box("brass", -0.15, bore + 0.8, barrel_end + 1.5, 0.15, bore + 1.5, barrel_end + 2.2, "brass")
    m.box("steel", -0.6, bore + 0.8, 0.0, -0.15, bore + 1.5, 0.6, "steel_dark")
    m.box("steel", 0.15, bore + 0.8, 0.0, 0.6, bore + 1.5, 0.6, "steel_dark")
    # stock: forestock, lock bed, wrist, butt, brass furniture
    fore_end = barrel_end + (10 if bell else 8)
    m.box("wood", -1.3, 8.2, fore_end, 1.3, bore - 0.2, -2, "wood")
    m.box("brass", -1.35, 8.1, fore_end - 1, 1.35, bore - 0.1, fore_end, "brass")
    m.box("wood", -1.4, 7.6, -2, 1.4, bore - 0.1, 6, "wood")
    m.box("wood", -1.1, 7.0, 6, 1.1, 9.6, 10, "wood")
    m.box("wood", -1.3, 5.0, 10, 1.3, 9.8, 13, "wood")
    m.box("wood", -1.5, 3.0, 13, 1.5, 9.9, 17.5, "wood")
    m.box("wood", -1.52, 6.0, 13.2, 1.52, 8.6, 16.5, "wood_dark")  # cheek panel
    m.box("brass", -1.55, 2.9, 17.5, 1.55, 10.0, 18.0, "brass")
    span = -2 - fore_end
    for f in (0.25, 0.55, 0.85):
        z = -2 - span * f
        m.box("brass", -1.4, 8.1, z - 0.5, 1.4, bore + 1.0, z + 0.5, "brass")
    # trigger guard and trigger
    m.box("brass", -0.4, 6.4, 2.0, 0.4, 6.8, 6.5, "brass")
    m.box("brass", -0.4, 6.4, 1.8, 0.4, 7.6, 2.2, "brass")
    m.box("brass", -0.4, 6.4, 6.3, 0.4, 7.4, 6.7, "brass")
    m.box("steel", -0.25, 6.9, 3.8, 0.25, 7.8, 4.2, "steel_dark")
    # ramrod pipes on the gun, the rod its own bone
    for f in (0.2, 0.5, 0.8):
        z = -2 - span * f
        m.box("brass", -0.5, 7.4, z - 0.6, 0.5, 8.4, z + 0.6, "brass")
    m.bone("ramrod", "gun", (0, 7.9, -6))
    m.box("ramrod", -0.25, 7.65, barrel_end + 1, 0.25, 8.15, -6, "steel_dark")
    m.box("ramrod", -0.3, 7.6, barrel_end + 0.5, 0.3, 8.2, barrel_end + 1, "brass")
    lock(m, 1.4, bore - 0.4, 0.4, 1.0)
    return m, {"muzzle": muzzle, "rod_len": -6 - (barrel_end + 0.5), "bore": bore, "rod_y": 7.9}


def pistol(ident="flintlock_pistol"):
    m = Model(ident)
    bore = 9.1
    end = -15.0
    frame(m, False, end - 0.5, bore)
    m.box("steel", -0.6, bore - 0.6, end, 0.6, bore + 0.6, 1.0, "steel")
    m.box("brass", -0.75, bore - 0.75, end - 0.4, 0.75, bore + 0.75, end + 0.5, "brass")
    m.box("steel", -0.35, bore - 0.35, end - 0.45, 0.35, bore + 0.35, end - 0.35, "black")
    m.box("brass", -0.12, bore + 0.6, end + 1.0, 0.12, bore + 1.0, end + 1.4, "brass")
    m.box("wood", -0.9, 7.6, end + 3, 0.9, bore - 0.1, 1.0, "wood")
    m.box("brass", -0.95, 7.5, end + 2.5, 0.95, bore, end + 3, "brass")
    m.box("wood", -1.0, 6.8, 1.0, 1.0, bore + 0.1, 5.5, "wood")
    # the grip sweeps down and back, capped with a brass pommel
    m.box("wood", -0.95, 1.6, 4.4, 0.95, 7.2, 7.6, "wood", rot=(22, 0, 0), pivot=(0, 7.2, 5.0))
    m.box("brass", -1.05, 1.0, 4.2, 1.05, 1.6, 7.8, "brass", rot=(22, 0, 0), pivot=(0, 7.2, 5.0))
    m.box("brass", -0.35, 5.7, 1.8, 0.35, 6.1, 4.8, "brass")
    m.box("brass", -0.35, 5.7, 1.6, 0.35, 6.9, 2.0, "brass")
    m.box("steel", -0.22, 6.2, 3.1, 0.22, 7.0, 3.5, "steel_dark")
    m.bone("ramrod", "gun", (0, 7.4, -1))
    m.box("ramrod", -0.2, 7.2, end + 0.8, 0.2, 7.6, -1.0, "steel_dark")
    m.box("ramrod", -0.25, 7.15, end + 0.4, 0.25, 7.65, end + 0.8, "brass")
    m.box("brass", -0.4, 7.0, -8, 0.4, 7.8, -7, "brass")
    lock(m, 1.0, bore - 0.3, 1.9, 0.75)
    return m, {"muzzle": end - 0.4, "rod_len": -1.0 - (end + 0.4), "bore": bore, "rod_y": 7.4}


# ---- pictures drawn from the boxes ----------------------------------------------------------------------------------


def side_view(m, size, pad, colour=None, base=BASE):
    """Project every box onto the z/y plane (muzzle left), painted by material or in one colour, fitted to size."""
    boxes = []
    for name in m.order:
        b = m.bones[name]
        if name.startswith(("righthand", "lefthand")):
            continue
        for c in b.get("cubes", []):
            mat = next(k for k, (u, v) in SWATCH.items() if abs(c["uv"]["north"]["uv"][0] - u - 0.05) < 1e-6
                       and abs(c["uv"]["north"]["uv"][1] - v - 0.05) < 1e-6)
            boxes.append((c, mat))
    pts = []
    for c, _ in boxes:
        pts += corners(c)
    zs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    w, h = size
    sc = min((w - 2 * pad) / (max(zs) - min(zs)), (h - 2 * pad) / (max(ys) - min(ys)))
    ox = (w - (max(zs) - min(zs)) * sc) / 2
    oy = (h - (max(ys) - min(ys)) * sc) / 2
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    order = sorted(boxes, key=lambda cm: cm[0]["origin"][0] + cm[0]["size"][0])  # far side first
    for c, mat in order:
        poly = [(ox + (z - min(zs)) * sc, h - oy - (y - min(ys)) * sc) for z, y in corners(c)]
        hull = convex(poly)
        fill = colour or base[mat] + (255,)
        dr.polygon(hull, fill=fill)
    if colour is None:
        img = outlined(img)
    return img


def corners(c):
    (x0, y0, z0), (sx, sy, sz) = c["origin"], c["size"]
    pts = [(z0, y0), (z0 + sz, y0), (z0, y0 + sy), (z0 + sz, y0 + sy)]
    if "rotation" in c:
        a = math.radians(c["rotation"][0])
        py, pz = c["pivot"][1], c["pivot"][2]
        out = []
        for z, y in pts:
            dz, dy = z - pz, y - py
            # rotation about x: +x tips +y towards -z
            out.append((pz + dz * math.cos(a) - dy * math.sin(a), py + dz * math.sin(a) + dy * math.cos(a)))
        return out
    return pts


def convex(pts):
    pts = sorted(set(pts))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lo, hi = [], []
    for p in pts:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0:
            lo.pop()
        lo.append(p)
    for p in reversed(pts):
        while len(hi) >= 2 and cross(hi[-2], hi[-1], p) <= 0:
            hi.pop()
        hi.append(p)
    return lo[:-1] + hi[:-1]


def outlined(img):
    out = img.copy()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if img.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < w and 0 <= y + dy < h and img.getpixel((x + dx, y + dy))[3]:
                    out.putpixel((x, y), (24, 16, 10, 255))
                    break
    return out


def ammo_icon(kind):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    if kind == "void_shot":
        dr.ellipse((9, 9, 23, 23), fill=(112, 64, 160, 255), outline=(20, 10, 26, 255))
        dr.ellipse((12, 12, 16, 16), fill=(236, 110, 255, 255))
    elif kind == "musket_ball":
        for cx, cy in ((11, 19), (20, 20), (16, 12)):
            dr.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=(92, 94, 100, 255), outline=(30, 30, 34, 255))
            dr.ellipse((cx - 3, cy - 3, cx - 1, cy - 1), fill=(170, 172, 178, 255))
    else:
        dr.rounded_rectangle((6, 9, 26, 28), 5, fill=(124, 86, 48, 255), outline=(52, 32, 16, 255))
        dr.rectangle((12, 5, 20, 10), fill=(150, 108, 64, 255), outline=(52, 32, 16, 255))
        for cx, cy in ((11, 16), (16, 19), (21, 15), (13, 23), (19, 24)):
            dr.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=(96, 98, 104, 255))
    return img


def muzzle_flash():
    """A flintlock's flash: a ragged orange tongue of flame with a white-hot heart, fading out into grey smoke."""
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(31)
    spikes = [rnd.uniform(0.55, 1.0) for _ in range(11)]
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) / (2 * math.pi)) % 1.0
            i = a * len(spikes)
            k = int(i)
            reach = spikes[k] + (spikes[(k + 1) % len(spikes)] - spikes[k]) * (i - k)
            t = d / reach
            if t >= 1.0:
                continue
            heat = 1.0 - t
            if heat > 0.7:
                col = (255, 250, 220)
            elif heat > 0.4:
                col = (255, int(150 + 230 * (heat - 0.4)), 60)
            elif heat > 0.15:
                col = (235, int(90 + 200 * (heat - 0.15)), 30)
            else:
                col = (120, 110, 104)
            alpha = int(255 * min(1.0, heat * 2.2) * (0.55 if heat <= 0.15 else 1.0))
            px[x, y] = (*col, alpha)
    return img


def bench():
    """The Gunsmith's Bench (TaCZ's two-block gun smith table): a plank bench with a vice, a musket being stocked,
    a powder keg and tools on the shelf, and a rack of ramrods and a mallet behind."""
    m = Model("gunsmith_bench")
    m.bone("bench")
    for x in (-7.5, 21.5):
        for z in (-7.5, 5.5):
            m.box("bench", x, 0, z, x + 2, 14, z + 2, "wood_dark")
    m.box("bench", -7, 3, -7, 23, 4.5, -6, "wood_dark")
    m.box("bench", -7, 3, 6, 23, 4.5, 7, "wood_dark")
    m.box("bench", -7, 4.5, -7, 23, 5.5, 7, "wood")
    m.box("bench", -8, 14, -8, 24, 16, 8, "wood")
    # the vice on the right-hand end, jaws open on a stock blank
    m.box("bench", 17, 16, -8.5, 23, 17.5, -4, "steel_dark")
    m.box("bench", 17.5, 17.5, -8.5, 18.5, 20.5, -5, "steel")
    m.box("bench", 21.5, 17.5, -8.5, 22.5, 20.5, -5, "steel")
    m.box("bench", 18.5, 17.5, -8, 21.5, 19.5, -5.5, "wood")
    m.box("bench", 22.5, 18.5, -7.5, 25.5, 19.2, -6.8, "steel_dark")
    # a musket laid on the top, lock towards you
    m.box("bench", -6, 16.2, -1.6, 14, 17.2, -0.6, "steel")
    m.box("bench", 3, 15.9, -2.1, 16, 17.4, 0.4, "wood")
    m.box("bench", 13, 15.6, -2.1, 18, 17.8, 0.1, "wood")
    m.box("bench", 4.5, 17.2, -2.3, 6.5, 17.8, -1.6, "steel_dark")
    m.box("bench", 9, 16.1, -2.2, 9.8, 17.5, 0.5, "brass")
    # powder horn and a pouch of balls on the top, a keg on the shelf
    m.box("bench", -5.5, 16, 3, -1.5, 17.5, 4.5, "brass")
    m.box("bench", 0, 16, 2.5, 2.5, 17.8, 5, "wood_dark")
    m.box("bench", -5, 5.5, -4, 1, 12.5, 2, "wood_dark")
    for y in (6.5, 11):
        m.box("bench", -5.3, y, -4.3, 1.3, y + 0.8, 2.3, "brass")
    m.box("bench", 8, 5.5, -3, 14, 7, 3, "wood")
    # the rack behind: two uprights, a rail, ramrods and a mallet hanging on it
    m.box("bench", -8, 16, 6, -6, 30, 8, "wood_dark")
    m.box("bench", 22, 16, 6, 24, 30, 8, "wood_dark")
    m.box("bench", -6, 27, 6.5, 22, 28.5, 7.5, "wood")
    for x in (0, 2, 4):
        m.box("bench", x, 17, 6.6, x + 0.5, 27, 7.1, "steel_dark")
    m.box("bench", 12, 20, 6.4, 13, 27, 7.4, "wood")
    m.box("bench", 10.5, 18.5, 6, 14.5, 21, 8, "wood_dark")
    return m


# ---- animations -----------------------------------------------------------------------------------------------------
# Hand poses (absolute, like every TaCZ gun): rotation, position. Long guns: right hand on the wrist, left under the
# forestock. Pistol: right hand on the grip, left cupping it.
HANDS_LONG = {"righthand": ([80, 12.5, -179], [-6.6, -12.5, 6.5]), "lefthand": ([103.5, -17, -132], [8.1, -11.5, -8.5])}
HANDS_PISTOL = {"righthand": ([89, 15, -180], [-6.7, -13.3, -0.8]), "lefthand": ([107, -26.5, -158], [8.4, -13, -11.3])}
HAMMER_COCKED, HAMMER_DOWN = [-20, 0, 0], [35, 0, 0]
FRIZZEN_SHUT, FRIZZEN_OPEN = [0, 0, 0], [62, 0, 0]
SCALE = [1, 1.5, 1]


def keys(pairs):
    return {f"{t:.4g}": [r(x) for x in v] for t, v in pairs}


def add(a, b):
    return [x + y for x, y in zip(a, b)]


def idle(hands, empty):
    bones = {h: {"rotation": rot, "position": pos, "scale": SCALE} for h, (rot, pos) in hands.items()}
    bones["hammer"] = {"rotation": HAMMER_DOWN if empty else HAMMER_COCKED}
    bones["frizzen"] = {"rotation": FRIZZEN_OPEN if empty else FRIZZEN_SHUT}
    bones["constraint"] = {"rotation": [0.2, 0.2, 0.1], "position": [0.1, 0.1, 0.2]}
    return {"animation_length": 0.05, "bones": bones}


def shoot(kick):
    k = kick
    return {"animation_length": 0.65, "bones": {
        "root": {"rotation": keys([(0, [0, 0, 0]), (0.03, [-9 * k, 1.5, 2.5]), (0.12, [-6 * k, 0.6, 1]),
                                   (0.35, [-1.5 * k, 0, 0]), (0.65, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.03, [0, 0.6 * k, 2.4 * k]), (0.15, [0, 0.3, 1.0]),
                                   (0.65, [0, 0, 0])])},
        "camera": {"rotation": keys([(0, [0, 0, 0]), (0.04, [-1.2 * k, 0.3, 0.8]), (0.3, [0, 0, 0])])},
        "hammer": {"rotation": keys([(0, HAMMER_COCKED), (0.02, HAMMER_DOWN)])},
        "frizzen": {"rotation": keys([(0, FRIZZEN_SHUT), (0.03, FRIZZEN_OPEN)])},
    }, "sound_effects": {}}


def reload(hands, g, long, length):
    """Muzzle up, powder and ball down the barrel, ramrod out, two strokes, rod back, frizzen shut, cock."""
    s = length / 4.0
    T = lambda t: round(t * s, 4)  # noqa: E731 - times are written for a 4 s reload, then scaled
    tilt = [-35, 8, 20] if long else [-40, 10, 15]
    drop = [-2, -6, 2] if long else [-1, -4, 1]
    lrot, lpos = hands["lefthand"]
    rod_out = -(g["rod_len"] + 1)                   # rod slid forward until it clears the pipes
    rod_in = rod_out + min(10, g["rod_len"] * 0.3)  # its tip inside the muzzle
    rod_deep = rod_in + min(8, g["rod_len"] * 0.25)
    lift = g["bore"] - g["rod_y"]
    muzzle_hand = add(lpos, [0, 3, g["muzzle"] - lpos[2] + 4])
    grip_rod = add(lpos, [0, -0.5, 2])
    bones = {
        "root": {"rotation": keys([(0, [0, 0, 0]), (T(0.35), tilt), (T(3.45), tilt), (T(3.9), [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (T(0.35), drop), (T(3.45), drop), (T(3.9), [0, 0, 0])])},
        "camera": {"rotation": keys([(0, [0, 0, 0]), (T(0.4), [-1, 0.5, 1.5]), (T(3.5), [-1, 0.5, 1.5]),
                                     (T(3.95), [0, 0, 0])])},
        "lefthand": {
            "rotation": keys([(0, lrot), (T(0.45), add(lrot, [-25, 0, 10])), (T(1.15), add(lrot, [-25, 0, 10])),
                              (T(1.35), lrot), (T(3.3), lrot), (T(3.6), lrot)]),
            "position": keys([
                (0, lpos), (T(0.45), muzzle_hand), (T(0.7), add(muzzle_hand, [0, 0.6, 0])),
                (T(0.85), muzzle_hand), (T(1.0), add(muzzle_hand, [0, 0.6, 0])), (T(1.15), muzzle_hand),
                (T(1.35), grip_rod), (T(1.85), add(grip_rod, [0, 0, rod_out])),
                (T(2.05), add(grip_rod, [0, lift + 1.5, rod_in])),
                (T(2.3), add(grip_rod, [0, lift + 1.5, rod_deep])), (T(2.5), add(grip_rod, [0, lift + 1.5, rod_in])),
                (T(2.75), add(grip_rod, [0, lift + 1.5, rod_deep])), (T(2.95), add(grip_rod, [0, lift + 1.5, rod_in])),
                (T(3.1), add(grip_rod, [0, 0, rod_out])), (T(3.35), grip_rod), (T(3.6), lpos)]),
            "scale": SCALE},
        "ramrod": {"position": keys([
            (0, [0, 0, 0]), (T(1.35), [0, 0, 0]), (T(1.85), [0, 0, rod_out]), (T(2.05), [0, lift, rod_in]),
            (T(2.3), [0, lift, rod_deep]), (T(2.5), [0, lift, rod_in]), (T(2.75), [0, lift, rod_deep]),
            (T(2.95), [0, lift, rod_in]), (T(3.1), [0, 0, rod_out]), (T(3.35), [0, 0, 0])])},
        "frizzen": {"rotation": keys([(0, FRIZZEN_OPEN), (T(3.4), FRIZZEN_OPEN), (T(3.45), FRIZZEN_SHUT)])},
        "hammer": {"rotation": keys([(0, HAMMER_DOWN), (T(3.5), HAMMER_DOWN), (T(3.6), HAMMER_COCKED)])},
    }
    sfx = {f"{T(0.45):.4g}": "powder", f"{T(1.4):.4g}": "rod_draw", f"{T(2.3):.4g}": "ram",
           f"{T(2.75):.4g}": "ram", f"{T(3.1):.4g}": "rod_draw", f"{T(3.42):.4g}": "frizzen", f"{T(3.55):.4g}": "cock"}
    return {"animation_length": round(length, 4), "loop": "hold_on_last_frame", "bones": bones,
            "sound_effects": {t: {"effect": f"{NS}:flintlock/{n}"} for t, n in sfx.items()}}


def inspect(hands, empty):
    rrot, rpos = hands["righthand"]
    hammer = HAMMER_DOWN if empty else HAMMER_COCKED
    return {"animation_length": 3.2, "loop": "hold_on_last_frame", "bones": {
        "root": {"rotation": keys([(0, [0, 0, 0]), (0.5, [8, -25, -35]), (1.6, [6, -30, -40]), (2.0, [-10, 20, 10]),
                                   (2.7, [-8, 22, 12]), (3.2, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (0.5, [-3, 2, 3]), (1.6, [-3, 2.5, 3]), (2.0, [2, 1, 2]),
                                   (2.7, [2, 1, 2]), (3.2, [0, 0, 0])])},
        "hammer": {"rotation": keys([(0, hammer), (0.9, hammer), (1.05, add(hammer, [-8, 0, 0])), (1.2, hammer)])},
        "frizzen": {"rotation": {"0": FRIZZEN_OPEN if empty else FRIZZEN_SHUT}},
        "righthand": {"rotation": {"0": rrot}, "position": {"0": rpos}, "scale": SCALE},
    }, "sound_effects": {"0": {"effect": f"{NS}:flintlock/draw"}, "1.05": {"effect": f"{NS}:flintlock/frizzen"},
                         "2.0": {"effect": f"{NS}:flintlock/draw"}}}


def rift_draw(hands, length):
    """The Void Armory's flintlock coming into your hand: pulled up out of the rift below, rolling level."""
    rrot, rpos = hands["righthand"]
    return {"animation_length": round(length, 4), "loop": "hold_on_last_frame", "bones": {
        "root": {"rotation": keys([(0, [-15, 15, -30]), (length * 0.8, [2, 0, 3]), (length, [0, 0, 0])]),
                 "position": keys([(0, [-2, -14, 3]), (length * 0.8, [0, 0.4, 0]), (length, [0, 0, 0])])},
        "camera": {"rotation": keys([(0, [-0.4, 0.3, 0.8]), (length, [0, 0, 0])])},
        "righthand": {"rotation": {"0": rrot}, "position": {"0": rpos}, "scale": SCALE},
    }, "sound_effects": {"0": {"effect": f"{NS}:flintlock/void_pull"}}}


def animations(hands, g, long, reload_len, kick):
    anims = {"static_idle": idle(hands, False), "static_bolt_caught": idle(hands, True), "shoot": shoot(kick),
             "reload_empty": reload(hands, g, long, reload_len), "inspect": inspect(hands, False),
             "inspect_empty": inspect(hands, True)}
    return {"format_version": "1.8.0", "animations": anims}


def void_animations(hands, kick, length):
    """No reloading (Q drops the pair for a fresh one): the shot, and the draw is the pull out of the rift."""
    anims = {"static_idle": idle(hands, False), "static_bolt_caught": idle(hands, True), "shoot": shoot(kick),
             "draw": rift_draw(hands, length), "inspect": inspect(hands, False), "inspect_empty": inspect(hands, True)}
    return {"format_version": "1.8.0", "animations": anims}


# Third person (Player Animation Library): our own poses. Degrees and pixels on the player's limbs; x below 0 lifts an
# arm forward. Long guns are held across the body, the stock under the right arm and the left hand forward on the
# stock; the pistol is held out in the right hand alone.
def pa_keys(pairs):
    return {f"{t:.4g}": {"post": [r(v) for v in val], "lerp_mode": "INOUTSINE"} for t, val in pairs}


def pa_pose(arms, length=2.0, sway=1.5, loop=True):
    """A held pose that breathes: each limb's rotation rises and falls by sway degrees over length seconds."""
    bones = {}
    for limb, (rot, pos) in arms.items():
        bones[limb] = {"rotation": pa_keys([(0, rot), (length / 2, add(rot, [-sway, 0, 0])), (length, rot)]),
                       "position": pa_keys([(0, pos), (length, pos)])}
    return {"loop": loop, "animation_length": length, "bones": bones}


def pa_once(arms, frames, length):
    """A one-shot: each limb steps through (time, rotation offset) on top of its pose."""
    bones = {}
    for limb, (rot, pos) in arms.items():
        bones[limb] = {"rotation": pa_keys([(t, add(rot, d)) for t, d in frames.get(limb, [(0, [0, 0, 0])])]),
                       "position": pa_keys([(0, pos), (length, pos)])}
    return {"animation_length": length, "bones": bones}


def player_animations(long, reload_len):
    if long:
        hold = {"right_arm": ([-62, -18, 6], [0, 0, 0]), "left_arm": ([-78, 34, 0], [0, 0, -1])}
        aim = {"right_arm": ([-88, -14, 0], [0, -0.5, 1]), "left_arm": ([-95, 38, 0], [0.5, -0.5, -2.5]),
               "head": ([0, 0, 0], [0, 0, 0])}
        run = {"right_arm": ([-34, -30, 24], [0, 0, 0]), "left_arm": ([-58, 46, 0], [0, 0, 0])}
        ram = [(0, [0, 0, 0]), (reload_len * 0.1, [70, 0, -10]), (reload_len * 0.85, [70, 0, -10]), (reload_len, [0, 0, 0])]
        stroke = [(0, [0, 0, 0]), (reload_len * 0.1, [-50, -20, 0])]
        for i in range(2):
            t = reload_len * (0.45 + i * 0.15)
            stroke += [(t, [-75, -20, 0]), (t + reload_len * 0.07, [-40, -20, 0])]
        stroke += [(reload_len * 0.85, [-50, -20, 0]), (reload_len, [0, 0, 0])]
        reload_frames = {"right_arm": ram, "left_arm": stroke}
    else:
        hold = {"right_arm": ([-55, -6, 0], [0, 0, 0])}
        aim = {"right_arm": ([-90, -4, 0], [0, -0.5, 0.5]), "head": ([0, 0, 0], [0, 0, 0])}
        run = {"right_arm": ([-25, -8, 6], [0, 0, 0])}
        hold_l = {"left_arm": ([-30, 20, 0], [0, 0, 0])}
        reload_frames = {"right_arm": [(0, [0, 0, 0]), (reload_len * 0.1, [35, 0, 0]),
                                       (reload_len * 0.85, [35, 0, 0]), (reload_len, [0, 0, 0])],
                         "left_arm": [(0, [0, 0, 0]), (reload_len * 0.1, [-25, 0, 0]),
                                      (reload_len * 0.5, [-55, 0, 0]), (reload_len * 0.6, [-30, 0, 0]),
                                      (reload_len * 0.7, [-55, 0, 0]), (reload_len, [0, 0, 0])]}
        hold, run = {**hold, **hold_l}, {**run, **hold_l}
    kick = [(0, [0, 0, 0]), (0.05, [-14, 0, 0]), (0.35, [0, 0, 0])]
    anims = {
        "hold_upper": pa_pose(hold), "walk_upper": pa_pose(hold, 0.7, 3.0), "crouch_walk_upper": pa_pose(hold, 1.0, 2.0),
        "aim_upper": pa_pose(aim, 3.0, 0.6), "run_upper": pa_pose(run, 0.6, 5.0),
        "normal_fire_upper": pa_once(hold, {"right_arm": kick, "left_arm": kick}, 0.35),
        "aim_fire_upper": pa_once(aim, {"right_arm": kick, "left_arm": kick}, 0.35),
        "reload_upper": pa_once(hold, reload_frames, round(reload_len, 4)),
        "melee_upper": pa_once(hold, {"right_arm": [(0, [0, 0, 0]), (0.15, [-30, 10, 0]), (0.55, [0, 0, 0])],
                                      "left_arm": [(0, [0, 0, 0]), (0.15, [-30, 0, 0]), (0.55, [0, 0, 0])]}, 0.55),
        "empty": {"loop": True, "bones": {"body": {"rotation": [0, 0, 0]}}},
    }
    return {"format_version": "1.8.0", "animations": anims}


STATE_MACHINE = """-- Jaymo's flintlocks: our own animation state machine for TaCZ (tools/gen_tacz_guns.py writes this file).
-- A muzzle-loader has little to track: a held pose (and the empty lock once it's fired), one-shot actions (draw,
-- put away, reload, inspect, stock blow), the kick of each shot, and how you're moving.

local POSE_LINE, KICK_LINE, MOVE_LINE = 0, 1, 2
local POSE, LOCK, ACTION = 0, 1, 2
local MOVE = 0

local function action(context)
    return context:getTrack(POSE_LINE, ACTION)
end

local function spent(context)
    return (not context:hasBulletInBarrel()) and context:getAmmoCount() <= 0
end

local function play(context, name)
    context:runAnimation(name, action(context), false, PLAY_ONCE_STOP, 0.15)
end

-- the held pose, always under everything else
local pose = {}
function pose.entry(this, context)
    context:runAnimation("static_idle", context:getTrack(POSE_LINE, POSE), false, LOOP, 0)
end

-- loaded: hammer cocked, pan shut; spent: hammer down, frizzen thrown open, until it's loaded again
local lock = { loaded = {}, spent = {} }
function lock.loaded.update(this, context)
    if spent(context) then
        context:trigger("spent")
    end
end
function lock.loaded.transition(this, context, input)
    if input == "spent" then
        context:runAnimation("static_bolt_caught", context:getTrack(POSE_LINE, LOCK), false, LOOP, 0)
        return this.lock.spent
    end
end
function lock.spent.update(this, context)
    if not spent(context) then
        context:trigger("primed")
    end
end
function lock.spent.transition(this, context, input)
    if input == "primed" then
        context:stopAnimation(context:getTrack(POSE_LINE, LOCK))
        return this.lock.loaded
    end
end

-- the hands' one-shot actions
local hands = { holstered = {}, ready = {}, away = {} }
function hands.holstered.transition(this, context, input)
    if input == INPUT_DRAW then
        play(context, "draw")
        return this.hands.ready
    end
end
function hands.ready.transition(this, context, input)
    if input == INPUT_PUT_AWAY then
        context:runAnimation("put_away", action(context), false, PLAY_ONCE_HOLD, 0.15)
        return this.hands.away
    elseif input == INPUT_RELOAD then
        play(context, "reload_empty")
    elseif input == INPUT_INSPECT and context:getAimingProgress() < 1 then
        play(context, spent(context) and "inspect_empty" or "inspect")
    elseif input == INPUT_BAYONET_STOCK or input == INPUT_BAYONET_PUSH or input == INPUT_BAYONET_MUZZLE then
        play(context, "melee_stock")
    elseif input == INPUT_SHOOT and context:getAimingProgress() > 0 then
        context:stopAnimation(action(context))
    end
end

-- each shot kicks on a track of its own, so quick shots overlap
local kick = {}
function kick.transition(this, context, input)
    if input == INPUT_SHOOT then
        context:runAnimation("shoot", context:findIdleTrack(KICK_LINE, false), true, PLAY_ONCE_STOP, 0)
    end
end

-- moving: standing breath, walking (its pace follows your steps), running
local stride = { still = {}, walking = { name = "" }, running = { started = false } }
local function stepping(context, name, fade)
    local state = stride.walking
    local track = context:getTrack(MOVE_LINE, MOVE)
    if state.name ~= name then
        state.name = name
        context:runAnimation(name, track, true, LOOP, fade)
        context:anchorWalkDist()
    end
    if name ~= "idle" then
        context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
    end
end
function stride.still.update(this, context)
    local track = context:getTrack(MOVE_LINE, MOVE)
    if context:isStopped(track) or context:isHolding(track) then
        context:runAnimation("idle", track, true, LOOP, 0)
    end
end
function stride.still.transition(this, context, input)
    if input == INPUT_RUN and context:isStopped(action(context)) then
        return this.stride.running
    elseif input == INPUT_RUN or input == INPUT_WALK then
        return this.stride.walking
    end
end
function stride.walking.entry(this, context)
    stride.walking.name = ""
end
function stride.walking.update(this, context)
    if context:getShootCoolDown() > 0 or not context:isOnGround() then
        stepping(context, "idle", 0.4)
    elseif context:getAimingProgress() > 0.5 then
        stepping(context, "walk_aiming", 0.3)
    elseif context:isInputUp() then
        stepping(context, "walk_forward", 0.4)
    elseif context:isInputDown() then
        stepping(context, "walk_backward", 0.4)
    elseif context:isInputLeft() or context:isInputRight() then
        stepping(context, "walk_sideway", 0.4)
    end
end
function stride.walking.exit(this, context)
    context:runAnimation("idle", context:getTrack(MOVE_LINE, MOVE), true, PLAY_ONCE_HOLD, 0.4)
end
function stride.walking.transition(this, context, input)
    if input == INPUT_IDLE then
        return this.stride.still
    elseif input == INPUT_RUN and context:isStopped(action(context)) then
        return this.stride.running
    end
end
function stride.running.entry(this, context)
    stride.running.started = false
    context:runAnimation("run_start", context:getTrack(MOVE_LINE, MOVE), true, PLAY_ONCE_HOLD, 0.2)
end
function stride.running.update(this, context)
    local track = context:getTrack(MOVE_LINE, MOVE)
    if context:isHolding(track) and not stride.running.started then
        stride.running.started = true
        context:runAnimation("run", track, true, LOOP, 0.2)
        context:anchorWalkDist()
    end
    if stride.running.started then
        context:setAnimationProgress(track, (context:getWalkDist() % 2.0) / 2.0, true)
    end
end
function stride.running.exit(this, context)
    context:runAnimation("run_end", context:getTrack(MOVE_LINE, MOVE), true, PLAY_ONCE_HOLD, 0.3)
end
function stride.running.transition(this, context, input)
    if input == INPUT_IDLE then
        return this.stride.still
    elseif input == INPUT_WALK or not context:isStopped(action(context)) then
        return this.stride.walking
    end
end

local M = { pose = pose, lock = lock, hands = hands, kick = kick, stride = stride }

function M:initialize(context)
    context:ensureTrackLineSize(3)
    context:ensureTracksAmount(POSE_LINE, 3)
    context:ensureTracksAmount(MOVE_LINE, 1)
    stride.walking.name = ""
    stride.running.started = false
end

function M:exit(context)
end

function M:states()
    return { self.pose, self.lock.loaded, self.hands.holstered, self.kick, self.stride.still }
end

return M
"""


# ---- sounds ---------------------------------------------------------------------------------------------------------
RATE = 44100


def lowpass(xs, a):
    out, y = [], 0.0
    for x in xs:
        y += a * (x - y)
        out.append(y)
    return out


def highpass(xs, a):
    lo = lowpass(xs, a)
    return [x - l for x, l in zip(xs, lo)]


def env(n, attack, decay):
    return [min(1.0, i / max(1, attack)) * math.exp(-i / decay) for i in range(n)]


def noise(n, seed):
    rnd = random.Random(seed)
    return [rnd.uniform(-1, 1) for _ in range(n)]


def mix(*parts):
    n = max(len(p) for p in parts)
    return [sum(p[i] for p in parts if i < len(p)) for i in range(n)]


def delay(xs, secs):
    return [0.0] * int(secs * RATE) + xs


def scale(xs, k):
    return [x * k for x in xs]


def shot(distant):
    n = int(1.3 * RATE)
    pan = [x * e for x, e in zip(highpass(noise(int(0.05 * RATE), 1), 0.3), env(int(0.05 * RATE), 40, 600))]
    boom_n = noise(n, 2)
    body = lowpass(boom_n, 0.06 if distant else 0.18)
    boom = [b * e * 4 for b, e in zip(body, env(n, 30, 0.22 * RATE))]
    crack = [x * e for x, e in zip(noise(n, 3), env(n, 5, 0.025 * RATE))]
    thump = [math.sin(2 * math.pi * 55 * i / RATE) * math.exp(-i / (0.12 * RATE)) for i in range(n)]
    tail = lowpass([x * e for x, e in zip(noise(n, 4), env(n, 2000, 0.5 * RATE))], 0.03)
    parts = [scale(pan, 0.35), delay(boom, 0.035), delay(scale(thump, 0.9), 0.035), delay(scale(tail, 1.6), 0.035)]
    if not distant:
        parts.append(delay(scale(crack, 0.8), 0.035))
    return mix(*parts)


def scrape(seed):
    n = int(0.4 * RATE)
    band = highpass(lowpass(noise(n, seed), 0.35), 0.12)
    wob = [0.6 + 0.4 * math.sin(2 * math.pi * 23 * i / RATE) for i in range(n)]
    return [b * w * e * 1.6 for b, w, e in zip(band, wob, env(n, 400, 0.18 * RATE))]


def thud(seed):
    n = int(0.25 * RATE)
    knock = [x * e * 2 for x, e in zip(lowpass(noise(n, seed), 0.08), env(n, 20, 0.03 * RATE))]
    ring = [math.sin(2 * math.pi * 1900 * i / RATE) * math.exp(-i / (0.05 * RATE)) * 0.12 for i in range(n)]
    return mix(knock, ring)


def click(seed, gap=None):
    n = int(0.08 * RATE)
    c = [x * e for x, e in zip(highpass(noise(n, seed), 0.25), env(n, 3, 0.004 * RATE))]
    if gap:
        return mix(c, delay(scale(c, 0.8), gap))
    return c


def rustle(seed, secs):
    n = int(secs * RATE)
    x = highpass(lowpass(noise(n, seed), 0.5), 0.2)
    grains = [1.0 if (i // 600) % 3 else 0.3 for i in range(n)]
    return [a * g * e * 0.6 for a, g, e in zip(x, grains, env(n, 800, secs * RATE / 2.5))]


def hit(seed, head):
    """A ball striking: a dull smack, with a hard knock on top for the head."""
    n = int(0.2 * RATE)
    smack = [x * e * 2 for x, e in zip(lowpass(noise(n, seed), 0.12), env(n, 10, 0.03 * RATE))]
    if not head:
        return smack
    knock = [math.sin(2 * math.pi * 620 * i / RATE) * math.exp(-i / (0.025 * RATE)) * 0.7 for i in range(n)]
    return mix(smack, knock)


def kill_knell():
    """A low, short toll when the shot kills."""
    n = int(0.7 * RATE)
    return [sum(math.sin(2 * math.pi * f * i / RATE) * w for f, w in ((196, 0.6), (392, 0.25), (587, 0.12)))
            * min(1.0, i / 200) * math.exp(-i / (0.22 * RATE)) for i in range(n)]


def void_pull():
    """A rising rush out of the rift with a glassy chime on top."""
    n = int(0.55 * RATE)
    sweep, y = [], 0.0
    src = noise(n, 18)
    for i, x in enumerate(src):
        a = 0.02 + 0.25 * (i / n) ** 2
        y += a * (x - y)
        sweep.append(y)
    rush = [v * min(1.0, i / (0.3 * RATE)) * math.exp(-max(0, i - 0.4 * RATE) / (0.03 * RATE)) * 2.5
            for i, v in enumerate(sweep)]
    chime = [sum(math.sin(2 * math.pi * f * i / RATE) for f in (1320, 1980, 2640)) * 0.08
             * math.exp(-max(0, i - 0.38 * RATE) / (0.12 * RATE)) * (1 if i > 0.38 * RATE else 0) for i in range(n + 6000)]
    return mix(rush, chime)


SOUNDS = {
    "musket_shoot": lambda: shot(False), "musket_shoot_3p": lambda: shot(True),
    "pistol_shoot": lambda: scale(shot(False), 0.8), "pistol_shoot_3p": lambda: scale(shot(True), 0.8),
    "powder": lambda: rustle(11, 0.5), "rod_draw": lambda: scrape(12), "ram": lambda: thud(13),
    "frizzen": lambda: click(14), "cock": lambda: click(15, 0.05), "draw": lambda: rustle(16, 0.35),
    "put_away": lambda: rustle(17, 0.3), "void_pull": lambda: void_pull(),
    # TaCZ's shared sounds (its default pack had them): a snap on an empty pan, the ball striking home
    "dry_fire": lambda: click(21), "flesh_hit": lambda: hit(22, False), "head_hit": lambda: hit(23, True),
    "kill": lambda: kill_knell(),
}


def write_ogg(path, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * 0.9)) * 32767)) for s in samples)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "s.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(data)
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4",
                        "-map_metadata", "-1", "-fflags", "+bitexact", path], check=True)


# ---- data -----------------------------------------------------------------------------------------------------------


def recoil(pitch, yaw):
    return {"pitch": [{"time": 0, "value": [pitch, pitch]}, {"time": 0.08, "value": [pitch * 0.6, pitch * 0.6]},
                      {"time": 0.3, "value": [-0.3, -0.3]}, {"time": 0.5, "value": [0, 0]}],
            "yaw": [{"time": 0, "value": [-yaw, yaw]}, {"time": 0.15, "value": [0, 0]}]}


GUNS = {
    "musket": {
        "type": "rifle", "defaults": "rifle", "ammo": "musket_ball", "pellets": 1, "damage": 16,
        "falloff": [(24, 16), (48, 11), ("infinite", 7)], "speed": 140, "gravity": 0.25,
        "reload": (3.4, 4.0), "inacc": (4.5, 6, 3, 2, 0.7), "recoil": (5.5, 1.2), "weight": 4.0, "kick": 1.0,
        "aim_time": 0.3, "zoom": 1.5, "sound": "musket",
        "recipe": [("tag", "c:ingots/iron", 4), ("tag", "minecraft:planks", 6), ("item", "minecraft:flint", 1)],
    },
    "blunderbuss": {
        "type": "shotgun", "defaults": "rifle", "ammo": "shot", "pellets": 9, "damage": 3.5,
        "falloff": [(8, 3.5), (16, 2), ("infinite", 0.5)], "speed": 90, "gravity": 0.3,
        "reload": (3.6, 4.2), "inacc": (8, 9, 7, 6, 5.5), "recoil": (7.5, 2.0), "weight": 3.8, "kick": 1.3,
        "aim_time": 0.25, "zoom": 1.2, "sound": "musket",
        "recipe": [("tag", "c:ingots/copper", 6), ("tag", "c:ingots/iron", 2), ("tag", "minecraft:planks", 5),
                   ("item", "minecraft:flint", 1)],
    },
    "flintlock_pistol": {
        "type": "pistol", "defaults": "pistol", "ammo": "musket_ball", "pellets": 1, "damage": 12,
        "falloff": [(12, 12), (24, 8), ("infinite", 4)], "speed": 100, "gravity": 0.3,
        "reload": (2.6, 3.0), "inacc": (3.5, 4.5, 2.5, 2, 1.4), "recoil": (6.0, 1.5), "weight": 1.6, "kick": 1.1,
        "aim_time": 0.18, "zoom": 1.2, "sound": "pistol",
        "recipe": [("tag", "c:ingots/iron", 2), ("tag", "minecraft:planks", 3), ("item", "minecraft:flint", 1)],
    },
    # Arsenal's Void Armory flintlock: summoned only (no recipe), one shot, never reloaded (Q drops the pair for a
    # fresh one, ArsenalFlintlock); damage is the old 9, and the void adds its share to summoned weapons' hits
    # (ArsenalVoid.modifyDamage). "reload" here is only its draw time.
    "void_flintlock": {
        "type": "pistol", "defaults": "pistol", "ammo": "void_shot", "pellets": 1, "damage": 9,
        "falloff": [(24, 9), (48, 7), ("infinite", 4)], "speed": 160, "gravity": 0.05,
        "reload": (0.45, 0.5), "inacc": (2.5, 3.5, 2, 1.5, 0.8), "recoil": (5.0, 1.2), "weight": 1.2, "kick": 1.0,
        "aim_time": 0.15, "zoom": 1.2, "sound": "pistol", "void": True, "atlas": "flintlocks_void",
        "recipe": None,
    },
}
AMMO = {
    "musket_ball": {"stack": 32, "tracer": "#FFE2B0", "count": 16,
                    "recipe": [("item", "minecraft:iron_nugget", 8), ("item", "minecraft:gunpowder", 2),
                               ("item", "minecraft:paper", 1)]},
    "shot": {"stack": 24, "tracer": "#FFE2B0", "count": 8,
             "recipe": [("item", "minecraft:iron_nugget", 9), ("item", "minecraft:gunpowder", 2),
                        ("item", "minecraft:leather", 1)]},
    "void_shot": {"stack": 64, "tracer": "#E070FF", "count": 1, "recipe": None},
}
NAMES = {"musket": ("Musket", "A long smoothbore flintlock. One ball, a long reload, and a hard hit out to 24 blocks."),
         "blunderbuss": ("Blunderbuss", "A brass flintlock with a flared mouth: a fistful of shot, deadly up close."),
         "flintlock_pistol": ("Flintlock Pistol", "A single-shot horse pistol, quicker to load than a musket."),
         "void_flintlock": ("Void Flintlock", "Out of the rift, one shot at a time: a spent one is dropped and a "
                                              "loaded one pulled from the void.")}


def gun_data(g):
    inacc = dict(zip(("stand", "move", "sneak", "lie", "aim"), g["inacc"]))
    return {
        "ammo": f"{NS}:{g['ammo']}", "ammo_amount": 1, "extended_mag_ammo_amount": [1, 1, 1],
        "bolt": "open_bolt",
        "rpm": 60, "fire_mode": ["semi"],
        "bullet": {"life": 1.0, "bullet_amount": g["pellets"], "damage": g["damage"], "tracer_count_interval": 0,
                   "extra_damage": {"armor_ignore": 0.2, "head_shot_multiplier": 1.6, "damage_adjust": [
                       {"distance": d, "damage": v} for d, v in g["falloff"]]},
                   "speed": g["speed"], "gravity": g["gravity"], "knockback": 0.6, "friction": 0.03,
                   "ignite": False, "pierce": 1},
        "reload": {"type": "magazine", "feed": {"empty": g["reload"][0]}, "cooldown": {"empty": g["reload"][1]}},
        "draw_time": 0.6, "put_away_time": 0.45, "aim_time": g["aim_time"], "sprint_time": 0.3,
        "weight": g["weight"], "movement_speed": {"base": 0.0, "aim": -0.3, "reload": -0.25},
        "crawl_recoil_multiplier": 0.5, "recoil": recoil(*g["recoil"]), "inaccuracy": inacc,
        "melee": {"distance": 3, "cooldown": 1.2, "default": {"animation_type": "melee_stock", "distance": 0,
                                                                "range_angle": 30, "damage": 4, "knockback": 0.8,
                                                                "prep": 0.1}},
        "allow_attachment_types": [],
    } | ({"draw_time": g["reload"][0]} if g.get("void") else {})


def display(name, g, long):
    snd = g["sound"]
    return {
        "model": f"{NS}:gun/{name}_geo", "texture": f"{NS}:gun/uv/{g.get('atlas', 'flintlocks')}",
        "hud": f"{NS}:gun/hud/{name}", "slot": f"{NS}:gun/slot/{name}",
        "animation": f"{NS}:{name}", "use_default_animation": g["defaults"], "third_person_animation": "default",
        # third person through Player Animation Library: our own poses
        "player_animator_3rd": f"{NS}:flintlock_{'long' if long else 'pistol'}.player_animation",
        "state_machine": f"{NS}:flintlock_state_machine",
        "transform": {"scale": {"thirdperson": [0.6, 0.6, 0.6], "ground": [0.6, 0.6, 0.6],
                                "fixed": [1.2, 1.2, 1.2] if long else [1.6, 1.6, 1.6]}},
        "muzzle_flash": {"texture": f"{NS}:flash/flintlock_flash", "scale": 1.4 if long else 1.0},
        "iron_zoom": g["zoom"], "zoom_model_fov": 30,
        "sounds": {"draw": f"{NS}:flintlock/draw", "put_away": f"{NS}:flintlock/put_away",
                   "shoot": f"{NS}:flintlock/{snd}_shoot", "shoot_3p": f"{NS}:flintlock/{snd}_shoot_3p",
                   "dry_fire": f"{NS}:flintlock/dry_fire", "fire_select": f"{NS}:flintlock/cock",
                   "head_hit": f"{NS}:flintlock/head_hit", "flesh_hit": f"{NS}:flintlock/flesh_hit",
                   "kill": f"{NS}:flintlock/kill", "melee_stock": f"{NS}:flintlock/ram"},
    }


def ingredient(kind, ref, n):
    return {"item": {"tag": ref} if kind == "tag" else {"item": ref}, "count": n}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def main():
    dump(f"{PACK}/gunpack.meta.json", {"namespace": NS})
    dump(f"{A}/gunpack_info.json", {"version": "1.0.0", "name": f"pack.{NS}.jaymos_flintlocks.name",
                                    "desc": f"pack.{NS}.jaymos_flintlocks.desc", "license": "Jaymo's Powers",
                                    "authors": ["Jaymo's Powers"], "date": "2026-10-14"})
    save(f"{A}/textures/gun/uv/flintlocks.png", atlas())
    save(f"{A}/textures/gun/uv/flintlocks_void.png", atlas(VOID))
    models = {"musket": (*long_gun("musket", -44.0, False, False), True),
              "blunderbuss": (*long_gun("blunderbuss", -30.0, True, True), True),
              "flintlock_pistol": (*pistol(), False), "void_flintlock": (*pistol("void_flintlock"), False)}
    lang = {f"pack.{NS}.jaymos_flintlocks.name": "Jaymo's Flintlocks",
            f"pack.{NS}.jaymos_flintlocks.desc": "Muskets, blunderbusses and pistols for Jaymo's Powers."}
    for name, (m, geo, long) in models.items():
        g = GUNS[name]
        dump(f"{A}/geo_models/gun/{name}_geo.json", m.json())
        base = VOID if g.get("atlas") == "flintlocks_void" else BASE
        save(f"{A}/textures/gun/slot/{name}.png", side_view(m, (64, 64), 2, base=base))
        save(f"{A}/textures/gun/hud/{name}.png", side_view(m, (384, 128), 6, (235, 235, 235, 230)))
        hands = HANDS_LONG if long else HANDS_PISTOL
        anims = void_animations(hands, g["kick"], g["reload"][0]) if g.get("void") \
            else animations(hands, geo, long, g["reload"][1] - 0.1, g["kick"])
        dump(f"{A}/animations/{name}.animation.json", anims)
        dump(f"{A}/display/guns/{name}_display.json", display(name, g, long))
        dump(f"{D}/index/guns/{name}.json", {"name": f"{NS}.gun.{name}.name", "display": f"{NS}:{name}_display",
                                             "data": f"{NS}:{name}_data", "tooltip": f"{NS}.gun.{name}.desc",
                                             "type": g["type"], "sort": 1})
        dump(f"{D}/data/guns/{name}_data.json", gun_data(g))
        if g["recipe"]:
            dump(f"{D}/recipe/gun/{name}.json", {"materials": [ingredient(*i) for i in g["recipe"]],
                                                 "result": {"type": "gun", "id": f"{NS}:{name}",
                                                            "group": f"{NS}:firearms"},
                                                 "type": "tacz:gun_smith_table_crafting"})
        lang[f"{NS}.gun.{name}.name"], lang[f"{NS}.gun.{name}.desc"] = NAMES[name]
    for name, a in AMMO.items():
        save(f"{A}/textures/ammo/slot/{name}.png", ammo_icon(name))
        dump(f"{A}/display/ammo/{name}_display.json", {"slot": f"{NS}:ammo/slot/{name}", "tracer_color": a["tracer"]})
        dump(f"{D}/index/ammo/{name}.json", {"name": f"{NS}.ammo.{name}.name", "display": f"{NS}:{name}_display",
                                             "stack_size": a["stack"], "sort": 1})
        if a["recipe"]:
            dump(f"{D}/recipe/ammo/{name}.json", {"materials": [ingredient(*i) for i in a["recipe"]],
                                                  "result": {"type": "ammo", "group": f"{NS}:shot_and_ball",
                                                             "id": f"{NS}:{name}", "count": a["count"]},
                                                  "type": "tacz:gun_smith_table_crafting"})
    # what every gun used to borrow from TaCZ's default pack, now our own
    os.makedirs(f"{A}/scripts", exist_ok=True)
    with open(f"{A}/scripts/flintlock_state_machine.lua", "w", encoding="utf-8", newline="\n") as f:
        f.write(STATE_MACHINE)
    reload_long = GUNS["musket"]["reload"][1] - 0.1
    reload_pistol = GUNS["flintlock_pistol"]["reload"][1] - 0.1
    dump(f"{A}/player_animator/flintlock_long.player_animation.json", player_animations(True, reload_long))
    dump(f"{A}/player_animator/flintlock_pistol.player_animation.json", player_animations(False, reload_pistol))
    save(f"{A}/textures/flash/flintlock_flash.png", muzzle_flash())
    bench_model = bench()
    dump(f"{A}/geo_models/block/gunsmith_bench.json", bench_model.json())
    dump(f"{A}/display/blocks/gunsmith_bench.json", {
        "model": f"{NS}:block/gunsmith_bench", "texture": f"{NS}:gun/uv/flintlocks",
        "transforms": {
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 0.5, -2], "scale": [0.22, 0.22, 0.22]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 0.5, 2], "scale": [0.22, 0.22, 0.22]},
            "firstperson_righthand": {"rotation": [0, 90, 0], "scale": [0.24, 0.24, 0.24]},
            "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 0, 4], "scale": [0.24, 0.24, 0.24]},
            "ground": {"translation": [2, 2, 0], "scale": [0.24, 0.24, 0.24]},
            "gui": {"rotation": [20, -140, 0], "translation": [-2.5, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
            "fixed": {"translation": [4, -3, -3], "scale": [0.48, 0.48, 0.48]}}})
    # TaCZ's own gun smith table block and item look for the index tacz:gun_smith_table, which only its default pack
    # had: ours points it at the bench, our tabs and a filter showing only our recipes
    dump(f"{PACK}/data/tacz/index/blocks/gun_smith_table.json", {
        "name": f"{NS}.block.gunsmith_bench.name", "display": f"{NS}:gunsmith_bench", "data": f"{NS}:gunsmith_bench",
        "tooltip": f"{NS}.block.gunsmith_bench.desc", "id": "tacz:gun_smith_table", "sort": 1})
    gun_icon = {"id": "tacz:modern_kinetic_gun", "components": {"minecraft:custom_data": f'{{GunId: "{NS}:musket"}}'}}
    ammo_icon_stack = {"id": "tacz:ammo", "components": {"minecraft:custom_data": f'{{AmmoId: "{NS}:musket_ball"}}'}}
    dump(f"{D}/data/blocks/gunsmith_bench.json", {"filter": f"{NS}:flintlocks", "tabs": [
        {"id": f"{NS}:firearms", "name": f"{NS}.tab.firearms", "icon": gun_icon},
        {"id": f"{NS}:shot_and_ball", "name": f"{NS}.tab.shot_and_ball", "icon": ammo_icon_stack}]})
    dump(f"{D}/recipe_filters/flintlocks.json", {"whitelist": [f"^{NS}:.*$"], "blacklist": []})
    lang[f"{NS}.block.gunsmith_bench.name"] = "Gunsmith's Bench"
    lang[f"{NS}.block.gunsmith_bench.desc"] = "Where muskets, blunderbusses and pistols are made, and their shot cast."
    lang[f"{NS}.tab.firearms"] = "Firearms"
    lang[f"{NS}.tab.shot_and_ball"] = "Shot and Ball"
    lang[f"{NS}.ammo.musket_ball.name"] = "Musket Balls"
    lang[f"{NS}.ammo.shot.name"] = "Shot"
    lang[f"{NS}.ammo.void_shot.name"] = "Void Shot"
    dump(f"{A}/lang/en_us.json", dict(sorted(lang.items())))
    for name, make in SOUNDS.items():
        path = f"{A}/tacz_sounds/flintlock/{name}.ogg"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        write_ogg(path, make())


if __name__ == "__main__":
    main()
