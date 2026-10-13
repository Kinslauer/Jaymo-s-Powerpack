"""Textures for the monsters of legend (kingdoms/src/main/java/com/jaymoskingdoms/monsters):
- the Mo' Creatures ones (GPL-3, originals in tools/mocreatures/) copied under our names (and the nightmare's and the bat
  horse's coats among the horses'), and the cave ogre: the green
  ogre's skin turned slate grey (its loincloth, teeth, horns and rings as they are);
- our own: the chest mimic, the mud golem (plain and mossy) and the silver sword's icon, drawn here.
Run from kingdoms/: python3 tools/gen_legends.py"""
import colorsys, os, random, shutil
from PIL import Image

MOC = 'tools/mocreatures/'
OUT = 'src/main/resources/assets/jaymoskingdoms/textures/entity/legend/'
ITEM = 'src/main/resources/assets/jaymoskingdoms/textures/item/'
os.makedirs(OUT, exist_ok=True)

COPIES = {'wolfblack': 'werewolf_black', 'wolfbrown': 'werewolf_brown', 'wolftimber': 'werewolf_timber', 'wolffire1': 'werewolf_fire_1',
          'wolffire2': 'werewolf_fire_2', 'wolffire3': 'werewolf_fire_3', 'weredude': 'werewolf_dude', 'werehuman': 'werewolf_human',
          'wereoldie': 'werewolf_oldie', 'werewoman': 'werewolf_woman', 'ogregreen': 'ogre_green', 'ogrered': 'ogre_fire', 'wraith': 'wraith',
          'flamewraith': 'flame_wraith', 'silverskeleton': 'silver_skeleton'}
for src, dst in COPIES.items():
    shutil.copyfile(MOC + src + '.png', OUT + dst + '.png')

# the mounts of legend, as horse breeds' coats (textures/entity/moc_horse/<breed>/<coat>.png)
HORSES = 'src/main/resources/assets/jaymoskingdoms/textures/entity/moc_horse/'
for src, dst in {'horsenightmare': 'nightmare/nightmare', 'horsenightmare1': 'nightmare/nightmare_1', 'horsebat': 'bat_horse/bat'}.items():
    os.makedirs(os.path.dirname(HORSES + dst), exist_ok=True)
    shutil.copyfile(MOC + src + '.png', HORSES + dst + '.png')

# the cave ogre: green skin to slate
ogre = Image.open(MOC + 'ogregreen.png').convert('RGBA')
px = ogre.load()
for y in range(ogre.height):
    for x in range(ogre.width):
        r, g, b, a = px[x, y]
        if a == 0:
            continue
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if 0.17 < h < 0.45 and s > 0.15:
            r2, g2, b2 = colorsys.hsv_to_rgb(0.6, s * 0.22, v * 0.9)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
ogre.save(OUT + 'ogre_cave.png')


def box(img, u, v, w, h, d, paint):
    """Calls paint(face, x, y, fw, fh) for each pixel of each face of a box's UV layout (face: top, bottom, east, north,
    west, south)."""
    faces = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
             'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h)}
    px = img.load()
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                c = paint(face, x, y, fw, fh)
                if c is not None:
                    px[fx + x, fy + y] = c


def jitter(c, n, rnd):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, ch + k)) for ch in c[:3]) + (255,)


# the mimic: oak boards banded with dark iron, a red maw, ivory teeth, a tongue
rnd = random.Random(7)
mimic = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
WOOD, WOOD_DARK, IRON, MAW, TOOTH, TONGUE = (150, 104, 56), (110, 74, 38), (58, 56, 60), (92, 18, 22), (232, 222, 196), (176, 60, 72)


def wood(face, x, y, fw, fh, inside):
    if inside:
        return jitter(MAW, 10, rnd)
    edge = x == 0 or y == 0 or x == fw - 1 or y == fh - 1
    if edge:
        return jitter(IRON, 6, rnd)
    plank = (y if face in ('north', 'south', 'east', 'west') else x) % 4 == 0
    return jitter(WOOD_DARK if plank else WOOD, 8, rnd)


box(mimic, 0, 0, 14, 5, 14, lambda f, x, y, w, h: wood(f, x, y, w, h, f == 'bottom'))   # lid: its underside is the maw
box(mimic, 0, 19, 14, 10, 14, lambda f, x, y, w, h: wood(f, x, y, w, h, f == 'top'))    # body: its top is the maw
box(mimic, 56, 0, 2, 4, 1, lambda f, x, y, w, h: jitter((200, 196, 186) if (f == 'north' and y == 1) else (150, 148, 140), 6, rnd))


def teeth(face, x, y, fw, fh):
    if face in ('top', 'bottom'):
        return jitter(TOOTH, 6, rnd)
    # pointed: each tooth two pixels wide, full at the root row, a point below
    if y == 0 or x % 2 == 0:
        return jitter(TOOTH, 8, rnd)
    return None


for u, v, w, h, d in ((64, 0, 12, 2, 1), (64, 4, 12, 2, 1), (64, 8, 1, 2, 11), (90, 8, 1, 2, 11)):
    box(mimic, u, v, w, h, d, teeth)
box(mimic, 64, 24, 4, 1, 8, lambda f, x, y, w, h: jitter(TONGUE if (x + y) % 5 else (150, 44, 56), 6, rnd))
mimic.save(OUT + 'mimic.png')


# the mud golem: wet mud, cracked and lumpy, two amber eyes; the mossy one grown over with moss and roots
def mud_golem(mossy, seed):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    MUD, MUD_DARK, MUD_DRY, MOSS, ROOT, EYE = (84, 62, 44), (58, 42, 30), (122, 98, 70), (78, 104, 46), (96, 72, 40), (255, 178, 60)

    def paint(part):
        def p(face, x, y, fw, fh):
            r = rnd.random()
            c = MUD_DARK if r < 0.12 else MUD_DRY if r < 0.2 else MUD
            if mossy and (face == 'top' or (y < 2 and face != 'bottom')) and rnd.random() < 0.7:
                c = MOSS
            if mossy and face != 'top' and rnd.random() < 0.04:
                c = ROOT
            if part == 'head' and face == 'north' and y == 4 and x in (1, 2, fw - 3, fw - 2):
                return EYE + (255,)
            if part == 'head' and face == 'north' and y == 5 and x in (1, 2, fw - 3, fw - 2):
                return (200, 120, 30, 255)
            return jitter(c, 7, rnd)
        return p

    box(img, 0, 0, 14, 12, 9, paint('torso'))
    box(img, 0, 21, 10, 4, 8, paint('belly'))
    box(img, 0, 33, 8, 7, 8, paint('head'))
    box(img, 32, 0, 9, 2, 2, paint('brow'))
    box(img, 36, 21, 5, 18, 6, paint('arm'))
    box(img, 0, 48, 6, 10, 6, paint('leg'))
    return img


mud_golem(False, 11).save(OUT + 'mud_golem.png')
mud_golem(True, 12).save(OUT + 'mud_golem_mossy.png')

# the silver sword's icon: a pale blade with a bright edge, a dark guard, a wrapped grip
SWORD = ["..............OO",
         ".............OEO",
         "............OEBO",
         "...........OEBO.",
         "..........OEBO..",
         ".........OEBO...",
         "........OEBO....",
         ".......OEBO.....",
         "......OEBO......",
         ".GG..OEBO.......",
         ".GGGOEBO........",
         "..GGGBO.........",
         "...HGGG.........",
         "..HH.GG.........",
         ".HH.............",
         "PP.............."]
COLOURS = {'O': (40, 44, 56), 'E': (250, 252, 255), 'B': (196, 206, 222), 'G': (70, 66, 80), 'H': (92, 60, 40), 'P': (190, 196, 210)}
sword = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
for y, row in enumerate(SWORD):
    for x, ch in enumerate(row):
        if ch in COLOURS:
            sword.putpixel((x, y), COLOURS[ch] + (255,))
sword.save(ITEM + 'silver_sword.png')
