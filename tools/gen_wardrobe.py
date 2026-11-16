"""Writes the Wardrobe's assets and data (costume/WardrobeBlock): a two-blocks-tall spruce wardrobe, a plinth at the
foot, two tall doors with iron pulls, louvred vents up top and a crown moulding. The sides, back and top are vanilla
spruce planks (no new art); the door fronts and the item sprite are painted here from nothing. Run from the repo root."""
import json
import os

from PIL import Image

R = "src/main/resources"
TEX = f"{R}/assets/jaymospowers/textures"

# spruce-like browns, darkest first, and the iron of the pulls
DEEP, DARK, MID, LIGHT, HIGH = (58, 40, 22), (84, 59, 33), (108, 78, 45), (130, 96, 56), (150, 114, 68)
IRON, IRON_DARK = (196, 196, 190), (110, 110, 106)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def grain(x, y):
    """A quiet, fixed wood grain: a few long darker streaks down each door."""
    return MID if (x * 7 + (y // 5) * 3) % 11 else DARK


def door(img, x0, x1, y0, y1, top_rail, bottom_rail):
    """One door between columns x0..x1 and rows y0..y1 (inclusive): a frame with a sunk panel."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), LIGHT if x == x0 or (y == y0 and top_rail) else DARK if x == x1 or (y == y1 and bottom_rail) else MID)
    py0, py1 = y0 + (2 if top_rail else 0), y1 - (2 if bottom_rail else 0)
    for y in range(py0, py1 + 1):
        for x in range(x0 + 2, x1 - 1):
            img.putpixel((x, y), grain(x, y))
    for x in range(x0 + 2, x1 - 1):
        if top_rail:
            img.putpixel((x, py0), DEEP)
        if bottom_rail:
            img.putpixel((x, py1), HIGH)
    for y in range(py0, py1 + 1):
        img.putpixel((x0 + 2, y), DEEP)
        img.putpixel((x1 - 2, y), HIGH)


def front(upper):
    """A door half: the lower one ends in its bottom rail, the upper one starts at its top rail and has the vents."""
    img = Image.new("RGBA", (16, 16), DEEP + (255,))
    y0, y1 = (2, 15) if upper else (0, 13)
    door(img, 1, 7, y0, y1, upper, not upper)
    door(img, 8, 14, y0, y1, upper, not upper)
    if upper:
        # louvres to air the clothes
        for y in (5, 7, 9):
            for x in list(range(3, 6)) + list(range(10, 13)):
                img.putpixel((x, y), DEEP)
                img.putpixel((x, y + 1), LIGHT)
    else:
        # the iron pulls, either side of the seam at hand height
        for y in (1, 2, 3):
            img.putpixel((6, y), IRON if y < 3 else IRON_DARK)
            img.putpixel((9, y), IRON if y < 3 else IRON_DARK)
    return img


def item():
    """The whole wardrobe front on, like a door item: crown, both doors, plinth."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(3, 13):
        img.putpixel((x, 0), LIGHT)
        img.putpixel((x, 15), DARK)
    for y in range(1, 15):
        for x in range(4, 12):
            img.putpixel((x, y), MID)
        img.putpixel((4, y), LIGHT)
        img.putpixel((11, y), DEEP)
        img.putpixel((7, y), DEEP)
        img.putpixel((8, y), LIGHT)
    for y in range(3, 13):
        img.putpixel((5, y), DARK)
        img.putpixel((10, y), DARK)
    for y in (3, 5):
        for x in (5, 6, 9, 10):
            img.putpixel((x, y), DEEP)
    img.putpixel((6, 9), IRON)
    img.putpixel((9, 9), IRON)
    for x in range(3, 13):
        img.putpixel((x, 14), DEEP)
    return img


os.makedirs(f"{TEX}/block/wardrobe", exist_ok=True)
os.makedirs(f"{TEX}/item", exist_ok=True)
front(False).save(f"{TEX}/block/wardrobe/front_lower.png")
front(True).save(f"{TEX}/block/wardrobe/front_upper.png")
item().save(f"{TEX}/item/wardrobe.png")

PLANKS = "minecraft:block/spruce_planks"


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def face(uv, tex, cull=None):
    f = {"uv": uv, "texture": tex}
    if cull:
        f["cullface"] = cull
    return f


# north is the front; the blockstate turns it to face whoever set it. Each half's boxes match its shape in WardrobeBlock.
write(f"{R}/assets/jaymospowers/models/block/wardrobe_lower.json", {
    "parent": "minecraft:block/block",
    "textures": {"particle": PLANKS, "planks": PLANKS, "front": "jaymospowers:block/wardrobe/front_lower"},
    "elements": [
        box([0, 0, 2], [16, 2, 16], {
            "north": face([0, 14, 16, 16], "#planks"), "south": face([0, 14, 16, 16], "#planks", "south"),
            "east": face([2, 14, 16, 16], "#planks", "east"), "west": face([0, 14, 14, 16], "#planks", "west"),
            "up": face([0, 2, 16, 16], "#planks"), "down": face([0, 2, 16, 16], "#planks", "down")}),
        box([1, 2, 3], [15, 16, 16], {
            "north": face([1, 0, 15, 14], "#front"), "south": face([1, 0, 15, 14], "#planks", "south"),
            "east": face([3, 0, 16, 14], "#planks"), "west": face([0, 0, 13, 14], "#planks")}),
    ],
})
write(f"{R}/assets/jaymospowers/models/block/wardrobe_upper.json", {
    "parent": "minecraft:block/block",
    "textures": {"particle": PLANKS, "planks": PLANKS, "front": "jaymospowers:block/wardrobe/front_upper"},
    "elements": [
        box([1, 0, 3], [15, 14, 16], {
            "north": face([1, 2, 15, 16], "#front"), "south": face([1, 2, 15, 16], "#planks", "south"),
            "east": face([3, 2, 16, 16], "#planks"), "west": face([0, 2, 13, 16], "#planks")}),
        box([0, 14, 2], [16, 16, 16], {
            "north": face([0, 0, 16, 2], "#planks"), "south": face([0, 0, 16, 2], "#planks", "south"),
            "east": face([2, 0, 16, 2], "#planks", "east"), "west": face([0, 0, 14, 2], "#planks", "west"),
            "up": face([0, 2, 16, 16], "#planks", "up"), "down": face([0, 2, 16, 16], "#planks")}),
    ],
})
write(f"{R}/assets/jaymospowers/models/item/wardrobe.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/wardrobe"}})
write(f"{R}/assets/jaymospowers/items/wardrobe.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/wardrobe"}})

variants = {}
for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for half in ("lower", "upper"):
        v = {"model": f"jaymospowers:block/wardrobe_{half}"}
        if y:
            v["y"] = y
        variants[f"facing={facing},half={half}"] = v
write(f"{R}/assets/jaymospowers/blockstates/wardrobe.json", {"variants": variants})

# only the lower half drops the wardrobe, as a door does
write(f"{R}/data/jaymospowers/loot_table/blocks/wardrobe.json", {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1,
        "condition": {"type": "minecraft:survives_explosion"},
        "entries": [{
            "type": "minecraft:item", "name": "jaymospowers:wardrobe",
            "condition": {"type": "minecraft:match_block", "blocks": "jaymospowers:wardrobe", "state": {"half": "lower"}},
        }],
    }],
    "random_sequence": "jaymospowers:blocks/wardrobe",
})

# a glass to dress at (the MCA editor), the Costume Table for the costumes, a chest for the weapons' pegs
write(f"{R}/data/jaymospowers/recipe/wardrobe.json", {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {"P": "minecraft:spruce_planks", "G": "minecraft:glass_pane", "T": "jaymospowers:costume_table", "C": "minecraft:chest"},
    "pattern": ["PGP", "PTP", "PCP"],
    "result": {"id": "jaymospowers:wardrobe"},
})

axe = f"{R}/data/minecraft/tags/block/mineable/axe.json"
with open(axe) as f:
    data = json.load(f)
if "jaymospowers:wardrobe" not in data["values"]:
    data["values"].append("jaymospowers:wardrobe")
write(axe, data)
