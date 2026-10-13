"""Writes Jaymo's Armory's own Better Combat animations and attack combos.

    python -I tools/combat_animations.py

Animations go to assets/jaymospowers/player_animations (the Emotecraft JSON that Better Combat's animations use, read
by Player Animation Library): ticks, radians, and each part's pose as pitch/yaw/roll plus its pivot x/y/z in model
pixels. Arm pitch below 0 lifts the arm forward; roll above 0 swings the hand toward the player's right; the head
and arms follow the torso. On `rightItem` the roll spins the held item round the forearm, which is how the
quarterstaff twirls (its model is held by the middle, see armory_assets.py), the pitch flips it end over end, and the
yaw only turns it about its own length.

Combos go to data/jaymospowers/weapon_attributes/combo_*.json; each weapon's own file names one as its parent. Better
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


def spin(start, end, total, steps, part, channel):
    """Frames turning `part`'s `channel` from 0 to `total` between ticks, evenly."""
    return [(round(start + (end - start) * i / steps, 2), "LINEAR", {part: {channel: total * i / steps}})
            for i in range(1, steps + 1)]


# --- the quarterstaff ---------------------------------------------------------------------------------------------

STAFF_GUARD = {
    "rightArm": {"pitch": -0.85, "yaw": -0.25, "roll": 0.15, "z": -0.8},
    "leftArm": {"pitch": -1.05, "yaw": 0.35, "roll": 0.9, "x": 5.2, "z": -1.2},
}


def staff_twirl_front():
    """Let go with the left hand and spin the staff like a propeller in front of you, twice round."""
    reach = {"rightArm": {"pitch": -1.5, "yaw": -0.15, "roll": 0.0, "x": -5, "y": 2, "z": -1.2},
             "leftArm": {"pitch": -0.35, "yaw": 0.0, "roll": -0.45, "x": 5, "y": 2, "z": 0},
             "torso": {"yaw": 0.2, "pitch": -0.08}, "rightItem": {"roll": 0}}
    frames = [(4, "EASEOUTQUAD", reach)]
    # 2 turns, quick in the middle: angles at ticks 6..16
    for tick, frac in ((6, 0.06), (8, 0.25), (10, 0.5), (12, 0.75), (14, 0.94), (16, 1.0)):
        frames.append((tick, "LINEAR", {"rightItem": {"roll": 2 * TURN * frac}}))
    frames[3][2].update({"rightArm": {"z": -2.2, "pitch": -1.55}, "torso": {"yaw": -0.1}})  # tick 10: push in
    frames[-1][2].update(hold("rightArm", "leftArm", "torso"))
    frames.append(snap(16, {"rightItem": {"roll": 0}}))
    frames.append((20, "EASEINOUTQUAD", {**STAFF_GUARD, "torso": {"yaw": 0, "pitch": 0}}))
    animation("staff_twirl_front", "Quarterstaff: twirl in front like a propeller", 4, 20, 28, frames)


def staff_whirlwind():
    """Staff held out to the right, still twirling, as you turn a full circle."""
    out = {"rightArm": {"pitch": -0.15, "yaw": 0, "roll": 1.45, "x": -5, "y": 2, "z": 0},
           "leftArm": {"pitch": -0.1, "yaw": 0, "roll": -1.25, "x": 5, "y": 2, "z": 0}}
    frames = [
        (5, "EASEOUTQUAD", {**out, "torso": {"yaw": 0.7, "pitch": -0.05}, "rightItem": {"roll": 0},
                            "rightLeg": {"pitch": -0.25, "yaw": 0.3}, "leftLeg": {"pitch": 0.25, "yaw": 0.3}}),
    ]
    # the body: 0.7 -> 0.7 - 1 turn (clockwise from above) between ticks 6 and 15, the staff twirls once with it
    for i, tick in enumerate((7, 9, 11, 13, 15), start=1):
        frames.append((tick, "LINEAR", {"torso": {"yaw": 0.7 - TURN * i / 5}, "rightItem": {"roll": TURN * i / 5},
                                        "rightLeg": {"yaw": 0.3 - 0.6 * i / 5}, "leftLeg": {"yaw": 0.3 - 0.6 * i / 5}}))
    frames[-1][2].update(hold("rightArm", "leftArm"))
    frames.append(snap(15, {"torso": {"yaw": 0.7}, "rightItem": {"roll": 0}}))
    frames.append((20, "EASEINOUTQUAD", {**STAFF_GUARD, "torso": {"yaw": 0, "pitch": 0}, "rightLeg": {"pitch": 0, "yaw": 0},
                                         "leftLeg": {"pitch": 0, "yaw": 0}}))
    animation("staff_whirlwind", "Quarterstaff: spin round a full circle with the staff out and twirling", 6, 20, 28,
              frames)


def staff_twirl_overhead():
    """Spin the staff flat over your head like a helicopter, twice round, then bring it down hard."""
    up = {"rightArm": {"pitch": -2.65, "yaw": 0, "roll": 0.15, "x": -5, "y": 2, "z": -1},
          "leftArm": {"pitch": -1.9, "yaw": 0, "roll": -0.55, "x": 5, "y": 2, "z": -0.5},
          "torso": {"pitch": 0.08, "yaw": 0}, "rightItem": {"roll": 0},
          "rightLeg": {"pitch": 0}, "leftLeg": {"pitch": 0}}
    frames = [(3, "EASEOUTQUAD", up)]
    for tick, frac in ((4, 0.04), (5, 0.12), (6, 0.25), (7, 0.4), (8, 0.55), (9, 0.7), (10, 0.85), (11, 1.0)):
        frames.append((tick, "LINEAR", {"rightItem": {"roll": 2 * TURN * frac}}))
    frames[-1][2].update(hold("rightArm", "leftArm", "torso", "rightLeg", "leftLeg"))
    frames.append(snap(11, {"rightItem": {"roll": 0}}))
    strike = {"rightArm": {"pitch": -1.0, "yaw": -0.2, "roll": 0.1, "z": -2},
              "leftArm": {"pitch": -0.95, "yaw": 0.35, "roll": 0.9, "x": 5.2, "z": -1.5},
              "torso": {"pitch": -0.45, "y": -0.3},
              "rightLeg": {"pitch": -0.35}, "leftLeg": {"pitch": 0.3, "z": 1.5}}
    frames.append((13, "EASEINQUAD", strike))
    frames.append((20, "EASEINOUTQUAD", {**STAFF_GUARD, "torso": {"pitch": 0, "y": 0},
                                         "rightLeg": {"pitch": 0}, "leftLeg": {"pitch": 0, "z": 0}}))
    animation("staff_twirl_overhead", "Quarterstaff: helicopter twirl over the head into an overhead strike", 3, 20, 28,
              frames)


# --- others -------------------------------------------------------------------------------------------------------

def polearm_whirl():
    """A long blade held low and out to the right, swept round a full circle (glaive, war scythe)."""
    low = {"rightArm": {"pitch": -0.75, "yaw": 0.45, "roll": 0.35, "x": -5, "y": 2, "z": -0.5},
           "leftArm": {"pitch": -0.95, "yaw": 0.55, "roll": 0.95, "x": 5, "y": 2, "z": -1.5},
           "torso": {"yaw": 0.9, "pitch": -0.15},
           "rightLeg": {"pitch": -0.2, "yaw": 0.35}, "leftLeg": {"pitch": 0.3, "yaw": 0.35}}
    frames = [(6, "EASEOUTQUAD", low)]
    for i, tick in enumerate((8, 10, 12, 14), start=1):
        frames.append((tick, "LINEAR", {"torso": {"yaw": 0.9 - TURN * i / 4},
                                        "rightLeg": {"yaw": 0.35 - 0.7 * i / 4}, "leftLeg": {"yaw": 0.35 - 0.7 * i / 4}}))
    frames[-1][2].update(hold("rightArm", "leftArm"))
    frames.append(snap(14, {"torso": {"yaw": 0.9}}))
    frames.append((20, "EASEINOUTQUAD", {"torso": {"yaw": 0, "pitch": 0}, "rightLeg": {"pitch": 0, "yaw": 0},
                                         "leftLeg": {"pitch": 0, "yaw": 0}}))
    animation("polearm_whirl", "Polearm: blade held low, swept round a full circle", 6, 20, 28, frames)


def dagger_flip():
    """Flip the knife end over end in your hand, catch it, and stab."""
    ready = {"rightArm": {"pitch": -0.95, "yaw": 0.35, "roll": 0.2, "x": -5, "y": 2, "z": -1},
             "leftArm": {"pitch": -0.3, "roll": -0.3}, "rightItem": {"pitch": 0, "roll": 0},
             "torso": {"yaw": -0.35}}
    frames = [(3, "EASEOUTQUAD", ready)]
    frames += spin(3, 8, -TURN, 4, "rightItem", "pitch")  # the same way it then turns into the stab
    frames[-1][2].update(hold("rightArm", "leftArm", "torso"))
    frames.append(snap(8, {"rightItem": {"pitch": 0}}))
    # into Better Combat's own one-handed stab
    stab = {"rightArm": {"pitch": -1.68, "yaw": 0.85, "roll": 0.18, "x": -5.57, "y": 1.58, "z": -2.68},
            "rightItem": {"pitch": -1.38, "roll": 1.56, "x": 0.98, "y": -2.08, "z": -1.78},
            "leftArm": {"pitch": 0.3, "roll": -0.98, "x": 5.3, "y": 3.26, "z": 0.42},
            "torso": {"yaw": 0.85, "pitch": -0.39, "roll": -0.42}}
    frames.append((11, "EASEINQUAD", stab))
    frames.append((20, "EASEINOUTQUAD", {"torso": {"yaw": 0.6, "pitch": -0.3, "roll": -0.3}}))
    animation("dagger_flip", "Dagger: flip the knife in the hand, then stab", 3, 20, 28, frames)


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
}


def main():
    for make in (staff_twirl_front, staff_whirlwind, staff_twirl_overhead, polearm_whirl, dagger_flip):
        make()
    for name, attributes in COMBOS.items():
        write(os.path.join(ATTRS, "combo_" + name + ".json"), {"attributes": attributes})


if __name__ == "__main__":
    main()
