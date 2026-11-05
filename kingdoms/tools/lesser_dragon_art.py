"""(0.7.522) What the lesser dragons' art tools share (tools/gen_<species>.py, docs/SaintsDragons-Plan.md): a body plan
(GEOMETRY rows: name, parent, uv part, box (x, y, z, w, h, d), pose (px, py, pz, rx, ry, rz), mirror) packed onto a
256x256 sheet, painted face by face, printed as the Java of a model's createBodyLayer, and rendered to a preview.
Model pixels, y down, the ground at 24, -z the front. All our own art: nothing of Saint's Dragons' art is used."""
import math, random

from PIL import Image, ImageDraw
from gen_dragons import SIZE

FACE_I = ['top', 'bottom', 'east', 'north', 'west', 'south']


class Plan:
    def __init__(self, geometry):
        self.geometry = geometry
        self.parts = []
        for _, _, uv, (x, y, z, w, h, d), _, _ in geometry:
            if uv not in [p[0] for p in self.parts]:
                self.parts.append((uv, w, h, d))
        self.dims = {n: (int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))) for n, w, h, d in self.parts}
        self.uv = self._pack()

    def _pack(self):
        def region(w, h, d):
            return 2 * (w + d), h + d
        order = sorted(self.parts, key=lambda p: -region(*self.dims[p[0]])[1])
        at, x, y, shelf = {}, 0, 0, 0
        for name, _, _, _ in order:
            rw, rh = region(*self.dims[name])
            if x + rw > SIZE:
                x, y, shelf = 0, y + shelf, 0
            at[name] = (x, y)
            x += rw
            shelf = max(shelf, rh)
        assert y + shelf <= SIZE, "the parts don't fit"
        return at

    def faces(self, name):
        u, v = self.uv[name]
        w, h, d = self.dims[name]
        return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
                'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}

    def paint(self, img, name, fn):
        px = img.load()
        for face, (fx, fy, fw, fh) in self.faces(name).items():
            for y in range(fh):
                for x in range(fw):
                    c = fn(face, x, y, fw, fh)
                    if c is not None:
                        px[fx + x, fy + y] = c

    # ---- the Java --------------------------------------------------------------------------------------------------

    def java(self):
        out = []
        var = {None: 'root'}
        for name, parent, uv, (x, y, z, w, h, d), (px, py, pz, rx, ry, rz), mirror in self.geometry:
            u, v = self.uv[uv]
            fn = 'mirrored' if mirror else 'box'
            if rx or ry or rz:
                pose = 'PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)' % tuple(jf(a) for a in (px, py, pz, rx, ry, rz))
            else:
                pose = 'PartPose.offset(%s, %s, %s)' % tuple(jf(a) for a in (px, py, pz))
            w, h, d = self.dims[uv]
            call = '%s.addOrReplaceChild("%s", %s(%d, %d, %s, %s, %s, %d, %d, %d), %s);' % (var[parent], name, fn, u, v, jf(x), jf(y), jf(z),
                                                                                            w, h, d, pose)
            if any(p[1] == name for p in self.geometry):
                var[name] = name.replace('_', '')
                call = 'PartDefinition %s = %s' % (var[name], call)
            out.append('\t\t' + call)
        return '\n'.join(out)

    # ---- the preview -----------------------------------------------------------------------------------------------

    def transforms(self, extra):
        by = {gm[0]: gm for gm in self.geometry}
        cache = {}

        def tf(name):
            if name in cache:
                return cache[name]
            _, parent, _, _, (px, py, pz, rx, ry, rz), _ = by[name]
            ex = extra.get(name, (0, 0, 0))
            R = rotm(rx + ex[0], ry + ex[1], rz + ex[2])
            if parent is None:
                res = (R, [px, py, pz])
            else:
                PR, PT = tf(parent)
                res = (mm(PR, R), [PT[k] + mv(PR, [px, py, pz])[k] for k in range(3)])
            cache[name] = res
            return res
        return tf

    def render(self, tex, out_img, ox, oy, yaw, scale, hidden=(), extra=None, pitch=0.42, ground=True):
        tf = self.transforms(extra or {})
        px = tex.load()
        polys = []
        cy_, sy_ = math.cos(yaw), math.sin(yaw)
        cp, sp = math.cos(pitch), math.sin(pitch)

        def proj(p):
            x, y, z = p[0], -p[1] + 24, p[2]
            X = x * cy_ - z * sy_
            Z = x * sy_ + z * cy_
            Y = y * cp + Z * sp
            D = Z * cp - y * sp
            return (ox + X * scale, oy - Y * scale, D)
        if ground:
            dr = ImageDraw.Draw(out_img)
            g = [proj((a, 24, b))[:2] for a, b in ((-40, -60), (40, -60), (40, 60), (-40, 60))]
            dr.polygon(g, fill=(48, 54, 50))
        for name, parent, uv, (x, y, z, w, h, d), _, mirror in self.geometry:
            if name in hidden:
                continue
            R, T = tf(name)
            W, H, Dd = self.dims[uv]

            def P(a, b, c):
                q = mv(R, [a, b, c])
                return proj([q[k] + T[k] for k in range(3)])
            fr = self.faces(uv)
            x0, y0, z0, x1, y1, z1 = x, y, z, x + W, y + H, z + Dd
            spec = {
                'top': ((x0, y0, z0), (1, 0, 0), (0, 0, 1)),
                'bottom': ((x0, y1, z0), (1, 0, 0), (0, 0, 1)),
                'north': ((x0, y0, z0), (1, 0, 0), (0, 1, 0)),
                'south': ((x1, y0, z1), (-1, 0, 0), (0, 1, 0)),
                'east': ((x0, y0, z1), (0, 0, -1), (0, 1, 0)),
                'west': ((x1, y0, z0), (0, 0, 1), (0, 1, 0)),
            }
            for face, (o, ua, va) in spec.items():
                fx, fy, fw, fh = fr[face]
                for ty in range(fh):
                    for txx in range(fw):
                        tx = fw - 1 - txx if mirror and face in ('top', 'bottom', 'north', 'south') else txx
                        c = px[fx + tx, fy + ty]
                        if c[3] == 0:
                            continue
                        a = [o[k] + ua[k] * txx + va[k] * ty for k in range(3)]
                        b = [a[k] + ua[k] for k in range(3)]
                        cc = [b[k] + va[k] for k in range(3)]
                        dd = [a[k] + va[k] for k in range(3)]
                        pts = [P(*q) for q in (a, b, cc, dd)]
                        depth = sum(p[2] for p in pts) / 4
                        polys.append((depth, [(p[0], p[1]) for p in pts], c[:3]))
        polys.sort(key=lambda p: -p[0])
        dr = ImageDraw.Draw(out_img)
        for _, pts, c in polys:
            dr.polygon(pts, fill=c)

    def top_y(self, extra=None, hidden=()):
        """The highest point of the model (model pixels, y down): for checking its height."""
        tf = self.transforms(extra or {})
        best = 99.0
        for name, parent, uv, (x, y, z, w, h, d), _, _ in self.geometry:
            if name in hidden:
                continue
            R, T = tf(name)
            W, H, Dd = self.dims[uv]
            for a in (x, x + W):
                for b in (y, y + H):
                    for c in (z, z + Dd):
                        q = mv(R, [a, b, c])
                        best = min(best, q[1] + T[1])
        return best


def jf(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    if s in ('-0', ''):
        s = '0'
    return s + ('.0F' if '.' not in s else 'F')


def rotm(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mm(mm(Z, Y), X)


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def noise_field(seed, cells=30, scale=7.0):
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / scale, y / scale
        x0, y0 = int(fx) % (cells - 1), int(fy) % (cells - 1)
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] * (1 - tx) + grid[y0][x0 + 1] * tx
        b = grid[y0 + 1][x0] * (1 - tx) + grid[y0 + 1][x0 + 1] * tx
        return a * (1 - ty) + b * ty
    return at


def chain(geometry, parent, start, segments, along, shrink_y=0.0):
    """Appends a chain of segments (name, (w, h, d), rx[, ry]) hanging one from the end of the next: `along` -1 runs
    to the front (-z, a neck), +1 to the back (+z, a tail); `start` the first one's pose offset on its parent."""
    prev_d = None
    for seg in segments:
        n, (w, h, d), rx = seg[0], seg[1], seg[2]
        ry = seg[3] if len(seg) > 3 else 0
        if prev_d is None:
            pose = (start[0], start[1], start[2], rx, ry, 0)
        else:
            pose = (0, shrink_y, along * (prev_d - 1), rx, ry, 0)
        box = (-w / 2, -h / 2, -d, w, h, d) if along < 0 else (-w / 2, -h / 2, 0, w, h, d)
        geometry.append((n, parent, n, box, pose, False))
        parent, prev_d = n, d
    return parent
