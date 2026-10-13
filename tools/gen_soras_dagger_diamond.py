"""Sora's Dagger, diamond stage (0.8.115): the gilded dagger with diamonds set along its edge, not a diamond blade.

Reads the gilded and the old all-diamond textures (the blade is where they differ) and writes the gilded art with the
blade's inner edge in the diamond colours. Run from the repo root: python3 -I tools/gen_soras_dagger_diamond.py <old diamond png>
"""
import os
import sys

from PIL import Image

ITEM = os.path.join("src", "main", "resources", "assets", "jaymospowers", "textures", "item")


def main():
    gold = Image.open(os.path.join(ITEM, "soras_dagger_gold.png")).convert("RGBA")
    diamond = Image.open(sys.argv[1]).convert("RGBA")
    w, h = gold.size
    blade = {(x, y) for x in range(w) for y in range(h) if gold.getpixel((x, y))[3] and gold.getpixel((x, y)) != diamond.getpixel((x, y))}
    def bare(x, y):
        return not (0 <= x < w and 0 <= y < h) or gold.getpixel((x, y))[3] == 0

    near = ((1, 0), (-1, 0), (0, 1), (0, -1))
    outline = {(x, y) for x, y in blade if any(bare(x + dx, y + dy) for dx, dy in near)}
    # the ring just inside the blade's dark outline: the set stones, in the old blade's bright diamond colours
    inlay = {(x, y) for x, y in blade - outline if any((x + dx, y + dy) in outline for dx, dy in near)}
    out = gold.copy()
    for x, y in inlay:
        out.putpixel((x, y), diamond.getpixel((x, y)))
    out.save(os.path.join(ITEM, "soras_dagger_diamond.png"))


if __name__ == "__main__":
    main()
