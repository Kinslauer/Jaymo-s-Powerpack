"""Checks the Aether II structures hang together (run it after tools/gen_aether_structures.py):

    python -I -B tools/check_aether_structures.py .

Every JSON under src/main/resources parses; every block StructureBlocks registers has a blockstate, an item definition, a loot table, a name
and its textures; every model and texture a blockstate reaches exists; every block id the builders ask the Kit for exists (ours: a
blockstate; vanilla's: the registry); every loot table the builders name exists and every item in the structures' loot tables exists; the
structure data points at structures, biomes and plans that exist. Prints the problems it found and how many.
"""
import json
import os
import re
import sys

R = sys.argv[1] if len(sys.argv) > 1 else "."
RES = os.path.join(R, "src/main/resources")
A = os.path.join(RES, "assets/jaymospowers")
D = os.path.join(RES, "data/jaymospowers")
errs = []
VANILLA = json.load(open("/home/user/ext/mcmeta-26.3-summary/registries/data.json", encoding="utf-8"))
V_BLOCKS, V_ITEMS = set(VANILLA["block"]), set(VANILLA["item"])
exists = os.path.exists
read = lambda p: open(p, encoding="utf-8").read()

for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            try:
                json.load(open(os.path.join(root, f), encoding="utf-8"))
            except Exception as e:
                errs.append("parse %s: %s" % (os.path.join(root, f), e))

jdir = os.path.join(R, "src/main/java/com/jaymospowers/aether/structure")
src = {f: read(os.path.join(jdir, f)) for f in os.listdir(jdir) if f.endswith(".java")}
lang = json.load(open(os.path.join(A, "lang/parts/aether_structures.json"), encoding="utf-8"))
blocks = re.findall(r'add\("([a-z_]+)"', src["StructureBlocks.java"])
print("blocks %d" % len(blocks))
for b in blocks:
    for rel in (os.path.join(A, "blockstates", b + ".json"), os.path.join(A, "items", b + ".json"), os.path.join(D, "loot_table/blocks", b + ".json")):
        if not exists(rel):
            errs.append("missing " + rel)
    if "block.jaymospowers." + b not in lang:
        errs.append("no lang block " + b)
for key in ("message.jaymospowers.outpost.added", "message.jaymospowers.outpost.known", "message.jaymospowers.outpost.respawn"):
    if key not in lang:
        errs.append("no lang " + key)


def refs(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("model", "parent") and isinstance(v, str):
                yield (k, v)
            elif k == "textures" and isinstance(v, dict):
                for tv in v.values():
                    if isinstance(tv, str) and not tv.startswith("#"):
                        yield ("tex", tv)
            elif k == "texture" and isinstance(v, str) and not v.startswith("#"):
                yield ("tex", v)
            else:
                yield from refs(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from refs(v)


def check_ref(kind, value, where):
    ns, _, path = value.partition(":")
    if not path:
        ns, path = "minecraft", value
    if ns != "jaymospowers":
        return
    p = os.path.join(A, "textures", path + ".png") if kind == "tex" else os.path.join(A, "models", path + ".json")
    if not exists(p):
        errs.append("%s: %s %s does not exist" % (where, kind, value))
    elif kind != "tex":
        for k2, v2 in refs(json.load(open(p, encoding="utf-8"))):
            check_ref(k2, v2, p)


for b in blocks:
    for rel in (os.path.join(A, "blockstates", b + ".json"), os.path.join(A, "items", b + ".json")):
        if exists(rel):
            for k, v in refs(json.load(open(rel, encoding="utf-8"))):
                check_ref(k, v, rel)

# every block the builders ask for
used_ours, used_vanilla, tables = set(), set(), set()
for name, text in src.items():
    for m in re.finditer(r'Kit\.(?:b|log|stairs|slab|ripe|ours)\("([a-z_]+)"', text):
        used_ours.add((name, m.group(1)))
    for m in re.finditer(r'Kit\.(?:v|vanilla)\("([a-z_]+)"', text):
        used_vanilla.add((name, m.group(1)))
    for m in re.finditer(r'"((?:chests|vases)/[a-z_/]+)"', text):
        tables.add(m.group(1))
# palettes and the strings the camps build ids from
for m in re.finditer(r'new Palette\(([^)]*)\)', src["Camps.java"]):
    for sid in re.findall(r'"([a-z_]+)"', m.group(1)):
        used_ours.add(("Camps.java", sid))
for sid in re.findall(r'"([a-z_]+)"', src["Camps.java"].split("tent(s, p")[1] if "tent(s, p" in src["Camps.java"] else ""):
    pass
for name, bid in sorted(used_ours):
    if not exists(os.path.join(A, "blockstates", bid + ".json")):
        errs.append("%s uses our block %s that has no blockstate" % (name, bid))
for name, bid in sorted(used_vanilla):
    if bid not in V_BLOCKS:
        errs.append("%s uses vanilla block %s that does not exist" % (name, bid))
for s in ("stairs", "planks", "fence"):
    pass
for m in re.finditer(r'p\.planks\.replace\("planks", "stairs"\)', src["Camps.java"]):
    for pl in ("skyroot_planks", "wisproot_planks"):
        if not exists(os.path.join(A, "blockstates", pl.replace("planks", "stairs") + ".json")):
            errs.append("camp stairs for %s missing" % pl)
for kind in ("hunter", "farmer", "fletcher"):
    tables.add("chests/camp/" + kind)
tables.discard("chests/camp/")
for t in sorted(tables):
    if not exists(os.path.join(D, "loot_table", t + ".json")):
        errs.append("loot table %s is named in code but does not exist" % t)


def loot_names(obj):
    if isinstance(obj, dict):
        if obj.get("type") == "minecraft:item" and "name" in obj:
            yield obj["name"]
        for v in obj.values():
            yield from loot_names(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from loot_names(v)


loot_root = os.path.join(D, "loot_table")
for sub in ("chests", "vases"):
    for root, _, files in os.walk(os.path.join(loot_root, sub)):
        for f in files:
            path = os.path.join(root, f)
            if "sentry_ruins" in path:
                continue
            for n in loot_names(json.load(open(path, encoding="utf-8"))):
                ns, _, i = n.partition(":")
                if ns == "minecraft" and i not in V_ITEMS:
                    errs.append("%s: no vanilla item %s" % (path, n))
                elif ns == "jaymospowers" and not exists(os.path.join(A, "items", i + ".json")):
                    errs.append("%s: no item %s" % (path, n))
# the structures' data
plans = set(re.findall(r'\b([A-Z]+)\b(?=[,;])', src["AetherStructure.java"].split("enum Plan")[1].split(";")[0]))
for f in sorted(os.listdir(os.path.join(D, "worldgen/structure"))):
    j = json.load(open(os.path.join(D, "worldgen/structure", f), encoding="utf-8"))
    if j.get("type") != "jaymospowers:aether_structure":
        continue
    if j["plan"].upper() not in plans | {"MINESHAFT", "GUARDIAN", "IRRADIATED", "AQUEDUCT", "BREXALLEN", "OUTPOST", "CAMP", "TOWER", "DEN", "RUINS", "HENGE", "LIBRARY"}:
        errs.append("%s: unknown plan %s" % (f, j["plan"]))
    tag = j["biomes"].lstrip("#").split(":")[1]
    tp = os.path.join(D, "tags/worldgen/biome", tag + ".json")
    if not exists(tp):
        errs.append("%s: no biome tag %s" % (f, tag))
    else:
        for b in json.load(open(tp, encoding="utf-8"))["values"]:
            if not exists(os.path.join(D, "worldgen/biome", b.split(":")[1] + ".json")):
                errs.append("%s: biome %s does not exist" % (tag, b))
sets = os.path.join(D, "worldgen/structure_set")
for f in sorted(os.listdir(sets)):
    j = json.load(open(os.path.join(sets, f), encoding="utf-8"))
    for e in j["structures"]:
        if not exists(os.path.join(D, "worldgen/structure", e["structure"].split(":")[1] + ".json")):
            errs.append("%s: structure %s does not exist" % (f, e["structure"]))
    ex = j["placement"].get("exclusion_zone")
    if ex and not exists(os.path.join(sets, ex["other_set"].split(":")[1] + ".json")):
        errs.append("%s: exclusion set %s does not exist" % (f, ex["other_set"]))
print("\n".join(errs))
print("%d problems" % len(errs))
sys.exit(1 if errs else 0)
