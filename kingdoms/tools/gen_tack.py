"""Draws Jaymo's Equestrian's tack (stage 2, 0.5.40), all original and procedural:

- the 3D tack's pieces (assets/jaymoskingdoms/tack/tack.json: boxes on the bones of MocHorseModel, Mo' Creatures' horse,
  read by the client's TackLayer) and their textures: textures/entity/tack/tack.png (leather, iron, brass) and
  tack_blanket.png (greyscale, tinted by the blanket's dye). Groups: war_saddle (deep seat, high cantle, long flaps,
  heavy stirrups), racing_saddle (small and flat, short stirrups), bridle (black leather, brass, a browband),
  bridle_reins (shown while ridden), shoes (iron rings at the hooves), blanket. (The plain headstall and reins any
  saddle comes with are Mo' Creatures'.)
- chain and plate barding for vanilla's horse armour model (our horses wear tools/gen_moc_horses.py's overlays instead) (textures/entity/equipment/horse_body/*.png, the horse's 64x64
  box layout: body, neck, head, muzzle and the tops of the legs).
- the items' 16x16 sprites.

Seeded, so every run draws the same pixels. Run from kingdoms/: python3 -I tools/gen_tack.py"""
import json
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "src/main/resources/assets/jaymoskingdoms"))
TEX = os.path.join(RES, "textures")

LEATHER_DARK = (74, 46, 26)
LEATHER = (122, 75, 42)
LEATHER_LIGHT = (156, 107, 62)
BLACK_LEATHER = (42, 34, 32)
IRON = (154, 158, 163)
BRASS = (201, 163, 59)
WAR_RED = (142, 42, 42)


def faces(u, v, w, h, d):
    """A box's faces in its texture (box UV): name -> (x, y, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "a": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "b": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def shade(c, f):
    return tuple(max(0, min(255, int(round(x * f)))) for x in c[:3]) + (255,)


# ---- the 3D tack -------------------------------------------------------------------------------------------------------
# (group, bone, pos, size, material, grow, rot) -- positions in the bone's own space (MocHorseModel's, 1/16 block): the
# saddles sit on its saddle bone (they move back with it when the horse rears), their stirrups hang from its stirrup
# bones (and swing with them), the bridle is on its head, the bridle's reins on its rein bones, the shoes on its hooves
L_HOOF, R_HOOF, LF_HOOF, RF_HOOF = "leg1_c", "leg2_c", "leg3_c", "leg4_c"
PIECES = [
    # the war saddle: a deep seat, a high cantle behind, a pommel in front, long flaps, a red saddle cloth edge, stirrups
    ("war_saddle", "saddle", (-5.5, 0.0, -4.0), (11, 1, 10), LEATHER_DARK, 0.0, None),
    ("war_saddle", "saddle", (-4.5, -5.0, 4.5), (9, 5, 2), LEATHER_DARK, 0.0, None),
    ("war_saddle", "saddle", (-3.0, -3.0, -5.0), (6, 3, 2), LEATHER_DARK, 0.0, None),
    ("war_saddle", "saddle", (5.0, 0.5, -3.0), (1, 8, 8), LEATHER, 0.0, None),
    ("war_saddle", "saddle", (-6.0, 0.5, -3.0), (1, 8, 8), LEATHER, 0.0, None),
    ("war_saddle", "saddle", (-6.0, -0.5, -6.0), (12, 1, 2), WAR_RED, 0.1, None),
    ("war_saddle", "saddle", (-5.5, 10.6, -0.5), (11, 1, 2), LEATHER_DARK, 0.0, None),
    ("war_saddle", "stirrup_left", (0.5, 0.0, -0.5), (1, 8, 1), LEATHER_DARK, 0.0, None),
    ("war_saddle", "stirrup_right", (-1.5, 0.0, -0.5), (1, 8, 1), LEATHER_DARK, 0.0, None),
    ("war_saddle", "stirrup_left", (0.5, 8.0, -2.0), (1, 1, 4), IRON, 0.0, None),
    ("war_saddle", "stirrup_right", (-1.5, 8.0, -2.0), (1, 1, 4), IRON, 0.0, None),
    # the racing saddle: small and flat, a low cantle, short flaps, stirrups ridden short
    ("racing_saddle", "saddle", (-4.5, 0.4, -2.0), (9, 1, 7), LEATHER_LIGHT, 0.0, None),
    ("racing_saddle", "saddle", (-3.0, -0.6, 4.0), (6, 1, 1), LEATHER_LIGHT, 0.0, None),
    ("racing_saddle", "saddle", (5.0, 0.8, -1.5), (1, 4, 5), LEATHER, 0.0, None),
    ("racing_saddle", "saddle", (-6.0, 0.8, -1.5), (1, 4, 5), LEATHER, 0.0, None),
    ("racing_saddle", "saddle", (-5.4, 10.6, 0.0), (11, 1, 1), LEATHER, 0.0, None),
    ("racing_saddle", "stirrup_left", (0.5, 0.0, -0.5), (1, 4, 1), LEATHER, 0.0, None),
    ("racing_saddle", "stirrup_right", (-1.5, 0.0, -0.5), (1, 4, 1), LEATHER, 0.0, None),
    ("racing_saddle", "stirrup_left", (0.5, 4.0, -1.5), (1, 1, 3), IRON, 0.0, None),
    ("racing_saddle", "stirrup_right", (-1.5, 4.0, -1.5), (1, 1, 3), IRON, 0.0, None),
    # the bridle: black leather with brass, a noseband, a band behind the eyes, a browband under the ears, the bit rings
    ("bridle", "head", (-2.0, -10.0, -4.0), (4, 5, 1), BLACK_LEATHER, 0.35, None),
    ("bridle", "head", (-2.5, -10.0, 2.0), (5, 5, 1), BLACK_LEATHER, 0.35, None),
    ("bridle", "head", (-3.0, -10.6, 3.0), (6, 1, 1), BRASS, 0.2, None),
    ("bridle", "head", (2.1, -8.0, -4.0), (1, 2, 2), BRASS, 0.05, None),
    ("bridle", "head", (-3.1, -8.0, -4.0), (1, 2, 2), BRASS, 0.05, None),
    ("bridle_reins", "rein_left", (2.8, -6.0, -6.0), (0, 3, 16), BLACK_LEATHER, 0.0, None),
    ("bridle_reins", "rein_right", (-2.8, -6.0, -6.0), (0, 3, 16), BLACK_LEATHER, 0.0, None),
    # horseshoes: an iron band round the bottom of each hoof
    ("shoes", L_HOOF, (-2.5, 7.1, -2.0), (4, 1, 4), IRON, 0.2, None),
    ("shoes", R_HOOF, (-1.5, 7.1, -2.0), (4, 1, 4), IRON, 0.2, None),
    ("shoes", LF_HOOF, (-2.4, 7.1, -2.1), (4, 1, 4), IRON, 0.2, None),
    ("shoes", RF_HOOF, (-1.6, 7.1, -2.1), (4, 1, 4), IRON, 0.2, None),
]
# the blanket: a pad under the saddle, down the flanks (greyscale, for the dye), with a darker border
BLANKET = [("blanket", "body", (-5.0, -8.0, -12.0), (10, 7, 12), 0.3, None)]


def pack(pieces, width):
    """Shelf-packs each box's footprint (2(w+d) by d+h) into a texture {width} wide; returns the uv of each and the height."""
    x, y, row, out = 0, 0, 0, []
    for size in pieces:
        w, h, d = (max(1, int(round(s))) if s else 0 for s in size)
        fw, fh = 2 * (w + d), d + h
        fw, fh = max(fw, 1), max(fh, 1)
        if x + fw > width:
            x, y, row = 0, y + row, 0
        out.append((x, y))
        x += fw
        row = max(row, fh)
    return out, y + row


def paint_box(img, rng, uv, size, color, border=None):
    px = img.load()
    w, h, d = (int(round(s)) for s in size)
    for name, (fx, fy, fw, fh) in faces(uv[0], uv[1], w, h, d).items():
        for i in range(fw):
            for j in range(fh):
                f = 0.92 + rng.random() * 0.12
                if name in ("bottom",):
                    f *= 0.75
                edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
                c = border if (border and edge) else color
                if edge and fw > 2 and fh > 2:
                    f *= 0.82
                px[fx + i, fy + j] = shade(c, f)


def tack(rng):
    pieces = []
    uvs, height = pack([p[3] for p in PIECES], 128)
    tex = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    assert height <= 128, height
    for (group, bone, pos, size, mat, grow, rot), uv in zip(PIECES, uvs):
        # reins: a plane, so only its strip is drawn (a 0-wide box's footprint is its two sides)
        paint_box(tex, rng, uv, tuple(max(s, 0) for s in size), mat, BRASS if group == "bridle" and mat == BLACK_LEATHER else None)
        pieces.append(piece(group, bone, pos, size, uv, grow, rot, "main"))
    blanket = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    buvs, bh = pack([b[3] for b in BLANKET], 64)
    for (group, bone, pos, size, grow, rot), uv in zip(BLANKET, buvs):
        paint_box(blanket, rng, uv, size, (232, 232, 232), (150, 150, 150))
        # a woven stripe a little in from the border
        px = blanket.load()
        for name, (fx, fy, fw, fh) in faces(uv[0], uv[1], *size).items():
            if name in ("a", "b") and fh > 4:
                for i in range(fw):
                    px[fx + i, fy + fh - 3] = shade((190, 190, 190), 1.0)
        pieces.append(piece(group, bone, pos, size, uv, grow, rot, "blanket"))
    assert bh <= 32
    os.makedirs(os.path.join(TEX, "entity", "tack"), exist_ok=True)
    tex.save(os.path.join(TEX, "entity", "tack", "tack.png"))
    blanket.save(os.path.join(TEX, "entity", "tack", "tack_blanket.png"))
    os.makedirs(os.path.join(RES, "tack"), exist_ok=True)
    with open(os.path.join(RES, "tack", "tack.json"), "w") as f:
        json.dump({"texture_size": [128, 128], "blanket_texture_size": [64, 32], "pieces": pieces}, f, indent=1)


def piece(group, bone, pos, size, uv, grow, rot, layer):
    p = {"group": group, "bone": bone, "layer": layer, "pos": list(pos), "size": list(size), "uv": list(uv)}
    if grow:
        p["grow"] = grow
    if rot:
        p["rot"] = list(rot)
    return p


# ---- barding ------------------------------------------------------------------------------------------------------------
# the horse model's boxes (64x64): body, neck, head, muzzle, the legs (only their tops get barding)
HORSE = {"body": (0, 32, 10, 10, 22), "neck": (0, 35, 4, 12, 7), "head": (0, 13, 6, 5, 7), "muzzle": (0, 25, 4, 5, 5)}
LEGS = (48, 21, 4, 11, 4)


def barding(name, rng, pixel):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for part, (u, v, w, h, d) in HORSE.items():
        for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
            if part == "body" and face == "bottom":
                continue
            for i in range(fw):
                for j in range(fh):
                    px[fx + i, fy + j] = pixel(part, face, i, j, fw, fh, rng)
    u, v, w, h, d = LEGS
    for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        if face in ("top", "bottom"):
            continue
        for i in range(fw):
            for j in range(min(fh, 5)):
                px[fx + i, fy + j] = pixel("leg", face, i, j, fw, 5, rng)
    img.save(os.path.join(TEX, "entity", "equipment", "horse_body", name + ".png"))


def chain(part, face, i, j, fw, fh, rng):
    # rows of little rings, offset every other row
    ring = (i + (j // 2) % 2) % 2 == 0
    f = (1.05 if ring else 0.62) * (0.92 + rng.random() * 0.12)
    if j == 0 or j == fh - 1:
        return shade(LEATHER_DARK, 1.0)
    return shade((150, 154, 160), f)


def plate(part, face, i, j, fw, fh, rng):
    base = (176, 180, 186)
    f = 0.95 + rng.random() * 0.06
    if j == 0 or i == 0:
        f *= 1.18
    if j == fh - 1 or i == fw - 1:
        f *= 0.7
    # overlapping lames across the body and neck
    if part in ("body", "neck") and face in ("a", "b", "top") and j % 4 == 3:
        f *= 0.72
    # rivets
    if (i % 5 == 2) and (j % 4 == 1) and part != "head":
        return shade(BRASS, 1.0)
    # the crest of the chanfron down the face
    if part in ("head", "muzzle") and face == "top" and i == fw // 2:
        return shade(WAR_RED, 1.0)
    return shade(base, f)


# ---- items ---------------------------------------------------------------------------------------------------------------

def sprite(name, rows, palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch] + (255,) if len(palette[ch]) == 3 else palette[ch]
    os.makedirs(os.path.join(TEX, "item"), exist_ok=True)
    img.save(os.path.join(TEX, "item", name + ".png"))


SADDLE_PAL = {"d": LEATHER_DARK, "l": LEATHER, "h": LEATHER_LIGHT, "i": IRON, "r": WAR_RED, "k": (40, 26, 16)}
ITEMS = {
    "war_saddle": ([
        "................",
        "..dd............",
        "..ddd......dd...",
        "..dhhd....dhd...",
        "...dhhddddhhd...",
        "...dllhhhhlld...",
        "..rrrrrrrrrrrr..",
        "..rdlllllllldr..",
        "...dlllllllld...",
        "...dllldllld....",
        "....dlld.dl.....",
        ".....dd..k......",
        ".........k......",
        "........iii.....",
        "................",
        "................"], SADDLE_PAL),
    "racing_saddle": ([
        "................",
        "................",
        "................",
        "................",
        "..d.........d...",
        "..dhhhhhhhhhd...",
        "...dhhhhhhhd....",
        "....dlllllld....",
        "....dllllld.....",
        ".....dl..d......",
        "......k.........",
        "......i.........",
        ".....iii........",
        "................",
        "................",
        "................"], SADDLE_PAL),
    "bridle": ([
        "................",
        "......kkkk......",
        ".....k....k.....",
        "....y......y....",
        "....k......k....",
        "....k......k....",
        "....kkkkkkkk....",
        "....k......k....",
        "....k......k....",
        "....yk....ky....",
        ".....kkkkkk.....",
        "......k..k......",
        ".....k....k.....",
        "....k......k....",
        "...k........k...",
        "................"], {"k": BLACK_LEATHER, "y": BRASS}),
    "horse_blanket": ([
        "................",
        "................",
        "...gwwwwwwwwg...",
        "..gwwwwwwwwwwg..",
        "..gwwwwwwwwwwg..",
        "..gwwwwwwwwwwg..",
        "..gwsssssssswg..",
        "..gwwwwwwwwwwg..",
        "..gwwwwwwwwwwg..",
        "..gwwwwwwwwwwg..",
        "..gwsssssssswg..",
        "..gwwwwwwwwwwg..",
        "...gggggggggg...",
        "................",
        "................",
        "................"], {"w": (236, 236, 236), "s": (196, 196, 196), "g": (150, 150, 150)}),
    "chain_barding": ([
        "................",
        "........cc......",
        ".......cmmc.....",
        "......cmcmc.....",
        ".....cmcmcmc....",
        "..ccccmcmcmc....",
        ".cmcmcmcmcmcc...",
        ".cmcmcmcmcmcmc..",
        ".cmcmcmcmcmcmc..",
        ".cddddddddddc...",
        ".cmcmcmcmcmc....",
        "..cmc.....cmc...",
        "..cc.......cc...",
        "................",
        "................",
        "................"], {"c": (120, 124, 130), "m": (176, 180, 186), "d": LEATHER_DARK}),
    "plate_barding": ([
        "................",
        "........pp......",
        ".......pwwp.....",
        "......pwrwp.....",
        ".....pwwrwwp....",
        "..pppwwwwwwp....",
        ".pwwwwwwwwwwp...",
        ".pwwwywwwywwwp..",
        ".pssssssssssssp.",
        ".pwwwwwwwwwwwp..",
        ".pwwwywwwywwp...",
        "..pwp.....pwp...",
        "..pp.......pp...",
        "................",
        "................",
        "................"], {"p": (110, 114, 120), "w": (190, 194, 200), "s": (140, 144, 150), "y": BRASS, "r": WAR_RED}),
    "horseshoes": ([
        "................",
        "................",
        "...iiii.........",
        "..iddddi........",
        ".id....di.......",
        ".id....di.......",
        ".id..iiii.......",
        ".id.iddddi......",
        "..dida...di.....",
        "...id....di.....",
        "...id....di.....",
        "...id....di.....",
        "....d.....d.....",
        "................",
        "................",
        "................"], {"i": (190, 194, 200), "d": (100, 104, 110), "a": (60, 60, 64)}),
    "hoof_pick": ([
        "................",
        "...........ii...",
        "..........i.....",
        ".........ii.....",
        "........ii......",
        ".......hh.......",
        "......hhh.......",
        ".....hhh........",
        "....hhh.........",
        "...hhh..........",
        "..hhh...........",
        "..hh............",
        "................",
        "................",
        "................",
        "................"], {"i": IRON, "h": LEATHER}),
    "oats": ([
        "................",
        ".......y........",
        "......yYy..y....",
        ".......y..yYy...",
        "...y...g...y....",
        "..yYy..g...g....",
        "...y...g..g.....",
        "....g..g.g......",
        ".....g.gg.......",
        "......ggg.......",
        ".......g........",
        ".......g........",
        ".......g........",
        "......g.........",
        "................",
        "................"], {"y": (214, 190, 120), "Y": (236, 214, 150), "g": (150, 170, 80)}),
}


def main():
    rng = random.Random(538)
    tack(rng)
    os.makedirs(os.path.join(TEX, "entity", "equipment", "horse_body"), exist_ok=True)
    barding("chain_barding", random.Random(1), chain)
    barding("plate_barding", random.Random(2), plate)
    for name, (rows, pal) in ITEMS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        sprite(name, rows, pal)
    print("drew the tack into", RES)


if __name__ == "__main__":
    main()
