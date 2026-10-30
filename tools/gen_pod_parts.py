"""Writes the space pod parts' assets (0.18.49, power/zfighter/pod/PodParts): for each of the seven part blocks
(pod_keel, pod_fin, pod_bell, pod_tank, pod_plate, pod_canopy, pod_mast) a 16x16 texture, a block model, a blockstate,
an item definition and a loot table (drops itself); and for the four metal parts that are forged (fin, plate, mast, tank)
the blank's textures, models and item definition (WeaponParts kinds pod_<part>_blank, shown stone and metal).

(0.18.51) Every part block also has a worn state (property damaged: a bent, scorched, cracked texture, pod_<part>_worn) that
its item keeps (block_state component), and the keel a mend grade (property grade 0 to 3: a brass band on its post for each
grade, models pod_keel_g1..g3). tools/gen_pod_mending.py draws the salvage rack and the three world parts.

    python -I tools/gen_pod_parts.py

The boxes in BOXES must match PodParts.shapeOf (pixels of 16). Model faces reference #variables, never a raw sprite id
(26.3 draws a raw id purple). Palette: iron, brass, bone, glass, red.
"""
import json
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import weapon_parts_assets as wp  # noqa: E402  (its helpers: drawn heads, stone recolour, the hot-tinted item definition)

ROOT = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "jaymospowers")
DATA = os.path.join(ROOT, "data", "jaymospowers")

IRON, BRASS, BONE = (74, 78, 86), (176, 134, 62), (226, 222, 210)
GLASS, RED = (120, 190, 230), (170, 46, 40)
SCORCH = (58, 46, 40)

PARTS = ["keel", "fin", "bell", "tank", "plate", "canopy", "mast"]
# the texture a part is drawn with (#all) and the one the accents use (#band: red band, rivets, beacon)
BASE = {"keel": IRON, "fin": IRON, "bell": BRASS, "tank": BRASS, "plate": BONE, "canopy": GLASS, "mast": IRON}
# element boxes, [x1, y1, z1, x2, y2, z2, texture variable]
BOXES = {
    "keel": [(2, 0, 2, 14, 3, 14, "all"), (6, 3, 6, 10, 11, 10, "all")],
    "fin": [(6, 0, 2, 10, 2, 10, "all"), (7, 2, 2, 9, 11, 6, "all")],
    "bell": [(4, 0, 4, 12, 3, 12, "all"), (5, 3, 5, 11, 7, 11, "all"), (6, 7, 6, 10, 11, 10, "all")],
    "tank": [(4, 0, 4, 12, 13, 12, "all"), (3, 5, 3, 13, 8, 13, "band")],
    "plate": [(1, 1, 6, 15, 15, 9, "all")],
    "canopy": [(3, 0, 3, 13, 3, 13, "frame"), (4, 3, 4, 12, 9, 12, "all"), (6, 9, 6, 10, 12, 10, "all")],
    "mast": [(7, 0, 7, 9, 13, 9, "all"), (6, 13, 6, 10, 16, 10, "band")],
}
FACES = ("north", "east", "south", "west", "up", "down")


def shade(colour, amount):
    return tuple(max(0, min(255, c + amount)) for c in colour)


def texture(part):
    """A 16x16 plate of the part's colour: darker edge, a few lighter flecks (fixed seed, so reruns match)."""
    rng = random.Random(part)
    base = BASE[part]
    img = Image.new("RGBA", (16, 16), base + (255,))
    for y in range(16):
        for x in range(16):
            amount = rng.randint(-6, 6)
            if x in (0, 15) or y in (0, 15):
                amount -= 22
            img.putpixel((x, y), shade(base, amount) + (255,))
    if part == "plate":
        for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
            img.putpixel((x, y), shade(IRON, 20) + (255,))
    if part == "canopy":
        for i in range(16):
            img.putpixel((i, 8), shade(IRON, 10) + (255,))
            img.putpixel((8, i), shade(IRON, 10) + (255,))
        for x, y in ((3, 3), (4, 3), (3, 4)):
            img.putpixel((x, y), (214, 238, 250, 255))
    if part == "keel":
        for i in range(2, 14, 4):
            img.putpixel((i, 7), shade(BRASS, 0) + (255,))
    return img


def solid(colour, rivets=None):
    img = Image.new("RGBA", (16, 16), colour + (255,))
    for y in range(16):
        for x in range(16):
            if y in (0, 15):
                img.putpixel((x, y), shade(colour, -26) + (255,))
    return img


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def model(part, worn=False):
    used = sorted({box[6] for box in BOXES[part]} | {"all"})
    tex = {v: "jaymospowers:block/pod_" + (part if v == "all" else "band" if v == "band" else part + "_frame") for v in used}
    if worn:
        tex["all"] += "_worn"
    tex["particle"] = tex["all"]
    elements = [{"from": list(b[:3]), "to": list(b[3:6]), "faces": {f: {"texture": "#" + b[6]} for f in FACES}} for b in BOXES[part]]
    return {"textures": tex, "elements": elements}


def worn_texture(part):
    """The part's texture gone dull: darker, scorched in patches, with cracks (fixed seed, so reruns match)."""
    rng = random.Random(part + "-worn")
    img = texture(part).convert("RGBA")
    px = img.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = tuple(int(c * 0.78) for c in (r, g, b)) + (a,)
    for _ in range(3):
        cx, cy = rng.randint(2, 13), rng.randint(2, 13)
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                if dx * dx + dy * dy <= 4 and rng.random() < 0.7:
                    x, y = cx + dx, cy + dy
                    if 0 <= x < 16 and 0 <= y < 16:
                        px[x, y] = shade(SCORCH, rng.randint(-6, 6)) + (255,)
    for _ in range(2):
        x, y = rng.randint(1, 14), 0
        while y < 16:
            px[x, y] = (22, 22, 26, 255)
            y += 1
            x = max(0, min(15, x + rng.choice((-1, 0, 0, 1))))
    return img


def keel_model(grade):
    """The keel with a brass band round its post for each grade (the bands sit just outside the post, 6 to 10)."""
    base = model("keel")
    base["textures"]["brass"] = "jaymospowers:block/pod_brass"
    for g in range(grade):
        y = 4 + 2 * g
        base["elements"].append({"from": [5, y, 5], "to": [11, y + 1, 11], "faces": {f: {"texture": "#brass"} for f in FACES}})
    return base


def select(prop, cases, fallback):
    """An item definition that picks its model from a block_state property of the stack."""
    return {"model": {"type": "minecraft:select", "property": "minecraft:block_state", "block_state_property": prop,
                      "cases": [{"when": when, "model": {"type": "minecraft:model", "model": "jaymospowers:block/" + name}} for when, name in cases],
                      "fallback": {"type": "minecraft:model", "model": "jaymospowers:block/" + fallback}}}


def blocks():
    for part in PARTS:
        name = "pod_" + part
        save(texture(part), "block/" + name)
        write(os.path.join(ASSETS, "models", "block", name + ".json"), model(part))
        # (the keel is only worn on a salvage rack; as a block it carries its grade instead)
        save(worn_texture(part), "block/" + name + "_worn")
        write(os.path.join(ASSETS, "models", "block", name + "_worn.json"), model(part, worn=True))
        props = ["damaged"]
        if part == "keel":
            for grade in (1, 2, 3):
                write(os.path.join(ASSETS, "models", "block", "%s_g%d.json" % (name, grade)), keel_model(grade))
            write(os.path.join(ASSETS, "blockstates", name + ".json"), {"multipart": [
                {"when": {"grade": str(grade)}, "apply": {"model": "jaymospowers:block/" + name + ("_g%d" % grade if grade else "")}}
                for grade in (0, 1, 2, 3)]})
            write(os.path.join(ASSETS, "items", name + ".json"), select("grade", [(str(g), "%s_g%d" % (name, g)) for g in (1, 2, 3)], name))
            props.append("grade")
        else:
            write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
                "damaged=false": {"model": "jaymospowers:block/" + name},
                "damaged=true": {"model": "jaymospowers:block/" + name + "_worn"}}})
            write(os.path.join(ASSETS, "items", name + ".json"), select("damaged", [("true", name + "_worn")], name))
        write(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                       "entries": [{"type": "minecraft:item", "name": "jaymospowers:" + name,
                                    "modifier": [{"type": "minecraft:copy_state", "block": "jaymospowers:" + name, "properties": props}]}]}],
            "random_sequence": "jaymospowers:blocks/" + name})
    save(solid(RED), "block/pod_band")
    save(solid(IRON), "block/pod_canopy_frame")
    save(solid(BRASS), "block/pod_brass")


# the blanks: 16x16 heads like the smith's ('#' outline, 'x' metal, 'o' shine), added to the weapon part drawings
BLANKS = {
    "pod_fin_blank": ("steel", [
        "................",
        "......##........",
        ".....#oo#.......",
        ".....#ox#.......",
        ".....#ox#.......",
        "....#oxx#.......",
        "....#oxx#.......",
        "...#oxxx#.......",
        "...#oxxx#.......",
        "..#oxxxx########",
        "..#oxxxxxxxxxxx#",
        "..#xxxxxxxxxxx#.",
        "...#############",
        "................",
        "................",
        "................"]),
    "pod_plate_blank": ("iron", [
        "................",
        "................",
        "..############..",
        ".#oooooooooooo#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        ".#oxxxxxxxxxxx#.",
        "..############..",
        "................",
        "................",
        "................"]),
    "pod_mast_blank": ("iron", [
        "................",
        "......##........",
        ".....#oo#.......",
        ".....#ox#.......",
        "......#x#.......",
        "......#x#.......",
        "......#x#.......",
        "......#x#.......",
        "......#x#.......",
        "......#x#.......",
        "......#x#.......",
        ".....#oxx#......",
        ".....#xxx#......",
        "......###.......",
        "................",
        "................"]),
    "pod_tank_blank": ("copper", [
        "................",
        "................",
        "....########....",
        "...#oooooooo#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#oxxxxxxx#...",
        "...#xxxxxxxx#...",
        "....########....",
        "................",
        "................"]),
}


def blanks():
    for part, (metal, rows) in BLANKS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), part
        wp.DRAWN[part] = rows
        heads = {"stone": wp.as_stone(wp.drawn(part, "iron")), metal: wp.drawn(part, metal)}
        cases = []
        for m, img in heads.items():
            rel = wp.OUT + part + "_" + m
            save(img, rel)
            write(os.path.join(ASSETS, "models", rel + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + rel}})
            cases.append({"when": m, "model": {"type": "minecraft:model", "model": "jaymospowers:" + rel}})
        write(os.path.join(ASSETS, "items", part + ".json"), {"model": wp.hot({
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
            "fallback": cases[0]["model"]})})


if __name__ == "__main__":
    blocks()
    blanks()
