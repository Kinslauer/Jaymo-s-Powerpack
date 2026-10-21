"""Generates Cryokinesis' textures (0.14.0). All original, painted as pixel grids in vanilla's style
(docs/Vanilla-Style.md: 16px, no black outline, light from the top-left, a handful of colours from vanilla's ice).
Run from the project root:  python -I tools/gen_cryo.py

  textures/item/cryo_ice_shard.png    the shard Ice Shards' snowballs wear: a sliver of ice on the diagonal
  textures/entity/cryo/lane.png       the Ice Slide's lane: a pale, cracked sheet of ice, drawn flat on the ground
"""
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymospowers"

# vanilla ice's ramp, darkest to lightest (read off ice.png / packed_ice.png)
ICE = [(56, 98, 168), (82, 128, 198), (116, 160, 226), (145, 183, 253), (176, 206, 255), (214, 232, 255), (245, 251, 255)]

# the shard: a long sliver from bottom-left to top-right. Digits index ICE, '.' is empty.
SHARD = [
    "..............36",
    ".............356",
    "............3554",
    "...........35541",
    "..........35541.",
    ".........35541..",
    "........35541...",
    ".......34541....",
    "......34531.....",
    ".....34431......",
    "....24431.......",
    "...2432.........",
    "..2321..........",
    ".1221...........",
    ".011............",
    "..0.............",
]


def shard():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(SHARD):
        for x, c in enumerate(row):
            if c.isdigit():
                img.putpixel((x, y), ICE[int(c)] + (255,))
    return img


def lane():
    """A sheet of ice: mottled pale blues, a few white cracks, mostly see-through (the renderer fades it further)."""
    rng = random.Random(2610)
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            shade = ICE[4 + rng.choice((0, 0, 0, 1, 1, -1))]
            img.putpixel((x, y), shade + (150 + rng.randrange(40),))
    # cracks: short white walks
    for _ in range(7):
        x, y = rng.randrange(size), rng.randrange(size)
        for _ in range(rng.randrange(5, 12)):
            img.putpixel((x % size, y % size), ICE[6] + (230,))
            x += rng.choice((-1, 0, 1))
            y += rng.choice((-1, 0, 1))
    # a soft edge so neighbouring patches blend
    for y in range(size):
        for x in range(size):
            edge = min(x, y, size - 1 - x, size - 1 - y)
            if edge < 4:
                r, g, b, a = img.getpixel((x, y))
                img.putpixel((x, y), (r, g, b, int(a * (edge + 1) / 5)))
    return img


def main():
    item = ASSETS / "textures" / "item"
    entity = ASSETS / "textures" / "entity" / "cryo"
    item.mkdir(parents=True, exist_ok=True)
    entity.mkdir(parents=True, exist_ok=True)
    shard().save(item / "cryo_ice_shard.png")
    lane().save(entity / "lane.png")
    print("wrote", item / "cryo_ice_shard.png")
    print("wrote", entity / "lane.png")


if __name__ == "__main__":
    main()
