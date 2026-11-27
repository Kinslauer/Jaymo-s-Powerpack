"""Draws the red strain's slender body with our tendrils and weapon arm on it, for Andy to judge before it goes in game.

    python3 -I tools/preview_slender.py OUT_DIR

Writes (all in OUT_DIR):
  slender-body.png          front, three-quarter, side and back: the slender body as it stands in the game's frame, our tendrils
                            out of its back (the red strain's: hooked blades on every tip) and the weapon arm's spear on the right
  slender-body-lifted.png   the same brightened, to read the dark art by
  slender-vs-big.png        the slender body beside the black big body (#2080's) and a plain player, to scale
  slender-stance.png        its hunch (WebSlender.hunch): standing, and curled over while its tendrils work (three-quarter, side)
  big-body-tendrils.png     the black big body with its own crown of tendrils and strands (WebHulk.TENDRIL_REST, STRANDS), brightened

What it draws is read from what the game will use, not retyped:
  * the body: tools/data/slender_geometry.json, checked cube for cube against the generated WebSlenderMesh.java, placed by
    WebSlender's SCALE and the joints in WebSlenderMesh (the frame WebSlender.shift moves the player model to);
  * the tendrils: WebTendrilLayer's SEGMENTS, SEGMENT_LENGTH, TIP, posed at rest on this body by WebSlender.TENDRIL_REST
    (each roots on the back or a shoulder, turns and lifts by its row's angles, curls by its row's curl, its own thickness and
    length; the sway, ripple and pulse are left out), drawn off the body's pivot as the layer draws them, and the thin
    strands of WebSlender.STRANDS among them (the same mesh, no blades), with the red strain's hooked blade (WebTendrilLayer.blade, kind 1) on each tip;
  * the weapon arm: WebMorphLayer.weapon's default spear, at the right hand, pointing down the arm (its real placement follows
    the strike; approximated here, and labelled so). Textures are the real red twins in assets.
Standing straight, lit by a fixed shade only (the game bends elbows and knees with the walk).
"""
import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))
import preview_bighead as pb  # noqa: E402  (the box renderer, the Java readers)

WEB, ENTITY, DATA = pb.WEB, pb.ENTITY, pb.DATA
Part, Tex, collect, render, add_glow, label, find = pb.Part, pb.Tex, pb.collect, pb.render, pb.add_glow, pb.label, pb.find
BACKGROUND = pb.BACKGROUND


def jfloat(path, name):
    return pb.jconst(path, name)


def jarray(path, name):
    """A float[][] constant of a Java file, as a list of rows."""
    text = (WEB / path).read_text(encoding="utf-8")
    body = re.search(name + r" = \{(.*?)\};", text, re.S).group(1)
    return [[float(x) for x in re.findall(pb.FLOAT, row)] for row in re.findall(r"\{([^{}]*)\}", body)]


# ---- the body ----------------------------------------------------------------------------------------------------------------
def source_tree(drop_weapons=True):
    data = json.loads((DATA / "slender_geometry.json").read_text(encoding="utf-8"))
    by = {p["name"]: Part(p["name"], p["cubes"], p["pos"], p["rot"]) for p in data["parts"]}
    root = Part("root", [], (0, 0, 0), (0, 0, 0))
    for p in data["parts"]:
        (by[p["parent"]] if p["parent"] else root).children.append(by[p["name"]])
    return root, by


def java_cubes():
    text = (WEB / "WebSlenderMesh.java").read_text(encoding="utf-8")
    out = []
    for m in re.finditer(r'addOrReplaceChild\("(\w+)", (CubeListBuilder\.create\(\).*?), PartPose', text):
        mirror = False
        for t in re.finditer(r"\.mirror\((false)?\)|\.texOffs\((\d+), (\d+)\)\.addBox\(([^)]*), new CubeDeformation\(" + pb.FLOAT + r"\)\)", m.group(2)):
            if t.group(0).startswith(".mirror"):
                mirror = t.group(1) is None
                continue
            out.append((m.group(1), (int(t.group(2)), int(t.group(3))), tuple(float(x.rstrip("F")) for x in t.group(4).split(", ")), mirror))
    return sorted(out)


class Frame:
    """WebSlender's frame: where the player model's joints go (pixels, y down, feet on 24), and its scale."""

    def __init__(self):
        m = "WebSlenderMesh.java"
        self.s = jfloat("WebSlender.java", "SCALE")
        g = jfloat(m, "GROUND")
        self.ground = g
        self.neck = (0.0, g + (jfloat(m, "NECK_Y") - g) * self.s, jfloat(m, "HEAD_Z") * self.s)
        self.shoulder = (jfloat(m, "SHOULDER_X") * self.s, g + (jfloat(m, "SHOULDER_Y") - g) * self.s)
        self.hip = (jfloat(m, "HIP_X") * self.s, g + (jfloat(m, "HIP_Y") - g) * self.s)
        self.neck_y_body = jfloat(m, "NECK_Y")


CUT = ("head", "right_arm", "left_arm", "right_leg", "left_leg")


def prune(part):
    """Cuts the other joints' subtrees off (they hang off their own holders), all the way down."""
    part.children = [c for c in part.children if c.name not in CUT]
    for c in part.children:
        prune(c)


def body_tree(frame):
    """The slender body cut at the joints and placed in the game's frame (the holders scaled), as WebSlenderMesh + WebSlender do."""
    _, by = source_tree()
    s = frame.s

    def holder(name, at, subtree, inner):
        h = Part(name, [], at, (0, 0, 0))
        h.scale = [s, s, s]
        top = by[subtree]
        top.pos = list(inner)
        prune(top)
        h.children = [top]
        return h

    hips_inner = (0.0, by["hips"].pos[1] - frame.neck_y_body, 0.0)
    parts = [
        holder("head", (0.0, frame.neck[1], 0.0), "head", (0.0, 0.0, frame.neck[2] / s)),
        holder("body", (0.0, frame.neck[1], 0.0), "hips", hips_inner),
        holder("right_arm", (-frame.shoulder[0], frame.shoulder[1], 0.0), "right_arm", (0.0, 0.0, 0.0)),
        holder("left_arm", (frame.shoulder[0], frame.shoulder[1], 0.0), "left_arm", (0.0, 0.0, 0.0)),
        holder("right_leg", (-frame.hip[0], frame.hip[1], 0.0), "right_leg", (0.0, 0.0, 0.0)),
        holder("left_leg", (frame.hip[0], frame.hip[1], 0.0), "left_leg", (0.0, 0.0, 0.0)),
    ]
    return parts


# ---- our tendrils and blades -------------------------------------------------------------------------------------------------
class Swatch:
    """WebTubes' swatches: the flesh texture's quadrants (BLACK 0, SHEEN 1, BONE 2, RED 3)."""

    def __init__(self, image):
        self.tex = Tex(image, (1.0, 1.0))

    def uv(self, swatch, k):
        su, sv = (swatch % 2) * 0.5, (swatch // 2) * 0.5
        u0 = su + 0.05 + k * 0.1
        return [(u0, sv + 0.05), (u0 + 0.1, sv + 0.05), (u0 + 0.1, sv + 0.45), (u0, sv + 0.45)]


def prism(out, sw, a, b, side, wa, ta, wb, tb, swatch):
    """WebTubes.prism, in pixels."""
    a, b = np.array(a, float), np.array(b, float)
    d = b - a
    if d @ d < 1e-8:
        return
    d /= np.linalg.norm(d)
    u = None if side is None else np.array(side, float) - d * (np.array(side, float) @ d)
    if u is None or u @ u < 1e-6:
        u = np.cross(d, (0, 1, 0)) if abs(d[1]) < 0.95 else np.cross(d, (1, 0, 0))
    u /= np.linalg.norm(u)
    v = np.cross(d, u)
    v /= np.linalg.norm(v)
    ea, eb = [u * wa, v * ta, -u * wa, -v * ta], [u * wb, v * tb, -u * wb, -v * tb]
    for k in range(4):
        n = (k + 1) % 4
        out.append(([a + ea[k], a + ea[n], b + eb[n], b + eb[k]], sw.uv(swatch, k), sw.tex))
    for at, e in ((a, ea), (b, eb)):
        out.append(([at + e[0], at + e[1], at + e[2], at + e[3]], sw.uv(swatch, 0), sw.tex))


def hook(out, sw, at, d, up, width, hook_len, curve):
    """WebTendrilLayer.blade's hook (kind 1)."""
    across = np.cross(d, up)
    across = np.array((1.0, 0, 0)) if across @ across < 1e-6 else across / np.linalg.norm(across)
    up = np.cross(across, d)
    up /= np.linalg.norm(up)
    prev = at
    for i in range(1, 7):
        f = i / 6.0
        p = at + d * hook_len * f + up * curve * f * f
        w0, w1 = width * (1 - 0.9 * (f - 1 / 6.0)), width * (1 - 0.9 * f) + 0.03
        prism(out, sw, prev, p, up, w0, 0.4, w1, 0.3, 1)
        prism(out, sw, prev - up * w0 * 0.85, p - up * w1 * 0.85, up, 0.35, 0.25, 0.2, 0.15, 2)
        prev = p


def spear(out, sw, at, d, g=1.0):
    """WebMorphLayer.weapon's default: a socket, a long leaf of a point, two pale barbs."""
    across = np.cross(d, (1.0, 0, 0)) if abs(d[0]) < 0.9 else np.cross(d, (0, 0, 1.0))
    across /= np.linalg.norm(across)
    base = at + d * 2.0 * g
    prism(out, sw, at, base, None, 1.0 * g, 1.0 * g, 1.3 * g, 1.3 * g, 0)
    belly, tip = base + d * 4.0 * g, base + d * 14.0 * g
    prism(out, sw, base, belly, across, 1.3 * g, 1.1 * g, 2.6 * g, 1.2 * g, 1)
    prism(out, sw, belly, tip, across, 2.6 * g, 1.2 * g, 0.05, 0.05, 0)
    for side in (-1, 1):
        prism(out, sw, belly + across * side * 2.2 * g, tip, across, 0.4 * g, 0.4 * g, 0.05, 0.05, 2)
        barb = base + across * side * 1.2 * g
        prism(out, sw, barb, barb + across * side * 3.0 * g - d * 3.0 * g, None, 0.7 * g, 0.7 * g, 0.05, 0.05, 2)


def chain(polys, tex, start, m, segs, seg_len, tip, curl):
    """One tendril's segments (WebTendrilLayer's mesh), from {@code start} in the frame {@code m}; returns the tip's frame."""
    pos = start
    for k in range(segs):
        w = 3.0 if k < segs - len(tip) else tip[k - (segs - len(tip))]
        c = dict(uv=(0, (k % 2) * 8), mirror=False, box=(-w / 2, -w / 2, 0.0, w, w, seg_len), grow=0.0)
        if k > 0:
            pos = pos + m @ np.array((0.0, 0.0, seg_len - 0.4))
            m = m @ pb.rot_matrix(curl, 0.0, 0.0)
        for verts, uvs in pb.cube_quads(c):
            polys.append(([m @ np.array(v) + pos for v in verts], uvs, tex))
    return pos, m


def grown(rows):
    """WebSlender.grown with WebHulk's TENDRIL_WIDE, _DEEP, _THICK, _LONG: the big body's crown."""
    wide, deep, thick, length = (jfloat("WebHulk.java", n) for n in ("TENDRIL_WIDE", "TENDRIL_DEEP", "TENDRIL_THICK", "TENDRIL_LONG"))
    return [[r[0] * wide, r[1], r[2] + deep, r[3], r[4], r[5], r[6] * thick, r[7] * length] for r in rows]


def tendril_polys(frame, tex, sw, strands=True, big=False, neck=None):
    """Our tendrils at rest, grown, off the body's pivot (the neck) as WebSlender.TENDRIL_REST poses them (WebHulk's on the
    big body), with hooked tips on the red ones (sw given); then the strands, the thin ones that only sway."""
    text = (WEB / "WebTendrilLayer.java").read_text(encoding="utf-8")
    segs = int(re.search(r"SEGMENTS = (\d+)", text).group(1))
    seg_len = jfloat("WebTendrilLayer.java", "SEGMENT_LENGTH")
    tip = [float(x) for x in re.findall(pb.FLOAT, re.search(r"TIP = \{([^}]*)\}", text).group(1))]
    width, hook_len, curve = (jfloat("WebTendrilLayer.java", n) for n in ("BLADE_WIDTH", "BLADE_HOOK", "BLADE_CURVE"))
    full = 1.3  # WebTendrilLayer: grown thickness and length
    polys = []
    body_at = np.array((0.0, frame.neck[1] if neck is None else neck, 0.0))
    rest, strand_rows = jarray("WebSlender.java", "TENDRIL_REST"), jarray("WebSlender.java", "STRANDS")
    if big:
        rest, strand_rows = grown(rest), grown(strand_rows)
    for r in rest:
        m = pb.rot_matrix(r[4], r[3], 0.0) @ np.diag((full * r[6], full * r[6], full * r[7]))
        pos, m = chain(polys, tex, body_at + np.array(r[:3]), m, segs, seg_len, tip, r[5])
        end = m @ np.array((0.0, 0.0, seg_len - 0.6)) + pos
        d = m @ np.array((0.0, 0.0, 1.0))
        up = m @ np.array((0.0, -1.0, 0.0))
        if sw is not None:
            hook(polys, sw, end, d / np.linalg.norm(d), up / np.linalg.norm(up), width, hook_len, curve)
    if strands:
        for r in strand_rows:
            m = pb.rot_matrix(r[4], r[3], 0.0) @ np.diag((full * r[6], full * r[6], full * r[7]))
            chain(polys, tex, body_at + np.array(r[:3]), m, segs, seg_len, tip, r[5])
    return polys


def scene(frame, tex, glow=False, tendrils=True, weapon=True):
    polys = []
    for part in body_tree(frame):
        collect(part, tex["glow" if glow else "body"], polys)
    if glow:
        return polys
    if tendrils:
        polys += tendril_polys(frame, tex["tendril"], tex["flesh"])
    if weapon:
        # the right hand: the forearm's fingertips (the original's units), in the frame
        hand = np.array((-frame.shoulder[0], frame.shoulder[1] + (10.5 + 12.5) * frame.s, 0.0))
        spear(polys, tex["flesh"], hand, np.array((0.0, 1.0, -0.35)) / math.hypot(1.0, 0.35), jfloat("WebMorphLayer.java", "WEAPON_SCALE_ARMOR"))
    return polys


def groups(frame, tex, elbow=0.0, thigh=0.0, knee=0.0, drop=0.0):
    """The slender scene in groups that move apart in the hunch: the upper body (with the tendrils), the head, each arm
    (the spear on the right), the legs; the elbows bent {@code elbow}, the thighs raised {@code thigh} and the knees folded
    {@code knee} (radians), all of it sunk {@code drop} pixels."""
    out = {}
    for part in body_tree(frame):
        for name in ("right_forearm", "left_forearm"):
            if find(part, name):
                find(part, name).rot[0] -= elbow
        if part.name in ("right_leg", "left_leg"):
            part.rot[0] -= thigh
            find(part, part.name.split("_")[0] + "_shin").rot[0] += knee
        part.pos[1] += drop
        polys = []
        collect(part, tex["body"], polys)
        out[part.name] = polys
    out["body"] += tendril_polys(frame, tex["tendril"], tex["flesh"])
    hand = np.array((-frame.shoulder[0], frame.shoulder[1] + (10.5 + 12.5) * frame.s, 0.0))
    if not elbow:
        spear(out["right_arm"], tex["flesh"], hand, np.array((0.0, 1.0, -0.35)) / math.hypot(1.0, 0.35), jfloat("WebMorphLayer.java", "WEAPON_SCALE_ARMOR"))
    return out


def turned(polys, about, angle):
    m = pb.rot_matrix(angle, 0.0, 0.0)
    about = np.array(about, float)
    return [([m @ (np.array(v) - about) + about for v in verts], uvs, t) for verts, uvs, t in polys]


def hunched(frame, tex, curl):
    """WebSlender.hunch standing (curl 0) or reared up while its tendrils work (curl 1): the back bent from the hips, the head
    craned up again, the arms hanging forward, the elbows crooked, the thighs forward and the knees folded, sunk on them.
    (The spear is left off here, the arm crooked.)"""
    bend = (lambda a, b: a + (b - a) * curl)(jfloat("WebSlender.java", "HUNCH"), jfloat("WebSlender.java", "HUNCH_TENDRILS"))
    head_up, arms = jfloat("WebSlender.java", "HEAD_UP"), jfloat("WebSlender.java", "ARMS_FORWARD")
    # the elbows: WebHulk's rest bend and WebSlender.ELBOWS; the knees: twice the thighs' raise (WebHulk.limbs, KNEE_CROUCH)
    thigh = jfloat("WebSlender.java", "THIGHS")
    drop = jfloat("WebSlender.java", "CROUCH_DROP")
    g = groups(frame, tex, jfloat("WebHulk.java", "ELBOW_REST") + jfloat("WebSlender.java", "ELBOWS"), thigh,
               thigh * jfloat("WebHulk.java", "KNEE_CROUCH"), drop)
    hip = (0.0, frame.hip[1] + drop, 0.0)
    rot = pb.rot_matrix(bend, 0.0, 0.0)
    def carried(point):
        return rot @ (np.array(point, float) - np.array(hip)) + np.array(hip)
    polys = turned(g["body"], hip, bend)
    polys += turned(turned(g["head"], hip, bend), carried((0.0, frame.neck[1] + drop, 0.0)), -bend * head_up)
    for side, name in ((-1, "right_arm"), (1, "left_arm")):
        shoulder = carried((side * frame.shoulder[0], frame.shoulder[1] + drop, 0.0))
        polys += turned(turned(g[name], hip, bend), shoulder, -(bend + arms))
    polys += g["right_leg"] + g["left_leg"]
    return polys


def stance_sheet(frame, tex, lift):
    size, zoom, centre = (440, 640), 10.5, (0, 1)
    tiles = []
    for curl, name in ((0.0, "standing, hunched"), (1.0, "tendrils at work, reared up")):
        for yaw in (35, 90):
            img = render(hunched(frame, tex, curl), yaw, size, zoom, centre, lift=lift)
            tiles.append(label(img, "red slender body, %s (%s)" % (name, "three-quarter" if yaw == 35 else "side")))
    return sheet(tiles, *size)


def big_sheet(lift):
    """The black big body (#2080's, its head too) with its new crown of tendrils and strands (WebHulk.TENDRIL_REST, STRANDS)."""
    game = pb.Game()
    btex = pb.textures()
    tendril = Tex(Image.open(ENTITY / "web_symbiote_tendril.png"), (32, 32))
    size, zoom, centre = (440, 700), 9.5, (0, -2)
    tiles = []
    for yaw, name in ((0, "front"), (35, "three-quarter"), (90, "side"), (180, "back")):
        polys = pb.body_polys(game, btex, game.jaw_rest) + tendril_polys(None, tendril, None, big=True, neck=-game.body_lift)
        img = render(polys, yaw, size, zoom, centre, lift=lift)
        if yaw != 180:
            img = add_glow(img, pb.body_polys(game, btex, game.jaw_rest, glow=True), yaw, size, zoom, centre)
        tiles.append(label(img, "black big body, its new tendrils, %s" % name))
    return sheet(tiles, *size)


def textures():
    flesh = Image.open(ENTITY / "web_symbiote_flesh_red.png")
    return dict(body=Tex(Image.open(ENTITY / "web_symbiote_slender.png"), (256, 256)),
                glow=Tex(Image.open(ENTITY / "web_symbiote_slender_glow.png"), (256, 256)),
                tendril=Tex(Image.open(ENTITY / "web_symbiote_tendril_red.png"), (32, 32)),
                flesh=Swatch(flesh), source=Tex(Image.open(DATA / "slender_source.png"), (256, 256)))


def sheet(tiles, w, h):
    out = Image.new("RGB", (w * len(tiles), h), BACKGROUND)
    for i, t in enumerate(tiles):
        out.paste(t.convert("RGB"), (i * w, 0))
    return out


def views(frame, tex, lift=0.0, note=""):
    size, zoom, centre = (440, 700), 10.5, (0, -1)
    tiles = []
    for yaw, name in ((0, "front"), (35, "three-quarter"), (90, "side"), (180, "back")):
        img = render(scene(frame, tex), yaw, size, zoom, centre, lift=lift)
        if yaw != 180:
            img = add_glow(img, scene(frame, tex, glow=True), yaw, size, zoom, centre)
        tiles.append(label(img, "red slender body, %s%s (tendrils at rest; spear placement approximated)" % (name, note)))
    return sheet(tiles, *size)


def main(out_dir):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    root, _ = source_tree()
    src = sorted((p.name, tuple(c["uv"]), tuple(float(x) for x in c["box"]), bool(c["mirror"])) for p in pb_walk(root) for c in p.cubes)
    assert src == java_cubes(), "generated Java differs from the recovered cubes"
    frame = Frame()
    tex = textures()
    views(frame, tex).save(out_dir / "slender-body.png")
    views(frame, tex, lift=2.2, note=", brightened").save(out_dir / "slender-body-lifted.png")
    stance_sheet(frame, tex, 1.6).save(out_dir / "slender-stance.png")
    big_sheet(2.4).save(out_dir / "big-body-tendrils.png")
    # to scale: a plain player (the 1.8-block box outline), the slender body, the big body
    size, zoom, centre = (360, 620), 11.0, (0, 2)
    tiles = []
    player = Image.new("RGBA", size, BACKGROUND + (255,))
    d = ImageDraw.Draw(player)
    top, bottom = size[1] / 2 + (-8 - centre[1]) * zoom, size[1] / 2 + (24 - centre[1]) * zoom
    d.rectangle((size[0] / 2 - 4 * zoom, top, size[0] / 2 + 4 * zoom, bottom), outline=(200, 200, 210))
    tiles.append(label(player, "a player (32 px: head top to feet)"))
    tiles.append(label(render(scene(frame, tex), 0, size, zoom, centre, lift=1.6), "red slender body (with tendrils)"))
    game = pb.Game()
    big = pb.render(pb.body_polys(game, pb.textures(), game.jaw_rest), 0, size, zoom, centre, lift=1.6)
    tiles.append(label(big, "black big body (#2080)"))
    for t in tiles:
        ImageDraw.Draw(t).line((0, size[1] / 2 + (24 - centre[1]) * zoom, size[0], size[1] / 2 + (24 - centre[1]) * zoom), fill=(90, 90, 100))
    sheet(tiles, *size).save(out_dir / "slender-vs-big.png")
    print("wrote the previews in", out_dir, "(scale %.2f, head top %.1f px, %.2f blocks)" % (frame.s, frame.neck[1] - 15 * frame.s,
                                                                                         (24 - (frame.neck[1] - 15 * frame.s)) / 16 * 0.9375))


def pb_walk(part):
    for c in part.children:
        yield c
        yield from pb_walk(c)


if __name__ == "__main__":
    main(sys.argv[1])
