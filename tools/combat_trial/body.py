"""The blocky player's arms and a held sword, in the game's model space (pixels; +x the player's left, +y down, -z
forward), so a pose can be checked by where its hands and blade end up. Matches what the game draws:

- a part turns by its pitch/yaw/roll as a ModelPart does (Rz * Ry * Rx) about its pivot; the arms ride the torso's yaw;
- an arm folds at ELBOW pixels down it (KneeBend about the part's X; + bend brings the forearm forward);
- the item sits at the fist's GRIP point and its blade runs along BLADE (ItemInHandLayer plus the handheld model's
  third-person display: rotation [0, -90, 55]).
"""
import math

ELBOW = 4.0
PIVOT = {"right": (-5.0, 2.0, 0.0), "left": (5.0, 2.0, 0.0)}
GRIP = {"right": (-1.0, 10.0, -2.0), "left": (1.0, 10.0, -2.0)}
# the blade's direction in the fist (part space): forward, a little down while the arm hangs
BLADE = (0.0, 0.1736, -0.9848)
# a two-handed grip: the second fist this far up the hilt from the first (toward the pommel)
HILT = 4.0


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def app(m, v):
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))


def rx(t):
    c, s = math.cos(t), math.sin(t)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(t):
    c, s = math.cos(t), math.sin(t)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def part(pitch, yaw, roll):
    return mul(rz(roll), mul(ry(yaw), rx(pitch)))


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def sub(a, b):
    return tuple(x - y for x, y in zip(a, b))


def scale(a, k):
    return tuple(x * k for x in a)


def fold(p, bend):
    """A point of the arm (part space) where it's drawn with the elbow folded by `bend`."""
    if p[1] <= ELBOW:
        return p
    m = rx(-bend)
    return add((0.0, ELBOW, 0.0), app(m, sub(p, (0.0, ELBOW, 0.0))))


def hand(side, arm, torso_yaw=0.0):
    """(grip point, blade direction) in model space for an arm pose {pitch, yaw, roll, bend}."""
    r = mul(ry(torso_yaw), part(arm.get("pitch", 0.0), arm.get("yaw", 0.0), arm.get("roll", 0.0)))
    bend = arm.get("bend", 0.0)
    grip = add(app(ry(torso_yaw), PIVOT[side]), app(r, fold(GRIP[side], bend)))
    blade = app(r, app(rx(-bend), BLADE))
    return grip, blade


def second_grip(right, torso_yaw=0.0):
    """Where the left fist belongs on the hilt, given the right arm's pose."""
    grip, blade = hand("right", right, torso_yaw)
    return sub(grip, scale(blade, HILT))


def nelder_mead(f, x0, step=0.2, iters=600):
    n = len(x0)
    pts = [list(x0)] + [[x0[j] + (step if j == i else 0.0) for j in range(n)] for i in range(n)]
    vals = [f(p) for p in pts]
    for _ in range(iters):
        order = sorted(range(n + 1), key=lambda i: vals[i])
        pts, vals = [pts[i] for i in order], [vals[i] for i in order]
        c = [sum(p[j] for p in pts[:-1]) / n for j in range(n)]
        refl = [c[j] + (c[j] - pts[-1][j]) for j in range(n)]
        fr = f(refl)
        if fr < vals[0]:
            exp = [c[j] + 2 * (c[j] - pts[-1][j]) for j in range(n)]
            fe = f(exp)
            pts[-1], vals[-1] = (exp, fe) if fe < fr else (refl, fr)
        elif fr < vals[-2]:
            pts[-1], vals[-1] = refl, fr
        else:
            con = [c[j] + 0.5 * (pts[-1][j] - c[j]) for j in range(n)]
            fc = f(con)
            if fc < vals[-1]:
                pts[-1], vals[-1] = con, fc
            else:
                pts = [pts[0]] + [[pts[0][j] + 0.5 * (p[j] - pts[0][j]) for j in range(n)] for p in pts[1:]]
                vals = [vals[0]] + [f(p) for p in pts[1:]]
    best = min(range(n + 1), key=lambda i: vals[i])
    return pts[best], vals[best]


def reach(side, target, start, torso_yaw=0.0, pull=None):
    """An arm pose (pitch, yaw, roll, bend) that puts the fist's grip on `target`: what an animator nudges the keys to
    by eye. `pull` adds a soft preference (a function of the pose) so the elbow sits naturally."""
    def cost(x):
        arm = {"pitch": x[0], "yaw": x[1], "roll": x[2], "bend": x[3]}
        g, _ = hand(side, arm, torso_yaw)
        d = sum(v * v for v in sub(g, target))
        if x[3] < 0:
            d += 50 * x[3] * x[3]
        if x[3] > 2.2:
            d += 50 * (x[3] - 2.2) ** 2
        return d + (pull(arm) if pull else 0.0)
    x, err = nelder_mead(cost, [start["pitch"], start["yaw"], start["roll"], start["bend"]])
    x, err = nelder_mead(cost, x, step=0.05)
    return {"pitch": x[0], "yaw": x[1], "roll": x[2], "bend": x[3]}, err
