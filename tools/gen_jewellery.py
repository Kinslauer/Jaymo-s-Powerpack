"""Draws the jewellery (0.19.87, Jewellery patch 4): frames, gem overlays, loose cut gems, the lapidary wheel and the
jeweller's bench, with their models, item definitions and blockstates. Everything is our own art. The gem overlays are
greyscale: the game tints them with the gem's colour (custom_model_data colour 0). Run it from the root:

    python -I tools/gen_jewellery.py [preview.png]

With a path it also writes an 8x preview sheet there. Source textures stay ungraded: run
`python -I tools/excalibur_style/grade.py` afterwards for the optional excalibur_style pack.
"""
import importlib.util
import json
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
NS = "jaymospowers"

# the baubles' palettes, loaded by path (python -I does not put this folder on the import path)
_spec = importlib.util.spec_from_file_location("gen_baubles", os.path.join(ROOT, "tools", "gen_baubles.py"))
_baubles = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_baubles)

GOLD = _baubles.GOLD
COPPER = _baubles.COPPER
SILVER = ((246, 248, 252, 255), (196, 204, 214, 255), (122, 130, 146, 255))
METALS = (("gold", GOLD), ("silver", SILVER), ("copper", COPPER))
KINDS = ("ring", "amulet", "bracelet", "circlet")
CUTS = ("cabochon", "table", "rose", "brilliant")
OUTLINE = (40, 28, 20, 255)
GEM_OUTLINE = (50, 50, 50, 255)
SOCKET = (46, 34, 30, 255)


def blank(size=16):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def put(img, x, y, color):
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def outline(img, color):
    """Every empty pixel touching a painted one (up, down, left, right) becomes the outline."""
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


def band(img, cx, cy, rx, ry, thick, tones):
    """An upright ring seen a little from above: an ellipse band, its far side dark, its near side lit."""
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            outer = (dx / rx) ** 2 + (dy / ry) ** 2
            inner = (dx / max(rx - thick, 0.5)) ** 2 + (dy / max(ry - thick, 0.5)) ** 2
            if outer <= 1.0 and inner > 1.0:
                if dy < 0:
                    tone = tones[0] if dx < 0 else tones[1]
                else:
                    tone = tones[1] if dx < 0 else tones[2]
                put(img, x, y, tone)


def bezel(img, x0, y0, n, metal, clip=False):
    """The setting: a metal frame one pixel wide around an n x n dark socket whose top left is (x0, y0)."""
    for y in range(y0 - 1, y0 + n + 1):
        for x in range(x0 - 1, x0 + n + 1):
            edge_top, edge_left = y == y0 - 1, x == x0 - 1
            edge_bottom, edge_right = y == y0 + n, x == x0 + n
            if clip and (edge_top or edge_bottom) and (edge_left or edge_right):
                continue
            if edge_top or edge_left:
                put(img, x, y, metal[0])
            elif edge_bottom or edge_right:
                put(img, x, y, metal[2])
            else:
                put(img, x, y, SOCKET)


# --- the four frames: each returns (image, (gem x0, gem y0, gem size)) ---

def ring_frame(metal):
    img = blank()
    band(img, 7.5, 11, 5.5, 4, 1.7, metal)
    bezel(img, 6, 3, 4, metal)
    outline(img, OUTLINE)
    return img, (6, 3, 4)


def amulet_frame(metal):
    img = blank()
    for i in range(6):
        put(img, 2 + i, i, metal[0] if i % 2 else metal[1])
        put(img, 13 - i, i, metal[2] if i % 2 else metal[1])
    bezel(img, 5, 7, 6, metal, clip=True)
    put(img, 7, 5, metal[1])
    put(img, 8, 5, metal[1])
    outline(img, OUTLINE)
    return img, (5, 7, 6)


def bracelet_frame(metal):
    img = blank()
    band(img, 7.5, 8, 7, 4.5, 2.0, metal)
    bezel(img, 6, 10, 4, metal)
    outline(img, OUTLINE)
    return img, (6, 10, 4)


def circlet_frame(metal):
    img = blank()
    for x in range(1, 15):
        for y in (10, 11, 12):
            put(img, x, y, metal[0] if y == 10 else metal[1] if y == 11 else metal[2])
    for x, top in ((1, 6), (2, 6), (3, 8), (4, 8), (11, 8), (12, 8), (13, 6), (14, 6)):
        for y in range(top, 10):
            put(img, x, y, metal[1] if x % 2 else metal[0])
    bezel(img, 6, 7, 4, metal)
    outline(img, OUTLINE)
    return img, (6, 7, 4)


FRAMES = {"ring": ring_frame, "amulet": amulet_frame, "bracelet": bracelet_frame, "circlet": circlet_frame}


# --- the four cuts, greyscale (tones 255 / 200 / 130 and a 255 highlight) ---

def gem_pixels(cut, n):
    """{(x, y): grey} for a gem of this cut filling an n x n box."""
    c = (n - 1) / 2.0
    out = {}
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            if cut == "cabochon":
                if (dx / (n / 2.0)) ** 2 + (dy / (n / 2.0)) ** 2 <= 1.0:
                    s = (dx + dy) / n
                    out[(x, y)] = 255 if s <= -0.25 else 130 if s >= 0.25 else 200
            elif cut == "table":
                rim = x in (0, n - 1) or y in (0, n - 1)
                if not rim:
                    out[(x, y)] = 255
                else:
                    out[(x, y)] = 200 if (x == 0 or y == 0) else 130
            elif cut == "rose":
                if (dx / (n / 2.0)) ** 2 + (dy / (n / 2.0)) ** 2 <= 1.15:
                    if abs(dx) < 0.6 and abs(dy) < 0.6:
                        out[(x, y)] = 255
                    else:
                        sector = int(((math.atan2(dy, dx) + math.pi) / (2 * math.pi)) * 6) % 3
                        out[(x, y)] = (255, 200, 130)[sector]
            else:
                top = n * 0.35
                half = c + 0.5 if y <= top else max(0.7, (c + 0.5) * (1 - (y - top) / (n - top)) + 0.2)
                if abs(dx) <= half:
                    if y <= top:
                        out[(x, y)] = 255 if y == 0 else 200
                    else:
                        out[(x, y)] = 255 if dx < -0.4 else 130 if dx > 0.4 else 200
    # a highlight pixel at the top left of the stone
    spots = sorted(out, key=lambda p: (p[1] + p[0], p[1]))
    if spots:
        out[spots[0]] = 255
    return out


def gem_overlay(cut, box):
    x0, y0, n = box
    img = blank()
    for (x, y), grey in gem_pixels(cut, n).items():
        put(img, x0 + x, y0 + y, (grey, grey, grey, 255))
    return img


def loose_gem(cut):
    """A loose greyscale stone, about 10 x 10 with a dark outline."""
    img = blank()
    for (x, y), grey in gem_pixels(cut, 10).items():
        put(img, 3 + x, 3 + y, (grey, grey, grey, 255))
    outline(img, GEM_OUTLINE)
    return img


# --- blocks ---

def noisy(rand, base, spread):
    v = rand.randint(-spread, spread)
    return tuple(max(0, min(255, b + v)) for b in base) + (255,)


def planks(seed, base=(160, 120, 70), boards=4):
    rand = random.Random(seed)
    img = blank()
    for y in range(16):
        for x in range(16):
            col = noisy(rand, base, 9)
            if y % boards == 0:
                col = tuple(int(c * 0.78) for c in col[:3]) + (255,)
            img.putpixel((x, y), col)
    return img


def wheel_stone():
    rand = random.Random(11)
    img = blank()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 7.8:
                base = (150, 150, 158) if d > 2.2 else (80, 80, 88)
                col = noisy(rand, base, 16)
                if 7.0 < d <= 7.8:
                    col = tuple(int(c * 0.8) for c in col[:3]) + (255,)
                img.putpixel((x, y), col)
    return img


def wheel_frame():
    img = planks(23, (150, 108, 62), 5)
    rand = random.Random(5)
    for x in range(16):
        for y in (7, 8):
            img.putpixel((x, y), noisy(rand, (150, 152, 160) if y == 7 else (104, 106, 116), 6))
    return img


def bench_top():
    rand = random.Random(31)
    img = planks(41, (140, 100, 58))
    for y in range(2, 14):
        for x in range(2, 14):
            img.putpixel((x, y), noisy(rand, (118, 58, 52), 8))
    # tiny tools: tweezers, a loupe and a blue stone
    for i in range(5):
        img.putpixel((4 + i, 4), (206, 210, 220, 255))
    img.putpixel((4, 5), (150, 154, 166, 255))
    for x, y in ((10, 9), (11, 9), (10, 10), (11, 10)):
        img.putpixel((x, y), (60, 100, 210, 255))
    img.putpixel((10, 9), (150, 190, 255, 255))
    for x, y in ((5, 10), (6, 10), (4, 11), (7, 11), (5, 12), (6, 12)):
        img.putpixel((x, y), (190, 150, 60, 255))
    return img


def bench_side():
    img = planks(53, (140, 100, 58))
    for x in range(16):
        img.putpixel((x, 3), (92, 62, 34, 255))
        img.putpixel((x, 4), (112, 78, 44, 255))
    return img


def bench_front():
    img = planks(67, (140, 100, 58))
    for x in range(2, 14):
        for y in (5, 13):
            img.putpixel((x, y), (82, 54, 30, 255))
    for y in range(5, 14):
        for x in (2, 13):
            img.putpixel((x, y), (82, 54, 30, 255))
    for x, y in ((7, 8), (8, 8), (7, 9), (8, 9)):
        img.putpixel((x, y), (226, 182, 70, 255))
    img.putpixel((7, 8), (255, 236, 130, 255))
    return img


def bench_bottom():
    return planks(79, (118, 84, 48))


# --- files ---

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        f.write(text)


def save(img, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def jwrite(rel, data):
    write(os.path.join(ASSETS, rel + ".json"), json.dumps(data, indent=2) + "\n")


def model(name):
    return {"type": "minecraft:model", "model": f"{NS}:{name}"}


def main():
    sheet = []
    boxes = {}
    for kind in KINDS:
        for metal_id, metal in METALS:
            img, box = FRAMES[kind](metal)
            boxes[kind] = box
            save(img, f"item/jewellery/{kind}_frame_{metal_id}")
            sheet.append(img)
            jwrite(f"models/item/jewellery/{kind}_frame_{metal_id}",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/jewellery/{kind}_frame_{metal_id}"}})
        for cut in CUTS:
            save(gem_overlay(cut, boxes[kind]), f"item/jewellery/{kind}_gem_{cut}")
            for metal_id, metal in METALS:
                jwrite(f"models/item/jewellery/{kind}_{metal_id}_{cut}",
                       {"parent": "minecraft:item/generated", "textures": {
                           "layer0": f"{NS}:item/jewellery/{kind}_frame_{metal_id}", "layer1": f"{NS}:item/jewellery/{kind}_gem_{cut}"}})
        # the frame's item definition: the metal picks the model (silver if unknown)
        jwrite(f"items/{kind}_frame", {"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": m, "model": model(f"item/jewellery/{kind}_frame_{m}")} for m, _ in METALS],
            "fallback": model(f"item/jewellery/{kind}_frame_silver")}})
        # the piece: metal, then cut, then the model with the gem's colour on layer 1
        tints = [{"type": "minecraft:constant", "value": -1},
                 {"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]

        def cut_select(metal_id):
            return {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1,
                    "cases": [{"when": c, "model": dict(model(f"item/jewellery/{kind}_{metal_id}_{c}"), tints=tints)} for c in CUTS],
                    "fallback": dict(model(f"item/jewellery/{kind}_{metal_id}_table"), tints=tints)}

        jwrite(f"items/gem_{kind}", {"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": m, "model": cut_select(m)} for m, _ in METALS],
            "fallback": cut_select("silver")}})
    for cut in CUTS:
        img = loose_gem(cut)
        save(img, f"item/jewellery/cut_gem_{cut}")
        sheet.append(img)
        jwrite(f"models/item/jewellery/cut_gem_{cut}",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/jewellery/cut_gem_{cut}"}})
    gem_tint = [{"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}]
    jwrite("items/cut_gem", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
        "cases": [{"when": c, "model": dict(model(f"item/jewellery/cut_gem_{c}"), tints=gem_tint)} for c in CUTS],
        "fallback": dict(model("item/jewellery/cut_gem_table"), tints=gem_tint)}})

    # blocks
    for name, img in (("lapidary_wheel_stone", wheel_stone()), ("lapidary_wheel_frame", wheel_frame()), ("jewellers_bench_top", bench_top()),
                      ("jewellers_bench_side", bench_side()), ("jewellers_bench_front", bench_front()), ("jewellers_bench_bottom", bench_bottom())):
        save(img, f"block/jewellery/{name}")
        sheet.append(img)
    def face(texture, uv=None):
        out = {"texture": f"#{texture}"}
        if uv:
            out["uv"] = uv
        return out

    def box(frm, to, texture):
        return {"from": frm, "to": to, "faces": {d: face(texture) for d in ("north", "east", "south", "west", "up", "down")}}

    jwrite("models/block/lapidary_wheel", {
        "parent": "minecraft:block/block",
        "textures": {"frame": f"{NS}:block/jewellery/lapidary_wheel_frame", "stone": f"{NS}:block/jewellery/lapidary_wheel_stone",
                     "particle": f"{NS}:block/jewellery/lapidary_wheel_frame"},
        "elements": [box([1, 0, 4], [15, 3, 12], "frame"), box([2, 3, 6], [4, 12, 10], "frame"), box([12, 3, 6], [14, 12, 10], "frame"),
                     box([4, 4, 7], [12, 12, 9], "stone")]})
    jwrite("models/block/jewellers_bench", {
        "parent": "minecraft:block/orientable_with_bottom",
        "textures": {"top": f"{NS}:block/jewellery/jewellers_bench_top", "front": f"{NS}:block/jewellery/jewellers_bench_front",
                     "side": f"{NS}:block/jewellery/jewellers_bench_side", "bottom": f"{NS}:block/jewellery/jewellers_bench_bottom"}})
    for name in ("lapidary_wheel", "jewellers_bench"):
        jwrite(f"items/{name}", {"model": model(f"block/{name}")})
        jwrite(f"blockstates/{name}", {"variants": {
            f"facing={d}": ({"model": f"{NS}:block/{name}", "y": y} if y else {"model": f"{NS}:block/{name}"})
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
    print("jewellery written")
    if len(sys.argv) > 1:
        scale = 8
        cols = 12
        rows = (len(sheet) + cols - 1) // cols
        out = Image.new("RGBA", (cols * (16 * scale + 8) + 8, rows * (16 * scale + 8) + 8), (198, 198, 198, 255))
        for i, img in enumerate(sheet):
            out.alpha_composite(img.resize((16 * scale, 16 * scale), Image.NEAREST),
                                (8 + (i % cols) * (16 * scale + 8), 8 + (i // cols) * (16 * scale + 8)))
        out.save(sys.argv[1])


if __name__ == "__main__":
    main()
