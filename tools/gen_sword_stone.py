"""Generates the sword in the stone and Durandal: block textures (blade, gold, gem, grip), the pedestal block model with
the sword driven into it (tilted, gold crossguard with a red stone, long grip), the empty pedestal, the item sprite,
models, item definitions, the blockstate and the worldgen. Run from the project root."""
from PIL import Image
import json, math, os

root = "src/main/resources/assets/jaymospowers"
data = "src/main/resources/data/jaymospowers"


def solid(name, base, edge, noise_seed=1):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = ((x * 7 + y * 13 + noise_seed * 5) % 9) / 9.0
            c = tuple(int(b + (e - b) * (0.25 * n + (0.75 if x in (0, 15) or y in (0, 15) else 0.0))) for b, e in zip(base, edge))
            img.putpixel((x, y), (*c, 255))
    img.save(f"{root}/textures/block/{name}.png")


solid("durandal_blade", (214, 218, 222), (150, 156, 164), 1)
solid("durandal_gold", (232, 190, 64), (160, 112, 24), 2)
solid("durandal_gem", (196, 30, 36), (110, 10, 16), 3)
solid("durandal_grip", (176, 92, 34), (110, 54, 18), 4)

# the block: pedestal (stone bricks) and the sword, tilted
tilt = {"origin": [8, 9, 8], "axis": "z", "angle": 22.5}


def box(f, t, tex, rot=None, uv_full=True):
    faces = {d: {"uv": [0, 0, 16, 16], "texture": tex} for d in ("north", "south", "east", "west", "up", "down")}
    e = {"from": f, "to": t, "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


pedestal = [
    box([1, 0, 1], [15, 4, 15], "#brick"),
    box([3, 4, 3], [13, 9, 13], "#cracked"),
]
sword = [
    box([7, 6, 7.4], [9, 22, 8.6], "#blade", tilt),
    box([3.5, 22, 7.2], [12.5, 23.5, 8.8], "#gold", tilt),
    box([3.5, 23.5, 7.2], [5, 26, 8.8], "#gold", tilt),
    box([11, 19.5, 7.2], [12.5, 22, 8.8], "#gold", tilt),
    box([7, 21.8, 6.9], [9, 23.7, 9.1], "#gem", tilt),
    box([7.25, 23.5, 7.25], [8.75, 31, 8.75], "#grip", tilt),
    box([6.75, 31, 6.75], [9.25, 32, 9.25], "#gold", tilt),
]
textures = {"brick": "minecraft:block/stone_bricks", "cracked": "minecraft:block/cracked_stone_bricks",
            "blade": "jaymospowers:block/durandal_blade", "gold": "jaymospowers:block/durandal_gold",
            "gem": "jaymospowers:block/durandal_gem", "grip": "jaymospowers:block/durandal_grip",
            "particle": "minecraft:block/stone_bricks"}
json.dump({"parent": "minecraft:block/block", "textures": textures, "elements": pedestal + sword},
          open(f"{root}/models/block/sword_in_stone.json", "w"), indent=2)
json.dump({"parent": "minecraft:block/block", "textures": textures, "elements": pedestal},
          open(f"{root}/models/block/sword_in_stone_empty.json", "w"), indent=2)
json.dump({"variants": {"sword=true": {"model": "jaymospowers:block/sword_in_stone"},
                        "sword=false": {"model": "jaymospowers:block/sword_in_stone_empty"}}},
          open(f"{root}/blockstates/sword_in_stone.json", "w"), indent=2)
json.dump({"model": {"type": "minecraft:model", "model": "jaymospowers:block/sword_in_stone"}},
          open(f"{root}/items/sword_in_stone.json", "w"), indent=2)

# Durandal's sprite: a long straight sword, diagonal, iron blade, gold guard with a red stone, long orange grip
SIZE = 32
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
D = (1 / math.sqrt(2), -1 / math.sqrt(2))
P = (1 / math.sqrt(2), 1 / math.sqrt(2))
O = (10.5, 21.5)
COL = {"B": (226, 230, 234), "b": (186, 192, 198), "E": (246, 248, 250), "G": (232, 190, 64), "g": (160, 112, 24),
       "R": (200, 34, 40), "H": (186, 98, 36), "h": (120, 60, 20)}


def cls(u, v):
    if 1.0 <= u <= 21.0:
        half = 1.6 * min(1.0, (21.0 - u) / 3.0) + 0.4
        if abs(v) <= half:
            return "E" if v > half - 0.8 else "B" if v < -half + 0.9 else "b"
    if -1.0 <= u < 1.0 and abs(v) <= 5.5:
        if abs(v) <= 1.0:
            return "R"
        return "g" if u < 0 else "G"
    if -8.5 <= u < -1.0 and abs(v) <= 0.95:
        return "h" if (int((u + 8.5) * 1.2)) % 2 == 0 else "H"
    if -10.0 <= u < -8.5 and abs(v) <= 1.6:
        return "G"
    return None


for y in range(SIZE):
    for x in range(SIZE):
        dx, dy = x + 0.5 - O[0], y + 0.5 - O[1]
        c = cls(dx * D[0] + dy * D[1], dx * P[0] + dy * P[1])
        if c:
            img.putpixel((x, y), (*COL[c], 255))
out = img.copy()
for y in range(SIZE):
    for x in range(SIZE):
        if img.getpixel((x, y))[3]:
            continue
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < SIZE and 0 <= ny < SIZE and img.getpixel((nx, ny))[3]:
                r, g, b, _ = img.getpixel((nx, ny))
                out.putpixel((x, y), (int(r * 0.3), int(g * 0.3), int(b * 0.34), 255))
                break
out.save(f"{root}/textures/item/durandal.png")
json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/durandal"}},
          open(f"{root}/models/item/durandal.json", "w"), indent=2)
json.dump({"model": {"type": "minecraft:model", "model": "jaymospowers:item/durandal"}},
          open(f"{root}/items/durandal.json", "w"), indent=2)

# worldgen
os.makedirs(f"{data}/worldgen/feature", exist_ok=True)
os.makedirs(f"{data}/worldgen/placed_feature", exist_ok=True)
json.dump({"type": "jaymospowers:sword_stone"}, open(f"{data}/worldgen/feature/sword_stone.json", "w"), indent=2)
json.dump({"feature": "jaymospowers:sword_stone", "placement": [
    {"type": "minecraft:rarity_filter", "chance": 700},
    {"type": "minecraft:in_square"},
    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
    {"type": "minecraft:biome"}]}, open(f"{data}/worldgen/placed_feature/sword_stone.json", "w"), indent=2)
