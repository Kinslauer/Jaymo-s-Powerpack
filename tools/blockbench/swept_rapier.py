"""(Andy's batch 4) The Armory's iron rapier (items/iron_rapier.json -> models/item/armory/iron_rapier) as the "Swept Rapier"
Blockbench Workshop model (CC BY 4.0; the file names no author), replacing the one tools/blockbench/make.py drew.

The file: 10 cubes (the blade and most of the hilt are flat planes, zero thick), six "mesh" elements that Java models can't
draw, and five textures of 64, 64, 64, 128 and 256 pixels (four texels to a UV unit in each) that go side by side into one
384x256 atlas (textures/item/armory/iron_rapier.png). Other changes:
  * the model is 62 units tall (a bit under four blocks): it is scaled by SCALE to the old rapier's 40 (pommel at -8, tip at
    32) and centred on x and z 8.
  * the whole model is turned a quarter about the vertical axis (SPIN) so its blade's flat faces the way our other 3D swords'
    do and display() works.
  * the six meshes were the little wedges and the swept guard bars of the hilt. They are rebuilt as small cubes from their
    world-space vertices: the three small ones (a roof ridge, a block and the tapered pommel nut) as one box each (a triangular
    prism becomes a box of its average height), and each bar, a bent sheet, as a chain of 0.5-thick plates, one per straight
    stretch (BARS below; their lengths, tilts and z spans are measured from the mesh vertices, after the mesh's own turn
    about its origin). Each plate takes the UV rectangle of the mesh face it stands for. The plates' tilts (35, 31, 37.5 degrees
    and so on) are kept exact: 26.3 takes any angle (bake.py, exact=True).
  * display(): the old rapier's values (flip=True, grip in the fist like our other 3D weapons); see the previews.

Run from the repo root: python3 -I tools/blockbench/swept_rapier.py
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import rot_y, bake_cube, build_atlas, flatten, load, rot_z, texture_images, texture_index, show_both_sides, uv_rotation, write_json
from display import display

SRC = os.path.join(HERE, "..", "bbmodels", "swept-rapier.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
SCALE = 40 / 62
FLIP = True
# the file's blade is flat to the x axis; display() holds blades flat to z (like our other 3D swords), so the model is turned 90 about y
SPIN = rot_y(90)
THICK = 0.5
FACES6 = ("north", "south", "east", "west", "up", "down")

bb = load(SRC)
textures = texture_images(bb)
# by hand: the 256 square at the left, the 128 beside it, the three 64s under that (id order 0..4 = 64, 64, 64, 128, 256)
layout = ([(256, 128), (320, 128), (256, 192), (256, 0), (0, 0)], (384, 256))
atlas, _, atlas_uv = build_atlas(textures, layout=layout)
shift = (8, -8 + 4 * SCALE, 8)  # model y -4 (the pommel) at -8; x, z middle on 8


def place(frm, to, chain, faces, worst):
    """faces: {face name: (uv rect [u0, v0, u1, v1], texture id)}; returns the baked element."""
    new, mapping, err = bake_cube(frm, to, chain, shift=shift, scale=SCALE, spin=SPIN, exact=True)
    worst[0] = max(worst[0], err)
    out = {}
    for name, (new_name, k) in mapping.items():
        if name not in faces:
            continue
        uv, tex = faces[name]
        if uv[0] == uv[2] or uv[1] == uv[3]:
            continue
        entry = {"uv": atlas_uv(texture_index(bb, tex), uv), "texture": "#0"}
        if k:
            entry["rotation"] = uv_rotation(k)
        out[new_name] = entry
    new["faces"] = out
    return show_both_sides(new, atlas)


worst = [0.0]
elements = []
# the ten cubes as drawn (none of them is turned)
for el, chain in flatten(bb):
    faces = {n: (f["uv"], f["texture"]) for n, f in el["faces"].items() if f.get("texture") is not None}
    elements.append(place(el["from"], el["to"], chain, faces, worst))


def box(frm, to, uvs, tex):
    """A box standing for a small mesh; uvs: {face: [u0, v0, u1, v1]} (the other faces take the first one's)."""
    default = next(iter(uvs.values()))
    return place(frm, to, [], {n: (uvs.get(n, default), tex) for n in FACES6}, worst)


# mesh 1, the roof ridge on the ricasso (origin 0,7,0): a triangular prism, 1 high, as a half-height box
elements.append(box((-0.5, 7, -1.075), (0.5, 7.5, 1.075), {
    "south": [2, 15, 3, 16], "north": [15, 6, 16, 7], "up": [2, 15, 3, 16], "down": [13, 12, 14, 14],
    "east": [4.05, 11, 6.2, 12], "west": [11.05, 6, 13.2, 7]}, 0))
# mesh 2, the block over it (origin 0,5,0): y 6 to 7
elements.append(box((-0.5, 6, -1.075), (0.5, 7, 1.075), {
    "east": [11.05, 8, 13.2, 9], "west": [11.05, 10, 13.2, 11], "up": [14, 0, 15, 2], "down": [14, 3, 15, 5],
    "south": [15, 8, 16, 9], "north": [10, 15, 11, 16]}, 0))
# mesh 3, the pommel nut's taper (origin 0,-2,0): a prism 2 wide at the top to a ridge at the bottom, as a box 1 wide
elements.append(box((-0.5, -3, -0.5), (0.5, -1, 0.5), {
    "east": [8.106, 0, 9.894, 2.236], "west": [2.106, 8, 3.894, 10.236], "up": [2, 12, 3, 14],
    "south": [11, 12, 12, 14], "north": [4, 13, 5, 15], "down": [8.106, 0, 8.6, 0.5]}, 0))

# the three swept guard bars: each a polyline in the xy plane (world, after the mesh's turn about its origin), a z span, and
# per stretch the UV rectangles of the mesh faces for its two sides and the texture id
BARS = [
    # mesh 4 (origin 1,5,3, not turned): straight down the +x side, then slanting in to the pommel
    ((1, 5), [(2.0, 10.5), (2.0, 5.5), (1.99, 5.296), (-0.06, -0.342)], (1, 5), [
        ([0, 0, 4, 5], [5, 7, 9, 12]), None, ([0, 5.204, 4, 11.204], [5, 0, 9, 6])], 3),
    # mesh 5 (origin -1,10,-1, turned -35 about z): a swept arm down the -x side
    ((-1, 10), [(-0.713, 10.41), (-2.434, 7.952), (-3.0, 7.0), (-3.0, 6.0)], (-3, 1), [
        ([6, 4, 10, 7], [6, 8, 10, 11]), ([11, 6, 15, 7], [11, 6, 15, 7]), ([11, 8, 15, 9], [11, 10, 15, 11])], 2),
    # mesh 6 (origin 0,0,3, turned 37.5): the arm on its far side
    ((0, 0), [(0.0, 0.0), (-2.435, 3.173), (-3.0, 6.0), (-3.0, 7.0)], (1, 6), [
        ([0, 0, 5, 4], [0, 5, 5, 9]), ([6, 0, 11, 3], [6, 0, 11, 3]), ([0, 10, 5, 11], [11, 4, 16, 5])], 2),
]
for _, points, (z0, z1), rects, tex in BARS:
    stretches = [i for i, r in enumerate(rects) if r is not None]
    seg_for = [0, 1, 2] if len(points) == 4 else []
    for i in range(len(points) - 1):
        rect = rects[i]
        if rect is None:  # the short hop between a bar's straight part and its slant: no plate of its own
            continue
        (ax, ay), (bx, by) = points[i], points[i + 1]
        length = math.hypot(bx - ax, by - ay)
        mx, my = (ax + bx) / 2, (ay + by) / 2
        mz = (z0 + z1) / 2
        tilt = math.degrees(math.atan2(-(bx - ax), by - ay)) if by >= ay else math.degrees(math.atan2(bx - ax, ay - by))
        frm = (mx - THICK / 2, my - length / 2, z0)
        to = (mx + THICK / 2, my + length / 2, z1)
        side_a, side_b = rect
        faces = {"east": (side_a, tex), "west": (side_b, tex), "north": (side_a[:2] + [side_a[0] + 1, side_a[1] + 1], tex),
                 "south": (side_a[:2] + [side_a[0] + 1, side_a[1] + 1], tex), "up": (side_a[:2] + [side_a[0] + 1, side_a[1] + 1], tex),
                 "down": (side_a[:2] + [side_a[0] + 1, side_a[1] + 1], tex)}
        elements.append(place(frm, to, [((mx, my, mz), rot_z(tilt))], faces, worst))

lo = min(min(e["from"][i] for e in elements) for i in range(3))
hi = max(max(e["to"][i] for e in elements) for i in range(3))
print(f"{len(elements)} elements, worst tilt error {worst[0]:.1f} degrees; extent {lo:.1f}..{hi:.1f}")
for axis in range(3):
    print("xyz"[axis], round(min(e["from"][axis] for e in elements), 1), round(max(e["to"][axis] for e in elements), 1))

atlas.save(f"{ASSETS}/textures/item/armory/iron_rapier.png")
model = {
    "credit": "Swept Rapier (Blockbench Workshop, CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": "jaymospowers:item/armory/iron_rapier", "particle": "jaymospowers:item/armory/iron_rapier"},
    "elements": elements,
    # the grip is the model's y -2 to 6, so its middle (2) lands at -4.1; the whole sword's length is centred on 12
    "display": display(tp=0.55, fp=0.5, gui=0.4, center_y=12, grip_y=(2 + 4) * SCALE - 8, flip=FLIP),
}
write_json(f"{ASSETS}/models/item/armory/iron_rapier.json", model)
