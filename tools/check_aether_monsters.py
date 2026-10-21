"""Checks the Aether II hostile mobs hang together (run it after tools/gen_aether_monsters.py):

    python -I tools/check_aether_monsters.py .

Every JSON under src/main/resources parses; every item AetherMonsters registers (drops, shots, spawn eggs) has an item
definition, a model, a texture and a name; every entity type has a name and a loot table; every mob has the skin its
renderer asks for (and the glow overlay, if it asks for one); every effect has an icon and a name; every model part a
model looks up by name is one it declares. Prints the problems it found and how many.
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

for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            try:
                json.load(open(os.path.join(root, f), encoding="utf-8"))
            except Exception as e:
                errs.append("parse %s: %s" % (os.path.join(root, f), e))

main = os.path.join(R, "src/main/java/com/jaymospowers/aether/monster")
reg = open(os.path.join(main, "AetherMonsters.java"), encoding="utf-8").read()
shots = open(os.path.join(main, "AetherShot.java"), encoding="utf-8").read()
fx = open(os.path.join(main, "AetherEffects.java"), encoding="utf-8").read()
lang = json.load(open(os.path.join(A, "lang/parts/aether_monsters.json"), encoding="utf-8"))

items = set(re.findall(r'(?:item|egg)\("([a-z_]+)"', reg))
shot_ids = set(re.findall(r'\("([a-z_]+)", [\d.]+\)', shots))
items |= shot_ids
entities = set(re.findall(r'type\("([a-z_]+)"', reg)) | shot_ids
mobs = set(re.findall(r'type\("([a-z_]+)"', reg))
effects = set(re.findall(r'register\("([a-z_]+)"', fx))
print("items %d, entities %d (mobs %d), effects %d" % (len(items), len(entities), len(mobs), len(effects)))

for i in sorted(items):
    for rel in ("items/%s.json" % i, "models/item/aether/%s.json" % i, "textures/item/aether/%s.png" % i):
        if not os.path.exists(os.path.join(A, rel)):
            errs.append("missing " + rel)
    if "item.jaymospowers." + i not in lang:
        errs.append("no lang item " + i)
for e in sorted(entities):
    if "entity.jaymospowers." + e not in lang:
        errs.append("no lang entity " + e)
for m in sorted(mobs):
    if not os.path.exists(os.path.join(D, "loot_table/entities/%s.json" % m)):
        errs.append("no loot table " + m)
    if m + "_spawn_egg" not in items:
        errs.append("no spawn egg for " + m)
for f in sorted(effects):
    if not os.path.exists(os.path.join(A, "textures/mob_effect/%s.png" % f)):
        errs.append("no effect icon " + f)
    if "effect.jaymospowers." + f not in lang:
        errs.append("no lang effect " + f)

# the skins and glow overlays the renderer asks for
rend = open(os.path.join(R, "src/client/java/com/jaymospowers/client/aether/monster/AetherMonsterRenderer.java"), encoding="utf-8").read()
for name in set(re.findall(r'return "([a-z_]+)";', rend)) | set(re.findall(r'golden \? "([a-z_]+)" : "([a-z_]+)"', rend)[0]):
    if not os.path.exists(os.path.join(A, "textures/entity/aether/%s.png" % name)):
        errs.append("missing skin / glow " + name)

# every getChild name is a declared part
mdir = os.path.join(R, "src/client/java/com/jaymospowers/client/aether/monster")
for f in sorted(os.listdir(mdir)):
    if not f.endswith("Model.java") or f == "AetherMonsterModel.java":
        continue
    s = open(os.path.join(mdir, f), encoding="utf-8").read()
    declared = set(re.findall(r'part\(\w+, "(\w+)"', s))
    for child in re.findall(r'getChild\("(\w+)"\)', s):
        if child not in declared:
            errs.append("%s looks up part %s that it does not declare" % (f, child))
    for prefix in re.findall(r'getChild\("(\w+)_" \+ i\)', s):
        for i in range(6):
            if "%s_%d" % (prefix, i) not in declared and not (prefix == "chunk" and i >= 4) and not (prefix == "petal" and i >= 6):
                if i < (4 if prefix == "chunk" else 6):
                    errs.append("%s: no part %s_%d" % (f, prefix, i))

for e in errs:
    print(e)
print(len(errs), "problems")
