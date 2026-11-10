"""Build the original heart-crystal ore Blockbench sources, textures and block models.

Run from the repository root with Python 3.12 and Pillow installed:
    python art/heart-crystal/generate_heart_crystal_ore.py

The rock host remains a full opaque cube. Crystal cuboids intersect its surface,
so there are no cut-out holes or extra blockstate/item variants. All art is
original; the supplied amethyst-heart photographs are visual references only.
"""

from __future__ import annotations

import base64
import io
import json
import random
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "art" / "heart-crystal"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "jaymospowers"
TEXTURES = ASSETS / "textures" / "block"
MODELS = ASSETS / "models" / "block"
NAMESPACE = "jaymospowers"
RESOLUTION = 32


def png_data(image: Image.Image) -> str:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")


def rock_texture(deepslate: bool) -> Image.Image:
    rng = random.Random(81173 if deepslate else 81172)
    image = Image.new("RGBA", (RESOLUTION, RESOLUTION))
    pixels = image.load()
    palette = (
        [(43, 45, 49), (49, 51, 55), (55, 57, 61), (61, 63, 67), (68, 70, 73)]
        if deepslate
        else [(103, 105, 105), (112, 114, 113), (120, 121, 120), (128, 129, 127), (135, 136, 133)]
    )
    coarse = [[rng.randrange(len(palette)) for _ in range(16)] for _ in range(16)]
    for y in range(RESOLUTION):
        for x in range(RESOLUTION):
            index = coarse[y // 2][x // 2]
            if rng.random() < 0.22:
                index = max(0, min(len(palette) - 1, index + rng.choice((-1, 1))))
            pixels[x, y] = (*palette[index], 255)
    draw = ImageDraw.Draw(image)
    if deepslate:
        for offset in (-18, -7, 5, 16, 27):
            draw.line((offset, 31, offset + 31, 0), fill=(38, 40, 44, 255), width=1)
            draw.line((offset + 2, 31, offset + 33, 0), fill=(65, 67, 70, 255), width=1)
    else:
        for _ in range(24):
            x, y = rng.randrange(32), rng.randrange(32)
            colour = rng.choice(((93, 95, 95, 255), (143, 143, 139, 255)))
            draw.line((x, y, min(31, x + rng.randrange(1, 4)), y), fill=colour)
    return image


def crystal_texture() -> Image.Image:
    """Four opaque 16x16 panels: shadow, body, light face and pale termination."""
    image = Image.new("RGBA", (32, 32))
    panels = [
        ((44, 16, 76), (75, 29, 119), (111, 53, 158)),
        ((70, 25, 112), (119, 51, 172), (164, 94, 207)),
        ((102, 46, 155), (163, 94, 211), (211, 158, 243)),
        ((142, 83, 194), (205, 151, 239), (241, 220, 255)),
    ]
    for panel, (dark, mid, light) in enumerate(panels):
        ox, oy = (panel % 2) * 16, (panel // 2) * 16
        for y in range(16):
            for x in range(16):
                colour = dark if x + y < 10 else mid if x - y < 7 else light
                image.putpixel((ox + x, oy + y), (*colour, 255))
        draw = ImageDraw.Draw(image)
        draw.polygon(
            [(ox, oy), (ox + 5, oy), (ox + 15, oy + 10), (ox + 15, oy + 15), (ox + 11, oy + 15)],
            fill=(*mid, 255),
        )
        draw.line((ox + 5, oy, ox + 15, oy + 10), fill=(*light, 255))
        draw.line((ox + 1, oy + 1, ox + 1, oy + 9), fill=(*light, 255))
        if panel >= 2:
            draw.point((ox + 3, oy + 2), fill=(247, 232, 255, 255))
            draw.point((ox + 4, oy + 2), fill=(247, 232, 255, 255))
    return image


def uv_panel(panel: int) -> list[int]:
    return [(panel % 2) * 16, (panel // 2) * 16, (panel % 2) * 16 + 16, (panel // 2) * 16 + 16]


def crystal_faces(panel: int) -> dict[str, dict]:
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        selected = 2 if face == "up" else 0 if face == "down" else panel
        faces[face] = {"uv": uv_panel(selected), "texture": 1}
    return faces


def rock_faces() -> dict[str, dict]:
    return {face: {"uv": [0, 0, 32, 32], "texture": 0} for face in ("north", "south", "east", "west", "up", "down")}


def map_point(face: str, u: float, v: float, depth: float) -> tuple[float, float, float]:
    if face == "north":
        return u, v, -depth
    if face == "south":
        return 16 - u, v, 16 + depth
    if face == "east":
        return 16 + depth, v, u
    if face == "west":
        return -depth, v, 16 - u
    if face == "up":
        return u, 16 + depth, v
    if face == "down":
        return u, -depth, 16 - v
    raise ValueError(face)


def rotation_for(face: str, angle: float, origin: tuple[float, float, float]) -> tuple[list[float], str, float]:
    if face in ("north", "south"):
        signed = angle if face == "north" else -angle
        return [0, 0, signed], "z", signed
    if face in ("east", "west"):
        signed = angle if face == "east" else -angle
        return [signed, 0, 0], "x", signed
    signed = -angle if face == "up" else angle
    return [0, signed, 0], "y", signed


def add_crystal(
    elements: list[dict],
    face: str,
    label: str,
    u: float,
    v: float,
    height: float,
    width: float,
    depth: float,
    angle: float,
    central: bool = False,
) -> None:
    if central:
        segments = [
            (0.00, 0.40, 0.82, 0.70, 0),
            (0.40, 0.72, 1.00, 1.00, 1),
            (0.72, 0.84, 0.72, 0.82, 2),
            (0.84, 1.00, 0.32, 0.58, 3),
        ]
    else:
        segments = [
            (0.00, 0.42, 1.00, 0.70, 0),
            (0.42, 0.76, 0.78, 1.00, 1),
            (0.76, 1.00, 0.36, 0.62, 3),
        ]
    origin = map_point(face, u, v, 0)
    rotation, axis, signed_angle = rotation_for(face, angle, origin)
    for index, (start, end, width_scale, depth_scale, panel) in enumerate(segments):
        half = width * width_scale / 2
        p0 = map_point(face, u - half, v + height * start, -0.30)
        p1 = map_point(face, u + half, v + height * end, depth * depth_scale)
        element = {
            "name": f"{face}_{label}_{index}",
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "from": [round(min(a, b), 3) for a, b in zip(p0, p1)],
            "to": [round(max(a, b), 3) for a, b in zip(p0, p1)],
            "autouv": 0,
            "origin": [round(value, 3) for value in origin],
            "faces": crystal_faces(panel),
            "type": "cube",
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"jaymo-heart-crystal:{face}:{label}:{index}")),
        }
        if signed_angle:
            element["rotation"] = rotation
        elements.append(element)


def build_elements() -> list[dict]:
    elements = [
        {
            "name": "opaque_rock_host",
            "box_uv": False,
            "rescale": False,
            "locked": False,
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "autouv": 0,
            "origin": [0, 0, 0],
            "faces": rock_faces(),
            "type": "cube",
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymo-heart-crystal:rock")),
        }
    ]
    # Two lobes, sloping sides and a bottom point surround the dominant crystal.
    satellites = [
        (3.5, 10.3, 3.7, 1.8, 1.25, 22.5),
        (12.5, 10.3, 3.7, 1.8, 1.25, -22.5),
        (2.4, 7.0, 3.1, 1.6, 1.05, 22.5),
        (13.6, 7.0, 3.1, 1.6, 1.05, -22.5),
        (4.0, 4.4, 2.8, 1.5, 0.95, 22.5),
        (12.0, 4.4, 2.8, 1.5, 0.95, -22.5),
        (5.9, 2.4, 2.3, 1.35, 0.85, 22.5),
        (10.1, 2.4, 2.3, 1.35, 0.85, -22.5),
        (8.0, 1.0, 1.9, 1.2, 0.75, 0),
    ]
    for face in ("north", "south", "east", "west"):
        add_crystal(elements, face, "heart_central", 8.0, 5.0, 9.4, 4.0, 2.15, 0, central=True)
        for index, values in enumerate(satellites):
            add_crystal(elements, face, f"heart_satellite_{index}", *values)
    # A quieter crown keeps the four vertical heart outlines readable in an
    # isometric view. The buried bottom stays plain rock instead of sprouting
    # apparently floating fragments when the block is seen from below.
    add_crystal(elements, "up", "crown_central", 8.0, 5.3, 5.4, 3.0, 1.8, 0)
    crown = [
        (3.8, 10.5, 2.8, 1.5, 0.95, 22.5),
        (12.2, 10.5, 2.8, 1.5, 0.95, -22.5),
        (4.6, 3.8, 2.6, 1.4, 0.85, 22.5),
        (11.4, 3.8, 2.6, 1.4, 0.85, -22.5),
    ]
    for index, values in enumerate(crown):
        add_crystal(elements, "up", f"crown_satellite_{index}", *values)
    return elements


def blockbench_outliner(elements: list[dict]) -> list[dict]:
    groups = []
    rock = elements[0]
    groups.append({
        "name": "rock",
        "origin": [8, 8, 8],
        "rotation": [0, 0, 0],
        "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymo-heart-crystal:group:rock")),
        "isOpen": False,
        "children": [rock["uuid"]],
    })
    for face in ("north", "south", "east", "west", "up", "down"):
        groups.append({
            "name": f"{face}_amethyst_heart",
            "origin": [8, 8, 8],
            "rotation": [0, 0, 0],
            "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"jaymo-heart-crystal:group:{face}")),
            "isOpen": False,
            "children": [element["uuid"] for element in elements[1:] if element["name"].startswith(face + "_")],
        })
    return groups


def game_element(element: dict) -> dict:
    result = {
        "name": element["name"],
        "from": element["from"],
        "to": element["to"],
        "faces": {
            face: {"uv": values["uv"], "texture": "#rock" if values["texture"] == 0 else "#crystal"}
            for face, values in element["faces"].items()
        },
    }
    if "rotation" in element:
        axis = "z" if element["rotation"][2] else "x" if element["rotation"][0] else "y"
        angle = element["rotation"][{"x": 0, "y": 1, "z": 2}[axis]]
        result["rotation"] = {
            "angle": angle,
            "axis": axis,
            "origin": element["origin"],
            "rescale": False,
        }
    return result


def write_variant(deepslate: bool, rock: Image.Image, crystal: Image.Image) -> None:
    name = "heart_crystal_ore_deepslate" if deepslate else "heart_crystal_ore"
    rock_path = TEXTURES / f"{name}.png"
    crystal_path = TEXTURES / "heart_crystal_facets.png"
    rock.save(rock_path)
    if not crystal_path.exists():
        crystal.save(crystal_path)
    elements = build_elements()
    textures = [
        {
            "name": name,
            "id": "0",
            "width": 32,
            "height": 32,
            "uv_width": 32,
            "uv_height": 32,
            "source": png_data(rock),
        },
        {
            "name": "heart_crystal_facets",
            "id": "1",
            "width": 32,
            "height": 32,
            "uv_width": 32,
            "uv_height": 32,
            "source": png_data(crystal),
        },
    ]
    bbmodel = {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
        "name": name,
        "java_block_version": "26.3",
        "ambientocclusion": True,
        "front_gui_light": False,
        "resolution": {"width": 32, "height": 32},
        "elements": elements,
        "outliner": blockbench_outliner(elements),
        "textures": textures,
    }
    (ART / f"{name}.bbmodel").write_text(json.dumps(bbmodel, indent=2) + "\n", encoding="utf-8")
    model = {
        "credit": "Original heart-crystal ore geometry and textures by Prometheus for Jaymo's Powers; supplied amethyst photographs used as visual references only.",
        "parent": "minecraft:block/block",
        "ambientocclusion": True,
        "texture_size": [32, 32],
        "textures": {
            "rock": f"{NAMESPACE}:block/{name}",
            "crystal": f"{NAMESPACE}:block/heart_crystal_facets",
            "particle": "#rock",
        },
        "elements": [game_element(element) for element in elements],
    }
    (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    ART.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    crystal = crystal_texture()
    write_variant(False, rock_texture(False), crystal)
    write_variant(True, rock_texture(True), crystal)
    for name in ("heart_crystal_ore", "heart_crystal_ore_deepslate"):
        json.loads((ART / f"{name}.bbmodel").read_text(encoding="utf-8"))
        json.loads((MODELS / f"{name}.json").read_text(encoding="utf-8"))
    print(f"Generated 2 editable Blockbench models, 2 block models and 3 opaque textures ({len(build_elements())} elements per variant).")


if __name__ == "__main__":
    main()
