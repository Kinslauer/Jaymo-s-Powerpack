"""Checks our Better Combat animations (Emotecraft JSON, read by Player Animation Library).

    python3 -I tools/check_player_animations.py

Every file in assets/jaymospowers/player_animations must parse; its emote must have beginTick <= endTick <= stopTick;
every move's tick must lie within 0..stopTick; every easing must be one of the names the format reads; every move must
name exactly one known part with known channels. With --expect, the begin/end/stop ticks must also match the ones
Better Combat's damage windows were tuned to (EXPECTED). Exits 1 on any problem.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANIMS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "player_animations")

# the easings in use (ours and Better Combat's own files); others may be valid, but these are known to load
EASINGS = {"LINEAR", "CONSTANT", "EASEINQUAD", "EASEOUTQUAD", "EASEINOUTQUAD"}
PARTS = {"head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightItem", "leftItem"}
CHANNELS = {"pitch", "yaw", "roll", "x", "y", "z", "bend", "axis"}
# (beginTick, endTick, stopTick) each animation's hit timing hangs on
EXPECTED = {
    "sword_twirl_slash": (9, 20, 28), "sword_whirl": (6, 20, 28), "axe_flip_chop": (9, 20, 28),
    "dagger_flip": (3, 20, 28), "polearm_whirl": (6, 20, 28), "staff_twirl_front": (4, 20, 28),
    "staff_whirlwind": (6, 20, 28), "staff_twirl_overhead": (3, 20, 28),
}


def check(path, expect):
    problems = []
    name = os.path.splitext(os.path.basename(path))[0]
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:
        return [f"{name}: does not parse: {e}"]
    emote = data.get("emote")
    if not isinstance(emote, dict):
        return [f"{name}: no emote"]
    begin, end, stop = emote.get("beginTick"), emote.get("endTick"), emote.get("stopTick")
    if not all(isinstance(t, (int, float)) for t in (begin, end, stop)):
        return [f"{name}: beginTick/endTick/stopTick missing"]
    if not 0 <= begin <= end <= stop:
        problems.append(f"{name}: ticks out of order ({begin}, {end}, {stop})")
    if expect and name in EXPECTED and (begin, end, stop) != EXPECTED[name]:
        problems.append(f"{name}: ticks {(begin, end, stop)} changed from {EXPECTED[name]}")
    moves = emote.get("moves")
    if not isinstance(moves, list) or not moves:
        return problems + [f"{name}: no moves"]
    for i, move in enumerate(moves):
        tick = move.get("tick")
        if not isinstance(tick, (int, float)) or not 0 <= tick <= stop:
            problems.append(f"{name}: move {i} tick {tick} outside 0..{stop}")
        if move.get("easing", "LINEAR") not in EASINGS:
            problems.append(f"{name}: move {i} easing {move.get('easing')!r}")
        parts = [k for k in move if k not in ("tick", "easing", "turn")]
        if len(parts) != 1 or parts[0] not in PARTS:
            problems.append(f"{name}: move {i} parts {parts}")
            continue
        for channel, value in move[parts[0]].items():
            if channel not in CHANNELS or not isinstance(value, (int, float)):
                problems.append(f"{name}: move {i} {parts[0]}.{channel} = {value!r}")
    return problems


def main():
    expect = "--expect" in sys.argv
    files = sorted(f for f in os.listdir(ANIMS) if f.endswith(".json"))
    problems = []
    for f in files:
        problems += check(os.path.join(ANIMS, f), expect)
    for p in problems:
        print(p)
    print(f"{len(files)} animations checked, {len(problems)} problems")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
