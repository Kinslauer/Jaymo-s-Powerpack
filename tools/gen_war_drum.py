"""Synthesizes the war drum an orc or barbarian band beats as it marches on a town (medieval picks, vp-patrols: Andy, "maybe
drums from approaching orc and barbarian warbands"): kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/war_drum.ogg.
Run it from the repository root:

    python3 -I tools/gen_war_drum.py

About two seconds, mono (so the game places it where the band is): one bar of a big hide drum, a heavy stroke and two
lighter ones after it, each a deep falling boom with the slap of the skin on top, and the rumble of the shell after.
Everything is generated from nothing: no recordings, no samples. Needs numpy and ffmpeg with libvorbis. The same seed
gives the same file.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "war_drum.ogg")
SR = 44100
LENGTH = 2.1
# the bar: (seconds in, how hard)
STROKES = [(0.02, 1.0), (0.62, 0.62), (0.94, 0.7)]
rng = np.random.default_rng(2204)


def t_axis(seconds):
    return np.arange(int(seconds * SR)) / SR


def band(x, low, high):
    """Band-pass by FFT (smooth edges)."""
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    gain = 1.0 / (1.0 + (low / np.maximum(f, 1.0)) ** 4) / (1.0 + (f / high) ** 4)
    return np.fft.irfft(spec * gain, len(x))


def place(out, sound, at):
    start = int(at * SR)
    end = min(len(out), start + len(sound))
    out[start:end] += sound[: end - start]


def stroke(hard):
    """One stroke: the skin's boom falling in pitch, its second mode, the slap, and the shell's rumble."""
    t = t_axis(1.3)
    f = 46.0 + 38.0 * np.exp(-t / 0.05) * hard
    boom = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t / (0.32 + 0.1 * hard))
    mode = np.sin(2.0 * np.pi * np.cumsum(f * 1.59) / SR + rng.uniform(0, 6.28)) * np.exp(-t / 0.16) * 0.35
    slap = band(rng.standard_normal(len(t)), 300.0, 2400.0) * np.exp(-t / 0.018) * 0.55
    rumble = band(rng.standard_normal(len(t)), 40.0, 160.0) * np.exp(-t / 0.4) * 0.5
    attack = np.clip(t / 0.003, 0.0, 1.0)
    return (boom * 1.2 + mode + slap + rumble) * attack * hard


def reverb(x, seconds=1.2, wet=0.22):
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.22 * SR))
    ir = band(ir, 60.0, 3000.0)
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    tail = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x)]
    return x + wet * tail


def main():
    out = np.zeros(int(LENGTH * SR))
    for at, hard in STROKES:
        place(out, stroke(hard), at)
    out = reverb(out)
    out = np.tanh(out / np.max(np.abs(out)) * 1.6)
    out *= 10 ** (-1.0 / 20.0) / np.max(np.abs(out))
    fade = int(0.2 * SR)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    pcm = (out * 32767.0).astype(np.int16)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "drum.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", OUT], check=True)
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
