"""(0.7.624) Skill bar icons for Powers' takedowns: move_takedown (a fist driving down, a burst under it) and
move_blink (a violet swirl with a figure's shadow). 16x16, drawn here, our own."""
from PIL import Image, ImageDraw
import math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/jaymoskingdoms/textures/spell")


def frame(bg, rim):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], fill=bg, outline=rim)
    return im, d


def takedown():
    im, d = frame((52, 30, 26, 255), (120, 72, 40, 255))
    # the burst on the ground
    for a in range(0, 360, 45):
        r = math.radians(a)
        d.line([8, 12, 8 + math.cos(r) * 5, 12 + math.sin(r) * 3], fill=(240, 190, 80, 255))
    d.line([2, 13, 13, 13], fill=(150, 110, 70, 255))
    # the fist, driving down
    d.rectangle([5, 3, 10, 8], fill=(226, 180, 140, 255), outline=(120, 80, 60, 255))
    for x in (6, 8):
        d.line([x, 8, x, 9], fill=(120, 80, 60, 255))
    d.rectangle([6, 1, 9, 2], fill=(90, 60, 120, 255))
    d.line([3, 2, 3, 6], fill=(255, 255, 255, 160))
    d.line([12, 2, 12, 6], fill=(255, 255, 255, 160))
    return im


def blink():
    im, d = frame((24, 18, 44, 255), (110, 80, 180, 255))
    for i in range(40):
        t = i / 40.0
        a = t * math.pi * 3.0
        r = 1.0 + t * 5.5
        x, y = 8 + math.cos(a) * r, 8 + math.sin(a) * r
        c = (int(150 + 100 * t), int(100 + 60 * t), 255, 255)
        d.point([round(x), round(y)], fill=c)
    # a figure's shadow at its heart
    d.rectangle([7, 6, 8, 7], fill=(10, 8, 16, 255))
    d.rectangle([6, 8, 9, 11], fill=(10, 8, 16, 255))
    d.point([8, 3], fill=(255, 255, 255, 255))
    return im


os.makedirs(OUT, exist_ok=True)
takedown().save(os.path.join(OUT, "move_takedown.png"))
blink().save(os.path.join(OUT, "move_blink.png"))
