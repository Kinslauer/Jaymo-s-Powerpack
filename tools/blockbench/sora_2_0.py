"""Turn Andy's four Sora Dagger 2.0 .bbmodels (tools/bbmodels/sora-dagger-2-0-<stage>.bbmodel) into the dagger's
stage item models (forward + reverse grip) and textures.

    py -3 -I tools/blockbench/sora_2_0.py            (from the project root)

Each .bbmodel is a java_block model with two textures: 0 "Dagger texture" (hilt and handle) and 1 "texture" (blade).
The game reads face textures by slot ("#0", "#1") and UVs in 0-16 per texture, so UVs are scaled from the model's
resolution (128, Tempest 320). Tempest's hilt texture is 1240px for a 320 UV space: it is cropped to the part the
model uses (UV 0-128) and resized to 512px so the item atlas keeps its mipmap levels.

Display: each model's own transforms, except "ground", which is the 0.4 pose from #1099 (the .bbmodels leave it out
or squash only x). The reverse grip (custom_model_data flag 0) is the forward model with #1099's reverse-grip hand
transforms; every 2.0 model has the same hand transforms, so those numbers carry over unchanged.
"""
import base64
import copy
import io
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent
SRC = ROOT / "tools/bbmodels"
ASSETS = ROOT / "src/main/resources/assets/jaymospowers"
NS = "jaymospowers"

# stage item -> (source .bbmodel, texture/model name)
STAGES = {
	"soras_dagger": ("sora-dagger-2-0-iron.bbmodel", "soras_dagger_2_0_iron"),
	"soras_dagger_gold": ("sora-dagger-2-0-gilded.bbmodel", "soras_dagger_2_0_gilded"),
	"soras_dagger_diamond": ("sora-dagger-2-0-diamond-edged.bbmodel", "soras_dagger_2_0_diamond"),
	"soras_dagger_tempest": ("sora-dagger-2-0-tempest.bbmodel", "soras_dagger_2_0_tempest"),
}
SLOTS = ("hilt", "blade")  # bbmodel texture 0, 1

GROUND = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]}
REVERSE = {  # from soras_dagger_2_0_reverse.json (#1099); scale stays the model's own
	"thirdperson_righthand": {"rotation": [-170, 0, 0], "translation": [0, -3.06, -0.34]},
	"thirdperson_lefthand": {"rotation": [-170, 0, 0], "translation": [0.25, -3.7, 0]},
	"firstperson_righthand": {"rotation": [-60, 0, 0], "translation": [0, 0.23, -3.6]},
	"firstperson_lefthand": {"rotation": [-60, 0, 0], "translation": [0, 0.23, -3.6]},
}
MAX_TEXTURE = 512


def image(tex):
	return Image.open(io.BytesIO(base64.b64decode(tex["source"].split(",", 1)[1]))).convert("RGBA")


def r(v):
	v = round(v, 4)
	return int(v) if v == int(v) else v


def convert(item, src, name):
	bb = json.loads((SRC / src).read_text(encoding="utf-8"))
	res = bb["resolution"]["width"]
	assert bb["resolution"]["height"] == res and len(bb["textures"]) == 2

	# UV space each texture is drawn in: the model's resolution, unless the texture is cropped below.
	space = []
	for i, tex in enumerate(bb["textures"]):
		img = image(tex)
		used = max(c for e in bb["elements"] for f in e["faces"].values() if f.get("texture") == i for c in f["uv"])
		uv_space = res
		if img.width > MAX_TEXTURE:
			uv_space = 1 << (int(used) - 1).bit_length()  # smallest power of two covering what is used
			crop = round(img.width * uv_space / res)
			img = img.crop((0, 0, crop, crop)).resize((MAX_TEXTURE, MAX_TEXTURE), Image.NEAREST)
		assert img.width == img.height and img.width % 16 == 0, (src, i, img.size)
		img.save(ASSETS / f"textures/item/{name}_{SLOTS[i]}.png")
		space.append(uv_space)

	elements = []
	for e in bb["elements"]:
		if e.get("export") is False:
			continue
		el = {"from": [r(v) for v in e["from"]], "to": [r(v) for v in e["to"]]}
		rot = [r(v) for v in e.get("rotation", [0, 0, 0])]
		axes = [a for a, v in zip("xyz", rot) if v]
		if axes:
			el["rotation"] = {"origin": [r(v) for v in e["origin"]]}
			if len(axes) == 1:
				el["rotation"].update(axis=axes[0], angle=rot["xyz".index(axes[0])])
			else:
				el["rotation"].update(x=rot[0], y=rot[1], z=rot[2])
			if e.get("rescale"):
				el["rotation"]["rescale"] = True
		if e.get("shade_direction_override"):
			el["shade_direction_override"] = e["shade_direction_override"]
		if e.get("light_emission"):
			el["light_emission"] = e["light_emission"]
		faces = {}
		for side, f in e["faces"].items():
			t = f.get("texture")
			if t is None:
				continue
			face = {"uv": [r(c * 16 / space[t]) for c in f["uv"]], "texture": f"#{t}"}
			if f.get("rotation"):
				face["rotation"] = f["rotation"]
			if f.get("cullface"):
				face["cullface"] = f["cullface"]
			if f.get("tintindex") is not None:
				face["tintindex"] = f["tintindex"]
			faces[side] = face
		el["faces"] = faces
		elements.append(el)

	display = copy.deepcopy(bb["display"])
	display["ground"] = copy.deepcopy(GROUND)
	tex = {"0": f"{NS}:item/{name}_hilt", "1": f"{NS}:item/{name}_blade", "particle": f"{NS}:item/{name}_hilt"}
	label = Path(src).stem.replace("sora-dagger-2-0-", "").replace("-", " ")
	forward = {"credit": f"Sora Dagger 2.0 ({label}) by Andy, converted from Blockbench by tools/blockbench/sora_2_0.py",
			   "textures": tex, "display": display, "elements": elements}
	reverse = copy.deepcopy(forward)
	reverse["credit"] += " (reverse grip)"
	for slot, t in REVERSE.items():
		reverse["display"][slot] = {**reverse["display"].get(slot, {}), **copy.deepcopy(t)}
		reverse["display"][slot] = {k: reverse["display"][slot][k] for k in ("rotation", "translation", "scale")
									if k in reverse["display"][slot]}
	for model, suffix in ((forward, ""), (reverse, "_reverse")):
		(ASSETS / f"models/item/{name}{suffix}.json").write_text(json.dumps(model, indent=2) + "\n")

	definition = {"model": {"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": 0,
							"on_true": {"type": "minecraft:model", "model": f"{NS}:item/{name}_reverse"},
							"on_false": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}}
	(ASSETS / f"items/{item}.json").write_text(json.dumps(definition, indent=2) + "\n")
	print(f"{item}: {name} ({len(elements)} cubes, UV spaces {space})")


for item, (src, name) in STAGES.items():
	convert(item, src, name)
