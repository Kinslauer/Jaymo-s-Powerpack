"""Art, models and lang for the ghostly crossbow (GhostlyCrossbow), drawn here pixel by pixel; nothing is copied.

    python3 tools/left_out_assets.py        (from kingdoms/)

Six 16x16 sprites (standby, three draw stages, loaded with a bolt, loaded with a rocket), their models, the item
definition (the crossbow's own: charge type, then the draw), and the lang part left_out_misc.json.
"""
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "jaymoskingdoms")
NS = "jaymoskingdoms"

STOCK = (150, 172, 196, 215)
PROD = (205, 228, 244, 225)
PROD_DARK = (120, 146, 176, 225)
STRING = (240, 248, 255, 235)
BOLT = (190, 255, 245, 245)
BOLT_TIP = (255, 255, 255, 255)
ROCKET = (255, 150, 120, 245)


def line(a, b):
    """Pixels on the straight (diagonal or axis) line a to b."""
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0))
    return [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)]


def sprite(pull, loaded=None):
    """pull 0..3 draws the string back toward the stock; loaded is None, 'bolt' or 'rocket'."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def put(p, colour):
        if 0 <= p[0] < 16 and 0 <= p[1] < 16:
            img.putpixel(p, colour)

    # the stock runs from the lower left up to the prod
    for p in line((2, 14), (9, 7)):
        put(p, STOCK)
    put((3, 14), PROD_DARK)
    put((2, 13), PROD_DARK)
    # the prod: a bow across the stock's end, its tips thrown forward
    for p in line((6, 4), (12, 10)):
        put(p, PROD)
    put((7, 3), PROD_DARK)
    put((13, 9), PROD_DARK)
    put((8, 7), PROD_DARK)
    # the string from tip to tip, pulled back toward the stock as it is drawn
    back = pull
    mid = (10 - back, 6 + back)
    for p in line((7, 3), mid) + line(mid, (13, 9)):
        put(p, STRING)
    if loaded == "bolt":
        for p in line((5, 11), (11, 5)):
            put(p, BOLT)
        put((12, 4), BOLT_TIP)
    elif loaded == "rocket":
        for p in line((5, 11), (11, 5)):
            put(p, ROCKET)
        put((12, 4), BOLT_TIP)
    return img


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


DISPLAY = {
    "thirdperson_righthand": {"rotation": [-90, 0, -60], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 30], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [-90, 0, -55], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-90, 0, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}


def main():
    sprites = {"standby": sprite(0), "pulling_0": sprite(1), "pulling_1": sprite(2), "pulling_2": sprite(3),
               "arrow": sprite(0, "bolt"), "firework": sprite(0, "rocket")}
    for name, img in sprites.items():
        path = os.path.join(ASSETS, "textures", "item", "ghostly_crossbow_" + name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/ghostly_crossbow_" + name}}
        if name == "standby":
            model["display"] = DISPLAY
            write(os.path.join(ASSETS, "models", "item", "ghostly_crossbow.json"), model)
        else:
            model = {"parent": NS + ":item/ghostly_crossbow", "textures": {"layer0": NS + ":item/ghostly_crossbow_" + name}}
            write(os.path.join(ASSETS, "models", "item", "ghostly_crossbow_" + name + ".json"), model)

    def model(n):
        return {"type": "minecraft:model", "model": NS + ":item/ghostly_crossbow_" + n}

    write(os.path.join(ASSETS, "items", "ghostly_crossbow.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:charge_type",
        "cases": [{"when": "arrow", "model": model("arrow")}, {"when": "rocket", "model": model("firework")}],
        "fallback": {
            "type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": {"type": "minecraft:model", "model": NS + ":item/ghostly_crossbow"},
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:crossbow/pull",
                        "entries": [{"threshold": 0.58, "model": model("pulling_1")}, {"threshold": 1.0, "model": model("pulling_2")}],
                        "fallback": model("pulling_0")}}}})
    write(os.path.join(ASSETS, "lang", "parts", "left_out_misc.json"), {
        "item.jaymoskingdoms.ghostly_crossbow": "Ghostly Crossbow",
    })


if __name__ == "__main__":
    main()
