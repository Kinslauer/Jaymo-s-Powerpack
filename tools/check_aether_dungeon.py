"""Checks the Sentry Ruins hang together (run it after tools/gen_aether_dungeon.py):

    python -I -B tools/check_aether_dungeon.py .

Every JSON under src/main/resources parses; every block DungeonBlocks registers has a blockstate, an item definition, a loot table, a name
and a texture; every model and texture the blockstates reach exists; every item and entity has what it needs; every skin the renderer asks
for exists; every model part looked up by name is declared; every block and loot table the structure's code names exists; the structure's
data points at what exists. Prints the problems it found and how many.
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

for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            try:
                json.load(open(os.path.join(root, f), encoding="utf-8"))
            except Exception as e:
                errs.append("parse %s: %s" % (os.path.join(root, f), e))

jdir = os.path.join(R, "src/main/java/com/jaymospowers/aether/dungeon")
read = lambda p: open(p, encoding="utf-8").read()
blocks_src = read(os.path.join(jdir, "DungeonBlocks.java"))
mobs_src = read(os.path.join(jdir, "DungeonMobs.java"))
lang = json.load(open(os.path.join(A, "lang/parts/aether_dungeon.json"), encoding="utf-8"))
exists = os.path.exists

blocks = re.findall(r'add\("([a-z_]+)"', blocks_src)
print("blocks %d" % len(blocks))
for b in blocks:
    for rel in (os.path.join(A, "blockstates", b + ".json"), os.path.join(A, "items", b + ".json"), os.path.join(D, "loot_table/blocks", b + ".json")):
        if not exists(rel):
            errs.append("missing " + rel)
    if "block.jaymospowers." + b not in lang:
        errs.append("no lang block " + b)


def refs(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("model", "parent") and isinstance(v, str):
                yield (k, v)
            elif k == "textures" and isinstance(v, dict):
                for tv in v.values():
                    if isinstance(tv, str) and not tv.startswith("#"):
                        yield ("tex", tv)
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
    if kind == "tex":
        p = os.path.join(A, "textures", path + ".png")
    else:
        p = os.path.join(A, "models", path + ".json")
    if not exists(p):
        errs.append("%s: %s %s does not exist" % (where, kind, value))


files = [os.path.join(A, "blockstates", b + ".json") for b in blocks] + [os.path.join(A, "items", b + ".json") for b in blocks]
for b in blocks:
    # every model a blockstate or item definition names, and each model's textures
    pass
seen = set()


def walk_model(value, where):
    ns, _, path = value.partition(":")
    if ns != "jaymospowers" or value in seen:
        return
    seen.add(value)
    p = os.path.join(A, "models", path + ".json")
    if not exists(p):
        errs.append("%s: model %s does not exist" % (where, value))
        return
    for kind, ref in refs(json.load(open(p, encoding="utf-8"))):
        if kind == "parent":
            walk_model(ref, p)
        else:
            check_ref("tex", ref, p)


for f in files:
    if exists(f):
        for kind, ref in refs(json.load(open(f, encoding="utf-8"))):
            walk_model(ref, f)

# items and entities
items = re.findall(r'(?:item|egg)\("([a-z_]+)"', mobs_src)
entities = re.findall(r'type\("([a-z_]+)"', mobs_src) + ["demolition_bomb"]
for i in items:
    for rel in ("items/%s.json" % i, "models/item/aether/%s.json" % i, "textures/item/aether/%s.png" % i):
        if not exists(os.path.join(A, rel)):
            errs.append("missing " + rel)
    if "item.jaymospowers." + i not in lang:
        errs.append("no lang item " + i)
for e in entities:
    if "entity.jaymospowers." + e not in lang:
        errs.append("no lang entity " + e)
for key in ("message.jaymospowers.slider.pickaxe", "message.jaymospowers.slider.too_far"):
    if key not in lang:
        errs.append("no lang " + key)

# the skins the renderer asks for
cdir = os.path.join(R, "src/client/java/com/jaymospowers/client/aether/dungeon")
rend = read(os.path.join(cdir, "DungeonRenderer.java"))
for name in sorted(set(re.findall(r'"((?:slider|detonation_sentry|sentry_golem|mimic)[a-z_]*)"', rend))):
    if not exists(os.path.join(A, "textures/entity/aether/%s.png" % name)):
        errs.append("missing skin " + name)

# every getChild name is a declared part
for f in sorted(os.listdir(cdir)):
    if not f.endswith("Model.java") or f == "DungeonModel.java":
        continue
    s = read(os.path.join(cdir, f))
    declared = set(re.findall(r'part\(\w+, "(\w+)"', s))
    for child in re.findall(r'getChild\("(\w+)"\)', s):
        if child not in declared:
            errs.append("%s looks up part %s that it does not declare" % (f, child))
    for prefix in re.findall(r'getChild\("(\w+)_" \+ i\)', s):
        for i in range(4):
            if "%s_%d" % (prefix, i) not in declared:
                errs.append("%s: no part %s_%d" % (f, prefix, i))

# what the structure's code builds with
all_blockstates = {f[:-5] for f in os.listdir(os.path.join(A, "blockstates"))}
for f in sorted(os.listdir(jdir)):
    if not f.startswith("Ruin"):
        continue
    s = read(os.path.join(jdir, f))
    for m in re.finditer(r'RuinKit\.(ours|state|lit)\("([a-z_]+)"\)', s):
        if m.group(2) not in all_blockstates:
            errs.append("%s: no block of ours %s" % (f, m.group(2)))
    for m in re.finditer(r'RuinKit\.(?:vanilla|vstate)\("([a-z_]+)"\)', s):
        if m.group(1) not in V_BLOCKS:
            errs.append("%s: no vanilla block %s" % (f, m.group(1)))
    for m in re.finditer(r'(?<![\w.])(?:loot)\("([a-z_]+)"\)', s):
        if not exists(os.path.join(D, "loot_table/chests/sentry_ruins", m.group(1) + ".json")):
            errs.append("%s: no loot table %s" % (f, m.group(1)))
    for m in re.finditer(r'(?:ours|state|lit)\("([a-z_]+)"', s):
        pass
# ids written as strings in switch or arrays in RuinPiece
for m in re.finditer(r'"(undershale[a-z_]*|sentry_[a-z_]+|arctic_[a-z_]+|icestone[a-z_]*|holystone[a-z_]*|agiosite|faded_[a-z_]+|mossy_holystone|locked_[a-z_]+)"',
                     read(os.path.join(jdir, "RuinPiece.java")) + read(os.path.join(jdir, "RuinKit.java"))):
    if m.group(1) not in all_blockstates:
        errs.append("code names block %s that has no blockstate" % m.group(1))

# loot tables: every item exists
for root, _, fs in os.walk(os.path.join(D, "loot_table/chests/sentry_ruins")):
    for f in fs:
        t = json.load(open(os.path.join(root, f), encoding="utf-8"))
        for p in t["pools"]:
            for e in p["entries"]:
                ns, _, n = e["name"].partition(":")
                if ns == "minecraft" and n not in V_ITEMS:
                    errs.append("%s: no vanilla item %s" % (f, n))
                if ns == "jaymospowers" and not exists(os.path.join(A, "items", n + ".json")):
                    errs.append("%s: no item of ours %s" % (f, n))

# the structure's data
st = json.load(open(os.path.join(D, "worldgen/structure/sentry_ruins.json"), encoding="utf-8"))
ss = json.load(open(os.path.join(D, "worldgen/structure_set/sentry_ruins.json"), encoding="utf-8"))
if st["type"] != "jaymospowers:sentry_ruins" or "sentry_ruins" not in blocks_src + read(os.path.join(jdir, "SentryRuins.java")):
    errs.append("structure type")
if ss["structures"][0]["structure"] != "jaymospowers:sentry_ruins":
    errs.append("structure set names another structure")
tag = json.load(open(os.path.join(D, "tags/worldgen/biome/has_structure/sentry_ruins.json"), encoding="utf-8"))
for b in tag["values"]:
    if not exists(os.path.join(D, "worldgen/biome", b.split(":")[1] + ".json")):
        errs.append("no biome " + b)
for t in ("slider_immune", "carries_sentry_current"):
    for v in json.load(open(os.path.join(D, "tags/block", t + ".json"), encoding="utf-8"))["values"]:
        if v.split(":")[1] not in blocks:
            errs.append("%s: %s is not one of the dungeon blocks" % (t, v))

for e in errs:
    print(e)
print(len(errs), "problems")
