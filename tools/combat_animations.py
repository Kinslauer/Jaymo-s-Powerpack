"""Writes Jaymo's Armory's own Better Combat animations and attack combos.

    python -I tools/combat_animations.py

Animations go to assets/jaymospowers/player_animations (the Emotecraft JSON that Better Combat's animations use, read
by Player Animation Library): ticks, radians, and each part's pose as pitch/yaw/roll plus its pivot x/y/z in model
pixels. Arm pitch below 0 lifts the arm forward; roll above 0 swings the hand toward the player's right; the head
and arms follow the torso. On `rightItem` the roll spins the held item round the forearm, which is how the
quarterstaff twirls (its model is held by the middle, see armory_assets.py), the pitch flips it end over end, and the
yaw only turns it about its own length.

Combos go to data/jaymospowers/weapon_attributes/combo_*.json; each weapon's own file names one as its parent (the
Armory's from armory_assets.py, the rest from OTHER_WEAPONS here). Better
Combat lands the hit at `upswing` of the swing, and plays an animation so that its endTick is the end of the swing.
A spin can't unwind on the way back to rest, so every spin ends on a whole turn and snaps back to 0 in the same
instant (`snap`), which looks the same.
"""
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ANIMS = os.path.join(RES, "assets", "jaymospowers", "player_animations")
ATTRS = os.path.join(RES, "data", "jaymospowers", "weapon_attributes")
NS = "jaymospowers"
TURN = 2 * math.pi

CHANNELS = ("pitch", "yaw", "roll", "x", "y", "z")
REST = {
    "head": {}, "torso": {}, "rightItem": {},
    "rightArm": {"x": -5, "y": 2}, "leftArm": {"x": 5, "y": 2},
    "rightLeg": {"x": -1.9, "y": 12}, "leftLeg": {"x": 1.9, "y": 12},
}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def animation(name, description, begin, end, stop, frames):
    """frames: (tick, easing into this frame, {part: {channel: value}}). A part keeps its last value for channels a
    frame leaves out. The format eases the stretch after a keyframe by that keyframe's easing, hence the shift."""
    last = {}
    moves = []
    leaving = [f[1] for f in frames[1:]] + ["EASEINOUTQUAD"]
    for (tick, _, parts), easing in zip(frames, leaving):
        for part, pose in parts.items():
            now = {**REST[part], **last.get(part, {}), **pose}
            last[part] = now
            for ch in CHANNELS:
                moves.append({"tick": tick, "easing": easing, "turn": 0, part: {ch: round(now.get(ch, 0.0), 4)}})
    write(os.path.join(ANIMS, name + ".json"), {
        "name": name, "author": "Jaymo's Powers", "description": description,
        "emote": {"isLoop": "false", "returnTick": 2, "beginTick": begin, "endTick": end, "stopTick": stop,
                  "degrees": False, "moves": moves}})


def snap(tick, parts):
    """The instant after `tick`: a spin that just finished whole turns goes back to 0 (or the given values)."""
    return (round(tick + 0.05, 2), "LINEAR", parts)


def hold(*parts):
    """Keep these parts where they are at this frame (else they drift toward the next keyframe early)."""
    return {part: {} for part in parts}


def spin(start, end, total, steps, part, channel, extra=None):
    """Frames turning `part`'s `channel` from 0 to `total` between ticks, starting from rest and slowing to a stop at
    the end: the keys are spaced along an ease-in-out curve, the first stretch eases in, the last eases out, and the
    ones between are straight (an eased curve on each would stop the spin at every key). `extra(f)` adds more parts
    turning with it at fraction f of the way (a whirl's torso and feet)."""
    frames = []
    for i in range(1, steps + 1):
        u = i / steps
        f = u * u * (3 - 2 * u)
        easing = "EASEINQUAD" if i == 1 else "EASEOUTQUAD" if i == steps else "LINEAR"
        parts = {part: {channel: total * f}}
        if extra:
            parts.update(extra(f))
        frames.append((round(start + (end - start) * u, 2), easing, parts))
    return frames


# --- the quarterstaff ---------------------------------------------------------------------------------------------
# (0.15.37) Every animation here was re-keyed: a wind-up before the hit and a follow-through after it, the head looking
# toward the blade, the torso and the legs (a step or a weight shift) in on it, and eased curves. The hit's own
# keyframe, beginTick, endTick and stopTick are as they were (Better Combat's damage window hangs on them).

STAFF_GUARD = {
    "rightArm": {"pitch": -0.85, "yaw": -0.25, "roll": 0.15, "z": -0.8},
    "leftArm": {"pitch": -1.05, "yaw": 0.35, "roll": 0.9, "x": 5.2, "z": -1.2},
}
STANCE_REST = {"head": {"pitch": 0, "yaw": 0}, "rightLeg": {"pitch": 0, "yaw": 0, "z": 0},
               "leftLeg": {"pitch": 0, "yaw": 0, "z": 0}}


def staff_twirl_front():
    """Draw the staff back, step in and let go with the left hand, and spin it like a propeller in front of you, twice
    round."""
    coil = {"rightArm": {"pitch": -0.95, "yaw": 0.3, "roll": 0.1, "x": -5, "y": 2, "z": -0.4},
            "leftArm": {"pitch": -0.8, "yaw": 0.3, "roll": 0.5, "x": 5, "y": 2, "z": -0.6},
            "torso": {"yaw": -0.3, "pitch": 0.05}, "rightItem": {"roll": 0},
            "head": {"pitch": 0.15, "yaw": -0.1},
            "rightLeg": {"pitch": 0.2}, "leftLeg": {"pitch": -0.05}}
    reach = {"rightArm": {"pitch": -1.5, "yaw": -0.15, "roll": 0.0, "x": -5, "y": 2, "z": -1.2},
             "leftArm": {"pitch": -0.35, "yaw": 0.0, "roll": -0.45, "x": 5, "y": 2, "z": 0},
             "torso": {"yaw": 0.2, "pitch": -0.08}, "rightItem": {"roll": 0},
             "head": {"pitch": 0.2, "yaw": 0.1},
             "rightLeg": {"pitch": 0.25}, "leftLeg": {"pitch": -0.3}}
    frames = [(2, "EASEOUTQUAD", coil), (4, "EASEINOUTQUAD", reach)]
    # 2 turns, quick in the middle, ticks 4..16
    twirl = spin(4, 16, 2 * TURN, 6, "rightItem", "roll")
    # tick 10: push in, the weight onto the front foot
    twirl[2][2].update({"rightArm": {"z": -2.2, "pitch": -1.55}, "torso": {"yaw": -0.1},
                        "leftLeg": {"pitch": -0.4}, "rightLeg": {"pitch": 0.3}})
    twirl[-1][2].update(hold("rightArm", "leftArm", "torso", "head", "rightLeg", "leftLeg"))
    frames += twirl
    frames.append(snap(16, {"rightItem": {"roll": 0}}))
    # carried on round into the guard
    frames.append((18, "EASEOUTQUAD", {"torso": {"yaw": -0.2, "pitch": 0.02}, "head": {"yaw": -0.05},
                                       "rightArm": {"pitch": -1.0, "z": -1.0}}))
    frames.append((20, "EASEINOUTQUAD", {**STAFF_GUARD, "torso": {"yaw": 0, "pitch": 0}, **STANCE_REST}))
    animation("staff_twirl_front", "Quarterstaff: twirl in front like a propeller", 4, 20, 28, frames)


def staff_whirlwind():
    """Wind up the other way, then the staff held out to the right, still twirling, as you turn a full circle."""
    coil = {"rightArm": {"pitch": -0.55, "yaw": 0.2, "roll": 0.85, "x": -5, "y": 2, "z": 0},
            "leftArm": {"pitch": -0.5, "yaw": 0, "roll": -0.6, "x": 5, "y": 2, "z": 0},
            "torso": {"yaw": 1.1, "pitch": 0.05}, "rightItem": {"roll": 0}, "head": {"yaw": -0.2, "pitch": 0.1},
            "rightLeg": {"pitch": -0.35, "yaw": 0.4}, "leftLeg": {"pitch": 0.35, "yaw": 0.4}}
    out = {"rightArm": {"pitch": -0.15, "yaw": 0, "roll": 1.45, "x": -5, "y": 2, "z": 0},
           "leftArm": {"pitch": -0.1, "yaw": 0, "roll": -1.25, "x": 5, "y": 2, "z": 0}}
    frames = [
        (3, "EASEOUTQUAD", coil),
        (5, "EASEINOUTQUAD", {**out, "torso": {"yaw": 0.7, "pitch": -0.05}, "rightItem": {"roll": 0},
                              "head": {"yaw": -0.45, "pitch": 0.05},
                              "rightLeg": {"pitch": -0.25, "yaw": 0.3}, "leftLeg": {"pitch": 0.25, "yaw": 0.3}}),
    ]
    # the body: 0.7 -> 0.7 - 1 turn (clockwise from above) between ticks 5 and 15, the staff twirls once with it
    frames += spin(5, 15, TURN, 5, "rightItem", "roll", lambda f: {
        "torso": {"yaw": 0.7 - TURN * f}, "rightLeg": {"yaw": 0.3 - 0.6 * f}, "leftLeg": {"yaw": 0.3 - 0.6 * f}})
    frames[-1][2].update(hold("rightArm", "leftArm", "head"))
    frames.append(snap(15, {"torso": {"yaw": 0.7}, "rightItem": {"roll": 0}}))
    # the turn carries on past, then settles
    frames.append((17, "EASEOUTQUAD", {"torso": {"yaw": 0.3, "pitch": 0.02}, "head": {"yaw": -0.15},
                                       "rightArm": {"pitch": -0.5, "roll": 1.0}, "leftArm": {"roll": -0.8},
                                       "rightLeg": {"pitch": -0.1, "yaw": -0.1}, "leftLeg": {"pitch": 0.1, "yaw": -0.1}}))
    frames.append((20, "EASEINOUTQUAD", {**STAFF_GUARD, "torso": {"yaw": 0, "pitch": 0}, **STANCE_REST}))
    animation("staff_whirlwind", "Quarterstaff: spin round a full circle with the staff out and twirling", 6, 20, 28,
              frames)


def staff_twirl_overhead():
    """Dip, spin the staff flat over your head like a helicopter, twice round, arch back, and bring it down hard."""
    dip = {"rightArm": {"pitch": -0.7, "yaw": -0.2, "roll": 0.15, "x": -5, "y": 2, "z": -0.8},
           "leftArm": {"pitch": -0.9, "yaw": 0.3, "roll": 0.8, "x": 5.2, "y": 2, "z": -1.2},
           "torso": {"pitch": -0.15, "yaw": 0}, "rightItem": {"roll": 0}, "head": {"pitch": 0.1},
           "rightLeg": {"pitch": -0.15}, "leftLeg": {"pitch": -0.15}}
    up = {"rightArm": {"pitch": -2.65, "yaw": 0, "roll": 0.15, "x": -5, "y": 2, "z": -1},
          "leftArm": {"pitch": -1.9, "yaw": 0, "roll": -0.55, "x": 5, "y": 2, "z": -0.5},
          "torso": {"pitch": 0.08, "yaw": 0}, "rightItem": {"roll": 0}, "head": {"pitch": -0.45},
          "rightLeg": {"pitch": 0}, "leftLeg": {"pitch": 0}}
    frames = [(1.5, "EASEOUTQUAD", dip), (3, "EASEINOUTQUAD", up)]
    frames += spin(3, 11, 2 * TURN, 8, "rightItem", "roll")
    frames[-1][2].update(hold("rightArm", "leftArm", "torso", "head", "rightLeg", "leftLeg"))
    frames.append(snap(11, {"rightItem": {"roll": 0}}))
    # the wind-up: arched back, staff high behind the head, weight on the back foot
    frames.append((12, "EASEOUTQUAD", {"rightArm": {"pitch": -2.95, "z": -0.6}, "leftArm": {"pitch": -2.3},
                                       "torso": {"pitch": 0.28}, "head": {"pitch": -0.3},
                                       "rightLeg": {"pitch": 0.15}, "leftLeg": {"pitch": -0.2}}))
    strike = {"rightArm": {"pitch": -1.0, "yaw": -0.2, "roll": 0.1, "z": -2},
              "leftArm": {"pitch": -0.95, "yaw": 0.35, "roll": 0.9, "x": 5.2, "z": -1.5},
              "torso": {"pitch": -0.45, "y": -0.3}, "head": {"pitch": 0.35},
              "rightLeg": {"pitch": -0.35}, "leftLeg": {"pitch": 0.3, "z": 1.5}}
    frames.append((13, "EASEINQUAD", strike))
    # follow-through: the staff carries on down a little, then comes back up
    frames.append((15, "EASEOUTQUAD", {"rightArm": {"pitch": -0.75, "z": -2.2}, "leftArm": {"pitch": -0.7},
                                       "torso": {"pitch": -0.55, "y": -0.35}, "head": {"pitch": 0.4}}))
    frames.append((20, "EASEINOUTQUAD", {**STAFF_GUARD, "torso": {"pitch": 0, "y": 0}, **STANCE_REST}))
    animation("staff_twirl_overhead", "Quarterstaff: helicopter twirl over the head into an overhead strike", 3, 20, 28,
              frames)


# --- others -------------------------------------------------------------------------------------------------------

def polearm_whirl():
    """Coil, then a long blade held low and out to the right, swept round a full circle (glaive, war scythe)."""
    coil = {"rightArm": {"pitch": -0.5, "yaw": 0.75, "roll": 0.3, "x": -5, "y": 2, "z": 0},
            "leftArm": {"pitch": -0.8, "yaw": 0.8, "roll": 0.8, "x": 5, "y": 2, "z": -1.0},
            "torso": {"yaw": 1.3, "pitch": -0.05}, "head": {"yaw": -0.35, "pitch": 0.2},
            "rightLeg": {"pitch": -0.3, "yaw": 0.45}, "leftLeg": {"pitch": 0.35, "yaw": 0.45}}
    low = {"rightArm": {"pitch": -0.75, "yaw": 0.45, "roll": 0.35, "x": -5, "y": 2, "z": -0.5},
           "leftArm": {"pitch": -0.95, "yaw": 0.55, "roll": 0.95, "x": 5, "y": 2, "z": -1.5},
           "torso": {"yaw": 0.9, "pitch": -0.15}, "head": {"yaw": -0.45, "pitch": 0.25},
           "rightLeg": {"pitch": -0.2, "yaw": 0.35}, "leftLeg": {"pitch": 0.3, "yaw": 0.35}}
    frames = [(3, "EASEOUTQUAD", coil), (6, "EASEINOUTQUAD", low)]
    # the blade swept round: torso and feet, 6..14 (the torso's own yaw is the turn; nothing else spins)
    sweep = spin(6, 14, -TURN, 4, "torso", "yaw", lambda f: {
        "rightLeg": {"yaw": 0.35 - 0.7 * f}, "leftLeg": {"yaw": 0.35 - 0.7 * f}})
    for frame in sweep:
        frame[2]["torso"]["yaw"] += 0.9
    sweep[-1][2].update(hold("rightArm", "leftArm", "head"))
    frames += sweep
    frames.append(snap(14, {"torso": {"yaw": 0.9}}))
    frames.append((17, "EASEOUTQUAD", {"torso": {"yaw": 0.4, "pitch": -0.08}, "head": {"yaw": -0.2, "pitch": 0.1},
                                       "rightLeg": {"pitch": -0.1, "yaw": -0.15}, "leftLeg": {"pitch": 0.15, "yaw": -0.15}}))
    frames.append((20, "EASEINOUTQUAD", {"torso": {"yaw": 0, "pitch": 0}, **STANCE_REST}))
    animation("polearm_whirl", "Polearm: blade held low, swept round a full circle", 6, 20, 28, frames)


def dagger_flip():
    """Flip the knife end over end in your hand, catch it, draw back, and lunge into the stab."""
    ready = {"rightArm": {"pitch": -0.95, "yaw": 0.35, "roll": 0.2, "x": -5, "y": 2, "z": -1},
             "leftArm": {"pitch": -0.3, "roll": -0.3}, "rightItem": {"pitch": 0, "roll": 0},
             "torso": {"yaw": -0.35}, "head": {"pitch": 0.3, "yaw": -0.2},
             "rightLeg": {"pitch": 0.1}, "leftLeg": {"pitch": -0.1}}
    frames = [(3, "EASEOUTQUAD", ready)]
    frames += spin(3, 8, -TURN, 4, "rightItem", "pitch")  # the same way it then turns into the stab
    frames[-1][2].update(hold("rightArm", "leftArm", "torso", "head", "rightLeg", "leftLeg"))
    frames.append(snap(8, {"rightItem": {"pitch": 0}}))
    # caught and drawn back: the shoulder turned away, the weight on the back foot
    frames.append((9.5, "EASEOUTQUAD", {"rightArm": {"pitch": -0.7, "yaw": 0.55, "z": 0.2},
                                        "torso": {"yaw": -0.55}, "head": {"pitch": 0.05, "yaw": 0.1},
                                        "rightLeg": {"pitch": 0.25}, "leftLeg": {"pitch": -0.2}}))
    # into Better Combat's own one-handed stab, lunging off the front foot
    stab = {"rightArm": {"pitch": -1.68, "yaw": 0.85, "roll": 0.18, "x": -5.57, "y": 1.58, "z": -2.68},
            "rightItem": {"pitch": -1.38, "roll": 1.56, "x": 0.98, "y": -2.08, "z": -1.78},
            "leftArm": {"pitch": 0.3, "roll": -0.98, "x": 5.3, "y": 3.26, "z": 0.42},
            "torso": {"yaw": 0.85, "pitch": -0.39, "roll": -0.42}, "head": {"pitch": 0.15, "yaw": -0.35},
            "rightLeg": {"pitch": 0.4}, "leftLeg": {"pitch": -0.5, "z": -1}}
    frames.append((11, "EASEINQUAD", stab))
    # driven on through, then drawn back a little
    frames.append((14, "EASEOUTQUAD", {"rightArm": {"pitch": -1.6, "z": -3.1}, "torso": {"yaw": 0.95, "pitch": -0.42},
                                       "head": {"yaw": -0.4}, "leftLeg": {"pitch": -0.55}}))
    frames.append((20, "EASEINOUTQUAD", {"torso": {"yaw": 0.6, "pitch": -0.3, "roll": -0.3}, **STANCE_REST}))
    animation("dagger_flip", "Dagger: flip the knife in the hand, then stab", 3, 20, 28, frames)


def sword_twirl_slash():
    """Spin the sword like a wheel at your side, wind up, then a stepping cut from left to right (Better Combat's own
    cut), carried through."""
    side = {"rightArm": {"pitch": -0.55, "yaw": 0, "roll": 0.35, "x": -5, "y": 2, "z": -0.5},
            "leftArm": {"pitch": -0.2, "roll": -0.25}, "rightItem": {"roll": 0}, "torso": {"yaw": -0.2},
            "head": {"pitch": 0.25, "yaw": -0.3},
            "rightLeg": {"pitch": 0.1}, "leftLeg": {"pitch": -0.1}}
    frames = [(1, "EASEOUTQUAD", side)]
    frames += spin(1, 7, TURN, 4, "rightItem", "roll")
    frames[-1][2].update(hold("rightArm", "leftArm", "torso", "head", "rightLeg", "leftLeg"))
    frames.append(snap(7, {"rightItem": {"roll": 0}}))
    wound = {"rightArm": {"pitch": -1.42, "yaw": -0.91, "roll": 0.21, "x": -4.9, "y": 2.17, "z": -2.4},
             "rightItem": {"pitch": -0.71, "roll": 1.43, "x": 0.51, "y": -2.19, "z": -0.09},
             "leftArm": {"pitch": 0.6, "yaw": -0.54, "roll": -0.28, "x": 5, "y": 2, "z": 0},
             "torso": {"yaw": 0.99, "pitch": 0.1, "roll": 0.06}, "head": {"pitch": 0.1, "yaw": 0.35},
             "rightLeg": {"pitch": 0.25}, "leftLeg": {"pitch": -0.3}}
    frames.append((9, "EASEINOUTQUAD", wound))
    frames.append((11, "EASEINQUAD", {
        "rightArm": {"pitch": -1.77, "yaw": -0.05, "roll": 0.06, "x": -3.8, "y": 1.91, "z": -3.69},
        "rightItem": {"pitch": -1.22, "roll": 1.58, "x": 1.05, "y": -1.99, "z": -0.04},
        "leftArm": {"pitch": 0.48, "yaw": -0.46, "roll": -0.4}, "torso": {"yaw": 0.03, "pitch": -0.2, "roll": 0},
        "head": {"pitch": 0.15, "yaw": 0}, "rightLeg": {"pitch": 0.35}, "leftLeg": {"pitch": -0.45, "z": -1}}))
    frames.append((15, "EASEOUTQUAD", {
        "rightArm": {"pitch": -2.38, "yaw": 1.2, "roll": -0.8, "x": -5, "y": 1.9, "z": -1.95},
        "torso": {"yaw": -0.49, "pitch": -0.19}, "head": {"yaw": -0.3}}))
    frames.append((20, "EASEINOUTQUAD", {"rightArm": {"pitch": -1.7, "yaw": 0.7, "roll": -0.4},
                                         "torso": {"yaw": -0.3, "pitch": -0.1}, **STANCE_REST}))
    animation("sword_twirl_slash", "Sword: twirl it at your side, then cut across", 9, 20, 28, frames)


def sword_whirl():
    """Wind up the other way, then sword arm straight out, blade along it, and a full turn on the spot, carried on."""
    coil = {"rightArm": {"pitch": -0.55, "yaw": 0.3, "roll": 0.85, "x": -5, "y": 2, "z": 0},
            "leftArm": {"pitch": -0.5, "yaw": 0, "roll": -0.5, "x": 5, "y": 2, "z": 0},
            "rightItem": {"pitch": -0.8},
            "torso": {"yaw": 1.1, "pitch": 0.0}, "head": {"yaw": -0.2, "pitch": 0.1},
            "rightLeg": {"pitch": -0.3, "yaw": 0.4}, "leftLeg": {"pitch": 0.3, "yaw": 0.4}}
    out = {"rightArm": {"pitch": -0.1, "yaw": 0, "roll": 1.45, "x": -5, "y": 2, "z": 0},
           "leftArm": {"pitch": -0.2, "yaw": 0, "roll": -0.9, "x": 5, "y": 2, "z": 0},
           "rightItem": {"pitch": -1.4},
           "torso": {"yaw": 0.7, "pitch": -0.05}, "head": {"yaw": -0.45, "pitch": 0.05},
           "rightLeg": {"pitch": -0.2, "yaw": 0.3}, "leftLeg": {"pitch": 0.25, "yaw": 0.3}}
    frames = [(3, "EASEOUTQUAD", coil), (5, "EASEINOUTQUAD", out)]
    turn = spin(5, 15, -TURN, 5, "torso", "yaw", lambda f: {
        "rightLeg": {"yaw": 0.3 - 0.6 * f}, "leftLeg": {"yaw": 0.3 - 0.6 * f}})
    for frame in turn:
        frame[2]["torso"]["yaw"] += 0.7
    turn[-1][2].update(hold("rightArm", "leftArm", "rightItem", "head"))
    frames += turn
    frames.append(snap(15, {"torso": {"yaw": 0.7}}))
    frames.append((17, "EASEOUTQUAD", {"torso": {"yaw": 0.3}, "head": {"yaw": -0.2},
                                       "rightArm": {"pitch": -0.4, "roll": 0.9},
                                       "rightLeg": {"pitch": -0.1, "yaw": -0.1}, "leftLeg": {"pitch": 0.1, "yaw": -0.1}}))
    frames.append((20, "EASEINOUTQUAD", {"torso": {"yaw": 0, "pitch": 0}, "rightItem": {"pitch": 0},
                                         "rightArm": {"pitch": -0.6, "roll": 0.3}, "leftArm": {"pitch": 0, "roll": -0.2},
                                         **STANCE_REST}))
    animation("sword_whirl", "Sword: spin round a full circle with the blade out", 6, 20, 28, frames)


def axe_flip_chop():
    """Raise the axe, flip it end over end in the hand, rear back, and chop straight down with a step (Better Combat's
    own slam)."""
    up = {"rightArm": {"pitch": -2.9, "yaw": 0.1, "roll": 0.2, "x": -5, "y": 1.5, "z": 0.5},
          "leftArm": {"pitch": -0.6, "roll": -0.3}, "rightItem": {"pitch": 0},
          "torso": {"pitch": 0.3, "yaw": 0}, "head": {"pitch": -0.45, "yaw": -0.1},
          "rightLeg": {"pitch": 0.1}, "leftLeg": {"pitch": -0.05}}
    frames = [(2, "EASEOUTQUAD", up)]
    frames += spin(2, 7, -TURN, 4, "rightItem", "pitch")
    frames[-1][2].update(hold("rightArm", "leftArm", "torso", "head", "rightLeg", "leftLeg"))
    frames.append(snap(7, {"rightItem": {"pitch": 0}}))
    frames.append((9, "EASEINOUTQUAD", {
        "rightArm": {"pitch": -3.81, "yaw": 0.41, "roll": 0.25, "x": -5, "y": 1.06, "z": 1.67},
        "rightItem": {"pitch": 0.05, "roll": -0.14, "yaw": -0.41, "x": -0.55, "y": 0.09, "z": -0.27},
        "leftArm": {"pitch": -1.31, "yaw": -0.06, "roll": -0.22, "x": 5, "y": 2, "z": 0},
        "torso": {"pitch": 0.51, "y": 0, "z": 0}, "head": {"pitch": -0.3, "yaw": 0},
        "rightLeg": {"pitch": 0.2}, "leftLeg": {"pitch": -0.35}}))
    frames.append((11, "EASEINQUAD", {
        "rightArm": {"pitch": -1.53, "yaw": -0.47, "roll": 0.21, "x": -4.96, "y": 2.25, "z": -2.08},
        "rightItem": {"pitch": -0.29, "roll": -0.24, "yaw": -0.4, "x": -0.23, "y": 0.2, "z": -1.92},
        "leftArm": {"pitch": 0.26, "yaw": -1.05, "roll": -1.3, "x": 5.03},
        "torso": {"pitch": -0.95, "y": -0.15, "z": -0.35}, "head": {"pitch": 0.45},
        "rightLeg": {"pitch": 0.45}, "leftLeg": {"pitch": -0.5, "z": -1.5}}))
    # carried down through the chop, then the weight comes back
    frames.append((14, "EASEOUTQUAD", {"rightArm": {"pitch": -1.25, "z": -1.9}, "torso": {"pitch": -1.05, "y": -0.13, "z": -0.33},
                                       "head": {"pitch": 0.5}}))
    frames.append((20, "EASEINOUTQUAD", {"torso": {"pitch": -1.0, "y": -0.11, "z": -0.3}, "head": {"pitch": 0.3},
                                         "rightLeg": {"pitch": 0.3}, "leftLeg": {"pitch": -0.35, "z": -1}}))
    animation("axe_flip_chop", "Axe: flip it in the raised hand, then chop down", 9, 20, 28, frames)


# --- combos -------------------------------------------------------------------------------------------------------

def attack(hitbox, damage, animation, sound, angle=None, upswing=0.5, **extra):
    a = {"hitbox": hitbox, "damage_multiplier": damage}
    if angle is not None:
        a["angle"] = angle
    a.update(upswing=upswing, animation=animation if ":" in animation else "bettercombat:" + animation,
             swing_sound={"id": "bettercombat:" + sound})
    a.update(extra)
    return a


H, V, F = "HORIZONTAL_PLANE", "VERTICAL_PLANE", "FORWARD_BOX"
DUAL_STAB = attack(F, 1.4, "dual_handed_stab", "dagger_slash", 150,
                   conditions=["DUAL_WIELDING_SAME_CATEGORY", "MAIN_HAND_ONLY"])

COMBOS = {
    # twirl, sweep, whirlwind, jab, helicopter into a strike
    "quarterstaff": {"range_bonus": 0.5, "two_handed": True, "category": "battlestaff", "attacks": [
        attack(V, 0.8, NS + ":staff_twirl_front", "staff_spin", 100, 0.5),
        attack(H, 1.0, "two_handed_slash_horizontal_left", "staff_slash", 180),
        attack(H, 1.2, NS + ":staff_whirlwind", "staff_spin", 360, 0.5),
        attack(F, 0.9, "two_handed_stab_right", "staff_stab"),
        attack(V, 1.5, NS + ":staff_twirl_overhead", "staff_slam", 150, 0.6),
    ]},
    # rising cut, cross cut, half-sword thrust, falling cut
    "longsword": {"range_bonus": 0.25, "two_handed": True, "pose": "bettercombat:pose_two_handed_sword",
                  "category": "claymore", "attacks": [
        attack(V, 0.9, "two_handed_slash_vertical_right", "claymore_swing", 150),
        attack(H, 1.0, "two_handed_slash_horizontal_left", "claymore_swing", 150),
        attack(F, 1.1, "two_handed_stab_right", "claymore_stab", 0),
        attack(V, 1.2, "two_handed_slash_vertical_left", "claymore_slam", 150),
    ]},
    # wide sweeps and a crushing overhead
    "claymore": {"range_bonus": 0.25, "two_handed": True, "pose": "bettercombat:pose_two_handed_sword",
                 "category": "claymore", "attacks": [
        attack(H, 0.8, "two_handed_slash_horizontal_right", "claymore_swing", 180),
        attack(H, 1.0, "two_handed_slash_horizontal_left", "claymore_swing", 180),
        attack(V, 1.4, "two_handed_slam_heavy", "claymore_slam", 150),
    ]},
    "broadsword": {"range_bonus": 0, "category": "sword", "attacks": [
        attack(H, 1.0, "one_handed_slash_horizontal_right", "sword_slash", 120),
        attack(V, 1.1, "one_handed_uppercut_right", "sword_slash", 120),
        attack(H, 1.0, "one_handed_slash_horizontal_left", "sword_slash", 120),
        attack(V, 1.3, "one_handed_slam", "sword_slash", 90),
    ]},
    # fencing: cut, cut, lunge
    "saber": {"range_bonus": 0, "category": "cutlass", "attacks": [
        attack(H, 0.9, "one_handed_slash_horizontal_right", "rapier_slash", 120),
        attack(H, 0.9, "one_handed_slash_horizontal_left", "rapier_slash", 120),
        attack(F, 1.2, "one_handed_stab", "rapier_stab", 0),
    ]},
    # hacking: sideways cut, downward chop
    "machete": {"range_bonus": -0.25, "category": "cutlass", "attacks": [
        attack(H, 0.9, "one_handed_slash_horizontal_right", "sword_slash", 120),
        attack(V, 1.1, "one_handed_slam", "axe_slash", 90, 0.4),
        attack(H, 0.9, "one_handed_slash_horizontal_left", "sword_slash", 120),
        attack(V, 1.1, "one_handed_slam", "axe_slash", 90, 0.4),
    ]},
    # chop, backhand, chop
    "cleaver": {"range_bonus": -0.25, "category": "axe", "attacks": [
        attack(V, 1.2, "one_handed_slam", "axe_slash", 90, 0.4),
        attack(H, 0.9, "one_handed_slash_horizontal_left", "axe_slash", 120),
        attack(V, 1.3, "one_handed_slam", "mace_slam", 90, 0.4),
    ]},
    "hatchet": {"range_bonus": -0.25, "category": "axe", "attacks": [
        attack(H, 1.0, "one_handed_slash_horizontal_right", "axe_slash", 90, 0.3),
        attack(H, 1.0, "one_handed_slash_horizontal_left", "axe_slash", 90, 0.3),
        attack(V, 1.2, "one_handed_slam", "axe_slash", 90, 0.4),
    ]},
    "war_axe": {"range_bonus": 0, "two_handed": True, "pose": "bettercombat:pose_two_handed_heavy",
                "category": "heavy_axe", "attacks": [
        attack(H, 1.0, "two_handed_slash_horizontal_right", "double_axe_swing", 180),
        attack(V, 1.4, "two_handed_slam_heavy", "hammer_slam", 150),
        attack(H, 1.1, "two_handed_spin", "double_axe_swing", 360),
    ]},
    "glaive": {"range_bonus": 0.5, "two_handed": True, "pose": "bettercombat:pose_two_handed_polearm",
               "category": "glaive", "attacks": [
        attack(V, 0.8, "two_handed_slash_vertical_right", "glaive_slash_quick", 150),
        attack(V, 0.8, "two_handed_slash_vertical_left", "glaive_slash_quick", 150),
        attack(H, 1.3, NS + ":polearm_whirl", "glaive_slash_slow", 360),
        attack(H, 1.2, "two_handed_slash_horizontal_right", "glaive_slash_slow", 180),
    ]},
    "war_scythe": {"range_bonus": 0.5, "two_handed": True, "pose": "bettercombat:pose_two_handed_scythe",
                   "category": "scythe", "attacks": [
        attack(H, 1.0, "two_handed_slash_horizontal_right", "scythe_slash", 150),
        attack(H, 1.0, "two_handed_slash_horizontal_left", "scythe_slash", 150),
        attack(H, 1.3, NS + ":polearm_whirl", "scythe_slash", 360),
    ]},
    # flanged mace and cudgels: swing, uppercut, overhead
    "mace": {"range_bonus": -0.25, "category": "mace", "attacks": [
        attack(H, 0.8, "one_handed_slash_horizontal_right", "mace_slash", 150),
        attack(V, 1.0, "one_handed_uppercut_right", "mace_slash", 90),
        attack(V, 1.4, "one_handed_slam", "mace_slam", 90),
    ]},
    # daggers and knives: cut, flip and stab, backhand
    "dagger": {"range_bonus": -0.5, "two_handed": False, "category": "dagger", "attacks": [
        attack(H, 0.9, "one_handed_slash_horizontal_right", "dagger_slash", 150),
        attack(F, 1.2, NS + ":dagger_flip", "dagger_slash", 0, 0.55),
        attack(H, 0.9, "one_handed_slash_horizontal_left", "dagger_slash", 150),
        DUAL_STAB,
    ]},
    "ninjato": {"range_bonus": 0, "two_handed": True, "pose": "bettercombat:pose_two_handed_katana",
                "category": "katana", "attacks": [
        attack(H, 0.8, "two_handed_slash_horizontal_right", "katana_slash", 120),
        attack(V, 1.2, "two_handed_slash_vertical_left", "katana_slash", 120),
        attack(F, 1.0, "two_handed_stab_right", "katana_slash", 0),
        attack(V, 1.2, "two_handed_slash_vertical_right", "katana_slash", 120),
    ]},
    # skewers and spears: thrusts, then the shaft swept sideways
    "spear": {"range_bonus": 1, "two_handed": True, "pose": "bettercombat:pose_two_handed_polearm",
              "category": "spear", "attacks": [
        attack(F, 1.0, "two_handed_stab_right", "spear_stab", 0),
        attack(F, 1.0, "two_handed_stab_left", "spear_stab", 0),
        attack(H, 0.7, "two_handed_slash_horizontal_right", "staff_slash", 120),
    ]},
    # vanilla swords: cut, twirl and cut back, thrust, spin
    "sword": {"range_bonus": 0, "category": "sword", "attacks": [
        attack(H, 1.0, "one_handed_slash_horizontal_right", "sword_slash", 120),
        attack(H, 1.0, NS + ":sword_twirl_slash", "sword_slash", 120, 0.55),
        attack(F, 1.0, "one_handed_stab", "sword_slash", 0),
        attack(H, 1.1, NS + ":sword_whirl", "sword_slash", 360),
    ]},
    # vanilla axes: two cuts, then flip it overhead and chop
    "axe": {"range_bonus": -0.25, "category": "axe", "attacks": [
        attack(H, 1.0, "one_handed_slash_horizontal_right", "axe_slash", 90, 0.3),
        attack(H, 1.0, "one_handed_slash_horizontal_left", "axe_slash", 90, 0.3),
        attack(V, 1.3, NS + ":axe_flip_chop", "axe_slash", 90, 0.55),
    ]},
}


# weapons outside the Armory (its own are written by armory_assets.py): vanilla swords and axes
# (Better Combat would otherwise give them its plain presets)
TIERS = ("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")
OTHER_WEAPONS = {
    **{f"minecraft:{t}_sword": "sword" for t in TIERS},
    **{f"minecraft:{t}_axe": "axe" for t in TIERS},
}


def main():
    for make in (staff_twirl_front, staff_whirlwind, staff_twirl_overhead, polearm_whirl, dagger_flip,
                 sword_twirl_slash, sword_whirl, axe_flip_chop):
        make()
    for name, attributes in COMBOS.items():
        write(os.path.join(ATTRS, "combo_" + name + ".json"), {"attributes": attributes})
    for item, combo in OTHER_WEAPONS.items():
        ns, path = item.split(":")
        write(os.path.join(RES, "data", ns, "weapon_attributes", path + ".json"), {"parent": NS + ":combo_" + combo})


if __name__ == "__main__":
    main()
