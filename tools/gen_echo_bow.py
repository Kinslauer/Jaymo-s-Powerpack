"""
The Echo Bow's art and files (jaymospowers:echo_bow, power/arsenal/EchoBow): four 16x16 icons (at rest and three
stages of the draw), their models, the item definition, the bow enchanting tag and the lang part. Our own drawing: a
pale blue stave with a white string, laid out the way every bow icon is (the arrow points up and to the left), so the
in-hand poses of a bow fit it.

Run from the project root: python3 -I tools/gen_echo_bow.py
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")

OUTLINE, DEEP, MID, LIGHT, SHINE = (28, 52, 92), (52, 98, 156), (96, 158, 214), (159, 216, 255), (232, 246, 255)
STRING, STRING_DIM = (232, 246, 255), (150, 190, 226)
SHAFT, HEAD = (196, 228, 250), (255, 255, 255)

# the in-hand poses of a bow (the same numbers the recurve bow's models carry)
DISPLAY = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}

LANG = {
    "item.jaymospowers.echo_bow": "Echo Bow",
    "item.jaymospowers.echo_bow.lore": "Whatever it looses is answered twice.",
}


def line(px, x0, y0, x1, y1, colour):
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        x = round(x0 + (x1 - x0) * i / steps)
        y = round(y0 + (y1 - y0) * i / steps)
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = colour + (255,)


def stave(px, bend):
    """The stave from its top tip (right) to its bottom tip (left), bellied to the upper left; a drawn bow bends more."""
    top, bottom = (14 - bend, 1), (1, 14 - bend)
    control = (1 - bend, 1 - bend)
    points = []
    for i in range(41):
        t = i / 40.0
        x = (1 - t) ** 2 * top[0] + 2 * (1 - t) * t * control[0] + t ** 2 * bottom[0]
        y = (1 - t) ** 2 * top[1] + 2 * (1 - t) * t * control[1] + t ** 2 * bottom[1]
        points.append((x, y))
    # a dark edge on the outside of the curve, light on the inside, two pixels thick
    for x, y in points:
        for dx, dy, colour in ((-1, -1, OUTLINE), (0, 0, MID), (1, 0, LIGHT), (0, 1, LIGHT)):
            xx, yy = round(x) + dx, round(y) + dy
            if 0 <= xx < 16 and 0 <= yy < 16 and (colour != OUTLINE or px[xx, yy][3] == 0):
                px[xx, yy] = colour + (255,)
    for x, y in points[::8]:
        px[min(15, max(0, round(x))), min(15, max(0, round(y)))] = DEEP + (255,)
    # the grip at the middle, and a glint
    gx, gy = points[20]
    px[round(gx), round(gy)] = SHINE + (255,)
    return top, bottom


def icon(pull):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = image.load()
    bend = 0 if pull < 2 else 1
    top, bottom = stave(px, bend)
    if pull == 0:
        line(px, top[0], top[1] + 1, bottom[0] + 1, bottom[1], STRING)
        return image
    # the string drawn back to the lower right, and the arrow lying on it
    back = {1: 9, 2: 10, 3: 12}[pull]
    line(px, top[0], top[1] + 1, back, back, STRING_DIM)
    line(px, bottom[0] + 1, bottom[1], back, back, STRING_DIM)
    tip = back - 9
    line(px, back, back, tip + 1, tip + 1, SHAFT)
    for x, y in ((tip, tip), (tip + 1, tip), (tip, tip + 1)):
        px[x, y] = HEAD + (255,)
    px[back, back] = STRING + (255,)
    return image


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def model(name):
    return {"type": "minecraft:model", "model": "jaymospowers:item/arsenal/" + name}


def main():
    names = ["echo_bow", "echo_bow_pulling_0", "echo_bow_pulling_1", "echo_bow_pulling_2"]
    os.makedirs(os.path.join(ASSETS, "textures", "item", "arsenal"), exist_ok=True)
    for pull, name in enumerate(names):
        icon(pull).save(os.path.join(ASSETS, "textures", "item", "arsenal", name + ".png"))
        write(os.path.join(ASSETS, "models", "item", "arsenal", name + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "jaymospowers:item/arsenal/" + name},
            "display": DISPLAY,
        })
    write(os.path.join(ASSETS, "items", "echo_bow.json"), {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:using_item",
        "on_false": model("echo_bow"),
        "on_true": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:use_duration",
            "scale": 0.05,
            "entries": [
                {"threshold": 0.65, "model": model("echo_bow_pulling_1")},
                {"threshold": 0.9, "model": model("echo_bow_pulling_2")},
            ],
            "fallback": model("echo_bow_pulling_0"),
        },
    }})
    write(os.path.join(ASSETS, "lang", "parts", "echo_bow.json"), LANG)
    tag = os.path.join(DATA, "minecraft", "tags", "item", "enchantable", "bow.json")
    values = json.load(open(tag))["values"] if os.path.exists(tag) else []
    if "jaymospowers:echo_bow" not in values:
        values.append("jaymospowers:echo_bow")
    write(tag, {"values": values})
    print("wrote the Echo Bow's files")


if __name__ == "__main__":
    main()
