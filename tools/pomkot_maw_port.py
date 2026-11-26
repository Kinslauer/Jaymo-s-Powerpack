"""
Ports Pomkot's World's Lost Cities buildings onto the Maw's ground (MawCities).

Original: "Pomkots World" by grc-mcs (github.com/grc-mcs/pomkots-world, MIT, see
src/main/resources/licenses/pomkots-world/LICENSE; Forge 1.20.1, a data-only Lost Cities add-on). Its asset files are
kept as they are; this only changes what would not load or would not fit the Maw:

- Its namespace pomkotsmechs: becomes jaymospowers:maw/ (every file sits under a maw/ folder of its kind).
- Left out: the aircraft and the warship (credited to Pine-san, not covered by the repo's licence), and the port and
  offshore districts, which stand in sea the Maw doesn't have. The multibuildings that set an aircraft on a pad keep
  the empty pad. Lost Cities' own oil rig, which Pomkot's scatters too, stays out with them.
- Its own worldstyle chooses districts by overworld biome, which the Maw's one biome never matches: ours (maw.json)
  draws every district it keeps, plus Lost Cities' own standard city, with no biome test.
- Blocks from Pomkots Mechs (pomkotscube, mechworkbench) become a chest and a smithing table; 1.20.1 ids that 26.3
  renamed (grass, chain) get their new names; water becomes air, since the Maw is dry.

Run from the project root with a clone of the repo:
    python3 -I tools/pomkot_maw_port.py <path to>/pomkots-world
"""
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "jaymospowers", "lostcities")
LICENSE = os.path.join(ROOT, "src", "main", "resources", "licenses", "pomkots-world", "LICENSE")
PREFIX = "jaymospowers:maw/"

# what stays out, by asset path
LEFT_OUT = re.compile(r"(^|/)(ships|port|offshore)(/|$)|aircraft|warship|^mega_platform$")
# an aircraft on a pad in a kept multibuilding: the empty pad stands in
STAND_IN = (re.compile(r'"pomkotsmechs:base/base_aircraft_ground\d\d"'), '"pomkotsmechs:base/base_ground_empty"')
BLOCKS = [
    (re.compile(r'"pomkotsmechs:pomkotscube\['), '"minecraft:chest['),
    (re.compile(r'"pomkotsmechs:mechworkbench\[[^"]*\]"'), '"minecraft:smithing_table"'),
    (re.compile(r'"minecraft:grass"'), '"minecraft:short_grass"'),
    (re.compile(r'"minecraft:chain([\["])'), r'"minecraft:iron_chain\1'),
    (re.compile(r'"minecraft:water\[level=0\]"'), '"minecraft:air"'),
]
REF = re.compile(r'"pomkotsmechs:([a-z0-9_/]+)')
# the districts the Maw draws (Pomkot's own weights; Lost Cities' standard city as one more district)
CITYSTYLES = [("factory", 9), ("base", 9), ("quarry", 9), ("megasolar", 9), ("standard", 9),
              ("standard2", 1), ("weatheredcity", 1)]


def drop_left_out(node):
    """A city style's choices without the left-out ones ({"factor", "value"} entries)."""
    if isinstance(node, dict):
        return {k: drop_left_out(v) for k, v in node.items()}
    if isinstance(node, list):
        return [drop_left_out(v) for v in node if not (isinstance(v, dict) and isinstance(v.get("value"), str)
                and v["value"].startswith("pomkotsmechs:") and LEFT_OUT.search(v["value"].split(":", 1)[1]))]
    return node


def main(repo):
    src = os.path.join(repo, "data", "pomkotsmechs", "lostcities")
    paths = {}
    for d, _, fs in os.walk(src):
        for f in fs:
            rel = os.path.relpath(os.path.join(d, f), src).replace(os.sep, "/")
            paths.setdefault(rel.split("/", 1)[1][:-5], []).append(rel)

    def text(rel):
        t = open(os.path.join(src, rel), encoding="utf-8").read()
        if rel.startswith("multibuildings/"):
            t = STAND_IN[0].sub(STAND_IN[1], t)
        elif rel.startswith("citystyles/"):
            t = json.dumps(drop_left_out(json.loads(t)), indent=2, ensure_ascii=False)
        for pattern, repl in BLOCKS:
            t = pattern.sub(repl, t)
        return t

    style = json.loads(open(os.path.join(src, "worldstyles", "pomkotsworld.json"), encoding="utf-8").read())
    style.pop("citybiomemultipliers")
    style["citystyles"] = [{"citystyle": "pomkotsmechs:" + c, "factor": f} for c, f in CITYSTYLES]
    style["citystyles"].append({"citystyle": "citystyle_standard", "factor": 9})
    # Lost Cities' own oil rig stands in sea too
    style["scattered"]["list"] = [s for s in style["scattered"]["list"]
                                  if s["name"] != "oilrig" and not LEFT_OUT.search(s["name"].split(":")[-1])]
    style_text = json.dumps(style, indent=2, ensure_ascii=False)

    keep, todo, missing = set(), [], set()

    def follow(t):
        for ref in REF.findall(t):
            if LEFT_OUT.search(ref):
                raise SystemExit("still refers to a left-out asset: " + ref)
            if ref not in paths:
                missing.add(ref)
            todo.extend(paths.get(ref, []))

    follow(style_text)
    while todo:
        rel = todo.pop()
        if rel not in keep:
            keep.add(rel)
            follow(text(rel))
    if missing:
        raise SystemExit("unresolved: " + ", ".join(sorted(missing)))

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    for rel in sorted(keep):
        kind, rest = rel.split("/", 1)
        out = os.path.join(OUT, kind, "maw", rest)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        t = text(rel).replace('"pomkotsmechs:', '"' + PREFIX)
        json.loads(t)
        open(out, "w", encoding="utf-8", newline="\n").write(t)
    os.makedirs(os.path.join(OUT, "worldstyles"), exist_ok=True)
    open(os.path.join(OUT, "worldstyles", "maw.json"), "w", encoding="utf-8", newline="\n").write(
        style_text.replace('"pomkotsmechs:', '"' + PREFIX) + "\n")
    os.makedirs(os.path.dirname(LICENSE), exist_ok=True)
    shutil.copyfile(os.path.join(repo, "LICENSE"), LICENSE)
    print(len(keep), "assets + worldstyle maw")


if __name__ == "__main__":
    main(sys.argv[1])
