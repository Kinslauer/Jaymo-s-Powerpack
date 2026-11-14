"""Software preview of a parsed Java model with its texture (orthographic, z-buffer, nearest sampling)."""
import numpy as np
from PIL import Image
import a2parse

def rmat(r):
    x, y, z = r
    cx, sx, cy, sy, cz, sz = np.cos(x), np.sin(x), np.cos(y), np.sin(y), np.cos(z), np.sin(z)
    return np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]) @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])

def world(model, hide=(), pose=None):
    """List of quads: (4x3 points, 4x2 uv, normal)."""
    parts = model["parts"]; cache = {}
    def xf(name):
        if name in cache: return cache[name]
        p = parts[name]
        rot = np.array(p["rot"], float); pos = np.array(p["pos"], float)
        if pose and name in pose:
            rot = rot + np.array(pose[name].get("rot", [0, 0, 0])); pos = pos + np.array(pose[name].get("pos", [0, 0, 0]))
        R = rmat(rot); T = pos
        if p["parent"]:
            PR, PT = xf(p["parent"]); R, T = PR @ R, PR @ T + PT
        cache[name] = (R, T); return cache[name]
    quads = []
    for name in model["order"]:
        if any(h in name for h in hide): continue
        p = parts[name]; R, T = xf(name)
        for b in p["boxes"]:
            g = b["grow"]
            x0, y0, z0 = b["x"] - g, b["y"] - g, b["z"] - g
            x1, y1, z1 = b["x"] + b["w"] + g, b["y"] + b["h"] + g, b["z"] + b["d"] + g
            f = a2parse.faces(b)
            # each face: 4 corners in order (u0v0, u1v0, u1v1, u0v1)
            defs = {
                "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
                "south": [(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)],
                "west": [(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)],
                "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
                "top": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
                "bottom": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            }
            for face, pts in defs.items():
                u, v, w, h = f[face]
                if w == 0 or h == 0: continue
                P = np.array(pts, float)
                if b["mirror"]:
                    # mirrored boxes flip the texture across x
                    P = P.copy(); P[:, 0] = (x0 + x1) - P[:, 0]
                    if face in ("west", "east"):
                        pass
                uv = np.array([(u, v), (u + w, v), (u + w, v + h), (u, v + h)], float)
                W = P @ R.T + T
                quads.append((W, uv))
    return quads

def render(model, tex, yaw=35, pitch=20, size=360, hide=(), pose=None, bg=(0, 0, 0, 0), scale=None):
    quads = world(model, hide, pose)
    ya, pi = np.radians(yaw), np.radians(pitch)
    Ry = np.array([[np.cos(ya), 0, np.sin(ya)], [0, 1, 0], [-np.sin(ya), 0, np.cos(ya)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(pi), -np.sin(pi)], [0, np.sin(pi), np.cos(pi)]])
    V = Rx @ Ry
    allp = np.concatenate([q[0] for q in quads]) @ V.T
    mn, mx = allp.min(0), allp.max(0); ext = max(mx[0] - mn[0], mx[1] - mn[1])
    s = scale or (size * 0.9 / ext); cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    img = np.zeros((size, size, 4), np.uint8); img[:] = bg
    zb = np.full((size, size), 1e9)
    T = np.asarray(tex.convert("RGBA")); th, tw = T.shape[:2]
    light = np.array([0.35, -0.8, -0.5]); light /= np.linalg.norm(light)
    for W, uv in quads:
        P = W @ V.T
        n = np.cross(P[1] - P[0], P[3] - P[0]); nn = np.linalg.norm(n)
        if nn < 1e-9: continue
        n /= nn
        wn = np.cross(W[1] - W[0], W[3] - W[0]); wn /= (np.linalg.norm(wn) + 1e-9)
        sh = 0.62 + 0.38 * abs(float(wn @ light))
        X = (P[:, 0] - cx) * s + size / 2; Y = (P[:, 1] - cy) * s + size / 2; Z = P[:, 2]
        x0, x1 = int(max(0, np.floor(X.min()))), int(min(size - 1, np.ceil(X.max())))
        y0, y1 = int(max(0, np.floor(Y.min()))), int(min(size - 1, np.ceil(Y.max())))
        if x1 < x0 or y1 < y0: continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        # parallelogram coords: p = P0 + a*(P1-P0) + b*(P3-P0)
        e1 = np.array([X[1] - X[0], Y[1] - Y[0]]); e2 = np.array([X[3] - X[0], Y[3] - Y[0]])
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-9: continue
        dx, dy = gx - X[0], gy - Y[0]
        a = (dx * e2[1] - dy * e2[0]) / det; b = (e1[0] * dy - e1[1] * dx) / det
        m = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
        z = Z[0] + a * (Z[1] - Z[0]) + b * (Z[3] - Z[0])
        tu = np.clip((uv[0, 0] + a * (uv[1, 0] - uv[0, 0])).astype(int), 0, tw - 1)
        tv = np.clip((uv[0, 1] + b * (uv[3, 1] - uv[0, 1])).astype(int), 0, th - 1)
        col = T[tv, tu]
        m &= (col[..., 3] > 20)
        sub = zb[y0:y1 + 1, x0:x1 + 1]; m &= (z < sub)
        sub[m] = z[m]
        c = col.astype(float); c[..., :3] *= sh; c[..., 3] = 255
        img[y0:y1 + 1, x0:x1 + 1][m] = c[m].astype(np.uint8)
    return Image.fromarray(img)

def sheet(model, tex, title=None, views=((35, 18), (145, 18), (90, 5)), size=440, hide=(), pose=None, bg=(46, 52, 64, 255)):
    ims = [render(model, tex, y, p, size, hide, pose) for y, p in views]
    out = Image.new("RGBA", (size * len(ims), size), bg)
    for i, im in enumerate(ims): out.alpha_composite(im, (i * size, 0))
    return out
