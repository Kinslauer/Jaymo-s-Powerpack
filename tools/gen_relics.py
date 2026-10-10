"""Generates the relic weapons' sprites (32x32) and item models: Soul Saber, Grand Shield, Voidreaver, Echo Cleaver,
Tidebreaker, Sunfang, and the Void Armory's flintlock. Each weapon is drawn analytically along the diagonal (like
Durandal in gen_sword_stone.py): u runs from the grip (bottom left) to the tip (top right), v across it.
Run from the project root."""
from PIL import Image, ImageDraw
import json, math

ROOT = "src/main/resources/assets/jaymospowers"
SIZE = 32
D = (1 / math.sqrt(2), -1 / math.sqrt(2))
P = (1 / math.sqrt(2), 1 / math.sqrt(2))


def outline(img):
    out = img.copy()
    for y in range(SIZE):
        for x in range(SIZE):
            if img.getpixel((x, y))[3]:
                continue
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < SIZE and 0 <= ny < SIZE and img.getpixel((nx, ny))[3]:
                    r, g, b, _ = img.getpixel((nx, ny))
                    out.putpixel((x, y), (int(r * 0.25), int(g * 0.25), int(b * 0.3), 255))
                    break
    return out


def draw(name, origin, cls, colours, model="minecraft:item/handheld"):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - origin[0], y + 0.5 - origin[1]
            c = cls(dx * D[0] + dy * D[1], dx * P[0] + dy * P[1])
            if c:
                img.putpixel((x, y), (*colours[c], 255))
    save(name, outline(img), model)


def save(name, img, model="minecraft:item/handheld"):
    img.save(f"{ROOT}/textures/item/{name}.png")
    json.dump({"parent": model, "textures": {"layer0": f"jaymospowers:item/{name}"}},
              open(f"{ROOT}/models/item/{name}.json", "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{name}"}},
              open(f"{ROOT}/items/{name}.json", "w"), indent=2)


def grip(u, v, start, end, half, a, b):
    if start <= u < end and abs(v) <= half:
        return a if int((u - start) * 1.3) % 2 == 0 else b
    return None


# Soul Saber: a curved sabre, blackened steel with a cyan soul-fire edge, a basket guard
def soul_saber(u, v):
    if 0.5 <= u <= 22.0:
        bend = 0.006 * (u - 0.5) ** 2
        w = v - bend
        half = 1.4 * min(1.0, (22.0 - u) / 4.0) + 0.3
        if -half <= w <= half:
            return "E" if w > half - 0.9 else "B" if w > -half + 0.7 else "b"
    if -1.5 <= u < 0.5 and -3.5 <= v <= 3.0:
        return "G" if abs(v) > 1.0 or u < -0.5 else "g"
    return grip(u, v, -8.0, -1.5, 0.95, "H", "h") or ("G" if -9.5 <= u < -8.0 and abs(v) <= 1.5 else None)


draw("soul_saber", (10.5, 21.5), soul_saber, {"E": (110, 240, 255), "B": (60, 64, 76), "b": (34, 36, 44), "G": (40, 200, 220),
                                             "g": (20, 110, 130), "H": (70, 50, 40), "h": (40, 28, 22)})


# Voidreaver: a long straight blade of purple-black void, a crescent guard, an eye of ender pommel
def voidreaver(u, v):
    if 0.5 <= u <= 22.5:
        half = 1.7 * min(1.0, (22.5 - u) / 2.5) + 0.2
        if abs(v) <= half:
            return "E" if abs(v) < 0.5 and int(u) % 3 == 0 else "B" if v > 0 else "b"
    if -1.0 <= u < 0.5 and abs(v) <= 4.5 and abs(v) >= 0.0:
        return "G" if abs(v) < 3.5 or u > -0.3 else None
    return grip(u, v, -7.0, -1.0, 0.95, "H", "h") or ("P" if -9.0 <= u < -7.0 and abs(v) <= 1.6 else None)


draw("voidreaver", (10.0, 22.0), voidreaver, {"E": (230, 160, 255), "B": (90, 40, 130), "b": (45, 15, 70), "G": (200, 190, 230),
                                             "H": (30, 20, 40), "h": (60, 40, 80), "P": (60, 200, 150)})


# Echo Cleaver: a broad, heavy cleaver of deepslate and sculk, glowing teal veins
def echo_cleaver(u, v):
    if 0.0 <= u <= 20.0:
        top = 3.6 if u < 17.0 else 3.6 * (20.0 - u) / 3.0
        if -1.6 <= v <= top:
            vein = abs((v - 1.0) - math.sin(u * 0.7) * 0.8) < 0.45
            return "T" if vein else "E" if v < -1.0 else "B" if v < 2.6 else "b"
    if -1.5 <= u < 0.0 and -2.5 <= v <= 4.4:
        return "G"
    return grip(u, v, -8.5, -1.5, 1.0, "H", "h")


draw("echo_cleaver", (10.0, 22.0), echo_cleaver, {"T": (40, 220, 210), "E": (200, 205, 210), "B": (60, 64, 72), "b": (36, 40, 46),
                                                 "G": (20, 70, 80), "H": (30, 34, 40), "h": (14, 90, 96)})


# Tidebreaker: a sea-green sword with a wave-cut edge, prismarine guard and a pearl
def tidebreaker(u, v):
    if 0.5 <= u <= 21.0:
        half = 1.5 * min(1.0, (21.0 - u) / 3.5) + 0.3 + 0.35 * math.sin(u * 1.1)
        if -1.6 <= v <= half:
            return "E" if v > half - 0.8 else "B" if v > -0.6 else "b"
    if -1.0 <= u < 0.5 and abs(v) <= 3.8:
        return "G"
    return grip(u, v, -7.5, -1.0, 0.95, "H", "h") or ("P" if -9.0 <= u < -7.5 and abs(v) <= 1.5 else None)


draw("tidebreaker", (10.5, 21.5), tidebreaker, {"E": (210, 250, 240), "B": (70, 180, 160), "b": (30, 110, 110), "G": (90, 150, 140),
                                               "H": (40, 70, 90), "h": (60, 100, 120), "P": (240, 235, 220)})


# Sunfang: a bronze khopesh, hooked forward at the end, a sun disc pommel
def sunfang(u, v):
    if 0.5 <= u <= 10.0 and abs(v) <= 0.9:
        return "b"
    if 10.0 <= u <= 21.0:
        t = (u - 10.0) / 11.0
        centre = -3.0 * math.sin(t * math.pi * 0.9)
        half = 1.6 - 0.6 * t
        if abs(v - centre) <= half:
            return "E" if v - centre < -half + 0.8 else "B"
    return grip(u, v, -6.5, 0.5, 0.95, "H", "h") or ("S" if -9.0 <= u < -6.5 and abs(v) <= 2.0 else None)


draw("sunfang", (10.5, 21.0), sunfang, {"E": (255, 230, 140), "B": (200, 140, 50), "b": (150, 100, 40), "H": (90, 50, 30),
                                       "h": (60, 120, 160), "S": (255, 200, 60)})

# Grand Shield: a tall kite shield, silver rim, deep blue field, a gold sunburst boss
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
kite = [(16, 2), (27, 5), (27, 14), (16, 30), (5, 14), (5, 5)]
d.polygon(kite, fill=(200, 205, 215))
inner = [(16, 4), (25, 6.5), (25, 13.5), (16, 27), (7, 13.5), (7, 6.5)]
d.polygon(inner, fill=(40, 60, 140))
d.line([(16, 5), (16, 26)], fill=(220, 180, 60), width=2)
d.line([(8, 11), (24, 11)], fill=(220, 180, 60), width=2)
d.ellipse([(12.5, 7.5), (19.5, 14.5)], fill=(240, 200, 80), outline=(160, 110, 30))
save("grand_shield", outline(img), "minecraft:item/generated")

# the flintlock: dark walnut stock, a long iron barrel, brass fittings, purple void glow in the pan
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.polygon([(6, 12), (28, 12), (28, 15), (6, 15)], fill=(110, 112, 120))     # barrel
d.line([(6, 12), (28, 12)], fill=(170, 172, 180))
d.rectangle([(26, 11), (28, 16)], fill=(190, 150, 60))                     # muzzle band
d.polygon([(4, 15), (16, 15), (16, 18), (10, 18), (6, 27), (1, 26), (4, 17)], fill=(96, 56, 30))  # stock and grip
d.line([(6, 18), (2, 25)], fill=(130, 80, 44))
d.ellipse([(0, 24), (5, 28)], fill=(190, 150, 60))                          # pommel cap
d.polygon([(9, 18), (13, 18), (12, 22), (10, 22)], outline=(190, 150, 60))  # trigger guard
d.line([(11, 18), (11, 20)], fill=(60, 60, 64))                             # trigger
d.polygon([(12, 9), (15, 11), (14, 12), (11, 10)], fill=(70, 70, 76))       # the hammer
d.rectangle([(15, 11), (17, 12)], fill=(170, 80, 220))                      # the pan, void-lit
save("flintlock", outline(img))
print("relics drawn")
