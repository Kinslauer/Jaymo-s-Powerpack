"""(0.7.309) The moonlit horse: a rare look any horse can be born with (Equine.MOONLIT), drawn as its own build of
BreedHorseModel. Its shape is jsellier23's Mystical Deer made into a horse (tools/horseify.py); this lays it on our bones
and bakes its own painted texture (and the silver hair horseify adds) into our box-UV layout, so it loads like any build.

Our bones: the body's pieces on body; the neck, head, ears, mane and the (empty) mouths all at the neck's base, so the
head moves as one as BreedHorseModel turns them; each leg's thigh (with its hock) on legN_a and its cannon on legN_b;
the tail under tail_a, turned back against the lift BreedHorseModel gives it. The vanilla saddle and the headstall are
the classic horse's (horse_rig), at the same places. Blockbench is y up with the ground at 0; ours is y down with the
ground at 24 and x flipped, converted as Blockbench's own Java export does."""
import base64
import copy
import io
import json
import math
import os
import sys

from PIL import Image

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import horse_rig as hr  # noqa: E402
import horseify  # noqa: E402

SOURCE = os.path.join(HERE, "horse_models", "source", "mystical_deer.bbmodel")
BUILD = "moonlit"
GROUND = 24.0
# the tail as BreedHorseModel holds it at rest (radians): its pieces are turned back by this so it hangs as drawn
TAIL_REST = -1.3089
# how much smaller than horseify makes it, so its back is as high as the classic horse's under the saddle (the tack
# hangs where the classic horse's back is)
SCALE = 0.93
# which deer groups go on which of our bones: bone -> (the group its pivot is at, the groups whose cubes it carries)
HEAD_ANCHOR = "cou"
BONES = {
    "body": ("corps", ["corps"]),
    "neck": (HEAD_ANCHOR, ["cou", "cou_haut"]),
    "head": (HEAD_ANCHOR, ["tete"]),
    "left_ear": (HEAD_ANCHOR, ["oreille_g"]),
    "right_ear": (HEAD_ANCHOR, ["oreille_d"]),
    "tail_a": ("queue", ["queue"]),
    # (deer left, at -x in Blockbench, is the horse's left, +x in ours)
    "leg1_a": ("patte_arg", ["patte_arg", "patte_arg_jarret"]), "leg1_b": ("patte_arg_bas", ["patte_arg_bas"]),
    "leg2_a": ("patte_ard", ["patte_ard", "patte_ard_jarret"]), "leg2_b": ("patte_ard_bas", ["patte_ard_bas"]),
    "leg3_a": ("patte_avg", ["patte_avg"]), "leg3_b": ("patte_avg_bas", ["patte_avg_bas"]),
    "leg4_a": ("patte_avd", ["patte_avd"]), "leg4_b": ("patte_avd_bas", ["patte_avd_bas"]),
}
# the cubes that are hair (horseify names them so): the mane goes on its own bone (hidden under barding)
MANE = "mane|hair"
# empty bones at another's pivot (BreedHorseModel looks for them all)
EMPTY = {"upper_mouth": HEAD_ANCHOR, "lower_mouth": HEAD_ANCHOR, "upper_mouth_open": HEAD_ANCHOR, "lower_mouth_open": HEAD_ANCHOR,
         "tail_b": "queue", "tail_c": "queue", "leg1_c": "patte_arg_bas", "leg2_c": "patte_ard_bas", "leg3_c": "patte_avg_bas",
         "leg4_c": "patte_avd_bas"}
# the classic horse's tack bones, as they are
TACK = ("head_saddle", "saddle", "saddle_b", "saddle_c", "stirrup_left", "stirrup_left_iron", "stirrup_right", "stirrup_right_iron",
        "bit_left", "bit_right", "rein_left", "rein_right")
# Blockbench's faces as ours (x flipped: its east, +x, is our -x side "a"); and how a box-UV face lies against the
# per-face UV of the same face (Blockbench shows the top turned round and the bottom flipped across)
FACE = {"up": "top", "down": "bottom", "north": "front", "south": "back", "east": "a", "west": "b"}
FLIP = {"top": (True, True), "bottom": (True, False)}


def deer():
    with open(SOURCE, encoding="utf-8") as fh:
        return horseify.horse(json.load(fh), "riding")


def build():
    """(parts, armour parts) of the moonlit horse; every cube carries `src` (its Blockbench faces) for bake()."""
    data = deer()
    m = horseify.Model(data)
    m.scale("racine", [0, 0, 0], SCALE, SCALE, SCALE)
    groups = {g["name"]: g for g in data["groups"]}
    parent = {}
    for name, (g, n, p) in m.nodes.items():
        parent[name] = m.gr[p["uuid"]]["name"] if p else None
    textures = [Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA") for t in data["textures"]]
    res = (data["resolution"]["width"], data["resolution"]["height"])
    parts = []
    made = set()

    def rot(g):
        r = g.get("rotation", [0, 0, 0])
        return (-r[0], -r[1], r[2])

    def add(name, par, pose):
        part = hr.Part(name, par, tuple(round(v, 4) for v in pose), [])
        parts.append(part)
        made.add(name)
        return part

    def cubes_into(part, group, holder_prefix, only=None, skip=()):
        """The group's own cubes onto `part` (in the group's space); a turned cube on a holder of its own."""
        o = groups[group]["origin"]
        k = 0
        for e in m.elems(group):
            if e.get("type", "cube") != "cube" or (only and e["name"] != only) or e["name"] in skip:
                continue
            er = e.get("rotation", [0, 0, 0])
            if any(abs(v) > 1e-6 for v in er):
                k += 1
                eo = e.get("origin", o)
                holder = add(f"{holder_prefix}_r{k}", part.name, (o[0] - eo[0], o[1] - eo[1], eo[2] - o[2], -er[0], -er[1], er[2]))
                holder.cubes.append(cube(e, eo, data, textures, res))
            else:
                part.cubes.append(cube(e, o, data, textures, res))

    def path(anchor, group):
        """The groups from `anchor` (not included) down to `group`."""
        out = []
        while group != anchor:
            out.append(group)
            group = parent[group]
            if group is None:
                raise SystemExit(f"{BUILD}: {out[0]} isn't under {anchor}")
        return out[::-1]

    def chain(bone, anchor, group, top):
        """The part that carries `group`'s cubes on `bone`: a part for each group between, posed from its parent group."""
        part, prev = top, anchor
        for g in path(anchor, group):
            name = f"{bone}_{g}"
            po, o = groups[prev]["origin"], groups[g]["origin"]
            pose = (po[0] - o[0], po[1] - o[1], o[2] - po[2], *rot(groups[g]))
            part = next((p for p in parts if p.name == name), None) or add(name, part.name, pose)
            prev = g
        return part

    def root(bone, anchor):
        o = groups[anchor]["origin"]
        return add(bone, None, (-o[0], GROUND - o[1], o[2], *rot(groups[anchor])))

    for bone, (anchor, carried) in BONES.items():
        top = root(bone, anchor)
        holder = top
        if bone == "tail_a":
            # turned back against the lift BreedHorseModel gives the tail, so at rest it hangs as drawn
            holder = add("tail_hang", bone, (0, 0, 0, -math.degrees(TAIL_REST), 0, 0))
        for g in carried:
            part = holder if g == anchor else chain(bone, anchor, g, holder)
            cubes_into(part, g, part.name, skip=(MANE,))
    mane = root("mane", HEAD_ANCHOR)
    for g in ("cou", "cou_haut"):
        cubes_into(mane if g == HEAD_ANCHOR else chain("mane", HEAD_ANCHOR, g, mane), g, f"mane_{g}", only=MANE)
    for bone, anchor in EMPTY.items():
        root(bone, anchor)
    classic = {p.name: p for p in hr.rig("palfrey")}
    for name in TACK:
        p = copy.deepcopy(classic[name])
        p.parent = None
        parts.append(p)
    hr.pack([p for p in parts if p.name not in TACK] + [p for p in parts if p.name in TACK])
    for p in parts:
        for c in p.cubes:
            if getattr(c, "bb_name", "") == "crane":
                c.eye = eye(c)  # (the armour leaves it open)
    armour = hr.plain_armour(BUILD, parts)
    return parts, armour


def sample(c, face, s, t):
    faces, textures, res = c.src
    f = faces.get(face)
    if f is None:
        return None
    flip_s, flip_t = FLIP.get(face, (False, False))
    s, t = (1 - s if flip_s else s), (1 - t if flip_t else t)
    u0, v0, u1, v1 = f["uv"]
    tex = textures[int(f["texture"])]
    u, v = u0 + s * (u1 - u0), v0 + t * (v1 - v0)
    return tex.getpixel((min(tex.width - 1, int(u * tex.width / res[0])), min(tex.height - 1, int(v * tex.height / res[1]))))


def eye(c):
    """Where the eye is painted on the skull's side, as (z, y) in its part's space: its darkest spot."""
    best = None
    for i in range(20):
        for j in range(20):
            s, t = (i + 0.5) / 20, (j + 0.5) / 20
            px = sample(c, "a", s, t)
            if px is None or px[3] < 20:
                continue
            lum = sum(px[:3])
            if best is None or lum < best[0]:
                best = (lum, s, t)
    if best is None:
        return None
    q = hr.local(c, "a", best[1], best[2])
    return (q[2], q[1])


def cube(e, o, data, textures, res):
    """One Blockbench cube as a Cube from its part's pivot `o`, sized to whole pixels about its middle (box UV needs them)."""
    f, t = e["from"], e["to"]
    size = [max(1.0, float(round(t[k] - f[k]))) if t[k] - f[k] > 0.05 else 0.0 for k in range(3)]
    mid = [(f[k] + t[k]) / 2 for k in range(3)]
    f2 = [mid[k] - size[k] / 2 for k in range(3)]
    t2 = [mid[k] + size[k] / 2 for k in range(3)]
    c = hr.Cube((round(o[0] - t2[0], 4), round(o[1] - t2[1], 4), round(f2[2] - o[2], 4)), tuple(size), kind="hide", region="moonlit")
    if "|hair" in e["name"]:
        c.kind, c.region = "hair", e["name"].split("|")[0]
    c.grow = float(e.get("inflate", 0.0))
    c.src = ({FACE[k]: v for k, v in e["faces"].items() if k in FACE and v.get("texture") is not None}, textures, res)
    c.bb_name = e["name"]
    return c


def bake(pxs):
    """The moonlit horse's own texture, from the deer's painted faces (and the silver hair), at our box UV."""
    out = {}
    for x in pxs:
        if getattr(x.cube, "src", None) is None:
            continue  # the classic tack bones (drawn by the tack's own texture)
        px = sample(x.cube, x.face, x.s, x.t)
        if px is not None and px[3] >= 20:
            out[(x.X, x.Y)] = px
    return out


if __name__ == "__main__":
    parts, armour = build()
    for p in parts:
        print(p.name, p.parent, p.pose, len(p.cubes))
