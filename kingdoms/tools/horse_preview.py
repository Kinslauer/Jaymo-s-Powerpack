"""A small z-sorted box renderer for previews of our horses (tools/gen_horses.py): draws a rig's boxes with their
textures from any side, lit roughly as Minecraft lights entities. Orthographic; every half texel is its own quad."""
import math

from PIL import Image, ImageDraw

import horse_rig as hr

LIGHT = (0.35, -0.85, -0.4)


def _norm(v):
    n = math.sqrt(sum(a * a for a in v)) or 1.0
    return tuple(a / n for a in v)


L = _norm(LIGHT)


def quads(parts, texture, poses=None, hidden=(), scale=2, grow=None):
    """[(depth-sortable model-space quad, colour)] for every opaque pixel of the parts' boxes. `texture` is the coat at
    `scale` pixels a texel; `hidden` names parts not drawn."""
    chains = hr.transforms(parts, poses)
    pix = texture.load()
    out = []
    for p in parts:
        if p.name in hidden:
            continue
        chain = chains[p.name]
        for c in p.cubes:
            g = c.grow if grow is None else grow
            for face, (u, v, fw, fh) in hr.faces(c).items():
                pw, ph = fw * scale, fh * scale
                if pw == 0 or ph == 0:
                    continue
                n = hr.apply(chain, hr.NORMALS[face], normal=True)
                for J in range(ph):
                    for I in range(pw):
                        col = pix[u * scale + I, v * scale + J]
                        if col[3] < 128:
                            continue
                        corners = []
                        for (ds, dt) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                            q = hr.local(c, face, (I + ds) / pw, (J + dt) / ph)
                            if g:
                                q = grown(c, q, g)
                            corners.append(hr.apply(chain, q))
                        out.append((corners, n, col[:3]))
    return out


def grown(c, q, g):
    x0, y0, z0 = c.pos
    w, h, d = c.size
    mid = (x0 + w / 2, y0 + h / 2, z0 + d / 2)
    half = (w / 2, h / 2, d / 2)
    return tuple(mid[k] + (q[k] - mid[k]) * ((half[k] + g) / half[k] if half[k] else 1) for k in range(3))


def render(qs, yaw=35.0, pitch=18.0, px=8, size=None, background=(0, 0, 0, 0)):
    """The quads seen from `yaw` degrees round from the horse's left side (0 = side on), `pitch` from above."""
    a, b = math.radians(yaw), math.radians(pitch)
    proj = []
    for corners, n, col in qs:
        pts = []
        for (x, y, z) in corners:
            # turn about the vertical, then tip toward the viewer
            X = x * math.cos(a) - z * math.sin(a)
            Z = x * math.sin(a) + z * math.cos(a)
            Y = y * math.cos(b) + Z * math.sin(b)
            Z = -y * math.sin(b) + Z * math.cos(b)
            pts.append((X, Y, Z))
        nx = n[0] * math.cos(a) - n[2] * math.sin(a)
        nz = n[0] * math.sin(a) + n[2] * math.cos(a)
        ny = n[1] * math.cos(b) + nz * math.sin(b)
        nz2 = -n[1] * math.sin(b) + nz * math.cos(b)
        light = 0.55 + 0.45 * max(0.0, -(nx * L[0] + ny * L[1] + nz2 * L[2]))
        # Minecraft's own face light: top 1, sides less, underneath least
        face_light = 1.0 if n[1] < -0.5 else 0.5 if n[1] > 0.5 else 0.8 if abs(n[2]) > 0.5 else 0.6
        f = 0.5 * light + 0.5 * face_light + 0.12
        proj.append((sum(p[2] for p in pts) / 4, [(p[0], p[1]) for p in pts], tuple(min(255, int(c * f)) for c in col)))
    xs = [p[0] for q in proj for p in q[1]]
    ys = [p[1] for q in proj for p in q[1]]
    x0, y0 = min(xs), min(ys)
    w = int((max(xs) - x0) * px) + 8
    h = int((max(ys) - y0) * px) + 8
    img = Image.new("RGBA", size or (w, h), background)
    ox = (img.width - w) // 2 + 4 if size else 4
    oy = (img.height - h) + 4 if size else 4
    d = ImageDraw.Draw(img)
    for depth, pts, col in sorted(proj, key=lambda q: -q[0]):
        d.polygon([(ox + (x - x0) * px, oy + (y - y0) * px) for x, y in pts], fill=col + (255,))
    return img
