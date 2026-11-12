"""Our own sounds for the parts of ImmersiveFX: Resurrected the pack lost when it came out for lag (PR #1361): footsteps
by material, armour by material, wading and swimming, steam, fire and bubble jets, workstations, dropped items, the
stomach, and beds. ImmersiveFX is AGPL and its sounds are recorded, so nothing of it is used: every take here is
synthesised from noise and tones, the same way as tools/gen_pool_sounds.py.

    python3 tools/gen_immersive_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files into src/main/resources/assets/jaymospowers/sounds/immersive/ and puts the "immersive.*" events
at the top of src/main/resources/assets/jaymospowers/sounds.json (the rest of the file is left as it is). The seeds keep
every file the same from run to run.
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers")
OUT = os.path.join(ROOT, "sounds", "immersive")
SOUNDS_JSON = os.path.join(ROOT, "sounds.json")
PREFIX = "immersive."


# ---- building blocks -------------------------------------------------------------------------------------------------

def clock(seconds):
    n = int(seconds * RATE)
    return n, np.arange(n) / RATE


def band(x, low, high, order=4):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / RATE)
    spec *= 1.0 / (1.0 + (f / high) ** order)
    spec *= 1.0 - 1.0 / (1.0 + (f / max(low, 1.0)) ** order)
    return np.fft.irfft(spec, len(x))


def noise(rng, n, low, high):
    return band(rng.standard_normal(n), low, high)


def env(t, attack, decay, at=0.0):
    """Rises over `attack` seconds from `at`, then falls away at `decay` per second."""
    s = t - at
    rise = np.clip(s / max(attack, 1e-4), 0, 1)
    fall = np.exp(-np.maximum(s - attack, 0) * decay)
    return np.where(s < 0, 0.0, rise * fall)


def swell(t, peak, width):
    return np.exp(-((t - peak) ** 2) / (2 * width ** 2))


def modal(t, freqs, decays, amps, at=0.0):
    """Struck body: damped sines (metal, wood, stone)."""
    s = np.maximum(t - at, 0)
    out = np.zeros_like(t)
    for f, d, a in zip(freqs, decays, amps):
        out += a * np.sin(2 * np.pi * f * s) * np.exp(-s * d)
    return np.where(t < at, 0.0, out) * np.minimum(1, s * 4000)


def thump(t, f0, decay, at=0.0, drop=0.5):
    """A soft low hit whose pitch sinks a little."""
    s = np.maximum(t - at, 0)
    f = f0 * (1 - drop * np.minimum(1, s * 12))
    phase = 2 * np.pi * np.cumsum(f) / RATE
    return np.where(t < at, 0.0, np.sin(phase) * np.exp(-s * decay) * np.minimum(1, s * 1500))


def grains(rng, n, t, count, spread, low, high, length=0.004, start=0.0, decay_over=None):
    """Many tiny clicks scattered through `spread` seconds: gravel, snow, coins in a heap, chain links."""
    out = np.zeros(n)
    g = int(length * RATE)
    for _ in range(count):
        at = start + rng.random() ** 1.4 * spread
        i = int(at * RATE)
        if i + g >= n:
            continue
        burst = rng.standard_normal(g) * np.exp(-np.arange(g) / (g / 4))
        level = 1.0 if decay_over is None else np.exp(-(at - start) / decay_over)
        out[i:i + g] += burst * level * (0.4 + 0.6 * rng.random())
    return band(out, low, high)


def bubble(t, at, f, rise=1.6, life=0.05):
    """One bubble: a sine whose pitch climbs as it closes."""
    s = np.maximum(t - at, 0)
    fr = f * (1 + rise * np.minimum(1, s / life))
    phase = 2 * np.pi * np.cumsum(fr) / RATE
    return np.where(t < at, 0.0, np.sin(phase) * np.exp(-s / (life * 0.6)) * np.minimum(1, s * 3000))


def bubbles(rng, t, count, spread, low, high, start=0.0, life=(0.02, 0.07)):
    out = np.zeros_like(t)
    for _ in range(count):
        out += bubble(t, start + rng.random() * spread, low + (high - low) * rng.random(), 1.2 + rng.random(),
                      life[0] + (life[1] - life[0]) * rng.random()) * (0.3 + 0.7 * rng.random())
    return out


def creak(rng, t, f0, length, at=0.0):
    """Stick-slip friction: a buzzing pulse train whose rate wanders (leather, wood frames, hinges)."""
    s = t - at
    rate = f0 * (1 + 0.35 * np.sin(2 * np.pi * (1.5 + rng.random() * 2) * s) + 0.15 * rng.standard_normal(len(t)).cumsum() / len(t) * 40)
    phase = np.cumsum(np.maximum(rate, 10)) / RATE
    pulses = (np.mod(phase, 1.0) < 0.08).astype(float)
    shape = np.where((s >= 0) & (s < length), np.sin(np.pi * np.clip(s / length, 0, 1)), 0.0)
    return band(pulses - pulses.mean(), 250, 2600) * shape


# ---- each family -----------------------------------------------------------------------------------------------------

def step(material, kind, rng):
    """One footfall. kind: walk, run (harder heel), jump (push-off scuff), land (a heavier drop onto it)."""
    weight = {"walk": 1.0, "run": 1.35, "jump": 0.8, "land": 1.8}[kind]
    length = {"walk": 0.28, "run": 0.22, "jump": 0.3, "land": 0.45}[kind]
    n, t = clock(length)
    j = lambda a, b: a + (b - a) * rng.random()
    heel = thump(t, j(70, 95), 38 / weight, drop=0.4) * 0.6 * weight
    if material == "grass":
        out = heel * 0.6 + noise(rng, n, 1500, 7000) * env(t, 0.018, 18) * 0.7
        out += grains(rng, n, t, 25, 0.08, 2500, 9000, 0.003) * 0.6
    elif material == "stone":
        out = heel + noise(rng, n, 1200, 5000) * env(t, 0.001, 140) * 1.2 * weight
        out += noise(rng, n, 2500, 8000) * env(t, 0.01, 30, 0.015) * 0.35
    elif material == "wood":
        f = j(170, 230)
        out = heel * 0.7 + modal(t, [f, f * 2.3, f * 4.1], [45, 70, 110], [1.0, 0.5, 0.25]) * 0.9 * weight
        out += noise(rng, n, 1500, 5000) * env(t, 0.001, 160) * 0.5
        if rng.random() < 0.35:
            out += creak(rng, t, j(110, 180), 0.12, 0.03) * 0.25
    elif material == "sand":
        out = heel * 0.5 + noise(rng, n, 300, 3500) * env(t, 0.02, 16) * 0.9
        out += grains(rng, n, t, 60, 0.12, 2000, 8000, 0.002) * 0.35
    elif material == "gravel":
        out = heel * 0.8 + grains(rng, n, t, int(55 * weight), 0.14, 900, 6500, 0.005, decay_over=0.08) * 1.3
    elif material == "snow":
        out = heel * 0.5 + grains(rng, n, t, int(80 * weight), 0.16, 500, 3200, 0.006, decay_over=0.1) * 1.1
        squeak = np.sin(2 * np.pi * j(1100, 1500) * t) * env(t, 0.03, 30) * 0.08
        out += band(squeak, 800, 2500)
    elif material == "metal":
        f = j(480, 600)
        out = heel * 0.7 + modal(t, [f, f * 2.76, f * 4.4, f * 6.9], [14, 22, 30, 45], [0.6, 0.45, 0.3, 0.2]) * 0.7 * weight
        out += noise(rng, n, 2000, 7000) * env(t, 0.001, 120) * 0.4
    elif material == "carpet":
        out = band(heel, 40, 400) * 1.2 + noise(rng, n, 200, 1500) * env(t, 0.02, 30) * 0.25
    elif material == "mud":
        out = heel * 0.6 + noise(rng, n, 150, 1400) * env(t, 0.01, 22) * 0.6
        out += bubbles(rng, t, 6, 0.12, 150, 450, start=0.02, life=(0.03, 0.06)) * 0.5
    else:
        raise ValueError(material)
    if kind == "jump":
        # the push-off: a backward scuff before the foot leaves
        out = out * 0.6 + noise(rng, n, 800, 6000) * env(t, 0.03, 25) * 0.25
    if kind == "land":
        out += thump(t, j(55, 70), 14, drop=0.5) * 0.7
    return out


ARMOURS = ["cloth", "leather", "chain", "scale", "plate", "heavy", "bone", "crystal"]


def armour(kind, rng):
    n, t = clock(0.4)
    j = lambda a, b: a + (b - a) * rng.random()
    if kind == "cloth":
        return noise(rng, n, 900, 5000) * (swell(t, 0.08, 0.04) + 0.6 * swell(t, 0.2, 0.05)) * 0.6
    if kind == "leather":
        return creak(rng, t, j(90, 150), 0.22, 0.02) + noise(rng, n, 400, 2000) * swell(t, 0.1, 0.05) * 0.2
    if kind == "chain":
        out = np.zeros(n)
        for _ in range(28):
            at = 0.02 + rng.random() ** 1.2 * 0.25
            f = j(3200, 8500)
            out += modal(t, [f, f * 1.47], [70, 110], [0.5, 0.25], at) * (0.3 + 0.7 * rng.random())
        return out
    if kind == "scale":
        out = np.zeros(n)
        for _ in range(12):
            at = 0.02 + rng.random() * 0.22
            f = j(1800, 4200)
            out += modal(t, [f, f * 1.9], [55, 90], [0.6, 0.3], at) * (0.4 + 0.6 * rng.random())
        return out + noise(rng, n, 1500, 6000) * swell(t, 0.12, 0.05) * 0.1
    if kind == "plate":
        f = j(620, 820)
        out = modal(t, [f, f * 2.32, f * 4.05, f * 5.9], [16, 24, 34, 50], [0.7, 0.45, 0.3, 0.15], 0.02)
        if rng.random() < 0.7:
            g = f * j(1.1, 1.3)
            out += modal(t, [g, g * 2.5, g * 4.3], [20, 30, 44], [0.4, 0.3, 0.15], j(0.07, 0.14))
        return out + noise(rng, n, 2000, 7000) * env(t, 0.001, 150, 0.02) * 0.3
    if kind == "heavy":
        f = j(330, 430)
        out = modal(t, [f, f * 2.1, f * 3.7, f * 5.2], [9, 15, 24, 36], [0.8, 0.5, 0.3, 0.15], 0.02)
        return out + thump(t, 90, 30, 0.02) * 0.4
    if kind == "bone":
        out = np.zeros(n)
        for _ in range(7):
            at = 0.02 + rng.random() * 0.2
            f = j(700, 1400)
            out += modal(t, [f, f * 2.7], [90, 140], [0.7, 0.3], at)
        return out
    if kind == "crystal":
        out = np.zeros(n)
        for _ in range(5):
            at = 0.02 + rng.random() * 0.2
            f = j(2500, 5200)
            out += modal(t, [f, f * 2.41, f * 3.9], [18, 26, 40], [0.5, 0.3, 0.2], at)
        return out
    raise ValueError(kind)


def water(kind, rng):
    j = lambda a, b: a + (b - a) * rng.random()
    if kind == "wade":
        n, t = clock(0.45)
        out = noise(rng, n, 200, 2500) * (swell(t, 0.08, 0.04) + 0.5 * swell(t, j(0.18, 0.26), 0.05))
        return out * 0.8 + bubbles(rng, t, 9, 0.3, 350, 1100, 0.03) * 0.45
    if kind == "stroke":
        n, t = clock(0.6)
        out = noise(rng, n, 150, 1600) * swell(t, j(0.14, 0.2), 0.07) * 0.9
        out += noise(rng, n, 1500, 5000) * swell(t, 0.2, 0.05) * 0.12
        return out + bubbles(rng, t, 6, 0.35, 300, 900, 0.1) * 0.3
    if kind == "dive":
        n, t = clock(1.1)
        out = thump(t, j(70, 90), 6, drop=0.5) * 0.9
        out += noise(rng, n, 300, 8000) * env(t, 0.005, 9) * 0.8
        return out + bubbles(rng, t, 45, 0.8, 200, 1400, 0.05, life=(0.03, 0.12)) * 0.4
    raise ValueError(kind)


def jet(kind, rng):
    j = lambda a, b: a + (b - a) * rng.random()
    if kind == "steam":
        n, t = clock(1.2)
        wob = 1 + 0.2 * np.sin(2 * np.pi * j(3, 6) * t)
        return noise(rng, n, 2200, 9500) * env(t, 0.06, 2.5) * wob
    if kind == "fire":
        n, t = clock(1.3)
        roar = noise(rng, n, 70, 1100) * swell(t, j(0.25, 0.35), 0.18)
        crackle = grains(rng, n, t, 40, 1.0, 1500, 7000, 0.002, 0.1) * 0.5
        return roar + crackle * swell(t, 0.4, 0.3)
    if kind == "bubbles":
        n, t = clock(1.0)
        return bubbles(rng, t, 55, 0.85, 250, 1500, 0.02) * np.minimum(1, (1.0 - t) * 6)
    raise ValueError(kind)


STATIONS = ["crafting", "anvil", "loom", "stonecutter", "smithing", "furnace", "grindstone", "cartography", "brewing",
            "tinker", "smeltery", "crucible", "kitchen", "enchanting"]


def station(kind, rng):
    """What a station sounds like as you open it and set to work."""
    j = lambda a, b: a + (b - a) * rng.random()
    n, t = clock(0.7)
    if kind == "crafting":
        f = j(180, 230)
        return modal(t, [f, f * 2.3], [40, 70], [1, 0.4], 0.01) + modal(t, [2600, 3900], [40, 60], [0.3, 0.2], j(0.1, 0.16))
    if kind == "anvil":
        f = j(900, 1050)
        return modal(t, [f, f * 2.7, f * 4.6, f * 6.3], [6, 9, 13, 18], [0.7, 0.5, 0.3, 0.2], 0.01) + thump(t, 80, 25, 0.01) * 0.4
    if kind == "loom":
        out = np.zeros(n)
        for at in (0.02, j(0.16, 0.22)):
            f = j(300, 420)
            out += modal(t, [f, f * 2.6], [70, 110], [1, 0.4], at)
        return out + noise(rng, n, 1200, 5000) * swell(t, 0.35, 0.08) * 0.15
    if kind == "stonecutter":
        f = j(90, 120) * (1 + 0.8 * np.minimum(1, t / 0.4))
        saw = 2 * np.mod(np.cumsum(f) / RATE, 1.0) - 1
        return band(saw, 100, 3000) * env(t, 0.08, 4) * 0.4 + noise(rng, n, 2000, 8000) * env(t, 0.1, 5) * 0.2
    if kind == "smithing":
        out = np.zeros(n)
        for at in (0.02, j(0.18, 0.25)):
            f = j(1800, 2300)
            out += modal(t, [f, f * 2.4, f * 3.9], [12, 18, 26], [0.6, 0.3, 0.2], at)
        return out
    if kind == "furnace":
        hatch = creak(rng, t, j(70, 110), 0.25, 0.0) * 0.6 + modal(t, [420, 980], [30, 50], [0.5, 0.25], 0.26)
        return hatch + noise(rng, n, 80, 1200) * swell(t, 0.45, 0.12) * 0.5
    if kind == "grindstone":
        return noise(rng, n, 150, 2500) * env(t, 0.1, 3) * (1 + 0.5 * np.sin(2 * np.pi * 6 * t)) * 0.7
    if kind == "cartography":
        return noise(rng, n, 1500, 8000) * (swell(t, 0.1, 0.04) + swell(t, 0.3, 0.05)) * 0.5
    if kind == "brewing":
        out = np.zeros(n)
        for at in (0.02, j(0.1, 0.14), j(0.22, 0.3)):
            f = j(2800, 4200)
            out += modal(t, [f, f * 2.2], [30, 45], [0.4, 0.2], at)
        return out
    if kind == "tinker":
        out = np.zeros(n)
        for _ in range(6):
            at = rng.random() * 0.4
            if rng.random() < 0.5:
                f = j(200, 320)
                out += modal(t, [f, f * 2.5], [60, 90], [0.7, 0.3], at)
            else:
                f = j(1500, 2800)
                out += modal(t, [f, f * 2.6], [35, 55], [0.4, 0.2], at)
        return out
    if kind == "smeltery":
        roar = noise(rng, n, 50, 700) * env(t, 0.15, 2.5)
        return roar + modal(t, [520, 1240], [10, 16], [0.3, 0.15], 0.05)
    if kind == "crucible":
        return modal(t, [j(1300, 1700), 3100], [25, 40], [0.4, 0.2], 0.01) + bubbles(rng, t, 18, 0.6, 200, 700, 0.05) * 0.5
    if kind == "kitchen":
        f = j(600, 800)
        return modal(t, [f, f * 2.9, f * 5.1], [10, 16, 25], [0.6, 0.35, 0.2], 0.01) + modal(t, [2400, 3600], [40, 55], [0.3, 0.2], j(0.15, 0.25))
    if kind == "enchanting":
        out = np.zeros(n)
        for i in range(4):
            out += noise(rng, n, 1500, 7000) * swell(t, 0.05 + i * 0.09 + rng.random() * 0.03, 0.02) * 0.4
        return out
    raise ValueError(kind)


CRAFTS = ["wood", "metal", "cloth", "stone", "tinker", "brew", "food"]


def craft(kind, rng):
    """The work of making the thing, as you take it from the grid."""
    j = lambda a, b: a + (b - a) * rng.random()
    n, t = clock(0.65)
    out = np.zeros(n)
    if kind == "wood":
        for i in range(3):
            f = j(200, 260)
            out += modal(t, [f, f * 2.4], [45, 70], [1, 0.4], 0.02 + i * j(0.12, 0.16))
    elif kind == "metal":
        for i in range(3):
            f = j(1100, 1500)
            out += modal(t, [f, f * 2.6, f * 4.2], [14, 22, 30], [0.6, 0.3, 0.2], 0.02 + i * j(0.14, 0.18))
    elif kind == "cloth":
        out = noise(rng, n, 1200, 7000) * swell(t, 0.12, 0.05) * 0.5
        out += modal(t, [4200, 6100], [60, 80], [0.3, 0.2], j(0.28, 0.34))
    elif kind == "stone":
        for i in range(3):
            out += noise(rng, n, 1000, 6000) * env(t, 0.001, 120, 0.02 + i * j(0.12, 0.16))
    elif kind == "tinker":
        for i in range(4):
            f = j(1500, 3000)
            out += modal(t, [f, f * 1.8], [80, 120], [0.5, 0.25], 0.02 + i * j(0.06, 0.09))
        out += modal(t, [300, 720], [40, 70], [0.6, 0.3], j(0.35, 0.42))
    elif kind == "brew":
        out = noise(rng, n, 300, 2500) * swell(t, 0.2, 0.08) * 0.4 + bubbles(rng, t, 10, 0.3, 400, 1200, 0.1) * 0.4
        out += modal(t, [3300, 5200], [30, 45], [0.3, 0.15], 0.5)
    elif kind == "food":
        for i in range(4):
            out += thump(t, j(150, 220), 60, 0.02 + i * j(0.09, 0.12), 0.3) * 0.6
            out += noise(rng, n, 2000, 7000) * env(t, 0.001, 200, 0.02 + i * 0.1) * 0.3
    else:
        raise ValueError(kind)
    return out


def smelted(rng):
    n, t = clock(0.6)
    return noise(rng, n, 2500, 9000) * env(t, 0.01, 7) * 0.4 + modal(t, [1700, 3900], [20, 30], [0.4, 0.2], 0.02)


DROPS = ["metal", "wood", "coin", "stone", "glass", "soft", "gem", "paper", "bone"]


def drop(kind, rng):
    """An item landing on the ground."""
    j = lambda a, b: a + (b - a) * rng.random()
    n, t = clock(0.7)
    if kind == "metal":
        f = j(1500, 2200)
        out = modal(t, [f, f * 2.76, f * 5.4], [7, 11, 16], [0.5, 0.3, 0.2], 0.005)
        return out + modal(t, [f * 1.03, f * 2.8], [12, 18], [0.25, 0.12], j(0.07, 0.11))
    if kind == "wood":
        return thump(t, j(110, 150), 30, 0.005, 0.3) + modal(t, [j(250, 320), 700], [60, 90], [0.5, 0.2], 0.005)
    if kind == "coin":
        out = np.zeros(n)
        at = 0.005
        for i in range(5):
            f = j(4000, 6500)
            out += modal(t, [f, f * 1.52, f * 2.3], [30, 45, 60], [0.5, 0.3, 0.15], at) * (0.85 ** i)
            at += j(0.04, 0.09) * (0.8 ** i)
        return out
    if kind == "stone":
        return noise(rng, n, 700, 5000) * env(t, 0.001, 90, 0.005) + thump(t, 120, 40, 0.005) * 0.4
    if kind == "glass":
        out = np.zeros(n)
        for at in (0.005, j(0.06, 0.1)):
            f = j(3000, 4500)
            out += modal(t, [f, f * 2.32, f * 3.7], [25, 35, 50], [0.4, 0.3, 0.2], at)
        return out
    if kind == "soft":
        return band(thump(t, j(80, 110), 35, 0.005, 0.3), 40, 600) + noise(rng, n, 300, 2000) * env(t, 0.005, 40, 0.005) * 0.2
    if kind == "gem":
        f = j(5000, 7000)
        return modal(t, [f, f * 2.1], [35, 55], [0.5, 0.25], 0.005)
    if kind == "paper":
        return noise(rng, n, 1500, 8000) * env(t, 0.01, 18, 0.005) * 0.5
    if kind == "bone":
        out = np.zeros(n)
        for at in (0.005, j(0.05, 0.08)):
            f = j(800, 1300)
            out += modal(t, [f, f * 2.9], [70, 110], [0.6, 0.3], at)
        return out
    raise ValueError(kind)


def stomach(kind, rng):
    j = lambda a, b: a + (b - a) * rng.random()
    if kind == "growl":
        n, t = clock(j(1.3, 2.0))
        f = j(95, 150) * (1 + 0.25 * np.sin(2 * np.pi * j(1.5, 3.0) * t) + 0.1 * np.sin(2 * np.pi * j(7, 11) * t))
        tone = np.sin(2 * np.pi * np.cumsum(f) / RATE)
        rumble = band(tone + 0.6 * rng.standard_normal(n), 60, 450)
        shape = swell(t, t[-1] * j(0.35, 0.55), t[-1] * 0.25)
        return rumble * shape + bubbles(rng, t, 10, t[-1] * 0.9, 120, 380, 0.05, (0.04, 0.09)) * 0.3
    if kind == "thirst":
        n, t = clock(0.7)
        click = thump(t, 220, 80, 0.05, 0.2) * 0.4
        rasp = noise(rng, n, 700, 3200) * swell(t, 0.35, 0.12) * 0.25
        return click + rasp
    raise ValueError(kind)


def bed(kind, rng):
    j = lambda a, b: a + (b - a) * rng.random()
    if kind == "lie":
        n, t = clock(1.0)
        rustle = noise(rng, n, 900, 6000) * (swell(t, 0.15, 0.08) + 0.7 * swell(t, 0.5, 0.12))
        return rustle * 0.6 + creak(rng, t, j(70, 100), 0.2, 0.1) * 0.25 + band(thump(t, 70, 12, 0.12, 0.3), 40, 300) * 0.4
    if kind == "rise":
        n, t = clock(0.9)
        rustle = noise(rng, n, 900, 6000) * (swell(t, 0.12, 0.07) + 0.6 * swell(t, 0.35, 0.08))
        return rustle * 0.6 + creak(rng, t, j(80, 120), 0.18, 0.05) * 0.25 + band(thump(t, 80, 30, j(0.5, 0.6), 0.3), 40, 400) * 0.5
    if kind == "turn":
        n, t = clock(1.2)
        return noise(rng, n, 700, 5000) * swell(t, 0.5, 0.2) * 0.45 + creak(rng, t, j(60, 90), 0.25, 0.35) * 0.15
    raise ValueError(kind)


# ---- writing -------------------------------------------------------------------------------------------------------

def save(name, x):
    x = np.asarray(x, dtype=np.float64)
    fade = min(len(x), int(0.01 * RATE))
    x[-fade:] *= np.linspace(1, 0, fade)
    peak = np.max(np.abs(x))
    if peak > 0:
        x = x / peak * 0.85
    pcm = (x * 32767).astype(np.int16)
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "take.wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", path], check=True)


EVENTS = {}


def family(event, folder, takes, make, seed, subtitle=None):
    names = []
    for i in range(takes):
        rng = np.random.default_rng(seed * 1000 + i)
        name = f"{folder}_{i + 1}"
        save(name, make(rng))
        names.append({"name": f"jaymospowers:immersive/{name}"})
    entry = {}
    if subtitle:
        entry["subtitle"] = f"subtitles.jaymospowers.immersive.{subtitle}"
    entry["sounds"] = names
    EVENTS[PREFIX + event] = entry


def main():
    seed = 1
    materials = ["grass", "stone", "wood", "sand", "gravel", "snow", "metal", "carpet", "mud"]
    for m in materials:
        for kind, takes in (("walk", 4), ("run", 3), ("jump", 2), ("land", 3)):
            family(f"step.{m}.{kind}", f"step/{m}_{kind}", takes, lambda r, m=m, k=kind: step(m, k, r), seed, "step")
            seed += 1
    for a in ARMOURS:
        family(f"armour.{a}", f"armour/{a}", 3, lambda r, a=a: armour(a, r), seed, "armour")
        seed += 1
    for kind, takes in (("wade", 4), ("stroke", 4), ("dive", 3)):
        family(f"water.{kind}", f"water/{kind}", takes, lambda r, k=kind: water(k, r), seed, kind)
        seed += 1
    for kind in ("steam", "fire", "bubbles"):
        family(f"jet.{kind}", f"jet/{kind}", 3, lambda r, k=kind: jet(k, r), seed, "jet_" + kind)
        seed += 1
    for s in STATIONS:
        family(f"station.{s}", f"station/{s}", 2, lambda r, s=s: station(s, r), seed, "station")
        seed += 1
    for c in CRAFTS:
        family(f"craft.{c}", f"craft/{c}", 3, lambda r, c=c: craft(c, r), seed, "craft")
        seed += 1
    family("craft.smelted", "craft/smelted", 3, smelted, seed, "smelted")
    seed += 1
    for d in DROPS:
        family(f"drop.{d}", f"drop/{d}", 3, lambda r, d=d: drop(d, r), seed, "drop")
        seed += 1
    family("stomach.growl", "stomach/growl", 4, lambda r: stomach("growl", r), seed, "growl")
    seed += 1
    family("stomach.thirst", "stomach/thirst", 3, lambda r: stomach("thirst", r), seed, "thirst")
    seed += 1
    for kind in ("lie", "rise", "turn"):
        family(f"bed.{kind}", f"bed/{kind}", 3, lambda r, k=kind: bed(k, r), seed, "bed")
        seed += 1

    with open(SOUNDS_JSON, encoding="utf-8") as f:
        existing = json.load(f)
    merged = dict(EVENTS)
    for key, value in existing.items():
        if not key.startswith(PREFIX):
            merged[key] = value
    with open(SOUNDS_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(merged, indent="\t", ensure_ascii=False) + "\n")
    print(f"{len(EVENTS)} events, {sum(len(e['sounds']) for e in EVENTS.values())} takes")


if __name__ == "__main__":
    main()
