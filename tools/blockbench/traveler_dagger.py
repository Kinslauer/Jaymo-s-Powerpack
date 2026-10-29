"""(0.17.164) The Armory's iron dagger drawn as polyjo's Traveler Dagger (Blockbench Workshop, CC BY 4.0,
https://blockbenchworkshop.com/model/polyjo/traveler-dagger-polyjo), the silver skeleton's (Dead Traveler's) own.
Its cubes are moved onto the item grid (centred, its pommel one pixel up), the two edge faces of its flat blade are
dropped, and our display() puts its grip in the fist. Its 16x16 texture is used as drawn.

Run from the repo root: python3 -I tools/blockbench/traveler_dagger.py
"""
import base64, io, json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "traveler-dagger-polyjo.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
DX, DY, DZ = 8, 1, 8
bb = json.load(open(SRC))
src = bb["textures"][0]["source"].split(",", 1)[1]
Image.open(io.BytesIO(base64.b64decode(src))).convert("RGBA").save(f"{ASSETS}/textures/item/armory/iron_dagger.png")

elements = []
for el in bb["elements"]:
    f, t = el["from"], el["to"]
    faces = {}
    for k, face in el["faces"].items():
        u0, v0, u1, v1 = face["uv"]
        if u0 == u1 or v0 == v1:
            continue
        faces[k] = {"uv": face["uv"], "texture": "#0"}
    elements.append({"from": [f[0] + DX, f[1] + DY, f[2] + DZ], "to": [t[0] + DX, t[1] + DY, t[2] + DZ], "faces": faces})

model = {
    "credit": "Traveler Dagger by polyjo (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/armory/iron_dagger", "particle": "jaymospowers:item/armory/iron_dagger"},
    "elements": elements,
    # the grip runs from pixel 1 to 6: its middle at 3.5; the dagger's length centred on 8
    "display": display(tp=0.8, fp=0.7, gui=0.85, center_y=8, grip_y=3.5),
}
with open(f"{ASSETS}/models/item/armory/iron_dagger.json", "w", newline="\n") as fh:
    json.dump(model, fh, indent="\t")
    fh.write("\n")
print(len(elements), "elements")
