"""The reaped's art (kingdoms/src/main/java/com/jaymoskingdoms/reaped): dead illagers the Reaper sent back to fight. All our
own, painted here on vanilla's illager model layout (64x64): the reaped pillager, vindicator and evoker, and their three
spawn eggs' icons. (Immersive Pillagers, whose undead illagers gave the idea, is GPL-3.0: nothing of it is used.)
Run from kingdoms/: python3 tools/gen_reaped.py"""
import os, random
from PIL import Image

ASSETS = 'src/main/resources/assets/jaymoskingdoms/'
ENT = ASSETS + 'textures/entity/reaped/'
ITEM = ASSETS + 'textures/item/'
os.makedirs(ENT, exist_ok=True)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def jitter(c, n, rnd):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, ch + k)) for ch in c[:3]) + (255,)


def box(img, u, v, w, h, d, paint):
    """Calls paint(face, x, y, fw, fh) for each pixel of each face of a box's UV layout."""
    faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
             'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}
    px = img.load()
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = paint(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c


# the dead's own colours: grave-grey skin gone green at the edges, bone, the dark where flesh has gone, the Reaper's red
SKIN, ROT, BONE, HOLLOW, RED, RED_HOT = rgb(0x7E8A7A), rgb(0x56634F), rgb(0xCFC8B0), rgb(0x16101A), rgb(0x7A0010), rgb(0xD81828)

# name: (hair, cloth, trim, legs, robe?) : the pillager's rotted leather, the vindicator's torn grey coat, the evoker's black robe
LOOKS = {
    'reaped_pillager': (0x2A2622, 0x4A3A2C, 0x2E241C, 0x33302C, False),
    'reaped_vindicator': (0x2A2622, 0x4C5054, 0x30343A, 0x2A2C30, False),
    'reaped_evoker': (0x3A3836, 0x1C1A20, 0x6E5E2E, 0x18161C, True),
}


def flesh(rnd, share=0.16):
    """Skin, rotted in patches."""
    return jitter(ROT, 5, rnd) if rnd.random() < share else jitter(SKIN, 6, rnd)


def reaped(name, seed):
    hair, cloth, trim, legs, robed = LOOKS[name]
    hair, cloth, trim, legs = rgb(hair), rgb(cloth), rgb(trim), rgb(legs)
    rnd = random.Random(seed)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def head(face, x, y, fw, fh):
        if face == 'top':
            # what's left of the hair, the scalp showing through
            return jitter(hair, 6, rnd) if rnd.random() < 0.6 else flesh(rnd, 0.4)
        if face == 'bottom':
            return jitter(BONE, 6, rnd)
        if face == 'north':
            if y == 3 and 1 <= x <= 6 and x not in (3, 4):
                return jitter(hair, 4, rnd)  # the one heavy brow, broken in the middle
            if y in (4, 5) and x in (1, 2, 5, 6):
                # sunken sockets, a red point far back in each
                return RED_HOT if (y == 4 and x in (2, 5)) else HOLLOW
            if y >= 8:
                # the jaw is bare bone, the teeth showing
                return HOLLOW if (y == 8 and x % 2 == 1 and 1 <= x <= 6) else jitter(BONE, 7, rnd)
            if y == 7 and x in (0, 7):
                return jitter(BONE, 7, rnd)
            return flesh(rnd)
        if y < 2 and rnd.random() < 0.7 or face == 'south' and y < 5 and rnd.random() < 0.75:
            return jitter(hair, 6, rnd)
        if y >= 8 and face != 'south':
            return jitter(BONE, 7, rnd)
        return flesh(rnd, 0.25)
    box(img, 0, 0, 8, 10, 8, head)
    # the nose is gone to a stump of bone
    box(img, 24, 0, 2, 4, 2, lambda f, x, y, fw, fh: jitter(BONE, 6, rnd) if y < 2 else None)

    def coat(face, x, y, fw, fh):
        if face == 'north':
            # a tear over the left ribs: three of them showing, dark between
            if 4 <= x <= 6 and 4 <= y <= 9:
                return jitter(BONE, 6, rnd) if y % 2 == 0 else HOLLOW
            if y < 1 or x in (3,) and y < 4:
                return jitter(trim, 6, rnd)
        if face in ('east', 'west', 'south') and y < 1:
            return jitter(trim, 6, rnd)
        if y >= fh - 2 and rnd.random() < 0.3:
            return jitter(trim, 6, rnd)
        c = jitter(cloth, 6, rnd)
        # grave dirt
        return jitter(rgb(0x3A3026), 5, rnd) if rnd.random() < 0.08 else c
    box(img, 16, 20, 8, 12, 6, coat)

    if robed:
        def robe(face, x, y, fw, fh):
            if face in ('top', 'bottom'):
                return jitter(cloth, 5, rnd)
            if y >= fh - 5 and rnd.random() < 0.16 * (y - fh + 6):
                return (0, 0, 0, 0)  # the hem in ribbons
            if face == 'north' and x in (3, 4) and y < fh - 5:
                return jitter(trim, 7, rnd) if rnd.random() < 0.8 else jitter(cloth, 5, rnd)  # the tarnished band, worn through
            if face == 'north' and 4 <= x <= 6 and 4 <= y <= 9:
                return jitter(BONE, 6, rnd) if y % 2 == 0 else HOLLOW
            return jitter(cloth, 5, rnd)
        box(img, 0, 38, 8, 20, 6, robe)

    def sleeve(face, x, y, fw, fh):
        if y >= fh - 2 and rnd.random() < 0.4:
            return (0, 0, 0, 0)  # frayed cuffs
        return jitter(cloth, 6, rnd)
    box(img, 44, 22, 4, 8, 4, sleeve)

    def crossed(face, x, y, fw, fh):
        if face == 'north' and (x < 2 or x > fw - 3):
            return jitter(BONE, 6, rnd)  # the hands are bone
        return jitter(cloth, 6, rnd)
    box(img, 40, 38, 8, 4, 4, crossed)

    def leg(face, x, y, fw, fh):
        if y >= fh - 3:
            return jitter(rgb(0x1E1A18), 4, rnd)  # boots
        if face == 'north' and x in (1, 2) and 3 <= y <= 5:
            return jitter(BONE, 6, rnd)  # a knee worn through
        return jitter(legs, 5, rnd)
    box(img, 0, 22, 4, 12, 4, leg)

    def arm(face, x, y, fw, fh):
        if face == 'bottom' or y >= fh - 2:
            return jitter(BONE, 6, rnd)  # bone hands
        if y >= fh - 5:
            # the forearm: flesh hanging off the bone, the sleeve ending in rags
            return jitter(cloth, 6, rnd) if y == fh - 5 and rnd.random() < 0.5 else flesh(rnd, 0.35)
        return jitter(cloth, 6, rnd)
    box(img, 40, 46, 4, 12, 4, arm)
    img.save(ENT + name + '.png')


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
    px[8, 7] = RED
    img.save(ITEM + name + '_spawn_egg.png')


if __name__ == '__main__':
    for n, key in enumerate(LOOKS):
        reaped(key, 4100 + n)
        egg(key, LOOKS[key][1], 0xCFC8B0)
