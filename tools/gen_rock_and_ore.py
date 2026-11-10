"""Generates the rock-and-ore art for Andy's TFC Hardrock picks (ground/Panning): the prospector's pick head (in stone,
for its cast, and copper, as tools/weapon_parts_assets.py draws it, so the part list there stays the source), the
prospector's pick and the wooden pan items, the support beam and sluice models (over vanilla's own textures, by
reference), their blockstates, and item JSON. Our own pixels, drawn here.
Run from the repo root:  python -I tools/gen_rock_and_ore.py
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import weapon_parts_assets as wpa  # noqa: E402

ASSETS = wpa.ASSETS


def write(rel, obj):
    wpa.write(os.path.join(ASSETS, rel), obj)


def grid(rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    return img


WOOD = {"k": (58, 38, 20), "w": (120, 82, 46), "l": (156, 112, 66)}
COPPER = dict(zip("#xo", wpa.DRAWN_COLOURS["copper"]))

PICK = [
    "................",
    "................",
    "....###.........",
    "....#o######....",
    "....#ooooxxx##..",
    "....#xx#kkxxxx#.",
    "....####kl####..",
    ".......kwk......",
    "......kwk.......",
    ".....kwk........",
    "....kwk.........",
    "...kwk..........",
    "..kwk...........",
    ".kwk............",
    ".kk.............",
    "................"]

PAN = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..kkkkkkkkkkkk..",
    ".klllllllllllllk",
    ".kwgGgggGggGgwk.",
    "..kwggGgggggwk..",
    "...kwwwwwwwwk...",
    "....kkkkkkkk....",
    "................",
    "................",
    "................",
    "................",
    "................"]
PAN_PAL = dict(WOOD, g=(124, 118, 112), G=(214, 170, 60))


def head():
    lang = {}
    part = "prospector_pick_head"
    heads = wpa.sprites("drawn:" + part, None)
    cases = []
    for metal in ("stone", "copper"):
        path = wpa.OUT + part + "_" + metal
        heads[metal].save(os.path.join(ASSETS, "textures", path + ".png"))
        write(os.path.join("models", path + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
        cases.append({"when": metal, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
    write(os.path.join("items", part + ".json"), {"model": wpa.hot({
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": cases[0]["model"]})})


def item(name, img):
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    img.save(os.path.join(ASSETS, "textures", "item", name + ".png"))
    write(os.path.join("models", "item", name + ".json"),
          {"parent": "minecraft:item/handheld" if name != "pan" else "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/" + name}})
    write(os.path.join("items", name + ".json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:item/" + name}})


def face(uv, tex, cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, tex_side, tex_end, cull=True):
    x0, y0, z0 = frm
    x1, y1, z1 = to
    return {"from": frm, "to": to, "faces": {
        "north": face([16 - x1, 16 - y1, 16 - x0, 16 - y0], tex_side),
        "south": face([x0, 16 - y1, x1, 16 - y0], tex_side),
        "west": face([z0, 16 - y1, z1, 16 - y0], tex_side),
        "east": face([16 - z1, 16 - y1, 16 - z0, 16 - y0], tex_side),
        "up": face([x0, z0, x1, z1], tex_end, "up" if cull and y1 == 16 else None),
        "down": face([x0, 16 - z1, x1, 16 - z0], tex_end, "down" if cull and y0 == 0 else None)}}


def blocks():
    write("models/block/support_beam.json", {
        "textures": {"side": "minecraft:block/stripped_spruce_log", "end": "minecraft:block/stripped_spruce_log_top",
                     "particle": "minecraft:block/stripped_spruce_log"},
        "elements": [box([4, 0, 4], [12, 16, 12], "#side", "#end")]})
    write("blockstates/support_beam.json", {"variants": {
        "axis=y": {"model": "jaymospowers:block/support_beam"},
        "axis=z": {"model": "jaymospowers:block/support_beam", "x": 90},
        "axis=x": {"model": "jaymospowers:block/support_beam", "x": 90, "y": 90}}})
    write("items/support_beam.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:block/support_beam"}})

    # a plank trough with riffle bars across its floor, gravel in it when loaded
    walls = [box([0, 0, 0], [16, 1, 16], "#planks", "#planks", False),
             box([0, 1, 0], [1, 6, 16], "#planks", "#planks", False),
             box([15, 1, 0], [16, 6, 16], "#planks", "#planks", False),
             box([1, 1, 3], [15, 2, 4], "#log", "#log", False),
             box([1, 1, 8], [15, 2, 9], "#log", "#log", False),
             box([1, 1, 13], [15, 2, 14], "#log", "#log", False)]
    tex = {"planks": "minecraft:block/spruce_planks", "log": "minecraft:block/stripped_spruce_log",
           "gravel": "minecraft:block/gravel", "particle": "minecraft:block/spruce_planks"}
    write("models/block/sluice.json", {"textures": tex, "elements": walls})
    write("models/block/sluice_loaded.json", {"textures": tex, "elements": walls + [box([1, 1, 1], [15, 4, 15], "#gravel", "#gravel", False)]})
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for load in range(9):
            m = {"model": "jaymospowers:block/sluice" + ("_loaded" if load else "")}
            if y:
                m["y"] = y
            variants[f"facing={facing},load={load}"] = m
    write("blockstates/sluice.json", {"variants": variants})
    write("items/sluice.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:block/sluice"}})


def main():
    head()
    item("prospector_pick", grid(PICK, dict(WOOD, **COPPER)))
    item("pan", grid(PAN, PAN_PAL))
    blocks()


if __name__ == "__main__":
    main()
