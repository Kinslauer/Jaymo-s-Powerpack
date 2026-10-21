"""Checks the Aether gear hangs together (run it after tools/gen_aether_gear.py):

    python -I tools/check_aether_gear.py .

Reads the ids the Java registers (the enums in com.jaymospowers.aether.gear and the plain item() calls of AetherGear), then
checks that each item has an item definition, a model, a texture and a name, each armour set an equipment asset and both
layer textures, the altar a blockstate, model, textures, loot table and name; that every JSON parses, every recipe's ids exist
(ours or vanilla's by prefix), no lang key is defined twice across the parts, and the tags point at real items.
"""
import glob
import json
import os
import re
import sys

R = sys.argv[1] if len(sys.argv) > 1 else "."
RES = os.path.join(R, "src/main/resources")
A = os.path.join(RES, "assets/jaymospowers")
D = os.path.join(RES, "data/jaymospowers")
G = os.path.join(R, "src/main/java/com/jaymospowers/aether/gear")
errs = []


def read(path):
    return open(path, encoding="utf-8").read()


def exists(p):
    return os.path.exists(p)


# 1. every json parses
for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            try:
                json.load(open(os.path.join(root, f), encoding="utf-8"))
            except Exception as e:
                errs.append("parse %s: %s" % (os.path.join(root, f), e))

# 2. the ids the Java registers
tiers = re.findall(r'^\t([A-Z]+)\("([a-z_]+)", BlockTags', read(os.path.join(G, "GearTier.java")), re.M)
sets = re.findall(r'^\t([A-Z_]+)\("([a-z_]+)", \d+', read(os.path.join(G, "ArmorSet.java")), re.M)
gliders = re.findall(r'([A-Z]+)\("([a-z]+)", \d+, \d+, [0-9.]+\)', read(os.path.join(G, "AercloudGliderItem.java")))
main = read(os.path.join(G, "AetherGear.java"))
plain = re.findall(r'(?:item|enchanted)\("([a-z_]+)"', main)
print("tiers", len(tiers), "sets", len(sets), "gliders", len(gliders), "plain", plain)
ids = set(plain) - {"altar"}
for _, t in tiers:
    for k in ("shortsword", "hammer", "pickaxe", "axe", "shovel", "hoe"):
        ids.add("%s_%s" % (t, k))
for _, s in sets:
    for p in ("helmet", "chestplate", "leggings", "boots"):
        ids.add("%s_%s" % (s, p))
for _, g in gliders:
    ids.add("%s_aercloud_glider" % g)
if len(tiers) != 5 or len(sets) != 6 or len(gliders) != 4:
    errs.append("expected 5 tiers, 6 sets, 4 gliders")

# 3. assets for each
lang = {}
for part in glob.glob(os.path.join(A, "lang/parts/*.json")):
    for k, v in json.load(open(part, encoding="utf-8")).items():
        if os.path.basename(part) != "aether_gear.json" and k in json.load(open(os.path.join(A, "lang/parts/aether_gear.json"), encoding="utf-8")):
            errs.append("lang key %s defined twice (%s)" % (k, os.path.basename(part)))
mine = json.load(open(os.path.join(A, "lang/parts/aether_gear.json"), encoding="utf-8"))
for i in sorted(ids):
    if not exists(os.path.join(A, "items", i + ".json")):
        errs.append("no item def " + i)
    if not exists(os.path.join(A, "models/item/aether", i + ".json")):
        errs.append("no item model " + i)
    if not exists(os.path.join(A, "textures/item/aether", i + ".png")):
        errs.append("no item texture " + i)
    if "item.jaymospowers." + i not in mine:
        errs.append("no lang " + i)
for _, s in sets:
    for folder in ("humanoid", "humanoid_leggings"):
        if not exists(os.path.join(A, "textures/entity/equipment", folder, s + ".png")):
            errs.append("no %s layer for %s" % (folder, s))
    eq = os.path.join(A, "equipment", s + ".json")
    if not exists(eq):
        errs.append("no equipment asset " + s)
# the altar
for p in ("blockstates/altar.json", "items/altar.json", "models/block/aether/altar.json", "textures/block/aether/altar_top.png",
          "textures/block/aether/altar_side.png", "textures/block/aether/altar_column.png", "textures/block/aether/altar_base.png"):
    if not exists(os.path.join(A, p)):
        errs.append("altar: missing " + p)
if not exists(os.path.join(D, "loot_table/blocks/altar.json")):
    errs.append("altar: no loot table")
for k in ("block.jaymospowers.altar", "container.jaymospowers.altar", "gui.jaymospowers.altar.shards", "gui.jaymospowers.altar.idle"):
    if k not in mine:
        errs.append("no lang " + k)
# models point at textures that exist
for f in glob.glob(os.path.join(A, "models/item/aether/*.json")):
    m = json.load(open(f, encoding="utf-8"))
    for t in m.get("textures", {}).values():
        ns, path = t.split(":")
        if not exists(os.path.join(RES, "assets", ns, "textures", path + ".png")) and os.path.basename(f)[:-5] in ids:
            errs.append("%s: texture %s missing" % (os.path.basename(f), t))
for t in json.load(open(os.path.join(A, "models/block/aether/altar.json")))["textures"].values():
    ns, path = t.split(":")
    if not exists(os.path.join(RES, "assets", ns, "textures", path + ".png")):
        errs.append("altar model: texture %s missing" % t)

# 4. every item id we know (items defs and blocks) for recipes and tags
known = {os.path.basename(f)[:-5] for f in glob.glob(os.path.join(A, "items", "*.json"))}


def item_ok(s):
    if s.startswith("#"):
        return True
    ns, name = s.split(":")
    return ns == "minecraft" or name in known


for f in glob.glob(os.path.join(D, "recipe/aether/*.json")):
    base = os.path.basename(f)[:-5]
    if not (base in ids or base.split("_from_")[0] in ids or base in ("altar", "arkenium_plate_from_smelting", "arkenium_plate_from_blasting")):
        continue
    r = json.load(open(f, encoding="utf-8"))
    refs = []
    if "key" in r:
        refs += list(r["key"].values())
    if "ingredient" in r:
        refs.append(r["ingredient"])
    refs.append(r["result"]["id"])
    for ref in refs:
        if not item_ok(ref):
            errs.append("recipe %s: unknown item %s" % (base, ref))
# every gear item has a recipe, bar neptune's (boss loot only)
for i in sorted(ids):
    if i.startswith("neptune_") or i.startswith("enchanted_"):
        continue
    if not exists(os.path.join(D, "recipe/aether", i + ".json")) and not exists(os.path.join(D, "recipe/aether", i + "_from_smelting.json")):
        errs.append("no recipe for " + i)
if not exists(os.path.join(D, "recipe/aether/altar.json")):
    errs.append("no recipe for altar")
for f in glob.glob(os.path.join(D, "tags/item/*.json")):
    for v in json.load(open(f, encoding="utf-8"))["values"]:
        v = v["id"] if isinstance(v, dict) else v
        if v.startswith("jaymospowers:") and not item_ok(v):
            errs.append("tag %s: unknown item %s" % (os.path.basename(f), v))
for tag in ("swords", "pickaxes", "axes", "shovels", "hoes", "head_armor", "chest_armor", "leg_armor", "foot_armor"):
    for v in json.load(open(os.path.join(RES, "data/minecraft/tags/item", tag + ".json")))["values"]:
        v = v["id"] if isinstance(v, dict) else v
        if v.startswith("jaymospowers:") and not item_ok(v):
            errs.append("minecraft tag %s: unknown item %s" % (tag, v))

print("problems:", len(errs))
for e in errs:
    print("  " + e)
sys.exit(1 if errs else 0)
