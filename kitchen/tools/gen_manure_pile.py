"""Droppings (ManurePileBlock): textures, blockstate and models for every kind, size and age, and the outline table
(SHAPES) written into ManurePileBlock.java between its SHAPES-BEGIN/END markers.

Each kind looks like the real thing: a plain dung heap (heap), cow pats (pats), horse apples (apples), deer and sheep
pellets (pellets), pig logs (logs), bird splashes with their white caps (bird), and a hunter's twisted scat (scat).
Each pat adds a bit: they spread out on the ground first, then heap up. Ages: 0 fresh, 1 crusted, 2 dry (paler).
Run from kitchen/: python3 tools/gen_manure_pile.py
"""
import json
import os
import random
import re

from PIL import Image

ASSETS = "src/main/resources/assets/jaymoskitchen"
JAVA = "src/main/java/com/jaymoskitchen/farm/ManurePileBlock.java"
MAX = 8
KINDS = ["heap", "pats", "apples", "pellets", "logs", "bird", "scat"]
AGES = 3

# texture palettes: dark, mid, light, flecks (straw, hair, bone), fleck chance
PALETTES = {
    "heap": ((52, 36, 19), (78, 55, 30), (101, 74, 41), (166, 140, 72), 0.05),
    "pats": ((50, 44, 22), (72, 63, 32), (92, 82, 44), (120, 112, 60), 0.04),
    "apples": ((88, 66, 30), (116, 90, 44), (140, 112, 58), (196, 170, 96), 0.18),
    "pellets": ((26, 18, 12), (40, 28, 18), (58, 42, 26), (78, 60, 38), 0.06),
    "logs": ((60, 48, 34), (82, 66, 46), (104, 86, 62), (130, 112, 84), 0.05),
    "bird": ((58, 62, 38), (76, 80, 50), (98, 100, 66), (120, 120, 90), 0.05),
    "scat": ((58, 52, 46), (80, 72, 62), (100, 92, 80), (200, 196, 184), 0.16),
}
DRY = (172, 162, 140)


def shade(c, age, v):
    k = (0.0, 0.2, 0.42)[age]
    return tuple(max(0, min(255, int(c[i] + (DRY[i] - c[i]) * k) + v)) for i in range(3)) + (255,)


def textures():
    os.makedirs(f"{ASSETS}/textures/block/droppings", exist_ok=True)
    for kind, (dark, mid, light, fleck, odds) in PALETTES.items():
        for age in range(AGES):
            rnd = random.Random(kind)
            img = Image.new("RGBA", (16, 16))
            for y in range(16):
                for x in range(16):
                    r = rnd.random()
                    c = dark if r < 0.3 else mid if r < 0.75 else light
                    if rnd.random() < odds:
                        c = fleck
                    img.putpixel((x, y), shade(c, age, rnd.randint(-6, 6)))
            for _ in range(5):
                x, y = rnd.randrange(16), rnd.randrange(16)
                for _ in range(rnd.randint(2, 4)):
                    img.putpixel((x % 16, y % 16), shade(tuple(int(v * 0.7) for v in dark), age, 0))
                    x += rnd.choice((-1, 0, 1))
                    y += rnd.choice((0, 1))
            img.save(f"{ASSETS}/textures/block/droppings/{kind}_{age}.png")
    # bird caps: chalk white, greying with age
    for age in range(AGES):
        rnd = random.Random("cap")
        img = Image.new("RGBA", (16, 16))
        base = ((236, 236, 226), (214, 212, 198), (190, 186, 170))[age]
        for y in range(16):
            for x in range(16):
                v = rnd.randint(-10, 6)
                img.putpixel((x, y), tuple(max(0, min(255, b + v)) for b in base) + (255,))
        img.save(f"{ASSETS}/textures/block/droppings/bird_cap_{age}.png")


def box(x0, z0, x1, z1, y0, y1, tex="#all"):
    x0, z0, x1, z1 = (max(0, min(16, v)) for v in (x0, z0, x1, z1))
    side = lambda a, b: [a, 16 - y1, b, 16 - y0]
    top = [x0, z0, x1, z1]
    return {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {
            "north": {"texture": tex, "uv": side(x0, x1)},
            "south": {"texture": tex, "uv": side(x0, x1)},
            "east": {"texture": tex, "uv": side(z0, z1)},
            "west": {"texture": tex, "uv": side(z0, z1)},
            "up": {"texture": tex, "uv": top},
            "down": {"texture": tex, "uv": top},
        },
    }


def lump(x0, z0, x1, z1, y0, y1):
    """A lump with a rounded top: the box, and a cap one pixel in and one pixel up if it's wide enough."""
    parts = [box(x0, z0, x1, z1, y0, y1)]
    if x1 - x0 >= 3 and z1 - z0 >= 3:
        parts.append(box(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y1, y1 + 1))
    return parts


# ---- each kind: the elements one pat adds, by its index (0-7) ----

HEAP = [(5, 5, 10, 10, 0, 2), (8, 7, 12, 12, 0, 2), (4, 7, 8, 11, 0, 2), (5, 5, 11, 11, 2, 4),
        (7, 4, 12, 8, 0, 2), (6, 6, 10, 10, 4, 6), (3, 3, 7, 7, 0, 3), (7, 7, 9, 9, 6, 8)]


def heap(i):
    return lump(*HEAP[i])


def pats(i):
    """Cow pats: flat round discs, side by side, then slopped on top of each other."""
    centres = [(8, 8, 3, 0), (5, 11, 3, 0), (11, 5, 3, 0), (11, 11, 3, 0), (5, 5, 3, 0), (8, 8, 2, 1), (7, 10, 2, 2), (9, 7, 2, 2)]
    cx, cz, r, y = centres[i]
    return [box(cx - r, cz - r + 1, cx + r, cz + r - 1, y, y + 1), box(cx - r + 1, cz - r, cx + r - 1, cz + r, y, y + 1),
            box(cx - r + 1, cz - r + 1, cx + r - 1, cz + r - 1, y + 1, y + 2)]


def apples(i):
    """Horse apples: a clutch of round balls each time."""
    centres = [(6, 6, 0), (9, 9, 0), (4, 10, 0), (10, 4, 0), (7, 7, 2), (11, 11, 0), (5, 9, 2), (8, 6, 4)]
    cx, cz, y = centres[i]
    out = []
    for dx, dz in ((0, 0), (3, 1), (1, 3)) if i < 6 else ((0, 0), (2, 2)):
        out += lump(cx + dx - 1, cz + dz - 1, cx + dx + 2, cz + dz + 2, y, y + 2)
    return out


def pellets(i):
    """Deer, sheep and goat pellets: a scatter of little beads in a patch, the later ones piled in the middle."""
    rnd = random.Random(f"pellets{i}")
    centres = [(8, 8), (5, 11), (11, 5), (11, 11), (5, 5), (8, 8), (7, 9), (9, 7)]
    cx, cz = centres[i]
    y = 0 if i < 5 else (1 if i < 7 else 2)
    out = []
    for _ in range(7):
        x, z = cx + rnd.randint(-3, 2), cz + rnd.randint(-3, 2)
        out.append(box(x, z, x + 1, z + 1, y, y + 1))
    return out


def logs(i):
    """Pig droppings: short lumpy logs, crossing as they pile."""
    spots = [(5, 6, "x", 0), (6, 9, "z", 0), (9, 4, "z", 0), (9, 10, "x", 0), (4, 4, "z", 0), (6, 7, "x", 2), (8, 5, "z", 2), (7, 8, "x", 4)]
    x, z, way, y = spots[i]
    long_, wide = 6, 2
    # the log, and a lump where it was broken off
    if way == "x":
        return [box(x, z, x + long_, z + wide, y, y + 2), box(x + 1, z, x + 3, z + wide, y + 2, y + 3)]
    return [box(x, z, x + wide, z + long_, y, y + 2), box(x, z + 3, x + wide, z + 5, y + 2, y + 3)]


def bird(i):
    """Bird droppings: little dark splashes, each with its chalk-white cap."""
    rnd = random.Random(f"bird{i}")
    centres = [(8, 8), (5, 11), (11, 5), (11, 11), (5, 5), (8, 4), (4, 8), (12, 8)]
    cx, cz = centres[i]
    out = []
    for _ in range(3):
        x, z = cx + rnd.randint(-2, 1), cz + rnd.randint(-2, 1)
        out.append(box(x, z, x + 2, z + 2, 0, 1))
        out.append(box(x, z, x + 1, z + 1, 1, 2, "#cap"))
    return out


def scat(i):
    """A hunter's scat: twisted ropes with a tapered end, full of fur and bits of bone."""
    spots = [(4, 7, 0, 0), (7, 4, 1, 0), (5, 10, 0, 0), (10, 6, 1, 0), (5, 6, 0, 2), (8, 6, 1, 2), (6, 9, 0, 2), (7, 7, 1, 4)]
    x, z, turn, y = spots[i]
    segs = [(0, 0, 3, 2, 2), (2, 1, 5, 3, 2), (4, 2, 7, 3, 1)]
    out = []
    for a0, b0, a1, b1, h in segs:
        if turn:
            out.append(box(x + b0, z + a0, x + b1, z + a1, y, y + h))
        else:
            out.append(box(x + a0, z + b0, x + a1, z + b1, y, y + h))
    return out


SHAPERS = {"heap": heap, "pats": pats, "apples": apples, "pellets": pellets, "logs": logs, "bird": bird, "scat": scat}


def rotated(x0, z0, x1, z1, turns):
    for _ in range(turns):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return x0, z0, x1, z1


def main():
    textures()
    os.makedirs(f"{ASSETS}/models/block/droppings", exist_ok=True)
    variants = {}
    table = []
    for kind in KINDS:
        row = []
        elements = []
        for n in range(1, MAX + 1):
            elements = elements + SHAPERS[kind](n - 1)
            tex = {"all": f"jaymoskitchen:block/droppings/{kind}_0", "particle": f"jaymoskitchen:block/droppings/{kind}_0"}
            if kind == "bird":
                tex["cap"] = "jaymoskitchen:block/droppings/bird_cap_0"
            name = f"{kind}_{n}"
            with open(f"{ASSETS}/models/block/droppings/{name}.json", "w") as f:
                json.dump({"parent": "minecraft:block/block", "textures": tex, "elements": elements}, f, indent=2)
                f.write("\n")
            for age in range(AGES):
                model = f"jaymoskitchen:block/droppings/{name}"
                if age:
                    aged = {"all": f"jaymoskitchen:block/droppings/{kind}_{age}", "particle": f"jaymoskitchen:block/droppings/{kind}_{age}"}
                    if kind == "bird":
                        aged["cap"] = f"jaymoskitchen:block/droppings/bird_cap_{age}"
                    with open(f"{ASSETS}/models/block/droppings/{name}_age{age}.json", "w") as f:
                        json.dump({"parent": model, "textures": aged}, f, indent=2)
                        f.write("\n")
                    model += f"_age{age}"
                variants[f"age={age},kind={kind},pats={n}"] = [{"model": model, **({"y": 90 * t} if t else {})} for t in range(4)]
            xs, zs = [], []
            for e in elements:
                for t in range(4):
                    x0, z0, x1, z1 = rotated(e["from"][0], e["from"][2], e["to"][0], e["to"][2], t)
                    xs += [x0, x1]
                    zs += [z0, z1]
            top = max(e["to"][1] for e in elements)
            row.append(f"Block.box({min(xs)}.0, 0.0, {min(zs)}.0, {max(xs)}.0, {top}.0, {max(zs)}.0)")
        table.append("\t\t{" + ", ".join(row) + "},")
    with open(f"{ASSETS}/blockstates/manure_pile.json", "w") as f:
        json.dump({"variants": variants}, f, indent=2)
        f.write("\n")
    src = open(JAVA).read()
    src = re.sub(r"(// SHAPES-BEGIN\n).*?(\t\t// SHAPES-END)", lambda m: m.group(1) + "\n".join(table) + "\n" + m.group(2), src, flags=re.S)
    open(JAVA, "w").write(src)


if __name__ == "__main__":
    os.chdir(os.path.join(os.path.dirname(__file__), ".."))
    main()
