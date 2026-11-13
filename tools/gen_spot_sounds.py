"""Our own spot sounds heard from a direction (Andy's four-mods pick fx_spots), synthesised, nothing sampled.

    python3 tools/gen_spot_sounds.py      (needs numpy and ffmpeg with libvorbis)

Writes into src/main/resources/assets/jaymospowers/sounds/spot/:
  woodpecker_1..4   a woodpecker drumming on a trunk: a fast roll of hollow knocks that slows and fades
"""
import os

import numpy as np

import gen_pool_sounds as g

g.OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "jaymospowers", "sounds", "spot")


def woodpecker(rng):
    seconds = 1.1 + 0.5 * rng.random()
    n = int(seconds * g.RATE)
    out = np.zeros(n)
    rate = 16 + 6 * rng.random()
    f = 900 + 400 * rng.random()
    at, k = 0.0, 0
    while at < seconds - 0.05:
        i = int(at * g.RATE)
        ln = int(0.03 * g.RATE)
        tt = np.arange(ln) / g.RATE
        knock = np.sin(2 * np.pi * f * tt) * np.exp(-tt * 140) + 0.5 * np.sin(2 * np.pi * f * 2.3 * tt) * np.exp(-tt * 200)
        out[i:i + ln] += knock * (1.0 - at / seconds) ** 0.6 * (0.8 + 0.2 * rng.random())
        k += 1
        at += 1.0 / (rate * (1.0 - 0.35 * at / seconds))
    return out


if __name__ == "__main__":
    for k in range(4):
        g.save(f"woodpecker_{k + 1}", woodpecker(np.random.default_rng(500 + k)))
