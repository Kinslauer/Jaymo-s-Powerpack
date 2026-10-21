"""Checks the Aether's assets and data hang together (run it after tools/gen_aether.py):

    python -I tools/check_aether_assets.py .

Every JSON under src/main/resources parses; every block registered in AetherBlocks has a blockstate, an item
definition, a loot table and a name; every model, texture, recipe, loot entry and tag the Aether writes points at
something that exists. Prints the problems it found and how many.
"""
import json, os, re, sys, glob
R = sys.argv[1] if len(sys.argv) > 1 else "."
RES = os.path.join(R, "src/main/resources")
A = os.path.join(RES, "assets/jaymospowers")
D = os.path.join(RES, "data/jaymospowers")
errs = []
# 1. every json parses
for root, _, files in os.walk(RES):
    for f in files:
        if f.endswith(".json"):
            try:
                json.load(open(os.path.join(root, f), encoding="utf-8"))
            except Exception as e:
                errs.append("parse %s: %s" % (os.path.join(root, f), e))
# 2. ids from Java
src = open(os.path.join(R, "src/main/java/com/jaymospowers/aether/AetherBlocks.java"), encoding="utf-8").read()
ids = set(re.findall(r'(?:block|add|ore)\("([a-z_]+)"', src))
for fam in re.findall(r'family\("([a-z_]+)"', src):
    for sh in ("stairs", "slab", "wall"):
        ids.add("%s_%s" % (fam, sh))
dyes = ["orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"]
for d in dyes:
    ids.add(d + "_cloudwool"); ids.add(d + "_cloudwool_carpet")
ids |= {"ambrosium_torch"}
ids.discard("ambrosium_wall_torch")
print("block ids from Java:", len(ids))
lang = json.load(open(os.path.join(A, "lang/parts/aether.json"), encoding="utf-8"))
def exists(p): return os.path.exists(p)
for b in sorted(ids):
    if not exists(os.path.join(A, "blockstates", b + ".json")): errs.append("no blockstate " + b)
    if not exists(os.path.join(A, "items", b + ".json")): errs.append("no item def " + b)
    if not exists(os.path.join(D, "loot_table/blocks", b + ".json")): errs.append("no loot " + b)
    if "block.jaymospowers." + b not in lang: errs.append("no lang " + b)
# items
isrc = open(os.path.join(R, "src/main/java/com/jaymospowers/aether/AetherItems.java"), encoding="utf-8").read()
for i in re.findall(r'item\("([a-z_]+)"', isrc):
    if not exists(os.path.join(A, "items", i + ".json")): errs.append("no item def " + i)
    if "item.jaymospowers." + i not in lang: errs.append("no lang item " + i)
    if not exists(os.path.join(A, "textures/item/aether", i + ".png")): errs.append("no item texture " + i)
# 3. models and textures referenced
def refs(obj):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("model", "parent", "sprite") and isinstance(v, str): yield (k, v)
            elif k == "textures" and isinstance(v, dict):
                for tv in v.values():
                    if isinstance(tv, str): yield ("tex", tv)
                    elif isinstance(tv, dict) and "sprite" in tv: yield ("tex", tv["sprite"])
            else:
                yield from refs(v)
    elif isinstance(obj, list):
        for v in obj: yield from refs(v)
def check_ref(kind, v, where):
    if v.startswith("#"): return
    if ":" in v:
        ns, path = v.split(":", 1)
    else:
        ns, path = "minecraft", v
    if ns != "jaymospowers": return
    if kind == "tex":
        if not exists(os.path.join(A, "textures", path + ".png")): errs.append("%s: missing texture %s" % (where, v))
    else:
        if not exists(os.path.join(A, "models", path + ".json")): errs.append("%s: missing model %s" % (where, v))
for sub in ("blockstates", "items", "models"):
    for p in glob.glob(os.path.join(A, sub, "**/*.json"), recursive=True):
        j = json.load(open(p, encoding="utf-8"))
        for k, v in refs(j):
            check_ref("tex" if k == "tex" else "model", v, os.path.relpath(p, A))
# 4. recipes reference real ids
known = set(ids) | set(re.findall(r'item\("([a-z_]+)"', isrc)) | {"ambrosium_shard","zanite_gemstone","golden_amber","blue_berry","white_apple"}
for p in glob.glob(os.path.join(D, "recipe/aether/*.json")):
    j = json.load(open(p, encoding="utf-8"))
    def walk(o):
        if isinstance(o, str):
            if o.startswith("jaymospowers:"):
                n = o.split(":")[1]
                if n.startswith("#") : return
                if n not in known and n not in ("aether_logs",): errs.append("recipe %s uses unknown %s" % (os.path.basename(p), o))
        elif isinstance(o, dict):
            for v in o.values(): walk(v)
        elif isinstance(o, list):
            for v in o: walk(v)
    walk(j)
# loot tables name known items
for p in [os.path.join(D, "loot_table/blocks", b + ".json") for b in ids]:
    if not os.path.exists(p): continue
    t = open(p, encoding="utf-8").read()
    for n in re.findall(r'"name": "jaymospowers:([a-z_]+)"', t):
        if n not in known: errs.append("loot %s: unknown item %s" % (os.path.basename(p), n))
# tags: only the Aether's own ids (the rest of the mod's blocks are another track's)
other = set()
for lp in glob.glob(os.path.join(A, "lang/parts/*.json")):
    if os.path.basename(lp).startswith("aether"): continue
    for k in json.load(open(lp, encoding="utf-8")):
        if k.startswith("block.jaymospowers.") or k.startswith("item.jaymospowers."):
            other.add(k.split(".", 2)[2])
for p in glob.glob(os.path.join(RES, "data/**/tags/**/*.json"), recursive=True):
    t = json.load(open(p, encoding="utf-8"))
    if not any(isinstance(v, str) and v.startswith("jaymospowers:") and v.split(":")[1] in known for v in t["values"]): continue
    for v in t["values"]:
        if isinstance(v, str) and v.startswith("jaymospowers:") and v.split(":")[1] not in known and v.split(":")[1] not in other and not v.startswith("jaymospowers:#"):
            errs.append("tag %s: unknown %s" % (os.path.relpath(p, RES), v))
print("\n".join(errs[:80]) or "all good")
print(len(errs), "problems")
