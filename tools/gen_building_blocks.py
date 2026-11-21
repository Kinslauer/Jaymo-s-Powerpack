"""Building blocks: a vertical slab and a quarter step for every block gen_layers.py covers.

Usage: python -I tools/gen_building_blocks.py <path to minecraft-client.jar>   (from the repo root)

Reads each base block's own model (the same way gen_layers.py does) for its textures and writes:
blockstates, models (block/buildblocks/<id>_vslab, <id>_quarter_<q>), item models, loot tables (26.3 form), recipes
(wood: the saw; stone: the stonecutter; and back into the block), the two block tags, lang/parts/buildblocks.json and
the Java list BuildList.java. The vertical slab is a half block (16 x 16 x 8) facing the side it sits on, two of which
fill a block; the quarter step is a beam 16 long, 8 wide, 8 tall, four of which fill a block.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_layers as gl  # noqa: E402

ROOT, ASSETS, DATA, NS = gl.ROOT, gl.ASSETS, gl.DATA, gl.NS
write = gl.write

# the four places a quarter step can sit in a block whose beams run north-south: (name, x0, x1, y0, y1)
QUARTERS = [("low_neg", 0, 8, 0, 8), ("low_pos", 8, 16, 0, 8), ("high_neg", 0, 8, 8, 16), ("high_pos", 8, 16, 8, 16)]


def box(x0, y0, z0, x1, y1, z1):
	"""A box with a face on every side, #side on the sides, culled where it lies on the block's edge."""
	def face(texture, cull):
		f = {"texture": texture}
		if cull:
			f["cullface"] = cull
		return f
	return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {
		"down": face("#bottom", "down" if y0 == 0 else None),
		"up": face("#top", "up" if y1 == 16 else None),
		"north": face("#side", "north" if z0 == 0 else None),
		"south": face("#side", "south" if z1 == 16 else None),
		"west": face("#side", "west" if x0 == 0 else None),
		"east": face("#side", "east" if x1 == 16 else None),
	}}


def template(element):
	return {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": [element]}


def loot(block_id, modifiers):
	return {
		"type": "minecraft:block",
		"pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{block_id}",
			"modifier": modifiers + [{"type": "minecraft:explosion_decay"}]}]}],
		"random_sequence": f"{NS}:blocks/{block_id}",
	}


def count(n, block_id, prop, value, add):
	return {"type": "minecraft:set_count", "count": n, "add": add, "condition": {
		"type": "minecraft:match_block", "blocks": f"{NS}:{block_id}", "state": {prop: value}}}


def recipe(kind, ingredients, result, n):
	return {"type": "minecraft:stonecutting", "ingredient": ingredients[0], "result": {"id": result, "count": n}} if kind == "stone" \
		else {"type": "minecraft:crafting_shapeless", "category": "building", "ingredients": ingredients,
			"result": {"id": result, "count": n}}


def main():
	models = gl.Models(sys.argv[1])
	b = os.path.join(ASSETS, "models", "block", "buildblocks")
	write(os.path.join(b, "template_vslab.json"), template(box(0, 0, 0, 16, 16, 8)))
	for q, x0, x1, y0, y1 in QUARTERS:
		write(os.path.join(b, f"template_quarter_{q}.json"), template(box(x0, y0, 0, x1, y1, 16)))
	lang = {f"block.{NS}.mixed_slab": "Mixed Slab"}
	listed = []
	tags = {"wood": [], "stone": []}
	for kind, bases in (("wood", gl.WOOD), ("stone", gl.STONE)):
		for base in bases:
			pid = base.split(":")[1]
			vid, qid = pid + "_vslab", pid + "_quarter"
			full_model = models.base_model(base)
			top, bottom, side = models.textures(full_model)
			textures = {"top": top, "bottom": bottom, "side": side}
			# vertical slab: facing is the side it sits on (north: the north half), turned from the north model
			write(os.path.join(b, f"{vid}.json"), {"parent": f"{NS}:block/buildblocks/template_vslab", "textures": textures})
			variants = {}
			for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
				v = {"model": f"{NS}:block/buildblocks/{vid}"}
				if y:
					v["y"] = y
					v["uvlock"] = True
				variants[f"double=false,facing={facing}"] = v
			variants["double=true"] = {"model": full_model}
			write(os.path.join(ASSETS, "blockstates", f"{vid}.json"), {"variants": variants})
			write(os.path.join(ASSETS, "items", f"{vid}.json"),
				{"model": {"type": "minecraft:model", "model": f"{NS}:block/buildblocks/{vid}"}})
			write(os.path.join(DATA, "loot_table", "blocks", f"{vid}.json"),
				loot(vid, [count(2, vid, "double", "true", False)]))
			# quarter step: four beams, each a model; an east-west block is the north-south one turned
			parts = []
			for q, *_ in QUARTERS:
				write(os.path.join(b, f"{qid}_{q}.json"), {"parent": f"{NS}:block/buildblocks/template_quarter_{q}", "textures": textures})
				for axis, y in (("z", 0), ("x", 90)):
					apply = {"model": f"{NS}:block/buildblocks/{qid}_{q}"}
					if y:
						apply["y"] = y
						apply["uvlock"] = True
					parts.append({"when": {"axis": axis, q: "true"}, "apply": apply})
			write(os.path.join(ASSETS, "blockstates", f"{qid}.json"), {"multipart": parts})
			write(os.path.join(ASSETS, "items", f"{qid}.json"),
				{"model": {"type": "minecraft:model", "model": f"{NS}:block/buildblocks/{qid}_low_neg"}})
			write(os.path.join(DATA, "loot_table", "blocks", f"{qid}.json"),
				loot(qid, [{"type": "minecraft:set_count", "count": 0, "add": False}]
					+ [count(1, qid, q, "true", True) for q, *_ in QUARTERS]))
			# cutting: wood at the saw (four blocks cut into eight vertical slabs, a vertical slab into two quarter steps;
			# a block or two alone would clash with the layers' and the planks' saw recipes), stone at the stonecutter
			rd = os.path.join(DATA, "recipe", "buildblocks")
			if kind == "wood":
				write(os.path.join(rd, f"{vid}.json"), recipe(kind, [f"{NS}:saw"] + [base] * 4, f"{NS}:{vid}", 8))
				write(os.path.join(rd, f"{qid}.json"), recipe(kind, [f"{NS}:saw", f"{NS}:{vid}"], f"{NS}:{qid}", 2))
			else:
				write(os.path.join(rd, f"{vid}.json"), recipe(kind, [base], f"{NS}:{vid}", 2))
				write(os.path.join(rd, f"{qid}.json"), recipe(kind, [base], f"{NS}:{qid}", 4))
				write(os.path.join(rd, f"{qid}_from_vslab.json"), recipe(kind, [f"{NS}:{vid}"], f"{NS}:{qid}", 2))
			write(os.path.join(rd, f"{vid}_to_block.json"), {"type": "minecraft:crafting_shapeless", "category": "building",
				"ingredients": [f"{NS}:{vid}"] * 2, "result": {"id": base}})
			write(os.path.join(rd, f"{qid}_to_block.json"), {"type": "minecraft:crafting_shapeless", "category": "building",
				"ingredients": [f"{NS}:{qid}"] * 4, "result": {"id": base}})
			title = gl.name(base)[:-len(" Layer")]
			lang[f"block.{NS}.{vid}"] = title + " Vertical Slab"
			lang[f"block.{NS}.{qid}"] = title + " Quarter Step"
			listed.append(base)
			tags[kind] += [f"{NS}:{vid}", f"{NS}:{qid}"]
	for kind, values in tags.items():
		write(os.path.join(DATA, "tags", "block", f"{kind}_build_blocks.json"), {"values": values})
	write(os.path.join(ASSETS, "lang", "parts", "buildblocks.json"), lang)
	java = os.path.join(ROOT, "src", "main", "java", "com", "jaymospowers", "buildblocks", "BuildList.java")
	os.makedirs(os.path.dirname(java), exist_ok=True)
	with open(java, "w", encoding="utf-8", newline="\n") as f:
		f.write("package com.jaymospowers.buildblocks;\n\n")
		f.write("/** Generated by tools/gen_building_blocks.py: the blocks that get a vertical slab and a quarter step. */\n")
		f.write("final class BuildList {\n\tstatic final String[] BASES = {\n")
		for base in listed:
			f.write(f"\t\t\"{base}\",\n")
		f.write("\t};\n\n\tprivate BuildList() {\n\t}\n}\n")
	print(f"{len(listed)} blocks, {2 * len(listed)} building blocks")


if __name__ == "__main__":
	main()
