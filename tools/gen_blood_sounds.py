"""Our own blood sounds, synthesised from noise and tones (nothing sampled, nothing borrowed).

    python3 tools/gen_blood_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/blood/:
  squelch_1..6   a foot coming down in a fresh pool: a soft low press, then a wet, sucking release
  hit_1..6       a blade or a shot biting into a bleeding body: a dull thud under a short wet slap
  burst_1..3     a body crushed or blown open: a heavy wet crunch with a spatter after it
Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "sounds", "blood")


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


def bubbles(rng, n, count, low, high, start, spread):
    """Little wet pops: short sine chirps that rise as a bubble closes, scattered after `start`."""
    out = np.zeros(n)
    for _ in range(count):
        at = int((start + rng.random() * spread) * RATE)
        length = int((0.012 + 0.025 * rng.random()) * RATE)
        if at + length >= n:
            continue
        t = np.arange(length) / RATE
        f = low + (high - low) * rng.random()
        # a closing bubble's pitch climbs
        phase = 2 * np.pi * (f * t + f * 2.5 * t * t / (length / RATE))
        out[at:at + length] += np.sin(phase) * np.exp(-t * (90 + 80 * rng.random())) * (0.4 + 0.6 * rng.random())
    return out


def squelch(rng):
    """A foot in a pool: a muffled press, then the suck of the sole lifting, with a few pops."""
    seconds = 0.42 + 0.08 * rng.random()
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    press = band(noise, 60, 420) * np.exp(-((t - 0.03) ** 2) / 0.0009)
    suck_at = 0.14 + 0.05 * rng.random()
    suck_env = np.exp(-((t - suck_at) ** 2) / 0.004) * (0.5 + 0.5 * np.sin(2 * np.pi * (28 + 12 * rng.random()) * t) ** 2)
    suck = band(noise, 250, 1400 + 600 * rng.random()) * suck_env
    pops = bubbles(rng, n, 5 + rng.integers(0, 5), 350, 900, suck_at - 0.03, 0.18)
    out = press * 1.1 + suck * 0.8 + pops * 0.35
    return out * np.minimum(1.0, t * 300.0) * np.minimum(1.0, (seconds - t) * 20.0)


def hit(rng):
    """A bite into flesh: a low thud with a short wet slap on top and a little spatter."""
    seconds = 0.32
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    f0 = 70 + 30 * rng.random()
    thud = np.sin(2 * np.pi * f0 * t * (1.0 - 0.6 * t)) * np.exp(-t * 28)
    slap = band(noise, 500, 3200 + 1500 * rng.random()) * np.exp(-t * (45 + 20 * rng.random()))
    spatter = band(noise, 1500, 6000) * np.exp(-((t - 0.07) ** 2) / 0.0012) * 0.25
    pops = bubbles(rng, n, 3 + rng.integers(0, 4), 500, 1300, 0.02, 0.12)
    out = thud * 1.0 + slap * 0.75 + spatter + pops * 0.25
    return out * np.minimum(1.0, t * 800.0)


def burst(rng):
    """A body crushed or blown open: a heavy wet crunch, then blood spattering down."""
    seconds = 0.9
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    f0 = 50 + 20 * rng.random()
    body = np.sin(2 * np.pi * f0 * t * (1.0 - 0.5 * t)) * np.exp(-t * 14)
    crunch = band(noise, 300, 2500) * np.exp(-t * 22) * (0.6 + 0.4 * (rng.random(n) > 0.92))
    rain = np.zeros(n)
    for _ in range(40):
        at = int((0.12 + 0.6 * rng.random()) * RATE)
        length = int(0.02 * RATE)
        if at + length < n:
            rain[at:at + length] += band(rng.standard_normal(length), 900, 5000) * np.exp(-np.arange(length) / (0.004 * RATE)) * rng.random()
    pops = bubbles(rng, n, 12, 300, 1000, 0.05, 0.4)
    out = body * 1.2 + crunch * 0.9 + rain * 0.35 + pops * 0.3
    return out * np.minimum(1.0, t * 600.0) * np.minimum(1.0, (seconds - t) * 8.0)


def main():
    for i in range(6):
        save(f"squelch_{i + 1}", squelch(np.random.default_rng(100 + i)))
        save(f"hit_{i + 1}", hit(np.random.default_rng(200 + i)))
    for i in range(3):
        save(f"burst_{i + 1}", burst(np.random.default_rng(300 + i)))


if __name__ == "__main__":
    main()
