"""(0.7.290) Rubber-folk's swallowing rubber (client RubberEngulf): pink rubber with a soft sheen band and faint mottling.
Writes src/main/resources/assets/jaymoskingdoms/textures/entity/heritage/rubber_engulf.png."""
import math, os, random
from PIL import Image

SIZE = 32
BASE = (240, 140, 184)   # the Rubber-folk pink (RubberFolk.PINK)
SHADE = (196, 96, 142)
SHINE = (255, 214, 232)

def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

def main():
    random.seed(290)
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            # a darker crease every so often across u, a sheen band down v, a little mottling
            crease = 0.5 + 0.5 * math.cos(x / SIZE * math.pi * 4)
            c = mix(SHADE, BASE, 0.55 + 0.45 * crease)
            band = math.exp(-((y - SIZE * 0.3) / (SIZE * 0.08)) ** 2) * (0.55 + 0.45 * crease)
            c = mix(c, SHINE, 0.6 * band)
            c = mix(c, SHADE, random.random() * 0.12)
            img.putpixel((x, y), c + (255,))
    out = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "heritage", "rubber_engulf.png")
    img.save(out)
    print(out)

if __name__ == "__main__":
    main()
