"""Ports Illager Structures (GNU GPL v3, by the Dawn of Time team: TheGoldenWorld and Jackie;
github.com/Dawn-of-Time-Project/illagerstructures, branch 26.3) into Jaymo's Kingdoms (also GPL v3), as the illager sites
(Andy, 2026-11-13: "can you add these? ... not all of them will be illagers").

    python -I tools/illager_sites_port.py <checkout of illagerstructures, branch 26.3>

What it writes (all under kingdoms/src/main/resources/data/jaymoskingdoms unless said):
- structure/illager_sites/<name>.nbt: each template with its mobs taken out (their spots kept as posts, below), the blocks
  from Amendments (a mod we don't ship) swapped for vanilla ones, every container pointed at the site's "unheld" junk
  table, the Ruined Church's suspicious gravel at our own archaeology table, and the Monk Tower's framed Totem of
  Undying swapped for a book (totems are no loot of ours). Animals, paintings, armour stands and item frames stay.
- worldgen/structure, template_pool and structure_set/illager_sites/*: as the mod's, without the Mohai Illager (Andy left
  it out); the Tipi places the single tipi it was meant to (the mod placed the tipi camp's build twice).
- tags/worldgen/biome/illager_sites/*: the mod's biome lists; tags/worldgen/structure/illager_sites.json: all of them.
- kingdoms/src/main/java/com/jaymoskingdoms/SitePosts.java: where the mod's mobs stood in each template (and what they
  were), for IllagerSites to settle its dwellers there.
"""
import gzip
import io
import json
import os
import struct
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "data", "jaymoskingdoms")
JAVA = os.path.join(ROOT, "kingdoms", "src", "main", "java", "com", "jaymoskingdoms", "SitePosts.java")
NS = "jaymoskingdoms"
LEFT_OUT = {"mohai_illager"}
# the structure → the template it places (the mod's tipi placed tipi_illager, the tipi camp's build)
TEMPLATE = {"tipi": "tipi"}
# which of the mod's chest tables each structure's containers used (for the junk they hold before anyone holds it)
JUNK = {"soldier_chest": "unheld_camp", "pirate_chest": "unheld_sea", "monk_chest": "unheld_retreat"}
# our own sites under illager_sites/ (not the mod's), kept in the structure tag: the arena (tools/arena_site.py)
OURS = {"arena"}
SALT_SHIFT = 7  # our sets' salts, so a world with the mod as well doesn't stack them

# ---- NBT ---------------------------------------------------------------------------------------------------------------
END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


class Tag:
    """A value with its NBT type, so it is written back as it was read."""

    def __init__(self, t, v):
        self.t, self.v = t, v


def read_payload(b, i, t):
    if t == BYTE:
        return struct.unpack_from(">b", b, i)[0], i + 1
    if t == SHORT:
        return struct.unpack_from(">h", b, i)[0], i + 2
    if t == INT:
        return struct.unpack_from(">i", b, i)[0], i + 4
    if t == LONG:
        return struct.unpack_from(">q", b, i)[0], i + 8
    if t == FLOAT:
        return struct.unpack_from(">f", b, i)[0], i + 4
    if t == DOUBLE:
        return struct.unpack_from(">d", b, i)[0], i + 8
    if t == BYTES:
        n = struct.unpack_from(">i", b, i)[0]
        return b[i + 4:i + 4 + n], i + 4 + n
    if t == STRING:
        n = struct.unpack_from(">H", b, i)[0]
        return b[i + 2:i + 2 + n].decode("utf-8"), i + 2 + n
    if t == LIST:
        et = b[i]
        n = struct.unpack_from(">i", b, i + 1)[0]
        i += 5
        out = []
        for _ in range(n):
            v, i = read_payload(b, i, et)
            out.append(Tag(et, v))
        return (et, out), i
    if t == COMPOUND:
        d = {}
        while True:
            tt = b[i]
            i += 1
            if tt == END:
                return d, i
            k, i = read_payload(b, i, STRING)
            v, i = read_payload(b, i, tt)
            d[k] = Tag(tt, v)
    if t == INTS:
        n = struct.unpack_from(">i", b, i)[0]
        return list(struct.unpack_from(">%di" % n, b, i + 4)), i + 4 + 4 * n
    if t == LONGS:
        n = struct.unpack_from(">i", b, i)[0]
        return list(struct.unpack_from(">%dq" % n, b, i + 4)), i + 4 + 8 * n
    raise ValueError(t)


def write_payload(out, t, v):
    if t == BYTE:
        out.write(struct.pack(">b", v))
    elif t == SHORT:
        out.write(struct.pack(">h", v))
    elif t == INT:
        out.write(struct.pack(">i", v))
    elif t == LONG:
        out.write(struct.pack(">q", v))
    elif t == FLOAT:
        out.write(struct.pack(">f", v))
    elif t == DOUBLE:
        out.write(struct.pack(">d", v))
    elif t == BYTES:
        out.write(struct.pack(">i", len(v)))
        out.write(v)
    elif t == STRING:
        e = v.encode("utf-8")
        out.write(struct.pack(">H", len(e)))
        out.write(e)
    elif t == LIST:
        et, items = v
        out.write(struct.pack(">bi", et if items else (et or END), len(items)))
        for item in items:
            write_payload(out, et, item.v)
    elif t == COMPOUND:
        for k, tag in v.items():
            out.write(struct.pack(">b", tag.t))
            write_payload(out, STRING, k)
            write_payload(out, tag.t, tag.v)
        out.write(b"\x00")
    elif t == INTS:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(">%di" % len(v), *v))
    elif t == LONGS:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(">%dq" % len(v), *v))


def load(path):
    b = gzip.open(path).read()
    assert b[0] == COMPOUND
    _, i = read_payload(b, 1, STRING)
    v, _ = read_payload(b, i, COMPOUND)
    return v


def save(path, root):
    buf = io.BytesIO()
    buf.write(b"\x0a")
    write_payload(buf, STRING, "")
    write_payload(buf, COMPOUND, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(buf.getvalue())


def s(tag):
    return tag.v if tag is not None else None


# ---- the template fixes ------------------------------------------------------------------------------------------------

def fix_palette(palette, blocks, name):
    """Amendments' wall lanterns become wall torches (same facing), its hanging banners hanging lanterns (chains where
    nothing is above). Returns how many were swapped."""
    states = palette.v[1]
    above_solid = {}
    pos_state = {}
    for b in blocks.v[1]:
        p = tuple(b.v["pos"].v[1][k].v for k in range(3))
        pos_state[p] = b.v["state"].v
    swapped = 0
    lantern_states = {}
    for idx, st in enumerate(states):
        nm = st.v["Name"].v
        props = st.v.get("Properties")
        pv = {k: t.v for k, t in props.v.items()} if props else {}
        if nm == "amendments:wall_lantern":
            st.v["Name"] = Tag(STRING, "minecraft:wall_torch")
            st.v["Properties"] = Tag(COMPOUND, {"facing": Tag(STRING, pv.get("facing", "north"))})
            swapped += sum(1 for v in pos_state.values() if v == idx)
        elif nm.startswith("amendments:ceiling_banner_"):
            lantern_states[idx] = True
        elif nm.startswith("amendments:"):
            raise SystemExit("%s: unhandled block %s" % (name, nm))
    if lantern_states:
        # hanging lanterns need a block above; per block, a chain where there is none
        solid = {p for p, v in pos_state.items() if states[v].v["Name"].v not in ("minecraft:air", "minecraft:structure_void", "minecraft:cave_air")}
        lantern = len(states)
        states.append(Tag(COMPOUND, {"Name": Tag(STRING, "minecraft:lantern"),
                                     "Properties": Tag(COMPOUND, {"hanging": Tag(STRING, "true"), "waterlogged": Tag(STRING, "false")})}))
        chain = len(states)
        states.append(Tag(COMPOUND, {"Name": Tag(STRING, "minecraft:iron_chain"),
                                     "Properties": Tag(COMPOUND, {"axis": Tag(STRING, "y"), "waterlogged": Tag(STRING, "false")})}))
        for b in blocks.v[1]:
            st = b.v["state"].v
            if st in lantern_states:
                p = tuple(b.v["pos"].v[1][k].v for k in range(3))
                up = (p[0], p[1] + 1, p[2])
                b.v["state"] = Tag(INT, lantern if up in solid else chain)
                swapped += 1
        # the old states stay in the palette, unused (indices must not move)
        for idx in lantern_states:
            states[idx] = Tag(COMPOUND, {"Name": Tag(STRING, "minecraft:air")})
    return swapped


def fix_containers(blocks, structure, junk_tables):
    """Every container to the structure's unheld junk table; suspicious gravel to our archaeology table."""
    n = 0
    for b in blocks.v[1]:
        nb = b.v.get("nbt")
        if nb is None or "LootTable" not in nb.v:
            continue
        old = nb.v["LootTable"].v
        if old.startswith("jvs:"):
            nb.v["LootTable"] = Tag(STRING, "%s:archaeology/illager_sites/church" % NS)
        else:
            key = old.split("/")[-1]
            nb.v["LootTable"] = Tag(STRING, "%s:chests/illager_sites/%s" % (NS, JUNK[key]))
            junk_tables.add(JUNK[key])
        n += 1
    return n


PENNED = ("fence", "iron_bars")


def post_kind(eid, nbt, near):
    """What stood there: R ranged, M melee, L leader, C caster, B beast, W worker, P penned (a captive's spot)."""
    if eid == "minecraft:pillager":
        return "R"
    if eid == "minecraft:vindicator":
        name = nbt.get("CustomName")
        return "L" if name is not None and "Leader" in json.dumps(str(name.v)) else "M"
    if eid in ("minecraft:evoker", "minecraft:illusioner", "minecraft:witch"):
        return "C"
    if eid == "minecraft:ravager":
        return "B"
    if eid in ("minecraft:villager", "minecraft:iron_golem"):
        return "P" if near >= 9 else "W"
    return None


KEEP = {"minecraft:chicken", "minecraft:pig", "minecraft:polar_bear", "minecraft:painting", "minecraft:armor_stand",
        "minecraft:item_frame", "minecraft:glow_item_frame"}


def fix_entities(root, name):
    """Takes the mobs out (their spots become posts); keeps animals and fittings."""
    blocks = root["blocks"].v[1]
    palette = root["palette"].v[1]
    grid = {}
    for b in blocks:
        p = tuple(b.v["pos"].v[1][k].v for k in range(3))
        grid[p] = palette[b.v["state"].v].v["Name"].v
    posts = []
    kept = []
    for e in root["entities"].v[1]:
        nbt = e.v["nbt"].v
        eid = nbt["id"].v
        bp = tuple(x.v for x in e.v["blockPos"].v[1])
        if eid in KEEP:
            if eid.endswith("item_frame") and "Item" in nbt and nbt["Item"].v.get("id") and nbt["Item"].v["id"].v == "minecraft:totem_of_undying":
                nbt["Item"].v["id"] = Tag(STRING, "minecraft:book")
            # (Forge's own attributes and capabilities mean nothing here, and only fill the log on load)
            for key in ("Attributes", "attributes", "ForgeCaps", "ForgeData", "neoforge:attachments"):
                nbt.pop(key, None)
            kept.append(e)
            continue
        near = 0
        for dx in range(-2, 3):
            for dy in range(-1, 3):
                for dz in range(-2, 3):
                    nm = grid.get((bp[0] + dx, bp[1] + dy, bp[2] + dz), "")
                    if any(k in nm for k in PENNED):
                        near += 1
        kind = post_kind(eid, nbt, near)
        if kind is not None:
            posts.append((bp[0], bp[1], bp[2], kind))
    root["entities"] = Tag(LIST, (COMPOUND, kept))
    return posts


# ---- worldgen JSON -----------------------------------------------------------------------------------------------------

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main(src):
    mod = os.path.join(src, "common", "src", "main", "resources", "data", "illagerstructures")
    out_struct = os.path.join(DATA, "structure", "illager_sites")
    sets = {}
    for f in sorted(os.listdir(os.path.join(mod, "worldgen", "structure_set"))):
        sets[f[:-5]] = json.load(open(os.path.join(mod, "worldgen", "structure_set", f)))
    all_posts = {}
    sizes = {}
    junk_tables = set()
    report = []
    for f in sorted(os.listdir(os.path.join(mod, "worldgen", "structure"))):
        name = f[:-5]
        if name in LEFT_OUT:
            continue
        st = json.load(open(os.path.join(mod, "worldgen", "structure", f)))
        pool = json.load(open(os.path.join(mod, "worldgen", "template_pool", name + ".json")))
        template = TEMPLATE.get(name, pool["elements"][0]["element"]["location"].split(":")[1])
        root = load(os.path.join(mod, "structure", template + ".nbt"))
        swapped = fix_palette(root["palette"], root["blocks"], name)
        chests = fix_containers(root["blocks"], name, junk_tables)
        posts = fix_entities(root, name)
        save(os.path.join(out_struct, name + ".nbt"), root)
        all_posts[name] = posts
        sizes[name] = [t.v for t in root["size"].v[1]]
        report.append("%s: %d posts, %d containers, %d blocks swapped" % (name, len(posts), chests, swapped))
        # the structure, its pool
        st["biomes"] = "#%s:illager_sites/%s" % (NS, st["biomes"].split(":")[1])
        st["start_pool"] = "%s:illager_sites/%s" % (NS, name)
        write_json(os.path.join(DATA, "worldgen", "structure", "illager_sites", name + ".json"), st)
        pool["name"] = "%s:illager_sites/%s" % (NS, name)
        pool["elements"] = [{"element": {"element_type": "minecraft:single_pool_element", "processors": "minecraft:empty",
                                         "projection": "rigid", "location": "%s:illager_sites/%s" % (NS, name)}, "weight": 1}]
        write_json(os.path.join(DATA, "worldgen", "template_pool", "illager_sites", name + ".json"), pool)
    for set_name, data in sets.items():
        data["structures"] = [{"structure": "%s:illager_sites/%s" % (NS, e["structure"].split(":")[1]), "weight": e["weight"]}
                              for e in data["structures"] if e["structure"].split(":")[1] not in LEFT_OUT]
        data["placement"]["salt"] = data["placement"]["salt"] + SALT_SHIFT
        write_json(os.path.join(DATA, "worldgen", "structure_set", "illager_sites", set_name + ".json"), data)
    for f in sorted(os.listdir(os.path.join(mod, "tags", "worldgen", "biome"))):
        if f.endswith(".json"):
            write_json(os.path.join(DATA, "tags", "worldgen", "biome", "illager_sites", f), json.load(open(os.path.join(mod, "tags", "worldgen", "biome", f))))
    write_json(os.path.join(DATA, "tags", "worldgen", "structure", "illager_sites.json"),
               {"values": ["%s:illager_sites/%s" % (NS, n) for n in sorted(set(all_posts) | OURS)]})
    write_java(all_posts, sizes)
    print("\n".join(report))
    print("junk tables used:", sorted(junk_tables))


def write_java(all_posts, sizes):
    lines = [
        "package com.jaymoskingdoms;",
        "",
        "import java.util.Map;",
        "",
        "/**",
        " * Generated by {@code tools/illager_sites_port.py}: don't edit by hand. Where Illager Structures' own mobs stood in each",
        " * of its templates (GNU GPL v3, by the Dawn of Time team), and what they were: {@code R} a pillager, {@code M} a",
        " * vindicator, {@code L} the raid leader, {@code C} an evoker, illusioner or witch, {@code B} a ravager, {@code W} a",
        " * villager at work, {@code P} someone penned in (a captive's spot). {@link IllagerSites} settles its dwellers there.",
        " */",
        "final class SitePosts {",
        "\t/** A post: its spot in the template, and what stood there. */",
        "\trecord Post(int x, int y, int z, char kind) {",
        "\t}",
        "",
        "\tprivate SitePosts() {",
        "\t}",
        "",
        "\t/** Structure path (under {@code illager_sites/}) → its template's posts. */",
        "\tstatic final Map<String, Post[]> POSTS = Map.ofEntries(",
    ]
    entries = []
    for name in sorted(all_posts):
        posts = ", ".join("new Post(%d, %d, %d, '%s')" % p for p in all_posts[name])
        entries.append("\t\tMap.entry(\"%s\", new Post[] {%s})" % (name, posts))
    lines.append(",\n".join(entries))
    lines.append("\t);")
    lines.append("")
    lines.append("\t/** Structure path → its template's size (x, y, z). */")
    lines.append("\tstatic final Map<String, int[]> SIZES = Map.ofEntries(")
    lines.append(",\n".join("\t\tMap.entry(\"%s\", new int[] {%d, %d, %d})" % (n, *sizes[n]) for n in sorted(sizes)))
    lines.append("\t);")
    lines.append("}")
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
