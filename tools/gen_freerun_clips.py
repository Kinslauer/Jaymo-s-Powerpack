#!/usr/bin/env python3
"""Writes freerun animation clips (Powers freerun / client pose classes) as GeckoLib .animation.json files.

The script is the source: change a pose here and run it again
(`python3 tools/gen_freerun_clips.py`). Poses are vanilla ModelPart angles in degrees
(xRot, yRot, zRot); the files take Blockbench's signs, x and y turned round
(KeyframeClip.partX/partY turn them back). Each clip runs from share 0 to 1, so times
here are shares of the clip.

Bones: body, head, right_arm, left_arm, right_leg, left_leg.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "jaymospowers", "geckolib", "animations", "entity")

STAND = {b: (0, 0, 0) for b in ("body", "head", "right_arm", "left_arm", "right_leg", "left_leg")}


def mirror(pose):
    """The same pose from the other side: right and left swapped, y and z turned round."""
    out = {}
    for bone, (x, y, z) in pose.items():
        other = bone.replace("right", "LEFT").replace("left", "right").replace("LEFT", "left")
        out[other] = (x, -y, -z)
    return out


# ---------------------------------------------------------------------------
# Cling: clinging to a wall, hands on it in front, climbing up hand over hand.
# Arms reach forward and slightly up, gripping; the body stays close to the wall;
# legs push against it, one knee drawn up then the other.
# ---------------------------------------------------------------------------
CLING = [
    (0.00, {
        "body": (0, 0, 0),
        "head": (-10, 0, 0),
        "right_arm": (-20, 0, 10),
        "left_arm": (-20, 0, -10),
        "right_leg": (-25, 0, 8),
        "left_leg": (-25, 0, -8),
    }),
    (0.25, {
        "body": (0, 0, 0),
        "head": (-10, 0, 0),
        "right_arm": (-45, 0, 10),
        "left_arm": (-10, 0, -10),
        "right_leg": (-40, 0, 12),
        "left_leg": (-15, 0, -8),
    }),
    (0.50, {
        "body": (0, 0, 0),
        "head": (-10, 0, 0),
        "right_arm": (-20, 0, 10),
        "left_arm": (-45, 0, -10),
        "right_leg": (-15, 0, 8),
        "left_leg": (-40, 0, -12),
    }),
    (0.75, {
        "body": (0, 0, 0),
        "head": (-10, 0, 0),
        "right_arm": (-45, 0, 10),
        "left_arm": (-10, 0, -10),
        "right_leg": (-40, 0, 12),
        "left_leg": (-15, 0, -8),
    }),
    (1.00, {
        "body": (0, 0, 0),
        "head": (-10, 0, 0),
        "right_arm": (-20, 0, 10),
        "left_arm": (-20, 0, -10),
        "right_leg": (-25, 0, 8),
        "left_leg": (-25, 0, -8),
    }),
]

# ---------------------------------------------------------------------------
# Ledge hang: hanging from a ledge, both arms up on the edge, legs dangling,
# swaying gently from the hands.
# ---------------------------------------------------------------------------
LEDGE_HANG = [
    (0.00, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 4),
        "left_leg": (20, 0, -4),
    }),
    (0.25, {
        "body": (5, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 8),
        "left_leg": (20, 0, -8),
    }),
    (0.50, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 4),
        "left_leg": (20, 0, -4),
    }),
    (0.75, {
        "body": (-5, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 8),
        "left_leg": (20, 0, -8),
    }),
    (1.00, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 4),
        "left_leg": (20, 0, -4),
    }),
]

# ---------------------------------------------------------------------------
# Ledge mantle: climbing up and over a ledge - pull up, knee onto the edge,
# then stand. Runs from hanging (0) to standing on top (1).
# ---------------------------------------------------------------------------
LEDGE_MANTLE = [
    (0.00, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 4),
        "left_leg": (20, 0, -4),
    }),
    (0.30, {
        "body": (-38, 0, 0),
        "head": (-15, 0, 0),
        "right_arm": (-26, 0, 10),
        "left_arm": (-26, 0, -10),
        "right_leg": (-86, 0, 2),
        "left_leg": (20, 0, -4),
    }),
    (0.60, {
        "body": (-38, 0, 0),
        "head": (-15, 0, 0),
        "right_arm": (-26, 0, 10),
        "left_arm": (-26, 0, -10),
        "right_leg": (-86, 0, 2),
        "left_leg": (-52, 0, -2),
    }),
    (0.85, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-10, 0, 5),
        "left_arm": (-10, 0, -5),
        "right_leg": (-5, 0, 2),
        "left_leg": (-5, 0, -2),
    }),
    (1.00, STAND),
]

# ---------------------------------------------------------------------------
# Climb down (Part B): the reverse of climbing up - lower from standing on top,
# one knee then the other down over the edge, then hang. Runs from standing (0)
# to hanging (1).
# ---------------------------------------------------------------------------
CLIMB_DOWN = [
    (0.00, STAND),
    (0.20, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-10, 0, 5),
        "left_arm": (-10, 0, -5),
        "right_leg": (-40, 0, 2),
        "left_leg": (-5, 0, -2),
    }),
    (0.50, {
        "body": (-38, 0, 0),
        "head": (-15, 0, 0),
        "right_arm": (-26, 0, 10),
        "left_arm": (-26, 0, -10),
        "right_leg": (-86, 0, 2),
        "left_leg": (-52, 0, -2),
    }),
    (0.80, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 4),
        "left_leg": (20, 0, -4),
    }),
    (1.00, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-163, 0, 6),
        "left_arm": (-163, 0, -6),
        "right_leg": (20, 0, 4),
        "left_leg": (20, 0, -4),
    }),
]

# ---------------------------------------------------------------------------
# Shimmy (Part B - around corners): moving sideways along a wall, hand over hand.
# The body rocks from hand to hand as the hands reach along the edge.
# ---------------------------------------------------------------------------
SHIMMY = [
    (0.00, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-20, 0, 10),
        "left_arm": (-20, 0, -10),
        "right_leg": (-25, 0, 8),
        "left_leg": (-25, 0, -8),
    }),
    (0.25, {
        "body": (4, 0, 2),
        "head": (0, 0, 0),
        "right_arm": (-45, 0, 12),
        "left_arm": (-10, 0, -8),
        "right_leg": (-35, 0, 10),
        "left_leg": (-18, 0, -6),
    }),
    (0.50, {
        "body": (0, 0, -4),
        "head": (0, 0, 0),
        "right_arm": (-10, 0, 8),
        "left_arm": (-45, 0, -12),
        "right_leg": (-18, 0, 6),
        "left_leg": (-35, 0, -10),
    }),
    (0.75, {
        "body": (-4, 0, -2),
        "head": (0, 0, 0),
        "right_arm": (-45, 0, 12),
        "left_arm": (-10, 0, -8),
        "right_leg": (-35, 0, 10),
        "left_leg": (-18, 0, -6),
    }),
    (1.00, {
        "body": (0, 0, 0),
        "head": (0, 0, 0),
        "right_arm": (-20, 0, 10),
        "left_arm": (-20, 0, -10),
        "right_leg": (-25, 0, 8),
        "left_leg": (-25, 0, -8),
    }),
]

CLIPS = {
    "player_freerun_cling": CLING,
    "player_freerun_ledge_hang": LEDGE_HANG,
    "player_freerun_ledge_mantle": LEDGE_MANTLE,
    "player_freerun_climb_down": CLIMB_DOWN,
    "player_freerun_shimmy": SHIMMY,
}


def to_blockbench(x, y, z):
    return [round(-x, 2), round(-y, 2), round(z, 2)]


def write(name, keys):
    bones = {}
    for bone in STAND:
        frames = {}
        for i, (t, pose) in enumerate(keys):
            ends = i == 0 or i == len(keys) - 1
            frames[f"{t:.2f}"] = {"vector": to_blockbench(*pose[bone]),
                                  "lerp_mode": "linear" if ends else "catmullrom"}
        bones[bone] = {"rotation": frames}
    data = {"format_version": "1.8.0",
            "animations": {name: {"loop": False, "animation_length": 1.0, "bones": bones}}}
    path = os.path.join(OUT, f"{name}.animation.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")
    print(path)


if __name__ == "__main__":
    for name, keys in CLIPS.items():
        write(name, keys)
