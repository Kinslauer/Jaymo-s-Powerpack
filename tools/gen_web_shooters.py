"""Generates the web shooter assets (0.12.3): the Web Shooter (a pair of silver wrist shooters) and Web Cartridge item
textures and their item model files, the worn shooters' entity texture (cuff, nozzle, cartridge boxes of
client/power/web/WebShootersClient), the braided web line's strand texture, and the two crafting recipes.
Hand-drawn pixel grids and simple formulas, our own art (the idea is from a Spider-Man mod video; nothing copied).
Run from the project root:  python -I tools/gen_web_shooters.py
"""
import json
from pathlib import Path

from PIL import Image

PROJECT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"
ROOT = PROJECT / "assets" / "jaymospowers"
DATA = PROJECT / "data" / "jaymospowers"

PALETTE = {
    ".": (0, 0, 0, 0),
    "d": (46, 50, 60, 255),     # outline
    "o": (84, 90, 104, 255),    # deep steel
    "g": (124, 130, 144, 255),  # shadowed steel
    "s": (172, 178, 190, 255),  # steel
    "h": (222, 228, 236, 255),  # bright steel
    "b": (252, 253, 255, 255),  # glint
    "n": (30, 32, 38, 255),     # nozzle bore
    "r": (224, 48, 58, 255),    # the power's red: the trigger pad
    "w": (246, 248, 252, 255),  # web fluid
    "f": (214, 222, 236, 255),  # web fluid, in shade
    "c": (170, 200, 220, 150),  # glass
}

# the pair: two upright cuffs, a red trigger pad and a fluid window on each, the nozzle under it
SHOOTER = [
    "................",
    "................",
    "..dddd....dddd..",
    ".dbhhsd..dbhhsd.",
    ".dhssgd..dhssgd.",
    ".dhrrgd..dhrrgd.",
    ".dhssgd..dhssgd.",
    ".dswfgd..dswfgd.",
    ".dswfgd..dswfgd.",
    ".dhssgd..dhssgd.",
    ".dgggod..dgggod.",
    "..dond....dond..",
    "...dd......dd...",
    "................",
    "................",
    "................",
]

# one cartridge: a glass tube of web fluid between two steel caps
CARTRIDGE = [
    "................",
    "......dddd......",
    ".....dbhsgd.....",
    ".....dddddd.....",
    ".....dcwwfd.....",
    ".....dcwwfd.....",
    ".....dcwffd.....",
    ".....dcwffd.....",
    ".....dcwwfd.....",
    ".....dcwwfd.....",
    ".....dddddd.....",
    ".....dhssgd.....",
    "......dnnd......",
    ".......dd.......",
    "................",
    "................",
]


def draw(grid):
    img = Image.new("RGBA", (16, 16))
    for y, row in enumerate(grid):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            img.putpixel((x, y), PALETTE[ch])
    return img


def item(name, grid):
    tex = ROOT / "textures" / "item" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    draw(grid).save(tex)
    model = ROOT / "models" / "item" / f"{name}.json"
    model.write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymospowers:item/{name}"}}, indent=2) + "\n")
    definition = ROOT / "items" / f"{name}.json"
    definition.write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"jaymospowers:item/{name}"}}, indent=2) + "\n")


def fill_box(img, u, v, w, h, d, side, top, bottom):
    """Paints a model box's texture (the vanilla cube layout from (u, v)): side(x, y) for the four sides, top/bottom flat."""
    for x in range(d, d + w):
        for y in range(0, d):
            img.putpixel((u + x, v + y), top)
            img.putpixel((u + x + w, v + y), bottom)
    for x in range(0, 2 * (w + d)):
        for y in range(h):
            img.putpixel((u + x, v + d + y), side(x, y))


def worn_texture():
    """The worn shooters (32x32): a 5x3x5 cuff, a 2x2x2 nozzle and a 2x3x2 cartridge."""
    img = Image.new("RGBA", (32, 32))
    steel = PALETTE["s"]

    def cuff(x, y):
        # bevelled bands: bright along the top edge, a dark seam at the bottom, a red trigger pad on one face
        if y == 0:
            return PALETTE["h"]
        if y == 2:
            return PALETTE["g"]
        if 6 <= x <= 8:
            return PALETTE["r"]
        return PALETTE["b"] if x % 5 == 1 else steel
    fill_box(img, 0, 0, 5, 3, 5, cuff, PALETTE["g"], PALETTE["o"])

    def nozzle(x, y):
        return PALETTE["o"] if y == 0 else PALETTE["g"]
    fill_box(img, 0, 10, 2, 2, 2, nozzle, PALETTE["g"], PALETTE["n"])

    def cartridge(x, y):
        return PALETTE["h"] if y == 0 else PALETTE["w"] if x % 4 != 3 else PALETTE["f"]
    fill_box(img, 0, 16, 2, 3, 2, cartridge, PALETTE["h"], PALETTE["g"])
    path = ROOT / "textures" / "entity" / "web_shooter.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)


def rope_texture():
    """One strand of the braided web line (16x16): across it (u) solid in the middle and see-through at the edges,
    along it (v) fine fibres twisting round it, repeating every 4 pixels so any quarter of it tiles."""
    alpha = [40, 95, 160, 210, 236, 248, 255, 255, 255, 255, 248, 236, 210, 160, 95, 40]
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            fibre = (x + y) % 4 == 0
            shade = 214 if fibre else 252 if (x + y) % 4 == 2 else 240
            # a little cooler toward the edges, as it rounds away
            edge = abs(x - 7.5) / 7.5
            img.putpixel((x, y), (int(shade - 14 * edge), int(shade - 10 * edge), int(min(255, shade + 2)), alpha[x]))
    img.save(ROOT / "textures" / "entity" / "web_rope.png")


def recipes():
    out = DATA / "recipe"
    out.mkdir(parents=True, exist_ok=True)
    (out / "web_shooter.json").write_text(json.dumps({
        "type": "minecraft:crafting_shaped",
        "category": "equipment",
        "key": {"I": "minecraft:iron_ingot", "R": "minecraft:redstone", "S": "minecraft:string"},
        "pattern": ["I I", "RSR", "I I"],
        "result": {"id": "jaymospowers:web_shooter"},
    }, indent=2) + "\n")
    (out / "web_cartridge.json").write_text(json.dumps({
        "type": "minecraft:crafting_shapeless",
        "category": "equipment",
        "ingredients": ["minecraft:string", "minecraft:string", "minecraft:slime_ball", "minecraft:glass_bottle"],
        "result": {"id": "jaymospowers:web_cartridge"},
    }, indent=2) + "\n")


if __name__ == "__main__":
    item("web_shooter", SHOOTER)
    item("web_cartridge", CARTRIDGE)
    worn_texture()
    rope_texture()
    recipes()
