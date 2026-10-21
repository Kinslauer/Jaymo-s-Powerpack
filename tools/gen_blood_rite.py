"""The Blood Rite's icon (Kingdoms 0.7.97): a stoppered vial of dark blood, drawn fresh on vanilla-like ramps, 16x16.
Run from the project root:  python -I tools/gen_blood_rite.py
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms",
                   "textures", "item", "blood_rite.png")
PALETTE = {
    "O": (38, 20, 24, 255),     # outline
    "C": (150, 106, 62, 255),   # cork
    "c": (108, 74, 42, 255),
    "G": (208, 226, 236, 255),  # glass
    "g": (152, 174, 190, 255),
    "W": (255, 255, 255, 255),  # glint
    "R": (196, 34, 44, 255),    # blood
    "r": (142, 18, 30, 255),
    "d": (88, 8, 18, 255),
}
ROWS = [
    "................",
    "......OOOO......",
    "......OCcO......",
    "......OCcO......",
    ".....OOOOOO.....",
    ".....OGggGO.....",
    ".....OGgggO.....",
    ".....OGrrgO.....",
    ".....OWRrdO.....",
    ".....OGRrdO.....",
    ".....ORRrdO.....",
    ".....ORrrdO.....",
    ".....ORrddO.....",
    "......OddO......",
    ".......OO.......",
    "................",
]
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for y, row in enumerate(ROWS):
    for x, ch in enumerate(row):
        if ch in PALETTE:
            img.putpixel((x, y), PALETTE[ch])
img.save(OUT)
print("Blood Rite icon written to", os.path.normpath(OUT))
