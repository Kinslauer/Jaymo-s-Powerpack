"""Writes the barrage clash's drum track and stings (Kingdoms 0.7.467), our own, synthesised from scratch.

The theme's big drum hits fall exactly on SpiritBarrage.BEATS (ticks of 50 ms), so the drum is the clock players strike to:
two count-in clicks 10 ticks apart, then 6 beats every 10 ticks, 6 every 8, 8 every 6. Between the beats run fast fist-like
ticks, under it a minor bass ostinato that climbs each section, over it a riser.

  python3 tools/barrage_music.py   (needs numpy and ffmpeg with libvorbis)

Writes kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/spirit/barrage_{theme,rush,draw}.ogg
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
TICK = 0.05
LEAD_IN = 20
COUNTS, GAPS = [6, 6, 8], [10, 8, 6]
OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "spirit")
rng = np.random.default_rng(467)


def beats():
    out, t = [], LEAD_IN
    for n, g in zip(COUNTS, GAPS):
        for _ in range(n):
            out.append((t, g))
            t += g
    return out


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-t / decay)


def lowpass(x, cutoff):
    # a simple one-pole, run twice
    k = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i, v in enumerate(x):
            acc = (1 - k) * v + k * acc
            y[i] = acc
        x = y
    return x


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def taiko(length=0.6, low=52.0, high=130.0, gain=1.0):
    n = int(SR * length)
    t = np.arange(n) / SR
    freq = low + (high - low) * np.exp(-t * 28)
    phase = 2 * np.pi * np.cumsum(freq) / SR
    body = np.sin(phase) * env(n, 0.002, 0.22)
    skin = lowpass(rng.standard_normal(n), 1800) * env(n, 0.001, 0.03) * 0.8
    return (body + skin) * gain


def clap(gain=0.5):
    n = int(SR * 0.18)
    noise = highpass(lowpass(rng.standard_normal(n), 5000), 900)
    e = np.zeros(n)
    for off in (0.0, 0.008, 0.017):
        i = int(off * SR)
        e[i:] += env(n - i, 0.0005, 0.03 if off < 0.017 else 0.07)
    return noise * e * gain


def tick_hit(gain=0.25, bright=6000):
    n = int(SR * 0.05)
    return highpass(lowpass(rng.standard_normal(n), bright), 2500) * env(n, 0.0005, 0.012) * gain


def click(gain=0.6, freq=1700):
    n = int(SR * 0.08)
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * freq * t) * env(n, 0.0005, 0.02) * gain


def bass(freq, length, gain=0.35):
    n = int(SR * length)
    t = np.arange(n) / SR
    saw = 2 * ((t * freq) % 1.0) - 1
    saw += 0.5 * (2 * ((t * freq * 1.005) % 1.0) - 1)
    return lowpass(saw, 420) * env(n, 0.004, length * 0.6) * gain


def crash(length=1.6, gain=0.5):
    n = int(SR * length)
    noise = highpass(rng.standard_normal(n), 3500)
    return noise * env(n, 0.001, length * 0.35) * gain


def gong(length=1.4, gain=0.5):
    n = int(SR * length)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for f, a in ((92, 1.0), (151, 0.6), (233, 0.4), (318, 0.25)):
        out += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6))
    return out * env(n, 0.003, length * 0.4) * gain


def place(track, sound, at):
    i = int(round(at * SR))
    j = min(len(track), i + len(sound))
    if i < len(track):
        track[i:j] += sound[: j - i]


def theme():
    bs = beats()
    end = (bs[-1][0] + 12) * TICK
    track = np.zeros(int(SR * (end + 0.6)))
    # the count-in: two clicks on the barrage's first tempo
    place(track, click(0.7, 1500), 0.0)
    place(track, click(0.8, 2000), 10 * TICK)
    # the bass: A minor, climbing a step each section (A, C, E), on every beat
    roots = [55.0, 65.41, 82.41]
    sec_of = []
    for s, n in enumerate(COUNTS):
        sec_of += [s] * n
    for idx, (b, g) in enumerate(bs):
        s = sec_of[idx]
        at = b * TICK
        # the beat: a big drum (harder as it speeds up) and a clap; every other beat a fifth over the root
        place(track, taiko(gain=1.0 + 0.15 * s), at)
        place(track, clap(0.35 + 0.1 * s), at)
        root = roots[s] * (1.5 if idx % 2 else 1.0)
        place(track, bass(root, g * TICK * 0.95), at)
        # between beats: fists, four to a beat
        for q in range(1, 4):
            place(track, tick_hit(0.18 + 0.05 * s, 5000 + 1500 * q), at + g * TICK * q / 4)
    # the last beat lands with a crash
    place(track, crash(1.0, 0.35), bs[-1][0] * TICK)
    # a riser under all of it, opening up
    n = len(track)
    t = np.arange(n) / SR
    rise = lowpass(rng.standard_normal(n), 900) * (t / t[-1]) ** 2 * 0.12
    track += rise
    return track


def rush():
    track = np.zeros(int(SR * 1.9))
    # a roll speeding up over a second, then the blow
    at, gap = 0.0, 0.09
    while at < 1.0:
        place(track, taiko(0.25, 70, 160, 0.55 + 0.4 * at), at)
        place(track, tick_hit(0.3), at + gap / 2)
        at += gap
        gap = max(0.035, gap * 0.9)
    place(track, taiko(0.9, 40, 110, 1.5), 1.02)
    place(track, crash(0.9, 0.55), 1.02)
    place(track, gong(0.9, 0.35), 1.02)
    return track


def draw():
    track = np.zeros(int(SR * 1.6))
    place(track, taiko(0.9, 45, 120, 1.3), 0.0)
    place(track, crash(1.4, 0.45), 0.0)
    place(track, gong(1.5, 0.45), 0.02)
    return track


def write(name, track):
    peak = np.max(np.abs(track))
    track = np.tanh(track / peak * 1.3) / np.tanh(1.3) * 0.9
    pcm = (track * 32767).astype(np.int16)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5",
                        os.path.join(OUT, name + ".ogg")], check=True)


if __name__ == "__main__":
    write("barrage_theme", theme())
    write("barrage_rush", rush())
    write("barrage_draw", draw())
