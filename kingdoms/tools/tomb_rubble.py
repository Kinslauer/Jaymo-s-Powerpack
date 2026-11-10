"""
Tiv: "Alot of buttons are placed in structures [...] They can all be removed in place of random amounts of our rocks
and pebbles. They all are trying to go for a debris effect, but now we have real debris."

The Stormborn Tomb's templates (kingdoms/src/main/resources/data/jaymoskingdoms/structure/stormborn_tomb/*.nbt) were
strewn with stone buttons as rubble. This takes every button that does nothing out of them: most leave bare floor, and
now and then one leaves a pile of 1-3 of Powers' loose rocks (jaymospowers:<kind>_rock) on the floor block it lay on,
in that floor's own stone. A button on a wall leaves nothing (a rock can't hang on a wall). In a world without Jaymo's
Powers the rocks load as air, so the floor is just bare.

The buttons in KEEP stay: two are wired (one opens a cell's iron bars, one is a disguised trigger for a hidden wall
door), and six are carved studs on the main hall's banner standards, not rubble.

The picks are seeded per template, so a rerun gives the same tomb; a rerun on edited templates changes nothing (no
rubble buttons are left). Run from the project root: python3 -I kingdoms/tools/tomb_rubble.py
"""
import glob
import gzip
import os
import random
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOMB = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "data", "jaymoskingdoms", "structure", "stormborn_tomb")

# How often a rubble button leaves a pile of rocks, out of 1; the rest leave bare floor.
PILE_CHANCE = 0.25
# Rocks in a pile (1, 2, 3), as weights: mostly two or three.
PILE_SIZES = (30, 40, 30)
# The rock for the floor a pile lies on (by the floor block's id); anything else gets plain stone rocks.
ROCK_FOR_FLOOR = {
    "minecraft:mossy_cobblestone": "jaymospowers:mossy_rock",
    "minecraft:mossy_stone_bricks": "jaymospowers:mossy_rock",
    "minecraft:andesite": "jaymospowers:andesite_rock",
}
STONE_ROCK = "jaymospowers:stone_rock"

# Buttons that stay, by template and position in it.
KEEP = {
    ("first_hallway", (15, 6, 14)): "the spruce button by the cell: its wire turns the torch off and the pistons open the iron bars",
    ("first_hallway", (24, 5, 47)): "the only input of the wire under the corridor floor: it turns two torches off and opens the wall at x 28",
    ("main", (21, 9, 37)): "stud on a banner standard",
    ("main", (21, 9, 40)): "stud on a banner standard",
    ("main", (69, 9, 38)): "stud on a banner standard",
    ("main", (69, 9, 43)): "stud on a banner standard",
    ("main", (70, 9, 39)): "stud on a banner standard",
    ("main", (70, 9, 42)): "stud on a banner standard",
}

# --- NBT (gzipped, big-endian), read and written back tag for tag ---
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
SCALAR = {BYTE: "b", SHORT: "h", INT: "i", LONG: "q", FLOAT: "f", DOUBLE: "d"}


class Tag:
    def __init__(self, type, value, elem=END):
        self.type, self.value, self.elem = type, value, elem


class Reader:
    def __init__(self, data):
        self.data, self.at = data, 0

    def take(self, fmt):
        value = struct.unpack_from(">" + fmt, self.data, self.at)
        self.at += struct.calcsize(fmt)
        return value[0]

    def bytes(self, n):
        value = self.data[self.at:self.at + n]
        self.at += n
        return value

    def string(self):
        return self.bytes(self.take("H")).decode("utf-8")

    def tag(self, type):
        if type in SCALAR:
            return Tag(type, self.take(SCALAR[type]))
        if type == BYTE_ARRAY:
            return Tag(type, self.bytes(self.take("i")))
        if type == STRING:
            return Tag(type, self.string())
        if type == LIST:
            elem, n = self.take("b"), self.take("i")
            return Tag(type, [self.tag(elem) for _ in range(n)], elem)
        if type == COMPOUND:
            value = {}
            while (inner := self.take("b")) != END:
                name = self.string()
                value[name] = self.tag(inner)
            return Tag(type, value)
        if type in (INT_ARRAY, LONG_ARRAY):
            n, fmt = self.take("i"), "i" if type == INT_ARRAY else "q"
            return Tag(type, [self.take(fmt) for _ in range(n)])
        raise ValueError(f"unknown NBT tag type {type}")


def put_string(out, text):
    raw = text.encode("utf-8")
    out += struct.pack(">H", len(raw)) + raw


def write(out, tag):
    if tag.type in SCALAR:
        out += struct.pack(">" + SCALAR[tag.type], tag.value)
    elif tag.type == BYTE_ARRAY:
        out += struct.pack(">i", len(tag.value)) + tag.value
    elif tag.type == STRING:
        put_string(out, tag.value)
    elif tag.type == LIST:
        out += struct.pack(">bi", tag.elem, len(tag.value))
        for item in tag.value:
            write(out, item)
    elif tag.type == COMPOUND:
        for name, item in tag.value.items():
            out += struct.pack(">b", item.type)
            put_string(out, name)
            write(out, item)
        out += struct.pack(">b", END)
    else:
        fmt = "i" if tag.type == INT_ARRAY else "q"
        out += struct.pack(f">i{len(tag.value)}{fmt}", len(tag.value), *tag.value)


def load(path):
    reader = Reader(gzip.decompress(open(path, "rb").read()))
    assert reader.take("b") == COMPOUND
    return reader.string(), reader.tag(COMPOUND)


def save(path, name, root):
    out = bytearray(struct.pack(">b", COMPOUND))
    put_string(out, name)
    write(out, root)
    with open(path, "wb") as f:
        f.write(gzip.compress(bytes(out), mtime=0))


def state(name, properties=None):
    value = {"Name": Tag(STRING, name)}
    if properties:
        value["Properties"] = Tag(COMPOUND, {k: Tag(STRING, v) for k, v in properties.items()})
    return Tag(COMPOUND, value)


def rubble(piece, root):
    """Takes the rubble buttons out of one template. Returns (buttons taken, piles left, rocks left)."""
    palette = root.value["palette"].value
    blocks = root.value["blocks"].value
    names = [p.value["Name"].value for p in palette]
    at = {tuple(t.value for t in b.value["pos"].value): b for b in blocks}
    index = {}

    def state_index(name, properties=None):
        key = (name, tuple(sorted((properties or {}).items())))
        if key not in index:
            for i, p in enumerate(palette):
                props = {k: v.value for k, v in p.value.get("Properties", Tag(COMPOUND, {})).value.items()}
                if p.value["Name"].value == name and props == (properties or {}):
                    index[key] = i
                    break
            else:
                palette.append(state(name, properties))
                names.append(name)
                index[key] = len(palette) - 1
        return index[key]

    rand = random.Random(f"stormborn-tomb-rubble/{piece}")
    taken = piles = rocks = 0
    for pos in sorted(at):
        block = at[pos]
        s = block.value["state"].value
        if not names[s].endswith("_button") or (piece, pos) in KEEP:
            continue
        taken += 1
        on_floor = palette[s].value["Properties"].value["face"].value == "floor"
        below = at.get((pos[0], pos[1] - 1, pos[2]))
        if on_floor and below is not None and rand.random() < PILE_CHANCE:
            size = rand.choices((1, 2, 3), weights=PILE_SIZES)[0]
            rock = ROCK_FOR_FLOOR.get(names[below.value["state"].value], STONE_ROCK)
            block.value["state"].value = state_index(rock, {"natural": "true", "rocks": str(size), "waterlogged": "false"})
            piles += 1
            rocks += size
        else:
            block.value["state"].value = state_index("minecraft:air")

    # drop the palette entries nothing uses any more (the buttons that are gone)
    used = sorted({b.value["state"].value for b in blocks})
    if len(used) < len(palette):
        remap = {old: new for new, old in enumerate(used)}
        root.value["palette"].value = [palette[i] for i in used]
        for b in blocks:
            b.value["state"].value = remap[b.value["state"].value]
    return taken, piles, rocks


def main():
    for path in sorted(glob.glob(os.path.join(TOMB, "*.nbt"))):
        piece = os.path.splitext(os.path.basename(path))[0]
        name, root = load(path)
        taken, piles, rocks = rubble(piece, root)
        if taken:
            save(path, name, root)
            print(f"{piece}: {taken} buttons out, {piles} piles of rocks ({rocks} rocks)")


if __name__ == "__main__":
    main()
