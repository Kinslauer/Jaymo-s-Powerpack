"""Generates the webline plank (web_bridge): its textures, blockstate and models. Run from the project root.

A plain web's plank is unseen in game (only the line is drawn), but keeps its white texture for particles. A symbiote's
plank shows: a mat of black corded rope (the suit) or of woven tendrils (Living Armor), as tall as the line where it
crosses the block (height 1-16), two sixteenths thick."""
from PIL import Image, ImageDraw
import json, random

root = "src/main/resources/assets/jaymospowers"

# the plain web
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
random.seed(7)
for y in (1, 4, 7, 10, 13):
    d.line([(0, y), (15, y)], fill=(236, 236, 240, 230))
for x in (2, 8, 13):
    d.line([(x, 0), (x, 15)], fill=(220, 220, 226, 200))
d.line([(0, 0), (15, 15)], fill=(245, 245, 250, 170))
d.line([(0, 15), (15, 0)], fill=(245, 245, 250, 170))
for _ in range(10):
    img.putpixel((random.randrange(16), random.randrange(16)), (255, 255, 255, 255))
img.save(f"{root}/textures/block/web_bridge.png")

# the suit's: thick black cords, woven over and under, a faint purple sheen along their tops, gaps between
DARK, MID, SHEEN = (11, 7, 16), (24, 15, 34), (46, 30, 66)
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for i in range(16):
    for j in range(16):
        # cords along both diagonals, two pixels wide
        a = (i + j) % 6
        b = (i - j) % 6
        on_a, on_b = a < 2, b < 2
        if not (on_a or on_b):
            continue
        # over and under: which cord is on top swaps every crossing
        top_a = ((i + j) // 6 + (i - j) // 6) % 2 == 0
        if on_a and (top_a or not on_b):
            color = SHEEN if a == 0 else MID
        else:
            color = SHEEN if b == 0 else MID
        if on_a and on_b and not top_a:
            color = DARK
        img.putpixel((i, j), (*color, 255))
random.seed(11)
for _ in range(7):
    # goo beading on the cords
    x, y = random.randrange(16), random.randrange(16)
    if img.getpixel((x, y))[3]:
        img.putpixel((x, y), (8, 5, 12, 255))
img.save(f"{root}/textures/block/web_bridge_rope.png")

# Living Armor's: tendrils, side by side and woven, in the tendrils' own dark flesh with purple veins
tendril = Image.open(f"{root}/textures/entity/web_symbiote_tendril.png").convert("RGBA")
img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
for i in range(16):
    for j in range(16):
        band = j % 4 if (i // 4) % 2 == 0 else i % 4
        if band == 3 and (i + j) % 3 == 0:
            continue  # small gaps between the strands
        px = tendril.getpixel(((i * 2 + 3) % 32, (j * 2 + 5) % 32))
        shade = 0.7 if band == 0 else 1.0 if band in (1, 2) else 0.55
        img.putpixel((i, j), (int(px[0] * shade), int(px[1] * shade), int(px[2] * shade), 255))
img.save(f"{root}/textures/block/web_bridge_tendril.png")

face = lambda uv, tex: {"uv": uv, "texture": tex}


def plank(texture, height):
    bottom = max(0, height - 2)
    side = [0, 16 - height, 16, 16 - bottom]
    return {"parent": "minecraft:block/block",
            "textures": {"mat": texture, "particle": texture},
            "elements": [{"from": [0, bottom, 0], "to": [16, height, 16], "faces": {
                "up": face([0, 0, 16, 16], "#mat"), "down": face([0, 0, 16, 16], "#mat"),
                "north": face(side, "#mat"), "south": face(side, "#mat"),
                "west": face(side, "#mat"), "east": face(side, "#mat")}}]}


json.dump(plank("jaymospowers:block/web_bridge", 2), open(f"{root}/models/block/web_bridge.json", "w"), indent=2)
variants = {}
for h in range(1, 17):
    variants[f"height={h},style=0"] = {"model": "jaymospowers:block/web_bridge"}
    for style, name in ((1, "rope"), (2, "tendril")):
        model = f"web_bridge_{name}_{h}"
        json.dump(plank(f"jaymospowers:block/web_bridge_{name}", h), open(f"{root}/models/block/{model}.json", "w"), indent=2)
        variants[f"height={h},style={style}"] = {"model": f"jaymospowers:block/{model}"}
json.dump({"variants": variants}, open(f"{root}/blockstates/web_bridge.json", "w"), indent=2)
