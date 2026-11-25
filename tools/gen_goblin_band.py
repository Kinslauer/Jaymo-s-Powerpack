"""Synthesizes the goblin band's four sounds (village mods patch 8, Andy's pick rb_band: "a drum, a bone flute, a gourd
shaker and a bass jug"): kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/goblin_band/{drum,flute,shaker,jug}.ogg.
Run it from the repository root:

    python3 -I tools/gen_goblin_band.py

Each is one short note, mono, which the game plays at the fire and pitches up or down for the tune (GoblinBands). The
flute and the jug are tuned to A (440 Hz and 110 Hz), so a pitch of 2^(n/12) plays n semitones off it. Everything is
generated from nothing: no recordings, no samples. Needs numpy and ffmpeg with libvorbis. The same seed gives the same files.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "goblin_band")
SR = 44100
rng = np.random.default_rng(808)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def band(x, low, high):
    """Band-pass by FFT (smooth edges)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    gain = 1.0 / (1.0 + (low / np.maximum(f, 1.0)) ** 4) / (1.0 + (f / high) ** 4)
    return np.fft.irfft(spec * gain, len(x))


def drum():
    """A small hand drum of stretched hide: a quick falling thump and the slap of a palm."""
    t = t_axis(0.5)
    f = 92.0 + 70.0 * np.exp(-t / 0.03)
    thump = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.12)
    slap = band(rng.standard_normal(len(t)), 500.0, 3000.0) * np.exp(-t / 0.012) * 0.5
    return (thump + slap) * np.clip(t / 0.002, 0.0, 1.0)


def flute():
    """A bone flute's note: a reedy A with a breathy edge, a little wobble, and a soft start."""
    t = t_axis(0.42)
    vib = 1.0 + 0.008 * np.sin(2.0 * np.pi * 5.5 * t)
    phase = 2.0 * np.pi * np.cumsum(440.0 * vib) / SR
    tone = np.sin(phase) + 0.32 * np.sin(2 * phase) + 0.12 * np.sin(3 * phase)
    breath = band(rng.standard_normal(len(t)), 900.0, 4200.0) * 0.18
    env = np.clip(t / 0.04, 0.0, 1.0) * np.clip((0.42 - t) / 0.12, 0.0, 1.0)
    return (tone + breath) * env * 0.7


def shaker():
    """A dried gourd full of seeds: a short rattle of high noise, a quick flick and its fall."""
    t = t_axis(0.22)
    grains = band(rng.standard_normal(len(t)), 2500.0, 9000.0)
    env = np.exp(-t / 0.045) * np.clip(t / 0.006, 0.0, 1.0)
    return grains * env * 0.8


def jug():
    """Blowing across a clay jug's mouth: a round low A with the hoot of the air in it."""
    t = t_axis(0.5)
    phase = 2.0 * np.pi * 110.0 * t
    tone = np.sin(phase) + 0.45 * np.sin(2 * phase + 0.4) + 0.1 * np.sin(3 * phase)
    air = band(rng.standard_normal(len(t)), 80.0, 600.0) * 0.25
    env = np.clip(t / 0.03, 0.0, 1.0) * np.exp(-t / 0.28)
    return (tone + air) * env * 0.9


def write(name, x):
    x = x / max(1e-9, np.max(np.abs(x))) * 0.85
    pcm = (x * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4",
                        os.path.join(OUT, name + ".ogg")], check=True)


if __name__ == "__main__":
    write("drum", drum())
    write("flute", flute())
    write("shaker", shaker())
    write("jug", jug())
    print("goblin band: 4 sounds in", OUT)
