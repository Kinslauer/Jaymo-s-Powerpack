"""FaceParts (client/FaceParts.java): checks every face of every box lands on painted texels, and draws the before-and-after
preview of each look's head (the skin's marks over a plain tinted face, then the same with the boxes).
  python3 -I tools/face_parts_preview.py <kingdoms dir> <minecraft client jar> <out dir> [--check]
Paints nothing: it only reads the textures the table names.
"""
import io
import math
import os
import re
import sys
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

KD, JAR, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
CHECK = "--check" in sys.argv
RES = os.path.join(KD, "src", "main", "resources", "assets")
SRC = open(os.path.join(KD, "src", "client", "java", "com", "jaymoskingdoms", "client", "FaceParts.java")).read()
TABLE = re.search(r'TABLE = """(.*?)""";', SRC, re.S).group(1)
jar = zipfile.ZipFile(JAR)
_cache = {}


def texture(ident):
    if ident not in _cache:
        ns, path = ident.split(":")
        if ns == "minecraft":
            _cache[ident] = Image.open(io.BytesIO(jar.read("assets/minecraft/" + path))).convert("RGBA")
        else:
            _cache[ident] = Image.open(os.path.join(RES, ns, path)).convert("RGBA")
    return _cache[ident]


def parse():
    looks, key = {}, None
    for raw in TABLE.split("\n"):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        t = line.split()
        if t[0].startswith("@"):
            key = t[0][1:]
            tex, size = None, (64, 64)
            for o in t[1:]:
                if o.startswith("tex="):
                    tex = o[4:]
                elif o.startswith("size="):
                    size = tuple(int(x) for x in o[5:].split(","))
            looks[key] = {"tex": tex, "size": size, "pieces": []}
            continue
        f = [float(x) for x in t[2:14]]
        p = {"id": t[0], "pair": t[1] == "1", "box": f[:6], "pivot": f[6:9], "rot": f[9:12], "tex": looks[key]["tex"],
             "size": looks[key]["size"], "uv": (0, 0), "tint": None, "s": (1, 1, 1)}
        for o in t[14:]:
            k, v = o.split("=", 1)
            if k == "uv":
                p["uv"] = tuple(int(x) for x in v.split(","))
            elif k == "tex":
                p["tex"] = v
            elif k == "size":
                p["size"] = tuple(int(x) for x in v.split(","))
            elif k == "s":
                p["s"] = tuple(float(x) for x in v.split(","))
            elif k == "tint":
                p["tint"] = tuple(int(v[i:i + 2], 16) for i in (0, 2, 4))
        looks[key]["pieces"].append(p)
    return looks


def faces(u, v, w, h, d):
    """Vanilla box UV: the unwrap of a w x h x d box at (u, v); sizes rounded the way ModelPart does (floor of dims)."""
    w, h, d = int(w), int(h), int(d)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def sample(img, p):
    """The image scaled to the size the model says (vanilla scales uv by texture size / model size)."""
    W, H = p["size"]
    if img.size != (W, H):
        img = img.resize((W, H), Image.NEAREST)
    return img


def check(looks):
    bad = 0
    for key, look in looks.items():
        for p in look["pieces"]:
            img = sample(texture(p["tex"]), p)
            x, y, z, w, h, d = p["box"]
            for face, (fx, fy, fw, fh) in faces(*p["uv"], w, h, d).items():
                holes = sum(1 for j in range(fh) for i in range(fw)
                            if not (0 <= fx + i < img.width and 0 <= fy + j < img.height) or img.getpixel((fx + i, fy + j))[3] < 26)
                if holes:
                    bad += 1
                    print("%-12s %-9s %-6s %d of %d texels clear" % (key, p["id"], face, holes, fw * fh))
    print("faces with holes:", bad)
    return bad


# ---- drawing (the same quad renderer as the Bio-built forms' preview)

def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def mat(t=(0, 0, 0), r=(0, 0, 0), s=(1, 1, 1)):
    m = np.eye(4)
    m[:3, :3] = rot(*r) @ np.diag(s)
    m[:3, 3] = t
    return m


def box(quads, m, tex, u, v, x, y, z, w, h, d, mirror=False, tint=None):
    for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        x0, y0, z0, x1, y1, z1 = x, y, z, x + w, y + h, z + d
        for j in range(fh):
            for i in range(fw):
                ii = fw - 1 - i if mirror else i
                if not (0 <= fx + ii < tex.width and 0 <= fy + j < tex.height):
                    continue
                c = tex.getpixel((fx + ii, fy + j))
                if c[3] < 26:
                    continue
                if tint:
                    c = tuple(int(c[k] * tint[k] / 255) for k in range(3)) + (255,)
                a, b, p, q = i / fw, (i + 1) / fw, j / fh, (j + 1) / fh
                L = lambda s, t0, t1: t0 + (t1 - t0) * s
                if face == "front":
                    pts, n = [(L(a, x0, x1), L(p, y0, y1), z0), (L(b, x0, x1), L(p, y0, y1), z0), (L(b, x0, x1), L(q, y0, y1), z0), (L(a, x0, x1), L(q, y0, y1), z0)], (0, 0, -1)
                elif face == "back":
                    pts, n = [(L(a, x1, x0), L(p, y0, y1), z1), (L(b, x1, x0), L(p, y0, y1), z1), (L(b, x1, x0), L(q, y0, y1), z1), (L(a, x1, x0), L(q, y0, y1), z1)], (0, 0, 1)
                elif face == ("left" if mirror else "right"):
                    pts, n = [(x0, L(p, y0, y1), L(a, z1, z0)), (x0, L(p, y0, y1), L(b, z1, z0)), (x0, L(q, y0, y1), L(b, z1, z0)), (x0, L(q, y0, y1), L(a, z1, z0))], (-1, 0, 0)
                elif face == ("right" if mirror else "left"):
                    pts, n = [(x1, L(p, y0, y1), L(a, z0, z1)), (x1, L(p, y0, y1), L(b, z0, z1)), (x1, L(q, y0, y1), L(b, z0, z1)), (x1, L(q, y0, y1), L(a, z0, z1))], (1, 0, 0)
                elif face == "top":
                    pts, n = [(L(a, x0, x1), y0, L(p, z1, z0)), (L(b, x0, x1), y0, L(p, z1, z0)), (L(b, x0, x1), y0, L(q, z1, z0)), (L(a, x0, x1), y0, L(q, z1, z0))], (0, -1, 0)
                else:
                    pts, n = [(L(a, x0, x1), y1, L(p, z0, z1)), (L(b, x0, x1), y1, L(p, z0, z1)), (L(b, x0, x1), y1, L(q, z0, z1)), (L(a, x0, x1), y1, L(q, z0, z1))], (0, 1, 0)
                P = np.array([list(pt) + [1] for pt in pts]) @ m.T
                quads.append((P[:, :3], c, m[:3, :3] @ np.array(n)))


def render(quads, yaw, size=(230, 230), scale=15.0):
    img = Image.new("RGB", size, (150, 186, 226))
    dr = ImageDraw.Draw(img)
    cy, sy = math.cos(yaw), math.sin(yaw)
    light = np.array([-0.3, -0.8, -0.5])
    light /= np.linalg.norm(light)
    out = []
    for P, c, N in quads:
        X = P[:, 0] * cy + P[:, 2] * sy
        Z = -P[:, 0] * sy + P[:, 2] * cy
        if -N[0] * sy + N[2] * cy > 0.05:
            continue
        lit = 0.6 + 0.4 * max(0.0, float(N @ light))
        col = tuple(int(min(255, ch * lit)) for ch in c[:3])
        pts = [(size[0] / 2 + X[k] * scale, size[1] / 2 + (P[k, 1] + 3) * scale) for k in range(4)]
        out.append((Z.mean(), pts, col))
    out.sort(key=lambda t: -t[0])
    for _, pts, col in out:
        dr.polygon(pts, fill=col)
    return img


def skin_for(key):
    """The look's own painted marks: variant/<key>_skin.png, or the heritage's first look."""
    base = os.path.join(RES, "jaymoskingdoms", "textures", "entity", "heritage")
    for path in (os.path.join(base, "variant", key + "_skin.png"), os.path.join(base, key.lstrip("*") + "_skin.png")):
        if os.path.exists(path):
            return Image.open(path).convert("RGBA")
    return None


def tint_of(key):
    hv = open(os.path.join(KD, "src", "main", "java", "com", "jaymoskingdoms", "HeritageVariants.java")).read()
    m = re.search(r"\b%s\(Heritage\.Kind\.\w+, (0x[0-9A-Fa-f]+|0), ([0-9.]+)F" % re.escape(key.lstrip("*").upper()), hv)
    base = np.array([200, 150, 115])
    if not m or m.group(1) == "0":
        return tuple(base)
    c = int(m.group(1), 16)
    c = np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255])
    a = float(m.group(2))
    return tuple(int(x) for x in base * (1 - a) + c * a)


def head(key, look, parts):
    face = Image.new("RGBA", (64, 64), tint_of(key) + (255,))
    skin = skin_for(key)
    if skin is not None:
        face.alpha_composite(skin.resize((64, 64), Image.NEAREST))
    quads = []
    box(quads, np.eye(4), face, 0, 0, -4, -8, -4, 8, 8, 8)
    if parts:
        for p in look["pieces"]:
            img = sample(texture(p["tex"]), p)
            for mirror in ((False, True) if p["pair"] else (False,)):
                s = -1 if mirror else 1
                px, py, pz = p["pivot"]
                rx, ry, rz = (math.radians(a) for a in p["rot"])
                m = mat(t=(px * s, py, pz), r=(rx, ry * s, rz * s), s=p["s"])
                x, y, z, w, h, d = p["box"]
                box(quads, m, img, *p["uv"], -(x + w) if mirror else x, y, z, w, h, d, mirror, p["tint"])
    return quads


def main():
    looks = parse()
    bad = check(looks)
    if CHECK:
        sys.exit(1 if bad else 0)
    os.makedirs(OUT, exist_ok=True)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 15)
    except OSError:
        font = ImageFont.load_default()
    cells = []
    for key, look in looks.items():
        before = render(head(key, look, False), math.radians(-35))
        after = render(head(key, look, True), math.radians(-35))
        side = render(head(key, look, True), math.radians(-80))
        cell = Image.new("RGB", (before.width * 3 + 8, before.height + 26), (30, 30, 36))
        cell.paste(before, (0, 26))
        cell.paste(after, (before.width + 4, 26))
        cell.paste(side, (before.width * 2 + 8, 26))
        name = key.lstrip("*").replace("_", " ").title() + (" (first look)" if key.startswith("*") else "")
        ImageDraw.Draw(cell).text((6, 4), name + ":  before  |  after  |  side", fill=(235, 235, 235), font=font)
        cells.append(cell)
    cols = 3
    rows = (len(cells) + cols - 1) // cols
    W, H = cells[0].width, cells[0].height
    sheet = Image.new("RGB", (cols * (W + 10), rows * (H + 10)), (20, 20, 24))
    for i, c in enumerate(cells):
        sheet.paste(c, ((i % cols) * (W + 10), (i // cols) * (H + 10)))
    sheet.save(os.path.join(OUT, "face-parts-before-after.png"))
    print("ok", len(cells))


if __name__ == "__main__":
    main()
