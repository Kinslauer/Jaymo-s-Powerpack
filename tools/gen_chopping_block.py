"""Writes the chopping blocks' assets and data (ground/Chopping): one per vanilla wood, drawn from that wood's own log
textures (no new art), a 14x10x14 round of log with the cut end up. Run from the repo root."""
import json
import os

R = "src/main/resources"
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "crimson", "warped"]


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def side(u0, u1):
    return {"uv": [u0, 6, u1, 16], "texture": "#side"}


# the shared shape: the bark round the sides, the log's end grain on top and bottom
write(f"{R}/assets/jaymospowers/models/block/chopping_block.json", {
    "parent": "minecraft:block/block",
    "textures": {"particle": "#side"},
    "elements": [{
        "from": [1, 0, 1], "to": [15, 10, 15],
        "faces": {
            "north": side(1, 15), "south": side(1, 15), "west": side(1, 15), "east": side(1, 15),
            "up": {"uv": [1, 1, 15, 15], "texture": "#end"},
            "down": {"uv": [1, 1, 15, 15], "texture": "#end", "cullface": "down"},
        },
    }],
})

tags = []
for wood in WOODS:
    log = wood + ("_stem" if wood in ("crimson", "warped") else "_log")
    name = f"{wood}_chopping_block"
    write(f"{R}/assets/jaymospowers/models/block/{name}.json", {
        "parent": "jaymospowers:block/chopping_block",
        "textures": {"side": f"minecraft:block/{log}", "end": f"minecraft:block/{log}_top"},
    })
    write(f"{R}/assets/jaymospowers/blockstates/{name}.json", {"variants": {"": {"model": f"jaymospowers:block/{name}"}}})
    write(f"{R}/assets/jaymospowers/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:block/{name}"}})
    write(f"{R}/data/jaymospowers/loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"jaymospowers:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })
    tags.append(f"jaymospowers:{name}")

axe = f"{R}/data/minecraft/tags/block/mineable/axe.json"
with open(axe) as f:
    data = json.load(f)
data["values"] += [t for t in tags if t not in data["values"]]
write(axe, data)
