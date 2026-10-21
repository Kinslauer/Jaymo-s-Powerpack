"""Writes the smithing blocks' assets and data (batch 2 of the Better With Mods port): the brick kiln's load and unfired
pot, the bellows and the forge hearth. Textures are painted on vanilla's own ramps (oak planks, leather, bricks, clay,
coal and fire), read from the 26.3 client jar (it holds assets/minecraft), in the vanilla style (docs/Vanilla-Style.md):

    python -I tools/gen_smithing.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.tinkering.kiln.Kiln and com.jaymospowers.tinkering.forge.Forge. The lang lines are
merged into lang/parts/tinkering.json (tools/tinkering_assets.py rewrites that file: run this after it).
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MC_DATA = os.path.join(RES, "data", "minecraft")
NS = "jaymospowers"
TEX = NS + ":block/tinkering/"

LANG = {
    "block.jaymospowers.kiln_load": "Kiln Load",
    "block.jaymospowers.unfired_pot": "Unfired Pot",
    "block.jaymospowers.bellows": "Bellows",
    "block.jaymospowers.forge_hearth": "Forge Hearth",
    "tooltip.jaymospowers.tinkering.unfired_pot": "Fire it in a brick kiln to make a decorated pot.",
    "tooltip.jaymospowers.tinkering.bellows": "Pump it (or blow wind into it): the fire it faces burns hotter for a while.",
    "tooltip.jaymospowers.tinkering.forge_hearth": "Burns coal or charcoal hot enough for netherrack and grout.",
    "message.jaymospowers.forge_hearth.no_fuel": "The hearth needs coal or charcoal.",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def ramp_of(im, n=None):
    """A texture's opaque colours, darkest first (all of them, or n spread over the range)."""
    cols = sorted({c[:3] for c in im.convert("RGBA").getdata() if c[3] == 255}, key=lambda c: c[0] * 3 + c[1] * 6 + c[2])
    if n is None or n >= len(cols):
        return cols
    return [cols[round(i * (len(cols) - 1) / (n - 1))] for i in range(n)]


def paint(rows, pal, base=None):
    """16 rows of keys into pal; '.' keeps the base texture (or leaves it clear)."""
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = base.copy().convert("RGBA") if base is not None else Image.new("RGBA", (16, 16))
    px = im.load()
    for y, row in enumerate(rows):
        for x, k in enumerate(row):
            if k != ".":
                px[x, y] = pal[k] + (255,)
    return im


def textures(jar):
    with zipfile.ZipFile(jar) as z:
        def vanilla(rel):
            im = Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")
            return im.crop((0, 0, 16, 16))

        planks, bricks, clay = vanilla("block/oak_planks"), vanilla("block/bricks"), vanilla("block/clay")
        wood = ramp_of(planks)  # 7 browns
        leather = ramp_of(vanilla("item/leather"))  # 6 browns
        brick = ramp_of(bricks)
        coal = ramp_of(vanilla("block/coal_block"))
        fire = ramp_of(vanilla("block/fire_0"), 7)
        clayr = ramp_of(clay)
        iron = [(0x44, 0x44, 0x44), (0x6b, 0x6b, 0x6b), (0x96, 0x96, 0x96), (0xbe, 0xbe, 0xbe)]
        out = os.path.join(ASSETS, "textures", "block", "tinkering")
        os.makedirs(out, exist_ok=True)

        # bellows: oak boards top and bottom, leather folds between; the front has an iron nozzle
        pal = {"0": wood[0], "1": wood[1], "2": wood[3], "3": wood[5],
               "a": leather[0], "b": leather[1], "c": leather[2], "d": leather[3], "e": leather[4], "f": leather[5],
               "i": iron[0], "j": iron[1], "k": iron[2], "l": iron[3]}
        side = (["." * 16] * 3 + ["0" * 16]
                + ["edcbbcdeedcbbcde", "fedccdeffedccdef", "dcbaabcddcbaabcd", "edcbbcdeedcbbcde",
                   "fedccdeffedccdef", "dcbaabcddcbaabcd", "edcbbcdeedcbbcde", "cbaaaabccbaaaabc"]
                + ["0" * 16] + ["." * 16] * 3)
        paint(side, pal, planks).save(os.path.join(out, "bellows_side.png"))
        front = (["." * 16] * 3 + ["0" * 16]
                 + ["edcbbcdeedcbbcde", "fedccdljkdccdeff", "dcbaabkiijbaabcd", "edcbbcjiiibbcdee",
                    "fedccdjiiiccdeff", "dcbaabkjjjbaabcd", "edcbbcdeedcbbcde", "cbaaaabccbaaaabc"]
                 + ["0" * 16] + ["." * 16] * 3)
        paint(front, pal, planks).save(os.path.join(out, "bellows_front.png"))
        top = ["." * 16] * 6 + ["......3332......", ".....3l..k1.....", ".....2k..j1.....", "......2111......"] + ["." * 16] * 6
        paint(top, pal, planks).save(os.path.join(out, "bellows_top.png"))

        # forge hearth: vanilla bricks; the front is an arched mouth full of coal (glowing when lit); the top a coal bed
        bp = {"m": brick[0], "n": brick[1], "o": brick[3]}
        for lit in (False, True):
            hot = fire if lit else None
            p = dict(bp)
            p.update({"0": coal[0], "1": coal[1], "2": coal[2], "3": coal[3], "4": coal[4]})
            if lit:
                p.update({"5": hot[0], "6": hot[1], "7": hot[2], "8": hot[3], "9": hot[5]})
            else:
                p.update({"5": coal[1], "6": coal[2], "7": coal[3], "8": coal[2], "9": coal[4]})
            mouth = (["." * 16] * 4
                     + ["....mnnnnnnm....", "...m00000000m...", "..m0000000000m..", "..m0005665000m..",
                        "..m0056776500m..", "..m0567887650m..", "..m5678998765m..", "..m2468998642m..",
                        "..m3142424143m..", "..mmmmmmmmmmmm..", "." * 16, "." * 16])
            paint(mouth, p, bricks).save(os.path.join(out, "forge_hearth_front" + ("_on" if lit else "") + ".png"))
            bed = (["." * 16] * 2
                   + ["..mmmmmmmmmmmm..", "..m1324132413m..", "..m4152637142m..", "..m2637485263m..",
                      "..m3148596314m..", "..m4259895241m..", "..m1426598652m..", "..m3152847153m..",
                      "..m2413627142m..", "..m4231524231m..", "..m1342413241m..", "..mmmmmmmmmmmm.."]
                   + ["." * 16] * 2)
            paint(bed, p, bricks).save(os.path.join(out, "forge_hearth_top" + ("_on" if lit else "") + ".png"))
        bricks.save(os.path.join(out, "forge_hearth_side.png"))

        # unfired pot: soft grey clay, lit from the top-left, a groove round the belly
        cp = {str(i): c for i, c in enumerate(clayr)}
        pot = ["5554444444443332", "5444444444433332", "4444444444433321", "4443333333332221",
               "4433333333332221", "4433333333332211", "1111111111111110", "3322222222221110",
               "4433333333332221", "4433333333332211", "4433333333322211", "4333333333322211",
               "4333333333222111", "3333333322222111", "3332222222221110", "2222222221111100"]
        paint(pot, cp).save(os.path.join(out, "unfired_pot.png"))


def models():
    mb = os.path.join(ASSETS, "models", "block", "tinkering")
    write(os.path.join(mb, "bellows.json"), {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "front": TEX + "bellows_front", "side": TEX + "bellows_side", "top": TEX + "bellows_top", "bottom": TEX + "bellows_top"}})
    # pumped: squeezed shut, the top board down on the folds
    face = lambda tex, uv: {"texture": "#" + tex, "uv": uv}
    write(os.path.join(mb, "bellows_pumped.json"), {"parent": "minecraft:block/block", "textures": {
        "particle": TEX + "bellows_side", "front": TEX + "bellows_front", "side": TEX + "bellows_side", "top": TEX + "bellows_top"},
        "elements": [{"from": [0, 0, 0], "to": [16, 10, 16], "faces": {
            "north": face("front", [0, 6, 16, 16]), "south": face("side", [0, 6, 16, 16]), "east": face("side", [0, 6, 16, 16]),
            "west": face("side", [0, 6, 16, 16]), "up": dict(face("top", [0, 0, 16, 16])),
            "down": dict(face("top", [0, 0, 16, 16]), cullface="down")}}]})
    for lit in ("", "_on"):
        write(os.path.join(mb, "forge_hearth" + lit + ".json"), {"parent": "minecraft:block/orientable_with_bottom", "textures": {
            "front": TEX + "forge_hearth_front" + lit, "side": TEX + "forge_hearth_side", "top": TEX + "forge_hearth_top" + lit,
            "bottom": TEX + "forge_hearth_side"}})
    box = lambda f, t: {"from": f, "to": t, "faces": {d: {"texture": "#pot", "uv": uv(d, f, t)}
                                                      for d in ("north", "south", "east", "west", "up", "down")}}

    def uv(d, f, t):
        if d in ("up", "down"):
            return [f[0], f[2], t[0], t[2]]
        a, b = (f[0], t[0]) if d in ("north", "south") else (f[2], t[2])
        return [a, 16 - t[1], b, 16 - f[1]]

    write(os.path.join(mb, "unfired_pot.json"), {"parent": "minecraft:block/block", "textures": {
        "particle": TEX + "unfired_pot", "pot": TEX + "unfired_pot"},
        "elements": [box([2, 0, 2], [14, 11, 14]), box([5, 11, 5], [11, 13, 11]), box([4, 13, 4], [12, 16, 12])]})
    # the load draws its items itself (KilnLoadRenderer); the model is only for its break particles
    write(os.path.join(mb, "kiln_load.json"), {"textures": {"particle": "minecraft:block/clay"}})

    bs = os.path.join(ASSETS, "blockstates")
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write(os.path.join(bs, "bellows.json"), {"variants": {
        "facing=%s,pumped=%s" % (f, p): {"model": NS + ":block/tinkering/bellows" + ("_pumped" if p == "true" else ""), "y": r}
        for f, r in rot.items() for p in ("false", "true")}})
    write(os.path.join(bs, "forge_hearth.json"), {"variants": {
        "facing=%s,lit=%s" % (f, l): {"model": NS + ":block/tinkering/forge_hearth" + ("_on" if l == "true" else ""), "y": r}
        for f, r in rot.items() for l in ("false", "true")}})
    write(os.path.join(bs, "unfired_pot.json"), {"variants": {"": {"model": NS + ":block/tinkering/unfired_pot"}}})
    write(os.path.join(bs, "kiln_load.json"), {"variants": {"": {"model": NS + ":block/tinkering/kiln_load"}}})
    for item in ("bellows", "forge_hearth", "unfired_pot"):
        write(os.path.join(ASSETS, "items", item + ".json"),
              {"model": {"type": "minecraft:model", "model": NS + ":block/tinkering/" + item}})


def data():
    for block in ("bellows", "forge_hearth", "unfired_pot"):
        write(os.path.join(DATA, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "random_sequence": NS + ":blocks/" + block, "pools": [{
                "rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + block}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    out = os.path.join(DATA, "recipe", "tinkering")
    write(os.path.join(out, "unfired_pot.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "key": {"#": "minecraft:clay_ball"},
        "pattern": [" # ", "# #", " # "], "result": {"id": NS + ":unfired_pot"}})
    write(os.path.join(out, "bellows.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"P": "#minecraft:planks", "L": "minecraft:leather", "C": "minecraft:copper_ingot"},
        "pattern": ["PPP", "LLL", "PCP"], "result": {"id": NS + ":bellows"}})
    write(os.path.join(out, "forge_hearth.json"), {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "key": {"B": "minecraft:bricks", "F": "minecraft:campfire"},
        "pattern": ["B B", "BFB", "BBB"], "result": {"id": NS + ":forge_hearth"}})
    write(os.path.join(DATA, "tags", "block", "kiln_walls.json"), {"values": [
        "minecraft:bricks", "minecraft:nether_bricks", "minecraft:red_nether_bricks", "minecraft:mud_bricks"]})
    # raw ore and ores: slow in a plain furnace (SlowOreSmeltMixin)
    opt = lambda t: {"id": t, "required": False}
    write(os.path.join(DATA, "tags", "item", "slow_smelt.json"), {"values": [
        "#minecraft:iron_ores", "#minecraft:gold_ores", "#minecraft:copper_ores", "#minecraft:coal_ores",
        "#minecraft:diamond_ores", "#minecraft:emerald_ores", "#minecraft:lapis_ores", "#minecraft:redstone_ores",
        "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore", "minecraft:ancient_debris",
        "minecraft:raw_iron", "minecraft:raw_gold", "minecraft:raw_copper",
        NS + ":silver_ore", NS + ":deepslate_silver_ore", NS + ":raw_silver",
        opt("#c:ores"), opt("#c:raw_materials")]})
    for tag, blocks in (("mineable/axe", ["bellows"]), ("mineable/pickaxe", ["forge_hearth"]), ("mineable/shovel", ["unfired_pot"])):
        path = os.path.join(MC_DATA, "tags", "block", tag + ".json")
        old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"values": []}
        ids = [NS + ":" + b for b in blocks]
        write(path, dict(old, values=[v for v in old["values"] if v not in ids] + ids))


def lang():
    path = os.path.join(ASSETS, "lang", "parts", "tinkering.json")
    old = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    old.update(LANG)
    write(path, old)


def main():
    if len(sys.argv) != 2 or not os.path.isfile(sys.argv[1]):
        sys.exit(__doc__)
    textures(sys.argv[1])
    models()
    data()
    lang()


if __name__ == "__main__":
    main()
