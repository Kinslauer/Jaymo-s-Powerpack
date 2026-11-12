"""Paints what a captain wears for a limb he came back without (CaptainWays.LIMBS, drawn by CaptainMarks): the box
textures for his hook (a leather cuff, an iron hook) and his peg leg (a leather strap, an oak peg, an iron cap), and the
illager copies of his face marks (an illager's head is 8x10x8, so the face rows sit two lower than a player's).
Run from the repo root: python3 kingdoms/tools/gen_captain_limbs.py"""
import random
from pathlib import Path
from PIL import Image

OUT = Path("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/captain")
MARKS = ["burned", "eye_patch", "iron_jaw", "stitched", "pale_eye", "claws"]


def fill(im, box, tones, r):
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        for y in range(y0, y1):
            im.putpixel((x, y), r.choice(tones))


def limbs():
    r = random.Random(11)
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    leather = [(92, 58, 34, 255), (104, 66, 38, 255), (82, 50, 30, 255)]
    iron = [(150, 154, 160, 255), (132, 136, 142, 255), (170, 174, 180, 255)]
    dark_iron = [(90, 92, 98, 255), (78, 80, 86, 255)]
    oak = [(150, 118, 72, 255), (138, 108, 64, 255), (124, 96, 58, 255)]
    fill(im, (0, 0, 16, 8), leather, r)  # the hook's cuff
    for x in range(0, 16, 3):
        im.putpixel((x, 5), (180, 184, 190, 255))  # its rivets
    for x in range(0, 16):
        im.putpixel((x, 7), (60, 38, 22, 255))  # its stitched hem
    fill(im, (0, 8, 14, 12), iron, r)  # the hook
    fill(im, (0, 12, 16, 20), leather, r)  # the peg's strap
    for x in range(0, 16):
        im.putpixel((x, 15), (60, 38, 22, 255))
        im.putpixel((x, 16), (140, 120, 60, 255) if x % 4 == 1 else (60, 38, 22, 255))  # a buckle, a band
    fill(im, (16, 0, 24, 11), oak, r)  # the peg
    for y in range(2, 11, 3):
        for x in range(16, 24):
            if r.random() < 0.5:
                im.putpixel((x, y), (108, 84, 50, 255))  # grain
    fill(im, (24, 0, 32, 3), dark_iron, r)  # the peg's cap
    im.save(OUT / "limbs.png")


def player_row(r):
    """The player-skin head row an illager's head row r takes its paint from (eyes 12 -> 14, mouth 14 -> 16)."""
    if r < 10:
        return r
    if r < 12:
        return 9 if r == 10 else None
    return r - 2


def illager(name):
    src = Image.open(OUT / f"mark_{name}.png").convert("RGBA")
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for x in range(0, 32):
        for y in range(0, 18):
            if x >= 24 and y < 8:
                continue  # an illager's nose is drawn there
            p = player_row(y) if y >= 8 else y
            if p is None or p >= 16:
                continue
            im.putpixel((x, y), src.getpixel((x, p)))
    im.save(OUT / f"mark_{name}_illager.png")


if __name__ == "__main__":
    limbs()
    for m in MARKS:
        illager(m)
