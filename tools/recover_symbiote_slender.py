"""Recovers the red form's slender body from Andy's own Symbiote 1.20.1 jar (his mod, made with Claude/GPT; Andy confirmed
the ownership on 2026-11-24 and authorised reusing it here, whatever the jar's All Rights Reserved metadata says).

The same bytecode replay as tools/recover_symbiote_head.py (whose helpers it uses), run on the jar's other form, the red
one (its FormGeometry method and texture still carry the jar's own name for it; nothing of ours does). It keeps the whole
body, head, jaw and tongue included, but not the original's six back tendrils nor its two arm blades: our own tendrils
(WebTendrilLayer) and weapon arm (WebMorphLayer) are drawn on it instead. Writes:

    tools/data/slender_geometry.json   the parts, cubes and UVs (256x256 UV space), and the mouth and eyes anchors
    tools/data/slender_source.png      the jar's texture (4x: 1024x1024), kept only inside the UV rectangles of those cubes
    tools/data/slender_source_glow.png likewise its glow texture

Run once, when the jar changes (tools/gen_web_slender.py reads only the files above):
    python3 -I tools/recover_symbiote_slender.py "<path to>/symbiote-1.20.1-1.0.1.jar"
Needs `javap` (any JDK) on the PATH and Pillow.
"""
import json
import re
import sys
import tempfile
import zipfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recover_symbiote_head as head  # noqa: E402  (javap, the replay, uv_rects)

DATA = head.DATA
# the jar's own names for the red form (its geometry method and its texture files)
JAR_METHOD = "LayerDefinition carnage();"
JAR_TEXTURE = "assets/symbiote/textures/entity/form/carnage%s.png"
# the original's own weapons, left out: our tendrils and weapon arm are drawn instead
DROPPED_ROOTS = ("tendril0_root", "tendril1_root", "tendril2_root", "tendril3_root", "tendril4_root", "tendril5_root",
                 "right_blade", "left_blade")
UV = 256


def form_lines(lines):
    """The red form's method, renamed so the head tool's replay (which looks for the black form's method) reads it."""
    body = head.method(lines, JAR_METHOD)
    return ["  public static LayerDefinition venom();"] + body[1:]


def anchors(lines):
    """The red form's Info anchors (the mouth and the eyes), from the static initializer, between its name and the next."""
    seq = list(head.instructions(head.method(lines, "static {}")))
    start = next(i for i, (op, rest) in enumerate(seq) if op in ("ldc", "ldc_w") and rest.endswith("String carnage"))
    end = next((i for i, (op, rest) in enumerate(seq) if i > start and op in ("ldc", "ldc_w") and re.search(r"String spiderman$", rest)), len(seq))
    stack, arrays = [], []
    for op, rest in seq[start:end]:
        if op in ("ldc", "ldc_w"):
            stack.append(head.constant(rest))
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
    names = next((a for a in arrays if a and isinstance(a[0], str) and "mouth" in a), None)
    floats = next((a for a in arrays[arrays.index(names) + 1:] if a and all(isinstance(v, float) for v in a)), None) if names else None
    if not names or not floats:
        return {}
    result = {}
    for i in range(0, len(names), 2):
        result[names[i]] = {"part": names[i + 1], "at": floats[i // 2 * 3:i // 2 * 3 + 3]}
    return {k: v for k, v in result.items() if k in ("mouth", "eyes")}


def main(jar):
    with tempfile.TemporaryDirectory() as work:
        form = head.javap(jar, head.FORM, work)
    parts = head.replay_venom(form_lines(form))
    by_name = {p["name"]: p for p in parts}

    def dropped(p):
        while p:
            if p["name"] in DROPPED_ROOTS:
                return True
            p = by_name.get(p["parent"])
        return False

    kept = [p for p in parts if not dropped(p)]
    names = {p["name"] for p in kept}
    for p in kept:
        assert p["parent"] in names or p["parent"] is None or p["parent"] not in by_name, p["name"]
    data = {"texture": [UV, UV],
            "parts": [{"name": p["name"], "parent": p["parent"] if p["parent"] in names else None, "pos": p["pos"], "rot": p["rot"],
                       "cubes": p["cubes"]} for p in kept],
            "anchors": anchors(form)}
    DATA.mkdir(exist_ok=True)
    (DATA / "slender_geometry.json").write_text(json.dumps(data, indent=1), encoding="utf-8")
    rects = head.uv_rects(kept)
    with zipfile.ZipFile(jar) as z:
        for suffix, dst in (("", "slender_source.png"), ("_glow", "slender_source_glow.png")):
            with z.open(JAR_TEXTURE % suffix) as f:
                image = Image.open(f).convert("RGBA")
            scale = image.width // UV
            keep = Image.new("RGBA", image.size, (0, 0, 0, 0))
            for x0, y0, x1, y1 in rects:
                box = (x0 * scale, y0 * scale, x1 * scale, y1 * scale)
                keep.paste(image.crop(box), box[:2])
            keep.save(DATA / dst, optimize=True)
    print("kept", len(kept), "of", len(parts), "parts,", sum(len(p["cubes"]) for p in kept), "cubes,", len(rects), "UV rects;",
          "anchors:", data["anchors"])


if __name__ == "__main__":
    main(sys.argv[1])
