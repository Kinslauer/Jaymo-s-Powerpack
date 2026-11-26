"""The Reason look's spawn egg (docs/Vanilla-Style.md): vanilla's spotted egg in a mob's two colours.

One egg shape and one spot pattern for every mob, so the eggs read as a set: the egg's body in the first colour on
four shades (lit upper-left, plain, shaded lower-right, outline), a ring of spots in the second colour on three, every
shade a clear step from the next and nothing blended. Light from the top-left; the outline is the body's darkest shade,
a step lighter on the lit side.

    egg(base, spot)           -> a 16x16 RGBA image (colours as (r, g, b) tuples)
    save(path, base, spot)    writes it
    restyle(path)             redraws an egg a generator just wrote, in the two colours it painted it with
                              (its commonest colour and the colour furthest from it): the hook each generator calls
                              after saving, so a rerun gives the Reason look's egg and not the old speckle
"""
import sys

from PIL import Image

# the egg: X body, O spot place; vanilla's egg is 12 wide at the belly, 14 high, narrower at the top
BODY = (
	"................",
	"......XXXX......",
	".....XXXXXX.....",
	"....XXXXXXXX....",
	"...XXXXXXXXXX...",
	"...XXXXXXXXXX...",
	"..XXXXXXXXXXXX..",
	"..XXXXXXXXXXXX..",
	"..XXXXXXXXXXXX..",
	"..XXXXXXXXXXXX..",
	"..XXXXXXXXXXXX..",
	"..XXXXXXXXXXXX..",
	"...XXXXXXXXXX...",
	"....XXXXXXXX....",
	".....XXXXXX.....",
	"................",
)
SPOTS = {(5, 4), (6, 4), (5, 5), (9, 5), (10, 5), (10, 6), (7, 7), (8, 7), (4, 8), (5, 8), (4, 9), (10, 9), (11, 9), (11, 10),
	(7, 10), (8, 10), (7, 11), (6, 6)}
# how far each shade sits from the colour: toward white (+) or black (-)
LIT, SHADE, DARK = 0.3, -0.22, -0.55


def _mix(c, k):
	t = 255 if k > 0 else 0
	return tuple(int(round(v + (t - v) * abs(k))) for v in c)


def egg(base, spot):
	body = {(x, y) for y, row in enumerate(BODY) for x, ch in enumerate(row) if ch == "X"}
	img = Image.new("RGBA", (16, 16))
	px = img.load()
	for x, y in body:
		edge = [(x + dx, y + dy) not in body for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
		colour = spot if (x, y) in SPOTS else base
		if any(edge):
			# the outline: lit on the upper-left edge, darkest on the lower-right
			k = DARK * (0.6 if (x + y) < 12 else 1.0)
		elif x + y <= 10 and (x - 4) + (y - 3) <= 4:
			k = LIT
		elif x + y >= 19 or (x >= 10 and y >= 9):
			k = SHADE
		else:
			k = 0
		px[x, y] = _mix(colour, k) + (255,)
	return img


def save(path, base, spot):
	egg(base, spot).save(path)


def _two_colours(img):
	px = [p[:3] for p in img.convert("RGBA").getdata() if p[3]]
	counts = {}
	for p in px:
		counts[p] = counts.get(p, 0) + 1
	# smooth jitter away first: bucket to 24s, then the mean colour of each bucket
	buckets = {}
	for p, n in counts.items():
		buckets.setdefault(tuple(v // 24 for v in p), []).append((p, n))
	means = []
	for items in buckets.values():
		total = sum(n for _, n in items)
		means.append((tuple(sum(p[i] * n for p, n in items) // total for i in range(3)), total))
	means.sort(key=lambda m: -m[1])
	base = means[0][0]
	rest = [m for m in means[1:] if m[1] >= 3] or means[1:] or [(base, 1)]
	spot = max(rest, key=lambda m: sum((m[0][i] - base[i]) ** 2 for i in range(3)) * m[1] ** 0.5)[0]
	return base, spot


def restyle(path):
	img = Image.open(path)
	base, spot = _two_colours(img)
	egg(base, spot).save(path)


if __name__ == "__main__":
	for p in sys.argv[1:]:
		restyle(p)
