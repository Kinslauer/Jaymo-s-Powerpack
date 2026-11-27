"""The Reason look's clean-up for script-painted textures (docs/Vanilla-Style.md, "Blocks, mobs and worn armour").

Script-made skins and icons got their texture from a per-pixel jitter: every pixel a hair lighter or darker than its
neighbours, so a face of one material holds dozens of near-twin colours. This takes that jitter out and keeps what was
drawn on purpose:

- smooth(img): a pixel that differs from the middle of its 3x3 neighbourhood by less than JITTER (a hair) takes that
  middle colour; a bigger difference is an edge, an eye, a stripe or a seam, and stays.
- ramps(img, n): folds the colours into at most n (median cut), so every material ends up as a few clear steps.
- clean(img, n): both. Transparent and half-transparent pixels keep their alpha.

    python -I tools/vanilla_style/reason_clean.py <n> <png>...     cleans the files in place

Use it only on our own art or on art whose licence allows changes (Cube Animals CC0, MIT mods); other mods' textures
get fresh paintings instead.
"""
import sys

from PIL import Image

JITTER = 14


def _lum(c):
	return c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114


def smooth(img):
	img = img.convert("RGBA")
	w, h = img.size
	src = img.load()
	out = img.copy()
	dst = out.load()
	for y in range(h):
		for x in range(w):
			c = src[x, y]
			if c[3] == 0:
				continue
			near = [src[x + dx, y + dy] for dx in (-1, 0, 1) for dy in (-1, 0, 1)
				if 0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3] > 0]
			if len(near) < 5:
				continue
			near.sort(key=_lum)
			mid = near[len(near) // 2]
			if 0 < abs(_lum(c) - _lum(mid)) < JITTER and max(abs(c[i] - mid[i]) for i in range(3)) < JITTER * 1.5:
				dst[x, y] = mid[:3] + (c[3],)
	return out


def ramps(img, n):
	img = img.convert("RGBA")
	alpha = img.getchannel("A")
	few = img.convert("RGB").quantize(n, method=Image.Quantize.MEDIANCUT).convert("RGBA")
	few.putalpha(alpha)
	return few


def clean(img, n):
	return ramps(smooth(img), n)


if __name__ == "__main__":
	n = int(sys.argv[1])
	for path in sys.argv[2:]:
		clean(Image.open(path), n).save(path)
