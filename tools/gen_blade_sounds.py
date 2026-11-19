"""Our own blade sounds, synthesised from noise and tones (nothing sampled, nothing borrowed).

    python3 tools/gen_blade_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/blade/:
  swing_light_1..5    a quick, thin cut of air (knives, sabers, a rapier)
  swing_medium_1..5   a sword's swing: fuller, a little longer
  swing_heavy_1..5    a great weapon's: low, slow, with weight behind it
  clash_1..6          steel on steel: a hard tick and a short bright ring of clashing partials
  draw_1..3           a blade leaving its scabbard: a rising scrape that ends on a faint ring
  armour_ring_1..3    a blow on plate: a hard tick and a low, slow-dying ring (hit_sounds)
  armour_rasp_1..3    a blow on mail: a rasp of rings shifting on each other, a faint jingle
  armour_thud_1..3    a blow on leather and padding: a dull low thump with a little slap
  stone_chip_1..2     steel on stone: a short dry tick and a grit of chips
Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "sounds", "blade")


def band(x, low, high, order=4):
    """Noise kept between two frequencies, done in the frequency domain."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / high) ** order)
    spec *= 1.0 - 1.0 / (1.0 + (f / max(low, 1.0)) ** order)
    return np.fft.irfft(spec, len(x))


def save(name, x):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x))
    if peak > 0:
        x = x / peak * 0.89
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, name + ".wav")
        with wave.open(path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)


def swing(rng, seconds, low, high, peak_at, air):
    """A cut of air: noise whose band slides up to the middle of the swing and back, loudest as the blade passes."""
    n = int(seconds * RATE)
    t = np.arange(n) / n
    noise = rng.standard_normal(n)
    # three bands, cross-faded, stand in for a moving filter
    centre = np.exp(-((t - peak_at) ** 2) / 0.035)
    lows = band(noise, low * 0.5, low * 1.6)
    mids = band(noise, low * 1.4, high * 0.7)
    highs = band(noise, high * 0.6, high * 1.5)
    body = lows * (1.0 - centre) * 0.7 + mids * (0.4 + 0.6 * centre) + highs * centre * air
    envelope = np.exp(-((t - peak_at) ** 2) / (0.05 + 0.02 * rng.random())) * np.minimum(1.0, t * 40.0) * np.minimum(1.0, (1.0 - t) * 12.0)
    return body * envelope


def clash(rng, base):
    """Steel on steel: a tick of noise, then a handful of partials that don't agree, dying fast."""
    seconds = 0.55
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    out = np.zeros(n)
    # a struck bar's partials, each knocked a little off, twice over for the two blades
    for blade in range(2):
        f0 = base * (1.0 if blade == 0 else 1.19 + 0.08 * rng.random())
        for k, ratio in enumerate((1.0, 2.76, 5.40, 8.93, 13.34)):
            f = f0 * ratio * (1.0 + 0.012 * (rng.random() - 0.5))
            if f > 16000:
                continue
            decay = 9.0 + 7.0 * k + 6.0 * rng.random()
            out += np.sin(2 * np.pi * f * t + rng.random() * 6.28) * np.exp(-decay * t) * (0.9 / (1 + k * 0.6))
    tick = band(rng.standard_normal(n), 2500, 12000) * np.exp(-90.0 * t) * 1.6
    scrape = band(rng.standard_normal(n), 4000, 9000) * np.exp(-22.0 * t) * 0.18 * (0.5 + 0.5 * np.sin(2 * np.pi * 45 * t))
    return (out + tick + scrape) * np.minimum(1.0, np.arange(n) / 30.0)


def draw(rng):
    """A blade drawn: a scrape that rises as it comes out, and a faint ring as the tip clears."""
    seconds = 0.42
    n = int(seconds * RATE)
    t = np.arange(n) / n
    noise = rng.standard_normal(n)
    rise = band(noise, 1800, 5000) * (1.0 - t) + band(noise, 4000, 11000) * t
    grain = 0.6 + 0.4 * np.sin(2 * np.pi * (60 + 240 * t) * t * seconds)
    scrape = rise * grain * np.minimum(1.0, t * 30.0) * np.exp(-((t - 0.55) ** 2) / 0.12)
    ts = np.arange(n) / RATE
    start = int(n * 0.78)
    ring = np.zeros(n)
    f = 3100 + 500 * rng.random()
    tail = ts[: n - start]
    ring[start:] = (np.sin(2 * np.pi * f * tail) + 0.5 * np.sin(2 * np.pi * f * 2.76 * tail)) * np.exp(-28.0 * tail) * 0.35
    return scrape + ring


def main():
    rng = np.random.default_rng(1341)
    for i in range(1, 6):
        save("swing_light_%d" % i, swing(rng, 0.20 + 0.03 * rng.random(), 900, 6500, 0.42 + 0.1 * rng.random(), 0.9))
        save("swing_medium_%d" % i, swing(rng, 0.28 + 0.04 * rng.random(), 500, 4200, 0.45 + 0.1 * rng.random(), 0.6))
        save("swing_heavy_%d" % i, swing(rng, 0.40 + 0.06 * rng.random(), 220, 2400, 0.5 + 0.1 * rng.random(), 0.35))
    for i in range(1, 7):
        save("clash_%d" % i, clash(rng, 1500 + 900 * rng.random()))
    for i in range(1, 4):
        save("draw_%d" % i, draw(rng))


def armour_ring(rng):
    """A blow on plate: a hard tick, then a low ring of a big, thick plate (lower and slower to die than a clash)."""
    n = int(0.5 * RATE)
    t = np.arange(n) / RATE
    f0 = 520 + 260 * rng.random()
    out = np.zeros(n)
    for k, ratio in enumerate((1.0, 2.32, 4.18, 6.7)):
        out += np.sin(2 * np.pi * f0 * ratio * t + rng.random() * 6.28) * np.exp(-(7.0 + 6.0 * k) * t) * (1.0 / (1 + k * 0.8))
    tick = band(rng.standard_normal(n), 1800, 7000) * np.exp(-110.0 * t) * 1.4
    thump = np.sin(2 * np.pi * 110 * t) * np.exp(-40.0 * t) * 0.8
    return (out + tick + thump) * np.minimum(1.0, np.arange(n) / 25.0)


def armour_rasp(rng):
    """A blow on mail: a cluster of tiny, bright rings knocking on each other, a rasp more than a ring."""
    n = int(0.32 * RATE)
    t = np.arange(n) / RATE
    out = np.zeros(n)
    for _ in range(28):
        at = int(rng.random() ** 1.6 * 0.14 * RATE)
        f = 3200 + 4200 * rng.random()
        length = n - at
        tt = np.arange(length) / RATE
        out[at:] += np.sin(2 * np.pi * f * tt + rng.random() * 6.28) * np.exp(-(160.0 + 120.0 * rng.random()) * tt) * (0.3 + 0.7 * rng.random())
    rasp = band(rng.standard_normal(n), 2500, 9000) * np.exp(-30.0 * t) * 0.5
    low = np.sin(2 * np.pi * 150 * t) * np.exp(-50.0 * t) * 0.35
    return out * 0.35 + rasp + low


def armour_thud(rng):
    """A blow on leather or padding: a low thump that dies at once, with a soft slap of cloth on top."""
    n = int(0.22 * RATE)
    t = np.arange(n) / RATE
    f = 85 + 60 * rng.random()
    body = np.sin(2 * np.pi * (f + 60 * np.exp(-30.0 * t)) * t) * np.exp(-24.0 * t)
    slap = band(rng.standard_normal(n), 300, 2200) * np.exp(-60.0 * t) * 0.7
    return (body + slap) * np.minimum(1.0, np.arange(n) / 20.0)


def stone_chip(rng):
    """Steel on stone: a short dry tick, a grit of chips falling, no ring at all."""
    n = int(0.24 * RATE)
    t = np.arange(n) / RATE
    tick = band(rng.standard_normal(n), 1200, 6500) * np.exp(-150.0 * t) * 1.5
    grit = np.zeros(n)
    for _ in range(6):
        at = int((0.02 + 0.16 * rng.random()) * RATE)
        grit[at:] += band(rng.standard_normal(n - at), 2000, 8000) * np.exp(-np.arange(n - at) / RATE * 220.0) * 0.35
    knock = np.sin(2 * np.pi * (230 + 90 * rng.random()) * t) * np.exp(-60.0 * t) * 0.5
    return tick + grit + knock


def hit_sounds():
    """Blows on armour and stone (Hackers 'n Slashers patch 3); a rng of its own, so the files above never change."""
    rng = np.random.default_rng(2241)
    for i in range(1, 4):
        save("armour_ring_%d" % i, armour_ring(rng))
        save("armour_rasp_%d" % i, armour_rasp(rng))
        save("armour_thud_%d" % i, armour_thud(rng))
    for i in range(1, 3):
        save("stone_chip_%d" % i, stone_chip(rng))


if __name__ == "__main__":
    main()
    hit_sounds()
