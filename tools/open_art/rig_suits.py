#!/usr/bin/env python3
"""The Hail and Tinker Rig suits (Andy, 2026-11-26: "make 2 new suits, one based off white, one based off the
gunmetal"): other artists' armour textures laid onto the worn Rig's plates (tools/gen_rig.py), pixel for pixel.

Claude threads don't paint (Andy, 2026-11-26), so nothing here draws: every pixel of a plate is looked up in the
source armour at the same spot on the body. A plate's face sits somewhere on its bone (the head, the body, an arm, a
leg); the source texture's own box for that bone has the same face, and the plate takes the pixel at the same place
on it (clamped to the box's edge for plates that stand off the body). The left arm and leg read the right ones'
mirror, as vanilla armour does.

- Hail (white): Power Armor: Renostalgized's worn layers (Thepigcat76, MIT), the vanilla humanoid layout.
- Tinker (gunmetal): ItsSory's Netherite Armor worn layers (MIT), the same layout; the gunmetal and cyan of the
  Militech exo set Andy picked. (Militech's own exo armour texture was tried first: it only covers Oritech's thin exo
  frame, so most plates came out bare.)

The glow pass of each suit is the source's own lit pixels (its bright cyans) where they land.

    python3 tools/open_art/rig_suits.py      # after tools/gen_rig.py (it reads the plates from there)
"""
import os
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import gen_rig  # noqa: E402

SRC = HERE / "sources"
OUT = Path(gen_rig.ASSETS) / "textures" / "entity" / "rig"

# A source box: (x0, y0, z0) in model space (pixels, y down, the feet at y 24, the right side at -x), size, uv.
VANILLA = {
	"head": ((-4, -8, -4), (8, 8, 8), (0, 0)),
	"body": ((-4, 0, -2), (8, 12, 4), (16, 16)),
	"right_arm": ((-8, 0, -2), (4, 12, 4), (40, 16)),
	"right_leg": ((-3.9, 12, -2), (4, 12, 4), (0, 16)),
}
MIRROR = {"left_arm": "right_arm", "left_leg": "right_leg"}
FLIP_FACE = {"left": "right", "right": "left"}


def face_point(face, frm, size, x, y):
	"""The model-space point at pixel (x, y) of a box's face, Minecraft's box net (see gen_rig.box_faces)."""
	(x0, y0, z0), (w, h, d) = frm, size
	if face == "front":
		return (x0 + x + 0.5, y0 + y + 0.5, z0)
	if face == "back":
		return (x0 + w - x - 0.5, y0 + y + 0.5, z0 + d)
	if face == "right":
		return (x0, y0 + y + 0.5, z0 + d - x - 0.5)
	if face == "left":
		return (x0 + w, y0 + y + 0.5, z0 + x + 0.5)
	if face == "top":
		return (x0 + x + 0.5, y0, z0 + d - y - 0.5)
	return (x0 + x + 0.5, y0 + h, z0 + y + 0.5)  # bottom


def face_pixel(face, frm, size, p):
	"""The inverse: the pixel of a box's face nearest a model-space point (clamped onto the face)."""
	(x0, y0, z0), (w, h, d) = frm, size
	px, py, pz = p

	def c(v, n):
		return max(0, min(n - 1, int(v // 1)))
	if face == "front":
		return c(px - x0, w), c(py - y0, h)
	if face == "back":
		return c(x0 + w - px, w), c(py - y0, h)
	if face == "right":
		return c(z0 + d - pz, d), c(py - y0, h)
	if face == "left":
		return c(pz - z0, d), c(py - y0, h)
	if face == "top":
		return c(px - x0, w), c(z0 + d - pz, d)
	return c(px - x0, w), c(pz - z0, d)


def lit(c):
	r, g, b, a = c
	return a > 0 and b >= 150 and g >= 150 and g - r >= 60


def sample(layers, boxes, bone, face, p):
	"""The source pixel for a point on a plate's face: the first layer that has an opaque pixel there, else the
	nearest opaque pixel on that face of the first layer that has any."""
	if bone in MIRROR:
		bone = MIRROR[bone]
		p = (-p[0], p[1], p[2])
		face = FLIP_FACE.get(face, face)
	frm, size, (u, v) = boxes[bone]
	fx, fy, fw, fh = gen_rig.box_faces(u, v, *size)[face]
	i, j = face_pixel(face, frm, size, p)
	for layer in layers:
		c = layer.getpixel((fx + i, fy + j))
		if c[3]:
			return c
	# a spot the source leaves bare takes the nearest plate pixel on that face, never a light (a bare arm would
	# otherwise come out striped with the edge's cyan)
	for layer in layers:
		best = None
		for y in range(fh):
			for x in range(fw):
				c = layer.getpixel((fx + x, fy + y))
				if c[3]:
					dist = (x - i) ** 2 + (y - j) ** 2 + (10000 if lit(c) else 0)
					if best is None or dist < best[0]:
						best = (dist, c)
		if best:
			return best[1]
	return None


def build(name, pick_layers, boxes):
	gen_rig.suit_pack()
	im = Image.new("RGBA", (gen_rig.SUIT_W, gen_rig.SUIT_H), (0, 0, 0, 0))
	glow = Image.new("RGBA", im.size, (0, 0, 0, 0))
	for plate, b in gen_rig.suit_boxes():
		bone = plate["bone"]
		piv = gen_rig.BONE_PIVOTS[bone]
		frm = tuple(b["frm"][k] + piv[k] for k in range(3))
		size = b["size"]
		layers = pick_layers(plate, bone)
		u, v = b["uv"]
		for face, (fx, fy, fw, fh) in gen_rig.box_faces(u, v, *size).items():
			for y in range(fh):
				for x in range(fw):
					c = sample(layers, boxes, bone, face, face_point(face, frm, size, x, y))
					if c is None:
						continue
					c = c[:3] + (255,)
					im.putpixel((fx + x, fy + y), c)
					if lit(c):
						glow.putpixel((fx + x, fy + y), c)
	im.save(OUT / f"suit_{name}.png")
	glow.save(OUT / f"suit_glow_{name}.png")


def layers_of(folder):
	top = Image.open(SRC / folder / "humanoid.png").convert("RGBA")
	legs = Image.open(SRC / folder / "humanoid_leggings.png").convert("RGBA")
	piece_arms = gen_rig.PIECE_IDS["arms"]

	def pick(plate, bone):
		# the Arms piece is worn in the legs slot: its hip harness and thigh plates take the leggings layer
		if plate["piece"] == piece_arms and bone not in ("right_arm", "left_arm"):
			return [legs, top]
		return [top, legs]
	return pick


def main():
	build("hail", layers_of("power_armor"), VANILLA)
	build("tinker", layers_of("netherite_armor"), VANILLA)
	print("wrote", OUT / "suit_hail.png", "and", OUT / "suit_tinker.png", "(with their glow passes)")


if __name__ == "__main__":
	main()
