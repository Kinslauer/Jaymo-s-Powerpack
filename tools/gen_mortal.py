"""Writes the assets of the axe on a chain (com.jaymospowers.mortal): its icon, its head as a forged weapon part (stone and
iron looks, glowing when hot like every part), its Better Combat moves and the lang of the mortal kit. Run from the repo root:

    python3 -I tools/gen_mortal.py
"""
import json
import os

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers"
DATA = "src/main/resources/data/jaymospowers"
IRON = {"#": (60, 60, 64), "x": (160, 162, 166), "o": (226, 228, 230)}
STONE = {"#": (58, 54, 50), "x": (122, 118, 112), "o": (176, 172, 164)}
AXE = dict(IRON, w=(120, 86, 52), W=(84, 58, 36), c=(96, 98, 104), C=(52, 52, 58))
HEAD = [
    "................", "................", "......####......", ".....#oxxx##....", "....#oxxxxxx#...", "...#oxxxxxxxx#..", "...#xxxxxxxxx#..",
    "...#xxxx##xxx#..", "...#xxx#..#xx#..", "....#xx#..#x#...", "....#xxx##xx#...", ".....#xxxxx#....", "......#xxx#.....", ".......###......", "................",
    "................"]
ICON = [
    "..........###...", ".........#oxx#..", "........#oxxxx#.", ".......W#xxxxx#.", "......WwW#xxx#..", ".....WwW.#xx#...", "....WwW...##....",
    "...WwW..........", "..WwW...........", ".WwWc...........", ".WWc.C..........", "...C.c..........", "....c.C.........", ".....C.c........", "......c.C.......",
    ".......C........"]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def paint(rows, palette, rel):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (rel, y)
        for x, c in enumerate(row):
            if c in palette:
                img.putpixel((x, y), palette[c] + (255,))
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def hot(node):
    """A head fresh off the Smithing Anvil glows orange (jaymospowers:heated_at), like every weapon part."""
    def tint_all(n):
        if isinstance(n, dict):
            n = {k: tint_all(v) for k, v in n.items()}
            if n.get("type") == "minecraft:model":
                n["tints"] = [{"type": "minecraft:constant", "value": -26040}]
        elif isinstance(n, list):
            n = [tint_all(v) for v in n]
        return n
    return {"type": "minecraft:condition", "property": "minecraft:has_component", "component": "jaymospowers:heated_at",
            "on_true": tint_all(node), "on_false": node}


def main():
    models = {}
    for metal, palette in (("stone", STONE), ("iron", IRON)):
        rel = "item/tinkering/weapon_part/chain_axe_head_" + metal
        paint(HEAD, palette, rel)
        write(os.path.join(ASSETS, "models", rel + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + rel}})
        models[metal] = {"type": "minecraft:model", "model": "jaymospowers:" + rel}
    node = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": "stone", "model": models["stone"]}, {"when": "iron", "model": models["iron"]}], "fallback": models["iron"]}
    write(os.path.join(ASSETS, "items", "chain_axe_head.json"), {"model": hot(node)})
    paint(ICON, AXE, "item/chain_axe")
    write(os.path.join(ASSETS, "models", "item", "chain_axe.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/chain_axe"}})
    write(os.path.join(ASSETS, "items", "chain_axe.json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:item/chain_axe"}})
    write(os.path.join(DATA, "weapon_attributes", "chain_axe.json"), {"parent": "jaymospowers:combo_axe"})
    write(os.path.join(ASSETS, "lang", "parts", "mortal.json"), {
        "item.jaymospowers.chain_axe": "Chain Axe",
        "item.jaymospowers.chain_axe_head": "Chain Axe Head",
        "jei.jaymospowers.chain_axe": ("An axe on a length of chain. Right click throws it out nine blocks and hauls it back: whoever it meets is cut "
                                       "and tugged toward you. Sneak as you throw to drag them all the way in. Forged at the Tool Station from a "
                                       "Chain Axe Head, a Tool Handle and an iron chain."),
        "message.jaymospowers.mortal.power_strike": "Power Strike",
        "message.jaymospowers.mortal.sweep_kick": "Sweep Kick",
        "message.jaymospowers.mortal.second_wind": "Second Wind",
        "message.jaymospowers.mortal.battle_cry": "Battle Cry",
        "message.jaymospowers.mortal.wait": "%s: %s s",
        "message.jaymospowers.mortal.second_wind.fresh": "You have breath enough.",
    })


if __name__ == "__main__":
    main()
