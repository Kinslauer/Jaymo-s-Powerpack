"""Writes the blue giant's ring (power/zfighter/ZFighterSpace.java, destination GIANT): the pod's stop round the gas giant
the Verdant World is the moon of. No ground: drifts of ice and rock hang in one wide band, veined with gravilite, over
the giant's clouds far below.

  data/jaymospowers/dimension/giant_ring.json                the world: a noise generator, one biome
  data/jaymospowers/dimension_type/giant_ring.json           black sky, the Overworld's day and night
  data/jaymospowers/worldgen/biome/giant_ring.json           nothing spawns; gravilite shards and veins
  data/jaymospowers/worldgen/noise/giant_ring/*.json         the noises below
  data/jaymospowers/worldgen/noise_settings/giant_ring.json  packed ice wherever the density is above 0
  data/jaymospowers/worldgen/material_rule/giant_ring.json   frost and rock on top, ice and tuff under
  data/jaymospowers/worldgen/feature + placed_feature/giant_ring_gravilite.json   gravilite veins in the drifts

The drifts: a flattened 3D noise above DRIFT_AT, only inside the band (MIDDLE, wandering, HALF thick); outside it the
density drops away so nothing hangs above or below. Change the numbers here and run `python3 tools/gen_giant_ring.py`.
Keep GIANT_FALL_Y in ZFighterSpace.java under the band's bottom.
"""
import json, os

# ---- tuning ----
MIN_Y, HEIGHT = -64, 384
# the band: its middle height, how far that wanders, and half its thickness (blocks)
MIDDLE, WANDER, HALF = 96.0, 10.0, 26.0
# a drift is where the drift noise runs above this (0.2: roughly a third of the band is solid)
DRIFT_AT = 0.22
# how flat the drifts lie: the drift noise runs this many times faster up and down than across
FLAT = 2.4
# how fast the density falls off past the band's edge (per block)
EDGE = 0.05
# gravilite veins: how many tries a chunk, and their size
VEINS, VEIN_SIZE = 6, 7

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/jaymospowers')
NS = 'jaymospowers:giant_ring/'


def write(path, obj):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def op(kind, left, right):
    return {'type': 'minecraft:' + kind, 'left': left, 'right': right}
def add(a, b): return op('add', a, b)
def sub(a, b): return op('sub', a, b)
def mul(a, b): return op('mul', a, b)
def noise(name, xz=1.0, y=0.0): return {'type': 'minecraft:noise', 'noise': NS + name, 'xz_scale': xz, 'y_scale': y}
Y = 'minecraft:y'

middle = add(MIDDLE, mul(noise('wander'), WANDER))
away = op('max', sub(Y, middle), sub(middle, Y))
# 0 inside the band, falling off past its edge
edge = op('min', 0.0, mul(sub(HALF, away), EDGE))
density = add(sub(noise('drift', 1.0, FLAT), DRIFT_AT), edge)

write('worldgen/noise/giant_ring/drift.json', {'base_octave': -5, 'octave_count': 3, 'amplitude_modifiers': [1.0, 0.6, 0.3]})
write('worldgen/noise/giant_ring/wander.json', {'base_octave': -8, 'octave_count': 1, 'amplitude_modifiers': [1.0]})

write('worldgen/noise_settings/giant_ring.json', {
    'default_block': 'minecraft:packed_ice',
    'default_fluid': 'minecraft:air',
    'disable_mob_generation': True,
    'legacy_random_source': False,
    'material_rule': 'jaymospowers:giant_ring',
    'noise': {'height': HEIGHT, 'min_y': MIN_Y},
    'noise_router': {
        'chunk_surface_level': 0.0, 'continents': 0.0, 'depth': 0.0, 'erosion': 0.0,
        'final_density': {'type': 'minecraft:interpolated', 'cell_size_xz': 4, 'cell_size_y': 4, 'input': density},
        'ridges': 0.0, 'temperature': 0.0, 'vegetation': 0.0,
    },
    'sea_level': MIN_Y,
    'spawn_target': [],
})


def block(name): return {'type': 'minecraft:block', 'result_state': name}
def when(test, then): return {'type': 'minecraft:condition', 'if_true': test, 'then_run': then}
def above(at, lo=None, hi=None):
    return {'type': 'minecraft:noise_threshold', 'noise': 'minecraft:surface',
            'min_threshold': -1.7976931348623157e+308 if lo is None else lo,
            'max_threshold': 1.7976931348623157e+308 if hi is None else hi}

write('worldgen/material_rule/giant_ring.json', {'type': 'minecraft:sequence', 'sequence': [
    # the tops: frost, with bare rock and old blue ice showing through
    when('minecraft:on_floor', {'type': 'minecraft:sequence', 'sequence': [
        when(above(None, 0.35), block('minecraft:calcite')),
        when(above(None, None, -0.4), block('minecraft:blue_ice')),
        block('minecraft:snow_block'),
    ]}),
    # under it: rock where the surface noise runs high, ice elsewhere (the default)
    when('minecraft:under_floor', when(above(None, 0.2), block('minecraft:tuff'))),
    when('minecraft:deep_under_floor', when(above(None, 0.2), block('minecraft:tuff'))),
]})

write('worldgen/feature/giant_ring_gravilite.json', {
    'type': 'minecraft:ore', 'discard_chance_on_air_exposure': 0.0, 'size': VEIN_SIZE,
    'targets': [
        {'state': 'jaymospowers:gravilite_block', 'target': {'block': 'minecraft:packed_ice', 'predicate_type': 'minecraft:block_match'}},
        {'state': 'jaymospowers:gravilite_block', 'target': {'block': 'minecraft:tuff', 'predicate_type': 'minecraft:block_match'}},
    ],
})
write('worldgen/placed_feature/giant_ring_gravilite.json', {
    'feature': 'jaymospowers:giant_ring_gravilite',
    'placement': [
        {'type': 'minecraft:count', 'count': VEINS},
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
            'min_inclusive': {'absolute': int(MIDDLE - HALF - WANDER)}, 'max_inclusive': {'absolute': int(MIDDLE + HALF + WANDER)}}},
        {'type': 'minecraft:biome'},
    ],
})

empty_spawns = {k: [] for k in ['ambient', 'axolotls', 'creature', 'misc', 'monster', 'underground_water_creature', 'water_ambient',
                                'water_creature']}
write('worldgen/biome/giant_ring.json', {
    'attributes': {
        'minecraft:gameplay/natural_mob_spawns': {'argument': {'spawn_costs': {}, 'spawns_by_category': empty_spawns}, 'modifier': 'overlay'},
        'minecraft:visual/sky_color': '#000000',
        'minecraft:visual/fog_color': '#0a1428',
        # ice dust glinting in the sunlight
        'minecraft:visual/ambient_particles': {'argument': [{'particle': {'type': 'minecraft:white_ash'}, 'probability': 0.004}],
                                               'modifier': 'append'},
    },
    'carvers': [],
    'downfall': 0.0,
    'effects': {'water_color': '#3f76e4'},
    # ore veins go in the underground ores step (6), the shards on the tops in vegetal decoration (9)
    'features': [[], [], [], [], [], [], ['jaymospowers:giant_ring_gravilite'], [], [], ['jaymospowers:eden_gravilite'], []],
    'has_precipitation': False,
    'temperature': -0.7,
})

write('dimension/giant_ring.json', {
    'type': 'jaymospowers:giant_ring',
    'generator': {'type': 'minecraft:noise', 'settings': 'jaymospowers:giant_ring',
                  'biome_source': {'type': 'minecraft:fixed', 'biome': 'jaymospowers:giant_ring'}},
})

music = {'max_delay': 24000, 'min_delay': 12000}
no_sleep = {'translate': 'block.minecraft.bed.no_sleep'}
write('dimension_type/giant_ring.json', {
    'ambient_light': 0.0,
    'attributes': {
        'minecraft:audio/background_music': {'creative': dict(music, sound='minecraft:music.creative'),
                                             'default': dict(music, sound='minecraft:music.game')},
        'minecraft:gameplay/bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'when_dark', 'error_message': no_sleep},
        'minecraft:gameplay/nether_portal_spawns_piglin': False,
        'minecraft:gameplay/respawn_anchor_works': False,
        'minecraft:gameplay/straw_bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'when_dark', 'destroy_on_leave': True,
                                              'error_message': no_sleep},
        'minecraft:visual/ambient_light_color': '#1c2638',
        'minecraft:visual/cloud_color': '#00000000',
        'minecraft:visual/cloud_height': 192.33,
        'minecraft:visual/fog_color': '#0a1428',
        'minecraft:visual/sky_color': '#000000',
    },
    'coordinate_scale': 1.0,
    'default_clock': 'minecraft:overworld',
    'has_ceiling': False,
    'has_ender_dragon_fight': False,
    'has_skylight': True,
    'height': HEIGHT,
    'infiniburn': '#minecraft:infiniburn_overworld',
    'logical_height': HEIGHT,
    'min_y': MIN_Y,
    'monster_spawn_block_light_limit': 0,
    'monster_spawn_light_level': 0,
    'timelines': '#minecraft:in_overworld',
})
print('wrote the giant ring')
