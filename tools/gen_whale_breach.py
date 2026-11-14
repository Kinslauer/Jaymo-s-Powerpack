"""Synthesizes the aerwhale's breach for its entrance film (Andy: "What if we played a loud whale breech"):
kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/film/whale_breach.ogg. Run it from the repository root:

    python3 -I tools/gen_whale_breach.py

About five seconds, mono (so the game places it where the whale is): a rush of air as it surges up, the crash as it
bursts through, a long rising and falling whale call over it, then the spray falling back. Everything is generated
from nothing: no recordings, no samples. Needs numpy and ffmpeg with libvorbis. The same seed gives the same file.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "film", "whale_breach.ogg")
SR = 44100
LENGTH = 5.2
# when the whale bursts through (seconds into the file); the rush leads up to it
IMPACT = 0.62
rng = np.random.default_rng(1455)


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


def rush():
    """Air torn open as it surges up: noise swept up through a band, swelling to the impact."""
    n = int(IMPACT * SR + 0.15 * SR)
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    # swept band, in short overlapping frames
    frame, hop = 2048, 512
    win = np.hanning(frame)
    for i in range(0, n - frame, hop):
        k = i / n
        centre = 180.0 + 1600.0 * k ** 1.6
        out[i:i + frame] += band(noise[i:i + frame] * win, centre * 0.5, centre * 1.8) * win
    env = np.clip(np.arange(n) / (IMPACT * SR), 0.0, 1.0) ** 2.2
    tail = np.exp(-np.clip(np.arange(n) - IMPACT * SR, 0.0, None) / (0.05 * SR))
    return out * env * tail * 0.9


def crash():
    """The burst: a deep thump and a wide crack of spray, decaying."""
    t = t_axis(2.4)
    f = 52.0 * np.exp(-t / 0.35) + 30.0
    thump = np.sin(2.0 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.45) * 1.2
    crack = band(rng.standard_normal(len(t)), 120.0, 4200.0) * np.exp(-t / 0.28) * 1.4
    hiss = band(rng.standard_normal(len(t)), 1500.0, 9000.0) * np.exp(-t / 0.7) * 0.35
    attack = np.clip(t / 0.006, 0.0, 1.0)
    return (thump + crack + hiss) * attack


def call():
    """The whale's call: a low moan that climbs, holds with a slow waver, and sinks away."""
    dur = 3.4
    t = t_axis(dur)
    k = t / dur
    # pitch path (Hz): up from 72 to 190, a hold, then down to 105
    f0 = np.interp(k, [0.0, 0.18, 0.45, 0.62, 1.0], [72.0, 190.0, 182.0, 160.0, 105.0])
    f0 = f0 * (1.0 + 0.018 * np.sin(2.0 * np.pi * 4.6 * t) * np.clip((k - 0.15) * 4.0, 0.0, 1.0))
    phase = 2.0 * np.pi * np.cumsum(f0) / SR
    voice = np.zeros(len(t))
    for h in range(1, 14):
        fh = f0 * h
        # two broad formants give it a throat
        weight = np.exp(-((fh - 420.0) / 260.0) ** 2) + 0.7 * np.exp(-((fh - 1050.0) / 380.0) ** 2) + 0.25 / h
        voice += weight * np.sin(h * phase + rng.uniform(0.0, 2.0 * np.pi))
    # a little breath and grit in it
    grit = 1.0 + 0.12 * np.sin(2.0 * np.pi * 31.0 * t)
    breath = band(rng.standard_normal(len(t)), 300.0, 2200.0) * 0.06
    env = np.clip(k / 0.12, 0.0, 1.0) ** 1.5 * np.clip((1.0 - k) / 0.35, 0.0, 1.0) ** 1.3
    return (voice * grit + breath) * env * 0.55


def spray():
    """The spray falling back: many small splashes, thinning out."""
    out = np.zeros(int(3.6 * SR))
    for _ in range(160):
        at = rng.uniform(0.0, 1.0) ** 1.8 * 3.0
        n = int(rng.uniform(0.04, 0.16) * SR)
        lo = rng.uniform(400.0, 1400.0)
        grain = band(rng.standard_normal(n), lo, lo * rng.uniform(2.5, 5.0))
        grain *= np.exp(-np.arange(n) / (n * 0.25)) * rng.uniform(0.05, 0.22) * (1.0 - at / 3.4)
        place(out, grain, at)
    return out


def reverb(x, seconds=1.9, wet=0.28):
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (0.32 * SR))
    ir = band(ir, 90.0, 5000.0)
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    tail = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x)]
    return x + wet * tail


def main():
    out = np.zeros(int(LENGTH * SR))
    place(out, rush(), 0.0)
    place(out, crash(), IMPACT)
    place(out, call(), IMPACT - 0.12)
    place(out, spray(), IMPACT + 0.25)
    out = reverb(out)
    # loud but rounded: soft-clip, then a -1 dB peak
    out = np.tanh(out / np.max(np.abs(out)) * 1.8)
    out *= 10 ** (-1.0 / 20.0) / np.max(np.abs(out))
    fade = int(0.25 * SR)
    out[-fade:] *= np.linspace(1.0, 0.0, fade)
    pcm = (out * 32767.0).astype(np.int16)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "breach.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", OUT], check=True)
    print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
