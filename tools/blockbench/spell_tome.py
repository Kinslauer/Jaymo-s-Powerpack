"""(Andy's batch 4) The spell tome (power/tome, jaymospowers:spell_tome) drawn as the "Weapon - Starter Tome" model from
Andy's Blockbench Workshop batch (CC BY 4.0; the file names no author). Its five cubes and its own display transforms
(held, GUI, ground, frame) are kept as drawn; the 64x64 texture is used as drawn too, except for the clasp gem.

The gem (the 45-degree cube on the cover) is painted bright green in the original. Here its six faces are turned to grey
(brightness kept) and given tintindex 0, and the item definition tints them with the element the tome is set to (the
jaymospowers:tome_element component): orange fire, pale blue frost, yellow lightning, violet arcane. One model, four tints.

The model's UVs are in pixels of its 64x64 texture; Java models want them in sixteenths of the texture, hence the scale.
The cover cube's turn is one axis at 45 degrees, which Java models allow, so nothing is baked.

Run from the repo root: python3 -I tools/blockbench/spell_tome.py
"""
import base64, io, json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "bbmodels", "weapon-starter-tome.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
GEM = 4  # the index of the gem's cube among the elements
# the element tints (ARGB as the signed ints item definitions use); keep in step with TomeElement
FP_SCALE, FP_AT = 0.6, [-1.0, 6.5, -1.0]
TINTS = {"fire": 0xFFFF7A1A, "frost": 0xFF7FD8FF, "lightning": 0xFFFFF06A, "arcane": 0xFFB06BFF}

bb = json.load(open(SRC))
img = Image.open(io.BytesIO(base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1]))).convert("RGBA")
W = bb["resolution"]["width"]
S = 16 / W

# the gem's pixels to grey
px = img.load()
for face in bb["elements"][GEM]["faces"].values():
    u0, v0, u1, v1 = face["uv"]
    for x in range(int(min(u0, u1)), int(max(u0, u1) + 0.999)):
        for y in range(int(min(v0, v1)), int(max(v0, v1) + 0.999)):
            r, g, b, a = px[x, y]
            if a:
                grey = min(255, int((0.299 * r + 0.587 * g + 0.114 * b) * 1.3))
                px[x, y] = (grey, grey, grey, a)
img.save(f"{ASSETS}/textures/item/spell_tome.png")

elements = []
for i, el in enumerate(bb["elements"]):
    faces = {}
    for k, face in el["faces"].items():
        u0, v0, u1, v1 = face["uv"]
        if u0 == u1 or v0 == v1:
            continue
        out = {"uv": [round(v * S, 4) for v in face["uv"]], "texture": "#0"}
        if i == GEM:
            out["tintindex"] = 0
        faces[k] = out
    cube = {"from": el["from"], "to": el["to"], "faces": faces}
    rot = el.get("rotation", [0, 0, 0])
    for axis, angle in zip("xyz", rot):
        if angle:
            cube["rotation"] = {"angle": angle, "axis": axis, "origin": el["origin"]}
    elements.append(cube)

# the file's display transforms, but the first-person hand: drawn at full size the book filled the screen's corner and was
# cut off, so it is scaled down to about a vanilla book's size
display = bb["display"]
for hand in ("firstperson_righthand", "firstperson_lefthand"):
    display[hand]["scale"] = [FP_SCALE] * 3
    display[hand]["translation"] = FP_AT

model = {
    "credit": "Weapon - Starter Tome (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/spell_tome", "particle": "jaymospowers:item/spell_tome"},
    "elements": elements,
    "display": display,
}
with open(f"{ASSETS}/models/item/spell_tome.json", "w", newline="\n") as fh:
    json.dump(model, fh, indent="\t")
    fh.write("\n")


def signed(argb):
    return argb - (1 << 32) if argb >= 1 << 31 else argb


definition = {"model": {
    "type": "minecraft:select",
    "property": "minecraft:component",
    "component": "jaymospowers:tome_element",
    "cases": [{"when": name, "model": {"type": "minecraft:model", "model": "jaymospowers:item/spell_tome",
                                       "tints": [{"type": "minecraft:constant", "value": signed(color)}]}}
              for name, color in TINTS.items()],
    "fallback": {"type": "minecraft:model", "model": "jaymospowers:item/spell_tome",
                 "tints": [{"type": "minecraft:constant", "value": signed(TINTS["fire"])}]},
}}
with open(f"{ASSETS}/items/spell_tome.json", "w", newline="\n") as fh:
    json.dump(definition, fh, indent="\t")
    fh.write("\n")
print(len(elements), "elements")
