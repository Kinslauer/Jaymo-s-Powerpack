"""Art and lang for the pitchfork (armory/Armory, tinkering/WeaponParts "pitchfork_tines"), drawn here by script.

    python -I tools/left_out_assets.py

Writes the pitchfork's item sprite, model and item definition, the tines part in stone and iron (same shape as every
other forged part: a custom_model_data select, orange while hot), and the lang part left_out_misc.json. Nothing is cut
from another mod's art.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import weapon_parts_assets as wpa  # noqa: E402  (its drawn colours, stone recolour and hot-glow wrapper)

ASSETS = wpa.ASSETS
IRON = wpa.DRAWN_COLOURS["iron"]
WOOD = ((74, 48, 24), (128, 90, 48))
BINDING = (70, 70, 74)


def tines():
    """Bar and three prongs of iron, pointing up and to the right: {(x, y): 'bar' | 'prong' | 'tip'}."""
    px = {}
    for k in range(-3, 4):
        px[(8 + k, 8 + k)] = "bar"
    for k, length in ((-3, 4), (0, 6), (3, 4)):
        for t in range(1, length + 1):
            px[(8 + k + t, 8 + k - t)] = "tip" if t == length else "prong"
    return px


def tines_image(metal):
    c_dark, c_mid, c_shine = wpa.DRAWN_COLOURS[metal]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), kind in tines().items():
        colour = {"bar": c_dark, "prong": c_mid, "tip": c_shine}[kind]
        img.putpixel((x, y), colour + (255,))
        # a thickened bar and shaded prongs read better at 16 px
        if kind == "bar" and x + 1 < 16:
            img.putpixel((x + 1, y), c_mid + (255,)) if img.getpixel((x + 1, y))[3] == 0 else None
    return img


def pitchfork_image():
    img = tines_image("iron")
    for t in range(1, 8):
        x, y = 8 - t, 8 + t
        img.putpixel((x, y), WOOD[t % 2] + (255,))
    img.putpixel((8, 8), BINDING + (255,))
    img.putpixel((7, 9), BINDING + (255,))
    return img


def main():
    tex = os.path.join(ASSETS, "textures", "item")
    os.makedirs(os.path.join(tex, "armory"), exist_ok=True)
    os.makedirs(os.path.join(tex, "tinkering", "weapon_part"), exist_ok=True)
    pitchfork_image().save(os.path.join(tex, "armory", "pitchfork.png"))
    wpa.write(os.path.join(ASSETS, "models", "item", "armory", "pitchfork.json"),
              {"parent": "minecraft:item/handheld", "textures": {"layer0": "jaymospowers:item/armory/pitchfork"}})
    wpa.write(os.path.join(ASSETS, "items", "pitchfork.json"),
              {"model": {"type": "minecraft:model", "model": "jaymospowers:item/armory/pitchfork"}})

    part = "pitchfork_tines"
    cases = []
    for metal, image in (("stone", wpa.as_stone(tines_image("iron"))), ("iron", tines_image("iron"))):
        path = wpa.OUT + part + "_" + metal
        image.save(os.path.join(ASSETS, "textures", path + ".png"))
        wpa.write(os.path.join(ASSETS, "models", path + ".json"),
                  {"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
        cases.append({"when": metal, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
    wpa.write(os.path.join(ASSETS, "items", part + ".json"), {"model": wpa.hot({
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": cases[0]["model"]})})

    wpa.write(os.path.join(ASSETS, "lang", "parts", "left_out_misc.json"), {
        "item.jaymospowers.pitchfork": "Pitchfork",
        "item.jaymospowers.pitchfork_tines": "Pitchfork Tines",
    })


if __name__ == "__main__":
    main()
