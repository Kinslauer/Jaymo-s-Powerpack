"""Generates the webline plank (web_bridge) texture, blockstate and model. Run from the project root."""
from PIL import Image, ImageDraw
import json, os, random

root = "src/main/resources/assets/jaymospowers"
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
json.dump({"variants": {f"height={h}": {"model": "jaymospowers:block/web_bridge"} for h in range(1, 17)}}, open(f"{root}/blockstates/web_bridge.json", "w"), indent=2)
face = lambda uv: {"uv": uv, "texture": "#web"}
model = {"parent": "minecraft:block/block",
         "textures": {"web": "jaymospowers:block/web_bridge", "particle": "jaymospowers:block/web_bridge"},
         "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
             "up": face([0, 0, 16, 16]), "down": {**face([0, 0, 16, 16]), "cullface": "down"},
             "north": face([0, 14, 16, 16]), "south": face([0, 14, 16, 16]),
             "west": face([0, 14, 16, 16]), "east": face([0, 14, 16, 16])}}]}
json.dump(model, open(f"{root}/models/block/web_bridge.json", "w"), indent=2)
