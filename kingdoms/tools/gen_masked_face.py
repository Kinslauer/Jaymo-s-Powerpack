"""The face a masked player shows in the tab list and social screen (MaskedFacesClient): a 64x64 skin whose head is a
dark cloth mask with two eye slits, hat layer empty. Writes textures/entity/identity/masked_face.png."""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/jaymoskingdoms/textures/entity/identity/masked_face.png")

random.seed(7)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
base = (38, 36, 44)


def cloth(x, y):
    n = random.randint(-6, 6)
    weave = 4 if (x + y) % 2 == 0 else 0
    return (base[0] + n + weave, base[1] + n + weave, base[2] + n + weave, 255)


# the head's six faces (u 0-32, v 0-16)
for x in range(0, 32):
    for y in range(0, 16):
        if (y < 8 and 8 <= x < 24) or y >= 8:
            img.putpixel((x, y), cloth(x, y))
# front face u 8-16, v 8-16: a seam across the brow, eye slits with a glint, a knot of folds at the mouth
for x in range(8, 16):
    img.putpixel((x, 10), (28, 26, 32, 255))
for x in (9, 10, 13, 14):
    img.putpixel((x, 12), (8, 8, 10, 255))
img.putpixel((10, 12), (150, 150, 160, 255))
img.putpixel((13, 12), (150, 150, 160, 255))
for x in range(10, 14):
    img.putpixel((x, 14), (30, 28, 34, 255))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print(OUT)
