import math

# (0.17.130) where vanilla's handheld sword puts the middle of its grip (the sprite's pixel 3,12), right hand: the held
# models put the middle of theirs there too, so they sit in the fist as deep as a sword does (Andy: the dagger sat high)
GRIP_ANCHOR = {"thirdperson": (0.0, -1.33, 1.44), "firstperson": (1.13, -0.87, -0.35)}


def _rotate(rx, ry, rz, v):
    """ItemTransform's rotation (JOML rotationXYZ: z first, then y, then x) applied to v."""
    x, y, z = v
    a, b, c = (math.radians(r) for r in (rx, ry, rz))
    x, y = x * math.cos(c) - y * math.sin(c), x * math.sin(c) + y * math.cos(c)
    x, z = x * math.cos(b) + z * math.sin(b), -x * math.sin(b) + z * math.cos(b)
    y, z = y * math.cos(a) - z * math.sin(a), y * math.sin(a) + z * math.cos(a)
    return x, y, z


def _held(context, rotation, scale, grip_y):
    gx, gy, gz = _rotate(*rotation, (0.0, (grip_y - 8) * scale, 0.0))
    ax, ay, az = GRIP_ANCHOR[context]
    return [round(ax - gx, 2), round(ay - gy, 2), round(az - gz, 2)]


def display(tp, fp, gui, center_y, grip_y, flip=False, tilt=0, fp_tilt=0):
    """Held/GUI transforms for an upright blade model: the middle of its grip at grip_y, its length centred on center_y.
    flip turns it half round the blade in the hand (the rapier's knuckle bow goes under the fingers, not over them).
    tilt and fp_tilt pitch the blade about the fist in third and first person (Sora's Dagger: angled up, or reversed)."""
    off = (center_y - 8) * gui
    d = round(off * math.sin(math.radians(45)), 2)
    g = round(gui, 3)
    yaw = 90 if flip else -90
    tp_rot, fp_rot = [tilt, yaw, 0], [fp_tilt, yaw, -25 if flip else 25]
    tp_at, fp_at = _held("thirdperson", tp_rot, tp, grip_y), _held("firstperson", fp_rot, fp, grip_y)
    mirror = lambda r: [r[0], -r[1], -r[2]]
    return {
        "thirdperson_righthand": {"rotation": tp_rot, "translation": tp_at, "scale": [tp] * 3},
        "thirdperson_lefthand": {"rotation": mirror(tp_rot), "translation": tp_at, "scale": [tp] * 3},
        "firstperson_righthand": {"rotation": fp_rot, "translation": fp_at, "scale": [fp] * 3},
        "firstperson_lefthand": {"rotation": mirror(fp_rot), "translation": fp_at, "scale": [fp] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [round(tp * 0.6, 3)] * 3},
        "gui": {"rotation": [0, 0, -45], "translation": [-d, -d, 0], "scale": [g] * 3},
        "fixed": {"rotation": [0, 0, -45], "translation": [-d, -d, 0], "scale": [g] * 3},
    }
