"""(0.17.201) The Echo Cleaver relic (power/arsenal/RelicWeapons, items/echo_cleaver.json -> models/item/echo_cleaver)
drawn as the "Mandrake" sword from the Blockbench Workshop (CC BY 4.0; tools/bbmodels/mandrake-sword.bbmodel): a blade
with a mandrake root wound round its hilt, whose scream suits the cleaver's sonic boom. The weapon keeps its name.

The file: 20 cubes on one 64x64 texture, upright along +y (pommel at -0.5, guard at 9-10, tip at 27), centred on x and
z 0, its turns about all three axes kept exactly (freeform.py). Moved to x and z 8; the old sprite echo_cleaver.png stays
for the vanilla-style pack.

Run from the repo root: python3 -I tools/blockbench/mandrake.py
"""
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import load, write_json
from display import display
from freeform import convert

SRC = os.path.join(HERE, "..", "bbmodels", "mandrake-sword.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"

elements, atlas = convert(load(SRC), centre=(8, 0, 8))
atlas.save(f"{ASSETS}/textures/item/echo_cleaver_3d.png")
model = {
    "credit": "Mandrake (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/echo_cleaver_3d", "particle": "jaymospowers:item/echo_cleaver_3d"},
    "elements": elements,
    "display": display(tp=0.75, fp=0.6, gui=0.5, center_y=13.25, grip_y=5),
}
write_json(f"{ASSETS}/models/item/echo_cleaver.json", model)
print(len(elements), "elements")
