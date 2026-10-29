"""(0.7.322) Coat patterns from Realistic Horse Genetics (sekelsta, github.com/sekelsta/horse-colors), whose art is CC-BY
4.0: the masks in tools/rhg (credits in tools/rhg/CREDITS.md), drawn for its vanilla-shaped horse, carried onto ours.

Each of our hide pixels is matched to the same place on that horse: its bone's box there (the body for everything on
the body bone, the head or muzzle for the head's pieces, the thigh, cannon or hoof for a leg's), scaled from our
bone's main box to theirs, on the face that looks the same way. Only the masks are taken (how much of a layer covers
that place, 0-1, and its colour); the colours and how they're laid on are ours (gen_horses.coat)."""
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "rhg")

# their horse's boxes (texture offset, box position from the bone's pivot, size), as their model lays them out
BOXES = {
    "body": ((0, 34), (-5.0, -8.0, -19.0), (10, 10, 24)),
    "head": ((0, 0), (-2.5, -10.0, -1.5), (5, 5, 7)),
    "neck": ((0, 12), (-2.05, -9.8, -2.0), (4, 14, 8)),
    "muzzle": ((24, 18), (-2.0, -10.0, -7.0), (4, 3, 6)),
    "lip": ((24, 27), (-2.0, -7.0, -6.5), (4, 2, 5)),
    "mane": ((58, 0), (-1.0, -11.5, 5.0), (2, 16, 4)),
    "dock": ((44, 0), (-1.0, -1.0, 0.0), (2, 2, 3)),
    "leg1_a": ((78, 29), (-2.5, -2.0, -2.5), (4, 9, 5)), "leg1_b": ((78, 43), (-2.0, 0.0, -1.0), (3, 5, 3)),
    "leg1_c": ((78, 51), (-2.5, 5.0, -1.5), (4, 3, 4)),
    "leg2_a": ((96, 29), (-1.5, -2.0, -2.5), (4, 9, 5)), "leg2_b": ((96, 43), (-1.0, 0.0, -1.0), (3, 5, 3)),
    "leg2_c": ((96, 51), (-1.5, 5.0, -1.5), (4, 3, 4)),
    "leg3_a": ((44, 29), (-1.9, -1.0, -1.0), (3, 8, 4)), "leg3_b": ((44, 41), (-1.9, 0.0, -0.5), (3, 5, 3)),
    "leg3_c": ((44, 51), (-2.4, 5.0, -1.0), (4, 3, 4)),
    "leg4_a": ((60, 29), (-1.1, -1.0, -1.0), (3, 8, 4)), "leg4_b": ((60, 41), (-1.1, 0.0, -0.5), (3, 5, 3)),
    "leg4_c": ((60, 51), (-1.6, 5.0, -1.0), (4, 3, 4)),
}
# our bones to theirs (pieces hung from a bone go with it)
BONE = {"body": "body", "neck": "neck", "head": "head", "upper_mouth": "muzzle", "upper_mouth_open": "muzzle",
        "lower_mouth": "lip", "lower_mouth_open": "lip", "tail_a": "dock"}
BONE.update({f"leg{k}_{s}": f"leg{k}_{s}" for k in range(1, 5) for s in "abc"})

_layers = {}


def layer(name):
    """A mask (by its path under tools/rhg, without .png), loaded once."""
    if name not in _layers:
        _layers[name] = Image.open(os.path.join(ROOT, name + ".png")).convert("RGBA").load()
    return _layers[name]


class Box:
    def __init__(self, uv, pos, size):
        self.uv, self.pos, self.size = uv, pos, size


def faces(b):
    u, v = b.uv
    w, h, d = b.size
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "a": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "b": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def face_st(b, face, p):
    """Where the point `p` (on or projected onto box `b`) lies across `face`, as (s, t) 0-1 (inverse of horse_rig.local)."""
    (x0, y0, z0), (w, h, d) = b.pos, b.size
    x, y, z = p
    x1, z1 = x0 + w, z0 + d
    st = {"top": ((x - x0) / w, (z1 - z) / d), "bottom": ((x - x0) / w, (z - z0) / d),
          "front": ((x - x0) / w, (y - y0) / h), "back": ((x1 - x) / w, (y - y0) / h),
          "a": ((z1 - z) / d, (y - y0) / h), "b": ((z - z0) / d, (y - y0) / h)}[face]
    return tuple(min(0.999, max(0.0, v)) for v in st)


class Mapper:
    """Where each of a build's hide pixels lies on Realistic Horse Genetics' horse (a texel there), or None."""

    def __init__(self, parts):
        self.by = {p.name: p for p in parts}
        self.main = {p.name: p.cubes[0] for p in parts if p.cubes}
        self.cache = {}

    def bone(self, name):
        """The bone a part belongs to that has a box on their horse."""
        while name is not None and name not in BONE:
            name = self.by[name].parent if name in self.by else None
        return name

    def texel(self, x):
        key = (x.X, x.Y)
        if key in self.cache:
            return self.cache[key]
        out = None
        ours = self.bone(x.part)
        if ours is not None and ours in self.main:
            theirs = BONE[ours]
            q = x.q
            if x.part != ours:
                # a piece hung from the bone (it's in the bone's space when unposed, as all ours are)
                pose = self.by[x.part].pose
                q = (q[0] + pose[0], q[1] + pose[1], q[2] + pose[2])
            if theirs == "head" and q[2] < -1.5:
                theirs = "muzzle"  # the head's pieces ahead of the skull go on the muzzle
            main = self.main[ours]
            b = Box(*BOXES[theirs])
            # scaled from our bone's main box onto theirs
            n = [(q[k] - main.pos[k]) / main.size[k] if main.size[k] else 0.5 for k in range(3)]
            p = tuple(b.pos[k] + min(1.0, max(0.0, n[k])) * b.size[k] for k in range(3))
            if ours == "head" or ours in ("upper_mouth", "lower_mouth"):
                p = tuple(min(b.pos[k] + b.size[k], max(b.pos[k], q[k])) for k in range(3))  # same size: as it is
            s, t = face_st(b, x.face, p)
            u, v, fw, fh = faces(b)[x.face]
            out = (int(u + s * fw), int(v + t * fh))
        self.cache[key] = out
        return out

    def cover(self, x, *names):
        """How much the layers `names` cover the pixel (the most of them, 0-1)."""
        tx = self.texel(x)
        if tx is None:
            return 0.0
        return max(layer(n)[tx][3] / 255.0 for n in names)

    def colour(self, x, name):
        """The layer's own colour and cover at the pixel."""
        tx = self.texel(x)
        if tx is None:
            return (0, 0, 0), 0.0
        r, g, b, a = layer(name)[tx]
        return (r, g, b), a / 255.0


SOCKS = tuple(f"socks/{k}_2" for k in range(4))
STOCKINGS = tuple(f"socks/{k}_5" for k in range(4))
