#!/usr/bin/env python3
"""Generate vanilla-style 16x16 cross-section textures for severed limbs, necks and waist halvings.

    python -I tools/gen_cut_faces.py

Follows docs/Vanilla-Style.md: discrete 4-7 step shade ramps, light from the top-left, darkest material shade at
shadowed edges (never pure black), no per-pixel noise, no soft gradients, no half-transparent pixels.

Outputs 16x16 PNGs to src/main/resources/assets/jaymospowers/textures/entity/wounds/:
  - cut_face_<kind>.png        (16x16 square cross-section for limb stumps, severed ends, necks and sockets)
  - cut_face_<kind>_torso.png  (16x8 waist cross-section at y=4..11 in a 16x16 PNG, 2:1 aspect for 8x4 torsos)
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "textures", "entity", "wounds")


def hx(s):
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def paint_grid(rows, pal):
    clean = [r.strip() for r in rows.strip("\n").split("\n")]
    assert len(clean) == 16 and all(len(r) == 16 for r in clean), [len(r) for r in clean]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(clean):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = hx(pal[ch])
    return im


# ---- Anatomy grids (flesh / bleeding creatures) -------------------------------------------------------------------
# Keys:
#   r = lit outer rind/dermis, e = mid outer rind, d = shadowed outer rind/fascia
#   1 = brightest lit meat, 2 = mid-lit meat, 3 = mid-dark meat, 4 = deep shadow meat
#   B = bright lit bone, b = mid bone, s = shadowed bone, m = marrow hollow
#   L, l, k = organ 1 (liver / primary lobe: lit, mid, dark)
#   G, g, c = organ 2 (stomach / gut coil: lit, mid, crease)

LIMB_FLESH = """
rrrrrrrrrrreeddd
r11112222233344d
r111222d2223344d
r11222BBBbb3334d
r1222BBbbbbb334d
r122BBbbmmbbs34d
r12dBbbmmmmbs34d
r222Bbbmmmmbs34d
r222bbmmmmbbss4d
e233bbmmbbbbss4d
d333sbbbbbbss44d
d3333ssbbsss344d
d333333dd333444d
d43333333344444d
d44433344444444d
dddddddddddddddd
"""

# 16x8 waist cross-section at y=4..11 (y=4 is front/belly, y=11 is back/spine)
TORSO_FLESH = """
................
................
................
................
rrrrrrrrrrrreedd
r11LLl222GGg334d
r1LLllk2GGcgg34d
r2LllkkBBbbcg34d
e22kk3Bbbmmbs44d
d33333bbmmbbss4d
d344333sbbsss44d
dddddddddddddddd
................
................
................
................
"""

# ---- Bleeding palettes (discrete ramps, high step contrast, ivory/chitin/pith bone ring) --------------------------

BLEEDING_PALETTES = {
    "flesh": {
        "r": "8e2a32", "e": "661820", "d": "420b12",
        "1": "cc424b", "2": "a82631", "3": "7d1620", "4": "590e16",
        "B": "f2ede0", "b": "d8ceb8", "s": "a89b82", "m": "4a0c14",
        "L": "8f2842", "l": "6b182e", "k": "470d1d",
        "G": "db8c88", "g": "b56262", "c": "7d383b",
    },
    "rotten": {
        "r": "6e6942", "e": "4a4529", "d": "262112",
        "1": "8a824c", "2": "6b6336", "3": "4d4624", "4": "363018",
        "B": "ded6b4", "b": "bfb48c", "s": "8f8462", "m": "2b2412",
        "L": "594238", "l": "402d26", "k": "291b17",
        "G": "998a5c", "g": "756840", "c": "4f4528",
    },
    "ender": {
        "r": "7a2e8a", "e": "501a5e", "d": "260930",
        "1": "b84ad4", "2": "8f2ca8", "3": "64197a", "4": "410e52",
        "B": "e8e6c8", "b": "c9c49f", "s": "96906e", "m": "360b47",
        "L": "6c2896", "l": "4d1770", "k": "2f0b47",
        "G": "d478eb", "g": "a64cc2", "c": "6e2687",
    },
    "dark": {
        "r": "2c3b42", "e": "1d262b", "d": "101317",
        "1": "3d5661", "2": "293b45", "3": "1c2830", "4": "141b21",
        "B": "cad4d1", "b": "9aa8a6", "s": "6b7878", "m": "0d1217",
        "L": "1f6e75", "l": "144b52", "k": "0c2c33",
        "G": "4c6b73", "g": "344c54", "c": "1e2e36",
    },
    "bug": {
        "r": "5c4528", "e": "3d2d19", "d": "24190d",
        "1": "ebdb52", "2": "c9b332", "3": "9c841e", "4": "6e5a12",
        "B": "f5f0ce", "b": "d6cca2", "s": "a69b72", "m": "524014",
        "L": "b87c28", "l": "8f5c18", "k": "5e3a0d",
        "G": "dce874", "g": "b2bf4b", "c": "7d8729",
    },
    "sap": {
        "r": "3b6924", "e": "284a18", "d": "19300e",
        "1": "7ac445", "2": "569e2b", "3": "3c751b", "4": "285211",
        "B": "e6e8c3", "b": "c3c799", "s": "8f9468", "m": "294a14",
        "L": "428036", "l": "2d5c24", "k": "1b3b15",
        "G": "a8db5c", "g": "7eb03a", "c": "527820",
    },
    "gold": {
        "r": "9e741e", "e": "6e5013", "d": "422e0a",
        "1": "fae26b", "2": "e0b638", "3": "b5881d", "4": "805c11",
        "B": "fffbe8", "b": "e6dcc0", "s": "b5aa8c", "m": "6e4c0e",
        "L": "d98b1e", "l": "ad6811", "k": "784409",
        "G": "fff39e", "g": "dec659", "c": "a68b29",
    },
}

# ---- Solid & elemental cross-sections (skeletons, golems, Stoneborn, slimes, magma, snow, wood, spirits) ----------

LIMB_BONE = """
bbbbbbbbbbbbssdd
bBBBBBbbbbbbssdd
bBBBBbbbbbbbssdd
bBBBbbssssbbbsdd
bBBbbssmmssbbsdd
bBBbsmmmmmmbssdd
bBbbsmmmmmmbssdd
bbbbsmmmmmmbssdd
bbbbsmmmmmmbssdd
sbbbsmmmmmmbsddd
ssbbbssmmssbsddd
ssbbbbssssbbsddd
ssbbbbbbbbbbssdd
dssbbbbbbbbsssdd
ddssssssssssssdd
dddddddddddddddd
"""

TORSO_BONE = """
................
................
................
................
bbbbbbbbbbbbssdd
bBBBBbbbbBBBBsdd
bBBbsmmbbmmsbsdd
bbbbsmmBBmmsbsdd
sbbbssbBBbssbsdd
dsbbbbbbbbbbssdd
ddssssssssssssdd
dddddddddddddddd
................
................
................
................
"""

BONE_PAL = {"B": "f5f1e4", "b": "ded6c1", "s": "b5aa92", "m": "6e6554", "d": "4a4337"}
WITHER_BONE_PAL = {"B": "787373", "b": "595454", "s": "3d3939", "m": "1f1c1c", "d": "141212"}

LIMB_STONE = """
4444444333332210
4555444333222210
4555543322443210
4554433224443210
4443332244332210
3333222443322110
3344422333222110
3455432222233210
3444332112344210
2333221122343210
2222211223332210
2233222233222110
1233322222211110
1122222111111100
1111111111100000
0000000000000000
"""

TORSO_STONE = """
................
................
................
................
4444444433332210
4555443322443210
4544332244543210
3332223344332210
3443211233221110
2332211222211100
1222111111110000
0000000000000000
................
................
................
................
"""

STONE_PAL = {"0": "212121", "1": "494949", "2": "5a5a5a", "3": "787777", "4": "95918d", "5": "b3b1af"}
OBSIDIAN_PAL = {"0": "120c1c", "1": "241936", "2": "382852", "3": "4f3b6e", "4": "6b548f", "5": "8e77b5"}

LIMB_METAL = """
4444444444443310
4665555555544310
4652222222214310
4521144441124310
4521455544124310
4521451134124310
4521451034124310
4521443334124310
4521444443124310
4521133331124310
4522111111224310
3442222222243310
3444444444433210
2333333333322110
1222222222111100
0000000000000000
"""

TORSO_METAL = """
................
................
................
................
4444444444443310
4655222222224310
4521454114531310
4521433114331310
3421133113312310
2344222222243210
1233333333322100
0000000000000000
................
................
................
................
"""

IRON_PAL = {"0": "181818", "1": "444444", "2": "6b6b6b", "3": "969696", "4": "bebebe", "5": "d8d8d8", "6": "ffffff"}
COPPER_PAL = {"0": "421c11", "1": "5f2c1c", "2": "803921", "3": "9c4e31", "4": "c96b53", "5": "e3826c", "6": "fdd4cb"}

LIMB_GOO = """
3333333333332210
3444444444333210
3455544433332210
3455443322222210
3444332211122210
3443322100012210
3433221004001210
3333221000001210
3332221100011210
2332222111112210
2333222222222110
2233332222221110
1223333322211100
1122222221111000
1111111111100000
0000000000000000
"""

TORSO_GOO = """
................
................
................
................
3333333333332210
3455443332222210
3443321001233210
3333221040123210
2332221100122110
2233322222221100
1122222211111000
0000000000000000
................
................
................
................
"""

GOO_PAL = {"0": "275221", "1": "3d7835", "2": "59a14e", "3": "76be6d", "4": "9ce08e", "5": "d4f7c8"}

LIMB_MAGMA = """
1111111111110000
1222222221111000
1233333222211100
1234444333221100
1234555443321100
1234566544322100
1234566554322100
1123455544321100
1123344443321100
0122334433221100
0112233332211000
0112222222111000
0011122211110000
0001111111000000
0000011100000000
0000000000000000
"""

TORSO_MAGMA = """
................
................
................
................
1111111111110000
1233443322332100
1234565433454210
1234565433443210
0123443322332100
0112222211221100
0011111100110000
0000000000000000
................
................
................
................
"""

MAGMA_PAL = {"0": "21120d", "1": "421d12", "2": "7a2914", "3": "b84714", "4": "eb7515", "5": "fcb826", "6": "fff394"}

LIMB_SNOW = """
4444444444443321
4555554444433321
4555544433344321
4554443333444321
4444333223343321
4443332222333221
3443322112233221
3333322112232211
3344332222332211
2344333223322110
2333333333222110
2223333222211110
1222222221111100
1112221111110000
1111111110000000
0000000000000000
"""

TORSO_SNOW = """
................
................
................
................
4444444444443321
4555443334443321
4544332112343221
3443322112233211
2333332223322110
2223333222211100
1112221111100000
0000000000000000
................
................
................
................
"""

SNOW_PAL = {"0": "6b8aa6", "1": "8fb0cc", "2": "b4d2eb", "3": "d5e8f7", "4": "edf6fc", "5": "ffffff"}

LIMB_WOOD = """
1111111111110000
1444444444432100
1455555554432100
1453333334432100
1453444423432100
1453422223432100
1453420023432100
1453420023432100
1453422223432100
1443333323432100
1344444443332100
0233333333221100
0122222222211000
0111111111100000
0000000000000000
0000000000000000
"""

TORSO_WOOD = """
................
................
................
................
1111111111110000
1455555555443100
1453344433443100
1453420023432100
1343332233332100
0234444444322100
0112222222111000
0000000000000000
................
................
................
................
"""

WOOD_PAL = {"0": "20180a", "1": "372910", "2": "594319", "3": "755821", "4": "9c7a32", "5": "b89458"}

LIMB_SPIRIT = """
4444444444332210
4555444433332210
4554433223332210
4544322554332210
4443325544432210
3433255400432110
3332254000032110
3332254000032110
3322244000432110
2322234444322110
2222333443321100
2223333222221100
1222222221111000
1112221111100000
1111111100000000
0000000000000000
"""

TORSO_SPIRIT = """
................
................
................
................
4444444444332210
4554332223332210
4433225543202210
3332254043202110
2322234432221100
2223333332211000
1112222111100000
0000000000000000
................
................
................
................
"""

SPIRIT_PAL = {"0": "1c222e", "1": "2e384a", "2": "46556e", "3": "687b96", "4": "94a8c2", "5": "cad8e8"}

SOLID_KINDS = {
    "bone": (LIMB_BONE, TORSO_BONE, BONE_PAL),
    "wither_bone": (LIMB_BONE, TORSO_BONE, WITHER_BONE_PAL),
    "stone": (LIMB_STONE, TORSO_STONE, STONE_PAL),
    "obsidian": (LIMB_STONE, TORSO_STONE, OBSIDIAN_PAL),
    "iron": (LIMB_METAL, TORSO_METAL, IRON_PAL),
    "copper": (LIMB_METAL, TORSO_METAL, COPPER_PAL),
    "goo": (LIMB_GOO, TORSO_GOO, GOO_PAL),
    "magma": (LIMB_MAGMA, TORSO_MAGMA, MAGMA_PAL),
    "snow": (LIMB_SNOW, TORSO_SNOW, SNOW_PAL),
    "wood": (LIMB_WOOD, TORSO_WOOD, WOOD_PAL),
    "spirit": (LIMB_SPIRIT, TORSO_SPIRIT, SPIRIT_PAL),
}


def save(im, name):
    os.makedirs(OUT, exist_ok=True)
    im.save(os.path.join(OUT, name + ".png"))


def main():
    count = 0
    for kind, pal in BLEEDING_PALETTES.items():
        save(paint_grid(LIMB_FLESH, pal), f"cut_face_{kind}")
        save(paint_grid(TORSO_FLESH, pal), f"cut_face_{kind}_torso")
        count += 2
    for kind, (limb_grid, torso_grid, pal) in SOLID_KINDS.items():
        save(paint_grid(limb_grid, pal), f"cut_face_{kind}")
        save(paint_grid(torso_grid, pal), f"cut_face_{kind}_torso")
        count += 2
    print(f"Wrote {count} cut-face textures to {OUT}")


if __name__ == "__main__":
    main()
