# Renders the spirit figures and arms as the game draws them (glowing shells over a grey player), from figures.py.
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from figures import FIG, ARM_INNER, ARM_SIZE, NAMES, GRIMSBY, VARRO, AMBROSE, THESSALY, QUIET

COLOR = {GRIMSBY: 0x9AB8FF, VARRO: 0xFFB347, AMBROSE: 0x9BE08F, THESSALY: 0xFF9ED8, QUIET: 0xC8C8DC}
MANA = 0x5AA0FF  # mana's blue (approx ManaClient.COLOR)
# anchors: up, back, right, idle, open
ANCH = {
    GRIMSBY: (4.0, 3.5, 0.0, (-1.35, -0.15, 0.30), False),
    VARRO: (6.5, 5.0, 2.5, (-1.15, 0.35, 0.55), True),
    AMBROSE: (6.0, 4.5, 0.0, (-1.0, 0.0, 0.35), False),
    THESSALY: (2.5, 3.0, 0.0, (-1.2, -0.2, 0.30), False),
    QUIET: (6.0, 4.5, 0.0, (-1.0, 0.0, 0.35), False),
}
FLOAT_SIZE_CAP = 1.6
BEHIND = 4.5


def rgb(c):
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], float) / 255.0


def mix(c):
    return rgb(MANA) * 0.5 + rgb(c) * 0.5


# ---- transforms (ModelPart: translate by pivot/16, rotate ZYX, scale) ----
def rot(xr, yr, zr):
    cx, sx, cy, sy, cz, sz = math.cos(xr), math.sin(xr), math.cos(yr), math.sin(yr), math.cos(zr), math.sin(zr)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def part(m, x=0, y=0, z=0, xr=0, yr=0, zr=0, s=(1, 1, 1)):
    t = np.eye(4)
    t[:3, 3] = [x, y, z]
    r = np.eye(4)
    r[:3, :3] = rot(xr, yr, zr)
    sc = np.diag([s[0], s[1], s[2], 1.0])
    return m @ t @ r @ sc


def cube(m, x0, y0, z0, w, h, d, g):
    x0, y0, z0 = x0 - g, y0 - g, z0 - g
    w, h, d = w + 2 * g, h + 2 * g, d + 2 * g
    pts = []
    for i in range(8):
        p = np.array([x0 + (w if i & 4 else 0), y0 + (h if i & 2 else 0), z0 + (d if i & 1 else 0), 1.0])
        pts.append((m @ p)[:3])
    return pts


FACES = [(0, 2, 6, 4), (1, 5, 7, 3), (0, 1, 3, 2), (4, 6, 7, 5), (2, 3, 7, 6), (0, 4, 5, 1)]


def box(lst, m, cx, y, cz, w, h, d, g):
    lst.append(cube(m, cx - w / 2, y, cz - d / 2, w, h, d, g))


# ---- the arms (SpiritRig.mesh) ----
ELBOW_Y, FORE = 4.0, 6.0


def fingers_n(k):
    return 3 if k in (GRIMSBY, QUIET) else 4


def palm_len(k):
    return {GRIMSBY: 2.6, VARRO: 4.0, AMBROSE: 5.0, THESSALY: 2.6}.get(k, 3.0)


def arm(lst, k, m, side, g, f):
    cx = -1.0 if side > 0 else 1.0
    inn = 1.0 if side > 0 else -1.0
    # upper
    if k == GRIMSBY:
        box(lst, m, cx, -2, 0, 3, 6, 3, g); box(lst, m, cx, -2.6, 0, 4, 2, 4, g)
    elif k == VARRO:
        box(lst, m, cx, -2, 0, 5, 6, 5, g); box(lst, m, cx, -3.2, 0, 7, 3.2, 6.4, g)
        box(lst, m, cx, -0.2, 0, 7.4, 0.8, 6.8, g); box(lst, m, cx, 2, 0, 5.6, 0.8, 5.6, g)
    elif k == AMBROSE:
        box(lst, m, cx, -2, 0, 4, 6, 4, g); box(lst, m, cx, -2.4, 0, 4.6, 1.6, 4.6, g)
    elif k == THESSALY:
        box(lst, m, cx, -2, 0, 3, 6, 3, g); box(lst, m, cx, 1, 0, 3.4, 0.6, 3.4, g)
    else:
        box(lst, m, cx, -2, 0, 4, 6, 4, g)
    e = part(m, 0, ELBOW_Y, 0, f["elbow"])
    if k == GRIMSBY: box(lst, e, cx, -0.9, 0, 3.4, 1.8, 3.4, g)
    elif k == VARRO: box(lst, e, cx, -1, 0, 5.8, 2, 5.8, g); box(lst, e, cx, -0.6, 2.9, 2, 1.6, 1.2, g)
    elif k == AMBROSE: box(lst, e, cx, -0.4, 0, 5.2, 3, 5.2, g)
    elif k == THESSALY: box(lst, e, cx, -0.6, 0, 3.2, 1.2, 3.2, g)
    else: box(lst, e, cx, -0.8, 0, 4.1, 1.6, 4.1, g)
    if k == GRIMSBY: box(lst, e, cx, 0, 0, 2.6, FORE, 2.6, g)
    elif k == VARRO: box(lst, e, cx, 0, 0, 5, FORE, 5, g); box(lst, e, cx, FORE - 1.2, 0, 5.8, 1.2, 5.8, g)
    elif k == AMBROSE: box(lst, e, cx, 0, 0, 3.6, FORE, 3.6, g)
    elif k == THESSALY: box(lst, e, cx, 0, 0, 2.6, FORE, 2.6, g)
    else: box(lst, e, cx, 0, 0, 4, FORE, 4, g)
    w = part(e, 0, FORE, 0, f["wrist"])
    if k == GRIMSBY: box(lst, w, cx, 0, 0, 3, 2.6, 2, g)
    elif k == VARRO:
        box(lst, w, cx, 0, 0, 6, 4, 5, g)
        for s in range(4): box(lst, w, cx - 2.25 + s * 1.5, 3.2, -2.7, 1.1, 1.1, 1.0, g)
    elif k == AMBROSE:
        box(lst, w, cx, 0, 0, 6, 5, 2, g); box(lst, w, cx, 1.2, -1.1, 3, 3, 0.3, g); box(lst, w, cx, -0.6, 0, 4.4, 1, 4.4, g)
    elif k == THESSALY:
        box(lst, w, cx, 0, 0, 2.8, 2.6, 1.4, g); box(lst, w, cx, -0.6, 0, 3.2, 0.8, 3.2, g)
    else: box(lst, w, cx, 0, 0, 4, 3, 2.2, g)
    base, tip, width, span = {GRIMSBY: (3, 3, 0.8, 2.4), VARRO: (2.4, 2.2, 1.3, 4.5), AMBROSE: (3.2, 2.8, 1.1, 4.6),
                              THESSALY: (2.6, 2.4, 0.6, 2.1)}.get(k, (3.8, 3.4, 0.9, 2.6))
    n = fingers_n(k)
    mid = (n - 1) * 0.5
    curl = f["curl"]
    for i in range(n):
        x = cx - span / 2 + span * i / (n - 1)
        longer = 0.4 if (n > 2 and 0 < i < n - 1) else 0.0
        fm = part(w, x, palm_len(k), 0, -curl * 1.45, 0, (mid - i) * 0.2 * f["spread"] * side)
        box(lst, fm, 0, 0, 0, width, base + longer, width, g)
        tm = part(fm, 0, base + longer, 0, -curl * 1.65)
        box(lst, tm, 0, 0, 0, width * 0.9, tip, width * 0.9, g)
    tw = {VARRO: 1.5, AMBROSE: 1.2, THESSALY: 0.7}.get(k, 1.0)
    half = {VARRO: 3.0, AMBROSE: 3.0, GRIMSBY: 1.4, THESSALY: 1.4}.get(k, 2.0)
    th = part(w, cx + inn * half, palm_len(k) * 0.35, -0.3, -curl * 0.9, 0,
              side * (curl * -0.15 + (1 - curl) * (0.55 + 0.3 * f["spread"])))
    box(lst, th, 0, 0, 0, tw, 2.2, tw, g)
    box(lst, part(th, 0, 2.2, 0, -curl * 0.8), 0, 0, 0, tw * 0.9, 2.0, tw * 0.9, g)
    if k == GRIMSBY:
        b = part(w, cx - inn * 1.6, -0.6, 0)
        box(lst, b, 0, 0, 0, 0.8, 12, 2.6, g); box(lst, b, 0, 12, 0, 0.8, 1.6, 1.4, g); box(lst, b, 0, -0.2, 0, 1.6, 0.8, 3.6, g)


def placement(k, style=None):
    style = k if style is None else style
    up, back, right, idle, opn = ANCH[style]
    size = ARM_SIZE[k]
    lift = min(max(size, 1.0), FLOAT_SIZE_CAP)
    F = FIG[k]
    fs = F["scale"]
    shoulder = fs * F["half"] + ARM_INNER[k] * size
    z = max(back * lift, BEHIND + F["front"] * fs)
    arm_y = 2.0 - up * lift
    return dict(size=size, lift=lift, fs=fs, shoulder=shoulder, z=z, arm_y=arm_y, top=arm_y - 2.0 * size, right=right * lift,
                idle=idle, open=opn)


def scene(k, g_arm, g_fig, pose="idle", t=0.0):
    p = placement(k)
    arms, fig = [], []
    root = np.eye(4)
    for i, side in enumerate((1.0, -1.0)):
        if pose == "idle":
            sx, sy, sz = p["idle"]
            f = dict(elbow=-0.75, wrist=0.15, curl=0.3, spread=0.3)
            if p["open"]:
                f.update(curl=0.12, spread=0.9, wrist=-0.45)
        else:  # a blow held: the sword's
            sx, sy, sz = -1.9, -0.3, 0.2
            f = dict(elbow=-0.15, wrist=0.2, curl=1.0, spread=0.0)
            if i == 1:
                sx, sy, sz = p["idle"]
                f = dict(elbow=-0.75, wrist=0.15, curl=0.3, spread=0.3)
        m = part(root, -side * p["shoulder"] - p["right"], p["arm_y"], p["z"], sx, sy * side, sz * side, (p["size"],) * 3)
        if g_arm is not None:
            arm(arms, k, m, side, g_arm, f)
    if g_fig is not None:
        F = FIG[k]
        throb = 1.0 + 0.03 * math.sin(t)
        fm = part(root, -p["right"], p["top"], p["z"], 0, 0, 0, (p["fs"] * throb,) * 3)
        for b in F["torso"]: box(fig, fm, *b[:6], g_fig)
        for b in F["skull"]: box(fig, fm, *b[:6], g_fig)
        tm = part(fm, 0, F["tail_y"], 0, 0.08 * math.sin(t * 0.7))
        for b in F["tail"]: box(fig, tm, *b[:6], g_fig)
    return arms, fig


def player():
    out = []
    m = np.eye(4)
    box(out, m, 0, -8, 0, 8, 8, 8, 0)     # head
    box(out, m, 0, 0, 0, 8, 12, 4, 0)     # body
    box(out, m, -6, -2 + 2, 0, 4, 12, 4, 0)  # arms
    box(out, m, 6, 0, 0, 4, 12, 4, 0)
    box(out, m, -2, 12, 0, 4, 12, 4, 0)   # legs
    box(out, m, 2, 12, 0, 4, 12, 4, 0)
    return out


# ---- the camera and the glow ----
W, H = 560, 720


def view(yaw, pitch):
    r = rot(pitch, 0, 0) @ rot(0, yaw, 0)
    return r


def project(pts, r, scale, ox, oy):
    out = []
    for p in pts:
        q = np.array([-p[0], -p[1], p[2]])  # model to world: x and y flipped (entity renderer)
        v = r @ q
        out.append((ox + v[0] * scale, oy - v[1] * scale, v[2]))
    return out


def render(k, yaw, pitch, figure_alpha, arms_alpha=1.0, pose="idle", t=0.0, scale=5.6, label=None):
    r = view(yaw, pitch)
    ox, oy = W / 2, H * 0.56
    img = np.zeros((H, W, 3))
    img[:] = np.array([0.10, 0.11, 0.13])
    depth = np.full((H, W), -1e9)
    yy, xx = np.mgrid[0:H, 0:W]

    def faces_of(cubes):
        for c in cubes:
            pr = project(c, r, scale, ox, oy)
            for fidx, f in enumerate(FACES):
                poly = [pr[i] for i in f]
                yield fidx, poly

    # the player, opaque, with depth (bigger z is nearer the camera here: v[2] toward the viewer)
    for fidx, poly in sorted(faces_of(player()), key=lambda fp: sum(p[2] for p in fp[1]) / 4):
        mask = Image.new("L", (W, H), 0)
        ImageDraw.Draw(mask).polygon([(p[0], p[1]) for p in poly], fill=255)
        mk = np.array(mask) > 0
        dz = sum(p[2] for p in poly) / 4
        upd = mk & (dz > depth)
        shade = [0.8, 0.9, 0.7, 0.75, 1.0, 0.5][fidx]
        img[upd] = np.array([0.42, 0.40, 0.38]) * shade
        depth[upd] = dz

    def glow(cubes, color, light):
        acc = np.zeros((H, W))
        for fidx, poly in faces_of(cubes):
            mask = Image.new("L", (W, H), 0)
            ImageDraw.Draw(mask).polygon([(p[0], p[1]) for p in poly], fill=255)
            mk = np.array(mask) > 0
            dz = sum(p[2] for p in poly) / 4
            acc += (mk & (dz > depth - 0.5)) * 1.0
        img[:] += np.clip(acc, 0, 3)[..., None] * color[None, None, :] * light

    col = mix(COLOR[k])
    pale = col * 0.6 + 0.4
    fig_shells = [(0.0, 0.10), (0.5, 0.04), (1.2, 0.016)]
    arm_shells = [(0.0, 0.14), (0.45, 0.05), (1.0, 0.02)]
    if figure_alpha > 0.01:
        for g, light in fig_shells:
            _, fig = scene(k, None, g, pose, t)
            glow(fig, pale if g == 0 else col, light * figure_alpha)
    for g, light in arm_shells:
        a, _ = scene(k, g, None, pose, t)
        glow(a, pale if g == 0 else col, light * arms_alpha)
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    if label:
        d = ImageDraw.Draw(im)
        d.text((12, 10), label, fill=(230, 230, 230), font=FONT)
    return im


try:
    FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
except Exception:
    FONT = ImageFont.load_default()


def sheet(k, path):
    tiles = [
        render(k, math.radians(35), math.radians(-18), 1.0, label="formed: figure + arms"),
        render(k, math.radians(35), math.radians(-18), 0.4, t=1.5, label="figure fading"),
        render(k, math.radians(35), math.radians(-18), 0.0, label="arms stay"),
        render(k, math.radians(95), math.radians(-5), 1.0, pose="blow", label="side, mid-blow"),
    ]
    out = Image.new("RGB", (W * len(tiles), H + 40), (20, 20, 24))
    for i, tl in enumerate(tiles):
        out.paste(tl, (i * W, 40))
    ImageDraw.Draw(out).text((14, 8), NAMES[k], fill=(255, 255, 255), font=FONT)
    out.save(path)


if __name__ == "__main__":
    ks = [int(a) for a in sys.argv[2:]] or range(5)
    for k in ks:
        sheet(k, "%s/%s.png" % (sys.argv[1], NAMES[k].lower().replace(" ", "-")))
