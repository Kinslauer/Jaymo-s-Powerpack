"""(0.17.85) Andy: "all auras need to be more of a wisp making contact and dissipating rather than being a solid mesh".
Generates the one texture every aura style's wisps are drawn with: a single tongue of light, root at the bottom (v = 1),
tip at the top (v = 0), bright down its middle and soft at its edges, narrowing and wavering up to a point, with a few
fine streaks running up it. Additive (dark is clear, light glows); the colour is the aura's, given per vertex.
Original art, posterised greys with a little grain like the other aura textures. Run from the repo root:
python3 -I tools/gen_ki_wisp.py"""
import math
import random
from PIL import Image

OUT = "src/main/resources/assets/jaymospowers/textures/entity/zfighter_ki_wisp.png"
W, H = 32, 64


def posterise(light, rng, steps=16):
    light = min(1.0, max(0.0, light + rng.uniform(-0.02, 0.02)))
    return int(255 * round(light * steps) / steps)


def main():
    rng = random.Random(77)
    streaks = [(rng.uniform(-0.35, 0.35), rng.uniform(0.5, 1.0), rng.uniform(0.0, math.tau)) for _ in range(5)]
    img = Image.new("RGB", (W, H))
    for y in range(H):
        up = 1.0 - (y + 0.5) / H  # 0 at the root (bottom), 1 at the tip (top)
        # the tongue's half-width (share of the cell) narrows to a point, and its middle wavers
        half = 0.46 * (1.0 - up) ** 0.75 + 0.02
        mid = 0.07 * math.sin(up * 5.5 + 0.4) * up
        # brightest a little above the root, dying out at the tip
        along = min(1.0, (1.0 - up) * 1.6 + 0.15) * (1.0 - up) ** 0.35
        # and soft where it touches the body
        along *= 0.4 + 0.6 * min(1.0, up / 0.15)
        for x in range(W):
            across = ((x + 0.5) / W - 0.5 - mid) / half
            if abs(across) >= 1.0:
                img.putpixel((x, y), (0, 0, 0))
                continue
            light = (1.0 - across * across) ** 1.6 * along
            for sx, strength, phase in streaks:
                d = abs(across - sx - 0.08 * math.sin(up * 9.0 + phase))
                if d < 0.09:
                    light += 0.18 * strength * (1.0 - d / 0.09) * along
            c = posterise(min(1.0, light), rng)
            img.putpixel((x, y), (c, c, c))
    img.save(OUT)


if __name__ == "__main__":
    main()
