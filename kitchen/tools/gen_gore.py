"""The insides of a body blown apart or eaten (corpse/Gibs, client GibRenderer) as a Blockbench model:
tools/bbmodels/gore-pieces.bbmodel, groups laid out in rows (organ, guts, ribs, spine, bone, tatters; then the variants: heart,
liver, lung, kidney, brain matter, skull; long gut rope, gut loop, gut heap, jaw), each
about its own middle, with one painted 64x64 texture. The soft pieces (organ, guts) are painted in greys because the
game tints them with the body's own blood colour; bone is painted as it is; the tatters' swatch is plain cloth, used
only when the game can't cut the scrap from the body's own skin (their top and bottom faces are mapped flat over the
swatch so the game can lay any patch of a skin over the whole scrap).

This script writes the first version. Once the model has been edited by hand in Blockbench, don't run it again (it
would overwrite the edits): only bake, from the repo root:
  python3 -I tools/bbmodel_bake.py tools/bbmodels/gore-pieces.bbmodel kitchen/src/main/resources/assets/jaymoskitchen gore
Run from the repo root: python3 -I kitchen/tools/gen_gore.py   (writes the .bbmodel and bakes it)"""
import base64, io, json, math, os, random, subprocess, sys, uuid
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..', '..')
OUT = os.path.join(ROOT, 'tools', 'bbmodels', 'gore-pieces.bbmodel')
ASSETS = os.path.join(ROOT, 'kitchen', 'src', 'main', 'resources', 'assets', 'jaymoskitchen')
SIZE = 64
# light to dark
ORGAN = [(236, 236, 236), (204, 204, 204), (166, 166, 166), (124, 124, 124), (92, 92, 92)]
GUT = [(255, 255, 255), (236, 236, 236), (208, 208, 208), (172, 172, 172), (140, 140, 140)]
BONE = [(252, 251, 237), (230, 226, 207), (197, 192, 165), (160, 154, 128), (122, 116, 96)]
BRAIN = [(236, 204, 202), (220, 182, 182), (196, 154, 158), (166, 124, 130), (132, 94, 102)]
CLOTH = [(156, 138, 110), (139, 122, 96), (116, 100, 78), (92, 78, 60), (70, 58, 44)]
BLOOD = [(125, 31, 32), (90, 20, 22)]
MARROW = [(120, 44, 40), (88, 28, 28)]
# the tatters' swatch: their top and bottom faces lie flat over it
SWATCH = (0, 56, 8, 64)
rng = random.Random(1486)
uid = lambda: str(uuid.UUID(int=rng.getrandbits(128)))


class Sheet:
    """The texture: faces take rectangles off shelves, left to right, top to bottom."""
    def __init__(self):
        self.img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
        self.x = self.y = self.row = 0

    def take(self, w, h):
        if self.x + w > SIZE:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        assert self.y + h <= SWATCH[1], 'texture full'
        at = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return at


def paint(sheet, at, w, h, look, face):
    palette, style = look
    shade = {'up': -1, 'down': 1}.get(face, 0)
    for j in range(h):
        for i in range(w):
            k = 1 + shade + rng.choice((-1, 0, 0, 0, 1))
            colour = palette[max(0, min(len(palette) - 1, k))]
            if style == 'organ' and rng.random() < 0.14:
                colour = palette[-1]
            elif style == 'gut' and i % 2 == 1 and w > 1:
                # a gut's coils: a darker ring every other pixel along it
                colour = palette[min(len(palette) - 1, k + 2)]
            elif style == 'bone' and rng.random() < 0.07:
                colour = rng.choice(BLOOD)
            elif style == 'brain' and rng.random() < 0.3:
                # a brain's folds: darker crevices
                colour = palette[min(len(palette) - 1, k + 2)]
            elif style == 'brain' and rng.random() < 0.05:
                colour = rng.choice(BLOOD)
            elif style == 'marrow':
                colour = rng.choice(MARROW)
            sheet.img.putpixel((at[0] + i, at[1] + j), colour + (255,))


class Piece:
    def __init__(self, name, x, z=0):
        self.name, self.x, self.z, self.cubes = name, x, z, []

    def cube(self, lo, size, rot=(0, 0, 0), look=None, name=None, faces=None):
        hi = [lo[i] + size[i] for i in range(3)]
        self.cubes.append({'lo': list(lo), 'hi': hi, 'rot': list(rot), 'look': look, 'name': name or self.name, 'faces': faces or {}})

    def rod(self, a, b, thick, look, axis='y', faces=None):
        """A bar from a to b: along x, then turned about y (a rope lying flat) or about z (a rib arching)."""
        length = math.dist(a, b)
        mid = [(a[i] + b[i]) / 2 for i in range(3)]
        if axis == 'y':
            rot = (0, math.degrees(math.atan2(-(b[2] - a[2]), b[0] - a[0])), 0)
        else:
            rot = (0, 0, math.degrees(math.atan2(b[1] - a[1], b[0] - a[0])))
        self.cube((mid[0] - length / 2, mid[1] - thick / 2, mid[2] - thick / 2), (length, thick, thick), rot, look, faces=faces)


def rotate(p, o, r):
    x, y, z = (p[i] - o[i] for i in range(3))
    rx, ry, rz = (math.radians(v) for v in r)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return [x + o[0], y + o[1], z + o[2]]


def organ():
    p = Piece('organ', -30)
    look = (ORGAN, 'organ')
    p.cube((-1.5, 0, -1.5), (3, 2.4, 3), (0, 8, 0), look)
    p.cube((-2.1, 0.2, 1.2), (2, 2, 2), (0, 15, 0), look)
    p.cube((0.1, 0.3, 1.3), (2, 2, 2), (0, -15, 0), look)
    p.cube((-1, 0.3, -2.5), (2, 1.6, 1.2), (0, 0, 0), look)
    p.cube((-0.6, 0.8, 3), (1, 1, 1.6), (0, 20, 0), (GUT, 'plain'))
    p.cube((0.7, 0.9, 2.8), (0.8, 0.8, 1.4), (0, -25, 0), (GUT, 'plain'))
    return p


def guts():
    p = Piece('guts', -18)
    path = [(-3.5, 0, -2), (-1, 0.2, -2.6), (1.5, 0, -2), (2.8, 0.3, -0.6), (1.2, 0.5, 0.4), (-1.2, 0.2, 0), (-2.8, 0, 1),
            (-1.6, 0.4, 2.4), (0.8, 0.1, 2.6), (3, 0, 2)]
    for a, b in zip(path, path[1:]):
        a, b = (a[0], a[1] + 0.8, a[2]), (b[0], b[1] + 0.8, b[2])
        # each length a little long, so the rope has no gaps at its bends
        d = [(b[i] - a[i]) / math.dist(a, b) * 0.4 for i in range(3)]
        p.rod([a[i] - d[i] for i in range(3)], [b[i] + d[i] for i in range(3)], 1.6, (GUT, 'gut'))
    return p


def ribs():
    p = Piece('ribs', -6)
    look = (BONE, 'bone')
    p.cube((-0.5, 3.1, -3), (1, 0.8, 6), (0, 0, 0), look)
    arch = [(0.4, 3.5), (2.3, 3.2), (3.4, 1.9), (3.7, 0.3)]
    for z in (-2.5, -1, 0.5, 2):
        for side in (1, -1):
            for a, b in zip(arch, arch[1:]):
                p.rod((side * a[0], a[1], z), (side * b[0], b[1], z), 0.8, look, 'z')
    return p


def spine():
    p = Piece('spine', 6)
    look = (BONE, 'bone')
    for i in range(5):
        z = -3.6 + i * 1.5
        lift = 0.5 * math.sin(i * 0.9)
        turn = (6 * math.cos(i * 1.3), 5 * math.sin(i * 1.7), 0)
        p.cube((-0.8, lift, z), (1.6, 1.4, 1.1), turn, look)
        p.cube((-0.3, lift + 1.2, z + 0.1), (0.6, 1.2, 0.8), (-25 + turn[0], turn[1], 0), look)
        p.cube((-1.6, lift + 0.5, z + 0.2), (0.8, 0.5, 0.6), turn, look)
        p.cube((0.8, lift + 0.5, z + 0.2), (0.8, 0.5, 0.6), turn, look)
        if i < 4:
            p.cube((-0.35, lift + 0.4, z + 1.0), (0.7, 0.7, 0.6), (0, 0, 0), (GUT, 'plain'))
    return p


def bone():
    p = Piece('bone', 18)
    look = (BONE, 'bone')
    p.cube((-0.45, 0.15, -2.4), (0.9, 0.9, 4.6), (0, 4, 0), look)
    p.cube((-1.1, 0, 1.9), (1.1, 1.2, 1.2), (0, 12, 0), look)
    p.cube((0, 0, 2.0), (1.1, 1.2, 1.1), (0, -10, 0), look)
    # the broken end: splinters, and the marrow showing
    p.cube((-0.45, 0.2, -3.4), (0.45, 0.5, 1.2), (10, 14, 0), look, faces={'north': (MARROW, 'marrow')})
    p.cube((0.05, 0.5, -3.0), (0.4, 0.45, 0.8), (-8, -10, 0), look, faces={'north': (MARROW, 'marrow')})
    return p


def tatters():
    p = Piece('tatters', 30)
    look = (CLOTH, 'cloth')
    p.cube((-2.6, 0, -1.4), (5.2, 0.2, 2.8), (0, 8, 0), look)
    p.cube((-3.2, 0.05, 0.6), (2.2, 0.2, 3.2), (0, -12, 4), look)
    p.cube((1.2, 0.05, -4.0), (1.4, 0.2, 2.9), (0, 20, -3), look)
    p.cube((0.4, 0.02, 1.0), (1.3, 0.2, 1.7), (0, 6, 0), look)
    p.cube((-1.4, 0.03, -2.6), (1.1, 0.2, 1.5), (0, -18, 0), look)
    return p


# ---- the variants (Andy, 2026-11-19: "there should be more entrail variations"), in rows behind the first six ----

def rope(p, path, thick, lift=0.8):
    """A rope of gut along {@code path}, each length a little long so it has no gaps at its bends."""
    for a, b in zip(path, path[1:]):
        a, b = (a[0], a[1] + lift, a[2]), (b[0], b[1] + lift, b[2])
        d = [(b[i] - a[i]) / math.dist(a, b) * 0.4 for i in range(3)]
        p.rod([a[i] - d[i] for i in range(3)], [b[i] + d[i] for i in range(3)], thick, (GUT, 'gut'))


def heart():
    p = Piece('heart', -30, 14)
    look = (ORGAN, 'organ')
    p.cube((-1.5, 0, -1.4), (3, 3.2, 2.8), (0, 10, 0), look)
    p.cube((-1.0, 0.1, 1.2), (2, 2, 1.6), (0, 10, 0), look)
    p.cube((-0.5, 0.3, 2.6), (1, 1.2, 1), (0, 10, 0), look)
    # the great vessels, cut short
    p.cube((-1.0, 3.0, -0.9), (0.9, 1.2, 0.9), (0, 0, 8), (GUT, 'plain'))
    p.cube((0.3, 2.9, -0.4), (0.8, 1.0, 0.8), (0, 0, -10), (GUT, 'plain'))
    return p


def liver():
    p = Piece('liver', -18, 14)
    look = (ORGAN, 'organ')
    p.cube((-2.6, 0, -1.6), (3.6, 1.6, 3.2), (0, 6, 0), look)
    p.cube((0.8, 0, -1.2), (2.4, 1.2, 2.4), (0, -14, 0), look)
    p.cube((-3.4, 0, -0.8), (1.2, 1.0, 1.8), (0, 10, 0), look)
    p.cube((0.2, 0.9, 0.9), (0.9, 0.8, 1.1), (0, 20, 0), (GUT, 'plain'))
    return p


def lung():
    p = Piece('lung', -6, 14)
    look = (GUT, 'organ')
    p.cube((-2.4, 0, -2.2), (2.2, 1.8, 4.4), (0, 8, 0), look)
    p.cube((0.3, 0, -1.8), (2.0, 1.6, 3.8), (0, -8, 0), look)
    p.cube((-0.4, 0.5, -3.6), (0.8, 0.8, 1.8), (0, 0, 0), (GUT, 'gut'))
    return p


def kidney():
    p = Piece('kidney', 6, 14)
    look = (ORGAN, 'organ')
    for side, turn in ((-1.6, 12), (1.0, -18)):
        p.cube((side - 0.6, 0, -1.3), (1.3, 1.1, 2.6), (0, turn, 0), look)
        p.cube((side + 0.4, 0.05, -0.5), (0.7, 0.9, 1.0), (0, turn, 0), (GUT, 'plain'))
    return p


def brain():
    """Brain matter: little pale lumps like pebbles, with chips of skull among them (painted, never tinted)."""
    p = Piece('brain', 18, 14)
    look = (BRAIN, 'brain')
    for x, z, s, turn in ((-1.8, -1.2, 1.5, 10), (0.6, -1.8, 1.1, -20), (1.4, 0.6, 1.3, 30), (-0.8, 1.2, 0.9, -5),
                          (-2.6, 0.8, 0.8, 40), (2.4, -0.6, 0.7, 15)):
        p.cube((x - s / 2, 0, z - s / 2), (s, s * 0.7, s), (0, turn, 0), look)
    for x, z, turn in ((0.2, 0.2, 25), (-2.0, -2.6, -30)):
        p.cube((x, 0, z), (0.7, 0.3, 0.5), (0, turn, 0), (BONE, 'bone'))
    return p


def skull():
    """A piece of a crushed skull: a curved plate of bone, bloodied inside, with a jagged edge."""
    p = Piece('skull', 30, 14)
    look = (BONE, 'bone')
    p.cube((-1.4, 1.4, -1.6), (2.8, 0.6, 3.2), (0, 0, 0), look, faces={'down': (BLOOD, 'plain')})
    p.cube((-2.5, 0.3, -1.4), (1.4, 0.6, 2.8), (0, 0, -40), look, faces={'down': (BLOOD, 'plain')})
    p.cube((1.1, 0.3, -1.4), (1.4, 0.6, 2.8), (0, 0, 40), look, faces={'down': (BLOOD, 'plain')})
    p.cube((-0.6, 1.5, 1.4), (1.0, 0.5, 0.8), (0, 25, 0), look)
    p.cube((0.2, 1.5, -2.3), (0.9, 0.5, 0.9), (0, -20, 0), look)
    return p


def guts_rope():
    """A long rope of gut, trailing loose."""
    p = Piece('guts_rope', -30, 28)
    path = [(-7 + i * 1.75, 0.15 * math.sin(i), 1.3 * math.sin(i * 0.9)) for i in range(9)]
    rope(p, path, 1.4)
    return p


def guts_loop():
    """A loop of gut that came out whole, its ends trailing."""
    p = Piece('guts_loop', -12, 28)
    path = [(-5.5, 0, -0.5), (-3.5, 0.1, -2.6), (-0.5, 0.2, -3.4), (2.5, 0.1, -2.6), (4.0, 0, -0.4), (2.6, 0.2, 1.8),
            (-0.4, 0.4, 2.4), (-2.8, 0.2, 1.4), (-1.6, 0.6, -0.8), (1.2, 0.5, -0.6), (3.0, 0.2, 3.6), (5.5, 0, 4.2)]
    rope(p, path, 1.5)
    return p


def guts_skein():
    """A heap of gut, folded back and forth on itself."""
    p = Piece('guts_skein', 8, 28)
    path = []
    for row in range(5):
        z = -3 + row * 1.5
        xs = (-3.5, 3.5) if row % 2 == 0 else (3.5, -3.5)
        y = 0.35 * (row % 3)
        path += [(xs[0], y, z), (xs[0] * 0.2, y + 0.3, z + 0.3), (xs[1], y, z)]
    rope(p, path, 1.3)
    return p


def jaw():
    """A broken jaw with its teeth."""
    p = Piece('jaw', 26, 28)
    look = (BONE, 'bone')
    p.cube((-1.5, 0, -2.2), (3.0, 0.9, 0.9), (0, 0, 0), look)
    p.rod((-1.4, 0.45, -1.9), (-2.6, 0.45, 1.4), 0.9, look)
    p.rod((1.4, 0.45, -1.9), (2.2, 0.45, 0.6), 0.9, look, faces={'south': (MARROW, 'marrow')})
    for x in (-1.1, -0.4, 0.3, 1.0):
        p.cube((x, 0.9, -2.1), (0.45, 0.5, 0.45), (0, 0, 0), (BONE[:2], 'plain'))
    return p


FACES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def build():
    sheet = Sheet()
    elements, outliner = [], []
    # the cloth swatch: a weave, bloodied at one corner
    for j in range(SWATCH[1], SWATCH[3]):
        for i in range(SWATCH[0], SWATCH[2]):
            k = 1 + ((i + j) % 2) + rng.choice((0, 0, 1))
            colour = rng.choice(BLOOD) if (i - SWATCH[0]) + (SWATCH[3] - 1 - j) < 3 or rng.random() < 0.06 else CLOTH[min(4, k)]
            sheet.img.putpixel((i, j), colour + (255,))
    # (the first six as they always were, so their paint stays the same; the variants after them)
    for piece in (organ(), guts(), ribs(), spine(), bone(), tatters(), heart(), liver(), lung(), kidney(), brain(), skull(),
                  guts_rope(), guts_loop(), guts_skein(), jaw()):
        # its bounds as it lies, turns and all, so the group sits about its middle
        corners = []
        for c in piece.cubes:
            mid = [(c['lo'][i] + c['hi'][i]) / 2 for i in range(3)]
            for x in (c['lo'][0], c['hi'][0]):
                for y in (c['lo'][1], c['hi'][1]):
                    for z in (c['lo'][2], c['hi'][2]):
                        corners.append(rotate((x, y, z), mid, c['rot']))
        lo = [min(p[i] for p in corners) for i in range(3)]
        hi = [max(p[i] for p in corners) for i in range(3)]
        # lifted so it stands on the ground in Blockbench, moved along x to its own place
        move = (piece.x - (lo[0] + hi[0]) / 2, -lo[1], piece.z - (lo[2] + hi[2]) / 2)
        children = []
        for c in piece.cubes:
            f = [round(c['lo'][i] + move[i], 4) for i in range(3)]
            t = [round(c['hi'][i] + move[i], 4) for i in range(3)]
            faces = {}
            for face, (ua, va) in FACES.items():
                if piece.name == 'tatters' and face in ('up', 'down'):
                    # flat over the swatch, by where the strip lies in the whole scrap
                    u0 = SWATCH[0] + (c['lo'][0] - lo[0]) / (hi[0] - lo[0]) * (SWATCH[2] - SWATCH[0])
                    u1 = SWATCH[0] + (c['hi'][0] - lo[0]) / (hi[0] - lo[0]) * (SWATCH[2] - SWATCH[0])
                    v0 = SWATCH[1] + (c['lo'][2] - lo[2]) / (hi[2] - lo[2]) * (SWATCH[3] - SWATCH[1])
                    v1 = SWATCH[1] + (c['hi'][2] - lo[2]) / (hi[2] - lo[2]) * (SWATCH[3] - SWATCH[1])
                    faces[face] = {'uv': [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)], 'texture': 0}
                    continue
                if piece.name == 'tatters':
                    # a strip's thin edge: a sliver of the swatch
                    u0 = SWATCH[0] + (c['lo'][0] - lo[0]) / (hi[0] - lo[0]) * (SWATCH[2] - SWATCH[0])
                    v0 = SWATCH[1] + (c['lo'][2] - lo[2]) / (hi[2] - lo[2]) * (SWATCH[3] - SWATCH[1])
                    faces[face] = {'uv': [round(u0, 3), round(v0, 3), round(u0 + 1, 3), round(v0 + 0.25, 3)], 'texture': 0}
                    continue
                w = max(1, round(c['hi'][ua] - c['lo'][ua]))
                h = max(1, round(c['hi'][va] - c['lo'][va]))
                at = sheet.take(w, h)
                paint(sheet, at, w, h, c['faces'].get(face, c['look']), face)
                faces[face] = {'uv': [at[0], at[1], at[0] + w, at[1] + h], 'texture': 0}
            mid = [round((f[i] + t[i]) / 2, 4) for i in range(3)]
            element = {'name': c['name'], 'box_uv': False, 'rescale': False, 'locked': False, 'from': f, 'to': t, 'autouv': 0,
                       'color': 0, 'origin': mid, 'rotation': [round(v, 3) for v in c['rot']], 'faces': faces, 'type': 'cube',
                       'uuid': uid()}
            elements.append(element)
            children.append(element['uuid'])
        origin = [piece.x, round((hi[1] - lo[1]) / 2, 4), piece.z]
        outliner.append({'name': piece.name, 'origin': origin, 'rotation': [0, 0, 0], 'color': 0, 'uuid': uid(), 'export': True,
                         'isOpen': False, 'locked': False, 'visibility': True, 'autouv': 0, 'children': children})
    data = io.BytesIO()
    sheet.img.save(data, 'PNG')
    texture = {'name': 'gore.png', 'folder': '', 'namespace': '', 'id': '0', 'particle': False, 'visible': True, 'mode': 'bitmap',
               'saved': True, 'uuid': uid(), 'source': 'data:image/png;base64,' + base64.b64encode(data.getvalue()).decode()}
    return {'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False}, 'name': 'gore-pieces',
            'resolution': {'width': SIZE, 'height': SIZE}, 'elements': elements, 'outliner': outliner, 'textures': [texture]}


if __name__ == '__main__':
    with open(OUT, 'w') as out:
        json.dump(build(), out)
    subprocess.check_call([sys.executable, '-I', os.path.join(ROOT, 'tools', 'bbmodel_bake.py'), OUT, ASSETS, 'gore'])
