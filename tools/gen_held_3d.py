"""
(Motion patch 5) 3D models, after Actions & Stuff's 3D items (ideas only; nothing of theirs is used).

Writes the built-in resource pack resourcepacks/held_3d ("Jaymo's Powers: 3D items and plants", on by default and
switched off in Resource Packs like any pack):

* Held items voxelised from their own sprites, as tools/gen_guns.py does for WeaponMod's guns: every opaque pixel
  becomes a small block, handles and heavy parts thicker than blades and strings, so each item keeps its exact look
  but has body in the hand. The inventory icon, item frames, shelves and dropped items keep the flat sprite (a
  display_context select around each model). The sprites themselves aren't copied: the models point at the items'
  own textures, so any resource pack that repaints them still shows through.
  - The vanilla tool set (every tier of sword, pickaxe, axe, shovel, hoe), bow, crossbow, fishing rod, shears, mace.
  - Compass, recovery compass, clock (every frame), totem of undying, brush, saddle. (The spyglass already has its
    own 3D model in hand.)
  - Bottles: potions, splash and lingering potions, experience, honey, ominous, dragon's breath, the empty glass
    bottle, and ours (essence phial, phial, purifying draught, Kitchen's milk bottle). Potion-coloured contents keep
    their tint and glow a little (light_emission); an experience bottle glows.
* (Not in the pack) our glow for the iron golem's eyes, textures/entity/iron_golem_eyes.png.
* Blocks: red and brown mushrooms as a little stem and cap; pointed dripstone as stacked, narrowing cuboids for each
  thickness (tip, frustum, middle, base, tip merge) both ways; the doubled smooth stone slab as one block (it is the
  only vanilla double slab that shows a seam).

Run from the project root:  python -I tools/gen_held_3d.py <path to the Minecraft 26.3 client jar>
"""
import io
import json
import os
import shutil
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "src", "main", "resources", "resourcepacks", "held_3d")
NS = "jaymospowers"
# where our own items' assets live, by namespace
OWN = {
    "jaymospowers": os.path.join(ROOT, "src", "main", "resources", "assets", "jaymospowers"),
    "jaymoskitchen": os.path.join(ROOT, "kitchen", "src", "main", "resources", "assets", "jaymoskitchen"),
}
FLAT = ["gui", "ground", "fixed", "on_shelf"]

TIERS = ["wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"]
TOOL_KINDS = ["sword", "pickaxe", "axe", "shovel", "hoe"]
# head thickness (pixels) of each tool kind; the handle is HANDLE thick
HEAD = {"sword": 1.25, "pickaxe": 1.5, "axe": 2.0, "shovel": 1.25, "hoe": 1.5}
HANDLE = 2.0

jar = None


# ----- reading assets ------------------------------------------------------------------------------------------------
def split(ident):
    ns, path = ident.split(":", 1) if ":" in ident else ("minecraft", ident)
    return ns, path


def read(ns, kind, path):
    """Bytes of assets/<ns>/<kind>/<path> from our sources or the vanilla jar; None if missing."""
    if ns in OWN:
        full = os.path.join(OWN[ns], kind, path)
        if os.path.exists(full):
            with open(full, "rb") as f:
                return f.read()
        return None
    name = f"assets/{ns}/{kind}/{path}"
    return jar.read(name) if name in jar.namelist() else None


def model_json(ident):
    ns, path = split(ident)
    data = read(ns, "models", path + ".json")
    return json.loads(data) if data else None


def resolved(ident):
    """(textures, display, root parent) of a model, following its parents."""
    textures, display, parent, chain = {}, None, None, ident
    for _ in range(8):
        m = model_json(chain)
        if m is None:
            break
        for k, v in m.get("textures", {}).items():
            textures.setdefault(k, v)
        if display is None and "display" in m:
            display = m["display"]
        if parent is None:
            parent = m.get("parent")
        if "parent" not in m:
            break
        chain = m["parent"]
    return textures, display, parent


def sprite(ident):
    ns, path = split(ident)
    data = read(ns, "textures", path + ".png")
    return Image.open(io.BytesIO(data)).convert("RGBA") if data else None


# ----- voxelising ----------------------------------------------------------------------------------------------------
def runs(img, depth_of):
    """Each row's runs of pixels with the same depth: (x0, x1, y, depth)."""
    w, h = img.size
    out = []
    for y in range(h):
        x = 0
        while x < w:
            d = depth_of(img.getpixel((x, y)))
            if d is None:
                x += 1
                continue
            start = x
            while x < w and depth_of(img.getpixel((x, y))) == d:
                x += 1
            out.append((start, x, y, d))
    return out


def elements(img, var, depth_of, tint=None, glow=0):
    w, _ = img.size
    s = 16.0 / w  # sprites bigger than 16 px still fill one block
    out = []
    for x0, x1, y, depth in runs(img, depth_of):
        z0, z1 = 8 - depth / 2, 8 + depth / 2
        u0, u1, v0, v1 = x0 * s, x1 * s, y * s, (y + 1) * s
        row = [u0, v0, u1, v1]
        faces = {
            "south": {"uv": row},
            "north": {"uv": [u1, v0, u0, v1]},
            "up": {"uv": row},
            "down": {"uv": row},
            "west": {"uv": [u0, v0, u0 + s, v1]},
            "east": {"uv": [u1 - s, v0, u1, v1]},
        }
        for face in faces.values():
            face["texture"] = "#" + var
            if tint is not None:
                face["tintindex"] = tint
        e = {"from": [round(x0 * s, 4), round(16 - (y + 1) * s, 4), z0], "to": [round(x1 * s, 4), round(16 - y * s, 4), z1],
             "faces": faces}
        if glow:
            e["light_emission"] = glow
        out.append(e)
    return out


def opaque(px):
    return px[3] >= 128


def grey(px):
    r, g, b, _ = px
    return max(r, g, b) - min(r, g, b) < 24


STICK = set()


def tool_depth(kind):
    head = HEAD[kind]

    def depth(px):
        if not opaque(px):
            return None
        return HANDLE if px[:3] in STICK else head
    return depth


def flat_depth(d):
    return lambda px: d if opaque(px) else None


def stringed(wood, string):
    """Bows, crossbows and rods: pale grey string (and the rod's line) thin, the rest thick."""
    def depth(px):
        if not opaque(px):
            return None
        r, g, b, _ = px
        return string if grey(px) and max(r, g, b) > 120 else wood
    return depth


def mace_depth(px):
    if not opaque(px):
        return None
    return HANDLE if px[:3] in STICK else 3.0


# item id -> (depth rule, {layer index: (tint index or None, glow)})
RULES = {}
for tier in TIERS:
    for kind in TOOL_KINDS:
        RULES[f"minecraft:{tier}_{kind}"] = (tool_depth(kind), {})
RULES.update({
    "minecraft:bow": (stringed(2.0, 0.5), {}),
    "minecraft:crossbow": (stringed(2.0, 0.5), {}),
    "minecraft:fishing_rod": (stringed(1.5, 0.5), {}),
    "minecraft:shears": (flat_depth(1.5), {}),
    "minecraft:mace": (mace_depth, {}),
    "minecraft:compass": (flat_depth(2.0), {}),
    "minecraft:recovery_compass": (flat_depth(2.0), {}),
    "minecraft:clock": (flat_depth(2.0), {}),
    "minecraft:totem_of_undying": (flat_depth(2.0), {}),
    "minecraft:brush": (flat_depth(1.75), {}),
    "minecraft:saddle": (flat_depth(3.0), {}),
})
# bottles: glass 3 px; contents a little thinner inside it, coloured by the item's tint and glowing a little
GLASS, CONTENTS, GLOW = 3.0, 2.6, 7
for bottle in ("potion", "splash_potion", "lingering_potion"):
    RULES[f"minecraft:{bottle}"] = ("bottle", {0: (0, GLOW)})
RULES["minecraft:experience_bottle"] = (flat_depth(GLASS), {"all": (None, 10)})
for bottle in ("honey_bottle", "glass_bottle", "ominous_bottle", "dragon_breath"):
    RULES[f"minecraft:{bottle}"] = (flat_depth(GLASS), {})
RULES["jaymospowers:essence_phial"] = ("bottle", {0: (0, GLOW)})
RULES["jaymospowers:phial"] = (flat_depth(GLASS), {})
RULES["jaymospowers:purifying_draught"] = (flat_depth(GLASS), {"all": (None, 4)})
RULES["jaymoskitchen:milk_bottle"] = (flat_depth(GLASS), {})


# ----- writing -------------------------------------------------------------------------------------------------------
def write(path, data, compact=True):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        if compact:
            json.dump(data, f, separators=(",", ":"))
        else:
            json.dump(data, f, indent=2)
            f.write("\n")


made = {}


def solid(model_id, rule):
    """Our 3D copy of an item model; its id, or None if it can't be made (no sprite)."""
    if model_id in made:
        return made[model_id]
    depth_rule, layers = rule
    textures, display, parent = resolved(model_id)
    names = sorted((k for k in textures if k.startswith("layer")), key=lambda k: int(k[5:]))
    if not names:
        made[model_id] = None
        return None
    els, tex = [], {}
    for name in names:
        index = int(name[5:])
        img = sprite(textures[name])
        if img is None:
            continue
        var = "l" + str(index)
        tex[var] = textures[name]
        tint, glow = layers.get(index, layers.get("all", (None, 0)))
        if depth_rule == "bottle":
            depth = flat_depth(CONTENTS if tint is not None else GLASS)
        else:
            depth = depth_rule
        els += elements(img, var, depth, tint, glow)
    if not els:
        made[model_id] = None
        return None
    tex["particle"] = tex[sorted(tex)[0]]
    ns, path = split(model_id)
    out_id = f"{NS}:item/held_3d/{ns}/{path.split('/')[-1]}"
    model = {"parent": parent or "minecraft:item/generated", "textures": tex, "gui_light": "front", "elements": els}
    if display:
        model["display"] = display
    write(os.path.join(PACK, "assets", NS, "models", "item", "held_3d", ns, path.split("/")[-1] + ".json"), model)
    made[model_id] = out_id
    return out_id


def wrap(node, rule):
    """Every model in an item definition, flat in the inventory and solid in hand."""
    if isinstance(node, list):
        return [wrap(n, rule) for n in node]
    if not isinstance(node, dict):
        return node
    kind = node.get("type")
    if kind == "minecraft:select" and node.get("property") == "minecraft:display_context":
        return node  # already shows something else in hand
    if kind == "minecraft:model":
        out = solid(node["model"], rule)
        if out is None:
            return node
        held = dict(node)
        held["model"] = out
        return {"type": "minecraft:select", "property": "minecraft:display_context",
                "cases": [{"when": FLAT, "model": node}], "fallback": held}
    return {k: wrap(v, rule) for k, v in node.items()}



# ----- JMB's Better Weapons (MIT, jmb19905; tools/jmb_weapons/LICENSE) ---------------------------------------------------
# Andy (2026-11-14) picked its 3D models for the longsword, glaive, battlestaff (our quarterstaff), club (our cudgel) and
# long axe (WeaponMod's battleaxe). Its templates and material palettes are copied into the pack as they are; stone and
# copper palettes are made from its iron one, recoloured along vanilla's stone and copper sword heads. Flat in the
# inventory as before; the quarterstaff swings across into JMB's blocking hold while raised.
JMB = os.path.join(ROOT, "tools", "jmb_weapons")
JMB_TEX = f"{NS}:item/held_3d/jmb/"
# our tiers -> JMB's palettes
JMB_PALETTE = {"wooden": "wooden", "wood": "wooden", "stone": "stone", "copper": "copper", "iron": "iron", "golden": "gold",
               "gold": "gold", "diamond": "diamond", "netherite": "netherite"}
# the quarterstaff's caps (JMB's battlestaff takes a block for them)
STAFF_CAPS = {"wooden": "minecraft:block/oak_planks", "iron": "minecraft:block/iron_block"}
CLUB_WOOD = {"wooden": "minecraft:block/oak_log", "stone": "minecraft:block/cobblestone"}


def ramp_palette(name, sword):
    """JMB's iron palette with its metal recoloured along a vanilla sword head's shades (dark to light)."""
    iron = Image.open(os.path.join(JMB, "textures", "iron_weapon.png")).convert("RGBA")
    head = sprite(f"minecraft:item/{sword}")
    pixels = lambda img: [img.getpixel((x, y)) for y in range(img.height) for x in range(img.width)]
    shades = sorted({px[:3] for px in pixels(head) if opaque(px) and px[:3] not in STICK}, key=lambda c: sum(c))
    out = iron.copy()
    lum = lambda c: c[0] * 0.3 + c[1] * 0.59 + c[2] * 0.11
    metal = [px for px in pixels(iron) if opaque(px) and not (px[0] > px[2] + 30)]
    lo, hi = min(lum(p) for p in metal), max(lum(p) for p in metal)
    for y in range(iron.height):
        for x in range(iron.width):
            px = iron.getpixel((x, y))
            if not opaque(px) or px[0] > px[2] + 30:  # handle (brown) stays
                continue
            t = 0.0 if hi == lo else (lum(px) - lo) / (hi - lo)
            c = shades[min(len(shades) - 1, int(t * (len(shades) - 1) + 0.5))]
            out.putpixel((x, y), (*c, px[3]))
    out.save(os.path.join(PACK, "assets", NS, "textures", "item", "held_3d", "jmb", name + ".png"))


def jmb_model(template, item_id, textures, display=None):
    model = {"parent": f"{NS}:item/held_3d/jmb/{template}", "textures": textures}
    if display:
        model["display"] = display
    write(os.path.join(PACK, "assets", NS, "models", "item", "held_3d", "jmb", item_id + ".json"), model, compact=False)
    return f"{NS}:item/held_3d/jmb/{item_id}"


def jmb_item(ns, item, flat_model, held):
    write(os.path.join(PACK, "assets", ns, "items", item + ".json"),
          {"model": {"type": "minecraft:select", "property": "minecraft:display_context",
                     "cases": [{"when": FLAT, "model": {"type": "minecraft:model", "model": flat_model}}], "fallback": held}},
          compact=False)


def jmb_weapons():
    tex_dir = os.path.join(PACK, "assets", NS, "textures", "item", "held_3d", "jmb")
    os.makedirs(tex_dir, exist_ok=True)
    shutil.copy(os.path.join(JMB, "LICENSE"), os.path.join(PACK, "JMB-BetterWeapons-LICENSE.txt"))
    for f in os.listdir(os.path.join(JMB, "textures")):
        shutil.copy(os.path.join(JMB, "textures", f), os.path.join(tex_dir, f[:-len("_weapon.png")] + ".png"))
    ramp_palette("stone", "stone_sword")
    ramp_palette("copper", "copper_sword")
    for f in os.listdir(os.path.join(JMB, "models")):
        model = json.load(open(os.path.join(JMB, "models", f), encoding="utf-8"))
        model.pop("parent", None)
        model.pop("textures", None)  # each weapon names its own
        write(os.path.join(PACK, "assets", NS, "models", "item", "held_3d", "jmb", f), model, compact=False)
    blocking = json.load(open(os.path.join(JMB, "models", "battlestaff_blocking.json"), encoding="utf-8"))["display"]
    count = 0
    for tier in ("wooden", "stone", "iron", "golden", "diamond", "netherite"):
        pal = JMB_TEX + JMB_PALETTE[tier]
        for kind, template in (("longsword", "longsword_template"), ("glaive", "glaive_template")):
            held = jmb_model(template, f"{tier}_{kind}", {"0": pal, "particle": pal})
            jmb_item(NS, f"{tier}_{kind}", f"{NS}:item/armory/{tier}_{kind}", {"type": "minecraft:model", "model": held})
            count += 1
    for tier, caps in STAFF_CAPS.items():
        textures = {"0": "minecraft:block/oak_log", "1": caps, "particle": "minecraft:block/oak_log"}
        held = jmb_model("battlestaff_template", f"{tier}_quarterstaff", textures)
        raised = jmb_model("battlestaff_template", f"{tier}_quarterstaff_blocking", textures, blocking)
        jmb_item(NS, f"{tier}_quarterstaff", f"{NS}:item/armory/{tier}_quarterstaff",
                 {"type": "minecraft:condition", "property": "minecraft:using_item",
                  "on_true": {"type": "minecraft:model", "model": raised}, "on_false": {"type": "minecraft:model", "model": held}})
        count += 1
    for tier, wood in CLUB_WOOD.items():
        held = jmb_model("club", f"{tier}_cudgel", {"0": wood, "particle": wood})
        jmb_item(NS, f"{tier}_cudgel", f"{NS}:item/armory/{tier}_cudgel", {"type": "minecraft:model", "model": held})
        count += 1
    # WeaponMod's battleaxes, as JMB's long axe
    for metal in ("wood", "stone", "copper", "iron", "gold", "diamond", "netherite"):
        pal = JMB_TEX + JMB_PALETTE[metal]
        held = jmb_model("longaxe_template", f"battleaxe_{metal}", {"0": pal, "particle": "minecraft:block/stripped_spruce_log"})
        jmb_item("weaponmod", f"battleaxe.{metal}", f"weaponmod:item/battleaxe.{metal}", {"type": "minecraft:model", "model": held})
        count += 1
    return count

# ----- blocks --------------------------------------------------------------------------------------------------------
def box(x0, y0, z0, x1, y1, z1, faces):
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


def all_faces(var, skip=()):
    return {f: {"texture": "#" + var} for f in ("north", "south", "east", "west", "up", "down") if f not in skip}


def mushroom(cap_tex, wide, tall):
    """A stem and a cap, the cap's underside the inside of a mushroom block."""
    lo, hi = 8 - wide / 2, 8 + wide / 2
    cap = all_faces("cap", skip=("down",))
    cap["down"] = {"texture": "#inside"}
    els = [box(7, 0, 7, 9, 4, 9, all_faces("stem", skip=("up", "down"))),
           box(lo, 4, lo, hi, 4 + tall, hi, cap)]
    if wide >= 6:
        els.append(box(lo + 1, 4 + tall, lo + 1, hi - 1, 5 + tall, hi - 1, all_faces("cap", skip=("down",))))
    return {"ambientocclusion": False,
            "textures": {"particle": cap_tex, "cap": cap_tex, "stem": "minecraft:block/mushroom_stem",
                         "inside": "minecraft:block/mushroom_block_inside"},
            "elements": els}


# half widths of each dripstone thickness from its broad end, as (from y, to y, half width) pointing up
DRIPSTONE = {
    "base": [(0, 6, 4.0), (6, 12, 3.5), (12, 16, 3.0)],
    "middle": [(0, 8, 3.0), (8, 16, 2.5)],
    "frustum": [(0, 6, 2.5), (6, 12, 2.0), (12, 16, 1.5)],
    "tip": [(0, 4, 1.5), (4, 9, 1.0), (9, 13, 0.5)],
    "tip_merge": [(0, 5, 1.5), (5, 11, 1.0), (11, 16, 0.5)],
}


def dripstone(thickness, up):
    els = []
    for y0, y1, hw in DRIPSTONE[thickness]:
        a, b = (y0, y1) if up else (16 - y1, 16 - y0)
        els.append(box(8 - hw, a, 8 - hw, 8 + hw, b, 8 + hw, all_faces("stone")))
    return {"ambientocclusion": False,
            "textures": {"particle": f"minecraft:block/pointed_dripstone_{'up' if up else 'down'}_{thickness}",
                         "stone": "minecraft:block/dripstone_block"},
            "elements": els}


def golem_eyes():
    """Our own glow for the iron golem's eyes (client/GolemEyes): a red-orange spark on each pupil and a faint ember in
    the sockets around it, where the eyes sit on the golem's 128 x 128 skin (head front: u 8-16, v 8-18)."""
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    for x, y in ((9, 14), (14, 14)):
        img.putpixel((x, y), (255, 92, 48, 255))
    for x, y in ((9, 13), (10, 13), (10, 14), (13, 13), (14, 13), (13, 14)):
        img.putpixel((x, y), (96, 22, 10, 255))
    path = os.path.join(ROOT, "src", "main", "resources", "assets", NS, "textures", "entity", "iron_golem_eyes.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    global jar
    golem_eyes()
    jar = zipfile.ZipFile(sys.argv[1])
    stick = sprite("minecraft:item/stick")
    for y in range(16):
        for x in range(16):
            px = stick.getpixel((x, y))
            if opaque(px):
                STICK.add(px[:3])
    if os.path.isdir(PACK):
        shutil.rmtree(PACK)
    write(os.path.join(PACK, "pack.mcmeta"),
          {"pack": {"description": "Jaymo's Powers: 3D items and plants", "min_format": 97, "max_format": 97}}, compact=False)
    for item, rule in RULES.items():
        ns, path = split(item)
        data = read(ns, "items", path + ".json")
        if data is None:
            print("missing", item)
            continue
        definition = json.loads(data)
        definition["model"] = wrap(definition["model"], rule)
        write(os.path.join(PACK, "assets", ns, "items", path + ".json"), definition, compact=False)
    blocks = os.path.join(PACK, "assets", "minecraft", "models", "block")
    write(os.path.join(blocks, "red_mushroom.json"), mushroom("minecraft:block/red_mushroom_block", 6, 2), compact=False)
    write(os.path.join(blocks, "brown_mushroom.json"), mushroom("minecraft:block/brown_mushroom_block", 8, 1), compact=False)
    for thickness in DRIPSTONE:
        for up in (True, False):
            write(os.path.join(blocks, f"pointed_dripstone_{'up' if up else 'down'}_{thickness}.json"), dripstone(thickness, up),
                  compact=False)
    write(os.path.join(blocks, "smooth_stone_slab_double.json"),
          {"parent": "minecraft:block/cube_all", "textures": {"all": "minecraft:block/smooth_stone"}}, compact=False)
    jmb = jmb_weapons()
    print(len(RULES), "items,", sum(1 for v in made.values() if v), "held models,", jmb, "JMB weapons")


if __name__ == "__main__":
    main()
