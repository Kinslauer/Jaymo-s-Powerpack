"""
Held 3D models for Balkon's WeaponMod: Legacy guns (flintlock, musket and its bayonets, blunderbuss), voxelised from
their own item sprites: every opaque pixel becomes a small block, wood thicker than metal, so the gun keeps its exact
look but has body in the hand. The inventory icon, item frames and dropped items keep the flat sprite.

The output is a built-in resource pack inside Jaymo's Powers (resourcepacks/held_guns), always on, so it sits above
WeaponMod's own assets. The sprites themselves aren't copied; the models point at weaponmod:item/<gun> (MIT).

Run from the project root:  python tools/gen_guns.py <path to balkons-weaponmod-legacy jar>
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

JAR = sys.argv[1]
PACK = "src/main/resources/resourcepacks/held_guns"
GUNS = ["flintlock", "musket", "blunderbuss"] + [f"musketbayonet.{m}" for m in
                                                  ("wood", "stone", "copper", "iron", "gold", "diamond", "netherite")]
# thickness (in pixels) and centre depth for each material
WOOD = (3.0, 8.0)
METAL = (2.0, 8.0)
HANDLE_DARK = (2.5, 8.0)


def material(px):
    r, g, b, a = px
    if a < 128:
        return None
    if r > 55 and r - b > 22 and r >= g:
        return WOOD
    if max(r, g, b) < 50:
        return HANDLE_DARK
    return METAL


def voxels(img, texture):
    elements = []
    w, h = img.size
    for y in range(h):
        x = 0
        while x < w:
            kind = material(img.getpixel((x, y)))
            if kind is None:
                x += 1
                continue
            start = x
            while x < w and material(img.getpixel((x, y))) == kind:
                x += 1
            depth, centre = kind
            z0, z1 = centre - depth / 2, centre + depth / 2
            row = [start, y, x, y + 1]
            faces = {
                "south": {"uv": row, "texture": "#gun"},
                "north": {"uv": [x, y, start, y + 1], "texture": "#gun"},
                "up": {"uv": row, "texture": "#gun"},
                "down": {"uv": row, "texture": "#gun"},
                "west": {"uv": [start, y, start + 1, y + 1], "texture": "#gun"},
                "east": {"uv": [x - 1, y, x, y + 1], "texture": "#gun"},
            }
            elements.append({"from": [start, 16 - (y + 1), z0], "to": [x, 16 - y, z1], "faces": faces})
    return elements


def main():
    with zipfile.ZipFile(JAR) as jar:
        def read_json(path):
            return json.loads(jar.read(path).decode("utf-8"))

        os.makedirs(f"{PACK}/assets/weaponmod/items", exist_ok=True)
        os.makedirs(f"{PACK}/assets/jaymospowers/models/item/gun", exist_ok=True)
        with open(f"{PACK}/pack.mcmeta", "w", encoding="utf-8", newline="\n") as f:
            json.dump({"pack": {"description": "Jaymo's Powers: 3D guns in hand", "min_format": 97, "max_format": 97}}, f, indent=2)
        for gun in GUNS:
            img = Image.open(io.BytesIO(jar.read(f"assets/weaponmod/textures/item/{gun}.png"))).convert("RGBA")
            texture = f"weaponmod:item/{gun}"
            elements = voxels(img, texture)
            variants = {}
            for suffix in ("", "_reload"):
                source = f"assets/weaponmod/models/item/{gun}{suffix}.json"
                if source not in jar.namelist():
                    continue
                original = read_json(source)
                model = {
                    "parent": original.get("parent", "item/handheld"),
                    "textures": {"gun": texture, "particle": texture},
                    "gui_light": "front",
                    "elements": elements,
                }
                if "display" in original:
                    model["display"] = original["display"]
                name = gun.replace(".", "_") + suffix
                with open(f"{PACK}/assets/jaymospowers/models/item/gun/{name}.json", "w", encoding="utf-8", newline="\n") as f:
                    json.dump(model, f, indent=1)
                variants[suffix] = (f"weaponmod:item/{gun}{suffix}", f"jaymospowers:item/gun/{name}")

            def by_context(flat, solid):
                # flat in the inventory, frames and on the ground; solid in hand
                return {"type": "minecraft:select", "property": "minecraft:display_context",
                        "cases": [{"when": ["gui", "ground", "fixed"], "model": {"type": "minecraft:model", "model": flat}}],
                        "fallback": {"type": "minecraft:model", "model": solid}}

            original_item = read_json(f"assets/weaponmod/items/{gun}.json")["model"]
            if original_item.get("type") == "minecraft:condition" and "_reload" in variants:
                item = dict(original_item)
                item["on_false"] = by_context(*variants[""])
                item["on_true"] = by_context(*variants["_reload"])
            else:
                item = by_context(*variants[""])
            with open(f"{PACK}/assets/weaponmod/items/{gun}.json", "w", encoding="utf-8", newline="\n") as f:
                json.dump({"model": item}, f, indent=2)
            print(gun, len(elements), "elements")


main()
