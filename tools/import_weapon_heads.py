"""Brings in Andy's reshaped weapon heads and their own casts (2026-10-23, Powers 0.15.138).

The heads come as iron only, rescaled to fill a 16x16 cast cavity 1:1, with a gold casting mould for each. The other
metals (and stone) are recoloured from the new iron one: each new pixel takes the colour the old texture's closest
iron shade had in that metal. Run after tools/weapon_parts_assets.py, which would put the old heads back:

    python -I tools/import_weapon_heads.py <unpacked texture pack>
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "jaymospowers")
PARTS = os.path.join(ROOT, "textures", "item", "tinkering", "weapon_part")
CASTS = os.path.join(ROOT, "textures", "item", "tinkering", "cast")


def shades(image):
    """The image's opaque colours, darkest first."""
    return sorted({p[:3] for p in image.convert("RGBA").get_flattened_data() if p[3]}, key=lambda c: 299 * c[0] + 587 * c[1] + 114 * c[2])


def recolour(new_iron, old_metal):
    """new_iron in old_metal's colours: a pixel's place in the iron's dark-to-light range picks the metal shade at the same
    place in the metal's (the old heads' shapes differ from metal to metal, so they can't be matched pixel for pixel)."""
    iron, metal = shades(new_iron), shades(old_metal)
    out = new_iron.convert("RGBA").copy()
    for x in range(out.width):
        for y in range(out.height):
            r, g, b, a = out.getpixel((x, y))
            if a and metal:
                t = iron.index((r, g, b)) / max(1, len(iron) - 1)
                out.putpixel((x, y), metal[round(t * (len(metal) - 1))] + (a,))
    return out


def main(pack):
    src = os.path.join(pack, "assets", "jaymospowers", "textures", "item", "tinkering")
    changed = []
    for f in sorted(os.listdir(os.path.join(src, "weapon_part"))):
        part = f[:-len("_iron.png")]
        ours = os.path.join(PARTS, f)
        new = Image.open(os.path.join(src, "weapon_part", f)).convert("RGBA")
        old = Image.open(ours).convert("RGBA")
        if old.size == new.size and list(old.get_flattened_data()) == list(new.get_flattened_data()):
            continue
        for metal in ("gold", "copper", "stone"):
            path = os.path.join(PARTS, part + "_" + metal + ".png")
            if os.path.exists(path):
                recolour(new, Image.open(path)).save(path)
        new.save(ours)
        changed.append(part)
    cases = []
    for f in sorted(os.listdir(os.path.join(src, "cast"))):
        part = f[:-4]
        if part not in changed:
            continue
        Image.open(os.path.join(src, "cast", f)).convert("RGBA").save(os.path.join(CASTS, f))
        with open(os.path.join(ROOT, "models", "item", "tinkering", "cast", part + ".json"), "w", encoding="utf-8") as out:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:item/tinkering/cast/" + part}}, out, indent=2)
        cases.append({"when": part, "model": {"type": "minecraft:model", "model": "jaymospowers:item/tinkering/cast/" + part}})
    with open(os.path.join(ROOT, "items", "weapon_cast.json"), "w", encoding="utf-8") as out:
        json.dump({"model": {"type": "minecraft:select", "property": "minecraft:component", "component": "jaymospowers:weapon_cast",
            "cases": cases, "fallback": {"type": "minecraft:model", "model": "jaymospowers:item/tinkering/cast/small_blade"}}}, out, indent=2)
    print(len(changed), "heads:", " ".join(changed))
    print(len(cases), "casts")


if __name__ == "__main__":
    main(sys.argv[1])
