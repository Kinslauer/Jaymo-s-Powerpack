"""(0.17.46, Andy's NOEA picks) A rough mock of the three Z Fighter aura styles side by side (Sparking, Smooth, Godly)
round a plain figure: each aura's outline from the same ring profile the client uses, filled from its own texture,
added onto dark like the in-game light. Not what the game draws exactly; for judging the look. Run from the repo root:
python3 -I tools/gen_ki_aura_styles_preview.py [out.png]"""
import math
import random
import sys
from PIL import Image, ImageDraw

TEX = "src/main/resources/assets/jaymospowers/textures/entity/"
OUT = sys.argv[1] if len(sys.argv) > 1 else "aura-styles-preview.png"
COLOR = (255, 214, 90)
CELL_W, CELL_H, SCALE = 220, 360, 1
FOOT, HEAD = 318, 108  # figure's feet and head top in a cell
rng = random.Random(3)

flame = Image.open(TEX + "zfighter_ki_aura.png").convert("L")
smooth = Image.open(TEX + "zfighter_ki_aura_smooth.png").convert("L")
godly = Image.open(TEX + "zfighter_ki_aura_godly.png").convert("L")
sparks = Image.open(TEX + "zfighter_ki_aura_sparks.png").convert("L")


def lerp_profile(profile, t):
    f = t * (len(profile) - 1)
    i = min(int(f), len(profile) - 2)
    return profile[i] + (profile[i + 1] - profile[i]) * (f - i)


def shell(img, tex, radius, light, top, scale, tongues=0.0, spin=0.0, strength=0.9):
    """Add one shell's front view: rows feet to top, half-width from the ring radius, columns from the texture's u."""
    px = img.load()
    height = FOOT - top
    for y in range(int(top), FOOT + 1):
        t = (FOOT - y) / height
        r = lerp_profile(radius, t) * scale
        lt = lerp_profile(light, t)
        hw = int(r * 70)
        for dx in range(-hw, hw + 1):
            cx = CELL_W // 2 + dx
            if not 0 <= cx < CELL_W:
                continue
            # the front of a cylinder: u runs with the angle, so it bunches towards the sides
            ang = math.asin(max(-1.0, min(1.0, dx / max(hw, 1))))
            u = ((ang / math.pi + 0.5) * 2.0 + spin) % 1.0
            v = (1.0 - t) % 1.0
            tv = tex.getpixel((int(u * (tex.width - 1)), int(v * (tex.height - 1)))) / 255.0
            edge = 1.0 - (abs(dx) / max(hw, 1)) ** 4
            k = tv * lt * edge * strength
            old = px[cx, y]
            px[cx, y] = tuple(min(255, old[i] + int(COLOR[i] * k)) for i in range(3))


def figure(draw):
    skin, cloth = (200, 160, 130), (40, 70, 160)
    cx = CELL_W // 2
    draw.ellipse([cx - 12, HEAD + 30, cx + 12, HEAD + 56], fill=skin)
    draw.rectangle([cx - 18, HEAD + 58, cx + 18, HEAD + 130], fill=cloth)
    draw.rectangle([cx - 30, HEAD + 60, cx - 20, HEAD + 125], fill=skin)
    draw.rectangle([cx + 20, HEAD + 60, cx + 30, HEAD + 125], fill=skin)
    draw.rectangle([cx - 17, HEAD + 130, cx - 3, FOOT], fill=cloth)
    draw.rectangle([cx + 3, HEAD + 130, cx + 17, FOOT], fill=cloth)


def cell(style):
    img = Image.new("RGB", (CELL_W, CELL_H), (14, 14, 26))
    figure(ImageDraw.Draw(img))
    if style == 0:
        radius = [0.30, 0.56, 0.72, 0.80, 0.79, 0.72, 0.58, 0.40, 0.14]
        ring = [0.15, 0.6, 0.85, 1.0, 1.0, 0.95, 0.8, 0.5, 0.0]
        shell(img, flame, radius, ring, 20, 1.0, strength=0.75)
        shell(img, flame, radius, ring, 40, 0.82, spin=0.4, strength=0.85)
        d = ImageDraw.Draw(img)
        for _ in range(9):
            x, y = rng.randint(50, 170), rng.randint(110, 290)
            n = rng.randint(2, 3)
            pts = [(x, y)]
            for _ in range(n):
                x += rng.choice((-6, -4, 4, 6))
                y += rng.randint(5, 9)
                pts.append((x, y))
            d.line(pts, fill=(255, 250, 225), width=1)
    elif style == 1:
        radius = [0.26, 0.50, 0.64, 0.70, 0.70, 0.64, 0.50, 0.30, 0.06]
        ring = [0.10, 0.60, 0.90, 1.0, 1.0, 0.95, 0.80, 0.45, 0.0]
        shell(img, smooth, radius, ring, 50, 1.0, strength=0.7)
        shell(img, smooth, radius, ring, 62, 0.86, spin=0.3, strength=0.8)
    else:
        radius = [0.20, 0.34, 0.40, 0.42, 0.40, 0.36, 0.30, 0.22, 0.15, 0.08, 0.0]
        ring = [0.10, 0.50, 0.75, 0.90, 0.90, 0.85, 0.75, 0.60, 0.40, 0.20, 0.0]
        shell(img, godly, radius, ring, 10, 1.1, strength=0.55)
        shell(img, godly, radius, ring, 30, 0.9, spin=0.5, strength=0.65)
        d = ImageDraw.Draw(img)
        for _ in range(16):
            x, y = CELL_W // 2 + rng.randint(-45, 45), rng.randint(40, 300)
            d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(255, 235, 170))
    return img


names = ["Sparking", "Smooth", "Godly"]
sheet = Image.new("RGB", (CELL_W * 3 + 40, CELL_H + 30), (14, 14, 26))
d = ImageDraw.Draw(sheet)
for i in range(3):
    sheet.paste(cell(i), (i * (CELL_W + 20), 30))
    d.text((i * (CELL_W + 20) + CELL_W // 2 - 20, 10), names[i], fill=(235, 235, 245))
sheet.save(OUT)
print("wrote", OUT)
