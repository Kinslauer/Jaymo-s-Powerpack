"""(0.17.169) The Soul Saber relic (power/arsenal/RelicWeapons), now named the Dreaded Blade: capellastudio's "Weapon - Dreaded Blade (Animated)"
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
from bake import bake_cube, flatten, rot_y, apply, uv_rotation

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
# (0.18.58) Andy: "Dreaded blade needs to be rotated in hand, it sits flat instead of vertical". The file draws the
# blade's wide face to x (as Sora's Dagger's did); a sword sprite, and display(), want it to z. Turn the model a quarter
# about the vertical through the grip (x 8, z 8), keeping its 45-degree turns exact.
SPIN = rot_y(90)
_p = apply(SPIN, (8, 0, 8))
SHIFT = (8 - _p[0], 0, 8 - _p[2])
elements = []
for el, chain in flatten(bb):
    faces = {}
    for k, face in el["faces"].items():
        u0, v0, u1, v1 = face["uv"]
        if face.get("texture") is None or u0 == u1 or v0 == v1:
            continue
        faces[k] = {"uv": [round(v * S, 4) for v in face["uv"]], "texture": f"#{face['texture']}"}
    if not faces:
        continue
    out, mapping, _ = bake_cube(el["from"], el["to"], chain, shift=SHIFT, spin=SPIN, exact=True)
    turned = {}
    for name, (new_name, k) in mapping.items():
        if name in faces:
            turned[new_name] = {**faces[name], **({"rotation": uv_rotation(k)} if k else {})}
    out["faces"] = turned
    if any(f["texture"] == "#1" for f in turned.values()):
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
