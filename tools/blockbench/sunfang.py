"""(Andy, 2026-10-29: "the sword can replace any of the free relic arsenal relic weapons") The Sunfang relic
(power/arsenal/RelicWeapons, items/sunfang.json -> models/item/sunfang), until now a flat sprite, drawn as the "Ancient
Blasting Sword" from the Blockbench Workshop (CC BY 4.0; tools/bbmodels/ancient-blasting-sword.bbmodel): a long dark blade
with a bright energy sheet running beside it. It is the only sword among the relics still without a model (the Dreaded
Blade, Voidreaver, Echo Cleaver and Tidebreaker have theirs; the Grand Shield is a shield), and its right click, a blast of
scouring sand, and its desert and jungle temples suit an "ancient blasting" sword. The weapon keeps its name, Sunfang;
the model's own name and its textures' names are not used.

The file: 17 cubes (16 of the sword, one tall plane for the energy sheet), two animated 32-wide strips of 32x32 frames: the
blade's (19 frames) and the sheet's (27), each its own texture with its own .mcmeta, seven frames a second (three ticks a
frame). The sheet is drawn at full light. The cubes' turns (all about x, 52.5 and so on) are kept exactly (26.3 reads an
element's rotation as free x/y/z degrees: freeform.py). The model is turned a quarter about y so it lies like a sprite
(the sheet facing front, the tip leaning right), and our display() puts its grip in the fist like the other 3D relics.

Run from the repo root: python3 -I tools/blockbench/sunfang.py
"""
import base64, io, json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from bake import apply, chain_matrix, flatten, mul, transform_point, write_json
from display import display
from freeform import euler

SRC = os.path.join(HERE, "..", "bbmodels", "ancient-blasting-sword.bbmodel")
ASSETS = "src/main/resources/assets/jaymospowers"
# the file's texture list: 0 the energy sheet, 1 the blade
NAMES = ["sunfang_fx", "sunfang_blade"]
# the file says seven frames a second: three ticks (of twenty a second) a frame
FRAMETIME = 3
# the energy sheet's texture, drawn at full light
SHEET = 0
# The file stands the sword upright with its sheet facing sideways (x) and its tip leaning toward -z. The model is turned
# a quarter about y (x' = -z, z' = x) so the sheet faces front and the tip leans toward +x like every other sprite-style
# blade, which is what display() holds in the fist; the sword's axis (x 7.75, z 7.6) goes to the middle of the model box.
FRAME = ((0, 0, -1), (0, 1, 0), (1, 0, 0))
PIVOT, CENTRE = (7.75, 0, 7.6), (8, 0, 8)

bb = json.load(open(SRC))
for name, tex in zip(NAMES, bb["textures"]):
    img = Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")
    assert img.height % img.width == 0, (name, img.size)
    img.save(f"{ASSETS}/textures/item/{name}.png")
    with open(f"{ASSETS}/textures/item/{name}.png.mcmeta", "w", newline="\n") as fh:
        json.dump({"animation": {"frametime": FRAMETIME}}, fh, indent="\t")
        fh.write("\n")

# a frame is the UV space (32x32), and a Java model counts its UVs in sixteenths of a frame
SU, SV = 16 / bb["resolution"]["width"], 16 / bb["resolution"]["height"]
elements = []
for el, chain in flatten(bb):
    faces = {}
    for name, face in el["faces"].items():
        if face.get("texture") is None:
            continue
        u0, v0, u1, v1 = face["uv"]
        if u0 == u1 or v0 == v1:
            continue
        entry = {"uv": [round(u0 * SU, 4), round(v0 * SV, 4), round(u1 * SU, 4), round(v1 * SV, 4)], "texture": f"#{int(face['texture'])}"}
        if face.get("rotation"):
            entry["rotation"] = face["rotation"]
        faces[name] = entry
    if not faces:
        continue
    g = el.get("inflate", 0) or 0
    anchor = el.get("origin", [0, 0, 0])
    world = transform_point(chain, anchor)
    rel = apply(FRAME, [world[i] - PIVOT[i] for i in range(3)])
    origin = [CENTRE[i] + rel[i] for i in range(3)]
    lo = [origin[i] + el["from"][i] - g - anchor[i] for i in range(3)]
    hi = [origin[i] + el["to"][i] + g - anchor[i] for i in range(3)]
    out = {"from": [round(v, 4) for v in lo], "to": [round(v, 4) for v in hi], "faces": faces}
    x, y, z = euler(mul(FRAME, chain_matrix(chain)))
    if x or y or z:
        out["rotation"] = {"origin": [round(v, 4) for v in origin], "x": x, "y": y, "z": z}
    if any(f["texture"] == f"#{SHEET}" for f in faces.values()):
        out["light_emission"] = 15
    for v in out["from"] + out["to"]:
        assert -16 <= v <= 32, out
    elements.append(out)

model = {
    "credit": "Ancient Blasting Sword, Blockbench Workshop (CC BY 4.0)",
    "texture_size": [16, 16],
    "textures": {"0": f"jaymospowers:item/{NAMES[0]}", "1": f"jaymospowers:item/{NAMES[1]}", "particle": f"jaymospowers:item/{NAMES[1]}"},
    "elements": elements,
    # the grip (the handle, y 1 to 5) in the fist, the blade's length (y -1.75 to 28) centred on 13
    "display": display(tp=0.75, fp=0.6, gui=0.5, center_y=13, grip_y=3),
}
write_json(f"{ASSETS}/models/item/sunfang.json", model)
print(len(elements), "elements")
