"""(0.17.166) The Costume Table's thief cut and thief's hood (costume/CostumeDesign: Cut.THIEF, Mask.THIEF_HOOD) from
eanncrod's "Thief Armor" (Blockbench Workshop, CC BY 4.0, https://blockbenchworkshop.com/model/eanncrod/thief-armor).

Its 128x128 box-UV texture is split for the costume's two dyes: the blue cloth goes to the primary texture, the metal,
leather and straps to the secondary, each turned grey (the dye tints it). The suit keeps the whole sheet; the hood
(an 8x10x8 box at 0,0) gets the 64x32 corner. Prints the cubes, in our player model's space (x and the rotations' x and
y turned about, y down from the head), for CostumeModels.THIEF_CUBES.

Run from the repo root: python3 -I tools/blockbench/thief_costume.py
"""
import base64, io, json, os, statistics
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "bbmodels", "thief-armor.bbmodel")
OUT = "src/main/resources/assets/jaymospowers/textures/entity/costume"
# each part's pivot in our (wide) player model
PIVOTS = {"body": (0, 0, 0), "head": (0, 0, 0), "right_arm": (-5, 2, 0), "left_arm": (5, 2, 0),
          "right_leg": (-1.9, 12, 0), "left_leg": (1.9, 12, 0)}
PARTS = {"bipedBody": "body", "bipedHead": "head", "bipedRightArm": "right_arm", "bipedLeftArm": "left_arm",
         "bipedRightLeg": "right_leg", "bipedLeftLeg": "left_leg"}

bb = json.load(open(SRC))
sheet = Image.open(io.BytesIO(base64.b64decode(bb["textures"][0]["source"].split(",", 1)[1]))).convert("RGBA")


def cloth(p):
    return p[2] - p[0] > 15


def grey(img, keep, target):
    """The pixels {keep} picks, turned grey with their median at {target}; the rest cleared."""
    px = [p for p in img.getdata() if p[3] > 0 and keep(p)]
    med = statistics.median(max(p[:3]) for p in px) or 1
    out = Image.new("RGBA", img.size)
    for y in range(img.height):
        for x in range(img.width):
            p = img.getpixel((x, y))
            if p[3] > 0 and keep(p):
                v = min(247, round(max(p[:3]) / med * target))
                out.putpixel((x, y), (v, v, v, p[3]))
    return out


primary = grey(sheet, cloth, 205)
secondary = grey(sheet, lambda p: not cloth(p), 190)
os.makedirs(f"{OUT}/suit", exist_ok=True)
for slim in ("", "_slim"):
    primary.save(f"{OUT}/suit/thief{slim}_primary.png")
    secondary.save(f"{OUT}/suit/thief{slim}_secondary.png")
primary.crop((0, 0, 64, 32)).save(f"{OUT}/mask/thief_hood_primary.png")
secondary.crop((0, 0, 64, 32)).save(f"{OUT}/mask/thief_hood_secondary.png")

# ---- cubes ----
groups = {g["uuid"]: g for g in bb["groups"]}
elements = {e["uuid"]: e for e in bb["elements"]}


def f(v):
    v = round(v, 4)
    return f"{v:g}F"


lines = []


def walk(node, part):
    for child in node.get("children", []):
        if isinstance(child, dict):
            g = groups[child["uuid"]]
            walk(child, PARTS.get(g["name"], part))
        else:
            e = elements[child]
            (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
            px, py, pz = PIVOTS[part]
            # our space: x turned about, y down from 24
            box = (-x1 - px, 24 - y1 - py, z0 - pz, x1 - x0, y1 - y0, z1 - z0)
            u, v = e.get("uv_offset", [0, 0])
            rot = e.get("rotation", [0, 0, 0])
            o = e.get("origin", [0, 0, 0])
            pivot = (-o[0] - px, 24 - o[1] - py, o[2] - pz)
            lines.append(f'\t\tnew ThiefCube("{part}", {u}, {v}, {", ".join(f(c) for c in box)}, {f(e.get("inflate", 0))}, '
                         f'{", ".join(f(c) for c in pivot)}, {f(-rot[0])}, {f(-rot[1])}, {f(rot[2])}, {str(bool(e.get("mirror_uv"))).lower()}),')


for root in bb["outliner"]:
    walk(root, "body")
print("\n".join(lines))
