"""(0.7.632) Skill bar icons for a vampire's moves (FormSkills.NIGHT_MOVES): night_dash (a red streak),
night_pound (a figure driving down, a burst under it), night_gaze (two red eyes, beams out of them). 16x16, our own.
Run from the repo root: python3 kingdoms/tools/gen_night_icons.py"""
import math, os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/jaymoskingdoms/textures/spell")


def frame():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], fill=(34, 10, 14, 255), outline=(140, 20, 30, 255))
    return im, d


im, d = frame()
for i, x in enumerate(range(3, 11)):
    d.line([x, 12 - i // 2, x + 3, 12 - i // 2], fill=(150 + i * 12, 20, 30, 255))
d.polygon([(10, 4), (13, 7), (10, 10)], fill=(230, 40, 50, 255))
im.save(os.path.join(OUT, "night_dash.png"))

im, d = frame()
d.line([8, 2, 8, 8], fill=(230, 220, 220, 255))
d.polygon([(6, 7), (10, 7), (8, 10)], fill=(230, 220, 220, 255))
for a in range(0, 360, 40):
    r = math.radians(a)
    d.line([8, 12, 8 + math.cos(r) * 6, 12 + math.sin(r) * 2.5], fill=(200, 30, 40, 255))
d.line([2, 13, 13, 13], fill=(90, 40, 40, 255))
im.save(os.path.join(OUT, "night_pound.png"))

im, d = frame()
for ex in (4, 9):
    d.ellipse([ex, 3, ex + 3, 6], fill=(255, 60, 60, 255), outline=(120, 0, 10, 255))
d.line([5, 6, 2, 14], fill=(255, 40, 50, 255))
d.line([10, 6, 13, 14], fill=(255, 40, 50, 255))
d.point([5, 4], fill=(255, 255, 255, 255))
d.point([10, 4], fill=(255, 255, 255, 255))
im.save(os.path.join(OUT, "night_gaze.png"))
print("wrote night_dash, night_pound, night_gaze")

# (0.7.634) night_feed: two fangs over a drop of blood
im, d = frame()
d.polygon([(4, 3), (7, 3), (5, 9)], fill=(240, 236, 230, 255))
d.polygon([(9, 3), (12, 3), (11, 9)], fill=(240, 236, 230, 255))
d.ellipse([6, 10, 10, 14], fill=(190, 10, 20, 255))
d.polygon([(8, 8), (6, 11), (10, 11)], fill=(190, 10, 20, 255))
im.save(os.path.join(OUT, "night_feed.png"))
print("wrote night_feed")
