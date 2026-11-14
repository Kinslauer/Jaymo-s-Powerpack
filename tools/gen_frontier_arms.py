"""
The Forge Hammer, the Fire Cannon and the Field Mortar: models and art from cleannrooster's Frontier Armaments (MIT,
tools/bbmodels/frontier-armaments/), made into ours.

- Forge Hammer (Powers, tinkering/smithing/ForgeHammers): the Forge Mace's model as a 3D item model, laid on the
  sprite's diagonal so vanilla's hand-held placements fit it. Iron and netherite wear the maker's sheets; copper and
  steel are the iron sheet with its greys turned to our own ramps (the handle's leather left alone). Its Forge Hammer
  Head (tinkering/WeaponParts) is drawn here.
- Fire Cannon (Kingdoms, FireCannon): the musketoon's model (tools/gen_guns_geckolib.py) wearing the maker's red sheet,
  its breech gem kept, no ramrod. In a hand, Powers' animated gun draws it (held_guns), as it does the musketoon.
- Field Mortar (Kingdoms, FieldMortar): the Mortar's sheet for the entity (its model is built in code, the maker's boxes),
  with the faces nobody painted painted; the mortar's and the shell's icons are drawn here.

Run from the project root:  python -I tools/gen_frontier_arms.py   (after tools/gen_guns_geckolib.py)
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_guns_geckolib as g  # noqa: E402
import weapon_parts_assets as wpa  # noqa: E402

SRC = os.path.join(g.BBMODELS, "frontier-armaments")
CREDIT = "Frontier Armaments by cleannrooster (MIT)"
R2 = 2 ** -0.5
# the hammer's shaft up the sprite's diagonal, its head across it, its thin side out of the sprite
UPRIGHT = ((0.0, R2, -R2), (0.0, R2, R2), (1.0, 0.0, 0.0))
HAMMER_PIVOT, HAMMER_CENTRE, HAMMER_SCALE = (0.0, 6.8, -0.44), (7.4, 7.4, 8.0), 1.05
RAMPS = {"copper": wpa.DRAWN_COLOURS["copper"], "steel": wpa.DRAWN_COLOURS["steel"]}
METALS = {"copper": "Copper", "iron": "Iron", "steel": "Steel", "netherite": "Netherite"}


def ramp(colours, t):
	"""Dark, middle, shine at t = 0, 0.55, 1."""
	a, b, k = (colours[0], colours[1], t / 0.55) if t < 0.55 else (colours[1], colours[2], (t - 0.55) / 0.45)
	return tuple(round(a[i] + (b[i] - a[i]) * k) for i in range(3))


def hammer_sheet(metal):
	if metal in ("iron", "netherite"):
		return Image.open(os.path.join(SRC, f"forgemace_{metal}.png")).convert("RGBA")
	im = Image.open(os.path.join(SRC, "forgemace_iron.png")).convert("RGBA")
	px = im.load()
	for y in range(im.height):
		for x in range(im.width):
			r, gr, b, a = px[x, y]
			if a and max(r, gr, b) - min(r, gr, b) < 12:
				# (the maker's greys run from 94 to 255)
				px[x, y] = ramp(RAMPS[metal], max(0.0, min(1.0, (r - 80) / 175))) + (a,)
	return im


def forge_hammers():
	geo = json.load(open(os.path.join(SRC, "forgemace.geo.json"), encoding="utf-8"))["minecraft:geometry"][0]
	cubes = [c for b in geo["bones"] for c in b.get("cubes", [])]
	lang = {"item.jaymospowers.forge_hammer_head": "Forge Hammer Head",
		"tooltip.jaymospowers.forge_hammer": "A smith's sidearm. Breaks stone like a pickaxe."}
	for metal, name in METALS.items():
		parts = [recentred(g.frontier_cube(c, (0, 0, 0), (0, 0, 0), 1.0)) for c in cubes]
		data = g.project("forge_hammer", [g.group("hammer", (0, 0, 0), parts)], [g.texture_entry("forge_hammer", hammer_sheet(metal))], (32, 32))
		elements, atlas = g.freeform_convert(data, UPRIGHT, HAMMER_PIVOT, HAMMER_CENTRE, HAMMER_SCALE)
		item = metal + "_forge_hammer"
		atlas.save(os.path.join(g.ASSETS, "textures", "item", "tinkering", "smithing", item + ".png"))
		texture = "jaymospowers:item/tinkering/smithing/" + item
		g.write(os.path.join(g.ASSETS, "models", "item", "tinkering", "smithing", item + ".json"), {
			"credit": "Forge Mace, " + CREDIT, "parent": "minecraft:item/handheld", "gui_light": "front",
			"textures": {"0": texture, "particle": texture}, "elements": elements})
		g.write(os.path.join(g.ASSETS, "items", item + ".json"),
			{"model": {"type": "minecraft:model", "model": "jaymospowers:item/tinkering/smithing/" + item}}, 2)
		g.write(os.path.join(g.ROOT, "src", "main", "resources", "data", "jaymospowers", "weapon_attributes", item + ".json"),
			{"parent": "jaymospowers:combo_mace"}, 2)
		lang["item.jaymospowers." + item] = name + " Forge Hammer"
	head_part("forge_hammer_head", hammer_head, ("copper", "iron", "steel"))
	g.write(os.path.join(g.ASSETS, "lang", "parts", "forge_hammer.json"), lang, 2)


def recentred(e):
	"""A cube turned about x on a far-off pivot, as the same cube turned about its own middle (an item model's boxes
	must lie near the item)."""
	if not e.get("rotation") or e["rotation"][1] or e["rotation"][2]:
		return e
	a = math.radians(e["rotation"][0])
	o, c = e["origin"], [(e["from"][i] + e["to"][i]) / 2 for i in range(3)]
	y, z = c[1] - o[1], c[2] - o[2]
	to = [c[0], o[1] + y * math.cos(a) - z * math.sin(a), o[2] + y * math.sin(a) + z * math.cos(a)]
	e["from"] = [e["from"][i] + to[i] - c[i] for i in range(3)]
	e["to"] = [e["to"][i] + to[i] - c[i] for i in range(3)]
	e["origin"] = to
	return e


def hammer_head(metal):
	"""A square-faced head lying on the diagonal, its eye (the handle's hole) in the middle: 16 by 16."""
	dark, mid, shine = wpa.DRAWN_COLOURS[metal]
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y in range(16):
		for x in range(16):
			# along the head (u) and across it (v), in pixels from its middle
			u, v = ((x - 7.5) - (y - 7.5)) * R2, ((x - 7.5) + (y - 7.5)) * R2
			if abs(u) <= 6.6 and abs(v) <= 3.3:
				edge = abs(u) > 5.6 or abs(v) > 2.4
				colour = dark if edge else shine if v < -1.0 and abs(u) < 5.0 else mid
				if abs(u) < 1.3 and abs(v) < 1.3:
					colour = (24, 22, 22)
				img.putpixel((x, y), colour + (255,))
	return img


def head_part(part, picture, metals):
	"""A forged part's pictures, models and item definition, as tools/weapon_parts_assets.py writes them: stone and each
	metal by custom_model_data, orange while hot."""
	cases = []
	for metal in ("stone",) + tuple(metals):
		image = wpa.as_stone(picture(metals[0])) if metal == "stone" else picture(metal)
		path = wpa.OUT + part + "_" + metal
		image.save(os.path.join(wpa.ASSETS, "textures", path + ".png"))
		wpa.write(os.path.join(wpa.ASSETS, "models", path + ".json"),
			{"parent": "minecraft:item/generated", "textures": {"layer0": "jaymospowers:" + path}})
		cases.append({"when": metal, "model": {"type": "minecraft:model", "model": "jaymospowers:" + path}})
	wpa.write(os.path.join(wpa.ASSETS, "items", part + ".json"), {"model": wpa.hot({
		"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
		"fallback": cases[0]["model"]})})


def fire_cannon():
	data = g.musketoon()
	ruby = Image.open(os.path.join(SRC, "firecannon_ruby.png")).convert("RGBA")
	data["textures"][0] = g.texture_entry("fire_cannon", ruby)
	elements, atlas = g.freeform_convert(data, g.LONG_GUN, g.MUSKETOON_PIVOT, g.MUSKETOON_CENTRE, g.MUSKETOON_SCALE, drop=("flash", "ramrod"))
	atlas.save(os.path.join(g.KINGDOMS, "textures", "item", "fire_cannon.png"))
	base = json.load(open(os.path.join(g.KINGDOMS, "models", "item", "musketoon.json"), encoding="utf-8"))
	g.write(os.path.join(g.KINGDOMS, "models", "item", "fire_cannon.json"), {
		"credit": "Firecannon, " + CREDIT, "gui_light": "front",
		"textures": {"0": "jaymoskingdoms:item/fire_cannon", "particle": "jaymoskingdoms:item/fire_cannon"}, "elements": elements,
		"display": base["display"]})
	still = {"type": "minecraft:model", "model": "jaymoskingdoms:item/fire_cannon"}
	g.write(os.path.join(g.KINGDOMS, "items", "fire_cannon.json"), {"model": still}, 2)
	# the animated gun's sheet: the musketoon's (its flash and ramrod rows kept), the red sheet over the gun's own
	sheet = Image.open(os.path.join(g.ASSETS, "textures", "entity", "gun_musketoon.png")).convert("RGBA")
	sheet.paste(ruby, (0, 0))
	sheet.save(os.path.join(g.ASSETS, "textures", "entity", "gun_fire_cannon.png"))
	gun = lambda pose: g.animated("musketoon", "jaymoskingdoms:item/fire_cannon", g.MUSKETOON_FRAME,
		texture="jaymospowers:textures/entity/gun_fire_cannon.png", hide=["ramrod"], pose=pose)
	g.write(os.path.join(g.HELD, "jaymoskingdoms", "items", "fire_cannon.json"), {"model": {"type": "minecraft:select",
		"property": "minecraft:display_context", "cases": [{"when": ["gui", "ground", "fixed"], "model": still}],
		"fallback": {"type": "minecraft:select", "property": "minecraft:charge_type",
			"cases": [{"when": ["arrow", "rocket"], "model": gun("loaded")}],
			"fallback": {"type": "minecraft:condition", "property": "minecraft:using_item", "on_true": gun("reload"),
				"on_false": gun("spent")}}}}, 2)


def mortar_icon():
	"""The mortar from its side: a squat grey tube leaning back on a crossed stand with dark feet."""
	rows = [
		"................",
		"..........oo....",
		".........ollo...",
		"........olmlo...",
		".......olmmo....",
		"......olmmo.....",
		".....olmmo......",
		"....olmmo.......",
		"....ommo........",
		"...obbbo........",
		"...obbbo........",
		"..osssssssso....",
		".ossssssssssso..",
		".kk....ss...kk..",
		".kk....kk...kk..",
		"................"]
	colours = {"o": (44, 46, 52), "l": (214, 216, 220), "m": (150, 152, 158), "b": (104, 78, 56), "s": (112, 114, 120), "k": (28, 28, 32)}
	return picture(rows, colours)


def shell_icon():
	"""A mortar shell: a dark iron ball, a brass band round it, a short fuse."""
	rows = [
		"................",
		"...........y....",
		"..........f.....",
		".........f......",
		"......oooo......",
		".....ollmmo.....",
		"....olmmmmdo....",
		"....ommmmmdo....",
		"....obbbbbbo....",
		"....ommmmddo....",
		"....omddddo.....",
		".....oddddo.....",
		"......oooo......",
		"................",
		"................",
		"................"]
	colours = {"o": (30, 32, 38), "l": (168, 172, 180), "m": (96, 100, 110), "d": (62, 66, 76), "b": (196, 150, 62), "f": (120, 98, 70),
		"y": (250, 200, 80)}
	return picture(rows, colours)


def picture(rows, colours):
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y, row in enumerate(rows):
		for x, c in enumerate(row):
			if c in colours:
				img.putpixel((x, y), colours[c] + (255,))
	return img


def mortar():
	sheet = g.paint_unpainted(Image.open(os.path.join(SRC, "mortar.png")).convert("RGBA"))
	sheet.save(os.path.join(g.KINGDOMS, "textures", "entity", "field_mortar.png"))
	for name, icon in (("field_mortar", mortar_icon()), ("mortar_shell", shell_icon())):
		icon.save(os.path.join(g.KINGDOMS, "textures", "item", name + ".png"))
		g.write(os.path.join(g.KINGDOMS, "models", "item", name + ".json"),
			{"parent": "minecraft:item/generated", "textures": {"layer0": "jaymoskingdoms:item/" + name}})
		g.write(os.path.join(g.KINGDOMS, "items", name + ".json"),
			{"model": {"type": "minecraft:model", "model": "jaymoskingdoms:item/" + name}}, 2)


if __name__ == "__main__":
	forge_hammers()
	fire_cannon()
	mortar()
