"""The chain axe's and the lasso's 3D models, as Blockbench projects and as the item models the game reads.

    python3 -I tools/gen_chain_weapons.py [preview.png]

Both are drawn the way the flail and the whip are: only the part in the fist is the held item (the axe's haft, the
lasso's coil); the chain and the rope are physics lines drawn by the game (client/armory/LashRenderer in Powers,
client/forms/LassoRope in Kingdoms), and the axe head and the loop are models put at the line's end.

Writes, for Powers: art/chain_axe.bbmodel (the whole axe: haft, chain, head), and the item models chain_axe_handle
(in the hand), chain_axe_swung (the head on the chain's end, hanging from the top middle of its box) and chain_axe_whole
(dropped, framed, on a stand). For Kingdoms: kingdoms/art/lasso.bbmodel, and lasso_coil (in the hand), lasso_loop (the
loop in flight and hanging) and lasso_whole. Inventory icons stay the painted sprites. Every box and pixel is our own
(docs/Chain-Weapons-Model-Spec.md).
"""
import base64
import io
import json
import math
import os
import random
import sys
import uuid

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "blockbench"))
from display import display

FACES = ("north", "south", "east", "west", "up", "down")
random.seed(11)


def jitter(c, n=5):
    return tuple(max(0, min(255, v + random.randint(-n, n))) for v in c) + (255,)


def paint(patches, painters):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for key, (x0, y0, x1, y1) in patches.items():
        for x in range(x0, x1):
            for y in range(y0, y1):
                img.putpixel((x, y), painters[key](x - x0, y - y0, x1 - x0, y1 - y0))
    return img


def element(name, frm, to, patch, patches, rotation=None):
    x0, y0, x1, y1 = patches[patch]
    el = {"name": name, "from": frm, "to": to, "faces": {f: {"uv": [x0 / 2, y0 / 2, x1 / 2, y1 / 2], "texture": "#0"} for f in FACES}}
    if rotation:
        el["rotation"] = rotation
    return el


def shifted(elements, dx, dy, prefix=""):
    out = []
    for el in elements:
        el = json.loads(json.dumps(el))
        el["name"] = prefix + el["name"]
        for k in ("from", "to"):
            el[k] = [el[k][0] + dx, el[k][1] + dy, el[k][2]]
        if "rotation" in el:
            o = el["rotation"]["origin"]
            el["rotation"]["origin"] = [o[0] + dx, o[1] + dy, o[2]]
        out.append(el)
    return out


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def write_model(assets, namespace, name, texture, elements, shown):
    write_json(os.path.join(assets, "models", "item", name + ".json"), {
        "textures": {"0": namespace + ":item/" + texture, "particle": namespace + ":item/" + texture},
        "elements": elements, "display": shown})


def write_bbmodel(path, name, img, elements, shown):
    """A Blockbench project (Java block/item format) of the same boxes, its texture inside it."""
    buf = io.BytesIO()
    img.save(buf, "PNG")
    cubes = []
    for i, el in enumerate(elements):
        cube = {"uuid": str(uuid.UUID(int=random.getrandbits(128))), "name": el["name"], "type": "cube", "box_uv": False,
                "from": el["from"], "to": el["to"], "autouv": 0, "color": i % 8,
                "origin": el.get("rotation", {}).get("origin", [(a + b) / 2 for a, b in zip(el["from"], el["to"])]),
                # Blockbench keeps face UVs in texture pixels (32), the game in sixteenths
                "faces": {f: {"uv": [v * 2 for v in face["uv"]], "texture": 0} for f, face in el["faces"].items()}}
        if "rotation" in el:
            turn = [0, 0, 0]
            turn["xyz".index(el["rotation"]["axis"])] = el["rotation"]["angle"]
            cube["rotation"] = turn
        cubes.append(cube)
    write_json(path, {
        "meta": {"format_version": "5.0", "model_format": "java_block", "box_uv": False}, "name": name,
        "resolution": {"width": 32, "height": 32}, "elements": cubes, "outliner": [c["uuid"] for c in cubes],
        "textures": [{"uuid": str(uuid.UUID(int=random.getrandbits(128))), "name": name, "id": "0", "width": 32, "height": 32,
                      "uv_width": 32, "uv_height": 32, "particle": True, "visible": True, "internal": True, "saved": False,
                      "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()}],
        "display": shown})


def item_definition(assets, namespace, name, hand, whole, icon):
    """In the fist: the bare handle. In a slot: the painted icon. Anywhere else (dropped, framed): the whole thing."""
    model = lambda m: {"type": "minecraft:model", "model": namespace + ":item/" + m}
    write_json(os.path.join(assets, "items", name + ".json"), {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": ["firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"], "model": model(hand)},
                  {"when": "gui", "model": model(icon)}],
        "fallback": model(whole)}})


# ---- the chain axe (Powers) ---------------------------------------------------------------------------------------------

AXE_ASSETS = "src/main/resources/assets/jaymospowers"
AXE_PATCHES = {"wood": (0, 0, 4, 16), "wrap": (4, 0, 8, 8), "iron": (8, 0, 16, 8), "dark": (8, 8, 12, 12), "link": (12, 8, 16, 12),
               "blade": (16, 0, 24, 12), "edge": (24, 0, 26, 12)}


def axe_texture():
    def iron(x, y, w, h):
        c = (150, 152, 158)
        if x == 0 or y == 0:
            c = (198, 200, 204)
        if x == w - 1 or y == h - 1:
            c = (92, 94, 100)
        return jitter(c)

    def blade(x, y, w, h):
        # forge-dark by the eye, ground brighter toward the edge, a few hammer marks
        c = tuple(int(96 + (176 - 96) * x / (w - 1)) + d for d in (0, 2, 6))
        if (x * 3 + y * 5) % 11 == 0:
            c = tuple(v - 16 for v in c)
        return jitter(c, 4)

    return paint(AXE_PATCHES, {
        "wood": lambda x, y, w, h: jitter((120, 86, 52) if x in (1, 2) else (84, 58, 36), 4),
        "wrap": lambda x, y, w, h: jitter((70, 44, 28) if (x + y) % 3 else (40, 26, 18), 4),
        "iron": iron,
        "dark": lambda x, y, w, h: jitter((60, 60, 64) if (x + y) % 2 else (84, 86, 92), 3),
        "link": lambda x, y, w, h: jitter((112, 114, 120) if x in (0, w - 1) or y in (0, h - 1) else (52, 52, 58), 3),
        "blade": blade,
        "edge": lambda x, y, w, h: jitter((226, 228, 230) if x == 1 else (190, 193, 198), 3)})


def chain_axe():
    p = AXE_PATCHES
    # the haft, upright; the chain leaves the staple on its top: HANDLE_TIP in client/armory/LashSockets
    handle = [
        element("butt_cap", [6.75, 0, 6.75], [9.25, 1, 9.25], "iron", p),
        element("haft", [7, 1, 7], [9, 11, 9], "wood", p),
        element("grip_wrap", [6.8, 2, 6.8], [9.2, 6.5, 9.2], "wrap", p),
        element("collar", [6.75, 11, 6.75], [9.25, 13, 9.25], "iron", p),
        element("staple", [7.5, 13, 7.5], [8.5, 14.5, 8.5], "dark", p)]
    # the head, hanging by its ring from the top middle of the box (8, 16, 8): a bearded blade, a spike behind the eye
    head = [
        element("ring", [7.5, 14.5, 7.5], [8.5, 16, 8.5], "dark", p),
        element("eye", [6.5, 9, 6.9], [9.5, 14.5, 9.1], "iron", p),
        element("back_spike", [4.5, 10.75, 7.5], [6.5, 12.75, 8.5], "iron", p),
        element("cheek", [9.5, 9.5, 7.4], [12, 14, 8.6], "blade", p),
        element("blade", [12, 8, 7.6], [14, 15, 8.4], "blade", p),
        element("beard", [12, 5, 7.6], [14, 8, 8.4], "blade", p),
        element("edge", [14, 5.5, 7.8], [15, 14.5, 8.2], "edge", p)]
    links = [
        element("link_1", [9.25, 13.75, 7.6], [10.75, 14.75, 8.4], "link", p),
        element("link_2", [10.75, 13.25, 7.75], [12.25, 14.25, 8.25], "link", p),
        element("link_3", [12.25, 12.5, 7.6], [13.75, 13.5, 8.4], "link", p)]
    whole = handle + links + shifted(head, 5.5, -3.5, "head_")
    img = axe_texture()
    path = os.path.join(AXE_ASSETS, "textures", "item", "chain_axe_model.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    held = display(tp=0.85, fp=0.68, gui=0.6, center_y=7, grip_y=4.25)
    plain = {"ground": {"translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]}, "fixed": {"rotation": [0, 0, -45], "translation": [-2, 0, 0], "scale": [0.7, 0.7, 0.7]}}
    write_model(AXE_ASSETS, "jaymospowers", "chain_axe_handle", "chain_axe_model", handle, held)
    write_model(AXE_ASSETS, "jaymospowers", "chain_axe_swung", "chain_axe_model", head, {})
    write_model(AXE_ASSETS, "jaymospowers", "chain_axe_whole", "chain_axe_model", whole, dict(held, **plain))
    # the swung head is its own item model, worn by a chain axe stack the renderer makes (the item_model component)
    write_json(os.path.join(AXE_ASSETS, "items", "chain_axe_swung.json"), {"model": {"type": "minecraft:model", "model": "jaymospowers:item/chain_axe_swung"}})
    item_definition(AXE_ASSETS, "jaymospowers", "chain_axe", "chain_axe_handle", "chain_axe_whole", "chain_axe")
    write_bbmodel("art/chain_axe.bbmodel", "chain_axe", img, whole, dict(held, **plain))
    return img, {"handle": handle, "head": head, "whole": whole}


# ---- the lasso (Kingdoms) -------------------------------------------------------------------------------------------------

LASSO_ASSETS = "kingdoms/src/main/resources/assets/jaymoskingdoms"
LASSO_PATCHES = {"rope": (0, 0, 16, 4), "rope_up": (0, 4, 4, 20), "knot": (16, 0, 22, 6), "leather": (4, 4, 10, 10)}


def lasso_texture():
    def strand(along, across):
        # three twisted strands of pale hemp, the lay showing as dark diagonals
        c = (196, 165, 106) if (along + across) % 3 else (140, 112, 66)
        if across == 0:
            c = tuple(min(255, v + 18) for v in c)
        return jitter(c, 4)

    return paint(LASSO_PATCHES, {
        "rope": lambda x, y, w, h: strand(x, y),
        "rope_up": lambda x, y, w, h: strand(y, x),
        "knot": lambda x, y, w, h: jitter((164, 134, 82) if (x + y) % 2 else (116, 92, 54), 4),
        "leather": lambda x, y, w, h: jitter((92, 60, 36) if (x + y) % 4 else (58, 36, 20), 4)})


def ring(prefix, x0, y0, x1, y1, z0, z1, t, patches):
    """A square hoop of rope in the x-y plane, t thick."""
    return [
        element(prefix + "_bottom", [x0, y0, z0], [x1, y0 + t, z1], "rope", patches),
        element(prefix + "_top", [x0, y1 - t, z0], [x1, y1, z1], "rope", patches),
        element(prefix + "_left", [x0, y0 + t, z0], [x0 + t, y1 - t, z1], "rope_up", patches),
        element(prefix + "_right", [x1 - t, y0 + t, z0], [x1, y1 - t, z1], "rope_up", patches)]


def lasso():
    p = LASSO_PATCHES
    # the coil, gripped by its bottom turn; the rope leaves the top of it: COIL_TIP in client/forms/LassoRope
    coil = (ring("coil_a", 3.5, 1, 12.5, 10, 6.6, 7.6, 1, p) + ring("coil_b", 4, 1.5, 12, 9.5, 7.6, 8.6, 1, p)
            + ring("coil_c", 3.5, 1, 12.5, 10, 8.6, 9.6, 1, p)
            + [element("keeper", [6.75, 0.5, 6.25], [9.25, 2.5, 9.95], "leather", p),
               element("lead", [7.5, 10, 7.6], [8.5, 11.5, 8.6], "rope_up", p)])
    # the loop, hanging by its honda knot from the top middle of the box (8, 16, 8)
    loop = ring("loop", 3, 3, 13, 14.5, 7.5, 8.5, 1, p) + [element("honda", [7, 14, 7], [9, 16, 9], "knot", p)]
    whole = coil + [element("tail", [8.5, 10.5, 7.6], [15, 11.5, 8.6], "rope", p)] + shifted(loop, 9, -7, "end_")
    img = lasso_texture()
    path = os.path.join(LASSO_ASSETS, "textures", "item", "lasso_model.png")
    img.save(path)
    held = display(tp=0.8, fp=0.62, gui=0.6, center_y=6, grip_y=1.5)
    plain = {"ground": {"translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]}, "fixed": {"translation": [-2, 2, 0], "scale": [0.6, 0.6, 0.6]}}
    write_model(LASSO_ASSETS, "jaymoskingdoms", "lasso_coil", "lasso_model", coil, held)
    write_model(LASSO_ASSETS, "jaymoskingdoms", "lasso_loop", "lasso_model", loop, {})
    write_model(LASSO_ASSETS, "jaymoskingdoms", "lasso_whole", "lasso_model", whole, dict(held, **plain))
    write_json(os.path.join(LASSO_ASSETS, "items", "lasso_loop.json"), {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/lasso_loop"}})
    item_definition(LASSO_ASSETS, "jaymoskingdoms", "lasso", "lasso_coil", "lasso_whole", "lasso")
    write_bbmodel("kingdoms/art/lasso.bbmodel", "lasso", img, whole, dict(held, **plain))
    return img, {"coil": coil, "loop": loop, "whole": whole}


# ---- a preview sheet -------------------------------------------------------------------------------------------------------

def draw(sheet, at, img, elements, yaw, pitch, scale):
    """Paints the boxes' faces back to front, each with its patch's middle colour, shaded by which way it faces."""
    cy, sy, cp, sp = math.cos(math.radians(yaw)), math.sin(math.radians(yaw)), math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(v):
        x, y, z = v[0] - 8, v[1] - 8, v[2] - 8
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return x, y, z

    corners = {"north": ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)), "south": ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
               "west": ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)), "east": ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
               "up": ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)), "down": ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1))}
    shade = {"up": 1.0, "south": 0.85, "north": 0.85, "east": 0.7, "west": 0.7, "down": 0.55}
    quads = []
    for el in elements:
        box = (el["from"], el["to"])
        for face, picks in corners.items():
            pts = [view([box[c[i]][i] for i in range(3)]) for c in picks]
            uv = el["faces"][face]["uv"]
            # the texture's own pixels across the face: a small grid of them
            n = 4
            for i in range(n):
                for j in range(n):
                    def at_uv(s, t):
                        a = [pts[0][k] + (pts[3][k] - pts[0][k]) * s for k in range(3)]
                        b = [pts[1][k] + (pts[2][k] - pts[1][k]) * s for k in range(3)]
                        return [a[k] + (b[k] - a[k]) * t for k in range(3)]
                    cell = [at_uv(i / n, j / n), at_uv((i + 1) / n, j / n), at_uv((i + 1) / n, (j + 1) / n), at_uv(i / n, (j + 1) / n)]
                    px = img.getpixel((min(31, int((uv[0] + (uv[2] - uv[0]) * (i + 0.5) / n) * 2)), min(31, int((uv[1] + (uv[3] - uv[1]) * (j + 0.5) / n) * 2))))
                    quads.append((sum(c[2] for c in cell) / 4, [(at[0] + c[0] * scale, at[1] - c[1] * scale) for c in cell],
                                  tuple(int(v * shade[face]) for v in px[:3])))
    pen = ImageDraw.Draw(sheet)
    for _, poly, colour in sorted(quads, key=lambda q: q[0]):
        pen.polygon(poly, fill=colour)


def preview(path, sets):
    cell = 300
    sheet = Image.new("RGB", (cell * 3, cell * len(sets)), (44, 46, 52))
    pen = ImageDraw.Draw(sheet)
    for row, (img, parts) in enumerate(sets):
        for col, (name, elements) in enumerate(parts.items()):
            draw(sheet, (cell * col + cell // 2, cell * row + cell // 2 + 10), img, elements, 30, 18, 12)
            pen.text((cell * col + 8, cell * row + 6), name, fill=(230, 230, 230))
    sheet.save(path)


if __name__ == "__main__":
    sets = [chain_axe(), lasso()]
    if len(sys.argv) > 1:
        preview(sys.argv[1], sets)
