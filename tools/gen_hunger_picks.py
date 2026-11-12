"""Paints the Gaunt (kingdoms/src/main/java/com/jaymoskingdoms/hunger/Gaunt.java, drawn by GauntModel): its 128x64 skin, the
Gaunt's heart (16x16 item art), the heart's item model and item definition (26.3: `minecraft:item/generated` with a `layer0`
texture and the `minecraft:model` definition) and the loot table of the Gaunt. Run it from the repository root:

    python -I tools/gen_hunger_picks.py

The skin follows GauntModel's boxes (each part's unwrap at the offsets listed in PARTS below): a tall, starved figure in
ash-grey skin drawn tight over the ribs, bone antlers, sunken black eyes with a faint ember in them. Needs only Pillow.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "kingdoms", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymoskingdoms")
DATA = os.path.join(RES, "data", "jaymoskingdoms")


def rgb(h, a=255):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


SKIN = [rgb(c) for c in ("6d7378", "7f868b", "949ba0", "aab1b5")]
DEEP = rgb("3b4146")
BONE = [rgb(c) for c in ("9c917b", "b9ae96", "d6ccb4", "ece4cf")]
HOLLOW = rgb("0d0f12")
EMBER = rgb("e08a3c")
SINEW = rgb("4a3a3a")

# part: (u, v, width, height, depth), as GauntModel's texOffs and boxes
PARTS = {
    "head": (0, 0, 6, 8, 6),
    "snout": (0, 16, 3, 3, 3),
    "jaw": (14, 16, 3, 2, 3),
    "antler": (0, 24, 1, 7, 1),
    "tine": (6, 24, 1, 4, 1),
    "body": (32, 0, 8, 16, 4),
    "arm": (60, 0, 3, 20, 3),
    "leg": (76, 0, 3, 16, 3),
}


def faces(u, v, w, h, d):
    """The six faces of a box unwrap, as (name, x, y, width, height)."""
    return [
        ("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d),
        ("right", u, v + d, d, h), ("front", u + d, v + d, w, h),
        ("left", u + d + w, v + d, d, h), ("back", u + 2 * d + w, v + d, w, h),
    ]


def paint(img, box, ramp, rng, shade=True):
    px = img.load()
    for name, x, y, w, h in faces(*box):
        for j in range(h):
            for i in range(w):
                # light from the top left: lighter up and to the left, darker down and to the right
                tone = 2 - (j * 2 // max(h, 1)) * (1 if shade else 0) // 1
                tone = max(0, min(len(ramp) - 1, tone + (1 if name == "top" else 0) - (1 if name in ("bottom", "left") else 0)))
                tone = max(0, min(len(ramp) - 1, tone + rng.choice((-1, 0, 0, 0, 1))))
                px[x + i, y + j] = ramp[tone]


def skin():
    rng = random.Random(7)
    img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    for key, ramp in (("head", SKIN), ("snout", SKIN), ("jaw", SKIN), ("antler", BONE), ("tine", BONE), ("body", SKIN),
                      ("arm", SKIN), ("leg", SKIN)):
        paint(img, PARTS[key], ramp, rng)
    px = img.load()
    # the face: two sunken eyes with an ember in each, a dark mouth line on the snout and the jaw
    _, hx, hy, _, _ = faces(*PARTS["head"])[3]
    for dx in (1, 4):
        px[hx + dx, hy + 3] = HOLLOW
        px[hx + dx, hy + 4] = HOLLOW
        px[hx + dx + (1 if dx == 1 else -1), hy + 3] = HOLLOW if dx == 1 else HOLLOW
    px[hx + 1, hy + 4] = EMBER
    px[hx + 4, hy + 4] = EMBER
    for i in range(6):
        px[hx + i, hy + 1] = DEEP if i in (0, 5) else px[hx + i, hy + 1]
    _, sx, sy, sw, sh = faces(*PARTS["snout"])[3]
    px[sx, sy + 2] = HOLLOW
    px[sx + 1, sy + 2] = HOLLOW
    px[sx + 2, sy + 2] = HOLLOW
    # the ribs: dark lines across the body's front, and the spine down its back
    _, bx, by, bw, bh = faces(*PARTS["body"])[3]
    for j in range(2, bh - 2, 3):
        for i in range(1, bw - 1):
            px[bx + i, by + j] = DEEP
        for i in range(1, bw - 1, 2):
            px[bx + i, by + j + 1] = SKIN[0]
    _, kx, ky, kw, kh = faces(*PARTS["body"])[5]
    for j in range(kh):
        px[kx + kw // 2, ky + j] = DEEP if j % 2 == 0 else SKIN[0]
    # sinew at the elbows and knees
    for key in ("arm", "leg"):
        for name, x, y, w, h in faces(*PARTS[key])[2:]:
            for i in range(w):
                px[x + i, y + h // 2] = SINEW
    # the claws: pale tips on the hands
    for name, x, y, w, h in faces(*PARTS["arm"])[2:]:
        for i in range(w):
            px[x + i, y + h - 1] = BONE[1]
    return img


HEART = {
    # a dark heart held in frost: rows of (start, end) columns
    3: [(4, 6), (9, 11)], 4: [(3, 7), (8, 12)], 5: [(3, 12)], 6: [(3, 12)], 7: [(4, 11)], 8: [(4, 11)],
    9: [(5, 10)], 10: [(6, 9)], 11: [(7, 8)],
}


def heart():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    body = [rgb(c) for c in ("2a0d18", "4a1426", "6e1d38", "8f2c4c")]
    frost = [rgb(c) for c in ("6fa3c2", "a8d4ea", "e4f4fb")]
    for y, spans in HEART.items():
        for a, b in spans:
            for x in range(a, b + 1):
                px[x, y] = body[1]
    for y, spans in HEART.items():
        for a, b in spans:
            px[a, y] = body[3] if y < 7 else body[2]
            px[b, y] = body[0]
    for y in (10, 11):
        for x in range(16):
            if px[x, y][3] and y == 11:
                px[x, y] = body[0]
    # veins of frost, and a few crystals along the top
    for x, y in ((6, 5), (7, 6), (8, 7), (9, 6), (5, 7), (6, 8), (10, 8)):
        px[x, y] = frost[0]
    for x, y in ((4, 2), (11, 2), (7, 2), (3, 3), (12, 3)):
        px[x, y] = frost[1]
    px[7, 1] = frost[2]
    px[4, 1] = frost[2]
    # the stump of the vessels
    for x in (7, 8):
        px[x, 2] = body[2]
        px[x, 3] = body[1]
    # outline
    solid = {(x, y) for x in range(16) for y in range(16) if px[x, y][3]}
    edge = rgb("13060b")
    for (x, y) in solid:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in solid:
                px[nx, ny] = edge
    return img


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main():
    os.makedirs(os.path.join(ASSETS, "textures", "entity", "legend"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures", "item"), exist_ok=True)
    skin().save(os.path.join(ASSETS, "textures", "entity", "legend", "gaunt.png"))
    heart().save(os.path.join(ASSETS, "textures", "item", "gaunts_heart.png"))
    write_json(os.path.join(ASSETS, "models", "item", "gaunts_heart.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/gaunts_heart"}})
    write_json(os.path.join(ASSETS, "items", "gaunts_heart.json"),
               {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/gaunts_heart"}})
    # the Gaunt's drop: its heart, and bones now and then
    write_json(os.path.join(DATA, "loot_table", "entities", "gaunt.json"), {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "jaymoskingdoms:gaunts_heart"}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:bone", "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}},
                {"function": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": 0.0, "max": 1.0},
                 "enchantment": "minecraft:looting"}]}]},
        ],
    })


if __name__ == "__main__":
    main()
