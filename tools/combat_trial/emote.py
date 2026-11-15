"""Writes a pose sampled once a tick as an Emotecraft animation, the JSON Better Combat's animations use and Player
Animation Library reads (see tools/combat_animations.py for the format). Both routes of the longsword trial end here, so
the two sets of files differ only in the motion each tool made.

A sample is {part: {channel: value}}: radians for pitch/yaw/roll (a ModelPart's xRot/yRot/zRot), model pixels for the
pivot x/y/z, and for an arm or leg `bend`, the fold at the elbow or knee in radians (+ folds an elbow forward and a knee
back; the game draws it with KneeBend, see CombatTrialBends). A channel that never leaves rest is left out, so the
game's own motion (the legs' walk) shows through it.
"""
import json
import os

PARTS = ("head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg")
CHANNELS = ("pitch", "yaw", "roll", "x", "y", "z", "bend")
REST = {
    "head": {}, "torso": {},
    "rightArm": {"x": -5, "y": 2}, "leftArm": {"x": 5, "y": 2},
    "rightLeg": {"x": -1.9, "y": 12}, "leftLeg": {"x": 1.9, "y": 12},
}
# a sample this close to the straight line between its neighbours is dropped
SLACK = {"pitch": 0.004, "yaw": 0.004, "roll": 0.004, "bend": 0.004, "x": 0.02, "y": 0.02, "z": 0.02}


def _track(samples, part, ch):
    rest = REST[part].get(ch, 0.0)
    return [s.get(part, {}).get(ch, rest) for s in samples], rest


def _keep(values, slack):
    """Indices of the samples needed to draw the track as straight pieces within `slack`."""
    n = len(values)
    keep, a, j = [0], 0, 2
    while j < n:
        straight = all(abs(values[i] - (values[a] + (values[j] - values[a]) * (i - a) / (j - a))) <= slack
                       for i in range(a + 1, j))
        if straight:
            j += 1
        else:
            a = j - 1
            keep.append(a)
            j = a + 2
    if n > 1:
        keep.append(n - 1)
    return keep


def write(path, name, description, samples, begin, end, loop=False, return_tick=0):
    """samples[t] is the pose at tick t; the animation stops after the last one."""
    stop = len(samples) - 1
    moves = []
    for part in PARTS:
        for ch in CHANNELS:
            if ch == "bend" and part in ("head", "torso"):
                continue
            values, rest = _track(samples, part, ch)
            if all(abs(v - rest) < 1e-4 for v in values):
                continue
            for i in _keep(values, SLACK[ch]):
                moves.append({"tick": i, "easing": "LINEAR", "turn": 0, part: {ch: round(values[i], 4)}})
    moves.sort(key=lambda m: m["tick"])
    emote = {"isLoop": "true" if loop else "false", "returnTick": return_tick, "beginTick": begin, "endTick": end,
             "stopTick": stop, "degrees": False, "moves": moves}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump({"name": name, "author": "Jaymo's Powers", "description": description, "emote": emote}, f, indent=1)
        f.write("\n")
