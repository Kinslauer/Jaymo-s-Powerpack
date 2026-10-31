"""Render a free-format (per-face UV) Blockbench model: bbrender.py model.bbmodel out.png [hide,comma,groups] [scale x,y,z per group 'name:sx:sy:sz;...']"""
import base64, io, json, math, sys
from PIL import Image, ImageDraw

def rot(r):
    rx, ry, rz = (math.radians(v) for v in r)
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = [[1,0,0],[0,cx,-sx],[0,sx,cx]]; Y = [[cy,0,sy],[0,1,0],[-sy,0,cy]]; Z = [[cz,-sz,0],[sz,cz,0],[0,0,1]]
    return mm(Z, mm(Y, X))
def mm(a, b): return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def mv(a, v): return [sum(a[i][k]*v[k] for k in range(3)) for i in range(3)]

def faces_of(e):
    f, t = e["from"], e["to"]; g = e.get("inflate", 0)
    x0, y0, z0 = f[0]-g, f[1]-g, f[2]-g; x1, y1, z1 = t[0]+g, t[1]+g, t[2]+g
    # corners in uv order: (u0,v0) (u1,v0) (u1,v1) (u0,v1)
    return {"north": [(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)],
            "south": [(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)],
            "east": [(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)],
            "west": [(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)],
            "up": [(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],
            "down": [(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)]}

def collect(data, hide=(), scale=None):
    """[(quad world corners, uv rect, face rotation)] with group transforms applied."""
    scale = scale or {}
    el = {e["uuid"]: e for e in data["elements"]}; gr = {g["uuid"]: g for g in data.get("groups", [])}
    out = []
    def tf(stack, p):
        for (o, R, S) in reversed(stack):
            p = [o[k] + S[k]*(p[k]-o[k]) for k in range(3)]
            q = mv(R, [p[k]-o[k] for k in range(3)]); p = [q[k]+o[k] for k in range(3)]
        return p
    def walk(n, stack):
        if isinstance(n, str):
            e = el.get(n)
            if not e or e.get("type", "cube") != "cube" or e.get("visibility") is False: return
            o = e.get("origin", [0,0,0]); R = rot(e.get("rotation", [0,0,0]))
            for fn, quad in faces_of(e).items():
                fc = e["faces"].get(fn)
                if not fc or fc.get("texture") is None: continue
                pts = [tf(stack + [(o, R, (1,1,1))], list(p)) for p in quad]
                out.append((pts, fc["uv"], fc.get("rotation", 0), fn, int(fc["texture"])))
            return
        g = dict(gr.get(n["uuid"], {})); g.update({k: v for k, v in n.items() if k != "children"})
        if g["name"] in hide or g.get("visibility") is False: return
        st = stack + [(g.get("origin",[0,0,0]), rot(g.get("rotation",[0,0,0])), scale.get(g["name"], (1,1,1)))]
        for c in n.get("children", []): walk(c, st)
    for n in data["outliner"]: walk(n, [])
    return out

def render(data, out, hide=(), scale=None, yaws=(30, 90, 150, 0), size=360, px=7.5, centre_y=16):
    texs = [Image.open(io.BytesIO(base64.b64decode(t["source"].split(",",1)[1]))).convert("RGBA") for t in data["textures"]]
    TL = [t.load() for t in texs]
    tw, th = data["resolution"]["width"], data["resolution"]["height"]
    quads = collect(data, hide, scale)
    sheet = Image.new("RGB", (size*len(yaws), size), (150, 170, 190))
    for i, yaw in enumerate(yaws):
        a = math.radians(yaw); pitch = math.radians(-15)
        def proj(p):
            x, y, z = p[0], p[1]-centre_y, p[2]
            x, z = x*math.cos(a) - z*math.sin(a), x*math.sin(a) + z*math.cos(a)
            y, z = y*math.cos(pitch) - z*math.sin(pitch), y*math.sin(pitch) + z*math.cos(pitch)
            return (size*i + size/2 + x*px, size*0.55 - y*px, z)
        polys = []
        light = {"up": 1.0, "down": .5, "north": .8, "south": .8, "east": .65, "west": .65}
        for pts, uv, frot, fn, ti in quads:
            tex = texs[ti]; T = TL[ti]; sx, sy = tex.width / tw, tex.height / th
            P = [proj(p) for p in pts]
            # backface cull
            ax, ay = P[1][0]-P[0][0], P[1][1]-P[0][1]; bx, by = P[3][0]-P[0][0], P[3][1]-P[0][1]
            if ax*by - ay*bx >= 0 and fn not in (): pass
            u0, v0, u1, v1 = uv; nu = max(1, round(abs(u1-u0))); nv = max(1, round(abs(v1-v0)))
            corners = [(u0,v0),(u1,v0),(u1,v1),(u0,v1)]
            k = (frot // 90) % 4; corners = corners[k:] + corners[:k]
            def at(s, t):
                top = [P[0][j] + (P[1][j]-P[0][j])*s for j in range(3)]; bot = [P[3][j] + (P[2][j]-P[3][j])*s for j in range(3)]
                return [top[j] + (bot[j]-top[j])*t for j in range(3)]
            def uvat(s, t):
                a0 = [corners[0][j] + (corners[1][j]-corners[0][j])*s for j in range(2)]; b0 = [corners[3][j] + (corners[2][j]-corners[3][j])*s for j in range(2)]
                return [a0[j] + (b0[j]-a0[j])*t for j in range(2)]
            for a_ in range(nu):
                for b_ in range(nv):
                    s0, s1, t0, t1 = a_/nu, (a_+1)/nu, b_/nv, (b_+1)/nv
                    u, v = uvat((s0+s1)/2, (t0+t1)/2)
                    c = T[min(tex.width-1, int(u*sx)), min(tex.height-1, int(v*sy))]
                    if c[3] < 30: continue
                    q = [at(s0,t0), at(s1,t0), at(s1,t1), at(s0,t1)]
                    z = sum(p[2] for p in q)/4
                    L = light[fn]
                    polys.append((z, [(p[0], p[1]) for p in q], tuple(int(c[j]*L) for j in range(3))))
        polys.sort(key=lambda p: -p[0])
        d = ImageDraw.Draw(sheet)
        for z, q, c in polys: d.polygon(q, fill=c)
    sheet.save(out, "PNG")

if __name__ == "__main__":
    data = json.load(open(sys.argv[1]))
    hide = sys.argv[3].split(",") if len(sys.argv) > 3 and sys.argv[3] else ()
    scale = {}
    if len(sys.argv) > 4:
        for s in sys.argv[4].split(";"):
            n, *v = s.split(":"); scale[n] = tuple(map(float, v))
    render(data, sys.argv[2], hide, scale)
