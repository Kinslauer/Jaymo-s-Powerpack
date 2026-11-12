"""Synthesizes the great foes' themes (kingdoms/src/main/resources/assets/jaymoskingdoms/sounds/foe/<theme>/<stem>.ogg) and
writes their entries into kingdoms/src/main/resources/assets/jaymoskingdoms/sounds.json. Run it from the repository root:

    python3 -I tools/gen_foe_themes.py

Every theme is a handful of short mono stems at a low sample rate: two drones, two pulses (drums), three motifs (a few
notes of a tune), and a stab for when the fight turns. The client (FoeMusicClient) plays them one at a time at uneven
intervals with a jittered pitch, so the stems recombine and the theme never reads as one loop. Each theme has its own
root, mode, tempo and timbre. Needs numpy and ffmpeg with libvorbis. Everything is generated from nothing: no recordings,
no samples.
"""
import json
import math
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
OUT = os.path.join(ASSETS, "sounds", "foe")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
SR = 22050
MODES = {
    "dorian": [0, 2, 3, 5, 7, 9, 10],
    "phrygian": [0, 1, 3, 5, 7, 8, 10],
    "lydian": [0, 2, 4, 6, 7, 9, 11],
    "locrian": [0, 1, 3, 5, 6, 8, 10],
    "minor": [0, 2, 3, 5, 7, 8, 10],
    "pentatonic": [0, 3, 5, 7, 10],
    "harmonic": [0, 2, 3, 5, 7, 8, 11],
    "mixolydian": [0, 2, 4, 5, 7, 9, 10],
    "dim": [0, 2, 3, 5, 6, 8, 9, 11],
}
# theme: root note (MIDI), mode, tempo (bpm), drone timbre, drum timbre, motif timbre, motif note unit (beats)
THEMES = {
    "highfield": dict(root=38, mode="dorian", bpm=84, drone="open", drum="tom", lead="horn", unit=1.0),
    "magnetic": dict(root=42, mode="phrygian", bpm=108, drone="beat", drum="clang", lead="metal", unit=0.5),
    "arctic": dict(root=40, mode="lydian", bpm=60, drone="glass", drum="soft", lead="bell", unit=1.5),
    "irradiated": dict(root=36, mode="locrian", bpm=126, drone="sub", drum="tick", lead="saw", unit=0.5),
    "gaunt": dict(root=35, mode="harmonic", bpm=48, drone="breath", drum="bone", lead="wail", unit=2.0),
    "ogre": dict(root=33, mode="pentatonic", bpm=72, drone="open", drum="stomp", lead="horn", unit=1.0),
    "wraith": dict(root=43, mode="dim", bpm=66, drone="choir", drum="soft", lead="bell", unit=1.5),
    "werewolf": dict(root=37, mode="minor", bpm=96, drone="breath", drum="tom", lead="wail", unit=0.75),
    "dragon": dict(root=31, mode="mixolydian", bpm=78, drone="open", drum="tom", lead="horn", unit=1.0),
    "warlord": dict(root=38, mode="phrygian", bpm=100, drone="sub", drum="stomp", lead="horn", unit=0.5),
}
STEMS = ["drone_a", "drone_b", "pulse_a", "pulse_b", "motif_a", "motif_b", "motif_c", "stab"]


def hz(midi):
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def lowpass(x, cutoff, slope=4.0):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    spec *= 1.0 / (1.0 + (f / cutoff) ** (2 * slope))
    return np.fft.irfft(spec, len(x))


def bandpass(x, centre, width):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    spec *= np.exp(-0.5 * ((f - centre) / width) ** 2)
    return np.fft.irfft(spec, len(x))


def saw(freq, n, harmonics=14):
    tt = np.arange(n) / SR
    out = np.zeros(n)
    for k in range(1, harmonics + 1):
        if freq * k > SR / 2 - 500:
            break
        out += np.sin(2 * math.pi * freq * k * tt) / k
    return out


def env(n, attack, release, curve=1.0):
    a = max(1, int(attack * SR))
    r = max(1, int(release * SR))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a) ** curve
    e[-r:] = np.minimum(e[-r:], np.linspace(1, 0, r) ** curve)
    return e


def decay(n, tau):
    return np.exp(-np.arange(n) / SR / tau)


def drone(rng, kind, root, mode, seconds, second_note):
    n = int(seconds * SR)
    f0 = hz(root)
    f1 = hz(root + MODES[mode][second_note % len(MODES[mode])])
    tt = np.arange(n) / SR
    swell = env(n, seconds * 0.35, seconds * 0.4, 1.5)
    if kind == "open":
        x = saw(f0, n, 8) * 0.6 + saw(f0 * 1.5, n, 6) * 0.35 + np.sin(2 * math.pi * f0 / 2 * tt) * 0.5
        x = lowpass(x, 700)
    elif kind == "beat":
        x = np.sin(2 * math.pi * f0 * tt) + np.sin(2 * math.pi * (f0 + 3.1) * tt) + 0.5 * saw(f0 * 2, n, 5) * np.sin(2 * math.pi * 0.7 * tt) ** 2
        x = lowpass(x, 900)
    elif kind == "glass":
        x = sum(np.sin(2 * math.pi * f0 * m * tt + rng.uniform(0, 6)) * a for m, a in ((2, 0.5), (3, 0.4), (4.01, 0.3), (6.02, 0.2)))
        x = x * (0.7 + 0.3 * np.sin(2 * math.pi * 0.4 * tt))
    elif kind == "sub":
        x = np.sin(2 * math.pi * f0 / 2 * tt) * 1.2 + np.sin(2 * math.pi * f0 * tt) * 0.4 * (np.sin(2 * math.pi * 2.1 * tt) > 0)
        x = lowpass(x + 0.25 * saw(f1, n, 5), 500)
    elif kind == "breath":
        noise = rng.standard_normal(n)
        x = bandpass(noise, f0 * 4, f0 * 1.5) * 4.0 * (0.6 + 0.4 * np.sin(2 * math.pi * 0.23 * tt)) + np.sin(2 * math.pi * f0 * tt) * 0.4
    else:  # choir
        x = np.zeros(n)
        for ratio, vib in ((1.0, 4.7), (1.5, 5.3), (2.0, 5.0)):
            src = saw(f0 * ratio * 2, n, 24) * (1 + 0.01 * np.sin(2 * math.pi * vib * tt))
            x += bandpass(src, 700, 160) + 0.6 * bandpass(src, 1100, 200)
        x = x * 0.5 * (0.7 + 0.3 * np.sin(2 * math.pi * 5.1 * tt))
    # a second voice a mode step above, quiet, so the two drones differ
    x = x + 0.25 * lowpass(saw(f1, n, 6), 800)
    return x * swell


def hit(rng, kind, n_sec=0.5, base=None):
    n = int(n_sec * SR)
    tt = np.arange(n) / SR
    if kind in ("tom", "stomp"):
        f = (base or (70 if kind == "stomp" else 110)) * (1 + 0.5 * np.exp(-tt * 18))
        body = np.sin(2 * math.pi * np.cumsum(f) / SR) * decay(n, 0.12 if kind == "tom" else 0.18)
        click = lowpass(rng.standard_normal(n), 2000) * decay(n, 0.01) * 0.4
        return body + click
    if kind == "clang":
        out = np.zeros(n)
        for ratio, a in ((1.0, 1.0), (2.76, 0.6), (5.4, 0.4), (8.93, 0.25)):
            out += np.sin(2 * math.pi * 180 * ratio * tt) * a * decay(n, 0.25 / ratio ** 0.4)
        return out * 0.6
    if kind == "soft":
        body = np.sin(2 * math.pi * 80 * (1 + 0.3 * np.exp(-tt * 12)) * tt) * decay(n, 0.2)
        return body * 0.8 + lowpass(rng.standard_normal(n), 600) * decay(n, 0.05) * 0.2
    if kind == "tick":
        return bandpass(rng.standard_normal(n), 3000, 1200) * decay(n, 0.006) * 2.0
    # bone: dry clacks
    return bandpass(rng.standard_normal(n), 1400, 400) * decay(n, 0.02) * 2.0 + np.sin(2 * math.pi * 420 * tt) * decay(n, 0.03) * 0.5


def place(buf, sound, at_sec, gain=1.0):
    i = int(at_sec * SR)
    end = min(len(buf), i + len(sound))
    if i < len(buf):
        buf[i:end] += sound[:end - i] * gain


def pulse(rng, theme, pattern):
    beat = 60.0 / theme["bpm"]
    seconds = beat * 4
    buf = np.zeros(int(seconds * SR))
    patterns = {
        0: [(0, 1.0), (1.5, 0.5), (2, 0.8), (3.25, 0.6)],
        1: [(0, 1.0), (0.75, 0.4), (2.5, 0.9), (3, 0.5), (3.5, 0.5)],
    }
    for pos, vel in patterns[pattern]:
        place(buf, hit(rng, theme["drum"], 0.6), pos * beat + rng.uniform(0, 0.012), vel)
    if theme["drum"] in ("tick", "bone"):
        for _ in range(int(seconds * 5)):
            place(buf, hit(rng, theme["drum"], 0.1), rng.uniform(0, seconds - 0.1), rng.uniform(0.2, 0.5))
    return lowpass(buf, 9000) * env(len(buf), 0.002, 0.08)


def voice(kind, freq, seconds):
    n = int(seconds * SR)
    tt = np.arange(n) / SR
    if kind == "horn":
        x = lowpass(saw(freq, n, 12), freq * 4) * env(n, 0.12, seconds * 0.4)
    elif kind == "metal":
        x = np.sin(2 * math.pi * freq * tt + 2.5 * np.sin(2 * math.pi * freq * 1.41 * tt)) * decay(n, seconds * 0.45)
    elif kind == "bell":
        x = sum(np.sin(2 * math.pi * freq * r * tt) * a * decay(n, seconds * 0.5 / r ** 0.5) for r, a in ((1, 1), (2.0, 0.5), (2.99, 0.3), (4.2, 0.2)))
    elif kind == "saw":
        x = lowpass(saw(freq, n, 10), 1800) * env(n, 0.01, seconds * 0.5)
        x = np.tanh(x * 2.0) * (0.6 + 0.4 * (np.sin(2 * math.pi * 11 * tt) > 0))
    else:  # wail: a voice sliding up into the note
        glide = freq * (0.92 + 0.08 * np.minimum(1.0, tt / 0.5))
        phase = np.cumsum(glide) / SR * 2 * math.pi
        x = sum(np.sin(phase * k) / k for k in range(1, 8)) * (1 + 0.02 * np.sin(2 * math.pi * 5.5 * tt)) * env(n, 0.25, seconds * 0.45)
        x = bandpass(x, 900, 500) * 3.0 + 0.3 * x
    return x


def motif(rng, theme, seconds=4.5):
    scale = MODES[theme["mode"]]
    unit = theme["unit"] * 60.0 / theme["bpm"]
    buf = np.zeros(int(seconds * SR))
    degree = int(rng.integers(0, len(scale)))
    count = int(rng.integers(3, 6))
    at = rng.uniform(0.1, 0.5)
    octave = 24 if theme["lead"] in ("bell", "metal") else 12
    for _ in range(count):
        step = int(rng.choice([-2, -1, 1, 2, 3, -3]))
        degree = int(np.clip(degree + step, 0, len(scale) * 2 - 1))
        note = theme["root"] + octave + scale[degree % len(scale)] + 12 * (degree // len(scale))
        length = unit * float(rng.choice([1, 1, 2, 3]))
        place(buf, voice(theme["lead"], hz(note), min(length + 0.8, 3.0)), at, 0.5)
        at += length
        if at > seconds - 1.0:
            break
    return buf


def stab(rng, theme):
    seconds = 3.5
    n = int(seconds * SR)
    tt = np.arange(n) / SR
    f0 = hz(theme["root"] + 12)
    rise = f0 * (1.0 + 0.5 * (tt / seconds) ** 2)
    x = np.sin(np.cumsum(rise) / SR * 2 * math.pi) + 0.5 * np.sin(np.cumsum(rise * 1.5) / SR * 2 * math.pi)
    x = (x + 0.3 * lowpass(rng.standard_normal(n), 3000)) * env(n, seconds * 0.75, 0.3, 2.0)
    place(x, hit(rng, theme["drum"], 0.6, 55), seconds - 0.7, 1.2)
    return x * 0.8


def normalise(x, peak=0.6):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def encode(samples, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    pcm = (np.clip(samples, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "x.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "0", "-ac", "1", path], check=True)


def main():
    entries = {}
    for name, theme in THEMES.items():
        rng = np.random.default_rng(sum(ord(c) * (i + 1) for i, c in enumerate(name)))
        stems = {
            "drone_a": drone(rng, theme["drone"], theme["root"], theme["mode"], 6.5, 4),
            "drone_b": drone(rng, theme["drone"], theme["root"] - 5, theme["mode"], 6.0, 2),
            "pulse_a": pulse(rng, theme, 0),
            "pulse_b": pulse(rng, theme, 1),
            "motif_a": motif(rng, theme),
            "motif_b": motif(rng, theme),
            "motif_c": motif(rng, theme),
            "stab": stab(rng, theme),
        }
        for stem in STEMS:
            encode(normalise(stems[stem]), os.path.join(OUT, name, stem + ".ogg"))
            entries["foe_theme.%s.%s" % (name, stem)] = {
                "subtitle": "subtitles.jaymoskingdoms.foe_theme",
                "sounds": [{"name": "jaymoskingdoms:foe/%s/%s" % (name, stem)}],
            }
    with open(SOUNDS_JSON, encoding="utf-8") as f:
        sounds = json.load(f)
    sounds = {k: v for k, v in sounds.items() if not k.startswith("foe_theme.")}
    sounds.update(entries)
    with open(SOUNDS_JSON, "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent="\t")
        f.write("\n")
    print("wrote", len(entries), "stems")


if __name__ == "__main__":
    main()
