"""Our own limb regrowth sounds, synthesised from noise and tones (nothing sampled, nothing borrowed).

    python3 tools/gen_regrow_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes mono .ogg files:
  Kingdoms  sounds/rubber/stretch_1..3   a Rubber-folk limb stretching back: a balloon being pulled, a rising rubbery squeak
  Kingdoms  sounds/rubber/pop_1..3       the end of it: a sharp balloon pop
  Powers    sounds/verdant/regrow_1..3   a Verdant limb growing back: a fleshy crunch, bone cracking under wet flesh
Re-run to change them; the seeds keep every file the same from run to run.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import gen_blood_sounds as blood  # noqa: E402  (band, bubbles, save)

RATE = blood.RATE
ROOT = os.path.join(os.path.dirname(__file__), "..")
KINGDOMS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "sounds", "rubber")
POWERS = os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers", "sounds", "verdant")


def save(folder, name, x):
    blood.OUT = folder
    blood.save(name, x)


def stretch(rng):
    """Rubber pulled tight: stick-slip squeaks whose pitch climbs as it stretches, wobbling as it goes."""
    seconds = 1.35 + 0.1 * rng.random()
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    # the squeak's pitch: rising, with a slow wobble and a little jitter (a hand pulling unevenly)
    low, high = 260 + 60 * rng.random(), 820 + 180 * rng.random()
    f = low + (high - low) * (t / seconds) ** 1.4
    f *= 1.0 + 0.05 * np.sin(2 * np.pi * (5 + 2 * rng.random()) * t) + 0.02 * band_noise(rng, n, 2, 20)
    phase = np.cumsum(2 * np.pi * f / RATE)
    # stick-slip: a buzzy, many-harmonic tone rather than a pure one
    tone = sum(np.sin(k * phase) / k ** 0.9 for k in range(1, 9))
    # it catches and lets go: the squeak comes in bursts
    grip = 0.55 + 0.45 * np.clip(np.sin(2 * np.pi * (7 + 4 * rng.random()) * t + 6 * band_noise(rng, n, 1, 6)), 0.0, 1.0)
    rub = blood.band(rng.standard_normal(n), 900, 5000) * 0.18
    out = blood.band(tone, 200, 6000) * grip + rub * grip
    swell = np.minimum(1.0, t * 6.0) * (0.55 + 0.45 * t / seconds)
    return out * swell * np.minimum(1.0, (seconds - t) * 25.0)


def band_noise(rng, n, low, high):
    """Slow wandering noise (-1..1) for wobble, made at a low rate and stretched out."""
    x = blood.band(rng.standard_normal(n), low, high, order=2)
    peak = np.max(np.abs(x))
    return x / peak if peak > 0 else x


def pop(rng):
    """A balloon bursting: a hard crack of noise, a short low thump under it and the snap of the skin."""
    seconds = 0.35
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    crack = blood.band(noise, 700, 9000) * np.exp(-t * (95 + 30 * rng.random()))
    f0 = 95 + 30 * rng.random()
    thump = np.sin(2 * np.pi * f0 * t * (1.0 - 0.8 * t)) * np.exp(-t * 40)
    snap = blood.band(noise, 2500, 8000) * np.exp(-((t - 0.012) ** 2) / 0.00002) * 0.6
    out = crack * 1.0 + thump * 0.7 + snap
    return out * np.minimum(1.0, t * 4000.0)


def crunch(rng):
    """Flesh knitting over a growing bone: crackling bone over a wet, sucking squelch, ending in a firm crunch."""
    seconds = 0.95 + 0.1 * rng.random()
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    noise = rng.standard_normal(n)
    # the wet part: muffled squelching that rises and falls
    wet_env = 0.5 + 0.5 * np.sin(2 * np.pi * (6 + 3 * rng.random()) * t) ** 2
    wet = blood.band(noise, 120, 900 + 300 * rng.random()) * wet_env * np.exp(-((t - 0.4) ** 2) / 0.12)
    pops = blood.bubbles(rng, n, 14 + rng.integers(0, 6), 300, 1000, 0.05, 0.7)
    # the bone: dense little cracks, thickening toward the end
    cracks = np.zeros(n)
    for _ in range(55):
        at = int(seconds * rng.random() ** 0.7 * RATE)
        length = int((0.004 + 0.01 * rng.random()) * RATE)
        if at + length < n:
            tick = blood.band(rng.standard_normal(length), 1200, 6000) * np.exp(-np.arange(length) / (0.0025 * RATE))
            cracks[at:at + length] += tick * (0.4 + 0.6 * rng.random())
    # and the final set: a heavy wet crunch
    end = 0.72 + 0.1 * rng.random()
    tail = np.clip(t - end, 0.0, None)
    f0 = 60 + 20 * rng.random()
    body = np.sin(2 * np.pi * f0 * tail) * np.exp(-tail * 20) * (t >= end)
    snap = blood.band(noise, 400, 3500) * np.exp(-tail * 35) * (t >= end)
    out = wet * 0.9 + pops * 0.3 + cracks * 0.8 + body * 0.9 + snap * 0.9
    return out * np.minimum(1.0, t * 200.0) * np.minimum(1.0, (seconds - t) * 15.0)


def main():
    for i in range(3):
        save(KINGDOMS, f"stretch_{i + 1}", stretch(np.random.default_rng(700 + i)))
        save(KINGDOMS, f"pop_{i + 1}", pop(np.random.default_rng(710 + i)))
        save(POWERS, f"regrow_{i + 1}", crunch(np.random.default_rng(720 + i)))


if __name__ == "__main__":
    main()
