#!/usr/bin/env python3
"""Writes the freerun animation clips as GeckoLib .animation.json files (Blockbench opens them; KeyframeClip plays them).

    python3 -I tools/gen_freerun_clips.py            # write the clips
    python3 -I tools/freerun_clip_preview.py OUT_DIR  # draw them, to check a pose before it goes in game

The script is the source (.claude/rules/blockbench-animation.md): change a key here and run it again. Poses are vanilla
ModelPart angles in degrees (x, y, z); the files take Blockbench's signs, x and y turned round (KeyframeClip.partX/partY
turn them back). Times are shares of the clip; each clip says how long it runs (seconds).

Bones: body, head, right_arm, left_arm, right_leg, left_leg, and four folds drawn with KneeBend, keyed on x only:
right_elbow, left_elbow (degrees, + brings the forearm forward) and right_knee, left_knee (degrees, + swings the shin
back). A clip may key only some bones: the pose class keeps its own motion for the rest.

Reference: Andy asked for Assassin's Creed Unity's freerun and real parkour footage as the inspiration (docs/
CombatAnimations-Plan.md, "Parkour animations"). Nothing is taken from the game's files or traced off video: the moves
are keyed by hand from how such moves are built (hands take the weight first, it drops through the arms, the legs follow
a beat behind).
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets",
                   "jaymospowers", "geckolib", "animations", "entity")

FOLDS = ("right_elbow", "left_elbow", "right_knee", "left_knee")


def key(t, **bones):
    """One key at share t: bone=(x, y, z) for a part, bone=degrees for a fold."""
    return t, bones


# ---------------------------------------------------------------------------
# Ledge mantle (LedgePose's climb up over the edge, 0 to 1). From the hang: a pull-up, chest to the edge, the elbows
# coming over it; then the press, straight arms on the top, as the right foot comes up onto the edge, the left knee
# after it; up to stand. The head looks up at the edge in the pull and down over the top in the press. The body's lean
# in over the edge stays LedgePose's own.
# ---------------------------------------------------------------------------
LEDGE_MANTLE = (0.9, [
    key(0.00, head=(-15, 0, 0), right_arm=(-163, 0, 11), left_arm=(-163, 0, -11), right_leg=(6, 0, 3), left_leg=(-4, 0, -3),
        right_elbow=15, left_elbow=15, right_knee=10, left_knee=14),
    # the pull: the upper arms drop forward and down, the forearms kept up at the hands on the edge
    key(0.15, head=(-20, 0, 0), right_arm=(-135, 0, 14), left_arm=(-135, 0, -14), right_leg=(-12, 0, 3), left_leg=(-6, 0, -3),
        right_elbow=60, left_elbow=60, right_knee=35, left_knee=28),
    key(0.30, head=(-8, 0, 0), right_arm=(-55, 0, 18), left_arm=(-55, 0, -18), right_leg=(-25, 0, 4), left_leg=(-8, 0, -3),
        right_elbow=115, left_elbow=115, right_knee=55, left_knee=35),
    # the elbows up over the edge and the hands under the chest: over into the press
    key(0.42, head=(10, 0, 0), right_arm=(-35, 0, 20), left_arm=(-35, 0, -20), right_leg=(-50, 0, 4), left_leg=(-4, 0, -3),
        right_elbow=80, left_elbow=80, right_knee=85, left_knee=32),
    key(0.52, head=(20, 0, 0), right_arm=(-20, 0, 18), left_arm=(-20, 0, -18), right_leg=(-80, 0, 4), left_leg=(4, 0, -3),
        right_elbow=30, left_elbow=30, right_knee=102, left_knee=30),
    key(0.65, head=(18, 0, 0), right_arm=(-15, 0, 15), left_arm=(-15, 0, -15), right_leg=(-95, 0, 4), left_leg=(0, 0, -3),
        right_elbow=8, left_elbow=8, right_knee=112, left_knee=40),
    key(0.80, head=(8, 0, 0), right_arm=(-12, 0, 10), left_arm=(-12, 0, -10), right_leg=(-60, 0, 4), left_leg=(-55, 0, -4),
        right_elbow=15, left_elbow=15, right_knee=78, left_knee=100),
    key(0.92, head=(2, 0, 0), right_arm=(-5, 0, 5), left_arm=(-5, 0, -5), right_leg=(-14, 0, 2), left_leg=(-20, 0, -2),
        right_elbow=12, left_elbow=12, right_knee=24, left_knee=34),
    key(1.00, head=(0, 0, 0), right_arm=(0, 0, 3), left_arm=(0, 0, -3), right_leg=(0, 0, 1), left_leg=(0, 0, -1),
        right_elbow=5, left_elbow=5, right_knee=0, left_knee=0),
])

# ---------------------------------------------------------------------------
# Ledge hang (a 2 second loop, laid over LedgePose's hang: its catch, sway and dangle stay). The arms take the weight in
# turn, one elbow giving a little as the other straightens, and the knees bend and hang loose by turns.
# ---------------------------------------------------------------------------
LEDGE_HANG = (2.0, [
    key(0.00, right_elbow=16, left_elbow=24, right_knee=10, left_knee=20),
    key(0.50, right_elbow=24, left_elbow=16, right_knee=20, left_knee=9),
    key(1.00, right_elbow=16, left_elbow=24, right_knee=10, left_knee=20),
])

# ---------------------------------------------------------------------------
# Shimmy (one hand-over-hand cycle along the edge, laid over LedgePose's shimmy, which lifts each hand off the edge and
# along it): the hand coming off bends at the elbow, the one holding takes the weight nearly straight; the knees swing
# with the body's rock from hand to hand.
# ---------------------------------------------------------------------------
SHIMMY = (1.0, [
    key(0.00, right_elbow=25, left_elbow=25, right_knee=12, left_knee=12),
    key(0.25, right_elbow=65, left_elbow=14, right_knee=24, left_knee=8),
    key(0.50, right_elbow=25, left_elbow=25, right_knee=12, left_knee=12),
    key(0.75, right_elbow=14, left_elbow=65, right_knee=8, left_knee=24),
    key(1.00, right_elbow=25, left_elbow=25, right_knee=12, left_knee=12),
])

CLIPS = {
    "player_freerun_ledge_mantle": LEDGE_MANTLE,
    "player_freerun_ledge_hang": LEDGE_HANG,
    "player_freerun_shimmy": SHIMMY,
}


def to_blockbench(bone, v):
    if bone in FOLDS:
        return [round(-v, 2), 0.0, 0.0]
    x, y, z = v
    return [round(-x, 2), round(-y, 2), round(z, 2)]


def write(name, clip):
    length, keys = clip
    bones = {}
    names = []
    for _, pose in keys:
        names += [b for b in pose if b not in names]
    for bone in names:
        frames = {}
        timed = [(t, pose[bone]) for t, pose in keys if bone in pose]
        for i, (t, v) in enumerate(timed):
            ends = i == 0 or i == len(timed) - 1
            frames[f"{t * length:.3f}".rstrip("0").rstrip(".")] = {"vector": to_blockbench(bone, v),
                                                                   "lerp_mode": "linear" if ends else "catmullrom"}
        bones[bone] = {"rotation": frames}
    data = {"format_version": "1.8.0",
            "animations": {name: {"loop": False, "animation_length": length, "bones": bones}}}
    path = os.path.join(OUT, f"{name}.animation.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")
    print(path)


if __name__ == "__main__":
    for name, clip in CLIPS.items():
        write(name, clip)
