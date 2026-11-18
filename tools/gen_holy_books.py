"""Holy book icons: each patron's sigil (from its shrine glyph) set in a sunken square on the cover of the vanilla book,
lying flat on the cover's tilt (upright as you'd hold the book, its top towards the spine's top).

Writes only the overlay (32x32, transparent elsewhere) to kingdoms textures item/holy_book/<patron>.png; the item
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

# The overlay is 32x32 (twice the book's 16x16) so a sigil still reads once it lies on the tilted cover.
SIZE = 32
# The cover in the overlay's pixels: its middle, one step along the book's width (towards the lower right) and one
# step along its height (up the spine, towards the upper right). A sigil is drawn in these (u right, v up) units.
C = (16.0, 11.0)
R = (3.0, 2.0)
U = (3.0, -1.8)
# The sunken square: the sigil's ground out to IN, its walls out to OUT (in the same units, scaled by K).
IN, OUT = 1.75, 2.45
K = IN / 2.3
GROUND = (30, 14, 0, 95)
WALL_SHADE = (18, 8, 0, 190)    # the upper and left walls, out of the light
WALL_LIT = (255, 232, 180, 110)  # the lower and right walls
# Where the vanilla book has pixels (16x16 rows 1-10: first and last column), so nothing is drawn off it.
BOOK_ROWS = {1: (8, 10), 2: (6, 11), 3: (4, 12), 4: (2, 13), 5: (0, 14), 6: (0, 15), 7: (0, 14), 8: (0, 14), 9: (0, 15), 10: (1, 15)}

_DET = R[0] * U[1] - R[1] * U[0]


def uv(x, y):
    dx, dy = x - C[0], y - C[1]
    return (dx * U[1] - dy * U[0]) / _DET / K, (R[0] * dy - R[1] * dx) / _DET / K


def at(u, v):
    return C[0] + K * (u * R[0] + v * U[0]), C[1] + K * (u * R[1] + v * U[1])


def on_book(x, y):
    span = BOOK_ROWS.get(y // 2)
    return span is not None and span[0] <= x // 2 <= span[1]


def line(*pts, w=0.8):
    """Pixels within w of the polyline through pts (cover units)."""
    out = set()
    for a, b in zip(pts, pts[1:]):
        (ax, ay), (bx, by) = at(*a), at(*b)
        dx, dy = bx - ax, by - ay
        l2 = dx * dx + dy * dy
        for y in range(SIZE):
            for x in range(SIZE):
                px, py = x + 0.5, y + 0.5
                t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
                if (px - ax - t * dx) ** 2 + (py - ay - t * dy) ** 2 <= w * w:
                    out.add((x, y))
    return out


def fill(test):
    """Pixels whose middle, in cover units, passes test(u, v)."""
    return {(x, y) for y in range(SIZE) for x in range(SIZE) if test(*uv(x + 0.5, y + 0.5))}


# Each sigil (after its shrine glyph): shapes in cover units (u, v in -2.3..2.3, v up the book), painted in order.
def sigil(key):
    if key == "hearthkeeper":
        return [(line((-1.6, -2), (-1.6, -0.4), (1.6, -0.4), (1.6, -2)), (206, 198, 186)),
                (fill(lambda u, v: (u / 0.8) ** 2 + ((v - 0.8) / 1.2) ** 2 <= 1), (224, 160, 80)),
                (fill(lambda u, v: abs(u) <= 0.3 and 0.2 <= v <= 1.0), (255, 214, 130))]
    if key == "anvil_father":
        return [(line((-2, 1.6), (2, 1.6)), (196, 200, 208)),
                (fill(lambda u, v: abs(u) <= 0.6 and -1.2 <= v <= 1.2), (140, 144, 152)),
                (line((-1.4, -1.6), (1.4, -1.6)), (196, 200, 208))]
    if key == "deep_mother":
        return [(line((-2, 1.4), (-1, 2), (0, 1.4), (1, 2), (2, 1.4)), (58, 143, 183)),
                (line((-2, 0.4), (-1, 1), (0, 0.4), (1, 1), (2, 0.4)), (58, 143, 183)),
                (line((0, 0), (0, -2)), (96, 190, 232))]
    if key == "green_huntsman":
        g = (110, 178, 66)
        return [(line((0, -2), (0, -0.6)), (78, 132, 46)), (line((0, -0.6), (-1.9, 1.9)), g), (line((0, -0.6), (1.9, 1.9)), g),
                (line((-1.1, 0.6), (-2, 0.6)), g), (line((1.1, 0.6), (2, 0.6)), g)]
    if key == "lantern_warden":
        return [(fill(lambda u, v: abs(u) <= 1.2 and -1.6 <= v <= 1.1), (107, 117, 178)),
                (fill(lambda u, v: abs(u) <= 0.55 and -1.0 <= v <= 0.5), (232, 236, 255)),
                (line((0, 1.5), (0, 2.2)), (154, 168, 255))]
    if key == "crowned_sun":
        c = (255, 224, 112)
        return [(fill(lambda u, v: u * u + (v + 0.5) ** 2 <= 1.7), (255, 194, 26)),
                (line((-1.2, 1.1), (-1.2, 2)), c), (line((0, 1.1), (0, 2.2)), c), (line((1.2, 1.1), (1.2, 2)), c)]
    if key == "ash_whisperer":
        a = (150, 140, 134)
        return [(line((-1.9, 1.6), (1.9, 1.6)), a), (line((-1.1, 0.8), (1.1, 0.8)), a),
                (fill(lambda u, v: (u / 0.9) ** 2 + ((v + 1) / 0.9) ** 2 <= 1), (224, 96, 42)),
                (fill(lambda u, v: (u / 0.4) ** 2 + ((v + 1.2) / 0.45) ** 2 <= 1), (255, 160, 64))]
    if key == "truth":
        return [(line((0, -2), (0, 2)), (242, 242, 242)), (line((-2, 0), (2, 0)), (242, 242, 242))]
    raise KeyError(key)


PATRONS = ["hearthkeeper", "anvil_father", "deep_mother", "green_huntsman", "lantern_warden", "crowned_sun", "ash_whisperer", "truth"]


def overlay(key):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            if not on_book(x, y):
                continue
            u, v = uv(x + 0.5, y + 0.5)
            m = max(abs(u), abs(v)) * K
            if m <= IN:
                img.putpixel((x, y), GROUND)
            elif m <= OUT:
                img.putpixel((x, y), WALL_SHADE if v * K > IN or u * K < -IN else WALL_LIT)
    for pixels, colour in sigil(key):
        for (x, y) in pixels:
            u, v = uv(x + 0.5, y + 0.5)
            if max(abs(u), abs(v)) * K <= IN and on_book(x, y):
                img.putpixel((x, y), colour + (255,))
    return img


def models():
    for key in PATRONS:
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
    sheet = Image.new("RGBA", (len(PATRONS) * (16 * scale + pad) + pad, 2 * (16 * scale + pad) + pad), (139, 139, 139, 255))
    for i, key in enumerate(PATRONS):
        for j, base in enumerate(bases):
            icon = Image.alpha_composite(base.resize((SIZE, SIZE), Image.NEAREST), overlay(key)).resize((16 * scale, 16 * scale), Image.NEAREST)
            sheet.alpha_composite(icon, (pad + i * (16 * scale + pad), pad + j * (16 * scale + pad)))
    sheet.save(out)


if __name__ == "__main__":
    os.makedirs(TEX, exist_ok=True)
    for key in PATRONS:
        overlay(key).save(os.path.join(TEX, key + ".png"))
    models()
    if len(sys.argv) == 5 and sys.argv[1] == "--preview":
        preview(sys.argv[2], sys.argv[3], sys.argv[4])
