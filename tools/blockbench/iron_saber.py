"""(0.18.78) The Iron Saber (armory/Armory, items/iron_saber.json -> models/item/armory/iron_saber) drawn as Andy's
"Saber reworked" Blockbench model (tools/bbmodels/saber-reworked.bbmodel; Andy: "can you replace the iron saber with the
new model"), in place of the flat sprite.

The file: 20 axis-aligned cubes (blade, guard with its knuckle bow, wrapped grip, pommel) in five textures, four 16x16
and one 32x32, all mapped in the project's 64x64 UV space, merged into one atlas (textures/item/armory/iron_saber_3d.png; the old sprite stays for anything that
still uses it). Turned a quarter so the blade's flat faces the way a sword sprite's does, its middle
on x and z 8; display() puts the grip (y 2 to 8 in the file) in the fist like the Armory's other 3D blades.

Run from the repo root: python3 -I tools/blockbench/iron_saber.py
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import apply, build_atlas, convert_cubes, load, rot_y, texture_images, write_json
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "saber-reworked.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
# the file draws the blade's wide face to x (its width runs along z); a sword sprite, and display(), want it to z, so
# the model turns a quarter about the vertical (as the Dreaded Blade does), then the blade's middle (x 9, z 6.25 in the
# file) is moved onto x and z 8
SPIN = rot_y(90)
_p = apply(SPIN, (9, 0, 6.25))
SHIFT = (8 - _p[0], 0, 8 - _p[2])

bb = load(SRC)
# every face's UVs count in the project's 64x64 space, whatever each texture's own size (the files say 16 or 32)
UV = bb["resolution"]["width"]
atlas, _, atlas_uv = build_atlas([(img, UV, UV, tid) for img, _, _, tid in texture_images(bb)])
elements, worst = convert_cubes(bb, atlas_uv, shift=SHIFT, spin=SPIN, atlas=atlas)
print(f"{len(elements)} elements, worst tilt error {worst:.1f} degrees")
atlas.save(f"{ASSETS}/textures/item/armory/iron_saber_3d.png")
model = {
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/armory/iron_saber_3d", "particle": "jaymospowers:item/armory/iron_saber_3d"},
    "elements": elements,
    # the grip (2 to 8) in the fist, the whole saber (0 to 30) centred on 15
    "display": display(tp=0.85, fp=0.68, gui=0.5, center_y=15, grip_y=5),
}
write_json(f"{ASSETS}/models/item/armory/iron_saber.json", model)
