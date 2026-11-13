"""
Ports the Church of Sin's one template into Jaymo's Powers as the Darkened Chapel.

Original: "Dark Fantasy: Church of Sin" by Tada_no_ahiru / bulochka_s_tiltom (MIT; Modrinth project LEQGVg4B, file
church_of_sin_Forge_v1.0.jar, Forge 1.20.1). Its structure file is kept block for block (every block in it is
vanilla); this only changes what would not load or would not fit the pack:

- Forge's leftovers go ("ForgeCaps" on block entities and entities, the forge: and reach-entity-attributes: attributes
  on the mobs).
- Supplementaries' bombs (two stacks) become gunpowder.
- The "Work In Progress" sign in the cellar goes.
- The ten icon paintings become our ten votive panels (jaymospowers:votive_1..10; tools/gen_votive_panels.py).
- The two written books are rewritten for the pack's own world (see Lore.md, "The chapel where the lantern went
  out"): a lantern-keeper's journal and a page of warning. Books only ever hint.

The file stays in its own data version (1.20.1): the game upgrades a template as it loads it, as it does for the
Stormborn Tomb's.

Run from the project root with the original jar's template:
    python3 -I tools/church_of_sin_port.py <path to>/data/church_of_sin/structures/church_of_sin.nbt
"""
import gzip
import io
import json
import os
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "jaymospowers", "structure", "darkened_chapel.nbt")

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)
_FMT = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}

JOURNAL_TITLE = "A Keeper's Journal"
JOURNAL_AUTHOR = "the keeper of the chapel"
JOURNAL = [
    "The fourth day of the leaf-fall.\n\nI have come to the chapel in the birches to keep its lantern. The last "
    "keeper left no word. The parish watches me from its doorways and will not meet my eye. They kneel readily "
    "enough,",
    "but not toward the lamp. They kneel toward the floor. The nave smells of the marsh and of cold ash, and nobody "
    "here has brought the Warden a candle in a long while.",
    "The twelfth day.\n\nI do not sleep. When I shut my eyes I am standing at the altar, reading the roll of the "
    "parish dead as a keeper should, and the dead are standing in the nave,",
    "saying each name back to me a breath before I reach it. I woke and the candle in my cell had burned to the "
    "dish. I had lit it within the hour.",
    "The nineteenth day.\n\nAt dusk a man stood at the edge of the wood. Too tall, his arms too long, and he gave "
    "back no light at all from my lantern. He looked at my window until I raised the lamp to him,",
    "and then he was not there. The dogs of the parish howled until morning. The parish did not ask me why.",
    "The thirtieth day.\n\nThe lantern will not stay lit. I trim it, I fill it, I shield it with my hands, and it "
    "goes out as though something beneath the floor had drawn a breath. There is soot on the walls that I did not "
    "make,",
    "and it moves when I do not look at it. They told me this morning, kindly, that the chapel has another keeper "
    "now, and that he comes up to them when they call. Warden, keep my light. I can no longer keep yours.",
]
WARNING_TITLE = "Nailed to the Loft Door"
WARNING_AUTHOR = "unsigned"
WARNING = [
    "Do not call on the one below.\nDo not say what he is called: he knows your breath already.\n\nHe was at the "
    "ford when the dark came. He does not say on whose side.",
    "He comes up only for his own: those whose gift has turned in the hand. He will be gentle with you. He will "
    "call it a kindness.\n\nIf your gift is still your own, light the lamp and go home.",
]


def _read_string(b):
    (n,) = struct.unpack(">H", b.read(2))
    return b.read(n).decode("utf-8")


def _read(b, t):
    """A tag's payload as [type, value]: a compound's value is a dict of name -> tag, a list's is [element type, tags]."""
    if t in _FMT:
        return [t, struct.unpack(_FMT[t], b.read(struct.calcsize(_FMT[t])))[0]]
    if t == BYTES:
        (n,) = struct.unpack(">i", b.read(4))
        return [t, b.read(n)]
    if t == STRING:
        return [t, _read_string(b)]
    if t == LIST:
        et = b.read(1)[0]
        (n,) = struct.unpack(">i", b.read(4))
        return [t, [et, [_read(b, et) for _ in range(n)]]]
    if t == COMPOUND:
        d = {}
        while True:
            tt = b.read(1)[0]
            if tt == END:
                return [t, d]
            name = _read_string(b)
            d[name] = _read(b, tt)
    if t in (INTS, LONGS):
        (n,) = struct.unpack(">i", b.read(4))
        f = "i" if t == INTS else "q"
        return [t, list(struct.unpack(">%d%s" % (n, f), b.read(n * (4 if t == INTS else 8))))]
    raise ValueError("tag type %d" % t)


def _write_string(o, s):
    raw = s.encode("utf-8")
    o.write(struct.pack(">H", len(raw)))
    o.write(raw)


def _write(o, tag):
    t, v = tag
    if t in _FMT:
        o.write(struct.pack(_FMT[t], v))
    elif t == BYTES:
        o.write(struct.pack(">i", len(v)))
        o.write(v)
    elif t == STRING:
        _write_string(o, v)
    elif t == LIST:
        et, items = v
        o.write(bytes([et if items else END]))
        o.write(struct.pack(">i", len(items)))
        for item in items:
            _write(o, item)
    elif t == COMPOUND:
        for name, child in v.items():
            o.write(bytes([child[0]]))
            _write_string(o, name)
            _write(o, child)
        o.write(bytes([END]))
    else:
        o.write(struct.pack(">i", len(v)))
        o.write(struct.pack(">%d%s" % (len(v), "i" if t == INTS else "q"), *v))


def load(path):
    b = io.BytesIO(gzip.open(path, "rb").read())
    assert b.read(1)[0] == COMPOUND
    _read_string(b)
    return _read(b, COMPOUND)


def save(root, path):
    o = io.BytesIO()
    o.write(bytes([COMPOUND]))
    _write_string(o, "")
    _write(o, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # mtime 0: the same input always gives the same file
    with open(path, "wb") as f, gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as z:
        z.write(o.getvalue())


def walk(tag, visit):
    """Calls visit on every compound in the tree, children first."""
    t, v = tag
    if t == COMPOUND:
        for child in list(v.values()):
            walk(child, visit)
        visit(v)
    elif t == LIST:
        for child in v[1]:
            walk(child, visit)


def book(compound, title, author, pages):
    tag = compound["tag"][1]
    tag["title"] = [STRING, title]
    tag["author"] = [STRING, author]
    tag["pages"] = [LIST, [STRING, [[STRING, json.dumps({"text": page})] for page in pages]]]


def main():
    root = load(sys.argv[1])
    data = root[1]
    palette = data["palette"][1][1]
    counts = {"caps": 0, "attributes": 0, "bombs": 0, "signs": 0, "paintings": 0, "books": 0}

    def state(name):
        for i, entry in enumerate(palette):
            if entry[1]["Name"][1] == name and "Properties" not in entry[1]:
                return i
        palette.append([COMPOUND, {"Name": [STRING, name]}])
        return len(palette) - 1

    def clean(compound):
        if "ForgeCaps" in compound:
            del compound["ForgeCaps"]
            counts["caps"] += 1
        attributes = compound.get("Attributes")
        if attributes is not None and attributes[0] == LIST:
            kept = [a for a in attributes[1][1] if a[1]["Name"][1].startswith("minecraft:")]
            counts["attributes"] += len(attributes[1][1]) - len(kept)
            attributes[1][1] = kept
        item = compound.get("id")
        if item is not None and item[0] == STRING:
            if item[1] == "supplementaries:bomb":
                item[1] = "minecraft:gunpowder"
                counts["bombs"] += 1
            elif item[1] == "minecraft:written_book" and "tag" in compound:
                first = json.loads(compound["tag"][1]["pages"][1][1][0][1])["text"]
                if first.startswith("October"):
                    book(compound, JOURNAL_TITLE, JOURNAL_AUTHOR, JOURNAL)
                else:
                    book(compound, WARNING_TITLE, WARNING_AUTHOR, WARNING)
                counts["books"] += 1
            elif item[1] == "minecraft:painting":
                variant = compound["variant"][1]
                assert variant.startswith("church_of_sin:icon_"), variant
                compound["variant"][1] = "jaymospowers:votive_" + variant.rsplit("_", 1)[1]
                counts["paintings"] += 1

    walk(root, clean)

    air = state("minecraft:air")
    for block in data["blocks"][1][1]:
        nbt = block[1].get("nbt")
        if nbt is None or "front_text" not in nbt[1]:
            continue
        lines = [json.loads(m[1]).get("text", "") for m in nbt[1]["front_text"][1]["messages"][1][1]]
        if "Work In Progress" in lines:
            block[1]["state"][1] = air
            del block[1]["nbt"]
            counts["signs"] += 1

    assert counts["bombs"] == 2 and counts["signs"] == 1 and counts["paintings"] == 10 and counts["books"] == 2, counts
    save(root, OUT)
    print("wrote", os.path.relpath(OUT, ROOT), counts)


if __name__ == "__main__":
    main()
