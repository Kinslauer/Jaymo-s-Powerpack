"""Synthesizes the Hollow's blight cough (Andy's "That's no regular cough"):
kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/blight/cough_<n>.ogg. Run it from the repository root:

    python3 -I tools/gen_blight_cough.py

Four short wet, rattling coughs, mono (the game places them at the cougher): each a few bursts of breath noise through a
throat band, a low rough voicing under it, and a catch of phlegm between bursts. Generated from nothing: no recordings,
no samples. Needs numpy and ffmpeg with libvorbis. The same seed gives the same files.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "blight")
SR = 44100
VARIANTS = 4
rng = np.random.default_rng(1600)


def band(x, low, high):
    """Band-pass by FFT (smooth edges)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    gain = 1.0 / (1.0 + (low / np.maximum(f, 1.0)) ** 4) / (1.0 + (f / high) ** 4)
    return np.fft.irfft(spec * gain, len(x))


def burst(length, pitch):
    """One cough: a hard onset of breath, a rough voiced bark under it, dying away."""
    n = int(length * SR)
    t = np.arange(n) / SR
    env = (1.0 - np.exp(-t / 0.006)) * np.exp(-t / (length * 0.32))
    breath = band(rng.standard_normal(n), 350.0, 3200.0)
    # the bark: a buzzing voice, its pitch falling, roughened by jitter
    f0 = pitch * (1.0 - 0.35 * t / length) * (1.0 + 0.04 * rng.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1)))
    phase = 2.0 * np.pi * np.cumsum(f0) / SR
    voice = np.sign(np.sin(phase)) * 0.5 + np.sin(2.0 * phase) * 0.3
    voice = band(voice, 120.0, 1800.0)
    # the wet rattle: amplitude flutter at 25-40 Hz
    rattle = 1.0 + 0.5 * np.sin(2.0 * np.pi * rng.uniform(25.0, 40.0) * t)
    out = (breath * 0.8 + voice * 0.5) * env * rattle
    return out / (np.max(np.abs(out)) + 1e-9)


def catch(length):
    """The breath caught between coughs: a short, low, wheezing inhale."""
    n = int(length * SR)
    t = np.arange(n) / SR
    env = np.sin(np.pi * t / length) ** 2
    wheeze = band(rng.standard_normal(n), 900.0, 2600.0) * env * 0.18
    return wheeze


def cough(index):
    bursts = 2 + index % 3
    pitch = rng.uniform(150.0, 210.0)
    parts = []
    for b in range(bursts):
        parts.append(burst(rng.uniform(0.14, 0.24) * (1.25 if b == 0 else 1.0), pitch * (1.0 - 0.06 * b)))
        if b < bursts - 1:
            parts.append(np.zeros(int(rng.uniform(0.03, 0.07) * SR)))
            if rng.random() < 0.6:
                parts.append(catch(rng.uniform(0.08, 0.14)))
    out = np.concatenate(parts + [np.zeros(int(0.05 * SR))])
    return out / (np.max(np.abs(out)) + 1e-9) * 0.85


def write(path, samples):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        name = tmp.name
    try:
        with wave.open(name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((np.clip(samples, -1.0, 1.0) * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", name, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    finally:
        os.remove(name)


def main():
    os.makedirs(OUT, exist_ok=True)
    for i in range(VARIANTS):
        write(os.path.join(OUT, "cough_%d.ogg" % i), cough(i))
    print("wrote %d coughs to %s" % (VARIANTS, OUT))


if __name__ == "__main__":
    main()
