"""The reaped's art (kingdoms/src/main/java/com/jaymoskingdoms/reaped): dead illagers the Reaper sent back to fight. All our
own, painted here for our own model (the client's ReapedModel: an illager's frame gone to bone, its arms hanging, an
illager's skull with its heavy brow, the clothes it died in hanging off it in rags; 96x64): the reaped pillager, vindicator
and evoker, and their three spawn eggs' icons. The outfits follow vanilla's three illagers so each can be told at a glance.
(Immersive Pillagers, whose undead illagers gave the idea, is GPL-3.0: nothing of it is used.)
Run from kingdoms/: python3 tools/gen_reaped.py [preview.png]"""
import math, os, random, sys
from PIL import Image

ASSETS = 'src/main/resources/assets/jaymoskingdoms/'
ENT = ASSETS + 'textures/entity/reaped/'
ITEM = ASSETS + 'textures/item/'
os.makedirs(ENT, exist_ok=True)
W, H = 96, 64

# The model's boxes, as ReapedModel.createBodyLayer lays them (keep the two in step): name -> (u, v, x, y, z, w, h, d,
# inflate, mirrored copy's x or None). Model space: y down, the feet at 24.
BOXES = {
    'skull': (0, 0, -4, -10, -4, 8, 8, 8, 0, None),
    'jaw': (0, 16, -3, -2, -3.5, 6, 2, 6, 0, None),
    'brow': (32, 0, -4, -7, -5, 8, 1, 1, 0, None),
    'nose': (32, 4, -1, -5, -5, 2, 2, 1, 0, None),
    'coat': (0, 24, -4, 0, -3, 8, 12, 6, 0.25, None),
    'ribs': (28, 24, -3, 0, -2, 6, 11, 4, 0, None),
    'robe': (64, 0, -4, 0, -3, 8, 18, 6, 0.5, None),
    'sleeve': (48, 24, -8, 0, -2, 4, 7, 4, 0, 4),
    'arm': (28, 38, -7, 0, -1, 2, 12, 2, 0, 5),
    'trouser': (0, 42, -4, 12, -2, 4, 8, 4, 0, 0),
    'shin': (36, 38, -3, 12, -1, 2, 12, 2, 0, 1),
    'boot': (44, 38, -4, 21, -2, 4, 3, 4, 0.1, 0),
}


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def jitter(c, n, rnd):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, ch + k)) for ch in c[:3]) + (255,)


def faces(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}


def box(img, name, paint):
    """Calls paint(face, x, y, fw, fh) for each pixel of each face of the named box's UV layout."""
    u, v, _, _, _, w, h, d, _, _ = BOXES[name]
    px = img.load()
    for face, (fx, fy, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = paint(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c


BONE, BONE_SHADE, HOLLOW, RED_HOT, GONE = rgb(0xD8D2BC), rgb(0xA39B84), rgb(0x16101A), rgb(0xE01C2C), (0, 0, 0, 0)
GRAVE = rgb(0x4A4032)

# name: coat, coat's front, collar, sleeve, cuff, trousers, boots, robe, robe's trim (the last two None for no robe)
LOOKS = {
    # the plaid shirt, the black belt with its buckle, teal sleeves, brown boots
    'reaped_pillager': dict(coat=0x3A1C22, front=0x2A1218, collar=0x707575, sleeve=0x1F3636, cuff=0x76432A, trouser=0x1E2A2A, boot=0x76432A, robe=None, trim=None),
    # the grey-brown jacket open on its maroon front, the pale collar, teal trousers
    'reaped_vindicator': dict(coat=0x3F3B37, front=0x43292C, collar=0x848989, sleeve=0x373330, cuff=0x2C2927, trouser=0x214D4C, boot=0x2C2927, robe=None, trim=None),
    # the black robe with its gold band and hem over the grey tunic
    'reaped_evoker': dict(coat=0x424C4E, front=0x394244, collar=0x848989, sleeve=0x1E1C1A, cuff=0xD8B84A, trouser=0x333B3C, boot=0x6B6B6B, robe=0x1E1F24, trim=0xD8B84A),
}


def bone(rnd, shade=0.14):
    return jitter(BONE_SHADE, 6, rnd) if rnd.random() < shade else jitter(BONE, 6, rnd)


def reaped(name, seed):
    look = {k: (rgb(c) if c is not None else None) for k, c in LOOKS[name].items()}
    rnd = random.Random(seed)
    img = Image.new('RGBA', (W, H), GONE)

    def skull(face, x, y, fw, fh):
        if face == 'north':
            if y in (4, 5) and x in (1, 2, 5, 6):
                # deep sockets under the brow, a red point far back in each
                return RED_HOT if (y == 4 and x in (2, 5)) else HOLLOW
            if y == 6 and x in (3, 4):
                return HOLLOW  # where the great nose was
            if y == 3:
                return jitter(BONE_SHADE, 5, rnd)  # under the brow ridge
            if y == 7:
                return HOLLOW if x in (0, 7) else (jitter(BONE, 4, rnd) if x % 2 == 0 else jitter(BONE_SHADE, 4, rnd))  # the upper teeth
            if (x, y) in ((5, 0), (5, 1), (6, 2)):
                return jitter(BONE_SHADE, 4, rnd)  # a crack down the forehead
            return bone(rnd, 0.08)
        if face in ('east', 'west'):
            if y in (4, 5) and 2 <= x <= 4:
                return jitter(BONE_SHADE, 6, rnd)  # the temple's hollow
            if y == 7 and x >= fw - 3 and face == 'east' or y == 7 and x <= 2 and face == 'west':
                return HOLLOW if x % 2 else jitter(BONE, 4, rnd)
        if face == 'bottom':
            return HOLLOW if 1 <= x <= 6 and 1 <= y <= 6 else jitter(BONE_SHADE, 5, rnd)
        return bone(rnd)
    box(img, 'skull', skull)

    def jaw(face, x, y, fw, fh):
        if face in ('north', 'east', 'west') and y == 0:
            return jitter(BONE, 4, rnd) if x % 2 == 0 else jitter(BONE_SHADE, 4, rnd)  # the lower teeth
        if face == 'top':
            return HOLLOW if 1 <= x <= 4 and y >= 1 else jitter(BONE_SHADE, 5, rnd)
        return bone(rnd, 0.25)
    box(img, 'jaw', jaw)
    box(img, 'brow', lambda f, x, y, fw, fh: bone(rnd, 0.3))
    box(img, 'nose', lambda f, x, y, fw, fh: HOLLOW if f == 'north' and y == 1 else bone(rnd, 0.3))

    def ribs(face, x, y, fw, fh):
        if face in ('top', 'bottom'):
            return HOLLOW
        if face == 'south':
            return bone(rnd) if x in (2, 3) or y % 2 == 0 and y < 9 else HOLLOW  # the spine, the ribs coming round
        if y >= 9:
            # the hips
            return bone(rnd) if face == 'north' and (x in (0, 1, 4, 5) or y == 10) else (bone(rnd) if face != 'north' and y == 9 else HOLLOW)
        if face == 'north' and x in (2, 3):
            return bone(rnd) if y < 6 else HOLLOW  # the breastbone
        return bone(rnd) if y % 2 == 0 else HOLLOW
    box(img, 'ribs', ribs)

    def torn(x, y, fw, fh, edge, share):
        """Whether the cloth is gone here: ragged along the bottom {@code edge} rows, and holed here and there."""
        return y >= fh - edge and rnd.random() < 0.3 * (y - fh + edge + 1) or rnd.random() < share

    def coat(face, x, y, fw, fh):
        if face == 'bottom':
            return GONE
        if face == 'top':
            return jitter(look['coat'], 5, rnd)
        if face == 'north':
            # a great tear over the left ribs
            if 5 <= x <= 7 and 3 <= y <= 8 or x == 4 and 5 <= y <= 7:
                return GONE
            if torn(x, y, fw, fh, 3, 0.03):
                return GONE
            if y < 3 and abs(x - 3.5) < 2 - y * 0.5:
                return jitter(look['collar'], 5, rnd)  # the collar's point
            if x in (3, 4):
                if name == 'reaped_vindicator' and y % 3 == 1:
                    return rgb(0x9A9C98)  # buttons
                return jitter(look['front'], 5, rnd)
            if name == 'reaped_pillager':
                if y in (9, 10):
                    return rgb(0x8FA0A0) if x in (2,) and y == 9 else jitter(rgb(0x232323), 4, rnd)  # the belt and its buckle
                if x + y in (5, 6) and y < 9:
                    return jitter(rgb(0x76432A), 6, rnd)  # the strap across the chest
                return jitter(look['front'] if (x + y) % 2 else look['coat'], 4, rnd)  # plaid
            return jitter(look['coat'], 5, rnd)
        if torn(x, y, fw, fh, 3, 0.05):
            return GONE
        if name == 'reaped_pillager':
            if y in (9, 10):
                return jitter(rgb(0x232323), 4, rnd)
            return jitter(look['front'] if (x + y) % 2 else look['coat'], 4, rnd)
        c = jitter(look['coat'], 5, rnd)
        return jitter(GRAVE, 5, rnd) if rnd.random() < 0.07 else c
    box(img, 'coat', coat)

    if look['robe'] is not None:
        def robe(face, x, y, fw, fh):
            if face == 'bottom':
                return GONE
            if face == 'top':
                return jitter(look['robe'], 4, rnd)
            if face == 'north':
                if 5 <= x <= 7 and 3 <= y <= 8 or x == 4 and 5 <= y <= 7:
                    return GONE  # the same tear, through robe and tunic both
                if y < 4 and abs(x - 3.5) < 2.5 - y * 0.6:
                    return GONE  # the robe's open neck
            if torn(x, y, fw, fh, 6, 0.03):
                return GONE
            if face == 'north' and x in (3, 4) or y == fh - 7:
                return jitter(look['trim'], 8, rnd) if rnd.random() < 0.8 else jitter(look['robe'], 4, rnd)  # the gold band, worn through
            return jitter(GRAVE, 5, rnd) if rnd.random() < 0.05 else jitter(look['robe'], 4, rnd)
        box(img, 'robe', robe)

    def sleeve(face, x, y, fw, fh):
        if face == 'bottom':
            return GONE
        if face != 'top' and torn(x, y, fw, fh, 3, 0.05):
            return GONE
        if y == fh - 4 and look['cuff'] is not None and name == 'reaped_evoker':
            return jitter(look['cuff'], 8, rnd)
        return jitter(look['sleeve'], 5, rnd)
    box(img, 'sleeve', sleeve)

    def limb(face, x, y, fw, fh):
        if face in ('top', 'bottom'):
            return jitter(BONE_SHADE, 5, rnd)
        if y in (5, 6):
            return jitter(BONE_SHADE, 5, rnd) if y == 5 else HOLLOW  # the joint
        if y >= fh - 2:
            return bone(rnd, 0.4)  # the hand, the foot
        return bone(rnd)
    box(img, 'arm', limb)
    box(img, 'shin', limb)

    def trouser(face, x, y, fw, fh):
        if face == 'bottom':
            return GONE
        if face != 'top' and torn(x, y, fw, fh, 3, 0.04):
            return GONE
        return jitter(look['trouser'], 5, rnd)
    box(img, 'trouser', trouser)

    def boot(face, x, y, fw, fh):
        if face == 'top':
            return HOLLOW if 1 <= x <= 2 and 1 <= y <= 2 else jitter(look['boot'], 5, rnd)
        if face == 'north' and y == 2 and x == 1:
            return jitter(BONE, 5, rnd)  # a toe through the leather
        return jitter(look['boot'], 6, rnd)
    box(img, 'boot', boot)
    img.save(ENT + name + '.png')
    return img


def egg(name, base, spot):
    """A spawn egg's icon, 16x16: an egg in the look's cloth with bone spots and one red one."""
    rnd = random.Random(name)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    rows = {2: (6, 9), 3: (5, 10), 4: (4, 11), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (3, 12), 9: (3, 12), 10: (3, 12), 11: (4, 11),
            12: (4, 11), 13: (6, 9)}
    spots = {(6, 4), (9, 6), (5, 8), (10, 10), (7, 11), (8, 3), (11, 8), (4, 6)}
    for y, (a, b) in rows.items():
        for x in range(a, b + 1):
            c = jitter(rgb(spot), 6, rnd) if (x, y) in spots else jitter(rgb(base), 6, rnd)
            if x == a or x == b or y in (2, 13):
                c = tuple(int(ch * 0.62) for ch in c[:3]) + (255,)
            elif x == a + 1 and y < 8:
                c = tuple(min(255, int(ch * 1.25)) for ch in c[:3]) + (255,)
            px[x, y] = c
    px[7, 7] = RED_HOT
    px[8, 7] = rgb(0x7A0010)
    img.save(ITEM + name + '_spawn_egg.png')


# ---- a preview: the model drawn from two sides, box by box, far faces first -------------------------------------------------

def preview(sheets, path, scale=14):
    views = [math.radians(a) for a in (155, 20)]
    cell = 32 * scale
    out = Image.new('RGBA', (cell * len(sheets) * len(views), 40 * scale), rgb(0x3A4450))
    for si, (name, tex) in enumerate(sheets):
        robed = LOOKS[name]['robe'] is not None
        for vi, yaw in enumerate(views):
            quads = []
            for part, (u, v, x, y, z, w, h, d, inf, mx) in BOXES.items():
                if part == 'robe' and not robed:
                    continue
                for x0 in ([x] if mx is None else [x, mx]):
                    f = faces(u, v, w, h, d)
                    a, b, c = (x0 - inf, y - inf, z - inf), (x0 + w + inf, y + h + inf, z + d + inf), None
                    corners = {
                        'north': [(b[0], a[1], a[2]), (a[0], a[1], a[2]), (a[0], b[1], a[2])],
                        'south': [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (b[0], b[1], b[2])],
                        'east': [(a[0], a[1], a[2]), (a[0], a[1], b[2]), (a[0], b[1], b[2])],
                        'west': [(b[0], a[1], b[2]), (b[0], a[1], a[2]), (b[0], b[1], a[2])],
                        'top': [(b[0], a[1], b[2]), (a[0], a[1], b[2]), (a[0], a[1], a[2])],
                    }
                    for face, pts in corners.items():
                        fx, fy, fw, fh = f[face]
                        img = tex.crop((fx, fy, fx + fw, fy + fh))
                        if mx is not None and x0 == mx:
                            img = img.transpose(Image.FLIP_LEFT_RIGHT)
                        proj = []
                        depth = 0.0
                        for (px_, py_, pz_) in pts:
                            rx = px_ * math.cos(yaw) - pz_ * math.sin(yaw)
                            rz = px_ * math.sin(yaw) + pz_ * math.cos(yaw)
                            proj.append((rx, py_ + rz * 0.28))
                            depth += rz
                        quads.append((depth / 3.0 + (-0.2 if part in ('ribs', 'arm', 'shin') else 0.2 if part == 'robe' else 0), img, proj, face))
            quads.sort(key=lambda q: q[0])
            ox, oy = (si * len(views) + vi) * cell + cell // 2, 13 * scale
            for _, img, (p0, p1, p2), face in quads:
                # p0 = top-left, p1 = top-right, p2 = bottom-right of the face as it's painted
                (x0, y0), (x1, y1), (x2, y2) = [(ox + p[0] * scale, oy + p[1] * scale) for p in (p0, p1, p2)]
                ex, ey, fx_, fy_ = (x1 - x0) / img.width, (y1 - y0) / img.width, (x2 - x1) / img.height, (y2 - y1) / img.height
                det = ex * fy_ - fx_ * ey
                if det <= 1e-6:
                    continue  # edge on, or facing away
                shade = {'top': 1.0, 'north': 0.9, 'south': 0.9}.get(face, 0.72)
                img = Image.merge('RGBA', [ch.point(lambda v, s=shade: int(v * s)) for ch in img.split()[:3]] + [img.split()[3]])
                ia, ib, id_, ie = fy_ / det, -fx_ / det, -ey / det, ex / det
                layer = img.transform(out.size, Image.AFFINE, (ia, ib, -(ia * x0 + ib * y0), id_, ie, -(id_ * x0 + ie * y0)), Image.NEAREST)
                out.alpha_composite(layer)
    out.save(path)


if __name__ == '__main__':
    sheets = []
    for n, key in enumerate(LOOKS):
        sheets.append((key, reaped(key, 4100 + n)))
        egg(key, LOOKS[key]['coat'] if LOOKS[key]['robe'] is None else LOOKS[key]['robe'], 0xD8D2BC)
    if len(sys.argv) > 1:
        preview(sheets, sys.argv[1])
