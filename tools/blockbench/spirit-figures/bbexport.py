# Writes each spirit figure as a .bbmodel (Blockbench 4.x, bedrock entity, y up; x mirrored as Blockbench shows ModelParts),
# with the manifested figure's moves keyed as animations: idle, speak, manifest, level_up, fade. The curves are the ones
# SpiritFigure.Model poses in code (figures.ANIMATIONS names them); Blockbench rotations are the model's with x and y negated.
import json, math, uuid, os, sys
from figures import FIG, NAMES, ANIMATIONS, QUIET

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
DEG = 180.0 / math.pi
# SpiritFigure's constants (keep in step with SpiritFigure.java.in)
NOD, TILT, POINT_X, POINT_SWING, POINT_TURN, HANG_SWAY, TALK_SPEED = 0.08, 0.06, -0.85, 0.28, 0.35, 0.08, 0.3
BREATH, BREATH_SPEED = 0.035, 0.08
LEVEL_RAISE, LEVEL_SPREAD = -2.75, 0.45
FORM_SPREAD, FADE_BOW, RISE_FROM = 0.8, 0.35, 4.0


def rot(xr, yr, zr):
    """A model rotation (radians) as Blockbench's (degrees, x and y negated)."""
    return {"x": round(-xr * DEG, 2), "y": round(-yr * DEG, 2), "z": round(zr * DEG, 2)}


def kf(channel, time, pt):
    return dict(channel=channel, data_points=[pt], uuid=str(uuid.uuid4()), time=round(time, 3), color=-1, interpolation="linear")


def poses(name, t):
    """Each bone's rotation (and the figure's position) at t seconds into animation name, as SpiritFigure.Model poses it."""
    ticks = t * 20.0
    talk = 1.0 if name == "speak" else 0.0
    unformed = 0.0
    up = 0.0
    if name == "manifest":
        unformed = 1.0 - min(1.0, t / ANIMATIONS[name]["length"])
    if name == "fade":
        unformed = min(1.0, t / ANIMATIONS[name]["length"])
    if name == "level_up":
        a = ticks
        up = (min(1.0, a / 6.0)) if a < 6 else 1.0 - max(0.0, min(1.0, (a - 24.0) / 10.0))
    breath = BREATH * math.sin(ticks * BREATH_SPEED)
    rx = (1 - talk) * (-0.1 + breath) + talk * (POINT_X - POINT_SWING * math.sin(ticks * TALK_SPEED))
    ry = talk * (POINT_TURN + 0.15 * math.sin(ticks * TALK_SPEED * 0.7))
    rz = (1 - talk) * 0.12 + talk * -0.1
    lx = 0.05 - breath - talk * (0.25 + HANG_SWAY * math.sin(ticks * TALK_SPEED + 1.0))
    lz = -0.12
    rx, lx = rx + (LEVEL_RAISE - rx) * up, lx + (LEVEL_RAISE - lx) * up
    rz, lz = rz + (LEVEL_SPREAD - rz) * up, lz + (-LEVEL_SPREAD - lz) * up
    rz += FORM_SPREAD * unformed
    lz -= FORM_SPREAD * unformed
    sx = talk * NOD * math.sin(ticks * TALK_SPEED * 1.8) + (FADE_BOW * unformed if name == "fade" else 0.0)
    sz = talk * TILT * math.sin(ticks * TALK_SPEED * 1.03)
    return {
        "skull": rot(sx, 0, sz),
        "right_arm": rot(rx, ry, rz),
        "left_arm": rot(lx, 0, lz),
        "tail": rot(0.03 * math.sin(ticks * 0.07), 0, 0.015 * math.sin(ticks * 0.049)),
        # (model y is down, Blockbench's up: the rise from a crouch is a drop here)
        "figure": {"x": 0, "y": round(-RISE_FROM * unformed * unformed + 0.25 * math.sin(ticks * BREATH_SPEED), 3), "z": 0},
    }


def animations(groups):
    anims = []
    for name, spec in ANIMATIONS.items():
        length = spec["length"]
        steps = max(4, int(round(length * 10)))
        animators = {}
        for bone in ("skull", "right_arm", "left_arm", "tail", "figure"):
            frames = []
            for i in range(steps + 1):
                t = length * i / steps
                p = poses(name, t)[bone]
                frames.append(kf("position" if bone == "figure" else "rotation", t, p))
            animators[groups[bone]["uuid"]] = dict(name=bone, type="bone", keyframes=frames)
        anims.append(dict(uuid=str(uuid.uuid4()), name=name, loop="loop" if spec["loop"] else "hold", override=False, length=length,
                          snapping=20, selected=False, anim_time_update="", blend_weight="", start_delay="", loop_delay="",
                          animators=animators))
    return anims


for k in range(5):
    slug = NAMES[k].lower().replace("the ", "").replace(" ", "_")
    elements = []
    groups = {}

    def group(name, origin):
        g = dict(name=name, origin=origin, rotation=[0, 0, 0], color=0, uuid=str(uuid.uuid4()), export=True, isOpen=True, visibility=True, children=[])
        groups[name] = g
        return g

    F = FIG[k]
    ax, ay = F["arm_at"]
    fig = group("figure", [0, 24, 0])
    skull = group("skull", [0, 24, 0])
    tail = group("tail", [0, 24 - F["tail_y"], 0])
    arm_r = group("right_arm", [-ax, 24 - ay, 0])
    arm_l = group("left_arm", [ax, 24 - ay, 0])

    def add(g, name, cx, y, cz, w, h, d):
        u = str(uuid.uuid4())
        faces = {f: {"uv": [0, 0, 1, 1], "texture": None} for f in ("north", "east", "south", "west", "up", "down")}
        elements.append(dict(name=name, type="cube", uuid=u, box_uv=False, rescale=False, locked=False, color=k, origin=list(g["origin"]),
                             **{"from": [round(-(cx + w / 2), 3), round(24 - (y + h), 3), round(cz - d / 2, 3)],
                                "to": [round(-(cx - w / 2), 3), round(24 - y, 3), round(cz + d / 2, 3)]}, faces=faces))
        g["children"].append(u)

    for grp, off, g in (("torso", 0.0, fig), ("skull", 0.0, skull), ("tail", F["tail_y"], tail)):
        for n, (cx, y, cz, w, h, d, c) in enumerate(F[grp]):
            add(g, c or "%s %d" % (grp, n), cx, y + off, cz, w, h, d)
    if k != QUIET:
        for side, g in ((1.0, arm_r), (-1.0, arm_l)):
            for n, (cx, y, cz, w, h, d, c) in enumerate(F["arm"]):
                add(g, c or "arm %d" % n, side * (ax + cx), ay + y, cz, w, h, d)
        fig["children"] += [skull, tail, arm_r, arm_l]
    else:
        fig["children"] += [skull, tail]
    model = dict(meta=dict(format_version="4.10", model_format="bedrock", box_uv=False),
                 name="spirit_figure_" + slug, model_identifier="spirit_figure_" + slug,
                 visible_box=[2, 3, 0], resolution=dict(width=16, height=16),
                 elements=elements, outliner=[fig], textures=[],
                 animations=animations(groups) if k != QUIET else [])
    with open(os.path.join(out, "spirit_figure_%s.bbmodel" % slug), "w") as fh:
        json.dump(model, fh, indent=1)
    print(slug, len(elements), "elements,", len(model["animations"]), "animations")
