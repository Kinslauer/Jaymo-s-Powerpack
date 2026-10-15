"""Writes the Rig's assets and data (0.11.82): the armour layers, the pieces' and salvage's item textures, the Rig Forge
and the wrecks (block models and textures), their blockstates, item definitions, loot tables, the Forge's recipe and
the salvage tag. Our own art, drawn here.

    python -I tools/gen_rig.py

The armour uses the vanilla humanoid armour layout (64x32): the `humanoid` layer covers the Helm (head), the Core (body
and arms: the Arms piece is worn in the legs slot, so the arm plating is drawn with the Core) and the Thrusters (the
boots, the legs' lower boxes); the `humanoid_leggings` layer is the Arms piece's frame: a hip harness and thigh struts.
Gunmetal plates, darker seams, cyan glow (the visor, the Core's reactor, the thruster vents).
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MC_DATA = os.path.join(RES, "data", "minecraft")
NS = "jaymospowers"

PLATE = (78, 84, 94)
PLATE_HI = (112, 120, 132)
PLATE_LO = (52, 56, 64)
SEAM = (30, 32, 38)
GLOW = (90, 230, 255)
GLOW_DIM = (40, 140, 170)
RUST = (120, 70, 40)
SCORCH = (34, 26, 24)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def box_faces(u, v, w, h, d):
    """A model box's faces in its texture: name -> (x, y, width, height), as Minecraft lays them out."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def jitter(c, rng, amount=6):
    k = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + k)) for x in c) + (255,)


def plate_pixel(x, y, w, h, rng):
    """Panelled plate: seams every few pixels, a lit top edge per panel."""
    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
        return jitter(PLATE_LO, rng, 3)
    if y % 4 == 0:
        return jitter(SEAM, rng, 2)
    if y % 4 == 1:
        return jitter(PLATE_HI, rng, 4)
    return jitter(PLATE, rng, 5)


def paint(im, u, v, w, h, d, part, rng):
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = plate_pixel(x, y, fw, fh, rng)
                c = detail(part, face, x, y, fw, fh, rng) or c
                im.putpixel((fx + x, fy + y), c)


def detail(part, face, x, y, w, h, rng):
    """The glow and the trim on top of the plates."""
    if part == "head" and face == "front":
        # the visor: a band across the eyes, brighter in the middle
        if 3 <= y <= 4 and 1 <= x <= w - 2:
            return GLOW + (255,) if 2 <= x <= w - 3 else GLOW_DIM + (255,)
        if y == 6 and 2 <= x <= w - 3:
            return SEAM + (255,)
    if part == "head" and face in ("left", "right") and y == 4 and 2 <= x <= 5:
        return GLOW_DIM + (255,)
    if part == "body" and face == "front":
        # the Core's reactor: a ring in the chest
        cx, cy = (w - 1) / 2.0, 4.0
        r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if r < 1.3:
            return (220, 255, 255, 255)
        if r < 2.4:
            return GLOW + (255,)
        if r < 3.1:
            return SEAM + (255,)
        if y >= h - 2:
            return jitter(PLATE_LO, rng, 2)
    if part == "body" and face == "back" and 2 <= x <= w - 3 and 2 <= y <= 7:
        # the spine's battery housing
        return GLOW_DIM + (255,) if x in (3, w - 4) and y % 2 == 0 else jitter(PLATE_LO, rng, 3)
    if part == "arm" and face in ("front", "left", "right", "back") and y in (1, 2) and face != "back":
        # the shoulder launcher's pods
        return jitter(PLATE_HI, rng, 3)
    if part == "arm" and face == "front" and y == h - 3 and 1 <= x <= w - 2:
        return GLOW_DIM + (255,)  # the palm emitter's ring at the wrist
    if part == "leg" and y >= h - 4:
        # the boots' thrusters: vents low on every side, glowing at the sole
        if y == h - 1:
            return GLOW_DIM + (255,) if face != "top" else None
        if y == h - 3 and x % 2 == 0:
            return SEAM + (255,)
        return jitter(PLATE_LO, rng, 4)
    if part == "leg" and face == "bottom":
        return GLOW + (255,) if 1 <= x <= w - 2 and 1 <= y <= h - 2 else None
    return None


def armour():
    rng = random.Random(0x5163)
    layer = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    paint(layer, 0, 0, 8, 8, 8, "head", rng)       # the Helm
    paint(layer, 16, 16, 8, 12, 4, "body", rng)    # the Core
    paint(layer, 40, 16, 4, 12, 4, "arm", rng)     # the arm plating (drawn with the Core)
    # the boots only use the leg boxes' lower part: plate the whole leg box, the thrusters sit low
    paint(layer, 0, 16, 4, 12, 4, "leg", rng)
    save(layer, os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid", "rig.png"))

    # the Arms piece (legs slot): a hip harness and thigh struts, mostly open frame
    rng = random.Random(0xA4A5)
    legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for face, (fx, fy, fw, fh) in box_faces(16, 16, 8, 12, 4).items():
        for y in range(fh):
            for x in range(fw):
                if face in ("top", "bottom"):
                    continue
                if y >= fh - 4:
                    # the harness belt, with the power couplings
                    c = GLOW_DIM + (255,) if y == fh - 3 and x % 3 == 1 else jitter(PLATE_LO, rng, 3)
                    legs.putpixel((fx + x, fy + y), c)
    for face, (fx, fy, fw, fh) in box_faces(0, 16, 4, 12, 4).items():
        for y in range(fh):
            for x in range(fw):
                if face in ("top", "bottom"):
                    continue
                if y < 7 and (x in (0, fw - 1) or y in (0, 3, 6)):
                    # struts down the thigh, braced
                    c = jitter(PLATE_HI if y == 0 else PLATE, rng, 4)
                    legs.putpixel((fx + x, fy + y), c)
    save(legs, os.path.join(ASSETS, "textures", "entity", "equipment", "humanoid_leggings", "rig.png"))
    write(os.path.join(ASSETS, "equipment", "rig.json"), {"layers": {
        "humanoid": [{"texture": f"{NS}:rig"}], "humanoid_leggings": [{"texture": f"{NS}:rig"}]}})


# ---- item textures (16x16) ----------------------------------------------------------------------------------------

def sprite(rows, palette):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c = palette[ch]
                im.putpixel((x, y), c + (255,) if len(c) == 3 else c)
    return im


PALETTE = {"#": SEAM, "p": PLATE, "h": PLATE_HI, "l": PLATE_LO, "g": GLOW, "d": GLOW_DIM, "w": (220, 255, 255),
           "r": RUST, "s": SCORCH, "o": (200, 110, 50)}

ITEMS = {
    "helm": [
        "................",
        ".....######.....",
        "....#hhhhhh#....",
        "...#hppppppl#...",
        "..#hpppppppl#...",
        "..#hppppppppl#..",
        "..#pgggggggpl#..",
        "..#pwgggggwpl#..",
        "..#ppppppppll#..",
        "..#lp#pppp#pl#..",
        "..#lpp####ppl#..",
        "...#lppppppl#...",
        "....#llllll#....",
        ".....######.....",
        "................",
        "................"],
    "core": [
        "................",
        "...##......##...",
        "..#hh######hh#..",
        "..#hpphhhhpph#..",
        "..#pppp##pppl#..",
        "..#ppp#dd#ppl#..",
        "..#pp#dwwd#pl#..",
        "..#pp#dwwd#pl#..",
        "..#ppp#dd#ppl#..",
        "..#pppp##pppl#..",
        "..#lpppppppll#..",
        "...#lppppppl#...",
        "...#ll#dd#ll#...",
        "....###..###....",
        "................",
        "................"],
    "arms": [
        "................",
        ".##..........##.",
        "#hh#........#hh#",
        "#ph#..####..#hp#",
        "#pl#.#hhhh#.#lp#",
        "#pl##pppppl##lp#",
        ".#p#l######l#p#.",
        ".#p#........#p#.",
        ".#pl#......#lp#.",
        ".#pl#......#lp#.",
        ".#dd#......#dd#.",
        ".#pp#......#pp#.",
        ".#ll#......#ll#.",
        "..##........##..",
        "................",
        "................"],
    "thrusters": [
        "................",
        "................",
        "...####..####...",
        "...#hp#..#hp#...",
        "...#pl#..#pl#...",
        "...#pl#..#pl#...",
        "...#pl#..#pl#...",
        "..#hpl#..#hpl#..",
        "..#ppl#..#ppl#..",
        ".#hppl#..#hppl#.",
        ".#pppll#.#pppll#",
        ".#######.#######",
        "..#dgd#...#dgd#.",
        "...#g#.....#g#..",
        "....o.......o...",
        "................"],
    "salvage": [
        "................",
        "................",
        "......###.......",
        ".....#hhp#......",
        "....#hppl##.....",
        "...#hpp#d#l#....",
        "...#pp#dgd#l#...",
        "..#hpl#ddd#pl#..",
        "..#pll####ppl#..",
        "..#ll#hhpppl#...",
        "...#.#ppppl#....",
        ".....#lpll#.....",
        "......####......",
        "................",
        "................",
        "................"],
    "scrap": [
        "................",
        "................",
        "................",
        ".......##.......",
        "......#sr#......",
        ".....#slrl#.....",
        "....#srll#s#....",
        "...#sl#rs#ls#...",
        "...#lrs##srl#...",
        "....#ssrlls#....",
        ".....#lsrs#.....",
        "......####......",
        "................",
        "................",
        "................",
        "................"],
}
ITEM_IDS = {"helm": "rig_helm", "core": "rig_core", "arms": "rig_arms", "thrusters": "rig_thrusters",
            "salvage": "rig_salvage", "scrap": "fallen_rig_scrap"}


def items():
    for name, rows in ITEMS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        save(sprite(rows, PALETTE), os.path.join(ASSETS, "textures", "item", "rig", name + ".png"))
        item_id = ITEM_IDS[name]
        write(os.path.join(ASSETS, "models", "item", "rig", name + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/rig/{name}"}})
        write(os.path.join(ASSETS, "items", item_id + ".json"),
              {"model": {"type": "minecraft:model", "model": f"{NS}:item/rig/{name}"}})


# ---- blocks -------------------------------------------------------------------------------------------------------

def forge_textures():
    rng = random.Random(0xF0F6)
    top = Image.new("RGBA", (16, 16))
    side = Image.new("RGBA", (16, 16))
    front = Image.new("RGBA", (16, 16))
    bottom = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            # top: an anvil-like work plate with a glowing cradle
            if edge:
                c = jitter(SEAM, rng, 2)
            elif 4 <= x <= 11 and 4 <= y <= 11:
                ring = x in (4, 11) or y in (4, 11)
                c = GLOW + (255,) if ring and (x + y) % 2 == 0 else jitter(PLATE_LO, rng, 3) if ring else jitter(SEAM, rng, 4)
            else:
                c = jitter(PLATE_HI if y < 2 else PLATE, rng, 5)
            top.putpixel((x, y), c)
            # sides: riveted plate, a vent band
            if edge:
                c = jitter(SEAM, rng, 2)
            elif y in (3, 12) and x % 3 == 1:
                c = jitter(PLATE_HI, rng, 3)
            elif 6 <= y <= 9:
                c = GLOW_DIM + (255,) if x % 2 == 0 and y in (7, 8) else jitter(PLATE_LO, rng, 3)
            else:
                c = jitter(PLATE, rng, 6)
            side.putpixel((x, y), c)
            # front: the furnace mouth, glowing
            if edge:
                c = jitter(SEAM, rng, 2)
            elif 4 <= x <= 11 and 7 <= y <= 13:
                inner = 5 <= x <= 10 and 8 <= y <= 12
                c = ((255, 160, 60, 255) if y >= 10 else GLOW + (255,)) if inner else jitter(SEAM, rng, 2)
            elif y == 3 and 3 <= x <= 12:
                c = GLOW_DIM + (255,)
            else:
                c = jitter(PLATE, rng, 6)
            front.putpixel((x, y), c)
            bottom.putpixel((x, y), jitter(PLATE_LO if not edge else SEAM, rng, 4))
    folder = os.path.join(ASSETS, "textures", "block", "rig_forge")
    for name, im in (("top", top), ("side", side), ("front", front), ("bottom", bottom)):
        save(im, os.path.join(folder, name + ".png"))
    write(os.path.join(ASSETS, "models", "block", "rig_forge.json"), {
        "parent": "minecraft:block/cube",
        "textures": {"particle": f"{NS}:block/rig_forge/side", "north": f"{NS}:block/rig_forge/front",
                     "south": f"{NS}:block/rig_forge/side", "east": f"{NS}:block/rig_forge/side",
                     "west": f"{NS}:block/rig_forge/side", "up": f"{NS}:block/rig_forge/top",
                     "down": f"{NS}:block/rig_forge/bottom"}})


def wreck_texture(name, fallen):
    """One 16x16 sheet for every face of the slumped suit: dented plate, a dead (or cracked) visor, rust or scorch."""
    rng = random.Random(0xDEAD if not fallen else 0xFA11)
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = plate_pixel(x % 8, y % 8, 8, 8, rng)
            roll = rng.random()
            if fallen and roll < 0.35:
                c = jitter(SCORCH, rng, 6)
            elif roll < (0.18 if not fallen else 0.12):
                c = jitter(RUST, rng, 10)
            im.putpixel((x, y), c)
    # the visor strip (top-left quarter is the head's face): dead, or cracked dim glow
    for x in range(1, 7):
        im.putpixel((x, 3), (40, 50, 56, 255) if fallen else ((30, 90, 100, 255) if x % 3 else (60, 160, 180, 255)))
    # the reactor (the body's front), long dark
    for (x, y) in ((11, 11), (12, 11), (11, 12), (12, 12)):
        im.putpixel((x, y), (20, 30, 34, 255))
    save(im, os.path.join(ASSETS, "textures", "block", "rig", name + ".png"))


def wreck_model(name):
    tex = f"{NS}:block/rig/{name}"

    def faces(u0, v0):
        return {f: {"uv": [u0, v0, u0 + 8, v0 + 8], "texture": "#all"} for f in ("north", "south", "east", "west", "up", "down")}

    write(os.path.join(ASSETS, "models", "block", name + ".json"), {
        "parent": "minecraft:block/block",
        "textures": {"all": tex, "particle": tex},
        "elements": [
            # the legs, stretched out
            {"from": [3, 0, 2], "to": [7, 3, 10], "faces": faces(0, 8)},
            {"from": [9, 0, 1], "to": [13, 3, 9], "faces": faces(0, 8)},
            # the torso, slumped back against nothing
            {"from": [3, 0, 9], "to": [13, 8, 14], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 0, 12]},
             "faces": faces(8, 8)},
            # the head, lolled to one side
            {"from": [5, 7, 9], "to": [11, 13, 15], "rotation": {"angle": 22.5, "axis": "z", "origin": [8, 8, 12]},
             "faces": faces(0, 0)},
            # an arm across the lap
            {"from": [4, 3, 5], "to": [12, 5, 8], "faces": faces(8, 0)},
        ]})
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})


def blocks():
    forge_textures()
    write(os.path.join(ASSETS, "blockstates", "rig_forge.json"), {"variants": {"": {"model": f"{NS}:block/rig_forge"}}})
    write(os.path.join(ASSETS, "items", "rig_forge.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/rig_forge"}})
    for name, fallen in (("dead_rig", False), ("fallen_rig", True)):
        wreck_texture(name, fallen)
        wreck_model(name)


# ---- data ---------------------------------------------------------------------------------------------------------

def count(item, lo, hi):
    return {"type": "minecraft:item", "name": item,
            "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}


def data():
    write(os.path.join(DATA, "loot_table", "blocks", "rig_forge.json"), {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/rig_forge",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:rig_forge"}],
                   "condition": {"type": "minecraft:survives_explosion"}}]})
    # a Dead Rig: enough salvage for about one piece, and what's left of its wiring
    write(os.path.join(DATA, "loot_table", "blocks", "dead_rig.json"), {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/dead_rig",
        "pools": [{"rolls": 1, "entries": [count(f"{NS}:rig_salvage", 3, 5)]},
                  {"rolls": 1, "entries": [count("minecraft:redstone", 1, 4)]},
                  {"rolls": 1, "entries": [count("minecraft:iron_nugget", 2, 6)]}]})
    # a Fallen Rig: older, burnt, picked over
    write(os.path.join(DATA, "loot_table", "blocks", "fallen_rig.json"), {
        "type": "minecraft:block", "random_sequence": f"{NS}:blocks/fallen_rig",
        "pools": [{"rolls": 1, "entries": [count(f"{NS}:fallen_rig_scrap", 2, 3)]},
                  {"rolls": 1, "entries": [dict(count("minecraft:iron_nugget", 1, 4),
                                                  condition={"type": "minecraft:random_chance", "chance": 0.5})]}]})
    write(os.path.join(DATA, "recipe", "rig_forge.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"I": "minecraft:iron_ingot", "S": f"#{NS}:rig_salvage", "R": "minecraft:redstone_block",
                "A": "minecraft:anvil", "F": "minecraft:blast_furnace"},
        "pattern": ["ISI", "RAR", "IFI"],
        "result": {"id": f"{NS}:rig_forge"}})
    write(os.path.join(DATA, "tags", "item", "rig_salvage.json"), {"values": [f"{NS}:rig_salvage", f"{NS}:fallen_rig_scrap"]})
    blocks_ = [f"{NS}:rig_forge", f"{NS}:dead_rig", f"{NS}:fallen_rig"]
    path = os.path.join(MC_DATA, "tags", "block", "mineable", "pickaxe.json")
    old = json.load(open(path)) if os.path.exists(path) else {"values": []}
    write(path, {"values": [v for v in old["values"] if v not in blocks_] + blocks_})


if __name__ == "__main__":
    armour()
    items()
    blocks()
    data()
