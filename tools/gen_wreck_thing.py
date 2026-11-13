"""Paints the icons of what the thing under the wreck leaves: the clinging coil (charm) and the deep tooth. Run from the repo root."""
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/item/"
PAL = {
    "c": (120, 86, 52), "C": (84, 58, 36),  # cord
    "p": (150, 72, 96), "P": (112, 46, 72), "d": (74, 28, 50),  # arm flesh
    "s": (226, 188, 196), "S": (190, 140, 156),  # suckers
    "t": (236, 230, 208), "T": (204, 194, 164), "u": (160, 148, 118), "r": (98, 44, 52),  # tooth, root
}
COIL = [
    "......cCc.......",
    ".....c...c......",
    ".....C...C......",
    "......c.c.......",
    ".......C........",
    "......dPd.......",
    ".....dPpPd......",
    "....dPpspPd.....",
    "....dPsSppPd....",
    "....dPppddpPd...",
    ".....dPd..dpPd..",
    "......d..dSpPd..",
    ".....dPddpspPd..",
    ".....dPpsppPd...",
    "......dPPPPd....",
    ".......dddd.....",
]
TOOTH = [
    "................",
    "..........rr....",
    ".........rrrr...",
    "........uTTrr...",
    ".......uTttTr...",
    "......uTtttTu...",
    ".....uTtttTu....",
    "....uTtttTu.....",
    "....uTttTu......",
    "...uTttTu.......",
    "...uTtTu........",
    "...uTtTu........",
    "...uTtu.........",
    "....uTu.........",
    "....uu..........",
    "................",
]
for name, rows in (("bauble/clinging_coil", COIL), ("deep_tooth", TOOTH)):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                im.putpixel((x, y), PAL[ch] + (255,))
    im.save(OUT + name + ".png")
