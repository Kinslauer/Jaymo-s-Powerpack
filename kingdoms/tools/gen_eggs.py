"""The dragon eggs (kingdoms/src/main/java/com/jaymoskingdoms/dragons/DragonEggs.java): one per lesser wind dragon, each
tinted to its species (Notus ember red, Boreas ice blue, Zephyrus swamp green, Eurus storm violet). Writes the 16x16
icon, the item model and the item definition of each.
Run from kingdoms/: python3 tools/gen_eggs.py"""
import os, random, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import sys as _sys
_sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
import vanilla_egg  # noqa: E402
from gen_dragons import rgb, jitter, mix, write, ASSETS, ITEM

# species -> (shell, dark speckle, bright speckle)
EGGS = {
    'notus': (0x9A3A22, 0x4E160C, 0xFFA030),
    'boreas': (0x7FB6DE, 0x3C6A92, 0xE4F6FF),
    'zephyrus': (0x5E8A3A, 0x2C4A1A, 0xB6E84A),
    'eurus': (0x7A5CC2, 0x3A2870, 0xD8C4FF),
}


def egg(name, shell, dark, bright, seed):
    """The Reason look's egg: vanilla's spotted egg (vanilla_egg.py) in the shell and its bright speckle."""
    vanilla_egg.save(ITEM + name + '.png', rgb(shell)[:3], rgb(bright)[:3])


if __name__ == '__main__':
    for d in (ITEM, ASSETS + 'items/', ASSETS + 'models/item/'):
        os.makedirs(d, exist_ok=True)
    for i, (species, (shell, dark, bright)) in enumerate(EGGS.items()):
        name = species + '_dragon_egg'
        egg(name, shell, dark, bright, 300 + i)
        write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
        write(ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
