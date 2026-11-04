"""Keeps Kingdoms lang in small part files (Powers does the same) so agents don't read a 12,000 line file.

The parts live in kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/*.json and the Kingdoms build merges
them into en_us.json. Any part file works for a key; a new feature can add its own part.

Run `python3 tools/lang_split.py` from the repo root. If a branch still has the old single
kingdoms/.../lang/en_us.json (for example after merging master into an older branch), its keys are moved into
the parts: a key already in a part is updated there, a new key goes to the part its prefix matches, else misc.json.
Then en_us.json is deleted. Safe to run again.
"""
import collections
import json
import os
import sys

LANG = "kingdoms/src/main/resources/assets/jaymoskingdoms/lang"
PARTS = os.path.join(LANG, "parts")
WHOLE = os.path.join(LANG, "en_us.json")
NS_MIN = 25   # a type.namespace group this big gets its own part (talk-odd.json)
TYPE_MIN = 8  # a type smaller than this goes into misc.json


def namespace(key):
    p = key.split(".")
    if "jaymoskingdoms" in p:
        i = p.index("jaymoskingdoms")
        if i < len(p) - 1:
            return p[i + 1]
    return None


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=collections.OrderedDict)


def save(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def first_split(whole):
    pair = collections.Counter((k.split(".")[0], namespace(k)) for k in whole)
    kind = collections.Counter(k.split(".")[0] for k in whole)

    def part(k):
        t, n = k.split(".")[0], namespace(k)
        if n and pair[(t, n)] >= NS_MIN:
            return f"{t}-{n}"
        return t if kind[t] >= TYPE_MIN else "misc"

    out = collections.defaultdict(collections.OrderedDict)
    for k, v in whole.items():
        out[part(k)][k] = v
    return out


def absorb(whole):
    parts = {f[:-5]: load(os.path.join(PARTS, f)) for f in sorted(os.listdir(PARTS)) if f.endswith(".json")}
    home = {k: name for name, d in parts.items() for k in d}
    touched = set()
    for k, v in whole.items():
        name = home.get(k)
        if name is None:
            t, n = k.split(".")[0], namespace(k)
            name = next((c for c in (f"{t}-{n}", t) if c in parts), "misc")
            parts.setdefault(name, collections.OrderedDict())
            home[k] = name
        if parts[name].get(k) != v:
            parts[name][k] = v
            touched.add(name)
    return {n: parts[n] for n in touched}


def main():
    os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if not os.path.exists(WHOLE):
        print("no kingdoms en_us.json to split; parts are up to date")
        return
    whole = load(WHOLE)
    if os.path.isdir(PARTS) and any(f.endswith(".json") for f in os.listdir(PARTS)):
        out = absorb(whole)
    else:
        os.makedirs(PARTS, exist_ok=True)
        out = first_split(whole)
    for name, data in out.items():
        save(os.path.join(PARTS, name + ".json"), data)
    os.remove(WHOLE)
    print(f"{len(whole)} keys -> {len(out)} part files written; en_us.json removed")


if __name__ == "__main__":
    sys.exit(main())
