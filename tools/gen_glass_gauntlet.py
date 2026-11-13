"""Writes the assets of the glass gauntlet (com.jaymospowers.power.arsenal.GlassGauntlet): its two icons (claws out, open
hand), the item definition that picks one by custom model data, and its lang. Run from the repo root:

    python3 -I tools/gen_glass_gauntlet.py
"""
import json
import os

from PIL import Image

ASSETS = "src/main/resources/assets/jaymospowers"
GLASS = {"#": (18, 14, 26), "x": (46, 36, 66), "o": (104, 84, 150), "w": (206, 194, 250), "e": (150, 70, 40)}
# claws out: the cuff low left, three long claws raking up to the right
FIGHT = [
    "...........#..#.", "..........#w#.#w", ".........#o#.#o#", "....#...#o#.#o#.", "...#w#.#x#.#x#..", "...#o##x#.#x#...", "...#xx#x##x#....",
    "..#xoxxxxxx#....", "..#xxoxxxx#.....", ".#xxxxoxx#......", ".#exxxxx#.......", "#xxexxx#........", "#oxxex#.........", "#xoxx#..........", ".#xx#...........",
    "..##............"]
# open hand: the same cuff, the fingers folded to a broad working edge
WORK = [
    "................", "................", ".......####.....", "......#owox#....", ".....#oxxxxx#...", "....#oxxxxxxx#..", "...#xxxxxxxxx#..",
    "..#xoxxxxxxxx#..", "..#xxoxxxxxx#...", ".#xxxxoxxxx#....", ".#exxxxxxx#.....", "#xxexxxx##......", "#oxxexx#........", "#xoxxx#.........", ".#xxx#..........",
    "..###..........."]
LANG = {
    "item.jaymospowers.glass_gauntlet": "Glass Gauntlet",
    "item.jaymospowers.glass_gauntlet.lore1": "Black glass, still warm. It fits whatever hand finds it.",
    "item.jaymospowers.glass_gauntlet.lore2": "Sneak and right click: claws out, or an open hand that digs, chops, shovels and tills.",
    "item.jaymospowers.glass_gauntlet.lore3": "Sneak and Reach, empty-handed: leave it in the rift, or call it back.",
    "message.jaymospowers.glass_gauntlet.work": "Open hand",
    "message.jaymospowers.glass_gauntlet.fight": "Claws out",
    "message.jaymospowers.glass_gauntlet.left": "The gauntlet stays in the rift.",
    "message.jaymospowers.glass_gauntlet.called": "The rift will bring the gauntlet first.",
    "jei.jaymospowers.glass_gauntlet": "Nobody makes one and no chest holds one. Once in a long while an Arsenal's Reach closes on it, and from then on the rift brings it first. Claws out it is a quick claw that guards; sneak and right click opens the hand, which digs, chops, shovels and tills like diamond, strips logs and tills soil.",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def paint(rows, rel):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    assert len(rows) == 16, rel
    for y, row in enumerate(rows):
        assert len(row) == 16, (rel, y, len(row))
        for x, c in enumerate(row):
            if c in GLASS:
                img.putpixel((x, y), GLASS[c] + (255,))
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    models = {}
    for mode, rows in (("fight", FIGHT), ("work", WORK)):
        rel = "item/glass_gauntlet" + ("" if mode == "fight" else "_work")
        paint(rows, rel)
        write(os.path.join(ASSETS, "models", rel + ".json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:" + rel}})
        models[mode] = {"type": "minecraft:model", "model": "jaymospowers:" + rel}
    write(os.path.join(ASSETS, "items", "glass_gauntlet.json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": "work", "model": models["work"]}], "fallback": models["fight"]}})
    write(os.path.join(ASSETS, "lang", "parts", "glass_gauntlet.json"), LANG)


if __name__ == "__main__":
    main()
