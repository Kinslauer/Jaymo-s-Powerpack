"""The art agent's tools: pixel textures, Blockbench models for GeckoLib 5, previews, and headless Blender.

Everything is written under OUT (tools/art-agent/out by default, or the ARTKIT_OUT env var):
  out/textures/<name>.png            painted textures
  out/models/<name>.bbmodel          Blockbench projects (GeckoLib "animated entity" format, box UV), open them in Blockbench
  out/assets/<mod>/...               GeckoLib files laid out like the mods' resources, ready to copy in
  out/previews/<name>.png            preview sheets (five turns of the model, drawn the way the game draws it)
  out/blender/<name>/...             whatever a Blender script saves

The model, export and preview steps reuse the repo's own converters (tools/bbmodel_geckolib.py, bbmodel_bake.py,
bbmodel_preview.py), so the agent's output goes through the same path as every other model in the pack.
"""

import base64
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import uuid

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(os.path.dirname(HERE))  # the repo's tools/ folder
OUT = os.environ.get("ARTKIT_OUT") or os.path.join(os.path.dirname(HERE), "out")
BLENDER = os.environ.get("BLENDER_BIN", "blender")
MODS = {"jaymospowers", "jaymoskingdoms", "jaymoskitchen"}
NAME = re.compile(r"^[a-z0-9_]{1,48}$")
FACES = ("north", "east", "south", "west", "up", "down")


def _path(*parts):
    p = os.path.join(OUT, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def _check_name(name):
    if not NAME.match(name or ""):
        raise ValueError(
            f"name {name!r} must be 1-48 lowercase letters, digits or underscores"
        )


def _png_part(path, scale_to=512):
    """A PNG as an image the model can look at, blown up with hard pixels so small textures stay readable."""
    from google.genai import types

    img = Image.open(path).convert("RGBA")
    k = max(1, scale_to // max(img.width, img.height))
    if k > 1:
        img = img.resize((img.width * k, img.height * k), Image.NEAREST)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return types.Part.from_bytes(data=buf.getvalue(), mime_type="image/png")


def _hex(c):
    c = c.strip().lstrip("#")
    if len(c) == 6:
        c += "ff"
    if len(c) != 8:
        raise ValueError(f"colour {c!r} must be #rrggbb or #rrggbbaa")
    return tuple(int(c[i : i + 2], 16) for i in (0, 2, 4, 6))


# ---------------------------------------------------------------- textures


def paint_texture(
    name: str, width: int, height: int, palette: dict[str, str], rows: list[str]
) -> dict:
    """Paints a pixel-art PNG texture, one character per pixel.

    Args:
        name: file name without .png (lowercase letters, digits, underscores).
        width: texture width in pixels (16, 32, 64 or 128 for Minecraft art).
        height: texture height in pixels.
        palette: maps one character to a colour, "#rrggbb" or "#rrggbbaa". "." is always fully transparent.
        rows: exactly `height` strings of exactly `width` characters each, top row first.

    Returns:
        The saved path, plus the texture itself as an image to check.
    """
    _check_name(name)
    if len(rows) != height or any(len(r) != width for r in rows):
        bad = [i for i, r in enumerate(rows) if len(r) != width]
        return {
            "error": f"need {height} rows of {width} characters; got {len(rows)} rows, wrong lengths at rows {bad[:10]}"
        }
    colours = {k: _hex(v) for k, v in palette.items()}
    colours["."] = (0, 0, 0, 0)
    missing = sorted({ch for r in rows for ch in r} - set(colours))
    if missing:
        return {"error": f"characters not in the palette: {''.join(missing)}"}
    img = Image.new("RGBA", (width, height))
    img.putdata([colours[ch] for r in rows for ch in r])
    path = _path("textures", name + ".png")
    img.save(path)
    return {"saved": path, "size": [width, height], "image": _png_part(path)}


def look_at(path: str) -> dict:
    """Shows an existing PNG (a texture, a preview, a Blender render, a reference picture) so you can judge it.

    Args:
        path: a PNG path, absolute or relative to the output folder.
    """
    p = path if os.path.isabs(path) else os.path.join(OUT, path)
    if not os.path.isfile(p):
        return {"error": f"no file at {p}"}
    return {"path": p, "image": _png_part(p)}


# ---------------------------------------------------------------- Blockbench models


def _box_uv(u, v, size, mirror):
    """Blockbench's box-UV layout of a cube's six faces as [u0, v0, u1, v1] (the up/down faces run backwards)."""
    w, h, d = size
    f = {
        "east": [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
        "up": [u + d + w, v + d, u + d, v],
        "down": [u + d + 2 * w, v, u + d + w, v + d],
    }
    if mirror:
        f["east"], f["west"] = f["west"], f["east"]
        for k in f:
            f[k] = [f[k][2], f[k][1], f[k][0], f[k][3]]
    return f


def build_model(name: str, texture: str, bones: list[dict]) -> dict:
    """Builds a Blockbench model (.bbmodel, GeckoLib animated-entity format, box UV) from bones and cubes.

    Units are Blockbench's: 16 = one block, y up, the model faces north (-z), feet at y=0. A player is 32 tall.

    Args:
        name: model name (lowercase letters, digits, underscores).
        texture: name of a texture made with paint_texture (its box-UV patches must fit every cube).
        bones: list of bones, parents before children. Each bone is a dict:
            name (unique), parent (a bone name, or omit for a root bone),
            pivot [x, y, z] (the point it turns about), rotation [x, y, z] degrees (optional),
            cubes: list of {origin [x, y, z] (the cube's lowest corner), size [w, h, d],
                            uv [u, v] (top-left of its box-UV patch: a w,h,d cube uses 2d+2w by d+h pixels),
                            inflate (optional, grows it without changing its UV), mirror (optional bool),
                            rotation [x, y, z] and pivot [x, y, z] (optional, a cube's own tilt)}.

    Returns:
        The .bbmodel path and any UV patches that run off the texture.
    """
    _check_name(name)
    tex_path = os.path.join(OUT, "textures", texture + ".png")
    if not os.path.isfile(tex_path):
        return {"error": f"no texture {texture!r}; paint it first"}
    img = Image.open(tex_path)
    tw, th = img.size
    with open(tex_path, "rb") as f:
        src = "data:image/png;base64," + base64.b64encode(f.read()).decode()
    tex_uuid = str(uuid.uuid4())
    elements, outliner, nodes, warnings = [], [], {}, []
    for b in bones:
        bn = b.get("name")
        if not bn or bn in nodes:
            return {"error": f"bone name {bn!r} is missing or used twice"}
        node = {
            "name": bn,
            "uuid": str(uuid.uuid4()),
            "origin": list(b.get("pivot", [0, 0, 0])),
            "rotation": list(b.get("rotation", [0, 0, 0])),
            "export": True,
            "children": [],
        }
        par = b.get("parent")
        if par:
            if par not in nodes:
                return {
                    "error": f"bone {bn!r} lists parent {par!r} before it exists; list parents first"
                }
            nodes[par]["children"].append(node)
        else:
            outliner.append(node)
        nodes[bn] = node
        for i, c in enumerate(b.get("cubes", [])):
            o, s = list(c["origin"]), list(c["size"])
            u, v = c.get("uv", [0, 0])
            mirror = bool(c.get("mirror"))
            faces = _box_uv(u, v, s, mirror)
            right, bottom = u + 2 * s[2] + 2 * s[0], v + s[2] + s[1]
            if right > tw or bottom > th:
                warnings.append(
                    f"{bn} cube {i}: UV patch at {u},{v} reaches {right},{bottom} but the texture is {tw}x{th}"
                )
            e = {
                "name": f"{bn}_{i}",
                "type": "cube",
                "uuid": str(uuid.uuid4()),
                "box_uv": True,
                "mirror_uv": mirror,
                "from": o,
                "to": [o[k] + s[k] for k in range(3)],
                "uv_offset": [u, v],
                "inflate": c.get("inflate", 0),
                "rotation": list(c.get("rotation", [0, 0, 0])),
                "origin": list(c.get("pivot", b.get("pivot", [0, 0, 0]))),
                "faces": {fn: {"uv": faces[fn], "texture": 0} for fn in FACES},
            }
            elements.append(e)
            node["children"].append(e["uuid"])
    data = {
        "meta": {
            "format_version": "5.0",
            "model_format": "animated_entity_model",
            "box_uv": True,
        },
        "name": name,
        "model_identifier": name,
        "resolution": {"width": tw, "height": th},
        "elements": elements,
        "outliner": outliner,
        "textures": [
            {
                "name": texture + ".png",
                "uuid": tex_uuid,
                "uv_width": tw,
                "uv_height": th,
                "source": src,
            }
        ],
        "animations": [],
    }
    old = _load(name)
    if old:
        data["animations"] = old.get(
            "animations", []
        )  # rebuilding the shape keeps its animations
    _save(name, data)
    return {
        "saved": _path("models", name + ".bbmodel"),
        "bones": len(bones),
        "cubes": len(elements),
        "uv_warnings": warnings,
    }


def _load(name):
    p = os.path.join(OUT, "models", name + ".bbmodel")
    if not os.path.isfile(p):
        return None
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def _save(name, data):
    with open(
        _path("models", name + ".bbmodel"), "w", encoding="utf-8", newline="\n"
    ) as f:
        json.dump(data, f, indent="\t")


def add_animation(
    name: str, animation: str, length: float, loop: str, keyframes: list[dict]
) -> dict:
    """Adds (or replaces) a GeckoLib animation on a model made with build_model.

    Values are added on top of the bone's resting pose, in degrees and Blockbench units, Bedrock's way round
    (positive x rotation tips the bone's top forward, toward north).

    Args:
        name: the model.
        animation: animation name, e.g. "idle", "walk", "attack".
        length: length in seconds.
        loop: "loop", "once" or "hold" (hold stays on the last frame).
        keyframes: list of {bone, channel ("rotation", "position" or "scale"), time (seconds), value [x, y, z],
                    interpolation ("linear", "catmullrom" or "step", optional)}.
    """
    data = _load(name)
    if data is None:
        return {"error": f"no model {name!r}; build it first"}
    if loop not in ("loop", "once", "hold"):
        return {"error": "loop must be loop, once or hold"}
    bones = set()

    def walk(n):
        if isinstance(n, dict):
            bones.add(n["name"])
            for ch in n.get("children", []):
                walk(ch)

    for n in data["outliner"]:
        walk(n)
    animators = {}
    for k in keyframes:
        if k.get("bone") not in bones:
            return {
                "error": f"keyframe names bone {k.get('bone')!r}, which the model doesn't have ({', '.join(sorted(bones))})"
            }
        if k.get("channel") not in ("rotation", "position", "scale"):
            return {
                "error": f"channel {k.get('channel')!r} must be rotation, position or scale"
            }
        an = animators.setdefault(
            k["bone"], {"name": k["bone"], "type": "bone", "keyframes": []}
        )
        x, y, z = [*k["value"], 0, 0, 0][:3]
        an["keyframes"].append(
            {
                "channel": k["channel"],
                "time": float(k["time"]),
                "uuid": str(uuid.uuid4()),
                "data_points": [{"x": x, "y": y, "z": z}],
                "interpolation": k.get("interpolation", "linear"),
            }
        )
    full = f"animation.{name}.{animation}"
    anims = [a for a in data.get("animations", []) if a["name"] != full]
    anims.append(
        {
            "uuid": str(uuid.uuid4()),
            "name": full,
            "loop": loop,
            "length": float(length),
            "animators": {str(uuid.uuid4()): a for a in animators.values()},
        }
    )
    data["animations"] = anims
    _save(name, data)
    return {
        "saved": _path("models", name + ".bbmodel"),
        "animations": [a["name"].split(".")[-1] for a in anims],
    }


def _run(cmd, timeout=180):
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return r.returncode, (r.stdout + r.stderr)[-3000:]


def export_geckolib(name: str, mod_id: str) -> dict:
    """Writes a model's GeckoLib 5 files (geo.json, animation.json, texture) the way Blockbench's GeckoLib export does.

    Args:
        name: the model.
        mod_id: which mod's assets folder to lay them out for: jaymospowers, jaymoskingdoms or jaymoskitchen.
    """
    if mod_id not in MODS:
        return {"error": f"mod_id must be one of {sorted(MODS)}"}
    src = os.path.join(OUT, "models", name + ".bbmodel")
    if not os.path.isfile(src):
        return {"error": f"no model {name!r}"}
    assets = os.path.join(OUT, "assets", mod_id)
    code, log = _run(
        [
            sys.executable,
            "-I",
            os.path.join(TOOLS, "bbmodel_geckolib.py"),
            src,
            assets,
            name,
        ]
    )
    if code:
        return {"error": log}
    return {
        "log": log.strip(),
        "files": [
            os.path.join(assets, "geckolib", "models", "entity", name + ".geo.json"),
            os.path.join(
                assets, "geckolib", "animations", "entity", name + ".animation.json"
            ),
            os.path.join(assets, "textures", "entity", name + ".png"),
        ],
    }


def preview_model(name: str, animation: str = "", time: float = 0.0) -> dict:
    """Draws a model the way the game will (five turns side by side) and shows you the picture.

    Back faces are culled, so a hole means a face is wound the wrong way or a cube is inside out.

    Args:
        name: the model.
        animation: an animation to pose it in, or "" for the resting pose.
        time: seconds into that animation.
    """
    src = os.path.join(OUT, "models", name + ".bbmodel")
    if not os.path.isfile(src):
        return {"error": f"no model {name!r}"}
    with tempfile.TemporaryDirectory() as tmp:
        code, log = _run(
            [
                sys.executable,
                "-I",
                os.path.join(TOOLS, "bbmodel_bake.py"),
                src,
                tmp,
                name,
            ]
        )
        if code:
            return {"error": log}
        out = _path("previews", f"{name}{'_' + animation if animation else ''}.png")
        code, log2 = _run(
            [
                sys.executable,
                "-I",
                os.path.join(TOOLS, "bbmodel_preview.py"),
                tmp,
                name,
                out,
                animation or "-",
                str(time),
            ]
        )
        if code:
            return {"error": log2}
    return {"saved": out, "notes": log2.strip(), "image": _png_part(out, 1800)}


# ---------------------------------------------------------------- Blender


def run_blender(name: str, script: str) -> dict:
    """Runs a Blender Python (bpy) script headless, for modelling, sculpting, baking or rendering in Blender.

    The script runs in Blender's background mode with a fresh empty scene. The variable OUT_DIR (a str) is set to
    this job's folder: save .blend, .glb, .obj and .png files there. A render saved as OUT_DIR + "/render.png" is
    shown back to you. Use the EEVEE or Workbench engine for quick renders (no GPU here).

    Args:
        name: job name (lowercase letters, digits, underscores); its folder is out/blender/<name>.
        script: the bpy script.
    """
    _check_name(name)
    job = os.path.join(OUT, "blender", name)
    os.makedirs(job, exist_ok=True)
    path = os.path.join(job, "script.py")
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"OUT_DIR = {job!r}\n" + script)
    try:
        code, log = _run(
            [
                BLENDER,
                "-b",
                "--factory-startup",
                "--python-exit-code",
                "1",
                "--python",
                path,
            ],
            timeout=600,
        )
    except FileNotFoundError:
        return {"error": f"Blender isn't installed here (BLENDER_BIN={BLENDER!r})"}
    except subprocess.TimeoutExpired:
        return {"error": "Blender ran past 10 minutes and was stopped"}
    res = {"exit_code": code, "log": log, "files": sorted(os.listdir(job))}
    render = os.path.join(job, "render.png")
    if os.path.isfile(render):
        res["image"] = _png_part(render, 1024)
    return res
