# HUNTERS ROAD 3 MODELS GENERATOR (PART 1 - header + helpers + Model class)

"""Hunters Road 3 models (Kingdoms): grave-gnawer, bloated dead, grave-brute,
bone-burrower, nest block and old-bones.

Builds Blockbench free-format .bbmodels (editable source, kept in kingdoms/art)
and converts them to GeckoLib geo + animation + texture with
tools/bbmodel_geckolib.py. Textures are painted here as clean vanilla-style
pixel art: flat shade ramps (4-7 shades), no gradients, no noise, under ~60
colours, no half-transparent pixels. Licence: all our own art - nothing from
The Conjunction of the Spheres / CD Projekt, kept apart from Digger and the
Nightfall plague in look.

Run from the repo root:
    python3 -I kingdoms/tools/gen_hunters_road_models.py
"""
from __future__ import annotations

import base64
import io
import json
import os
import subprocess
import sys
import uuid
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
ART = os.path.join(ROOT, "kingdoms", "art")
GEO_DIR = os.path.join(ASSETS, "geckolib", "models", "entity")
ANIM_DIR = os.path.join(ASSETS, "geckolib", "animations", "entity")
TEX_DIR = os.path.join(ASSETS, "textures", "entity")

SW = 8  # texture swatch size in pixels (64x64 sheet -> 8 swatches across)

# Vanilla-style corpse/cave palette: flat shade ramps, no gradients.
PALETTE = {
    "flesh": (200, 200, 184),     # corpse pale
    "flesh_d": (160, 160, 148),   # shaded flesh
    "flesh_dd": (122, 122, 112),  # deep shadow flesh
    "bone": (238, 232, 220),      # clean bone
    "bone_d": (204, 196, 184),    # shaded bone
    "filth": (76, 60, 46),        # grave filth
    "filth_d": (50, 38, 28),      # deep filth
    "skin": (224, 192, 180),      # pinkish dead flesh
    "skin_d": (180, 152, 144),    # shaded skin
    "socket": (20, 20, 20),       # eye sockets / mouth
    "spine": (96, 96, 104),       # bone spines
    "spine_d": (60, 60, 68),      # shaded spines
    "sick": (132, 152, 112),      # bloated sick green
    "sick_d": (92, 112, 80),      # shaded sick
    "grave": (110, 96, 84),       # old grave cloth / stone
    "grave_d": (80, 68, 58),
}

FACES = ("north", "south", "east", "west", "up", "down")


def uid(*parts):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jaymoskingdoms:hunters_road:" + ":".join(str(p) for p in parts)))


class Model:
    """One Blockbench free-format model plus its single embedded texture sheet."""

    def __init__(self, name, tex_w, tex_h):
        self.name = name
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.img = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.img)
        self.swatches = []
        self.elements = []
        self.groups = []
        self.group_uuid = {}  # bone name -> group uuid
        self.root_groups = []  # group uuids with no parent
        self.animations = []  # Blockbench-format animations

    # ---- texture -------------------------------------------------------------
    def swatch(self, key, gx, gy, gw=1, gh=1):
        """Paint one swatch on the sheet and return its pixel UV rect."""
        color = PALETTE[key]
        x0, y0, x1, y1 = gx * SW, gy * SW, (gx + gw) * SW, (gy + gh) * SW
        self.draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color + (255,))
        self.swatches.append((gx, gy, gw, gh, color))
        return (x0, y0, x1, y1)

    def uv(self, key, gx, gy, gw=1, gh=1):
        return list(self.swatch(key, gx, gy, gw, gh))

    # ---- model building ------------------------------------------------------
    def bone(self, name, parent, pivot, rot=(0, 0, 0)):
        u = uid("bone", name)
        self.group_uuid[name] = u
        g = {
            "name": name,
            "origin": [float(v) for v in pivot],
            "rotation": [float(v) for v in rot],
            "color": 0, "isOpen": True, "export": True, "visibility": True,
            "mirror_uv": False, "locked": False, "uuid": u,
            "children": [], "parent": parent,
        }
        self.groups.append(g)
        if parent is None:
            self.root_groups.append(u)
        else:
            # link this bone into its parent group's children
            for pg in self.groups:
                if pg["uuid"] == self.group_uuid.get(parent):
                    pg["children"].append(u)
                    break
        return u

    def cube(self, bone, name, f, t, swatch_uv, origin=None, rot=None, inflate=0):
        bu = self.group_uuid[bone]
        u = uid("cube", name)
        x0, y0, z0 = f
        x1, y1, z1 = t
        o = origin if origin is not None else [x0, y0, z0]
        e = {
            "name": name, "box_uv": False, "rescale": False, "locked": False,
            "from": [float(x0), float(y0), float(z0)],
            "to": [float(x1), float(y1), float(z1)], "autouv": 0,
            "color": len(self.elements) % 8,
            "origin": [float(v) for v in o], "inflate": float(inflate),
            "faces": {face: {"uv": list(swatch_uv), "texture": 0} for face in FACES},
            "type": "cube", "uuid": u,
        }
        if rot is not None:
            e["rotation"] = [float(v) for v in rot]
        self.elements.append(e)
        for g in self.groups:
            if g["uuid"] == bu:
                g["children"].append(u)
                break
        return u

    def bbmodel(self):
        by_uuid = {g["uuid"]: g for g in self.groups}

        def attach(uuid_):
            g = by_uuid[uuid_]
            node = {k: v for k, v in g.items() if k != "uuid"}
            node["uuid"] = g["uuid"]
            kids = []
            for c in g["children"]:
                if c in by_uuid:
                    kids.append(attach(c))
                else:
                    kids.append(c)  # element uuid reference
            if kids:
                node["children"] = kids
            return node

        outliner = [attach(u) for u in self.root_groups]

        tex = self.img
        buf = io.BytesIO()
        tex.save(buf, format="PNG")
        encoded = base64.b64encode(buf.getvalue()).decode("ascii")

        return {
            "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
            "name": self.name, "model_identifier": self.name, "visible_box": [4, 4, 1],
            "resolution": {"width": self.tex_w, "height": self.tex_h},
            "elements": self.elements,
            "outliner": outliner,
            "textures": [{
                "name": self.name + ".png", "folder": self.name, "namespace": "jaymoskingdoms",
                "id": "0", "uuid": uid("texture", self.name),
                "relative_path": "textures/entity/" + self.name + ".png",
                "source": "data:image/png;base64," + encoded,
                "width": self.tex_w, "height": self.tex_h,
                "uv_width": self.tex_w, "uv_height": self.tex_h, "visible": True,
            }],
            "animations": self.animations,
        }


def write_bbmodel(model):
    path = os.path.join(ART, model.name + ".bbmodel")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model.bbmodel(), f, indent="\t")
        f.write("\n")
    return path


def convert(model_name):
    """Run bbmodel_geckolib.py to emit geo + animation + texture sheet."""
    subprocess.run(
        [sys.executable, "-I", os.path.join(ROOT, "tools", "bbmodel_geckolib.py"),
         os.path.join(ART, model_name + ".bbmodel"), ASSETS, model_name],
        check=True,
    )


def render(model_name, out_png):
    """Render the bbmodel with bbrender.py for visual verification."""
    subprocess.run(
        [sys.executable, os.path.join(ROOT, "tools", "mesh_to_cubes", "bbrender.py"),
         os.path.join(ART, model_name + ".bbmodel"), out_png],
        check=True,
    )

# =============================================================================
# Per-entity geometry. Blockbench space: y up, ground at 0, centred on x=0.
# Each swatch is one solid shade; every cube face maps to one swatch so the
# whole model reads as clean flat-shaded cuboids.
# =============================================================================

def grave_gnaver():
    """grave-gnawer: hunched pack corpse-eater, wolf-sized, low to ground."""
    m = Model("grave_gnawer", 64, 64)
    m.uv("flesh", 0, 0)
    m.uv("flesh_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("spine", 4, 0)
    m.bone("body", None, (0, 2, 0))
    m.cube("body", "torso", (-3, 1, -2.5), (3, 3.4, 2.5), m.uv("flesh", 0, 0), origin=(-3, 1, -2.5))
    m.cube("body", "belly", (-2, 0.5, -1.5), (2, 1.6, 1.5), m.uv("flesh_d", 1, 0), origin=(-2, 0.5, -1.5))
    m.bone("head", "body", (0, 3.2, -2.4), (0, 0, 0))
    m.cube("head", "head", (-1.6, -0.4, -2.4), (1.6, 1.6, -0.6), m.uv("flesh_d", 1, 0), origin=(-1.6, -0.4, -2.4))
    m.cube("head", "jaw", (-1.4, 0.4, -2.2), (1.4, 1.2, -1.6), m.uv("bone", 2, 0), origin=(-1.4, 0.4, -2.2))
    m.cube("head", "leye", (-1.0, 0.8, -2.35), (-0.6, 1.2, -2.25), m.uv("socket", 3, 0))
    m.cube("head", "reye", (0.6, 0.8, -2.35), (1.0, 1.2, -2.25), m.uv("socket", 3, 0))
    # legs: feet on the ground (y = 0)
    m.cube("body", "leg_fl", (-2.4, 0.0, 1.4), (-1.6, 1.4, 2.2), m.uv("flesh_d", 1, 0), origin=(-2.4, 0.0, 1.4))
    m.cube("body", "leg_fr", (1.6, 0.0, 1.4), (2.4, 1.4, 2.2), m.uv("flesh_d", 1, 0), origin=(1.6, 0.0, 1.4))
    m.cube("body", "leg_bl", (-2.4, 0.0, -1.8), (-1.6, 1.4, -1.0), m.uv("flesh_d", 1, 0), origin=(-2.4, 0.0, -1.8))
    m.cube("body", "leg_br", (1.6, 0.0, -1.8), (2.4, 1.4, -1.0), m.uv("flesh_d", 1, 0), origin=(1.6, 0.0, -1.8))
    m.bone("tail", "body", (-3.2, 2.4, 0))
    m.cube("tail", "tail", (-4.0, 0.8, -0.6), (-3.2, 1.6, 0.6), m.uv("flesh", 0, 0), origin=(-4.0, 0.8, -0.6), rot=(0, 0, 20))
    return m


def pack_leader():
    """spined pack-leader: bigger grave-gnawer with back spines."""
    m = Model("pack_leader", 64, 64)
    m.uv("flesh", 0, 0)
    m.uv("flesh_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("spine", 4, 0)
    m.bone("body", None, (0, 2.6, 0))
    m.cube("body", "torso", (-4, 1.2, -3), (4, 3.6, 3), m.uv("flesh", 0, 0), origin=(-4, 1.2, -3))
    m.cube("body", "belly", (-2.6, 0.6, -2), (2.6, 1.6, 2), m.uv("flesh_d", 1, 0), origin=(-2.6, 0.6, -2))
    m.bone("head", "body", (0, 3, -2.4), (0, 0, 0))
    m.cube("head", "head", (-2, -0.6, -2.4), (2, 1.8, -0.4), m.uv("flesh", 0, 0), origin=(-2, -0.6, -2.4))
    m.cube("head", "jaw", (-1.6, 0.2, -2.2), (1.6, 1.2, -1.6), m.uv("bone", 2, 0), origin=(-1.6, 0.2, -2.2))
    m.cube("head", "leye", (-1.2, 0.8, -2.35), (-0.7, 1.2, -2.25), m.uv("socket", 3, 0))
    m.cube("head", "reye", (0.7, 0.8, -2.35), (1.2, 1.2, -2.25), m.uv("socket", 3, 0))
    for i, sx in enumerate((-2.4, -0.8, 0.8, 2.4)):
        m.cube("body", "spine%d" % i, (sx - 0.5, 3.2, -0.4), (sx + 0.5, 4.6, 0.4), m.uv("spine", 4, 0),
               origin=(sx - 0.5, 3.2, -0.4), rot=(0, 0, 0))
    m.cube("body", "leg_fl", (-3.2, 0.0, 1.4), (-2.2, 1.4, 2.4), m.uv("flesh_d", 1, 0), origin=(-3.2, 0.0, 1.4))
    m.cube("body", "leg_fr", (2.2, 0.0, 1.4), (3.2, 1.4, 2.4), m.uv("flesh_d", 1, 0), origin=(2.2, 0.0, 1.4))
    m.cube("body", "leg_bl", (-3.2, -0.6, -2.0), (-2.2, 1.4, -1.0), m.uv("flesh_d", 1, 0), origin=(-3.2, -0.6, -2.0))
    m.cube("body", "leg_br", (2.2, -0.6, -2.0), (3.2, 1.4, -1.0), m.uv("flesh_d", 1, 0), origin=(2.2, -0.6, -2.0))
    m.bone("tail", "body", (-3.8, 2.4, 0))
    m.cube("tail", "tail", (-4.4, 0.6, -0.8), (-3.8, 1.4, 0.8), m.uv("flesh", 0, 0), origin=(-4.4, 0.6, -0.8), rot=(0, 0, 20))
    return m


def bloated_dead():
    """bloated dead: swollen zombie, round, slow."""
    m = Model("bloated_dead", 64, 64)
    m.uv("sick", 0, 0)
    m.uv("sick_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("filth", 4, 0)
    m.bone("body", None, (0, 2.2, 0))
    m.cube("body", "torso", (-2.6, 0.6, -2.2), (2.6, 3.4, 2.2), m.uv("sick", 0, 0), origin=(-2.6, 0.6, -2.2))
    m.cube("body", "belly", (-2.0, 0.2, -1.2), (2.0, 2.4, 1.2), m.uv("sick_d", 1, 0), origin=(-2.0, 0.2, -1.2))
    m.bone("head", "body", (0, 2.4, -1.6), (0, 0, 0))
    m.cube("head", "head", (-1.8, -0.6, -2.0), (1.8, 1.6, -0.2), m.uv("sick", 0, 0), origin=(-1.8, -0.6, -2.0))
    m.cube("head", "jaw", (-1.5, 0.4, -1.8), (1.5, 1.2, -1.2), m.uv("bone", 2, 0), origin=(-1.5, 0.4, -1.8))
    m.cube("head", "leye", (-1.1, 0.6, -1.95), (-0.6, 1.0, -1.85), m.uv("socket", 3, 0))
    m.cube("head", "reye", (0.6, 0.6, -1.95), (1.1, 1.0, -1.85), m.uv("socket", 3, 0))
    m.cube("body", "arm_l", (-3.0, 0.8, -1.0), (-2.0, 2.4, 0.6), m.uv("sick_d", 1, 0), origin=(-3.0, 0.8, -1.0))
    m.cube("body", "arm_r", (2.0, 0.8, -1.0), (3.0, 2.4, 0.6), m.uv("sick_d", 1, 0), origin=(2.0, 0.8, -1.0))
    m.cube("body", "leg_l", (-1.4, -0.6, 1.0), (-0.6, 1.2, 2.0), m.uv("sick_d", 1, 0), origin=(-1.4, -0.6, 1.0))
    m.cube("body", "leg_r", (0.6, -0.6, 1.0), (1.4, 1.2, 2.0), m.uv("sick_d", 1, 0), origin=(0.6, -0.6, 1.0))
    return m


def spined_bloated():
    """spined bloated dead: bloated dead with back spines."""
    m = Model("spined_bloated", 64, 64)
    m.uv("sick", 0, 0)
    m.uv("sick_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("spine", 4, 0)
    m.bone("body", None, (0, 2.4, 0))
    m.cube("body", "torso", (-3.0, 0.8, -2.4), (3.0, 3.6, 2.4), m.uv("sick", 0, 0), origin=(-3.0, 0.8, -2.4))
    m.cube("body", "belly", (-2.2, 0.4, -1.4), (2.2, 2.6, 1.4), m.uv("sick_d", 1, 0), origin=(-2.2, 0.4, -1.4))
    m.bone("head", "body", (0, 2.6, -1.8), (0, 0, 0))
    m.cube("head", "head", (-2.0, -0.6, -2.2), (2.0, 1.8, -0.4), m.uv("sick", 0, 0), origin=(-2.0, -0.6, -2.2))
    m.cube("head", "jaw", (-1.6, 0.4, -2.0), (1.6, 1.2, -1.4), m.uv("bone", 2, 0), origin=(-1.6, 0.4, -2.0))
    m.cube("head", "leye", (-1.2, 0.6, -2.15), (-0.7, 1.0, -2.05), m.uv("socket", 3, 0))
    m.cube("head", "reye", (0.7, 0.6, -2.15), (1.2, 1.0, -2.05), m.uv("socket", 3, 0))
    for i, sx in enumerate((-2.0, -0.6, 0.6, 2.0)):
        m.cube("body", "spine%d" % i, (sx - 0.5, 3.4, -0.5), (sx + 0.5, 4.8, 0.5), m.uv("spine", 4, 0),
               origin=(sx - 0.5, 3.4, -0.5))
    m.cube("body", "arm_l", (-3.4, 1.0, -1.0), (-2.2, 2.6, 0.6), m.uv("sick_d", 1, 0), origin=(-3.4, 1.0, -1.0))
    m.cube("body", "arm_r", (2.2, 1.0, -1.0), (3.4, 2.6, 0.6), m.uv("sick_d", 1, 0), origin=(2.2, 1.0, -1.0))
    m.cube("body", "leg_l", (-1.6, -0.6, 1.0), (-0.8, 1.4, 2.2), m.uv("sick_d", 1, 0), origin=(-1.6, -0.6, 1.0))
    m.cube("body", "leg_r", (0.8, -0.6, 1.0), (1.6, 1.4, 2.2), m.uv("sick_d", 1, 0), origin=(0.8, -0.6, 1.0))
    return m


def grave_brute():
    """grave-brute: deep-cave miniboss, big charger, larger than the gnawer."""
    m = Model("grave_brute", 96, 96)
    m.uv("flesh", 0, 0)
    m.uv("flesh_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("spine", 4, 0)
    m.bone("body", None, (0, 3.4, 0))
    m.cube("body", "torso", (-5, 1.4, -3.5), (5, 4.6, 3.5), m.uv("flesh", 0, 0), origin=(-5, 1.4, -3.5))
    m.cube("body", "belly", (-3.6, 0.8, -2.4), (3.6, 2.2, 2.4), m.uv("flesh_d", 1, 0), origin=(-3.6, 0.8, -2.4))
    m.bone("head", "body", (0, 4, -3.2), (0, 0, 0))
    m.cube("head", "head", (-3, -0.8, -3.4), (3, 2.6, -0.8), m.uv("flesh", 0, 0), origin=(-3, -0.8, -3.4))
    m.cube("head", "jaw", (-2.6, 0.4, -3.2), (2.6, 1.8, -2.4), m.uv("bone", 2, 0), origin=(-2.6, 0.4, -3.2))
    m.cube("head", "leye", (-1.9, 1.0, -3.35), (-1.3, 1.6, -3.25), m.uv("socket", 3, 0))
    m.cube("head", "reye", (1.3, 1.0, -3.35), (1.9, 1.6, -3.25), m.uv("socket", 3, 0))
    m.cube("body", "arm_l", (-5.6, 1.6, -1.4), (-4.0, 3.8, 0.8), m.uv("flesh_d", 1, 0), origin=(-5.6, 1.6, -1.4))
    m.cube("body", "arm_r", (4.0, 1.6, -1.4), (5.6, 3.8, 0.8), m.uv("flesh_d", 1, 0), origin=(4.0, 1.6, -1.4))
    m.cube("body", "leg_l", (-2.4, -0.6, 1.6), (-1.2, 2.2, 3.2), m.uv("flesh_d", 1, 0), origin=(-2.4, -0.6, 1.6))
    m.cube("body", "leg_r", (1.2, -0.6, 1.6), (2.4, 2.2, 3.2), m.uv("flesh_d", 1, 0), origin=(1.2, -0.6, 1.6))
    m.cube("body", "leg_lb", (-2.4, -0.6, -2.8), (-1.2, 2.2, -1.6), m.uv("flesh_d", 1, 0), origin=(-2.4, -0.6, -2.8))
    m.cube("body", "leg_rb", (1.2, -0.6, -2.8), (2.4, 2.2, -1.6), m.uv("flesh_d", 1, 0), origin=(1.2, -0.6, -2.8))
    m.bone("tail", "body", (-5.4, 3.2, 0))
    m.cube("tail", "tail", (-6.2, 0.8, -1.0), (-5.4, 2.0, 1.0), m.uv("flesh", 0, 0), origin=(-6.2, 0.8, -1.0), rot=(0, 0, 15))
    return m


def bone_burrower():
    """bone-burrower: segmented grub that burrows and leaps."""
    m = Model("bone_burrower", 64, 64)
    m.uv("flesh", 0, 0)
    m.uv("flesh_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("filth", 4, 0)
    m.bone("body", None, (0, 0.8, 0))
    segs = [(-2.6, 0.2, -1.8), (-1.2, 0.2, -1.0), (0.2, 0.2, -0.6), (1.6, 0.2, 0.2)]
    for i, (x, y, z) in enumerate(segs):
        m.cube("body", "seg%d" % i, (x, y, z - 0.7), (x + 1.4, y + 1.0, z + 0.7),
               m.uv("flesh" if i % 2 == 0 else "flesh_d", 0 if i % 2 == 0 else 1, 0),
               origin=(x, y, z - 0.7))
    m.bone("head", "body", (-2.6, 0.6, -2.4), (0, 0, 0))
    m.cube("head", "head", (-3.2, 0.0, -3.2), (-2.2, 1.2, -2.2), m.uv("flesh", 0, 0), origin=(-3.2, 0.0, -3.2))
    m.cube("head", "mouth", (-3.2, 0.4, -3.2), (-2.2, 0.9, -3.0), m.uv("bone", 2, 0), origin=(-3.2, 0.4, -3.2))
    m.cube("head", "leye", (-3.0, 0.5, -3.1), (-2.7, 0.8, -3.05), m.uv("socket", 3, 0))
    m.cube("head", "reye", (-2.5, 0.5, -3.1), (-2.2, 0.8, -3.05), m.uv("socket", 3, 0))
    m.cube("body", "claw_l", (-2.6, -0.4, -1.6), (-1.8, 0.4, -1.0), m.uv("bone", 2, 0), origin=(-2.6, -0.4, -1.6))
    m.cube("body", "claw_r", (1.8, -0.4, -1.6), (2.6, 0.4, -1.0), m.uv("bone", 2, 0), origin=(1.8, -0.4, -1.6))
    m.bone("tail", "body", (1.6, 0.6, 0.8), (0, 0, 0))
    m.cube("tail", "tail", (1.0, 0.2, 0.2), (2.4, 1.0, 1.4), m.uv("flesh_d", 1, 0), origin=(1.0, 0.2, 0.2), rot=(0, 0, -15))
    return m


def heavy_burrower():
    """heavy bone-burrower: bigger, tougher burrower variant."""
    m = Model("heavy_burrower", 64, 64)
    m.uv("flesh", 0, 0)
    m.uv("flesh_d", 1, 0)
    m.uv("bone", 2, 0)
    m.uv("socket", 3, 0)
    m.uv("filth", 4, 0)
    m.bone("body", None, (0, 1.0, 0))
    segs = [(-3.4, 0.3, -2.4), (-1.6, 0.3, -1.4), (0.2, 0.3, -0.8), (2.0, 0.3, 0.2)]
    for i, (x, y, z) in enumerate(segs):
        m.cube("body", "seg%d" % i, (x, y, z - 0.9), (x + 1.8, y + 1.4, z + 0.9),
               m.uv("flesh" if i % 2 == 0 else "flesh_d", 0 if i % 2 == 0 else 1, 0),
               origin=(x, y, z - 0.9))
    m.bone("head", "body", (-3.4, 0.8, -3.0), (0, 0, 0))
    m.cube("head", "head", (-4.2, 0.0, -4.0), (-3.0, 1.6, -3.0), m.uv("flesh", 0, 0), origin=(-4.2, 0.0, -4.0))
    m.cube("head", "mouth", (-4.2, 0.5, -4.0), (-3.0, 1.2, -3.7), m.uv("bone", 2, 0), origin=(-4.2, 0.5, -4.0))
    m.cube("head", "leye", (-3.9, 0.6, -3.9), (-3.5, 1.0, -3.8), m.uv("socket", 3, 0))
    m.cube("head", "reye", (-3.3, 0.6, -3.9), (-2.9, 1.0, -3.8), m.uv("socket", 3, 0))
    m.cube("body", "claw_l", (-3.4, -0.4, -2.0), (-2.4, 0.6, -1.4), m.uv("bone", 2, 0), origin=(-3.4, -0.4, -2.0))
    m.cube("body", "claw_r", (2.4, -0.4, -2.0), (3.4, 0.6, -1.4), m.uv("bone", 2, 0), origin=(2.4, -0.4, -2.0))
    m.bone("tail", "body", (2.0, 0.8, 1.0), (0, 0, 0))
    m.cube("tail", "tail", (1.4, 0.3, 0.4), (3.2, 1.3, 1.8), m.uv("flesh_d", 1, 0), origin=(1.4, 0.3, 0.4), rot=(0, 0, -15))
    return m


def nest_block():
    """nest block: bones and filth, a decorative block (sits flat, no animation)."""
    m = Model("nest_block", 64, 64)
    m.uv("bone", 0, 0)
    m.uv("bone_d", 1, 0)
    m.uv("filth", 2, 0)
    m.uv("filth_d", 3, 0)
    m.uv("socket", 4, 0)
    m.bone("block", None, (0, 0, 0))
    m.cube("block", "block", (-8, 0, -8), (8, 8, 8), m.uv("filth", 2, 0), origin=(-8, 0, -8))
    m.cube("block", "bone1", (-7, 6, -7), (-3, 8, -3), m.uv("bone", 0, 0), origin=(-7, 6, -7), rot=(0, 20, 10))
    m.cube("block", "bone2", (3, 5, 3), (7, 7, 7), m.uv("bone_d", 1, 0), origin=(3, 5, 3), rot=(0, -15, -10))
    m.cube("block", "bone3", (-6, 7, 2), (-2, 9, 6), m.uv("bone", 0, 0), origin=(-6, 7, 2), rot=(20, 0, 5))
    m.cube("block", "filth1", (-5, -0.5, 5), (-1, 1, 9), m.uv("filth_d", 3, 0), origin=(-5, -0.5, 5))
    return m


def old_bones():
    """old-bones: old skeleton + fallen warrior (two poses on one blocky figure)."""
    m = Model("old_bones", 64, 64)
    m.uv("bone", 0, 0)
    m.uv("bone_d", 1, 0)
    m.uv("grave", 2, 0)
    m.uv("grave_d", 3, 0)
    m.uv("socket", 4, 0)
    m.bone("body", None, (0, 4, 0))
    m.cube("body", "torso", (-2.5, 2, -1.5), (2.5, 5.5, 1.5), m.uv("grave", 2, 0), origin=(-2.5, 2, -1.5))
    m.cube("body", "ribcage", (-2, 3.5, -1.2), (2, 5.2, 0.2), m.uv("bone", 0, 0), origin=(-2, 3.5, -1.2))
    m.bone("head", "body", (0, 5.2, -0.8), (0, 0, 0))
    m.cube("head", "head", (-1.8, 0.4, -2.0), (1.8, 2.0, 0.4), m.uv("bone", 0, 0), origin=(-1.8, 0.4, -2.0))
    m.cube("head", "leye", (-1.2, 1.2, -1.9), (-0.8, 1.6, -1.85), m.uv("socket", 4, 0))
    m.cube("head", "reye", (0.8, 1.2, -1.9), (1.2, 1.6, -1.85), m.uv("socket", 4, 0))
    m.cube("body", "arm_l", (-3.4, 3.6, -1.0), (-2.2, 5.4, 0.4), m.uv("grave_d", 3, 0), origin=(-3.4, 3.6, -1.0))
    m.cube("body", "arm_r", (2.2, 3.6, -1.0), (3.4, 5.4, 0.4), m.uv("grave_d", 3, 0), origin=(2.2, 3.6, -1.0))
    m.cube("body", "leg_l", (-1.4, 0.2, 0.6), (-0.6, 3.2, 1.6), m.uv("grave_d", 3, 0), origin=(-1.4, 0.2, 0.6))
    m.cube("body", "leg_r", (0.6, 0.2, 0.6), (1.4, 3.2, 1.6), m.uv("grave_d", 3, 0), origin=(0.6, 0.2, 0.6))
    return m

MODELS = {
    "grave_gnawer": grave_gnaver,
    "pack_leader": pack_leader,
    "bloated_dead": bloated_dead,
    "spined_bloated": spined_bloated,
    "grave_brute": grave_brute,
    "bone_burrower": bone_burrower,
    "heavy_burrower": heavy_burrower,
    "nest_block": nest_block,
    "old_bones": old_bones,
}

# animations: (name, loop, [(bone, channel, [(time, x, y, z), ...]), ...])
# time in seconds. Blockbench free format stores Java-model way; bbmodel_geckolib
# flips rotation x/y and position x for free-format projects.
ANIMATIONS = {
    "grave_gnawer": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.25, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 2, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0.2, 0), (0.5, 0, 0, 0), (0.75, 0, -0.1, 0), (1.0, 0, 0, 0)]),
            ("leg_fl", "rotation", [(0.0, 20, 0, 0), (0.5, -20, 0, 0), (1.0, 20, 0, 0)]),
            ("leg_fr", "rotation", [(0.0, -20, 0, 0), (0.5, 20, 0, 0), (1.0, -20, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0.4, 0), (0.5, 0, 0, 0), (0.75, 0, -0.2, 0), (1.0, 0, 0, 0)]),
            ("leg_fl", "rotation", [(0.0, 35, 0, 0), (0.5, -35, 0, 0), (1.0, 35, 0, 0)]),
            ("leg_fr", "rotation", [(0.0, -35, 0, 0), (0.5, 35, 0, 0), (1.0, -35, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.2, 0, -0.1, 0.5), (0.5, 0, 0, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.3, -25, 0, 0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "lunge": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0, 1.2), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.3, -40, 0, 0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
    "pack_leader": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.25, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 2, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0.2, 0), (0.5, 0, 0, 0), (0.75, 0, -0.1, 0), (1.0, 0, 0, 0)]),
            ("leg_fl", "rotation", [(0.0, 20, 0, 0), (0.5, -20, 0, 0), (1.0, 20, 0, 0)]),
            ("leg_fr", "rotation", [(0.0, -20, 0, 0), (0.5, 20, 0, 0), (1.0, -20, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0.4, 0), (0.5, 0, 0, 0), (0.75, 0, -0.2, 0), (1.0, 0, 0, 0)]),
            ("leg_fl", "rotation", [(0.0, 35, 0, 0), (0.5, -35, 0, 0), (1.0, 35, 0, 0)]),
            ("leg_fr", "rotation", [(0.0, -35, 0, 0), (0.5, 35, 0, 0), (1.0, -35, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.2, 0, -0.1, 0.5), (0.5, 0, 0, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.3, -25, 0, 0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "lunge": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0, 1.2), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.3, -40, 0, 0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
    "bloated_dead": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.6, 0, 0.2, 0), (1.2, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.6, 2, 0, 0), (1.2, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.15, 0), (0.6, 0, 0, 0), (0.9, 0, -0.1, 0), (1.2, 0, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.3, 0), (0.6, 0, 0, 0), (0.9, 0, -0.15, 0), (1.2, 0, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, -0.1, 0.6), (0.7, 0, 0, 0), (1.2, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.4, -30, 0, 0), (0.8, 0, 0, 0), (1.2, 0, 0, 0)]),
        ]),
        "swell": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.4, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 5, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
    "spined_bloated": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.6, 0, 0.2, 0), (1.2, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.6, 2, 0, 0), (1.2, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.15, 0), (0.6, 0, 0, 0), (0.9, 0, -0.1, 0), (1.2, 0, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.3, 0), (0.6, 0, 0, 0), (0.9, 0, -0.15, 0), (1.2, 0, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, -0.1, 0.6), (0.7, 0, 0, 0), (1.2, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.4, -30, 0, 0), (0.8, 0, 0, 0), (1.2, 0, 0, 0)]),
        ]),
        "swell": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.4, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 5, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
    "grave_brute": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.3, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 3, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0.25, 0), (0.5, 0, 0, 0), (0.75, 0, -0.15, 0), (1.0, 0, 0, 0)]),
            ("leg_l", "rotation", [(0.0, 25, 0, 0), (0.5, -25, 0, 0), (1.0, 25, 0, 0)]),
            ("leg_r", "rotation", [(0.0, -25, 0, 0), (0.5, 25, 0, 0), (1.0, -25, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0.45, 0), (0.5, 0, 0, 0), (0.75, 0, -0.3, 0), (1.0, 0, 0, 0)]),
            ("leg_l", "rotation", [(0.0, 40, 0, 0), (0.5, -40, 0, 0), (1.0, 40, 0, 0)]),
            ("leg_r", "rotation", [(0.0, -40, 0, 0), (0.5, 40, 0, 0), (1.0, -40, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.2, 0, -0.15, 0.8), (0.5, 0, 0, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.3, -30, 0, 0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "charge": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, 0, 2.0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.3, -45, 0, 0), (0.6, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
    "bone_burrower": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.15, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 3, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.2, 0), (0.6, 0, 0, 0), (0.9, 0, -0.1, 0), (1.2, 0, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.35, 0), (0.6, 0, 0, 0), (0.9, 0, -0.15, 0), (1.2, 0, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, -0.1, 0.8), (0.6, 0, 0, 0), (1.2, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.4, -35, 0, 0), (0.8, 0, 0, 0), (1.2, 0, 0, 0)]),
        ]),
        "burrow": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.4, 0, -0.9, 0), (1.0, 0, -0.9, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.4, 90, 0, 0), (1.0, 90, 0, 0)]),
        ]),
        "emerge": (False, [
            ("body", "position", [(0.0, 0, -0.9, 0), (0.4, 0, 0.2, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 90, 0, 0), (0.4, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
    "heavy_burrower": {
        "idle": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.5, 0, 0.2, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.5, 3, 0, 0), (1.0, 0, 0, 0)]),
        ]),
        "walk": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.2, 0), (0.6, 0, 0, 0), (0.9, 0, -0.1, 0), (1.2, 0, 0, 0)]),
        ]),
        "run": (True, [
            ("body", "position", [(0.0, 0, 0, 0), (0.3, 0, 0.35, 0), (0.6, 0, 0, 0), (0.9, 0, -0.15, 0), (1.2, 0, 0, 0)]),
        ]),
        "attack": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.25, 0, -0.1, 0.8), (0.6, 0, 0, 0), (1.2, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.4, -35, 0, 0), (0.8, 0, 0, 0), (1.2, 0, 0, 0)]),
        ]),
        "burrow": (False, [
            ("body", "position", [(0.0, 0, 0, 0), (0.4, 0, -0.9, 0), (1.0, 0, -0.9, 0)]),
            ("head", "rotation", [(0.0, 0, 0, 0), (0.4, 90, 0, 0), (1.0, 90, 0, 0)]),
        ]),
        "emerge": (False, [
            ("body", "position", [(0.0, 0, -0.9, 0), (0.4, 0, 0.2, 0), (1.0, 0, 0, 0)]),
            ("head", "rotation", [(0.0, 90, 0, 0), (0.4, 0, 0, 0), (1.0, 0, 0, 0)]),
        ]),
    },
}


def bake_animations(model, anim_defs):
    """Write Blockbench-format animation keyframes into the model."""
    bone_uuid = model.group_uuid
    for name, (loop, tracks) in anim_defs.items():
        track_len = 0.0
        for _, _, keys in tracks:
            if keys:
                track_len = max(track_len, keys[-1][0])
        length = max(track_len, 0.5)
        animators = {}
        for bone, channel, keys in tracks:
            bu = bone_uuid.get(bone)
            if bu is None:
                continue
            kfs = []
            for i, (t, x, y, z) in enumerate(keys):
                kfs.append({
                    "channel": channel,
                    "data_points": [{"x": float(x), "y": float(y), "z": float(z)}],
                    "uuid": uid("key", name, bone, channel, i),
                    "time": float(t),
                    "color": -1,
                    "interpolation": "catmullrom",
                })
            animators[bu] = {"name": bone, "type": "bone", "keyframes": kfs}
        model.animations.append({
            "uuid": uid("animation", name),
            "name": "animation." + model.name + "." + name,
            "loop": loop,
            "override": False,
            "length": round(float(length), 4),
            "snapping": 20,
            "selected": False,
            "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
            "animators": animators,
        })


def main():
    os.makedirs(GEO_DIR, exist_ok=True)
    os.makedirs(ANIM_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(ART, exist_ok=True)

    built = []
    for model_name, factory in MODELS.items():
        model = factory()
        anim_defs = ANIMATIONS.get(model_name, {})
        if anim_defs:
            bake_animations(model, anim_defs)
        bm_path = write_bbmodel(model)
        convert(model_name)
        render(model_name, os.path.join(ART, model_name + "-render.png"))
        built.append(model_name)
        print("built", model_name, "->", os.path.relpath(bm_path, ROOT))
    print("done:", ", ".join(built))


if __name__ == "__main__":
    main()
