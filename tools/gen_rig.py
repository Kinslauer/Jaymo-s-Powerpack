"""Writes the Rig's assets and data (0.11.82): the armour layers, the pieces' and salvage's item textures, the Rig Forge
and the wrecks (block models and textures), their blockstates, item definitions, loot tables, the Forge's recipe and
the salvage tag. Our own art, drawn here.

    python -I tools/gen_rig.py

The armour uses the vanilla humanoid armour layout (64x32): the `humanoid` layer covers the Helm (head), the Core (body
and arms: the Arms piece is worn in the legs slot, so the arm plating is drawn with the Core) and the Thrusters (the
boots, the legs' lower boxes); the `humanoid_leggings` layer is the Arms piece's frame: a hip harness and thigh struts.
Gunmetal plates, darker seams, cyan glow (the visor, the Core's reactor, the thruster vents).

(0.11.103) On players the suit is drawn as a layered model of its own (client `rig/RigSuit`), not the vanilla armour
layers (those stay for armour stands and mobs): an under-suit with glowing seams, main plates, raised trim, vents,
pistons at the elbows and knees, the spine unit on the back and a lit chest core. `suit()` writes its texture
(`textures/entity/rig/suit.png`), the glow pass (`suit_glow.png`: only the lit pixels) and the generated
`RigSuitMesh.java` (every plate its own part, with when and from where it swings in as the suit assembles).
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


# ---- the worn suit (0.11.103): a layered model of its own (client rig/RigSuit) -------------------------------------

SUIT_W, SUIT_H = 128, 64
SUIT_JAVA = os.path.join(ROOT, "src", "client", "java", "com", "jaymospowers", "client", "rig", "RigSuitMesh.java")
# Rig.Piece's ordinals
PIECE_IDS = {"helm": 0, "core": 1, "arms": 2, "thrusters": 3}
BONE_PIVOTS = {
    "head": (0, 0, 0), "body": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
    "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0),
}
EMPTY_CHILD = {"head": "hat", "body": "jacket", "right_arm": "right_sleeve", "left_arm": "left_sleeve",
               "right_leg": "right_pants", "left_leg": "left_pants"}
MIRROR_BONE = {"right_arm": "left_arm", "right_leg": "left_leg", "body": "body", "head": "head"}
# the left side's copies stand this much further out, so mirrored plates that meet (the chest's halves, the thighs)
# never share a face
LEFT_GROW = 0.05
SUIT_PLATES = []


def plate(bone, name, piece, start, dur, pivot, boxes, came=(0, 0, 0, 0, 0, 0, 1.0), heavy=False, mirror=False):
    """One plate: a part of its own on `bone`, locked at `pivot` (bone space, pixels), with its boxes (from, size, paint,
    grow; bone space). It swings in over `dur` ticks from `start` (ticks after its piece is put on), from `came`: an
    offset (dx, dy, dz pixels), a turn (rx, ry, rz radians) and a starting scale. `heavy`: a deeper clunk as it locks.
    `mirror`: the left side's copy too (x flipped)."""
    SUIT_PLATES.append(dict(bone=bone, name=name, piece=PIECE_IDS[piece], start=start, dur=dur, pivot=pivot,
                            boxes=[dict(frm=b[0], size=b[1], paint=b[2], grow=b[3]) for b in boxes], came=came, heavy=heavy))
    if mirror:
        boxes_l = [((-f[0] - sz[0], f[1], f[2]), sz, paint_, grow + LEFT_GROW) for f, sz, paint_, grow in boxes]
        dx, dy, dz, rx, ry, rz, s0 = came
        SUIT_PLATES.append(dict(bone=MIRROR_BONE[bone], name=name[:-2] + "_l" if name.endswith("_r") else name + "_l",
                                piece=PIECE_IDS[piece], start=start, dur=dur, pivot=(-pivot[0], pivot[1], pivot[2]),
                                boxes=[dict(frm=b[0], size=b[1], paint=b[2], grow=b[3]) for b in boxes_l],
                                came=(-dx, dy, dz, rx, -ry, -rz, s0), heavy=heavy))


# -- the Core: the spine unit lands on the back first, unfolds its wings, the under-suit spreads from it, then the
#    chest's halves swing round from the back and meet, the reactor slots in between them, the abdomen, the shoulders,
#    the upper arms, last the collar
plate("body", "spine", "core", 0, 8, (0, 5, 3.3), [
    ((-2, 0.5, 2.3), (4, 10, 2), "spine", 0.0),
    ((-1.5, -1, 3), (1, 2, 1), "port", 0.0),
    ((0.5, -1, 3), (1, 2, 1), "port", 0.0),
    ((-2.5, 1.5, 3.3), (1, 8, 1), "piston", -0.1),
    ((1.5, 1.5, 3.3), (1, 8, 1), "piston", -0.1)], came=(0, -6, 10, 0.6, 0, 0, 0.2), heavy=True)
plate("body", "wing_r", "core", 6, 7, (-2, 4.5, 2.8), [((-4.1, 1, 2.3), (2, 7, 1), "plate", 0.1)],
      came=(0, 0, 0, 0, 2.8, 0, 1.0), mirror=True)
plate("body", "under_body", "core", 8, 10, (0, 4, 2), [((-4, 0, -2), (8, 12, 4), "under", 0.3)], came=(0, 0, 0, 0, 0, 0, 0.0))
plate("body", "chest_r", "core", 12, 6, (-4, 3, 2), [
    ((-4, 0.5, -3), (4, 6, 1), "plate", 0.1),
    ((-4, 0.5, -3), (4, 1, 1), "trim", 0.3),
    ((-4, 0.5, -3), (1, 6, 1), "trim", 0.25)], came=(0, 0, 0, 0, 2.8, 0, 1.0), mirror=True)
plate("body", "core", "core", 17, 5, (0, 3, -3), [
    ((-1.5, 1.5, -3.8), (3, 3, 1), "core", 0.0),
    ((-2, 1, -3.5), (4, 4, 1), "ring", 0.0)], came=(0, 0, -5, 0, 0, 0, 0.4), heavy=True)
plate("body", "abs", "core", 15, 6, (0, 9, -3), [
    ((-3.5, 7, -2.9), (7, 2, 1), "abs", 0.1),
    ((-3, 9.5, -2.9), (6, 2, 1), "abs", 0.05)], came=(0, 4, -2, 0, 0, 0, 0.6))
plate("right_arm", "under_r", "core", 10, 10, (-1, -2, 0), [((-3, -2, -2), (4, 12, 4), "under", 0.3)],
      came=(0, 0, 0, 0, 0, 0, 0.0), mirror=True)
plate("right_arm", "pauldron_r", "core", 19, 6, (-1, -2, 0), [
    ((-3.5, -2.5, -2), (4, 3, 4), "plate", 0.8),
    ((-3.5, 0.5, -2), (4, 1, 4), "trim", 0.7),
    ((-2.5, -3.8, -1), (2, 1, 2), "ridge", 0.0)], came=(0, -5, 4, -0.9, 0, 0, 0.7), heavy=True, mirror=True)
plate("right_arm", "bicep_r", "core", 23, 5, (-1, 1, 0), [((-3, 1.5, -2), (4, 3, 4), "plate2", 0.6)],
      came=(0, -4, 0, 0, 0, 0, 0.8), mirror=True)
plate("body", "collar", "core", 26, 4, (0, 0, 0), [((-3.5, 0, -2.5), (7, 1, 5), "trim", 0.2)], came=(0, -3, 0, 0, 0, 0, 0.5))

# -- the Arms (the frame): the hip harness closes from both sides and its buckle locks, the under-suit runs down the
#    legs, the thighs swing in, the gauntlets slide up the forearms, then the elbow and knee joints with their pistons
plate("body", "belt_r", "arms", 0, 6, (-2, 11, 0), [((-4, 10, -2), (4, 2, 4), "plate2", 0.55)], came=(-5, 0, 0, 0, 0, 0, 1.0),
      mirror=True)
plate("body", "buckle", "arms", 5, 3, (0, 11, -3), [((-1, 10, -3.2), (2, 2, 1), "buckle", 0.0)], came=(0, 0, -3, 0, 0, 0, 0.0),
      heavy=True)
plate("right_leg", "under_r", "arms", 4, 8, (0, 0, 0), [((-2, 0, -2), (4, 12, 4), "under", 0.3)], came=(0, 0, 0, 0, 0, 0, 0.0),
      mirror=True)
plate("right_leg", "thigh_r", "arms", 5, 6, (0, 0, 0), [
    ((-2, 0.5, -2), (4, 5, 4), "plate2", 0.6),
    ((-1.5, 1, -2.9), (3, 1, 1), "trim", 0.0)], came=(0, 0, 4, 0.8, 0, 0, 0.8), mirror=True)
plate("right_arm", "gauntlet_r", "arms", 9, 6, (-1, 10, 0), [
    ((-3, 5, -2), (4, 5, 4), "plate", 0.7),
    ((-3, 9, -2), (4, 1, 4), "cuff", 0.9),
    ((-2.5, 6, -2.9), (3, 3, 1), "trim", 0.0)], came=(0, 6, 0, 0, 0, 0, 0.6), mirror=True)
plate("right_arm", "elbow_r", "arms", 13, 4, (-1, 4, 0), [
    ((-2, 3.5, 2.2), (2, 2, 1), "cap", 0.0),
    ((-4.5, 2, -0.5), (1, 6, 1), "piston", -0.2)], came=(0, 0, 0, 0, 0, 0, 0.0), heavy=True, mirror=True)
plate("right_leg", "knee_r", "arms", 15, 4, (0, 6, -2), [
    ((-1.5, 5, -3), (3, 2, 1), "cap", 0.1),
    ((-3.2, 3, -0.5), (1, 6, 1), "piston", -0.2)], came=(0, 0, 0, 0, 0, 0, 0.0), heavy=True, mirror=True)

# -- the Thrusters: the shins rise from the ground, the boots clamp on under them
plate("right_leg", "shin_r", "thrusters", 0, 6, (0, 11, 0), [
    ((-2, 6.5, -2), (4, 4, 4), "plate", 0.7),
    ((-1, 7, -2.9), (2, 3, 1), "trim", 0.0)], came=(0, 4, 0, 0, 0, 0, 0.7), mirror=True)
plate("right_leg", "boot_r", "thrusters", 4, 6, (0, 12, 0), [
    ((-2, 10, -2), (4, 2, 4), "plate2", 0.85),
    ((-2, 11, -3), (4, 1, 1), "trim", 0.15),
    ((-1.5, 10.5, 2.2), (3, 2, 1), "nozzle", 0.0),
    ((-3.1, 10.2, -1), (1, 1, 2), "vent", 0.0)], came=(0, 3, 0, 0, 0, 0, 0.5), heavy=True, mirror=True)

# -- the Helm, last: the back shell rises up the neck, the ear pods close in, the faceplate swings down over the face
#    (its visor lit), the crest on top
plate("head", "helm_back", "helm", 0, 6, (0, 0, 4), [((-4, -8, 0), (8, 8, 4), "helm_back", 0.6)], came=(0, 5, 4, 1.0, 0, 0, 1.0))
plate("head", "ear_r", "helm", 4, 5, (-4.7, -3.5, 0), [((-5.2, -5, -1.5), (1, 3, 3), "earpod", 0.0)], came=(-3, 0, 0, 0, 0, 0, 0.5),
      mirror=True)
plate("head", "helm_face", "helm", 8, 7, (0, -8, 0), [
    ((-4, -8, -4), (8, 8, 4), "helm_face", 0.7),
    ((-3, -1.5, -5), (6, 2, 1), "grille", 0.0)], came=(0, 0, 0, -1.5, 0, 0, 1.0), heavy=True)
plate("head", "crest", "helm", 15, 3, (0, -9, 0), [((-0.5, -9.6, -3), (1, 1, 7), "ridge", 0.0)], came=(0, -2, 0, 0, 0, 0, 0.0))


def suit_boxes():
    for p in SUIT_PLATES:
        for b in p["boxes"]:
            yield p, b


def suit_pack():
    """Shelf-packs every box's UV net into the texture, tallest first."""
    boxes = [b for _, b in suit_boxes()]
    for b in boxes:
        w, h, d = b["size"]
        b["foot"] = (2 * d + 2 * w, d + h)
    x = y = shelf = 0
    for b in sorted(boxes, key=lambda b: (-b["foot"][1], -b["foot"][0])):
        fw, fh = b["foot"]
        if x + fw > SUIT_W:
            x, y, shelf = 0, y + shelf, 0
        b["uv"] = (x, y)
        x += fw
        shelf = max(shelf, fh)
    assert y + shelf <= SUIT_H, ("suit texture too small", y + shelf)


STEEL = (176, 184, 196)
STEEL_LO = (116, 122, 134)
RIVET = (150, 158, 170)
WHITE_HOT = (220, 255, 255)
SIDES = ("front", "back", "left", "right")


def suit_pixel(kind, face, x, y, w, h, rng):
    """One pixel of a box's face: (r, g, b) and whether it glows, or None to leave it see-through."""
    side = face in SIDES
    if kind == "under":
        # the flexible under-suit: a dark weave, a rib every third row, a glowing seam down each side
        if side and face in ("left", "right") and x == w // 2 and h > 4:
            return GLOW_DIM, True
        if face == "front" and w >= 8 and x in (w // 2 - 1, w // 2) and y % 3 == 1:
            return GLOW_DIM, True
        base = (44, 47, 54) if (x + y) % 2 == 0 else (36, 38, 44)
        if side and y % 3 == 0:
            base = PLATE_LO
        return jitter(base, rng, 2)[:3], False
    if kind in ("plate", "plate2", "abs", "helm_back", "helm_face", "cap"):
        base = PLATE if kind != "plate2" else (66, 71, 80)
        hi = PLATE_HI if kind != "plate2" else (96, 103, 114)
        if kind == "helm_face" and face == "front":
            # the visor: a lit band across the eyes in a dark frame, cheek vents under it, a seam down the chin
            if y in (2, 3) and 1 <= x <= w - 2:
                return (WHITE_HOT if 3 <= x <= w - 4 and y == 2 else GLOW), True
            if y in (1, 4) and 0 < x < w - 1:
                return SEAM, False
            if y in (5, 6) and x in (1, 2, w - 3, w - 2) and (x + y) % 2 == 1:
                return SEAM, False
            if y >= 5 and x in (w // 2 - 1, w // 2) and y == h - 1:
                return SEAM, False
        if kind == "helm_face" and face in ("left", "right") and y in (2, 3) and x <= 1:
            # the visor wraps a little round the sides
            return GLOW_DIM, True
        if kind == "helm_back" and face == "back" and 2 <= y <= 5 and 1 <= x <= w - 2:
            # cooling slats down the back of the head
            return (SEAM if y % 2 == 0 else PLATE_LO), False
        if kind in ("helm_back", "helm_face") and face == "top" and x == w // 2:
            return SEAM, False
        if kind == "abs" and face == "front" and x == w // 2:
            return SEAM, False
        if kind == "cap" and side and (x, y) == (w // 2, h // 2):
            return RIVET, False
        # bevelled panels: lit top and left edges, shaded bottom and right, a panel line through the bigger faces,
        # rivets in the corners
        if y == 0 or (x == 0 and side):
            c = hi
        elif y == h - 1 or (x == w - 1 and side):
            c = PLATE_LO
        elif h >= 6 and side and y == h // 2:
            c = SEAM
        elif w >= 5 and h >= 4 and (x in (1, w - 2)) and (y in (1, h - 2)):
            c = RIVET
        else:
            c = base
        return jitter(c, rng, 4)[:3], False
    if kind == "trim":
        if face == "bottom":
            return SEAM, False
        c = (150, 158, 170) if y == 0 else PLATE_HI if y < h - 1 or h == 1 else SEAM
        if side and h == 1 and x % 4 == 3:
            c = PLATE
        return jitter(c, rng, 3)[:3], False
    if kind == "ridge":
        return jitter(PLATE_HI if (x + y) % 2 == 0 else PLATE, rng, 3)[:3], False
    if kind == "spine":
        if face == "back":
            # the spine unit's battery housing: a column of lit cells, in a dark frame
            if x in (0, w - 1) or y in (0, h - 1):
                return PLATE_HI if y == 0 else PLATE_LO, False
            return (GLOW, True) if y % 2 == 1 else (GLOW_DIM, True) if y % 4 == 2 else (PLATE_LO, False)
        if face == "top":
            return jitter(PLATE_HI, rng, 3)[:3], False
        if side and y % 3 == 1:
            return SEAM, False
        return jitter(PLATE, rng, 4)[:3], False
    if kind == "port":
        if face == "top":
            return GLOW, True
        return (SEAM if y % 2 == 0 else PLATE_LO), False
    if kind == "core":
        if face == "front":
            cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
            r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            return (WHITE_HOT if r < 0.8 else GLOW), True
        return GLOW_DIM, True
    if kind == "ring":
        if face == "front":
            if x in (0, w - 1) or y in (0, h - 1):
                corner = x in (0, w - 1) and y in (0, h - 1)
                return (GLOW_DIM, True) if corner else (PLATE_HI, False)
            return SEAM, False
        return PLATE_LO, False
    if kind == "vent":
        if side:
            if y % 2 == 0:
                return (GLOW_DIM, True) if 0 < x < w - 1 else (SEAM, False)
            return PLATE_LO, False
        return SEAM, False
    if kind == "cuff":
        if face == "front" and x % 2 == 1:
            return GLOW_DIM, True
        return jitter(PLATE_HI, rng, 3)[:3], False
    if kind == "piston":
        if not side:
            return STEEL_LO, False
        if y in (0, h - 1):
            return SEAM, False
        if y == h // 2:
            # where the rod slides into its sleeve
            return PLATE_LO, False
        return (STEEL if y < h // 2 else (150, 158, 170)), False
    if kind == "nozzle":
        if face == "back":
            if x in (0, w - 1) or y in (0, h - 1):
                return SEAM, False
            return WHITE_HOT if x == w // 2 else GLOW, True
        return PLATE_LO, False
    if kind == "buckle":
        if face == "front":
            if x in (0, w - 1) or y in (0, h - 1):
                return GLOW_DIM, True
            return GLOW, True
        return PLATE_LO, False
    if kind == "grille":
        if face == "front":
            return (SEAM if x % 2 == 0 else PLATE_LO), False
        return PLATE_LO, False
    if kind == "earpod":
        if face in ("left", "right") and (x, y) == (w // 2, h // 2):
            return GLOW, True
        if side and (x in (0, w - 1) or y in (0, h - 1)):
            return PLATE, False
        return PLATE_LO, False
    raise ValueError(kind)


def suit_paint():
    rng = random.Random(0x5017)
    im = Image.new("RGBA", (SUIT_W, SUIT_H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (SUIT_W, SUIT_H), (0, 0, 0, 0))
    for _, b in suit_boxes():
        w, h, d = b["size"]
        u, v = b["uv"]
        for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
            for y in range(fh):
                for x in range(fw):
                    px = suit_pixel(b["paint"], face, x, y, fw, fh, rng)
                    if px is None:
                        continue
                    c, lit = px
                    im.putpixel((fx + x, fy + y), tuple(c) + (255,))
                    if lit:
                        glow.putpixel((fx + x, fy + y), tuple(c) + (255,))
    folder = os.path.join(ASSETS, "textures", "entity", "rig")
    save(im, os.path.join(folder, "suit.png"))
    save(glow, os.path.join(folder, "suit_glow.png"))


def jf(v):
    return ("%.2f" % v).rstrip("0").rstrip(".") + "F" if v != int(v) else "%d.0F" % v


def suit_java():
    L = []
    L.append("package com.jaymospowers.client.rig;")
    L.append("")
    L.append("import net.minecraft.client.model.geom.PartPose;")
    L.append("import net.minecraft.client.model.geom.builders.CubeDeformation;")
    L.append("import net.minecraft.client.model.geom.builders.CubeListBuilder;")
    L.append("import net.minecraft.client.model.geom.builders.LayerDefinition;")
    L.append("import net.minecraft.client.model.geom.builders.MeshDefinition;")
    L.append("import net.minecraft.client.model.geom.builders.PartDefinition;")
    L.append("")
    L.append("/**")
    L.append(" * GENERATED by {@code tools/gen_rig.py}: don't edit by hand (change the plates there and run it again). The worn")
    L.append(" * Rig's plates over the player model's skeleton, every plate its own part on its bone, UV-mapped onto")
    L.append(" * {@code textures/entity/rig/suit.png}; and how each one assembles ({@link RigSuit}).")
    L.append(" */")
    L.append("final class RigSuitMesh {")
    L.append("\tstatic final int TEX_W = %d, TEX_H = %d;" % (SUIT_W, SUIT_H))
    L.append("\t/** Each plate: its bone and its name. */")
    L.append("\tstatic final String[][] PLATES = {")
    for p in SUIT_PLATES:
        L.append("\t\t{\"%s\", \"%s\"}," % (p["bone"], p["name"]))
    L.append("\t};")
    L.append("\t/** The piece each plate belongs to (Rig.Piece's ordinal). */")
    L.append("\tstatic final int[] PIECE = {%s};" % ", ".join(str(p["piece"]) for p in SUIT_PLATES))
    L.append("\t/** When each plate starts in (ticks after its piece is put on) and how long it takes to lock. */")
    L.append("\tstatic final float[][] TIME = {")
    for p in SUIT_PLATES:
        L.append("\t\t{%s, %s}," % (jf(p["start"]), jf(p["dur"])))
    L.append("\t};")
    L.append("\t/** Where each plate comes in from: offset (pixels), turn (radians), starting scale. */")
    L.append("\tstatic final float[][] CAME = {")
    for p in SUIT_PLATES:
        L.append("\t\t{%s}," % ", ".join(jf(v) for v in p["came"]))
    L.append("\t};")
    L.append("\t/** The plates that lock on with a heavier clunk. */")
    L.append("\tstatic final boolean[] HEAVY = {%s};" % ", ".join("true" if p["heavy"] else "false" for p in SUIT_PLATES))
    spans = [0.0] * 4
    for p in SUIT_PLATES:
        spans[p["piece"]] = max(spans[p["piece"]], p["start"] + p["dur"])
    L.append("\t/** How long each piece takes to assemble (ticks, to its last plate's lock). */")
    L.append("\tstatic final float[] SPAN = {%s};" % ", ".join(jf(v) for v in spans))
    L.append("")
    L.append("\tprivate RigSuitMesh() {")
    L.append("\t}")
    L.append("")
    L.append("\t/** {@code slim}: the arm plates sit half a pixel in, over a slim arm's middle (RigSuit narrows them too). */")
    L.append("\tstatic LayerDefinition create(boolean slim) {")
    L.append("\t\tfloat rs = slim ? 0.5F : 0.0F;")
    L.append("\t\tMeshDefinition mesh = new MeshDefinition();")
    L.append("\t\tPartDefinition root = mesh.getRoot();")
    for bone, (px, py, pz) in BONE_PIVOTS.items():
        var = bone.replace("_", "")
        L.append("\t\tPartDefinition %s = root.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.offset(%s, %s, %s));"
                 % (var, bone, jf(px), jf(py), jf(pz)))
        L.append("\t\t%s.addOrReplaceChild(\"%s\", CubeListBuilder.create(), PartPose.ZERO);" % (var, EMPTY_CHILD[bone]))
    for p in SUIT_PLATES:
        var = p["bone"].replace("_", "")
        px, py, pz = p["pivot"]
        cubes = "CubeListBuilder.create()"
        for b in p["boxes"]:
            fx, fy, fz = b["frm"]
            w, h, d = b["size"]
            cubes += ".texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(%s))" % (
                b["uv"][0], b["uv"][1], jf(fx - px), jf(fy - py), jf(fz - pz), jf(w), jf(h), jf(d), jf(b["grow"]))
        x = jf(px)
        if p["bone"] == "right_arm":
            x += " + rs"
        elif p["bone"] == "left_arm":
            x += " - rs"
        L.append("\t\t%s.addOrReplaceChild(\"%s\", %s, PartPose.offset(%s, %s, %s));" % (var, p["name"], cubes, x, jf(py), jf(pz)))
    L.append("\t\treturn LayerDefinition.create(mesh, TEX_W, TEX_H);")
    L.append("\t}")
    L.append("}")
    with open(SUIT_JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(L) + "\n")


def suit():
    suit_pack()
    suit_paint()
    suit_java()

if __name__ == "__main__":
    armour()
    suit()
    items()
    blocks()
    data()
