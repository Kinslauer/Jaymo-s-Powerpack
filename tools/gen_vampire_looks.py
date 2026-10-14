"""Vampire player looks (Kingdoms 0.7.22): overlays on the 64x64 player skin layout.

pale.png  - translucent ashen wash over the head and hands (always on a vampire).
fangs.png - two white fangs under the mouth (night).
eyes.png  - red eyes, drawn emissive (night).
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets",
                   "jaymoskingdoms", "textures", "entity", "vampire")
os.makedirs(OUT, exist_ok=True)

PALE = (226, 230, 238, 110)


def fill(img, x0, y0, x1, y1, color):
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), color)


pale = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
fill(pale, 8, 0, 24, 8, PALE)    # head top and bottom
fill(pale, 0, 8, 32, 16, PALE)   # head sides, face, back
fill(pale, 40, 29, 56, 32, PALE)  # right hand (classic and slim)
fill(pale, 32, 61, 48, 64, PALE)  # left hand
pale.save(os.path.join(OUT, "pale.png"))

fangs = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
for x in (10, 13):
    fangs.putpixel((x, 14), (246, 244, 236, 255))
fangs.save(os.path.join(OUT, "fangs.png"))

eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
for x in (9, 10, 13, 14):
    eyes.putpixel((x, 12), (255, 30, 24, 255))
eyes.save(os.path.join(OUT, "eyes.png"))
print("wrote", OUT)
