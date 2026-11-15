"""Writes the assets, data and lang of the medieval picks, patch 8 ("Shields and smithing", docs/Medieval-Rows.md) for
Jaymo's Powers:

* gu-netherite: the netherite shield (its dark plate, from the shield's own texture recoloured; its item definition on
  the jaymospowers:netherite_shield special renderer; a shield decal for each vanilla armour trim pattern, taken from the
  pattern's own chest decal and laid over the shield's face, paletted as trims are);
* me-alloys: lead (ores, raw lead, ingot, nugget, the deep vein under the mountains), the realm steels' ingots, their
  molten textures and hot-ingot looks, and their melting;
* me-mail: mail rings and mail sheets (drawn here), their recipes, the chainmail armour laced from sheets;
* me-inscribe: the Inscription Table (a stone desk with runes cut in its top) and the chisel;
* the names (lang/parts/medieval_smithing.json).

Art is drawn here or is vanilla's own recoloured, so point it at the 26.3 client jar:

    python3 -I tools/gen_medieval_smithing.py ~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar

The ids must match com.jaymospowers.combat.ShieldCraft, tinkering.smithing.RealmSteels and MailRings, and
enchanting.Inscription. Safe to run again (every file is overwritten, every edit to a shared file is idempotent).
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_stone_age as sa  # noqa: E402

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data")
NS = "jaymospowers"

# ---- tuning ----
# lead: one vein of LEAD_VEIN blocks in LEAD_RARITY chunks, between LEAD_LOW and LEAD_HIGH, under the mountains only
LEAD_VEIN, LEAD_RARITY, LEAD_LOW, LEAD_HIGH = 9, 2, -48, 24
LEAD_PER_CHUNK = 6
# mail: rings one hammering of three nuggets makes, rings in a sheet, sheets in each piece
RINGS_PER_HAMMERING = 3
SHEETS = {"chainmail_helmet": ["SSS", "S S"], "chainmail_chestplate": ["S S", "SSS", "SSS"],
          "chainmail_leggings": ["SSS", "S S", "S S"], "chainmail_boots": ["S S", "S S"]}
# mB: an ingot is 90, a nugget 10; ore and raw ore melt doubled; a pinch of ash 50
INGOT, NUGGET, ORE, ASH = 90, 10, 180, 50

# colour ramps (dark, light)
NETHERITE = ((26, 22, 26), (112, 100, 104))
LEAD = ((52, 56, 70), (170, 178, 196))
DEEP_STEEL = ((34, 40, 52), (140, 152, 172))
BRIGHT_STEEL = ((120, 130, 146), (250, 252, 255))
ASH_BRONZE = ((58, 34, 18), (190, 132, 84))
MOLTEN = {"lead": ((70, 74, 90), (196, 204, 222)), "ash": ((70, 66, 62), (200, 196, 190)),
          "deep_steel": ((60, 64, 84), (190, 200, 226)), "bright_steel": ((170, 180, 196), (255, 255, 255)),
          "ash_bronze": ((110, 56, 20), (230, 160, 96))}
STEELS = {"deep_steel": DEEP_STEEL, "bright_steel": BRIGHT_STEEL, "ash_bronze": ASH_BRONZE}
TRIMS = ["sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib", "spire", "wayfinder", "shaper",
         "silence", "raiser", "host", "flow", "bolt"]

LANG = {
    "item.jaymospowers.netherite_shield": "Netherite Shield",
    "enchantment.jaymospowers.pummelling": "Pummelling",
    "enchantment.jaymospowers.barbed": "Barbed",
    "block.jaymospowers.lead_ore": "Lead Ore",
    "block.jaymospowers.deepslate_lead_ore": "Deepslate Lead Ore",
    "item.jaymospowers.raw_lead": "Raw Lead",
    "item.jaymospowers.lead_ingot": "Lead Ingot",
    "item.jaymospowers.lead_nugget": "Lead Nugget",
    "item.jaymospowers.deep_steel_ingot": "Deep Steel Ingot",
    "item.jaymospowers.bright_steel_ingot": "Bright Steel Ingot",
    "item.jaymospowers.ash_bronze_ingot": "Ash Bronze Ingot",
    "molten.jaymospowers.lead": "Molten Lead",
    "molten.jaymospowers.ash": "Ash",
    "molten.jaymospowers.deep_steel": "Deep Steel",
    "molten.jaymospowers.bright_steel": "Bright Steel",
    "molten.jaymospowers.ash_bronze": "Ash Bronze",
    "message.jaymospowers.smithing.realm_steel": "You don't know the working of %s",
    "message.jaymospowers.smithing.realm_design": "The %s is another realm's design",
    "item.jaymospowers.mail_ring": "Mail Rings",
    "item.jaymospowers.mail_sheet": "Mail Sheet",
    "item.jaymospowers.chisel": "Chisel",
    "block.jaymospowers.inscription_table": "Inscription Table",
    "screen.jaymospowers.inscription.title": "Words of Power",
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def png(path, im):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def ref(model):
    return {"model": {"type": "minecraft:model", "model": model}}


def merge_tag(path, values):
    old = json.load(open(path)) if os.path.exists(path) else {"values": []}
    have = old.get("values", [])
    keys = [v if isinstance(v, str) else v.get("id") for v in have]
    for v in values:
        if (v if isinstance(v, str) else v.get("id")) not in keys:
            have.append(v)
    old["values"] = have
    write(path, old)


def spots(im, ramp):
    """Only an ore's coloured spots, recoloured on the ramp; its stone is kept."""
    keep_mask = []
    rgba = im.convert("RGBA")
    for y in range(rgba.height):
        for x in range(rgba.width):
            r, g, b, a = rgba.getpixel((x, y))
            hi, lo = max(r, g, b), min(r, g, b)
            keep_mask.append(hi == 0 or (hi - lo) / hi < 0.18)
    w = rgba.width
    return sa.recolour(rgba, *ramp, keep=lambda x, y: keep_mask[y * w + x])


# ---------------------------------------------------------------- drawn art

def mail_ring():
    """A little heap of linked iron rings."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for cx, cy in ((5, 6), (10, 6), (7, 10), (12, 11), (4, 12)):
        d.ellipse((cx - 3, cy - 3, cx + 2, cy + 2), outline=(70, 72, 78, 255))
        d.arc((cx - 3, cy - 3, cx + 2, cy + 2), 180, 300, fill=(206, 210, 216, 255))
    return im


def mail_sheet():
    """A square of riveted mail: rows of rings, each overlapping the next."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(2, 14):
        for x in range(2, 14):
            on = (x + (y // 2) % 2) % 2 == 0
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                continue
            px[x, y] = (196, 200, 208, 255) if on else (84, 86, 94, 255)
    for x in range(2, 14):
        px[x, 13] = (60, 62, 68, 255)
    for y in range(2, 14):
        px[13, y] = (60, 62, 68, 255)
    return im


def chisel():
    """A stonemason's chisel: a wooden grip and a bright steel blade, on the diagonal."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for i in range(7):
        x, y = 2 + i, 13 - i
        px[x, y] = (110, 72, 38, 255)
        px[x + 1, y] = (74, 46, 22, 255)
    for i in range(5):
        x, y = 9 + i, 6 - i
        px[x, y] = (214, 220, 228, 255)
        px[x + 1, y] = (120, 126, 136, 255)
    px[14, 1] = (250, 252, 255, 255)
    return im


def runed_top(stone):
    """The table's top: smooth stone with lines of runes cut into it."""
    im = stone.copy()
    px = im.load()
    for row in (4, 8, 12):
        for x in range(2, 14):
            if (x * 7 + row) % 5 in (0, 2):
                px[x, row] = (58, 56, 60, 255)
                if (x + row) % 3 == 0:
                    px[x, row - 1] = (58, 56, 60, 255)
    for x in range(16):
        px[x, 0] = px[x, 15] = (92, 90, 94, 255)
    for y in range(16):
        px[0, y] = px[15, y] = (92, 90, 94, 255)
    return im


def shield_decal(humanoid):
    """A trim pattern's chest decal, stretched over the shield's face (the plate's front: u 1-13, v 1-23)."""
    chest = humanoid.crop((20, 20, 28, 32)).resize((12, 22), Image.NEAREST)
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    out.paste(chest, (1, 1))
    return out


# ---------------------------------------------------------------- assets

def art(jar):
    def vanilla(rel):
        return Image.open(io.BytesIO(jar.read("assets/minecraft/textures/" + rel + ".png"))).convert("RGBA")

    tex = os.path.join(ASSETS, "textures")
    # -- the netherite shield and its trims
    png(os.path.join(tex, "entity", "netherite_shield.png"), sa.recolour(vanilla("entity/shield/shield_base_nopattern"), *NETHERITE))
    for pattern in TRIMS:
        png(os.path.join(tex, "trims", "entity", "shield", pattern + ".png"), shield_decal(vanilla("trims/entity/humanoid/" + pattern)))
        write(os.path.join(tex, "trims", "entity", "shield", pattern + ".png.mcmeta"), {"palette": {"base_palette": "trim_base"}})
    # -- lead and the realm steels
    png(os.path.join(tex, "block", "medieval", "lead_ore.png"), spots(vanilla("block/iron_ore"), LEAD))
    png(os.path.join(tex, "block", "medieval", "deepslate_lead_ore.png"), spots(vanilla("block/deepslate_iron_ore"), LEAD))
    png(os.path.join(tex, "item", "medieval", "raw_lead.png"), sa.recolour(vanilla("item/raw_iron"), *LEAD))
    png(os.path.join(tex, "item", "medieval", "lead_ingot.png"), sa.recolour(vanilla("item/iron_ingot"), *LEAD))
    png(os.path.join(tex, "item", "medieval", "lead_nugget.png"), sa.recolour(vanilla("item/iron_nugget"), *LEAD))
    for steel, ramp in STEELS.items():
        png(os.path.join(tex, "item", "medieval", steel + "_ingot.png"), sa.recolour(vanilla("item/iron_ingot"), *ramp))
    src = os.path.join(tex, "block", "tinkering", "molten", "iron.png")
    with open(src + ".mcmeta", encoding="utf-8") as f:
        meta = json.load(f)
    for mid, (dark, light) in MOLTEN.items():
        hot = sa.recolour(Image.open(src), dark, light)
        png(os.path.join(tex, "block", "tinkering", "molten", mid + ".png"), hot)
        write(os.path.join(tex, "block", "tinkering", "molten", mid + ".png.mcmeta"), meta)
        png(os.path.join(tex, "gui", "tinkering", "molten", mid + ".png"), hot.crop((0, 0, 16, 16)))
    # -- mail, the chisel, the Inscription Table
    png(os.path.join(tex, "item", "medieval", "mail_ring.png"), mail_ring())
    png(os.path.join(tex, "item", "medieval", "mail_sheet.png"), mail_sheet())
    png(os.path.join(tex, "item", "medieval", "chisel.png"), chisel())
    stone = vanilla("block/smooth_stone")
    png(os.path.join(tex, "block", "medieval", "inscription_table_top.png"), runed_top(stone))
    png(os.path.join(tex, "block", "medieval", "inscription_table_side.png"), sa.recolour(vanilla("block/smooth_stone_slab_side"),
                                                                                         (70, 68, 72), (176, 174, 178)))
    png(os.path.join(tex, "block", "medieval", "inscription_table_foot.png"), sa.recolour(vanilla("block/stone_bricks"), (60, 58, 62),
                                                                                         (160, 158, 162)))


def models():
    items = os.path.join(ASSETS, "items")
    mi = os.path.join(ASSETS, "models", "item", "medieval")
    mb = os.path.join(ASSETS, "models", "block", "medieval")
    bs = os.path.join(ASSETS, "blockstates")
    # the netherite shield: the shield's own display, its particle netherite
    write(os.path.join(ASSETS, "models", "item", "netherite_shield.json"),
          {"parent": "minecraft:item/shield", "textures": {"particle": "minecraft:block/netherite_block"}})
    write(os.path.join(ASSETS, "models", "item", "netherite_shield_blocking.json"),
          {"parent": "minecraft:item/shield_blocking", "textures": {"particle": "minecraft:block/netherite_block"}})
    special = {"type": NS + ":netherite_shield"}
    write(os.path.join(items, "netherite_shield.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:special", "base": NS + ":item/netherite_shield", "model": special},
        "on_true": {"type": "minecraft:special", "base": NS + ":item/netherite_shield_blocking", "model": special},
        "transformation": {"left_rotation": [0.0, 0.0, 0.0, 1.0], "right_rotation": [0.0, 0.0, 0.0, 1.0],
                           "scale": [1.0, -1.0, -1.0], "translation": [0.0, 0.0, 0.0]}}})
    # plain items
    for name in ("raw_lead", "lead_ingot", "lead_nugget", "deep_steel_ingot", "bright_steel_ingot", "ash_bronze_ingot", "mail_ring",
                 "mail_sheet"):
        write(os.path.join(mi, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/medieval/" + name}})
        write(os.path.join(items, name + ".json"), ref(NS + ":item/medieval/" + name))
    write(os.path.join(mi, "chisel.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": NS + ":item/medieval/chisel"}})
    write(os.path.join(items, "chisel.json"), ref(NS + ":item/medieval/chisel"))
    # ores
    for name in ("lead_ore", "deepslate_lead_ore"):
        write(os.path.join(mb, name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": NS + ":block/medieval/" + name}})
        write(os.path.join(bs, name + ".json"), {"variants": {"": {"model": NS + ":block/medieval/" + name}}})
        write(os.path.join(items, name + ".json"), ref(NS + ":block/medieval/" + name))
    # the Inscription Table: a slab top on a thick foot (InscriptionTableBlock's shape)
    t = NS + ":block/medieval/inscription_table_"

    def box(frm, to, top, side, bottom):
        faces = {}
        for face, texture in (("up", top), ("down", bottom), ("north", side), ("south", side), ("east", side), ("west", side)):
            faces[face] = {"texture": texture}
        return {"from": frm, "to": to, "faces": faces}
    write(os.path.join(mb, "inscription_table.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": t + "side", "top": t + "top", "side": t + "side", "foot": t + "foot"},
        "elements": [box([0, 10, 0], [16, 14, 16], "#top", "#side", "#side"), box([3, 0, 3], [13, 10, 13], "#foot", "#foot", "#foot")]})
    write(os.path.join(bs, "inscription_table.json"), {"variants": {"": {"model": NS + ":block/medieval/inscription_table"}}})
    write(os.path.join(items, "inscription_table.json"), ref(NS + ":block/medieval/inscription_table"))

    # the hot ingot of each realm steel (and lead): the ingot glowing
    hot_path = os.path.join(items, "hot_ingot.json")
    hot_def = json.load(open(hot_path))
    cases = hot_def["model"]["cases"]
    template = next((c for c in cases if c["when"] == "steel"), None) or next(c for c in cases if c["when"] == "iron")
    for mid in ("lead", "deep_steel", "bright_steel", "ash_bronze"):
        if not any(c["when"] == mid for c in cases):
            case = json.loads(json.dumps(template))
            case["when"] = mid
            case["model"]["model"] = NS + ":item/medieval/" + mid + "_ingot"
            cases.append(case)
    write(hot_path, hot_def)


# ---------------------------------------------------------------- data

def recipe(name, data):
    write(os.path.join(DATA, NS, "recipe", "medieval", name + ".json"), data)


def data():
    loot = os.path.join(DATA, NS, "loot_table", "blocks")
    for block in ("lead_ore", "deepslate_lead_ore"):
        write(os.path.join(loot, block + ".json"), {"type": "minecraft:block", "random_sequence": NS + ":blocks/" + block, "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": NS + ":" + block},
                {"type": "minecraft:item", "name": NS + ":raw_lead", "modifier": [
                    {"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                    {"type": "minecraft:explosion_decay"}]}]}]}]})
    write(os.path.join(loot, "inscription_table.json"), {"type": "minecraft:block", "random_sequence": NS + ":blocks/inscription_table",
                                                          "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item",
                                                                                              "name": NS + ":inscription_table"}],
                                                                     "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tags = os.path.join(DATA, "minecraft", "tags", "block")
    merge_tag(os.path.join(tags, "mineable", "pickaxe.json"), [NS + ":lead_ore", NS + ":deepslate_lead_ore", NS + ":inscription_table"])
    merge_tag(os.path.join(tags, "needs_stone_tool.json"), [NS + ":lead_ore", NS + ":deepslate_lead_ore"])
    merge_tag(os.path.join(DATA, "c", "tags", "block", "ores", "lead.json"), [NS + ":lead_ore", NS + ":deepslate_lead_ore"])
    merge_tag(os.path.join(DATA, "c", "tags", "item", "ores", "lead.json"), [NS + ":lead_ore", NS + ":deepslate_lead_ore"])
    merge_tag(os.path.join(DATA, "c", "tags", "item", "raw_materials", "lead.json"), [NS + ":raw_lead"])
    merge_tag(os.path.join(DATA, "c", "tags", "item", "ingots", "lead.json"), [NS + ":lead_ingot"])

    # lead's vein, deep under the mountains (RealmSteels adds it to mountain biomes)
    write(os.path.join(DATA, NS, "worldgen", "feature", "ore_lead.json"), {
        "type": "minecraft:ore", "discard_chance_on_air_exposure": 0.3, "size": LEAD_VEIN, "targets": [
            {"state": NS + ":lead_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
            {"state": NS + ":deepslate_lead_ore",
             "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]})
    write(os.path.join(DATA, NS, "worldgen", "placed_feature", "ore_lead.json"), {"feature": NS + ":ore_lead", "placement": [
        {"type": "minecraft:count", "count": LEAD_PER_CHUNK},
        {"type": "minecraft:rarity_filter", "chance": LEAD_RARITY},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": LEAD_LOW},
                                                      "max_inclusive": {"absolute": LEAD_HIGH}}},
        {"type": "minecraft:biome"}]})

    # lead: smelted, and nuggets to ingots and back
    for kind, cook in (("smelting", 200), ("blasting", 100)):
        for src in ("raw_lead", "lead_ore", "deepslate_lead_ore"):
            recipe("lead_ingot_from_" + kind + "_" + src, {"type": "minecraft:" + kind, "category": "misc", "cookingtime": cook,
                                                            "experience": 0.7, "group": "lead_ingot", "ingredient": NS + ":" + src,
                                                            "result": {"id": NS + ":lead_ingot"}})
    recipe("lead_ingot_from_nuggets", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["###", "###", "###"],
                                       "key": {"#": NS + ":lead_nugget"}, "result": {"id": NS + ":lead_ingot", "count": 1}})
    recipe("lead_nuggets", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [NS + ":lead_ingot"],
                            "result": {"id": NS + ":lead_nugget", "count": 9}})
    # mail: nuggets hammered into rings, rings linked into sheets, sheets laced into mail
    recipe("mail_rings", {"type": NS + ":hammer_crafting", "category": "misc",
                          "ingredients": ["minecraft:iron_nugget"] * RINGS_PER_HAMMERING + ["#" + NS + ":hammers"],
                          "result": {"id": NS + ":mail_ring", "count": RINGS_PER_HAMMERING}})
    recipe("mail_sheet", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["###", "###", "###"],
                          "key": {"#": NS + ":mail_ring"}, "result": {"id": NS + ":mail_sheet", "count": 1}})
    for piece, pattern in SHEETS.items():
        recipe(piece + "_from_mail_sheets", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern,
                                             "key": {"S": NS + ":mail_sheet"}, "result": {"id": "minecraft:" + piece, "count": 1}})
    # the Inscription Table and the chisel
    recipe("inscription_table", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["ABA", "SSS", " S "],
                                 "key": {"A": "minecraft:amethyst_shard", "B": "minecraft:book", "S": "minecraft:smooth_stone"},
                                 "result": {"id": NS + ":inscription_table", "count": 1}})
    recipe("chisel", {"type": NS + ":hammer_crafting", "category": "equipment",
                      "ingredients": ["minecraft:iron_ingot", "minecraft:stick", "#" + NS + ":hammers"],
                      "result": {"id": NS + ":chisel", "count": 1}})

    # melting: lead ore and raw lead doubled, ash, mail rings and sheets (ingots melt as Tinkering.ALLOY_INGOTS)
    write(os.path.join(DATA, NS, "tinkering_melting", "medieval_smithing.json"), {"items": {
        NS + ":raw_lead": {"lead": ORE}, NS + ":lead_ore": {"lead": ORE}, NS + ":deepslate_lead_ore": {"lead": ORE},
        NS + ":lead_nugget": {"lead": NUGGET}, NS + ":ash": {"ash": ASH},
        NS + ":mail_ring": {"iron": NUGGET}, NS + ":mail_sheet": {"iron": INGOT}}})


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: gen_medieval_smithing.py <minecraft-client.jar>")
    with zipfile.ZipFile(sys.argv[1]) as jar:
        art(jar)
    models()
    data()
    write(os.path.join(ASSETS, "lang", "parts", "medieval_smithing.json"), LANG)


if __name__ == "__main__":
    main()
