"""(Jung) Boxing: one Blockbench project per style, with every stance, block, punch and evade it has.

    python3 tools/blockbench/boxing_rig.py            writes art/boxing/<style>.bbmodel from the game's own numbers
    python3 tools/blockbench/boxing_rig.py --check    and checks the rig against the pose maths (see check())

The pose maths are a copy of client/boxing/BoxingPoses.java (which still draws the game), and every number is read from
that file, so a project made now shows what the game does now. Open a project in Blockbench with the GeckoLib Animation
Utils plugin (File > New > GeckoLib Animated Model is the format it is saved in), pick an animation in the Animate tab
and play it.

The rig is the player model, cut where the game bends it: each arm at the elbow (5 px down), each leg at the knee
(6 px down) and the body at the waist (6 px down). A forearm, shin or waist turns about X to fold forward or back; the
Bastion's lead forearm and a hook's forearm fold across about Z. The fist gear (a wrap block on each hand) rides the
forearm. Bones are named the way Player Animation Library names the player's (head, body, right_arm, left_arm,
right_leg, left_leg), plus right_forearm, left_forearm, right_shin, left_shin and waist for the bends.

Axes: Blockbench's (up is +Y, forward is -Z, the player's right is +X). A model part's rotation (x, y, z) in the game
is (-x, -y, z) here, and its offset (x, y, z) is (-x, -y, z): the game's model has Y down and its right on -X.
"""
import base64, io, json, math, os, re, sys, uuid
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
POSES = os.path.join(REPO, "src/client/java/com/jaymospowers/client/boxing/BoxingPoses.java")
OUT = os.path.join(REPO, "art/boxing")

# every `NAME = 1.5F` in BoxingPoses
K = {m.group(1): float(m.group(2)) for m in re.finditer(r"\b([A-Z][A-Z_0-9]+) = (-?[0-9.]+)F", open(POSES).read())}
# the longest windup a punch has (Boxing.WINDUP_SLOWEST, level 1); the pose's windup ends a little early so the punch is at full
# reach as the blow lands
BOXING = open(os.path.join(REPO, "src/main/java/com/jaymospowers/boxing/Boxing.java")).read()
WINDUP = float(re.search(r"\bWINDUP_SLOWEST = (\d+)", BOXING).group(1))
PUNCH_TICKS = 6.0

ARROW_SLIT, LONG_GUARD, BASTION = 0, 1, 2


# ---- 3x3 rotations (row by row) and a tiny transform kit: [r0..r8, tx, ty, tz] ----
def RX(a):
    c, s = math.cos(a), math.sin(a)
    return [1, 0, 0, 0, c, -s, 0, s, c, 0, 0, 0]


def RY(a):
    c, s = math.cos(a), math.sin(a)
    return [c, 0, s, 0, 1, 0, -s, 0, c, 0, 0, 0]


def RZ(a):
    c, s = math.cos(a), math.sin(a)
    return [c, -s, 0, s, c, 0, 0, 0, 1, 0, 0, 0]


def T(x, y, z):
    return [1, 0, 0, 0, 1, 0, 0, 0, 1, x, y, z]


def mul(A, B):
    o = [0.0] * 12
    for r in range(3):
        for c in range(3):
            o[r * 3 + c] = A[r * 3] * B[c] + A[r * 3 + 1] * B[3 + c] + A[r * 3 + 2] * B[6 + c]
    for r in range(3):
        o[9 + r] = A[r * 3] * B[9] + A[r * 3 + 1] * B[10] + A[r * 3 + 2] * B[11] + A[9 + r]
    return o


def apply(M, p):
    return [M[0] * p[0] + M[1] * p[1] + M[2] * p[2] + M[9], M[3] * p[0] + M[4] * p[1] + M[5] * p[2] + M[10],
            M[6] * p[0] + M[7] * p[1] + M[8] * p[2] + M[11]]


class Part:
    def __init__(self, x=0.0, y=0.0, z=0.0):
        self.x, self.y, self.z, self.xRot, self.yRot, self.zRot = x, y, z, 0.0, 0.0, 0.0


REST = {"head": (0, 0, 0), "body": (0, 0, 0), "rightArm": (-5, 2, 0), "leftArm": (5, 2, 0), "rightLeg": (-1.9, 12, 0),
        "leftLeg": (1.9, 12, 0)}


class Pose:
    """The model as BoxingPoses leaves it: the six parts, each elbow's fold (and whether across), the knees, the spine."""

    def __init__(self):
        self.parts = {name: Part(*rest) for name, rest in REST.items()}
        self.fold = {"r": 0.0, "rAcross": False, "l": 0.0, "lAcross": False}
        self.knees = 0.0
        self.spine = 0.0


def pose(style, mode, time, thrown=None, move=None):
    """The model for {@code style} doing {@code mode} ('idle', 'block', 'gassed', 'jab', 'cross', 'straight', 'lead_hook',
    'rear_hook', 'lead_hook_full', 'uppercut') at {@code time} ticks; {@code thrown}: a punch's (wind, at), each 0 to 1;
    {@code move}: an evade ('slip_left', 'slip_right', 'bob', 'pull', 'roll') and its age in ticks. As BoxingPoses.apply."""
    m = Pose()
    p = m.parts
    head, body, rightArm, leftArm, rightLeg, leftLeg = (p[n] for n in ("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"))
    lead_right = style == BASTION
    lead, rear = (rightArm, leftArm) if lead_right else (leftArm, rightArm)
    le, re_ = {"fold": 0.0, "across": False}, {"fold": 0.0, "across": False}
    turn = 0.0

    def set_(arm, right, pitch, inn):
        arm.xRot, arm.yRot, arm.zRot = pitch, (-inn if right else inn), 0.0

    def bend(a):
        body.xRot += a
        back = math.sin(a) * K["TORSO"]
        rightLeg.z += back
        leftLeg.z += back

    def lean(a):
        body.zRot += a
        head.zRot += a * 0.6

    def sway_shoulders(a):
        across, down = math.sin(a) * K["TORSO"], (1 - math.cos(a)) * K["TORSO"]
        body.zRot += a
        body.x += across
        body.y += down
        for q in (head, rightArm, leftArm):
            q.x += across
            q.y += down
        head.zRot += a * 0.6

    def leg(l, spread, step, toes, knee):
        M = mul(mul(mul(RZ(spread), RX(step)), RY(toes)), RX(l.xRot - knee))
        l.yRot = -math.asin(max(-1.0, min(1.0, M[6])))
        l.xRot = math.atan2(M[7], M[8])
        l.zRot = math.atan2(M[3], M[0])

    def base(spread, stagger, knees, blade):
        sink = (K["THIGH"] + K["SHIN"]) * (1 - math.cos(knees) * math.cos(stagger) * math.cos(spread))
        for q in (head, body, rightArm, leftArm, rightLeg, leftLeg):
            q.y += sink
        feet = (-blade if lead_right else blade) * K["FEET_TURN"]
        leg(rightLeg, spread, -stagger if lead_right else stagger, feet, knees)
        leg(leftLeg, -spread, stagger if lead_right else -stagger, feet, knees)
        m.knees = knees * 2

    def hunch(crouch, h):
        upper, lower = crouch + h * 0.5, crouch - h * 0.5
        body.xRot += upper
        back = (math.sin(upper) + math.sin(lower)) * K["TORSO"] * 0.5
        rightLeg.z += back
        leftLeg.z += back
        m.spine = -h

    def shell_head():
        head.zRot += K["BASTION_HEAD_TILT"] if lead_right else -K["BASTION_HEAD_TILT"]
        head.xRot += K["BASTION_CHIN"]

    def reach(arm, right, pitch, inn, amt):
        arm.xRot += (pitch - arm.xRot) * amt
        y = -inn if right else inn
        arm.yRot += (y - arm.yRot) * amt

    def finish():
        r, l = (le, re_) if lead_right else (re_, le)
        m.fold = {"r": -r["fold"] if r["across"] else r["fold"], "rAcross": r["across"], "l": l["fold"], "lAcross": l["across"]}
        return m

    if mode == "gassed":
        heave = math.sin(time * K["HEAVE_RATE"]) * K["HEAVE"]
        set_(lead, lead_right, K["GASSED_PITCH"], K["GASSED_IN"])
        set_(rear, not lead_right, K["GASSED_PITCH"], K["GASSED_IN"])
        le["fold"] = re_["fold"] = K["GASSED_FOLD"]
        lead.y += heave
        rear.y += heave
        bend(K["SLUMP"])
        head.xRot += K["SLUMP"]
        return finish()

    blocking = mode == "block"
    if blocking:
        if style == ARROW_SLIT:
            set_(lead, lead_right, K["SLIT_BLOCK_PITCH"], K["SLIT_BLOCK_IN"])
            set_(rear, not lead_right, K["SLIT_BLOCK_PITCH"], K["SLIT_BLOCK_IN"])
            le["fold"] = re_["fold"] = K["SLIT_BLOCK_FOLD"]
            hunch(K["SLIT_BLOCK_CROUCH"], K["SLIT_HUNCH"])
            base(K["SLIT_SPREAD"], K["SLIT_STAGGER"], K["SLIT_BLOCK_KNEES"], 0.0)
        elif style == LONG_GUARD:
            set_(lead, lead_right, K["LONG_BLOCK_PITCH"], K["LONG_BLOCK_IN"])
            set_(rear, not lead_right, K["LONG_BLOCK_PITCH"], K["LONG_BLOCK_IN"])
            le["fold"] = re_["fold"] = K["LONG_BLOCK_FOLD"]
            bend(K["LONG_LEAN"])
            head.xRot += K["LONG_CHIN"]
            turn += K["LONG_BLADE"]
            base(K["LONG_SPREAD"], K["LONG_STAGGER"], K["LONG_KNEES"], 0.0)
        else:
            set_(lead, lead_right, K["BASTION_BLOCK_LEAD_PITCH"], K["BASTION_LEAD_IN"])
            le["fold"], le["across"] = K["BASTION_BLOCK_ACROSS"], True
            lead.y -= K["BASTION_BLOCK_UP"]
            set_(rear, not lead_right, K["BASTION_BLOCK_REAR_PITCH"], K["BASTION_BLOCK_REAR_IN"])
            re_["fold"] = K["BASTION_BLOCK_REAR_FOLD"]
            turn += K["BASTION_BLOCK_BLADE"]
            bend(K["BASTION_LEAN"])
            shell_head()
            base(K["BASTION_SPREAD"], K["BASTION_STAGGER"], K["BASTION_KNEES"], K["BASTION_BLADE"])
    elif style == ARROW_SLIT:
        sway = math.sin(time * K["SLIT_SWAY_RATE"]) * K["SLIT_SWAY"]
        set_(lead, lead_right, K["SLIT_PITCH"], K["SLIT_IN"])
        set_(rear, not lead_right, K["SLIT_PITCH"], K["SLIT_IN"])
        le["fold"] = re_["fold"] = K["SLIT_FOLD"]
        sway_shoulders(sway)
        lead.zRot += sway
        rear.zRot += sway
        hunch(K["SLIT_CROUCH"], K["SLIT_HUNCH"])
        base(K["SLIT_SPREAD"], K["SLIT_STAGGER"], K["SLIT_KNEES"], 0.0)
    elif style == LONG_GUARD:
        set_(lead, lead_right, K["LONG_LEAD_PITCH"] + math.sin(time * K["LONG_PAW_RATE"]) * K["LONG_PAW"],
             K["LONG_LEAD_IN"] + math.cos(time * K["LONG_PAW_SIDE_RATE"]) * K["LONG_PAW_SIDE"])
        set_(rear, not lead_right, K["LONG_REAR_PITCH"], K["LONG_REAR_IN"])
        le["fold"], re_["fold"] = K["LONG_LEAD_FOLD"], K["LONG_REAR_FOLD"]
        bend(K["LONG_LEAN"])
        turn = K["LONG_BLADE"]
        head.xRot += K["LONG_CHIN"]
        base(K["LONG_SPREAD"], K["LONG_STAGGER"], K["LONG_KNEES"], 0.0)
    else:
        set_(lead, lead_right, K["BASTION_LEAD_PITCH"], K["BASTION_LEAD_IN"])
        set_(rear, not lead_right, K["BASTION_REAR_PITCH"], K["BASTION_REAR_IN"])
        le["fold"], le["across"] = K["BASTION_LEAD_ACROSS"], True
        re_["fold"] = K["BASTION_REAR_FOLD"]
        lead.y -= K["BASTION_SHOULDER"]
        phase = (time % K["SHRUG_EVERY"]) / K["SHRUG_TICKS"]
        if phase < 1:
            lead.y -= math.sin(phase * math.pi) * K["SHRUG_UP"]
        bend(K["BASTION_LEAN"])
        shell_head()
        turn = K["BASTION_BLADE"]
        base(K["BASTION_SPREAD"], K["BASTION_STAGGER"], K["BASTION_KNEES"], K["BASTION_BLADE"])

    if thrown is not None and not blocking:
        wind, at = thrown
        winding = wind < 1
        coil = math.sin(wind * math.pi / 2)
        if winding:
            out = -K["STRAIGHT_COIL"] * coil
        elif at < K["SNAP"]:
            out = -K["STRAIGHT_COIL"] + (1 + K["STRAIGHT_COIL"]) * math.sin(at / K["SNAP"] * math.pi / 2)
        else:
            out = math.cos((at - K["SNAP"]) / (1 - K["SNAP"]) * math.pi / 2)
        fwd = max(0.0, out)
        if mode == "jab":
            reach(lead, lead_right, K["JAB_PITCH"], 0.0, out)
            le["fold"] *= 1 - out
            lead.y -= K["JAB_SHOULDER"] * fwd
            turn += K["JAB_TURN"] * out
        elif mode in ("cross", "straight"):
            reach(rear, not lead_right, K["STRAIGHT_PITCH"], 0.0, out)
            re_["fold"] *= 1 - out
            if not le["across"]:
                reach(lead, lead_right, K["HOME_PITCH"], K["HOME_IN"], fwd)
                le["fold"] += (K["HOME_FOLD"] - le["fold"]) * fwd
            bend(K["CROSS_LEAN"] * fwd)
            (leftLeg if lead_right else rightLeg).yRot += (K["CROSS_KNEE"] if lead_right else -K["CROSS_KNEE"]) * fwd
            turn -= K["CROSS_TURN"] * out
        elif mode == "uppercut":
            dip = coil if winding else 1 - min(1.0, max(0.0, at * 2))
            reach(rear, not lead_right, K["UPPERCUT_FROM"] + (K["UPPERCUT_TO"] - K["UPPERCUT_FROM"]) * at, K["UPPERCUT_IN"], coil if winding else 1.0)
            amt = coil if winding else 1.0
            start = 0.0 if re_["across"] else re_["fold"]
            re_["fold"], re_["across"] = start + (K["UPPERCUT_FOLD"] - start) * amt, False
            bend(K["UPPERCUT_DIP"] * dip)
            turn += -K["PUNCH_TURN"] * (-K["HOOK_COIL"] * coil if winding else math.sin(at * math.pi))
        else:
            leading, heavy = mode != "rear_hook", mode == "lead_hook_full"
            arm = lead if leading else rear
            right = leading == lead_right
            home = min(1.0, (1 - at) / K["HOOK_HOME"])
            inn = coil if winding else home
            sweep = 0.0 if winding else math.sin(max(0.0, min(1.0, at / K["HOOK_LANDS"])) * math.pi / 2)
            frm, to = (K["HOOK_FROM"], K["HOOK_TO"]) if heavy else (K["LIGHT_HOOK_FROM"], K["LIGHT_HOOK_TO"])
            reach(arm, right, K["HOOK_PITCH"], frm + (to - frm) * sweep, inn)
            arm.zRot = 0.0
            e = le if leading else re_
            if not winding or wind > 0.5:
                e["fold"], e["across"] = (K["HOOK_ACROSS"] if heavy else K["LIGHT_HOOK_ACROSS"]), True
            through = -K["HOOK_COIL"] * coil if winding else (-K["HOOK_COIL"] + (1 + K["HOOK_COIL"]) * sweep) * home
            planted = max(0.0, through)
            (rightLeg if right else leftLeg).yRot += (-1 if right else 1) * (K["HOOK_PIVOT"] if heavy else K["LIGHT_HOOK_PIVOT"]) * planted
            bend(K["HOOK_LEAN"] * planted)
            turned = (K["HOOK_TURN"] if heavy else K["LIGHT_HOOK_TURN"]) * through
            turn += turned if leading else -turned

    if move is not None:
        kind, age = move
        amount = math.sin(min(1.0, max(0.0, age / K["MOVE_TICKS"])) * math.pi)
        if kind == "slip_left":
            lean(K["SLIP_LEAN"] * amount)
        elif kind == "slip_right":
            lean(-K["SLIP_LEAN"] * amount)
        elif kind == "bob":
            bend(K["BOB_DUCK"] * amount)
            head.xRot += K["BOB_DUCK"] * amount * 0.5
        elif kind == "pull":
            bend(K["PULL_LEAN"] * amount)
        elif kind == "roll":
            turn += K["ROLL_TURN"] * amount
            lead.y -= K["ROLL_UP"] * amount
            bend(K["ROLL_LEAN"] * amount)
            head.zRot += (K["ROLL_HEAD"] if lead_right else -K["ROLL_HEAD"]) * amount

    a = -turn if lead_right else turn
    if a != 0:
        body.yRot += a
        rightArm.z, rightArm.x = math.sin(a) * K["SHOULDER_OUT"], -math.cos(a) * K["SHOULDER_OUT"]
        leftArm.z, leftArm.x = -math.sin(a) * K["SHOULDER_OUT"], math.cos(a) * K["SHOULDER_OUT"]
    return finish()


# ---- each style's moves: (name, kind, length in ticks, loop, sampler(t) -> Pose) ----
WIND = WINDUP - K["SNAP"] * PUNCH_TICKS


def punch(style, mode):
    def at(t):
        if t < WIND:
            return pose(style, mode, t, (t / WIND, 0.0))
        return pose(style, mode, t, (1.0, min(1.0, (t - WIND) / PUNCH_TICKS)))
    return (mode, "once", WIND + PUNCH_TICKS, at)


def evade(style, kind, after=None):
    return (kind, "once", K["MOVE_TICKS"], lambda t: pose(style, "idle", 0.0, None, (kind, t)))


def moves(style):
    idle_len = {ARROW_SLIT: 2 * math.pi / K["SLIT_SWAY_RATE"], LONG_GUARD: 8 * math.pi / K["LONG_PAW_SIDE_RATE"] / 1.0,
                BASTION: K["SHRUG_EVERY"]}[style]
    if style == LONG_GUARD:
        # the pawing's two rates meet again about here (to a degree or so)
        idle_len = 4 * 2 * math.pi / K["LONG_PAW_SIDE_RATE"]
    out = [("idle", "loop", idle_len, lambda t: pose(style, "idle", t)),
           ("block", "loop", 20.0, lambda t: pose(style, "block", t)),
           ("gassed", "loop", 2 * math.pi / K["HEAVE_RATE"], lambda t: pose(style, "gassed", t))]
    if style == ARROW_SLIT:
        out += [punch(style, "jab"), punch(style, "lead_hook"), punch(style, "cross"), punch(style, "lead_hook_full"),
                punch(style, "uppercut"), evade(style, "slip_left"), evade(style, "slip_right"), evade(style, "bob")]
    elif style == LONG_GUARD:
        out += [punch(style, "jab"), punch(style, "cross"), evade(style, "pull"), punch(style, "straight")]
        out[-1] = ("pull_counter",) + out[-1][1:]
    else:
        out += [punch(style, "jab"), evade(style, "roll"), punch(style, "straight"), evade(style, "slip_left"), evade(style, "slip_right")]
        out[-3] = ("roll_straight",) + out[-3][1:]
    return out


# ---- the rig in Blockbench's space ----
# a bone: name, pivot, parent; its cubes: (from, to, uv block, inflate)
BONES = [
    ("body", [0, 24, 0], None), ("waist", [0, 18, 0], "body"), ("head", [0, 24, 0], None),
    ("right_arm", [5, 22, 0], None), ("right_forearm", [6, 17, 0], "right_arm"),
    ("left_arm", [-5, 22, 0], None), ("left_forearm", [-6, 17, 0], "left_arm"),
    ("right_leg", [1.9, 12, 0], None), ("right_shin", [1.9, 6, 0], "right_leg"),
    ("left_leg", [-1.9, 12, 0], None), ("left_shin", [-1.9, 6, 0], "left_leg"),
]
CUBES = {
    "body": [("chest", [-4, 18, -2], [4, 24, 2], "shirt", 0)],
    "waist": [("belly", [-4, 12, -2], [4, 18, 2], "shirt2", 0)],
    "head": [("head", [-4, 24, -4], [4, 32, 4], "head", 0)],
    "right_arm": [("upper arm", [4, 17, -2], [8, 24, 2], "sleeve", 0)],
    "right_forearm": [("forearm", [4, 12, -2], [8, 17, 2], "skin", 0), ("hand wrap", [4, 12, -2], [8, 16, 2], "wrap", 0.55)],
    "left_arm": [("upper arm", [-8, 17, -2], [-4, 24, 2], "sleeve", 0)],
    "left_forearm": [("forearm", [-8, 12, -2], [-4, 17, 2], "skin", 0), ("hand wrap", [-8, 12, -2], [-4, 16, 2], "wrap", 0.55)],
    "right_leg": [("thigh", [-0.1, 6, -2], [3.9, 12, 2], "pants", 0)],
    "right_shin": [("shin", [-0.1, 0, -2], [3.9, 6, 2], "boot", 0)],
    "left_leg": [("thigh", [-3.9, 6, -2], [0.1, 12, 2], "pants", 0)],
    "left_shin": [("shin", [-3.9, 0, -2], [0.1, 6, 2], "boot", 0)],
}
# 64x64 texture: each block is a box-UV patch, (u, v, colour)
UV = {"head": (0, 0, (198, 140, 104)), "shirt": (32, 0, (60, 92, 128)), "shirt2": (32, 10, (52, 82, 116)),
      "sleeve": (0, 16, (60, 92, 128)), "skin": (16, 16, (198, 140, 104)), "wrap": (32, 20, (226, 214, 188)),
      "pants": (0, 32, (54, 58, 74)), "boot": (16, 32, (40, 36, 34))}


def texture():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    sizes = {}
    for bone, cubes in CUBES.items():
        for _, a, b, key, _ in cubes:
            sizes[key] = (abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]))
    for key, (u, v, col) in UV.items():
        w, h, d = (math.ceil(s) for s in sizes[key])
        for x in range(u, u + 2 * (w + d)):
            for y in range(v, v + h + d):
                if x < 64 and y < 64:
                    shade = 1.0 if y >= v + d else 0.9
                    px[x, y] = tuple(int(c * shade) for c in col) + (255,)
    # hair on top of the head and round its upper rows, eyes on its front (north) face
    u, v, _ = UV["head"]
    for x in range(u, u + 32):
        for y in range(v, v + 8 + 2):
            if y < v + 8 and not (u + 8 <= x < u + 16 or u + 16 <= x < u + 24) or y >= v + 8:
                px[x, y] = (84, 58, 40, 255)
    for x in range(u + 8, u + 16):
        px[x, v + 7] = (84, 58, 40, 255)
    for x in (u + 9, u + 10, u + 13, u + 14):
        px[x, v + 12] = (30, 30, 34, 255)
        px[x, v + 13] = (30, 30, 34, 255)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def box_faces(u, v, w, h, d):
    return {
        "north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": 0},
        "east": {"uv": [u, v + d, u + d, v + d + h], "texture": 0},
        "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": 0},
        "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": 0},
        "up": {"uv": [u + d + w, v + d, u + d, v], "texture": 0},
        "down": {"uv": [u + d + 2 * w, v, u + d + w, v + d], "texture": 0},
    }


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymospowers/boxing/" + "/".join(parts)))


# ---- a pose as bone values (degrees, pixels) ----
PART_OF = {"head": "head", "body": "body", "right_arm": "rightArm", "left_arm": "leftArm", "right_leg": "rightLeg",
           "left_leg": "leftLeg"}


def bone_values(m):
    """Each bone's (rotation, position) in Blockbench's space for pose {@code m}."""
    out = {}
    for bone, name in PART_OF.items():
        q = m.parts[name]
        x0, y0, z0 = REST[name]
        out[bone] = ([-math.degrees(q.xRot), -math.degrees(q.yRot), math.degrees(q.zRot)], [-(q.x - x0), -(q.y - y0), q.z - z0])
    for side, key in (("right", "r"), ("left", "l")):
        f = m.fold[key]
        out[side + "_forearm"] = ([0.0, 0.0, math.degrees(f)] if m.fold[key + "Across"] else [-math.degrees(f), 0.0, 0.0], [0.0, 0.0, 0.0])
        out[side + "_shin"] = ([-math.degrees(m.knees), 0.0, 0.0], [0.0, 0.0, 0.0])
    out["waist"] = ([-math.degrees(m.spine), 0.0, 0.0], [0.0, 0.0, 0.0])
    return out


def keyframes(sampler, length, loop):
    """Samples a move: a key every tick for a short one, every two ticks for a long loop; channels that never move keep one key."""
    step = 1.0 if length <= 30 else 2.0
    times = []
    t = 0.0
    while t < length - 1e-6:
        times.append(t)
        t += step
    times.append(length)
    samples = [(t, bone_values(sampler(min(t, length if loop == "once" else t)))) for t in times]
    if loop == "loop":
        # the last key is the first again, so the loop closes
        samples[-1] = (length, samples[0][1])
    tracks = {}
    for bone in samples[0][1]:
        for ch, idx in (("rotation", 0), ("position", 1)):
            vals = [(t, v[bone][idx]) for t, v in samples]
            if all(max(abs(a - b) for a, b in zip(vals[0][1], val)) < 0.05 for _, val in vals):
                if max(abs(c) for c in vals[0][1]) < 0.05:
                    continue
                vals = [vals[0]]
            tracks.setdefault(bone, []).append((ch, vals))
    return tracks


STYLE = {ARROW_SLIT: ("arrow_slit", "Arrow Slit"), LONG_GUARD: ("long_guard", "Long Guard"), BASTION: ("bastion", "Bastion")}


def project(style):
    sid, title = STYLE[style]
    elements, groups = [], {}
    for bone, pivot, parent in BONES:
        groups[bone] = {"name": bone, "origin": pivot, "color": 0, "uuid": uid(sid, "bone", bone), "export": True,
                        "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0, "children": []}
        for name, a, b, key, inflate in CUBES[bone]:
            u, v, _ = UV[key]
            w, h, d = abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2])
            el = {"name": name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
                  "allow_mirror_modeling": True, "from": [min(a[0], b[0]), min(a[1], b[1]), min(a[2], b[2])],
                  "to": [max(a[0], b[0]), max(a[1], b[1]), max(a[2], b[2])], "autouv": 0, "color": 0, "inflate": inflate,
                  "origin": pivot, "uv_offset": [u, v], "faces": box_faces(u, v, math.ceil(w), math.ceil(h), math.ceil(d)),
                  "type": "cube", "uuid": uid(sid, "cube", bone, name)}
            elements.append(el)
            groups[bone]["children"].append(el["uuid"])
    outliner = []
    for bone, _, parent in BONES:
        (groups[parent]["children"] if parent else outliner).append(groups[bone])
    animations = []
    for name, loop, length, sampler in moves(style):
        tracks = keyframes(sampler, length, loop)
        animators = {}
        for bone, chans in tracks.items():
            kfs = []
            for ch, vals in chans:
                for t, v in vals:
                    kfs.append({"channel": ch, "data_points": [{"x": round(v[0], 3), "y": round(v[1], 3), "z": round(v[2], 3)}],
                                "uuid": uid(sid, name, bone, ch, "%.3f" % t), "time": round(t / 20.0, 4), "color": -1,
                                "interpolation": "linear"})
            animators[groups[bone]["uuid"]] = {"name": bone, "type": "bone", "keyframes": kfs}
        animations.append({"uuid": uid(sid, "anim", name), "name": "%s.%s" % (sid, name), "loop": loop,
                           "override": False, "length": round(length / 20.0, 4), "snapping": 20, "selected": False,
                           "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})
    return {
        "meta": {"format_version": "4.10", "model_format": "animated_entity_model", "box_uv": True},
        "name": sid, "model_identifier": "", "modded_entity_entity_class": "", "modded_entity_version": "",
        "modded_entity_flip_y": True, "visible_box": [1, 1, 0], "variable_placeholders": "",
        "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": 64, "height": 64}, "elements": elements, "outliner": outliner,
        "textures": [{"path": "", "name": "boxer.png", "folder": "", "namespace": "", "id": "0", "width": 64, "height": 64,
                      "uv_width": 64, "uv_height": 64, "particle": False, "use_as_default": True, "layers_enabled": False,
                      "sync_to_project": "", "render_mode": "default", "render_sides": "auto", "frame_time": 1,
                      "frame_order_type": "loop", "frame_order": "", "frame_interpolate": False, "visible": True,
                      "internal": True, "saved": True, "uuid": uid(sid, "texture"), "relative_path": "", "source": texture()}],
        "animations": animations,
    }


# ---- the check: the rig, posed by its own bone values, puts every box where the pose maths do ----
def check():
    worst = 0.0
    for style in STYLE:
        for name, loop, length, sampler in moves(style):
            for t in (0.0, length * 0.37, length * 0.74):
                m = sampler(t)
                vals = bone_values(m)
                for bone, pivot, parent in BONES:
                    if parent:
                        continue
                    # Blockbench: the bone turns about its pivot (Z, then Y, then X applied last to the point first), then moves
                    rot, pos = vals[bone]
                    R = mul(mul(RZ(math.radians(rot[2])), RY(math.radians(rot[1]))), RX(math.radians(rot[0])))
                    for _, a, b, _, _ in CUBES[bone]:
                        for corner in ((a[0], a[1], a[2]), (b[0], b[1], b[2]), (a[0], b[1], b[2])):
                            local = [corner[i] - pivot[i] for i in range(3)]
                            bb = apply(R, local)
                            bb = [bb[i] + pivot[i] + pos[i] for i in range(3)]
                            # the same point by the game's model: Blockbench (x, y, z) is the game's (-x, 24 - y, z)
                            q = m.parts[PART_OF[bone]]
                            M = mul(mul(mul(T(q.x, q.y, q.z), RZ(q.zRot)), RY(q.yRot)), RX(q.xRot))
                            x0, y0, z0 = REST[PART_OF[bone]]
                            game_local = [-corner[0] - x0, 24 - corner[1] - y0, corner[2] - z0]
                            g = apply(M, game_local)
                            worst = max(worst, abs(-g[0] - bb[0]), abs(24 - g[1] - bb[1]), abs(g[2] - bb[2]))
    print("rig check: worst gap between the rig and the pose maths %.4f px" % worst)
    return worst < 0.01


def main():
    os.makedirs(OUT, exist_ok=True)
    for style, (sid, _) in STYLE.items():
        with open(os.path.join(OUT, sid + ".bbmodel"), "w") as f:
            json.dump(project(style), f, indent=1)
        print("wrote", os.path.relpath(os.path.join(OUT, sid + ".bbmodel"), REPO), "-", ", ".join(n for n, *_ in moves(style)))
    if "--check" in sys.argv and not check():
        sys.exit(1)


if __name__ == "__main__":
    main()
