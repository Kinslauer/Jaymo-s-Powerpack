"""Draws a contact sheet of a Blockbench animated model: one row per animation, a few moments along each.

    python3 tools/blockbench/bbmodel_sheet.py art/boxing/arrow_slit.bbmodel out.png [yaw]

It reads only the .bbmodel (bones, cubes, keyframes), so it shows what Blockbench will play: a check that a project
says what it should, without opening Blockbench. Plain flat shading; the face is drawn dark so it shows which way the model looks. [yaw]: degrees round from
straight on (default 35).
"""
import json, math, sys
from PIL import Image, ImageDraw, ImageFont


def rot(rx, ry, rz):
    """Blockbench's bone rotation (degrees): Z, then Y, then X, as a 3x3 row-major matrix."""
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    ca, sa, cb, sb, cc, sc = math.cos(a), math.sin(a), math.cos(b), math.sin(b), math.cos(c), math.sin(c)
    X = [[1, 0, 0], [0, ca, -sa], [0, sa, ca]]
    Y = [[cb, 0, sb], [0, 1, 0], [-sb, 0, cb]]
    Z = [[cc, -sc, 0], [sc, cc, 0], [0, 0, 1]]
    return mm(mm(Z, Y), X)


def mm(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(A, v):
    return [sum(A[i][k] * v[k] for k in range(3)) for i in range(3)]


def sample(kfs, t):
    """Linear between the keys either side of {@code t} (seconds)."""
    if not kfs:
        return None
    kfs = sorted(kfs, key=lambda k: k[0])
    if t <= kfs[0][0]:
        return kfs[0][1]
    for (t0, v0), (t1, v1) in zip(kfs, kfs[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return kfs[-1][1]


def main():
    path, out = sys.argv[1], sys.argv[2]
    d = json.load(open(path))
    cubes = {e["uuid"]: e for e in d["elements"]}
    tex_color = {}
    bones = {}

    def walk(node, parent):
        bones[node["uuid"]] = (node, parent)
        for ch in node["children"]:
            if isinstance(ch, dict):
                walk(ch, node["uuid"])

    for n in d["outliner"]:
        walk(n, None)
    colors = {"head": (198, 140, 104), "chest": (60, 92, 128), "belly": (52, 82, 116), "upper arm": (60, 92, 128),
              "forearm": (198, 140, 104), "hand wrap": (226, 214, 188), "thigh": (54, 58, 74), "shin": (40, 36, 34),
              "elbow": (60, 92, 128), "knee": (54, 58, 74), "waist joint": (60, 92, 128)}
    anims = d["animations"]
    old = [int(x) for x in str(d["meta"].get("format_version", "5.0")).split(".")[:2]] < [5, 0]
    cols, cw, ch = 5, 170, 210
    img = Image.new("RGB", (cols * cw + 150, len(anims) * ch), (236, 232, 222))
    draw = ImageDraw.Draw(img)
    yaw = float(sys.argv[3]) if len(sys.argv) > 3 else 35.0
    view = mm(rot(-12, 0, 0), rot(0, yaw, 0))
    for row, anim in enumerate(anims):
        draw.text((6, row * ch + ch // 2 - 6), anim["name"].split(".", 1)[1], fill=(30, 30, 30))
        length = float(anim["length"])
        tracks = {}
        for bu, an in anim["animators"].items():
            for k in an["keyframes"]:
                p = k["data_points"][0]
                v = [float(p["x"]), float(p["y"]), float(p["z"])]
                # as Blockbench 5 opens a project older than format 5.0: rotation X and Y and position X flipped
                if old and k["channel"] in ("rotation", "position"):
                    v[0] = -v[0]
                    if k["channel"] == "rotation":
                        v[1] = -v[1]
                tracks.setdefault((bu, k["channel"]), []).append((float(k["time"]), v))
        for col in range(cols):
            t = length * col / (cols - 1)
            ox, oy = 150 + col * cw + cw // 2, row * ch + ch - 20

            def world(bu):
                node, parent = bones[bu]
                r = sample(tracks.get((bu, "rotation")), t) or [0, 0, 0]
                p = sample(tracks.get((bu, "position")), t) or [0, 0, 0]
                R = rot(*r)
                piv = node["origin"]

                def f(v):
                    q = mv(R, [v[i] - piv[i] for i in range(3)])
                    q = [q[i] + piv[i] + p[i] for i in range(3)]
                    return world(parent)(q) if parent else q
                return f

            polys = []
            for bu, (node, _) in bones.items():
                W = world(bu)
                for ch_ in node["children"]:
                    if isinstance(ch_, dict):
                        continue
                    e = cubes[ch_]
                    g = e.get("inflate", 0)
                    a = [e["from"][i] - g for i in range(3)]
                    b = [e["to"][i] + g for i in range(3)]
                    corners = [[(a, b)[i >> 2 & 1][0], (a, b)[i >> 1 & 1][1], (a, b)[i & 1][2]] for i in range(8)]
                    pts = [mv(view, W(c)) for c in corners]
                    for fi, face in enumerate(((0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5))):
                        q = [pts[i] for i in face]
                        z = sum(p_[2] for p_ in q) / 4
                        u = [q[1][i] - q[0][i] for i in range(3)]
                        v = [q[3][i] - q[0][i] for i in range(3)]
                        n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
                        ln = math.sqrt(sum(x * x for x in n)) or 1
                        shade = 0.55 + 0.45 * abs(n[0] * 0.3 + n[1] * 0.6 + n[2] * 0.74) / ln
                        base = colors.get(e["name"], (150, 150, 150))
                        if e["name"] == "head" and fi == 4:
                            base = (40, 40, 44)  # the face (north, -Z): which way it looks
                        col_ = tuple(int(c * shade) for c in base)
                        polys.append((z, [(ox - p_[0] * 5, oy - p_[1] * 5) for p_ in q], col_))
            # the camera is out front (at -Z) looking back: the far faces (largest z) first
            for z, poly, col_ in sorted(polys, key=lambda x: -x[0]):
                draw.polygon(poly, fill=col_, outline=(0, 0, 0))
            draw.text((ox - 18, row * ch + 4), "%.2fs" % t, fill=(90, 90, 90))
    img.save(out)


if __name__ == "__main__":
    main()
