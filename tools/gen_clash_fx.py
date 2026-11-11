"""Sprites for combat/ClashFx: hot sparks (white, tinted by heat in code) and wood shards (pale, tinted by the wood's colour).
Run from the repo root: python3 tools/gen_clash_fx.py"""
from PIL import Image
import os

OUT = "src/main/resources/assets/jaymospowers/textures/particle/clash"
os.makedirs(OUT, exist_ok=True)

def save(name, rows, palette):
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                img.putpixel((x, y), palette[ch])
    img.save(f"{OUT}/{name}.png")

# sparks: a white-hot heart (#), a bright rim (+), a faint glow (.)
SPARK = {"#": (255, 255, 255, 255), "+": (255, 244, 214, 230), ".": (255, 214, 150, 120)}
save("spark_0", [
    "        ",
    "        ",
    "   .    ",
    "  .+.   ",
    "  +#+.  ",
    "  .+.   ",
    "   .    ",
    "        "], SPARK)
save("spark_1", [
    "        ",
    "     .  ",
    "    .+  ",
    "   .#.  ",
    "  .+.   ",
    "  +.    ",
    "  .     ",
    "        "], SPARK)
save("spark_2", [
    "        ",
    "        ",
    "        ",
    "   .+   ",
    "   +#.  ",
    "    .   ",
    "        ",
    "        "], SPARK)
save("spark_3", [
    "        ",
    "        ",
    " .      ",
    " .++.   ",
    "  .+#+. ",
    "     .. ",
    "        ",
    "        "], SPARK)

# shards: pale wood with a lit edge (L), its face (M), the shadowed edge (D) and the dark split end (X)
WOOD = {"L": (255, 250, 238, 255), "M": (226, 214, 196, 255), "D": (172, 158, 140, 255), "X": (112, 100, 88, 255)}
save("splinter_0", [
    "        ",
    "      L ",
    "     LM ",
    "    LMD ",
    "   LMD  ",
    "  LMD   ",
    " XMD    ",
    " XD     "], WOOD)
save("splinter_1", [
    "        ",
    "        ",
    " X      ",
    " LLLL   ",
    " MMMMLL ",
    "  DDMMD ",
    "     DX ",
    "        "], WOOD)
save("splinter_2", [
    "        ",
    "   L    ",
    "   LM   ",
    "   LM   ",
    "   MD   ",
    "   MD   ",
    "   XD   ",
    "        "], WOOD)
save("splinter_3", [
    "        ",
    "        ",
    "  LL    ",
    "  LMM   ",
    "  MMD   ",
    "   DX   ",
    "        ",
    "        "], WOOD)
save("splinter_4", [
    "X       ",
    " L      ",
    " LM     ",
    "  MM    ",
    "  LMD   ",
    "   MD   ",
    "    DL  ",
    "     X  "], WOOD)
print("wrote", sorted(os.listdir(OUT)))
