"""Our own skins for the future's machines (Chrono's Los Perrito future): three androids. (The mechs have their own
model and skins now: tools/gen_android_mech.py.)

Androids are 64x64 on the humanoid layout (head, hat, body, right arm, right leg; the left limbs mirror). Each has an
_eyes texture that glows in the dark.

    python3 tools/gen_future_machines.py
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "textures",
                   "entity", "future")


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c)


def box(img, u, v, w, h, d, colour):
    for name, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        for i in range(fw):
            for j in range(fh):
                y = -1 if name == "top" else h if name == "bottom" else j
                c = colour(name, i, y, fw, fh)
                if c is not None:
                    img.putpixel((fx + i, fy + j), tuple(c[:3]) + ((c[3],) if len(c) > 3 else (255,)))


ANDROIDS = [
    # name, shell, joint, eye glow
    ("synthetic", (226, 228, 232), (40, 42, 48), (80, 200, 255)),
    ("security", (34, 36, 42), (90, 94, 100), (255, 50, 40)),
    ("torn", (196, 160, 130), (150, 156, 166), (255, 170, 40)),   # synthetic skin over a chrome frame
]


def android(index):
    name, shell, joint, eye = ANDROIDS[index]
    r = random.Random(0xA11D + index)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    chrome = (170, 176, 186)

    def surface(base, face):
        c = shade(base, 0.92 + r.random() * 0.12)
        return shade(c, 0.88) if face in ("left", "right") else shade(c, 0.94) if face == "back" else c

    def torn(c):
        # the torn one shows its frame through rents in the skin and clothes
        return chrome if name == "torn" and r.random() < 0.18 else c

    def head(face, x, y, fw, fh):
        c = surface(shell, face)
        if face == "front":
            if y == 4 and x in (1, 2, 5, 6):
                return (20, 20, 24)
            if name == "security" and y == 4 and 1 <= x <= 6:
                return (60, 14, 14)                      # visor band
            if y == 6 and 2 <= x <= 5:
                return shade(shell, 0.7)                 # mouth seam
            if name == "torn" and x >= 5 and 2 <= y <= 6:
                return shade(chrome, 0.9 + r.random() * 0.15)
        if face in ("left", "right") and y == 4 and x in (3, 4):
            return joint                                 # ear port
        if face == "top" and name != "torn":
            return shade(c, 0.95)
        if name == "torn" and face == "top":
            return (50, 40, 32)                          # hair
        if name == "torn" and face != "bottom" and y <= 1:
            return (50, 40, 32)
        return torn(c)

    def body(face, x, y, fw, fh):
        if name == "torn":
            c = surface((70, 80, 96), face)              # a torn jacket
            if y >= 10:
                c = surface((40, 44, 56), face)
            return torn(c)
        c = surface(shell, face)
        if face == "front":
            if y == 3 and 2 <= x <= 5:
                return eye if name == "synthetic" else (200, 30, 30)  # chest light
            if x in (3, 4) and y >= 5 and y % 2 == 0:
                return shade(joint, 1.1)                 # spine plates seen through
            if y == 9:
                return joint
            if name == "security" and y == 1 and x in (1, 2):
                return (200, 180, 60)                    # badge
        if y in (0, 11) or (face in ("left", "right") and x == 1):
            return joint
        return c

    def arm(face, x, y, fw, fh):
        c = surface(shell if name != "torn" else (70, 80, 96), face)
        if name == "torn" and y >= 6:
            c = surface((196, 160, 130), face)
        if y in (3, 4) and name != "torn":
            return joint                                 # elbow
        if y >= 10 or face == "bottom":
            return shade(joint, 1.2) if name != "torn" else surface((196, 160, 130), face)
        return torn(c)

    def leg(face, x, y, fw, fh):
        c = surface(shell if name != "torn" else (40, 44, 56), face)
        if y in (5, 6) and name != "torn":
            return joint                                 # knee
        if y >= 11 or face == "bottom":
            return shade(joint, 0.8)
        return torn(c)

    box(img, 0, 0, 8, 8, 8, head)
    box(img, 16, 16, 8, 12, 4, body)
    box(img, 40, 16, 4, 12, 4, arm)
    box(img, 0, 16, 4, 12, 4, leg)
    # eyes: the head's front, at the sockets (a visor band for security)
    for x in range(8):
        for y in range(8):
            lit = (y == 4 and x in (2, 5)) or (name == "security" and y == 4 and 1 <= x <= 6)
            if lit:
                eyes.putpixel((8 + x, 8 + y), eye + (255,))
                img.putpixel((8 + x, 8 + y), shade(eye, 0.6) + (255,))
    for x in range(8):
        for y in range(12):
            if y == 3 and 2 <= x <= 5 and name != "torn":
                eyes.putpixel((20 + x, 20 + y), (eye if name == "synthetic" else (255, 40, 30)) + (255,))
    return name, img, eyes


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(len(ANDROIDS)):
        name, img, eyes = android(i)
        img.save(os.path.join(OUT, "android_%s.png" % name))
        eyes.save(os.path.join(OUT, "android_%s_eyes.png" % name))
    print("wrote the machines to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
