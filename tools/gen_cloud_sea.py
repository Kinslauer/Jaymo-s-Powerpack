"""Writes the Cloud Sea's world generation (aether/cloudsea/CloudSea.java): the band of cloud and open air between the
Overworld and the Aether in a One Dimension world.

  data/jaymospowers/dimension/cloud_sea.json            the band: a noise generator, one biome
  data/jaymospowers/dimension_type/cloud_sea.json       256 tall, the Overworld's day, night, sky and fog
  data/jaymospowers/worldgen/biome/cloud_sea.json       nothing spawns, nothing grows
  data/jaymospowers/worldgen/noise/cloud_sea/*.json     the noises below
  data/jaymospowers/worldgen/noise_settings/cloud_sea.json   cloudbank wherever the density is above 0
  data/jaymospowers/worldgen/material_rule/cloud_sea.json

Two decks, each "how far inside the bank is this spot" (blocks, scaled) plus a little 3D puff:
  the overcast   one bank, its middle wandering about OVERCAST, with wide wells where the hole noise runs high
  the low banks  loose clouds about LOW, only where their mask runs high
Heights are the band's own (0 at its floor). Change the numbers here, run `python3 tools/gen_cloud_sea.py`, and keep the
copies in CloudSea.java (LOW_DECK, OVERCAST, OVERCAST_BOTTOM, OVERCAST_TOP) in step.
"""
import json, os

# ---- tuning ----
HEIGHT = 256
# the overcast: middle height and how far it wanders; half its thickness and how far that varies
OVERCAST, OVERCAST_WANDER = 184.0, 6.0
OVERCAST_HALF, OVERCAST_HALF_VARY = 7.0, 4.0
# wells: the hole noise above this is open sky (0.5: about 9% of the sky open, 0.55: about 6%, 0.6: about 4%)
WELL_AT = 0.55
# the low banks: middle height and wander; mask above LOW_AT is cloud (0.2: about a quarter of the sky); half thickness at most
LOW, LOW_WANDER = 72.0, 10.0
LOW_AT, LOW_HALF = 0.2, 7.0
# how hard the 3D puff roughens each deck's edge
OVERCAST_PUFF, LOW_PUFF = 0.25, 0.3
# density per block of depth into a bank
SLOPE = 0.15

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/jaymospowers')
NS = 'jaymospowers:cloud_sea/'

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
def clamp(x, lo, hi): return {'type': 'minecraft:clamp', 'input': x, 'min': lo, 'max': hi}
def noise(name, xz=1.0, y=0.0): return {'type': 'minecraft:noise', 'noise': NS + name, 'xz_scale': xz, 'y_scale': y}
Y = 'minecraft:y'

def bank(middle, half, puff):
    """Density of a bank: positive within `half` blocks of `middle` (both density functions), roughened by the puff."""
    away = op('max', sub(Y, middle), sub(middle, Y))
    return add(mul(sub(half, away), SLOPE), mul(noise('puff', 1.0, 1.0), puff))

overcast = sub(
    bank(add(OVERCAST, mul(noise('overcast_height'), OVERCAST_WANDER)),
         add(OVERCAST_HALF, mul(clamp(mul(noise('overcast_thickness'), 2.0), -1.0, 1.0), OVERCAST_HALF_VARY)), OVERCAST_PUFF),
    mul(clamp(mul(sub(noise('wells'), WELL_AT), 8.0), 0.0, 1.0), 30.0))
low = bank(add(LOW, mul(noise('low_height'), LOW_WANDER)),
           mul(clamp(mul(sub(noise('low_mask'), LOW_AT), 4.0), -1.0, 1.0), LOW_HALF), LOW_PUFF)
final = {'type': 'minecraft:interpolated', 'cell_size_xz': 4, 'cell_size_y': 4, 'input': op('max', overcast, low)}

# base octave -8: features about 256 blocks across; -5: about 32
for name, octave, count in [('overcast_height', -8, 2), ('overcast_thickness', -7, 2), ('wells', -8, 2), ('puff', -5, 3),
                            ('low_mask', -7, 3), ('low_height', -8, 2)]:
    write('worldgen/noise/cloud_sea/%s.json' % name, {'base_octave': octave, 'octave_count': count})

write('worldgen/material_rule/cloud_sea.json', {'type': 'minecraft:block', 'result_state': 'jaymospowers:cloudbank'})
write('worldgen/noise_settings/cloud_sea.json', {
    'default_block': 'jaymospowers:cloudbank',
    'default_fluid': 'minecraft:water',
    'disable_mob_generation': True,
    'legacy_random_source': False,
    'material_rule': 'jaymospowers:cloud_sea',
    'noise': {'height': HEIGHT, 'min_y': 0},
    'noise_router': {'chunk_surface_level': 0.0, 'continents': 0.0, 'depth': 0.0, 'erosion': 0.0, 'final_density': final,
                     'ridges': 0.0, 'temperature': 0.0, 'vegetation': 0.0},
    'sea_level': -64,
    'spawn_target': [],
})
write('dimension/cloud_sea.json', {
    'type': 'jaymospowers:cloud_sea',
    'generator': {'type': 'minecraft:noise', 'settings': 'jaymospowers:cloud_sea',
                  'biome_source': {'type': 'minecraft:fixed', 'biome': 'jaymospowers:cloud_sea'}},
})
music = {'creative': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.creative'},
         'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.game'}}
no_sleep = {'translate': 'block.minecraft.bed.no_sleep'}
write('dimension_type/cloud_sea.json', {
    'ambient_light': 0.0,
    'attributes': {
        'minecraft:audio/background_music': music,
        'minecraft:gameplay/bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'never', 'error_message': no_sleep},
        'minecraft:gameplay/nether_portal_spawns_piglin': False,
        'minecraft:gameplay/respawn_anchor_works': False,
        'minecraft:gameplay/straw_bed_rule': {'can_set_spawn': 'never', 'can_sleep': 'never', 'destroy_on_leave': True,
                                              'error_message': no_sleep},
        'minecraft:visual/ambient_light_color': '#0a0a0a',
        # the game's own flat clouds stay where the Overworld has them, so they are still under you once you are up here
        # (CustomChunks reads a band's attributes as they are: this height is the stacked level's, and the Overworld is
        # the band that keeps its own heights)
        'minecraft:visual/cloud_color': '#ccffffff',
        'minecraft:visual/cloud_height': 192.33,
        'minecraft:visual/fog_color': '#c0d8ff',
        'minecraft:visual/sky_color': '#78a7ff',
    },
    'coordinate_scale': 1.0,
    'default_clock': 'minecraft:overworld',
    'has_ceiling': False,
    'has_ender_dragon_fight': False,
    'has_skylight': True,
    'height': HEIGHT,
    'infiniburn': '#minecraft:infiniburn_overworld',
    'logical_height': HEIGHT,
    'min_y': 0,
    'monster_spawn_block_light_limit': 0,
    'monster_spawn_light_level': 0,
    'timelines': '#minecraft:in_overworld',
})
none = {k: [] for k in ['ambient', 'axolotls', 'creature', 'misc', 'monster', 'underground_water_creature', 'water_ambient',
                        'water_creature']}
write('worldgen/biome/cloud_sea.json', {
    'attributes': {'minecraft:gameplay/natural_mob_spawns': {'argument': {'spawn_costs': {}, 'spawns_by_category': none},
                                                              'modifier': 'overlay'}},
    'carvers': [],
    'downfall': 0.4,
    'effects': {'water_color': '#3f76e4'},
    'features': [[] for _ in range(11)],
    'has_precipitation': False,
    'temperature': 0.5,
})
print('wrote the Cloud Sea')
