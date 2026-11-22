"""Writes the art of every fist and gauntlet weapon from one base: Gallant Gauntlets' iron gauntlet (by Joshieman,
CC BY-NC 4.0; art/gallant_gauntlets/CREDITS.md), reskinned in our colours. Andy (2026-11-22): "reskin a copy, and use
that base for all our gauntlet or fist weapons. except the hidden blade".

    python -I tools/gauntlet_assets.py

From the base (art/gallant_gauntlets/iron_gauntlet*.png) it writes, for the Fang Gauntlets (armory/Armory), boxing's
fist gear (boxing/BoxingGear: Hand Wraps, the cestus, the bracers) and the Glass Gauntlet's claws-out form
(power/arsenal/GlassGauntlet):
  - the inventory icon (textures/item/...), the base's 16x16 gauntlet recoloured: the hand in one material, the cuff
    in another, plus the piece's own marks (spikes, studs, straps, bandage turns);
  - how it looks worn (textures/entity/fang_gauntlets.png, textures/entity/boxing/<item>.png, 64x32, for
    client/armory/FangGauntletsClient): the base's sleeve (its 3D model is a 4x6x4 sleeve round the fist) with the
    base's own side and knuckle textures recoloured, and the piece's extras beside it.
The Glass Gauntlet's open-hand icon (tools/gen_glass_gauntlet.py) is a different shape (an open hand) and stays.
Nothing else in the base is changed; what was changed is listed in art/gallant_gauntlets/CREDITS.md.
"""
import json
import os

from PIL import Image

BASE = os.path.join("art", "gallant_gauntlets")
ASSETS = os.path.join("src", "main", "resources", "assets", "jaymospowers", "textures")

# ramps, darkest (the outline) to lightest: our existing gear colours (tools/boxing_assets.py, the bandage item,
# tools/gen_glass_gauntlet.py), with a step between the outline and the shade
IRON = [(52, 52, 58), (88, 90, 96), (120, 122, 128), (160, 162, 166), (226, 228, 230)]
STEEL = [(30, 34, 42), (58, 64, 76), (80, 88, 100), (112, 122, 136), (186, 196, 210)]
DARK_IRON = [(36, 36, 40), (62, 62, 68), (92, 94, 100), (128, 130, 136), (186, 188, 194)]
LEATHER = [(50, 30, 16), (84, 52, 28), (110, 70, 40), (142, 94, 54), (176, 124, 78)]
BANDAGE = [(107, 95, 74), (160, 150, 126), (201, 191, 166), (230, 223, 204), (247, 243, 232)]
GLASS = [(18, 14, 26), (30, 24, 44), (46, 36, 66), (104, 84, 150), (206, 194, 250)]
BONE = [(96, 88, 72), (160, 152, 134), (200, 196, 182), (236, 236, 228), (252, 252, 246)]

# each piece: (icon path, worn texture path or None, hand ramp, cuff ramp, the extra: spikes, studs, straps, bandage)
PIECES = [
    ("item/armory/fang_gauntlets", "entity/fang_gauntlets", DARK_IRON, LEATHER, ("spikes", BONE)),
    ("item/boxing/hand_wraps", "entity/boxing/hand_wraps", BANDAGE, BANDAGE, ("bandage", BANDAGE)),
    ("item/boxing/leather_cestus", "entity/boxing/leather_cestus", LEATHER, LEATHER, ("studs", LEATHER)),
    ("item/boxing/iron_cestus", "entity/boxing/iron_cestus", LEATHER, LEATHER, ("studs", IRON)),
    ("item/boxing/steel_cestus", "entity/boxing/steel_cestus", LEATHER, LEATHER, ("studs", STEEL)),
    ("item/boxing/leather_bracers", "entity/boxing/leather_bracers", LEATHER, LEATHER, ("straps", LEATHER)),
    ("item/boxing/iron_bracers", "entity/boxing/iron_bracers", LEATHER, IRON, ("straps", LEATHER)),
    ("item/boxing/steel_bracers", "entity/boxing/steel_bracers", LEATHER, STEEL, ("straps", LEATHER)),
    ("item/glass_gauntlet", None, GLASS, GLASS, ("spikes", GLASS)),
]

# where the base icon's knuckles are: the studs sit along them, the spikes run out from them up and to the right
KNUCKLES = [(10, 4), (11, 5), (12, 6), (13, 7)]
SPIKES = [[(10, 2), (11, 1), (12, 0)], [(12, 3), (13, 2), (14, 1)], [(14, 5), (15, 4)]]


def load(name):
    return Image.open(os.path.join(BASE, name)).convert("RGBA")


def step(value, cuts):
    """The ramp step for a grey value: how many of the cuts it is at or above."""
    return sum(1 for cut in cuts if value >= cut)


def is_cuff(x, y):
    """The base icon's cuff: its lower left, below the diagonal where the fist begins."""
    return x - y <= -4


def icon(hand, cuff, extra):
    base = load("iron_gauntlet.png")
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = base.getpixel((x, y))
            if a == 0:
                continue
            ramp = cuff if is_cuff(x, y) else hand
            # the base's greys: 37 outline, ~74-89 dark, ~107-135 body, ~143-178 light, 238 shine
            out.putpixel((x, y), ramp[step(r, (60, 100, 140, 200))] + (255,))
    kind, ramp = extra
    if kind == "studs":
        for i, (x, y) in enumerate(KNUCKLES):
            out.putpixel((x, y), ramp[4 if i % 2 == 0 else 3] + (255,))
    elif kind == "straps":
        # two straps round the cuff, across the diagonal
        for (x, y) in [(2, 9), (3, 10), (4, 11), (2, 12), (3, 13)]:
            if out.getpixel((x, y))[3]:
                out.putpixel((x, y), ramp[1] + (255,))
    elif kind == "bandage":
        # the turns of a bandage: a darker line every few pixels across the wound cloth, and the loose end
        for y in range(16):
            for x in range(16):
                p = out.getpixel((x, y))
                if p[3] and p[:3] != ramp[0] and (x + y) % 3 == 0:
                    out.putpixel((x, y), ramp[2] + (255,))
        for (x, y) in [(0, 13), (0, 14)]:
            out.putpixel((x, y), ramp[3] + (255,))
    elif kind == "spikes":
        for spike in SPIKES:
            for i, (x, y) in enumerate(spike):
                out.putpixel((x, y), ramp[1 if i == 0 else 4 if i == len(spike) - 1 else 3] + (255,))
    return out


def recolour(img, box, ramp, lo, hi):
    """The base's texture in box, its greys spread over ramp steps lo..hi."""
    crop = img.crop(box)
    values = [crop.getpixel((x, y))[0] for y in range(crop.height) for x in range(crop.width) if crop.getpixel((x, y))[3]]
    darkest, lightest = min(values), max(values)
    out = Image.new("RGBA", crop.size, (0, 0, 0, 0))
    for y in range(crop.height):
        for x in range(crop.width):
            v, _, _, a = crop.getpixel((x, y))
            if a:
                t = 0.0 if lightest == darkest else (v - darkest) / (lightest - darkest)
                out.putpixel((x, y), ramp[lo + round(t * (hi - lo))] + (255,))
    return out


def box(out, u, v, w, h, d, side, top):
    """A box's faces unfolded the vanilla way at (u, v): top and bottom of d rows, then the four sides of h rows."""
    for (x0, y0, fw, fh, tile) in [(u + d, v, w, d, top), (u + d + w, v, w, d, top),
                                   (u, v + d, d, h, side), (u + d, v + d, w, h, side),
                                   (u + d + w, v + d, d, h, side), (u + 2 * d + w, v + d, w, h, side)]:
        for y in range(fh):
            for x in range(fw):
                out.putpixel((x0 + x, y0 + y), tile.getpixel((x % tile.width, y % tile.height)))


def worn(hand, cuff, extra):
    """64x32, for FangGauntletsClient: the sleeve (4x6x4) at (0, 0), a cestus's knuckle band (4x1x4) at (0, 12), a
    bracer's guard (4x3x4) at (0, 18), the Fang Gauntlets' three spikes (1x7x1 each) at (32 + 8n, 0)."""
    sides, cap = load("iron_gauntlet_3d_2.png"), load("iron_gauntlet_3d_1.png")
    out = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    side = recolour(sides, (0, 0, 4, 6), hand, 1, 4)
    top = recolour(cap, (0, 0, 4, 4), hand, 2, 4)
    kind, ramp = extra
    if kind == "bandage":
        for y in range(side.height):
            for x in range(side.width):
                if (x + 2 * y) % 4 == 0:
                    side.putpixel((x, y), ramp[1] + (255,))
    box(out, 0, 0, 4, 6, 4, side, top)
    # the cuff's lower edge: the base's cuff, in its own material, round the wrist end of the sleeve
    for x in range(16):
        out.putpixel((x, 4), cuff[2] + (255,))
        out.putpixel((x, 5), cuff[3] + (255,))
    if kind == "studs":
        band = Image.new("RGBA", (2, 1))
        band.putpixel((0, 0), ramp[4] + (255,))
        band.putpixel((1, 0), ramp[2] + (255,))
        box(out, 0, 12, 4, 1, 4, band, band)
    guard = recolour(sides, (4, 0, 8, 3), cuff, 1, 4)
    if kind == "straps":
        for x in range(guard.width):
            guard.putpixel((x, 1), ramp[1] + (255,))
    box(out, 0, 18, 4, 3, 4, guard, guard)
    if kind == "spikes":
        for n in range(3):
            for y in range(8):
                for x in range(4):
                    out.putpixel((32 + 8 * n + x, y), (ramp[1] if y < 2 else ramp[4] if x == 1 else ramp[3]) + (255,))
    return out


def save(img, rel):
    path = os.path.join(ASSETS, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", path)


def held(name, ramp):
    """A piece held rather than worn (the Glass Gauntlet): the base's own 3D model in the hand, a sleeve round the fist,
    its side and knuckle textures recoloured (the item definition picks it in the hands: tools/gen_glass_gauntlet.py)."""
    save(recolour(load("iron_gauntlet_3d_2.png"), (0, 0, 8, 7), ramp, 1, 4), "item/" + name + "_3d_side")
    save(recolour(load("iron_gauntlet_3d_1.png"), (0, 0, 4, 8), ramp, 2, 4), "item/" + name + "_3d_cap")
    with open(os.path.join(BASE, "iron_gauntlet_3d.json"), encoding="utf-8") as f:
        model = json.load(f)
    model["textures"] = {"0": "jaymospowers:item/" + name + "_3d_cap", "1": "jaymospowers:item/" + name + "_3d_side",
                         "particle": "jaymospowers:item/" + name}
    # ours: three claws out past the knuckles (the base's sleeve ends at y 7), in the cap's light glass
    for x in (5.4, 6.7, 8.0):
        faces = {face: {"uv": [0, 0, 1, 4], "texture": "#0"} for face in ("north", "east", "south", "west", "up", "down")}
        model["elements"].append({"from": [x, 7, 7.6], "to": [x + 0.6, 11, 8.4], "faces": faces})
    path = os.path.join(ASSETS, "..", "models", "item", name + "_held.json")
    with open(path, "w", newline="\n") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    print("wrote", path)


def main():
    for icon_path, worn_path, hand, cuff, extra in PIECES:
        save(icon(hand, cuff, extra), icon_path)
        if worn_path:
            save(worn(hand, cuff, extra), worn_path)
    held("glass_gauntlet", GLASS)


if __name__ == "__main__":
    main()
