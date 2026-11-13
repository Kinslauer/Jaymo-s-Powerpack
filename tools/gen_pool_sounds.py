"""Our own extra takes of the game's one-recording sounds, so they choose from a pool (Andy, four-mods picks:
"what if we made sounds choose from a pool so the outcomes are more varied?"). Synthesised from noise and tones,
nothing sampled. The game's own recording stays in each pool; ours are added beside it in
src/main/resources/assets/minecraft/sounds.json ("replace": false), so a sound that had one take now has four.

    python3 tools/gen_pool_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/pool/ and rewrites the pool sounds.json.
Re-run to change them; the seeds keep every file the same from run to run.
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets")
OUT = os.path.join(ROOT, "jaymospowers", "sounds", "pool")
POOL_JSON = os.path.join(ROOT, "minecraft", "sounds.json")
TAKES = 3


def band(x, low, high, order=4):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / high) ** order)
    spec *= 1.0 - 1.0 / (1.0 + (f / max(low, 1.0)) ** order)
    return np.fft.irfft(spec, len(x))


def save(name, x):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x))
    if peak > 0:
        x = x / peak * 0.85
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, name + ".wav")
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "4",
                        os.path.join(OUT, name + ".ogg")], check=True)


def clock(seconds):
    n = int(seconds * RATE)
    return n, np.arange(n) / RATE


def pop(rng):
    """A small pop: a bubble of air closing, the pitch jumping up as it does."""
    n, t = clock(0.12)
    f = 700 + 300 * rng.random()
    sweep = f * (1 + 1.8 * np.minimum(1, t / 0.025))
    phase = 2 * np.pi * np.cumsum(sweep) / RATE
    return np.sin(phase) * np.exp(-t * (45 + 20 * rng.random())) * np.minimum(1, t * 2000)


def orb(rng):
    """A bright, glassy tick: two high partials that don't quite agree."""
    n, t = clock(0.35)
    f = 1800 + 900 * rng.random()
    out = np.sin(2 * np.pi * f * t) + 0.5 * np.sin(2 * np.pi * f * 2.41 * t)
    return out * np.exp(-t * (14 + 6 * rng.random())) * np.minimum(1, t * 3000)


def twang(rng):
    """A bowstring let go: a plucked low string under a short rush of air."""
    n, t = clock(0.45)
    f = 95 + 40 * rng.random()
    string = sum(np.sin(2 * np.pi * f * k * t) / k * np.exp(-t * (8 + 6 * k)) for k in range(1, 6))
    air = band(rng.standard_normal(n), 800, 4000) * np.exp(-((t - 0.05) ** 2) / 0.002) * 0.4
    return (string + air) * np.minimum(1, t * 1500)


def creak(rng):
    """A lid lifted on a stiff hinge: a rough, rising, stick-slip squeak."""
    n, t = clock(0.5 + 0.2 * rng.random())
    f = 180 + 120 * rng.random() + 160 * t
    stick = (np.sin(2 * np.pi * np.cumsum(f) / RATE * 0.5 * (1 + 0.1 * rng.random())) > 0.6).astype(float)
    body = band(stick * rng.standard_normal(n), 300, 2500)
    return body * np.sin(np.pi * t / t[-1]) ** 0.7


def clank(rng, heavy):
    """Iron struck: a hard tick then inharmonic partials; heavier lower and longer."""
    n, t = clock(1.2 if heavy else 0.6)
    base = (140 if heavy else 420) * (1 + 0.15 * rng.random())
    out = np.zeros(n)
    for k, r in enumerate((1.0, 2.32, 4.25, 6.63, 9.38)):
        out += np.sin(2 * np.pi * base * r * t + rng.random() * 6) * np.exp(-t * (3 + 5 * k) * (1 if heavy else 1.8)) / (1 + k)
    hit = band(rng.standard_normal(n), 1000, 7000) * np.exp(-t * 90)
    return out + hit * 0.6


def gulp(rng):
    """A swallow: a low wet knock with a falling bubble."""
    n, t = clock(0.3)
    f = 220 + 80 * rng.random() - 300 * t
    knock = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t * 22)
    wet = band(rng.standard_normal(n), 300, 1200) * np.exp(-t * 30) * 0.3
    return knock + wet


def burp(rng):
    """A short belch: a rough low buzz that wobbles down."""
    n, t = clock(0.4 + 0.15 * rng.random())
    f = 110 + 30 * rng.random() - 40 * t
    buzz = np.sign(np.sin(2 * np.pi * np.cumsum(f) / RATE)) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * t))
    return band(buzz, 80, 900) * np.sin(np.pi * t / t[-1]) ** 0.5


def snap(rng):
    """Something giving way: a sharp crack and a scatter of small bits."""
    n, t = clock(0.4)
    crack = band(rng.standard_normal(n), 1500, 8000) * np.exp(-t * 70)
    bits = np.zeros(n)
    for _ in range(8):
        at = int((0.03 + 0.25 * rng.random()) * RATE)
        ln = int(0.01 * RATE)
        if at + ln < n:
            bits[at:at + ln] += band(rng.standard_normal(ln), 2000, 9000) * np.exp(-np.arange(ln) / 80.0) * rng.random()
    return crack + bits * 0.5


def click(rng, wood):
    """A switch thrown: two quick ticks, duller for wood."""
    n, t = clock(0.12)
    out = np.zeros(n)
    hi = 2500 if wood else 5000
    for at in (0.0, 0.02 + 0.015 * rng.random()):
        i = int(at * RATE)
        ln = int(0.015 * RATE)
        out[i:i + ln] += band(rng.standard_normal(ln), 600 if wood else 1500, hi) * np.exp(-np.arange(ln) / 60.0)
    return out


def fizz(rng):
    """Water on fire: a hiss that swells and dies."""
    n, t = clock(0.8 + 0.3 * rng.random())
    hiss = band(rng.standard_normal(n), 2500, 9000)
    crackle = (rng.random(n) > 0.997) * rng.standard_normal(n) * 3
    return (hiss + crackle) * np.exp(-t * 4) * np.minimum(1, t * 40)


def plop(rng):
    """Something light into water: a hollow plop and a few drops after."""
    n, t = clock(0.6)
    f = 400 + 200 * rng.random()
    bloop = np.sin(2 * np.pi * np.cumsum(f * (1 + 2 * np.minimum(1, t / 0.04))) / RATE) * np.exp(-t * 30)
    spray = band(rng.standard_normal(n), 1500, 6000) * np.exp(-((t - 0.06) ** 2) / 0.003) * 0.4
    return bloop + spray


def thud(rng, big):
    """A body landing: a low thump, bigger falls lower with a scuff of grit."""
    n, t = clock(0.5 if big else 0.25)
    f = (55 if big else 90) * (1 + 0.2 * rng.random())
    body = np.sin(2 * np.pi * f * t * (1 - 0.4 * t)) * np.exp(-t * (12 if big else 25))
    grit = band(rng.standard_normal(n), 400, 3000) * np.exp(-t * 35) * (0.5 if big else 0.3)
    return body + grit


def strike(rng):
    """Flint on steel: a scrape and a bright spark."""
    n, t = clock(0.35)
    scrape = band(rng.standard_normal(n), 2000, 7000) * np.exp(-((t - 0.04) ** 2) / 0.001)
    spark = np.sin(2 * np.pi * (3500 + 1500 * rng.random()) * t) * np.exp(-((t - 0.07) ** 2) / 0.0004) * 0.4
    return scrape + spark


def thunk(rng):
    """A wooden lid dropped shut: a hollow knock."""
    n, t = clock(0.3)
    f = 160 + 60 * rng.random()
    knock = np.sin(2 * np.pi * f * t) * np.exp(-t * 28) + 0.4 * np.sin(2 * np.pi * f * 2.7 * t) * np.exp(-t * 45)
    return knock + band(rng.standard_normal(n), 300, 2500) * np.exp(-t * 80) * 0.5


# vanilla event -> (our take's name, maker). The game's own recording stays first in each pool.
POOLS = {
    "entity.item.pickup": ("pop", pop),
    "entity.experience_orb.pickup": ("orb", orb),
    "entity.arrow.shoot": ("twang", twang),
    "block.chest.open": ("creak", creak),
    "block.anvil.land": ("anvil_land", lambda r: clank(r, True)),
    "block.anvil.use": ("anvil_use", lambda r: clank(r, False)),
    "entity.generic.drink": ("gulp", gulp),
    "entity.player.burp": ("burp", burp),
    "entity.item.break": ("snap", snap),
    "block.lever.click": ("click", lambda r: click(r, False)),
    "block.wooden_button.click_on": ("wood_click", lambda r: click(r, True)),
    "block.fire.extinguish": ("fizz", fizz),
    "entity.fishing_bobber.splash": ("plop", plop),
    "entity.player.small_fall": ("fall_small", lambda r: thud(r, False)),
    "entity.player.big_fall": ("fall_big", lambda r: thud(r, True)),
    "entity.generic.small_fall": ("fall_small", lambda r: thud(r, False)),
    "entity.generic.big_fall": ("fall_big", lambda r: thud(r, True)),
    "item.flintandsteel.use": ("strike", strike),
    "block.barrel.close": ("thunk", thunk),
}


def main():
    made = set()
    pool_json = {}
    for i, (event, (name, maker)) in enumerate(POOLS.items()):
        entries = []
        for k in range(TAKES):
            file = f"{name}_{k + 1}"
            if file not in made:
                save(file, maker(np.random.default_rng(1000 + 10 * sorted(POOLS).index(event) + k)))
                made.add(file)
            entries.append({"name": f"jaymospowers:pool/{file}", "volume": 0.8})
        pool_json[event] = {"replace": False, "sounds": entries}
    with open(POOL_JSON, "w") as f:
        json.dump(pool_json, f, indent="\t")
        f.write("\n")


if __name__ == "__main__":
    main()
