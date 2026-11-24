"""Draws the big body's new head and the whole big body, for checking them before they go in game.

    python3 -I tools/preview_bighead.py OUT_DIR

Writes (all in OUT_DIR):
  bighead-heads.png        the original's head | our old head | the new head, front and side, jaw hanging and open
  bighead-fullbody.png     the whole big body with the new head, front, three-quarter, side and back, in the textures' own colours
  bighead-fullbody-lifted.png   the same brightened, to read the dark art by
  bighead-tongue.png       the tongue in the mouth, half out, all the way out and whipping (front and side)
  bighead-tongue.gif       one flick: out, held, back in, and the long pause between (the pause is shown shortened)

A small z-buffer box renderer of Minecraft's model parts (a part's pivot, its Z-Y-X rotation and its scale, each cube's box
unwrap and mirror, as ModelPart.Cube builds them). What it draws is read from what the game will use, not retyped:
  * the original's head: tools/data/bighead_geometry.json (recovered from the jar) and its texture;
  * the new head: client/power/web/WebBigHeadMesh.java (the generated Java; checked cube for cube against that data),
    placed with WebBigHead's SCALE, OFFSET_Y, OFFSET_Z, with the tongue laid out by the same formulas as
    WebBigHead.HeadModel.setupAnim (ported here: keep the two in step) from WebHulk's flick timing constants;
  * the old head and the body: client/power/web/WebHulkMesh.java in WebHulk.shift's frame at full growth, with the
    textures in assets. Standing straight (the game bends the elbows and knees with the walk) and lit by a fixed shade only.
"""
import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

TOOLS = Path(__file__).resolve().parent
PROJECT = TOOLS.parent
WEB = PROJECT / "src" / "client" / "java" / "com" / "jaymospowers" / "client" / "power" / "web"
ENTITY = PROJECT / "src" / "main" / "resources" / "assets" / "jaymospowers" / "textures" / "entity"
DATA = TOOLS / "data"
BACKGROUND = (46, 48, 58)
FLOAT = r"(-?[\d.]+)F"


class Part:
    def __init__(self, name, cubes, pos, rot):
        self.name, self.cubes, self.pos, self.rot = name, cubes, list(pos), list(rot)
        self.scale = [1.0, 1.0, 1.0]
        self.children = []
        self.visible = True


class Tex:
    def __init__(self, image, uv_size):
        self.array = np.asarray(image.convert("RGBA"))
        self.uv_size = uv_size


def jconst(path, name):
    """A float constant of a Java file: NAME = 0.22F."""
    return float(re.search(r"\b" + name + r" = " + FLOAT, (WEB / path).read_text(encoding="utf-8")).group(1))


# ---- reading the Java meshes -----------------------------------------------------------------------------------------------
def parse_java(path):
    """The parts of a generated mesh, in the tree the Java builds (parent by variable name)."""
    text = path.read_text(encoding="utf-8")
    variables = {"root": Part("root", [], (0, 0, 0), (0, 0, 0))}
    for m in re.finditer(r'(?:PartDefinition (\w+) = )?(\w+)\.addOrReplaceChild\("(\w+)", (CubeListBuilder\.create\(\).*?), (PartPose\.[^;]*?)\);', text):
        var, parent, name, cubes_src, pose_src = m.groups()
        cubes, mirror = [], False
        for t in re.finditer(r"\.mirror\((false)?\)|\.texOffs\((\d+), (\d+)\)\.addBox\(([^)]*), new CubeDeformation\(" + FLOAT + r"\)\)", cubes_src):
            if t.group(0).startswith(".mirror"):
                mirror = t.group(1) is None
                continue
            box = [float(x.rstrip("F")) for x in t.group(4).split(", ")]
            cubes.append(dict(uv=(int(t.group(2)), int(t.group(3))), mirror=mirror, box=box, grow=float(t.group(5))))
        pos, rot = (0, 0, 0), (0, 0, 0)
        nums = [float(x) for x in re.findall(FLOAT, pose_src)]
        if "offsetAndRotation" in pose_src:
            pos, rot = nums[:3], nums[3:]
        elif "offset" in pose_src:
            pos = nums
        part = Part(name, cubes, pos, rot)
        variables[parent].children.append(part)
        if var:
            variables[var] = part
    return variables["root"]


def parse_source(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    by = {p["name"]: Part(p["name"], p["cubes"], p["pos"] if p["name"] != "head" else (0, 0, 0), p["rot"]) for p in data["parts"]}
    root = Part("root", [], (0, 0, 0), (0, 0, 0))
    for p in data["parts"]:
        (by[p["parent"]] if p["parent"] else root).children.append(by[p["name"]])
    return root


def find(part, name):
    for c in part.children:
        if c.name == name:
            return c
        found = find(c, name)
        if found:
            return found
    return None


def cubes_of(part):
    out = [(tuple(c["uv"]), tuple(c["box"]), bool(c["mirror"])) for c in part.cubes]
    for child in part.children:
        out += cubes_of(child)
    return out


# ---- drawing -----------------------------------------------------------------------------------------------------------------
def rot_matrix(x, y, z):
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def cube_quads(c):
    """ModelPart.Cube's six polygons: (vertices, four (u, v) texels), as the game builds them."""
    x, y, z, w, h, d = c["box"]
    g = c["grow"]
    x0, y0, z0, x1, y1, z1 = x - g, y - g, z - g, x + w + g, y + h + g, z + d + g
    if c["mirror"]:
        x0, x1 = x1, x0
    v7, v0_, v1_, v2 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v3, v4, v5, v6 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    u, v = c["uv"]
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    quads = [
        ([v4, v0_, v1_, v5], (f6, f11, f8, f12)), ([v7, v3, v6, v2], (f4, f11, f5, f12)), ([v4, v3, v7, v0_], (f5, f10, f6, f11)),
        ([v1_, v2, v6, v5], (f6, f11, f7, f10)), ([v0_, v7, v2, v1_], (f5, f11, f6, f12)), ([v3, v4, v5, v6], (f8, f11, f9, f12)),
    ]
    out = []
    for verts, (a, b, c2, d2) in quads:
        uvs = [(c2, b), (a, b), (a, d2), (c2, d2)]
        if c["mirror"]:
            verts, uvs = verts[::-1], uvs[::-1]
        out.append((verts, uvs))
    return out


def collect(part, tex, out, parent_m=None, parent_t=None):
    """Appends (vertices, uvs, texture) for the part and its children."""
    parent_m = np.eye(3) if parent_m is None else parent_m
    parent_t = np.zeros(3) if parent_t is None else parent_t
    if not part.visible:
        return
    m = parent_m @ rot_matrix(*part.rot) @ np.diag(part.scale)
    t = parent_m @ np.array(part.pos) + parent_t
    for c in part.cubes:
        for verts, uvs in cube_quads(c):
            out.append(([m @ np.array(v) + t for v in verts], uvs, tex))
    for child in part.children:
        collect(child, tex, out, m, t)


def render(polys, yaw, size, zoom, centre, shade=True, lift=0.0, background=BACKGROUND):
    """Orthographic, turned {@code yaw} degrees about the vertical: 0 the front (the viewer in front of the model, -z), 90 the side."""
    a = math.radians(yaw)
    ca, sa = math.cos(a), math.sin(a)
    w, h = size
    img = np.zeros((h, w, 4), np.uint8)
    img[:] = tuple(background) + (255,)
    zbuf = np.full((h, w), 1e9)
    light = np.array([-0.35, -0.75, -0.55])
    light /= np.linalg.norm(light)
    for verts, uvs, tex in polys:
        pts = []
        for p in verts:
            pts.append((w / 2 + (p[0] * ca - p[2] * sa - centre[0]) * zoom, h / 2 + (p[1] - centre[1]) * zoom, p[0] * sa + p[2] * ca))
        n = np.cross(np.array(verts[1]) - np.array(verts[0]), np.array(verts[2]) - np.array(verts[1]))
        nl = np.linalg.norm(n)
        shade_k = 1.0
        if shade and nl > 1e-9:
            n = n / nl
            # the face looks at the viewer (its outward side): orient the normal the way the model is seen from
            toward = np.array([-sa, 0.0, -ca]) * 1.0
            if n @ toward < 0:
                n = -n
            shade_k = 0.6 + 0.4 * max(0.0, float(n @ -light) * 0.5 + 0.5)
        tw, th = tex.array.shape[1], tex.array.shape[0]
        for tri in ((0, 1, 2), (0, 2, 3)):
            pa, pb, pc = (pts[i] for i in tri)
            ua, ub, uc = (uvs[i] for i in tri)
            minx, maxx = int(max(0, min(pa[0], pb[0], pc[0]))), int(min(w - 1, max(pa[0], pb[0], pc[0])))
            miny, maxy = int(max(0, min(pa[1], pb[1], pc[1]))), int(min(h - 1, max(pa[1], pb[1], pc[1])))
            if minx > maxx or miny > maxy:
                continue
            den = (pb[1] - pc[1]) * (pa[0] - pc[0]) + (pc[0] - pb[0]) * (pa[1] - pc[1])
            if abs(den) < 1e-9:
                continue
            xs, ys = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            l1 = ((pb[1] - pc[1]) * (xs - pc[0]) + (pc[0] - pb[0]) * (ys - pc[1])) / den
            l2 = ((pc[1] - pa[1]) * (xs - pc[0]) + (pa[0] - pc[0]) * (ys - pc[1])) / den
            l3 = 1 - l1 - l2
            inside = (l1 >= -0.01) & (l2 >= -0.01) & (l3 >= -0.01)
            if not inside.any():
                continue
            depth = l1 * pa[2] + l2 * pb[2] + l3 * pc[2]
            u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0]
            v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1]
            px = np.clip((u / tex.uv_size[0] * tw).astype(int), 0, tw - 1)
            py = np.clip((v / tex.uv_size[1] * th).astype(int), 0, th - 1)
            colour = tex.array[py, px]
            ok = inside & (colour[..., 3] > 20) & (depth < zbuf[miny:maxy + 1, minx:maxx + 1])
            sub = img[miny:maxy + 1, minx:maxx + 1]
            rgb = colour[..., :3].astype(float) * shade_k
            if lift:
                rgb = np.clip(rgb * lift + 12, 0, 255)
            sub[ok, :3] = rgb[ok].astype(np.uint8)
            zbuf[miny:maxy + 1, minx:maxx + 1][ok] = depth[ok]
    return Image.fromarray(img)


def add_glow(img, polys_glow, yaw, size, zoom, centre):
    """The eyes render type adds the glow texture over the head at full brightness."""
    g = np.asarray(render(polys_glow, yaw, size, zoom, centre, shade=False, background=(0, 0, 0))).astype(int)
    base = np.asarray(img).astype(int)
    base[..., :3] = np.clip(base[..., :3] + g[..., :3] * 2, 0, 255)
    return Image.fromarray(base.astype(np.uint8))


def label(img, text):
    ImageDraw.Draw(img).text((6, 4), text, fill=(235, 235, 240, 255))
    return img


# ---- the head, posed the way the game poses it ------------------------------------------------------------------------------
class Game:
    """The constants the Java uses, read from the Java."""

    def __init__(self):
        self.scale, self.oy, self.oz = (jconst("WebBigHead.java", n) for n in ("SCALE", "OFFSET_Y", "OFFSET_Z"))
        self.lead, self.rest, self.segment = (jconst("WebBigHead.java", n) for n in ("TONGUE_LEAD", "TONGUE_REST", "SEGMENT"))
        self.droop, self.ripple, self.sway, self.flutter = (jconst("WebHulk.java", n) for n in ("TONGUE_DROOP", "TONGUE_RIPPLE", "TONGUE_SWAY", "TONGUE_FLUTTER"))
        self.jaw_rest, self.jaw_breath, self.jaw_flick = (jconst("WebHulk.java", n) for n in ("JAW_REST", "JAW_BREATH", "JAW_FLICK"))
        self.flick_ticks, self.flick_out, self.flick_back, self.every = (jconst("WebHulk.java", n) for n in ("FLICK_TICKS", "FLICK_OUT", "FLICK_BACK", "TONGUE_EVERY"))
        self.body_lift, self.leg_lift = jconst("WebHulkMesh.java", "BODY_LIFT"), jconst("WebHulkMesh.java", "LEG_LIFT")
        self.arm_out, self.leg_out, self.head_down, self.head_forward = (jconst("WebHulk.java", n) for n in ("ARM_OUT", "LEG_OUT", "HEAD_DOWN", "HEAD_FORWARD"))

    def flick_out_at(self, f):
        """WebHulk.flickOut."""
        if f < 0:
            return 0.0
        out = f / self.flick_out if f < self.flick_out else (1 - (f - self.flick_back) / (1 - self.flick_back) if f > self.flick_back else 1.0)
        out = min(max(out, 0.0), 1.0)
        return out * out * (3 - 2 * out)

    def pose_head(self, root, jaw, out=0.0, whip=0.0, age=0.0):
        """WebBigHead.HeadModel.setupAnim for a built, full-grown head (the tongue ported from tongueOut)."""
        head = find(root, "head")
        head.scale = [self.scale] * 3
        head.pos = [0.0, self.oy, self.oz]
        find(root, "jaw").rot[0] = jaw
        t = [find(root, "tongue%d" % i) for i in range(4)]
        t[0].visible = out > 0.02
        if out > 0.02:
            t[0].scale[2] = out
            t[0].rot[0] = self.rest - jaw * self.lead
            for i in range(1, 4):
                f = i / 3.0
                t[i].rot[0] = self.segment * (self.droop * (1 - whip * 0.5) + math.sin(age * 0.23 - i * 0.6) * self.ripple * f)
                t[i].rot[1] = self.segment * (math.sin(age * 0.13 - i * 0.45) * self.sway * (0.3 + f) * 0.5 + math.sin(age * 1.9 - i * 0.7) * self.flutter * f * whip)
        return head


def textures():
    return dict(
        hulk=Tex(Image.open(ENTITY / "web_symbiote_hulk.png"), (256, 128)),
        head=Tex(Image.open(ENTITY / "web_symbiote_bighead.png"), (256, 192)),
        glow=Tex(Image.open(ENTITY / "web_symbiote_bighead_glow.png"), (256, 192)),
        source=Tex(Image.open(DATA / "bighead_source.png"), (256, 192)),
    )


def new_head_polys(game, tex, jaw, out=0.0, whip=0.0, age=0.0, at=(0.0, 0.0, 0.0), glow=False):
    root = parse_java(WEB / "WebBigHeadMesh.java")
    head = game.pose_head(root, jaw, out, whip, age)
    polys = []
    collect(head, tex["glow"] if glow else tex["head"], polys, np.eye(3), np.array(at, float))
    return polys


def head_tile(game, tex, which, yaw, jaw, size=(300, 300), zoom=11.0, lift=0.0):
    if which == "source":
        root = parse_source(DATA / "bighead_geometry.json")
        find(root, "jaw").rot[0] = jaw
        find(root, "tongue0").visible = False
        head = find(root, "head")
        head.scale = [game.scale] * 3
        polys = []
        collect(head, tex["source"], polys)
        return render(polys, yaw, size, zoom, (0, 0), lift=lift)
    if which == "old":
        old = parse_java(WEB / "WebHulkMesh.java")
        find(old, "jaw").rot[0] = jaw
        polys = []
        for name in ("skull", "cranium", "muzzle", "fangs", "fangs_r", "fangs_l", "throat", "jaw"):
            collect(find(find(old, "head"), name), tex["hulk"], polys)
        return render(polys, yaw, size, zoom, (0, -2), lift=lift)
    polys = new_head_polys(game, tex, jaw)
    img = render(polys, yaw, size, zoom, (0, -2), lift=lift)
    return add_glow(img, new_head_polys(game, tex, jaw, glow=True), yaw, size, zoom, (0, -2))


def body_polys(game, tex, head_jaw, out=0.0, whip=0.0, age=0.0, glow=False):
    """The big body in WebHulk.shift's frame (full growth, standing straight) with the new head on its neck."""
    body = parse_java(WEB / "WebHulkMesh.java")
    shifts = {"body": (0, -game.body_lift, 0), "right_arm": (-game.arm_out, -game.body_lift, 0), "left_arm": (game.arm_out, -game.body_lift, 0),
              "right_leg": (-game.leg_out, -game.leg_lift, 0), "left_leg": (game.leg_out, -game.leg_lift, 0)}
    polys = []
    for part in body.children:
        if part.name == "head":
            continue
        d = shifts.get(part.name, (0, 0, 0))
        part.pos = [part.pos[0] + d[0], part.pos[1] + d[1], part.pos[2] + d[2]]
        if not glow:
            collect(part, tex["hulk"], polys)
    neck = (0.0, -game.body_lift + game.head_down, -game.head_forward)
    polys += new_head_polys(game, tex, head_jaw, out, whip, age, at=neck, glow=glow)
    return polys


def full_body(game, tex, lift=0.0):
    jaw = game.jaw_rest
    tiles = []
    size = (400, 600)
    for yaw, name in ((0, "front"), (35, "three-quarter"), (90, "side"), (180, "back")):
        img = render(body_polys(game, tex, jaw), yaw, size, 12.0, (0, 4), lift=lift)
        if yaw != 180:
            img = add_glow(img, body_polys(game, tex, jaw, glow=True), yaw, size, 12.0, (0, 4))
        tiles.append(label(img, "big body + new head, %s%s" % (name, ", brightened" if lift else "")))
    sheet = Image.new("RGB", (400 * 4, 600), BACKGROUND)
    for i, t in enumerate(tiles):
        sheet.paste(t.convert("RGB"), (i * 400, 0))
    return sheet


def tongue_frame(game, tex, f, tick, yaw, size=(340, 340), zoom=17.0):
    jaw = game.jaw_rest + game.jaw_flick * math.sin(math.pi * f if f >= 0 else 0)
    out = game.flick_out_at(f)
    whip = math.sin(math.pi * f) if f >= 0 else 0.0
    polys = new_head_polys(game, tex, jaw, out, whip, tick)
    img = render(polys, yaw, size, zoom, (0, 0))
    return add_glow(img, new_head_polys(game, tex, jaw, out, whip, tick, glow=True), yaw, size, zoom, (0, 0)), out


def tongue_assets(game, tex, out_dir):
    cells = []
    for f, name in ((-1.0, "in the mouth"), (0.15, "shooting out"), (0.45, "out"), (0.8, "drawing back")):
        row = []
        for yaw in (0, 90):
            img, out = tongue_frame(game, tex, f, 5.0 + f * 12, yaw)
            row.append(label(img, "%s (flick %.2f, out %.2f) %s" % (name, f, out, "front" if yaw == 0 else "side")))
        cells.append(row)
    sheet = Image.new("RGB", (340 * 4, 340 * 2), BACKGROUND)
    for c, row in enumerate(cells):
        for r, t in enumerate(row):
            sheet.paste(t.convert("RGB"), (c * 340, r * 340))
    sheet.save(out_dir / "bighead-tongue.png")
    # one flick, tick by tick (two frames a tick), then the pause (the real one is TONGUE_EVERY - FLICK_TICKS ticks long)
    frames, durations = [], []
    n = int(game.flick_ticks)
    for step in range(n * 2 + 1):
        f = step / (n * 2.0)
        a, _ = tongue_frame(game, tex, f, step / 2.0, 20, size=(300, 300), zoom=15.0)
        b, _ = tongue_frame(game, tex, f, step / 2.0, 90, size=(300, 300), zoom=15.0)
        frame = Image.new("RGB", (600, 300), BACKGROUND)
        frame.paste(a.convert("RGB"), (0, 0))
        frame.paste(b.convert("RGB"), (300, 0))
        ImageDraw.Draw(frame).text((6, 4), "tick %.1f of %d" % (step / 2.0, n), fill=(235, 235, 240))
        frames.append(frame)
        durations.append(25)
    pause_ticks = int(game.every - game.flick_ticks)
    held = frames[0].copy()
    ImageDraw.Draw(held).rectangle((0, 280, 600, 300), fill=BACKGROUND)
    ImageDraw.Draw(held).text((6, 284), "pause: %d ticks (%.1f s), shown shortened" % (pause_ticks, pause_ticks / 20.0), fill=(235, 235, 240))
    frames.append(held)
    durations.append(1500)
    frames[0].save(out_dir / "bighead-tongue.gif", save_all=True, append_images=frames[1:], duration=durations, loop=0, optimize=False)


def main(out_dir):
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    game = Game()
    tex = textures()
    # the generated Java must be the recovered geometry, cube for cube
    source = sorted(cubes_of(parse_source(DATA / "bighead_geometry.json")))
    java = sorted(cubes_of(parse_java(WEB / "WebBigHeadMesh.java")))
    assert len(source) == len(java) == 80 and source == java, "generated Java differs from the recovered cubes"
    for lift, name in ((0.0, "bighead-heads.png"), (2.6, "bighead-heads-lifted.png")):
        rows = []
        for jaw in (game.jaw_rest, 0.72):
            for yaw in (0, 90):
                rows.append([label(head_tile(game, tex, w, yaw, jaw, lift=lift), "%s %s jaw %.2f%s" % (w, "front" if yaw == 0 else "side", jaw, ", brightened" if lift else ""))
                             for w in ("source", "old", "new")])
        sheet = Image.new("RGB", (900, 1200), BACKGROUND)
        for r, row in enumerate(rows):
            for c, t in enumerate(row):
                sheet.paste(t.convert("RGB"), (c * 300, r * 300))
        sheet.save(out_dir / name)
    full_body(game, tex).save(out_dir / "bighead-fullbody.png")
    full_body(game, tex, lift=2.6).save(out_dir / "bighead-fullbody-lifted.png")
    tongue_assets(game, tex, out_dir)
    print("wrote the previews in", out_dir)


if __name__ == "__main__":
    main(sys.argv[1])
