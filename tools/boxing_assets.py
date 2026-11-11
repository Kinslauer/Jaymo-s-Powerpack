"""Writes boxing's item art (boxing/BoxingGear): the Hand Wraps, the cestus and the bracers, and the forged heads of
the metal ones (the cestus's studs, the bracers' plates), drawn here pixel by pixel. Placeholder art until someone
draws better; nothing is copied from another mod.

    python -I tools/boxing_assets.py

The heads follow tools/weapon_parts_assets.py ('#' outline, 'x' metal, 'o' shine), like tools/gen_pod_parts.py's
blanks: a stone one to shape at the Part Builder, and the metal the gear is made in.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import weapon_parts_assets as parts  # noqa: E402

ASSETS = parts.ASSETS

# gear sprites: '.' clear, 'o' outline, 'c' body, 's' shade, 'h' light, 'm' studs or straps, 'M' their shine
WRAPS = [
    "................",
    "................",
    "....oooooooo....",
    "...ochcchcchco..",
    "...occsccsccso..",
    "..ooccccccccsso.",
    ".ocsooooooooosso",
    ".occhcccccccccso",
    ".ocssccsccsccsso",
    "..ooccccccccsso.",
    "...osssssssssso.",
    "....occcccco....",
    "....ochccsco....",
    "....ocsscsco....",
    "....ocssssco....",
    ".....oooooo....."]
CESTUS = [
    "................",
    "................",
    "....oooooooo....",
    "...oMcMcMcMco...",
    "...omsmsmsmso...",
    "..ooccccccccsso.",
    ".ocsooooooooosso",
    ".occMcMcMcMcccso",
    ".ocsmcmsmcmscsso",
    "..ooccccccccsso.",
    "...osssssssssso.",
    "....occcccco....",
    "....ochccsco....",
    "....ocsscsco....",
    "....ocssssco....",
    ".....oooooo....."]
BRACERS = [
    "................",
    ".....oooooo.....",
    "....ohhcccso....",
    "....occcccso....",
    "....oMmmmmmo....",
    "....occcccso....",
    "....ohccccso....",
    "....occcccso....",
    "....oMmmmmmo....",
    "....occcccso....",
    "....occccsso....",
    ".....occcso.....",
    ".....oMmmmo.....",
    ".....ocssso.....",
    "......oooo......",
    "................"]

CLOTH = {"o": (138, 132, 114), "c": (232, 226, 208), "s": (205, 198, 178), "h": (248, 244, 232)}
LEATHER = {"o": (58, 36, 20), "c": (142, 94, 54), "s": (110, 70, 40), "h": (176, 124, 78)}
IRON = {"o": (60, 60, 64), "c": (160, 162, 166), "s": (120, 122, 128), "h": (226, 228, 230)}
STEEL = {"o": (34, 38, 46), "c": (112, 122, 136), "s": (80, 88, 100), "h": (186, 196, 210)}
# item, sprite, the body's colours, the studs' or straps' (colour, shine)
GEAR = [
    ("hand_wraps", WRAPS, CLOTH, None),
    ("leather_cestus", CESTUS, LEATHER, ((84, 52, 28), (120, 78, 44))),
    ("iron_cestus", CESTUS, LEATHER, ((160, 162, 166), (226, 228, 230))),
    ("steel_cestus", CESTUS, LEATHER, ((112, 122, 136), (186, 196, 210))),
    ("leather_bracers", BRACERS, LEATHER, ((84, 52, 28), (120, 78, 44))),
    ("iron_bracers", BRACERS, IRON, ((96, 62, 34), (142, 94, 54))),
    ("steel_bracers", BRACERS, STEEL, ((96, 62, 34), (142, 94, 54))),
]

STUDS = [
    "................",
    "................",
    "................",
    "................",
    "..##..##..##....",
    ".#ox##ox##ox#...",
    ".#xx##xx##xx#...",
    ".#############..",
    ".#xxxxxxxxxxx#..",
    ".#oxxxoxxxoxx#..",
    "..###########...",
    "................",
    "................",
    "................",
    "................",
    "................"]
PLATES = [
    "................",
    "................",
    "...##########...",
    "..#oooooooooo#..",
    "..#oxxxxxxxxx#..",
    "..#oxxxxxxxxx#..",
    "..#xxxxxxxxxx#..",
    "...#xxxxxxxx#...",
    "...#oxxxxxxx#...",
    "...#xxxxxxxx#...",
    "....########....",
    "................",
    "................",
    "................",
    "................",
    "................"]
# head (WeaponParts part id), drawing, metal
HEADS = [("cestus_studs", STUDS, "iron"), ("steel_cestus_studs", STUDS, "steel"),
         ("bracer_plates", PLATES, "iron"), ("steel_bracer_plates", PLATES, "steel")]


def sprite(rows, colours):
    assert len(rows) == 16 and all(len(row) == 16 for row in rows), rows
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in colours:
                out.putpixel((x, y), colours[c] + (255,))
    return out


def save(img, path):
    full = os.path.join(ASSETS, "textures", path + ".png")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def main():
    for item, rows, body, trim in GEAR:
        colours = dict(body)
        if trim:
            colours["m"], colours["M"] = trim
        path = "item/boxing/" + item
        save(sprite(rows, colours), path)
        parts.write(os.path.join(ASSETS, "models", path + ".json"),
                    {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:" + path}})
        parts.write(os.path.join(ASSETS, "items", item + ".json"),
                    {"model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
    for part, rows, metal in HEADS:
        iron = sprite(rows, dict(zip("#xo", parts.DRAWN_COLOURS["iron"])))
        heads = {"stone": parts.as_stone(iron), metal: sprite(rows, dict(zip("#xo", parts.DRAWN_COLOURS[metal])))}
        cases = []
        for name, img in heads.items():
            path = parts.OUT + part + "_" + name
            save(img, path)
            parts.write(os.path.join(ASSETS, "models", path + ".json"),
                        {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
            cases.append({"when": name, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
        parts.write(os.path.join(ASSETS, "items", part + ".json"), {"model": parts.hot({
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[0]["model"]})})


if __name__ == "__main__":
    main()
