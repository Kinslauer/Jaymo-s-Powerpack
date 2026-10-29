"""(Kingdoms 0.7.346) The elemental spell tomes' model and textures, from Andy's Blockbench model
tools/bbmodels/weapon-starter-tome.bbmodel (java_block, 5 cubes, one 64x64 texture, its own display transforms).

One model per element (`item/<element>_tome`: the five cubes and the display from the .bbmodel as drawn) with a
recoloured texture each: the cover and the gem tinted per element, the pages, the clasp's iron and the gem's glints kept
as drawn. Each element's item definition (`items/<element>_tome.json`) points at its model.

Run from the repo root: python3 -I tools/blockbench/elemental_tomes.py
"""
import base64, io, json, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "bbmodels", "weapon-starter-tome.bbmodel")
ASSETS = os.path.join(HERE, "..", "..", "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms")
MOD = "jaymoskingdoms"

# element: (cover colour, gem colour), the tint of the mid-tones: shading comes from the original's own shades
ELEMENTS = {
    "water": ((30, 98, 130), (90, 230, 230)),
    "fire": ((176, 66, 24), (255, 206, 52)),
    "earth": ((80, 106, 56), (222, 166, 60)),
    "air": ((150, 178, 198), (232, 255, 244)),
    "lightning": ((74, 64, 130), (255, 236, 80)),
    "ice": ((150, 205, 232), (70, 120, 230)),
    "restoration": ((214, 176, 70), (110, 240, 120)),
    "death": ((52, 40, 70), (176, 112, 255)),
    "blood": ((120, 16, 24), (255, 60, 70)),
}
COVER_REF = (140, 90, 67)  # the original cover's mid brown
GEM_REF = (83, 219, 73)  # the original gem's mid green


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def kind(p):
    r, g, b, a = p
    if a == 0:
        return None
    if g > r + 20 and g > b + 20:
        return "gem"
    if r >= 200 or (abs(r - g) < 14 and abs(g - b) < 14):
        return None  # pages, glints
    if r > g > b and r - b > 30:
        return "cover"
    return None  # the clasp's iron (b > g > r)


def tint(p, target, ref):
    k = lum(p) / lum(ref)
    return tuple(max(0, min(255, round(t * k))) for t in target) + (p[3],)


def recolour(src, cover, gem):
    out = src.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            p = px[x, y]
            what = kind(p)
            if what == "cover":
                px[x, y] = tint(p, cover, COVER_REF)
            elif what == "gem":
                px[x, y] = tint(p, gem, GEM_REF)
    return out


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as fh:
        json.dump(data, fh, indent="\t")
        fh.write("\n")


bb = json.load(open(SRC))
png = base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1])
base = Image.open(io.BytesIO(png)).convert("RGBA")

S = 16 / bb["resolution"]["width"]  # the model's UVs are in 64ths of the texture's width
elements = []
for el in bb["elements"]:
    faces = {}
    for k, face in el["faces"].items():
        u0, v0, u1, v1 = face["uv"]
        if face.get("texture") is None or u0 == u1 or v0 == v1:
            continue
        faces[k] = {"uv": [round(v * S, 4) for v in face["uv"]], "texture": "#0"}
    if not faces:
        continue
    out = {"from": el["from"], "to": el["to"], "faces": faces}
    rot = el.get("rotation", [0, 0, 0])
    for axis, angle in zip("xyz", rot):
        if angle:
            out["rotation"] = {"angle": angle, "axis": axis, "origin": el["origin"]}
    elements.append(out)

# Display: Andy's own transforms sat the book on its corner in the slot and were too big in the first-person hand, so the
# book is placed here: each context turns it (rotation), scales it and puts the middle of the book (the cubes' centre) at
# an anchor, the way display.py puts a blade's grip in the fist. The cover faces east/west in the model, the spine south,
# the pages' edge north; the top is up.
import sys
sys.path.insert(0, HERE)
from display import _rotate

lo = [min(e["from"][i] for e in elements) for i in range(3)]
hi = [max(e["to"][i] for e in elements) for i in range(3)]
CENTRE = [(lo[i] + hi[i]) / 2 - 8 for i in range(3)]


def place(rot, scale, anchor):
    g = _rotate(*rot, tuple(c * scale for c in CENTRE))
    return {"rotation": rot, "translation": [round(anchor[i] - g[i], 2) for i in range(3)], "scale": [scale] * 3}


def mirror(d):
    r = d["rotation"]
    return {"rotation": [r[0], -r[1], -r[2]], "translation": d["translation"], "scale": d["scale"]}


TP = place([90, 0, 0], 0.8, (0.0, -1.0, 1.0))
FP = place([10, -70, 0], 0.55, (-0.5, 2.5, -1.0))
display = {
    "thirdperson_righthand": TP, "thirdperson_lefthand": mirror(TP),
    "firstperson_righthand": FP, "firstperson_lefthand": mirror(FP),
    "gui": place([20, -50, 0], 1.25, (0.0, 0.0, 0.0)),
    "ground": place([0, -90, 0], 0.7, (0.0, 3.0, 0.0)),
    "fixed": place([0, -90, 0], 1.7, (0.0, 0.0, 0.0)),
}

for name, (cover, gem) in ELEMENTS.items():
    tex = f"{MOD}:item/{name}_tome"
    os.makedirs(f"{ASSETS}/textures/item", exist_ok=True)
    recolour(base, cover, gem).save(f"{ASSETS}/textures/item/{name}_tome.png")
    write_json(f"{ASSETS}/models/item/{name}_tome.json", {
        "credit": "Weapon - Starter Tome, Andy's Blockbench model",
        "textures": {"0": tex, "particle": tex},
        "elements": elements,
        "display": display,
    })
    write_json(f"{ASSETS}/items/{name}_tome.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{name}_tome"}})
print(len(elements), "cubes,", len(ELEMENTS), "elements")
