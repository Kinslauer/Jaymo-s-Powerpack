"""The dragon eggs (kingdoms/src/main/java/com/jaymoskingdoms/dragons/DragonEggs.java): one per lesser wind dragon, each
tinted to its species (Notus ember red, Boreas ice blue, Zephyrus swamp green, Eurus storm violet). Writes the 16x16
icon, the item model and the item definition of each.
Run from kingdoms/: python3 tools/gen_eggs.py"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from gen_dragons import rgb, jitter, mix, write, ASSETS, ITEM

# species -> (shell, dark speckle, bright speckle)
EGGS = {
    'notus': (0x9A3A22, 0x4E160C, 0xFFA030),
    'boreas': (0x7FB6DE, 0x3C6A92, 0xE4F6FF),
    'zephyrus': (0x5E8A3A, 0x2C4A1A, 0xB6E84A),
    'eurus': (0x7A5CC2, 0x3A2870, 0xD8C4FF),
}


def egg(name, shell, dark, bright, seed):
    rnd = random.Random(seed)
    shell, dark, bright = rgb(shell), rgb(dark), rgb(bright)
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.2, (y - 8.0) / 7.2
            # an egg: narrower at the top
            if dx * dx * (1.0 + 0.3 * (8.0 - y) / 7.2) + dy * dy <= 1.0:
                c = shell
                r = rnd.random()
                if r < 0.12:
                    c = dark
                elif r < 0.2:
                    c = bright
                # light from the top-left, shade at the lower right
                light = 0.3 * (1.0 - (x + y) / 22.0)
                c = mix(c, rgb(0xFFFFFF) if light > 0.12 else rgb(0x000000), abs(light - 0.12) * 0.9)
                px[x, y] = jitter(c, 5, rnd)
    img.save(ITEM + name + '.png')


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/'):
        os.makedirs(d, exist_ok=True)
    for i, (species, (shell, dark, bright)) in enumerate(EGGS.items()):
        name = species + '_dragon_egg'
        egg(name, shell, dark, bright, 300 + i)
        write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
        write(ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
