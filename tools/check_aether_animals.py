"""Checks Aether II's animals hang together (run it after tools/gen_aether_animals.py):

    python -I tools/check_aether_animals.py .

Every JSON under src/main/resources parses; every item AetherAnimals registers (food, materials, eggs, spawn eggs) and
every spawn egg of AetherMobs has an item definition, a model, a texture and a name; every entity type has a name and a
loot table; every skin a renderer look asks for exists; the moa egg block has a blockstate, every model it names, every
texture those name, an item definition, a loot table and a name; every cooking recipe names real items; every model's
getChild names are parts it declares. Prints the problems found and how many.
"""
import json
import os
import re
import sys

R = sys.argv[1] if len(sys.argv) > 1 else "."
RES = os.path.join(R, "src/main/resources")
A = os.path.join(RES, "assets/jaymospowers")
D = os.path.join(RES, "data/jaymospowers")
JAVA = os.path.join(R, "src/main/java/com/jaymospowers/aether/mob")
CLIENT = os.path.join(R, "src/client/java/com/jaymospowers/client/aether/mob")
errs = []


def have(path):
    if not os.path.exists(path):
        errs.append("missing " + os.path.relpath(path, R))
        return False
    return True


for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            try:
                json.load(open(os.path.join(root, f), encoding="utf-8"))
            except Exception as e:
                errs.append("parse %s: %s" % (os.path.join(root, f), e))

reg = open(os.path.join(JAVA, "AetherAnimals.java"), encoding="utf-8").read()
mobs_reg = open(os.path.join(JAVA, "AetherMobs.java"), encoding="utf-8").read()
lang = {}
for f in sorted(os.listdir(os.path.join(A, "lang/parts"))):
    if f.startswith("aether"):
        for k, v in json.load(open(os.path.join(A, "lang/parts", f), encoding="utf-8")).items():
            if k in lang:
                errs.append("lang key twice: " + k)
            lang[k] = v

items = set(re.findall(r'(?:meat|item|egg)\("([a-z_]+)"', reg)) | set(re.findall(r'(?:egg|item)\("([a-z_]+_(?:spawn_)?egg)"', mobs_reg))
items |= set(re.findall(r'egg\("([a-z_]+)"', mobs_reg))
entities = set(re.findall(r'type\("([a-z_]+)"', reg)) | set(re.findall(r'ResourceKey\.create\(Registries\.ENTITY_TYPE, JaymosPowers\.id\("([a-z_]+)"\)', reg))
mobs = set(re.findall(r'type\("([a-z_]+)"', reg)) | set(re.findall(r'type\("([a-z_]+)"', mobs_reg))
print("items %d, entities %d (mobs %d)" % (len(items), len(entities), len(mobs)))

for i in sorted(items):
    for rel in ("items/%s.json" % i, "models/item/aether/%s.json" % i, "textures/item/aether/%s.png" % i):
        have(os.path.join(A, rel))
    if "item.jaymospowers." + i not in lang:
        errs.append("no lang item " + i)
for e in sorted(entities):
    if "entity.jaymospowers." + e not in lang:
        errs.append("no lang entity " + e)
for m in sorted(mobs):
    have(os.path.join(D, "loot_table/entities/%s.json" % m))
    if m + "_spawn_egg" not in items:
        errs.append("no spawn egg for " + m)

# the skins the looks ask for
WOOL = ["white", "light_blue", "cyan", "lime", "pink", "purple", "light_gray", "gray", "yellow", "brown", "magenta", "orange", "red", "green", "blue", "black"]
skins = []
for z in ("highfields", "magnetic", "arctic"):
    skins += ["kirrid_%s_%s" % (z, w) for w in WOOL + ["undyed"]] + ["burrukai_" + z, "taegore_" + z]
skins += ["sheepuff_" + w for w in WOOL]
lz = re.search(r'VARIANTS = \{([^}]*)\}', open(os.path.join(JAVA, "SkyrootLizard.java"), encoding="utf-8").read()).group(1)
skins += ["skyroot_lizard_" + v for v in re.findall(r'"(\w+)"', lz)]
ins = open(os.path.join(JAVA, "Insect.java"), encoding="utf-8").read()
for arr, prefix in (("GLITTERWING", "glitterwing_"), ("SHROUDWING", "shroudwing_")):
    body = re.search(arr + r' = \{([^}]*)\}', ins).group(1)
    skins += [prefix + v for v in re.findall(r'"(\w+)"', body)]
skins += ["prismallard", "aerwhale", "phyg", "flying_cow", "aerbunny", "moa_blue", "moa_white", "moa_black"]
for s in skins:
    have(os.path.join(A, "textures/entity/aether/%s.png" % s))
wool_java = open(os.path.join(JAVA, "Wool.java"), encoding="utf-8").read()
if sorted(re.findall(r'"(\w+)"', re.search(r'NAMES = \{([^}]*)\}', wool_java).group(1))) != sorted(WOOL):
    errs.append("Wool.NAMES differs from the checker's list")

# the moa egg block
for rel in ("blockstates/moa_egg.json", "items/moa_egg.json", "models/item/aether/moa_egg.json"):
    have(os.path.join(A, rel))
have(os.path.join(D, "loot_table/blocks/moa_egg.json"))
if "block.jaymospowers.moa_egg" not in lang:
    errs.append("no lang block moa_egg")
state = json.load(open(os.path.join(A, "blockstates/moa_egg.json"), encoding="utf-8"))
seen = set()
for part in state["multipart"]:
    w = part["when"]
    seen.add((w["kind"], w["stage"], w["low"]))
    mp = part["apply"]["model"].split(":")[1]
    if have(os.path.join(A, "models", mp + ".json")):
        model = json.load(open(os.path.join(A, "models", mp + ".json"), encoding="utf-8"))
        for tex in model["textures"].values():
            if not tex.startswith("#"):
                have(os.path.join(A, "textures", tex.split(":")[1] + ".png"))
for k in "012":
    for s in "012":
        for low in ("true", "false"):
            if (k, s, low) not in seen:
                errs.append("moa egg blockstate lacks kind=%s stage=%s low=%s" % (k, s, low))

# recipes
known = set(items) | {"blue_moa_egg", "white_moa_egg", "black_moa_egg"}
rdir = os.path.join(D, "recipe/aether")
cooked = 0
RAW = {"raw_taegore_meat", "burrukai_rib_cut", "kirrid_loin", "prismallard_leg"}
for f in sorted(os.listdir(rdir)):
    if not f.endswith(".json"):
        continue
    r = json.load(open(os.path.join(rdir, f), encoding="utf-8"))
    if r.get("type") in ("minecraft:smelting", "minecraft:smoking", "minecraft:campfire_cooking") and r["ingredient"].split(":")[1] in RAW:
        cooked += 1
        for name in (r["ingredient"], r["result"]["id"]):
            if name.split(":")[1] not in known:
                errs.append("recipe %s names unknown item %s" % (f, name))
if cooked != 12:
    errs.append("expected 12 cooking recipes, found %d" % cooked)

# every getChild name is a declared part
for f in sorted(os.listdir(CLIENT)):
    if not f.endswith("Model.java") or f == "AetherAnimalModel.java":
        continue
    s = open(os.path.join(CLIENT, f), encoding="utf-8").read()
    declared = set(re.findall(r'part\(\w+, "(\w+)"', s))
    for child in re.findall(r'getChild\("(\w+)"\)', s):
        if child not in declared:
            errs.append("%s looks up part %s that it does not declare" % (f, child))

for e in errs:
    print(e)
print(len(errs), "problems")
