"""Paints the great foes' trophies (kingdoms trophies/Trophies.look: keep the names the same) and writes the trophy's item
definition that picks one by the look on the stack. Each is drawn as its left half and mirrored. Run from the repo root."""
import json
import os
from PIL import Image

ASSETS = "kingdoms/src/main/resources/assets/jaymoskingdoms/"
BONE = {"b": (236, 228, 204), "c": (202, 190, 160), "d": (150, 136, 108), "k": (30, 22, 20)}
LOOKS = {
    # a horned skull, long in the jaw
    "dragon": (BONE, [
        "..c.....", "..bc....", "..cbc...", "...cbc..", "...dbbcc", "..dbbbbb", ".dbbbbbb", ".dbkkbbb",
        ".dbkkbbb", ".dbbbbbb", "..dbbbdb", "..dbbbkb", "...dbbbb", "...dbcbc", "....dk.k", "........"]),
    "wither": ({"b": (70, 70, 74), "c": (48, 48, 52), "d": (28, 28, 32), "k": (230, 230, 236)}, [
        "........", "........", "..dddddd", ".dcccccc", ".dcbbbbb", ".dcbbbbb", ".dckkbbb", ".dckkbbb",
        ".dcbbbbb", ".dcbbbdd", ".dcbbbbb", ".dcbkbkb", ".dccbbbb", "..dddddd", "........", "........"]),
    "warden": ({"b": (24, 74, 84), "c": (16, 50, 60), "d": (8, 28, 36), "k": (60, 220, 214), "h": (214, 208, 180)}, [
        "h.......", "hh......", ".hh.....", "..hhdddd", "..dhcccc", ".dccbbbb", ".dcbbbbb", ".dcbbbbb",
        ".dcbbbbb", ".dcbbbkk", ".dcbbkkk", ".dcbbbkk", ".dccbbbb", "..dddddd", "........", "........"]),
    "elder_guardian": ({"b": (190, 184, 160), "c": (150, 146, 128), "d": (104, 100, 90), "k": (40, 20, 20), "s": (212, 120, 72), "e": (236, 232, 220)}, [
        "...s....", "...s...s", "s..ddddd", ".sdccccc", "..dcbbbb", ".dcbbbbb", ".dcbbbee", "sdcbbeek",
        ".dcbbeek", ".dcbbbee", ".dcbbbbb", "..dcbbbb", ".sdccccc", "s..ddddd", "...s...s", "...s...."]),
    "minotaur": (BONE, [
        "b.......", "bc......", "cbc.....", ".cbbc...", "..cbbbcc", "...dbbbb", "...dbbbb", "...dkkbb",
        "...dkkbb", "...dbbbb", "....dbbb", "....dbkb", "....dbbb", "....dcbc", ".....ddd", "........"]),
    "gorgon": ({"b": (150, 186, 120), "c": (108, 148, 88), "d": (60, 92, 52), "k": (236, 210, 60), "s": (44, 110, 60), "t": (26, 70, 40)}, [
        ".s...s..", "s.t.s.ts", "s.s.t.s.", ".tsstsst", "..tddddd", "..dccccc", ".sdcbbbb", "s.dcbbbb",
        "t.dckkbb", ".sdcbbbb", "..dcbbbb", "..dcbbdd", "..dccbbb", "...ddddd", "........", "........"]),
    "grizzly": ({"b": (150, 104, 64), "c": (112, 74, 44), "d": (70, 44, 26), "k": (20, 14, 12), "m": (196, 160, 116)}, [
        "........", "..dd....", ".dccd...", ".dcbcddd", "..dcbbbb", "..dcbbbb", ".dcbbbbb", ".dcbkbbb",
        ".dcbbbbb", ".dcbbmmm", "..dcbmmm", "..dcbmmk", "...dcmmm", "....dddd", "........", "........"]),
    "snow_giant": ({"b": (244, 248, 252), "c": (208, 220, 232), "d": (150, 170, 192), "k": (20, 30, 50), "m": (96, 130, 170)}, [
        "........", "...ddddd", "..dccccc", ".dcbbbbb", ".dcbbbbb", "dcbbbmmm", "dcbbmmmm", "dcbbmkkm",
        "dcbbmmmm", "dcbbmmmm", ".dcbbmmk", ".dcbbbmm", "..dcbbbb", "...dcccc", "....dddd", "........"]),
    # its maw from above: a ring of teeth
    "sand_worm": ({"b": (214, 196, 148), "c": (186, 165, 118), "d": (132, 110, 76), "k": (64, 16, 20), "t": (238, 232, 214)}, [
        "........", "....dddd", "..ddcccc", ".dccbbbb", ".dcbbttt", "dcbbtkkk", "dcbtkkkk", "dcbtkkkk",
        "dcbtkkkk", "dcbtkkkk", "dcbbtkkk", ".dcbbttt", ".dccbbbb", "..ddcccc", "....dddd", "........"]),
    # its beak, in a collar of flesh
    "wreck_thing": ({"b": (150, 72, 96), "c": (112, 46, 72), "d": (74, 28, 50), "k": (30, 22, 20), "h": (70, 54, 46), "s": (226, 188, 196)}, [
        "........", "........", "....dddd", "..ddcccc", ".dccbbsb", ".dcbsbbb", "dcbbbhhh", "dcsbhhkk",
        "dcbbhkkk", "dcbbhhkk", ".dcsbhhk", ".dcbbbhh", "..dccbbh", "...ddccc", ".....ddd", "........"]),
    "fortress_knight": ({"b": (74, 66, 72), "c": (52, 46, 52), "d": (30, 26, 32), "k": (220, 60, 40), "g": (150, 120, 60)}, [
        ".......g", "......gg", "...ddddg", "..dccccc", ".dcbbbbb", ".dcbbbbb", ".dcbbbbb", ".dckkkkb",
        ".dcbbbbb", ".dcbbbdb", ".dcbbbdb", ".dcbbbdb", ".dccbbdb", "..dddddd", "........", "........"]),
    # her flukes
    "great_whale": ({"b": (120, 132, 146), "c": (88, 100, 116), "d": (56, 66, 82)}, [
        "........", "dd......", "dccd....", "dcbccd..", ".dcbbcdd", ".dcbbbcc", "..dcbbbb", "...dcbbb",
        "....dccb", "......dc", "......dc", "......dc", "......dc", ".....dcc", ".....ddd", "........"]),
    "ogre": ({"b": (132, 160, 92), "c": (98, 124, 66), "d": (58, 78, 40), "k": (24, 20, 14), "t": (236, 228, 204)}, [
        "........", "...ddddd", "..dccccc", ".dcbbbbb", "ddcbbbbb", "dcdbbbbb", "dcdbkkbb", ".ddbbbbb",
        "..dbbbbb", "..dbbbdd", "..dtbbbb", "..dtkkkk", "..dctbbb", "...dcccc", "....dddd", "........"]),
    # a face of the old stone
    "giant": ({"b": (176, 182, 176), "c": (134, 142, 138), "d": (88, 96, 96), "k": (120, 230, 236), "m": (96, 150, 96)}, [
        "........", "..mddddd", ".mdccccc", ".dcbbbbb", ".dcbbbbb", ".dcbbbbb", ".dckkkbb", ".dcbbbbb",
        ".dcbbbbd", ".mcbbbbd", ".dcbbbbb", ".dcbdddd", ".dccbbbb", "..mddddd", "........", "........"]),
}

os.makedirs(ASSETS + "textures/item/trophy", exist_ok=True)
os.makedirs(ASSETS + "models/item/trophy", exist_ok=True)
cases = []
for name, (palette, rows) in LOOKS.items():
    assert len(rows) == 16 and all(len(r) == 8 for r in rows), name
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row + row[::-1]):
            if ch != ".":
                im.putpixel((x, y), palette[ch] + (255,))
    im.save(ASSETS + "textures/item/trophy/" + name + ".png")
    with open(ASSETS + "models/item/trophy/" + name + ".json", "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/trophy/" + name}}, f, indent="\t")
        f.write("\n")
    cases.append({"when": name, "model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/trophy/" + name}})
with open(ASSETS + "items/great_foe_trophy.json", "w") as f:
    json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
                         "fallback": {"type": "minecraft:model", "model": "jaymoskingdoms:item/great_foe_trophy"}}}, f, indent="\t")
    f.write("\n")
