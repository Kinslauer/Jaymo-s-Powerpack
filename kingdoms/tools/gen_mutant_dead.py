"""The mutant zombie as one of the present day's old dead (Andy, 2026-11-22: "i want it to take after the new skins we
have in place of the default zombie blue and cyan").

Mutant Monsters' mutant zombie (a jar we never change; its art is All Rights Reserved, so none of it is used here) wears
the game's zombie colours, a cyan shirt and blue trousers, which the present's zombies no longer do: they wear our old
dead skins (gen_old_dead.py, client/OldDeadSkins), villagers dead a very long time, in their land's robes gone to dun,
torn, the hands and shins down to bone. This draws the mutant in one of those skins: each of its boxes is laid out from
the matching part of an old dead skin (the head from the head, the chest and the waist from the upper and lower body,
the upper and lower arms from the sleeve and the forearm, the thighs and shins from the leg), face by face, stretched
to the mutant's size.

Only the mutant's box layout is used (from the mod's model: where each box sits on its 128x128 sheet and its size);
every pixel comes from our own skin. Written to Kingdoms' built-in pack resourcepacks/mutant_dead (client/MutantDead
turns it on when Mutant Monsters is loaded), at the mod's own texture path, so the pack's copy is the one drawn.

Run from the project root: python3 -I kingdoms/tools/gen_mutant_dead.py   (needs Pillow)
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SKIN = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "assets", "jaymoskingdoms", "textures", "entity", "old_dead",
	"risen", "plain", "plains_1_0.png")
OUT = os.path.join(ROOT, "kingdoms", "src", "main", "resources", "resourcepacks", "mutant_dead", "assets", "mutantmonsters", "textures",
	"entity", "mutant_zombie.png")
SHEET = 128

# the old dead skin's boxes (the game's 64x64 humanoid layout): (u, v, width, height, depth)
HEAD, BODY, ARM, LEG = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)

# the mutant's boxes (u, v, width, height, depth), each with the part of the skin it is drawn from and the rows of
# that part's sides it takes (from, to: a share of the part's height; the top and bottom faces come along as they are)
MUTANT = [
	((0, 0, 8, 8, 8), HEAD, (0.0, 1.0)),          # head
	((0, 16, 24, 12, 16), BODY, (0.0, 0.6)),      # chest: the shoulders and the breast
	((0, 44, 14, 16, 12), BODY, (0.45, 1.0)),     # waist: the belly, the belt and the robe's skirt
	((104, 0, 6, 16, 6), ARM, (0.0, 0.6)),        # upper arm: the sleeve
	((104, 22, 6, 16, 6), ARM, (0.4, 1.0)),       # lower arm: the forearm and the hand
	((80, 0, 6, 11, 6), LEG, (0.0, 0.6)),         # thigh
	((80, 17, 6, 8, 6), LEG, (0.5, 1.0)),         # shin and foot
]


def faces(u, v, w, h, d):
	"""A box's faces on its sheet, by name: (x0, y0, x1, y1)."""
	return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
		"right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
		"left": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


def lay(src, rect, dst, into):
	"""{rect} of {src} stretched (nearest pixel) into {into} of {dst}."""
	x0, y0, x1, y1 = rect
	part = src.crop((x0, y0, x1, y1)).resize((into[2] - into[0], into[3] - into[1]), Image.NEAREST)
	dst.paste(part, (into[0], into[1]))


def main():
	skin = Image.open(SKIN).convert("RGBA")
	out = Image.new("RGBA", (SHEET, SHEET), (0, 0, 0, 0))
	for box, part, (a, b) in MUTANT:
		to, of = faces(*box), faces(*part)
		for name in ("top", "bottom"):
			lay(skin, of[name], out, to[name])
		for name in ("right", "front", "left", "back"):
			x0, y0, x1, y1 = of[name]
			ya, yb = y0 + round((y1 - y0) * a), y0 + round((y1 - y0) * b)
			lay(skin, (x0, ya, x1, max(yb, ya + 1)), out, to[name])
	os.makedirs(os.path.dirname(OUT), exist_ok=True)
	out.save(OUT)
	print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
	main()
