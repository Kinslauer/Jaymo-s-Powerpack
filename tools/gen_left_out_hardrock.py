"""Art, models and sound for the Hardrock parts PR #1253 left out, in Jaymo's Powers: the hammock (cloth slung between two
ropes), the hot air balloon's item, and the balloon burner's roar. Painted and synthesised from scratch (seeded noise on our own
colour ramps; nothing sampled, nothing taken from vanilla or any other mod).

    python3 -I tools/gen_left_out_hardrock.py      (needs Pillow, numpy and ffmpeg with libvorbis)

Writes the textures, block models, blockstates, item definitions, the loot table and recipes, and
assets/jaymospowers/sounds/rough/burner.ogg. The sound event itself is in sounds.json (rough.balloon.burner).
"""
import json
import os
import random
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from PIL import Image

MAIN = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ASSETS = MAIN / "assets" / "jaymospowers"
DATA = MAIN / "data" / "jaymospowers"
TEX = ASSETS / "textures"

CLOTH = [(168, 52, 44), (190, 72, 56), (212, 98, 74)]
CLOTH_D = (120, 34, 32)
STRIPE = (232, 218, 182)
ROPE = [(140, 112, 72), (166, 136, 92), (186, 158, 112)]
RED, WHITE, BASKET = (176, 52, 44), (232, 224, 204), [(96, 62, 36), (122, 84, 50), (146, 106, 66)]


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def cloth_texture(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = min(2, int(rng.random() * 2.4))
            c = CLOTH[v]
            if x % 5 == 2:
                c = STRIPE if (y + x) % 7 else CLOTH_D
            if y in (0, 15):
                c = CLOTH_D
            img.putpixel((x, y), c + (255,))
    return img


def rope_texture(rng):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = ROPE[(x + y // 2) % 3] if rng.random() > 0.15 else ROPE[0]
            img.putpixel((x, y), c + (255,))
    return img


def hammock_icon():
    img = Image.new("RGBA", (16, 16))
    # a sagging cloth between two posts, ropes up to the posts
    for x in range(2, 14):
        sag = 8 + round(3.0 * (1.0 - abs(x - 7.5) / 5.5)) if abs(x - 7.5) < 5.5 else 6 + abs(x - 7.5) // 2
        sag = int(sag)
        for y in range(sag, sag + 3):
            img.putpixel((x, y), (CLOTH[0] if y == sag + 2 else CLOTH[1] if (x // 3) % 2 else STRIPE) + (255,))
    for y in range(3, 14):
        img.putpixel((1, y), BASKET[1] + (255,))
        img.putpixel((14, y), BASKET[1] + (255,))
    for y in range(3, 14):
        img.putpixel((0, y), BASKET[0] + (255,))
        img.putpixel((15, y), BASKET[0] + (255,))
    for x, y in ((2, 7), (3, 7), (12, 7), (13, 7)):
        img.putpixel((x, y), ROPE[2] + (255,))
    return img


def balloon_icon():
    img = Image.new("RGBA", (16, 16))
    for y in range(0, 10):
        half = [3, 5, 6, 7, 7, 7, 6, 5, 4, 3][y]
        for x in range(8 - half, 8 + half):
            stripe = ((x - (8 - half)) // 2) % 2 == 0
            c = RED if stripe else WHITE
            if y == 9 or x == 8 - half or x == 8 + half - 1:
                c = tuple(int(v * 0.7) for v in c)
            img.putpixel((x, y), c + (255,))
    for x, y in ((5, 10), (6, 11), (10, 10), (9, 11)):
        img.putpixel((x, y), ROPE[2] + (255,))
    for y in range(12, 15):
        for x in range(5, 11):
            img.putpixel((x, y), BASKET[(x + y) % 3] + (255,))
    for x in range(5, 11):
        img.putpixel((x, 14), BASKET[0] + (255,))
    img.putpixel((7, 11), (255, 190, 60, 255))
    img.putpixel((8, 11), (255, 236, 140, 255))
    return img


def bed_half(part):
    """A hammock half: a sling of cloth 3 sixteenths thick hung 9 up, rising to a rope and a hook at its outer end. Modelled
    like the beds: the head half lies toward -Z (north), the foot half toward +Z, each 16 long; textures wrap."""
    sling = {"from": [1, 9, 0], "to": [15, 12, 16], "faces": {
        "up": {"uv": [1, 0, 15, 16], "texture": "#cloth"}, "down": {"uv": [1, 0, 15, 16], "texture": "#cloth"},
        "east": {"uv": [0, 9, 16, 12], "texture": "#cloth"}, "west": {"uv": [0, 9, 16, 12], "texture": "#cloth"},
        "north": {"uv": [1, 9, 15, 12], "texture": "#cloth"}, "south": {"uv": [1, 9, 15, 12], "texture": "#cloth"}}}
    z = 14 if part == "head" else 0
    cord_z = (15, 16) if part == "head" else (0, 1)
    # the outer end's ropes rise to a ring at the post, and so does the other half's, so the two make a V
    ropes = []
    for x in (3, 12):
        ropes.append({"from": [x, 12, cord_z[0]], "to": [x + 1, 15, cord_z[1]], "faces": {
            f: {"uv": [0, 0, 1, 3], "texture": "#rope"} for f in ("north", "south", "east", "west")}})
    return {"parent": "minecraft:block/block", "textures": {"particle": "jaymospowers:block/hammock_cloth", "cloth": "jaymospowers:block/hammock_cloth",
                                                             "rope": "jaymospowers:block/hammock_rope"},
            "elements": [sling] + ropes}


def blockstate():
    variants = {}
    turns = {"north": 0, "east": 90, "south": 180, "west": 270}
    for facing, y in turns.items():
        for occupied in ("false", "true"):
            for part in ("head", "foot"):
                v = {"model": f"jaymospowers:block/hammock_{part}"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},occupied={occupied},part={part}"] = v
    return {"variants": variants}


# ---- the burner's roar ----

RATE = 44100


def lowpass_fast(x, cutoff):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / cutoff) ** 4)
    return np.fft.irfft(spec, len(x))


def burner():
    """A propane-style roar: filtered noise with a slow swell and a low flutter, 1.6 s, faded at both ends."""
    rng = np.random.default_rng(1112)
    n = int(RATE * 1.6)
    t = np.arange(n) / RATE
    hiss = lowpass_fast(rng.standard_normal(n), 2600)
    body = lowpass_fast(rng.standard_normal(n), 420) * 2.2
    flutter = 0.75 + 0.25 * np.sin(2 * np.pi * 9.0 * t + 0.6) * np.sin(2 * np.pi * 2.3 * t)
    swell = np.minimum(1.0, t / 0.18) * np.minimum(1.0, (t[-1] - t) / 0.35)
    x = (hiss * 0.8 + body) * flutter * swell
    x = x / np.max(np.abs(x)) * 0.85
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    out = ASSETS / "sounds" / "rough"
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5", str(out / "burner.ogg")], check=True)
    os.remove(path)


def main():
    rng = random.Random(20261112)
    (TEX / "block").mkdir(parents=True, exist_ok=True)
    (TEX / "item").mkdir(parents=True, exist_ok=True)
    cloth_texture(rng).save(TEX / "block" / "hammock_cloth.png")
    rope_texture(rng).save(TEX / "block" / "hammock_rope.png")
    hammock_icon().save(TEX / "item" / "hammock.png")
    balloon_icon().save(TEX / "item" / "hot_air_balloon.png")
    for part in ("head", "foot"):
        write(ASSETS / "models" / "block" / f"hammock_{part}.json", bed_half(part))
    write(ASSETS / "blockstates" / "hammock.json", blockstate())
    for item in ("hammock", "hot_air_balloon"):
        write(ASSETS / "models" / "item" / f"{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymospowers:item/{item}"}})
        write(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{item}"}})
    write(DATA / "loot_table" / "blocks" / "hammock.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"}, "entries": [{
            "type": "minecraft:item", "name": "jaymospowers:hammock", "condition": {
                "type": "minecraft:match_block", "blocks": "jaymospowers:hammock", "state": {"part": "head"}}}]}],
        "random_sequence": "jaymospowers:blocks/hammock"})
    write(DATA / "recipe" / "rough" / "hammock.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["SWS", "R R"], "key": {"S": "minecraft:stick", "W": "#minecraft:wool", "R": "minecraft:string"},
        "result": {"id": "jaymospowers:hammock", "count": 1}})
    write(DATA / "recipe" / "rough" / "hot_air_balloon.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["WWW", "SCS", "PPP"], "key": {"W": "#minecraft:wool", "S": "minecraft:string", "C": "minecraft:campfire", "P": "#minecraft:planks"},
        "result": {"id": "jaymospowers:hot_air_balloon", "count": 1}})
    burner()
    print("done")


if __name__ == "__main__":
    main()
