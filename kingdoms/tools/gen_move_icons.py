"""(0.7.625) Skill bar icons for Powers' takedowns: move_takedown (a fist driving down, a burst under it) and
move_blink (a violet swirl with a figure's shadow). (0.7.626) Web's and the symbiote's bare-handed moves. 16x16, drawn
here, our own."""
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


WEB_BG, WEB_RIM = (30, 34, 46, 255), (170, 170, 190, 255)
GOO_BG, GOO_RIM = (14, 12, 16, 255), (90, 90, 100, 255)
WHITE, GREY, RED = (235, 235, 240, 255), (130, 130, 140, 255), (200, 40, 40, 255)


def finish():
    im, d = frame(WEB_BG, WEB_RIM)
    d.line([3, 12, 12, 12], fill=GREY)
    d.rectangle([4, 10, 11, 11], fill=(120, 110, 100, 255))
    d.line([8, 2, 8, 8], fill=WHITE)
    d.polygon([(6, 7), (10, 7), (8, 10)], fill=WHITE)
    return im


def counter():
    im, d = frame(WEB_BG, WEB_RIM)
    for x, y in ((4, 4), (7, 3), (10, 4)):
        d.line([x, y, x + 1, y + 2, x, y + 3], fill=(255, 230, 90, 255))
    d.arc([3, 6, 13, 14], 200, 340, fill=WHITE)
    d.polygon([(12, 8), (14, 11), (11, 11)], fill=WHITE)
    return im


def fists():
    im, d = frame(WEB_BG, WEB_RIM)
    d.rectangle([5, 5, 10, 10], fill=(226, 180, 140, 255), outline=(120, 80, 60, 255))
    for i in range(3, 14, 3):
        d.line([i, 3, 16 - i, 13], fill=(240, 240, 245, 200))
    return im


def arm():
    im, d = frame(GOO_BG, GOO_RIM)
    d.polygon([(3, 13), (6, 10), (12, 2), (13, 3), (7, 11), (4, 14)], fill=(40, 40, 48, 255), outline=WHITE)
    return im


def grab():
    im, d = frame(GOO_BG, GOO_RIM)
    for x in (4, 7, 10):
        d.line([x, 3, x + 1, 9], fill=GREY)
    d.rectangle([4, 9, 12, 12], fill=(40, 40, 48, 255), outline=GREY)
    d.ellipse([7, 4, 10, 7], outline=RED)
    return im


def tendril():
    im, d = frame(GOO_BG, GOO_RIM)
    pts = [(2 + i, 12 - int(4 * math.sin(i / 2.0))) for i in range(12)]
    d.line(pts, fill=WHITE, width=1)
    d.point([13, 5], fill=WHITE)
    return im


def spikes():
    im, d = frame(GOO_BG, GOO_RIM)
    for a in range(0, 360, 40):
        r = math.radians(a)
        d.line([8, 8, 8 + math.cos(r) * 6.5, 8 + math.sin(r) * 6.5], fill=WHITE)
    d.ellipse([6, 6, 10, 10], fill=(40, 40, 48, 255))
    return im


def consume():
    im, d = frame(GOO_BG, GOO_RIM)
    d.pieslice([2, 3, 14, 13], 30, 330, fill=(40, 40, 48, 255), outline=GREY)
    for x in range(5, 12, 2):
        d.point([x, 5], fill=WHITE)
        d.point([x, 11], fill=WHITE)
    return im


def shroud():
    im, d = frame(GOO_BG, GOO_RIM)
    for y in range(3, 14, 2):
        d.line([3, y, 12, y], fill=(80, 80, 92, 140))
    d.rectangle([7, 5, 8, 6], fill=(200, 200, 210, 120))
    d.rectangle([6, 7, 9, 11], fill=(200, 200, 210, 90))
    return im


os.makedirs(OUT, exist_ok=True)
for name, draw in (("finish", finish), ("counter", counter), ("fists", fists), ("arm", arm), ("grab", grab), ("tendril", tendril),
                   ("spikes", spikes), ("consume", consume), ("shroud", shroud)):
    draw().save(os.path.join(OUT, "move_" + name + ".png"))
takedown().save(os.path.join(OUT, "move_takedown.png"))
blink().save(os.path.join(OUT, "move_blink.png"))
