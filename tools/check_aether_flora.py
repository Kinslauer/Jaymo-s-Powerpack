"""Checks Track N2's output (run after tools/gen_aether_flora.py):

    python -I tools/check_aether_flora.py

Every id AetherFlora.java registers must have a blockstate, an item definition (all but the potted plants), a loot table, a lang
name and a texture; every model a blockstate or item definition names must exist, and every texture a model names too;
every JSON the generator wrote must parse; lang keys must not clash with another lang part. Exits 1 on any problem.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "jaymospowers", "aether", "AetherFlora.java")
problems = []


def bad(msg):
    problems.append(msg)


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        bad("does not parse: %s (%s)" % (path, e))
        return None


def registered():
    """The block ids, items and potted blocks the Java registers (patterns expanded the way the Java does)."""
    src = open(JAVA, encoding="utf-8").read()
    blocks, items, potted = [], [], []
    for m in re.finditer(r'\b(?:block|flora|leaves)\("([a-z_]+)"', src):
        blocks.append(m.group(1))
    for m in re.finditer(r'\bsapling\("([a-z]+)", MapColor', src):
        blocks.append(m.group(1) + "_sapling")
    for m in re.finditer(r'\bwoodSet\((?:[A-Z]+), "([a-z]+)"', src):
        n = m.group(1)
        blocks += [n + "_log", n + "_wood", "stripped_" + n + "_log", "stripped_" + n + "_wood", n + "_planks"]
    for m in re.finditer(r'\bshaped\((?:[A-Z]+), "([a-z]+)"', src):
        n = m.group(1)
        blocks += [n + s for s in ("_stairs", "_slab", "_fence", "_fence_gate", "_door", "_trapdoor", "_button", "_pressure_plate")]
    # the helper bodies themselves (name + "_log" and so on) are not literals worth counting
    blocks = [b for b in blocks if not b.startswith("_") and not b.endswith("_")]
    for m in re.finditer(r'\b(?:item|food)\("([a-z_]+)"', src):
        items.append(m.group(1))
    for m in re.finditer(r'\bpot\("([a-z_]+)"', src):
        potted.append("potted_" + m.group(1))
    return blocks, items, potted


def png_exists(ref, kind):
    ns, _, path = ref.partition(":")
    if ns != "jaymospowers":
        return True
    return os.path.exists(os.path.join(ASSETS, "textures", path + ".png"))


def model_path(ref):
    ns, _, path = ref.partition(":")
    return os.path.join(ASSETS, "models", path + ".json") if ns == "jaymospowers" else None


def model_refs(node, out):
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "model" and isinstance(v, str):
                out.add(v)
            else:
                model_refs(v, out)
    elif isinstance(node, list):
        for v in node:
            model_refs(v, out)


def main():
    blocks, items, potted = registered()
    if len(set(blocks)) != len(blocks):
        bad("duplicate block registrations: %s" % sorted({b for b in blocks if blocks.count(b) > 1}))
    lang = load(os.path.join(ASSETS, "lang", "parts", "aether_flora.json")) or {}
    others = set()
    for p in glob.glob(os.path.join(ASSETS, "lang", "parts", "*.json")):
        if not p.endswith("aether_flora.json"):
            d = load(p)
            others |= set(d or {})
    for k in lang:
        if k in others:
            bad("lang key also in another part: " + k)
    for b in blocks + potted:
        if not os.path.exists(os.path.join(ASSETS, "blockstates", b + ".json")):
            bad("no blockstate: " + b)
        if not os.path.exists(os.path.join(DATA, "loot_table", "blocks", b + ".json")):
            bad("no loot table: " + b)
        if "block.jaymospowers." + b not in lang:
            bad("no lang name: " + b)
    for b in blocks:
        if not os.path.exists(os.path.join(ASSETS, "items", b + ".json")):
            bad("no item definition: " + b)
    for i in items:
        if not os.path.exists(os.path.join(ASSETS, "items", i + ".json")):
            bad("no item definition: " + i)
        if "item.jaymospowers." + i not in lang:
            bad("no lang name: item " + i)
        if not os.path.exists(os.path.join(ASSETS, "textures", "item", "aether", i + ".png")):
            bad("no item texture: " + i)
    # every model named by a blockstate or item definition, every texture named by a model
    seen = set()
    for b in blocks + potted + items:
        for kind, folder in (("bs", os.path.join(ASSETS, "blockstates")), ("it", os.path.join(ASSETS, "items"))):
            p = os.path.join(folder, b + ".json")
            if os.path.exists(p):
                d = load(p)
                refs = set()
                model_refs(d, refs)
                seen |= refs
    for ref in sorted(seen):
        mp = model_path(ref)
        if mp is None:
            continue
        if not os.path.exists(mp):
            bad("missing model: " + ref)
            continue
        d = load(mp)
        for t in (d or {}).get("textures", {}).values():
            if isinstance(t, dict):
                t = t.get("sprite", "")
            if isinstance(t, str) and not t.startswith("#") and not png_exists(t, "block"):
                bad("missing texture %s (in %s)" % (t, ref))
        parent = (d or {}).get("parent", "")
        if parent.startswith("jaymospowers:") and not os.path.exists(model_path(parent)):
            bad("missing parent model " + parent)
    # every JSON this generator owns parses (the rest were checked above); the tag files too
    for p in glob.glob(os.path.join(RES, "data", "**", "*.json"), recursive=True):
        load(p)
    for p in glob.glob(os.path.join(ASSETS, "**", "*.json"), recursive=True):
        load(p)
    print("%d blocks, %d potted, %d items checked, %d models seen" % (len(blocks), len(potted), len(items), len(seen)))
    for p in problems:
        print("PROBLEM:", p)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
