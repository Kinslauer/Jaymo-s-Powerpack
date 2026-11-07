"""Paints the Bane's brand (Banes.BRAND, drawn by BaneBrand for the one player who sees it), from nothing: a 64x64 overlay
in the player skin layout, only the forehead painted (the face is x 8..15, y 8..15; the brow rows 8..10), a small hooked
rune that glows faint red, drawn with the eyes render type so it shows in the dark. Run from the repo root:
python3 kingdoms/tools/gen_brand.py"""
from pathlib import Path
from PIL import Image

OUT = Path("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/captain")
GLOW, EDGE = (255, 70, 60, 255), (150, 24, 30, 255)


def main():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # a hook and a bar: |^ over the brow, a tail down the bridge of the nose
    for x, y in [(11, 8), (12, 8), (10, 9), (13, 9), (11, 10), (12, 10), (12, 11)]:
        im.putpixel((x, y), GLOW)
    for x, y in [(10, 8), (13, 8), (11, 9), (12, 9)]:
        im.putpixel((x, y), EDGE)
    OUT.mkdir(parents=True, exist_ok=True)
    im.save(OUT / "brand.png")


if __name__ == "__main__":
    main()
