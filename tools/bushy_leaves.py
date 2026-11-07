"""Generates the "living world" built-in pack's bushy leaves: every vanilla leaf block also draws two crossed planes of
its own texture that reach past the block, so canopies look fuller with a soft, ragged edge (our own take on bushy-leaves
packs; only vanilla's own textures are referenced, nothing is copied). Run from the root: python -I tools/bushy_leaves.py"""
import json
import os

PACK = "src/main/resources/resourcepacks/living_world/"
# leaf block -> whether its model is biome-tinted (vanilla's block/leaves parent) or plain (cube_all)
LEAVES = {
    "oak_leaves": True, "spruce_leaves": True, "birch_leaves": True, "jungle_leaves": True, "acacia_leaves": True,
    "dark_oak_leaves": True, "mangrove_leaves": True, "cherry_leaves": True, "pale_oak_leaves": True,
    "azalea_leaves": False, "flowering_azalea_leaves": False,
    "red_poplar_leaves": False, "orange_poplar_leaves": False, "yellow_poplar_leaves": False,
}
# how far the planes reach past the block on each side, in pixels
REACH = 3


def plane(axis_from, axis_to, tinted):
    face = {"uv": [0, 0, 16, 16], "texture": "#all"}
    if tinted:
        face["tintindex"] = 0
    faces = ({"north": face, "south": face} if axis_from[2] == axis_to[2] else {"west": face, "east": face})
    return {"from": axis_from, "to": axis_to, "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45},
            "shade_direction_override": "up", "faces": faces}


def template(tinted):
    lo, hi = -REACH, 16 + REACH
    # turned 45 degrees, a plane must be this long for its ends to stick out REACH past the block's sides
    half = round((8 + REACH) * 2 ** 0.5, 2)
    a, b = round(8 - half, 2), round(8 + half, 2)
    return {
        "ambientocclusion": False,
        "textures": {"particle": "#all"},
        "elements": [plane([a, lo, 8], [b, hi, 8], tinted), plane([8, lo, a], [8, hi, b], tinted)],
    }


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    models = PACK + "assets/jaymospowers/models/block/bushy/"
    write(models + "tinted_fringe.json", template(True))
    write(models + "fringe.json", template(False))
    for name, tinted in LEAVES.items():
        write(models + name + ".json", {"parent": "jaymospowers:block/bushy/" + ("tinted_fringe" if tinted else "fringe"),
                                        "textures": {"all": "minecraft:block/" + name}})
        write(PACK + "assets/minecraft/blockstates/" + name + ".json", {"multipart": [
            {"apply": {"model": "minecraft:block/" + name}},
            {"apply": {"model": "jaymospowers:block/bushy/" + name}},
        ]})
    write(PACK + "pack.mcmeta", {"pack": {"description": "Jaymo's Powers: bushy leaves, grass and snow edges", "min_format": 97, "max_format": 97}})


if __name__ == "__main__":
    main()
