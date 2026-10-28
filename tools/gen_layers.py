"""Layer blocks: a snow-layer-style block (1-8 layers, 2 px each) for every wooden and stone block below.

Usage: python -I tools/gen_layers.py <path to 26.3.jar>   (from the repo root)

Reads each base block's own model (vanilla ones from the game jar, ours from the repo) for its textures and writes:
blockstates, models (block/layers/<id>_height<2..14>; 8 layers use the base block's own model), item models, loot
tables (one item per layer), recipes (wood: the saw and the block in a crafting grid; stone: the stonecutter; 8
layers craft back into the block), the two block tags, lang/parts/layers.json, the Java list LayerList.java, and the
saw (texture, model; it is forged, see tinkering/WeaponParts).
"""
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "jaymospowers")
DATA = os.path.join(RES, "data", "jaymospowers")
NS = "jaymospowers"

VANILLA_WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"]
WOOD = [f"minecraft:{w}_planks" for w in VANILLA_WOODS + ["crimson", "warped", "bamboo"]]
WOOD += [f"minecraft:{w}_log" for w in VANILLA_WOODS] + [f"minecraft:stripped_{w}_log" for w in VANILLA_WOODS]
WOOD += ["minecraft:crimson_stem", "minecraft:warped_stem", "minecraft:stripped_crimson_stem", "minecraft:stripped_warped_stem",
	"minecraft:bamboo_block", "minecraft:stripped_bamboo_block", "minecraft:bamboo_mosaic"]
WOOD += [f"{NS}:{p}" for p in ["skyroot_planks", "greatroot_planks", "wisproot_planks", "amberoot_planks",
	"skyroot_log", "stripped_skyroot_log", "golden_oak_log", "greatroot_log"]]

STONE = [f"minecraft:{s}" for s in [
	"stone", "cobblestone", "mossy_cobblestone", "smooth_stone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
	"granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
	"deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles",
	"tuff", "polished_tuff", "tuff_bricks", "calcite", "dripstone_block",
	"sandstone", "smooth_sandstone", "cut_sandstone", "red_sandstone", "smooth_red_sandstone", "cut_red_sandstone",
	"bricks", "mud_bricks", "blackstone", "polished_blackstone", "polished_blackstone_bricks", "smooth_basalt",
	"nether_bricks", "red_nether_bricks", "end_stone", "end_stone_bricks", "purpur_block", "quartz_block", "smooth_quartz",
	"prismarine", "prismarine_bricks", "dark_prismarine"]]
STONE += [f"{NS}:{s}" for s in ["holystone", "mossy_holystone", "holystone_bricks", "agiosite", "agiosite_bricks",
	"ichorite", "ichorite_bricks", "undershale", "undershale_bricks"]]


def write(path, obj):
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w", encoding="utf-8", newline="\n") as f:
		json.dump(obj, f, indent=2)
		f.write("\n")


def rgb(h):
	return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# the saw, painted on a shade grid lit from the top-left: k-w the steel blade, darkest to lightest (the lower edge
# toothed dark), 0-3 the wooden grip (vanilla stick.png's ramp) with a hole for the fingers
SAW_SHADES = dict(zip("kdglw", map(rgb, ("2a2a2a", "5e5e5e", "8f8f8f", "c6c6c6", "eeeeee"))))
SAW_SHADES.update(zip("0123", map(rgb, ("493615", "281e0b", "896727", "684e1e"))))
SAW_GRID = (
	"................",
	"............kkk.",
	"...........kwlgk",
	"..........kwlggk",
	".........kwlggdk",
	"........kwlgggdk",
	".......kwlgggdk.",
	"......kwlgggddk.",
	"..0kkkwlgggddkd.",
	".0230wlgggddkd..",
	"02..2lgggddkd...",
	"02..0gggddkd....",
	".0223ggddkd.....",
	"..0231ddkd......",
	"...011dkd.......",
	"................",
)


def saw():
	from PIL import Image
	im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for y, row in enumerate(SAW_GRID):
		for x, ch in enumerate(row):
			if ch in SAW_SHADES:
				im.putpixel((x, y), SAW_SHADES[ch] + (255,))
	path = os.path.join(ASSETS, "textures", "item", "saw.png")
	os.makedirs(os.path.dirname(path), exist_ok=True)
	im.save(path)
	write(os.path.join(ASSETS, "models", "item", "saw.json"),
		{"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/saw"}})
	write(os.path.join(ASSETS, "items", "saw.json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/saw"}})
	# no crafting recipe: forged at the Tool Station from a Saw Blade (tinkering/WeaponParts)


class Models:
	def __init__(self, jar):
		self.jar = zipfile.ZipFile(jar)

	def read(self, ns, kind, path):
		if ns == "minecraft":
			return json.loads(self.jar.read(f"assets/minecraft/{kind}/{path}.json"))
		with open(os.path.join(RES, "assets", ns, kind, path + ".json"), encoding="utf-8") as f:
			return json.load(f)

	def base_model(self, block):
		"""The model id a block shows standing upright (axis=y, or its first variant)."""
		ns, path = block.split(":")
		variants = self.read(ns, "blockstates", path)["variants"]
		v = variants.get("axis=y", variants.get(""))
		if isinstance(v, list):
			v = v[0]
		return v["model"]

	def textures(self, model):
		"""top/bottom/side of a cube_all, cube_column or cube_bottom_top model (following parents)."""
		ns, path = (model.split(":") if ":" in model else ("minecraft", model))
		m = self.read(ns, "models", path)
		tex = dict(m.get("textures", {}))
		parent = m.get("parent", "").replace("minecraft:", "")
		if parent.startswith("block/cube_column"):
			parent = "block/cube_column"
		while parent and parent not in ("block/cube_all", "block/cube_column", "block/cube_bottom_top", "block/cube"):
			pns, ppath = parent.split(":") if ":" in parent else ("minecraft", parent)
			pm = self.read(pns, "models", ppath)
			tex = {**pm.get("textures", {}), **tex}
			parent = pm.get("parent", "").replace("minecraft:", "")
			if parent.startswith("block/cube_column"):
				parent = "block/cube_column"
		def full(t):
			while t.startswith("#"):
				t = tex[t[1:]]
			return t if ":" in t else "minecraft:" + t
		if parent == "block/cube_all" or "all" in tex:
			return full(tex["all"]), full(tex["all"]), full(tex["all"])
		if parent == "block/cube_column":
			return full(tex["end"]), full(tex["end"]), full(tex["side"])
		if parent == "block/cube_bottom_top":
			return full(tex["top"]), full(tex["bottom"]), full(tex["side"])
		if parent == "block/cube":
			return full(tex["up"]), full(tex["down"]), full(tex["north"])
		raise ValueError(f"{model}: not a plain cube ({parent})")


def layer_id(base):
	ns, path = base.split(":")
	return path + "_layer"


def name(base):
	words = base.split(":")[1].split("_")
	if words[-1] == "block" and len(words) > 1:
		words = words[:-1]
	if words[-1] in ("planks", "bricks", "tiles"):
		words[-1] = words[-1][:-1]
	return " ".join(w.capitalize() for w in words) + " Layer"


def template(h):
	faces = {
		"down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
		"up": {"uv": [0, 0, 16, 16], "texture": "#top"},
	}
	for side in ("north", "south", "west", "east"):
		faces[side] = {"uv": [0, 16 - h, 16, 16], "texture": "#side", "cullface": side}
	return {
		"parent": "minecraft:block/block",
		"textures": {"particle": "#side"},
		"elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": faces}],
	}


def main():
	models = Models(sys.argv[1])
	saw()
	for h in range(2, 16, 2):
		write(os.path.join(ASSETS, "models", "block", "layers", f"template_height{h}.json"), template(h))
	lang = {"item.jaymospowers.saw": "Saw"}
	listed = []
	tags = {"wood": [], "stone": []}
	for kind, bases in (("wood", WOOD), ("stone", STONE)):
		for base in bases:
			lid = layer_id(base)
			full_model = models.base_model(base)
			top, bottom, side = models.textures(full_model)
			variants = {}
			for n in range(1, 9):
				if n == 8:
					model = full_model
				else:
					model = f"{NS}:block/layers/{lid}_height{2 * n}"
					write(os.path.join(ASSETS, "models", "block", "layers", f"{lid}_height{2 * n}.json"), {
						"parent": f"{NS}:block/layers/template_height{2 * n}",
						"textures": {"top": top, "bottom": bottom, "side": side},
					})
				variants[f"layers={n}"] = {"model": model}
			write(os.path.join(ASSETS, "blockstates", f"{lid}.json"), {"variants": variants})
			write(os.path.join(ASSETS, "items", f"{lid}.json"),
				{"model": {"type": "minecraft:model", "model": f"{NS}:block/layers/{lid}_height2"}})
			counts = [{"function": "minecraft:set_count", "count": n, "add": False, "conditions": [{
				"condition": "minecraft:block_state_property", "block": f"{NS}:{lid}", "properties": {"layers": str(n)}}]}
				for n in range(2, 9)]
			write(os.path.join(DATA, "loot_table", "blocks", f"{lid}.json"), {
				"type": "minecraft:block",
				"pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{lid}",
					"functions": counts + [{"function": "minecraft:explosion_decay"}]}]}],
				"random_sequence": f"{NS}:blocks/{lid}",
			})
			result = {"id": f"{NS}:{lid}", "count": 8}
			if kind == "wood":
				cut = {"type": "minecraft:crafting_shapeless", "category": "building",
					"ingredients": [f"{NS}:saw", base], "result": result}
			else:
				cut = {"type": "minecraft:stonecutting", "ingredient": base, "result": result}
			write(os.path.join(DATA, "recipe", "layers", f"{lid}.json"), cut)
			write(os.path.join(DATA, "recipe", "layers", f"{lid}_to_block.json"), {
				"type": "minecraft:crafting_shapeless", "category": "building",
				"ingredients": [f"{NS}:{lid}"] * 8, "result": {"id": base}})
			lang[f"block.{NS}.{lid}"] = name(base)
			listed.append(base)
			tags[kind].append(f"{NS}:{lid}")
	for kind, values in tags.items():
		write(os.path.join(DATA, "tags", "block", f"{kind}_layers.json"), {"values": values})
		write(os.path.join(DATA, "tags", "item", f"{kind}_layers.json"), {"values": values})
	write(os.path.join(ASSETS, "lang", "parts", "layers.json"), lang)
	java = os.path.join(ROOT, "src", "main", "java", "com", "jaymospowers", "layers", "LayerList.java")
	with open(java, "w", encoding="utf-8", newline="\n") as f:
		f.write("package com.jaymospowers.layers;\n\n")
		f.write("/** Generated by tools/gen_layers.py: the blocks that get a layer block ({@code <path>_layer}). */\n")
		f.write("final class LayerList {\n\tstatic final String[] BASES = {\n")
		for base in listed:
			f.write(f"\t\t\"{base}\",\n")
		f.write("\t};\n\n\tprivate LayerList() {\n\t}\n}\n")
	print(f"{len(listed)} layer blocks")


if __name__ == "__main__":
	main()
