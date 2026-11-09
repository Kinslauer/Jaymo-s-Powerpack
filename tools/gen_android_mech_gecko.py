"""Shared GeckoLib rig and original keyframes for the four android mech builds.

The geometry and 128x128 skins come from our existing tools/gen_android_mech.py sources. This script merges their
optional parts into one skeleton, adds editable Blockbench keyframes, writes art/android_mech.bbmodel plus a filtered
editable project for each build, and exports the shared model/animation files with tools/bbmodel_geckolib.py.

Run from the repository root:
    python -I tools/gen_android_mech_gecko.py

Rotation keys use the old ModelPart convention (degrees; positive x follows AndroidMechModel's xRot). Position y is
positive when the old code moved a part up (torso.y -= 1), matching docs/AI-HANDBOOK.md's keyframe convention.
"""
import copy
import json
import math
import os
import subprocess
import sys
import uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "blockbench", "android_mech")
ART = os.path.join(ROOT, "art")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers")
BUILDS = ("warden", "bulwark", "strider", "reclaimer")
NAME = "android_mech"

# Old AndroidMechModel constants, in degrees.
STRIDE = math.degrees(0.7)
KNEE = math.degrees(0.7)
ARM_SWAY = math.degrees(0.3)
BODY_SWAY = math.degrees(0.05)
AIM = math.degrees(-1.45)
SMASH_UP = math.degrees(-3.5)
SMASH_DOWN = math.degrees(-0.5)
IDLE_LEN, WALK_LEN = 4.0, 1.0


def nodes_of(nodes):
    for node in nodes:
        if isinstance(node, dict):
            yield node
            yield from nodes_of(node.get("children", []))


def load_sources():
    out = {}
    for build in BUILDS:
        path = os.path.join(SOURCE, "android_mech_" + build + ".bbmodel")
        with open(path, encoding="utf-8") as f:
            out[build] = json.load(f)
    return out


def merge_skeleton(sources):
    """One skeleton with every optional part; the renderer hides the parts a build doesn't wear."""
    data = copy.deepcopy(sources["warden"])
    data["name"] = NAME
    data["model_identifier"] = NAME
    data["geckolib_modid"] = "jaymospowers"
    data["geckolib_filepath_cache"] = {
        "model": "src/main/resources/assets/jaymospowers/geckolib/models/entity/android_mech.geo.json",
        "animation": "src/main/resources/assets/jaymospowers/geckolib/animations/entity/android_mech.animation.json",
        "texture": "src/main/resources/assets/jaymospowers/textures/entity/future/android_mech_warden.png",
    }
    elements = {e["name"]: e for e in data["elements"]}
    nodes = {n["name"]: n for n in nodes_of(data["outliner"])}

    def add_missing(node, parent):
        existing = nodes.get(node["name"])
        if existing is None:
            existing = copy.deepcopy(node)
            existing["children"] = []
            if parent is None:
                data["outliner"].append(existing)
            else:
                parent["children"].append(existing)
            nodes[node["name"]] = existing
        for child in node.get("children", []):
            if isinstance(child, str):
                donor = next(e for e in sources[build]["elements"] if e["uuid"] == child)
                if donor["name"] not in elements:
                    copied = copy.deepcopy(donor)
                    data["elements"].append(copied)
                    elements[donor["name"]] = copied
                by_uuid = {e["uuid"]: e for e in data["elements"]}
                child_names = {by_uuid[c]["name"] for c in existing.get("children", []) if isinstance(c, str)}
                if donor["name"] not in child_names:
                    existing["children"].append(elements[donor["name"]]["uuid"])
            else:
                add_missing(child, existing)

    for build in BUILDS[1:]:
        donor_parents = {}
        for n in nodes_of(sources[build]["outliner"]):
            for child in n.get("children", []):
                if isinstance(child, dict):
                    donor_parents[child["name"]] = n["name"]
        for root in sources[build]["outliner"]:
            add_missing(root, None)
        # add_missing's recursion already places every descendant under its existing parent.
        assert donor_parents

    # Stable, build-independent ids make the generated projects and animation exports reproducible.
    old_to_new_element = {}
    for e in data["elements"]:
        old = e["uuid"]
        e["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers:android_mech:element:" + e["name"]))
        old_to_new_element[old] = e["uuid"]
    # Donor ids were copied after the initial element map; normalize every old id, not just warden's.
    for n in nodes_of(data["outliner"]):
        n["uuid"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers:android_mech:bone:" + n["name"]))
        n["children"] = [old_to_new_element.get(c, c) if isinstance(c, str) else c for c in n.get("children", [])]
    # Some copied donor cubes were not in the first old-id map. Key by name as a fallback.
    by_name = {e["name"]: e["uuid"] for e in data["elements"]}
    donor_id_to_name = {}
    for source in sources.values():
        donor_id_to_name.update({e["uuid"]: e["name"] for e in source["elements"]})
    for n in nodes_of(data["outliner"]):
        n["children"] = [by_name[donor_id_to_name[c]] if isinstance(c, str) and c in donor_id_to_name else c
                         for c in n.get("children", [])]
    return data


class Pose:
    def __init__(self):
        self.rot = {}
        self.pos = {}

    def r(self, bone, xyz):
        old = self.rot.get(bone, (0.0, 0.0, 0.0))
        self.rot[bone] = tuple(old[i] + xyz[i] for i in range(3))
        return self

    def p(self, bone, xyz):
        old = self.pos.get(bone, (0.0, 0.0, 0.0))
        self.pos[bone] = tuple(old[i] + xyz[i] for i in range(3))
        return self

    def copy(self):
        out = Pose()
        out.rot = dict(self.rot)
        out.pos = dict(self.pos)
        return out


class Anim:
    def __init__(self, name, length, loop="once"):
        self.name, self.length, self.loop = name, length, loop
        self.keys = {}

    def key(self, time, pose, interpolation="linear"):
        for bone, value in pose.rot.items():
            self.keys.setdefault(bone, {}).setdefault("rotation", []).append((time, value, interpolation))
        for bone, value in pose.pos.items():
            self.keys.setdefault(bone, {}).setdefault("position", []).append((time, value, interpolation))
        return self

    def blockbench(self, uuids):
        animators = {}
        for bone, channels in self.keys.items():
            keyframes = []
            for channel, keys in channels.items():
                for time, value, interpolation in keys:
                    keyframes.append({
                        "channel": channel,
                        "data_points": [{"x": round(value[0], 4), "y": round(value[1], 4), "z": round(value[2], 4)}],
                        "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL,
                                               "jaymospowers:android_mech:%s:%s:%s:%s" % (self.name, bone, channel, time))),
                        "time": round(time, 4), "color": -1, "interpolation": interpolation,
                    })
            animators[uuids[bone]] = {"name": bone, "type": "bone", "keyframes": keyframes}
        return {
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers:android_mech:animation:" + self.name)),
            "name": "animation.android_mech." + self.name,
            "loop": self.loop, "override": False, "length": self.length, "snapping": 24,
            "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
            "animators": animators,
        }


def animations():
    out = []

    idle = Anim("idle", IDLE_LEN, "loop")
    for i in range(9):
        t = IDLE_LEN * i / 8
        wave = math.sin(math.tau * i / 8)
        p = Pose().p("torso", (0, -0.3 * wave, 0))
        p.r("head", (1.2 * wave, 0, 0))
        p.r("right_arm", (0.8 * wave, 0, 0)).r("left_arm", (-0.8 * wave, 0, 0))
        idle.key(t, p, "catmullrom")
    out.append(idle)

    # This is the old setupAnim walk: opposite legs, a knee folding on each forward swing, counter-swinging arms,
    # and the same body roll. One cycle is one second before the controller's speed multiplier.
    walk = Anim("walk", WALK_LEN, "loop")
    for i in range(17):
        t = WALK_LEN * i / 16
        phase = math.tau * i / 16
        swing = math.cos(phase)
        p = Pose()
        p.r("right_leg", (-swing * STRIDE, 0, 0)).r("left_leg", (swing * STRIDE, 0, 0))
        p.r("shin_r", (max(0.0, math.sin(phase)) * KNEE, 0, 0))
        p.r("shin_l", (max(0.0, -math.sin(phase)) * KNEE, 0, 0))
        p.r("right_arm", (swing * ARM_SWAY, 0, 0)).r("left_arm", (-swing * ARM_SWAY, 0, 0))
        p.r("torso", (0, 0, math.sin(phase) * BODY_SWAY))
        walk.key(t, p)
    out.append(walk)

    # Reference-only pose for the old/new render comparison. In game the renderer supplies target pitch/yaw.
    aim = Anim("aim", 0.05, "loop")
    aim.key(0, Pose().r("right_arm", (AIM, 0, 0)).r("left_arm", (AIM, 0, 0)))
    out.append(aim)

    # Ten ticks, as the old attackAnimationTick: lift both fists, then bring them down together.
    smash = Anim("smash", 0.5)
    for t, angle, torso in ((0.0, 0.0, 0.0), (0.05, SMASH_DOWN, 0.0), (0.25, SMASH_UP, -5.0),
                            (0.40, SMASH_DOWN, 7.0), (0.5, 0.0, 0.0)):
        p = Pose().r("right_arm", (angle, 0, 0)).r("left_arm", (angle, 0, 0)).r("torso", (torso, 0, 0))
        smash.key(t, p)
    out.append(smash)

    # A shot's recoil lives below the shoulder, leaving the renderer's per-frame cannon aim intact.
    cannon = Anim("cannon", 0.24)
    for t, kick, slide in ((0.0, 0.0, 0.0), (0.04, 7.0, 1.4), (0.10, -2.0, -0.25), (0.24, 0.0, 0.0)):
        p = Pose()
        for side in ("r", "l"):
            p.r("forearm_" + side, (kick, 0, 0))
        p.p("cannon_r", (0, 0, slide)).p("cannon_l", (0, 0, slide))
        cannon.key(t, p)
    out.append(cannon)

    shell = Anim("shell", 0.65)
    for t, body, pack in ((0.0, 0.0, 0.0), (0.18, -10.0, -6.0), (0.30, 9.0, 8.0), (0.65, 0.0, 0.0)):
        p = Pose().r("torso", (body, 0, 0)).r("head", (-body * 0.5, 0, 0))
        p.r("backpack", (pack, 0, 0)).r("shell_pod", (pack, 0, 0))
        shell.key(t, p)
    out.append(shell)

    hurt = Anim("hurt", 0.35)
    for t, body, head in ((0.0, 0.0, 0.0), (0.07, -8.0, -12.0), (0.18, 4.0, 5.0), (0.35, 0.0, 0.0)):
        hurt.key(t, Pose().r("torso", (body, 0, 0)).r("head", (head, 0, 0)))
    out.append(hurt)

    # The legs buckle and the upper chassis pitches forward; the action controller holds the final frame.
    death = Anim("death", 0.9)
    death.key(0.0, Pose())
    death.key(0.18, Pose().r("right_leg", (-16, 0, 0)).r("left_leg", (-16, 0, 0))
              .r("shin_r", (28, 0, 0)).r("shin_l", (28, 0, 0)).r("torso", (-12, 0, 0)))
    death.key(0.65, Pose().r("right_leg", (-32, 0, 5)).r("left_leg", (-32, 0, -5))
              .r("shin_r", (65, 0, 0)).r("shin_l", (65, 0, 0))
              .r("torso", (58, 0, 4)).p("torso", (0, -8, 2))
              .r("right_arm", (-25, 0, -8)).r("left_arm", (-25, 0, 8)).r("head", (24, 0, 0)))
    death.key(0.9, Pose().r("right_leg", (-35, 0, 5)).r("left_leg", (-35, 0, -5))
              .r("shin_r", (72, 0, 0)).r("shin_l", (72, 0, 0))
              .r("torso", (72, 0, 4)).p("torso", (0, -10, 3))
              .r("right_arm", (-32, 0, -8)).r("left_arm", (-32, 0, 8)).r("head", (30, 0, 0)))
    out.append(death)
    return out


def filtered_project(data, source, build):
    """Editable per-build project with its own original skin and only the parts that build shows."""
    out = copy.deepcopy(data)
    out["name"] = NAME + "_" + build
    out["textures"] = copy.deepcopy(source["textures"])
    allowed = {n["name"] for n in nodes_of(source["outliner"])}
    elements = {e["uuid"]: e for e in out["elements"]}

    def keep(node):
        if isinstance(node, str):
            return elements[node]["name"] in allowed
        return node["name"] in allowed

    def filter_nodes(nodes):
        result = []
        for node in nodes:
            if not keep(node):
                continue
            if isinstance(node, dict):
                node["children"] = filter_nodes(node.get("children", []))
            result.append(node)
        return result

    out["outliner"] = filter_nodes(out["outliner"])
    used = {c for n in nodes_of(out["outliner"]) for c in n.get("children", []) if isinstance(c, str)}
    out["elements"] = [e for e in out["elements"] if e["uuid"] in used]
    for anim in out["animations"]:
        anim["animators"] = {k: v for k, v in anim["animators"].items() if v["name"] in allowed}
    return out


def validate(data):
    names = [n["name"] for n in nodes_of(data["outliner"])]
    assert len(names) == len(set(names)) == 33, names
    assert len(data["elements"]) == 33
    for e in data["elements"]:
        assert len(e["faces"]) == 6, e["name"]
        assert all(fc.get("texture") == 0 for fc in e["faces"].values()), e["name"]
        for fc in e["faces"].values():
            u0, v0, u1, v1 = fc["uv"]
            assert 0 <= min(u0, u1) <= max(u0, u1) <= 128, (e["name"], fc)
            assert 0 <= min(v0, v1) <= max(v0, v1) <= 128, (e["name"], fc)
    expected = {"idle", "walk", "aim", "smash", "cannon", "shell", "hurt", "death"}
    assert {a["name"].split(".")[-1] for a in data["animations"]} == expected


def main():
    sources = load_sources()
    data = merge_skeleton(sources)
    uuids = {n["name"]: n["uuid"] for n in nodes_of(data["outliner"])}
    data["animations"] = [a.blockbench(uuids) for a in animations()]
    validate(data)
    os.makedirs(ART, exist_ok=True)
    projects = [(NAME, data)] + [(NAME + "_" + b, filtered_project(data, sources[b], b)) for b in BUILDS]
    for name, project in projects:
        path = os.path.join(ART, name + ".bbmodel")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(project, f, indent="\t")
        print("wrote", os.path.relpath(path, ROOT))
    subprocess.run([sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"),
                    os.path.join(ART, NAME + ".bbmodel"), ASSETS, NAME], check=True)
    # The renderer picks the original per-build skins in textures/entity/future/; don't ship a duplicate atlas.
    atlas = os.path.join(ASSETS, "textures", "entity", NAME + ".png")
    if os.path.exists(atlas):
        os.remove(atlas)


if __name__ == "__main__":
    main()
