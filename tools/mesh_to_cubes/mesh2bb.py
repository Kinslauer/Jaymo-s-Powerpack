"""Turn a realistic triangle-mesh model (GLB/GLTF/OBJ/PLY, e.g. exported from Blender) into a Blockbench cube model.

    python3 tools/mesh_to_cubes/mesh2bb.py model.glb out.bbmodel [options]

What it does:
  1. Scales the mesh so it stands --height pixels tall (Blockbench units), feet on y=0, centred on x/z.
  2. Voxelises it on a --pitch pixel grid (inside test = majority vote of x/y/z ray parity, so small holes don't matter).
  3. Splits the voxels into parts: from the skeleton when the model is rigged (each joint name maps to a part such as
     head, left_arm, tail), or one "body" part otherwise. Each part becomes a Blockbench group with its pivot at the
     joint it bends from, so it can be animated.
  4. Fits each part with as few axis-aligned cubes as it can (big boxes first, grown while --fill of each new slab is
     solid), until --coverage of the part's voxels are inside a cube or --max-cubes is reached.
  5. Paints a 1 texel = 1 pixel texture: every texel takes the colour of the nearest point on the original surface, then
     the whole sheet is cut down to --colours colours (no dithering), the way vanilla mob skins use few flat shades.
     Faces hidden inside the same part get no texture, so they take no sheet space.

Options:
  --height N       model height in pixels (default 16)
  --pitch N        voxel size in pixels; 2 gives chunkier, vanilla-like blocks (default 1)
  --fill F         how solid each new slab of a growing cube must be, 0-1; lower = fewer, rougher cubes (default 0.7)
  --coverage F     stop once this share of a part is covered (default 0.97)
  --max-cubes N    cube cap per part (default 12)
  --colours N      palette size of the painted sheet (default 20)
  --yaw D          turn the model D degrees about y first; Blockbench mobs face north (-z) and glTF faces +z, so
                   GLB/GLTF default to 180, everything else to 0
  --min-bits N     after the cube budget, still add cubes for loose bits of at least N voxels (ears, horns); default 2
  --smooth N       majority filter size per face against speckle from photo textures; 0 = off (default 3)
  --parts JSON     {"joint name substring": "part", ...}, checked before the built-in rules
  --preview PNG    also write a before/after sheet (mesh on top, cubes below)

Needs: numpy, scipy, trimesh, pillow, pygltflib (for rigged GLB parts).
"""
import argparse
import base64
import io
import json
import math
import os
import sys
import uuid as uuidlib

import numpy as np
import trimesh
from PIL import Image, ImageDraw
from scipy import ndimage
from scipy.spatial import cKDTree

FACES = ("north", "east", "south", "west", "up", "down")


# ---------------------------------------------------------------- loading

def load(path):
    """(vertices, triangles, per-triangle uv (n,3,2) or None, texture image or None, per-triangle colour fallback,
    per-vertex joint names or None)."""
    scene = trimesh.load(path, process=False, force="scene")
    verts, tris, uvs, cols, joints = [], [], [], [], []
    tex = None
    off = 0
    rig = rig_labels(path)
    for name, nodes in scene.graph.geometry_nodes.items():
        g = scene.geometry[name]
        if not isinstance(g, trimesh.Trimesh):
            continue
        for node in nodes:
            m, _ = scene.graph.get(node)
            v = trimesh.transformations.transform_points(g.vertices, m)
            verts.append(v)
            tris.append(g.faces + off)
            off += len(v)
            vis = g.visual
            fc = np.full((len(g.faces), 4), 180, np.uint8)
            fu = None
            if vis.kind == "texture" and getattr(vis, "uv", None) is not None:
                mat = vis.material
                img = getattr(mat, "baseColorTexture", None) or getattr(mat, "image", None)
                if img is not None and tex is None:
                    tex = img.convert("RGBA")
                    fu = np.asarray(vis.uv)[g.faces]
                base = getattr(mat, "baseColorFactor", None)
                if base is not None:
                    fc[:] = np.asarray(base, np.uint8)
            elif vis.kind is not None:
                fc = np.asarray(vis.face_colors, np.uint8)
            uvs.append(fu if fu is not None else np.full((len(g.faces), 3, 2), np.nan))
            cols.append(fc)
            labels = rig.get(name) if rig else None
            joints.append(labels if labels is not None and len(labels) == len(v) else np.array(["body"] * len(v)))
    v = np.concatenate(verts)
    t = np.concatenate(tris)
    return v, t, np.concatenate(uvs), tex, np.concatenate(cols), np.concatenate(joints)


def rig_labels(path):
    """{mesh name: per-vertex dominant joint name} read straight from a GLB/GLTF skin, or {}."""
    if not path.lower().endswith((".glb", ".gltf")):
        return {}
    try:
        import pygltflib
    except ImportError:
        return {}
    G = pygltflib.GLTF2().load(path)
    if not G.skins:
        return {}
    blob = G.binary_blob()

    def acc(i):
        a = G.accessors[i]
        bv = G.bufferViews[a.bufferView]
        n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}[a.type]
        dt = {5121: np.uint8, 5123: np.uint16, 5125: np.uint32, 5126: np.float32}[a.componentType]
        start = (bv.byteOffset or 0) + (a.byteOffset or 0)
        stride = bv.byteStride or n * np.dtype(dt).itemsize
        raw = np.frombuffer(blob, np.uint8, count=stride * (a.count - 1) + n * np.dtype(dt).itemsize, offset=start)
        rows = np.lib.stride_tricks.as_strided(raw, (a.count, n * np.dtype(dt).itemsize), (stride, 1))
        return np.ascontiguousarray(rows).view(dt).reshape(a.count, n)

    out = {}
    for node in G.nodes:
        if node.mesh is None or node.skin is None:
            continue
        skin = G.skins[node.skin]
        names = [G.nodes[j].name or f"joint{j}" for j in skin.joints]
        mesh = G.meshes[node.mesh]
        per = []
        for p in mesh.primitives:
            if p.attributes.JOINTS_0 is None:
                return {}
            J = acc(p.attributes.JOINTS_0)
            W = acc(p.attributes.WEIGHTS_0).astype(np.float32)
            per.append(np.array([names[J[i, np.argmax(W[i])]] for i in range(len(J))]))
        lab = np.concatenate(per)
        out[mesh.name or f"mesh{node.mesh}"] = lab
        out[mesh.name] = lab
    # trimesh names geometry after the mesh; with one skinned mesh also accept any name
    if len(set(map(id, out.values()))) == 1:
        lab = next(iter(out.values()))
        return _Any(lab)
    return out


class _Any(dict):
    def __init__(self, lab):
        super().__init__()
        self.lab = lab

    def get(self, k, d=None):
        return self.lab

    def __bool__(self):
        return True


RULES = [  # (all substrings, part) checked in order on the lower-cased joint name
    (("head",), "head"), (("jaw",), "head"), (("ear",), "head"), (("neck",), "neck"), (("tail",), "tail"),
    (("left", "arm"), "left_arm"), (("left", "hand"), "left_arm"), (("left", "shoulder"), "left_arm"),
    (("right", "arm"), "right_arm"), (("right", "hand"), "right_arm"), (("right", "shoulder"), "right_arm"),
    (("left", "leg"), "left_leg"), (("left", "foot"), "left_leg"), (("left", "thigh"), "left_leg"),
    (("right", "leg"), "right_leg"), (("right", "foot"), "right_leg"), (("right", "thigh"), "right_leg"),
    (("wing", "left"), "left_wing"), (("wing", "right"), "right_wing"),
]


def part_of(joint, extra):
    j = joint.lower()
    for k, p in extra.items():
        if k.lower() in j:
            return p
    for keys, p in RULES:
        if all(k in j for k in keys):
            return p
    return "body"


# ---------------------------------------------------------------- surface samples

def surface(v, t, uv, tex, fcol, labels, n=250_000):
    """Random surface points with their colour and part label."""
    mesh = trimesh.Trimesh(v, t, process=False)
    pts, fi = trimesh.sample.sample_surface(mesh, n, seed=1)
    tri = v[t[fi]]
    bary = trimesh.triangles.points_to_barycentric(tri, pts)
    col = fcol[fi][:, :3].astype(np.float32)
    if tex is not None:
        fu = uv[fi]
        ok = ~np.isnan(fu[:, 0, 0])
        u = np.einsum("ij,ijk->ik", bary[ok], fu[ok])
        T = np.asarray(tex)
        x = np.clip((u[:, 0] % 1.0) * T.shape[1], 0, T.shape[1] - 1).astype(int)
        y = np.clip((1 - (u[:, 1] % 1.0)) * T.shape[0], 0, T.shape[0] - 1).astype(int)
        col[ok] = T[y, x, :3]
    corner = np.argmax(bary, axis=1)
    lab = labels[t[fi, corner]]
    return pts, col, lab


# ---------------------------------------------------------------- voxels

def inside(v, t, shape, pitch):
    """Majority vote of ray parity along x, y and z through every voxel centre."""
    votes = np.zeros(shape, np.int8)
    tri = v[t]
    for axis in range(3):
        a, b = [i for i in range(3) if i != axis]
        hits = [[[] for _ in range(shape[b])] for _ in range(shape[a])]
        for T in tri:
            P = T[:, [a, b]] / pitch - 0.5
            lo = np.floor(P.min(0)).astype(int)
            hi = np.ceil(P.max(0)).astype(int)
            d = (P[1, 0] - P[0, 0]) * (P[2, 1] - P[0, 1]) - (P[2, 0] - P[0, 0]) * (P[1, 1] - P[0, 1])
            if abs(d) < 1e-12:
                continue
            for i in range(max(lo[0], 0), min(hi[0], shape[a] - 1) + 1):
                for j in range(max(lo[1], 0), min(hi[1], shape[b] - 1) + 1):
                    w1 = ((P[1, 1] - P[2, 1]) * (i - P[2, 0]) + (P[2, 0] - P[1, 0]) * (j - P[2, 1])) / d
                    w2 = ((P[2, 1] - P[0, 1]) * (i - P[2, 0]) + (P[0, 0] - P[2, 0]) * (j - P[2, 1])) / d
                    w3 = 1 - w1 - w2
                    if min(w1, w2, w3) < -1e-9:
                        continue
                    hits[i][j].append((w1 * T[0, axis] + w2 * T[1, axis] + w3 * T[2, axis]) / pitch - 0.5)
        col = np.zeros(shape, bool)
        for i in range(shape[a]):
            for j in range(shape[b]):
                h = sorted(hits[i][j])
                for k in range(0, len(h) - 1, 2):
                    s, e = int(math.ceil(h[k])), int(math.floor(h[k + 1]))
                    idx = [slice(None)] * 3
                    idx[a], idx[b], idx[axis] = i, j, slice(max(s, 0), max(min(e + 1, shape[axis]), 0))
                    col[tuple(idx)] = True
        votes += col
    return votes >= 2


# ---------------------------------------------------------------- cube fitting

def fit(mask, fill, coverage, max_cubes, extra=6, min_bits=2):
    """Greedy boxes [(lo, hi)] in voxel indices (hi exclusive) covering mask."""
    remaining = mask.copy()
    total = mask.sum()
    boxes = []
    dist = ndimage.distance_transform_edt(np.pad(mask, 1))[1:-1, 1:-1, 1:-1]
    while len(boxes) < max_cubes + extra:
        if remaining.sum() <= (1 - coverage) * total or len(boxes) >= max_cubes:
            # past the budget, only keep chasing thin bits that stick out (ears, horns, fingers)
            lab, n = ndimage.label(remaining)
            sizes = ndimage.sum(remaining, lab, range(1, n + 1)) if n else []
            big = [i + 1 for i, s in enumerate(sizes) if s >= min_bits]
            if not big:
                break
            keep = np.isin(lab, big)
        else:
            keep = remaining
        score = np.where(keep, dist, -1)
        seed = np.unravel_index(np.argmax(score), mask.shape)
        lo, hi = list(seed), [s + 1 for s in seed]
        grown = True
        while grown:
            grown = False
            for ax in range(3):
                for side in (0, 1):
                    l, h = lo[:], hi[:]
                    if side == 0:
                        if l[ax] == 0:
                            continue
                        l[ax] -= 1
                        sl = [slice(l[i], h[i]) for i in range(3)]
                        sl[ax] = slice(l[ax], l[ax] + 1)
                    else:
                        if h[ax] == mask.shape[ax]:
                            continue
                        h[ax] += 1
                        sl = [slice(l[i], h[i]) for i in range(3)]
                        sl[ax] = slice(h[ax] - 1, h[ax])
                    slab = mask[tuple(sl)]
                    if slab.mean() >= fill:
                        lo, hi, grown = l, h, True
        sl = tuple(slice(lo[i], hi[i]) for i in range(3))
        if remaining[sl].sum() == 0:
            remaining[seed] = False
            continue
        remaining[sl] = False
        boxes.append((lo, hi))
    return boxes


# ---------------------------------------------------------------- faces / uv (same corner order as bbrender.py)

def quad(f, t, name):
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[name]


NORMAL = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_size(f, t, name):
    q = np.array(quad(f, t, name), float)
    return int(round(np.linalg.norm(q[1] - q[0]))), int(round(np.linalg.norm(q[3] - q[0])))


def texel_points(f, t, name, nu, nv):
    q = np.array(quad(f, t, name), float)
    s = (np.arange(nu) + 0.5) / nu
    r = (np.arange(nv) + 0.5) / nv
    S, R = np.meshgrid(s, r)
    top = q[0] + (q[1] - q[0]) * S[..., None]
    bot = q[3] + (q[2] - q[3]) * S[..., None]
    return top + (bot - top) * R[..., None]  # (nv, nu, 3)


def pack(rects):
    """Shelf packing of (w, h) -> positions, sheet size (power of two square)."""
    order = sorted(range(len(rects)), key=lambda i: -rects[i][1])
    area = sum(w * h for w, h in rects)
    size = 16
    while True:
        while size * size < area:
            size *= 2
        pos, x, y, shelf = {}, 0, 0, 0
        ok = True
        for i in order:
            w, h = rects[i]
            if w > size:
                ok = False
                break
            if x + w > size:
                x, y, shelf = 0, y + shelf, 0
            if y + h > size:
                ok = False
                break
            pos[i] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            return pos, size
        size *= 2


# ---------------------------------------------------------------- main conversion

def convert(path, height=16, pitch=1, fill=0.7, coverage=0.97, max_cubes=12, colours=20, yaw=None, parts=None, min_bits=2, smooth=3):
    if yaw is None:
        yaw = 180.0 if path.lower().endswith((".glb", ".gltf")) else 0.0
    v, t, uv, tex, fcol, joints = load(path)
    extra = parts or {}
    labels = np.array([part_of(j, extra) for j in joints])
    if yaw:
        a = math.radians(yaw)
        R = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        v = v @ R.T
    lo, hi = v.min(0), v.max(0)
    k = height / (hi[1] - lo[1])
    v = (v - [(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2]) * k
    pts, col, lab = surface(v, t, uv, tex, fcol, labels)

    origin = np.floor(v.min(0) / pitch) * pitch - pitch
    shape = tuple(int(s) for s in np.ceil((v.max(0) - origin) / pitch).astype(int) + 2)
    # inside test on a grid twice as fine; a voxel is solid when at least 2 of its 8 sub-voxels are, so thin legs
    # survive without fat bodies swelling
    fine = inside(v - origin, t, tuple(2 * s for s in shape), pitch / 2)
    solid = fine.reshape(shape[0], 2, shape[1], 2, shape[2], 2).sum(axis=(1, 3, 5)) >= 2
    sv = np.floor((pts - origin) / pitch).astype(int)
    sv = np.clip(sv, 0, np.array(shape) - 1)
    # surface voxels only fill in where the inside test found nothing next to them (thin ears, fins, blades), so
    # thick parts don't swell by half a voxel on every side
    skin = np.zeros(shape, bool)
    skin[sv[:, 0], sv[:, 1], sv[:, 2]] = True
    near_inside = ndimage.binary_dilation(solid)
    solid |= skin & ~near_inside

    # part of every voxel = part of the nearest surface sample
    tree = cKDTree(pts)
    idx = np.argwhere(solid)
    centres = origin + (idx + 0.5) * pitch
    _, near = tree.query(centres)
    names = sorted(set(lab))
    vox_part = np.full(shape, -1, int)
    vox_part[tuple(idx.T)] = [names.index(p) for p in lab[near]]

    groups = []
    for pi, pname in enumerate(names):
        mask = vox_part == pi
        if mask.sum() == 0:
            continue
        boxes = fit(mask, fill, coverage, max_cubes, min_bits=min_bits)
        # pivot = middle of the voxels this part shares a face with another part (where it bends), else its centre
        other = (vox_part >= 0) & (vox_part != pi)
        touch = mask & ndimage.binary_dilation(other, np.ones((3, 3, 3), bool))
        pv = np.argwhere(touch if touch.any() and pname != "body" else mask)
        pivot = origin + (pv.mean(0) + 0.5) * pitch
        groups.append((pname, pivot, [(origin + np.array(b[0]) * pitch, origin + np.array(b[1]) * pitch) for b in boxes]))

    # faces, culled where the same part's other cubes cover them
    rects, jobs = [], []
    for gi, (pname, pivot, boxes) in enumerate(groups):
        for bi, (f, tt) in enumerate(boxes):
            for name in FACES:
                nu, nv = face_size(f, tt, name)
                if nu == 0 or nv == 0:
                    continue
                P = texel_points(f, tt, name, nu, nv)
                probe = P + np.array(NORMAL[name]) * 0.5
                covered = np.zeros(P.shape[:2], bool)
                for oj, (of, ot) in enumerate(boxes):
                    if oj == bi:
                        continue
                    covered |= np.all((probe > of - 1e-6) & (probe < ot + 1e-6), axis=-1)
                if covered.all():
                    jobs.append((gi, bi, name, None, None))
                    continue
                jobs.append((gi, bi, name, len(rects), P))
                rects.append((nu, nv))
    pos, size = pack(rects)
    sheet = np.zeros((size, size, 4), np.uint8)
    for gi, bi, name, ri, P in jobs:
        if ri is None:
            continue
        x, y = pos[ri]
        nu, nv = rects[ri]
        # look a little inside the cube so thin parts pick their own colour
        q = P - np.array(NORMAL[name]) * min(0.5 * pitch, 0.5)
        _, near = tree.query(q.reshape(-1, 3), k=4)
        c = col[near].mean(1).reshape(nv, nu, 3)
        sheet[y:y + nv, x:x + nu, :3] = c
        sheet[y:y + nv, x:x + nu, 3] = 255
    img = Image.fromarray(sheet, "RGBA")
    rgb = img.convert("RGB").quantize(colors=colours, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    img = Image.merge("RGBA", (*rgb.split(), img.split()[3]))
    if smooth:
        # majority filter inside each face so photo-like textures lose their speckle and read as flat pixel shades
        from PIL import ImageFilter
        for ri, (x, y) in pos.items():
            w, h = rects[ri]
            if w >= 3 and h >= 3:
                box = (x, y, x + w, y + h)
                img.paste(img.crop(box).filter(ImageFilter.ModeFilter(smooth)), box)

    return build_bbmodel(os.path.splitext(os.path.basename(path))[0], groups, jobs, pos, rects, img, size)


def uid():
    return str(uuidlib.uuid4())


def r4(a):
    return [round(float(x), 4) for x in a]


def build_bbmodel(name, groups, jobs, pos, rects, img, size):
    elements, outliner, group_list = [], [], []
    faces_of = {}
    for gi, bi, fname, ri, _ in jobs:
        if ri is None:
            faces_of.setdefault((gi, bi), {})[fname] = {"uv": [0, 0, 0, 0], "texture": None}
        else:
            x, y = pos[ri]
            w, h = rects[ri]
            faces_of.setdefault((gi, bi), {})[fname] = {"uv": [x, y, x + w, y + h], "texture": 0}
    for gi, (pname, pivot, boxes) in enumerate(groups):
        children = []
        for bi, (f, t) in enumerate(boxes):
            e = {"name": f"{pname}_{bi}", "box_uv": False, "render_order": "default", "locked": False, "export": True,
                 "rescale": False, "from": r4(f), "to": r4(t), "autouv": 0, "color": gi % 8, "origin": r4(pivot),
                 "faces": faces_of[(gi, bi)], "type": "cube", "uuid": uid()}
            elements.append(e)
            children.append(e["uuid"])
        g = {"name": pname, "origin": r4(pivot), "rotation": [0, 0, 0], "color": gi % 8, "uuid": uid(),
             "export": True, "isOpen": False, "locked": False, "visibility": True, "autouv": 0}
        group_list.append(g)
        outliner.append({"uuid": g["uuid"], "isOpen": False, "children": children})
    buf = io.BytesIO()
    img.save(buf, "PNG")
    tex = {"name": f"{name}.png", "path": "", "folder": "", "namespace": "", "id": "0", "width": size, "height": size,
           "uv_width": size, "uv_height": size, "particle": False, "render_mode": "default", "render_sides": "auto",
           "visible": True, "internal": True, "saved": False, "uuid": uid(),
           "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}
    return {"meta": {"format_version": "5.0", "model_format": "free", "box_uv": False}, "name": name,
            "model_identifier": "", "visible_box": [1, 1, 0], "resolution": {"width": size, "height": size},
            "elements": elements, "groups": group_list, "outliner": outliner, "textures": [tex], "animations": []}


# ---------------------------------------------------------------- preview

def render_mesh(path, height, yaw, out_size=320, yaws=(30, 90, 150, 0), px=None):
    """Flat-shaded painter's render of the original mesh, in the same frame and camera as bbrender.py."""
    v, t, uv, tex, fcol, _ = load(path)
    if yaw:
        a = math.radians(yaw)
        R = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
        v = v @ R.T
    lo, hi = v.min(0), v.max(0)
    v = (v - [(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2]) * (height / (hi[1] - lo[1]))
    tri = v[t]
    col = fcol[:, :3].astype(float)
    if tex is not None:
        ok = ~np.isnan(uv[:, 0, 0])
        c = uv[ok].mean(1)
        T = np.asarray(tex)
        x = np.clip((c[:, 0] % 1) * T.shape[1], 0, T.shape[1] - 1).astype(int)
        y = np.clip((1 - c[:, 1] % 1) * T.shape[0], 0, T.shape[0] - 1).astype(int)
        col[ok] = T[y, x, :3]
    n = np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0])
    n /= np.linalg.norm(n, axis=1, keepdims=True) + 1e-12
    light = np.array([0.4, 0.8, -0.45])
    light /= np.linalg.norm(light)
    shade = 0.55 + 0.45 * np.abs(n @ light)
    sheet = Image.new("RGB", (out_size * len(yaws), out_size), (150, 170, 190))
    d = ImageDraw.Draw(sheet)
    for i, yw in enumerate(yaws):
        a, p = math.radians(yw), math.radians(-15)
        x, y, z = tri[..., 0], tri[..., 1] - 16 * height / 32, tri[..., 2]
        x, z = x * math.cos(a) - z * math.sin(a), x * math.sin(a) + z * math.cos(a)
        y, z = y * math.cos(p) - z * math.sin(p), y * math.sin(p) + z * math.cos(p)
        scale = px or out_size / (height * 2.2)
        X = out_size * i + out_size / 2 + x * scale
        Y = out_size * 0.55 - y * scale
        for k in np.argsort(-z.mean(1)):
            d.polygon(list(zip(X[k], Y[k])), fill=tuple(int(c) for c in col[k] * shade[k]))
    return sheet


def render_cubes(bb, height, out_size=320, yaws=(30, 90, 150, 0)):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import bbrender
    tmp = io.BytesIO()
    bbrender.render(bb, tmp, yaws=yaws, size=out_size, px=out_size / (height * 2.2), centre_y=16 * height / 32)
    tmp.seek(0)
    return Image.open(tmp).convert("RGB")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("mesh")
    ap.add_argument("out")
    ap.add_argument("--height", type=float, default=16)
    ap.add_argument("--pitch", type=float, default=1)
    ap.add_argument("--fill", type=float, default=0.7)
    ap.add_argument("--coverage", type=float, default=0.97)
    ap.add_argument("--max-cubes", type=int, default=12)
    ap.add_argument("--colours", type=int, default=20)
    ap.add_argument("--yaw", type=float, default=None)
    ap.add_argument("--min-bits", type=int, default=2)
    ap.add_argument("--smooth", type=int, default=3)
    ap.add_argument("--parts", default=None)
    ap.add_argument("--preview", default=None)
    a = ap.parse_args()
    parts = json.loads(a.parts) if a.parts else None
    bb = convert(a.mesh, a.height, a.pitch, a.fill, a.coverage, a.max_cubes, a.colours, a.yaw, parts, a.min_bits, a.smooth)
    with open(a.out, "w") as f:
        json.dump(bb, f)
    n = len(bb["elements"])
    print(f"{a.out}: {n} cubes in {len(bb['groups'])} parts ({', '.join(g['name'] for g in bb['groups'])}), "
          f"{bb['resolution']['width']}px sheet")
    if a.preview:
        yaw = a.yaw if a.yaw is not None else (180.0 if a.mesh.lower().endswith((".glb", ".gltf")) else 0.0)
        top = render_mesh(a.mesh, a.height, yaw)
        bot = render_cubes(bb, a.height)
        sheet = Image.new("RGB", (top.width, top.height + bot.height + 24), (40, 40, 48))
        sheet.paste(top, (0, 0))
        sheet.paste(bot, (0, top.height + 24))
        ImageDraw.Draw(sheet).text((8, top.height + 6), f"before: triangle mesh   |   after: {n} Blockbench cubes", fill=(230, 230, 230))
        sheet.save(a.preview)
        print("preview", a.preview)


if __name__ == "__main__":
    main()
