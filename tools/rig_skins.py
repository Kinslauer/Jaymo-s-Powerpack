"""Paints the two giant rigs' skins (Patch 8) from our own android mech skins: the Frostborn Siege Rig (ice-blue, white
plate) from the Bulwark build and the Tinkerer's Colossus (brass and rust) from the Reclaimer build, with their glow sheets
(pale cyan and amber). Everything here is the mod's own art; nothing comes from the Rim mod.

Run from the repository root:  python3 -I tools/rig_skins.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

DIR = Path("src/main/resources/assets/jaymospowers/textures/entity/future")
# (source build, new name, hue 0..1, saturation factor, value factor, eyes hue)
SKINS = [
    ("bulwark", "frostborn", 0.55, 0.55, 1.12, 0.5),
    ("reclaimer", "tinkerer", 0.08, 0.95, 0.95, 0.1),
]


def hsv(img: Image.Image, hue: float, sat: float, val: float, fixed_hue: bool) -> Image.Image:
    a = np.asarray(img.convert("RGBA")).astype(np.float32) / 255.0
    rgb = a[..., :3]
    mx = rgb.max(axis=2)
    mn = rgb.min(axis=2)
    delta = mx - mn
    s = np.where(mx > 0, delta / np.maximum(mx, 1e-6), 0.0)
    h = np.full_like(mx, hue)
    s = np.clip(s * sat if not fixed_hue else np.maximum(s, 0.5), 0.0, 1.0)
    v = np.clip(mx * val, 0.0, 1.0)
    # the hue is set, not turned: a hard-surface skin has little hue worth keeping
    h6 = h * 6.0
    i = int(np.floor(h6[0, 0])) % 6
    f = h6 - np.floor(h6)
    p = v * (1.0 - s)
    q = v * (1.0 - s * f)
    t = v * (1.0 - s * (1.0 - f))
    r, g, b = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i]
    out = np.concatenate([np.stack([r, g, b], axis=2), a[..., 3:4]], axis=2)
    return Image.fromarray((out * 255.0 + 0.5).astype(np.uint8), "RGBA")


def main() -> None:
    for source, name, hue, sat, val, eyes in SKINS:
        hsv(Image.open(DIR / f"android_mech_{source}.png"), hue, sat, val, False).save(DIR / f"android_mech_{name}.png", optimize=True)
        hsv(Image.open(DIR / f"android_mech_{source}_eyes.png"), eyes, 1.0, 1.0, True).save(DIR / f"android_mech_{name}_eyes.png", optimize=True)
        print(name)


if __name__ == "__main__":
    main()
