"""(0.17.201) Arsenal's Void Flintlock (power/arsenal/ArsenalFlintlock, items/flintlock.json) drawn as the "Dueling Pistol"
from the Blockbench Workshop (CC BY 4.0; tools/bbmodels/dueling-pistol.bbmodel), in the hand and in the inventory alike.

The file: 12 cubes in a 32x32 UV space on a 64x64 texture, the barrel along +x (muzzle at x 15), the grip low at the back.
Its cubes are laid on the diagonal the old voxelised sprite (flintlock_held) lay on, as gen_flintlock_rifle.py lays the
musket: muzzle up and left, the top of the gun up and right, so the hand-held placements tuned for the sprite still fit.
Turns are kept exactly (freeform.py). The spent gun uses the same model (the old sprites showed the hammer down).

Run from the repo root: python3 -I tools/blockbench/dueling_pistol.py
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import load, write_json
from freeform import convert

SRC = os.path.join(HERE, "..", "bbmodels", "dueling-pistol.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
R = 1 / math.sqrt(2)
# the gun's +x (muzzle) to up-left, its +y (top) to up-right, its +z out of the sprite's back
FRAME = ((-R, R, 0), (R, R, 0), (0, 0, -1))
PIVOT = (7.3, 2.8, 8.16)
CENTRE = (7.6, 8.4, 8.0)
SCALE = 0.85

elements, atlas = convert(load(SRC), FRAME, PIVOT, CENTRE, SCALE)
atlas.save(f"{ASSETS}/textures/item/flintlock_3d.png")
model = {
    "credit": "Dueling Pistol (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "gui_light": "front",
    "textures": {"0": "jaymospowers:item/flintlock_3d", "particle": "jaymospowers:item/flintlock_3d"},
    "elements": elements,
    "display": {
        # third person as flintlock_held had it, first person as vanilla's handheld
        "thirdperson_righthand": {"rotation": [10, -90, 55], "translation": [0, 2.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [10, 90, -55], "translation": [0, 2.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    },
}
write_json(f"{ASSETS}/models/item/flintlock_3d.json", model)
write_json(f"{ASSETS}/items/flintlock.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/flintlock_3d"}})
print(len(elements), "elements")
