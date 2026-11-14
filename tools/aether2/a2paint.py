"""Paints a skin onto a parsed model's boxes. Our own pixels: nothing read from any other texture."""
import random, re, zlib
from PIL import Image
import a2parse

def hx(s): return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))
def ramp(*s): return [hx(c) for c in s]
def mix(a, b, t): return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))
def ramp_from(base, n=5, dark=0.52, light=1.22, cool=(20, 24, 48), warm=(255, 250, 225)):
    """A ramp around one colour: shadows lean cool and dark, lights lean warm."""
    b = hx(base) if isinstance(base, str) else base
    out = []
    for i in range(n):
        t = i / (n - 1)
        f = dark + (light - dark) * t
        c = tuple(max(0, min(255, v * f)) for v in b)
        c = mix(c, cool, 0.22 * (1 - t)) if t < 0.5 else mix(c, warm, 0.18 * (t - 0.5) * 2)
        out.append(tuple(int(v) for v in c))
    return out

FACE_LIGHT = dict(top=0.9, bottom=-1.1, north=0.15, south=-0.25, west=-0.05, east=-0.05)

class Painter:
    def __init__(self, model, seed):
        self.m = model; self.im = Image.new("RGBA", (model["tw"], model["th"]), (0, 0, 0, 0)); self.px = self.im.load()
        self.rng = random.Random(zlib.crc32(seed.encode()))
        self.noise = {}
    def n(self, x, y, cell=2):
        """Blotchy noise in -1..1: neighbours share a value, the way hand-placed clusters look."""
        k = (x // cell, y // cell, cell)
        if k not in self.noise: self.noise[k] = self.rng.uniform(-1, 1)
        return self.noise[k]
    def put(self, x, y, c, a=255):
        if 0 <= x < self.im.width and 0 <= y < self.im.height: self.px[x, y] = (c[0], c[1], c[2], a)
    def face(self, rect, face, rmp, style="plain", strength=1.0, a=255):
        u, v, w, h = rect
        if w <= 0 or h <= 0: return
        n = len(rmp); mid = (n - 1) / 2
        for j in range(h):
            for i in range(w):
                x, y = u + i, v + j
                val = FACE_LIGHT[face] * 0.9
                if face in ("north", "south", "west", "east"):
                    val += 0.9 - 1.9 * (j + 0.5) / h          # lit from above
                    if i == 0 or i == w - 1: val -= 0.35     # the turn of the form
                else:
                    if i == 0 or i == w - 1 or j == 0 or j == h - 1: val -= 0.3
                if style == "plain":
                    val += self.n(x, y, 2) * 0.55 + self.n(x, y, 1) * 0.25
                elif style == "wool":
                    val += self.n(x, y, 2) * 0.55 + self.n(x + 1, y + 1, 4) * 0.35
                    if (i + (j // 3) * 2) % 5 == 0 and j % 3 == 2: val -= 0.7     # curls
                elif style == "feather":       # rows of overlapping feathers: a shadow line under each row, staggered
                    row = j % 4; off = (j // 4) % 2 * 2
                    val += self.n(x, y, 2) * 0.3
                    if row == 3 and (i + off) % 4 != 0: val -= 0.55
                    if row == 0 and (i + off) % 4 == 2: val += 0.35
                elif style == "plume":      # long feathers: stripes along the long axis, a pale shaft
                    lon = w >= h
                    c = (j if lon else i); span = (h if lon else w)
                    val += self.n(x, y, 2) * 0.25
                    if span >= 3 and c == span // 2: val -= 0.45           # the shaft
                    if span >= 3 and (c == 0 or c == span - 1): val -= 0.25
                elif style == "fur":
                    val += self.n(x, y * 3, 1) * 0.45 + self.n(x, y, 3) * 0.5 + (-0.3 if (i * 3 + j) % 5 == 0 else 0)
                elif style == "scale":         # rows of scutes: a dark line every third row, broken
                    val += self.n(x, y, 2) * 0.35
                    if j % 3 == 2 and (i + j // 3) % 3 != 0: val -= 0.5
                elif style == "stone":
                    val += self.n(x, y, 3) * 0.7 + self.n(x, y, 1) * 0.3
                    if self.n(x * 7, y * 7, 1) > 0.86: val -= 1.2             # pits
                elif style == "horn":
                    val += (0.5 if j % 3 == 0 else 0) + self.n(x, y, 1) * 0.25
                elif style == "smooth":
                    val += self.n(x, y, 3) * 0.3
                elif style == "flat":
                    val = FACE_LIGHT[face] * 0.4 + self.n(x, y, 2) * 0.3
                idx = int(round(mid + val * strength * (n - 1) / 3.2))
                self.put(x, y, rmp[max(0, min(n - 1, idx))], a)
    def box(self, b, rmp, style="plain", strength=1.0, a=255, only=None):
        for f, rect in a2parse.faces(b).items():
            if only and f not in only: continue
            self.face(rect, f, rmp, style, strength, a)
    def rect(self, b, face, x, y, w, h, c, a=255):
        """Pixels inside one face of a box, x right and y down from the face's top-left."""
        u, v, fw, fh = a2parse.faces(b)[face]
        for j in range(h):
            for i in range(w):
                if 0 <= x + i < fw and 0 <= y + j < fh: self.put(u + x + i, v + y + j, c, a)
    def eye(self, b, face, x, y, w=2, h=2, white=(240, 240, 235), pupil=(20, 20, 28), pupil_side="front", glint=True):
        """An eye on a side face. On west/east faces 'front' is the edge that touches the north face."""
        self.rect(b, face, x, y, w, h, white)
        fw = a2parse.faces(b)[face][2]
        if face == "west": px_ = x + w - 1 if pupil_side == "front" else x
        elif face == "east": px_ = x if pupil_side == "front" else x + w - 1
        else: px_ = x if pupil_side == "left" else x + w - 1
        self.rect(b, face, px_, y, 1, h, pupil)
        if glint and h > 1: self.rect(b, face, px_, y, 1, 1, mix(pupil, (255, 255, 255), 0.35))

def paint(model, seed, rules, default=None):
    """rules: list of (regex on part name, ramp, style[, strength]). First match wins."""
    p = Painter(model, seed)
    for name in model["order"]:
        part = model["parts"][name]
        # a rotation helper part (name_r1) takes its parent's look
        look = None
        for nm in (name, part["parent"] or ""):
            for r in rules:
                if re.search(r[0], nm):
                    look = r; break
            if look: break
        look = look or default
        if not look: continue
        for b in part["boxes"]:
            p.box(b, look[1], look[2], look[3] if len(look) > 3 else 1.0)
    return p

def side_eyes(p, box, ex, ey, w=2, h=2, white=(240, 240, 235), pupil=(20, 20, 28)):
    """The same eye on both side faces, ex pixels back from the front edge."""
    d = a2parse.faces(box)["west"][2]
    p.eye(box, "west", d - ex - w, ey, w, h, white, pupil)
    p.eye(box, "east", ex, ey, w, h, white, pupil)

def front_eyes(p, box, ex, ey, w=2, h=2, white=(240, 240, 235), pupil=(20, 20, 28)):
    """Two eyes on the front face, ex pixels in from each side, pupils inward."""
    fw = a2parse.faces(box)["north"][2]
    p.eye(box, "north", ex, ey, w, h, white, pupil, pupil_side="right")
    p.eye(box, "north", fw - ex - w, ey, w, h, white, pupil, pupil_side="left")

def tip(p, box, face, rmp, along="x", end="hi", n=2):
    """Recolours the last n pixels of a face along one axis (feather tips, hooves, claws), keeping the shading under it."""
    u, v, w, h = a2parse.faces(box)[face]
    for j in range(h):
        for i in range(w):
            c = i if along == "x" else j; span = w if along == "x" else h
            k = (span - 1 - c) if end == "hi" else c
            if k < n and 0 <= u + i < p.im.width and 0 <= v + j < p.im.height:
                old = p.px[u + i, v + j]
                lum = (old[0] * 0.3 + old[1] * 0.59 + old[2] * 0.11) / 255.0
                idx = max(0, min(len(rmp) - 1, int(lum * len(rmp))))
                # a ragged inner edge
                if k == n - 1 and (i * 7 + j * 3) % 3 == 0: continue
                p.put(u + i, v + j, rmp[idx])

def face_point(b, face, i, j):
    """Box-local centre (x, y, z) of pixel (i, j) of a face, the way the game maps it (mirrored boxes reflect in x)."""
    x0, y0, z0 = b["x"], b["y"], b["z"]; w, h, d = b["w"], b["h"], b["d"]
    fi, fj = i + 0.5, j + 0.5
    if face == "north": pt = (x0 + fi, y0 + fj, z0)
    elif face == "south": pt = (x0 + w - fi, y0 + fj, z0 + d)
    elif face == "west": pt = (x0, y0 + fj, z0 + d - fi)
    elif face == "east": pt = (x0 + w, y0 + fj, z0 + fi)
    elif face == "top": pt = (x0 + fi, y0, z0 + d - fj)
    else: pt = (x0 + fi, y0 + h, z0 + fj)
    if b["mirror"]: pt = (2 * x0 + w - pt[0], pt[1], pt[2])
    return pt

def plume(p, b, rmp, acc=None, acc_from=0.68, taper_from=0.5, blunt=0.3):
    """A long thin box painted as one feather: cut to a tapering blade with a shaft, the tip in the accent ramp."""
    lo = (b["x"], b["y"], b["z"]); size = (b["w"], b["h"], b["d"])
    order = sorted(range(3), key=lambda a: -size[a]); la, wa = order[0], order[1]      # long axis, width axis
    tip_hi = abs(lo[la] + size[la]) >= abs(lo[la])
    n = len(rmp)
    for face, (u, v, fw, fh) in a2parse.faces(b).items():
        for j in range(fh):
            for i in range(fw):
                pt = face_point(b, face, i, j)
                t = (pt[la] - lo[la]) / size[la]; t = t if tip_hi else 1 - t
                c = (pt[wa] - lo[wa]) / size[wa] * 2 - 1 if size[wa] > 0 else 0.0     # -1..1 across the blade
                half = 1.0 if t < taper_from else blunt + (1 - blunt) * (1 - ((t - taper_from) / (1 - taper_from)) ** 1.6)
                x, y = u + i, v + j
                if abs(c) > half + 1e-6:
                    p.put(x, y, (0, 0, 0), 0); continue
                val = p.n(x, y, 2) * 0.35 + (0.5 - t) * 0.5
                if size[wa] >= 3 and abs(c) < 1.0 / size[wa]: val -= 0.7                 # shaft
                if abs(c) > half - 2.0 / max(size[wa], 1) and size[wa] >= 4: val -= 0.3   # edge
                if int(t * size[la]) % 3 == 2 and size[wa] >= 4 and abs(c) > 0.3: val -= 0.25    # barbs
                r = acc if (acc and t > acc_from + p.n(x, y, 1) * 0.04) else rmp
                idx = int(round((n - 1) / 2 + val * (n - 1) / 3.0))
                p.put(x, y, r[max(0, min(len(r) - 1, idx))])


def plumes(p, m, match, rmp, acc=None, **kw):
    """plume() on every box of the parts whose name (or whose parent's name) contains one of the words."""
    words = match.split("|")
    for name in m["order"]:
        part = m["parts"][name]
        if any(w in name or w in (part["parent"] or "") for w in words):
            for b in part["boxes"]:
                plume(p, b, rmp, acc, **kw)

def clear_except(p, m, keep):
    """Blanks every box whose part name lacks all of the words (for a glow layer)."""
    words = keep.split("|"); keepers = []
    for name in m["order"]:
        if any(w in name for w in words):
            for b in m["parts"][name]["boxes"]: keepers += list(a2parse.faces(b).values())
    mask = set()
    for u, v, w, h in keepers:
        for j in range(h):
            for i in range(w): mask.add((u + i, v + j))
    for y in range(p.im.height):
        for x in range(p.im.width):
            if (x, y) not in mask: p.px[x, y] = (0, 0, 0, 0)

def fan(p, b, rmp, acc=None, scallop=3):
    """A flat plane that spreads both ways from its middle (a frill, a pair of flaps): scalloped outer edges."""
    lo = (b["x"], b["y"], b["z"]); size = (b["w"], b["h"], b["d"])
    order = sorted(range(3), key=lambda a: -size[a]); la, wa = order[0], order[1]
    n = len(rmp)
    for face, (u, v, fw, fh) in a2parse.faces(b).items():
        for j in range(fh):
            for i in range(fw):
                pt = face_point(b, face, i, j)
                t = abs((pt[la] - lo[la]) / size[la] * 2 - 1)                 # 0 at the middle, 1 at either end
                c = int(pt[wa] - lo[wa])
                x, y = u + i, v + j
                edge = 1.0 - (0.22 if c % scallop == scallop - 1 else 0.0) - (0.12 if c % scallop == 0 else 0.0)
                if t > edge or t < 0.12:
                    p.put(x, y, (0, 0, 0), 0); continue
                val = (0.5 - t) * 0.9 + p.n(x, y, 2) * 0.3 - (0.5 if c % scallop == 0 else 0)
                r = acc if (acc and t > 0.7) else rmp
                p.put(x, y, r[max(0, min(len(r) - 1, int(round((n - 1) / 2 + val * (n - 1) / 3.0))))])

def shape(p, b, fn):
    """Paints every face pixel of a box from fn(point, lo, size) -> colour or None (cut out)."""
    lo = (b["x"], b["y"], b["z"]); size = (b["w"], b["h"], b["d"])
    for face, (u, v, fw, fh) in a2parse.faces(b).items():
        for j in range(fh):
            for i in range(fw):
                c = fn(face_point(b, face, i, j), lo, size, u + i, v + j)
                if c is None: p.put(u + i, v + j, (0, 0, 0), 0)
                else: p.put(u + i, v + j, c)
