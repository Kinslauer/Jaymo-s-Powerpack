"""Longsword trial, the Blockbench route: three Still Water longsword moves keyed by hand on the blocky player, the way
an animator keys them in Blockbench, and played the way Blockbench plays them.

    python -I tools/combat_trial/blockbench_route.py               # write the .bbmodel from KEYS, then convert it
    python -I tools/combat_trial/blockbench_route.py --from-model  # convert art/combat_trial/longsword_blockbench.bbmodel
                                                                   # as it is (after editing it in Blockbench)

The model (art/combat_trial/longsword_blockbench.bbmodel) is the player's boxes with the forearms as their own bones, and
a sword in the right fist; it opens in Blockbench as a Modded Entity. Each pose below is a key per bone, in whole
degrees with Blockbench's signs: a bone's X and Y turn the other way from the game's (KeyframeClip), so an arm raised
forward is +X here. A forearm's X is the elbow's fold. Keys ease smoothly through each other (Blockbench's Catmull-Rom),
except into a strike, which is linear. Each arm is keyed on its own (forward kinematics): the left fist sits on the hilt
at every key, and may drift off it between keys. That drift is part of what the trial compares with the Blender route.

Every tick of each animation is sampled, as Blockbench interpolates it, into the game's player_animations
(tools/combat_trial/emote.py). These are the default; the Blender route's are in the built-in resource pack
"longsword_blender", over them when it's switched on.
"""
import json
import math
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import emote  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODEL = os.path.join(ROOT, "art", "combat_trial", "longsword_blockbench.bbmodel")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "player_animations")

# The key poses, game-side (MC degrees: pitch, yaw, roll, elbow fold) for (right arm, left arm). The right fist was
# placed first; the left one then set on the hilt, 4 pixels up it toward the pommel. The torso stays still: Player
# Animation Library turns it the other way from the game and doesn't carry the arms round with it.
POSES = {
    # middle guard: hands before the belly, the point at the eyes
    "guard": ((-28, -15, -19, 1), (-5, 10, 38, 0)),
    # the guard's breath: the hands rise a little
    "breath": ((-30, -15, -19, 4), (-8, 13, 37, 0)),
    # first cut: the sword up by the right shoulder
    "cut_up": ((-37, 1, 21, 98), (-70, 58, 14, 0)),
    # ... falling across, the hands out in front
    "cut_hit": ((-48, -1, -57, 0), (-9, 24, 17, 43)),
    # ... and through, low on the left
    "cut_end": ((-20, 25, -56, 0), (48, 1, 25, 70)),
    # heavy overhead: the sword raised high behind the head
    "ovh_up": ((-155, -12, 24, 0), (-137, 21, -32, 0)),
    # ... falling straight down the middle
    "ovh_hit": ((-52, -18, -24, 0), (-29, 26, 26, 0)),
    # ... and through, the point low
    "ovh_end": ((-19, -1, -28, 0), (3, 4, 37, 0)),
}

# name -> (description, loop, beginTick, endTick, [(tick, pose, interpolation)])
MOVES = {
    "longsword_guard": ("Longsword (Still Water): the middle guard, breathing", True, 0, 40, [
        (0, "guard", "catmullrom"), (20, "breath", "catmullrom"), (40, "guard", "catmullrom")]),
    "longsword_first_cut": ("Longsword (Still Water): from the guard, a cut falling right to left", False, 0, 20, [
        (0, "guard", "catmullrom"), (7, "cut_up", "catmullrom"), (10, "cut_hit", "linear"),
        (13, "cut_end", "catmullrom"), (20, "cut_end", "catmullrom"), (30, "guard", "catmullrom")]),
    "longsword_heavy_overhead": ("Longsword (Still Water): raised high, then a falling cut down the middle", False, 0, 20, [
        (0, "guard", "catmullrom"), (9, "ovh_up", "catmullrom"), (10, "ovh_up", "catmullrom"),
        (12, "ovh_hit", "linear"), (15, "ovh_end", "catmullrom"), (20, "ovh_end", "catmullrom"),
        (30, "guard", "catmullrom")]),
}

# --- the model ------------------------------------------------------------------------------------------------------
# Blockbench's space: y up from the feet, the player's right at +x, facing -z (the game's model space turned half round
# about z, and 24 pixels up).
BONES = {
    # name: (pivot, parent, [cube (from, to)])
    "body": ((0, 24, 0), None, [((-4, 12, -2), (4, 24, 2))]),
    "head": ((0, 24, 0), "body", [((-4, 24, -4), (4, 32, 4))]),
    "right_arm": ((5, 22, 0), "body", [((4, 18, -2), (8, 24, 2))]),
    "right_forearm": ((5, 18, 0), "right_arm", [((4, 12, -2), (8, 18, 2))]),
    "sword": ((6, 12, -2), "right_forearm", [((5.5, 11.5, -28), (6.5, 12.5, 4)), ((3.5, 11.5, -3), (8.5, 12.5, -2))]),
    "left_arm": ((-5, 22, 0), "body", [((-8, 18, -2), (-4, 24, 2))]),
    "left_forearm": ((-5, 18, 0), "left_arm", [((-8, 12, -2), (-4, 18, 2))]),
    "right_leg": ((1.9, 12, 0), None, [((-0.1, 0, -2), (3.9, 12, 2))]),
    "left_leg": ((-1.9, 12, 0), None, [((-3.9, 0, -2), (0.1, 12, 2))]),
}
# the blade leaves the fist 5 degrees up from straight ahead (body.BLADE)
SWORD_TILT = (5, 0, 0)


def bb_keys(pose):
    """A pose's keys per bone, Blockbench's way round."""
    (rp, ry, rr, rb), (lp, ly, lr, lb) = POSES[pose]
    return {"right_arm": (-rp, -ry, rr), "right_forearm": (rb, 0, 0),
            "left_arm": (-lp, -ly, lr), "left_forearm": (lb, 0, 0)}


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers/combat_trial/" + name))


def write_model():
    elements, groups = [], {}
    for name, (pivot, parent, cubes) in BONES.items():
        children = []
        for i, (a, b) in enumerate(cubes):
            cid = uid(f"{name}/cube{i}")
            elements.append({"name": name, "box_uv": True, "rescale": False, "locked": False, "from": list(a), "to": list(b),
                             "autouv": 0, "color": len(elements) % 8, "origin": list(pivot), "uv_offset": [0, 0],
                             "faces": {f: {"uv": [0, 0, 0, 0], "texture": None} for f in
                                       ("north", "east", "south", "west", "up", "down")},
                             "type": "cube", "uuid": cid})
            children.append(cid)
        groups[name] = {"name": name, "origin": list(pivot), "rotation": list(SWORD_TILT) if name == "sword" else [0, 0, 0],
                        "color": 0, "uuid": uid(name), "export": True, "isOpen": True, "visibility": True,
                        "children": children, "_parent": parent}
    outliner = []
    for name, g in groups.items():
        parent = g.pop("_parent")
        (groups[parent]["children"] if parent else outliner).append(g)

    animations = []
    for name, (desc, loop, begin, end, frames) in MOVES.items():
        animators = {}
        for tick, pose, interp in frames:
            for bone, rot in bb_keys(pose).items():
                a = animators.setdefault(uid(bone), {"name": bone, "type": "bone", "keyframes": []})
                a["keyframes"].append({"channel": "rotation", "data_points": [{"x": rot[0], "y": rot[1], "z": rot[2]}],
                                       "uuid": uid(f"{name}/{bone}/{tick}"), "time": tick / 20, "color": -1,
                                       "interpolation": interp})
        animations.append({"uuid": uid(name), "name": name, "loop": "loop" if loop else "once", "override": False,
                           "length": frames[-1][0] / 20, "snapping": 20, "selected": False, "anim_time_update": "",
                           "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})
    model = {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
             "name": "longsword_blockbench", "model_identifier": "", "modded_entity_entity_class": "",
             "modded_entity_version": "Fabric 1.17+", "modded_entity_flip_y": True, "visible_box": [1, 1, 0],
             "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [],
             "unhandled_root_fields": {}, "resolution": {"width": 64, "height": 64}, "elements": elements,
             "outliner": outliner, "textures": [], "animations": animations}
    os.makedirs(os.path.dirname(MODEL), exist_ok=True)
    with open(MODEL, "w", newline="\n") as f:
        json.dump(model, f, indent="\t")
        f.write("\n")


# --- Blockbench's playback ------------------------------------------------------------------------------------------

def catmull(t, p0, p1, p2, p3):
    v0, v1 = (p2 - p0) * 0.5, (p3 - p1) * 0.5
    return ((2 * p1 - 2 * p2 + v0 + v1) * t + (-3 * p1 + 3 * p2 - 2 * v0 - v1)) * t * t + v0 * t + p1


def sample(keys, time, axis):
    """A channel at `time` (seconds), as Blockbench interpolates it: Catmull-Rom between two keys where either is
    catmullrom (through the keys either side), else straight; held before the first key and after the last."""
    val = [float(k["data_points"][0][axis]) for k in keys]
    times = [k["time"] for k in keys]
    if time <= times[0]:
        return val[0]
    if time >= times[-1]:
        return val[-1]
    i = max(j for j in range(len(keys)) if times[j] <= time)
    if i == len(keys) - 1:
        return val[-1]
    a, b = keys[i], keys[i + 1]
    t = (time - times[i]) / (times[i + 1] - times[i]) if times[i + 1] > times[i] else 1.0
    if a["interpolation"] == "step":
        return val[i]
    if "catmullrom" in (a["interpolation"], b["interpolation"]):
        p0 = val[i - 1] if i > 0 else val[i]
        p3 = val[i + 2] if i + 2 < len(keys) else val[i + 1]
        return catmull(t, p0, val[i], val[i + 1], p3)
    return val[i] + (val[i + 1] - val[i]) * t


def convert():
    with open(MODEL) as f:
        model = json.load(f)
    for anim in model["animations"]:
        name = anim["name"]
        desc, loop, begin, end, _ = MOVES[name]
        bones = {}
        for a in anim["animators"].values():
            keys = sorted((k for k in a["keyframes"] if k["channel"] == "rotation"), key=lambda k: k["time"])
            if keys:
                bones[a["name"]] = keys
        samples = []
        for tick in range(round(anim["length"] * 20) + 1):
            def rot(bone):
                keys = bones.get(bone)
                if not keys:
                    return 0.0, 0.0, 0.0
                return tuple(math.radians(sample(keys, tick / 20, ax)) for ax in "xyz")
            pose = {}
            for side, part in (("right", "rightArm"), ("left", "leftArm")):
                x, y, z = rot(side + "_arm")
                pose[part] = {"pitch": -x, "yaw": -y, "roll": z, "bend": rot(side + "_forearm")[0]}
            samples.append(pose)
        emote.write(os.path.join(OUT, name + ".json"), name, desc + " (Blockbench route)", samples, begin, end,
                    loop=loop)


if __name__ == "__main__":
    if "--from-model" not in sys.argv:
        write_model()
    convert()
