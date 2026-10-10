"""Generates the swords in stones (see SwordStone.Variant): for each, the block textures (blade, guard, gem, grip), the
pedestal block model with the sword driven into it (tilted, a guard with a gem, a long grip), the empty pedestal, the
item sprite (32x32, drawn along the diagonal), models, item definitions, the blockstate and the worldgen.
Durandal (the Overworld), Laevateinn (the Nether), Caladbolg (the End), Gram (the Deep Dark), Hrunting (the frozen
peaks). Run from the project root."""
from PIL import Image
import json, math, os

root = "src/main/resources/assets/jaymospowers"
data = "src/main/resources/data/jaymospowers"
os.makedirs(f"{data}/worldgen/feature", exist_ok=True)
os.makedirs(f"{data}/worldgen/placed_feature", exist_ok=True)

# each: block id, sword id, feature id, colours (blade, blade edge, guard, gem, grip), pedestal textures,
# the sword's shape (blade width, guard span, tilt), the sprite's blade profile, and its placement
VARIANTS = [
    dict(block="sword_in_stone", sword="durandal", feature="sword_stone",
         blade=(214, 218, 222), edge=(150, 156, 164), guard=(232, 190, 64), gem=(196, 30, 36), grip=(176, 92, 34),
         brick="minecraft:block/stone_bricks", cracked="minecraft:block/cracked_stone_bricks",
         width=2.0, span=9.0, tilt=22.5, profile="straight",
         placement=[{"type": "minecraft:rarity_filter", "chance": 700}, {"type": "minecraft:in_square"},
                    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]),
    dict(block="nether_sword_stone", sword="laevateinn", feature="sword_stone_nether",
         blade=(70, 30, 30), edge=(255, 120, 40), guard=(40, 36, 44), gem=(255, 170, 40), grip=(120, 20, 20),
         brick="minecraft:block/polished_blackstone_bricks", cracked="minecraft:block/gilded_blackstone",
         width=2.4, span=7.0, tilt=-18.0, profile="flame",
         placement=[{"type": "minecraft:rarity_filter", "chance": 260}, {"type": "minecraft:in_square"},
                    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 32},
                                                                  "max_inclusive": {"absolute": 100}}},
                    {"type": "minecraft:biome"}]),
    dict(block="end_sword_stone", sword="caladbolg", feature="sword_stone_end",
         blade=(220, 200, 240), edge=(160, 110, 200), guard=(230, 225, 170), gem=(90, 230, 190), grip=(110, 70, 140),
         brick="minecraft:block/end_stone_bricks", cracked="minecraft:block/purpur_block",
         width=1.6, span=10.0, tilt=12.0, profile="needle",
         placement=[{"type": "minecraft:rarity_filter", "chance": 220}, {"type": "minecraft:in_square"},
                    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]),
    dict(block="deep_sword_stone", sword="gram", feature="sword_stone_deep",
         blade=(40, 60, 70), edge=(60, 230, 220), guard=(30, 34, 40), gem=(40, 220, 210), grip=(20, 50, 60),
         brick="minecraft:block/deepslate_tiles", cracked="minecraft:block/cracked_deepslate_tiles",
         width=2.6, span=8.0, tilt=-25.0, profile="broad",
         placement=[{"type": "minecraft:rarity_filter", "chance": 40}, {"type": "minecraft:in_square"},
                    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -52},
                                                                  "max_inclusive": {"absolute": -10}}},
                    {"type": "minecraft:biome"}]),
    dict(block="frost_sword_stone", sword="hrunting", feature="sword_stone_frost",
         blade=(200, 235, 255), edge=(120, 180, 230), guard=(170, 190, 210), gem=(80, 140, 255), grip=(60, 80, 120),
         brick="minecraft:block/packed_ice", cracked="minecraft:block/blue_ice",
         width=1.8, span=8.5, tilt=30.0, profile="jagged",
         placement=[{"type": "minecraft:rarity_filter", "chance": 350}, {"type": "minecraft:in_square"},
                    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]),
]


def solid(name, base, edge, seed):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            n = ((x * 7 + y * 13 + seed * 5) % 9) / 9.0
            c = tuple(int(b + (e - b) * (0.25 * n + (0.75 if x in (0, 15) or y in (0, 15) else 0.0))) for b, e in zip(base, edge))
            img.putpixel((x, y), (*c, 255))
    img.save(f"{root}/textures/block/{name}.png")


def darker(c, k=0.6):
    return tuple(int(v * k) for v in c)


def box(f, t, tex, rot=None):
    e = {"from": f, "to": t, "faces": {d: {"uv": [0, 0, 16, 16], "texture": tex} for d in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


def cls_for(profile):
    def cls(u, v):
        if 1.0 <= u <= 21.0:
            taper = min(1.0, (21.0 - u) / 3.0)
            if profile == "flame":
                half = (1.5 + 0.5 * math.sin(u * 0.9)) * taper + 0.4
            elif profile == "needle":
                half = 1.1 * min(1.0, (21.0 - u) / 8.0) + 0.3
            elif profile == "broad":
                half = 2.2 * taper + 0.4
            elif profile == "jagged":
                half = (1.5 + (0.6 if int(u) % 3 == 0 else 0.0)) * taper + 0.3
            else:
                half = 1.6 * taper + 0.4
            if abs(v) <= half:
                return "E" if v > half - 0.8 else "B" if v < -half + 0.9 else "b"
        if -1.0 <= u < 1.0 and abs(v) <= 5.5:
            return "R" if abs(v) <= 1.0 else ("g" if u < 0 else "G")
        if -8.5 <= u < -1.0 and abs(v) <= 0.95:
            return "h" if int((u + 8.5) * 1.2) % 2 == 0 else "H"
        if -10.0 <= u < -8.5 and abs(v) <= 1.6:
            return "G"
        return None
    return cls


D = (1 / math.sqrt(2), -1 / math.sqrt(2))
P = (1 / math.sqrt(2), 1 / math.sqrt(2))
for i, var in enumerate(VARIANTS):
    sw = var["sword"]
    prefix = "durandal" if sw == "durandal" else sw
    solid(f"{prefix}_blade", var["blade"], var["edge"], 1 + i * 4)
    solid(f"{prefix}_gold", var["guard"], darker(var["guard"]), 2 + i * 4)
    solid(f"{prefix}_gem", var["gem"], darker(var["gem"], 0.5), 3 + i * 4)
    solid(f"{prefix}_grip", var["grip"], darker(var["grip"]), 4 + i * 4)

    tilt = {"origin": [8, 9, 8], "axis": "z", "angle": var["tilt"]}
    w, s = var["width"], var["span"]
    lo, hi = 8 - w / 2, 8 + w / 2
    pedestal = [box([1, 0, 1], [15, 4, 15], "#brick"), box([3, 4, 3], [13, 9, 13], "#cracked")]
    sword = [
        box([lo, 6, 7.4], [hi, 22, 8.6], "#blade", tilt),
        box([8 - s / 2, 22, 7.2], [8 + s / 2, 23.5, 8.8], "#gold", tilt),
        box([8 - s / 2, 23.5, 7.2], [8 - s / 2 + 1.5, 26, 8.8], "#gold", tilt),
        box([8 + s / 2 - 1.5, 19.5, 7.2], [8 + s / 2, 22, 8.8], "#gold", tilt),
        box([7, 21.8, 6.9], [9, 23.7, 9.1], "#gem", tilt),
        box([7.25, 23.5, 7.25], [8.75, 31, 8.75], "#grip", tilt),
        box([6.75, 31, 6.75], [9.25, 32, 9.25], "#gold", tilt),
    ]
    textures = {"brick": var["brick"], "cracked": var["cracked"],
                "blade": f"jaymospowers:block/{prefix}_blade", "gold": f"jaymospowers:block/{prefix}_gold",
                "gem": f"jaymospowers:block/{prefix}_gem", "grip": f"jaymospowers:block/{prefix}_grip", "particle": var["brick"]}
    b = var["block"]
    json.dump({"parent": "minecraft:block/block", "textures": textures, "elements": pedestal + sword},
              open(f"{root}/models/block/{b}.json", "w"), indent=2)
    json.dump({"parent": "minecraft:block/block", "textures": textures, "elements": pedestal},
              open(f"{root}/models/block/{b}_empty.json", "w"), indent=2)
    json.dump({"variants": {"sword=true": {"model": f"jaymospowers:block/{b}"}, "sword=false": {"model": f"jaymospowers:block/{b}_empty"}}},
              open(f"{root}/blockstates/{b}.json", "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"jaymospowers:block/{b}"}}, open(f"{root}/items/{b}.json", "w"), indent=2)

    # the sprite
    blade, edge = var["blade"], var["edge"]
    col = {"B": tuple(min(255, int(c * 1.1)) for c in blade), "b": blade, "E": tuple(min(255, int(c * 1.15 + 20)) for c in edge),
           "G": var["guard"], "g": darker(var["guard"], 0.7), "R": var["gem"], "H": var["grip"], "h": darker(var["grip"], 0.65)}
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    cls = cls_for(var["profile"])
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - 10.5, y + 0.5 - 21.5
            c = cls(dx * D[0] + dy * D[1], dx * P[0] + dy * P[1])
            if c:
                img.putpixel((x, y), (*col[c], 255))
    out = img.copy()
    for y in range(32):
        for x in range(32):
            if img.getpixel((x, y))[3]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < 32 and 0 <= ny < 32 and img.getpixel((nx, ny))[3]:
                    r, g, bb, _ = img.getpixel((nx, ny))
                    out.putpixel((x, y), (int(r * 0.3), int(g * 0.3), int(bb * 0.34), 255))
                    break
    out.save(f"{root}/textures/item/{sw}.png")
    json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"jaymospowers:item/{sw}"}},
              open(f"{root}/models/item/{sw}.json", "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{sw}"}}, open(f"{root}/items/{sw}.json", "w"), indent=2)

    # worldgen
    f = var["feature"]
    json.dump({"type": f"jaymospowers:{f}"}, open(f"{data}/worldgen/feature/{f}.json", "w"), indent=2)
    json.dump({"feature": f"jaymospowers:{f}", "placement": var["placement"]}, open(f"{data}/worldgen/placed_feature/{f}.json", "w"), indent=2)
print(len(VARIANTS), "swords in stones")
