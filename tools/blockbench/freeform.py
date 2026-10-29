"""A Blockbench model's cubes as Java item-model elements with their turns kept exactly: 26.3 reads an element's
rotation as free x/y/z degrees (CuboidRotation.EulerXYZRotation, JOML rotationZYX, so R = Rz * Ry * Rx), so nothing is
snapped to the old single-axis 22.5-degree steps the way bake.convert_cubes does. Group turns are folded into each cube,
inflate grows the box, and the whole model can be laid into a new frame (as gen_flintlock_rifle.py lays the musket on
the sprite's diagonal).

convert(bb, frame, pivot, centre, scale): a model point p lands at centre + scale * frame * (p - pivot); frame is a 3x3
matrix (rows), identity to keep the model's own axes.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bake import apply, build_atlas, chain_matrix, flatten, mul, texture_images, transform_point

IDENTITY = ((1, 0, 0), (0, 1, 0), (0, 0, 1))


def euler(m):
    """M = Rz * Ry * Rx back to (x, y, z) degrees."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(m[2][1], m[2][2])
        c = math.atan2(m[1][0], m[0][0])
    else:
        a = math.atan2(-m[1][2], m[1][1])
        c = 0.0
    return [round(math.degrees(v), 4) for v in (a, b, c)]


def convert(bb, frame=IDENTITY, pivot=(0, 0, 0), centre=(0, 0, 0), scale=1.0, drop=()):
    """(elements, atlas image). Cubes named or grouped under a name in drop are left out."""
    atlas, _, atlas_uv = build_atlas(texture_images(bb))
    dropped = dropped_uuids(bb, set(drop))
    out = []
    for el, chain in flatten(bb):
        if el["uuid"] in dropped or el.get("export") is False:
            continue
        g = el.get("inflate", 0) or 0
        frm = [el["from"][i] - g for i in range(3)]
        to = [el["to"][i] + g for i in range(3)]
        anchor = el.get("origin", [0, 0, 0])
        world = transform_point(chain, anchor)
        rel = apply(frame, [world[i] - pivot[i] for i in range(3)])
        origin = [centre[i] + scale * rel[i] for i in range(3)]
        lo = [origin[i] + scale * (frm[i] - anchor[i]) for i in range(3)]
        hi = [origin[i] + scale * (to[i] - anchor[i]) for i in range(3)]
        faces = {}
        for name, face in el["faces"].items():
            if face.get("texture") is None:
                continue
            u0, v0, u1, v1 = face["uv"]
            if u0 == u1 or v0 == v1:
                continue
            entry = {"uv": atlas_uv(int(face["texture"]), face["uv"]), "texture": "#0"}
            if face.get("rotation"):
                entry["rotation"] = face["rotation"]
            faces[name] = entry
        if not faces:
            continue
        element = {"from": [round(v, 4) for v in lo], "to": [round(v, 4) for v in hi], "faces": faces}
        x, y, z = euler(mul(frame, chain_matrix(chain)))
        if x or y or z:
            element["rotation"] = {"origin": [round(v, 4) for v in origin], "x": x, "y": y, "z": z}
        for v in element["from"] + element["to"]:
            assert -16 <= v <= 32, element
        out.append(element)
    return out, atlas


def dropped_uuids(bb, names):
    out = set()
    elements = {e["uuid"]: e for e in bb["elements"]}

    def walk(nodes, inside):
        for n in nodes:
            if isinstance(n, str):
                if inside or elements.get(n, {}).get("name") in names:
                    out.add(n)
            else:
                walk(n.get("children", []), inside or n.get("name") in names)
    groups = {g["uuid"]: g["name"] for g in bb.get("groups", [])}
    def named(nodes):
        for n in nodes:
            if isinstance(n, dict):
                n.setdefault("name", groups.get(n["uuid"]))
                named(n.get("children", []))
    named(bb.get("outliner", []))
    walk(bb.get("outliner", []), False)
    return out
