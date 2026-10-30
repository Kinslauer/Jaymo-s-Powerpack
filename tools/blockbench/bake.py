"""Shared helpers for the Blockbench-to-Java-item-model converters in this folder (harpoon.py, swept_rapier.py,
voidreaver.py, tidebreaker.py; spell_tome.py and the older ones need none of it).

Java item models allow each element ONE rotation, about one axis, at -45, -22.5, 0, 22.5 or 45 degrees. Blockbench lets a
cube turn about all three axes by any angle, and lets the groups it sits in turn as well. {@link bake_cube} takes a cube
and the whole chain of turns above it and finds the nearest legal element: it looks through every way of laying the box
down on the axes (the 24 quarter-turn orientations, which swap its sides and move its faces and their textures along) and
every legal single turn on top, and keeps the pair that comes closest to the real orientation. The cube's centre is placed
exactly where Blockbench puts it; only its tilt is approximated, by at most the error {@link bake_cube} reports.

Coordinates, faces and UV turns follow the game's own conventions (FaceInfo's vertex order, a face's "rotation" turning its
texture clockwise), not the preview script's, which turns a rotated face the other way; the models made here only rotate
faces that are not plain one-pixel patches, so the previews match.
"""
import base64, io, json, math
from PIL import Image

AXES = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}
LEGAL_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)
FACE_NAMES = ("north", "south", "east", "west", "up", "down")
# vanilla FaceInfo: each face's four corners as (x, y, z) picks of the box (0 = min side, 1 = max), the texture's
# (u0,v0), (u0,v1), (u1,v1), (u1,v0) corners in that order
FACE_CORNERS = {
    "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
    "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    "north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
}
FACE_NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
NORMAL_FACE = {v: k for k, v in FACE_NORMAL.items()}


# ---- small matrix helpers (3x3 as tuples of rows) ----

def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def apply(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def transpose(m):
    return tuple(tuple(m[j][i] for j in range(3)) for i in range(3))


def rot_x(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return ((1, 0, 0), (0, c, -s), (0, s, c))


def rot_y(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return ((c, 0, s), (0, 1, 0), (-s, 0, c))


def rot_z(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


ROTATION_SIGN = 1


def blockbench_rotation(rx, ry, rz):
    """Blockbench's Euler order: x first, then y, then z."""
    rx, ry, rz = rx * ROTATION_SIGN, ry * ROTATION_SIGN, rz * ROTATION_SIGN
    return mul(rot_z(rz), mul(rot_y(ry), rot_x(rx)))


def axis_rotation(axis, angle):
    return {"x": rot_x, "y": rot_y, "z": rot_z}[axis](angle)


def angle_between(a, b):
    """The angle (degrees) of the turn taking orientation a to orientation b."""
    m = mul(transpose(a), b)
    cos = max(-1.0, min(1.0, (m[0][0] + m[1][1] + m[2][2] - 1) / 2))
    return math.degrees(math.acos(cos))


def _octahedral():
    """The 24 proper rotations that map the axes onto the axes: signed permutation matrices of determinant +1."""
    out = []
    import itertools
    for perm in itertools.permutations(range(3)):
        for signs in itertools.product((1, -1), repeat=3):
            m = tuple(tuple(signs[i] if perm[i] == j else 0 for j in range(3)) for i in range(3))
            det = (m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                   + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]))
            if det == 1:
                out.append(m)
    return out


OCTA = _octahedral()
LEGAL = [("x", 0.0)] + [(axis, a) for axis in "xyz" for a in LEGAL_ANGLES if a]
LEGAL_MATRICES = [(axis, a, axis_rotation(axis, a)) for axis, a in LEGAL]


# ---- loading and flattening ----

def load(path):
    with open(path) as fh:
        return json.load(fh)


def texture_images(bb):
    """Each texture of the model as (RGBA image, uv_width, uv_height, id), in file order."""
    out = []
    for tex in bb["textures"]:
        img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
        out.append((img, tex.get("uv_width") or img.width, tex.get("uv_height") or img.height, tex.get("id", str(len(out)))))
    return out


def _group_table(bb):
    """uuid -> (origin, rotation) of every group, whether the file keeps them in 'groups' or inline in the outliner."""
    table = {g["uuid"]: g for g in bb.get("groups", [])}

    def walk(nodes):
        for n in nodes:
            if isinstance(n, dict):
                table.setdefault(n["uuid"], n)
                walk(n.get("children", []))
    walk(bb.get("outliner", []))
    return table


def flatten(bb, types=("cube",)):
    """Every element of the given types with the chain of group turns above it:
    [(element, [(origin, rotation matrix) from the element's own turn outward])]."""
    groups = _group_table(bb)
    elements = {e["uuid"]: e for e in bb["elements"]}
    out = []

    def walk(nodes, chain):
        for n in nodes:
            if isinstance(n, str):
                el = elements.get(n)
                if el is not None and el.get("type", "cube") in types:
                    own = []
                    rot = el.get("rotation")
                    if rot and any(rot):
                        own.append((el.get("origin", [0, 0, 0]), blockbench_rotation(*rot)))
                    out.append((el, own + chain))
            else:
                g = groups[n["uuid"]]
                link = []
                rot = g.get("rotation")
                if rot and any(rot):
                    link.append((g.get("origin", [0, 0, 0]), blockbench_rotation(*rot)))
                walk(n.get("children", []), link + chain)
    walk(bb["outliner"], [])
    return out


def transform_point(chain, p):
    for origin, rot in chain:
        rel = tuple(p[i] - origin[i] for i in range(3))
        r = apply(rot, rel)
        p = tuple(r[i] + origin[i] for i in range(3))
    return p


def chain_matrix(chain):
    m = ((1, 0, 0), (0, 1, 0), (0, 0, 1))
    for _, rot in chain:
        m = mul(rot, m)
    return m


# ---- baking ----

def euler_zyx(m):
    """(x, y, z) in degrees with m = Rz(z) Ry(y) Rx(x): the order 26.3's {"x", "y", "z"} element rotation (JOML rotationZYX) and
    Blockbench both use. m is a proper rotation that is not (nearly) a quarter turn about y."""
    y = math.asin(max(-1.0, min(1.0, -m[2][0])))
    assert abs(math.cos(y)) > 1e-6, "gimbal lock"
    x = math.atan2(m[2][1], m[2][2])
    z = math.atan2(m[1][0], m[0][0])
    back = mul(rot_z(math.degrees(z)), mul(rot_y(math.degrees(y)), rot_x(math.degrees(x))))
    assert all(abs(back[i][j] - m[i][j]) < 1e-6 for i in range(3) for j in range(3)), (m, back)
    return tuple(round(math.degrees(v), 4) + 0.0 for v in (x, y, z))


def exact_rotation(m, origin):
    """The 26.3 element "rotation" for the rotation matrix m about origin (angles are free there: any one axis, or x, y, z
    together), or None when m is the identity."""
    x, y, z = euler_zyx(m)
    turned = [(a, v) for a, v in zip("xyz", (x, y, z)) if abs(v) > 1e-4]
    if not turned:
        return None
    origin = [round(c, 4) for c in origin]
    if len(turned) == 1:
        return {"angle": turned[0][1], "axis": turned[0][0], "origin": origin}
    return {"origin": origin, "x": x, "y": y, "z": z}


def bake_cube(frm, to, chain, shift=(0, 0, 0), scale=1.0, spin=None, exact=False):
    """The legal element standing in for a cube turned by `chain`, moved by `shift` after scaling its position (and size)
    by `scale` about the origin; `spin` (axis-free rotation matrix, applied last, about the origin) turns the whole model.
    Returns (element dict without faces, face map {original face: (new face, uv corner shift k)}, error in degrees)."""
    centre = tuple((frm[i] + to[i]) / 2 for i in range(3))
    half = tuple((to[i] - frm[i]) / 2 for i in range(3))
    world = transform_point(chain, centre)
    R = chain_matrix(chain)
    if spin is not None:
        world = apply(spin, world)
        R = mul(spin, R)
    world = tuple(world[i] * scale + shift[i] for i in range(3))
    best = None
    for S in OCTA:
        M = mul(R, transpose(S))
        if exact:
            # 26.3 takes any angle (and x, y, z together): only the quarter turns are absorbed by swapping the box's
            # sides, whatever is left over stays exact
            err = angle_between(M, ((1, 0, 0), (0, 1, 0), (0, 0, 1)))
            if best is None or err < best[0] - 1e-9:
                best = (err, S, None, None)
            continue
        for axis, angle, Q in LEGAL_MATRICES:
            err = angle_between(M, Q)
            if best is None or err < best[0] - 1e-9:
                best = (err, S, axis, angle)
    err, S, axis, angle = best
    if exact:
        residual = mul(R, transpose(S))
        err = 0.0
    h = apply(S, half)
    new_half = tuple(abs(h[i]) * scale for i in range(3))
    element = {
        "from": [round(world[i] - new_half[i], 4) for i in range(3)],
        "to": [round(world[i] + new_half[i], 4) for i in range(3)],
    }
    if exact:
        rotation = exact_rotation(residual, world)
        if rotation:
            element["rotation"] = rotation
    elif angle:
        element["rotation"] = {"angle": angle, "axis": axis, "origin": [round(c, 4) for c in world]}
    faces = {}
    for name in FACE_NAMES:
        n = apply(S, FACE_NORMAL[name])
        new_name = NORMAL_FACE[tuple(int(round(c)) for c in n)]
        # the original face's corners, laid down by S, against the new face's own
        orig = [apply(S, tuple((2 * c - 1) * half[i] for i, c in enumerate(pick))) for pick in FACE_CORNERS[name]]
        new = [tuple((2 * c - 1) * h_abs for c, h_abs in zip(pick, tuple(abs(x) for x in h))) for pick in FACE_CORNERS[new_name]]
        shift_k = None
        for k in range(4):
            if all(max(abs(new[j][i] - orig[(j + k) % 4][i]) for i in range(3)) < 1e-6 for j in range(4)):
                shift_k = k
                break
        assert shift_k is not None, (name, new_name)
        faces[name] = (new_name, shift_k)
    return element, faces, err


FLAT_FACES = {0: ("west", "east"), 1: ("down", "up"), 2: ("north", "south")}


def _region_clear(image, uv):
    """True when every texel under a face's UV rectangle (sixteenths of `image`) is fully transparent."""
    w, h = image.size
    u0, v0, u1, v1 = uv
    x0, x1 = sorted((u0 * w / 16, u1 * w / 16))
    y0, y1 = sorted((v0 * h / 16, v1 * h / 16))
    box = (int(x0 + 1e-6), int(y0 + 1e-6), max(int(x0 + 1e-6) + 1, math.ceil(x1 - 1e-6)), max(int(y0 + 1e-6) + 1, math.ceil(y1 - 1e-6)))
    return image.crop(box).getchannel("A").getextrema()[1] == 0


def show_both_sides(element, image, thin=0.7):
    """(Andy: the Iron Rapier's and Iron Dagger's blades were not there in game.) A plane or a thin plate (under `thin` units
    through, on any axis) has a face on each of its two broad sides, and the file paints its picture on one of them only: the
    other face's UV rectangle sits on empty texels. Culling hides the face turned away from the viewer, so seen from the empty
    side nothing at all is drawn. Here the empty side takes the picture of the other one (turned about u, so that it reads the
    right way round from behind). `image` is the atlas the element's UVs (in sixteenths) point into. Edits and returns `element`."""
    for a in range(3):
        if element["to"][a] - element["from"][a] > thin:
            continue
        pair = [n for n in FLAT_FACES[a] if n in element["faces"]]
        if len(pair) != 2:
            continue
        fa, fb = (element["faces"][n] for n in pair)
        clear_a, clear_b = _region_clear(image, fa["uv"]), _region_clear(image, fb["uv"])
        if clear_a == clear_b:
            continue
        shown, blank = (fa, fb) if clear_b else (fb, fa)
        u0, v0, u1, v1 = shown["uv"]
        blank["uv"] = [u1, v0, u0, v1]
        blank["texture"] = shown["texture"]
        blank.pop("rotation", None)
        if shown.get("rotation"):
            blank["rotation"] = shown["rotation"]
    return element


def uv_rotation(k):
    """A face whose vertex j takes texture corner (j + k) % 4 is the plain face turned this many degrees (clockwise)."""
    return (k * 90) % 360


# ---- textures ----

def pack(sizes, columns=None):
    """Lay out images of the given (w, h) in a grid or shelves: returns ([(x, y)...], (atlas w, atlas h))."""
    if columns:
        cw = max(w for w, h in sizes)
        ch = max(h for w, h in sizes)
        pos = [((i % columns) * cw, (i // columns) * ch) for i in range(len(sizes))]
        rows = (len(sizes) + columns - 1) // columns
        return pos, (cw * min(columns, len(sizes)), ch * rows)
    order = sorted(range(len(sizes)), key=lambda i: -sizes[i][1])
    shelf_w = max(max(w for w, h in sizes), math.ceil(math.sqrt(sum(w * h for w, h in sizes))))
    x = y = row_h = 0
    pos = [None] * len(sizes)
    atlas_w = 0
    for i in order:
        w, h = sizes[i]
        if x + w > shelf_w:
            x, y, row_h = 0, y + row_h, 0
        pos[i] = (x, y)
        x += w
        row_h = max(row_h, h)
        atlas_w = max(atlas_w, x)
    return pos, (atlas_w, y + row_h)


def build_atlas(textures, columns=None, layout=None):
    """Merges the model's textures into one image. Returns (image, [(x, y) of each], function (texture index, [u, v...]) ->
    UVs in the atlas, in the sixteenths a Java model counts in). layout = ([(x, y) of each], (width, height)) to place them
    by hand instead of in a grid or shelves."""
    pos, (aw, ah) = layout if layout else pack([img.size for img, _, _, _ in textures], columns)
    atlas = Image.new("RGBA", (aw, ah), (0, 0, 0, 0))
    for (img, _, _, _), (x, y) in zip(textures, pos):
        atlas.paste(img, (x, y))

    def uv(index, coords):
        img, uvw, uvh, _ = textures[index]
        x, y = pos[index]
        out = []
        for i in range(0, len(coords), 2):
            out.append(round((x + coords[i] * img.width / uvw) * 16 / aw, 4))
            out.append(round((y + coords[i + 1] * img.height / uvh) * 16 / ah, 4))
        return out
    return atlas, pos, uv


def texture_index(bb, texture):
    """The position in the file's texture list of the texture a face names (a face's "texture" is an index into the list,
    whatever the textures' own ids say)."""
    return int(texture)


def convert_cubes(bb, atlas_uv, shift=(0, 0, 0), scale=1.0, spin=None, texture=None, atlas=None):
    """Every cube of the model as legal elements, its faces' UVs put into the atlas (texture: a texture index to use for every
    face instead of the face's own; atlas: the merged image, to give a one-side-painted plane its picture on the back too, see
    show_both_sides). Faces with no texture or no area are left out. Returns (elements, worst tilt error)."""
    elements, worst = [], 0.0
    for el, chain in flatten(bb):
        new, mapping, err = bake_cube(el["from"], el["to"], chain, shift=shift, scale=scale, spin=spin)
        worst = max(worst, err)
        faces = {}
        for name, (new_name, k) in mapping.items():
            face = el["faces"][name]
            if face.get("texture") is None:
                continue
            u0, v0, u1, v1 = face["uv"]
            if u0 == u1 or v0 == v1:
                continue
            index = texture if texture is not None else texture_index(bb, face["texture"])
            entry = {"uv": atlas_uv(index, face["uv"]), "texture": "#0"}
            if k:
                entry["rotation"] = uv_rotation(k)
            faces[new_name] = entry
        if faces:
            new["faces"] = faces
            elements.append(show_both_sides(new, atlas) if atlas is not None else new)
    return elements, worst


def write_json(path, obj):
    with open(path, "w", newline="\n") as fh:
        json.dump(obj, fh, indent="\t")
        fh.write("\n")
