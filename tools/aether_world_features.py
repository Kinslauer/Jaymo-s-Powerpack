"""The Aether's worldgen features (used by gen_aether_world.py): trees, ores, clouds, plants, lakes and the rest, as
configured features (worldgen/feature) and placed features (worldgen/placed_feature), plus the per-biome lists of them.

Numbers come from the behaviour notes (natural.md sections 2-9); where the notes' placement does not fit our islands
(the notes use the surface heightmap) the placement scans down from above the lowland instead, see surface().
"""
import gen_aether_world as g

NS = g.NS

# ---------------------------------------------------------------- tuning
# A scan can be at most 32 blocks long, so attempts start at a random height over the lowland's tops (about y 95 to 135)
# and scan down to the first ground: an attempt lands when its start is in the air less than 32 blocks above ground.
LOW_START = (110, 138)
LOW_STEPS = 32
HIGH_RANGE = (150, 276)         # upper islets: random start heights, scanning down
HIGH_STEPS = 32
HIGH_SHARE = 0.5                # upper islets get this share of a lowland feature's attempts
TOP = 383                       # the highest block of the world


# ---------------------------------------------------------------- json helpers

def B(name):
    return name if ':' in name else NS + ':' + name


def state(block, **props):
    d = {'id': B(block)}
    if props:
        d['properties'] = {k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in props.items()}
    return d


def weighted(entries):
    """entries: (block id or state dict, weight)."""
    return {'type': 'minecraft:weighted', 'entries': [{'data': (e if isinstance(e, dict) else B(e)), 'weight': w} for e, w in entries]}


def simple_block(to_place):
    return {'type': 'minecraft:simple_block', 'to_place': to_place if isinstance(to_place, dict) else state(to_place)}


def tag(name):
    return {'type': 'minecraft:matching_block_tag', 'tag': name}


AIR = tag('minecraft:air')


def blocks(*names, offset=None):
    d = {'type': 'minecraft:matching_blocks', 'blocks': [B(n) for n in names] if len(names) > 1 else B(names[0])}
    if offset:
        d['offset'] = offset
    return d


def all_of(*preds):
    return {'type': 'minecraft:all_of', 'predicates': list(preds)}


def any_of(*preds):
    return {'type': 'minecraft:any_of', 'predicates': list(preds)}


# ---------------------------------------------------------------- placement modifiers

def count(n):
    return {'type': 'minecraft:count', 'count': n}


def count_uniform(a, b):
    return {'type': 'minecraft:count', 'count': {'type': 'minecraft:uniform', 'min_inclusive': a, 'max_inclusive': b}}


def count_weighted(pairs):
    return {'type': 'minecraft:count', 'count': {'type': 'minecraft:weighted_list', 'distribution': [{'data': d, 'weight': w} for d, w in pairs]}}


def rarity(n):
    return {'type': 'minecraft:rarity_filter', 'chance': n}


IN_SQ = {'type': 'minecraft:in_square'}
BIOME = {'type': 'minecraft:biome'}
OFF_UP = {'type': 'minecraft:offset', 'x': 0, 'y': 1, 'z': 0}
OFF_DOWN = {'type': 'minecraft:offset', 'x': 0, 'y': -1, 'z': 0}


def hr_uniform(a, b):
    return {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': a}, 'max_inclusive': {'absolute': b}}}


def hr_trapezoid(a, b, plateau=0):
    return {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid', 'min_inclusive': {'absolute': a}, 'max_inclusive': {'absolute': b}, 'plateau': plateau}}


def env_scan(steps, target, allowed=AIR, direction='down'):
    return {'type': 'minecraft:environment_scan', 'allowed_search_condition': allowed, 'direction_of_search': direction,
            'max_steps': steps, 'target_condition': target}


STURDY_UP = {'type': 'minecraft:has_sturdy_face', 'direction': 'up'}
WATER = {'type': 'minecraft:matching_fluids', 'fluids': 'minecraft:water'}


def trap_offset(xz, y):
    t = lambda r: {'type': 'minecraft:trapezoid', 'min': -r, 'max': r, 'plateau': 0}
    return {'type': 'minecraft:offset', 'x': t(xz), 'y': t(y), 'z': t(xz)}


def surface(layer='low', target=STURDY_UP, allowed=AIR, up=True):
    """Start above the ground of a layer of islands and scan down to it; ends on the air block above it (or on the
    ground block itself with up=False)."""
    if layer == 'low':
        chain = [hr_uniform(*LOW_START), env_scan(LOW_STEPS, target, allowed)]
    else:
        chain = [hr_uniform(*HIGH_RANGE), env_scan(HIGH_STEPS, target, allowed)]
    return chain + ([OFF_UP] if up else [])


def would_survive(block):
    return {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:would_survive', 'state': state(block)}}


def filt(pred):
    return {'type': 'minecraft:block_predicate_filter', 'predicate': pred}


GRASS_BELOW = blocks('aether_grass_block', offset=[0, -1, 0])
ON_GRASS = all_of(AIR, GRASS_BELOW)


def high_count(n):
    return max(1, int(round(n * HIGH_SHARE)))


# ---------------------------------------------------------------- registry of what is written

FEATURES = {}     # name -> configured feature json
PLACED = {}       # name -> placed feature json


def feature(name, cfg):
    FEATURES[name] = cfg


def placed(name, feature_name, placement):
    PLACED[name] = {'feature': B(feature_name), 'placement': placement}


# ---------------------------------------------------------------- trees

def leaves(block):
    return {'id': B(block), 'properties': {'distance': '7', 'persistent': 'false', 'waterlogged': 'false'}}


def log(block):
    return {'id': B(block), 'properties': {'axis': 'y'}}


def uniform(a, b):
    return {'type': 'minecraft:uniform', 'min_inclusive': a, 'max_inclusive': b}


def straight(base, a, b):
    return {'type': 'minecraft:straight_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b}


def giant(base, a, b):
    return {'type': 'minecraft:giant_trunk_placer', 'base_height': base, 'height_rand_a': a, 'height_rand_b': b}


def blob(radius, height, offset=0):
    return {'type': 'minecraft:blob_foliage_placer', 'radius': radius, 'height': height, 'offset': offset}


TWO = {'type': 'minecraft:two_layers_feature_size'}


def ground_cover(block, tries, radius=4, height=2, **props):
    return {'type': 'minecraft:place_on_ground', 'block_state_provider': state(block, **props),
            'height': height, 'radius': radius, 'tries': tries}


def mossy_base(block):
    return {'type': 'minecraft:alter_ground', 'provider': state(block)}


# species: how each tree is shaped (placers and the blocks it is made of)
SPECIES = {}


def species(name, wood, leaf, trunk, foliage, size=TWO, ignore_vines=True):
    SPECIES[name] = dict(wood=wood, leaf=leaf, trunk=trunk, foliage=foliage, size=size, ignore_vines=ignore_vines)


CHERRY_TRUNK = {'type': 'minecraft:cherry_trunk_placer', 'base_height': 6, 'height_rand_a': 1, 'height_rand_b': 0,
                'branch_count': {'type': 'minecraft:weighted_list', 'distribution': [{'data': 1, 'weight': 1}, {'data': 2, 'weight': 1}, {'data': 3, 'weight': 1}]},
                'branch_horizontal_length': uniform(2, 4), 'branch_start_offset_from_top': {'min_inclusive': -4, 'max_inclusive': -3},
                'branch_end_offset_from_top': uniform(-1, 0)}
CHERRY_FOLIAGE = {'type': 'minecraft:cherry_foliage_placer', 'radius': 3, 'height': 4, 'offset': 0, 'wide_bottom_layer_hole_chance': 0.25,
                  'corner_hole_chance': 0.25, 'hanging_leaves_chance': 0.17, 'hanging_leaves_extension_chance': 0.33}
FANCY = (straight(3, 11, 0), {'type': 'minecraft:fancy_foliage_placer', 'radius': 2, 'height': 4, 'offset': 4},
         {'type': 'minecraft:two_layers_feature_size', 'limit': 0, 'min_clipped_height': 4, 'upper_size': 0})
FANCY_TRUNK = {'type': 'minecraft:fancy_trunk_placer', 'base_height': 3, 'height_rand_a': 11, 'height_rand_b': 0}
GIANT_SIZE = {'type': 'minecraft:two_layers_feature_size', 'lower_size': 1, 'upper_size': 2}
THREE = {'type': 'minecraft:three_layers_feature_size', 'upper_size': 2}

species('skyroot', 'skyroot_log', 'skyroot_leaves', straight(4, 2, 1), blob(2, 3))
species('short_skyroot', 'skyroot_log', 'skyroot_leaves', straight(4, 2, 0), blob(2, 3))
species('large_skyroot', 'skyroot_log', 'skyroot_leaves', FANCY_TRUNK, FANCY[1], FANCY[2])
species('skyplane', 'skyroot_log', 'skyplane_leaves', CHERRY_TRUNK, CHERRY_FOLIAGE, {'type': 'minecraft:two_layers_feature_size', 'upper_size': 2})
species('short_skyplane', 'skyroot_log', 'skyplane_leaves', dict(CHERRY_TRUNK, base_height=5), CHERRY_FOLIAGE, {'type': 'minecraft:two_layers_feature_size', 'upper_size': 2})
species('skybirch', 'skyroot_log', 'skybirch_leaves', straight(5, 2, 2),
        {'type': 'minecraft:random_spread_foliage_placer', 'radius': 3, 'offset': 0, 'foliage_height': 3, 'leaf_placement_attempts': 70}, THREE)
species('skypine', 'skyroot_log', 'skypine_leaves', straight(8, 3, 2),
        {'type': 'minecraft:spruce_foliage_placer', 'radius': uniform(3, 4), 'offset': uniform(1, 3), 'trunk_height': uniform(1, 2)},
        {'type': 'minecraft:two_layers_feature_size', 'limit': 2, 'upper_size': 2})
species('wisproot', 'wisproot_log', 'wisproot_leaves', straight(11, 4, 0), blob(3, 4))
species('wisptop', 'wisproot_log', 'wisptop_leaves', straight(13, 6, 0), blob(3, 4))
species('greatroot', 'greatroot_log', 'greatroot_leaves', {'type': 'minecraft:mega_jungle_trunk_placer', 'base_height': 6, 'height_rand_a': 2, 'height_rand_b': 5},
        {'type': 'minecraft:jungle_foliage_placer', 'radius': 3, 'height': 2, 'offset': 0}, GIANT_SIZE, False)
species('greatoak', 'skyroot_log', 'greatoak_leaves', giant(16, 2, 1), blob(5, 4), GIANT_SIZE)
species('short_greatoak', 'skyroot_log', 'greatoak_leaves', giant(12, 2, 1), blob(4, 4), GIANT_SIZE)
species('greatboa', 'greatroot_log', 'greatboa_leaves', giant(15, 3, 8),
        {'type': 'minecraft:mega_pine_foliage_placer', 'radius': 0, 'offset': 0, 'crown_height': uniform(9, 14)}, GIANT_SIZE, False)
species('amberoot', 'amberoot_log', 'amberoot_leaves', straight(5, 2, 1), blob(2, 3))
species('amberoot_dense', 'amberoot_log', 'amberoot_leaves', straight(5, 2, 1), blob(3, 3))
species('amberoot_sparse', 'amberoot_log', 'amberoot_leaves', straight(4, 2, 0), blob(1, 2))
species('amberoot_singular', 'amberoot_log', 'amberoot_leaves', straight(4, 1, 0), blob(2, 2))
species('amberoot_large', 'amberoot_log', 'amberoot_leaves', FANCY_TRUNK, FANCY[1], FANCY[2])

# decorated variants: (variant name, species, decorators)
FERNS = ground_cover('bush_fern', 18, radius=3)
LEAF_PILES = ground_cover('skyroot_leaf_pile', 28, radius=4)
SNOW_UNDER = ground_cover('arctic_snow', 40, radius=4, layers=1)
MOSS_BASE = mossy_base('bryalinn_moss_block')
VARIANTS = [
    ('nest_skyroot', 'large_skyroot', [LEAF_PILES]),
    ('skyroot_leafpile', 'skyroot', [LEAF_PILES]),
    ('wisptop_leafpile', 'wisptop', [LEAF_PILES]),
    ('short_skyroot_ferns', 'short_skyroot', [FERNS]),
    ('skyroot_ferns', 'skyroot', [FERNS]),
    ('large_skyroot_ferns', 'large_skyroot', [FERNS]),
    ('short_skyplane_ferns', 'short_skyplane', [FERNS]),
    ('short_greatoak_ferns', 'short_greatoak', [FERNS]),
    ('wisproot_ferns', 'wisproot', [FERNS]),
    ('amberoot_fields', 'amberoot_sparse', [FERNS]),
    ('skypine_decorated', 'skypine', [SNOW_UNDER]),
    ('greatboa_decorated', 'greatboa', [SNOW_UNDER]),
    ('amberoot_snowy', 'amberoot', [SNOW_UNDER]),
]
IRRADIATED = ['skyroot', 'large_skyroot', 'wisproot', 'skyplane', 'greatoak', 'skybirch', 'wisptop', 'greatroot', 'skypine', 'greatboa']
# the sapling trees: sapling name -> species
SAPLING_TREES = ['skyplane', 'skybirch', 'skypine', 'wisproot', 'wisptop', 'greatroot', 'greatoak', 'greatboa', 'amberoot']


def tree_config(sp, leaf=None, decorators=None, wisptop_moss=False):
    s = SPECIES[sp]
    decos = list(decorators or [])
    if sp == 'wisptop' and not decorators:
        decos.append(MOSS_BASE)
    return {
        'type': 'minecraft:tree',
        'below_trunk_provider': state('aether_dirt'),
        'decorators': decos,
        'foliage_placer': s['foliage'],
        'foliage_provider': leaves(leaf or s['leaf']),
        'ignore_vines': s['ignore_vines'],
        'minimum_size': s['size'],
        'trunk_placer': s['trunk'],
        'trunk_provider': log(s['wood']),
    }


def build_trees():
    for sp in SPECIES:
        feature(sp + '_tree', tree_config(sp))
    for name, sp, decos in VARIANTS:
        feature(name + '_tree', tree_config(sp, decorators=decos))
    for sp in IRRADIATED:
        feature('irradiated_' + sp + '_tree', tree_config(sp, leaf='irradiated_leaves'))
    # fallen logs (vanilla fallen trees)
    for wood, short in (('skyroot_log', 'skyroot'), ('wisproot_log', 'wisproot')):
        feature('fallen_' + short + '_log', {'type': 'minecraft:fallen_tree', 'log_decorators': [], 'stump_decorators': [],
                                             'log_length': uniform(4, 7), 'trunk_provider': log(wood)})


def selector(default, alternates):
    """A vanilla random_selector over trees: each alternate in turn with its chance, else the default."""
    def inline(name):
        return {'feature': B(name), 'placement': []}
    return {'type': 'minecraft:random_selector', 'default': inline(default),
            'features': [{'chance': chance, 'feature': inline(name)} for name, chance in alternates]}


FALLEN = ('fallen_skyroot_log', 0.0125)
FALLEN_WISP = ('fallen_wisproot_log', 0.0125)
GOLDEN = ('golden_oak_tree', 0.01)

IRR_ALTS = [('irradiated_large_skyroot_tree', 0.025), ('irradiated_wisproot_tree', 0.05), ('irradiated_skyplane_tree', 0.002),
            ('irradiated_greatoak_tree', 0.075), ('irradiated_skybirch_tree', 0.005), ('irradiated_wisptop_tree', 0.0075),
            ('irradiated_greatroot_tree', 0.125), ('irradiated_skypine_tree', 0.001), ('irradiated_greatboa_tree', 0.025),
            ('amberoot_sparse_tree', 0.025), ('amberoot_singular_tree', 0.065), ('amberoot_large_tree', 0.065)]

# biome -> (default tree, alternates, placement count spec)
TREE_BIOMES = {
    'verdant_woods': ('skyroot_tree', [('large_skyroot_tree', 0.6), ('nest_skyroot_tree', 0.015), ('skyplane_tree', 0.01), ('wisproot_tree', 0.1),
                                       ('short_greatoak_tree', 0.005), ('amberoot_dense_tree', 0.05), FALLEN, GOLDEN],
                      {'count': [(16, 9), (17, 1)]}),
    'violet_highwoods': ('wisptop_tree', [('wisptop_leafpile_tree', 0.25), ('skybirch_tree', 0.005), ('greatroot_tree', 0.002),
                                          ('amberoot_sparse_tree', 0.0025), FALLEN_WISP], {'count': [(12, 1), (13, 1)]}),
    'shrouded_forest': ('skyplane_tree', [('skyplane_tree', 0.1), ('skyroot_tree', 0.025), ('skyroot_leafpile_tree', 0.015), ('nest_skyroot_tree', 0.0015),
                                          ('wisproot_tree', 0.015), ('greatoak_tree', 0.005), ('amberoot_sparse_tree', 0.01), FALLEN, GOLDEN],
                        {'count': [(150, 3), (200, 1)]}),
    'turquoise_forest': ('skybirch_tree', [('wisptop_tree', 0.0075), ('greatroot_tree', 0.05), ('amberoot_dense_tree', 0.005), FALLEN_WISP],
                         {'count': [(2, 1), (3, 1)]}),
    'enduring_woodland': ('skypine_decorated_tree', [('greatboa_decorated_tree', 0.03), ('amberoot_snowy_tree', 0.00375)], {'count': [(20, 1), (24, 1)]}),
    'frozen_lakes': ('skypine_decorated_tree', [('greatboa_decorated_tree', 0.35), ('amberoot_snowy_tree', 0.0075)], {'count': [(1, 1)]}),
    'frigid_sierra': ('greatboa_decorated_tree', [('skypine_decorated_tree', 0.1), ('amberoot_snowy_tree', 0.0025)], {'rarity': 4}),
    'shimmering_basin': ('short_skyroot_ferns_tree', [('skyroot_ferns_tree', 0.6), ('large_skyroot_ferns_tree', 0.3), ('short_skyplane_ferns_tree', 0.05),
                                                      ('short_greatoak_ferns_tree', 0.025), ('amberoot_fields_tree', 0.1)], {'rarity': 5}),
    'flourishing_field': ('short_skyroot_ferns_tree', [('skyroot_ferns_tree', 0.1), ('large_skyroot_ferns_tree', 0.3), ('short_skyplane_ferns_tree', 0.01),
                                                       ('wisproot_ferns_tree', 0.2), ('short_greatoak_ferns_tree', 0.005), ('amberoot_fields_tree', 0.1)],
                          {'rarity': 10}),
    'magnetic_scar': ('skybirch_tree', [('wisptop_tree', 0.35), ('greatroot_tree', 0.01), ('amberoot_sparse_tree', 0.025)], {'rarity': 3}),
    'glistening_swamp': ('skybirch_tree', [('wisptop_tree', 0.3), ('amberoot_sparse_tree', 0.01)], {'rarity': 10}),
    'battleground_wastes': ('irradiated_skyroot_tree', IRR_ALTS, {'rarity': 3}),
    'contaminated_jungle': ('irradiated_skyroot_tree', IRR_ALTS, {'count': [(12, 1), (13, 1)]}),
}


def build_tree_placements():
    for biome, (default, alts, spec) in TREE_BIOMES.items():
        feature('trees_' + biome, selector(default, alts))
        if 'count' in spec:
            pairs = spec['count']
            head = [count(pairs[0][0])] if len(pairs) == 1 else [count_weighted(pairs)]
            high_n = max(1, int(round(pairs[0][0] * HIGH_SHARE)))
            high_head = [count(high_n)]
        else:
            head = [rarity(spec['rarity'])]
            high_head = [rarity(spec['rarity'] * 3)]
        placed('trees_' + biome, 'trees_' + biome, head + [IN_SQ] + surface('low') + [would_survive('skyroot_sapling'), BIOME])
        placed('trees_' + biome + '_high', 'trees_' + biome, high_head + [IN_SQ] + surface('high') + [would_survive('skyroot_sapling'), BIOME])


# ---------------------------------------------------------------- ores

STONE_HOST = NS + ':aether_stone_ore_replaceables'
SHALE_HOST = NS + ':aether_undershale_ore_replaceables'


def ore(name, size, targets, discard=0.0):
    feature(name, {'type': 'minecraft:ore', 'size': size, 'discard_chance_on_air_exposure': discard,
                   'targets': [{'target': {'predicate_type': 'minecraft:tag_match', 'tag': host}, 'state': state(block)} for host, block in targets]})


def both(stone_block, shale_block=None):
    t = [(STONE_HOST, stone_block)]
    if shale_block:
        t.append((SHALE_HOST, shale_block))
    return t


def ore_placed(name, n, height, biome_filter=True):
    placed(name, name, [count(n), IN_SQ, height] + ([BIOME] if biome_filter else []))


def build_ores():
    ore('ore_agiosite', 64, [(SHALE_HOST, 'agiosite')])
    ore_placed('ore_agiosite', 4, hr_uniform(0, 96))
    ore('ore_agiosite_small', 32, [(SHALE_HOST, 'agiosite')])
    ore_placed('ore_agiosite_small', 3, hr_trapezoid(0, 96, 48))
    ore('ore_ambrosium', 16, both('ambrosium_ore', 'undershale_ambrosium_ore'))
    ore_placed('ore_ambrosium', 20, hr_uniform(24, 128))
    ore('ore_ambrosium_high', 16, both('ambrosium_ore', 'undershale_ambrosium_ore'))
    ore_placed('ore_ambrosium_high', 4, hr_uniform(128, TOP))
    ore('ore_arkenium', 6, both('arkenium_ore', 'undershale_arkenium_ore'), 0.25)
    ore_placed('ore_arkenium', 11, hr_trapezoid(0, 90, 32))
    ore('ore_corrobonite', 5, [(SHALE_HOST, 'corrobonite_ore')])
    ore_placed('ore_corrobonite', 3, hr_trapezoid(0, 65, 35))
    ore('ore_glint', 4, both('glint_ore', 'undershale_glint_ore'))
    ore_placed('ore_glint', 6, hr_uniform(0, 114))
    ore('ore_gravitite', 5, both('gravitite_ore', 'undershale_gravitite_ore'))
    ore_placed('ore_gravitite', 7, hr_trapezoid(0, 70, 40))
    ore('ore_gravitite_buried', 5, both('gravitite_ore', 'undershale_gravitite_ore'), 0.5)
    ore_placed('ore_gravitite_buried', 4, hr_trapezoid(0, 80, 20))
    ore('ore_zanite', 6, both('zanite_ore', 'undershale_zanite_ore'), 0.15)
    ore_placed('ore_zanite', 12, hr_uniform(55, 160))
    ore('ore_zanite_mountain', 4, both('zanite_ore', 'undershale_zanite_ore'))
    ore_placed('ore_zanite_mountain', 5, hr_uniform(35, TOP))
    ore('ore_quartz', 15, both('holystone_quartz_ore'))
    ore_placed('ore_quartz', 6, hr_uniform(128, TOP))
    ore('ore_icestone', 32, both('icestone'))
    ore_placed('ore_icestone', 9, hr_uniform(0, TOP))
    ore('ore_icestone_small', 16, both('icestone'))
    ore_placed('ore_icestone_small', 6, hr_uniform(0, TOP))
    ore('ore_crude_scatterglass', 24, both('crude_scatterglass') + [(SHALE_HOST, 'crude_scatterglass')])
    ore_placed('ore_crude_scatterglass', 8, hr_uniform(0, TOP))
    # ichorite (brown stone) pockets in the lower undershale, around the Hestveil caverns
    ore('ore_ichorite', 33, [(SHALE_HOST, 'ichorite')])
    ore_placed('ore_ichorite', 6, hr_uniform(0, 70))


ORES = ['ore_agiosite', 'ore_agiosite_small', 'ore_ambrosium', 'ore_ambrosium_high', 'ore_arkenium', 'ore_corrobonite', 'ore_glint', 'ore_gravitite',
        'ore_gravitite_buried', 'ore_zanite', 'ore_zanite_mountain', 'ore_quartz', 'ore_icestone', 'ore_icestone_small', 'ore_crude_scatterglass', 'ore_ichorite']


# ---------------------------------------------------------------- aerclouds

CLOUDS = {   # kind: (steps, spread, thickness)
    'cold': (16, 3, 1), 'golden': (6, 2, 1), 'blue': (10, 3, 1), 'green': (10, 3, 1), 'purple': (10, 3, 1), 'storm': (14, 4, 2),
}
# (kind, band, rarity, height range)
CLOUD_PLACEMENTS = [
    ('cold', 'surface', 36, hr_uniform(96, 208)), ('golden', 'surface', 384, hr_trapezoid(96, 208, 48)), ('blue', 'surface', 128, hr_uniform(96, 208)),
    ('green', 'surface', 196, hr_uniform(96, 208)), ('purple', 'surface', 156, hr_uniform(96, 208)),
    ('blue', 'lower', 128, hr_uniform(0, 96)), ('green', 'lower', 128, hr_uniform(0, 96)), ('purple', 'lower', 128, hr_uniform(0, 96)),
    ('storm', 'lower', 64, hr_uniform(64, 96)),
    ('cold', 'middle', 48, hr_uniform(208, 256)), ('storm', 'middle', 768, hr_uniform(208, 256)),
    ('green', 'high', 512, hr_uniform(256, 304)), ('purple', 'high', 512, hr_uniform(256, 304)), ('storm', 'high', 1024, hr_uniform(288, 320)),
]


def build_clouds():
    for kind, (steps, spread, thick) in CLOUDS.items():
        feature('aercloud_' + kind, {'type': NS + ':aercloud', 'cloud': kind, 'bounds': steps, 'spread': spread, 'thickness': thick})
    names = []
    for kind, band, chance, height in CLOUD_PLACEMENTS:
        n = 'aercloud_%s_%s' % (kind, band)
        placed(n, 'aercloud_' + kind, [rarity(chance), IN_SQ, height, BIOME])
        names.append(n)
    return names


# ---------------------------------------------------------------- the rest

def patch(name, to_place, tries, layer='low', centers=1, chance=None, spread=7, ground=ON_GRASS, high=True):
    """A patch of single blocks: `centers` patch centres per chunk (with 1 in `chance` odds), `tries` blocks around each."""
    feature(name, simple_block(to_place))
    head = ([rarity(chance)] if chance else []) + ([count(centers)] if centers != 1 else [])
    tail = [BIOME, count(tries), trap_offset(spread, 3), filt(ground)]
    placed(name, name, head + [IN_SQ] + surface('low') + tail)
    if high:
        h_head = ([rarity(chance * 3)] if chance else [rarity(3)]) if centers == 1 else [count(high_count(centers))]
        placed(name + '_high', name, h_head + [IN_SQ] + surface('high') + tail)


GRASSES = weighted([('aether_short_grass', 5), ('aether_medium_grass', 3), ('aether_long_grass', 2)])


def build_plants():
    # grass and ferns
    patch('grass_small', weighted([('aether_short_grass', 1)]), 80, centers=1)
    patch('grass_medium', weighted([('aether_medium_grass', 1)]), 64, centers=1, chance=2)
    patch('grass_large', weighted([('aether_long_grass', 1)]), 48, centers=1, chance=3)
    patch('grass_field', GRASSES, 256, centers=1, chance=2)
    patch('grass_irradiated', weighted([('aether_short_grass', 3), ('aether_medium_grass', 1)]), 64, centers=1)
    patch('fern_patch', state('bush_fern'), 144, centers=1, chance=2)
    patch('aether_bush_patch', state('aether_bush'), 28, centers=1, chance=2)
    patch('valkyrie_sprouts', state('valkyrie_sprout'), 24, centers=1, chance=6)
    patch('brettl_patch', state('brettl_plant'), 8, centers=1, chance=5)
    patch('holystone_rocks', state('holystone_rock'), 10, centers=2)
    patch('holystone_twigs', state('holystone_twig'), 8, centers=2, chance=2)
    # berries and oranges
    patch('blueberry_common', state('berry_bush'), 20, chance=8)
    patch('blueberry_rare', state('berry_bush'), 20, chance=20)
    patch('blueberry_irradiated', state('berry_bush'), 20, chance=24)
    orange = weighted([(state('orange_tree', age=3, half='lower'), 3), (state('orange_tree', age=2, half='lower'), 1)])
    patch('orange_trees', orange, 8, chance=8)
    patch('orange_trees_irradiated', orange, 14, chance=4)
    # flowers
    highfield = weighted([('hesperose', 3), ('tarabloom', 2), ('poasprout', 3), ('lilichime', 3), ('pluracian', 2), ('blade_poa', 3), ('satival_shoot', 1),
                          ('white_flower', 3), ('purple_flower', 3)])
    patch('flowers_highfields', highfield, 40, centers=2, chance=2)
    patch('flowers_field', highfield, 80, centers=2)
    arctic = weighted([('lilichime', 3), ('blade_poa', 2), ('pluracian', 2), ('white_flower', 4)])
    patch('flowers_arctic', arctic, 40, chance=2)
    magnetic = weighted([('pluracian', 3), ('tarabloom', 3), ('purple_flower', 3), ('poasprout', 1)])
    patch('flowers_magnetic', magnetic, 40, chance=2)
    # shrooms
    patch('magnetic_shrooms', state('magnetic_shroom'), 30, centers=1, chance=3, ground=all_of(AIR, any_of(blocks('aether_grass_block', offset=[0, -1, 0]), blocks('ferrosite', offset=[0, -1, 0]), blocks('bryalinn_moss_block', offset=[0, -1, 0]), blocks('shayelinn_moss_block', offset=[0, -1, 0]))))
    patch('magnetic_shrooms_swamp', state('magnetic_shroom'), 40, centers=2, chance=2, ground=all_of(AIR, any_of(blocks('aether_grass_block', offset=[0, -1, 0]), blocks('shayelinn_moss_block', offset=[0, -1, 0]))))
    patch('rotshrooms', state('rotshroom'), 32, centers=1, chance=3)
    # huge magnetic shrooms (vanilla huge mushroom shapes with our blocks)
    feature('huge_magnetic_shroom', {
        'type': 'minecraft:huge_red_mushroom',
        'can_place_on': tag(NS + ':aether_mushroom_can_place_on'),
        'cap_provider': state('magnetic_shroom_block', up=True, down=False, north=True, south=True, east=True, west=True),
        'stem_provider': state('magnetic_shroom_stem', up=False, down=False, north=True, south=True, east=True, west=True),
    })
    placed('huge_magnetic_shrooms', 'huge_magnetic_shroom', [rarity(4), IN_SQ] + surface('low') + [BIOME])
    placed('huge_magnetic_shrooms_swamp', 'huge_magnetic_shroom', [count(2), IN_SQ] + surface('low') + [BIOME])
    # moss patches
    for moss, ground, carpet in (('bryalinn', 'bryalinn_moss_block', 'bryalinn_moss_carpet'), ('shayelinn', 'shayelinn_moss_block', 'shayelinn_moss_carpet'),
                                 ('ambrelinn', 'ambrelinn_moss_block', 'ambrelinn_moss_carpet')):
        feature('moss_vegetation_' + moss, simple_block(weighted([(carpet, 25), ('aether_short_grass', 30), ('aether_bush', 4)])))
        feature('moss_patch_' + moss, {
            'type': 'minecraft:vegetation_patch', 'depth': 1, 'extra_bottom_block_chance': 0.0, 'extra_edge_column_chance': 0.3,
            'ground_state': state(ground), 'replaceable': '#' + NS + ':aether_moss_replaceable', 'surface': 'floor', 'vegetation_chance': 0.7,
            'vegetation_feature': {'feature': B('moss_vegetation_' + moss), 'placement': []}, 'vertical_range': 5,
            'xz_radius': uniform(4, 7)})
    placed('moss_patch_bryalinn', 'moss_patch_bryalinn', [rarity(3), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('moss_patch_bryalinn_forest', 'moss_patch_bryalinn', [rarity(2), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('moss_patch_shayelinn', 'moss_patch_shayelinn', [rarity(3), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('moss_patch_shayelinn_swamp', 'moss_patch_shayelinn', [count(2), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('moss_patch_ambrelinn', 'moss_patch_ambrelinn', [rarity(4), IN_SQ] + surface('low', up=False) + [BIOME])
    # arilum on lake water
    water_scan = surface('low', target=WATER)
    # the notes' chances (1 in 4, 36, 12) are for their many noise lakes; ours are fewer, so each attempt is likelier
    for nm, blk, chance, tries in (('arilum_short', 'arilum', 2, 6), ('arilum', 'arilum', 6, 10), ('arilum_blooming', 'blooming_arilum', 4, 4)):
        feature(nm, simple_block(state(blk)))
        placed(nm, nm, [rarity(chance), IN_SQ] + water_scan + [BIOME, count(tries), trap_offset(5, 1), filt(all_of(AIR, blocks('minecraft:water', offset=[0, -1, 0])))])


def disk(name, block, radius, target_blocks, half_height=1, chance=None, count_n=1, target_pred=None, up=False, layer='low'):
    feature(name, {'type': 'minecraft:disk', 'half_height': half_height, 'radius': uniform(*radius), 'state_provider': state(block),
                   'target': target_pred or {'type': 'minecraft:matching_blocks', 'blocks': [B(b) for b in target_blocks]}})
    head = ([rarity(chance)] if chance else []) + ([count(count_n)] if count_n != 1 else [])
    placed(name, name, head + [IN_SQ] + surface(layer, up=up) + [BIOME])


def build_misc():
    ground = ['aether_grass_block', 'aether_dirt', 'coarse_aether_dirt', 'holystone', 'mossy_holystone']
    # lakes and ponds
    water = {'id': 'minecraft:water', 'properties': {'level': '0'}}
    def lake(name, barrier):
        feature(name, {'type': 'minecraft:lake', 'barrier': state(barrier), 'fluid': water, 'can_place_feature': {'type': 'minecraft:true'},
                       'can_replace_with_air_or_fluid': {'type': 'minecraft:not', 'predicate': tag('minecraft:features_cannot_replace')},
                       'can_replace_with_barrier': {'type': 'minecraft:not', 'predicate': tag('minecraft:features_cannot_replace')}})
    lake('lake_water', 'aether_dirt')
    lake('lake_arctic', 'arctic_packed_ice')
    lake('lake_swamp', 'ferrosite_mud')
    placed('lake_water', 'lake_water', [rarity(25), IN_SQ] + surface('low') + [BIOME])
    placed('lake_basin', 'lake_water', [rarity(6), IN_SQ] + surface('low') + [BIOME])
    placed('lake_arctic', 'lake_arctic', [rarity(4), IN_SQ] + surface('low') + [BIOME])
    placed('lake_pond_arctic', 'lake_water', [rarity(2), IN_SQ] + surface('low') + [BIOME])
    placed('lake_swamp', 'lake_swamp', [rarity(3), IN_SQ] + surface('low') + [BIOME])
    # ice sheets over arctic water
    disk('arctic_ice_sheet', 'arctic_ice', (5, 8), [], half_height=0, chance=1, target_pred=WATER, up=False)
    PLACED['arctic_ice_sheet']['placement'] = [IN_SQ] + surface('low', target=WATER, up=False) + [BIOME]
    # springs
    feature('spring_water', {'type': 'minecraft:spring_feature', 'state': {'id': 'minecraft:water', 'properties': {'falling': 'true'}},
                             'valid_blocks': [B('holystone'), B('aether_dirt'), B('undershale'), B('ferrosite')]})
    placed('spring_water', 'spring_water', [count(15), IN_SQ, hr_uniform(32, 256), BIOME])
    placed('spring_water_swamp', 'spring_water', [count(20), IN_SQ, hr_uniform(128, 200), BIOME])
    # surface decoration: discs
    disk('disk_coarse_dirt', 'coarse_aether_dirt', (2, 5), ['aether_grass_block', 'aether_dirt'], chance=3)
    disk('disk_silt', 'shimmering_silt', (2, 4), ['aether_grass_block', 'aether_dirt'], chance=6)
    disk('disk_ferrosite_sand', 'ferrosite_sand', (2, 5), ['aether_grass_block', 'aether_dirt', 'holystone', 'ferrosite'], chance=5)
    disk('disk_quicksoil', 'quicksoil', (2, 4), ['aether_grass_block', 'aether_dirt'], chance=14)
    disk('disk_packed_ice', 'arctic_packed_ice', (2, 5), ['aether_grass_block', 'aether_dirt', 'holystone', 'arctic_snow_block'], chance=3)
    # boulders
    for nm, block, chance in (('boulder_mossy', 'mossy_holystone', 6), ('boulder_icestone', 'icestone', 3), ('boulder_holystone', 'holystone', 8)):
        feature(nm, {'type': 'minecraft:block_blob', 'can_place_on': tag(NS + ':aether_boulder_can_place_on'), 'state': state(block)})
        placed(nm, nm, [rarity(chance), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('boulder_icestone_tundra', 'boulder_icestone', [rarity(2), IN_SQ] + surface('low', up=False) + [BIOME])
    # java features
    feature('cloudbed', {'type': NS + ':cloudbed'})
    placed('cloudbed', 'cloudbed', [])
    feature('ice_spike', {'type': NS + ':ice_spike'})
    placed('ice_spikes', 'ice_spike', [count(3), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('ice_spike_clusters', 'ice_spike', [count(7), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('ice_spikes_coast', 'ice_spike', [count(2), IN_SQ] + surface('low', up=False) + [BIOME])
    feature('ferrosite_spike', {'type': NS + ':ferrosite_spire', 'kind': 'spike'})
    feature('ferrosite_pillar', {'type': NS + ':ferrosite_spire', 'kind': 'pillar'})
    placed('ferrosite_spikes', 'ferrosite_spike', [count(2), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('ferrosite_spikes_scar', 'ferrosite_spike', [count(5), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('ferrosite_pillars', 'ferrosite_pillar', [rarity(3), IN_SQ] + surface('low', up=False) + [BIOME])
    placed('ferrosite_pillars_high', 'ferrosite_pillar', [rarity(4), IN_SQ] + surface('high', up=False) + [BIOME])
    feature('moa_nest', {'type': NS + ':moa_nest'})
    placed('moa_nests', 'moa_nest', [rarity(14), IN_SQ] + surface('low') + [BIOME])
    feature('pointed_holystone', {'type': NS + ':pointed_holystone'})
    placed('pointed_holystone', 'pointed_holystone', [rarity(75), IN_SQ] + surface('low') + [BIOME, count_uniform(64, 104), trap_offset(8, 3),
                                                                                              filt(all_of(AIR, any_of(blocks('holystone', offset=[0, -1, 0]), blocks('aether_grass_block', offset=[0, -1, 0]), blocks('aether_dirt', offset=[0, -1, 0]))))])
    placed('pointed_holystone_cave', 'pointed_holystone', [count(3), IN_SQ, hr_uniform(12, 56), BIOME])
    # the Aether I crystal island, now a rare sight in open sky
    feature('crystal_island', {'type': NS + ':crystal_island'})
    placed('crystal_island', 'crystal_island', [rarity(24), IN_SQ, hr_uniform(150, 260), BIOME])
    # quicksoil under island edges (the old shelf, now between y 40 and 110)
    feature('quicksoil_shelf', {'type': NS + ':quicksoil_shelf', 'min_y': 40, 'max_y': 110})
    placed('quicksoil_shelf', 'quicksoil_shelf', [rarity(5), IN_SQ])
    # stored for the crude scatterglass etc: nothing else


# biome name -> placed features by decoration step, built from the lists below
STEP_NAMES = ['raw_generation', 'lakes', 'local_modifications', 'underground_structures', 'surface_structures', 'strongholds',
              'underground_ores', 'underground_decoration', 'fluid_springs', 'vegetal_decoration', 'top_layer_modification']

# the one master order every biome's lists are cut from (so the game never meets conflicting orders)
MASTER = {
    0: ['cloudbed'],
    1: ['lake_water', 'lake_basin', 'lake_arctic', 'lake_pond_arctic', 'lake_swamp'],
    2: ['disk_coarse_dirt', 'disk_silt', 'disk_ferrosite_sand', 'disk_quicksoil', 'disk_packed_ice', 'arctic_ice_sheet', 'boulder_mossy', 'boulder_icestone',
        'boulder_icestone_tundra', 'boulder_holystone', 'moss_patch_bryalinn', 'moss_patch_bryalinn_forest', 'moss_patch_shayelinn', 'moss_patch_shayelinn_swamp',
        'moss_patch_ambrelinn', 'ice_spikes', 'ice_spike_clusters', 'ice_spikes_coast', 'ferrosite_spikes', 'ferrosite_spikes_scar', 'ferrosite_pillars',
        'ferrosite_pillars_high', 'quicksoil_shelf'],
    6: ORES,
    7: ['pointed_holystone', 'pointed_holystone_cave'],
    8: ['spring_water', 'spring_water_swamp'],
    9: [],   # filled in by build_master (trees first, then the plants)
    10: [],  # clouds, crystal islands
}
VEGETAL = [
    'TREES', 'TREES_HIGH', 'moa_nests', 'huge_magnetic_shrooms', 'huge_magnetic_shrooms_swamp',
    'grass_field', 'grass_large', 'grass_medium', 'grass_small', 'grass_irradiated', 'fern_patch', 'aether_bush_patch',
    'blueberry_common', 'blueberry_rare', 'blueberry_irradiated', 'orange_trees', 'orange_trees_irradiated',
    'flowers_field', 'flowers_highfields', 'flowers_arctic', 'flowers_magnetic', 'valkyrie_sprouts', 'brettl_patch',
    'magnetic_shrooms', 'magnetic_shrooms_swamp', 'rotshrooms', 'holystone_rocks', 'holystone_twigs',
    'arilum_short', 'arilum', 'arilum_blooming',
]


# ---------------------------------------------------------------- the biomes' lists

# in every biome (including the cave layer): anything whose placement is decided by height rather than by the surface,
# because the biome at that height is whichever the depth router picks there
COMMON = ['cloudbed', 'quicksoil_shelf', 'pointed_holystone_cave', 'spring_water', 'crystal_island'] + ORES + [
    n for (k, b, c, h) in CLOUD_PLACEMENTS for n in ['aercloud_%s_%s' % (k, b)]]
SURFACE_COMMON = ['pointed_holystone', 'holystone_rocks']

PLAN = {
    'hestveil_caverns': [],
    'expanse': [],
    'shimmering_basin': ['lake_basin', 'disk_silt', 'TREES', 'TREES_HIGH', 'grass_medium', 'grass_small', 'flowers_highfields', 'fern_patch', 'blueberry_common',
                         'arilum_short', 'arilum', 'arilum_blooming', 'orange_trees', 'moa_nests'],
    'flourishing_field': ['lake_water', 'disk_coarse_dirt', 'TREES', 'TREES_HIGH', 'grass_field', 'grass_large', 'flowers_field', 'blueberry_common', 'moa_nests',
                          'aether_bush_patch', 'holystone_twigs', 'arilum_short', 'arilum'],
    'verdant_woods': ['lake_water', 'disk_coarse_dirt', 'moss_patch_bryalinn_forest', 'boulder_mossy', 'TREES', 'TREES_HIGH', 'grass_medium', 'grass_small', 'fern_patch',
                      'flowers_highfields', 'blueberry_common', 'orange_trees', 'moa_nests', 'holystone_twigs', 'valkyrie_sprouts', 'brettl_patch'],
    'shrouded_forest': ['lake_water', 'moss_patch_bryalinn', 'moss_patch_ambrelinn', 'boulder_mossy', 'TREES', 'TREES_HIGH', 'fern_patch', 'grass_small', 'flowers_highfields',
                        'blueberry_common', 'holystone_twigs', 'rotshrooms', 'aether_bush_patch', 'moa_nests'],
    'violet_highwoods': ['lake_water', 'disk_ferrosite_sand', 'moss_patch_shayelinn', 'ferrosite_spikes', 'TREES', 'TREES_HIGH', 'huge_magnetic_shrooms', 'grass_small', 'fern_patch',
                         'flowers_magnetic', 'blueberry_rare', 'magnetic_shrooms'],
    'turquoise_forest': ['lake_water', 'disk_ferrosite_sand', 'moss_patch_shayelinn', 'ferrosite_spikes', 'TREES', 'TREES_HIGH', 'grass_small', 'grass_medium', 'flowers_magnetic',
                         'blueberry_rare', 'magnetic_shrooms'],
    'glistening_swamp': ['lake_swamp', 'disk_ferrosite_sand', 'moss_patch_shayelinn_swamp', 'spring_water_swamp', 'TREES', 'TREES_HIGH', 'huge_magnetic_shrooms_swamp',
                         'flowers_magnetic', 'rotshrooms', 'magnetic_shrooms_swamp', 'arilum_short', 'arilum'],
    'magnetic_scar': ['disk_ferrosite_sand', 'boulder_holystone', 'ferrosite_spikes_scar', 'ferrosite_pillars', 'ferrosite_pillars_high', 'TREES', 'TREES_HIGH',
                      'grass_small', 'flowers_magnetic', 'magnetic_shrooms'],
    'frozen_lakes': ['lake_arctic', 'lake_pond_arctic', 'arctic_ice_sheet', 'ice_spikes', 'disk_packed_ice', 'boulder_icestone', 'TREES', 'TREES_HIGH', 'flowers_arctic'],
    'sheer_tundra': ['lake_pond_arctic', 'ice_spike_clusters', 'disk_packed_ice', 'boulder_icestone_tundra', 'flowers_arctic', 'grass_small'],
    'enduring_woodland': ['lake_pond_arctic', 'ice_spikes_coast', 'disk_packed_ice', 'boulder_icestone', 'TREES', 'TREES_HIGH', 'fern_patch', 'flowers_arctic', 'blueberry_rare'],
    'frigid_sierra': ['ice_spikes_coast', 'disk_packed_ice', 'boulder_icestone_tundra', 'TREES', 'TREES_HIGH'],
    'battleground_wastes': ['lake_water', 'disk_quicksoil', 'boulder_holystone', 'TREES', 'TREES_HIGH', 'grass_irradiated', 'blueberry_irradiated', 'holystone_twigs', 'orange_trees_irradiated'],
    'contaminated_jungle': ['lake_water', 'moss_patch_ambrelinn', 'boulder_holystone', 'TREES', 'TREES_HIGH', 'grass_irradiated', 'fern_patch', 'blueberry_irradiated',
                            'orange_trees_irradiated', 'holystone_twigs'],
}
BIOMES = list(PLAN)


def build_all():
    build_trees()
    build_tree_placements()
    build_ores()
    build_clouds()
    build_plants()
    build_misc()


def lists_for(biome):
    """The biome's 11 decoration step lists, each cut from the one master order."""
    plan = set(PLAN[biome]) | set(COMMON)
    if PLAN[biome]:
        plan |= set(SURFACE_COMMON)
        if 'TREES' in PLAN[biome]:
            plan |= {'trees_' + biome, 'trees_' + biome + '_high'}
    cloud_names = ['aercloud_%s_%s' % (k, b) for (k, b, c, h) in CLOUD_PLACEMENTS]
    vegetal = []
    for n in VEGETAL:
        vegetal.append('trees_' + biome if n == 'TREES' else 'trees_' + biome + '_high' if n == 'TREES_HIGH' else n)
    orders = {i: MASTER.get(i, []) for i in range(11)}
    orders[9] = vegetal
    orders[10] = cloud_names + ['crystal_island']
    return [[NS + ':' + n for n in orders[i] if n in plan] for i in range(11)]
