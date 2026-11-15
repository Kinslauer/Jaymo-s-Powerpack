"""Draws the item textures, models and item definitions of the Tinkerer's mimic gadgets (tinker/Mimic.java).

Run from the repository root: python3 tools/gen_tinker_mimic.py
Each gadget is brass, iron or copper work with the colour of the ability it copies: 16x16, vanilla-style shading.
"""
import json
import os

from PIL import Image, ImageDraw

K = "kingdoms/src/main/resources/assets/jaymoskingdoms"
BRASS, BRASS_D, BRASS_L = (196, 158, 72), (132, 100, 40), (236, 206, 120)
IRON, IRON_D, IRON_L = (160, 164, 172), (98, 102, 112), (214, 218, 224)
COPPER, COPPER_D, COPPER_L = (192, 110, 72), (126, 66, 42), (232, 156, 112)
LEATHER, LEATHER_D = (128, 82, 50), (84, 52, 30)
WOOD, WOOD_D = (128, 92, 56), (86, 60, 34)
OUT = (34, 28, 26)


def canvas():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def c(rgb, a=255):
    return tuple(rgb) + (a,)


def px(im, pts, rgb):
    for p in pts:
        im.putpixel(p, c(rgb))


def bar(d, box, base, dark, light):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=c(base))
    d.line([(x0, y1), (x1, y1)], fill=c(dark))
    d.line([(x1, y0), (x1, y1)], fill=c(dark))
    d.line([(x0, y0), (x1 - 1, y0)], fill=c(light))


def outline(im):
    """A dark rim round every drawn pixel, as vanilla items have."""
    src = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3] > 0:
                        im.putpixel((x, y), c(OUT))
                        break
    return im


def handle(d, x, y0, y1):
    d.line([(x, y0), (x, y1)], fill=c(WOOD))
    d.line([(x + 1, y0), (x + 1, y1)], fill=c(WOOD_D))


def gun(core, muzzle, body=IRON):
    """A short hand-gadget pointing up and right: a grip, a barrel, a coloured core."""
    im, d = canvas()
    handle(d, 4, 10, 13)
    bar(d, (3, 7, 10, 9), body, shade(body, -60), shade(body, 50))
    bar(d, (9, 4, 12, 7), BRASS, BRASS_D, BRASS_L)
    d.rectangle([6, 6, 7, 7], fill=c(core))
    px(im, [(12, 3), (13, 3), (13, 4)], muzzle)
    return outline(im)


def shade(rgb, n):
    return tuple(max(0, min(255, v + n)) for v in rgb)


def lift_rig():
    im, d = canvas()
    handle(d, 7, 9, 14)
    d.arc([3, 1, 12, 10], 180, 360, fill=c(IRON), width=2)
    d.rectangle([3, 5, 4, 8], fill=c((200, 40, 40)))
    d.rectangle([11, 5, 12, 8], fill=c((60, 90, 200)))
    px(im, [(7, 4), (8, 4)], (170, 120, 230))
    return outline(im)


def bellows():
    im, d = canvas()
    d.polygon([(2, 6), (10, 3), (10, 12), (2, 9)], fill=c(LEATHER))
    for x in (4, 6, 8):
        d.line([(x, 5), (x, 10)], fill=c(LEATHER_D))
    bar(d, (10, 6, 14, 8), BRASS, BRASS_D, BRASS_L)
    px(im, [(1, 7), (1, 8)], WOOD)
    return outline(im)


def grapple():
    im, d = canvas()
    d.ellipse([2, 6, 9, 13], fill=c(IRON))
    d.ellipse([4, 8, 7, 11], fill=c((235, 235, 235)))
    d.line([(8, 7), (12, 3)], fill=c((235, 235, 235)))
    px(im, [(12, 2), (13, 3), (11, 2), (13, 4)], IRON_L)
    return outline(im)


def watch():
    im, d = canvas()
    d.ellipse([3, 3, 12, 12], fill=c(BRASS))
    d.ellipse([4, 4, 11, 11], fill=c((246, 236, 200)))
    d.line([(7, 7), (7, 5)], fill=c(OUT))
    d.line([(7, 7), (9, 8)], fill=c(OUT))
    d.rectangle([7, 1, 8, 2], fill=c(BRASS_D))
    px(im, [(5, 9), (10, 5)], (220, 170, 40))
    return outline(im)


def frost():
    im = gun((120, 210, 255), (220, 245, 255), COPPER)
    px(im, [(14, 2), (12, 1)], (200, 240, 255))
    return im


def smother():
    im, d = canvas()
    d.ellipse([2, 7, 8, 13], fill=c(LEATHER))
    bar(d, (7, 8, 12, 10), IRON, IRON_D, IRON_L)
    px(im, [(13, 7), (14, 6), (13, 5), (12, 4)], (110, 110, 110))
    px(im, [(14, 4), (11, 3)], (150, 150, 150))
    return outline(im)


def horn():
    im, d = canvas()
    d.polygon([(2, 11), (6, 8), (12, 3), (14, 6), (8, 11), (3, 13)], fill=c(COPPER))
    d.line([(12, 3), (14, 6)], fill=c(COPPER_L))
    d.line([(3, 13), (8, 11)], fill=c(COPPER_D))
    px(im, [(6, 10), (9, 8)], (190, 130, 230))
    return outline(im)


def gauntlet():
    im, d = canvas()
    d.rectangle([4, 4, 11, 12], fill=c(IRON))
    for x in (4, 6, 8, 10):
        d.rectangle([x, 2, x + 1, 4], fill=c(IRON_L))
    d.rectangle([4, 11, 11, 13], fill=c(LEATHER))
    d.line([(4, 7), (11, 7)], fill=c((40, 50, 90)))
    d.line([(11, 4), (11, 12)], fill=c(IRON_D))
    return outline(im)


def chain_hook():
    im, d = canvas()
    for i, (x, y) in enumerate([(2, 12), (4, 10), (6, 8), (8, 6)]):
        d.rectangle([x, y, x + 1, y + 1], fill=c(IRON if i % 2 else IRON_D))
    d.arc([8, 1, 14, 7], 0, 270, fill=c(IRON_L), width=2)
    px(im, [(9, 5)], IRON_L)
    return outline(im)


def band():
    im, d = canvas()
    d.ellipse([2, 4, 13, 12], fill=c(BRASS))
    d.ellipse([4, 6, 11, 10], fill=(0, 0, 0, 0))
    d.rectangle([6, 3, 9, 6], fill=c((60, 220, 90)))
    px(im, [(7, 4)], (190, 255, 200))
    return outline(im)


def ki():
    return gun((255, 214, 80), (255, 250, 200), COPPER)


def blink():
    im, d = canvas()
    for y in range(3, 13, 2):
        d.line([(4, y), (11, y)], fill=c(COPPER))
        d.line([(4, y + 1), (11, y + 1)], fill=c(COPPER_D))
    d.rectangle([6, 6, 9, 9], fill=c((120, 30, 160)))
    px(im, [(7, 7)], (220, 120, 255))
    return outline(im)


def claws():
    im, d = canvas()
    d.rectangle([3, 8, 10, 13], fill=c(LEATHER))
    for x in (3, 6, 9):
        d.line([(x, 8), (x + 2, 3)], fill=c(IRON_L))
        d.line([(x + 1, 8), (x + 3, 4)], fill=c(IRON_D))
    return outline(im)


def spitter():
    return gun((255, 140, 30), (255, 220, 80))


def cannon():
    im, d = canvas()
    d.polygon([(2, 12), (9, 5), (12, 8), (5, 14)], fill=c(IRON))
    d.line([(9, 5), (12, 8)], fill=c(IRON_L))
    d.ellipse([10, 3, 14, 7], fill=c(IRON_D))
    d.ellipse([11, 4, 13, 6], fill=c((255, 120, 30)))
    d.rectangle([2, 12, 4, 14], fill=c(LEATHER))
    return outline(im)


def lens():
    im, d = canvas()
    handle(d, 4, 10, 14)
    d.ellipse([4, 2, 13, 11], fill=c(COPPER))
    d.ellipse([6, 4, 11, 9], fill=c((110, 220, 200)))
    px(im, [(7, 5)], (220, 255, 245))
    return outline(im)


def rake():
    im, d = canvas()
    d.line([(3, 13), (10, 6)], fill=c(WOOD))
    d.line([(4, 13), (11, 6)], fill=c(WOOD_D))
    bar(d, (8, 2, 14, 4), IRON, IRON_D, IRON_L)
    for x in (8, 10, 12, 14):
        px(im, [(x, 5), (x, 6)], (236, 232, 214))
    return outline(im)


def charge():
    im, d = canvas()
    d.ellipse([3, 5, 12, 14], fill=c((90, 160, 70)))
    d.ellipse([4, 6, 9, 10], fill=c((130, 200, 100)))
    d.rectangle([7, 2, 8, 5], fill=c((220, 210, 180)))
    px(im, [(9, 1), (8, 1)], (255, 160, 40))
    px(im, [(6, 9), (9, 9), (7, 11), (8, 11)], (30, 50, 20))
    return outline(im)


def sling():
    im, d = canvas()
    d.line([(3, 13), (7, 8)], fill=c(WOOD))
    d.line([(7, 8), (5, 3)], fill=c(WOOD))
    d.line([(7, 8), (11, 4)], fill=c(WOOD))
    d.line([(5, 3), (9, 6)], fill=c(LEATHER))
    d.line([(11, 4), (9, 6)], fill=c(LEATHER))
    d.ellipse([8, 5, 11, 8], fill=c((180, 60, 200)))
    return outline(im)


ICONS = {
    "lift_rig": lift_rig, "gust_bellows": bellows, "grapple_spinner": grapple, "rewind_watch": watch, "frost_projector": frost,
    "smother_pump": smother, "sonic_horn": horn, "parry_gauntlet": gauntlet, "chain_hook": chain_hook, "growth_band": band,
    "ki_blaster": ki, "blink_coil": blink, "climbing_claws": claws, "flame_spitter": spitter, "bellows_cannon": cannon,
    "beam_lens": lens, "jaw_rake": rake, "hissing_charge": charge, "potion_sling": sling,
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    os.makedirs(f"{K}/textures/item", exist_ok=True)
    for name, draw in ICONS.items():
        draw().save(f"{K}/textures/item/{name}.png")
        write(f"{K}/models/item/{name}.json", {"parent": "minecraft:item/handheld" if name in ("jaw_rake", "lift_rig") else "minecraft:item/generated",
                                               "textures": {"layer0": f"jaymoskingdoms:item/{name}"}})
        write(f"{K}/items/{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}})
    print(f"drew {len(ICONS)} mimic gadgets")
