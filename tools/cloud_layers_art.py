#!/usr/bin/env python3
"""Draws the cloud decks' textures (Kingdoms CloudDecks): two tiling 128x128 cloud fields, white with alpha.

`cloud_light.png` is scattered fair-weather cloud (about a third of the sky); `cloud_heavy.png` is a near-solid overcast
with holes. Both tile, are made of hard pixel steps (each pixel is eight blocks of sky), and have a thin edge so a deck
stacked of slices looks thick in the middle and ragged at the rim. Our own noise, no other mod's art.
Run: python3 tools/cloud_layers_art.py
"""
import os

import numpy as np
from PIL import Image

SIZE = 128
OUT = os.path.join(os.path.dirname(__file__), "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "skies")


def field(rng, octaves):
    """Tiling noise 0..1: white noise low-passed in the frequency domain (so it wraps), octave by octave."""
    total = np.zeros((SIZE, SIZE))
    freq = np.fft.fftfreq(SIZE)
    radius = np.hypot(*np.meshgrid(freq, freq))
    for scale, weight in octaves:
        noise = np.fft.fft2(rng.standard_normal((SIZE, SIZE)))
        noise *= np.exp(-(radius * scale) ** 2)
        layer = np.real(np.fft.ifft2(noise))
        layer = (layer - layer.mean()) / layer.std()
        total += layer * weight
    total = (total - total.min()) / (total.max() - total.min())
    return total


def cloud(seed, coverage):
    rng = np.random.default_rng(seed)
    noise = field(rng, ((9.0, 1.0), (4.0, 0.55), (2.0, 0.3)))
    cut = np.quantile(noise, 1.0 - coverage)
    depth = np.clip((noise - cut) / (1.0 - cut), 0.0, 1.0)
    # steps: a thin rim, then thicker body
    alpha = np.where(depth <= 0.0, 0, np.where(depth < 0.12, 120, np.where(depth < 0.4, 200, 255))).astype(np.uint8)
    out = np.zeros((SIZE, SIZE, 4), dtype=np.uint8)
    out[..., :3] = 255
    out[..., 3] = alpha
    return Image.fromarray(out, "RGBA")


def main():
    os.makedirs(OUT, exist_ok=True)
    cloud(2611, 0.34).save(os.path.join(OUT, "cloud_light.png"))
    cloud(2612, 0.72).save(os.path.join(OUT, "cloud_heavy.png"))


if __name__ == "__main__":
    main()
