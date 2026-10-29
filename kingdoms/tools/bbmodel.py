"""Blockbench models for our horses (0.7.322): a build (or its barding) read from a Blockbench project, and every build
written out as one for whoever models them.

Make a horse in Blockbench as a "Modded Entity" project (box UV). Name each group after one of our bones (body, head,
neck, mane, leg1_a ... leg4_c, tail_a, tail_b, tail_c, the saddle, stirrup, bit and rein bones and so on; the starter
files, `python3 tools/bbmodel.py export`, have them all in place) and save it as tools/horse_models/<build>.bbmodel.
gen_horses.py then takes its shapes instead of horse_rig.py's: extra groups are fine (they hang from their parent
group), groups named armor_<anything> are the barding, and a cube with its own rotation is hung from a group of its
own as Blockbench's Java export does it. Each cube's name says how it's painted: its region (head, neck, body, mane,
chanfron, crinet, caparison, ...), then optionally |kind (hide, hair, hoof, armor ...), |axis and |tip for hair, as
in horse_rig.Cube, e.g. "mane|hair|z|1". The texture is laid out and painted by gen_horses.py, so the project's own
UVs and texture don't matter (yet).

Blockbench keeps models with y up, the ground at 0 and absolute pivots; Minecraft's parts are y down, pivots relative
to the parent and x flipped. The conversion here is the one Blockbench's own Java export does."""
import json
import math
import os
import sys
import uuid

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import horse_rig as hr  # noqa: E402

MODELS = os.path.join(HERE, "horse_models")
# the bones BreedHorseModel and the tack look for: a model without one of these can't be animated
REQUIRED = ("body", "tail_a", "tail_b", "tail_c", "head", "upper_mouth", "lower_mouth", "upper_mouth_open", "lower_mouth_open",
            "left_ear", "right_ear", "neck", "mane", "head_saddle", "saddle", "saddle_b", "saddle_c", "stirrup_left",
            "stirrup_left_iron", "stirrup_right", "stirrup_right_iron", "bit_left", "bit_right", "rein_left", "rein_right") + tuple(
    f"leg{k}_{s}" for k in range(1, 5) for s in "abc")
GROUND = 24.0  # a root part's pivot y in Minecraft is this less its height in Blockbench


def cube_name(c):
    """A cube's name in Blockbench: how it's painted."""
    bits = [c.region or c.kind]
    extra = [c.kind if c.kind != "hide" and c.region else "", c.axis or "", str(c.tip) if c.axis else ""]
    while extra and not extra[-1]:
        extra.pop()
    if getattr(c, "nostril", False):
        extra += [""] * (3 - len(extra)) + ["nostril"]
    return "|".join(bits + extra)


def parse_name(name):
    bits = (name.split("|") + ["", "", "", ""])[:5]
    region, kind, axis, tip, flag = bits
    kinds = {"hide", "hair", "hoof", "armor", "headstall", "saddle", "strap", "iron", "rein", "wing", "feather", "horn"}
    if not kind and region in kinds:
        kind, region = region, None
    c = hr.Cube((0, 0, 0), (0, 0, 0), kind=kind or "hide", region=region or None, axis=axis or None,
                tip=int(tip) if tip else 1)
    if flag == "nostril":
        c.nostril = True
    return c


# ---- reading ------------------------------------------------------------------------------------------------------------
def read(path):
    """A Blockbench project as (horse parts, barding parts), parents before children, texture laid out afresh."""
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    if data.get("meta", {}).get("box_uv") is False:
        print(f"{path}: per-face UV; we lay the texture out ourselves, so that's fine")
    if data.get("modded_entity_flip_y", True) is False:
        raise SystemExit(f"{path}: turn \"Flip Y Axis\" back on in the project settings (Minecraft's parts are y down)")
    elements = {e["uuid"]: e for e in data.get("elements", []) if e.get("export", True)}
    groups = {g["uuid"]: g for g in data.get("groups", [])}  # (Blockbench 5 keeps groups apart from the outliner)
    parts = []

    def group_of(node):
        if isinstance(node, dict):
            g = dict(groups.get(node.get("uuid"), {}))
            g.update({k: v for k, v in node.items() if k != "children"})
            return g, node.get("children", [])
        return None, None

    def walk(node, parent):
        g, children = group_of(node)
        if g is None:
            return
        name = g["name"]
        o = g.get("origin", [0, 0, 0])
        r = g.get("rotation", [0, 0, 0])
        if parent is None:
            pose = (-o[0], GROUND - o[1], o[2])
        else:
            po = parent[1]
            pose = (po[0] - o[0], po[1] - o[1], o[2] - po[2])
        part = hr.Part(name, parent[0] if parent else None, (*map(lambda v: round(v, 4), pose), -r[0], -r[1], r[2]), [])
        parts.append(part)
        spun = 0
        for child in children:
            if isinstance(child, str):
                e = elements.get(child)
                if e is None or e.get("type", "cube") != "cube":
                    continue
                er = e.get("rotation", [0, 0, 0])
                if any(abs(v) > 1e-6 for v in er):
                    # a turned cube: on a part of its own at the cube's pivot, as Blockbench's Java export does it
                    spun += 1
                    eo = e.get("origin", o)
                    holder = hr.Part(f"{name}_r{spun}", name, (round(o[0] - eo[0], 4), round(o[1] - eo[1], 4), round(eo[2] - o[2], 4),
                                                              -er[0], -er[1], er[2]), [])
                    parts.append(holder)
                    holder.cubes.append(cube(e, eo))
                else:
                    part.cubes.append(cube(e, o))
            else:
                walk(child, (name, o))

    for node in data.get("outliner", []):
        walk(node, None)
    names = {p.name for p in parts}
    missing = [b for b in REQUIRED if b not in names]
    if missing:
        raise SystemExit(f"{path}: no group for {', '.join(missing)} (see the starter files)")
    horse = [p for p in parts if not p.name.startswith("armor_")]
    armour = [p for p in parts if p.name.startswith("armor_")]
    for p in armour:
        for c in p.cubes:
            c.kind = "armor"
    hr.pack(horse)
    if armour:
        hr.pack(armour, hr.ARMOR_TEXTURE)
    return horse, armour


def cube(e, o):
    """One Blockbench cube as a Cube from its part's pivot `o` (Blockbench space)."""
    c = parse_name(e.get("name", "hide"))
    f, t = e["from"], e["to"]
    c.size = tuple(round(t[k] - f[k], 4) for k in range(3))
    c.pos = (round(o[0] - t[0], 4), round(o[1] - t[1], 4), round(f[2] - o[2], 4))
    c.grow = float(e.get("inflate", 0.0))
    return c


# ---- writing ------------------------------------------------------------------------------------------------------------
def write(parts, path, texture=None, name="horse"):
    """`parts` as a Blockbench project (box UV, Modded Entity), with `texture` (a PNG path) on it if given."""
    by = {p.name: p for p in parts}
    origins = {}

    def origin(p):
        if p.name not in origins:
            if p.parent is None:
                origins[p.name] = (-p.pose[0], GROUND - p.pose[1], p.pose[2])
            else:
                po = origin(by[p.parent])
                origins[p.name] = (po[0] - p.pose[0], po[1] - p.pose[1], po[2] + p.pose[2])
        return origins[p.name]

    elements, nodes = [], {}
    for p in parts:
        o = origin(p)
        node = {"name": p.name, "origin": [round(v, 4) for v in o], "rotation": [-p.pose[3], -p.pose[4], p.pose[5]],
                "uuid": str(uuid.uuid4()), "export": True, "isOpen": False, "visibility": True, "children": []}
        for c in p.cubes:
            w, h, d = c.size
            to = (o[0] - c.pos[0], o[1] - c.pos[1], o[2] + c.pos[2] + d)
            fr = (to[0] - w, to[1] - h, o[2] + c.pos[2])
            u, v = c.uv or (0, 0)
            el = {"name": cube_name(c), "box_uv": True, "rescale": False, "from": [round(x, 4) for x in fr],
                  "to": [round(x, 4) for x in to], "autouv": 0, "color": 0, "inflate": c.grow, "origin": [round(x, 4) for x in o],
                  "uv_offset": [u, v], "faces": box_faces(u, v, w, h, d), "type": "cube", "uuid": str(uuid.uuid4())}
            elements.append(el)
            node["children"].append(el["uuid"])
        nodes[p.name] = node
    outliner = []
    for p in parts:
        (nodes[p.parent]["children"] if p.parent else outliner).append(nodes[p.name])
    data = {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True}, "name": name,
            "model_identifier": name, "modded_entity_version": "Fabric 1.17+", "visible_box": [1, 1, 0],
            "resolution": {"width": hr.TEXTURE[0], "height": hr.TEXTURE[1]}, "elements": elements, "outliner": outliner,
            "textures": []}
    if texture:
        import base64
        with open(texture, "rb") as fh:
            src = "data:image/png;base64," + base64.b64encode(fh.read()).decode()
        data["textures"].append({"name": os.path.basename(texture), "id": "0", "render_mode": "default", "uuid": str(uuid.uuid4()),
                                 "source": src, "width": hr.TEXTURE[0] * 2, "height": hr.TEXTURE[1] * 2, "uv_width": hr.TEXTURE[0],
                                 "uv_height": hr.TEXTURE[1]})
        for el in elements:
            for face in el["faces"].values():
                face["texture"] = 0
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=1)


def box_faces(u, v, w, h, d):
    """Blockbench's own box UV faces (it works these out again from uv_offset; written so older versions show them)."""
    return {"north": {"uv": [u + d, v + d, u + d + w, v + d + h]}, "east": {"uv": [u, v + d, u + d, v + d + h]},
            "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h]},
            "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h]},
            "up": {"uv": [u + d + w, v + d, u + d, v]}, "down": {"uv": [u + d + 2 * w, v, u + d + w, v + d]}}


def load(build):
    """A build's (horse parts, barding parts): its Blockbench model if there is one, else horse_rig's."""
    path = os.path.join(MODELS, build + ".bbmodel")
    if os.path.exists(path):
        horse, armour = read(path)
        return horse, armour or hr.armour(build, horse)
    parts = hr.rig(build)
    return parts, hr.armour(build, parts)


def main(argv):
    if len(argv) >= 2 and argv[1] == "export":
        out = argv[2] if len(argv) > 2 else os.path.join(MODELS, "starters")
        for b in hr.BUILDS:
            parts = hr.rig(b)
            armour = hr.armour(b, parts)
            coats = os.path.join(HERE, "..", "src/main/resources/assets/jaymoskingdoms/textures/entity/breed_horse", b)
            tex = os.path.join(coats, sorted(os.listdir(coats))[0]) if os.path.isdir(coats) else None
            write(parts, os.path.join(out, b + ".bbmodel"), tex, b)
            write(parts + armour, os.path.join(out, b + "_barded.bbmodel"), None, b + "_barded")
        print(f"wrote starters for {len(hr.BUILDS)} builds to {out}")
    else:
        print(__doc__)


if __name__ == "__main__":
    main(sys.argv)
