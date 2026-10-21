#!/usr/bin/env python3
"""The Aether's dimension data (Track W): noises, density functions, noise settings, dimension and its type, the 16 biomes
and their tags, and every worldgen feature (trees, ores, clouds, plants, ...) the biomes list.

Clean room: behaviour and numbers from the notes only; every line of JSON is written here.
Run from the repo root: python3 -I tools/gen_aether_world.py   (stdlib only). Rerunning rewrites the same files.

The terrain is plain density-function JSON; tools/preview_aether_terrain.py evaluates the written files, so tune the numbers
at the top of the terrain section here and look at the picture again.
"""
import json
import os
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
DATA = os.path.join(ROOT, 'src/main/resources/data/jaymospowers')
NS = 'jaymospowers'


# ---------------------------------------------------------------- output

def write(rel, obj):
    path = os.path.join(DATA, rel + '.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, sort_keys=False)
        f.write('\n')


def wipe(rel):
    shutil.rmtree(os.path.join(DATA, rel), ignore_errors=True)


def noise_def(name, first_octave, amplitudes):
    """A noise in the 26.3 format: octave 0 is 2^first_octave blocks long, one amplitude per octave."""
    write('worldgen/noise/aether/' + name, {
        'base_octave': first_octave,
        'octave_count': len(amplitudes),
        'amplitude_modifiers': amplitudes,
    })


# ---------------------------------------------------------------- density function builders

def df(name, obj):
    write('worldgen/density_function/aether/' + name, obj)


def ref(name):
    return NS + ':aether/' + name


def add(a, b):
    return {'type': 'minecraft:add', 'left': a, 'right': b}


def sub(a, b):
    return {'type': 'minecraft:sub', 'left': a, 'right': b}


def mul(a, b):
    return {'type': 'minecraft:mul', 'left': a, 'right': b}


def mn(a, b):
    return {'type': 'minecraft:min', 'left': a, 'right': b}


def mx(a, b):
    return {'type': 'minecraft:max', 'left': a, 'right': b}


def clamp(x, lo, hi):
    return {'type': 'minecraft:clamp', 'input': x, 'min': lo, 'max': hi}


def absf(x):
    return {'type': 'minecraft:abs', 'input': x}


def noise(name, xz, y=0.0):
    return {'type': 'minecraft:noise', 'noise': NS + ':aether/' + name, 'xz_scale': xz, 'y_scale': y}


def shifted_noise(name, xz, y=0.0):
    n = noise(name, xz, y)
    n['shift_x'] = 'minecraft:shift_x'
    n['shift_z'] = 'minecraft:shift_z'
    return n


Y = 'minecraft:y'


def spline_y(points):
    """Piecewise curve of y: points are (y, value); slopes follow the neighbours so it reads as a smooth ramp."""
    pts = []
    for i, (loc, val) in enumerate(points):
        if 0 < i < len(points) - 1:
            d = (points[i + 1][1] - points[i - 1][1]) / (points[i + 1][0] - points[i - 1][0])
        else:
            d = 0.0
        pts.append({'derivative': round(d, 6), 'location': loc, 'value': val})
    return {'type': 'minecraft:spline', 'spline': {'coordinate': Y, 'points': pts}}


def lin(x, scale, offset=0.0):
    """x * scale + offset."""
    r = mul(x, scale)
    return add(r, offset) if offset else r


# ---------------------------------------------------------------- terrain numbers
# World: noise runs y 0..384 (dimension y -32..384). Everything below is a density in "about 0.1 per block" units; the
# surface is where it crosses 0. One island layer is: a top height, a thickness, and the shaped lens between them.

# Climate (the biome source reads these).  value = noise * scale; the zones are bands of the result.
TEMPERATURE_SCALE, HUMIDITY_SCALE, EROSION_SCALE, WEIRDNESS_SCALE = 2.4, 2.4, 2.6, 2.6
CLIMATE_XZ = 0.9             # world-coordinate multiplier of the climate noises (smaller = wider zones)

# The lowland islands (the base layer).
LOW_MASK_XZ = 0.8             # island size: 1 / this x the noise's 512-block octave
LOW_MASK_SCALE = 3.0          # mask noise std is 0.34: x3 gives about -1..1
LOW_THRESHOLD = 0.0           # mask above this is island
LOW_THICK = 105.0             # blocks of thickness per unit of mask above the threshold
LOW_BOTTOM_MIN = 6            # the underside never goes below this
LOW_TOP = 108.0               # mean top height
LOW_HILLS = 16.0              # blocks of top height per unit of the hill noise (std 0.34)
LOW_MASS_LIFT = 8.0           # centres of islands stand a little higher
LOW_TOP_SLOPE, LOW_BOTTOM_SLOPE = 0.35, 0.12   # how fast the lens' density grows off its two faces (per block)
LOW_TOP_WOBBLE, LOW_BOTTOM_WOBBLE = 0.45, 1.6   # 3D noise added to each face
# Upper layers: (name, mask octave scale, top height, hills, thickness, threshold, mask xz)
UPPER_LAYERS = [
    # name, mask_xz, mean top, hills amp, height-noise xz, max thickness, threshold, min bottom
    ('shatter_a', 1.30, 168.0, 38.0, 0.35, 70.0, 0.75, 132.0),
    ('shatter_b', 1.10, 226.0, 34.0, 0.30, 60.0, 0.85, 176.0),
]
UPPER_MIN_THICK = 6.0        # an islet is at least this thick wherever it exists at all
UPPER_TOP_SLOPE, UPPER_BOTTOM_SLOPE = 0.35, 0.16
UPPER_TOP_WOBBLE, UPPER_BOTTOM_WOBBLE = 0.35, 1.2

# Caves (the cave layer is carved out of the base layer only).
CAVE_XZ, CAVE_Y = 0.9, 1.4
CAVE_RADIUS = 0.16           # tunnel radius in noise units (about 3 blocks)
CAVE_TOP = (88, 108)         # no tunnels above: they fade out between these heights
HESTVEIL_WINDOW = [(8, 0.0), (20, 1.0), (40, 1.0), (58, 0.0)]   # where the big caverns can open
HESTVEIL_THRESHOLD = 0.2     # noise needed for a cavern inside the window


def build_terrain():
    noise_def('island_mask', -9, [1.0, 1.0, 0.45])
    noise_def('island_hills', -6, [1.0, 1.0, 0.5, 0.3])
    noise_def('island_rough', -5, [1.0, 1.0, 0.6, 0.4])
    noise_def('cave_a', -6, [1.0, 1.0, 0.5])
    noise_def('cave_b', -6, [1.0, 1.0, 0.5])
    noise_def('hestveil', -6, [1.0, 1.0, 0.6])
    noise_def('temperature', -9, [1.0, 1.0, 0.7])
    noise_def('humidity', -9, [1.0, 1.0, 0.7])
    noise_def('erosion', -9, [1.0, 1.0, 0.7])
    noise_def('weirdness', -8, [1.0, 1.0, 0.7])
    noise_def('ferrosite', -6, [1.0, 1.0])
    noise_def('moss', -6, [1.0, 1.0])
    noise_def('ice', -6, [1.0, 1.0])
    noise_def('snow', -7, [1.0, 1.0])
    noise_def('irradiation', -6, [1.0, 1.0])
    for i in range(len(UPPER_LAYERS)):
        noise_def('mask_' + UPPER_LAYERS[i][0], -7, [1.0, 1.0, 0.6])
        noise_def('height_' + UPPER_LAYERS[i][0], -7, [1.0, 1.0, 0.5])

    # ---- climate
    df('climate/temperature', lin(shifted_noise('temperature', CLIMATE_XZ), TEMPERATURE_SCALE))
    df('climate/humidity', lin(shifted_noise('humidity', CLIMATE_XZ), HUMIDITY_SCALE))
    df('climate/erosion', lin(shifted_noise('erosion', CLIMATE_XZ), EROSION_SCALE))
    df('climate/weirdness', lin(shifted_noise('weirdness', CLIMATE_XZ), WEIRDNESS_SCALE))
    # depth is the height band the biome source sorts by: cave layer, lowland, middle, high
    df('climate/depth', spline_y([(0, -1.6), (50, -1.0), (180, 0.3), (240, 0.65), (384, 1.5)]))

    rough = noise('island_rough', 1.0, 1.0)

    def layer(mask, mask_xz, hills, hills_xz, top, hills_amp, thick, threshold, bottom_min, top_slope, bottom_slope, top_wob, bottom_wob, lift, min_thick=0.0):
        m = lin(noise(mask, mask_xz), LOW_MASK_SCALE)
        height = add(add(top, lin(noise(hills, hills_xz), hills_amp)), lin(clamp(m, 0.0, 1.0), lift))
        over = sub(m, threshold)
        thickness = add(lin(clamp(over, 0.0, 1.0), thick), lin(clamp(lin(over, 40.0), 0.0, 1.0), min_thick))
        bottom = mx(sub(height, thickness), bottom_min)
        t = add(lin(sub(height, Y), top_slope), lin(rough, top_wob))
        b = add(lin(sub(Y, bottom), bottom_slope), lin(rough, bottom_wob))
        return mn(t, b)

    df('terrain/base_islands', layer('island_mask', LOW_MASK_XZ, 'island_hills', 1.0, LOW_TOP, LOW_HILLS, LOW_THICK, LOW_THRESHOLD,
                                      LOW_BOTTOM_MIN, LOW_TOP_SLOPE, LOW_BOTTOM_SLOPE, LOW_TOP_WOBBLE, LOW_BOTTOM_WOBBLE, LOW_MASS_LIFT))
    upper = None
    for (name, mask_xz, top, hills_amp, hills_xz, thick, threshold, bottom_min) in UPPER_LAYERS:
        l = layer('mask_' + name, mask_xz, 'height_' + name, hills_xz, top, hills_amp, thick, threshold, bottom_min,
                  UPPER_TOP_SLOPE, UPPER_BOTTOM_SLOPE, UPPER_TOP_WOBBLE, UPPER_BOTTOM_WOBBLE, 6.0, UPPER_MIN_THICK)
        upper = l if upper is None else mx(upper, l)
    df('terrain/shattered_islands', upper)

    # tunnels: where both of two noises are near zero; big caverns near the bottom
    ca = noise('cave_a', CAVE_XZ, CAVE_Y)
    cb = noise('cave_b', CAVE_XZ, CAVE_Y)
    tube = mx(absf(ca), absf(cb))
    fade = spline_y([(CAVE_TOP[0], 0.0), (CAVE_TOP[1], 6.0)])
    df('terrain/caves', add(lin(sub(tube, CAVE_RADIUS), 12.0), fade))
    window = spline_y(HESTVEIL_WINDOW)
    hest = mul(noise('hestveil', 0.9, 1.3), window)
    df('terrain/hestveil', sub(5.0, lin(hest, 5.0 / HESTVEIL_THRESHOLD)))

    # the base layer cut by caves, then the upper layer joined on top above y 128
    base = mn(mn(ref('terrain/base_islands'), ref('terrain/caves')), ref('terrain/hestveil'))
    upper_gated = {'type': 'minecraft:range_choice', 'input': Y, 'min_inclusive': -4096.0, 'max_exclusive': 128.0,
                   'when_in_range': -1.0, 'when_out_of_range': ref('terrain/shattered_islands')}
    inner = mx(base, upper_gated)
    df('terrain/final_islands', add({
        'type': 'minecraft:squeeze',
        'input': {'type': 'minecraft:interpolated', 'cell_size_xz': 4, 'cell_size_y': 4, 'input': inner},
    }, {'type': 'minecraft:beardifier'}))


# ---------------------------------------------------------------- noise settings, dimension, material rule

def build_noise_settings():
    final = ref('terrain/final_islands')
    write('worldgen/noise_settings/aether', {
        'default_block': NS + ':holystone',
        'default_fluid': 'minecraft:water',
        'disable_mob_generation': False,
        'legacy_random_source': True,
        'material_rule': NS + ':aether',
        'noise': {'height': 384, 'min_y': 0},
        'noise_router': {
            'chunk_surface_level': 0.0,
            'continents': 0.0,
            'depth': ref('climate/depth'),
            'erosion': ref('climate/erosion'),
            'final_density': final,
            'ridges': ref('climate/weirdness'),
            'temperature': ref('climate/temperature'),
            'vegetation': ref('climate/humidity'),
        },
        'sea_level': -64,
        'spawn_target': [],
    })


def build_dimension_type():
    write('dimension_type/aether', {
        'ambient_light': 0.0,
        'attributes': {
            'minecraft:audio/ambient_sounds': {'mood': {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.cave', 'tick_delay': 6000}},
            'minecraft:audio/background_music': {
                'creative': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.creative'},
                'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.game'},
            },
            'minecraft:gameplay/bed_rule': {'can_set_spawn': 'always', 'can_sleep': 'when_dark', 'error_message': {'translate': 'block.minecraft.bed.no_sleep'}},
            'minecraft:gameplay/nether_portal_spawns_piglin': True,
            'minecraft:gameplay/respawn_anchor_works': False,
            'minecraft:gameplay/straw_bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'when_dark', 'destroy_on_leave': True,
                                                  'error_message': {'translate': 'block.minecraft.bed.no_sleep'}},
            'minecraft:visual/ambient_light_color': '#0a0a0a',
            'minecraft:visual/cloud_color': '#ffffffff',
            'minecraft:visual/cloud_height': 320.33,
            'minecraft:visual/fog_color': '#ecebfc',
            'minecraft:visual/sky_color': '#c9d1ff',
        },
        'coordinate_scale': 1.0,
        'default_clock': 'minecraft:overworld',
        'has_ceiling': False,
        'has_ender_dragon_fight': False,
        'has_skylight': True,
        'height': 416,
        'infiniburn': '#minecraft:infiniburn_overworld',
        'logical_height': 416,
        'min_y': -32,
        'monster_spawn_block_light_limit': 0,
        'monster_spawn_light_level': {'type': 'minecraft:uniform', 'max_inclusive': 7, 'min_inclusive': 0},
        'timelines': '#minecraft:in_overworld',
    })


ARCTIC = ['frozen_lakes', 'sheer_tundra', 'enduring_woodland', 'frigid_sierra']
MAGNETIC = ['violet_highwoods', 'turquoise_forest', 'glistening_swamp', 'magnetic_scar']
HIGHFIELDS = ['shimmering_basin', 'flourishing_field', 'verdant_woods', 'shrouded_forest']
IRRADIATED = ['battleground_wastes', 'contaminated_jungle']

ARCTIC_MAX_T, IRRADIATED_MIN_T, MAGNETIC_MIN_E = -0.4, 0.65, 0.485
LAKE_WEIRD = 0.3                  # weirdness at or above this picks the lake variants
DRY_MAX, WET_MIN = -0.35, 0.45    # humidity bands inside a zone


def build_dimension():
    boxes = []

    def box(biome, t, h, e, w, d):
        boxes.append({'biome': NS + ':' + biome, 'parameters': {
            'temperature': list(t), 'humidity': list(h), 'continentalness': [-2.0, 2.0], 'erosion': list(e),
            'weirdness': list(w), 'depth': list(d), 'offset': 0.0}})

    ANY = (-2.0, 2.0)
    box('hestveil_caverns', ANY, ANY, ANY, ANY, (-2.0, -1.0))
    for d, mid in (((-1.0, 0.3), False), ((0.3, 2.0), True)):
        non_mag_e = (-2.0, MAGNETIC_MIN_E)
        mag_e = (MAGNETIC_MIN_E, 2.0)
        # arctic
        t = (-2.0, ARCTIC_MAX_T)
        if mid:
            box('frigid_sierra', t, ANY, non_mag_e, ANY, d)
        else:
            box('frozen_lakes', t, ANY, non_mag_e, (LAKE_WEIRD, 2.0), d)
            box('sheer_tundra', t, (-2.0, DRY_MAX), non_mag_e, (-2.0, LAKE_WEIRD), d)
            box('enduring_woodland', t, (DRY_MAX, 2.0), non_mag_e, (-2.0, LAKE_WEIRD), d)
        # highfields
        t = (ARCTIC_MAX_T, IRRADIATED_MIN_T)
        box('shimmering_basin', t, ANY, non_mag_e, (LAKE_WEIRD, 2.0), d)
        box('flourishing_field', t, (-2.0, DRY_MAX), non_mag_e, (-2.0, LAKE_WEIRD), d)
        box('verdant_woods', t, (DRY_MAX, WET_MIN), non_mag_e, (-2.0, LAKE_WEIRD), d)
        box('shrouded_forest', t, (WET_MIN, 2.0), non_mag_e, (-2.0, LAKE_WEIRD), d)
        # irradiated
        t = (IRRADIATED_MIN_T, 2.0)
        box('battleground_wastes', t, (-2.0, 0.2), non_mag_e, ANY, d)
        box('contaminated_jungle', t, (0.2, 2.0), non_mag_e, ANY, d)
        # magnetic: every temperature below the irradiated band; the irradiated band's high erosion is magnetic scar
        t = (-2.0, IRRADIATED_MIN_T)
        box('magnetic_scar', t, ANY, mag_e, (LAKE_WEIRD, 2.0), d)
        box('violet_highwoods', t, (-2.0, DRY_MAX), mag_e, (-2.0, LAKE_WEIRD), d)
        box('turquoise_forest', t, (DRY_MAX, WET_MIN), mag_e, (-2.0, LAKE_WEIRD), d)
        box('glistening_swamp', t, (WET_MIN, 2.0), mag_e, (-2.0, LAKE_WEIRD), d)
        box('magnetic_scar', (IRRADIATED_MIN_T, 2.0), ANY, mag_e, ANY, d)
    write('dimension/aether', {
        'type': NS + ':aether',
        'generator': {'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:multi_noise', 'biomes': boxes}, 'settings': NS + ':aether'},
    })


GRASS_MIN_Y = 80      # below this the cave floors stay bare rock (the lowland tops are all above it)


def build_material_rule():
    def cond(c, then):
        return {'type': 'minecraft:condition', 'if_true': c, 'then_run': then}

    def both(c1, c2, then):
        return cond(c1, cond(c2, then))

    def biome_is(*names):
        return {'type': 'minecraft:biome', 'biome_is': [NS + ':' + n for n in names]}

    def thresh(name, lo, hi):
        return {'type': 'minecraft:noise_threshold', 'noise': NS + ':aether/' + name, 'min_threshold': lo, 'max_threshold': hi}

    def block(name):
        return {'type': 'minecraft:block', 'result_state': NS + ':' + name}

    def surface_and_under(c1, c2, name):
        return [cond('minecraft:on_floor', both(c1, c2, block(name))), cond('minecraft:under_floor', both(c1, c2, block(name)))]

    seq = []
    seq += surface_and_under(biome_is('frigid_sierra'), thresh('snow', -0.5, 0.35), 'arctic_snow_block')
    seq += surface_and_under(biome_is(*ARCTIC), thresh('ice', 0.2, 1.0), 'arctic_packed_ice')
    seq.append(cond('minecraft:on_floor', both(biome_is('battleground_wastes'), thresh('irradiation', 0.1, 1.0), block('irradiated_holystone'))))
    seq.append(cond('minecraft:on_floor', both(biome_is('magnetic_scar'), thresh('ferrosite', 0.0, 1.0), block('ferrosite'))))
    seq.append(cond('minecraft:deep_under_floor', cond(biome_is(*MAGNETIC), block('ferrosite'))))
    seq.append(cond('minecraft:on_floor', both(thresh('moss', 0.32, 1.0), 'minecraft:not_underwater', block('mossy_holystone'))))
    y_above = {'type': 'minecraft:y_above', 'anchor': {'absolute': GRASS_MIN_Y}, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
    seq.append(cond('minecraft:on_floor', cond(y_above, {'type': 'minecraft:sequence', 'sequence': [
        cond('minecraft:not_underwater', block('aether_grass_block')), block('aether_dirt')]})))
    seq.append(cond('minecraft:under_floor', cond(y_above, block('aether_dirt'))))
    seq.append(cond({'type': 'minecraft:vertical_gradient', 'random_name': NS + ':undershale', 'true_at_and_below': {'absolute': 56},
                     'false_at_and_above': {'absolute': 78}}, block('undershale')))
    write('worldgen/material_rule/aether', {'type': 'minecraft:sequence', 'sequence': seq})


# ---------------------------------------------------------------- biomes

# name: (temperature, downfall, fog, sky, water_fog, water, grass)
HIGH_COL = ('#ecebfc', '#c9d1ff', '#55708a', '#a2d5f2')
MAG_COL = ('#edeef5', '#c5cbeb', '#607496', '#abbdd9')
ARC_COL = ('#f3f0ff', '#e7e3fc', '#3e5082', '#637aa8')
IRR_COL = ('#f0e8be', '#fcebc5', '#bccc81', '#aed4bf')
BIOME_DATA = {
    'frozen_lakes': (-0.2, 0.5, ARC_COL, '#c4f0ec'), 'sheer_tundra': (-0.4, 0.5, ARC_COL, '#c4f0ec'),
    'enduring_woodland': (-0.3, 0.4, ARC_COL, '#b8e8dc'), 'frigid_sierra': (-0.5, 0.9, ARC_COL, '#c4f0ec'),
    'shimmering_basin': (0.5, 0.5, HIGH_COL, '#b1ffcb'), 'flourishing_field': (0.8, 0.4, HIGH_COL, '#b1ffcb'),
    'verdant_woods': (0.7, 0.8, HIGH_COL, '#a6f5bf'), 'shrouded_forest': (0.7, 0.8, HIGH_COL, '#9de8b4'),
    'violet_highwoods': (0.7, 0.8, MAG_COL, '#bfe6d6'), 'magnetic_scar': (0.6, 0.3, MAG_COL, '#c9e0d0'),
    'turquoise_forest': (0.7, 0.8, MAG_COL, '#a8ece0'), 'glistening_swamp': (0.8, 0.9, MAG_COL, '#98d8c4'),
    'battleground_wastes': (1.0, 0.3, IRR_COL, '#d8e69a'), 'contaminated_jungle': (1.0, 0.3, IRR_COL, '#c8dc84'),
    'hestveil_caverns': (0.5, 0.5, HIGH_COL, '#b1ffcb'), 'expanse': (0.5, 0.5, HIGH_COL, '#b5ffd0'),
}
CREATURE_PROBABILITY = 0.2
IRRADIATED_PARTICLE_CHANCE = 0.00625


def build_biomes(features):
    for name, (temp, down, (fog, sky, water_fog, water), grass) in BIOME_DATA.items():
        attrs = {
            'minecraft:gameplay/creature_world_gen_spawn_probability': CREATURE_PROBABILITY,
            'minecraft:gameplay/natural_mob_spawns': {
                'argument': {'spawn_costs': {}, 'spawns_by_category': {c: [] for c in (
                    'ambient', 'axolotls', 'creature', 'misc', 'monster', 'underground_water_creature', 'water_ambient', 'water_creature')}},
                'modifier': 'overlay'},
            'minecraft:visual/fog_color': fog,
            'minecraft:visual/sky_color': sky,
            'minecraft:visual/water_fog_color': water_fog,
        }
        if name in IRRADIATED:
            attrs['minecraft:visual/ambient_particles'] = {
                'argument': [{'particle': {'type': 'minecraft:end_rod'}, 'probability': IRRADIATED_PARTICLE_CHANCE}], 'modifier': 'append'}
        write('worldgen/biome/' + name, {
            'attributes': attrs, 'carvers': [], 'downfall': down,
            'effects': {'water_color': water, 'grass_color': grass, 'foliage_color': grass},
            'features': features.lists_for(name), 'has_precipitation': True, 'temperature': temp,
        })
    zones = {'is_aether': list(BIOME_DATA), 'is_aether_highfields': HIGHFIELDS, 'is_aether_magnetic': MAGNETIC, 'is_aether_arctic': ARCTIC,
             'is_aether_irradiated': IRRADIATED}
    for tag_name, members in zones.items():
        write('tags/worldgen/biome/' + tag_name, {'values': [NS + ':' + m for m in members]})


def build_block_tags():
    def tagfile(name, blocks):
        write('tags/block/' + name, {'values': [NS + ':' + b for b in blocks]})
    tagfile('aether_moss_replaceable', ['aether_grass_block', 'aether_dirt', 'coarse_aether_dirt', 'holystone', 'ferrosite', 'ferrosite_sand'])
    tagfile('aether_boulder_can_place_on', ['aether_grass_block', 'aether_dirt', 'coarse_aether_dirt', 'holystone', 'mossy_holystone', 'ferrosite',
                                             'arctic_snow_block', 'arctic_packed_ice'])
    tagfile('aether_mushroom_can_place_on', ['aether_grass_block', 'aether_dirt', 'ferrosite', 'ferrosite_mud', 'bryalinn_moss_block',
                                              'shayelinn_moss_block', 'ambrelinn_moss_block'])


NAMES = {
    'frozen_lakes': 'Frozen Lakes', 'sheer_tundra': 'Sheer Tundra', 'enduring_woodland': 'Enduring Woodland', 'frigid_sierra': 'Frigid Sierra',
    'shimmering_basin': 'Shimmering Basin', 'flourishing_field': 'Flourishing Field', 'verdant_woods': 'Verdant Woods',
    'shrouded_forest': 'Shrouded Forest', 'violet_highwoods': 'Violet Highwoods', 'turquoise_forest': 'Turquoise Forest',
    'glistening_swamp': 'Glistening Swamp', 'magnetic_scar': 'Magnetic Scar', 'battleground_wastes': 'Battleground Wastes',
    'contaminated_jungle': 'Contaminated Jungle', 'hestveil_caverns': 'Hestveil Caverns', 'expanse': 'Expanse',
}


def write_features(features):
    wipe('worldgen/feature_tmp')
    for n, cfg in features.FEATURES.items():
        write('worldgen/feature/' + n, cfg)
    for n, cfg in features.PLACED.items():
        write('worldgen/placed_feature/' + n, cfg)


OLD_FILES = [
    'worldgen/biome/skyroot_meadow', 'worldgen/biome/skyroot_forest', 'worldgen/biome/skyroot_grove', 'worldgen/biome/skyroot_woodland',
    'timeline/aether_noon', 'tags/timeline/in_aether', 'worldgen/density_function/aether/islands', 'worldgen/density_function/aether/base_3d_noise',
]
OLD_FEATURES = ['aether_ore_ambrosium', 'aether_ore_dirt', 'aether_ore_gravitite', 'aether_ore_gravitite_buried', 'aether_ore_icestone', 'aether_ore_zanite',
                'aether_trees', 'aether_trees_forest', 'aether_trees_grove', 'aether_trees_meadow', 'aether_trees_woodland', 'aether_water_lake', 'aether_water_spring',
                'aether_grass', 'aether_tall_grass', 'aether_white_flower', 'aether_purple_flower', 'aether_berry_bush', 'aercloud_bank', 'aercloud_blue', 'aercloud_cold',
                'aercloud_golden']


def remove_old():
    for rel in OLD_FILES:
        path = os.path.join(DATA, rel + '.json')
        if os.path.exists(path):
            os.remove(path)
    for n in OLD_FEATURES:
        for kind in ('feature', 'placed_feature'):
            path = os.path.join(DATA, 'worldgen/%s/%s.json' % (kind, n))
            if os.path.exists(path):
                os.remove(path)


def main(parts):
    if 'terrain' in parts:
        wipe('worldgen/density_function/aether')
        wipe('worldgen/noise/aether')
        build_terrain()
    if 'world' in parts:
        import aether_world_features as features
        features.build_all()
        remove_old()
        write_features(features)
        build_noise_settings()
        build_dimension_type()
        build_dimension()
        build_material_rule()
        build_biomes(features)
        build_block_tags()
        write_lang()


def write_lang():
    d = {'biome.%s.%s' % (NS, k): v for k, v in NAMES.items()}
    out = os.path.join(ROOT, 'src/main/resources/assets/jaymospowers/lang/parts/aether_world.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write('\n')


if __name__ == '__main__':
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_aether_world
    gen_aether_world.main(sys.argv[1:] or ['terrain', 'world'])
