#!/usr/bin/env python3
"""Draws the tundra's snow mote frames (Powers, client.tundra.SnowMote) and the particle's sprite list.

Four 8x8 flecks, white with a cold blue edge: a pinprick, a soft dot, a small star and a flake of three arms, so a drift
of them doesn't read as one stamped shape. Nothing of the game's snowflake is copied; every pixel is set here.
Run from the repository root: python3 tools/snow_mote_assets.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymospowers")
WHITE = (250, 252, 255)
EDGE = (196, 214, 236)

# '#' white, '+' cold edge, '.' clear
FRAMES = {
    "snow_mote_0": ("........", "........", "........", "...+....", "..+#+...", "...+....", "........", "........"),
    "snow_mote_1": ("........", "........", "..+++...", ".+###+..", ".+###+..", "..+++...", "........", "........"),
    "snow_mote_2": ("........", "...+....", "...#....", ".+###+..", "...#....", "...+....", "........", "........"),
    "snow_mote_3": ("........", ".+...+..", "..+#+...", "...#....", "..+#+...", ".+...+..", "........", "........"),
}


def main():
    folder = os.path.join(ROOT, "textures", "particle")
    os.makedirs(folder, exist_ok=True)
    for name, rows in FRAMES.items():
        im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch == "#":
                    im.putpixel((x, y), WHITE + (255,))
                elif ch == "+":
                    im.putpixel((x, y), EDGE + (210,))
        im.save(os.path.join(folder, name + ".png"))
    sprites = os.path.join(ROOT, "particles")
    os.makedirs(sprites, exist_ok=True)
    with open(os.path.join(sprites, "snow_mote.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"textures": ["jaymospowers:" + name for name in FRAMES]}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
