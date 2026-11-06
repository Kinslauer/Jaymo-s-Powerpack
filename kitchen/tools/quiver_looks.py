# Repaints the Kitchen back-quiver texture in each RPG Series quiver's colours (palette only; no pixels copied).
import sys
from PIL import Image
src, outdir = sys.argv[1], sys.argv[2]
base = Image.open(src).convert('RGBA')
LEVELS = ['281c12', '462c18', '683e20', '8c5830', '966e3c', 'b27a46']  # the leather's shades, dark to light
h = lambda s: tuple(int(s[i:i+2], 16) for i in (0, 2, 4))
LOOKS = {
    # tube/cap leather, rim, strap
    'small_quiver':  (['1c1616', '2f1a17', '3d2321', '4a2f26', '5a3e31', '6d4534'],
                      ['1c1616', '2f1a17', '3d2321', '5a4020', '896727', 'a8853a'],
                      ['3a3434', '4e4848', '6b6464', '7a6e6e', '897a7a', 'a89c94']),
    'medium_quiver': (['2c1f1c', '412e1c', '593927', '6d4e30', '805d3b', 'a8773c'],
                      ['2c1f1c', '412e1c', '6d4e30', '8a6a2a', 'a87a2c', 'd0a03a'],
                      ['3a3434', '4e4848', '6b6464', '7a6e6e', '897a7a', 'c0b4a8']),
    'large_quiver':  (['0e2e2b', '123d3a', '194e47', '1f5a4b', '24664f', '3a8068'],
                      ['241f20', '343436', '4c4d4f', '706770', '87898a', 'babfc0'],
                      ['241f20', '343436', '4c4d4f', '5d565d', '766a76', '87898a']),
}
def region(x, y):
    if x >= 28 or (24 <= x < 28 and y >= 20):
        return None            # arrow shafts and fletching stay as they are
    if 20 <= x < 24 and y < 18:
        return 2               # strap
    if 15 <= y < 21:
        return 1               # rim
    return 0                   # tube and end cap
for name, ramps in LOOKS.items():
    out = base.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            hexc = '%02x%02x%02x' % (r, g, b)
            reg = region(x, y)
            if a == 0 or reg is None or hexc not in LEVELS:
                continue
            px[x, y] = h(ramps[reg][LEVELS.index(hexc)]) + (a,)
    out.save(f'{outdir}/{name}.png')
