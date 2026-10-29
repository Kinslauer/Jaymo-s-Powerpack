"""(Andy's batch 4) The Tidebreaker relic (power/arsenal/RelicWeapons, items/tidebreaker.json -> models/item/tidebreaker) drawn
as the Blockbench Workshop model "Lighting Spaer" (CC BY 4.0; the file names no author), tools/bbmodels/lighting-spaer.bbmodel.

The file: 20 cubes already standing in the model box (x 1 to 15, y -16 to 32, z 6.5 to 9.5), java_block format with its own
display transforms (held a bit off the hand, GUI and frame), one 64x64 texture.
  * 16 of the cubes are turned: 45, 22.5 and -22.5 about z are legal as drawn; the rest turn by 5 degrees about two axes (the
    band's tilted slats) or are -90 + 5 combinations, and bake.py gives each the nearest legal single-axis turn (5 degrees
    becomes 0): the largest tilt error is printed.
  * its display transforms are replaced by display(): the file's held the spear off the fist, and ours puts the grip
    (the shaft's middle, y 0 in the file) in the fist like our other 3D weapons.
  * texture used as drawn (textures/item/tidebreaker_3d.png; the old sprite tidebreaker.png stays).

Run from the repo root: python3 -I tools/blockbench/tidebreaker.py
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import build_atlas, convert_cubes, load, texture_images, write_json
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "lighting-spaer.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"

bb = load(SRC)
atlas, _, atlas_uv = build_atlas(texture_images(bb))
elements, worst = convert_cubes(bb, atlas_uv)
print(f"{len(elements)} elements, worst tilt error {worst:.1f} degrees")
atlas.save(f"{ASSETS}/textures/item/tidebreaker_3d.png")
model = {
    "credit": "Lighting Spaer (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/tidebreaker_3d", "particle": "jaymospowers:item/tidebreaker_3d"},
    "elements": elements,
    "display": display(tp=0.55, fp=0.45, gui=0.36, center_y=8, grip_y=-6),
}
write_json(f"{ASSETS}/models/item/tidebreaker.json", model)
