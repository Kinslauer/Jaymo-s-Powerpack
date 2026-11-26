#!/usr/bin/env python3
"""Draws the cast skin's item picture (16x16) and the bloom's petal texture (16x16): our own art, no outside files.

The skin is a thin black husk, a shed skin lying crumpled: dark body, a lighter wet edge, a split down it and a few
holes where the tendrils were. The bloom's texture is the same black goo with a faint violet sheen, for the petals.
Run from the repo root: python3 tools/gen_symbiote_skin.py
"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def png(path, pixels, size=16):
    raw = b""
    for y in range(size):
        raw += b"\x00" + b"".join(struct.pack("BBBB", *pixels[y][x]) for x in range(size))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    data += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


CLEAR = (0, 0, 0, 0)
BLACK = (14, 12, 18, 255)
DEEP = (24, 20, 30, 255)
SHEEN = (58, 44, 78, 255)
EDGE = (84, 72, 104, 255)

# a crumpled husk: 1 body, 2 deeper fold, 3 sheen, 4 wet edge, 5 hole, 0 nothing
SKIN = [
    "0000000000000000",
    "0000044444400000",
    "0000413333140000",
    "0004133313314000",
    "0041311112331400",
    "0413112222113140",
    "0411122552211140",
    "0413122552221340",
    "0413112222131140",
    "0041331122313140",
    "0004113311331400",
    "0000411133114000",
    "0000044113440000",
    "0000004444000000",
    "0000000000000000",
    "0000000000000000",
]
COLORS = {"0": CLEAR, "1": BLACK, "2": DEEP, "3": SHEEN, "4": EDGE, "5": CLEAR}


def skin():
    return [[COLORS[ch] for ch in row] for row in SKIN]


def bloom():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            n = (x * 7 + y * 13) % 11
            row.append(SHEEN if n == 0 else DEEP if n < 4 else BLACK)
        rows.append(row)
    return rows


if __name__ == "__main__":
    png(ROOT / "src/main/resources/assets/jaymospowers/textures/item/symbiote_skin.png", skin())
    png(ROOT / "src/main/resources/assets/jaymospowers/textures/entity/web_symbiote_bloom.png", bloom())
    print("wrote the cast skin and the bloom texture")
