#!/usr/bin/env python3
"""Writes the mount-climbing clips (Powers MountClimb / client MountClimbPose) as GeckoLib .animation.json files.

The script is the source: change a pose here and run it again (python3 tools/gen_mount_climb_clips.py). Poses are
written as a vanilla ModelPart's angles in degrees (xRot, yRot, zRot); the files take Blockbench's signs, x and y
turned round (KeyframeClip.partX/partY turn them back). Each clip runs from share 0 to 1 (the game stretches it to the
climb's real length, longer in armour), so times here are shares of the clip.

Climbs onto a mount end on vanilla's riding pose, and climbs off start on it, so nothing jumps when the seat takes over.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers",
                   "geckolib", "animations", "entity")

# vanilla's riding pose (HumanoidModel.setupAnim, riding): legs forward and splayed, arms forward to the reins
RIDE = {
    "body": (0, 0, 0),
    "right_arm": (-36, 0, 0),
    "left_arm": (-36, 0, 0),
    "right_leg": (-81, 18, 4.5),
    "left_leg": (-81, -18, -4.5),
}
STAND = {b: (0, 0, 0) for b in RIDE}


def mirror(pose):
    """The same pose from the other side: right and left swapped, y and z turned round."""
    out = {}
    for bone, (x, y, z) in pose.items():
        other = bone.replace("right", "LEFT").replace("left", "right").replace("LEFT", "left")
        out[other] = (x, -y, -z)
    return out


# from the mount's left: left foot in the stirrup, hands on the saddle, up, the right leg swung out and over, sat
ON_LEFT = [
    (0.0, STAND),
    (0.25, {"body": (12, 0, 0), "right_arm": (-150, 0, 12), "left_arm": (-140, 0, -8),
            "right_leg": (6, 0, 2), "left_leg": (-95, 0, -10)}),
    (0.5, {"body": (25, 0, -6), "right_arm": (-120, 0, 10), "left_arm": (-115, 0, -6),
           "right_leg": (-30, 0, 62), "left_leg": (-40, 0, -6)}),
    (0.75, {"body": (14, 0, -3), "right_arm": (-60, 0, 4), "left_arm": (-55, 0, -4),
            "right_leg": (-70, 18, 22), "left_leg": (-70, -18, -6)}),
    (1.0, RIDE),
]

# from behind: hands flat on the rump, a jump with the legs parted, a slide forward into the seat
ON_BACK = [
    (0.0, STAND),
    (0.2, {"body": (35, 0, 0), "right_arm": (-100, 0, 8), "left_arm": (-100, 0, -8),
           "right_leg": (22, 0, 3), "left_leg": (22, 0, -3)}),
    (0.45, {"body": (22, 0, 0), "right_arm": (-75, 0, 18), "left_arm": (-75, 0, -18),
            "right_leg": (-60, 10, 55), "left_leg": (-60, -10, -55)}),
    (0.7, {"body": (10, 0, 0), "right_arm": (-45, 0, 6), "left_arm": (-45, 0, -6),
           "right_leg": (-80, 18, 15), "left_leg": (-80, -18, -15)}),
    (1.0, RIDE),
]

# down the side: hands on the saddle, the right leg swung back over, a drop, standing
OFF_FORWARD = [
    (0.0, RIDE),
    (0.3, {"body": (20, 0, 6), "right_arm": (-110, 0, 8), "left_arm": (-105, 0, -8),
           "right_leg": (-40, 0, 60), "left_leg": (-60, -18, -6)}),
    (0.6, {"body": (12, 0, 3), "right_arm": (-60, 0, 6), "left_arm": (-55, 0, -6),
           "right_leg": (10, 0, 10), "left_leg": (-40, 0, -4)}),
    (0.85, {"body": (8, 0, 0), "right_arm": (-15, 0, 4), "left_arm": (-15, 0, -4),
            "right_leg": (-12, 0, 2), "left_leg": (-14, 0, -2)}),
    (1.0, STAND),
]

# back over the rump: pushed back on the hands, slid off, landing bent at the knees
OFF_BACK = [
    (0.0, RIDE),
    (0.35, {"body": (30, 0, 0), "right_arm": (-90, 0, 14), "left_arm": (-90, 0, -14),
            "right_leg": (-50, 10, 40), "left_leg": (-50, -10, -40)}),
    (0.7, {"body": (15, 0, 0), "right_arm": (-30, 0, 8), "left_arm": (-30, 0, -8),
           "right_leg": (-15, 0, 5), "left_leg": (-15, 0, -5)}),
    (1.0, STAND),
]

# heaving a downed friend over a horse's back (Sling): from a hold in the arms, a crouch under the weight, then up and
# over in one push with the arms thrown high and the back arched, and a settle back to standing
HEAVE = [
    (0.0, {"body": (0, 0, 0), "right_arm": (-55, 0, -8), "left_arm": (-55, 0, 8),
           "right_leg": (0, 0, 2), "left_leg": (0, 0, -2)}),
    (0.3, {"body": (22, 0, 0), "right_arm": (-30, 0, -12), "left_arm": (-30, 0, 12),
           "right_leg": (-28, 0, 4), "left_leg": (14, 0, -4)}),
    (0.6, {"body": (-14, 0, 0), "right_arm": (-160, 0, -10), "left_arm": (-160, 0, 10),
           "right_leg": (12, 0, 3), "left_leg": (-22, 0, -3)}),
    (0.82, {"body": (-4, 0, 0), "right_arm": (-110, 0, -6), "left_arm": (-110, 0, 6),
            "right_leg": (4, 0, 2), "left_leg": (-6, 0, -2)}),
    (1.0, STAND),
]

CLIPS = {
    "on_left": ON_LEFT,
    "on_right": [(t, mirror(p)) for t, p in ON_LEFT],
    "on_back": ON_BACK,
    "off_forward": OFF_FORWARD,
    "off_back": OFF_BACK,
    "heave": HEAVE,
}


def to_blockbench(x, y, z):
    return [round(-x, 2), round(-y, 2), round(z, 2)]


def write(name, keys):
    bones = {}
    for bone in RIDE:
        frames = {}
        for i, (t, pose) in enumerate(keys):
            ends = i == 0 or i == len(keys) - 1
            frames[f"{t:.2f}"] = {"vector": to_blockbench(*pose[bone]), "lerp_mode": "linear" if ends else "catmullrom"}
        bones[bone] = {"rotation": frames}
    data = {"format_version": "1.8.0",
            "animations": {name: {"loop": False, "animation_length": 1.0, "bones": bones}}}
    path = os.path.join(OUT, f"player_mount_{name}.animation.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")
    print(path)


if __name__ == "__main__":
    for name, keys in CLIPS.items():
        write(name, keys)
