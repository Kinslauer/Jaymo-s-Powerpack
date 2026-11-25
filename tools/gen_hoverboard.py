"""Hoverboard textures: the bomb glider's own skin (tools/gen_bomb_glider.py) re-coloured for the future.

The hull's iron is lightened to a pale metal, so a dye tints it (HoverboardRenderer); the burner, its nozzle, the stripe
and the slung sticks become cyan emitters and a power cell, and are copied to a second sheet of their own
(hoverboard_glow.png) that is drawn full bright and untinted. The leather foot straps go dark grip tape. The wings are
not used (the model's body part only). Also the 16x16 item sprite, seen from above.
Run from the repository root: python -I tools/gen_hoverboard.py
"""
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/"
CLEAR = (0, 0, 0, 0)
CYAN = (70, 220, 255)
CYAN_HOT = (200, 250, 255)


def clamp(v):
    return max(0, min(255, int(v)))


def skins():
    src = Image.open(OUT + "entity/bomb_glider.png").convert("RGBA")
    hull = Image.new("RGBA", src.size, CLEAR)
    glow = Image.new("RGBA", src.size, CLEAR)
    for x in range(src.width):
        for y in range(src.height):
            r, g, b, a = src.getpixel((x, y))
            if not a:
                continue
            sat = max(r, g, b) - min(r, g, b)
            if sat <= 25:
                # iron: pale metal, shading kept
                k = 1.45
                hull.putpixel((x, y), (clamp(r * k + 20), clamp(g * k + 20), clamp(b * k + 22), 255))
            elif r > 150:
                # brass, flame, stripe, sticks: an emitter
                lit = CYAN_HOT if r + g > 440 else CYAN
                shade = max(r, g, b) / 255.0
                c = tuple(clamp(v * (0.55 + 0.45 * shade)) for v in lit)
                hull.putpixel((x, y), c + (255,))
                glow.putpixel((x, y), c + (255,))
            else:
                # leather straps, the dark stripe: grip tape
                hull.putpixel((x, y), (46, 48, 54, 255))
    hull.save(OUT + "entity/hoverboard.png")
    glow.save(OUT + "entity/hoverboard_glow.png")


def sprite():
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = lambda x, y, c: img.putpixel((x, y), c + (255,)) if 0 <= x < 16 and 0 <= y < 16 else None
    # the board seen from above, nose up and a little to the right: a pale hull with a dark rim and cyan emitters
    for y in range(2, 14):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (2, 13)
            px(x, y, (88, 92, 104) if edge else (206, 210, 220) if (x + y) % 5 else (186, 190, 202))
    for x in (7, 8):
        px(x, 1, (206, 210, 220))
    for y in (4, 11):
        px(7, y, CYAN)
        px(8, y, CYAN_HOT)
    for x in (6, 9):
        px(x, 8, (46, 48, 54))
    for x in (6, 7, 8, 9):
        px(x, 14, CYAN)
    px(7, 15, CYAN_HOT)
    px(8, 15, CYAN_HOT)
    img.save(OUT + "item/armory/hoverboard.png")


if __name__ == "__main__":
    skins()
    sprite()
