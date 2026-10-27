"""Draws the art and writes the model JSON for Z Fighter patch 6 track A (0.17.60, Andy's NOEA picks): the Gravity Machine
block, the Capsule Seal block, the three pocket capsules and the three fighter weapons (Telescoping Staff, Titanforged
Greatsword, Hero's Blade). Everything is our own pixel art (a one-pixel dark outline, three tones lit from the top left).
Run it from the root:

    python -I tools/gen_zfighter_lookout.py

26.3 item models name their faces through texture variables (``layer0``), never raw sprite ids, or they render purple;
this writes the same shape as the other simple items (``parent: minecraft:item/generated`` or ``handheld`` +
``textures.layer0``). The two blocks are full cubes (so full occlusion is right for them).
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NS = "jaymospowers"

OUTLINE = (28, 28, 36, 255)
CLEAR = (0, 0, 0, 0)


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def outline(img, color=OUTLINE):
    marks = []
    for y in range(img.height):
        for x in range(img.width):
            if img.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < img.width and 0 <= ny < img.height and img.getpixel((nx, ny))[3] != 0:
                        marks.append((x, y))
                        break
    for x, y in marks:
        img.putpixel((x, y), color)


def tone(c, k):
    """c lightened (k > 0) or darkened (k < 0) by k/10."""
    f = 1.0 + k / 10.0
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


def line(img, x0, y0, x1, y1, colors, width=1):
    """A diagonal bar from (x0, y0) to (x1, y1), width pixels thick; colors is (light, mid, dark) across its thickness."""
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        for w in range(width):
            off = w - (width - 1) / 2.0
            # across the bar is the up-left to down-right direction
            px = int(round(x - off * 0.7))
            py = int(round(y - off * 0.7))
            shade = colors[0] if off < -0.2 else colors[2] if off > 0.2 else colors[1]
            put(img, px, py, shade)


# ---------------------------------------------------------------- items


def capsule(body):
    """A pill lying on the diagonal: its lower left half in the kind's colour, its upper right half white, a dark band between."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    a, b = (4.5, 11.5), (11.5, 4.5)
    radius = 3.4
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            vx, vy = b[0] - a[0], b[1] - a[1]
            length2 = vx * vx + vy * vy
            t = max(0.0, min(1.0, ((px - a[0]) * vx + (py - a[1]) * vy) / length2))
            cx, cy = a[0] + vx * t, a[1] + vy * t
            d = math.hypot(px - cx, py - cy)
            if d > radius:
                continue
            if abs(t - 0.5) < 0.08:
                color = tone((60, 62, 74), 0)
            elif t < 0.5:
                color = body
            else:
                color = (236, 238, 244, 255)
            # lit from the top left: the upper left side lighter, the lower right darker
            side = (px - cx) * -0.7 + (py - cy) * -0.7
            if side > radius * 0.45:
                color = tone(color, 3)
            elif side < -radius * 0.45:
                color = tone(color, -3)
            img.putpixel((x, y), color)
    # a glint
    put(img, 5, 9, (255, 255, 255, 255))
    put(img, 6, 8, (255, 255, 255, 255))
    outline(img)
    return img


def staff():
    img = Image.new("RGBA", (16, 16), CLEAR)
    wood = ((176, 128, 78, 255), (138, 94, 52, 255), (96, 62, 34, 255))
    gold = ((255, 226, 120, 255), (222, 178, 60, 255), (150, 108, 30, 255))
    line(img, 2, 14, 13, 3, wood, 2)
    # the rings between its sections
    for t in (0.3, 0.55, 0.78):
        x, y = 2 + 11 * t, 14 - 11 * t
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(img, int(round(x)) + dx - 1, int(round(y)) + dy, gold[1])
    # the caps and the jewel at the top
    line(img, 1, 15, 2, 14, gold, 2)
    line(img, 13, 3, 14, 2, gold, 2)
    put(img, 14, 1, (110, 220, 255, 255))
    put(img, 15, 0, (210, 250, 255, 255))
    put(img, 13, 1, (60, 150, 220, 255))
    outline(img)
    return img


def sword(blade, edge, guard, grip, wide, gem):
    img = Image.new("RGBA", (16, 16), CLEAR)
    # the blade from the guard up to the tip
    line(img, 6, 9, 14, 1, (edge, blade, tone(blade, -3)), wide)
    put(img, 15, 0, edge)
    # the cross guard, across the blade's foot
    line(img, 3, 8, 7, 12, (guard[0], guard[1], guard[2]), 2)
    put(img, 3, 7, guard[1])
    put(img, 8, 13, guard[1])
    # the grip and the pommel
    line(img, 4, 11, 1, 14, (grip[0], grip[1], grip[2]), 1)
    put(img, 1, 14, gem)
    put(img, 0, 15, gem)
    put(img, 5, 10, gem)
    outline(img)
    return img


# ---------------------------------------------------------------- blocks


def plate(base, rivet, dial=None, lit=False, vents=False):
    img = Image.new("RGBA", (16, 16), base)
    dark, light = tone(base, -3), tone(base, 2)
    for i in range(16):
        for edge in (0, 15):
            put(img, i, edge, dark)
            put(img, edge, i, dark)
        put(img, i, 1, light)
        put(img, 1, i, light)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(img, x, y, rivet)
    if vents:
        for y in range(4, 12, 2):
            for x in range(4, 12):
                put(img, x, y, tone(base, -5))
                put(img, x, y + 1, light)
    if dial is not None:
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if 4.2 <= d < 5.4:
                    put(img, x, y, tone(base, 3))
                elif d < 4.2:
                    put(img, x, y, dial if lit else tone(dial, -5))
        for x, y in ((7, 7), (8, 7), (7, 8)):
            put(img, x, y, (255, 255, 255, 255) if lit else tone(dial, 4))
        # the needle
        for i in range(3):
            put(img, 8 + i, 6 - i, (255, 240, 255, 255) if lit else tone(dial, 6))
    return img


def seal():
    img = Image.new("RGBA", (16, 16), (126, 200, 236, 255))
    dark, light = (62, 120, 170, 255), (206, 240, 255, 255)
    for i in range(16):
        for edge in (0, 15):
            put(img, i, edge, dark)
            put(img, edge, i, dark)
        put(img, i, 1, light)
        put(img, 1, i, light)
    # a pill across the middle
    for y in range(16):
        for x in range(16):
            if 5 <= y <= 10 and 3 <= x <= 12:
                inside = (4 <= y <= 11) and ((x - 7.5) ** 2 / 20.0 + (y - 7.5) ** 2 / 9.0 <= 1.0)
                if inside:
                    put(img, x, y, (236, 238, 244, 255) if x >= 8 else (222, 70, 60, 255))
    for y in range(5, 11):
        put(img, 8, y, (60, 62, 74, 255))
    return img


# ---------------------------------------------------------------- files


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def save(img, kind, name):
    path = os.path.join(ASSETS, "textures", kind, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def item_files(name, parent="minecraft:item/generated"):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})


def block_item(name, model):
    write_json(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": f"{NS}:block/{model}"})


def main():
    # the pocket capsules
    for name, body in (("pocket_hut_capsule", (222, 70, 60, 255)), ("pocket_skiff_capsule", (60, 130, 214, 255)),
                       ("pocket_steed_capsule", (74, 170, 92, 255))):
        save(capsule(body), "item", name)
        item_files(name)
    # the weapons
    save(staff(), "item", "telescoping_staff")
    item_files("telescoping_staff", "minecraft:item/handheld")
    save(sword((178, 190, 214, 255), (232, 240, 255, 255), ((255, 226, 120, 255), (222, 178, 60, 255), (150, 108, 30, 255)),
               ((120, 80, 60, 255), (86, 54, 40, 255), (56, 34, 26, 255)), 3, (200, 50, 60, 255)), "item", "titanforged_greatsword")
    item_files("titanforged_greatsword", "minecraft:item/handheld")
    save(sword((96, 214, 232, 255), (214, 252, 255, 255), ((255, 226, 120, 255), (222, 178, 60, 255), (150, 108, 30, 255)),
               ((70, 90, 190, 255), (46, 60, 140, 255), (30, 40, 96, 255)), 2, (255, 214, 90, 255)), "item", "heros_blade")
    item_files("heros_blade", "minecraft:item/handheld")

    # the Gravity Machine: top and bottom plates, a side with a dial that lights up
    iron = (112, 116, 128, 255)
    rivet = (200, 204, 214, 255)
    save(plate(iron, rivet, vents=True), "block", "gravity_machine_top")
    save(plate(tone(iron, -3), rivet), "block", "gravity_machine_bottom")
    save(plate(iron, rivet, dial=(120, 90, 220, 255), lit=False), "block", "gravity_machine_side")
    save(plate(iron, rivet, dial=(190, 150, 255, 255), lit=True), "block", "gravity_machine_side_on")
    for state, side in (("off", "gravity_machine_side"), ("on", "gravity_machine_side_on")):
        write_json(os.path.join(ASSETS, "models", "block", f"gravity_machine_{state}.json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {"top": f"{NS}:block/gravity_machine_top", "bottom": f"{NS}:block/gravity_machine_bottom", "side": f"{NS}:block/{side}"}})
    write_json(os.path.join(ASSETS, "blockstates", "gravity_machine.json"), {"variants": {
        "lit=false": {"model": f"{NS}:block/gravity_machine_off"}, "lit=true": {"model": f"{NS}:block/gravity_machine_on"}}})
    block_item("gravity_machine", "gravity_machine_off")

    # the Capsule Seal
    save(seal(), "block", "capsule_seal")
    write_json(os.path.join(ASSETS, "models", "block", "capsule_seal.json"), {
        "parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/capsule_seal"}})
    write_json(os.path.join(ASSETS, "blockstates", "capsule_seal.json"), {"variants": {
        f"facing={d}": {"model": f"{NS}:block/capsule_seal"} for d in ("north", "south", "east", "west")}})
    block_item("capsule_seal", "capsule_seal")


if __name__ == "__main__":
    main()
