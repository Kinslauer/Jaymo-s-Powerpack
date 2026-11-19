"""Towns and the sea (medieval deco picks): the figureheads at ships' prows, the trade signs over workplaces, the song
stone in a capital's square and the journey book. Writes block and item models, blockstates, loot tables, recipes, the
new tags, the lang part and draws the textures (the figureheads take each wood's own vanilla planks texture; the signs'
marks, the stone's cut face and the book are drawn here, our own). Safe to run again. Run from the kingdoms project
root: python -I tools/gen_towns_sea.py"""
from PIL import Image
import json
import os

NS = "jaymoskingdoms"
RES = "src/main/resources/"
ASSETS = RES + "assets/" + NS + "/"
DATA = RES + "data/" + NS + "/"

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo"]
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
CARVINGS = ["lion", "gull", "stag", "whale", "serpent", "maiden"]
MARKS = ["anvil", "chisel", "loaf", "mug", "needle", "scales", "cleaver", "fish", "horseshoe", "bow", "sword"]


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def title(name):
    return " ".join(w.capitalize() for w in name.split("_"))


def box(frm, to, tex):
    return {"from": list(frm), "to": list(to), "faces": {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}


# --- the figureheads: each carving's boxes (pixels, bow toward north) ------------------------------------------------

STEM = [((5, 0, 10), (11, 4, 16)), ((6, 4, 9), (10, 10, 13))]
CARVED = {
    "lion": [((4, 8, 3), (12, 15, 10)), ((6, 8, 1), (10, 11, 3)), ((3, 6, 8), (13, 16, 12)), ((5, 15, 5), (7, 16, 7)), ((9, 15, 5), (11, 16, 7))],
    "gull": [((6, 6, 6), (10, 10, 13)), ((7, 10, 4), (9, 13, 7)), ((7.5, 11, 1), (8.5, 12, 4)), ((1, 7, 7), (6, 9, 12)), ((10, 7, 7), (15, 9, 12))],
    "stag": [((6, 6, 4), (10, 11, 10)), ((7, 6, 2), (9, 8, 4)), ((4, 11, 5), (5, 16, 6)), ((11, 11, 5), (12, 16, 6)),
             ((3, 14, 5), (5, 15, 6)), ((11, 14, 5), (13, 15, 6))],
    "whale": [((3, 3, 2), (13, 11, 12)), ((4, 1, 0), (12, 3, 8)), ((6, 11, 6), (10, 13, 9))],
    "serpent": [((7, 4, 9), (9, 9, 13)), ((7, 9, 6), (9, 13, 10)), ((6, 12, 2), (10, 15, 7)), ((6.5, 10, 2), (7.5, 12, 3)), ((8.5, 10, 2), (9.5, 12, 3))],
    "maiden": [((5, 2, 8), (11, 9, 13)), ((6, 9, 8), (10, 14, 12)), ((5, 8, 11), (11, 15, 14)), ((3, 5, 4), (5, 8, 10)), ((11, 5, 4), (13, 8, 10))],
}


def figureheads():
    names = {}
    for wood in WOODS:
        name = wood + "_figurehead"
        for carving in CARVINGS:
            write(ASSETS + "models/block/%s_%s.json" % (name, carving), {
                "parent": "minecraft:block/block",
                "textures": {"0": "minecraft:block/%s_planks" % wood, "particle": "minecraft:block/%s_planks" % wood},
                "elements": [box(a, b, "#0") for a, b in STEM + CARVED[carving]],
            })
        write(ASSETS + "blockstates/%s.json" % name, {"variants": {
            "facing=%s,carving=%s" % (f, c): {"model": "%s:block/%s_%s" % (NS, name, c), "y": y} if y else {"model": "%s:block/%s_%s" % (NS, name, c)}
            for f, y in FACINGS.items() for c in CARVINGS}})
        write(ASSETS + "items/%s.json" % name, {"model": {"type": "minecraft:model", "model": "%s:block/%s_lion" % (NS, name)}})
        write(DATA + "loot_table/blocks/%s.json" % name, {
            "type": "minecraft:block",
            "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": "%s:%s" % (NS, name)}], "rolls": 1}],
            "random_sequence": "%s:blocks/%s" % (NS, name)})
        write(DATA + "recipe/%s.json" % name, {
            "type": "minecraft:crafting_shaped", "category": "misc", "group": NS + ":figurehead",
            "pattern": [" ##", "###", "|  "], "key": {"#": "minecraft:%s_planks" % wood, "|": "minecraft:stick"},
            "result": {"count": 1, "id": "%s:%s" % (NS, name)}})
        names["block.%s.%s" % (NS, name)] = title(wood) + " Figurehead"
    write(DATA + "tags/block/figureheads.json", {"replace": False, "values": ["%s:%s_figurehead" % (NS, w) for w in WOODS]})
    return names


# --- the trade signs: a board with the trade's mark, 12 by 12 pixels of it ---------------------------------------------

GLYPHS = {
    "anvil": ["............", ".##########.", ".##########.", "..########..", "....####....", "....####....", "...######...", "..########..", "..########.."],
    "chisel": ["...######...", "...######...", ".....##.....", ".....oo.....", ".....oo.....", ".....oo.....", ".....oo.....", "......o....."],
    "loaf": ["............", "...######...", "..########..", ".##o##o##o#.", ".##########.", ".##########.", "..########.."],
    "mug": [".#######....", ".#ooooo#.##.", ".#######.#.#", ".#######.#.#", ".#######.##.", ".#######....", ".#######...."],
    "needle": ["..........#.", ".........#o.", "........#...", ".......#....", "......#.....", ".....#......", "....#.......", "...#........", "..#o........", ".#.........."],
    "scales": [".....##.....", ".##########.", ".#...##...#.", ".#...##...#.", "####.##.####", ".##..##..##.", ".....##.....", "....####...."],
    "cleaver": [".#########..", ".#ooooooo#..", ".#ooooooo#..", ".#########..", ".#########..", "......##....", "......##....", "......##...."],
    "fish": ["...####.....", "..######..#.", ".##o#####.##", ".#########.#", ".##o#####.##", "..######..#.", "...####....."],
    "horseshoe": [".###....###.", ".#o#....#o#.", ".#o#....#o#.", ".#o#....#o#.", ".##o....o##.", "..##o..o##..", "...######..."],
    "bow": ["...###......", "..#...o.....", ".#....o.....", ".#....o.....", ".#....o.....", "..#...o.....", "...###......"],
    "sword": [".....oo.....", ".....oo.....", ".....oo.....", ".....oo.....", ".....oo.....", ".....oo.....", "...######...", ".....##.....", ".....##.....", ".....##....."],
}
BOARD, FRAME, INK, METAL = (176, 138, 84, 255), (92, 64, 36, 255), (38, 28, 22, 255), (206, 206, 212, 255)


def sign_texture(mark):
    img = Image.new("RGBA", (16, 16), BOARD)
    for i in range(16):
        for j in range(16):
            if i in (0, 15) or j in (0, 15):
                img.putpixel((i, j), FRAME)
            elif (i + j * 3) % 7 == 0:
                img.putpixel((i, j), (166, 128, 76, 255))
    rows = GLYPHS[mark]
    top = (16 - len(rows)) // 2
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in "#o":
                img.putpixel((2 + x, top + y), INK if c == "#" else METAL)
    return img


def signs():
    for mark in MARKS:
        out = ASSETS + "textures/block/trade_sign_%s.png" % mark
        os.makedirs(os.path.dirname(out), exist_ok=True)
        sign_texture(mark).save(out)
        faces = {f: {"texture": "#side"} for f in ("south", "east", "west", "up", "down")}
        faces["north"] = {"texture": "#mark"}
        write(ASSETS + "models/block/trade_sign_%s.json" % mark, {
            "parent": "minecraft:block/block",
            "textures": {"mark": "%s:block/trade_sign_%s" % (NS, mark), "side": "minecraft:block/oak_planks", "particle": "minecraft:block/oak_planks"},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]})
    write(ASSETS + "blockstates/trade_sign.json", {"variants": {
        "facing=%s,mark=%s" % (f, m): {"model": "%s:block/trade_sign_%s" % (NS, m), "y": y} if y else {"model": "%s:block/trade_sign_%s" % (NS, m)}
        for f, y in FACINGS.items() for m in MARKS}})
    return {"block.%s.trade_sign" % NS: "Trade Sign"}


# --- the song stone: a stele with its face cut in rows ------------------------------------------------------------------

def stone_texture():
    img = Image.new("RGBA", (16, 16), (128, 128, 132, 255))
    for i in range(16):
        for j in range(16):
            if (i * 5 + j * 11) % 9 == 0:
                img.putpixel((i, j), (116, 116, 120, 255))
            if i in (0, 15) or j in (0, 15):
                img.putpixel((i, j), (96, 96, 100, 255))
    for y in (3, 6, 9, 12):
        x = 3
        for run in (3, 2, 4, 2):
            for k in range(run):
                img.putpixel((x + k, y), (58, 58, 64, 255))
            x += run + 1
            if x > 12:
                break
    return img


def song_stone():
    out = ASSETS + "textures/block/song_stone.png"
    stone_texture().save(out)
    faces = {f: {"texture": "#side"} for f in ("south", "east", "west", "up", "down")}
    faces["north"] = {"texture": "#face"}
    write(ASSETS + "models/block/song_stone.json", {
        "parent": "minecraft:block/block",
        "textures": {"face": "%s:block/song_stone" % NS, "side": "minecraft:block/stone_bricks", "particle": "minecraft:block/stone_bricks"},
        "elements": [box((2, 0, 2), (14, 3, 14), "#side"), {"from": [4, 3, 4], "to": [12, 15, 12], "faces": faces}]})
    write(ASSETS + "blockstates/song_stone.json", {"variants": {
        "facing=%s" % f: {"model": "%s:block/song_stone" % NS, "y": y} if y else {"model": "%s:block/song_stone" % NS} for f, y in FACINGS.items()}})
    write(ASSETS + "items/song_stone.json", {"model": {"type": "minecraft:model", "model": "%s:block/song_stone" % NS}})
    write(DATA + "loot_table/blocks/song_stone.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [{"type": "minecraft:item", "name": NS + ":song_stone"}], "rolls": 1}],
        "random_sequence": NS + ":blocks/song_stone"})
    return {"block.%s.song_stone" % NS: "Song Stone", "building.%s.song_stone" % NS: "Song Stone"}


# --- the journey book ---------------------------------------------------------------------------------------------------

def journey_book():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    cover, edge, page, ribbon, mark = (94, 70, 44, 255), (58, 40, 24, 255), (226, 214, 180, 255), (62, 116, 150, 255), (214, 190, 120, 255)
    for x in range(2, 14):
        for y in range(1, 15):
            img.putpixel((x, y), edge if x in (2, 13) or y in (1, 14) else cover)
    for y in range(3, 13):
        img.putpixel((12, y), page)
    for y in range(1, 8):
        img.putpixel((9, y), ribbon)
    for x, y in ((5, 11), (6, 10), (7, 9), (6, 8), (5, 7), (6, 6), (7, 5)):
        img.putpixel((x, y), mark)
    out = ASSETS + "textures/item/journey_book.png"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    write(ASSETS + "models/item/journey_book.json", {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/journey_book"}})
    write(ASSETS + "items/journey_book.json", {"model": {"type": "minecraft:model", "model": NS + ":item/journey_book"}})
    write(DATA + "recipe/journey_book.json", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ["minecraft:book", "minecraft:compass"],
                                              "result": {"id": NS + ":journey_book"}})


if __name__ == "__main__":
    figure = figureheads()
    sign = signs()
    stone = song_stone()
    journey_book()
    write(ASSETS + "lang/parts/towns-sea-blocks.json", {**figure, **sign, **stone, "item.%s.journey_book" % NS: "Journey Book"})
