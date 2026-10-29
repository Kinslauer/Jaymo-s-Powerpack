"""The second batch of Blockbench Workshop creatures' data (kingdoms/src/main/java/com/jaymoskingdoms/beasts): their
spawn eggs' icons and item models, and their loot tables. Their models and textures come from tools/bbmodel_bake.py
(credits in textures/entity/bb/CREDITS.txt).
Run from kingdoms/: python3 tools/gen_beasts.py"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_nether import ASSETS, entry, egg, rgb, table, write  # noqa: E402

EGGS = {'minotaur_spawn_egg': (0x5A3B28, 0xD4A640), 'piglin_king_spawn_egg': (0xC9877A, 0xE8B840),
        'redstone_golem_spawn_egg': (0x8A8A8A, 0xE0201A), 'skulk_golem_spawn_egg': (0x1F2A33, 0x2FB5B5),
        'wild_boar_spawn_egg': (0x4A3326, 0xD8C8A8), 'scalebane_spawn_egg': (0x3E6B3A, 0xC8A050)}


def rare(item, chance):
    """One {@code item}, {@code chance} of the time."""
    return {'type': 'minecraft:item', 'name': item, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': chance}]}


def loot():
    table('minotaur', [entry('minecraft:leather', 2, 4), entry('minecraft:beef', 2, 5), entry('minecraft:gold_ingot', 1, 3),
                       entry('minecraft:bone', 1, 3)])
    table('piglin_king', [entry('minecraft:gold_ingot', 6, 12), entry('minecraft:gold_block', 1, 2, False),
                          rare('minecraft:netherite_scrap', 0.5), rare('minecraft:piglin_head', 1.0)])
    table('redstone_golem', [entry('minecraft:redstone', 6, 14), entry('minecraft:redstone_block', 0, 2), rare('minecraft:diamond', 0.25)])
    table('skulk_golem', [entry('minecraft:sculk', 2, 5), entry('minecraft:cobbled_deepslate', 1, 3), rare('minecraft:echo_shard', 0.3)])
    table('wild_boar', [entry('minecraft:porkchop', 1, 3), entry('minecraft:leather', 0, 1)])
    table('scalebane', [entry('minecraft:arrow', 0, 3), entry('minecraft:bone', 0, 2), rare('minecraft:turtle_scute', 0.15)])


if __name__ == '__main__':
    for i, (name, (base, spots)) in enumerate(EGGS.items()):
        egg(name, rgb(base), rgb(spots), 140 + i)
        write(ASSETS + 'items/' + name + '.json', {'model': {'type': 'minecraft:model', 'model': 'jaymoskingdoms:item/' + name}})
        write(ASSETS + 'models/item/' + name + '.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'jaymoskingdoms:item/' + name}})
    loot()
