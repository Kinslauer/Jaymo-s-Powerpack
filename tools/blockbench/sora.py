"""Turn the friend's Sora's dagger .bbmodel into the pack's four stage item models + textures.

Merges its second texture into the 64x64 atlas, replaces the one 90-degree-rotated cube (Java item models only take
22.5-degree steps) with the same box unrotated, and recolours the atlas per stage.
"""
import base64, copy, io, json, sys
from PIL import Image
sys.path.insert(0, sys.argv[1])
from display import display

SRC, REPO, OUT = sys.argv[2], sys.argv[3], sys.argv[4]
bb = json.load(open(SRC))
img = lambda t: Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA")
atlas = img(bb["textures"][2])
small = img(bb["textures"][0])  # 32px drawn at uv_width 16: uv * 2 = pixel
DX, DY = 28, 0  # free spot in the atlas
atlas.paste(small.crop((0, 8, 20, 18)), (DX, DY))

FACES = ("north", "east", "south", "west", "up", "down")
elements = []
for el in bb["elements"]:
    el = copy.deepcopy(el)
    if any(f.get("texture") == 0 for f in el["faces"].values()):
        for f in el["faces"].values():
            u0, v0, u1, v1 = f["uv"]
            f["uv"] = [DX + u0 * 2, DY + (v0 * 2 - 8), DX + u1 * 2, DY + (v1 * 2 - 8)]
            f["texture"] = 2
    rot = el.get("rotation", [0, 0, 0])
    if rot == [0, 90, 0]:
        # +90 about Y round the origin maps (x, z) -> (z, -x): the box swings to the other side of its origin
        ox, oz = el["origin"][0], el["origin"][2]
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        xs = sorted([ox + (z0 - oz), ox + (z1 - oz)])
        zs = sorted([oz - (x0 - ox), oz - (x1 - ox)])
        el["from"], el["to"] = [xs[0], y0, zs[0]], [xs[1], y1, zs[1]]
        f = el["faces"]
        el["faces"] = {"north": f["east"], "west": f["north"], "south": f["west"], "east": f["south"], "up": f["up"], "down": f["down"]}
        el["rotation"] = [0, 0, 0]
    elif any(rot):
        raise SystemExit(f"unexpected rotation {rot}")
    elements.append(el)

GOLD = {(173, 143, 34): 0, (231, 192, 49): 1, (123, 99, 26): 2}
EDGE = (177, 178, 179)
DARK = (83, 97, 116)
STAGES = {
    "soras_dagger": {"gold": [(96, 100, 108), (140, 145, 152), (64, 66, 72)]},
    "soras_dagger_gold": {},
    "soras_dagger_diamond": {"edge": (120, 236, 220)},
    "soras_dagger_tempest": {"gold": [(150, 190, 220), (210, 235, 250), (80, 110, 150)], "edge": (150, 250, 240), "dark": (40, 70, 120)},
}

def recolour(base, spec):
    im = base.copy()
    px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            c = (r, g, b)
            if c in GOLD and "gold" in spec:
                px[x, y] = spec["gold"][GOLD[c]] + (a,)
            elif c == EDGE and "edge" in spec:
                px[x, y] = spec["edge"] + (a,)
            elif c == DARK and "dark" in spec:
                px[x, y] = spec["dark"] + (a,)
    return im

def r(v):
    v = round(v, 4)
    return int(v) if float(v).is_integer() else v

tex_dir = f"{REPO}/src/main/resources/assets/jaymospowers/textures/item"
model_dir = f"{REPO}/src/main/resources/assets/jaymospowers/models/item"
# (0.17.163) the friend's own display transforms when the file has them all (his second pass set every slot, squashing
# the blade thin in the hand); otherwise ours
FRIEND = bb.get("display", {})
SLOTS = ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand", "ground", "gui", "fixed")
DISPLAY = FRIEND if all(k in FRIEND for k in SLOTS) else display(tp=0.5, fp=0.45, gui=0.55, center_y=16, grip_y=4)
for name, spec in STAGES.items():
    recolour(atlas, spec).save(f"{tex_dir}/{name}.png")
    model = {
        "credit": "Sora's dagger model by Andy's friend, made with Blockbench",
        "texture_size": [64, 64],
        "textures": {"0": f"jaymospowers:item/{name}", "particle": f"jaymospowers:item/{name}"},
        "elements": [],
        "display": DISPLAY,
    }
    for el in elements:
        faces = {}
        for k in FACES:
            if k in el["faces"]:
                faces[k] = {"uv": [r(v * 16 / 64) for v in el["faces"][k]["uv"]], "texture": "#0"}
        model["elements"].append({"from": [r(v) for v in el["from"]], "to": [r(v) for v in el["to"]], "faces": faces})
    with open(f"{model_dir}/{name}.json", "w") as fh:
        json.dump(model, fh, indent="\t")
        fh.write("\n")

# a cleaned .bbmodel (one texture, no rotated cube, our display) for Andy and his friend to keep editing
clean = copy.deepcopy(bb)
for el, new in zip(clean["elements"], elements):
    el.update({k: new[k] for k in ("from", "to", "faces")})
    el["rotation"] = [0, 0, 0]
    for f in el["faces"].values():
        f["texture"] = 0
buf = io.BytesIO(); atlas.save(buf, "PNG")
t = clean["textures"][2]
t["id"] = "0"; t["name"] = "soras_dagger_gold"; t["particle"] = True
t["source"] = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
clean["textures"] = [t]
clean["display"] = DISPLAY
json.dump(clean, open(f"{OUT}/soras_dagger.bbmodel", "w"))
print(len(elements), "elements")
