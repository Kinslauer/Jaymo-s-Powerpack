"""Fish and critter icons for Jaymo's Powers, each with its own silhouette (docs/Vanilla-Style.md).

Every species is a small hand-drawn grid (head on the left, `.` clear) painted from its own palette: flat bands of
back, flank and belly, fins in their own colour, a pattern in clean shapes, no noise. The outline is the darkest
shade of the region it wraps (lit a step lighter on the top and left), as on vanilla's cod and salmon. The cooked
icon is the same shape in the colours of vanilla's cooked cod (white and sea fish), cooked salmon (oily fish) or a
boiled shell (crustaceans). `tools/gen_fishing_species.py` calls `icon()` and `egg()`.

Letters: B back, F flank, L belly, N fin, P pattern, Q second pattern colour, W white, E eye, K dark (claw tips, bill).
"""
from PIL import Image


def _rgb(c):
	return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def _shade(c, f):
	return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def _mix(a, b, t):
	return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


GRIDS = {
	"carp": """
.....NNNNN.....
..BBBBBBBBBB.N.
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFPFFPFFPFFNN.
BFFFPFFPFFPFNN.
.LLFFFFFFFFFNN.
..LLLLLLLLL.N..
...NN..NN......
""",
	"pike": """
...............
....BBBBBB.NN..
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFPFFPFFPFFBN
BBFFFFFFFFFFFNN
.LLLLLLLLLLLNN.
...LLLLLLL.NN..
""",
	"perch": """
....NNNNN......
...NBNNBNBB.N..
..BBPBBPBBPBBNN
.BBBPBBPBBPBBNN
BEFFPFFPFFPFFNN
BFFFPFFPFFPFNN.
.LLLLLLLLLLNN..
..LNLLLLLLN....
...NN..........
""",
	"zander": """
...............
...NNNNN.NNNN..
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFPFFPFFPFFNN
BFFFFFFFFFFFNN.
.LLLLLLLLLLLNN.
..LLLLLLLLLNN..
""",
	"bream": """
......NNNNN....
....BBBBBBBBN..
..BBBBBBBBBBBNN
.BBBBBBBBBBBBNN
BEFFFFFFFFFFNN.
BFFFFFFFFFFFNN.
BFFPFFPFFPFFNN.
.LLLLLLLLLLLN..
..LLLLLLLLNN...
....NNNNNN.....
""",
	"tench": """
....BBBBBBB....
..BBBBBBBBBBB..
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFFFFFFFFFNNN
BFFFFFFFFFFFNNN
.LLLLLLLLLLLNN.
..LLLLLLLLL.N..
...NN..NN......
""",
	"catfish": """
...............
....BBBBBBBB...
.BBBBBBBBBBBBB.
BBBBBBBBBBBBBBN
BBEFPFFPFFPFFNN
.BFFFFFFFFFFNN.
..LLLLLLLLLLNN.
.PPLLLLLLNNNN..
.P..NNNN.......
""",
	"eel": """
...............
.BBBB..........
BEFFBB.....BBB.
BFFFFBB...BFFB.
.LFFFFBBBBFFFLB
..LLFFFFFFFFLLN
....LLLLLLLLLNN
...............
""",
	"trout": """
.........N.....
....BBBBBBBN...
..BBBPBBPBBBNN.
.BBBBBBBBBBBBNN
BEFPFFPFFPFFFNN
BFFFPFFPFFPFFNN
.LLLLLLLLLLLNN.
..LLLLLLLLN.N..
.....NN........
""",
	"chub": """
...............
...BBBBBBBB....
.BBBBBBBBBBBNN.
BBBBBBBBBBBBNNN
BEFFPFFPFFPFFNN
BFFFFFFFFFFFNNN
.LLLLLLLLLLLNN.
..LLLLLLLLN.N..
....NN..NN.....
""",
	"char": """
.........N.....
....BBBBBBBN...
..BBBBBBBBBBNN.
.BBBBBBBBBBBBNN
BEFPFFPFFPFFFNN
BFFFPFFPFFPFFNN
.LLLLLLLLLLLNN.
..LLLLLLLLL.WN.
.....NW........
""",
	"grayling": """
.....NNNNNN....
....NNNNNNNN.N.
..BBBBBBBBBBBNN
.BBBBBBBBBBBBNN
BEFFPFFPFFPFFNN
BFFFFFFFFFFFNN.
.LLLLLLLLLLLNN.
..LLLLLLLLNN...
""",
	"whitefish": """
...............
....BBBBBBB.N..
..BBBBBBBBBBBNN
.BBBBBBBBBBBBNN
BEFFFFFFFFFFNN.
BFFPFFPFFPFFNN.
.LLLLLLLLLLLNN.
..LLLLLLLLNN.N.
....NN.........
""",
	"burbot": """
...............
.NN..NNNNNN....
BBBBBBBBBBBBBBN
BEBBPBBPBBPBBNN
BFFFFFFFFFFFFNN
.LFFFFFFFFFFFNN
.P.LLLLLLLLNNNN
.P..NNNNNN.....
""",
	"mackerel": """
..........N.N..
....BBBB.NN.NN.
..BBBPBBPBBNBNN
.BBBBPBBPBBBBNN
BEFFFFFFFFFFNNN
BFFFFFFFFFFFNN.
.LLLLLLLLLLLNN.
..LLLLLLLNNN.N.
.....N.N.......
""",
	"herring": """
...............
...BBBBBBB.....
.BBBBBBBBBBB.N.
BBBBBBBBBBBBNNN
BEFFFFFFFFFFNNN
BLLLLLLLLLLLNN.
.LLLLLLLLLLN.N.
...N...NN......
""",
	"pollock": """
...............
..NNNN.NNNN....
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFFFFFFFFFNNN
BFPPPPPPPPPFNN.
.LLLLLLLLLLNNN.
..LLLLLLNNN.N..
""",
	"sea_bass": """
...NNNNN.......
..NBBBBBNNNN...
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFFFFFFFFFNNN
BFFFFFFFFFFFNN.
.LLLLLLLLLLNN..
..LLLLLNNNN....
""",
	"halibut": """
...............
......BBBBBB...
..BBBBBBBBBBBB.
.BBBPBBBBPBBBBN
BBBEBBBBBBBBBNN
BBBBBBBPBBBBBNN
.NBBBBBBBBBBBN.
..NNNNNNNNNNN..
""",
	"swordfish": """
.....NNN.......
....BBBBBBN..N.
KKKBBBBBBBBBBNN
KKBBBBBBBBBBBNN
.BEFFFFFFFFFFNN
..FFFFFFFFFFNNN
...LLLLLLLLNN.N
.....N.........
""",
	"catshark": """
...............
......NN.NN....
.BBBBBBBBBBBBNN
BBPBBPBBPBBBBNN
BEFFFFFFFFFFFNN
BFFPFFPFFPFFNN.
.LLLLLLLLLLLN..
...N...N.......
""",
	"dogfish": """
.......NN......
......BNNBN.NN.
.BBBBBBBBBBBBBN
BBBPBBPBBPBBBNN
BEFFFFFFFFFFFNN
BFFFFFFFFFFFNN.
.LLLLLLLLLLLN..
....NN.NN......
""",
	"blue_shark": """
.......NN......
......BNNB.....
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFFFFFFFFFFNN
.FFFFFFFFFFFNN.
..LLLLLLLLLN...
.....NNNN......
""",
	"hammerhead": """
...............
.....NN........
KKKBBBBBBBBBBNN
BBEBBBBBBBBBBNN
KKKFFFFFFFFFFNN
..FFFFFFFFFFNN.
..LLLLLLLLLN...
.....NNN.......
""",
	"great_white": """
.......N.......
......BNB......
.BBBBBBBBBBBBNN
BBBBBBBBBBBBBNN
BEFFFFFFFFFFFNN
.LFFLFLFLFFFFNN
..LLLLLLLLLLNN.
.....NNNN......
""",
	"shrimp": """
......BBBB.....
....BBBBBBB....
..BBBPBBPBB....
.BBEBBPBBPBB...
BBBBBBBBBBBBB..
.NNFFFFFFFFBB..
..N.LLLLLLLNN..
......N.N.NN...
""",
	"crayfish": """
..N..NN........
.NNN.NN........
.NN..BBBBBB....
..NBEBBBBBBBB..
..NBBBPBBPBBBB.
...FFFFFFFFFFNN
..N.LLLLLLLLNN.
.NN.N.N.N......
""",
	"crab": """
..NN......NN...
.NNNN....NNNN..
.NNN.BBBB.NNN..
..N.BBEBEBB.N..
...BBBBBBBBB...
..NFFPFFPFFN...
.N.LLLLLLLL.N..
N..N.N..N.N..N.
""",
	"lobster": """
.NNN.N.........
NNNNN.N........
NNN..BBBBBB....
.NNNBEBBBBBBB..
.N.BBBBPBBPBBBB
..NFFFFFFFFFFNN
.N.LLLLLLLLLNN.
NN.N.N.N.N.....
""",
	"jellyfish": """
....FFFFFF.....
..FFFFFFFFFF...
.FFFPFFFFPFFF..
.FFFFFFFFFFFF..
.NNNNNNNNNNNN..
..N.N.N.N.N.N..
..N.N...N.N.N..
...N...N...N...
""",
	"cavefish": """
...............
....BBBBBB.....
..BBBBBBBBBB.N.
.BBBBBBBBBBBNNN
BEFFFFFFFFFFNN.
.LLLLLLLLLLNN..
..LLLLLLLLN....
""",
	"olm": """
...............
.BB............
BEBBB..........
BBBBBBB.BBBB...
.LLFFFBBBFFBBN.
..LLLLFFFFLLLNN
...N.N.LLLLN.N.
...N.N.........
""",
	"ash_molly": """
...............
....NNNNN......
..BBBBBBBBB.N..
.BBBBBBBBBBNNN.
BEFFFPFFPFFNNN.
BFFFFFFFFFFNN..
.LLLLLLLLLN.N..
..NNN..........
""",
	"cinder_pupfish": """
...............
.....NNNN......
...BBBBBBBB.N..
..BBPBBPBBBBNN.
.BEFFFFFFFFNNN.
.BFPFFPFFFFNNN.
..LLLLLLLLN.N..
....NN.........
""",
	"ember_tilapia": """
.....NNNNNN....
...NNNBBBBBNN..
..BBBBBBBBBBBNN
.BBBBBBBBBBBBNN
BEFFPFFPFFPFFNN
BFFFPFFPFFPFNN.
.LLLLLLLLLLLNN.
..LLLLLLLLLNN..
....NNNNNN.....
""",
}


def _render(name):
	rows = [r.rstrip() for r in GRIDS[name].strip("\n").split("\n")]
	rows = [r.ljust(16, ".")[:16] for r in rows]
	h = len(rows)
	ys = [y for y in range(h) if any(c != "." for c in rows[y])]
	xs = [x for r in rows for x, c in enumerate(r) if c != "."]
	x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
	ox, oy = (16 - (x1 - x0 + 1)) // 2 - x0, (16 - (y1 - y0 + 1)) // 2 - y0
	grid = {}
	for y in range(h):
		for x, c in enumerate(rows[y]):
			if c != "." and 0 <= x + ox < 16 and 0 <= y + oy < 16:
				grid[(x + ox, y + oy)] = c
	return grid


COOKED_WHITE = dict(B=(0x98, 0x6d, 0x4e), F=(0xb1, 0x89, 0x53), L=(0xd6, 0xc5, 0xad), N=(0xc6, 0xa2, 0x71), P=(0x98, 0x6d, 0x4e),
		Q=(0x98, 0x6d, 0x4e), W=(0xd6, 0xc5, 0xad), E=(0x6b, 0x44, 0x2b), K=(0x6b, 0x44, 0x2b))
COOKED_OILY = dict(B=(0xa4, 0x42, 0x22), F=(0xdf, 0x59, 0x0a), L=(0xf5, 0x8a, 0x48), N=(0xf4, 0x6f, 0x20), P=(0xa4, 0x42, 0x22),
		Q=(0xa4, 0x42, 0x22), W=(0xfb, 0xd8, 0xc2), E=(0x69, 0x2e, 0x09), K=(0x69, 0x2e, 0x09))


def icon(sid, pal, cooked=False, fillet="white", cooked_tint=None):
	"""A 16x16 icon of species `sid` from the species' palette (`pal`, 0xRRGGBB values)."""
	grid = _render(sid)
	if cooked:
		if fillet == "shell":
			t = _rgb(cooked_tint or 0xd2401e)
			col = dict(B=t, F=_mix(t, (255, 190, 140), 0.25), L=_mix(t, (255, 225, 190), 0.55), N=_shade(t, 0.8), P=_shade(t, 0.7),
					Q=_shade(t, 0.7), W=_mix(t, (255, 240, 220), 0.6), E=_shade(t, 0.35), K=_shade(t, 0.5))
		else:
			col = dict(COOKED_OILY if fillet == "oily" else COOKED_WHITE)
	else:
		back, flank, belly, fin = (_rgb(pal[k]) for k in ("back", "flank", "belly", "fin"))
		pc = _rgb(pal.get("pc", pal["back"]))
		pc2 = _rgb(pal.get("pc2", pal.get("pc", pal["back"])))
		eye = (22, 20, 18)
		col = dict(B=back, F=flank, L=belly, N=fin, P=pc, Q=pc2, W=(240, 240, 236), E=eye, K=_shade(back, 0.7))
		if sid in ("cavefish", "olm"):
			col["E"] = _shade(flank, 0.55)
		if pal["pattern"] in ("smooth",) and sid not in ("jellyfish",):
			col["P"] = flank
		if sid in ("halibut", "catshark", "dogfish", "pike", "trout", "pollock", "mackerel", "grayling", "char", "ash_molly", "lobster"):
			pass
		if _lum(pc) > _lum(flank) - 12 and _lum(pc) < _lum(flank) + 12:
			col["P"] = _shade(pc, 0.8)
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	px = img.load()
	for (x, y), c in grid.items():
		edge = [(x + dx, y + dy) not in grid for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
		base = col[c]
		if c == "E":
			px[x, y] = base + (255,)
			continue
		if any(edge):
			lit = ((x - 1, y) not in grid or (x, y - 1) not in grid) and not ((x + 1, y) not in grid or (x, y + 1) not in grid)
			ring = _shade(base, 0.62 if lit else 0.45)
			if cooked:
				ring = _shade(base, 0.7 if lit else 0.5)
			px[x, y] = ring + (255,)
		else:
			px[x, y] = base + (255,)
	return img


def _lum(c):
	return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
