"""The Armory's iron rapier and iron dagger, modelled in 3D with WaitMyDawn/blockbench-mcp (MIT).

Run from a clone of blockbench-mcp (PYTHONPATH=<clone>): python tools/blockbench/make.py <repo root> <preview dir>
Writes the item models and 32x32 textures into the Armory's folders and the .bbmodel files beside this script.
"""
import json, os, random, sys
from PIL import Image
from blockbench_mcp import tools as t

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from display import display

REPO, PREVIEW = sys.argv[1], sys.argv[2]
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(REPO, "src/main/resources/assets/jaymospowers")
random.seed(7)

# 32x32 atlas: each patch is a box of pixels the cube faces stretch over
P = {
    "blade":  (0, 0, 4, 32),    # flat of the blade
    "edge":   (4, 0, 6, 32),    # sharpened edge / spine
    "tip":    (6, 0, 8, 4),
    "guard":  (8, 0, 16, 8),    # darker forged iron
    "grip":   (8, 8, 12, 24),   # leather wrap
    "pommel": (16, 0, 22, 6),
    "cup":    (16, 8, 26, 18),
    "wire":   (12, 8, 14, 24),  # iron wire wrap (rapier grip)
}

def jitter(c, n=6):
    return tuple(max(0, min(255, v + random.randint(-n, n))) for v in c) + (255,)

def paint(img):
    px = img.load()
    def fill(key, fn):
        x0, y0, x1, y1 = P[key]
        for x in range(x0, x1):
            for y in range(y0, y1):
                px[x, y] = fn(x - x0, y - y0, x1 - x0, y1 - y0)
    # blade: bright centre fuller, darker toward the edges, faint vertical shine
    def blade(x, y, w, h):
        base = [(150, 156, 164), (214, 220, 226), (196, 202, 210), (138, 144, 152)][x]
        return jitter(base, 4)
    fill("blade", blade)
    fill("edge", lambda x, y, w, h: jitter((232, 236, 240) if x == 0 else (200, 205, 212), 3))
    fill("tip", lambda x, y, w, h: jitter((236, 240, 244), 3))
    def guard(x, y, w, h):
        c = (96, 100, 108)
        if y == 0 or x == 0: c = (140, 145, 152)
        if y == h - 1 or x == w - 1: c = (64, 66, 72)
        return jitter(c, 6)
    fill("guard", guard)
    def grip(x, y, w, h):  # brown leather with diagonal wraps
        c = (92, 60, 36) if (x + y) % 4 else (58, 36, 20)
        return jitter(c, 6)
    fill("grip", grip)
    def wire(x, y, w, h):
        c = (170, 174, 180) if y % 2 == 0 else (96, 100, 106)
        return jitter(c, 4)
    fill("wire", wire)
    def pommel(x, y, w, h):
        c = (120, 125, 132)
        if (x, y) in ((1, 1), (2, 1), (1, 2)): c = (210, 214, 220)
        if x == w - 1 or y == h - 1: c = (70, 72, 78)
        return jitter(c, 5)
    fill("pommel", pommel)
    def cup(x, y, w, h):
        rim = x in (0, w - 1) or y in (0, h - 1)
        c = (150, 155, 162) if rim else (108, 112, 120)
        if not rim and (x * 3 + y * 5) % 11 == 0: c = (128, 132, 140)  # hammer marks
        return jitter(c, 5)
    fill("cup", cup)

def faces(key, skip=()):
    x0, y0, x1, y1 = P[key]
    return {f: {"uv": [x0, y0, x1, y1]} for f in ("north", "south", "east", "west", "up", "down") if f not in skip}

def build(name, cubes, display):
    t.project_create(name, format="java_block", texture_width=32, texture_height=32)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    paint(img)
    tex_path = f"{ASSETS}/textures/item/armory/{name}.png"
    img.save(tex_path)
    t.texture_create(name, width=32, height=32, source_path=tex_path)
    for cname, frm, to, key in cubes:
        t.cube_create(cname, frm, to, texture=name, faces=faces(key))
    t.render_preview(f"{PREVIEW}/{name}_preview.png", width=640, height=640, yaw=35, pitch=20)
    model_path, bb_path = f"{ASSETS}/models/item/armory/{name}.json", f"{HERE}/{name}.bbmodel"
    t.export_model("java_block", model_path, modid="jaymospowers")
    t.project_save(bb_path)
    for path in (model_path, bb_path):
        with open(path) as fh:
            data = json.load(fh)
        data["display"] = display
        if path.endswith(".json"):
            data["textures"] = {"0": f"jaymospowers:item/armory/{name}", "particle": f"jaymospowers:item/armory/{name}"}
            for el in data["elements"]:
                for f in el["faces"].values():
                    f["texture"] = "#0"
        with open(path, "w", newline="\n") as fh:
            json.dump(data, fh, indent="\t" if path.endswith(".json") else None)
            fh.write("\n")
    print(name, t.validate_quality()["data"]["verdict"], t.validate_quality()["data"]["counts"])

# Model stands upright along Y. (0.17.130) Andy: the rapier's blade was too short, so its hilt sits 12 lower and the
# blade runs from just over it to the top; display() puts each grip in the fist where a vanilla sword's would be.
RAPIER = [
    ("pommel",       [7, -8, 7],      [9, -6, 9],         "pommel"),
    ("grip",         [7.5, -6, 7.5],  [8.5, -1.5, 8.5],   "wire"),
    ("quillons",     [3, -1.5, 7.5],  [13, -0.5, 8.5],    "guard"),
    ("cup",          [5.5, -1, 6],    [10.5, 0, 10],      "cup"),
    ("knuckle_bow",  [11.5, -7, 7.6], [12.5, -1.5, 8.4],  "guard"),
    ("bow_foot",     [9, -7, 7.6],    [11.5, -6, 8.4],    "guard"),
    ("ricasso",      [7.5, 0, 7.7],   [8.5, 1.5, 8.3],    "guard"),
    ("blade",        [7.6, 1.5, 7.85], [8.4, 31, 8.15],   "blade"),
    ("blade_edge",   [7.75, 1.5, 7.8], [8.25, 31, 8.2],   "edge"),
    ("tip",          [7.8, 31, 7.9],  [8.2, 32, 8.1],     "tip"),
]
DAGGER = [
    ("pommel",       [7, 3, 7],       [9, 4.5, 9],        "pommel"),
    ("grip",         [7.5, 4.5, 7.5], [8.5, 8.5, 8.5],    "grip"),
    ("crossguard",   [5, 8.5, 7.25],  [11, 9.5, 8.75],    "guard"),
    ("blade",        [7, 9.5, 7.75],  [9, 17.5, 8.25],    "blade"),
    ("blade_spine",  [7.75, 9.5, 7.6], [8.25, 17.5, 8.4], "edge"),
    ("tip_lower",    [7.5, 17.5, 7.8], [8.5, 19, 8.2],    "blade"),
    ("tip",          [7.75, 19, 7.85], [8.25, 20.5, 8.15], "tip"),
]

# the rapier is turned half round in the hand so its knuckle bow guards the fingers (Andy: it faced up)
build("iron_rapier", RAPIER, display(tp=0.55, fp=0.5, gui=0.4, center_y=12, grip_y=-3.75, flip=True))
# (0.17.166) the iron dagger is polyjo's Traveler Dagger now (tools/blockbench/traveler_dagger.py)
# build("iron_dagger", DAGGER, display(tp=0.7, fp=0.6, gui=0.62, center_y=11.75, grip_y=6.5))
