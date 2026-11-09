"""Aetherion's editable Blockbench model and GeckoLib animations, rebuilt from our own Java model.

The geometry and 512x512 box-UV layout come from kingdoms/tools/gen_aetherion.py, so its four existing hides and four
marking sheets fit unchanged. The old AetherionDragonModel.setupAnim is the animation specification: idle, walk, fly,
glide, landing, sitting, crashed, bite and Cataclysm are keyed here; a tail sweep is added for WindDragon.swept().
Head look, climb pitch and the breath jaw stay per-frame in AetherionDragonRenderer.

Run from the repository root: python -I kingdoms/tools/gen_aetherion_geo.py
"""
from __future__ import annotations

import base64
import io
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS = os.path.join(ROOT, "kingdoms", "tools")
sys.path.insert(0, TOOLS)
import gen_aetherion as art

NAME = "aetherion"
MODEL = os.path.join(ROOT, "kingdoms", "art", "aetherion.bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
HIDE = os.path.join(ASSETS, "textures", "entity", "dragon", "aetherion", "hide_0.png")
SIZE = 512
NECK = [f"neck{i}" for i in range(4)]
TAIL = [f"tail{i}" for i in range(7)]
LEGS = ["left_front", "right_front", "left_hind", "right_hind"]
SIDES = ["l", "r"]
FINGERS = 3

# The old model's tuning, in ModelPart radians and model pixels.
SIT_DROP, CRASH_DROP = 9.0, 20.0
FOLD_ARM_Y, FOLD_ARM_Z = -0.35, -1.25
FOLD_FORE_Y, FOLD_FORE_Z = 0.2, 2.75
FOLD_HAND_Z, FOLD_CLOSE = -2.55, 0.8
TUCK_FRONT_UP, TUCK_FRONT_LOW = 1.45, -2.8
TUCK_HIND_UP, TUCK_HIND_LOW = 1.5, -2.6
TUCK_FOOT, TUCK_SHRINK, TUCK_RAISE = 1.25, 0.55, 3.0
BEAT_ARM, BEAT_LIFT, BEAT_FORE, BEAT_HAND, BEAT_LAG = 0.65, 0.1, 0.32, 0.28, 0.7

IDLE_LEN, WALK_LEN, LAND_LEN = 8.0, 1.2, 0.85
BITE_LEN, SWEEP_LEN, CATACLYSM_LEN = 0.7, 1.0, 3.5
FLY_LEN = math.tau / 0.32 / 20.0
GLIDE_LEN = 4.0

ROWS = {row[0]: row for row in art.GEOMETRY}
REST = {name: tuple(row[4][3:6]) for name, row in ROWS.items()}
REST_POS = {name: tuple(row[4][:3]) for name, row in ROWS.items()}


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


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def bb_point(x, y, z):
    """A Java ModelPart point in Blockbench's x-mirrored, y-up space (relative points need no ground offset)."""
    return [-x, -y, z]


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymoskingdoms:aetherion:" + ":".join(map(str, parts))))


def face_uv(uv_name, mirror):
    """The vanilla box-UV layout as per-face Blockbench UVs, including CubeListBuilder.mirror()."""
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
        for name in ("north", "south", "up", "down"):
            out[name][0], out[name][2] = out[name][2], out[name][0]
    return out


def make_model():
    """Build the same nested pivots, rest rotations and cubes as AetherionDragonModel.createBodyLayer()."""
    origins = {}

    def origin(name):
        if name in origins:
            return origins[name]
        row = ROWS[name]
        parent, pose = row[1], row[4]
        local = bb_point(*pose[:3])
        if parent is None:
            out = local
        else:
            po = origin(parent)
            # Outliner origins are stored in the parent's local frame; the parent's rest rotation is applied on bake.
            out = [po[i] + local[i] for i in range(3)]
        origins[name] = out
        return out

    elements, groups = [], {}
    for index, (name, parent, uv_name, box, pose, mirror) in enumerate(art.GEOMETRY):
        x, y, z, w, h, d = box
        o = origin(name)
        a = bb_point(x, y, z)
        b = bb_point(x + w, y + h, z + d)
        lo = [o[i] + min(a[i], b[i]) for i in range(3)]
        hi = [o[i] + max(a[i], b[i]) for i in range(3)]
        element_id = uid("cube", name)
        elements.append({
            "name": name,
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "from": lo,
            "to": hi,
            "autouv": 0,
            "color": index % 8,
            "origin": o,
            "inflate": 0,
            "faces": {face: {"uv": uv, "texture": 0} for face, uv in face_uv(uv_name, mirror).items()},
            "type": "cube",
            "uuid": element_id,
        })
        rest = REST[name]
        groups[name] = {
            "name": name,
            "origin": o,
            "rotation": [-math.degrees(rest[0]), -math.degrees(rest[1]), math.degrees(rest[2])],
            "color": 0,
            "isOpen": True,
            "export": True,
            "visibility": True,
            "mirror_uv": False,
            "locked": False,
            "uuid": uid("bone", name),
            "children": [element_id],
            "parent": parent,
        }

    outliner = []
    for name, group in groups.items():
        parent = group.pop("parent")
        (groups[parent]["children"] if parent in groups else outliner).append(group)

    with open(HIDE, "rb") as f:
        encoded = base64.b64encode(f.read()).decode("ascii")
    model = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": NAME,
        "model_identifier": NAME,
        "visible_box": [12, 7, 8],
        "resolution": {"width": SIZE, "height": SIZE},
        "elements": elements,
        "outliner": outliner,
        "textures": [{
            "name": "hide_0.png",
            "folder": "aetherion",
            "namespace": "jaymoskingdoms",
            "id": "0",
            "uuid": uid("texture", "hide_0"),
            "relative_path": os.path.relpath(HIDE, os.path.dirname(MODEL)).replace(os.sep, "/"),
            "source": "data:image/png;base64," + encoded,
            "width": SIZE,
            "height": SIZE,
            "uv_width": SIZE,
            "uv_height": SIZE,
            "visible": True,
        }],
        "animations": [],
    }
    return model


class Pose:
    """ModelPart rotation offsets (radians), pivot offsets (pixels, y down) and scales, by bone."""

    def __init__(self):
        self.rot, self.pos, self.scale = {}, {}, {}

    def r(self, bone, value):
        self.rot[bone] = add(self.rot.get(bone, (0.0, 0.0, 0.0)), value)
        return self

    def p(self, bone, value):
        self.pos[bone] = add(self.pos.get(bone, (0.0, 0.0, 0.0)), value)
        return self

    def s(self, bone, value):
        self.scale[bone] = value
        return self

    def only(self, bones):
        out = Pose()
        bones = set(bones)
        out.rot = {b: v for b, v in self.rot.items() if b in bones}
        out.pos = {b: v for b, v in self.pos.items() if b in bones}
        out.scale = {b: v for b, v in self.scale.items() if b in bones}
        return out


def model_pose(t=0.0, fly=0.0, land=0.0, crash=0.0, sit=0.0, glide=0.0, walk=0.0, step=0.0, flap=0.0,
               pitch=0.0, head_yaw=0.0, head_pitch=0.0, breath=0.0, action=0, action_x=0.0):
    """The old setupAnim, returned as offsets from the rest pose (head look/breath/pitch normally stay in the renderer)."""
    p = Pose()
    fly, land = ease(fly), ease(land) * (1.0 - ease(fly))
    crash = ease(crash) * (1.0 - fly)
    sit = ease(sit) * (1.0 - crash) * (1.0 - fly) * (1.0 - land)
    glide = ease(glide) * fly
    aloft = max(fly, land)
    on_ground = 1.0 - aloft
    walk = min(walk, 1.0) * on_ground * (1.0 - sit) * (1.0 - crash)
    tuck = fly
    breath = ease(breath)

    lunge = bell(action_x, 0.0, 1.0) if action == 1 else 0.0
    snap = rise(action_x, 0.0, 0.35) * (1.0 - rise(action_x, 0.45, 0.6)) if action == 1 else 0.0
    rear = rise(action_x, 0.0, 0.3) * (1.0 - rise(action_x, 0.55, 0.75)) if action == 2 else 0.0
    slam = bell(action_x, 0.55, 1.0) if action == 2 else 0.0
    roar = bell(action_x, 0.15, 0.9) if action == 2 else 0.0

    body_y = sit * SIT_DROP + crash * CRASH_DROP + abs(math.sin(step)) * 0.6 * walk
    body_y += math.sin(t * 0.07) * 0.2 * on_ground - rear * 4.0
    p.p("body", (0.0, body_y, 0.0))
    body_pitch = pitch * fly - sit * 0.12 - rear * 0.55 + slam * 0.1
    body_roll = math.sin(step) * 0.02 * walk
    body_yaw = math.sin(step) * 0.04 * walk
    p.r("body", (body_pitch, body_yaw, body_roll))

    straight = max(aloft * 0.7, breath * 0.5, lunge * 0.6)
    for i, bone in enumerate(NECK):
        lag = i * 0.6
        sway = math.sin(t * 0.05 - lag) * 0.03 * on_ground + math.sin(step * 2.0 - lag) * 0.02 * walk
        target = REST[bone][0] * (1.0 - straight) + sway - (lunge * 0.15 if i < 2 else -lunge * 0.1)
        target -= roar * (0.2 if i < 2 else 0.05) + pitch * fly * 0.25
        target += crash * 0.12 + sit * 0.05
        p.r(bone, (target - REST[bone][0], head_yaw * 0.2 - body_yaw * 0.4, 0.0))
    head = REST["head"][0] * (1.0 - straight * 0.6) + head_pitch * 0.5 + lunge * 0.25 - roar * 0.35 - crash * 0.2
    p.r("head", (head - REST["head"][0], head_yaw * 0.2, 0.0))
    jaw = 0.03 + (0.5 - 0.5 * math.cos(t * 0.04)) * 0.05 + breath * 0.65 + snap * 0.75 + roar * 0.85
    p.r("jaw", (jaw, 0.0, 0.0))

    for i, bone in enumerate(TAIL):
        k = i / (len(TAIL) - 1)
        lag = i * 0.65
        sway = math.sin(t * 0.06 - lag) * (0.03 + k * 0.04) * (1.0 - aloft * 0.5)
        sway += math.sin(step - lag) * (0.06 + k * 0.04) * walk
        target = REST[bone][0] * (1.0 - aloft * 0.8) - (pitch * fly * 0.5 if i == 0 else 0.0)
        target += math.sin(t * 0.1 - lag) * 0.03 * aloft + (rear * 0.5 + sit * 0.15 if i == 0 else 0.0)
        p.r(bone, (target - REST[bone][0], sway - body_yaw * 0.3, 0.0))

    for i, name in enumerate(LEGS):
        front, left = i < 2, i % 2 == 0
        upper, lower, foot = name + "_leg", name + "_shin", name + "_foot"
        phase = 0.0 if front == left else math.pi
        swing = math.cos(step + phase) * 0.4 * walk
        lift = (1.0 - math.sin(step + phase)) * 0.5
        knee = lift * lift * 0.45 * walk
        up = REST[upper][0] + swing
        low = REST[lower][0] + (-knee if front else knee)
        if front:
            up, low = lerp(land, up, 0.3), lerp(land, low, -0.2)
            up, low = lerp(rear, up, -0.6), lerp(rear, low, -0.9)
        else:
            up, low = lerp(land, up, 0.2), lerp(land, low, 0.4)
            up, low = lerp(sit, up, -0.9), lerp(sit, low, 1.5)
            up += rear * 0.5
        up = lerp(crash, up, -0.6 if front else 0.8)
        up = lerp(tuck, up, TUCK_FRONT_UP if front else TUCK_HIND_UP)
        low = lerp(tuck, low, TUCK_FRONT_LOW if front else TUCK_HIND_LOW)
        p.s(upper, (1.0 - tuck * TUCK_SHRINK,) * 3)
        p.p(upper, (0.0, -tuck * TUCK_RAISE, 0.0))
        p.r(upper, (up - REST[upper][0], 0.0, (-1.0 if left else 1.0) * crash * 0.7))
        p.r(lower, (low - REST[lower][0], 0.0, 0.0))
        flat = -(up + low - REST[upper][0] - REST[lower][0])
        foot_target = lerp(tuck, REST[foot][0] + flat * on_ground * (1.0 - crash) + land * 0.4, TUCK_FOOT)
        p.r(foot, (foot_target - REST[foot][0], 0.0, 0.0))

    beat = fly * (1.0 - glide) + land + slam
    open_wings = max(aloft, rear, slam)
    fold = (1.0 - open_wings) * (1.0 - crash * 0.5)
    phase = flap + (t * 0.5 if slam > 0.0 else 0.0)
    for wi, side in enumerate(SIDES):
        sx = 1.0 if wi == 0 else -1.0
        arm_up = BEAT_LIFT + BEAT_ARM * math.sin(phase) * beat + glide * 0.05 + rear
        fore_up = BEAT_FORE * math.sin(phase - BEAT_LAG) * beat + glide * 0.04 - rear * 0.25
        hand_up = BEAT_HAND * math.sin(phase - BEAT_LAG * 2.0) * beat + glide * 0.06 - rear * 0.2
        arm_up -= crash * 0.35
        p.r("wing_" + side, (0.0, sx * lerp(fold, math.cos(phase) * 0.14 * beat - rear * 0.2, FOLD_ARM_Y),
                             -sx * lerp(fold, arm_up, -FOLD_ARM_Z)))
        p.r("wing_fore_" + side, (0.0, sx * fold * FOLD_FORE_Y, -sx * lerp(fold, fore_up, -FOLD_FORE_Z)))
        p.r("wing_hand_" + side, (0.0, 0.0, -sx * lerp(fold, hand_up, -FOLD_HAND_Z)))
        spread = 1.0 + math.cos(phase - BEAT_LAG) * 0.08 * beat
        for f in range(FINGERS):
            bone = f"finger{f}_{side}"
            target = REST[bone][1] * lerp(fold, spread, 1.0 - FOLD_CLOSE)
            p.r(bone, (0.0, target - REST[bone][1], 0.0))
    return p


class Anim:
    def __init__(self, name, length, loop):
        self.name, self.length, self.loop = name, length, loop
        self.keys = {}

    def key(self, time, pose, interpolation="catmullrom"):
        for bone, value in pose.rot.items():
            degrees = tuple(math.degrees(v) for v in value)
            self.keys.setdefault(bone, {}).setdefault("rotation", []).append((time, degrees, interpolation))
        for bone, value in pose.pos.items():
            self.keys.setdefault(bone, {}).setdefault("position", []).append((time, bb_point(*value), interpolation))
        for bone, value in pose.scale.items():
            self.keys.setdefault(bone, {}).setdefault("scale", []).append((time, value, interpolation))
        return self

    def blockbench(self, uuids):
        animators = {}
        for bone, channels in self.keys.items():
            keyframes = []
            for channel, keys in channels.items():
                for index, (time, value, interpolation) in enumerate(keys):
                    keyframes.append({
                        "channel": channel,
                        "data_points": [{"x": round(value[0], 5), "y": round(value[1], 5), "z": round(value[2], 5)}],
                        "uuid": uid("key", self.name, bone, channel, index),
                        "time": round(time, 5),
                        "color": -1,
                        "interpolation": interpolation,
                    })
            animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": keyframes}
        return {
            "uuid": uid("animation", self.name),
            "name": "animation.aetherion." + self.name,
            "loop": self.loop,
            "override": False,
            "length": self.length,
            "snapping": 24,
            "selected": False,
            "anim_time_update": "",
            "blend_weight": "",
            "start_delay": "",
            "loop_delay": "",
            "animators": animators,
        }


def build_animations():
    animations = []

    idle = Anim("idle", IDLE_LEN, "loop")
    for i in range(17):
        t = IDLE_LEN * i / 16
        idle.key(t, model_pose(t=0.0 if i == 16 else t * 20.0))
    animations.append(idle)

    walk = Anim("walk", WALK_LEN, "loop")
    for i in range(17):
        walk.key(WALK_LEN * i / 16, model_pose(walk=1.0, step=math.tau * 2.0 * i / 16))
    animations.append(walk)

    fly = Anim("fly", FLY_LEN, "loop")
    for i in range(17):
        share = i / 16
        fly.key(FLY_LEN * share, model_pose(t=0.0 if i == 16 else FLY_LEN * share * 20.0,
                                            fly=1.0, flap=math.tau * share))
    animations.append(fly)

    glide = Anim("glide", GLIDE_LEN, "loop")
    for i in range(9):
        t = GLIDE_LEN * i / 8
        glide.key(t, model_pose(t=0.0 if i == 8 else t * 20.0, fly=1.0, glide=1.0))
    animations.append(glide)

    land = Anim("land", LAND_LEN, "once")
    land.key(0.0, model_pose(land=1.0))
    land.key(0.25, model_pose(land=0.75, fly=0.1))
    land.key(LAND_LEN, model_pose())
    animations.append(land)

    animations.append(Anim("sit", 0.05, "loop").key(0.0, model_pose(sit=1.0), "linear"))
    animations.append(Anim("crash", 0.05, "loop").key(0.0, model_pose(crash=1.0), "linear"))

    bite_bones = set(NECK + ["head", "jaw"])
    bite = Anim("bite", BITE_LEN, "once")
    for i in range(9):
        x = i / 8
        bite.key(BITE_LEN * x, model_pose(action=1, action_x=x).only(bite_bones))
    animations.append(bite)

    cataclysm_bones = set(NECK + TAIL + ["body", "head", "jaw"])
    for name in LEGS:
        cataclysm_bones.update((name + "_leg", name + "_shin", name + "_foot"))
    for side in SIDES:
        cataclysm_bones.update(("wing_" + side, "wing_fore_" + side, "wing_hand_" + side))
        cataclysm_bones.update(f"finger{f}_{side}" for f in range(FINGERS))
    cataclysm = Anim("cataclysm", CATACLYSM_LEN, "once")
    for i in range(21):
        x = i / 20
        cataclysm.key(CATACLYSM_LEN * x, model_pose(t=x * 70.0, action=2, action_x=x).only(cataclysm_bones))
    animations.append(cataclysm)

    # WindDragon's base tail sweep previously had no Aetherion pose; give its server hook a short, lagged lash.
    sweep_bones = set(["body"] + TAIL)
    sweep = Anim("sweep", SWEEP_LEN, "once")
    for time, body, lag in ((0.0, 0.0, 0.0), (0.2, -0.25, 0.35), (0.5, 0.5, -0.65), (0.75, 0.2, -0.25), (1.0, 0.0, 0.0)):
        p = Pose().r("body", (0.0, body, 0.0))
        for i, bone in enumerate(TAIL):
            p.r(bone, (0.0, lag * (i + 1) / len(TAIL), 0.0))
        sweep.key(time, p.only(sweep_bones))
    animations.append(sweep)
    return animations


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
    model["animations"] = [anim.blockbench(uuids) for anim in build_animations()]
    os.makedirs(os.path.dirname(MODEL), exist_ok=True)
    with open(MODEL, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent="\t")
        f.write("\n")
    print("wrote", os.path.relpath(MODEL, ROOT))
    subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), MODEL, ASSETS, NAME], check=True)
    # The renderer selects the existing hide and marking sheets by DragonLook; don't add a duplicate stacked hide.
    sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
    if os.path.exists(sheet):
        os.remove(sheet)


if __name__ == "__main__":
    main()
