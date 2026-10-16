"""Tiny pixel canvas for vanilla-style icons built from parts. Each pixel stores (part ramp, level).
finish() outlines every part in its own darkest shade (lighter on the lit top-left), like vanilla."""
from PIL import Image, ImageDraw

class Canvas:
    def __init__(self):
        self.p = {}            # (x, y) -> (ramp, level)
        self.part = 0; self.pid = {}  # (x, y) -> id of the part that drew it; later parts sit in front
    def new_part(self): self.part += 1
    def px(self, x, y, ramp, lvl):
        if 0 <= x < 16 and 0 <= y < 16: self.p[(x, y)] = (ramp, lvl); self.pid[(x, y)] = self.part
    def rect(self, x0, y0, x1, y1, ramp, lvl):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): self.px(x, y, ramp, lvl)
    def poly(self, pts, ramp, lvl):
        m = Image.new('1', (16, 16)); ImageDraw.Draw(m).polygon(pts, fill=1)
        for y in range(16):
            for x in range(16):
                if m.getpixel((x, y)): self.px(x, y, ramp, lvl)
    def line(self, x0, y0, x1, y1, ramp, lvl):
        dx, dy = abs(x1 - x0), -abs(y1 - y0); sx = 1 if x0 < x1 else -1; sy = 1 if y0 < y1 else -1; e = dx + dy
        while True:
            self.px(x0, y0, ramp, lvl)
            if (x0, y0) == (x1, y1): break
            e2 = 2 * e
            if e2 >= dy: e += dy; x0 += sx
            if e2 <= dx: e += dx; y0 += sy
    def grid(self, x0, y0, rows, legend):
        """stamp a small hand-drawn grid; legend maps char -> (ramp, level)"""
        for j, r in enumerate(rows):
            for i, c in enumerate(r):
                if c in legend: self.px(x0 + i, y0 + j, *legend[c])
    def finish(self, ramps, outline=True):
        im = Image.new('RGBA', (16, 16)); out = im.load()
        filled = set(self.p)
        for (x, y), (ramp, lvl) in self.p.items():
            r = ramps[ramp]
            if outline:
                n = {(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))}
                if not n <= filled:
                    lit = (x - 1, y) not in filled or (x, y - 1) not in filled
                    dark = (x + 1, y) not in filled or (x, y + 1) not in filled
                    lvl = 1 if lit and not dark else 0
                elif any(self.pid.get((x + dx, y + dy), 1e9) < self.pid[(x, y)] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    lvl = min(lvl, 1)   # a part's edge against something behind it: vanilla's inner outline
            out[x, y] = r[max(0, min(lvl, len(r) - 1))] + (255,)
        return im
