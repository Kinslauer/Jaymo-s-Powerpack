"""The Mo' Creatures beasts Kingdoms keeps (kingdoms/src/main/java/com/jaymoskingdoms/monsters/MocBeasts.java, the wyvern in
dragons/Wyverns.java, the winged and horned horses in equestrian/WonderHorses.java):
- the originals' textures (GPL-3, in tools/mocreatures/, see licenses/mocreatures) copied under our names:
  the Nether scorpion, the manticore's four coats, the golem's block sheet and its four glows, the zebra and the zorse,
  and the wyvern's four hides (mountain, arctic, cave, dark: the ones that suit its high crags);
- the wonder horse sheet: the pegasus's wing feathers from the original's pegasus coat, and a horn drawn here;
- our own: the manticore spine, the wyvern egg and the spawn eggs' icons, with their item models and definitions;
- the loot of the animals.
Run from kingdoms/: python3 tools/gen_moc_beasts.py"""
import json, os, random, shutil
from PIL import Image
import sys as _sys
_sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import reason_fold  # noqa: E402
import sys as _sys
_sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import vanilla_egg  # noqa: E402

MOC = 'tools/mocreatures/'
SRC = '/home/user/src/moc/src/main/resources/assets/mocreatures/textures/models/'
ASSETS = 'src/main/resources/assets/jaymoskingdoms/'
DATA = 'src/main/resources/data/jaymoskingdoms/'
ENTITY = ASSETS + 'textures/entity/moc/'
DRAGON = ASSETS + 'textures/entity/dragon/wyvern/'
ITEM = ASSETS + 'textures/item/'

# original -> ours (copied into tools/mocreatures/ the first time, when the original's folder is there)
COPIES = {'scorpionnether': 'scorpion_nether', 'BCmanticore': 'manticore', 'BCmanticoreDark': 'manticore_dark', 'BCmanticoreBlue': 'manticore_blue',
          'BCmanticoreGreen': 'manticore_green', 'golemt': 'golem', 'golemeffect1': 'golem_effect_1', 'golemeffect2': 'golem_effect_2',
          'golemeffect3': 'golem_effect_3', 'golemeffect4': 'golem_effect_4', 'horsezebra': 'zebra', 'horsezorse': 'zorse'}
HIDES = ['wyvernmountain', 'wyvernarctic', 'wyverncave', 'wyverndark']


def original(name):
    local = MOC + name + '.png'
    if not os.path.exists(local) and os.path.exists(SRC + name + '.png'):
        shutil.copyfile(SRC + name + '.png', local)
    return local


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent='\t')
        f.write('\n')


def egg_icon(name, shell, spot, seed, spiky=False):
    """The Reason look's egg: vanilla's spotted egg (vanilla_egg.py) in the beast's two colours."""
    vanilla_egg.save(ITEM + name + '.png', tuple(shell[:3]), tuple(spot[:3]))


def spine_icon():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for i in range(14):
        x, y = 2 + i, 13 - i
        for w, c in ((0, (236, 226, 196)), (1, (184, 160, 120))):
            if 0 <= x + w < 16 and 0 <= y < 16:
                px[x + w, y] = c + (255,)
        if i > 9:
            px[x, y] = (96, 70, 40, 255)
    img.save(ITEM + 'manticore_spine.png')


def item_files(name):
    write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
    write(ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})


def drop(item, lo, hi, looting=True):
    mods = [{'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    if looting:
        mods.append({'type': 'minecraft:enchanted_count_increase', 'count': {'type': 'minecraft:uniform', 'min': 0.0, 'max': 1.0},
                     'enchantment': 'minecraft:looting'})
    return {'type': 'minecraft:item', 'name': item, 'modifier': mods}


def loot(path, kind, *pools):
    write(DATA + 'loot_table/' + path + '.json', {'type': 'minecraft:' + kind, 'pools': [{'rolls': 1, 'entries': [p]} for p in pools]})


if __name__ == '__main__':
    os.makedirs(ENTITY, exist_ok=True)
    os.makedirs(DRAGON, exist_ok=True)
    os.makedirs(ITEM, exist_ok=True)
    for src, dst in COPIES.items():
        shutil.copyfile(original(src), ENTITY + dst + '.png')
    for i, src in enumerate(HIDES):
        img = Image.open(original(src)).convert('RGBA')
        reason_fold.save(img, DRAGON + 'hide_%d.png' % i)
        # (no glowing marks: its original has none; an empty sheet is what the renderer's glow layer draws)
        Image.new('RGBA', img.size, (0, 0, 0, 0)).save(DRAGON + 'marks_%d.png' % i)

    # the wonder horse sheet (256x256, the model's 128x128 at twice the size): wings from the original's pegasus, a horn of ivory
    sheet = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    pegasus = Image.open(original('horsepegasus')).convert('RGBA')
    sheet.paste(pegasus.crop((0, 136, 256, 256)), (0, 136))
    px = sheet.load()
    for y in range(0, 48):
        for x in range(0, 32):
            stripe = (x + y) % 6 < 3
            px[x, y] = (238, 230, 205, 255) if stripe else (206, 192, 160, 255)
    sheet.save(ASSETS + 'textures/entity/wonder_horse.png')

    spine_icon()
    item_files('manticore_spine')
    egg_icon('wyvern_egg', (96, 120, 78), (46, 62, 40), 71, True)
    item_files('wyvern_egg')
    for name, shell, spot, seed in (('nether_scorpion', (122, 38, 30), (36, 12, 12), 72), ('manticore', (196, 128, 52), (122, 54, 24), 73),
                                    ('big_golem', (132, 130, 124), (80, 100, 70), 74), ('zebra', (232, 232, 228), (28, 28, 30), 75),
                                    ('zorse', (176, 128, 84), (40, 30, 24), 76), ('wyvern_dragon', (96, 120, 78), (210, 190, 120), 77)):
        egg_icon(name + '_spawn_egg', shell, spot, seed)
        item_files(name + '_spawn_egg')

    # loot: the animals' own (the golem gives back its blocks as it falls, the wyvern its bones and hides: in their classes)
    loot('entities/zebra', 'entity', drop('minecraft:leather', 0, 2))
    loot('entities/zorse', 'entity', drop('minecraft:leather', 1, 3))
    loot('entities/manticore', 'entity', drop('minecraft:leather', 2, 5), drop('minecraft:bone', 1, 3))
    loot('entities/nether_scorpion', 'entity', drop('minecraft:spider_eye', 0, 2), drop('minecraft:blaze_powder', 0, 1))
    # the clutch in a wyvern's nest: its egg, and what a mother drags home to a nest
    write(DATA + 'loot_table/chests/wyvern_nest.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'jaymoskingdoms:wyvern_egg'}]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 3, 'max': 6}, 'entries': [drop('minecraft:bone', 2, 6, False), drop('minecraft:mutton', 1, 4, False),
                                                                                 drop('minecraft:leather', 1, 3, False), drop('minecraft:feather', 2, 6, False)]}]})
