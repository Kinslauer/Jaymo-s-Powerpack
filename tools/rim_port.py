"""Ports three of the Rim mod's sea-beast rigs (CC0, ThePoupyBuTers) into Jaymo's Kingdoms as the deep beasts.

Andy (2026-11-25): the sea beasts "keep Rim's body shapes, repainted and renamed". So the cube shapes are kept as they
are (each rig is a list of parts with a pivot, a rotation and boxes, the way a vanilla model is built), the files are
renamed to ours, and the hide is repainted: the sea-teal and grey hide goes abyssal violet and ink, and the glow sheet
goes amber. Nothing here carries a Pacific Rim name.

Usage (from the repository root), with the Rim 1.4.1 Fabric jar unpacked in <rim>:
    python3 -I tools/rim_port.py <rim-unpacked-folder>

Writes kingdoms/src/main/resources/assets/jaymoskingdoms/deep_models/<id>.json and
textures/entity/deep/<id>.png and <id>_glow.png.
"""
import colorsys
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# Rim's file stem -> our id
SPECIES = {
    "knifehead": "deep_lancer",
    "otachi": "deep_gloomwing",
    "leatherback": "deep_behemoth",
}
# the hide: hue turned from sea-teal to abyssal violet; the glow: every lit pixel turned amber
HIDE_TURN = 95.0 / 360.0
HIDE_SAT, HIDE_VAL = 1.3, 0.82
GLOW_HUE = 38.0 / 360.0


def repaint_hide(src: Path, dst: Path) -> None:
    img = Image.open(src).convert("RGBA")
    a = np.asarray(img).astype(np.float32) / 255.0
    rgb = a[..., :3]
    mx = rgb.max(axis=2)
    mn = rgb.min(axis=2)
    delta = mx - mn
    sat = np.where(mx > 0, delta / np.maximum(mx, 1e-6), 0.0)
    # hue in 0..1
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    hue = np.zeros_like(mx)
    d = np.maximum(delta, 1e-6)
    hue = np.where(mx == r, ((g - b) / d) % 6.0, hue)
    hue = np.where(mx == g, (b - r) / d + 2.0, hue)
    hue = np.where(mx == b, (r - g) / d + 4.0, hue)
    hue = (hue / 6.0) % 1.0
    hue = np.where(delta < 1e-4, 0.78, hue)
    hue = (hue + HIDE_TURN) % 1.0
    # grey stays a little violet instead of staying grey
    sat = np.clip(np.maximum(sat * HIDE_SAT, 0.22), 0.0, 0.85)
    val = np.clip(mx * HIDE_VAL, 0.0, 1.0)
    out = np.zeros_like(rgb)
    h6 = hue * 6.0
    i = np.floor(h6).astype(int) % 6
    f = h6 - np.floor(h6)
    p = val * (1.0 - sat)
    q = val * (1.0 - sat * f)
    t = val * (1.0 - sat * (1.0 - f))
    for k, (rr, gg, bb) in enumerate([(val, t, p), (q, val, p), (p, val, t), (p, q, val), (t, p, val), (val, p, q)]):
        m = i == k
        out[..., 0] = np.where(m, rr, out[..., 0])
        out[..., 1] = np.where(m, gg, out[..., 1])
        out[..., 2] = np.where(m, bb, out[..., 2])
    res = np.concatenate([out, a[..., 3:4]], axis=2)
    Image.fromarray((res * 255.0 + 0.5).astype(np.uint8), "RGBA").save(dst, optimize=True)


def repaint_glow(src: Path, dst: Path) -> None:
    img = Image.open(src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, al = px[x, y]
            if al == 0:
                continue
            v = max(r, g, b) / 255.0
            rr, gg, bb = colorsys.hsv_to_rgb(GLOW_HUE, 0.85, min(1.0, v * 1.1))
            px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), al)
    img.save(dst, optimize=True)


def egg(out: Path, ours: str, spot: tuple) -> None:
    """The spawn egg: a violet egg with amber speckles, its item and model files beside it."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 8.0) / (6.6 if y > 7 else 7.6)
            if dx * dx + dy * dy <= 1.0:
                shade = 1.0 - 0.25 * (dx + dy)
                px[x, y] = (int(78 * shade), int(44 * shade), int(120 * shade), 255)
    for x, y in spot:
        px[x, y] = (240, 170, 50, 255)
    img.save(out / f"textures/item/{ours}_spawn_egg.png")
    (out / "models/item").mkdir(parents=True, exist_ok=True)
    (out / "items").mkdir(parents=True, exist_ok=True)
    (out / f"models/item/{ours}_spawn_egg.json").write_text(
        json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskingdoms:item/{ours}_spawn_egg"}}, indent=2))
    (out / f"items/{ours}_spawn_egg.json").write_text(
        json.dumps({"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{ours}_spawn_egg"}}, indent=2))


EGG_SPOTS = {
    "deep_lancer": [(5, 7), (9, 6), (7, 10), (10, 11)],
    "deep_gloomwing": [(6, 6), (10, 8), (5, 11), (8, 12)],
    "deep_behemoth": [(5, 8), (8, 5), (10, 10), (7, 12)],
}


def main() -> None:
    rim = Path(sys.argv[1])
    out = Path("kingdoms/src/main/resources/assets/jaymoskingdoms")
    (out / "deep_models").mkdir(parents=True, exist_ok=True)
    (out / "textures/entity/deep").mkdir(parents=True, exist_ok=True)
    for stem, ours in SPECIES.items():
        text = (rim / f"assets/rim/rigs/kaiju/{stem}.json").read_text()
        # the rig files write numbers like "0." (Gson accepts them, strict JSON does not)
        rig = json.loads(re.sub(r"(\d)\.(?=[,\]\s}])", r"\1.0", text))
        width, height = rig["texture"]
        parts = [{"name": p["name"], "parent": p["parent"], "pivot": p["pivot"], "rotation": p["rotation"], "cubes": p["cubes"]}
                 for p in rig["parts"]]
        (out / "deep_models" / f"{ours}.json").write_text(json.dumps({"width": width, "height": height, "parts": parts}, separators=(",", ":")))
        tex = rim / f"assets/rim/textures/entity/kaiju/{stem}.png"
        repaint_hide(tex, out / f"textures/entity/deep/{ours}.png")
        repaint_glow(rim / f"assets/rim/textures/entity/kaiju/{stem}_glow.png", out / f"textures/entity/deep/{ours}_glow.png")
        (out / "textures/item").mkdir(parents=True, exist_ok=True)
        egg(out, ours, EGG_SPOTS[ours])
        print(ours, len(parts), "parts", width, height)


if __name__ == "__main__":
    main()
