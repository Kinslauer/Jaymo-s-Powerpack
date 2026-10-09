"""Generates Sora's Dagger, after a curved brass-hilted dagger: a 32x32 sprite per stage (a curving, fullered blade, a
C-guard whose tips curl toward the blade, a bent spiral-grooved grip and a scrolled pommel), their item models and item
definitions. Run from the project root.

Each pixel is classed by where it falls along the dagger (u, from the guard toward the tip) and across it (v, toward
the edge), so the shapes stay crisp at this size."""
from PIL import Image
import json, math

root = "src/main/resources/assets/jaymospowers"
SIZE = 32
ORIGIN = (12.0, 19.0)  # where the blade meets the guard
DIR = (1 / math.sqrt(2), -1 / math.sqrt(2))  # along the blade: up and right
PERP = (1 / math.sqrt(2), 1 / math.sqrt(2))  # across it, toward the edge: down and right
BLADE = 25.5
GRIP = 9.0
GRIP_BEND = 0.05  # how far the grip curves down toward the edge side (per u²)


def local(x, y):
    dx, dy = x + 0.5 - ORIGIN[0], y + 0.5 - ORIGIN[1]
    return dx * DIR[0] + dy * DIR[1], dx * PERP[0] + dy * PERP[1]


def blade_center(u):
    # the blade sweeps back: the tip curls up, away from the edge
    return -0.0062 * u * u


def classify(u, v):
    # blade
    if 0.3 <= u <= BLADE:
        t = u / BLADE
        taper = max(0.0, 1.0 - t ** 3)
        c = blade_center(u)
        spine = c - 2.4 * taper
        edge = c + (2.6 + 1.5 * math.sin(math.pi * min(1.0, t * 1.15))) * taper
        if t > 0.86:
            # the clipped point: the edge sweeps up to meet the back
            edge = min(edge, spine + (1.0 - t) / 0.14 * 4.0)
        if spine <= v <= edge:
            if t > 0.93:
                return "T"
            if v - spine < 1.0:
                return "B"
            if edge - v < 1.15:
                return "E"
            if 1.5 <= u <= BLADE * 0.62 and abs(v - (c - 0.4)) < 0.55:
                return "F"
            return "b"
    # the guard: a bar across, its tips curling toward the blade
    for k in range(-52, 53):
        tv = k / 10.0
        gu = 0.05 * tv * tv - 0.9
        if math.hypot(u - gu, v - tv) < 0.95:
            return "g" if u < gu - 0.2 else "G"
    for side in (-1, 1):
        # the tips hook inward, toward the blade
        if math.hypot(u - (0.05 * 27 - 0.9 + 1.2), v - side * 4.6) < 1.05:
            return "G"
    # the grip, bending down toward the edge side, with a spiral groove
    if -GRIP <= u < -1.2:
        d = -1.2 - u
        gv = GRIP_BEND * d * d
        if abs(v - gv) < 1.7:
            groove = (d * 0.62 + (v - gv) * 0.55) % 1.6
            return "h" if groove < 0.45 else "H"
    # the scrolled pommel at its end
    d = GRIP + 0.9
    gv = GRIP_BEND * (d - 1.2) ** 2 + 0.6
    r = math.hypot(u + d + 0.3, v - gv)
    if r < 2.3:
        return "p" if 0.7 < r < 1.3 and (u + d + 0.3) > -0.4 else "P"
    return None


STAGES = {
    # blade: B (back), b, F (fuller), E (edge), T (tip); hilt: G, g, H, h (groove), P, p
    "soras_dagger": dict(blade=(0xB9BEC4, 0xD4D8DC, 0x9AA0A6, 0xF2F4F6, 0xE6E9EC),
                         hilt=(0x9DA2A8, 0x666B71, 0x6B4A2E, 0x452D1A, 0x9DA2A8, 0x5C6167)),
    "soras_dagger_gold": dict(blade=(0xB9BEC4, 0xD4D8DC, 0x9AA0A6, 0xF2F4F6, 0xE6E9EC),
                              hilt=(0xD9A942, 0x9C6E1E, 0xC9952F, 0x7E5714, 0xD9A942, 0x8A5F18)),
    "soras_dagger_diamond": dict(blade=(0x3FBFB3, 0x6EE6D9, 0x2A9C93, 0xD8FFFA, 0xBFFCF5),
                                 hilt=(0xD9A942, 0x9C6E1E, 0xC9952F, 0x7E5714, 0xD9A942, 0x8A5F18)),
    "soras_dagger_tempest": dict(blade=(0x9FD3EC, 0xD4F0FC, 0x7FBCE0, 0xFFFFFF, 0xF2FBFF),
                                 hilt=(0xF0C65A, 0xA4741F, 0xD4A23A, 0x82581A, 0x7FE3F0, 0x2F9DB0),
                                 wisps=0xCFF3FF),
}


def rgb(c):
    return (c >> 16 & 255, c >> 8 & 255, c & 255)


def wisps(img, color):
    # curls of wind trailing off the blade
    for cx, cy, r, a0, a1 in ((13, 9, 4.5, 200, 330), (21, 4, 3.5, 190, 320), (24, 14, 3.0, 20, 150)):
        for a in range(a0, a1, 6):
            x = int(round(cx + r * math.cos(math.radians(a))))
            y = int(round(cy + r * math.sin(math.radians(a))))
            if 0 <= x < SIZE and 0 <= y < SIZE and img.getpixel((x, y))[3] == 0:
                img.putpixel((x, y), (*rgb(color), 150))


for name, stage in STAGES.items():
    B, b, F, E, T = stage["blade"]
    G, g, H, h, P, p = stage["hilt"]
    colors = {"B": B, "b": b, "F": F, "E": E, "T": T, "G": G, "g": g, "H": H, "h": h, "P": P, "p": p}
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            part = classify(*local(x, y))
            if part:
                img.putpixel((x, y), (*rgb(colors[part]), 255))
    # a dark outline, the shade of what it borders (as vanilla's tools have)
    outline = img.copy()
    for y in range(SIZE):
        for x in range(SIZE):
            if img.getpixel((x, y))[3]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < SIZE and 0 <= ny < SIZE and img.getpixel((nx, ny))[3] == 255:
                    r, gg, bb, _ = img.getpixel((nx, ny))
                    outline.putpixel((x, y), (int(r * 0.32), int(gg * 0.32), int(bb * 0.36), 255))
                    break
    img = outline
    if "wisps" in stage:
        wisps(img, stage["wisps"])
    img.save(f"{root}/textures/item/{name}.png")
    json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"jaymospowers:item/{name}"}},
              open(f"{root}/models/item/{name}.json", "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{name}"}},
              open(f"{root}/items/{name}.json", "w"), indent=2)
