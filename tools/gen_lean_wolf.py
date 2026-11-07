"""Paints the lean wolf's coats (Kingdoms 0.7.574): a werewolf player's survivalist form, LeanWolfModel. Our own art,
drawn from nothing but noise and the model's boxes: one coat for each of the werewolf's coats (black, brown, timber)
and three flickering frames of the fiery one.

    python -I tools/gen_lean_wolf.py

Part boxes are read from LeanWolfModel.createBodyLayer (the textures are twice its 64 x 64 texture size).
"""
import os
import re

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KINGDOMS = os.path.join(ROOT, "kingdoms", "src")
MODEL = os.path.join(KINGDOMS, "client", "java", "com", "jaymoskingdoms", "client", "monsters", "LeanWolfModel.java")
TEX = os.path.join(KINGDOMS, "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "legend")
SCALE = 2
SIZE = 64 * SCALE

# coat: (back, flank, belly) RGB
COATS = {
    "black": ((14, 14, 17), (32, 31, 36), (62, 58, 60)),
    "brown": ((40, 25, 13), (78, 50, 28), (138, 104, 70)),
    "timber": ((92, 90, 88), (152, 148, 144), (214, 210, 204)),
    "fire": ((14, 4, 3), (36, 10, 6), (70, 22, 10)),
}
EYE = (232, 180, 40)
NOSE = (12, 10, 10)


def parts():
    """name -> (u, v, w, h, d) in texture pixels."""
    src = open(MODEL, encoding="utf-8").read()
    out = {}
    for m in re.finditer(r'addOrReplaceChild\("(\w+)", CubeListBuilder\.create\(\)\.texOffs\((\d+), (\d+)\)\.addBox\([^,]+,[^,]+,[^,]+, ([\d.]+)F, ([\d.]+)F, ([\d.]+)F\)', src):
        name = m.group(1)
        u, v = int(m.group(2)), int(m.group(3))
        w, h, d = (int(float(x)) for x in m.groups()[3:])
        out[name] = tuple(x * SCALE for x in (u, v, w, h, d))
    return out


def faces(part):
    """face name -> (x0, y0, x1, y1) of a box's texture (the usual layout)."""
    u, v, w, h, d = part
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def fur(rng, shape):
    """Streaky fur: noise stretched along the hair's fall (down the image)."""
    n = rng.random((shape[0] // 3 + 2, shape[1] + 2))
    n = np.repeat(n, 3, axis=0)[: shape[0], : shape[1]]
    return 0.75 * n + 0.25 * rng.random(shape)


def paint(coat, seed, frame=0):
    rng = np.random.default_rng(seed)
    back, flank, belly = (np.array(c, float) for c in COATS[coat])
    img = np.zeros((SIZE, SIZE, 4), float)
    for name, part in parts().items():
        for face, (x0, y0, x1, y1) in faces(part).items():
            hgt, wid = y1 - y0, x1 - x0
            if hgt <= 0 or wid <= 0:
                continue
            # shade: the back dark, the sides fading to the pale belly downwards
            t = np.linspace(0.0, 1.0, hgt)[:, None, None]
            if face == "top":
                base = np.broadcast_to(back, (hgt, wid, 3)).copy()
            elif face == "bottom":
                base = np.broadcast_to(belly, (hgt, wid, 3)).copy()
            else:
                base = back * (1 - t) * 0.5 + flank * 0.5 + (belly - flank) * np.clip(t - 0.45, 0, 1) * 0.9
                base = np.broadcast_to(base, (hgt, wid, 3)).copy()
            if name.startswith("leg") and face not in ("top", "bottom"):
                # (dark socks to the paws)
                base[int(hgt * 0.72):] = back * 0.8
            if name == "ruff":
                base = base * 1.08
            noise = fur(rng, (hgt, wid))[:, :, None]
            col = base * (0.72 + 0.56 * noise)
            if coat == "fire":
                # embers glowing through the fur, shifting by frame
                glow = rng.random((hgt, wid)) ** (10 - frame)
                ember = np.array([255, 120 + 40 * frame, 30], float)
                col = col + glow[:, :, None] * ember * 0.9
            img[y0:y1, x0:x1, :3] = col
            img[y0:y1, x0:x1, 3] = 255
    p = parts()
    # the head's front: two yellow eyes; the snout's front: a black nose; the tail's tip: dark
    hx0, hy0, hx1, hy1 = faces(p["head"])["front"]
    s = SCALE
    for ex in (hx0 + 1 * s, hx1 - 3 * s):
        img[hy0 + 2 * s: hy0 + 3 * s, ex: ex + 2 * s, :3] = EYE
        img[hy0 + 2 * s: hy0 + 3 * s, ex + (s if ex < (hx0 + hx1) // 2 else 0): ex + (s if ex < (hx0 + hx1) // 2 else 0) + s // 2 + 1, :3] = NOSE
    sx0, sy0, sx1, sy1 = faces(p["snout"])["front"]
    img[sy0: sy0 + 2 * s, sx0 + s: sx1 - s, :3] = NOSE
    for face in ("front", "back", "left", "right"):
        tx0, ty0, tx1, ty1 = faces(p["tail"])[face]
        img[ty1 - 3 * s: ty1, tx0:tx1, :3] *= 0.45
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def main():
    for i, coat in enumerate(["black", "brown", "timber"]):
        paint(coat, 1000 + i).save(os.path.join(TEX, f"werewolf_lean_{coat}.png"))
    for f in range(3):
        paint("fire", 2000, f).save(os.path.join(TEX, f"werewolf_lean_fire_{f + 1}.png"))
    print("painted", len(parts()), "boxes")


if __name__ == "__main__":
    main()
