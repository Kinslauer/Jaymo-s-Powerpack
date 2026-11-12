"""Draws and writes the gear patch (src/main/java/com/jaymospowers/gear): arrowheads, fitted arrows, the throwables, the belts of
throwables, and the Tool Station blanks of the iron ones. Our own 16x16 art, in the pack's palette (outline, body, shine), and
every model, item definition, recipe and lang key those items need. Run it from the root:

    python -I tools/gen_gear_picks.py

Models use #variable-free `minecraft:item/generated` with a `layer0` texture (26.3 item models), item definitions are the
`minecraft:model` shape, and the headed arrow and the Tinkers blanks select on custom model data like the weapon parts do.
The blanks' ids must match the Kinds added to tinkering/WeaponParts.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
ITEM_DIR = "item/gear/"
PART_DIR = "item/tinkering/weapon_part/"


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def ramp(hexcolor):
    base = rgb(hexcolor)
    return [mix(base, (255, 255, 255), 0.5), base, mix(base, (0, 0, 0), 0.4), mix(base, (0, 0, 0), 0.72)]


# ---- arrows: the geometry of tools/gen_fletching.py ----
STICK = [rgb("896727"), rgb("684e1e")]
FEATHER = [rgb("f2f2f2"), rgb("cfcfcf"), rgb("a0a0a0")]
SHAFT = [(x, 15 - x) for x in range(3, 12)]
TAIL = {(1, 12): 1, (2, 12): 0, (1, 13): 2, (2, 11): 0, (3, 11): 1, (2, 14): 2, (3, 13): 1, (4, 13): 2, (0, 14): 2, (1, 14): 1}
HEAD = [(14, 1, 0), (13, 2, 0), (12, 3, 1), (13, 1, 1), (14, 2, 1), (12, 2, 1), (13, 3, 2), (12, 4, 3), (11, 3, 2), (11, 2, 3), (14, 3, 3),
        (13, 0, 3), (15, 0, 3)]
# what makes each head look its own, as (x, y, ramp index) beside the arrowhead and on the loose head
ACCENTS = {
    "bola": [(10, 1, 1), (9, 2, 2), (15, 4, 1), (15, 5, 2)],
    "net": [(10, 2, 0), (10, 4, 0), (12, 5, 0), (15, 3, 0)],
    "caltrop": [(15, 3, 0), (14, 4, 1), (10, 1, 0), (11, 5, 1)],
    "harpoon": [(10, 4, 1), (10, 5, 2), (11, 5, 2)],
    "grapple": [(15, 4, 1), (15, 5, 1), (14, 5, 2)],
    "smoke": [(10, 0, 0), (9, 1, 1), (11, 1, 1), (8, 0, 2)],
    "flare": [(15, 2, 0), (12, 0, 0), (10, 1, 1), (15, 5, 1)],
    "whistle": [(10, 3, 0), (9, 4, 1), (10, 5, 1)],
    "barbed": [(11, 5, 1), (10, 4, 2), (14, 5, 1), (15, 4, 2)],
    "broadhead": [(11, 4, 1), (14, 4, 1), (13, 4, 2), (12, 5, 2)],
    "fire": [(15, 1, 0), (12, 1, 0), (13, 0, 0), (10, 2, 1)],
    "flash": [(15, 0, 0), (11, 0, 0), (15, 4, 0), (10, 4, 0)],
    "drill": [(15, 1, 0), (14, 0, 1), (13, 3, 0), (15, 3, 1)],
    "boomerang": [(10, 2, 1), (9, 3, 1), (10, 4, 2), (11, 5, 2)],
}
HEADS = {  # id: (colour, display name, tooltip)
    "bola": ("9A7A4A", "Bola", "Wraps and trips whoever it hits, through the bolas' own cord."),
    "net": ("CFC8A8", "Net", "Bursts into a net where it lands and roots everything near for a few seconds."),
    "caltrop": ("7A7C84", "Caltrop", "Scatters caltrops where it lands: they cut and slow whoever steps on them."),
    "harpoon": ("A8AEB8", "Harpoon", "Drags what it hits toward you on a line."),
    "grapple": ("8AA0B4", "Grapple", "Where it sticks, it pulls you."),
    "smoke": ("808086", "Smoke", "Bursts into a cloud that hides you; mobs inside lose their target."),
    "flare": ("FF5A30", "Flare", "Lights and marks the ground where it lands: everything near glows for fifteen seconds."),
    "whistle": ("E0C878", "Whistle", "Shrieks where it lands and calls every mob in earshot to the spot."),
    "barbed": ("9A2424", "Barbed", "Barbs that open a bleeding cut."),
    "broadhead": ("D6DAE2", "Broadhead", "Hits much harder, but flies slower and drops sooner."),
    "fire": ("FF8A20", "Fire", "Sets the arrow alight."),
    "flash": ("FFFFF0", "Flash", "A blinding flash where it lands."),
    "drill": ("5A6C7E", "Drill", "Pierces a second target, and goes through a raised shield."),
    "boomerang": ("B07A30", "Boomerang", "Comes back to your hand if it misses."),
}


def blank():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def headed_arrow(head):
    img = blank()
    for i, (x, y) in enumerate(SHAFT):
        img.putpixel((x, y), STICK[i % 2])
    for x, y in SHAFT[:-1]:
        if img.getpixel((x + 1, y))[3] == 0:
            img.putpixel((x + 1, y), STICK[1])
    for p, shade in TAIL.items():
        img.putpixel(p, FEATHER[shade])
    colours = ramp(HEADS[head][0])
    for x, y, i in HEAD:
        img.putpixel((x, y), colours[i])
    for x, y, i in ACCENTS[head]:
        if img.getpixel((x, y))[3] == 0:
            img.putpixel((x, y), colours[i])
    return img


def head_icon(head):
    """The loose head: a big kite-shaped point, its socket at the bottom, and the same accents blown up."""
    img = blank()
    colours = ramp(HEADS[head][0])
    rows = {2: (7, 8), 3: (6, 9), 4: (5, 10), 5: (4, 11), 6: (4, 11), 7: (5, 10), 8: (6, 9), 9: (7, 8)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            edge = x in (a, b) or y in (2, 9)
            img.putpixel((x, y), colours[3] if edge else colours[0] if x < 8 and y < 6 else colours[1] if y < 7 else colours[2])
    for y in range(10, 14):
        for x in (7, 8):
            img.putpixel((x, y), STICK[0] if y % 2 else STICK[1])
    for i, (x, y, shade) in enumerate(ACCENTS[head]):
        px, py = 2 + (x - 8) * 1 + 3, 3 + y
        px, py = max(0, min(15, px)), max(0, min(15, py))
        if img.getpixel((px, py))[3] == 0:
            img.putpixel((px, py), colours[shade])
    return img


# ---- throwables and belts, drawn from grids ----
PAL = {"#": rgb("2c2622"), "c": rgb("b0704c"), "C": rgb("d4966c"), "g": rgb("8c8c92"), "G": rgb("c8c8ce"), "w": rgb("f0f0ec"),
       "y": rgb("ecd478"), "r": rgb("b42824"), "k": rgb("3c3c42"), "n": rgb("6eaa3c"), "N": rgb("aadc64"), "i": rgb("a0a2a6"),
       "I": rgb("e2e4e6"), "b": rgb("785430"), "l": rgb("96643c"), "L": rgb("be8c56"), "s": rgb("707a88"), "S": rgb("bac4d2"),
       "a": rgb("c8e6f0"), "p": rgb("d8d0b4"), "o": rgb("e89a48")}
GRIDS = {
    "smoke_pot": [
        "................",
        ".......gG.......",
        "......gGgg......",
        ".....gG.gGg.....",
        "......gg.g......",
        ".......##.......",
        "......#CC#......",
        ".....#CCcc#.....",
        "....#CCcccc#....",
        "....#Ccccckc#...",
        "....#Cccckkc#...",
        "....#cccckcc#...",
        ".....#cccccc#...",
        "......#cccc#....",
        ".......####.....",
        "................"],
    "flash_powder": [
        "................",
        "................",
        "......w..w......",
        ".......ww.......",
        ".....w.yy.w.....",
        "......#pp#......",
        ".....#ppppp#....",
        "....#pppyppp#...",
        "....#ppyyypp#...",
        "....#pppyppp#...",
        ".....#ppppp#....",
        "......#####.....",
        "................",
        "................",
        "................",
        "................"],
    "sneezing_powder": [
        "................",
        "..w.............",
        "....w...w.......",
        "..........w.....",
        ".....#####......",
        "....#bLLLb#.....",
        "....#bbbbb#.....",
        "...#ppppppp#....",
        "...#pwpppwp#....",
        "...#pppwppp#....",
        "...#ppppppp#....",
        "....#ppppp#.....",
        ".....#####......",
        "................",
        "................",
        "................"],
    "bleeding_dart": [
        "................",
        "...............r",
        "..............rr",
        ".............#Ir",
        "............#Ii.",
        "...........#Ii..",
        "..........#ii#..",
        ".........#bb#...",
        "........#bb#....",
        ".......#bb#.....",
        "......#bb#r.....",
        ".....#rrr#......",
        "....#rrr#.......",
        "....##r#........",
        "................",
        "................"],
    "acid_vial": [
        "................",
        ".......##.......",
        ".......bb.......",
        "......#aa#......",
        "......#aa#......",
        ".....#aaaa#.....",
        "....#aNNNNa#....",
        "....#NNNNNN#....",
        "....#NnNNnN#....",
        "....#NNnnNN#....",
        "....#nNNNNn#....",
        ".....#nnnn#.....",
        "......####......",
        "................",
        "................",
        "................"],
    "choking_vial": [
        "................",
        ".......##.......",
        ".......bb.......",
        "......#aa#......",
        "......#aa#......",
        ".....#aaaa#.....",
        "....#aGGGGa#....",
        "....#GgGGgG#....",
        "....#gGGgGg#....",
        "....#GgggGG#....",
        "....#gGGGgg#....",
        ".....#gggg#.....",
        "......####......",
        "................",
        "................",
        "................"],
    "caltrops": [
        "................",
        ".......I........",
        ".......i........",
        "..I....i....I...",
        "...i...i...i....",
        "....i#iii#i.....",
        ".....#iiii#.....",
        "......#ii#......",
        "...I..#ii#..I...",
        "....i#iiii#i....",
        ".....i#ii#i.....",
        ".....i.##.i.....",
        "....I......I....",
        "................",
        "................",
        "................"],
    "bear_trap": [
        "................",
        "................",
        "................",
        "..I.I.I.I.I.I...",
        ".#i#i#i#i#i#i#..",
        ".#i.i.i.i.i.i#..",
        "#IsssssssssssI#.",
        "#sIIIIIIIIIIIs#.",
        "#ss#########ss#.",
        "#bbbbbbbbbbbbb#.",
        ".#bllllllllllb#.",
        "..#bbbbbbbbbb#..",
        "...###kk#####...",
        ".......kk.......",
        "........kk......",
        "................"],
}
BELTS = {
    "leather_thrower_belt": ("96643c", "be8c56", "a0a2a6"),
    "iron_thrower_belt": ("96643c", "be8c56", "e2e4e6"),
    "steel_thrower_belt": ("707a88", "bac4d2", "f0f4fa"),
}


def belt_icon(strap, shine, buckle):
    img = blank()
    s, h, b = rgb(strap), rgb(shine), rgb(buckle)
    dark = mix(s, (0, 0, 0), 0.5)
    for x in range(1, 15):
        img.putpixel((x, 5), dark)
        img.putpixel((x, 6), h)
        img.putpixel((x, 7), s)
        img.putpixel((x, 8), s)
        img.putpixel((x, 9), dark)
    # three little pouches hung from it, a buckle in the middle
    for cx in (3, 12):
        for y in range(10, 14):
            for x in range(cx - 1, cx + 2):
                img.putpixel((x, y), s if y < 13 else dark)
        img.putpixel((cx, 10), h)
    for y in range(5, 10):
        for x in (7, 8):
            img.putpixel((x, y), b if y in (6, 7, 8) else dark)
    img.putpixel((7, 7), dark)
    img.putpixel((8, 7), dark)
    return img


def grid_image(rows, palette=PAL):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "a grid must be 16 by 16"
    img = blank()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c in palette:
                img.putpixel((x, y), palette[c])
    return img


# ---- Tool Station blanks: '#' outline, 'x' metal, 'o' shine ----
BLANKS = {
    "barbed_head_blank": ("Barbed Head Blank", [
        "................", "................", "........##......", ".......#oo#.....", "......#oxx#.....", "......#oxxx#....", ".....#oxxxx#....",
        "..#.#oxxxxx#.#..", ".#x##xxxxx#x#x#.", "..#x#xxxxx#x#...", "...##xxxxx##....", ".....#xxx#......", "......#xx#......", "......#xx#......", ".......##.......",
        "................"]),
    "harpoon_arrowhead_blank": ("Harpoon Arrowhead Blank", [
        "................", "........##......", ".......#oo#.....", "......#oxxx#....", "......#oxxx#....", ".....#oxxxxx#...", ".....#xx#xxx#...",
        "....#xx#.#xx#...", "....#x#..#.#x#..", "....##...##.##..", "......#xx#......", "......#xx#......", "......#xx#......", ".....#xxxx#.....", ".....######.....",
        "................"]),
    "grapple_head_blank": ("Grapple Head Blank", [
        "................", "......####......", ".....#oxxo#.....", ".....#x##x#.....", ".....#x..x#.....", "......#..x#.....", "......##x#......",
        ".....#oxx#......", "....#oxxx#......", "....#xxx#.#.....", "....#xx#.#x#....", "...#xxx#..#x#...", "...#xx#....##...", "...##.#.........", "................",
        "................"]),
    "drill_head_blank": ("Drill Head Blank", [
        "................", ".......##.......", "......#oo#......", "......#ox#......", ".....#oxx#......", ".....#xx#x#.....", "....#oxxxx#.....",
        "....#xx#xx#.....", "...#oxxxxxx#....", "...#xxx#xxx#....", "..#oxxxxxxxx#...", "..#xxxxxxxxx#...", "..############..", "................", "................",
        "................"]),
    "broadhead_blank": ("Broadhead Blank", [
        "................", ".........##.....", "........#oo#....", ".......#oxxx#...", "......#oxxxx#...", ".....#oxxxxxx#..", "....#oxxxxxxx#..",
        "...#oxxxxxxx#...", "..#oxxxxxxx#....", "...#xxxxxx#.....", "....#xxxx#......", ".....#xx#.......", "....#xx#........", "...#xx#.........", "...##...........",
        "................"]),
    "caltrop_head_blank": ("Caltrop Head Blank", [
        "................", "................", "......#oo#......", ".....#oxxo#.....", "..#..#xxxx#..#..", ".#o#.#xxxx#.#o#.", "..#o##xxxx##o#..",
        "...#oxxxxxxo#...", "....#xxxxxx#....", "....#oxxxxo#....", ".....#oxxo#.....", "....#o#xx#o#....", "...#o#.##.#o#...", "...##......##...", "................",
        "................"]),
    "caltrops_blank": ("Caltrops Blank", [
        "................", "................", ".......##.......", "......#oo#......", "..#...#ox#...#..", ".#o#..#xx#..#o#.", "..#o#.#xx#.#o#..",
        "...#oo#xx#oo#...", "....#xxxxxxx#...", ".....#xxxxx#....", "....#oxxxxxo#...", "...#o#xxxx#o#...", "..#o#.#xx#.#o#..", "..##...##...##..", "................",
        "................"]),
    "dart_tip_blank": ("Dart Tip Blank", [
        "................", "................", "................", "............##..", "...........#oo#.", "..........#oxx#.", ".........#oxx#..",
        "........#oxx#...", ".......#oxx#....", "......#oxx#.....", ".....#oxx#......", "....#oxx#.......", "...#oxx#........", "...###..........", "................",
        "................"]),
    "trap_jaws_blank": ("Trap Jaws Blank", [
        "................", "................", "................", "................", "..#.#.#.#.#.#...", ".#x#x#x#x#x#x#..", ".#x.x.x.x.x.x#..",
        "#oxxxxxxxxxxxo#.", "#xooooooooooox#.", "#xx#########xx#.", ".#xxxxxxxxxxx#..", "..###########...", "................", "................", "................",
        "................"]),
}
BLANK_TO_ITEM = {
    "barbed_head_blank": "head_barbed", "harpoon_arrowhead_blank": "head_harpoon", "grapple_head_blank": "head_grapple",
    "drill_head_blank": "head_drill", "broadhead_blank": "head_broadhead", "caltrop_head_blank": "head_caltrop",
    "caltrops_blank": "caltrops", "dart_tip_blank": "bleeding_dart", "trap_jaws_blank": "bear_trap",
}
IRON = {"#": (60, 60, 64, 255), "x": (160, 162, 166, 255), "o": (226, 228, 230, 255)}
STONE = {"#": (58, 54, 50, 255), "x": (122, 118, 112, 255), "o": (176, 172, 164, 255)}

THROWABLES = {
    "smoke_pot": ("Smoke Pot", "A clay pot of stinging smoke. It breaks where it lands and chokes and blinds whoever stands in the cloud."),
    "flash_powder": ("Flash Powder", "A twist of powder that goes off in a blinding flash where it lands."),
    "sneezing_powder": ("Sneezing Powder", "Breathed in, it makes mobs lose their target and stumble, and shakes a player out of whatever they were holding ready."),
    "bleeding_dart": ("Bleeding Dart", "An iron-tipped dart that opens a bleeding cut."),
    "acid_vial": ("Acid Vial", "Splashes a pool that eats the armour of everyone in it."),
    "choking_vial": ("Choking Vial", "A vial of heavy gas: a cloud that sickens, weakens and slows whoever breathes it."),
    "caltrops": ("Caltrops", "Scatters iron spikes that cut and slow whoever steps on them."),
    "bear_trap": ("Bear Trap", "Set where it lands: it snaps shut on the first thing to step near it and holds it fast."),
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def model(rel, texture):
    write(os.path.join(ASSETS, "models", rel + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + texture}})


def definition(item, rel):
    write(os.path.join(ASSETS, "items", item + ".json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:" + rel}})


def simple_item(item, img):
    rel = ITEM_DIR + item
    save(img, rel)
    model(rel, rel)
    definition(item, rel)


def part_model(part, metal, rows):
    pal = IRON if metal == "iron" else STONE
    colours = {c: pal[c] for c in pal}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (part, y)
        for x, c in enumerate(row):
            if c in colours:
                img.putpixel((x, y), colours[c])
    rel = PART_DIR + part + "_" + metal
    save(img, rel)
    model(rel, rel)
    return {"type": "minecraft:model", "model": "jaymospowers:" + rel}


def hot(node):
    """A head fresh off the Smithing Anvil glows orange (jaymospowers:heated_at), like every weapon part."""
    tint = -26040

    def tint_all(n):
        if isinstance(n, dict):
            n = {k: tint_all(v) for k, v in n.items()}
            if n.get("type") == "minecraft:model":
                n["tints"] = [{"type": "minecraft:constant", "value": tint}]
        elif isinstance(n, list):
            n = [tint_all(v) for v in n]
        return n
    return {"type": "minecraft:condition", "property": "minecraft:has_component", "component": "jaymospowers:heated_at",
            "on_true": tint_all(node), "on_false": node}


def recipes():
    def shapeless(name, ingredients, result, count=1):
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "equipment", "ingredients": ingredients,
            "result": {"id": "jaymospowers:" + result, "count": count}})

    def shaped(name, pattern, key, result):
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key,
            "result": {"id": "jaymospowers:" + result}})

    # the iron heads and throwables (barbed, harpoon, grapple, drill, broadhead, caltrop heads, caltrops, darts, the bear trap)
    # have no grid recipe: they are forged at the Tool Station (tinkering/WeaponParts)
    shapeless("head_bola", ["minecraft:string", "minecraft:string", "minecraft:string", "minecraft:flint"], "head_bola", 2)
    shapeless("head_net", ["minecraft:string", "minecraft:string", "minecraft:string", "minecraft:string", "minecraft:vine"], "head_net", 2)
    shapeless("head_smoke", ["minecraft:clay_ball", "minecraft:charcoal", "minecraft:string"], "head_smoke", 2)
    shapeless("head_flare", ["minecraft:blaze_powder", "minecraft:paper", "minecraft:glowstone_dust"], "head_flare", 2)
    shapeless("head_whistle", ["minecraft:bone", "minecraft:feather"], "head_whistle", 2)
    shapeless("head_fire", ["minecraft:blaze_powder", "minecraft:charcoal", "minecraft:string"], "head_fire", 2)
    shapeless("head_flash", ["minecraft:glowstone_dust", "minecraft:blaze_powder", "minecraft:paper"], "head_flash", 2)
    shapeless("head_boomerang", ["minecraft:stick", "minecraft:stick", "minecraft:string"], "head_boomerang", 2)
    shapeless("smoke_pot", ["minecraft:clay_ball", "minecraft:charcoal", "minecraft:spider_eye"], "smoke_pot", 2)
    shapeless("flash_powder", ["minecraft:glowstone_dust", "minecraft:bone_meal", "minecraft:blaze_powder"], "flash_powder", 4)
    shapeless("sneezing_powder", ["minecraft:bone_meal", "minecraft:fern", "minecraft:sugar"], "sneezing_powder", 4)
    shapeless("acid_vial", ["minecraft:glass_bottle", "minecraft:fermented_spider_eye", "minecraft:slime_ball"], "acid_vial", 1)
    shapeless("choking_vial", ["minecraft:glass_bottle", "minecraft:poisonous_potato", "minecraft:charcoal"], "choking_vial", 1)
    shaped("leather_thrower_belt", ["LLL", "S S"], {"L": "minecraft:leather", "S": "minecraft:string"}, "leather_thrower_belt")
    shaped("iron_thrower_belt", [" N ", "NBN", " N "], {"N": "minecraft:iron_nugget", "B": "jaymospowers:leather_thrower_belt"}, "iron_thrower_belt")
    shaped("steel_thrower_belt", [" N ", "NBN", " N "], {"N": "jaymospowers:steel_nugget", "B": "jaymospowers:iron_thrower_belt"}, "steel_thrower_belt")


def main():
    lang = {}
    # loose heads and the fitted arrow
    cases = []
    for head, (colour, name, tip) in HEADS.items():
        simple_item("head_" + head, head_icon(head))
        lang["item.jaymospowers.head_" + head] = name + " Arrowhead"
        lang["item.jaymospowers.headed_arrow." + head] = name + " Arrow"
        lang["tooltip.jaymospowers.head." + head] = tip
        rel = ITEM_DIR + "arrow_" + head
        save(headed_arrow(head), rel)
        model(rel, rel)
        cases.append({"when": head, "model": {"type": "minecraft:model", "model": "jaymospowers:" + rel}})
    write(os.path.join(ASSETS, "items", "headed_arrow.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases, "fallback": cases[0]["model"]}})
    lang["item.jaymospowers.headed_arrow"] = "Fitted Arrow"
    # throwables and belts
    for item, (name, tip) in THROWABLES.items():
        simple_item(item, grid_image(GRIDS[item]))
        lang["item.jaymospowers." + item] = name
        lang["tooltip.jaymospowers.throwable." + item] = tip
    for item, (strap, shine, buckle) in BELTS.items():
        simple_item(item, belt_icon(strap, shine, buckle))
    lang["item.jaymospowers.leather_thrower_belt"] = "Leather Belt of Throwables"
    lang["item.jaymospowers.iron_thrower_belt"] = "Iron Belt of Throwables"
    lang["item.jaymospowers.steel_thrower_belt"] = "Steel Belt of Throwables"
    # the Tinkers blanks
    for part, (name, rows) in BLANKS.items():
        stone = part_model(part, "stone", rows)
        iron = part_model(part, "iron", rows)
        node = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                "cases": [{"when": "stone", "model": stone}, {"when": "iron", "model": iron}], "fallback": stone}
        write(os.path.join(ASSETS, "items", part + ".json"), {"model": hot(node)})
        lang["item." + "jaymospowers." + part] = name
    recipes()
    lang.update({
        "tooltip.jaymospowers.head.fit": "Fit to %s plain arrows at the Fletching Station.",
        "tooltip.jaymospowers.head.spent": "Used up where it lands.",
        "tooltip.jaymospowers.head.kept": "Stays in the arrow: pick it up again.",
        "tooltip.jaymospowers.throwable.belt": "Throw it from the hand, or keep it in a belt of throwables.",
        "tooltip.jaymospowers.thrower_belt.slots": "Carries %s of %s kinds.",
        "tooltip.jaymospowers.thrower_belt.how": "Click throwables onto it to load it; right-click it onto an empty slot to take the picked ones out.",
        "key.jaymospowers.quiver_next": "Quiver: Next Arrowhead",
        "key.jaymospowers.belt_next": "Belt: Next Throwable",
        "key.jaymospowers.belt_throw": "Belt: Throw",
        "message.jaymospowers.quiver.none": "No quiver on you.",
        "message.jaymospowers.quiver.empty": "The quiver is empty.",
        "message.jaymospowers.quiver.top": "Next: %s x %s",
        "message.jaymospowers.belt.none": "No belt of throwables worn.",
        "message.jaymospowers.belt.empty": "The belt is empty.",
        "message.jaymospowers.belt.picked": "Belt: %s x %s (%s of %s)",
        "message.jaymospowers.shield.braced": "Braced ==========",
        "jei.jaymospowers.arrowhead_fitting": "Fitting an Arrowhead",
        "jei.jaymospowers.arrowhead_fitting.how": "At the Fletching Station: arrows in the first slot, the head in the second.",
        "jei.jaymospowers.thrower_belt": "Worn in the belt slot. Click throwables onto it to load it. Cycle the picked kind with one key and throw it with another (set both in Controls).",
    })
    write(os.path.join(ASSETS, "lang", "parts", "gear_picks.json"), lang)


if __name__ == "__main__":
    main()
