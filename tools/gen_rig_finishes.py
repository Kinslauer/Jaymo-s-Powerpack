"""Writes the Rig's finishes (our own art, drawn here): a painted layer over the suit's plates for each finish a wearer
can earn (`rig/RigFinish`).

    python -I tools/gen_rig_finishes.py

Each is `textures/entity/rig/finish_<name>.png`, on the suit's UVs: only pixels that are plate in `suit.png` (and neither a
light in `suit_glow.png` nor trim in `suit_trim.png`) are painted, so it fits every frame. Run `tools/gen_rig.py` and
`tools/gen_rig_frames.py` first if the suit's plates change.
"""
import os
import random

from PIL import Image

RIG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymospowers", "textures", "entity",
    "rig")


def plates():
    suit = Image.open(os.path.join(RIG, "suit.png")).convert("RGBA")
    glow = Image.open(os.path.join(RIG, "suit_glow.png")).convert("RGBA")
    trim = Image.open(os.path.join(RIG, "suit_trim.png")).convert("RGBA")
    w, h = suit.size
    mask = {(x, y) for x in range(w) for y in range(h)
        if suit.getpixel((x, y))[3] > 0 and glow.getpixel((x, y))[3] == 0 and trim.getpixel((x, y))[3] == 0}
    return w, h, mask


def save(name, w, h, pixels):
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for (x, y), rgb in pixels.items():
        image.putpixel((x, y), rgb + (255,))
    image.save(os.path.join(RIG, "finish_" + name + ".png"))


def deepwalker(w, h, mask):
    """Veins of light, branching across the plates (drawn lit)."""
    rng = random.Random(7101)
    pixels = {}
    cells = sorted(mask)
    for _ in range(len(cells) // 55):
        x, y = rng.choice(cells)
        dx, dy = rng.choice([(1, 0), (-1, 0), (0, 1), (0, -1)])
        for step in range(rng.randint(5, 13)):
            if (x, y) not in mask:
                break
            pixels[(x, y)] = (0x29, 0xDF, 0xEB) if step % 4 else (0xB8, 0xFF, 0xF6)
            if rng.random() < 0.35:
                dx, dy = (dy, dx) if rng.random() < 0.5 else (-dy, -dx)
            x, y = x + dx, y + dy
    for (x, y) in list(pixels):
        # a dim edge beside each vein
        for n in ((x + 1, y), (x, y + 1)):
            if n in mask and n not in pixels and rng.random() < 0.3:
                pixels[n] = (0x0A, 0x5F, 0x6C)
    return pixels


def skybreaker(w, h, mask):
    """Swept bands of pale sky and white, as if the air had polished them on."""
    pixels = {}
    for (x, y) in mask:
        band = (x + 2 * y) % 14
        if band < 1:
            pixels[(x, y)] = (0xF4, 0xFA, 0xFF)
        elif band < 3:
            pixels[(x, y)] = (0x9C, 0xC8, 0xF2)
    return pixels


def last_stand(w, h, mask):
    """Soot and scorching in blotches, embers still lit at their edges."""
    rng = random.Random(4402)
    grid = {(gx, gy): rng.random() for gx in range(w // 4 + 2) for gy in range(h // 4 + 2)}

    def noise(x, y):
        gx, gy, fx, fy = x // 4, y // 4, (x % 4) / 4.0, (y % 4) / 4.0
        top = grid[(gx, gy)] * (1 - fx) + grid[(gx + 1, gy)] * fx
        bottom = grid[(gx, gy + 1)] * (1 - fx) + grid[(gx + 1, gy + 1)] * fx
        return top * (1 - fy) + bottom * fy

    pixels = {}
    for (x, y) in mask:
        n = noise(x, y)
        if n > 0.62:
            pixels[(x, y)] = (0x1B, 0x1A, 0x1C) if rng.random() < 0.7 else (0x2C, 0x26, 0x23)
        elif n > 0.57:
            pixels[(x, y)] = (0xE8, 0x66, 0x2A) if rng.random() < 0.22 else (0x3A, 0x30, 0x2B)
    return pixels


def main():
    w, h, mask = plates()
    for name, paint in (("deepwalker", deepwalker), ("skybreaker", skybreaker), ("last_stand", last_stand)):
        pixels = paint(w, h, mask)
        save(name, w, h, pixels)
        print(name, len(pixels), "of", len(mask))


if __name__ == "__main__":
    main()
