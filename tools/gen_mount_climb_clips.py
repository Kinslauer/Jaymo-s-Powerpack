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

# onto a kneeling horse (armoured rider): low and short, a hand on the saddle, one leg lifted across, a settle into the seat
ON_KNEEL = [
    (0.0, STAND),
    (0.3, {"body": (14, 0, 0), "right_arm": (-70, 0, 10), "left_arm": (-65, 0, -8),
           "right_leg": (-55, 0, 24), "left_leg": (-8, 0, -2)}),
    (0.65, {"body": (10, 0, -2), "right_arm": (-55, 0, 6), "left_arm": (-50, 0, -4),
            "right_leg": (-72, 12, 18), "left_leg": (-40, -6, -4)}),
    (1.0, RIDE),
]

# up behind the rider in the saddle: both hands taken and pulled up, a foot hitched, the leg swung over, a settle
ON_PILLION = [
    (0.0, STAND),
    (0.25, {"body": (8, 0, 0), "right_arm": (-160, 0, 8), "left_arm": (-150, 0, -10),
            "right_leg": (-45, 0, 12), "left_leg": (-5, 0, -2)}),
    (0.55, {"body": (-8, 0, 0), "right_arm": (-140, 0, 6), "left_arm": (-120, 0, -6),
            "right_leg": (-85, 14, 40), "left_leg": (-60, -6, -6)}),
    (0.8, {"body": (4, 0, 0), "right_arm": (-80, 0, 4), "left_arm": (-70, 0, -4),
           "right_leg": (-82, 18, 12), "left_leg": (-78, -18, -6)}),
    (1.0, RIDE),
]

# the horse shied: from wherever the climb had got to (the game blends in the pose it was in), arms out, down the flank to a stagger
OFF_SLIP = [
    (0.0, {"body": (18, 0, 4), "right_arm": (-100, 0, 14), "left_arm": (-95, 0, -14),
           "right_leg": (-40, 0, 30), "left_leg": (-40, 0, -10)}),
    (0.4, {"body": (24, 0, 8), "right_arm": (-60, 0, 40), "left_arm": (-70, 0, -35),
           "right_leg": (-20, 0, 12), "left_leg": (-25, 0, -4)}),
    (0.75, {"body": (14, 0, 0), "right_arm": (-25, 0, 24), "left_arm": (-30, 0, -22),
            "right_leg": (-14, 0, 4), "left_leg": (-14, 0, -4)}),
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

# onto a kneeling war elephant or mammoth: a foot up on the folded foreleg, hands to the harness, hauled up the flank, the leg swung over
ON_BIG_KNEEL = [
    (0.0, STAND),
    (0.25, {"body": (10, 0, 0), "right_arm": (-140, 0, 10), "left_arm": (-130, 0, -8),
            "right_leg": (-75, 0, 12), "left_leg": (-5, 0, -2)}),
    (0.55, {"body": (22, 0, -4), "right_arm": (-125, 0, 8), "left_arm": (-115, 0, -6),
            "right_leg": (-40, 0, 55), "left_leg": (-60, 0, -4)}),
    (0.8, {"body": (12, 0, -2), "right_arm": (-65, 0, 6), "left_arm": (-60, 0, -4),
           "right_leg": (-72, 16, 20), "left_leg": (-72, -14, -6)}),
    (1.0, RIDE),
]

# lifted in an elephant's trunk: reaching for it, borne up with the legs dangling and the arms out for balance, set down on its back
ON_TRUNK = [
    (0.0, STAND),
    (0.2, {"body": (-4, 0, 0), "right_arm": (-100, 0, 20), "left_arm": (-100, 0, -20),
           "right_leg": (-10, 0, 3), "left_leg": (-10, 0, -3)}),
    (0.5, {"body": (-10, 0, 0), "right_arm": (-70, 0, 45), "left_arm": (-70, 0, -45),
           "right_leg": (-35, 0, 6), "left_leg": (-45, 0, -6)}),
    (0.8, {"body": (-4, 0, 0), "right_arm": (-60, 0, 30), "left_arm": (-60, 0, -30),
           "right_leg": (-60, 10, 12), "left_leg": (-62, -10, -8)}),
    (1.0, RIDE),
]

# a moa crouches: knees bent, a hop with the legs tucked, landing astride its low back
ON_CROUCH = [
    (0.0, STAND),
    (0.25, {"body": (18, 0, 0), "right_arm": (-70, 0, 12), "left_arm": (-70, 0, -12),
            "right_leg": (-45, 0, 3), "left_leg": (-45, 0, -3)}),
    (0.5, {"body": (8, 0, 0), "right_arm": (-90, 0, 22), "left_arm": (-90, 0, -22),
           "right_leg": (-70, 0, 22), "left_leg": (-70, 0, -22)}),
    (0.8, {"body": (6, 0, 0), "right_arm": (-55, 0, 8), "left_arm": (-55, 0, -8),
           "right_leg": (-80, 16, 10), "left_leg": (-80, -16, -10)}),
    (1.0, RIDE),
]

# up a lowered wing from the mount's left, like a ramp: arms out for balance, a step at a time, a leg swung over at the top
ON_WING_LEFT = [
    (0.0, STAND),
    (0.2, {"body": (8, 0, 0), "right_arm": (-25, 0, 50), "left_arm": (-25, 0, -50),
           "right_leg": (-5, 0, 2), "left_leg": (-38, 0, -4)}),
    (0.45, {"body": (14, 0, -3), "right_arm": (-15, 0, 65), "left_arm": (-15, 0, -65),
            "right_leg": (-40, 0, 4), "left_leg": (8, 0, -2)}),
    (0.7, {"body": (16, 0, -4), "right_arm": (-60, 0, 30), "left_arm": (-60, 0, -30),
           "right_leg": (-50, 0, 40), "left_leg": (-45, 0, -4)}),
    (0.88, {"body": (8, 0, -2), "right_arm": (-50, 0, 10), "left_arm": (-48, 0, -8),
            "right_leg": (-75, 16, 16), "left_leg": (-72, -14, -6)}),
    (1.0, RIDE),
]

# up a dragon's lowered neck from its head: hands forward on the scales, leaning in, shuffling up one knee at a time, then astride
ON_NECK = [
    (0.0, STAND),
    (0.2, {"body": (20, 0, 0), "right_arm": (-110, 0, 10), "left_arm": (-110, 0, -10),
           "right_leg": (-50, 0, 6), "left_leg": (-10, 0, -3)}),
    (0.45, {"body": (26, 0, 0), "right_arm": (-125, 0, 14), "left_arm": (-95, 0, -14),
            "right_leg": (-20, 0, 10), "left_leg": (-60, 0, -8)}),
    (0.7, {"body": (20, 0, 0), "right_arm": (-100, 0, 12), "left_arm": (-120, 0, -12),
           "right_leg": (-62, 4, 18), "left_leg": (-35, 0, -10)}),
    (0.88, {"body": (10, 0, 0), "right_arm": (-60, 0, 6), "left_arm": (-60, 0, -6),
            "right_leg": (-78, 16, 12), "left_leg": (-76, -14, -6)}),
    (1.0, RIDE),
]

# up a dragon's lowered wing from the left: a long scramble up the leathery ramp on hands and feet, arms out when it sways, then over onto the back
ON_DRAGON_WING_LEFT = [
    (0.0, STAND),
    (0.15, {"body": (16, 0, 0), "right_arm": (-60, 0, 30), "left_arm": (-60, 0, -30),
            "right_leg": (-3, 0, 2), "left_leg": (-45, 0, -4)}),
    (0.4, {"body": (24, 0, -4), "right_arm": (-100, 0, 20), "left_arm": (-70, 0, -35),
           "right_leg": (-45, 0, 3), "left_leg": (-12, 0, -3)}),
    (0.65, {"body": (26, 0, -4), "right_arm": (-85, 0, 30), "left_arm": (-105, 0, -20),
            "right_leg": (-20, 0, 6), "left_leg": (-55, 0, -6)}),
    (0.85, {"body": (14, 0, -2), "right_arm": (-60, 0, 14), "left_arm": (-60, 0, -10),
            "right_leg": (-60, 10, 42), "left_leg": (-52, 0, -4)}),
    (0.93, {"body": (8, 0, 0), "right_arm": (-45, 0, 8), "left_arm": (-45, 0, -6),
            "right_leg": (-78, 16, 14), "left_leg": (-76, -14, -6)}),
    (1.0, RIDE),
]

CLIPS = {
    "on_left": ON_LEFT,
    "on_right": [(t, mirror(p)) for t, p in ON_LEFT],
    "on_back": ON_BACK,
    "off_forward": OFF_FORWARD,
    "off_back": OFF_BACK,
    "on_kneel": ON_KNEEL,
    "on_pillion": ON_PILLION,
    "off_slip": OFF_SLIP,
    "heave": HEAVE,
    "on_big_kneel": ON_BIG_KNEEL,
    "on_trunk": ON_TRUNK,
    "on_crouch": ON_CROUCH,
    "on_wing_left": ON_WING_LEFT,
    "on_wing_right": [(t, mirror(p)) for t, p in ON_WING_LEFT],
    "on_neck": ON_NECK,
    "on_dragon_wing_left": ON_DRAGON_WING_LEFT,
    "on_dragon_wing_right": [(t, mirror(p)) for t, p in ON_DRAGON_WING_LEFT],
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
