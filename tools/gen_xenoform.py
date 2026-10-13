"""Generates the Xenoform assets: the Xeno-Band item, the Fallen Star block, and the aliens' 64x64 skins
(the vanilla skin layout for the limbs, the free rows under it for each form's extra parts; see
client/power/xenoform/XenoFormModels). Everything is drawn from code (noise and simple shapes); nothing is copied.
Run from the project root:  python tools/gen_xenoform.py
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"
rng = random.Random(2603)


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def save(img, rel):
    path = ROOT / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def shade(color, amount):
    return tuple(max(0, min(255, int(c * amount))) for c in color[:3]) + (255,)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# ---- the band (item, 16x16) ----
BAND = [
    "................",
    "................",
    ".....dddddd.....",
    "....dmmmmmmd....",
    "...dmggggggmd...",
    "...dmg.ll.gmd...",
    "..dmg.lGGl.gmd..",
    "..dmg.lGGl.gmd..",
    "..dmg.lGGl.gmd..",
    "..dmg.lGGl.gmd..",
    "...dmg.ll.gmd...",
    "...dmggggggmd...",
    "....dmmmmmmd....",
    ".....dddddd.....",
    "................",
    "................",
]
BAND_PALETTE = {
    ".": (0, 0, 0, 0),
    "d": (34, 40, 48, 255),      # dark rim
    "m": (120, 132, 148, 255),   # alien metal
    "g": (176, 186, 200, 255),   # bright metal
    "l": (28, 80, 44, 255),      # dial surround
    "G": (92, 240, 122, 255),    # the green dial face
}


def band():
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(BAND):
        for x, ch in enumerate(row):
            img.putpixel((x, y), BAND_PALETTE[ch])
    save(img, "item/xeno_band.png")
    write_json(ROOT / "models" / "item" / "xeno_band.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/xeno_band"}})
    write_json(ROOT / "items" / "xeno_band.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/xeno_band"}})


# ---- the fallen star (block, 16x16) ----
def fallen_star():
    img = Image.new("RGBA", (16, 16))
    base = (38, 34, 46)
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), shade(base, 0.8 + rng.random() * 0.4))
    # veins of green light
    for _ in range(5):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randrange(4, 9)):
            img.putpixel((x % 16, y % 16), lerp((92, 240, 122), (220, 255, 230), rng.random() * 0.5))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1))
    save(img, "block/fallen_star.png")
    write_json(ROOT / "models" / "block" / "fallen_star.json",
               {"parent": "minecraft:block/cube_all", "textures": {"all": "jaymospowers:block/fallen_star"}})
    write_json(ROOT / "blockstates" / "fallen_star.json", {"variants": {"": {"model": "jaymospowers:block/fallen_star"}}})
    write_json(ROOT / "models" / "item" / "fallen_star.json", {"parent": "jaymospowers:block/fallen_star"})
    write_json(ROOT / "items" / "fallen_star.json", {"model": {"type": "minecraft:model", "model": "jaymospowers:item/fallen_star"}})


# ---- the aliens (entity skins, 64x64) ----
def noise_fill(img, color, spread=0.25, box=None):
    x0, y0, x1, y1 = box or (0, 0, 64, 64)
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), shade(color, 1.0 - spread / 2 + rng.random() * spread))


def rect(img, box, color):
    ImageDraw.Draw(img).rectangle([box[0], box[1], box[2] - 1, box[3] - 1], fill=color)


def eyes(img, color, y=11, wide=False):
    # the head's front face is at (8,8)-(16,16)
    if wide:
        rect(img, (9, y, 11, y + 2), color)
        rect(img, (13, y, 15, y + 2), color)
    else:
        rect(img, (10, y, 11, y + 1), color)
        rect(img, (13, y, 14, y + 1), color)


def clear_hat(img):
    rect(img, (32, 0, 64, 16), (0, 0, 0, 0))


def bulwark():
    img = Image.new("RGBA", (64, 64))
    steel = (120, 130, 142)
    noise_fill(img, steel, 0.3)
    # plates: darker seams every few pixels over the body and arms
    for y in range(16, 32, 4):
        rect(img, (16, y, 56, y + 1), shade(steel, 0.6))
    for x in range(16, 56, 6):
        rect(img, (x, 16, x + 1, 32), shade(steel, 0.65))
    # the brow and chest plates (rows 32+): heavier, darker metal
    noise_fill(img, shade(steel, 0.75), 0.2, (0, 32, 26, 38))
    noise_fill(img, shade(steel, 0.7), 0.2, (0, 38, 38, 49))
    # rivets
    for _ in range(40):
        x, y = rng.randrange(0, 64), rng.randrange(16, 49)
        img.putpixel((x, y), shade(steel, 1.35))
    eyes(img, (255, 200, 60, 255), 12, wide=True)
    clear_hat(img)
    save(img, "entity/xenoform_bulwark.png")


def pyre():
    img = Image.new("RGBA", (64, 64))
    rock = (52, 30, 24)
    noise_fill(img, rock, 0.35)
    # glowing cracks of magma over everything
    for _ in range(60):
        x, y = rng.randrange(64), rng.randrange(64)
        heat = rng.random()
        for _ in range(rng.randrange(3, 10)):
            img.putpixel((x % 64, y % 64), lerp((255, 90, 20), (255, 230, 120), heat))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1))
    # the plumes and crest (rows 32+) are flame: yellow hearts, orange edges
    for box in ((0, 32, 14, 43), (14, 32, 34, 37), (0, 44, 12, 53)):
        noise_fill(img, (255, 120, 30), 0.4, box)
        inner = (box[0] + 2, box[1] + 2, box[2] - 2, box[3] - 2)
        if inner[2] > inner[0] and inner[3] > inner[1]:
            noise_fill(img, (255, 220, 110), 0.3, inner)
    # the core: a white-hot square on the chest
    noise_fill(img, (255, 240, 180), 0.15, (12, 44, 24, 50))
    eyes(img, (255, 250, 200, 255), 11)
    clear_hat(img)
    save(img, "entity/xenoform_pyre.png")


def blur():
    img = Image.new("RGBA", (64, 64))
    hide = (70, 130, 170)
    noise_fill(img, hide, 0.25)
    # a paler belly and throat
    noise_fill(img, (150, 200, 220), 0.2, (20, 20, 28, 32))   # body front
    noise_fill(img, (150, 200, 220), 0.2, (8, 13, 16, 16))    # chin
    # stripes down the back and the tail
    for y in range(20, 32, 3):
        rect(img, (32, y, 40, y + 1), shade(hide, 0.6))
    for box in ((0, 32, 22, 45), (22, 32, 36, 38), (0, 45, 22, 56), (22, 45, 42, 55)):
        noise_fill(img, shade(hide, 0.85), 0.25, box)
    for x in range(2, 42, 4):
        rect(img, (x, 45, x + 1, 56), shade(hide, 0.55))
    # fin and crest tips bright
    noise_fill(img, (160, 230, 255), 0.2, (0, 32, 22, 34))
    eyes(img, (255, 240, 120, 255), 11, wide=True)
    clear_hat(img)
    save(img, "entity/xenoform_blur.png")


def prism():
    img = Image.new("RGBA", (64, 64))
    crystal = (150, 100, 210)
    noise_fill(img, crystal, 0.3)
    # facets: diagonal bands of light and shade over everything
    for y in range(64):
        for x in range(64):
            band = ((x + y) // 5) % 3
            if band == 0:
                img.putpixel((x, y), shade(crystal, 1.25))
            elif band == 2:
                img.putpixel((x, y), shade(crystal, 0.7))
    # the crown, side crystals and back (rows 32+) are brighter, almost white at the tips
    for box in ((0, 32, 8, 40), (8, 32, 16, 39), (16, 32, 34, 41), (34, 32, 42, 39)):
        noise_fill(img, (205, 170, 250), 0.25, box)
        rect(img, (box[0], box[1], box[2], box[1] + 2), (240, 225, 255, 255))
    eyes(img, (255, 255, 255, 255), 11, wide=True)
    clear_hat(img)
    save(img, "entity/xenoform_prism.png")


def wisp():
    img = Image.new("RGBA", (64, 64))
    mist = (170, 215, 230)
    noise_fill(img, mist, 0.3)
    # the hood (24,32)-(64,52) darker, the tatters (0,32)-(24,42) ragged: holes along the bottom
    noise_fill(img, (90, 130, 150), 0.3, (24, 32, 64, 52))
    noise_fill(img, (150, 195, 215), 0.3, (0, 32, 24, 42))
    for x in range(0, 24):
        if rng.random() < 0.5:
            rect(img, (x, 40 + rng.randrange(0, 2), x + 1, 42), (0, 0, 0, 0))
    # hollow black eyes in the hood's front (the hood's front face is at (34,42)-(44,52))
    rect(img, (36, 45, 38, 48), (10, 10, 20, 255))
    rect(img, (40, 45, 42, 48), (10, 10, 20, 255))
    eyes(img, (20, 30, 40, 255), 11, wide=True)
    clear_hat(img)
    save(img, "entity/xenoform_wisp.png")


def gremlin():
    img = Image.new("RGBA", (64, 64))
    skin = (120, 180, 70)
    noise_fill(img, skin, 0.3)
    # the big skull (0,32)-(40,52): same green, with huge yellow eyes on its front face (10,42)-(20,52)
    noise_fill(img, shade(skin, 1.05), 0.25, (0, 32, 40, 52))
    rect(img, (11, 44, 14, 48), (250, 230, 80, 255))
    rect(img, (16, 44, 19, 48), (250, 230, 80, 255))
    rect(img, (12, 45, 13, 47), (20, 20, 20, 255))
    rect(img, (17, 45, 18, 47), (20, 20, 20, 255))
    rect(img, (13, 50, 17, 51), (60, 30, 30, 255))  # a grin
    # ears (40,32)-(50,38) pink inside
    noise_fill(img, shade(skin, 0.9), 0.2, (40, 32, 50, 38))
    rect(img, (41, 33, 44, 37), (220, 150, 150, 255))
    clear_hat(img)
    save(img, "entity/xenoform_gremlin.png")


def knell():
    img = Image.new("RGBA", (64, 64))
    sculk = (20, 44, 52)
    noise_fill(img, sculk, 0.35)
    # veins of cyan light threading the whole hide
    for _ in range(50):
        x, y = rng.randrange(64), rng.randrange(64)
        for _ in range(rng.randrange(3, 8)):
            img.putpixel((x % 64, y % 64), lerp((31, 184, 200), (120, 240, 250), rng.random() * 0.6))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1))
    # horns (0,32)-(8,40) bone; ribs (8,32)-(32,42) glow; shoulders (0,42)-(34,50) dark
    noise_fill(img, (200, 190, 170), 0.2, (0, 32, 8, 40))
    noise_fill(img, (40, 200, 215), 0.35, (8, 32, 32, 42))
    for y in range(33, 42, 2):
        rect(img, (10, y, 30, y + 1), (10, 30, 36, 255))
    noise_fill(img, shade(sculk, 0.8), 0.25, (0, 42, 34, 50))
    # no eyes: a smooth, blind face with a faint glow where they'd be
    rect(img, (9, 10, 15, 12), (18, 90, 100, 255))
    clear_hat(img)
    save(img, "entity/xenoform_knell.png")


def wyrm():
    img = Image.new("RGBA", (64, 64))
    scale = (30, 22, 40)
    noise_fill(img, scale, 0.35)
    # scales: a brick pattern of slightly lighter rows
    for y in range(0, 64, 3):
        for x in range((y // 3) % 2 * 2, 64, 4):
            rect(img, (x, y, x + 2, y + 1), shade(scale, 1.3))
    # horns (0,32)-(18,41) pale; tail (18,32)-(44,45) and tip (18,45)-(38,55) like the body; wings (0,50)-(44,59)
    # membrane purple with dark bones
    noise_fill(img, (190, 180, 170), 0.2, (0, 32, 18, 41))
    noise_fill(img, (110, 50, 150), 0.3, (0, 50, 44, 59))
    for x in range(0, 44, 7):
        rect(img, (x, 50, x + 1, 59), (25, 15, 35, 255))
    eyes(img, (200, 90, 240, 255), 11, wide=True)
    clear_hat(img)
    save(img, "entity/xenoform_wyrm.png")


def nadir():
    """The void given shape: near-black with faint stars, a face of deep nothing, and the green xeno insignia on the chest."""
    img = Image.new("RGBA", (64, 64))
    void = (9, 7, 16)
    noise_fill(img, void, 0.2)
    # faint stars all over, a few brighter
    for _ in range(140):
        x, y = rng.randrange(64), rng.randrange(64)
        img.putpixel((x, y), lerp((40, 30, 70), (120, 100, 170), rng.random()))
    for _ in range(18):
        x, y = rng.randrange(64), rng.randrange(64)
        img.putpixel((x, y), (200, 190, 240, 255))
    # the face (8,8)-(16,16): a deeper void, violet at the edges, a single pale point low in it
    for y in range(8, 16):
        for x in range(8, 16):
            d = max(abs(x - 11.5), abs(y - 11.5)) / 3.5
            img.putpixel((x, y), lerp((2, 1, 6), (60, 30, 110), d * d))
    img.putpixel((11, 13), (210, 200, 250, 255))
    img.putpixel((12, 13), (160, 140, 220, 255))
    # the insignia on the chest (front face (20,20)-(28,32)): an hourglass of band green, 5 wide, 7 tall
    green = (92, 240, 122, 255)
    for i, w in enumerate((5, 3, 1, 1, 1, 3, 5)):
        y = 22 + i
        rect(img, (24 - w // 2, y, 24 - w // 2 + w, y + 1), green)
    rect(img, (23, 25, 25, 26), (170, 255, 190, 255))
    # the shoulders (0,32)-(36,42) and the mantle (36,32)-(58,47): the same void, the mantle ragged at its foot
    noise_fill(img, (6, 4, 12), 0.2, (0, 32, 36, 42))
    noise_fill(img, (14, 10, 26), 0.3, (36, 32, 58, 47))
    for x in range(36, 58):
        if rng.random() < 0.45:
            rect(img, (x, 45 + rng.randrange(0, 2), x + 1, 47), (0, 0, 0, 0))
    for _ in range(30):
        x, y = rng.randrange(36, 58), rng.randrange(32, 45)
        img.putpixel((x, y), lerp((40, 30, 70), (140, 120, 200), rng.random()))
    clear_hat(img)
    save(img, "entity/xenoform_nadir.png")


if __name__ == "__main__":
    band()
    fallen_star()
    bulwark()
    pyre()
    blur()
    prism()
    wisp()
    gremlin()
    knell()
    wyrm()
    nadir()
    print("Xenoform assets written under", ROOT)
