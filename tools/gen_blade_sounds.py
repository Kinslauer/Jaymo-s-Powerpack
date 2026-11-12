"""Our own blade sounds, synthesised from noise and tones (nothing sampled, nothing borrowed).

    python3 tools/gen_blade_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/blade/:
  swing_light_1..5    a quick, thin cut of air (knives, sabers, a rapier)
  swing_medium_1..5   a sword's swing: fuller, a little longer
  swing_heavy_1..5    a great weapon's: low, slow, with weight behind it
  clash_1..6          steel on steel: a hard tick and a short bright ring of clashing partials
  draw_1..3           a blade leaving its scabbard: a rising scrape that ends on a faint ring
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


if __name__ == "__main__":
    main()
