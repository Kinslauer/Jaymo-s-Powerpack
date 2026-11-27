#!/usr/bin/env python3
"""Writes the data of Hunter's Road patch 6, part B: crucible recipes, essences, tags, the refill enchantment, formula trades,
recipes, and the blocks' blockstates, models, item definitions and loot tables. Run from the repo root."""
import json
import os

P = 'src/main/resources/'
D = P + 'data/jaymospowers/'
A = P + 'assets/jaymospowers/'


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


# ---- crucible: draughts in three grades, monster brews, toxic paste --------------------------------------------------
DRAUGHTS = {
    'slow_mending': {'life': 6, 'plant': 3, 'earth': 2},
    'grey_sight': {'shadow': 5, 'mind': 4, 'light': 2},
    'savagery': {'war': 5, 'fire': 3, 'beast': 3},
    'keen_edge': {'metal': 5, 'war': 4, 'spark': 2},
    'deep_breath': {'air': 5, 'water': 5, 'life': 3},
    'thorned_blood': {'death': 4, 'life': 4, 'metal': 3, 'spirit': 2},
    'thin_venom': {'venom': 4, 'salt': 5, 'water': 3},
    'plenty': {'life': 4, 'plant': 5, 'earth': 3},
    'cleanser': {'salt': 6, 'water': 5, 'light': 3},
}
GRADES = [('plain', 1.0, ''), ('fine', 1.5, '_fine'), ('expert', 2.2, '_expert')]
MONSTERS = {
    'marsh_fury': {'venom': 8, 'rot': 6, 'beast': 4, 'war': 4},
    'grave_hunger': {'death': 8, 'undeath': 6, 'beast': 4, 'life': 3},
    'pack_marrow': {'beast': 8, 'death': 4, 'life': 5, 'rot': 3},
    'fog_shroud': {'spirit': 8, 'water': 6, 'air': 4, 'shadow': 4},
    'imp_gallop': {'fire': 7, 'beast': 7, 'motion': 6, 'war': 3},
    'troll_blood': {'life': 10, 'earth': 6, 'beast': 5, 'rot': 3},
}
ALL_FORMULAS = list(DRAUGHTS) + list(MONSTERS)


def recipe(path, catalyst, essences, item, research, grade=''):
    result = {'item': item, 'count': 1}
    if grade:
        result['grade'] = grade
    put(path, {'catalyst': catalyst, 'essences': essences, 'result': result, 'research': research})


for name, base in DRAUGHTS.items():
    for grade, mult, suffix in GRADES:
        recipe(D + 'alchemy/crucible/draught_%s%s.json' % (name, suffix), 'minecraft:glass_bottle',
               {k: max(1, round(v * mult)) for k, v in base.items()}, 'jaymospowers:draught_' + name, 'formula_' + name, grade)
for name, ess in MONSTERS.items():
    recipe(D + 'alchemy/crucible/brew_%s.json' % name, 'minecraft:glass_bottle', ess, 'jaymospowers:brew_' + name, 'formula_' + name)
recipe(D + 'alchemy/crucible/toxic_paste.json', 'jaymospowers:poison_paste', {'venom': 8, 'rot': 6, 'fire': 4},
       'jaymospowers:toxic_paste', 'poison_pastes')

# ---- essences of the new items -----------------------------------------------------------------------------------------
put(D + 'alchemy/essences/hunters_alchemy.json', {'items': {
    'jaymospowers:poison_paste': {'venom': 3, 'rot': 2},
    'jaymospowers:toxic_paste': {'venom': 6, 'rot': 4, 'fire': 2},
    'jaymospowers:pure_toxin': {'venom': 8, 'salt': 3},
}})

# ---- tags -------------------------------------------------------------------------------------------------------------
put(D + 'tags/item/strong_spirits.json', {'values': [{'id': 'jaymoskitchen:strong_spirits', 'required': False}]})
put(D + 'tags/item/poison_pastes.json', {'values': ['jaymospowers:poison_paste', 'jaymospowers:toxic_paste', 'jaymospowers:pure_toxin']})
put(D + 'tags/item/enchantable/hunters_flask.json', {'values': ['jaymospowers:hunters_flask', 'jaymospowers:netherite_hunters_flask']})

# ---- the refill enchantment (it fills a flask overnight; the knowledge gate is in code) -------------------------------------
put(D + 'enchantment/refill.json', {
    'anvil_cost': 4,
    'description': {'translate': 'enchantment.jaymospowers.refill'},
    'effects': {},
    'max_cost': {'base': 45, 'per_level_above_first': 25},
    'max_level': 1,
    'min_cost': {'base': 20, 'per_level_above_first': 25},
    'slots': ['mainhand', 'offhand'],
    'supported_items': '#jaymospowers:enchantable/hunters_flask',
    'weight': 2,
})

# ---- formulas for sale from the wandering trader -----------------------------------------------------------------------
trades = []
for i, name in enumerate(ALL_FORMULAS):
    put(D + 'villager_trade/hunter/wandering_trader/formula_%s.json' % name, {
        'gives': {'id': 'jaymospowers:formula', 'components': {'jaymospowers:formula': name}},
        'max_uses': 1,
        'reputation_discount': 0.05,
        'wants': {'id': 'minecraft:emerald', 'count': 8 + (i % 4) * 4 + (6 if name in MONSTERS else 0)},
    })
    trades.append('jaymospowers:hunter/wandering_trader/formula_' + name)

tag_path = P + 'data/minecraft/tags/villager_trade/wandering_trader/uncommon.json'
tag = json.load(open(tag_path))
tag['values'] = [v for v in tag['values'] if '/formula_' not in v] + trades
put(tag_path, tag)

# ---- recipes ----------------------------------------------------------------------------------------------------------
R = D + 'recipe/hunter/'
put(R + 'testing_needle.json', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
    'ingredients': ['minecraft:iron_nugget', 'minecraft:glass_pane', 'minecraft:copper_ingot'], 'result': {'id': 'jaymospowers:testing_needle', 'count': 1}})
put(R + 'hunters_flask.json', {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': [' L ', 'CBC', ' C '],
    'key': {'L': 'minecraft:leather', 'C': 'minecraft:copper_ingot', 'B': 'minecraft:glass_bottle'}, 'result': {'id': 'jaymospowers:hunters_flask', 'count': 1}})
put(R + 'netherite_hunters_flask.json', {'type': 'minecraft:smithing_transform', 'template': 'minecraft:netherite_upgrade_smithing_template',
    'base': 'jaymospowers:hunters_flask', 'addition': '#minecraft:netherite_tool_materials', 'result': {'id': 'jaymospowers:netherite_hunters_flask'}})
put(R + 'alchemy_bench.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['CGC', 'PPP', 'P P'],
    'key': {'C': 'minecraft:copper_ingot', 'G': 'minecraft:glass_pane', 'P': '#minecraft:planks'}, 'result': {'id': 'jaymospowers:alchemy_bench', 'count': 1}})
put(R + 'vial_rack.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['CCC', 'B B', 'CCC'],
    'key': {'C': 'minecraft:copper_ingot', 'B': 'minecraft:glass_bottle'}, 'result': {'id': 'jaymospowers:vial_rack', 'count': 1}})
put(R + 'bell_jar.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': [' C ', 'G G', 'CCC'],
    'key': {'C': 'minecraft:copper_ingot', 'G': 'minecraft:glass'}, 'result': {'id': 'jaymospowers:bell_jar', 'count': 1}})
put(R + 'copper_scale.json', {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': ['CCC', ' C ', ' C '],
    'key': {'C': 'minecraft:copper_ingot'}, 'result': {'id': 'jaymospowers:copper_scale', 'count': 1}})
put(R + 'loose_papers.json', {'type': 'minecraft:crafting_shapeless', 'category': 'building',
    'ingredients': ['minecraft:paper', 'minecraft:paper', 'minecraft:ink_sac'], 'result': {'id': 'jaymospowers:loose_papers', 'count': 2}})

# ---- furniture blocks --------------------------------------------------------------------------------------------------
FACES = ['down', 'up', 'north', 'south', 'west', 'east']


def cube(a, b, tex):
    return {'from': a, 'to': b, 'faces': {f: {'texture': '#' + tex} for f in FACES}}


TEX = {'c': 'jaymospowers:block/hunter/lab_copper', 'g': 'jaymospowers:block/hunter/lab_glass', 'l': 'jaymospowers:block/hunter/lab_liquid',
       'p': 'jaymospowers:block/hunter/lab_paper', 'k': 'jaymospowers:block/hunter/lab_cork', 'w': 'jaymospowers:block/hunter/bench_wood'}

MODELS = {
    'alchemy_bench': ('w', [cube([0, 10, 0], [16, 14, 16], 'w'), cube([1, 0, 1], [4, 10, 4], 'w'), cube([12, 0, 1], [15, 10, 4], 'w'),
                            cube([1, 0, 12], [4, 10, 15], 'w'), cube([12, 0, 12], [15, 10, 15], 'w'), cube([2, 14, 10], [14, 15, 14], 'c'),
                            cube([3, 15, 11], [4, 16, 13], 'g'), cube([7, 15, 11], [9, 16, 13], 'l')]),
    'vial_rack': ('c', [cube([1, 0, 4], [15, 1, 12], 'c'), cube([1, 5, 4], [15, 6, 12], 'c'), cube([1, 9, 4], [15, 10, 12], 'c'),
                        cube([1, 0, 4], [2, 10, 12], 'c'), cube([14, 0, 4], [15, 10, 12], 'c')]
                 + [cube([3 + 3 * i, 1, 6], [5 + 3 * i, 5, 8], 'g') for i in range(4)] + [cube([3 + 3 * i, 1, 6], [5 + 3 * i, 3, 8], 'l') for i in range(4)]
                 + [cube([3 + 3 * i, 6, 6], [5 + 3 * i, 9, 8], 'g') for i in range(4)] + [cube([3 + 3 * i, 6, 6], [5 + 3 * i, 7, 8], 'l') for i in range(4)]),
    'bell_jar': ('g', [cube([3, 0, 3], [13, 2, 13], 'c'), cube([4, 2, 4], [12, 13, 12], 'g'), cube([5, 2, 5], [11, 6, 11], 'l'),
                       cube([7, 13, 7], [9, 15, 9], 'k')]),
    'copper_scale': ('c', [cube([5, 0, 5], [11, 2, 11], 'c'), cube([7, 2, 7], [9, 12, 9], 'c'), cube([1, 12, 7], [15, 13, 9], 'c'),
                           cube([0, 8, 6], [4, 9, 10], 'c'), cube([12, 8, 6], [16, 9, 10], 'c'), cube([1, 9, 7], [2, 12, 8], 'c'),
                           cube([14, 9, 7], [15, 12, 8], 'c')]),
    'loose_papers': ('p', [cube([2, 0, 3], [10, 1, 12], 'p'), cube([6, 1, 2], [14, 2, 11], 'p'), cube([4, 2, 5], [12, 3, 14], 'p'),
                           cube([11, 0, 9], [14, 1, 11], 'k')]),
}
for name, (particle, elements) in MODELS.items():
    used = {'particle': TEX[particle]}
    for el in elements:
        for f in el['faces'].values():
            used[f['texture'][1:]] = TEX[f['texture'][1:]]
    put(A + 'models/block/%s.json' % name, {'textures': used, 'elements': elements})
    put(A + 'blockstates/%s.json' % name, {'variants': {'': {'model': 'jaymospowers:block/' + name}}})
    put(A + 'items/%s.json' % name, {'model': {'type': 'minecraft:model', 'model': 'jaymospowers:block/' + name}})
    put(D + 'loot_table/blocks/%s.json' % name, {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'jaymospowers:' + name}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
print('ok')
