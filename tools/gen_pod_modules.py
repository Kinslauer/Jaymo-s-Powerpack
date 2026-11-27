"""Writes the assets of the pod's travel kit (power/zfighter/pod: PodModules, PodLog, PodStardust, PodReturn, PodBeacons,
fuel/FuelWorks, fuel/PodFuel's fuel can): a 16x16 icon, item model and item definition for each item, textures, models,
blockstates, item definitions and loot tables for the landing beacon and the fuel works, and the forged blanks of the five
iron modules (legs, ki cannon, shield, mining beam, tow line: WeaponParts kinds pod_module_<key>_blank).

    python -I tools/gen_pod_modules.py

Icons are drawn from the grids below ('#' outline, 'x' body, 'o' shine, 'b' brass, 'r' red, 'g' glass, 'y' light).
"""
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_pod_parts as pp  # noqa: E402  (its helpers: save, write, the blank drawing)

ASSETS, DATA = pp.ASSETS, pp.DATA
PALETTE = {"#": (40, 42, 52), "x": (96, 102, 122), "o": (170, 176, 196), "b": (176, 134, 62), "r": (170, 46, 40),
           "g": (120, 190, 230), "y": (255, 226, 110), "w": (232, 226, 210), "p": (150, 100, 200), "k": (60, 60, 70)}

ICONS = {
    "pod_module_legs": [
        "................", "................", "....##....##....", "...#oo#..#oo#...", "...#ox#..#ox#...", "...#ox#..#ox#...", "...#ox#..#ox#...",
        "...#ox#..#ox#...", "..#oxx#..#oxx#..", "..#xxx#..#xxx#..", ".#oxxx####xxxo#.", ".#xxxxxxxxxxxx#.", ".##############.", "................",
        "................", "................"],
    "pod_module_hold": [
        "................", "................", "..############..", ".#oooooooooooo#.", ".#xxxxxxxxxxxx#.", ".#xxxx#bb#xxxx#.", ".#xxxx#bb#xxxx#.",
        ".#xxxxxxxxxxxx#.", ".#xxxxxxxxxxxx#.", ".#xxxxxxxxxxxx#.", ".#oooooooooooo#.", "..############..", "................",
        "................", "................", "................"],
    "pod_module_seat": [
        "................", "....######......", "...#oooooo#.....", "...#bbbbbb#.....", "...#bbbbbb#.....", "...#bbbbbb#.....", "...#bbbbbb#.....",
        "...#bbbbbbbbbb#.", "...#bbbbbbbbbb#.", "...#bbbbbbbbbb#.", "...#xxxxxxxxxx#.", "....##########..", "....#x#....#x#..", "....###....###..",
        "................", "................"],
    "pod_module_extra_tank": [
        "................", "......####......", "....########....", "...#oooooooo#...", "...#bbbbbbbb#...", "...#bbbbbbbb#...", "...#rrrrrrrr#...",
        "...#rrrrrrrr#...", "...#bbbbbbbb#...", "...#bbbbbbbb#...", "...#bbbbbbbb#...", "...#xxxxxxxx#...", "....########....", "................",
        "................", "................"],
    "pod_module_kick_stage": [
        "................", "................", ".......##.......", "......#oo#......", ".....#oxxx#.....", "....#oxxxxx#....", "....#bbbbbb#....",
        "....#bbbbbb#....", "....#xxxxxx#....", ".....#xxxx#.....", "....#rr##rr#....", "...#rrr..rrr#...", "....#r#..#r#....", "................",
        "................", "................"],
    "pod_module_scanner": [
        "................", ".....######.....", "...##oooooo##...", "..#oo......oo#..", "..#o...yy...o#..", "..#o..#yy#..o#..", "...#o.#yy#.o#...",
        "....#.#xx#.#....", ".......#xx#.....", ".......#xx#.....", "......#oxxx#....", "......#xxxx#....", ".......####.....", "................",
        "................", "................"],
    "pod_module_ki_cannon": [
        "................", "................", "................", "...........#r#..", "..........#rrr#.", ".####.....#rrr#.", "#oooo######rr#..",
        "#xxxxxxxxxxxx#..", "#xxxxxxxxxxxx#..", "#oooo######rr#..", ".####.....#rrr#.", "..........#rrr#.", "...........#r#..", "................",
        "................", "................"],
    "pod_module_shield": [
        "................", "...############.", "..#oooooooooox#.", "..#oxxxxxxxxxx#.", "..#oxxbbbbxxxx#.", "..#oxxbyybxxxx#.", "..#oxxbbbbxxxx#.",
        "..#oxxxxxxxxxx#.", "...#xxxxxxxxx#..", "....#xxxxxxx#...", ".....#xxxxx#....", "......#xxx#.....", ".......###......", "................",
        "................", "................"],
    "pod_module_mining_beam": [
        "................", "................", "..##............", ".#oo#...........", ".#ox#...rr......", "..#x#..rrrr.....", "..#x#.rrrrrr....",
        "..#xx#.rrrr.....", "..#xxx#.rr......", "..#xxxxx#.......", ".#xxxxxxx#......", ".#oxxxxxx#......", ".########.......", "................",
        "................", "................"],
    "pod_module_tow_line": [
        "................", "................", "......####......", ".....#oooo#.....", ".....#o##o#.....", ".....#o##o#.....", ".....#oooo#.....",
        "......#bb#......", "......#bb#......", ".....#b##b#.....", "....#b#..#b#....", "....#b#..#b#....", ".....#b..b#.....", "......####......",
        "................", "................"],
    "stardust": [
        "................", "................", "......y.........", ".....yyy........", "......y...y.....", "..y......yyy....", ".yyy......y.....",
        "..y.....##......", ".......#ww#.....", "......#wyyw#....", "......#wyyw#....", ".......#ww#.....", "........##......", "................",
        "................", "................"],
    "return_stage": [
        "................", ".......##.......", "......#oo#......", ".....#oxxx#.....", ".....#oxxx#.....", ".....#bbbb#.....", ".....#oxxx#.....",
        ".....#oxxx#.....", ".....#bbbb#.....", "....##oxxx##....", "...#oo#xxx#oo#..", "...#xx#rrr#xx#..", "....##.#r#.##...", "........#.......",
        "................", "................"],
    "pilots_logbook": [
        "................", "..############..", ".#bbbbbbbbbbbb#.", ".#bwwwwwwwwwwb#.", ".#bw#####w##wb#.", ".#bwwwwwwwwwwb#.", ".#bw#######wwb#.",
        ".#bwwwwwwwwwwb#.", ".#bw######wwwb#.", ".#bwwwwwwwwwwb#.", ".#bbbbbbbbbbbb#.", "..############..", "................", "................",
        "................", "................"],
    "fuel_can": [
        "................", "................", ".....####.......", "....#rr##.......", "....#rrr#.......", "...########.....", "..#oooooooo#....",
        "..#rrrrrrrr#....", "..#rwwwwwwr#....", "..#rrrrrrrr#....", "..#rrrrrrrr#....", "..#kkkkkkkk#....", "...########.....", "................",
        "................", "................"],
}

MODULE_BLANKS = {
    "pod_module_legs_blank": ("iron", ICONS["pod_module_legs"]),
    "pod_module_ki_cannon_blank": ("iron", ICONS["pod_module_ki_cannon"]),
    "pod_module_shield_blank": ("iron", ICONS["pod_module_shield"]),
    "pod_module_mining_beam_blank": ("iron", ICONS["pod_module_mining_beam"]),
    "pod_module_tow_line_blank": ("iron", ICONS["pod_module_tow_line"]),
}


def icon(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in PALETTE:
                img.putpixel((x, y), PALETTE[c] + (255,))
    return img


def item_files(name, model_ref=None):
    pp.write(os.path.join(ASSETS, "items", name + ".json"),
             {"model": {"type": "minecraft:model", "model": model_ref or "jaymospowers:item/" + name}})
    if model_ref is None:
        pp.write(os.path.join(ASSETS, "models", "item", name + ".json"),
                 {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/" + name}})


def tex(base, size_noise=5, name="x"):
    rng = random.Random(name)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            amount = rng.randint(-size_noise, size_noise) - (20 if x in (0, 15) or y in (0, 15) else 0)
            img.putpixel((x, y), pp.shade(base, amount) + (255,))
    return img


def box(frm, to, var):
    return {"from": frm, "to": to, "faces": {f: {"texture": "#" + var} for f in pp.FACES}}


def block_loot(name, props=()):
    entry = {"type": "minecraft:item", "name": "jaymospowers:" + name}
    pp.write(os.path.join(DATA, "loot_table", "blocks", name + ".json"),
             {"type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [entry]}],
              "random_sequence": "jaymospowers:blocks/" + name})


def blocks():
    # the landing beacon: an iron foot and post, a lamp that is lit once the pilot has lit it
    pp.save(tex(pp.IRON, name="beacon"), "block/landing_beacon")
    pp.save(tex((96, 80, 60), name="lamp"), "block/landing_beacon_lamp")
    pp.save(tex((255, 214, 110), 8, "lamplit"), "block/landing_beacon_lamp_lit")
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        pp.write(os.path.join(ASSETS, "models", "block", "landing_beacon" + suffix + ".json"), {
            "textures": {"all": "jaymospowers:block/landing_beacon", "lamp": "jaymospowers:block/landing_beacon_lamp" + suffix,
                         "particle": "jaymospowers:block/landing_beacon"},
            "elements": [box([3, 0, 3], [13, 3, 13], "all"), box([7, 3, 7], [9, 11, 9], "all"), box([6, 11, 6], [10, 15, 10], "lamp")]})
    pp.write(os.path.join(ASSETS, "blockstates", "landing_beacon.json"), {"variants": {
        "lit=false": {"model": "jaymospowers:block/landing_beacon"}, "lit=true": {"model": "jaymospowers:block/landing_beacon_lit"}}})
    item_files("landing_beacon", "jaymospowers:block/landing_beacon")
    block_loot("landing_beacon")
    # the fuel works: a riveted iron drum with a fire door
    pp.save(tex((78, 82, 96), name="works_top"), "block/fuel_works_top")
    pp.save(tex((90, 94, 108), name="works_side"), "block/fuel_works_side")
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        side = tex((90, 94, 108), name="works_side" + suffix)
        for y in range(5, 11):
            for x in range(4, 12):
                side.putpixel((x, y), (255, 150, 40, 255) if lit else (30, 28, 30, 255))
        pp.save(side, "block/fuel_works_front" + suffix)
        pp.write(os.path.join(ASSETS, "models", "block", "fuel_works" + suffix + ".json"), {
            "parent": "minecraft:block/cube",
            "textures": {"particle": "jaymospowers:block/fuel_works_side", "down": "jaymospowers:block/fuel_works_top",
                         "up": "jaymospowers:block/fuel_works_top", "north": "jaymospowers:block/fuel_works_front" + suffix,
                         "east": "jaymospowers:block/fuel_works_side", "south": "jaymospowers:block/fuel_works_side",
                         "west": "jaymospowers:block/fuel_works_side"}})
    pp.write(os.path.join(ASSETS, "blockstates", "fuel_works.json"), {"variants": {
        "lit=false": {"model": "jaymospowers:block/fuel_works"}, "lit=true": {"model": "jaymospowers:block/fuel_works_lit"}}})
    item_files("fuel_works", "jaymospowers:block/fuel_works")
    block_loot("fuel_works")


def items():
    for name, rows in ICONS.items():
        pp.save(icon(rows), "item/" + name)
        item_files(name)


def blanks():
    pp.BLANKS.clear()
    for name, (metal, rows) in MODULE_BLANKS.items():
        pp.BLANKS[name] = (metal, ["".join(c if c in "#xo." else ("x" if c != "." else ".") for c in row) for row in rows])
    pp.blanks()


if __name__ == "__main__":
    items()
    blocks()
    blanks()
