"""Stegonaut's editable Blockbench model and its GeckoLib keyframes, all our own.

The body (every box, pivot, rest angle and the 256x256 box-UV layout) is read from kingdoms/tools/gen_stegonaut.py's
GEOMETRY table, the same one the hides and marking sheets are painted from, so textures/entity/dragon/stegonaut/
hide_*.png and marks_*.png fit unchanged. The motion is the old StegonautDragonModel.setupAnim, ported below
(`model_pose`) and sampled into keyframes:
- states: idle (with its three grumbles: a head toss, a head tilt, the neck lifted), walk, run (the gallop), sit (the
  doze), crash, swim, hurt (the flinch) and death;
- moves the server starts: chin_slam, ground_slam, chew (the ground eating: scoop, chew, spit), roar, bite, graze,
  and a tail sweep (its swept() hook had no pose before; a swing of the body and tail like the other walkers').
Stegonaut never flies, so there is no fly, glide or land. Head look, the jaw at a breath and the look/tack visibility
stay per frame in StegonautDragonRenderer.

Writes kingdoms/art/stegonaut.bbmodel, then the GeckoLib files with tools/bbmodel_geckolib.py.
Run from the repository root: python3 -I kingdoms/tools/gen_stegonaut_geo.py
"""
from __future__ import annotations

import base64
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS = os.path.join(ROOT, "kingdoms", "tools")
sys.path.insert(0, TOOLS)
_argv, sys.argv = sys.argv, sys.argv[:1]
import gen_stegonaut as art  # noqa: E402  (its geometry and UV layout; importing it writes nothing)
sys.argv = _argv

NAME = "stegonaut"
MODEL = os.path.join(ROOT, "kingdoms", "art", NAME + ".bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
HIDE = os.path.join(ASSETS, "textures", "entity", "dragon", NAME, "hide_0.png")
SIZE = 256
ROWS = {row[0]: row for row in art.GEOMETRY}
REST = {name: tuple(row[4][3:6]) for name, row in ROWS.items()}
NECK = ["neck%d" % i for i in range(4)]
TAIL = ["tail%d" % i for i in range(5)]
LEGS = ["left_front", "right_front", "left_hind", "right_hind"]
PLATES = 5
PLATE_LEAN, ROAR_FLARE = 0.16, 0.22
CRASH_DROP, SIT_DROP = 16.0, 8.0

# its moves (StegonautDragon's numbers) and how long each plays (StegonautDragon.ACTION_TICKS, in seconds)
CHIN_SLAM, GROUND_SLAM, CHEW, ROAR, BITE_MOVE, GRAZE = 1, 2, 3, 4, 5, 6
ACTION_TICKS = {CHIN_SLAM: 16, GROUND_SLAM: 24, CHEW: 32, ROAR: 60, BITE_MOVE: 20, GRAZE: 30}
ACTION_NAME = {CHIN_SLAM: "chin_slam", GROUND_SLAM: "ground_slam", CHEW: "chew", ROAR: "roar", BITE_MOVE: "bite", GRAZE: "graze"}
# loops (ticks): the idle is one grumble cycle of the old code (260 ticks, the grumble in its first 44)
IDLE_TICKS, GRUMBLE_TICKS, STATE_TICKS, SWIM_TICKS = 260, 44, 160, 100
# the walk and the run are baked at these walk speeds (WalkAnimationState.speed); one stride is 2 pi of step = pos * 0.55
WALK_AT, RUN_AT, STEP_RATE = 0.5, 1.0, 0.55
HURT_TICKS, DEATH_TICKS, SWEEP_LEN = 10, 20, 1.0


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def ease(x):
    x = clamp(x)
    return x * x * (3.0 - 2.0 * x)


def bell(x, a, b):
    return 0.0 if x <= a or x >= b else math.sin(ease((x - a) / (b - a)) * math.pi)


def hold(x, a, peak, fall, b):
    if x <= a or x >= b:
        return 0.0
    return ease((x - a) / (peak - a)) if x < peak else 1.0 if x < fall else 1.0 - ease((x - fall) / (b - fall))


def lerp(k, a, b):
    return a + (b - a) * k


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymoskingdoms:stegonaut:" + ":".join(map(str, parts))))


# ---- the body ------------------------------------------------------------------------------------------------------
# A model point (x, y, z), y down with the ground at 24, is (-x, 24 - y, z) in Blockbench (tools/bbmodel_bake.py).

def face_uv(uv_name, mirror):
    u, v = art.UV[uv_name]
    w, h, d = art.DIMS[uv_name]
    out = {
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + w, v, u + d + 2 * w, v + d],
    }
    if mirror:
        # as vanilla mirrors a box: every face flipped left to right, and the east and west faces trade places
        for name in out:
            out[name][0], out[name][2] = out[name][2], out[name][0]
        out["east"], out["west"] = out["west"], out["east"]
    return out


def make_model():
    origins = {}

    def origin(name):
        if name not in origins:
            parent, pose = ROWS[name][1], ROWS[name][4]
            if parent is None:
                origins[name] = [-pose[0], 24.0 - pose[1], pose[2]]
            else:
                po = origin(parent)
                origins[name] = [po[0] - pose[0], po[1] - pose[1], po[2] + pose[2]]
        return origins[name]

    elements, groups = [], {}
    for index, (name, parent, uv_name, box, pose, mirror) in enumerate(art.GEOMETRY):
        x, y, z, w, h, d = box
        w, h, d = math.ceil(w), math.ceil(h), math.ceil(d)
        o = origin(name)
        lo = [o[0] - (x + w), o[1] - (y + h), o[2] + z]
        hi = [o[0] - x, o[1] - y, o[2] + z + d]
        element_id = uid("cube", name)
        elements.append({
            "name": name, "box_uv": False, "rescale": False, "locked": False,
            "from": lo, "to": hi, "autouv": 0, "color": index % 8, "origin": o, "inflate": 0,
            "faces": {face: {"uv": uv, "texture": 0} for face, uv in face_uv(uv_name, mirror).items()},
            "type": "cube", "uuid": element_id,
        })
        rest = REST[name]
        groups[name] = {
            "name": name, "origin": o,
            "rotation": [-math.degrees(rest[0]), -math.degrees(rest[1]), math.degrees(rest[2])],
            "color": 0, "isOpen": True, "export": True, "visibility": True, "mirror_uv": False,
            "locked": False, "uuid": uid("bone", name), "children": [element_id], "parent": parent,
        }
    outliner = []
    for name, group in groups.items():
        parent = group.pop("parent")
        (groups[parent]["children"] if parent in groups else outliner).append(group)
    with open(HIDE, "rb") as f:
        encoded = base64.b64encode(f.read()).decode("ascii")
    return {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": NAME, "model_identifier": NAME, "visible_box": [5, 5, 2.5],
        "resolution": {"width": SIZE, "height": SIZE}, "elements": elements, "outliner": outliner,
        "textures": [{
            "name": "hide_0.png", "folder": NAME, "namespace": "jaymoskingdoms", "id": "0",
            "uuid": uid("texture", "hide_0"),
            "relative_path": os.path.relpath(HIDE, os.path.dirname(MODEL)).replace(os.sep, "/"),
            "source": "data:image/png;base64," + encoded,
            "width": SIZE, "height": SIZE, "uv_width": SIZE, "uv_height": SIZE, "visible": True,
        }],
        "animations": [],
    }


# ---- the motion: StegonautDragonModel.setupAnim, ported ----------------------------------------------------------------

class Pose:
    """Absolute ModelPart angles (radians) and pivot moves (model pixels, y down) by bone, as setupAnim set them."""

    def __init__(self):
        self.rot, self.pos = {}, {}

    def r(self, bone, x=None, y=None, z=None):
        cur = list(self.rot.get(bone, REST[bone]))
        for i, v in enumerate((x, y, z)):
            if v is not None:
                cur[i] = v
        self.rot[bone] = tuple(cur)

    def p(self, bone, value):
        self.pos[bone] = add(self.pos.get(bone, (0.0, 0.0, 0.0)), value)

    def offsets(self):
        """Rotations as offsets from rest (what a keyframe holds)."""
        return {b: tuple(v - r for v, r in zip(val, REST[b])) for b, val in self.rot.items()}


class Clock:
    """`sin(t * k)` for a loop of `period` ticks: k is rounded to whole turns of the loop so it closes seamlessly."""

    def __init__(self, t, period=None):
        self.t, self.period = t, period

    def k(self, k):
        if not self.period:
            return k
        turns = max(1, round(k * self.period / math.tau))
        return turns * math.tau / self.period

    def sin(self, k, phase=0.0):
        return math.sin(self.t * self.k(k) + phase)

    def cos(self, k, phase=0.0):
        return math.cos(self.t * self.k(k) + phase)


def model_pose(t=0.0, period=None, walk_speed=0.0, step=0.0, crash=0.0, sit=0.0, swim=0.0, dead=0.0, hurt=0.0,
               action=0, share=0.0, grumble_kind=-1, grumble_at=0.0):
    """The old setupAnim (head look and the breath's jaw left to the renderer). `grumble_at`: ticks into a grumble."""
    c = Clock(t, period)
    p = Pose()
    dead = ease(dead)
    crash = ease(crash)
    swim = ease(swim) * (1.0 - crash)
    sit = ease(sit) * (1.0 - crash) * (1.0 - swim)
    walk = min(walk_speed, 1.0) * (1.0 - crash) * (1.0 - sit) * (1.0 - dead)
    run = ease((walk_speed - 0.55) / 0.35) * walk
    a = action
    chin = share if a == CHIN_SLAM else 0.0
    slam = share if a == GROUND_SLAM else 0.0
    chew = share if a == CHEW else 0.0
    roar_share = share if a == ROAR else 0.0
    bite = share if a == BITE_MOVE else 0.0
    graze = share if a == GRAZE else 0.0
    rear = 0.0 if slam <= 0.0 else ease(slam / 0.6) if slam < 0.6 else 1.0 - ease((slam - 0.6) / 0.25)
    impact = bell(slam, 0.68, 1.0)
    chin_down = hold(chin, 0.0, 0.36, 0.36, 0.62)
    chin_up = bell(chin, 0.36, 1.0)
    scoop = hold(chew, 0.0, 0.3, 0.3, 0.5)
    cheeks = hold(chew, 0.3, 0.42, 0.68, 0.78)
    throw_back = hold(chew, 0.64, 0.74, 0.74, 0.84)
    fling = bell(chew, 0.74, 1.0)
    roar = hold(roar_share, 0.0, 0.2, 0.66, 1.0)
    roar_sweep = roar * math.sin(roar_share * math.pi * 2.0)
    bite_back = hold(bite, 0.0, 0.34, 0.34, 0.6)
    bite_in = bell(bite, 0.34, 1.0)
    graze0 = hold(graze, 0.0, 0.22, 0.8, 1.0)
    chomp = graze0 * (0.5 - 0.5 * math.cos(graze * math.pi * 4.0))
    flinch = 0.0 if hurt <= 0.0 else hold(1.0 - hurt, 0.0, 0.2, 0.2, 1.0)
    idle = ((1.0 - min(walk_speed * 4.0, 1.0)) * (1.0 - sit) * (1.0 - crash) * (1.0 - swim) * (1.0 - dead)
            * (1.0 if a == 0 else 0.0))
    grumble = idle * bell(grumble_at, 0.0, GRUMBLE_TICKS) if grumble_kind >= 0 else 0.0
    toss = grumble if grumble_kind == 0 else 0.0
    tilt = grumble if grumble_kind == 1 else 0.0
    raise_ = grumble if grumble_kind == 2 else 0.0
    turn = math.sin(grumble_at / GRUMBLE_TICKS * math.pi * 2.0)
    doze = sit * sit

    body_y = (crash * CRASH_DROP + sit * SIT_DROP + abs(math.sin(step)) * (0.6 + run * 1.0) * walk
              + c.sin(0.08 - doze * 0.04) * (0.15 + doze * 0.15)
              - rear * 3.0 + impact * 1.6 + chin_down * 1.0 - chin_up * 1.2)
    p.p("body", (0.0, body_y, 0.0))
    p.r("body", -rear * 0.38 + impact * 0.2 + sit * -0.12 + swim * 0.04 + math.cos(step * 2.0) * 0.05 * run - flinch * 0.12,
        0.0, math.sin(step) * 0.035 * walk * (1.0 - run) + flinch * 0.16)

    share4 = (0.4, 0.25, 0.2, 0.15)
    neck_down = (scoop * 1.5 - cheeks * 0.15 - throw_back * 0.45 + fling * 0.55
                 + chin_down * 1.0 - chin_up * 0.5 - roar * 0.35 + rear * 0.15 + crash * 1.1 + sit * 0.2 + doze * 0.25
                 - bite_back * 0.3 + bite_in * 0.45 + graze0 * 1.45 - raise_ * 0.3 + dead * 0.6)
    for i, bone in enumerate(NECK):
        p.r(bone, REST[bone][0] + neck_down * share4[i] + math.sin(step * 2.0 - i * 0.6) * 0.04 * walk
            + c.sin(0.05, -i * 0.5) * 0.015,
            math.sin(step - i * 0.6) * 0.03 * walk + roar_sweep * 0.05, None)
    p.r("head", REST["head"][0] - neck_down * 0.55 + scoop * 0.45 - throw_back * 0.3 + fling * 0.3
        + chin_down * 0.25 - chin_up * 0.35 - roar * 0.3 - bite_back * 0.3 + bite_in * 0.25 + graze0 * 0.5
        - toss * 0.17 - tilt * 0.24 + doze * 0.28,
        roar_sweep * 0.25 + toss * turn * 0.14 + doze * 0.14,
        tilt * turn * 0.17)
    p.r("jaw", 0.03 + roar * 0.6 + chin_up * 0.15
        + scoop * 0.6 + cheeks * (0.21 + math.sin(t * 0.9) * 0.05) + fling * 0.7
        + bite_back * 0.66 - bite_in * 0.05 + chomp * 0.5 + flinch * 0.5 + grumble * 0.44 + dead * 0.5, None, None)

    for i, bone in enumerate(TAIL):
        p.r(bone, REST[bone][0] - walk * 0.03 + crash * (0.25 if i == 0 else 0.0) + rear * 0.12
            - (impact * 0.3 if i == 0 else 0.0) + math.sin(step * 2.0 - i * 0.5) * 0.05 * run,
            c.sin(0.06, -i * 0.6) * (0.05 + walk * 0.08) + math.sin(step - i * 0.7) * 0.06 * walk * (1.0 - run)
            + math.sin(slam * math.pi * 2.0) * 0.15 * rear + c.sin(0.5, -i * 0.6) * 0.12 * roar
            + c.cos(0.2, -i * 0.6) * 0.12 * swim, None)

    lean = PLATE_LEAN + roar * ROAR_FLARE
    for i in range(PLATES):
        p.r("plate%d_l" % i, None, None, lean)
        p.r("plate%d_r" % i, None, None, -lean)

    for i, leg in enumerate(LEGS):
        front, left = i < 2, i % 2 == 0
        trot = 0.0 if front == left else math.pi
        gallop = (0.0 if front else math.pi) + (0.0 if left else math.pi * 0.25)
        phase = lerp(run, trot, gallop)
        swing = math.cos(step + phase) * (0.45 + run * 0.15) * walk
        lift = (1.0 - math.sin(step + phase)) * 0.5
        knee = lift * lift * (0.45 + run * 0.2) * walk
        up, low = swing, knee
        if front:
            up = lerp(rear, up, -0.7) + impact * 0.28
            low = lerp(rear, low, 0.9)
        else:
            up = lerp(sit, up, -0.9)
            low = lerp(sit, low, 1.3)
        up = lerp(crash, up, -0.9 if front else 0.9)
        low = lerp(crash, low, 1.2 if front else -0.4)
        up += swim * c.sin(0.25, phase) * (0.5 if front else 0.3)
        low = lerp(dead, low, 0.0)
        p.r(leg + "_leg", up, None, (-1.0 if left else 1.0) * (crash * 0.6 + ((rear * 0.24 + swim * 0.24) if front else 0.0) + dead * 0.45))
        p.r(leg + "_shin", low, None, None)
        p.r(leg + "_foot", -(up + low) * (1.0 - swim) * (1.0 - dead), None, None)
    return p


# ---- keyframes ------------------------------------------------------------------------------------------------------

class Anim:
    def __init__(self, name, length, loop="once", bones=None):
        self.name, self.length, self.loop, self.bones = name, length, loop, bones
        self.samples = []  # (time, Pose)

    def key(self, time, pose):
        self.samples.append((time, pose))
        return self

    def blockbench(self, uuids):
        """Every channel that moves at all is keyed at every sample (so it holds its value at the ends too)."""
        channels = {}
        for time, pose in self.samples:
            for bone, v in pose.offsets().items():
                channels.setdefault((bone, "rotation"), []).append((time, tuple(math.degrees(a) for a in v)))
            for bone, v in pose.pos.items():
                channels.setdefault((bone, "position"), []).append((time, (-v[0], -v[1], v[2])))
        animators = {}
        for (bone, channel), keys in sorted(channels.items()):
            if self.bones is not None and bone not in self.bones:
                continue
            if self.bones is None and max(abs(a) for _, v in keys for a in v) < 1.0E-4:
                continue  # a state leaves still bones to rest; a move keys every bone it owns, so it replaces the state
            frames = animators.setdefault(uuids[bone], {"name": bone, "type": "bone", "keyframes": []})["keyframes"]
            for index, (time, value) in enumerate(keys):
                frames.append({
                    "channel": channel, "data_points": [{"x": round(value[0], 4), "y": round(value[1], 4), "z": round(value[2], 4)}],
                    "uuid": uid("key", self.name, bone, channel, index), "time": round(time, 4), "color": -1,
                    "interpolation": "catmullrom",
                })
        return {
            "uuid": uid("animation", self.name), "name": "animation.stegonaut." + self.name,
            "loop": self.loop, "override": False, "length": round(self.length, 4), "snapping": 20, "selected": False,
            "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators,
        }


def descendants(*roots):
    out = set(roots)
    changed = True
    while changed:
        changed = False
        for name, row in ROWS.items():
            if row[1] in out and name not in out:
                out.add(name)
                changed = True
    return out


def legs(*which):
    return {leg + part for leg in which for part in ("_leg", "_shin", "_foot")}


HEAD = {"head", "jaw"}
NECK_HEAD = set(NECK) | HEAD
PLATE_BONES = {"plate%d_%s" % (i, s) for i in range(PLATES) for s in "lr"}
# the bones each move owns (the action controller replaces these; the rest go on walking or standing underneath)
ACTION_BONES = {
    CHIN_SLAM: {"body"} | NECK_HEAD,
    GROUND_SLAM: {"body"} | NECK_HEAD | set(TAIL) | legs("left_front", "right_front"),
    CHEW: NECK_HEAD,
    ROAR: NECK_HEAD | set(TAIL) | PLATE_BONES,
    BITE_MOVE: NECK_HEAD,
    GRAZE: NECK_HEAD,
}
HURT_BONES = {"body"} | NECK_HEAD


def sampled(name, ticks, pose_at, loop="once", bones=None, every=2):
    """`pose_at(share)` sampled every `every` ticks over `ticks` (and at the end)."""
    anim = Anim(name, ticks / 20.0, loop, bones)
    steps = max(4, round(ticks / every))
    for i in range(steps + 1):
        s = i / steps
        anim.key(anim.length * s, pose_at(s))
    return anim


def sweep_pose(s):
    """The tail sweep (new: the old model had none): wound the other way, then the body and tail swung right round."""
    p = model_pose(t=s * 20.0 * SWEEP_LEN)
    keys = [(0.0, 0.0, 0.0), (0.25, -0.22, 0.35), (0.55, 0.38, -0.65), (0.78, 0.2, -0.3), (1.0, 0.0, 0.0)]
    for (s0, b0, l0), (s1, b1, l1) in zip(keys, keys[1:]):
        if s0 <= s <= s1:
            k = ease((s - s0) / (s1 - s0))
            body, lag = lerp(k, b0, b1), lerp(k, l0, l1)
            break
    p.r("body", None, body, None)
    for i, bone in enumerate(TAIL):
        cur = p.rot[bone]
        p.r(bone, None, cur[1] + lag * (i + 1) / len(TAIL), None)
    return p


def build_animations():
    out = []
    idle = lambda kind: (lambda s: model_pose(t=s * IDLE_TICKS, period=IDLE_TICKS, grumble_kind=kind, grumble_at=s * IDLE_TICKS))
    out.append(sampled("idle", IDLE_TICKS, idle(-1), "loop", every=10))
    for kind, name in enumerate(("idle_toss", "idle_tilt", "idle_raise")):
        # the grumble in its first 44 ticks (sampled finely there), then the plain idle
        anim = Anim(name, IDLE_TICKS / 20.0, "loop")
        times = [i * 2 for i in range(GRUMBLE_TICKS // 2 + 1)] + list(range(GRUMBLE_TICKS + 10, IDLE_TICKS + 1, 10))
        for tick in sorted(set(times + [IDLE_TICKS])):
            anim.key(tick / 20.0, idle(kind)(tick / IDLE_TICKS))
        out.append(anim)
    walk_ticks = math.tau / (STEP_RATE * WALK_AT)
    run_ticks = math.tau / (STEP_RATE * RUN_AT)
    out.append(sampled("walk", walk_ticks, lambda s: model_pose(t=s * walk_ticks, period=walk_ticks, walk_speed=WALK_AT, step=math.tau * s),
                       "loop", every=1))
    out.append(sampled("run", run_ticks, lambda s: model_pose(t=s * run_ticks, period=run_ticks, walk_speed=RUN_AT, step=math.tau * s),
                       "loop", every=0.5))
    out.append(sampled("sit", STATE_TICKS, lambda s: model_pose(t=s * STATE_TICKS, period=STATE_TICKS, sit=1.0), "loop", every=8))
    out.append(sampled("crash", STATE_TICKS, lambda s: model_pose(t=s * STATE_TICKS, period=STATE_TICKS, crash=1.0), "loop", every=8))
    out.append(sampled("swim", SWIM_TICKS, lambda s: model_pose(t=s * SWIM_TICKS, period=SWIM_TICKS, swim=1.0), "loop", every=2))
    for action, name in ACTION_NAME.items():
        ticks = ACTION_TICKS[action]
        out.append(sampled(name, ticks, lambda s, a=action, n=ticks: model_pose(t=s * n, action=a, share=s),
                           bones=ACTION_BONES[action], every=1))
    out.append(sampled("sweep", SWEEP_LEN * 20, sweep_pose, bones={"body"} | set(TAIL), every=1))
    out.append(sampled("hurt", HURT_TICKS, lambda s: model_pose(t=s * HURT_TICKS, hurt=1.0 - s), bones=HURT_BONES, every=1))
    out.append(sampled("death", DEATH_TICKS, lambda s: model_pose(t=s * DEATH_TICKS, dead=s), "hold", bones=set(ROWS), every=1))
    return out


def main():
    model = make_model()
    uuids = {}

    def collect(node):
        uuids[node["name"]] = node["uuid"]
        for child in node.get("children", []):
            if isinstance(child, dict):
                collect(child)
    for node in model["outliner"]:
        collect(node)
    assert len(uuids) == len(art.GEOMETRY) == len(model["elements"]), "bone names must be unique"
    model["animations"] = [anim.blockbench(uuids) for anim in build_animations()]
    os.makedirs(os.path.dirname(MODEL), exist_ok=True)
    with open(MODEL, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent="\t")
        f.write("\n")
    print("wrote", os.path.relpath(MODEL, ROOT), "bones", len(uuids), "animations", len(model["animations"]))
    subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), MODEL, ASSETS, NAME], check=True)
    sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
    if os.path.exists(sheet):
        os.remove(sheet)  # the hides are drawn from textures/entity/dragon/stegonaut/


if __name__ == "__main__":
    main()
