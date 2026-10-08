"""Generates the Chronokinesis HUD sheet (clock faces and ability glyphs).

All original pixel art: clock faces are drawn procedurally, glyphs from hand-made 10x10 grids.
Run from the project root:  python tools/gen_chrono.py

Sheet layout (64x32, assets/jaymospowers/textures/gui/chrono_hud.png):
  (0,0)   18x18  clock face, idle
  (18,0)  18x18  clock face, active (brighter bezel, warm glow)
  (0,18)  10x10  glyph: Rewind     (10,18) Stasis
  (20,18) 10x10  glyph: Overclock  (30,18) Time Bloom
  (40,18) 10x10  glyph: Paradox Cut (the ascended ability)

Also the Stopped Clock item (16x16, textures/item/chrono_stopped_clock.png) and its item/model JSON.
"""
import math
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"
OUT = ASSETS / "textures" / "gui" / "chrono_hud.png"
CLOCK_OUT = ASSETS / "textures" / "item" / "chrono_stopped_clock.png"

GOLD_LIGHT = (255, 226, 140, 255)
GOLD = (242, 193, 78, 255)
GOLD_DARK = (168, 116, 36, 255)
FACE = (34, 26, 44, 225)
FACE_ACTIVE = (58, 40, 30, 235)
TICK = (205, 170, 105, 255)
TICK_MAJOR = (255, 238, 190, 255)

GLYPH_PALETTE = {
    ".": (0, 0, 0, 0),
    "#": (255, 236, 176, 255),  # cream gold
    "g": (242, 193, 78, 255),   # gold
    "d": (168, 116, 36, 255),   # dark gold
    "s": (255, 170, 60, 255),   # amber sand
    "l": (120, 200, 90, 255),   # leaf
    "L": (70, 150, 60, 255),    # leaf shadow
    "w": (255, 255, 255, 255),  # glint
    "c": (168, 232, 255, 255),  # pale cyan
    "C": (90, 170, 210, 255),   # cyan shadow
}

GLYPHS = [
    # Rewind: two back-pointing triangles
    [
        "..........",
        "....g...g.",
        "...gg..gg.",
        "..g#g.g#g.",
        ".g##gg##g.",
        ".d##dd##d.",
        "..d#d.d#d.",
        "...dd..dd.",
        "....d...d.",
        "..........",
    ],
    # Stasis: hourglass
    [
        ".gggggggg.",
        "..g....g..",
        "..gssssg..",
        "...gssg...",
        "....gg....",
        "....gg....",
        "...g.sg...",
        "..g.sssg..",
        "..dssssd..",
        ".dddddddd.",
    ],
    # Overclock: lightning bolt
    [
        ".....ggg..",
        "....g##g..",
        "...g##g...",
        "..g##ggg..",
        "..dgg##g..",
        "....g#g...",
        "...g#g....",
        "..g#d.....",
        "..gd......",
        "..........",
    ],
    # Time Bloom: sprout with a golden bud
    [
        "....ww....",
        "...wggw...",
        "....gd....",
        ".ll..d.ll.",
        "lllL.dLlll",
        ".LLL.dLLL.",
        ".....d....",
        "...dgggd..",
        "..dggggd..",
        "..........",
    ],
    # Paradox Cut: a crescent slash with its own pale echo behind it
    [
        "........#.",
        ".......#g.",
        "..c...#g..",
        ".c...#g...",
        "c...#gd...",
        "c..#gd....",
        ".c#gd.....",
        "..cd......",
        "...C......",
        "..........",
    ],
]

# The Stopped Clock: a pocket watch with a cracked crystal, both hands stuck at twelve, a faint cyan glint.
CLOCK_PALETTE = {
    ".": (0, 0, 0, 0),
    "o": (58, 40, 22, 255),     # outline
    "d": (150, 102, 36, 255),   # dark gold
    "g": (226, 178, 70, 255),   # gold
    "l": (255, 228, 150, 255),  # light gold
    "f": (214, 230, 236, 255),  # face
    "s": (170, 196, 210, 255),  # face shadow
    "k": (40, 34, 44, 255),     # hands
    "x": (120, 210, 240, 255),  # crack (cyan glint)
    "w": (255, 255, 255, 255),  # glint
}

CLOCK = [
    "......olo.......",
    "......ogo.......",
    ".....oolgoo.....",
    "...oogglgggoo...",
    "..ogglffffgddo..",
    "..oglffkfffsdo..",
    ".oglfffkffxfsdo.",
    ".oglffwkfxffsdo.",
    ".ogfffkkxffffdo.",
    ".ogffffxfffsfdo.",
    ".ogffffxffffsdo.",
    "..ogfsfxfffsdo..",
    "..odgsssssddgo..",
    "...oodddddooo...",
    ".....ooooo......",
    "................",
]

ITEM_JSON = """{
  "model": {
    "type": "minecraft:model",
    "model": "jaymospowers:item/chrono_stopped_clock"
  }
}
"""

MODEL_JSON = """{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "jaymospowers:item/chrono_stopped_clock"
  }
}
"""


def stopped_clock() -> Image.Image:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(CLOCK):
        for x, ch in enumerate(row.ljust(16, ".")[:16]):
            img.putpixel((x, y), CLOCK_PALETTE[ch])
    return img


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print("wrote", path)


def clock_face(active: bool) -> Image.Image:
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    cx = cy = 9.0
    for y in range(18):
        for x in range(18):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= 7.0:
                img.putpixel((x, y), FACE_ACTIVE if active else FACE)
            elif d <= 8.6:
                # bezel, lit from the top-left
                light = -(dx + dy) / 12.0
                if light > 0.25:
                    col = GOLD_LIGHT
                elif light < -0.35:
                    col = GOLD_DARK
                else:
                    col = GOLD
                if active:
                    col = tuple(min(255, int(c * 1.12 + 18)) for c in col[:3]) + (255,)
                img.putpixel((x, y), col)
    # hour marks
    for h in range(12):
        a = h / 12.0 * math.tau
        r = 5.9
        px = int(math.floor(cx + math.sin(a) * r))
        py = int(math.floor(cy - math.cos(a) * r))
        img.putpixel((px, py), TICK_MAJOR if h % 3 == 0 else TICK)
    return img


def glow(img: Image.Image) -> Image.Image:
    """A soft one-pixel warm halo just outside the bezel."""
    out = img.copy()
    for y in range(18):
        for x in range(18):
            if img.getpixel((x, y))[3] != 0:
                continue
            near = any(
                0 <= x + ox < 18 and 0 <= y + oy < 18 and img.getpixel((x + ox, y + oy))[3] == 255
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            if near:
                out.putpixel((x, y), (255, 214, 110, 110))
    return out


def glyph(rows) -> Image.Image:
    img = Image.new("RGBA", (10, 10), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row.ljust(10, ".")[:10]):
            img.putpixel((x, y), GLYPH_PALETTE[ch])
    return img


def main() -> None:
    sheet = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    sheet.paste(clock_face(False), (0, 0))
    sheet.paste(glow(clock_face(True)), (18, 0))
    for i, rows in enumerate(GLYPHS):
        sheet.paste(glyph(rows), (i * 10, 18))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print("wrote", OUT)

    CLOCK_OUT.parent.mkdir(parents=True, exist_ok=True)
    stopped_clock().save(CLOCK_OUT)
    print("wrote", CLOCK_OUT)
    write_text(ASSETS / "items" / "chrono_stopped_clock.json", ITEM_JSON)
    write_text(ASSETS / "models" / "item" / "chrono_stopped_clock.json", MODEL_JSON)


if __name__ == "__main__":
    main()
