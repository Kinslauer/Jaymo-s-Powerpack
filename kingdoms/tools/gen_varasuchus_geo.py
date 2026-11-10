"""Varasuchus's editable Blockbench model and its GeckoLib keyframes, all our own.

The body is kingdoms/tools/gen_varasuchus.py's own table (GEOMETRY) with its 256x256 box-UV layout, so every hide and
marking sheet it paints fits unchanged. The poses are the old VarasuchusDragonModel.setupAnim (removed with this),
ported line for line into pose() below and sampled into keyframes:
- loops the `move` controller picks: idle, one 15-second loop for each of its four fidgets (hiss, head shake, tail swish, sniff), walk, run (the
  gallop), upright (raging: stand, stand_walk), sit (basking), crash, swim;
- moves the server starts on the `action` controller: claw, horn, dash, barrage, shift, guard, parry, rage, bite, sweep,
  each also as `<move>_up` played when it's upright (raging), and hurt and death.
Its head looking round and its jaw while it breathes stay in VarasuchusDragonRenderer (they follow the game every frame),
and so do its looks (horns, scutes) and its tack.

Keyframes are the game's way (degrees, ModelPart angles; positions with x and y turned round), as the "free" project
stores them. Positions in Blockbench: (x, y, z) in the model is (-x, 24 - y, z), feet on the ground at y = 0.

Run from the repository root: python3 -I kingdoms/tools/gen_varasuchus_geo.py [--compare out_dir]
(--compare also renders each old pose beside the new animation into out_dir, with tools/bbmodel_preview.py.)
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
sys.path.insert(0, os.path.join(ROOT, "kingdoms", "tools"))
import gen_varasuchus as art  # noqa: E402

NAME = "varasuchus"
MODEL = os.path.join(ROOT, "kingdoms", "art", NAME + ".bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
HIDE = os.path.join(ASSETS, "textures", "entity", "dragon", NAME, "hide_0.png")
SIZE = 256
ROWS = {row[0]: row for row in art.GEOMETRY}
REST = {name: tuple(row[4][3:6]) for name, row in ROWS.items()}
REST_POS = {name: tuple(row[4][:3]) for name, row in ROWS.items()}
NTAIL = 12
TAIL = ["tail%d" % i for i in range(NTAIL)]
LEGS = ["left_front", "right_front", "left_hind", "right_hind"]

# the old model's constants (VarasuchusDragonModel)
CRASH_DROP, SIT_DROP = 17.0, 7.0
WRAP = [0.6, 0.7, 0.7, 0.6, 0.3, 0.02, 0.02, 0.02, 0.02, 0.02, 0.02, 0.02]
STAND_TILT, HIP_Y, HIP_Z = 1.05, 3.0, 13.0

# the entity's moves (VarasuchusDragon): number, name, length in ticks (its ACTION_TICKS)
NONE, CLAW, HORN, DASH, BARRAGE, SHIFT, GUARD, PARRY, RAGE, BITE, SWEEP = range(11)
MOVES = {CLAW: ("claw", 10), HORN: ("horn", 14), DASH: ("dash", 24 + 6 + 6), BARRAGE: ("barrage", 8 * 6 + 6),
         SHIFT: ("shift", 10 + 4), GUARD: ("guard", 12 + 40), PARRY: ("parry", 12), RAGE: ("rage", 30),
         BITE: ("bite", 16), SWEEP: ("sweep", 26)}
# walk speeds (the game's walkAnimation.speed) the walk and the gallop are keyed at; the dash's own walk under it
WALK_AT, RUN_AT, DASH_WALK = 0.45, 0.9, 0.4
# loop lengths (ticks): idle and each fidget one 300-tick cycle of the old model's; the still poses and the swim
IDLE_TICKS, STATE_TICKS, SWIM_TICKS = 300, 120, 120
HURT_TICKS, DEATH_TICKS = 10, 20


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def ease(v):
    v = clamp(v)
    return v * v * (3.0 - 2.0 * v)


def rise(v, a, b):
    return ease((v - a) / (b - a))


def bell(v, a, b):
    if v <= a or v >= b:
        return 0.0
    return math.sin((v - a) / (b - a) * math.pi) ** 2


def lerp(k, a, b):
    return a + (b - a) * k


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymoskingdoms:varasuchus:" + ":".join(map(str, parts))))


# ---- the model ------------------------------------------------------------------------------------------------------

def face_uv(uv_name, mirror):
    u, v = art.PLAN.uv[uv_name]
    w, h, d = art.PLAN.dims[uv_name]
    out = {
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east": [u, v + d, u + d, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + w, v, u + d + 2 * w, v + d],
    }
    if mirror:
        # a mirrored box: its east and west faces swap, and every face reads right to left
        out["east"], out["west"] = out["west"], out["east"]
        for name in out:
            out[name][0], out[name][2] = out[name][2], out[name][0]
    return out


def make_model():
    """Every bone a group at its pivot holding its one cube, posed at rest; the hide as its texture."""
    pivots = {}

    def pivot(name):
        """A bone's pivot in model space (the game's way, y down): Blockbench keeps every pivot where it lies at rest
        with no rotation applied, so it's just the sum of the offsets down from the root."""
        if name not in pivots:
            row = ROWS[name]
            local = list(row[4][:3])
            pivots[name] = local if row[1] is None else [a + b for a, b in zip(pivot(row[1]), local)]
        return pivots[name]

    elements, groups = [], {}
    for index, (name, parent, uv_name, box, pose, mirror) in enumerate(art.GEOMETRY):
        x, y, z, w, h, d = box
        px, py, pz = pivot(name)
        o = [-px, 24.0 - py, pz]
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
        "name": NAME, "model_identifier": NAME, "visible_box": [4, 4, 1],
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


# ---- the old model's poses ------------------------------------------------------------------------------------------

class Pose:
    """Absolute ModelPart angles (radians) and pivots (model pixels) for the bones the old model posed."""

    def __init__(self):
        self.rot, self.pos = {}, {}

    def offsets(self, bones=None):
        """As keyframes want them: added to the rest pose (rotations in degrees; positions x and y turned round)."""
        rot = {b: tuple(math.degrees(v - r) for v, r in zip(a, REST[b])) for b, a in self.rot.items() if bones is None or b in bones}
        pos = {}
        for b, a in self.pos.items():
            if bones is None or b in bones:
                d = [v - r for v, r in zip(a, REST_POS[b])]
                pos[b] = (-d[0], -d[1], d[2])
        return rot, pos


class Clock:
    """sin(t * w) of the old model, its speed w nudged to fit a whole number of turns into a loop of `ticks` (so the
    loop has no seam); left as it was for a move, which plays once."""

    def __init__(self, ticks=None):
        self.ticks = ticks

    def w(self, speed):
        if not self.ticks:
            return speed
        turns = max(1, round(speed * self.ticks / math.tau))
        return turns * math.tau / self.ticks


def pose(t=0.0, clock=None, walk_speed=0.0, step=0.0, crash=0.0, sit=0.0, swim=0.0, stand=0.0, dead=0.0, hurt=0.0,
         action=NONE, x=0.0, age=0.0, fidget_kind=-1, into=0.0):
    """VarasuchusDragonModel.setupAnim, line for line, with the head's look and the breath's jaw left out (the renderer
    adds them) and its looks and tack too. `step` is its walkAnimationPos * 0.5; `x` the move's share, `age` its ticks;
    `fidget_kind` the idle fidget (0 hiss, 1 shake, 2 swish, 3 sniff; -1 none) and `into` how many ticks into it."""
    w = (clock or Clock()).w
    p = Pose()
    sin, cos = math.sin, math.cos
    dead = ease(dead)
    crash, swim = ease(crash), ease(swim) * (1.0 - ease(crash))
    sit = ease(sit) * (1.0 - crash) * (1.0 - swim)
    a = action
    dash = rise(x, 0.0, 0.12) * (1.0 - rise(x, 0.62, 0.8)) if a == DASH else 0.0
    walk = clamp(walk_speed + dash * 0.6) * (1.0 - crash) * (1.0 - sit) * (1.0 - swim) * (1.0 - dead)
    run = ease((walk_speed + dash * 0.6 - 0.55) / 0.35) * walk

    claw = bell(x, 0.0, 1.0) if a == CLAW else 0.0
    claw_down = rise(x, 0.3, 0.6) if a == CLAW else 0.0
    dip = rise(x, 0.0, 0.35) * (1.0 - rise(x, 0.35, 0.55)) if a == HORN else 0.0
    toss = bell(x, 0.35, 1.0) if a == HORN else 0.0
    barrage = rise(x, 0.0, 0.08) * (1.0 - rise(x, 0.9, 1.0)) if a == BARRAGE else 0.0
    rake_l = barrage * (0.5 - 0.5 * cos(age * math.pi / 6.0))
    rake_r = barrage * (0.5 + 0.5 * cos(age * math.pi / 6.0))
    crouch = bell(x, 0.0, 1.0) if a == SHIFT else 0.0
    guard = rise(x, 0.0, 0.23) * (1.0 - rise(x, 0.85, 1.0)) if a == GUARD else 0.0
    parry = bell(x, 0.0, 1.0) if a == PARRY else 0.0
    rage = bell(x, 0.0, 1.0) if a == RAGE else 0.0
    bite_back = rise(x, 0.0, 0.35) * (1.0 - rise(x, 0.35, 0.5)) if a == BITE else 0.0
    bite_in = bell(x, 0.35, 1.0) if a == BITE else 0.0
    sweep = bell(x, 0.0, 1.0) if a == SWEEP else 0.0
    flinch = 0.0 if hurt <= 0.0 else bell(1.0 - hurt, 0.0, 1.0) * (1.0 - dead)
    idle = ((1.0 - min(walk_speed * 4.0, 1.0)) * (1.0 - sit) * (1.0 - crash) * (1.0 - swim) * (1.0 - dead)
            * (1.0 if a == NONE else 0.0) * (1.0 - ease(stand)))
    fidget = idle * bell(into, 0.0, 90.0 if fidget_kind == 3 else 50.0) if fidget_kind >= 0 else 0.0
    hiss = fidget if fidget_kind == 0 else 0.0
    shake = fidget * sin(into * 0.6) if fidget_kind == 1 else 0.0
    swish = fidget if fidget_kind == 2 else 0.0
    sniff = fidget if fidget_kind == 3 else 0.0
    sniff_turn = sniff * sin(into * math.pi / 45.0)
    bask = sit * sit
    stand = ease(stand) * (1.0 - crash) * (1.0 - swim)
    tilt = -STAND_TILT * stand
    hip_y = HIP_Y * cos(tilt) - HIP_Z * sin(tilt)
    hip_z = HIP_Y * sin(tilt) + HIP_Z * cos(tilt)

    by, bz = REST_POS["body"][1], REST_POS["body"][2]
    body_y = ((HIP_Y - hip_y) + by + crash * CRASH_DROP + sit * SIT_DROP + abs(sin(step)) * 0.5 * walk + sin(t * w(0.06)) * 0.15
              + dash * 2.0 + crouch * 4.0 - rage * 2.0 + abs(sin(step)) * 1.5 * run + dead * 6.0 + bite_in * 1.0)
    p.pos["body"] = (0.0, body_y, bz + (HIP_Z - hip_z))
    body_x = (tilt + dash * 0.06 - sit * 0.14 - rage * 0.18 + dip * 0.05 + sin(step) * 0.12 * run - bite_back * 0.06
              + bite_in * 0.05)
    body_yr = (sin(step) * 0.08 * walk * (1.0 - run) + sin(t * w(0.14)) * 0.07 * swim - guard * 0.22 - parry * 0.1
               + sweep * 0.3)
    body_z = sin(step) * 0.025 * walk * (1.0 - run) + flinch * 0.14
    p.rot["body"] = (body_x, body_yr, body_z)

    down = (crash * 0.45 + swim * 0.2 + dip * 0.35 - toss * 0.45 - rage * 0.5 + dash * 0.15 - bite_back * 0.35
            + bite_in * 0.3 + hiss * 0.15 + sniff * 0.55 - flinch * 0.4 + bask * 0.3 + dead * 0.6)
    p.rot["neck0"] = (REST["neck0"][0] + down * 0.55 + sin(step * 2.0) * 0.02 * walk - tilt * 0.55,
                      -body_yr * 0.5 + sniff_turn * 0.3 + shake * 0.12, 0.0)
    p.rot["neck1"] = (REST["neck1"][0] + down * 0.45 + sin(step * 2.0 - 0.5) * 0.02 * walk - tilt * 0.3,
                      -sin(step - 0.5) * 0.04 * walk + sniff_turn * 0.3 + shake * 0.18, 0.0)
    p.rot["head"] = (REST["head"][0] - down * 0.3 - toss * 0.25 - crash * 0.2, shake * 0.25, shake * 0.2 + dead * 0.3)
    p.rot["jaw"] = (0.04 + (0.5 - 0.5 * cos(t * w(0.03))) * 0.12 * (1.0 - walk) + rage * 0.85 + dash * 0.55 + toss * 0.2
                    + bite_back * 0.95 + flinch * 0.5 + hiss * 0.75 + bask * (0.75 + sin(t * w(0.03)) * 0.05) + dead * 0.4,
                    0.0, 0.0)

    for i, bone in enumerate(TAIL):
        k = i / (NTAIL - 1)
        lag = i * 0.7
        sway = (sin(step - lag) * (0.07 + k * 0.05) * walk + sin(t * w(0.05) - lag) * 0.04
                + sin(t * w(0.28) - lag) * (0.14 + k * 0.12) * swim)
        whip = ((bell(x, 0.55 + k * 0.15, 0.85 + k * 0.15) * 0.8 if a == DASH else 0.0)
                + (bell(x, k * 0.25, 0.6 + k * 0.4) * 0.6 if a == PARRY else 0.0))
        wrap = max(guard, whip)
        lash = bell(x, k * 0.25, 0.65 + k * 0.35) if a == SWEEP else 0.0
        flick = swish * sin(into * 0.5 - i * 0.6) * k * 0.3
        yr = (sway * (1.0 - wrap) + wrap * WRAP[i] + body_yr * -0.3 - lash * 0.45 + flick) * (1.0 - dead) + dead * 0.06
        xr = (REST[bone][0] * (1.0 - max(swim * 0.7, wrap * 0.85)) - dash * 0.03 + crash * (0.08 if i == 0 else 0.0)
              - sit * (0.1 if i == 0 else 0.0) - (tilt * 0.85 if i == 0 else 0.0))
        p.rot[bone] = (xr, yr, 0.0)

    for i, leg in enumerate(LEGS):
        front, left = i < 2, i % 2 == 0
        phase = lerp(run, 0.0 if front == left else math.pi,
                     (0.0 if left else 0.35) if front else (math.pi * 0.9 if left else math.pi * 1.25))
        swing = cos(step + phase) * (0.45 + run * 0.2) * walk
        lift = (1.0 - sin(step + phase)) * 0.5
        knee = lift * lift * 0.5 * walk
        up, low = swing, knee
        if not front:
            up -= tilt
        else:
            up = lerp(stand, up, -0.25 + cos(step + phase) * 0.15 * walk)
            low = lerp(stand, low, -0.9)
        if front:
            rake = max(claw, rake_l) if left else rake_r
            strike = claw_down * claw if left else 0.0
            up = lerp(rake, up, -1.1 + strike * 0.6)
            low = lerp(rake, low, 0.7 - strike * 0.3)
            up = lerp(rage, up, -0.4)
        else:
            up = lerp(sit, up, -0.9)
            low = lerp(sit, low, 1.4)
        up = lerp(crouch, up, -0.3 if front else 0.3)
        low = lerp(crouch, low, 0.6 if front else 0.3)
        # (the stroke's t is the game's age; in a loop it turns a whole number of times)
        stroke = t * w(0.08 + min(walk_speed, 1.0) * 0.14)
        up = lerp(swim, up, 1.3 + sin(stroke + phase) * 0.12)
        up = lerp(dead, up, -0.35 if front else 0.35)
        low = lerp(dead, low, 0.0)
        low = lerp(swim, low, 0.2)
        side = -1.0 if left else 1.0
        upper, shin, foot = leg + "_leg", leg + "_shin", leg + "_foot"
        p.rot[upper] = (up, 0.0, REST[upper][2] * (1.0 - swim * 0.6) + side * crash * 0.9 + side * dead * 0.5)
        p.rot[shin] = (low, 0.0, REST[shin][2] * (1.0 - swim * 0.6) - side * crash * 0.6)
        p.rot[foot] = (-(up + low + (0.0 if front else tilt)) * (1.0 - swim) * ((1.0 - stand) if front else 1.0)
                       + (stand * 0.4 if front else 0.0), 0.0, 0.0)
    return p


# ---- keyframes ------------------------------------------------------------------------------------------------------

class Anim:
    def __init__(self, name, length, loop="once", bones=None, body_pos=True):
        self.name, self.length, self.loop = name, length, loop
        # a move keys only its own bones (the move controller goes on underneath on the rest)
        self.bones, self.body_pos = bones, body_pos
        self.keys = {}

    def key(self, time, p):
        rot, pos = p.offsets(self.bones)
        if not self.body_pos:
            pos.pop("body", None)
        for bone, v in rot.items():
            self.keys.setdefault(bone, {}).setdefault("rotation", []).append((time, v))
        for bone, v in pos.items():
            self.keys.setdefault(bone, {}).setdefault("position", []).append((time, v))
        return self

    def blockbench(self, uuids):
        animators = {}
        for bone, channels in self.keys.items():
            keyframes = []
            for channel, keys in channels.items():
                # a loop's channel that never leaves rest is left out (a move keeps all of its bones, to hold them)
                if self.bones is None and all(max(map(abs, v)) < 1e-4 for _, v in keys):
                    continue
                for index, (time, v) in enumerate(keys):
                    keyframes.append({
                        "channel": channel, "data_points": [{"x": round(v[0], 4), "y": round(v[1], 4), "z": round(v[2], 4)}],
                        "uuid": uid("key", self.name, bone, channel, index), "time": round(time, 4), "color": -1,
                        "interpolation": "catmullrom",
                    })
            if keyframes:
                animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": keyframes}
        return {
            "uuid": uid("animation", self.name), "name": "animation.varasuchus." + self.name,
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


POSED = set(pose().rot) | set(pose().pos)
TAIL_SET = set(TAIL)
FRONT = {leg + part for leg in LEGS[:2] for part in ("_leg", "_shin", "_foot")}
ALL_LEGS = {leg + part for leg in LEGS for part in ("_leg", "_shin", "_foot")}
HEAD = {"neck0", "neck1", "head", "jaw"}
# each move: the bones it keys, and whether it moves the body up and down (or leaves that to the move controller)
MOVE_BONES = {
    CLAW: (FRONT, False), HORN: ({"body"} | HEAD, False), DASH: (POSED, True), BARRAGE: (FRONT, False),
    SHIFT: ({"body"} | ALL_LEGS, True), GUARD: ({"body"} | TAIL_SET | HEAD, False), PARRY: ({"body"} | TAIL_SET | HEAD, False),
    RAGE: (POSED, True), BITE: ({"body"} | HEAD, True), SWEEP: ({"body"} | TAIL_SET | HEAD, False),
}
HURT_BONES = {"body"} | HEAD


def sampled(name, ticks, at, per_tick=1.0, loop="once", bones=None, body_pos=True):
    """Keys `at(ticks_in)` every 1/per_tick ticks (and at the end) over `ticks` ticks."""
    anim = Anim(name, ticks / 20.0, loop, bones, body_pos)
    n = max(1, round(ticks * per_tick))
    for i in range(n + 1):
        anim.key(ticks * i / n / 20.0, at(ticks * i / n))
    return anim


def walk_step(speed, ticks):
    """One whole stride (step through 2 pi) at a walk speed: walkAnimationPos gains `speed` a tick, step is half that."""
    return math.tau / (speed * 0.5)


def dash_steps(ticks):
    """The dash's step through it: the walk under it plus its own burst (walkAnimationPos follows the speed)."""
    out, s = [0.0], 0.0
    length = MOVES[DASH][1]
    for i in range(ticks):
        x = (i + 0.5) / length
        d = rise(x, 0.0, 0.12) * (1.0 - rise(x, 0.62, 0.8))
        s += min(1.0, DASH_WALK + d * 0.6) * 0.5
        out.append(s)
    return out


def build():
    out = []
    idle_clock = Clock(IDLE_TICKS)
    for kind, name in enumerate(("hiss", "shake", "swish", "sniff")):
        # the fidget in its first 50 (the sniff 90) ticks, the rest of the cycle stood still; keyed closer in the fidget
        anim = Anim("idle_" + name, IDLE_TICKS / 20.0, "loop")
        times = [i * 1.0 for i in range(0, 91)] + [float(tk) for tk in range(95, IDLE_TICKS + 1, 5)]
        for tk in times:
            anim.key(tk / 20.0, pose(t=tk, clock=idle_clock, fidget_kind=kind, into=tk))
        out.append(anim)
    for name, speed, stand in (("walk", WALK_AT, 0.0), ("run", RUN_AT, 0.0), ("stand_walk", WALK_AT, 1.0)):
        ticks = walk_step(speed, 0) / 1.0
        out.append(sampled(name, ticks, lambda tk, speed=speed, stand=stand: pose(walk_speed=speed, step=tk * speed * 0.5, stand=stand),
                           1.0, "loop"))
    state_clock = Clock(STATE_TICKS)
    out.append(sampled("stand", STATE_TICKS, lambda tk: pose(t=tk, clock=state_clock, stand=1.0), 0.25, "loop"))
    out.append(sampled("sit", STATE_TICKS, lambda tk: pose(t=tk, clock=state_clock, sit=1.0), 0.25, "loop"))
    out.append(sampled("crash", STATE_TICKS, lambda tk: pose(t=tk, clock=state_clock, crash=1.0), 0.25, "loop"))
    swim_clock = Clock(SWIM_TICKS)
    out.append(sampled("swim", SWIM_TICKS, lambda tk: pose(t=tk, clock=swim_clock, swim=1.0), 0.5, "loop"))

    steps = dash_steps(MOVES[DASH][1])
    for move, (name, ticks) in MOVES.items():
        bones, body_pos = MOVE_BONES[move]
        for up in (False, True):
            def at(tk, move=move, ticks=ticks, up=up):
                x = tk / ticks
                # raging it rises as the roar begins (standAnim climbs 0.05 a tick)
                stand = (min(1.0, tk * 0.05) if move == RAGE else 1.0) if up else 0.0
                walk = DASH_WALK if move == DASH else 0.0
                step = steps[min(len(steps) - 1, int(round(tk)))] if move == DASH else 0.0
                return pose(t=tk, walk_speed=walk, step=step, stand=stand, action=move, x=x, age=tk)
            out.append(sampled(name + ("_up" if up else ""), ticks, at, 2.0 if ticks <= 16 else 1.0,
                               bones=bones, body_pos=body_pos))
    out.append(sampled("hurt", HURT_TICKS, lambda tk: pose(t=tk, hurt=1.0 - tk / HURT_TICKS), 2.0, bones=HURT_BONES, body_pos=False))
    death = sampled("death", DEATH_TICKS, lambda tk: pose(t=tk, dead=tk / DEATH_TICKS), 1.0)
    death.loop = "hold"
    out.append(death)
    return out


def main():
    model = make_model()
    uuids = {}

    def collect(node):
        if isinstance(node, str):
            return
        uuids[node["name"]] = node["uuid"]
        for child in node.get("children", []):
            collect(child)
    for node in model["outliner"]:
        collect(node)
    assert len(uuids) == len(art.GEOMETRY) == len(model["elements"])
    anims = build()
    model["animations"] = [a.blockbench(uuids) for a in anims]
    os.makedirs(os.path.dirname(MODEL), exist_ok=True)
    with open(MODEL, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent="\t")
        f.write("\n")
    print("wrote", os.path.relpath(MODEL, ROOT), "bones", len(uuids), "animations", len(anims))
    subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), MODEL, ASSETS, NAME], check=True)
    sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
    if os.path.exists(sheet):
        os.remove(sheet)  # the hides are drawn from textures/entity/dragon/varasuchus/


# ---- old beside new ---------------------------------------------------------------------------------------------------

# what to compare: animation, the second shown, and the old model's state at that moment (pose()'s arguments)
def comparisons():
    walk_ticks = walk_step(WALK_AT, 0)
    run_ticks = walk_step(RUN_AT, 0)
    out = [("idle_hiss", 10.0, dict(t=200, clock=Clock(IDLE_TICKS), fidget_kind=0, into=200)), ("idle_hiss", 1.25, dict(t=25, clock=Clock(IDLE_TICKS), fidget_kind=0, into=25)),
           ("idle_shake", 0.7, dict(t=14, clock=Clock(IDLE_TICKS), fidget_kind=1, into=14)),
           ("idle_swish", 1.4, dict(t=28, clock=Clock(IDLE_TICKS), fidget_kind=2, into=28)),
           ("idle_sniff", 1.5, dict(t=30, clock=Clock(IDLE_TICKS), fidget_kind=3, into=30)),
           ("walk", walk_ticks / 4 / 20, dict(walk_speed=WALK_AT, step=walk_ticks / 4 * WALK_AT * 0.5)),
           ("run", run_ticks / 4 / 20, dict(walk_speed=RUN_AT, step=run_ticks / 4 * RUN_AT * 0.5)),
           ("stand", 0.0, dict(stand=1.0)),
           ("stand_walk", walk_ticks / 4 / 20, dict(walk_speed=WALK_AT, step=walk_ticks / 4 * WALK_AT * 0.5, stand=1.0)),
           ("sit", 0.0, dict(sit=1.0)), ("crash", 0.0, dict(crash=1.0)), ("swim", 1.0, dict(t=20, clock=Clock(SWIM_TICKS), swim=1.0))]
    peaks = {CLAW: 0.5, HORN: 0.65, DASH: 0.7, BARRAGE: 0.3, SHIFT: 0.5, GUARD: 0.5, PARRY: 0.45, RAGE: 0.5, BITE: 0.3, SWEEP: 0.5}
    steps = dash_steps(MOVES[DASH][1])
    for move, (name, ticks) in MOVES.items():
        tk = round(peaks[move] * ticks)
        for up in (False, True):
            stand = (min(1.0, tk * 0.05) if move == RAGE else 1.0) if up else 0.0
            extra = dict(walk_speed=DASH_WALK, step=steps[tk]) if move == DASH else {}
            out.append((name + ("_up" if up else ""), tk / 20.0,
                        dict(t=tk, stand=stand, action=move, x=tk / ticks, age=tk, **extra), MOVE_BONES[move][0],
                        dict(stand=stand)))
    out.append(("hurt", 0.25, dict(t=5, hurt=0.5), HURT_BONES, {}))
    out.append(("death", 1.0, dict(t=20, dead=1.0)))
    return out


def pose_env(p, bones=None):
    """The old pose for tools/bbmodel_preview.py's POSE: angles set, pivots moved (the game's way)."""
    items = []
    for b, a in p.rot.items():
        if bones is None or b in bones:
            items.append("%s:=%s" % (b, ":".join("%.4f" % math.degrees(v) for v in a)))
    for b, a in p.pos.items():
        if bones is None or b in bones:
            items.append("%s@=%s" % (b, ":".join("%.4f" % (v - r) for v, r in zip(a, REST_POS[b]))))
    return ",".join(items)


def compare(out_dir):
    from PIL import Image, ImageDraw
    os.makedirs(out_dir, exist_ok=True)
    scratch = os.path.join(out_dir, "scratch")
    drop = "short_horn_l,short_horn_r,nose_horn,saddle,strap,pad"
    subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_bake.py"), MODEL, scratch, NAME, "--drop", drop],
                   check=True, stdout=subprocess.DEVNULL)
    preview = os.path.join(ROOT, "tools", "bbmodel_preview.py")
    rows = []
    for index, item in enumerate(comparisons()):
        name, sec, state = item[:3]
        old_png, new_png = (os.path.join(scratch, "%02d_%s_%s.png" % (index, name, k)) for k in ("old", "new"))
        old = pose(**state)
        env = dict(os.environ, POSE=pose_env(old))
        subprocess.run([sys.executable, "-I", preview, scratch, NAME, old_png, "-", "0", HIDE], env=env, check=True, stdout=subprocess.DEVNULL)
        new_env = dict(os.environ, POSE="")
        if len(item) > 3:
            # a move keys only its bones: the rest are the move controller's (stood, or upright), as in the game
            under = pose(**item[4])
            new_env["POSE"] = pose_env(under, set(under.rot) - item[3])
        subprocess.run([sys.executable, "-I", preview, scratch, NAME, new_png, name, str(sec), HIDE], env=new_env, check=True,
                       stdout=subprocess.DEVNULL)
        rows.append((name, sec, Image.open(old_png).copy(), Image.open(new_png).copy()))
    w, h = rows[0][2].size
    label = 22
    sheet = Image.new("RGB", (w, len(rows) * (2 * h + label)), (40, 44, 50))
    d = ImageDraw.Draw(sheet)
    for i, (name, sec, a, b) in enumerate(rows):
        y = i * (2 * h + label)
        d.text((8, y + 5), "%s at %.2fs: top, the old Java pose; below, the new GeckoLib animation" % (name, sec), fill=(235, 235, 235))
        sheet.paste(a, (0, y + label))
        sheet.paste(b, (0, y + label + h))
    sheet = sheet.resize((sheet.width * 2 // 3, sheet.height * 2 // 3), Image.LANCZOS).quantize(128)
    sheet.save(os.path.join(out_dir, NAME + "-comparisons.png"), optimize=True)
    print("wrote", os.path.join(out_dir, NAME + "-comparisons.png"), len(rows), "poses")


if __name__ == "__main__":
    if "--compare" in sys.argv:
        compare(sys.argv[sys.argv.index("--compare") + 1])
    else:
        main()
