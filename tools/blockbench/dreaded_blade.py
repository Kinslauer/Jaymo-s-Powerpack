"""(0.17.166) The Soul Saber relic (power/arsenal/RelicWeapons), now named the Dreaded Blade: capellastudio's "Weapon - Dreaded Blade (Animated)"
(Blockbench Workshop, CC BY 4.0, https://blockbenchworkshop.com/model/capellastudio/weapon-dreaded-blade-animated)
as the item's model. Its two animated 32x320 textures (ten frames, two ticks each) are written with their .mcmeta; the
rune plane on the second is drawn at full light (light_emission 15). Cubes, UVs and 45-degree turns as drawn; our
display() puts its grip in the fist like the Armory's 3D blades.

Run from the repo root: python3 -I tools/blockbench/dreaded_blade.py
"""
import base64, io, json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "weapon-dreaded-blade-animated.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
NAMES = ["dreaded_blade", "dreaded_blade_fx"]
bb = json.load(open(SRC))
for name, tex in zip(NAMES, bb["textures"]):
    png = base64.b64decode(tex["source"].split(",", 1)[1])
    Image.open(io.BytesIO(png)).convert("RGBA").save(f"{ASSETS}/textures/item/{name}.png")
    with open(f"{ASSETS}/textures/item/{name}.png.mcmeta", "w", newline="\n") as fh:
        json.dump({"animation": {"frametime": tex.get("frame_time", 2)}}, fh, indent="\t")
        fh.write("\n")

S = 16 / bb["resolution"]["width"]  # the model's UVs are in 32ths of the texture's width
elements = []
for el in bb["elements"]:
    faces = {}
    for k, face in el["faces"].items():
        u0, v0, u1, v1 = face["uv"]
        if face.get("texture") is None or u0 == u1 or v0 == v1:
            continue
        faces[k] = {"uv": [round(v * S, 4) for v in face["uv"]], "texture": f"#{face['texture']}"}
    if not faces:
        continue
    out = {"from": el["from"], "to": el["to"], "faces": faces}
    rot = el.get("rotation", [0, 0, 0])
    for axis, angle in zip("xyz", rot):
        if angle:
            out["rotation"] = {"angle": angle, "axis": axis, "origin": el["origin"]}
    if any(f["texture"] == "#1" for f in faces.values()):
        out["light_emission"] = 15
    elements.append(out)

model = {
    "credit": "Weapon - Dreaded Blade (Animated) by capellastudio (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/dreaded_blade", "1": "jaymospowers:item/dreaded_blade_fx",
                 "particle": "jaymospowers:item/dreaded_blade"},
    "elements": elements,
    # the grip (2.75 to 7.75) in the fist, the blade's 24 pixels centred on 12.7
    "display": display(tp=0.85, fp=0.68, gui=0.6, center_y=12.7, grip_y=5.25),
}
with open(f"{ASSETS}/models/item/soul_saber.json", "w", newline="\n") as fh:
    json.dump(model, fh, indent="\t")
    fh.write("\n")
with open(f"{ASSETS}/items/soul_saber.json", "w", newline="\n") as fh:
    json.dump({"model": {"type": "minecraft:model", "model": "jaymospowers:item/soul_saber"}}, fh, indent="\t")
    fh.write("\n")
print(len(elements), "elements")
