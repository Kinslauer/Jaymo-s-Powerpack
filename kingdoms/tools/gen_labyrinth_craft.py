"""Art and item/block definitions for the labyrinth-builders' craft (kingdoms/.../beasts/LabyrinthCraft.java).

Writes: the item and block textures, the rune gate's blockstate and models, the item definitions, the brazier mimic's
skin, and the bombardier beetle (the crystal beetle's model with its crystals taken off, its shell repainted).
Run with `py kingdoms/tools/gen_labyrinth_craft.py` (needs Pillow); the output is committed.
"""
import colorsys
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "assets" / "jaymoskingdoms"
TABLETS = ["anthu", "kesh", "orrim"]
BRONZE = [(0x5E, 0x3A, 0x1C), (0x8C, 0x5A, 0x2B), (0xC2, 0x84, 0x3F), (0xE6, 0xB0, 0x5C)]
STONE = [(0x3A, 0x38, 0x3A), (0x52, 0x4F, 0x52), (0x6A, 0x66, 0x68), (0x80, 0x7B, 0x7C)]
AMBER = (0xFF, 0xB0, 0x30)


def write(path, data):
	path.parent.mkdir(parents=True, exist_ok=True)
	if isinstance(data, (dict, list)):
		path.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8")
	else:
		path.write_text(data, encoding="utf-8")


def img(size=16):
	return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def save(image, rel):
	path = RES / "textures" / rel
	path.parent.mkdir(parents=True, exist_ok=True)
	image.save(path)


def speckle(image, box, palette, seed):
	rng = random.Random(seed)
	x0, y0, x1, y1 = box
	for x in range(x0, x1):
		for y in range(y0, y1):
			image.putpixel((x, y), palette[rng.choice([0, 1, 1, 2, 2, 3])] + (255,))


# ---- glyphs: three runes of the old tongue, each a few strokes on a 8x10 field ----
GLYPHS = {
	"anthu": [((0, 9), (4, 0)), ((4, 0), (7, 9)), ((2, 5), (6, 5))],
	"kesh": [((1, 0), (1, 9)), ((1, 4), (6, 0)), ((1, 5), (6, 9))],
	"orrim": [((1, 0), (6, 0)), ((1, 0), (1, 9)), ((6, 0), (6, 9)), ((3, 3), (4, 6))],
}


def line(draw, a, b, ox, oy, color):
	draw.line([(a[0] + ox, a[1] + oy), (b[0] + ox, b[1] + oy)], fill=color + (255,))


def tablet(name):
	im = img()
	speckle(im, (2, 1, 14, 15), STONE, hash(name) & 0xFFFF)
	d = ImageDraw.Draw(im)
	d.rectangle((2, 1, 13, 14), outline=STONE[0] + (255,))
	for a, b in GLYPHS[name]:
		line(d, a, b, 4, 3, AMBER)
	save(im, f"item/rune_tablet_{name}.png")


def plate():
	im = img()
	d = ImageDraw.Draw(im)
	d.ellipse((1, 1, 14, 14), fill=BRONZE[2] + (255,), outline=BRONZE[0] + (255,))
	for x, y in [(7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)]:
		im.putpixel((x, y), BRONZE[1] + (255,))
	d.ellipse((5, 5, 10, 10), fill=BRONZE[0] + (255,), outline=BRONZE[1] + (255,))
	d.point([(3, 3), (4, 3), (3, 4)], fill=BRONZE[3] + (255,))
	save(im, "item/geared_plate.png")


def gland():
	im = img()
	d = ImageDraw.Draw(im)
	d.ellipse((4, 5, 11, 13), fill=(0xC8, 0x5A, 0x1E, 255), outline=(0x6A, 0x2A, 0x10, 255))
	d.ellipse((6, 7, 8, 9), fill=(0xFF, 0xC0, 0x60, 255))
	d.line([(7, 2), (8, 5)], fill=(0x3A, 0x2A, 0x20, 255))
	save(im, "item/beetle_gland.png")


def flamer():
	im = img()
	d = ImageDraw.Draw(im)
	d.line([(2, 13), (11, 4)], fill=BRONZE[2] + (255,), width=3)
	d.line([(2, 14), (11, 5)], fill=BRONZE[0] + (255,))
	d.rectangle((10, 2, 13, 5), fill=BRONZE[1] + (255,), outline=BRONZE[0] + (255,))
	d.ellipse((3, 8, 7, 12), fill=(0xC8, 0x5A, 0x1E, 255), outline=(0x6A, 0x2A, 0x10, 255))
	im.putpixel((14, 1), AMBER + (255,))
	im.putpixel((15, 0), (0xFF, 0xE0, 0x80, 255))
	save(im, "item/gland_flamer.png")


def afterblow():
	im = img()
	d = ImageDraw.Draw(im)
	d.line([(3, 12), (12, 3)], fill=BRONZE[2] + (255,), width=3)
	d.line([(4, 12), (13, 3)], fill=BRONZE[3] + (255,))
	d.line([(2, 13), (4, 11)], fill=BRONZE[0] + (255,), width=2)
	d.line([(1, 14), (3, 12)], fill=(0x4A, 0x30, 0x20, 255), width=2)
	# the second edge, a pale after-image
	d.line([(5, 10), (11, 4)], fill=AMBER + (255,))
	im.putpixel((14, 2), (0xFF, 0xE0, 0x90, 255))
	save(im, "item/afterblow.png")


def gate(lit):
	im = img()
	speckle(im, (0, 0, 16, 16), STONE, 77 + lit)
	d = ImageDraw.Draw(im)
	d.rectangle((0, 0, 15, 15), outline=BRONZE[0] + (255,))
	d.rectangle((1, 1, 14, 14), outline=BRONZE[2] + (255,))
	for i in range(3):
		x = 3 + i * 4
		on = i < lit
		d.rectangle((x, 6, x + 2, 9), fill=(AMBER if on else STONE[0]) + (255,), outline=BRONZE[1] + (255,))
	save(im, f"block/rune_gate_{lit}.png")


def gate_files():
	states = {}
	for a in (False, True):
		for k in (False, True):
			for o in (False, True):
				lit = int(a) + int(k) + int(o)
				states[f"anthu={str(a).lower()},kesh={str(k).lower()},orrim={str(o).lower()}"] = {"model": f"jaymoskingdoms:block/rune_gate_{lit}"}
	write(RES / "blockstates" / "rune_gate.json", {"variants": states})
	for lit in range(4):
		write(RES / "models" / "block" / f"rune_gate_{lit}.json",
			{"parent": "minecraft:block/cube_all", "textures": {"all": f"jaymoskingdoms:block/rune_gate_{lit}"}})
	write(RES / "items" / "rune_gate.json", {"model": {"type": "minecraft:model", "model": "jaymoskingdoms:block/rune_gate_0"}})


def flat_items(names):
	for name in names:
		write(RES / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}})
		write(RES / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"jaymoskingdoms:item/{name}"}})


def handheld_items(names):
	for name in names:
		write(RES / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"jaymoskingdoms:item/{name}"}})
		write(RES / "models" / "item" / f"{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": f"jaymoskingdoms:item/{name}"}})


def compass_item():
	"""The game's compass dispatch (32 frames round, pointing at the lodestone tracker's target), each frame tinted bronze."""
	tint = [{"type": "minecraft:constant", "value": 0xC8A060}]
	entries = [{"model": {"type": "minecraft:model", "model": f"minecraft:item/compass_{(16 + i) % 32:02d}", "tints": tint},
		"threshold": 0.0 if i == 0 else i - 0.5} for i in range(33)]
	write(RES / "items" / "labyrinth_compass.json", {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:compass",
		"target": "lodestone", "scale": 32.0, "entries": entries}})


# ---- the brazier mimic: a stone-footed iron bowl of fire, 64x64 (BrazierMimicModel) ----
def brazier_skin():
	im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	speckle(im, (0, 0, 48, 15), STONE, 1)  # base
	speckle(im, (0, 16, 24, 30), STONE, 2)  # stem
	iron = [(0x1E, 0x1E, 0x22), (0x2C, 0x2C, 0x32), (0x3A, 0x3A, 0x42), (0x4A, 0x44, 0x40)]
	speckle(im, (0, 32, 56, 51), iron, 3)  # bowl
	rng = random.Random(4)
	for x in range(24, 56):  # fire
		for y in range(16, 30):
			heat = 1.0 - (y - 16) / 14.0
			col = (0xFF, int(0x60 + 0xA0 * heat + rng.randint(-14, 14)), int(0x10 + 0x50 * heat * heat))
			im.putpixel((x, y), tuple(max(0, min(255, c)) for c in col) + (255,))
	speckle(im, (0, 52, 8, 57), [(0xD8, 0xD0, 0xB8), (0xC4, 0xBC, 0xA4), (0xE8, 0xE0, 0xCC), (0xB0, 0xA8, 0x90)], 5)  # teeth
	speckle(im, (10, 52, 22, 59), STONE, 6)  # legs
	im.save(RES / "textures" / "entity" / "legend" / "brazier_mimic.png")


# ---- the bombardier beetle: the crystal beetle's body, its crystals taken off and its shell repainted ----
def bombardier():
	src = RES / "textures" / "entity" / "bb" / "crystal_beetle.png"
	skin = Image.open(src).convert("RGBA")
	stops = [(0.0, (0x14, 0x18, 0x26)), (0.45, (0x6A, 0x2E, 0x14)), (0.75, (0xC0, 0x62, 0x1E)), (1.0, (0xF0, 0xA0, 0x40))]
	out = skin.copy()
	for x in range(skin.width):
		for y in range(skin.height):
			r, g, b, a = skin.getpixel((x, y))
			if a == 0:
				continue
			v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[2]
			for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
				if v <= t1:
					f = (v - t0) / (t1 - t0) if t1 > t0 else 0
					out.putpixel((x, y), tuple(int(c0[i] + (c1[i] - c0[i]) * f) for i in range(3)) + (a,))
					break
	out.save(RES / "textures" / "entity" / "bb" / "bombardier_beetle.png")
	model = json.loads((RES / "bbmodels" / "crystal_beetle.json").read_text(encoding="utf-8"))
	for bone in model["bones"]:
		if bone["name"] == "crystals":
			bone["quads"] = []
	(RES / "bbmodels" / "bombardier_beetle.json").write_text(json.dumps(model, separators=(",", ":")), encoding="utf-8")


def main():
	for name in TABLETS:
		tablet(name)
	plate()
	gland()
	flamer()
	afterblow()
	for lit in range(4):
		gate(lit)
	gate_files()
	flat_items(["geared_plate", "beetle_gland"] + [f"rune_tablet_{n}" for n in TABLETS])
	handheld_items(["afterblow", "gland_flamer"])
	compass_item()
	brazier_skin()
	bombardier()


if __name__ == "__main__":
	main()
