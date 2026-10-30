"""(Andy's batch 4) The harpoon, in 3D: the Armory's harpoon (items/harpoon.json -> models/item/armory/harpoon) and Jaymo's
Kingdoms' whaler harpoon (jaymoskingdoms:item/harpoon) both get the "Harpoon" Blockbench Workshop model (CC BY 4.0; the
file names no author) as their look. The thrown harpoon of the Armory (ThrownWeaponRenderer) draws the item's model, so it
changes too; the whaling harpoon that lodges in a whale (Kingdoms' HarpoonRenderer) draws its own boxes and is unchanged.

The file: 109 voxel-like cubes laid out on the diagonal like a sprite (tip up and to the right), box_uv off (each face has its
own UVs), nested groups that turn the barbs and the head (about the z axis, plus 10 degrees about x and y on two of them),
and seven 32x32 textures (iron, aeternium, diamond, gold, netherite, ruby, stone) whose UVs are in a 16x16 space. All its
faces use one texture, aeternium's; here:
  * the seven textures go side by side into one 128x64 atlas (textures/item/armory/harpoon_3d.png, and Kingdoms' copy), so a
    tier is only a different tile; both of ours use IRON (VARIANT) tile.
  * the group turns are baked into the cubes (bake.py, exact=True): every cube keeps its exact place and its exact turn.
    26.3 takes any angle about one axis, or x, y and z together, so only the quarter turns are absorbed (by swapping a box's
    sides) and whatever is left stays as drawn. (0.17.x baked them to the nearest multiple of 22.5 up to 45, which bent the
    barbs, up to 14 degrees off, and scrambled the head.)
  * the whole model is scaled to SCALE (0.75) of the file's size (the file's is two blocks long) and centred on 8,8,8 along its
    axis, so the thrown harpoon spins and sinks its tip right.
  * display(): the model is a diagonal sprite shape, so display(roll=45) stands it upright for the hand; the grip is a third
    of the way up the shaft.
The old 16x16 sprite textures/item/armory/harpoon.png stays: the Tinkering "Harpoon Head" part icon is cut from it.

Run from the repo root: python3 -I tools/blockbench/harpoon.py
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import bake_cube, build_atlas, flatten, load, texture_images, transform_point, uv_rotation, write_json
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "harpoon.bbmodel")
POWERS = "src/main/resources/assets/jaymospowers"
KINGDOMS = "kingdoms/src/main/resources/assets/jaymoskingdoms"
VARIANT = 0  # pike_iron
SCALE = 0.75
GRIP = 0.34  # how far up the shaft (tail 0, tip 1) the fist holds it

bb = load(SRC)
textures = texture_images(bb)
atlas, tile_at, atlas_uv = build_atlas(textures, columns=4)
cubes = flatten(bb)

# where the cubes' centres are in the file, to centre the model: the axis runs tail (low x + y) to tip (high x + y)
centres = []
for el, chain in cubes:
    c = tuple((el["from"][i] + el["to"][i]) / 2 for i in range(3))
    centres.append(transform_point(chain, c))
along = [(c[0] + c[1]) / math.sqrt(2) for c in centres]
tail, tip = min(along), max(along)
mid = (tail + tip) / 2
# the model's own middle on the axis line, and the axis's sideways offset from the 8,8 diagonal
lo = min(centres, key=lambda c: c[0] + c[1])
hi = max(centres, key=lambda c: c[0] + c[1])
axis_side = ((lo[0] - lo[1]) + (hi[0] - hi[1])) / 2 / math.sqrt(2)
mid_xy = ((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2)
z_mid = (min(c[2] for c in centres) + max(c[2] for c in centres)) / 2
centre = (mid_xy[0], mid_xy[1], z_mid)
# the axis midpoint goes to 8,8,8 (the axis passes through the middle of the item)
shift = tuple(8 - centre[i] * SCALE for i in range(3))

elements = []
worst = 0.0
for el, chain in cubes:
    new, faces, err = bake_cube(el["from"], el["to"], chain, shift=shift, scale=SCALE, exact=True)
    worst = max(worst, err)
    out_faces = {}
    for name, (new_name, k) in faces.items():
        face = el["faces"][name]
        if face.get("texture") is None:
            continue
        u0, v0, u1, v1 = face["uv"]
        if u0 == u1 or v0 == v1:
            continue
        entry = {"uv": atlas_uv(VARIANT, face["uv"]), "texture": "#0"}
        if k:
            entry["rotation"] = uv_rotation(k)
        out_faces[new_name] = entry
    new["faces"] = out_faces
    elements.append(new)

# the model's extent along its length, as if upright (for the grip and where the thrown one's tip is)
ups = []
for e in elements:
    for corner in ((e["from"][0], e["from"][1]), (e["to"][0], e["to"][1])):
        ups.append(8 + ((corner[0] - 8) + (corner[1] - 8)) / math.sqrt(2))
tail_y, tip_y = min(ups), max(ups)
grip_y = tail_y + GRIP * (tip_y - tail_y)
reach = (tip_y - 8)
print(f"{len(elements)} elements, worst tilt error {worst:.1f} degrees; length {tip_y - tail_y:.1f}, tip {reach:.1f} past the middle")
print(f"x {min(e['from'][0] for e in elements):.1f}..{max(e['to'][0] for e in elements):.1f} y {min(e['from'][1] for e in elements):.1f}..{max(e['to'][1] for e in elements):.1f}")


def model(texture):
    return {
        "credit": "Harpoon (Blockbench Workshop, CC BY 4.0)",
        "texture_size": [16, 16],
        "textures": {"0": texture, "particle": texture},
        "elements": elements,
        "display": display(tp=0.6, fp=0.5, gui=0.62, center_y=8, grip_y=grip_y, roll=45),
    }


atlas.save(f"{POWERS}/textures/item/armory/harpoon_3d.png")
write_json(f"{POWERS}/models/item/armory/harpoon.json", model("jaymospowers:item/armory/harpoon_3d"))
atlas.save(f"{KINGDOMS}/textures/item/harpoon_3d.png")
write_json(f"{KINGDOMS}/models/item/harpoon.json", model("jaymoskingdoms:item/harpoon_3d"))
