"""The Reason look's last step for a painted skin (docs/Vanilla-Style.md): the generator has already dropped its per-pixel
jitter, and this folds what is left (smooth noise fields, painted gradients, strands) into at most N colours, so a hide is
a few clear tones with its scales, stripes and marks on top, as vanilla's mobs are. It is tools/vanilla_style/reason_clean.py
(a jitter smoother, then a median-cut fold); alpha is kept.

    save(img, path, n=32)       folds img and writes it
    fold_files(n, paths...)     folds PNGs in place
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "tools", "vanilla_style"))
import reason_clean  # noqa: E402
from PIL import Image  # noqa: E402

COLOURS = 32


def fold(img, n=COLOURS):
	return reason_clean.clean(img, n)


def save(img, path, n=COLOURS):
	fold(img, n).save(path)


def fold_files(n, paths):
	for p in paths:
		save(Image.open(p), p, n)


if __name__ == "__main__":
	fold_files(int(sys.argv[1]), sys.argv[2:])
