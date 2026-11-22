"""Draws a freerun clip (tools/gen_freerun_clips.py) on the default player model, frame by frame, from the side and the
front, to check its poses before they go in game.

    python3 -I tools/freerun_clip_preview.py OUT_DIR [clip ...]

Reads the written .animation.json the way KeyframeClip does (linear and Catmull-Rom keys, Blockbench signs turned back)
and draws it with tools/pose_preview.py's renderer, folds bent as KneeBend bends them. Only the clip's own keys are
drawn: the motion a pose class lays under or over it (LedgePose's sway, lean and catch) isn't.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pose_preview import model, render, sheet, mat, T, R  # noqa: E402
import gen_freerun_clips as gen  # noqa: E402

PARTS = {"head": "head", "body": "body", "right_arm": "rightArm", "left_arm": "leftArm", "right_leg": "rightLeg",
         "left_leg": "leftLeg"}
FOLDS = {"right_elbow": ("rightArm", -1.0), "left_elbow": ("leftArm", -1.0), "right_knee": ("rightLeg", 1.0),
         "left_knee": ("leftLeg", 1.0)}


def load(name):
    with open(os.path.join(gen.OUT, f"{name}.animation.json"), encoding="utf-8") as f:
        anim = json.load(f)["animations"][name]
    bones = {}
    for bone, ch in anim["bones"].items():
        keys = sorted((float(t), v["vector"], v.get("lerp_mode") == "catmullrom") for t, v in ch["rotation"].items())
        bones[bone] = keys
    return anim["animation_length"], bones


def degrees(keys, axis, time):
    if time <= keys[0][0]:
        return keys[0][1][axis]
    for i in range(1, len(keys)):
        tb, vb, sb = keys[i]
        if time > tb:
            continue
        ta, va, sa = keys[i - 1]
        t = 1.0 if tb - ta <= 1e-5 else (time - ta) / (tb - ta)
        if not sa and not sb:
            return va[axis] + (vb[axis] - va[axis]) * t
        p0 = keys[max(0, i - 2)][1][axis]
        p3 = keys[min(len(keys) - 1, i + 1)][1][axis]
        p1, p2 = va[axis], vb[axis]
        t2, t3 = t * t, t * t * t
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
    return keys[-1][1][axis]


def pose(bones, length, share):
    p = model()
    time = share * length
    for bone, keys in bones.items():
        if bone in PARTS:
            # Blockbench's x and y turned round, as KeyframeClip.partX/partY
            p[PARTS[bone]].set(math.radians(-degrees(keys, 0, time)), math.radians(-degrees(keys, 1, time)),
                               math.radians(degrees(keys, 2, time)))
        elif bone in FOLDS:
            part, sign = FOLDS[bone]
            p[part].bend = sign * math.radians(-degrees(keys, 0, time))
    return p


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    names = sys.argv[2:] or list(gen.CLIPS)
    os.makedirs(out, exist_ok=True)
    for name in names:
        length, bones = load(name)
        frames = []
        for i in range(9):
            share = i / 8
            for yaw in (90, 0):
                frames.append(render(pose(bones, length, share), mat(), [], yaw, size=220, scale=95,
                                     label=f"{name[16:]} {share:.2f} {'side' if yaw else 'front'}"))
        path = os.path.join(out, f"{name}.png")
        sheet(frames, 6).save(path)
        print(path)


if __name__ == "__main__":
    main()
