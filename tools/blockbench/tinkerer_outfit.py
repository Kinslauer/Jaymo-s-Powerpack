"""(Andy, 2026-10-29) The Mad Tinkerer's outfit: the Blockbench Workshop "sanguine outfit" (a wide-brim feathered hat, a long
coat with tails, bracers and boots; Bedrock armour style, one 128x128 box-UV texture), darkened, baked for Jaymo's
Kingdoms' loader (client/bb/BbModel) and drawn over the Tinkerer MCA villager by client/TinkererOutfit.

The recolour is Andy's "dark" pick from the three he was shown: every pixel's HSV value x0.6, except the gold and brass
(hue 0.08-0.17, saturation over 0.35, value over 0.45), which only lose a quarter (x0.75) so the trim still shines. It
covers the whole texture, and runs on the model's own embedded sheet each time, so the shade stays reproducible.

Writes, in kingdoms/src/main/resources/assets/jaymoskingdoms:
  bbmodels/tinkerer_sanguine.json, textures/entity/bb/tinkerer_sanguine.png

Run from the repo root: python3 -I tools/blockbench/tinkerer_outfit.py
"""
import base64, colorsys, io, json, os, sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
import bbmodel_bake

SRC = os.path.join(HERE, "..", "bbmodels", "sanguine-outfit.bbmodel")
ASSETS = "kingdoms/src/main/resources/assets/jaymoskingdoms"
NAME = "tinkerer_sanguine"
# the darkening: everything's value x0.6, the gold and brass only x0.75
VALUE, GOLD_VALUE = 0.6, 0.75
GOLD_HUE, GOLD_SAT, GOLD_BRIGHT = (0.08, 0.17), 0.35, 0.45


def darken(img):
    out = img.convert("RGBA")
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            gold = GOLD_HUE[0] < h < GOLD_HUE[1] and s > GOLD_SAT and v > GOLD_BRIGHT
            v *= GOLD_VALUE if gold else VALUE
            r, g, b = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(r * 255), round(g * 255), round(b * 255), a)
    return out


def main():
    with open(SRC, encoding="utf-8") as f:
        data = json.load(f)
    for tex in data["textures"]:
        sheet = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1])))
        buf = io.BytesIO()
        darken(sheet).save(buf, "PNG")
        tex["source"] = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    model, atlas, _ = bbmodel_bake.bake(data)
    os.makedirs(f"{ASSETS}/bbmodels", exist_ok=True)
    os.makedirs(f"{ASSETS}/textures/entity/bb", exist_ok=True)
    with open(f"{ASSETS}/bbmodels/{NAME}.json", "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, separators=(",", ":"))
    atlas.save(f"{ASSETS}/textures/entity/bb/{NAME}.png")
    print(NAME, len(model["bones"]), "bones", sum(len(b["quads"]) for b in model["bones"]), "quads", model["tex"])


if __name__ == "__main__":
    main()
