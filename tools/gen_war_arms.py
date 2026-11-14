"""Draws and writes the armoured wars' arms (src/main/java/com/jaymospowers/armory/WarArms.java) and the forged parts of
those and of Kingdoms' early guns: the bec de corbin, the war dart, the heavy crossbow (and its winding and loaded
pictures), the Bec de Corbin Head, War Dart Heads, Heavy Crossbow Prod, Handgonne Barrel and Arquebus Barrel. Our own
art, drawn here from shapes laid along the sprite's diagonal, in the pack's palette (outline, body, shine); with every
model, item definition, Better Combat file, tag line and lang key they need (lang part war_arms.json, which also holds
the smithing lines of the same patch: the botched forging and the patterns). Run it from the root:

    python -I tools/gen_war_arms.py

The parts' ids must match the Kinds added to tinkering/WeaponParts.
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import gen_gear_picks as gear  # noqa: E402

RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
MCDATA = os.path.join(RES, "data", "minecraft")
ARMORY = "item/armory/"

rgb = gear.rgb
WOOD = [rgb("b08a52"), rgb("896727"), rgb("5a4218")]
DARK_WOOD = [rgb("8a5a34"), rgb("68442a"), rgb("42281a")]
IRON = [rgb("e2e4e6"), rgb("a8acb6"), rgb("5c5e66")]
LEAD = [rgb("9aa2b0"), rgb("687080"), rgb("3a3e4a")]
FEATHER = [rgb("f2f2f2"), rgb("cfcfcf"), rgb("8c8c8c")]
STEEL = [rgb("c4c8d2"), rgb("868a96"), rgb("44464e")]
STRING = [rgb("d8d8d0")] * 3
BOLT = [rgb("e8d8a8"), rgb("c8b078"), rgb("8a7444")]


def diagonal(size, x0, y0, shapes):
	"""A picture drawn from shapes laid along the sprite's diagonal. Each pixel gets a (along, across): `along` counts
	diagonal steps from (x0, y0) toward the top right, `across` toward the top left. `shapes` is a list of (test, ramp):
	the last shape whose test(along, across) holds paints the pixel. Edges are darkened (the ramp's last colour), and a
	pixel with nothing up-left of it takes the ramp's first (the shine)."""
	kind = {}
	for y in range(size):
		for x in range(size):
			a, b = ((x - x0) - (y - y0)) / 2, (-(x - x0) - (y - y0)) / 2
			for test, ramp in shapes:
				if test(a, b):
					kind[(x, y)] = ramp
	img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
	for (x, y), ramp in kind.items():
		edge = any((x + dx, y + dy) not in kind for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
		lit = (x - 1, y) not in kind or (x, y - 1) not in kind
		img.putpixel((x, y), ramp[2] if edge and not lit else ramp[0] if lit else ramp[1])
	return img


def rows_of(shapes, x0, y0):
	"""The same drawing as a Tool Station part's 16 rows: '#' outline, 'x' metal, 'o' shine."""
	filled = set()
	for y in range(16):
		for x in range(16):
			a, b = ((x - x0) - (y - y0)) / 2, (-(x - x0) - (y - y0)) / 2
			if any(test(a, b) for test in shapes):
				filled.add((x, y))
	edge = {(x, y) for x, y in filled if any((x + dx, y + dy) not in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
	rows = []
	for y in range(16):
		row = ""
		for x in range(16):
			if (x, y) not in filled:
				row += "."
			elif (x, y) in edge:
				row += "#"
			else:
				# (the shine runs along the inside of the top and left edges)
				row += "o" if (x - 1, y) in edge or (x, y - 1) in edge else "x"
		rows.append(row)
	return rows


# ---- the weapons ----

def bec_head(a0, k):
	"""The bec de corbin's head from `a0` along, `k` times life size: langets, the block, the hammer and its face to the
	top left, the crow's beak curving back to the bottom right, the spike on top."""
	return [
		lambda a, b: a0 - 3 * k <= a <= a0 and abs(b) <= 1.0,
		lambda a, b: a0 <= a <= a0 + 4 * k and abs(b) <= 1.5 * k,
		lambda a, b: a0 + 0.5 * k <= a <= a0 + 3.5 * k and 1.5 * k <= b <= 4.5 * k,
		lambda a, b: a0 - 0.5 * k <= a <= a0 + 4.5 * k and 3.5 * k <= b <= 5.0 * k,
		lambda a, b: -6.0 * k <= b <= -1.5 * k and abs(a - (a0 + 2 * k - 0.07 * b * b / k)) <= 1.5 * k * (1 - (abs(b) - 1.5 * k) / (4.8 * k)),
		lambda a, b: a0 + 4 * k <= a <= a0 + 9.5 * k and abs(b) <= 1.1 * k * (a0 + 9.5 * k - a) / (5.5 * k) + 0.2,
	]


def bec_de_corbin():
	shapes = [(lambda a, b: 0 <= a <= 17 and abs(b) <= 0.5, WOOD)] + [(test, STEEL) for test in bec_head(15.5, 1.3)]
	return diagonal(32, 2, 29, shapes)


def war_dart():
	return diagonal(16, 1, 14, [
		(lambda a, b: 0 <= a <= 3 and abs(b) <= 1.5 - a * 0.3, FEATHER),
		(lambda a, b: 1.5 <= a <= 7 and abs(b) <= 0.25, WOOD),
		(lambda a, b: 6 <= a <= 8.5 and abs(b) <= 1.0, LEAD),
		(lambda a, b: 8.5 <= a <= 13 and abs(b) <= 1.3 * (13 - a) / 4.5 + 0.1, IRON),
	])


def prod_line(b, at=10.8, bend=0.055):
	"""Where the crossbow's steel prod lies (how far along) at `b` across."""
	return at - bend * b * b


def heavy_crossbow(pull, crank, bolt):
	"""The heavy crossbow: its tiller, the steel prod across its nose, a stirrup, the crank at its butt (`crank`: which
	side its handle is on), the string drawn back `pull` steps from rest, and (`bolt`) a bolt in the groove."""
	nut = 8.8 - pull

	def string(a, b):
		# two straight runs from the limb tips to the nut
		return abs(b) <= 5.6 and abs(a - (nut + (prod_line(5.6) - nut) * abs(b) / 5.6)) <= 0.26

	shapes = [
		(lambda a, b: 0 <= a <= 11 and abs(b) <= 0.5, DARK_WOOD),
		(lambda a, b: 0 <= a <= 2.5 and abs(b) <= 1.0, DARK_WOOD),
		(lambda a, b: 11.5 <= a <= 13.5 and abs(b) <= 1.0 and not (12 <= a <= 13 and abs(b) <= 0.25), IRON),
		(string, STRING),
		(lambda a, b: abs(b) <= 6 and abs(a - prod_line(b)) <= 0.55, STEEL),
		(lambda a, b: 0.5 <= a <= 2 and 1.0 <= b * crank <= 2.6, IRON),
		(lambda a, b: 1.5 <= a <= 3.0 and 2.1 <= b * crank <= 3.2, LEAD),
	]
	if bolt:
		shapes.append((lambda a, b: nut <= a <= 11.4 and abs(b) <= 0.25, BOLT))
		shapes.append((lambda a, b: 11.4 <= a <= 13.0 and abs(b) <= 0.5, IRON))
	return diagonal(16, 1, 14, shapes)


# ---- the Tool Station parts ----

PARTS = {
	"bec_de_corbin_head": ("Bec de Corbin Head", lambda: rows_of(bec_head(3.0, 0.72), 3, 12)),
	"war_dart_heads": ("War Dart Heads", lambda: rows_of([
		(lambda a, b, k=k: 2 <= a <= 4 and abs(b - k) <= 0.9) for k in (-2.5, 0.5, 3.5)] + [
		(lambda a, b, k=k: 4 <= a <= 9.5 + k * 0.4 and abs(b - k) <= 1.0 * (9.5 + k * 0.4 - a) / 5.5 + 0.1) for k in (-2.5, 0.5, 3.5)], 2, 13)),
	"heavy_crossbow_prod": ("Heavy Crossbow Prod", lambda: rows_of([
		lambda a, b: abs(b) <= 6.2 and abs(a - prod_line(b, 7.5, 0.07)) <= 0.8 + 0.3 * (1 - abs(b) / 6.2),
		lambda a, b: 6.2 <= a <= 9.6 and abs(b) <= 1.0], 1, 14)),
	"handgonne_barrel": ("Handgonne Barrel", lambda: rows_of([
		lambda a, b: 1.5 <= a <= 11.5 and abs(b) <= 1.5,
		lambda a, b: (1.5 <= a <= 2.5 or 6.0 <= a <= 6.8 or 10.4 <= a <= 11.5) and abs(b) <= 2.0,
		lambda a, b: 0 <= a <= 1.5 and abs(b) <= 1.0], 1, 14)),
	"arquebus_barrel": ("Arquebus Barrel", lambda: rows_of([
		lambda a, b: 0.5 <= a <= 12.5 and abs(b) <= 0.8,
		lambda a, b: 11.7 <= a <= 12.5 and abs(b) <= 1.3,
		lambda a, b: 0.5 <= a <= 2.6 and abs(b) <= 1.3,
		lambda a, b: 2.6 <= a <= 3.8 and -2.2 <= b <= -0.8], 1, 14)),
}


def write(path, data):
	gear.write(path, data)


def armory_item(item, img, parent="minecraft:item/handheld", display=None):
	rel = ARMORY + item
	gear.save(img, rel)
	model = {"parent": parent, "textures": {"layer0": "jaymospowers:" + rel}}
	if display:
		model["display"] = display
	write(os.path.join(ASSETS, "models", rel + ".json"), model)
	return {"type": "minecraft:model", "model": "jaymospowers:" + rel}


def merge_tag(path, values):
	old = json.load(open(path)) if os.path.exists(path) else {"values": []}
	for value in values:
		if value not in old["values"]:
			old["values"].append(value)
	write(path, old)


def main():
	# the bec de corbin: drawn long, held as the glaive is
	glaive = json.load(open(os.path.join(ASSETS, "models", "item", "armory", "iron_glaive.json")))
	write(os.path.join(ASSETS, "items", "bec_de_corbin.json"), {"model": armory_item("bec_de_corbin", bec_de_corbin(), display=glaive["display"])})
	write(os.path.join(ASSETS, "items", "war_dart.json"), {"model": armory_item("war_dart", war_dart())})
	# the heavy crossbow: at rest, three steps of the winding (the crank's handle going round), and spanned with its bolt
	bow = "minecraft:item/crossbow"
	rest = armory_item("heavy_crossbow", heavy_crossbow(0.0, -1, False), bow)
	winding = [armory_item("heavy_crossbow_winding_%d" % i, heavy_crossbow(pull, crank, False), bow)
		for i, (pull, crank) in enumerate(((1.6, 1), (3.2, -1), (4.8, 1)))]
	spanned = armory_item("heavy_crossbow_spanned", heavy_crossbow(4.8, -1, True), bow)
	# (the winding is three times a crossbow's 25 ticks: armory/HeavyCrossbowItem.WIND)
	write(os.path.join(ASSETS, "items", "heavy_crossbow.json"), {"model": {
		"type": "minecraft:select", "property": "minecraft:charge_type",
		"cases": [{"when": ["arrow", "rocket"], "model": spanned}],
		"fallback": {"type": "minecraft:condition", "property": "minecraft:using_item", "on_false": rest,
			"on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 1 / 75,
				"entries": [{"threshold": 0.4, "model": winding[1]}, {"threshold": 0.8, "model": winding[2]}], "fallback": winding[0]}}}})
	# Better Combat: the bec swings like a two-handed hammer on a pole (overhead blows, then a thrust of the spike);
	# the dart jabs like a dagger
	hammer = lambda animation, mult, sound: {"hitbox": "VERTICAL_PLANE", "damage_multiplier": mult, "angle": 60, "upswing": 0.5,
		"animation": animation, "swing_sound": {"id": sound}}
	write(os.path.join(DATA, "weapon_attributes", "bec_de_corbin.json"), {"attributes": {
		"range_bonus": 0.5, "two_handed": True, "pose": "bettercombat:pose_two_handed_polearm", "category": "hammer",
		"attacks": [hammer("bettercombat:two_handed_slam_heavy", 1.0, "bettercombat:hammer_slam"),
			{"hitbox": "FORWARD_BOX", "damage_multiplier": 0.85, "angle": 0, "upswing": 0.5,
				"animation": "bettercombat:two_handed_stab_right", "swing_sound": {"id": "bettercombat:spear_stab"}},
			hammer("bettercombat:two_handed_slam_heavy", 1.25, "bettercombat:hammer_slam")]}})
	write(os.path.join(DATA, "weapon_attributes", "war_dart.json"), {"parent": "jaymospowers:combo_dagger"})
	# tags: the bec breaks bones and takes a weapon's enchantments; the crossbow takes a crossbow's
	merge_tag(os.path.join(DATA, "tags", "item", "crushing.json"), ["jaymospowers:bec_de_corbin"])
	merge_tag(os.path.join(MCDATA, "tags", "item", "enchantable", "melee_weapon.json"), ["jaymospowers:bec_de_corbin"])
	merge_tag(os.path.join(MCDATA, "tags", "item", "enchantable", "durability.json"), ["jaymospowers:bec_de_corbin", "jaymospowers:heavy_crossbow"])
	merge_tag(os.path.join(MCDATA, "tags", "item", "enchantable", "crossbow.json"), ["jaymospowers:heavy_crossbow"])
	lang = {
		"item.jaymospowers.bec_de_corbin": "Bec de Corbin",
		"item.jaymospowers.bec_de_corbin.hint": "A hammer and a crow's beak on a pole. Knocks a raised shield aside.",
		"item.jaymospowers.war_dart": "War Dart",
		"item.jaymospowers.war_dart.hint": "Hold to draw back, release to throw. Pick it up where it lands.",
		"item.jaymospowers.heavy_crossbow": "Heavy Crossbow",
		"item.jaymospowers.heavy_crossbow.hint": "Wound back with its crank: slow to span, and its bolt goes through the first body it strikes.",
		"weapon_kind.jaymospowers.polehammer": "Crushes and thrusts. Wants room.",
		"blueprint.jaymospowers.frontier.juggernaut": "Juggernaut Plate",
		"message.jaymospowers.smithing.botched": "The metal cracked: the %s is lost.",
		"message.jaymospowers.smithing.pattern_ask": "Use the table again to take the %s apart for its design. The piece will be destroyed.",
		"message.jaymospowers.smithing.pattern_done": "Drawn up: %s",
		"message.jaymospowers.smithing.pattern_no_paper": "You need paper in your inventory to draw up a design.",
	}
	for part, (name, rows) in PARTS.items():
		rows = rows()
		stone = gear.part_model(part, "stone", rows)
		iron = gear.part_model(part, "iron", rows)
		node = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
			"cases": [{"when": "stone", "model": stone}, {"when": "iron", "model": iron}], "fallback": stone}
		write(os.path.join(ASSETS, "items", part + ".json"), {"model": gear.hot(node)})
		lang["item.jaymospowers." + part] = name
	write(os.path.join(ASSETS, "lang", "parts", "war_arms.json"), lang)


if __name__ == "__main__":
	main()
