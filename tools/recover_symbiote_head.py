"""Recovers the head of the Venom form from Andy's own Symbiote 1.20.1 jar (his mod, made with Claude/GPT; Andy confirmed
the ownership on 2026-11-24 and authorised reusing it here, whatever the jar's All Rights Reserved metadata says).

The jar has no .bbmodel or GeckoLib JSON: its model is built in code (com/symbiote/client/model/FormGeometry.venom()).
This tool reads that method's bytecode with `javap` and replays the builder calls (CubeListBuilder.texOffs/addBox/mirror,
PartPose.offset/offsetAndRotation, PartDefinition.addOrReplaceChild), the way a decompiler would, so the numbers are the
original's and nothing is retyped. It keeps only the head and the jaw, with their fang parts and the four tongue segments
(no body), and writes:

    tools/data/bighead_geometry.json   the parts, cubes, UVs, and the rest-pose anchors (mouth, eyes) of the head
    tools/data/bighead_source.png      the jar's venom.png, kept only inside the UV rectangles of those cubes (the body
    tools/data/bighead_source_glow.png and the rest are not imported); likewise venom_glow.png

Run once, when the jar changes (it isn't needed afterwards, tools/gen_web_bighead.py reads the files above):
    python3 -I tools/recover_symbiote_head.py "<path to>/symbiote-1.20.1-1.0.1.jar"
Needs `javap` (any JDK) on the PATH and Pillow.
"""
import json
import math
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

from PIL import Image

DATA = Path(__file__).resolve().parent / "data"
FORM = "com/symbiote/client/model/FormGeometry"
PARTS_KEPT_ROOTS = ("head", "jaw")


def javap(jar, entry, workdir):
    with zipfile.ZipFile(jar) as z:
        z.extract(entry + ".class", workdir)
    out = subprocess.run(["javap", "-p", "-c", "-constants", str(Path(workdir) / (entry + ".class"))],
                         check=True, capture_output=True, text=True).stdout
    return out.split("\n")


def instructions(lines):
    for line in lines:
        m = re.match(r"\s+\d+: (\w+)\s*(.*)", line)
        if m:
            yield m.groups()


def method(lines, signature):
    start = next(i for i, line in enumerate(lines) if signature in line)
    end = start + 1
    while end < len(lines) and not re.match(r"  (public|static|private)", lines[end]):
        end += 1
    return lines[start:end]


def constant(rest):
    kind, value = re.search(r"// (float|String|int) (.*)", rest).groups()
    return float(value.rstrip("f")) if kind == "float" else (int(value) if kind == "int" else value)


def replay_venom(lines):
    """Replays FormGeometry.venom(): every addOrReplaceChild call, in order, with the parent it was called on."""
    stack, local, parts = [], {}, []
    for op, rest in instructions(method(lines, "LayerDefinition venom();")):
        if op in ("ldc", "ldc_w"):
            stack.append(constant(rest))
        elif op.startswith("fconst_") or op.startswith("iconst_"):
            stack.append(float(op[-1]) if op.startswith("f") else int(op[-1]))
        elif op in ("bipush", "sipush"):
            stack.append(int(rest))
        elif op in ("new", "getstatic"):
            stack.append((op,))
        elif op == "dup":
            stack.append(stack[-1])
        elif op == "pop":
            stack.pop()
        elif op.startswith("astore"):
            local[int(rest) if op == "astore" else int(op[-1])] = stack.pop()
        elif op.startswith("aload"):
            stack.append(local[int(rest) if op == "aload" else int(op[-1])])
        elif op.startswith("invoke"):
            target = re.search(r"// (?:Interface)?Method ([^ ]+)", rest).group(1)
            name, descriptor = target.split(":", 1)
            arguments, _ = re.match(r"\((.*?)\)(.*)", descriptor).groups()
            args = [stack.pop() for _ in range(len(re.findall(r"[FZI]|L[^;]*;", arguments)))][::-1]
            receiver = stack.pop() if op != "invokestatic" else None
            if 'CubeDeformation."<init>"' in name:
                stack[-1] = ("deform", args[0])
            elif name.endswith("CubeListBuilder.m_171558_"):
                stack.append({"cubes": [], "uv": None, "mirror": False})
            elif name.endswith("m_171514_"):
                receiver["uv"] = args
                stack.append(receiver)
            elif name.endswith("m_171480_"):
                receiver["mirror"] = True
                stack.append(receiver)
            elif name.endswith("m_171555_"):
                receiver["mirror"] = bool(args[0])
                stack.append(receiver)
            elif name.endswith("m_171488_"):
                receiver["cubes"].append({"uv": receiver["uv"], "mirror": receiver["mirror"], "box": args[:6], "grow": args[6][1]})
                stack.append(receiver)
            elif name.endswith("PartPose.m_171419_"):
                stack.append(("pose", args[:3], [0.0, 0.0, 0.0]))
            elif name.endswith("PartPose.m_171423_"):
                stack.append(("pose", args[:3], args[3:]))
            elif name.endswith("MeshDefinition.m_171576_"):
                stack.append({"name": None})
            elif name.endswith("PartDefinition.m_171599_"):
                pose = args[2] if args[2][0] == "pose" else ("pose", [0.0, 0.0, 0.0], [0.0, 0.0, 0.0])
                part = {"name": args[0], "parent": receiver["name"], "pos": pose[1], "rot": pose[2], "cubes": args[1]["cubes"]}
                parts.append(part)
                stack.append(part)
            elif "MeshDefinition" in name or "LayerDefinition" in name:
                pass
            else:
                raise SystemExit("unhandled call: " + name)
    return parts


def anchors(lines):
    """The venom Info's anchors (name, head-space x y z): the mouth and the eyes."""
    seq = list(instructions(method(lines, "static {}")))
    start = next(i for i, (op, rest) in enumerate(seq) if op in ("ldc", "ldc_w") and rest.endswith("String venom"))
    end = next(i for i, (op, rest) in enumerate(seq) if i > start and op in ("ldc", "ldc_w") and rest.endswith("String carnage"))
    stack, arrays = [], []
    for op, rest in seq[start:end]:
        if op in ("ldc", "ldc_w"):
            stack.append(constant(rest))
        elif op in ("bipush", "sipush"):
            stack.append(int(rest))
        elif op.startswith("iconst_") or op.startswith("fconst_"):
            stack.append(float(op[-1]) if op.startswith("f") else int(op[-1]))
        elif op in ("anewarray", "newarray"):
            stack.pop()
            arrays.append([])
        elif op in ("aastore", "fastore"):
            value = stack.pop()
            stack.pop()
            arrays[-1].append(value)
    names, floats = arrays[3], arrays[4]
    result = {}
    for i in range(0, len(names), 2):
        result[names[i]] = {"part": names[i + 1], "at": floats[i // 2 * 3:i // 2 * 3 + 3]}
    return {k: v for k, v in result.items() if k in ("mouth", "eyes")}


def uv_rects(parts):
    """Each cube's texture rectangle (the box-unwrap of Minecraft: 2*(w+d) wide, d+h tall)."""
    rects = []
    for part in parts:
        for cube in part["cubes"]:
            u, v = cube["uv"]
            w, h, d = (math.ceil(n) for n in cube["box"][3:6])
            rects.append((int(u), int(v), int(u) + 2 * (d + w), int(v) + d + h))
    return rects


def main(jar):
    with tempfile.TemporaryDirectory() as work:
        form = javap(jar, FORM, work)
    parts = replay_venom(form)
    assert len(parts) == 78, len(parts)
    assert sum(len(p["cubes"]) for p in parts) == 214
    by_name = {p["name"]: p for p in parts}
    kept = []
    for p in parts:
        root = p["name"]
        while by_name[root]["parent"] in by_name and root not in PARTS_KEPT_ROOTS:
            root = by_name[root]["parent"]
        if root in PARTS_KEPT_ROOTS:
            kept.append(p)
    kept_names = {p["name"] for p in kept}
    assert len(kept) == 30 and sum(len(p["cubes"]) for p in kept) == 80, (len(kept), sum(len(p["cubes"]) for p in kept))
    for p in kept:
        assert p["parent"] in kept_names or p["name"] == "head", p["name"]
    jaw_parent = by_name["jaw"]["parent"]
    assert jaw_parent == "head", jaw_parent
    data = {"texture": [256, 192], "parts": [{"name": p["name"], "parent": p["parent"] if p["name"] != "head" else None, "pos": p["pos"],
                                              "rot": p["rot"], "cubes": p["cubes"]} for p in kept],
            "anchors": anchors(form), "headPivotInTorso": by_name["head"]["pos"]}
    DATA.mkdir(exist_ok=True)
    (DATA / "bighead_geometry.json").write_text(json.dumps(data, indent=1), encoding="utf-8")
    rects = uv_rects(kept)
    with zipfile.ZipFile(jar) as z:
        for src, dst in (("venom.png", "bighead_source.png"), ("venom_glow.png", "bighead_source_glow.png")):
            with z.open("assets/symbiote/textures/entity/form/" + src) as f:
                image = Image.open(f).convert("RGBA")
            scale = image.width // 256
            keep = Image.new("RGBA", image.size, (0, 0, 0, 0))
            for x0, y0, x1, y1 in rects:
                box = (x0 * scale, y0 * scale, x1 * scale, y1 * scale)
                keep.paste(image.crop(box), box[:2])
            keep.save(DATA / dst, optimize=True)
    print("kept", len(kept), "parts,", sum(len(p["cubes"]) for p in kept), "cubes,", len(rects), "UV rects")


if __name__ == "__main__":
    main(sys.argv[1])
