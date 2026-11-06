"""Paints the marks a captain carries back from how he nearly died (CaptainWays.MARKS, drawn by CaptainMarks), from nothing:
64x64 overlays in the player skin layout, only the head painted. The face is x 8..15, y 8..15 (x 8 is his right side);
eyes on row 12 (x 9-10 and 13-14), mouth on row 14. Run from the repo root: python3 kingdoms/tools/gen_scars.py"""
import random
from pathlib import Path
from PIL import Image

OUT = Path("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/entity/captain")
SCAR, SCAR_DARK, THREAD = (150, 52, 52, 255), (98, 28, 30, 255), (40, 30, 22, 255)


def canvas():
    return Image.new("RGBA", (64, 64), (0, 0, 0, 0))


def burned():
    im, r = canvas(), random.Random(7)
    tones = [(170, 60, 50, 255), (196, 92, 80, 255), (120, 40, 34, 255), (214, 130, 112, 255), (70, 30, 26, 255)]
    # his left half of the face, and on round the left side of his head
    for x in range(12, 16):
        for y in range(9, 16):
            if r.random() < 0.85 - (15 - x) * 0.12:
                im.putpixel((x, y), r.choice(tones))
    for x in range(16, 20):
        for y in range(10, 16):
            if r.random() < 0.7 - (x - 16) * 0.15:
                im.putpixel((x, y), r.choice(tones))
    im.putpixel((13, 12), (230, 220, 200, 255))  # the eye, half closed and pale in the burn
    return im


def eye_patch():
    im = canvas()
    black, strap = (22, 18, 16, 255), (58, 40, 26, 255)
    for x, y in [(9, 11), (10, 11), (9, 12), (10, 12), (9, 13), (10, 13), (8, 12)]:
        im.putpixel((x, y), black)
    for x in range(11, 16):
        im.putpixel((x, 10 if x < 13 else 9), strap)
    for x in range(0, 8):
        im.putpixel((x, 12 if x < 4 else 11), strap)  # his right side, down to the patch
    for x in range(16, 24):
        im.putpixel((x, 9), strap)  # his left side
    for x in range(24, 32):
        im.putpixel((x, 10), strap)  # round the back
    return im


def iron_jaw():
    im = canvas()
    iron, dark, light = (120, 124, 130, 255), (70, 72, 78, 255), (170, 174, 180, 255)
    for x in range(9, 15):
        for y in (14, 15):
            im.putpixel((x, y), light if y == 14 and x in (10, 13) else iron)
    for x in (9, 14):
        im.putpixel((x, 15), dark)  # rivets
    for x in range(4, 8):
        im.putpixel((x, 15), iron)
    for x in range(16, 20):
        im.putpixel((x, 15), iron)
    im.putpixel((4, 15), dark)
    im.putpixel((19, 15), dark)
    return im


def stitched():
    im = canvas()
    # a long cut down his left cheek, sewn shut
    for y in range(9, 16):
        im.putpixel((13 if y < 12 else 14, y), SCAR)
    for y in (10, 12, 14):
        x = 13 if y < 12 else 14
        im.putpixel((x - 1, y), THREAD)
        im.putpixel((x + 1, y), THREAD)
    # and one at the corner of his mouth
    im.putpixel((10, 14), SCAR_DARK)
    im.putpixel((9, 15), SCAR_DARK)
    return im


def pale_eye():
    im = canvas()
    for x in (13, 14):
        im.putpixel((x, 12), (226, 228, 222, 255))
    im.putpixel((14, 12), (200, 206, 210, 255))
    for y in (10, 11, 13, 14):
        im.putpixel((14 if y < 12 else 13, y), SCAR)  # the scar through it
    return im


def claws():
    im = canvas()
    # three rakes across his right cheek
    for start in (8, 10, 12):
        for i in range(5):
            x, y = start + i // 2, 10 + i
            if 8 <= x < 16 and y < 16:
                im.putpixel((x, y), SCAR if i % 2 == 0 else SCAR_DARK)
    for y in range(11, 15):
        im.putpixel((6 + (y - 11) // 2, y), SCAR)  # round onto the side of his head
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, paint in [("burned", burned), ("eye_patch", eye_patch), ("iron_jaw", iron_jaw), ("stitched", stitched),
                        ("pale_eye", pale_eye), ("claws", claws)]:
        paint().save(OUT / f"mark_{name}.png")
        print("wrote", OUT / f"mark_{name}.png")


if __name__ == "__main__":
    main()
