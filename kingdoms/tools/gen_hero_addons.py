"""The hero add-on picks' art: the signal brazier (two textures, unlit and lit models, blockstate, item, loot table,
recipe) and the bundle of evidence a night crook drops (icon, item definition and model). Run from kingdoms/:
python -I tools/gen_hero_addons.py

Every face names a #variable (26.3 models: a raw sprite id renders purple). The brazier texture is a strip atlas: a face
uses the whole band it belongs to, so the bands are drawn to look right stretched."""
import json
import os
from PIL import Image

RES = "src/main/resources"
ASSETS = RES + "/assets/jaymoskingdoms"
DATA = RES + "/data/jaymoskingdoms"


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# bands (rows): stone pillar, iron bowl, coals, flame
STONE, IRON, COALS, FLAME = [0, 0, 16, 6], [0, 6, 16, 12], [0, 12, 16, 14], [0, 14, 16, 16]


def brazier_texture(lit):
    img = Image.new("RGB", (16, 16))
    px = img.load()
    brick = [(150, 150, 150), (131, 131, 131), (112, 112, 112)]
    mortar = (97, 97, 97)
    iron = [(168, 170, 176), (140, 142, 150), (108, 110, 120), (82, 84, 94)]
    for y in range(0, 6):  # stone bricks
        for x in range(16):
            row = y % 3
            offset = 0 if y < 3 else 4
            seam = row == 2 or (x + offset) % 8 == 7
            c = brick[1]
            if seam:
                c = mortar
            elif row == 0 or (x + offset) % 8 == 0:
                c = brick[0]
            elif (x * 7 + y * 3) % 11 == 0:
                c = brick[2]
            px[x, y] = c
    for y in range(6, 12):  # the iron bowl, riveted
        for x in range(16):
            row = y - 6
            c = iron[1]
            if row == 0:
                c = iron[0]
            elif row == 5:
                c = iron[3]
            elif x % 4 == 3:
                c = iron[2]
            if row in (1, 4) and x % 8 == 2:
                c = iron[3]
            px[x, y] = c
    for y in range(12, 14):  # coals
        for x in range(16):
            if lit:
                px[x, y] = (255, 140, 40) if (x + y) % 3 else (214, 84, 24)
            else:
                px[x, y] = (46, 42, 40) if (x + y) % 3 else (30, 28, 28)
    for x in range(16):  # the flame
        for y in (14, 15):
            if lit:
                px[x, y] = (255, 226, 120) if (x + y) % 2 else (255, 176, 60)
            else:
                px[x, y] = (46, 42, 40)
    img.save(ASSETS + "/textures/block/signal_brazier%s.png" % ("_lit" if lit else ""))


def box(lo, hi, band, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": lo, "to": hi, "faces": {f: {"uv": band, "texture": tex} for f in faces}}


def elements(lit):
    t = "#brazier"
    out = [
        box([4, 0, 4], [12, 3, 12], STONE, t),
        box([6, 3, 6], [10, 8, 10], STONE, t),
        box([2, 8, 2], [14, 9, 14], IRON, t),
        box([3, 9, 3], [13, 12, 13], IRON, t, ("north", "south", "east", "west", "down")),
        box([4, 11, 4], [12, 12, 12], COALS, t, ("up",)),
    ]
    if lit:
        out.append(box([5, 12, 5], [11, 16, 11], FLAME, t, ("north", "south", "east", "west", "up")))
    return out


def model(lit):
    tex = "jaymoskingdoms:block/signal_brazier" + ("_lit" if lit else "")
    return {"parent": "minecraft:block/block", "textures": {"particle": tex, "brazier": tex}, "elements": elements(lit)}


def evidence_icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    paper, light, dark, edge = (214, 196, 156, 255), (232, 218, 182, 255), (176, 156, 118, 255), (96, 76, 52, 255)
    twine, wax = (150, 112, 70, 255), (168, 48, 44, 255)
    for y in range(3, 14):  # a folded bundle of papers
        for x in range(3, 13):
            c = paper
            if y == 3 or x == 3:
                c = light
            elif y == 13 or x == 12:
                c = dark
            px[x, y] = c
    for x in range(3, 13):
        px[x, 2], px[x, 14] = edge, edge
    for y in range(2, 15):
        px[2, y], px[13, y] = edge, edge
    for y in range(3, 14):  # tied with twine, sealed in red wax
        px[7, y] = twine
        px[8, y] = twine
    for x in range(3, 13):
        px[x, 8] = twine
    for (x, y) in [(7, 7), (8, 7), (7, 9), (8, 9), (6, 8), (9, 8)]:
        px[x, y] = wax
    for y in range(5, 8):  # a line of writing
        for x in range(4, 7):
            if (x + y) % 2 == 0:
                px[x, y] = edge
    img.save(ASSETS + "/textures/item/crook_evidence.png")


def main():
    brazier_texture(False)
    brazier_texture(True)
    put(ASSETS + "/models/block/signal_brazier.json", model(False))
    put(ASSETS + "/models/block/signal_brazier_lit.json", model(True))
    put(ASSETS + "/blockstates/signal_brazier.json", {"variants": {
        "lit=false": {"model": "jaymoskingdoms:block/signal_brazier"},
        "lit=true": {"model": "jaymoskingdoms:block/signal_brazier_lit"}}})
    put(ASSETS + "/items/signal_brazier.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/signal_brazier"}})
    put(DATA + "/loot_table/blocks/signal_brazier.json", {
        "type": "minecraft:block", "random_sequence": "jaymoskingdoms:blocks/signal_brazier",
        "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
                   "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:signal_brazier"}]}]})
    put(DATA + "/recipe/signal_brazier.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["I I", "ICI", "BBB"],
        "key": {"I": "minecraft:iron_ingot", "C": "minecraft:coal", "B": "minecraft:stone_bricks"},
        "result": {"count": 1, "id": "jaymoskingdoms:signal_brazier"}})
    evidence_icon()
    put(ASSETS + "/models/item/crook_evidence.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/crook_evidence"}})
    put(ASSETS + "/items/crook_evidence.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/crook_evidence"}})


main()
