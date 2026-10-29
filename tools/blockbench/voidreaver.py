"""(Andy's batch 4) The Voidreaver relic (power/arsenal/RelicWeapons, items/voidreaver.json -> models/item/voidreaver) drawn
as a Blockbench Workshop sword model (CC BY 4.0), the file tools/bbmodels/trollhunters-sword-of-daylight.bbmodel. Only the
model's shape is used: the weapon keeps our name, Voidreaver, everywhere (the model's credit line says only that it is from
the Blockbench Workshop).

The file: 36 cubes, mostly flat planes (the blade and the glowing wings), one 64x64 texture whose UVs are in a 32x32 space.
  * the cubes turn about all three axes (the wings by -90/57.5, the blade fins by -45 and so on): bake.py gives each the
    nearest legal single-axis turn, quarter turns swapping its sides; the largest tilt error is printed.
  * centred on x and z 8, lifted 2 so its length (-5 to 30) sits inside the model box; the texture is used as drawn
    (textures/item/voidreaver_3d.png, copied; the old sprite voidreaver.png stays for anything that still uses it).
  * display(): the grip (its handle, y -5 to 2 in the file) in the fist like our other 3D weapons.

Run from the repo root: python3 -I tools/blockbench/voidreaver.py
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import build_atlas, convert_cubes, load, texture_images, write_json
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "trollhunters-sword-of-daylight.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
LIFT = 2

bb = load(SRC)
atlas, _, atlas_uv = build_atlas(texture_images(bb))
elements, worst = convert_cubes(bb, atlas_uv, shift=(8, LIFT, 8))
print(f"{len(elements)} elements, worst tilt error {worst:.1f} degrees")
atlas.save(f"{ASSETS}/textures/item/voidreaver_3d.png")
model = {
    "credit": "Sword model from the Blockbench Workshop (CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/voidreaver_3d", "particle": "jaymospowers:item/voidreaver_3d"},
    "elements": elements,
    "display": display(tp=0.7, fp=0.55, gui=0.5, center_y=10.5 + LIFT, grip_y=-1.5 + LIFT),
}
write_json(f"{ASSETS}/models/item/voidreaver.json", model)
