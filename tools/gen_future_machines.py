"""Our own skins for the future's machines (Chrono's Los Perrito future): three androids and the war mech.

Androids are 64x64 on the humanoid layout (head, hat, body, right arm, right leg; the left limbs mirror). The mech is
128x128 on the iron golem's layout, drawn as a gunmetal walker. Each has an _eyes texture that glows in the dark.

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


def mech():
    r = random.Random(0x3EC4)
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    eyes = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    metal = (78, 84, 92)
    dark = (40, 42, 46)
    hazard = (210, 170, 40)
    rust = (120, 70, 40)
    glow = (255, 60, 30)

    def plate(face, x, y, fw, fh, base=metal):
        c = shade(base, 0.9 + r.random() * 0.15)
        if face in ("left", "right"):
            c = shade(c, 0.85)
        elif face == "back":
            c = shade(c, 0.9)
        if r.random() < 0.04:
            c = rust
        if (x == 0 or x == fw - 1 or y in (0, fh - 1)) and face not in ("top", "bottom"):
            c = shade(c, 0.7)                            # plate edges
        return c

    def head(face, x, y, fw, fh):
        c = plate(face, x, y, fw, fh)
        if face == "front" and 4 <= y <= 5 and 1 <= x <= 6:
            return (60, 14, 10)                          # visor slit
        if face == "front" and y >= 8:
            return dark                                  # jaw grille
        return c

    def nose(face, x, y, fw, fh):
        return shade(dark, 1.2) if face != "front" else (20, 20, 22)  # a sensor barrel

    def body(face, x, y, fw, fh):
        c = plate(face, x, y, fw, fh)
        if face == "front":
            if 3 <= y <= 7 and 6 <= x <= 11:
                return dark if (y + x) % 2 else shade(dark, 1.3)   # reactor vent
            if 5 == y and x in (8, 9):
                return (180, 40, 20)
            if y == 1 and (x < 4 or x > 13):
                return hazard if (x // 2) % 2 == 0 else dark
            if y == 9 and x in (2, 3, 14, 15):
                return (230, 230, 230)                   # stencil
        if face == "top" and (x < 4 or x >= fw - 4):
            return hazard if ((x + y) // 2) % 2 == 0 else dark
        return c

    def waist(face, x, y, fw, fh):
        return plate(face, x, y, fw, fh, dark)

    def arm(face, x, y, fw, fh):
        c = plate(face, x, y, fw, fh)
        if face == "bottom":
            return (12, 12, 14) if 1 <= x <= 2 else dark  # the muzzle
        if y >= 22:
            return shade(dark, 1.1) if y % 3 else (24, 24, 26)  # cannon barrel
        if y in (2, 3) and face == "front":
            return hazard if x % 2 == 0 else dark
        if y == 12:
            return dark                                  # elbow
        return c

    def leg(face, x, y, fw, fh):
        c = plate(face, x, y, fw, fh)
        if y in (6, 7):
            return dark                                  # knee
        if y >= 13:
            return shade(dark, 0.9)                      # foot
        if face == "front" and y == 2:
            return hazard if x % 2 == 0 else dark
        return c

    box(img, 0, 0, 8, 10, 8, head)
    box(img, 24, 0, 2, 4, 2, nose)
    box(img, 0, 40, 18, 12, 11, body)
    box(img, 0, 70, 9, 5, 6, waist)
    box(img, 60, 21, 4, 30, 6, arm)
    box(img, 60, 58, 4, 30, 6, arm)
    box(img, 37, 0, 6, 16, 5, leg)
    box(img, 60, 0, 6, 16, 5, leg)
    # glow: the visor and the reactor
    fx, fy = 8, 8
    for x in range(1, 7):
        for y in (4, 5):
            eyes.putpixel((fx + x, fy + y), glow + (255,))
    bx, by = 11, 51
    for x in (8, 9):
        eyes.putpixel((bx + x, by + 5), (255, 120, 40, 255))
    # grime
    for _ in range(900):
        x, y = r.randrange(128), r.randrange(128)
        c = img.getpixel((x, y))
        if c[3]:
            img.putpixel((x, y), shade(c[:3], 0.8) + (255,))
    return img, eyes


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(len(ANDROIDS)):
        name, img, eyes = android(i)
        img.save(os.path.join(OUT, "android_%s.png" % name))
        eyes.save(os.path.join(OUT, "android_%s_eyes.png" % name))
    img, eyes = mech()
    img.save(os.path.join(OUT, "war_mech.png"))
    eyes.save(os.path.join(OUT, "war_mech_eyes.png"))
    print("wrote the machines to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
