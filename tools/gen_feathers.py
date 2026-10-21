"""Generates the Feathers HUD sheet (the stamina row above the hunger bar).

Original pixel art, drawn from a 9x9 grid like vanilla's hunger and heart icons: one feather on the diagonal (quill
bottom-left, tip top-right), light from the top-left, a dark outline as vanilla's HUD icons have.
Run from the project root:  python -I tools/gen_feathers.py

Sheet layout (99x9, assets/jaymospowers/textures/gui/feathers.png), each icon 9x9 at x = 9 * column:
  0 container (empty)   1 feather   2 half feather   3 golden   4 half golden   5 strain   6 half strain
  7 held back by armour   8 exhausted   9 half exhausted   10 container, flashing (just spent)
A half keeps the quill end (the left five columns), as vanilla's half shank keeps its bone.
Also writes a 4x preview to build/feathers-preview.png.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "gui" / "feathers.png"
PREVIEW = ROOT / "build" / "feathers-preview.png"

# o outline, d shade (lower-right of the vane), m body, l lit, h glint, q quill and shaft, Q quill shade
FEATHER = [
    "......oo.",
    ".....olqo",
    "....ohqmo",
    "...ohqmdo",
    "..ohqmdo.",
    ".olqmdo..",
    ".oqmdo...",
    "oqQoo....",
    "oo.......",
]

RAMPS = {
    #           outline          shade            body             lit              glint
    "blue":   ((22, 34, 66),   (40, 84, 160),   (70, 128, 214),  (140, 190, 244), (214, 236, 255)),
    "gold":   ((74, 46, 8),    (170, 112, 18),  (226, 170, 34),  (250, 214, 92),  (255, 246, 184)),
    "red":    ((62, 10, 10),   (140, 28, 28),   (200, 52, 48),   (240, 110, 92),  (255, 196, 180)),
    "iron":   ((36, 36, 40),   (92, 92, 98),    (136, 136, 142), (182, 182, 188), (222, 222, 226)),
    "pale":   ((40, 48, 62),   (88, 100, 118),  (120, 134, 154), (162, 174, 190), (202, 210, 220)),
}
QUILL = {"blue": ((236, 226, 196), (170, 156, 120)), "gold": ((255, 240, 200), (196, 150, 70)),
         "red": ((240, 214, 196), (170, 120, 104)), "iron": ((210, 210, 214), (140, 140, 146)),
         "pale": ((214, 214, 210), (150, 150, 146))}
CONTAINER = ((16, 16, 18), (52, 52, 58))
FLASH = ((240, 240, 255), (96, 104, 128))


def icon(ramp=None, half=False, container=None):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    for y, row in enumerate(FEATHER):
        for x, c in enumerate(row):
            if c == ".":
                continue
            if container is not None:
                rgb = container[0] if c == "o" else container[1]
            else:
                if half and x > 4:
                    continue
                o, d, m, l, h = RAMPS[ramp]
                q, Q = QUILL[ramp]
                rgb = {"o": o, "d": d, "m": m, "l": l, "h": h, "q": q, "Q": Q}[c]
            img.putpixel((x, y), rgb + (255,))
    return img


def main():
    icons = [
        icon(container=CONTAINER),
        icon("blue"), icon("blue", half=True),
        icon("gold"), icon("gold", half=True),
        icon("red"), icon("red", half=True),
        icon("iron"),
        icon("pale"), icon("pale", half=True),
        icon(container=FLASH),
    ]
    sheet = Image.new("RGBA", (9 * len(icons), 9), (0, 0, 0, 0))
    for i, im in enumerate(icons):
        sheet.paste(im, (9 * i, 0))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    big = Image.new("RGBA", (sheet.width * 6 + 8, 9 * 6 + 8), (120, 160, 90, 255))
    big.paste(sheet.resize((sheet.width * 6, 54), Image.NEAREST), (4, 4))
    big.save(PREVIEW)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
