"""The thirteen sea-blooded Tideborn looks (Kingdoms 0.7.485, Andy: "tideborn need to vary wildly ... they are fish people so
they can draw from any sealife"): shark, manta, octopus, anglerfish, jellyfish, moray, crab, pufferfish, lionfish, seadragon,
swordfish, sea slug and sea turtle.

Reads the pieces from client SeaParts.TABLE (kingdoms/src/client/.../SeaParts.java), lays each look's boxes out on its sheet
the way SeaParts.pack does, and writes, under textures/entity/heritage/:

parts/sea_<key>.png        the model parts' sheet (64x64), painted per piece by its mat=
parts/sea_<key>_glow.png   drawn emissive over it (only the lit parts: a lure, a bell's rim, frill tips)
variant/<key>_skin.png     marks on the 64x64 skin: face, gills and forearms (HeritageLooks/VillagerLooks draw them)
variant/<key>_glow.png     the marks' emissive twin (lit as the Tideborn's are)
variant/<key>_eyes.png     the stock eye spots in the look's eye colour (VariantLooks has the same colours)
variant/<key>_fins.png     the Tideborn's own fins in the look's colours (+ _fins_glow), for the looks that keep them

The sources stay ungraded; the optional Excalibur look grades them into its own pack (python -I
tools/excalibur_style/grade.py). Nothing is copied; all pixel art is drawn here.
Run from the project root:  python -I tools/gen_tideborn_sea.py
"""
import math
import os
import random
import re

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
JAVA = os.path.join(ROOT, "kingdoms", "src", "client", "java", "com", "jaymoskingdoms", "client", "SeaParts.java")
TEX = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "heritage")
PARTS, VARIANT = os.path.join(TEX, "parts"), os.path.join(TEX, "variant")
SHEET = 64


# ---- the table (the same parse as SeaParts.parse)

def parse_table(path=JAVA):
    src = open(path, encoding="utf-8").read()
    body = re.search(r'TABLE = """(.*?)""";', src, re.S).group(1)
    designs, cur = [], None
    for raw in body.split("\n"):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        t = line.split()
        if t[0].startswith("@"):
            cur = {"key": t[0][1:], "fins": "ALL", "glow": "WET", "translucent": False, "pieces": []}
            for opt in t[1:]:
                if opt.startswith("fins="):
                    cur["fins"] = opt[5:]
                elif opt.startswith("glow="):
                    cur["glow"] = opt[5:]
                elif opt == "translucent":
                    cur["translucent"] = True
            designs.append(cur)
            continue
        f = [float(v) for v in t[3:15]]
        p = {"id": t[0], "anchor": t[1], "pair": t[2] == "1", "x": f[0], "y": f[1], "z": f[2],
             "w": round(f[3]), "h": round(f[4]), "d": round(f[5]), "p": f[6:9], "r": f[9:12],
             "n": 1, "step": (0.0, 0.0, 0.0), "bend": (0.0, 0.0, 0.0), "taper": 1.0, "wave": None, "ar": 1.2,
             "s": (1.0, 1.0, 1.0), "mat": None, "on": None, "children": []}
        for opt in t[15:]:
            k, v = opt.split("=", 1)
            vals = v.split(",")
            if k == "on":
                p["on"] = v
            elif k == "n":
                p["n"] = int(v)
            elif k in ("step", "bend", "s"):
                p[k] = tuple(float(a) for a in vals)
            elif k in ("taper", "ar"):
                p[k] = float(v)
            elif k == "wave":
                p["wave"] = (float(vals[0]), vals[1], float(vals[2]), float(vals[3]))
            elif k == "mat":
                p["mat"] = v
        if p["on"]:
            parent = [q for q in cur["pieces"] if q["id"] == p["on"]][-1]
            parent["children"].append(p)
        cur["pieces"].append(p)
    for d in designs:
        pack(d["pieces"])
    return designs


def pack(pieces):
    """SeaParts.pack: table order, left to right in rows; a box's unwrap is 2(w+d) wide, d+h tall."""
    x = y = row = 0
    for p in pieces:
        wide, tall = 2 * (p["w"] + p["d"]), p["d"] + p["h"]
        if x + wide > SHEET:
            x, y, row = 0, y + row, 0
        p["u"], p["v"] = x, y
        x += wide
        row = max(row, tall)
    assert y + row <= SHEET, "a look's boxes run off its sheet"


def faces(u, v, w, h, d):
    """A box's unwrapped faces: name -> (x, y, width, height) on the sheet (the vanilla cube layout)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def box_cell(face, i, j, w, h, d):
    """Where texel (i, j) of a face sits on the box, as whole cells (X across, Y down, Z back; 0 when a size is 0).
    The unwrap runs right, front, left, back round the box, the top's bottom row against the front's top row."""
    W, H, D = max(w, 1), max(h, 1), max(d, 1)
    if face == "front":
        return i, j, 0
    if face == "back":
        return W - 1 - i, j, D - 1
    if face == "right":
        return 0, j, D - 1 - i
    if face == "left":
        return W - 1, j, i
    if face == "top":
        return i, 0, D - 1 - j
    return i, H - 1, j  # bottom


# ---- colour helpers

def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def mix(a, b, t):
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3)) + (a[3] if len(a) > 3 else 255,)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def noisy(rng, pal):
    return rng.choice(pal)


# ---- the parts' paints: fn(face, X, Y, Z, W, H, D, rng) -> colour or None; glow fn the same (None: no glow)

def solid(pal, under=None):
    def f(face, X, Y, Z, W, H, D, rng):
        if under is not None and face == "bottom":
            return noisy(rng, under)
        return noisy(rng, pal)
    return f


SHARK = [rgb(0x7A858E), rgb(0x6E7982), rgb(0x838E96)]
SHARK_DARK = [rgb(0x5A646C), rgb(0x525C64)]
SHARK_PALE = [rgb(0xD6D8D4), rgb(0xC8CCC8)]


def shark_fin(face, X, Y, Z, W, H, D, rng):
    t = 1.0 - Y / max(1, H - 1)  # 0 at the root, 1 at the tip
    lead, trail = t * (D - 2.2), D - 0.4 - 1.8 * t ** 0.6
    if not (lead - 0.6 <= Z <= trail):
        return None
    if Z <= lead + 0.4 or Y == 0:
        return noisy(rng, SHARK_DARK)
    return noisy(rng, SHARK)


def caudal(main, dark):
    def f(face, X, Y, Z, W, H, D, rng):
        c = H * 0.6  # the fork: the upper lobe longer
        side = c if Y < c else H - c
        k = abs(Y + 0.5 - c) / max(1.0, side)
        if not (k * D - 1.6 <= Z <= 0.8 + k * D * 0.95):
            return None
        return noisy(rng, dark) if Z >= k * D * 0.95 - 0.2 else noisy(rng, main)
    return f


MANTA = [rgb(0x2E3A4C), rgb(0x34404F), rgb(0x283445)]
MANTA_PALE = [rgb(0xE4E6E2), rgb(0xD8DCD8)]


def manta_wing(face, X, Y, Z, W, H, D, rng):
    reach = 1.0 + (W - 2) / 2.0 * Y if Y <= 2 else (W - 1) - (Y - 2) * ((W - 1) / (H - 2.6))
    if X > reach:
        return None
    if face == "front":  # the underside, toward the body: pale, grey at the edge
        return noisy(rng, MANTA_PALE) if X < reach - 1 else rgb(0x9AA2AA)
    if X >= reach - 1:
        return rgb(0x1E2836)
    if (X * 7 + Y * 3) % 11 == 0 and Y < 6:  # pale shoulder patches
        return rgb(0x8A96A6)
    return noisy(rng, MANTA)


OCTO = [rgb(0xB0485E), rgb(0xA23E54), rgb(0xBC5468)]
OCTO_SPOT = rgb(0xE08A8A)
SUCKER = [rgb(0xF0C8BC), rgb(0xE6B4A8)]


def octo_mantle(face, X, Y, Z, W, H, D, rng):
    if rng.random() < 0.12:
        return OCTO_SPOT
    if rng.random() < 0.08:
        return rgb(0x7A2A40)
    return noisy(rng, OCTO)


def octo_arm(face, X, Y, Z, W, H, D, rng):
    if face in ("front", "back") and (X + Y) % 2 == 0:
        return noisy(rng, SUCKER)
    return noisy(rng, OCTO)


ANGLER = [rgb(0x4A4234), rgb(0x40392C), rgb(0x544A3A)]
IVORY = [rgb(0xEEEAD8), rgb(0xE0DAC4)]


def angler_stalk(face, X, Y, Z, W, H, D, rng):
    return noisy(rng, ANGLER) if Y > 0 else rgb(0x6A5C44)


def angler_bulb(face, X, Y, Z, W, H, D, rng):
    return rgb(0xC8F0E0) if (X + Y + Z) % 2 else rgb(0xA8E4D4)


def angler_bulb_glow(face, X, Y, Z, W, H, D, rng):
    return rgb(0xD8FFF0) if (X + Y + Z) % 2 else rgb(0x90F0D8)


def angler_jaw(face, X, Y, Z, W, H, D, rng):
    if face == "top" and Z == 0 and X % 2 == 0:  # a row of needle teeth on the lip
        return noisy(rng, IVORY)
    if face == "front" and Y == 0 and X % 2 == 0:
        return noisy(rng, IVORY)
    if face == "top":
        return rgb(0x5A2A2A)  # the inside of the mouth
    return noisy(rng, ANGLER)


def tooth(face, X, Y, Z, W, H, D, rng):
    return IVORY[0] if Y == 0 else IVORY[1]


JELLY = [rgb(0xD8B8F4, 150), rgb(0xCCAAEE, 140), rgb(0xE2C8F8, 150)]


def jelly_bell(face, X, Y, Z, W, H, D, rng):
    if face == "bottom":
        return rgb(0xB070C0, 120)
    if face != "top" and Y == H - 1:
        return rgb(0xF0A0D8, 190)  # the rim
    if face == "top" and (X - W / 2) ** 2 + (Z - D / 2) ** 2 < (W / 4) ** 2:
        return rgb(0xF4C0E8, 170)  # the four-leaf middle
    return noisy(rng, JELLY)


def jelly_bell_glow(face, X, Y, Z, W, H, D, rng):
    if face != "top" and face != "bottom" and Y == H - 1 and (X + Z) % 2 == 0:
        return rgb(0xE890FF)
    return None


def jelly_thread(face, X, Y, Z, W, H, D, rng):
    return rgb(0xF0B8E4, 170) if Y % 2 else rgb(0xE0A8E8, 150)


def jelly_thread_glow(face, X, Y, Z, W, H, D, rng):
    return rgb(0xC070F0) if Y == 2 and face in ("front", "back") else None


MORAY = [rgb(0x4E6A2E), rgb(0x445E28), rgb(0x587434)]
GOLD = rgb(0xD8C048)


def moray_hide(face, X, Y, Z, W, H, D, rng):
    if rng.random() < 0.18:
        return GOLD
    return noisy(rng, MORAY)


def moray_fin(face, X, Y, Z, W, H, D, rng):
    # a ribbon: the outer edge (away from the body) yellow-green, rounded ends on the long ones
    long = max(H, D)
    if long >= 7 and ((Z == 0 and Y in (0, H - 1) and H > D) or (Y == 0 and Z in (0, D - 1) and D > H)):
        return None
    outer = (W == 0 and D <= 2 and Z == D - 1) or (W == 0 and H <= 2 and Y == 0)
    if outer:
        return rgb(0xB4C458)
    return noisy(rng, [rgb(0x6A8A38), rgb(0x5E7E32)])


CRAB = [rgb(0xC0502E), rgb(0xB04428), rgb(0xCC6034)]


def crab_shell(face, X, Y, Z, W, H, D, rng):
    if rng.random() < 0.1:
        return rgb(0xE8A070)  # pale bumps
    if face in ("back", "top", "left", "right") and (Y == 0 or Y == H - 1):
        return rgb(0x8A3020)
    return noisy(rng, CRAB)


def crab_spike(face, X, Y, Z, W, H, D, rng):
    return rgb(0xF0C8A0) if Y == 0 else CRAB[0]


def crab_claw(face, X, Y, Z, W, H, D, rng):
    if Y >= H - 2:
        return rgb(0x2A1E1A) if Y == H - 1 else rgb(0x6A2A1E)  # the black tips
    if face in ("front", "right") and Y % 2 == 0:
        return rgb(0xE07A4A)
    return noisy(rng, CRAB)


def crab_stalk(face, X, Y, Z, W, H, D, rng):
    return rgb(0xD87A54) if Y % 2 else rgb(0xC86A48)


def crab_eye(face, X, Y, Z, W, H, D, rng):
    if face == "front" and X == 0 and Y == 0:
        return rgb(0xE8F0F0)  # a glint
    return rgb(0x161616) if Y < H - 1 else rgb(0x3A2420)


PUFF_SPINE = [rgb(0xE4D8A4), rgb(0xD8CC94)]


def puffer_spine(face, X, Y, Z, W, H, D, rng):
    return rgb(0x5A4428) if Y == 0 else noisy(rng, PUFF_SPINE)


LION_RED, LION_WHITE = [rgb(0xB83A2E), rgb(0xA8322A)], [rgb(0xF0E6DA), rgb(0xE6DACC)]


def lion_ray(face, X, Y, Z, W, H, D, rng):
    # a venom spine (the column nearest the root: x 0 on a forward-facing ray, z 0 on a back ray) and a membrane that
    # runs only partway up it, banded red and white every two rows
    spine = (X == 0) if D == 0 else (Z == 0)
    band = LION_RED if (Y // 2) % 2 == 0 else LION_WHITE
    if spine:
        return rgb(0x3A1E1E) if Y == 0 else noisy(rng, band)
    if Y < H * 0.35:
        return None
    return shade(noisy(rng, band), 0.85)


def lion_fan(face, X, Y, Z, W, H, D, rng):
    r = math.hypot(Z + 0.5, Y + 0.5)
    if r > 6.6:
        return None
    ang = math.atan2(Y + 0.5, Z + 0.5)
    ray = abs((ang / (math.pi / 2)) * 5 - round((ang / (math.pi / 2)) * 5)) < 0.18
    if ray:
        return rgb(0x3A1E1E) if r > 5.6 else LION_WHITE[0]
    return shade(LION_RED[0], 0.9) if int(r) % 2 else LION_WHITE[1]


DRAGON = [rgb(0xC8A848), rgb(0xBC9C40), rgb(0xD4B454)]
DRAGON_BAND = rgb(0xD8782E)


def dragon_crown(face, X, Y, Z, W, H, D, rng):
    if face == "top" and (X + Z) % 2 == 0:
        return rgb(0xE89040)
    return noisy(rng, DRAGON)


def frond_stalk(face, X, Y, Z, W, H, D, rng):
    return rgb(0xB89838) if Y % 2 else rgb(0xA88A30)


def frond_leaf(face, X, Y, Z, W, H, D, rng):
    # a ragged leaf on its stalk: wide at the middle, notched, a midrib up its middle
    cx, w = (W - 1) / 2.0, [1.0, 2.0, 2.0, 1.0][Y % 4] if H == 4 else 2.0
    if abs(X - cx) > w - 0.4:
        return None
    if (X + Y * 2) % 5 == 0 and abs(X - cx) > 0.6:
        return None
    if abs(X - cx) < 0.6:
        return rgb(0x9A7A2A)
    return rgb(0xA8B040) if (X + Y) % 3 else rgb(0xD88A38)


def dragon_tail(face, X, Y, Z, W, H, D, rng):
    if Z == 0:
        return DRAGON_BAND
    if rng.random() < 0.12:
        return rgb(0xF0E0B0)
    return noisy(rng, DRAGON)


SWORD = [rgb(0x3E5A88), rgb(0x365080), rgb(0x46628E)]
SILVER = [rgb(0xB8C4CC), rgb(0xA8B4BE)]


def sword_bill(face, X, Y, Z, W, H, D, rng):
    if face == "bottom" or (face in ("left", "right") and Y == H - 1):
        return noisy(rng, SILVER)
    return rgb(0x2E3E58) if face == "top" else noisy(rng, [rgb(0x4A5A74), rgb(0x445470)])


def sword_sail(face, X, Y, Z, W, H, D, rng):
    depth = (D - 0.6) * (1.0 - Y / 12.0) ** 0.7 + 0.6
    if Z > depth:
        return None
    if Y % 2 == 0 and Z < depth - 0.8:
        return rgb(0x24365A)  # the rays
    if rng.random() < 0.1:
        return rgb(0x7A8AD8)  # violet flecks
    return noisy(rng, SWORD)


def sword_hide(face, X, Y, Z, W, H, D, rng):
    return noisy(rng, SILVER) if face == "bottom" else noisy(rng, SWORD)


SLUG = [rgb(0x3A50D8), rgb(0x3248C8), rgb(0x4458E0)]
SLUG_ORANGE, SLUG_WHITE = rgb(0xF08A2A), rgb(0xF4F0E8)


def slug_horn(face, X, Y, Z, W, H, D, rng):
    return SLUG_WHITE if Y % 2 == 0 else SLUG_ORANGE


def slug_cerata(face, X, Y, Z, W, H, D, rng):
    if Y == 0:
        return SLUG_ORANGE
    if Y == 1:
        return SLUG_WHITE
    return noisy(rng, SLUG)


def slug_cerata_glow(face, X, Y, Z, W, H, D, rng):
    return rgb(0xFF9A3A) if Y == 0 else None


TURTLE_SHELL = [rgb(0x5A4A2E), rgb(0x52432A), rgb(0x625234)]
TURTLE_SEAM = rgb(0xB89A58)
TURTLE_SKIN = [rgb(0x7A8A50), rgb(0x6E7E48), rgb(0x84944E)]


def turtle_shell(face, X, Y, Z, W, H, D, rng):
    # scutes: a honeycomb of plates edged pale
    if face in ("back", "top", "bottom"):
        col, row = X, Y if face == "back" else Z
        off = 2 if (col // 4) % 2 else 0
        if col % 4 == 0 or (row + off) % 4 == 0:
            return TURTLE_SEAM
    if face in ("left", "right") and Y % 3 == 0:
        return TURTLE_SEAM
    return noisy(rng, TURTLE_SHELL)


def turtle_dome(face, X, Y, Z, W, H, D, rng):
    if face == "back" and (X % 4 == 1 or Y % 5 == 0):
        return rgb(0xC4A860)
    return noisy(rng, [rgb(0x6A5834), rgb(0x725E38)])


def turtle_skin(face, X, Y, Z, W, H, D, rng):
    return rgb(0xD8D090) if rng.random() < 0.15 else noisy(rng, TURTLE_SKIN)


def turtle_beak(face, X, Y, Z, W, H, D, rng):
    return rgb(0x8A8470) if Y == 0 else rgb(0x6A6450)


MATS = {
    "shark_fin": shark_fin, "shark_hide": solid(SHARK, SHARK_PALE), "shark_caudal": caudal(SHARK, SHARK_DARK),
    "manta_wing": manta_wing, "manta_dark": solid(MANTA, MANTA_PALE),
    "octo_mantle": octo_mantle, "octo_arm": octo_arm,
    "angler_stalk": angler_stalk, "angler_bulb": angler_bulb, "angler_jaw": angler_jaw, "tooth": tooth,
    "jelly_bell": jelly_bell, "jelly_thread": jelly_thread,
    "moray_hide": moray_hide, "moray_fin": moray_fin,
    "crab_shell": crab_shell, "crab_spike": crab_spike, "crab_claw": crab_claw, "crab_stalk": crab_stalk, "crab_eye": crab_eye,
    "puffer_spine": puffer_spine,
    "lion_ray": lion_ray, "lion_fan": lion_fan,
    "dragon_crown": dragon_crown, "frond_stalk": frond_stalk, "frond_leaf": frond_leaf, "dragon_tail": dragon_tail,
    "sword_bill": sword_bill, "sword_sail": sword_sail, "sword_hide": sword_hide, "sword_caudal": caudal(SWORD, [rgb(0x24365A)]),
    "slug_horn": slug_horn, "slug_cerata": slug_cerata,
    "turtle_shell": turtle_shell, "turtle_dome": turtle_dome, "turtle_skin": turtle_skin, "turtle_beak": turtle_beak,
}
GLOWS = {"angler_bulb": angler_bulb_glow, "jelly_bell": jelly_bell_glow, "jelly_thread": jelly_thread_glow,
         "slug_cerata": slug_cerata_glow}


def paint_sheet(design):
    sheet, glow = Image.new("RGBA", (SHEET, SHEET)), Image.new("RGBA", (SHEET, SHEET))
    for p in design["pieces"]:
        fn, gfn = MATS[p["mat"]], GLOWS.get(p["mat"])
        rng = random.Random(sum(map(ord, design["key"] + p["id"])) * 31)
        w, h, d = p["w"], p["h"], p["d"]
        for face, (fx, fy, fw, fh) in faces(p["u"], p["v"], w, h, d).items():
            for j in range(fh):
                for i in range(fw):
                    X, Y, Z = box_cell(face, i, j, w, h, d)
                    c = fn(face, X, Y, Z, w, h, d, rng)
                    if c:
                        sheet.putpixel((fx + i, fy + j), c)
                    if gfn:
                        g = gfn(face, X, Y, Z, w, h, d, rng)
                        if g:
                            glow.putpixel((fx + i, fy + j), g)
    return sheet, glow


# ---- the marks on the skin (64x64 player layout): face front (8..16, 8..16), its sides (0..8 right, 16..24 left), the
# forearms (the arms' side faces, ARMS rows up from the hand), the hands' bottoms

ARMS = ((40, 56, 20), (32, 48, 52))
HANDS = ((47, 16, 52, 20), (39, 48, 44, 52))
EYES = ((9, 12), (10, 12), (13, 12), (14, 12))


def put(img, x, y, c):
    if 0 <= x < 64 and 0 <= y < 64 and c:
        img.putpixel((x, y), c)


def forearms(fn, reach=8):
    """fn(x, y, r: rows up from the hand, col: across the arm's four sides) on the forearms."""
    for x0, x1, y0 in ARMS:
        for y in range(y0, y0 + 12):
            r = y0 + 11 - y
            if r < reach:
                for x in range(x0, x1):
                    fn(x, y, r, x - x0)


def hands(fn):
    for x0, y0, x1, y1 in HANDS:
        for x in range(x0, x1):
            for y in range(y0, y1):
                fn(x, y)


def gills(solid, colour, glow=None, glow_colour=None, rows=(12, 13, 14), cols=(3, 5)):
    """Slits down the sides of the head behind the jaw (both sides: right face cols from its front edge)."""
    for y in rows:
        for c in cols:
            for x in (7 - c, 16 + c):
                put(solid, x, y, colour)
                if glow is not None:
                    put(glow, x, y, glow_colour)


def teeth(solid, row=15, xs=range(9, 15), colour=(236, 232, 216, 255), gum=(70, 30, 34, 255)):
    for x in xs:
        put(solid, x, row, colour if x % 2 else gum)


def scatter_forearms(rng, img, colours, chance, reach=8):
    forearms(lambda x, y, r, c: put(img, x, y, rng.choice(colours)) if rng.random() < chance * (1 - r / (reach + 2)) else None, reach)


def m_greyfin(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, rng.choice(SHARK if r > 1 else SHARK_DARK)) if rng.random() < 0.9 - r * 0.08 else None, 7)
    gills(s, (60, 66, 72, 255), cols=(2, 3, 4))
    teeth(s)
    for x in range(9, 15):  # the pale throat under the jaw line
        put(s, x, 15, (214, 216, 212, 255) if x in (9, 14) else None)


def m_mantawing(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, rng.choice(MANTA) if c % 4 in (1, 2, 3) or r < 2 else rng.choice(MANTA_PALE)), 7)
    for x in range(8, 16):  # dark over the brow, pale spots over the cheeks
        put(s, x, 8, MANTA[0])
        put(s, x, 9, MANTA[1] if x % 3 else None)
    for x, y in ((9, 14), (14, 14), (10, 15), (13, 15)):
        put(s, x, y, MANTA_PALE[0])
    gills(s, (34, 44, 60, 255), rows=(13, 14), cols=(2, 4))


def m_eightarm(rng, s, g):
    scatter_forearms(rng, s, OCTO + [OCTO_SPOT], 0.85, 8)
    hands(lambda x, y: put(s, x, y, rng.choice(SUCKER) if (x + y) % 2 else OCTO[0]))
    for x, y in ((8, 11), (11, 11), (12, 11), (15, 11), (8, 13), (15, 13)):  # dark rings round the eyes
        put(s, x, y, (110, 40, 60, 255))
    for x, y in ((10, 9), (13, 10), (9, 14), (14, 15)):
        put(s, x, y, OCTO_SPOT)


def m_lanternjaw(rng, s, g):
    scatter_forearms(rng, s, ANGLER + [(110, 96, 70, 255)], 0.95, 8)
    for x, y in ((8, 9), (11, 8), (15, 10), (9, 14), (14, 13), (12, 9)):  # warts
        put(s, x, y, (106, 92, 68, 255))
    for x in range(8, 16):
        put(s, x, 11, ANGLER[1])
    teeth(s, row=15)
    forearms(lambda x, y, r, c: (put(g, x, y, (150, 240, 210, 255)) if r == 4 and c % 4 == 1 else None), 8)


def m_driftbell(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, (230, 206, 246, 255)) if (c + r) % 3 == 0 else None, 8)
    forearms(lambda x, y, r, c: put(g, x, y, (226, 150, 255, 255)) if (c + r) % 6 == 0 else None, 8)
    for x in range(8, 16):
        put(s, x, 8, (236, 214, 248, 255))
        put(g, x, 8, (200, 130, 240, 255) if x % 2 else None)


def m_moray(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, GOLD if rng.random() < 0.2 else rng.choice(MORAY)) if rng.random() < 0.95 - r * 0.06 else None, 8)
    for y in range(8, 16):
        for x in range(8, 16):
            if rng.random() < 0.12 and y != 12:
                put(s, x, y, GOLD)
    teeth(s, row=15, xs=range(8, 16))
    teeth(s, row=14, xs=(9, 14))
    gills(s, (52, 70, 32, 255), rows=(13, 14), cols=(3,))


def m_shellclaw(rng, s, g):
    def plates(x, y, r, c):
        if r < 7:
            put(s, x, y, (138, 48, 32, 255) if r % 3 == 0 else rng.choice(CRAB))
    forearms(plates, 7)
    hands(lambda x, y: put(s, x, y, CRAB[1]))
    for x, y in ((10, 15), (13, 15), (11, 14), (12, 14)):  # mouthparts
        put(s, x, y, (120, 44, 30, 255))
    for x, y in ((8, 9), (15, 9), (9, 10), (14, 10)):
        put(s, x, y, (232, 160, 112, 255))


def m_spinepuff(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, (90, 68, 40, 255) if rng.random() < 0.25 else (216, 196, 120, 255)) if rng.random() < 0.9 - r * 0.07 else None, 8)
    for x, y in ((8, 9), (10, 8), (13, 8), (15, 9), (9, 10), (14, 10)):
        put(s, x, y, (90, 68, 40, 255))
    for x in range(9, 15):  # the round pale chin
        put(s, x, 15, (232, 226, 200, 255))
    for x, y in ((8, 14), (15, 14)):
        put(s, x, y, (232, 226, 200, 255))


def m_lionmane(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, rng.choice(LION_RED) if (c // 2) % 2 == 0 else rng.choice(LION_WHITE)), 8)
    for y in range(8, 16):  # a red band down through each eye, white between
        for x in (9, 10, 13, 14):
            if y != 12:
                put(s, x, y, LION_RED[0] if y < 15 else None)
    for y in range(8, 16):
        for c in (1, 2, 5, 6):
            put(s, 7 - c, y, LION_RED[0] if c in (1, 2) else LION_WHITE[0])
            put(s, 16 + c, y, LION_RED[0] if c in (1, 2) else LION_WHITE[0])


def m_weedfrond(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, DRAGON_BAND if r % 3 == 0 else rng.choice(DRAGON)) if rng.random() < 0.9 else None, 8)
    for x, y in ((9, 9), (14, 9), (8, 14), (15, 14), (11, 15), (12, 15)):
        put(s, x, y, (240, 224, 176, 255))
    for x in range(8, 16):
        put(s, x, 10 if x % 2 else 11, DRAGON_BAND if x not in (9, 10, 13, 14) else None)


def m_swordbill(rng, s, g):
    forearms(lambda x, y, r, c: put(s, x, y, rng.choice(SWORD) if r > 3 else rng.choice(SILVER)) if c % 4 != 0 or r < 6 else put(s, x, y, (50, 70, 120, 255)), 8)
    for x in range(8, 16):
        put(s, x, 8, SWORD[0])
        put(s, x, 9, SWORD[1])
        put(s, x, 15, SILVER[0])
    gills(s, (40, 56, 90, 255), rows=(12, 13, 14), cols=(2,))


def m_frillback(rng, s, g):
    def f(x, y, r, c):
        put(s, x, y, SLUG_ORANGE if r in (0, 6) else SLUG_WHITE if (c + r * 3) % 7 == 0 else rng.choice(SLUG))
        if r in (0, 6):
            put(g, x, y, (255, 150, 60, 255))
    forearms(f, 7)
    for x in range(8, 16):
        put(s, x, 8, SLUG_ORANGE)
        put(g, x, 8, (255, 150, 60, 255))
    for x, y in ((8, 10), (15, 10), (8, 14), (15, 14), (11, 15), (12, 15)):
        put(s, x, y, SLUG_WHITE)


def m_oldshell(rng, s, g):
    def scales(x, y, r, c):
        if (c % 3 == 0 and r % 2 == 0) or r % 3 == 0:
            put(s, x, y, (200, 190, 120, 255))
        else:
            put(s, x, y, rng.choice(TURTLE_SKIN))
    forearms(scales, 8)
    hands(lambda x, y: put(s, x, y, rng.choice(TURTLE_SKIN)))
    for x, y in ((8, 8), (9, 8), (14, 8), (15, 8), (8, 9), (15, 9), (11, 8), (12, 8)):  # plates on the brow
        put(s, x, y, (200, 190, 120, 255))
    for x in range(10, 14):  # the beak's line
        put(s, x, 14, (90, 86, 70, 255))


MARKS = {"greyfin": m_greyfin, "mantawing": m_mantawing, "eightarm": m_eightarm, "lanternjaw": m_lanternjaw,
         "driftbell": m_driftbell, "moray": m_moray, "shellclaw": m_shellclaw, "spinepuff": m_spinepuff,
         "lionmane": m_lionmane, "weedfrond": m_weedfrond, "swordbill": m_swordbill, "frillback": m_frillback,
         "oldshell": m_oldshell}
# the eyes' colours (client VariantLooks has the same)
EYE = {"greyfin": 0xE8EEF2, "mantawing": 0xB8E0FF, "eightarm": 0xFFC060, "lanternjaw": 0xF0FFE0, "driftbell": 0xF0C8FF,
       "moray": 0xFFE060, "shellclaw": 0xFFD040, "spinepuff": 0x60D8FF, "lionmane": 0xFF5040, "weedfrond": 0xFFB040,
       "swordbill": 0xC8E8FF, "frillback": 0xFF9030, "oldshell": 0xE0D890}
# the Tideborn's own fins in each look's colours: membrane, spine, edge, edge glow
FIN_COLOURS = {"greyfin": (0x7A858E, 0x5A646C, 0x9AA4AC, 0x8090A0), "mantawing": (0x34404F, 0x283445, 0x5A6A80, 0x6080B0),
               "eightarm": (0xB0485E, 0x8A3448, 0xE08A8A, 0xE07080), "lanternjaw": (0x4A4234, 0x3A3428, 0x6A5C44, 0x90F0D0),
               "driftbell": (0xD8B8F4, 0xB890E0, 0xF0D0FF, 0xE090FF), "moray": (0x587434, 0x445E28, 0xB4C458, 0xC8D860),
               "shellclaw": (0xC0502E, 0x8A3020, 0xE8A070, 0xF08A50), "spinepuff": (0xD8C070, 0x8A6A38, 0xE8DCA0, 0xF0E090),
               "lionmane": (0xF0E6DA, 0xB83A2E, 0xB83A2E, 0xFF6040), "weedfrond": (0xB8A040, 0xA0802A, 0xD8782E, 0xF0A040),
               "swordbill": (0x3E5A88, 0x24365A, 0xB8C4CC, 0x80A0FF), "frillback": (0x3A50D8, 0x2A3CB0, 0xF08A2A, 0xFF9A3A),
               "oldshell": (0x7A8A50, 0x5A4A2E, 0xC4A860, 0xD8C870)}
FINS = {"crest": (0, 0, 6, 4), "ear": (0, 12, 4, 5), "arm": (16, 12, 3, 6), "arm_long": (24, 0, 3, 8)}


def fin_shape(kind, d, h):
    """gen_heritage_looks.fin_shape: lozenges, the same both ways round."""
    cells = set()
    for r in range(h):
        if kind == "crest":
            half = min(d // 2, 1 + r)
            cols = range(d // 2 - half, d // 2 + half)
        else:
            cols = range(1, d - 1) if (r == 0 or r == h - 1) and d > 2 else range(d)
        for c in cols:
            cells.add((c, r))
    return cells


def fins_for(key):
    membrane, spine, edge, edge_glow = (rgb(c) for c in FIN_COLOURS[key])
    vf, vg = Image.new("RGBA", (32, 32)), Image.new("RGBA", (32, 32))
    for kind, (u, v, d, h) in FINS.items():
        cells = fin_shape(kind, d, h)
        for face in (0, 1):
            ox, oy = u + face * d, v + d
            for c, r in cells:
                edge_px = any((c + dc, r + dr) not in cells for dc, dr in ((1, 0), (-1, 0), (0, -1), (0, 1))
                              if 0 <= c + dc < d and 0 <= r + dr < h) or r == 0
                vf.putpixel((ox + c, oy + r), edge if edge_px else spine if c % 2 == 0 else membrane)
                if edge_px:
                    vg.putpixel((ox + c, oy + r), edge_glow)
    return vf, vg


def main():
    os.makedirs(PARTS, exist_ok=True)
    os.makedirs(VARIANT, exist_ok=True)
    written = []
    for design in parse_table():
        key = design["key"]
        sheet, glow = paint_sheet(design)
        outs = [(sheet, os.path.join(PARTS, "sea_%s.png" % key))]
        if design["glow"] != "NONE":  # (a look whose parts never light has no glow twin)
            outs.append((glow, os.path.join(PARTS, "sea_%s_glow.png" % key)))
        for img, path in outs:
            img.save(path)
            written.append(path)
        s, gl = Image.new("RGBA", (64, 64)), Image.new("RGBA", (64, 64))
        MARKS[key](random.Random(sum(map(ord, key)) * 31), s, gl)
        eyes = Image.new("RGBA", (64, 64))
        for x, y in EYES:
            eyes.putpixel((x, y), rgb(EYE[key]))
        vf, vg = fins_for(key)
        for img, name in ((s, "_skin"), (gl, "_glow"), (eyes, "_eyes"), (vf, "_fins"), (vg, "_fins_glow")):
            path = os.path.join(VARIANT, key + name + ".png")
            img.save(path)
            written.append(path)
    print(len(written), "written under", os.path.relpath(TEX, ROOT))


if __name__ == "__main__":
    main()
