"""Fits grade.json: the colour shift from vanilla 26.3's textures to Excalibur's (by Maffhew), per hue band.

Statistics only. For each of 12 hue bands (and greys) it keeps 21 lightness and 21 saturation quantiles from each pack
and a small hue nudge; no pixel, palette entry or texture of Excalibur's is kept or copied. Excalibur is CC BY-NC-ND:
the pack ships it unchanged (client/PackResourcePacks) and our own textures are graded toward its mood with grade.py.

    python -I tools/excalibur_style/fit.py <vanilla 26.3 assets/minecraft/textures> <Excalibur's assets/minecraft/textures> tools/excalibur_style/grade.json
"""
import colorsys
import json
import os
import sys

import numpy as np
from PIL import Image

BANDS = 12
Q = np.linspace(0, 1, 21)


def pixels(path):
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    a = np.asarray(im.crop((0, 0, w, min(h, w))), dtype=float) / 255
    a = a[a[..., 3] > 0.5][:, :3]
    return np.array([colorsys.rgb_to_hls(*c) for c in a]) if len(a) else np.zeros((0, 3))


def curves(a, b):
    return {'L': [np.quantile(a[:, 1], Q).tolist(), np.quantile(b[:, 1], Q).tolist()],
            'S': [np.quantile(a[:, 2], Q).tolist(), np.quantile(b[:, 2], Q).tolist()]}


def main(vanilla, excalibur, out):
    v, e = [], []
    for kind in ('item', 'block', 'entity'):
        for root, _, files in os.walk(os.path.join(vanilla, kind)):
            for n in files:
                rel = os.path.relpath(os.path.join(root, n), vanilla)
                if n.endswith('.png') and os.path.exists(os.path.join(excalibur, rel)):
                    v.append(pixels(os.path.join(vanilla, rel)))
                    e.append(pixels(os.path.join(excalibur, rel)))
    v, e = np.concatenate(v), np.concatenate(e)
    model = {'grey': curves(v[v[:, 2] < 0.12], e[e[:, 2] < 0.12]), 'bins': []}
    cv, ce = v[v[:, 2] >= 0.12], e[e[:, 2] >= 0.12]
    for i in range(BANDS):
        lo, hi = i / BANDS, (i + 1) / BANDS
        a = cv[(cv[:, 0] >= lo) & (cv[:, 0] < hi)]
        b = ce[(ce[:, 0] >= lo - 0.5 / BANDS) & (ce[:, 0] < hi + 0.5 / BANDS)]
        mean = lambda h: np.angle(np.mean(np.exp(2j * np.pi * h))) / (2 * np.pi)
        shift = (mean(b[:, 0]) - mean(a[:, 0]) + 0.5) % 1 - 0.5
        band = curves(a, b)
        band['dh'] = float(shift * 0.5)
        band['n'] = [len(a), len(b)]
        model['bins'].append(band)
    with open(out, 'w') as f:
        json.dump(model, f)


if __name__ == '__main__':
    main(*sys.argv[1:4])
