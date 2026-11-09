"""Atroxiia's editable Blockbench model and original GeckoLib keyframes.

Geometry and the 256x256 box-UV layout come from our own kingdoms/tools/gen_atroxiia.py, so every existing hide and
marking sheet fits unchanged. AtroxiiaDragonModel.setupAnim is the motion specification: the same equations are sampled
into idle/fidgets, walk/run, sit, crash, swim, all eight moves, hurt and death. Head look and look/saddle visibility stay
per-frame in AtroxiiaDragonRenderer. Atroxiia does not fly, so there are no fly/glide/land states.

Run from the repository root: python -I kingdoms/tools/gen_atroxiia_geo.py
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
import gen_atroxiia as art

NAME = "atroxiia"
MODEL = os.path.join(ROOT, "kingdoms", "art", NAME + ".bbmodel")
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
HIDE = os.path.join(ASSETS, "textures", "entity", "dragon", NAME, "hide_0.png")
SIZE = 256
ROWS = {row[0]: row for row in art.GEOMETRY}
REST = {name: tuple(row[4][3:6]) for name, row in ROWS.items()}
NECK = [f"neck{i}" for i in range(3)]
TAIL = [f"tail{i}" for i in range(11)]
SIDES = ("left", "right")
IDLE_LEN, WALK_LEN, RUN_LEN, SWIM_LEN, STATE_LEN = 16.0, 1.2, 0.75, 5.0, 4.0
HURT_LEN, DEATH_LEN = 0.5, 1.0
ACTION_LEN = {1: 0.8, 2: 0.6, 3: 1.3, 4: 1.5, 5: 1.0, 6: 2.3, 7: 0.8, 8: 1.2}
ACTION_NAME = {1: "slam", 2: "swipe", 3: "strike", 4: "gungnir", 5: "sweep", 6: "quake", 7: "bite", 8: "lash"}


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
    return [-x, -y, z]


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymoskingdoms:atroxiia:" + ":".join(map(str, parts))))


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
        for name in ("north", "south", "up", "down"):
            out[name][0], out[name][2] = out[name][2], out[name][0]
    return out


def make_model():
    origins = {}

    def origin(name):
        if name in origins:
            return origins[name]
        row = ROWS[name]
        parent, pose = row[1], row[4]
        local = bb_point(*pose[:3])
        out = local if parent is None else [origin(parent)[i] + local[i] for i in range(3)]
        origins[name] = out
        return out

    elements, groups = [], {}
    for index, (name, parent, uv_name, box, pose, mirror) in enumerate(art.GEOMETRY):
        x, y, z, w, h, d = box
        o = origin(name)
        a, b = bb_point(x, y, z), bb_point(x + w, y + h, z + d)
        lo = [o[i] + min(a[i], b[i]) for i in range(3)]
        hi = [o[i] + max(a[i], b[i]) for i in range(3)]
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
        "name": NAME, "model_identifier": NAME, "visible_box": [12, 7, 14],
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


class Pose:
    """Old ModelPart rotation offsets in radians and pivot offsets in model pixels, by bone."""
    def __init__(self):
        self.rot, self.pos = {}, {}

    def r(self, bone, value):
        self.rot[bone] = add(self.rot.get(bone, (0.0, 0.0, 0.0)), value)
        return self

    def p(self, bone, value):
        self.pos[bone] = add(self.pos.get(bone, (0.0, 0.0, 0.0)), value)
        return self

    def only(self, bones):
        out = Pose()
        bones = set(bones)
        out.rot = {b: v for b, v in self.rot.items() if b in bones}
        out.pos = {b: v for b, v in self.pos.items() if b in bones}
        return out


def model_pose(t=0.0, walk_speed=0.0, step=0.0, crash=0.0, sit=0.0, swim=0.0, dead=0.0, hurt=0.0,
               action=0, action_x=0.0, idle_kind=-1, idle_into=0.0):
    """The old setupAnim, returned as offsets from rest; look and visibility are deliberately left to the renderer."""
    p = Pose()
    dead = ease(dead)
    crash, swim = ease(crash), ease(swim) * (1.0 - ease(crash))
    sit = ease(sit) * (1.0 - crash) * (1.0 - swim)
    walk = min(walk_speed * 2.2, 1.0) * (1.0 - crash) * (1.0 - sit) * (1.0 - swim) * (1.0 - dead)
    run = ease((walk_speed - 0.55) / 0.35) * walk
    x, a = action_x, action

    slamUp = rise(x, 0.0, 0.5) * (1.0 - rise(x, 0.5, 0.62)) if a == 1 else 0.0
    slamDown = rise(x, 0.5, 0.62) * (1.0 - rise(x, 0.7, 1.0)) if a == 1 else 0.0
    swipe = bell(x, 0.0, 1.0) if a == 2 else 0.0
    swipeAcross = rise(x, 0.2, 0.7) if a == 2 else 0.0
    jabL = bell(x, 0.14, 0.38) + bell(x, 0.62, 0.86) if a == 3 else 0.0
    jabR = bell(x, 0.38, 0.62) if a == 3 else 0.0
    arch = rise(x, 0.0, 0.45) * (1.0 - rise(x, 0.75, 1.0)) if a == 4 else 0.0
    thrust = rise(x, 0.5, 0.6) * (1.0 - rise(x, 0.7, 0.95)) if a == 4 else 0.0
    sweep = bell(x, 0.0, 1.0) if a == 5 else 0.0
    rear = (rise(x, 0.0, 0.4) * (1.0 - rise(x, 0.4, 0.46))
            + rise(x, 0.5, 0.68) * (1.0 - rise(x, 0.68, 0.74))) if a == 6 else 0.0
    roar = rise(x, 0.0, 0.15) * (1.0 - rise(x, 0.8, 1.0)) if a == 6 else 0.0
    biteBack = rise(x, 0.0, 0.35) * (1.0 - rise(x, 0.35, 0.5)) if a == 7 else 0.0
    biteIn = bell(x, 0.35, 1.0) if a == 7 else 0.0
    lashing = 1.0 if a == 8 else 0.0
    flinch = bell(1.0 - hurt, 0.0, 1.0) * (1.0 - dead) if hurt > 0.0 else 0.0

    idle = ((1.0 - min(walk_speed * 4.0, 1.0)) * (1.0 - sit) * (1.0 - crash) * (1.0 - swim) * (1.0 - dead)
            * (1.0 if a == 0 else 0.0))
    duration = 70.0 if idle_kind == 0 else 80.0 if idle_kind == 1 else 45.0
    fidget = idle * bell(idle_into, 0.0, duration) if idle_kind >= 0 else 0.0
    taunt = fidget if idle_kind == 0 else 0.0
    sniff = fidget if idle_kind == 1 else 0.0
    sniffTurn = sniff * math.sin(idle_into * math.pi / 40.0)
    grumble = fidget if idle_kind == 2 else 0.0
    doze = sit * sit

    heave = ((0.5 - 0.5 * math.cos(step * 2.0)) * (1.0 - run) + (0.5 - 0.5 * math.cos(step)) * run)
    drop = (crash * 20.0 + sit * 8.0 + swim * 6.0 - rear * 6.0 + slamDown * 1.5 + thrust * 1.5 + dead * 14.0
            - taunt * 4.0 + biteIn * 1.0)
    body_y = drop - heave * 2.6 * walk + math.sin(t * 0.07) * 0.25
    p.p("body", (0.0, body_y, 0.0))
    body_x = (-rear * 0.45 - slamUp * 0.12 + slamDown * 0.08 + thrust * 0.06 + swim * 0.15
              + (heave - 0.5) * 0.16 * run - taunt * 0.3 + biteIn * 0.06 - flinch * 0.12)
    body_z = math.sin(step) * 0.13 * walk * (1.0 - run) + flinch * 0.12 + dead * 0.2
    body_y_rot = (math.sin(step) * 0.14 * walk * (1.0 - run) + math.sin(t * 0.12) * 0.06 * swim
                  - swipeAcross * swipe * 0.15)
    p.r("body", (body_x, body_y_rot, body_z))

    neckDown = (crash + swim * 0.6 - roar * 0.25 + slamDown * 0.25 + thrust * 0.2 - biteBack * 0.45 + biteIn * 0.5
                - taunt * 0.3 + sniff * 0.9 - flinch * 0.35 + doze * 0.9 + dead * 1.2)
    for i, bone in enumerate(NECK):
        share = (3 - i) / 6.0
        rx = neckDown * share - body_x * (0.6 if i == 0 else 0.0)
        rx += math.sin(step * 2.0 - i * 0.5) * 0.03 * walk + math.sin(t * 0.05 - i * 0.45) * 0.02
        ry = -body_y_rot * (0.8 if i == 0 else 0.0) - math.sin(step - i * 0.6) * 0.04 * walk
        ry += sniffTurn * 0.25 + (doze * 0.5 if i == 2 else 0.0)
        p.r(bone, (rx, ry, 0.0))
    head_x = -neckDown * 0.4 - roar * 0.3 - taunt * 0.25
    p.r("head", (head_x, 0.0, grumble * 0.3 * math.sin(idle_into * 0.08) + dead * 0.35 - body_z * 0.6))
    jaw = (0.04 + roar * 0.8 + (0.5 - 0.5 * math.cos(t * 0.04)) * 0.04 + swipe * 0.25 + thrust * 0.3
           + biteBack * 0.9 + taunt * 0.75 + grumble * 0.3 + flinch * 0.45 + dead * 0.4)
    p.r("jaw", (jaw, 0.0, 0.0))
    head_absolute = REST["head"][0] + head_x
    p.r("braid", (math.sin(t * 0.08) * 0.04 + walk * 0.1 - head_absolute * 0.3, 0.0, 0.0))
    nemes = -math.sin(t * 0.07) * 0.02 - roar * 0.12
    p.r("nemes_l", (0.0, 0.0, nemes)).r("short_nemes_l", (0.0, 0.0, nemes))
    p.r("nemes_r", (0.0, 0.0, -nemes)).r("short_nemes_r", (0.0, 0.0, -nemes))

    lift = drop / 20.0
    for i, bone in enumerate(TAIL):
        k = i / 10.0
        rx = lift * 0.45 - (body_x if i == 0 else 0.0) if i <= 2 else -lift * 1.2 if i == 3 else 0.0
        if i >= 3:
            rx += arch * 0.27 + thrust * (0.7 if i == 3 else 0.3 if i == 4 else -0.12)
        rx += math.sin(t * 0.06 - i * 0.5) * 0.015
        wave = math.sin(step - i * 0.75) * (0.1 + k * 0.22) * walk * (1.0 - run * 0.5)
        lash = lashing * bell(x, k * 0.3, 0.6 + k * 0.4) * (0.15 + k * 0.35)
        curl = doze * (0.28 if i >= 2 else 0.1)
        ry = (wave - lash + curl) * (1.0 - dead) + math.sin(t * 0.05 - i * 0.5) * 0.03 * (1.0 - dead)
        ry += math.sin(t * 0.22 - i * 0.6) * (0.08 + k * 0.1) * swim
        ry += sweep * 0.32 * (1.0 - k * 0.3) * (1.0 if i >= 2 else 0.3) - (body_y_rot if i == 0 else 0.0)
        p.r(bone, (rx, ry, 0.0))

    for i, side_name in enumerate(SIDES):
        left = i == 0
        side = 1.0 if left else -1.0
        upper, fore = side_name + "_arm", side_name + "_forearm"
        hand = side_name + "_hand"
        phase = lerp(run, 0.0 if left else math.pi, 0.0 if left else 0.25)
        swing = math.cos(step + phase) * (0.75 + run * 0.15) * walk
        raise_ = (1.0 - math.sin(step + phase)) * 0.5
        elbow = raise_ * raise_ * walk
        up, low = REST[upper][0] + swing, REST[fore][0] + elbow
        uz, lz = REST[upper][2], REST[fore][2]
        if left:
            up, low = lerp(slamUp, up, -2.4), lerp(slamUp, low, -0.2)
            up, low = lerp(slamDown, up, -0.6), lerp(slamDown, low, 0.3)
        else:
            up, low = lerp(swipe, up, -1.3), lerp(swipe, low, 0.4)
            uz = lerp(swipe, uz, REST[upper][2] + side * (0.8 - swipeAcross * 1.4))
        jab = jabL if left else jabR
        up, low = lerp(jab, up, -1.4), lerp(jab, low, -0.1)
        up, low = lerp(rear, up, -1.9), lerp(rear, low, 0.2)
        up = lerp(taunt, up, -1.2)
        uz = lerp(taunt, uz, REST[upper][2] + (taunt * side) * 0.9)
        low = lerp(taunt, low, -0.3)
        up -= biteIn * 0.2
        up, low = lerp(sit, up, -0.5 - doze * 0.2), lerp(sit, low, 1.1 + doze * 0.2)
        up, low = lerp(dead, up, -0.6), lerp(dead, low, 0.4)
        uz = lerp(dead, uz, -side)
        up, low = lerp(crash, up, -0.4), lerp(crash, low, 0.2)
        uz, lz = lerp(crash, uz, -side * 1.2), lerp(crash, lz, side * 0.9)
        stroke = t * (0.08 + min(walk_speed, 1.0) * 0.14)
        up = lerp(swim, up, 1.2 + math.sin(stroke + phase) * 0.2)
        low = lerp(swim, low, 0.3)
        p.r(upper, (up - REST[upper][0], 0.0, uz - REST[upper][2]))
        p.r(fore, (low - REST[fore][0], 0.0, lz - REST[fore][2]))
        flat = 1.0 - max(max(slamUp * (1.0 if left else 0.0), jab), swim)
        p.r(hand, (-(up + low) * flat + (1.0 - flat) * -0.3, 0.0, -(uz + lz) * flat))
        curl = elbow * 0.6 + crash * 0.3 + (slamDown if left else swipe) * 0.5 - jab * 0.2
        for f in range(3):
            p.r(f"{side_name}_finger{f}", (curl, 0.0, 0.0))
        p.r(side_name + "_thumb", (curl * 0.6, 0.0, 0.0))
    return p


class Anim:
    def __init__(self, name, length, loop="once"):
        self.name, self.length, self.loop = name, length, loop
        self.keys = {}

    def key(self, time, pose, interpolation="catmullrom"):
        for bone, value in pose.rot.items():
            if max(map(abs, value)) < 1.0E-8:
                continue
            degrees = tuple(math.degrees(v) for v in value)
            self.keys.setdefault(bone, {}).setdefault("rotation", []).append((time, degrees, interpolation))
        for bone, value in pose.pos.items():
            if max(map(abs, value)) < 1.0E-8:
                continue
            self.keys.setdefault(bone, {}).setdefault("position", []).append((time, bb_point(*value), interpolation))
        return self

    def blockbench(self, uuids):
        animators = {}
        for bone, channels in self.keys.items():
            keyframes = []
            for channel, keys in channels.items():
                for index, (time, value, interpolation) in enumerate(keys):
                    keyframes.append({
                        "channel": channel, "data_points": [{"x": round(value[0], 5), "y": round(value[1], 5), "z": round(value[2], 5)}],
                        "uuid": uid("key", self.name, bone, channel, index), "time": round(time, 5), "color": -1,
                        "interpolation": interpolation,
                    })
            animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": keyframes}
        return {
            "uuid": uid("animation", self.name), "name": "animation.atroxiia." + self.name,
            "loop": self.loop, "override": False, "length": self.length, "snapping": 24, "selected": False,
            "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators,
        }


def descendants(root):
    out = {root}
    changed = True
    while changed:
        changed = False
        for name, row in ROWS.items():
            if row[1] in out and name not in out:
                out.add(name)
                changed = True
    return out


HEAD_GROUP = descendants("head")
TAIL_GROUP = set().union(*(descendants(t) for t in TAIL))
ARMS = {name for name in ROWS if name.startswith(("left_", "right_"))}
BODY_NECK = {"body", "chest", "collar", *NECK} | HEAD_GROUP
ALL = set(ROWS)
ACTION_BONES = {
    1: BODY_NECK | TAIL_GROUP | {n for n in ARMS if n.startswith("left_")},
    2: BODY_NECK | TAIL_GROUP | {n for n in ARMS if n.startswith("right_")},
    3: ARMS,
    4: BODY_NECK | TAIL_GROUP,
    5: TAIL_GROUP,
    6: BODY_NECK | TAIL_GROUP | ARMS,
    7: BODY_NECK | TAIL_GROUP | ARMS,
    8: TAIL_GROUP,
}


def sampled(name, length, pose_at, steps=16, loop="once"):
    anim = Anim(name, length, loop)
    for i in range(steps + 1):
        share = i / steps
        anim.key(length * share, pose_at(share))
    return anim


def build_animations():
    out = [sampled("idle", IDLE_LEN, lambda s: model_pose(t=s * 320.0), 32, "loop")]
    for kind, name in enumerate(("idle_taunt", "idle_sniff", "idle_grumble")):
        out.append(sampled(name, IDLE_LEN,
                           lambda s, kind=kind: model_pose(t=s * 320.0, idle_kind=kind, idle_into=s * 320.0), 64, "loop"))
    out.append(sampled("walk", WALK_LEN, lambda s: model_pose(walk_speed=1.0 / 2.2, step=math.tau * s), 24, "loop"))
    out.append(sampled("run", RUN_LEN, lambda s: model_pose(walk_speed=1.0, step=math.tau * s), 24, "loop"))
    out.append(sampled("sit", STATE_LEN, lambda s: model_pose(t=s * 80.0, sit=1.0), 16, "loop"))
    out.append(sampled("crash", STATE_LEN, lambda s: model_pose(t=s * 80.0, crash=1.0), 16, "loop"))
    out.append(sampled("swim", SWIM_LEN, lambda s: model_pose(t=s * 100.0, swim=1.0), 32, "loop"))
    for action, name in ACTION_NAME.items():
        length = ACTION_LEN[action]
        out.append(sampled(name, length,
                           lambda s, action=action: model_pose(t=s * ACTION_LEN[action] * 20.0, action=action, action_x=s)
                           .only(ACTION_BONES[action]), max(16, round(length * 20))))
    out.append(sampled("hurt", HURT_LEN, lambda s: model_pose(t=s * 10.0, hurt=1.0 - s).only(BODY_NECK | TAIL_GROUP), 10))
    death = sampled("death", DEATH_LEN, lambda s: model_pose(t=s * 20.0, dead=s), 20)
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
    assert all(len(e["faces"]) == 6 for e in model["elements"])
    model["animations"] = [anim.blockbench(uuids) for anim in build_animations()]
    os.makedirs(os.path.dirname(MODEL), exist_ok=True)
    with open(MODEL, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, indent="\t")
        f.write("\n")
    print("wrote", os.path.relpath(MODEL, ROOT), "bones", len(uuids), "animations", len(model["animations"]))
    subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"), MODEL, ASSETS, NAME], check=True)
    sheet = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
    if os.path.exists(sheet):
        os.remove(sheet)


if __name__ == "__main__":
    main()
