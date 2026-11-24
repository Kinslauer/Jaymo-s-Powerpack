"""Draws the big body's new head beside the old head and the head it came from, for checking it before it goes in game.

    python3 -I tools/preview_bighead.py OUT.png

A small z-buffer box renderer of Minecraft's model parts (a part's pivot, its Z-Y-X rotation and its scale, each cube's
box unwrap and mirror, as ModelPart.Cube builds them). Three things are read from what the game will use, not retyped:
  * the SOURCE head: tools/data/bighead_geometry.json (the original's cubes, recovered from the jar) and its texture;
  * the NEW head: client/power/web/WebBigHeadMesh.java (the generated Java), placed with WebBigHead's SCALE, OFFSET_Y and
    OFFSET_Z, and the texture in assets;
  * the OLD head and the body: WebHulkMesh.java (the big body, in WebHulk.shift's frame at full growth).
Columns: source head | old head | new head, each from the front and the side, jaw closed and open; then the new head on
the whole big body, front and side. (A preview, not the game: no lighting but a fixed shade, no first-person.)
"""
import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

TOOLS = Path(__file__).resolve().parent
PROJECT = TOOLS.parent
WEB = PROJECT / "src" / "client" / "java" / "com" / "jaymospowers" / "client" / "power" / "web"
ENTITY = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"
DATA = TOOLS / "data"
JAW_OPEN = 0.5  # radians, for the "open" pictures (the big body hangs it 0.22 at rest, 0.72 in a snarl)


class Part:
    def __init__(self, name, cubes, pos, rot):
        self.name, self.cubes, self.pos, self.rot = name, cubes, list(pos), list(rot)
        self.scale = [1.0, 1.0, 1.0]
        self.children = []
        self.visible = True


# ---- reading the Java meshes -----------------------------------------------------------------------------------------
FLOAT = r"(-?[\d.]+)F"


def parse_java(path):
    """The parts of a generated mesh: {name: Part}, in the tree the Java builds (parent chain by variable name)."""
    text = path.read_text(encoding="utf-8")
    parts, variables = {}, {}
    root = Part("root", [], (0, 0, 0), (0, 0, 0))
    variables["root"] = root
    for m in re.finditer(r'(?:PartDefinition (\w+) = )?(\w+)\.addOrReplaceChild\("(\w+)", (CubeListBuilder\.create\(\).*?), (PartPose\.[^;]*?)\);', text):
        var, parent, name, cubes_src, pose_src = m.groups()
        cubes, mirror = [], False
        for t in re.finditer(r"\.mirror\((false)?\)|\.texOffs\((\d+), (\d+)\)\.addBox\(([^)]*), new CubeDeformation\(" + FLOAT + r"\)\)", cubes_src):
            if t.group(0).startswith(".mirror"):
                mirror = t.group(1) is None
                continue
            box = [float(x.rstrip("F")) for x in t.group(4).split(", ")]
            cubes.append(dict(uv=(int(t.group(2)), int(t.group(3))), mirror=mirror, box=box, grow=float(t.group(5))))
        pos, rot = (0, 0, 0), (0, 0, 0)
        nums = [float(x) for x in re.findall(FLOAT, pose_src)]
        if "offsetAndRotation" in pose_src:
            pos, rot = nums[:3], nums[3:]
        elif "offset" in pose_src:
            pos = nums
        part = Part(name, cubes, pos, rot)
        # a name can repeat under different parents (elbow, knee): the variable names the one that's meant
        owner = variables[parent]
        owner.children.append(part)
        if var:
            variables[var] = part
        parts[(owner.name, name)] = part
    return root


def parse_source(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    by = {}
    for p in data["parts"]:
        by[p["name"]] = Part(p["name"], p["cubes"], p["pos"] if p["name"] != "head" else (0, 0, 0), p["rot"])
    root = Part("root", [], (0, 0, 0), (0, 0, 0))
    for p in data["parts"]:
        (by[p["parent"]] if p["parent"] else root).children.append(by[p["name"]])
    return root


def find(part, name):
    for c in part.children:
        if c.name == name:
            return c
        found = find(c, name)
        if found:
            return found
    return None


# ---- drawing -------------------------------------------------------------------------------------------------------------
def rot_matrix(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def cube_quads(c):
    """ModelPart.Cube's six polygons: (vertices, (u0, v0, u1, v1) in texels), in the order the game builds them."""
    x, y, z, w, h, d = c["box"]
    g = c["grow"]
    x0, y0, z0, x1, y1, z1 = x - g, y - g, z - g, x + w + g, y + h + g, z + d + g
    if c["mirror"]:
        x0, x1 = x1, x0
    v7, v0_, v1_, v2 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v3, v4, v5, v6 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v = c["uv"]
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    quads = [
        ([v4, v0_, v1_, v5], (f6, f11, f8, f12)),  # east
        ([v7, v3, v6, v2], (f4, f11, f5, f12)),  # west
        ([v4, v3, v7, v0_], (f5, f10, f6, f11)),  # down
        ([v1_, v2, v6, v5], (f6, f11, f7, f10)),  # up
        ([v0_, v7, v2, v1_], (f5, f11, f6, f12)),  # north
        ([v3, v4, v5, v6], (f8, f11, f9, f12)),  # south
    ]
    out = []
    for verts, (a, b, c2, d2) in quads:
        uvs = [(c2, b), (a, b), (a, d2), (c2, d2)]
        if c["mirror"]:
            verts, uvs = verts[::-1], uvs[::-1]
        out.append((verts, uvs))
    return out


def collect(part, parent_m, parent_t, out, skip=()):
    if not part.visible or part.name in skip:
        return
    m = parent_m @ rot_matrix(*part.rot) @ np.diag(part.scale)
    t = parent_m @ np.array(part.pos) + parent_t
    for c in part.cubes:
        for verts, uvs in cube_quads(c):
            out.append(([m @ np.array(v) + t for v in verts], uvs))
    for child in part.children:
        collect(child, m, t, out, skip)


def render(polys, tex, tex_size, view, size=300, zoom=10.0, centre=(0, 0)):
    """Orthographic. view 'front': screen x = model x, depth -z toward the viewer; 'side': screen x = -z, depth x."""
    img = np.zeros((size, size, 4), np.uint8)
    img[:] = (46, 48, 58, 255)
    zbuf = np.full((size, size), 1e9)
    tw, th = tex.size
    texa = np.asarray(tex)
    for verts, uvs in polys:
        pts = []
        for p in verts:
            sx, sy = (p[0], p[1]) if view == "front" else (-p[2], p[1])
            depth = p[2] if view == "front" else p[0]
            pts.append((size / 2 + (sx - centre[0]) * zoom, size / 2 + (sy - centre[1]) * zoom, depth))
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (pts[i] for i in tri)
            ua, ub, uc = (uvs[i] for i in tri)
            minx, maxx = int(max(0, min(a[0], b[0], c[0]))), int(min(size - 1, max(a[0], b[0], c[0])))
            miny, maxy = int(max(0, min(a[1], b[1], c[1]))), int(min(size - 1, max(a[1], b[1], c[1])))
            if minx > maxx or miny > maxy:
                continue
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            l1 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
            l2 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
            l3 = 1 - l1 - l2
            inside = (l1 >= -0.01) & (l2 >= -0.01) & (l3 >= -0.01)
            if not inside.any():
                continue
            depth = l1 * a[2] + l2 * b[2] + l3 * c[2]
            u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0]
            v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1]
            px = np.clip((u / tex_size[0] * tw).astype(int), 0, tw - 1)
            py = np.clip((v / tex_size[1] * th).astype(int), 0, th - 1)
            colour = texa[py, px]
            ok = inside & (colour[..., 3] > 20) & (depth < zbuf[miny:maxy + 1, minx:maxx + 1])
            sub = img[miny:maxy + 1, minx:maxx + 1]
            sub[ok] = colour[ok]
            zbuf[miny:maxy + 1, minx:maxx + 1][ok] = depth[ok]
    return Image.fromarray(img)


def lit(tex):
    """The head's art lies in near-black: lift it for the picture (the game's lighting would brighten it too)."""
    a = np.asarray(tex.convert("RGBA")).astype(float)
    a[..., :3] = np.clip(a[..., :3] * 1.0 + 28, 0, 255)
    return Image.fromarray(a.astype(np.uint8))


def head_polys(root, jaw_open, scale=1.0, offset=(0, 0, 0), skip=()):
    jaw = find(root, "jaw")
    if jaw:
        jaw.rot[0] = jaw_open
    top = find(root, "head") or root
    top.scale = [scale] * 3
    top.pos = list(offset)
    out = []
    collect(top, np.eye(3), np.zeros(3), out, skip)
    return out


def constants():
    text = (WEB / "WebBigHead.java").read_text(encoding="utf-8")
    m = re.search(r"SCALE = " + FLOAT + r", OFFSET_Y = " + FLOAT + r", OFFSET_Z = " + FLOAT, text)
    return [float(x) for x in m.groups()]


def label(img, text):
    ImageDraw.Draw(img).text((6, 4), text, fill=(235, 235, 240, 255))
    return img


def main(out_path):
    scale, oy, oz = constants()
    source = parse_source(DATA / "bighead_geometry.json")
    new = parse_java(WEB / "WebBigHeadMesh.java")
    # the generated Java must be the recovered geometry, cube for cube
    src_cubes = [(c["uv"], tuple(c["box"]), c["mirror"]) for p in json.loads((DATA / "bighead_geometry.json").read_text())["parts"] for c in p["cubes"]]
    java_cubes = []

    def walk(p):
        java_cubes.extend((c["uv"], tuple(c["box"]), c["mirror"]) for c in p.cubes)
        for c in p.children:
            walk(c)
    walk(new)
    assert len(java_cubes) == len(src_cubes) == 76
    assert sorted((tuple(uv), tuple(b), m) for uv, b, m in java_cubes) == sorted((tuple(uv), tuple(b), m) for uv, b, m in src_cubes),         "generated Java differs from the recovered cubes"
    old_root = parse_java(WEB / "WebHulkMesh.java")
    tex_new = lit(Image.open(ENTITY / "web_symbiote_bighead.png"))
    tex_hulk = lit(Image.open(ENTITY / "web_symbiote_hulk.png"))
    tex_src = lit(Image.open(DATA / "bighead_source.png"))
    glow = Image.open(ENTITY / "web_symbiote_bighead_glow.png").convert("RGBA")
    tiles = []
    for jaw_open in (0.22, 0.72):
        for view in ("front", "side"):
            row = []
            # source head: the original's own size and pivot (units x0.6, the same as ours, to compare shapes)
            polys = head_polys(parse_source(DATA / "bighead_geometry.json"), jaw_open, scale)
            row.append(label(render(polys, tex_src, (256, 192), view, zoom=11, centre=(0, 0)), "source %s jaw %.2f" % (view, jaw_open)))
            old = parse_java(WEB / "WebHulkMesh.java")
            hh = find(old, "head")
            find(old, "jaw").rot[0] = jaw_open
            polys = []
            keep = {"skull", "cranium", "muzzle", "fangs", "fangs_r", "fangs_l", "throat", "jaw"}
            for c in hh.children:
                if c.name in keep:
                    collect(c, np.eye(3), np.zeros(3), polys)
            row.append(label(render(polys, tex_hulk, (256, 128), view, zoom=11, centre=(0, -2)), "old head %s" % view))
            polys = head_polys(parse_java(WEB / "WebBigHeadMesh.java"), jaw_open, scale, (0, oy, oz))
            img = render(polys, tex_new, (256, 192), view, zoom=11, centre=(0, -2))
            # (the glow: eyes and fang glints, drawn bright over it)
            img2 = render(polys, glow, (256, 192), view, zoom=11, centre=(0, -2))
            g = np.asarray(img2).astype(int)
            base = np.asarray(img).astype(int)
            mask = (g[..., :3].sum(axis=2) > 120) & (g[..., :3].sum(axis=2) != 46 + 48 + 58)
            base[mask] = np.clip(base[mask] + g[mask] * 2, 0, 255)  # additive, as the eyes render type is
            row.append(label(Image.fromarray(base.astype(np.uint8)), "new head %s (scale %.2f)" % (view, scale)))
            tiles.append(row)
    # the whole big body in WebHulk.shift's frame, with the new head on the neck
    body = parse_java(WEB / "WebHulkMesh.java")
    h_pos = (0.0, -6.0 - 1.5, -4.0)  # the neck (BODY_LIFT up) and the head HEAD_DOWN/HEAD_FORWARD from it
    shift = {"body": (0, -6, 0), "right_arm": (-10, -4, 0), "left_arm": (10, -4, 0), "right_leg": (-4.1, 8, 0), "left_leg": (4.1, 8, 0)}
    polys = []
    for c in body.children:
        if c.name == "head":
            continue
        c.pos = list(shift.get(c.name, c.pos))
        if c.name == "body":
            c.scale = [1, 1, 1]
        collect(c, np.eye(3), np.zeros(3), polys)
    head = head_polys(parse_java(WEB / "WebBigHeadMesh.java"), 0.22, scale, (0, oy, oz))
    moved = [([np.array(v) + np.array(h_pos) for v in verts], uvs) for verts, uvs in head]
    bodies = []
    for view in ("front", "side"):
        a = render(polys, tex_hulk, (256, 128), view, size=420, zoom=9, centre=(0, 2))
        b = render(moved, tex_new, (256, 192), view, size=420, zoom=9, centre=(0, 2))
        arr = np.asarray(a).copy()
        barr = np.asarray(b)
        # the head over the body (a z-buffer of one: head wins where it has pixels that aren't the background)
        mask = (barr[..., :3] != (46, 48, 58)).any(axis=2)
        arr[mask] = barr[mask]
        bodies.append(label(Image.fromarray(arr), "new head on the big body, %s" % view))
    w = 300 * 3
    sheet = Image.new("RGBA", (max(w, 420 * 2), 300 * 4 + 420), (20, 20, 24, 255))
    for r, row in enumerate(tiles):
        for c, tile in enumerate(row):
            sheet.paste(tile, (c * 300, r * 300))
    for i, b in enumerate(bodies):
        sheet.paste(b, (i * 420, 1200))
    sheet.convert("RGB").save(out_path)
    print("wrote", out_path)


if __name__ == "__main__":
    main(sys.argv[1])
