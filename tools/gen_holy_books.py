"""Holy book icons: each patron's sigil (from its shrine glyph) drawn small on the cover of the vanilla book.

Writes only the sigil overlay (16x16, transparent elsewhere) to kingdoms textures item/holy_book/<patron>.png; the item
models put it as layer1 over minecraft:item/book (the book of commandments) or minecraft:item/written_book (the patron's
lore book), so no vanilla pixels are copied. With --preview BOOK.png WRITTEN.png it also renders a preview sheet.

    python3 tools/gen_holy_books.py [--preview book.png written_book.png out.png]
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
TEX = os.path.join(ROOT, "textures", "item", "holy_book")

# The cover's middle on the vanilla book (its top face runs from (8,1) to the spine at (2..14, 9)).
X0, Y0 = 5, 2
# The shadow each sigil pixel casts on the cover (one down), so it reads as pressed on.
SHADOW = (40, 22, 6, 150)

# 7 wide, 6 tall. Row 0 sits on the cover's narrow top, so only its middle column may be used.
SIGILS = {
    "hearthkeeper": ({"F": (224, 160, 80), "f": (255, 214, 130), "h": (176, 168, 158)}, [
        "...F...",
        "..FfF..",
        "..FfF..",
        "...F...",
        ".hhhhh.",
        ".h...h.",
    ]),
    "anvil_father": ({"A": (196, 200, 208), "a": (140, 144, 152)}, [
        ".......",
        "AAAAAAA",
        ".aAAAa.",
        "..AAA..",
        "..aAa..",
        ".AAAAA.",
    ]),
    "deep_mother": ({"w": (58, 143, 183), "d": (96, 190, 232)}, [
        ".......",
        ".ww.ww.",
        "w..w..w",
        ".ww.ww.",
        "...d...",
        "...d...",
    ]),
    "green_huntsman": ({"g": (110, 178, 66), "G": (78, 132, 46)}, [
        ".......",
        "g.....g",
        "gg...gg",
        ".g...g.",
        "..GgG..",
        "...g...",
    ]),
    "lantern_warden": ({"L": (154, 168, 255), "C": (107, 117, 178), "W": (232, 236, 255)}, [
        "...L...",
        "..CCC..",
        ".CWWWC.",
        ".CWWWC.",
        "..CCC..",
        "...L...",
    ]),
    "crowned_sun": ({"c": (255, 224, 112), "S": (255, 194, 26), "s": (204, 155, 20)}, [
        ".......",
        ".c.c.c.",
        ".ccccc.",
        "s.SSS.s",
        ".SSSSS.",
        "..SSS..",
    ]),
    "ash_whisperer": ({"a": (150, 140, 134), "A": (110, 102, 98), "e": (224, 96, 42), "E": (255, 160, 64)}, [
        ".......",
        ".aaaaa.",
        "..AAA..",
        "...e...",
        "..eEe..",
        ".......",
    ]),
    "truth": ({"T": (242, 242, 242), "t": (169, 169, 169)}, [
        "...T...",
        ".t.T.t.",
        "TTTTTTT",
        ".t.T.t.",
        "...T...",
        "..tTt..",
    ]),
}


def overlay(key):
    colours, rows = SIGILS[key]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    lit = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                lit.add((X0 + x, Y0 + y))
                img.putpixel((X0 + x, Y0 + y), colours[ch] + (255,))
    for (x, y) in lit:
        if (x, y + 1) not in lit:
            img.putpixel((x, y + 1), SHADOW)
    return img


def models():
    for key in SIGILS:
        for kind, base in (("holy_book", "minecraft:item/book"), ("holy_lore", "minecraft:item/written_book")):
            name = f"{kind}_{key}"
            with open(os.path.join(ROOT, "models", "item", name + ".json"), "w") as f:
                json.dump({"parent": "minecraft:item/generated",
                           "textures": {"layer0": base, "layer1": f"jaymoskingdoms:item/holy_book/{key}"}}, f, indent=2)
                f.write("\n")
            with open(os.path.join(ROOT, "items", name + ".json"), "w") as f:
                json.dump({"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}}, f, indent=2)
                f.write("\n")


def preview(book, written, out):
    scale, pad = 10, 12
    bases = [Image.open(book).convert("RGBA"), Image.open(written).convert("RGBA")]
    sheet = Image.new("RGBA", (len(SIGILS) * (16 * scale + pad) + pad, 2 * (16 * scale + pad) + pad), (139, 139, 139, 255))
    for i, key in enumerate(SIGILS):
        for j, base in enumerate(bases):
            icon = Image.alpha_composite(base, overlay(key)).resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.alpha_composite(icon, (pad + i * (16 * scale + pad), pad + j * (16 * scale + pad)))
    sheet.save(out)


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    for key in SIGILS:
        overlay(key).save(os.path.join(TEX, key + ".png"))
    models()
    if len(sys.argv) == 5 and sys.argv[1] == "--preview":
        preview(sys.argv[2], sys.argv[3], sys.argv[4])
