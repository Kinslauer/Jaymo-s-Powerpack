"""Generate the original Renaissance-study telescope art and editable Blockbench sources."""
from __future__ import annotations

import base64
import copy
import importlib
import io
import json
from pathlib import Path
import sys
import uuid

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / "kingdoms/src/main/resources/assets/jaymoskingdoms"
NS = uuid.UUID("d754f2d4-3876-4f75-9bd0-8e0ea3f97f2d")

INK = "#2b211a"
INK_SOFT = "#584333"
WOOD = "#755239"
WOOD_LIGHT = "#a07852"
WOOD_DARK = "#493426"
BRASS = "#a47c36"
BRASS_LIGHT = "#d0ab5a"
BRASS_DARK = "#604625"
GLASS = "#345d61"
GLASS_LIGHT = "#8aaca1"
GLASS_DARK = "#1e3435"
PAPER = "#c2a77a"
GUIDE = "#8d7351"


def png_source(image: Image.Image) -> str:
    buf = io.BytesIO()
    image.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode("ascii")


def make_atlas() -> Image.Image:
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)

    # Walnut: long grain and short, curved hatching rather than tonal gradients.
    d.rectangle((0, 0, 31, 31), fill=WOOD)
    for y, colour in ((2, WOOD_DARK), (9, INK_SOFT), (17, WOOD_DARK), (26, INK_SOFT)):
        d.line([(0, y), (5, y - 1), (11, y), (18, y + 1), (25, y), (31, y - 1)], fill=colour)
    for y in (5, 13, 22, 29):
        d.line([(1, y), (9, y - 1), (18, y), (30, y - 1)], fill=WOOD_LIGHT)
    for x in range(-8, 32, 5):
        d.line([(x, 31), (x + 13, 18)], fill=INK_SOFT)
    d.line([(0, 0), (31, 0), (31, 31), (0, 31), (0, 0)], fill=INK)

    # Aged brass: hard-edged lathe marks, hatch shadows and peened rivets.
    d.rectangle((32, 0, 63, 31), fill=BRASS)
    for y in (3, 8, 23, 28):
        d.line((32, y, 63, y), fill=BRASS_DARK)
    for y in (5, 20):
        d.line((32, y, 63, y), fill=BRASS_LIGHT)
    for x in range(24, 64, 5):
        d.line([(x, 31), (x + 13, 18)], fill=BRASS_DARK)
    for x, y in ((35, 4), (60, 4), (35, 15), (60, 15)):
        d.point((x, y), fill=INK)
        d.point((x + 1, y), fill=BRASS_LIGHT)
    d.rectangle((32, 0, 63, 31), outline=INK)

    # Objective glass: a study of a round lens, with an ink rim and two reflected strokes.
    d.rectangle((0, 32, 31, 63), fill=BRASS_DARK)
    d.ellipse((2, 34, 29, 61), fill=INK, outline=BRASS_LIGHT, width=1)
    d.ellipse((4, 36, 27, 59), fill=GLASS, outline=GLASS_DARK, width=2)
    d.arc((6, 37, 24, 55), 190, 290, fill=GLASS_LIGHT, width=2)
    d.arc((8, 42, 26, 60), 15, 105, fill=GLASS_DARK, width=1)
    d.line([(7, 52), (12, 57), (17, 59)], fill=GLASS_LIGHT)
    d.point((8, 39), fill=PAPER)

    # Engraved altitude scale. Construction lines remain visible behind the working scale.
    d.rectangle((32, 32, 63, 63), fill=PAPER)
    d.line((33, 47, 62, 47), fill=GUIDE)
    d.line((47, 33, 47, 62), fill=GUIDE)
    d.ellipse((35, 35, 59, 59), outline=GUIDE)
    d.arc((36, 35, 61, 61), 195, 345, fill=INK, width=2)
    cx, cy = 48, 54
    for angle in range(200, 341, 20):
        import math
        a = math.radians(angle)
        x0 = round(cx + 9 * math.cos(a))
        y0 = round(cy + 9 * math.sin(a))
        x1 = round(cx + 13 * math.cos(a))
        y1 = round(cy + 13 * math.sin(a))
        d.line((x0, y0, x1, y1), fill=INK)
    d.ellipse((46, 52, 50, 56), fill=BRASS, outline=INK)
    d.line((48, 54, 58, 42), fill=INK, width=2)
    d.rectangle((32, 32, 63, 63), outline=INK)
    return im


def make_icon() -> Image.Image:
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # A side-profile instrument: one broad objective, a tapered wooden tube and
    # an unmistakable three-legged stand. Keep the lens large enough to survive
    # at inventory scale; thin concentric rings made the old icon read as a bug.
    d.line([(2, 8), (11, 4)], fill=INK, width=4)
    d.line([(3, 8), (10, 5)], fill=WOOD, width=2)
    d.line([(4, 7), (10, 4)], fill=WOOD_LIGHT)
    d.line([(0, 9), (3, 8)], fill=INK, width=2)
    d.point((1, 9), fill=BRASS_LIGHT)
    d.point((2, 8), fill=BRASS)
    # Binding straps cross the tube rather than segmenting it into a body.
    for x, y in ((5, 7), (9, 5)):
        d.line([(x - 1, y - 1), (x + 1, y + 1)], fill=BRASS_LIGHT)
        d.point((x + 1, y), fill=BRASS_DARK)
    d.rectangle((11, 1, 15, 5), fill=INK)
    d.line([(12, 1), (14, 1), (14, 4), (12, 4)], fill=BRASS_LIGHT)
    d.rectangle((12, 2, 14, 4), fill=BRASS)
    d.rectangle((13, 2, 14, 3), fill=GLASS)
    d.point((13, 2), fill=GLASS_LIGHT)
    d.rectangle((7, 8, 9, 10), fill=INK)
    d.point((8, 8), fill=BRASS_LIGHT)
    d.point((8, 9), fill=BRASS)
    # Three separated legs, with the rear leg shortened in perspective.
    legs = [((7, 10), (3, 15)), ((8, 10), (8, 15)), ((9, 10), (12, 14))]
    for start, end in legs:
        d.line([start, end], fill=INK, width=2)
    d.line([(7, 11), (4, 14)], fill=WOOD_LIGHT)
    d.line([(8, 11), (8, 14)], fill=WOOD)
    d.line([(10, 11), (12, 13)], fill=WOOD_LIGHT)
    for x, y in ((3, 15), (8, 15), (12, 14)):
        d.point((x, y), fill=BRASS_LIGHT)
    return im


class Model:
    def __init__(self, name: str, texture: Image.Image, resolution: int):
        self.name = name
        self.texture = texture
        self.resolution = resolution
        self.elements: list[dict] = []
        self.groups: dict[str, list[str]] = {}

    def cube(self, group: str, name: str, bounds, material="wood", face_materials=None,
             rotation=(0, 0, 0), origin=(0, 0, 0)):
        ident = str(uuid.uuid5(NS, self.name + "/" + name))
        regions = {"wood": (0, 0, 32, 32), "brass": (32, 0, 64, 32),
                   "glass": (0, 32, 32, 64), "dial": (32, 32, 64, 64)}
        faces = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            mat = (face_materials or {}).get(face, material)
            faces[face] = {"uv": list(regions[mat]), "texture": 0}
        element = {
            "name": name, "box_uv": False, "rescale": False, "locked": False,
            "from": list(bounds[:3]), "to": list(bounds[3:]), "autouv": 0,
            "origin": list(origin), "rotation": list(rotation), "faces": faces,
            "type": "cube", "uuid": ident,
        }
        self.elements.append(element)
        self.groups.setdefault(group, []).append(ident)

    def data(self) -> dict:
        group_ids = {}
        outliner = []
        for group, children in self.groups.items():
            gid = str(uuid.uuid5(NS, self.name + "/group/" + group))
            group_ids[group] = gid
            outliner.append({"name": group, "origin": [8, 10, 8], "color": 0,
                             "uuid": gid, "export": True, "mirrorUV": False,
                             "isOpen": True, "locked": False, "visibility": True,
                             "autouv": 0, "children": children})
        return {
            "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False},
            "name": self.name, "java_block_version": "26.3", "ambientocclusion": True,
            "front_gui_light": False, "credit": "Original art for Jaymo's Kingdoms; no external assets used.",
            "resolution": {"width": self.resolution, "height": self.resolution},
            "elements": self.elements, "outliner": outliner,
            "textures": [{"name": self.name, "id": "0", "width": self.resolution,
                          "height": self.resolution, "uv_width": self.resolution,
                          "uv_height": self.resolution, "source": png_source(self.texture)}],
        }

    def geckolib_data(self) -> dict:
        """The placed model centred for GeckoLib, with a fixed base and two aiming joints."""
        assert self.name == "telescope"
        data = self.data()
        data["credit"] += " Articulated base/azimuth/elevation rig for GeckoLib."
        data["elements"] = copy.deepcopy(self.elements)
        for element in data["elements"]:
            for key in ("from", "to", "origin"):
                element[key][0] -= 8
                element[key][1] += 0.82
                element[key][2] -= 8

        def node(name, origin, children):
            return {"name": name, "origin": origin, "color": 0,
                    "uuid": str(uuid.uuid5(NS, self.name + "/rig/" + name)),
                    "export": True, "mirrorUV": False, "isOpen": True, "locked": False,
                    "visibility": True, "autouv": 0, "children": children}

        elevation = node("elevation", [0, 13.82, 0], self.groups["barrel"] + self.groups["optics"])
        azimuth = node("azimuth", [0, 11.32, 0], self.groups["mount"] + [elevation])
        data["outliner"] = [node("base", [0, 0, 0], self.groups["tripod"] + [azimuth])]

        def keys(entries):
            return [{"channel": "rotation", "data_points": [{"x": vector[0], "y": vector[1], "z": vector[2]}],
                     "time": time, "interpolation": "linear"} for time, vector in entries]

        def animation(name, tracks, length=1.0, loop="once"):
            animators = {}
            for bone, entries in tracks.items():
                ident = str(uuid.uuid5(NS, self.name + "/rig/" + bone))
                animators[ident] = {"name": bone, "type": "bone", "keyframes": keys(entries)}
            return {"name": name, "loop": loop, "length": length, "animators": animators}

        zero = {"azimuth": [(0, (0, 0, 0))], "elevation": [(0, (0, 0, 0))]}
        data["animations"] = [
            animation("idle", zero, loop="loop"),
            animation("azimuth", {"azimuth": [(0, (0, 0, 0)), (1, (0, 90, 0)), (2, (0, 0, 0))]}, length=2.0),
            animation("elevation", {"elevation": [(0, (0, 0, 0)), (1, (-22.5, 0, 0)), (2, (0, 0, 0))]}, length=2.0),
            animation("aim", {"azimuth": [(0, (0, 0, 0)), (1, (0, 90, 0))],
                              "elevation": [(0, (0, 0, 0)), (1, (-22.5, 0, 0))]}, loop="hold"),
        ]
        return data


def stepped_barrel(model: Model, group: str, prefix: str, z0: float, z1: float,
                   material: str, rotation, origin, over=0.0):
    model.cube(group, prefix + "_core", (6.4 - over, 10.8 - over, z0, 9.6 + over, 15.2 + over, z1),
               material, rotation=rotation, origin=origin)
    model.cube(group, prefix + "_left", (5.5 - over, 11.7 - over, z0, 6.4 - over, 14.3 + over, z1),
               material, rotation=rotation, origin=origin)
    model.cube(group, prefix + "_right", (9.6 + over, 11.7 - over, z0, 10.5 + over, 14.3 + over, z1),
               material, rotation=rotation, origin=origin)


def build_block_model(atlas: Image.Image) -> Model:
    m = Model("telescope", atlas, 64)
    for label, rotation in (("left", (0, 0, -22.5)), ("right", (0, 0, 22.5)),
                            ("back", (22.5, 0, 0))):
        m.cube("tripod", label + "_leg", (7.6, -0.82, 7.6, 8.4, 9.1, 8.4),
               rotation=rotation, origin=(8, 10, 8))
        m.cube("tripod", label + "_hinge", (7.35, 8.2, 7.35, 8.65, 10, 8.65),
               "brass", rotation=rotation, origin=(8, 10, 8))
        m.cube("tripod", label + "_foot", (7.35, -0.82, 7.35, 8.65, 0.2, 8.65),
               "brass", rotation=rotation, origin=(8, 10, 8))
    m.cube("tripod", "head_block", (6.5, 9.3, 6.5, 9.5, 11, 9.5))
    m.cube("tripod", "head_collar", (6.2, 9.7, 6.2, 9.8, 10.5, 9.8), "brass")

    m.cube("mount", "left_support", (5.4, 10.5, 7.4, 6.2, 13.2, 8.6))
    m.cube("mount", "right_support", (9.8, 10.5, 7.4, 10.6, 13.2, 8.6))
    m.cube("mount", "pivot_axle", (5, 12.4, 7.5, 11, 13.4, 8.5), "brass")
    m.cube("mount", "altitude_scale", (10.65, 10.2, 5.8, 10.85, 13.4, 10.2), "brass",
           {"east": "dial", "west": "dial"})

    rot = (22.5, 0, 0)
    pivot = (8, 13, 8)
    stepped_barrel(m, "barrel", "walnut_tube", 0, 15, "wood", rot, pivot)
    for i, (z0, z1) in enumerate(((1, 1.8), (5, 5.8), (10.5, 11.3), (14, 14.8))):
        stepped_barrel(m, "barrel", f"brass_binding_{i + 1}", z0, z1, "brass", rot, pivot, 0.18)
    stepped_barrel(m, "optics", "objective_rim", -0.7, 0.2, "brass", rot, pivot, 0.3)
    m.cube("optics", "objective_lens", (6.4, 11, -0.78, 9.6, 15, -0.7), "glass",
           rotation=rot, origin=pivot)
    m.cube("optics", "eyepiece", (7, 11.8, 15, 9, 14.2, 16.8), "brass", rotation=rot, origin=pivot)
    m.cube("optics", "eyepiece_glass", (7.2, 12, 16.8, 8.8, 14, 16.86), "glass",
           rotation=rot, origin=pivot)
    m.cube("optics", "focus_screw", (10.45, 12.4, 11.6, 11.45, 13.6, 12.8), "brass",
           rotation=rot, origin=pivot)
    return m


def build_item_model(icon: Image.Image) -> Model:
    m = Model("telescope_item", icon, 16)
    m.cube("icon", "engraved_telescope_sprite", (0, 0, 7.75, 16, 16, 8.25))
    # Only the broad faces carry the sprite; edge UVs sample its transparent corner.
    for name, face in m.elements[0]["faces"].items():
        face["uv"] = [0, 0, 16, 16] if name in ("north", "south") else [0, 0, 1, 1]
    return m


def export_java_block(model: Model) -> dict:
    elements = []
    for e in model.elements:
        out = {"from": e["from"], "to": e["to"], "faces": {}}
        for name, face in e["faces"].items():
            out["faces"][name] = {"uv": face["uv"], "texture": "#0"}
        axes = [(i, value) for i, value in enumerate(e["rotation"]) if value]
        if axes:
            if len(axes) != 1:
                raise ValueError(f"Minecraft needs one rotation axis: {e['name']}")
            axis, angle = axes[0]
            out["rotation"] = {"origin": e["origin"], "axis": "xyz"[axis], "angle": angle}
        elements.append(out)
    return {"credit": "Original art for Jaymo's Kingdoms; no external assets used.",
            "parent": "minecraft:block/block", "texture_size": [64, 64],
            "textures": {"0": "jaymoskingdoms:block/telescope",
                         "particle": "jaymoskingdoms:block/telescope"},
            "elements": elements}


def dump(path: Path, data: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    atlas = make_atlas()
    icon = make_icon()
    atlas.save(ASSETS / "textures/block/telescope.png")
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    icon.save(ASSETS / "textures/item/telescope.png")
    block = build_block_model(atlas)
    item = build_item_model(icon)
    block_source = block.geckolib_data()
    dump(HERE / "telescope.bbmodel", block_source)
    dump(HERE / "telescope_item.bbmodel", item.data())
    dump(ASSETS / "models/block/telescope.json", export_java_block(block))
    dump(ASSETS / "models/item/telescope.json", {
        "credit": "Original art for Jaymo's Kingdoms; no external assets used.",
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "jaymoskingdoms:item/telescope"},
    })
    dump(ASSETS / "items/telescope.json", {
        "model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/telescope"}
    })

    sys.path.insert(0, str(ROOT / "tools"))
    convert = importlib.import_module("bbmodel_geckolib").convert
    geo, animations, _ = convert(block_source, "telescope")
    dump(ASSETS / "geckolib/models/block/telescope.geo.json", geo)
    dump(ASSETS / "geckolib/animations/block/telescope.animation.json", animations)
    print(f"Wrote {len(block.elements)} block elements, the GeckoLib aiming rig and the 16x16 item icon")


if __name__ == "__main__":
    main()
