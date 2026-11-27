#!/usr/bin/env python3
"""Draws the skill-bar icons of a vampire's shapes and claw moves (kingdoms/src/main/resources/assets/jaymoskingdoms/textures/spell/night_*.png).

Same 16x16 tile and palette as the Blood Dash, Groundpound, Blood Gaze and Feed icons: a dark blood-black ground, two reds
and bone white. Run from the repo root: python3 tools/gen_night_bar_icons.py
"""
from pathlib import Path

from PIL import Image

OUT = Path("kingdoms/src/main/resources/assets/jaymoskingdoms/textures/spell")
PALETTE = {
    ".": (34, 10, 14, 255),
    "r": (140, 20, 30, 255),
    "R": (190, 10, 20, 255),
    "w": (240, 236, 230, 255),
}

ICONS = {
    # a bat, wings spread
    "night_bat": [
        "................",
        "..r..........r..",
        ".rrr...r..r.rrr.",
        "rrrrr.rrrrrrrrrr",
        "rrrrrrrrRRrrrrrr",
        "rr.rrrrRRRRrrr.r",
        "r...rrrrwwrrr..r",
        "....rrrrwwrr....",
        ".....rrrrrr.....",
        "......rrrr......",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    # three small bats round one point
    "night_colony": [
        "................",
        "...r.r..........",
        "..rrrrr.....r.r.",
        "...rRr.....rrrrr",
        "..............Rr",
        "................",
        "......r.r.......",
        ".....rrrrr......",
        "......rRr.......",
        "................",
        "..r.r...........",
        ".rrrrr..........",
        "..rRr...........",
        "................",
        "................",
        "................",
    ],
    # drifting bands of mist
    "night_mist": [
        "................",
        "................",
        "..rrr....rrr....",
        ".rrrrrrrrrrrrr..",
        "..rrr..rrr......",
        "................",
        "....rrrrrr......",
        "..rrrrrrrrrrr...",
        ".rrrr....rrrrr..",
        "................",
        "...rrr....rrrr..",
        ".rrrrrrrrrrrr...",
        "..rr....rrr.....",
        "................",
        "................",
        "................",
    ],
    # an eye with a red pupil
    "night_stare": [
        "................",
        "................",
        "................",
        "....rrrrrrrr....",
        "..rrwwwwwwwwrr..",
        ".rwwwwwRRwwwwwr.",
        "rwwwwwRRRRwwwwwr",
        "rwwwwwRRRRwwwwwr",
        ".rwwwwwRRwwwwwr.",
        "..rrwwwwwwwwrr..",
        "....rrrrrrrr....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    # three slashes
    "night_rake": [
        "................",
        "..R....R....R...",
        "..RR...RR...RR..",
        "...RR...RR...RR.",
        "....RR...RR...RR",
        ".....R....R....R",
        "......r....r....",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    # one great tear, split open
    "night_rend": [
        "................",
        "..............R.",
        ".............RR.",
        "............RRr.",
        "...........RRr..",
        "..........RRr...",
        ".........RRr....",
        "........wRr.....",
        ".......wwr......",
        "......wwr.......",
        ".....wwr........",
        "....wRr.........",
        "...RRr..........",
        "..RRr...........",
        "..Rr............",
        "................",
    ],
    # a claw coming down on its mark
    "night_pounce": [
        "................",
        "...w..w..w......",
        "...w..w..w......",
        "...ww.ww.ww.....",
        "....w..w..w.....",
        ".....rrrrr......",
        "......rrr.......",
        ".......r........",
        "................",
        "...RRRRRRRRRR...",
        "..R..R....R..R..",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    # a wide-open mouth, the sound going out
    "night_roar": [
        "................",
        "................",
        "...w.w.w.w......",
        "..rwrwrwrwrR....",
        "..rrrrrrrrrrR.R.",
        "..rRRRRRRRrrR...",
        "..rRRRRRRRrrR.R.",
        "..rrrrrrrrrrR...",
        "..rwrwrwrwrR....",
        "...w.w.w.w......",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    # a winged horror, arms spread
    "night_true_shape": [
        "................",
        "...r........r...",
        "..rr...ww...rr..",
        ".rrr..rwwr..rrr.",
        "rrrrr.rRRr.rrrrr",
        "rrrrrrrwwrrrrrrr",
        ".rrrrrrrrrrrrrr.",
        "..rrr.rrrr.rrr..",
        "...r..rrrr..r...",
        ".......rr.......",
        "......r..r......",
        "......r..r......",
        "................",
        "................",
        "................",
        "................",
    ],
    # three rings of blood
    "night_barrier": [
        "................",
        "....rrrrrrrr....",
        "..rrR......Rrr..",
        ".rR..rrrrrr..Rr.",
        ".r..rR....Rr..r.",
        "rR.rR..rr..Rr.Rr",
        "r..r..rRRr..r..r",
        "r..r..rRRr..r..r",
        "rR.rR..rr..Rr.Rr",
        ".r..rR....Rr..r.",
        ".rR..rrrrrr..Rr.",
        "..rrR......Rrr..",
        "....rrrrrrrr....",
        "................",
        "................",
        "................",
    ],
    # a blink: a bat and the line it leaves
    "night_batstep": [
        "................",
        "................",
        "...........r.r..",
        "..........rrrrr.",
        ".........rrRRrr.",
        "r.......rrrrrrr.",
        "rr.....r.r.r.r..",
        "rrr.............",
        ".rrr............",
        "..rr............",
        "...r............",
        "................",
        "................",
        "................",
        "................",
        "................",
    ],
    # a fan of shards
    "night_flechettes": [
        "................",
        "..............R.",
        "........R....Rw.",
        "........Rw..Rw..",
        "..R......Rw.w...",
        "..Rw..R..Rw.....",
        "...Rw..Rw.......",
        "....Rw..Rw......",
        ".....rRw.Rw.....",
        "......rrRrw.....",
        ".....rrrrr......",
        "....rrrr........",
        "................",
        "................",
        "................",
        "................",
    ],
}


def main() -> None:
    for name, rows in ICONS.items():
        assert len(rows) == 16 and all(len(r) == 16 for r in rows), name
        image = Image.new("RGBA", (16, 16))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                image.putpixel((x, y), PALETTE[ch])
        image.save(OUT / f"{name}.png")
        print("wrote", name)


if __name__ == "__main__":
    main()
