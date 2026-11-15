"""Continents: the Overworld's land as a few great continents with wide oceans between (Andy, 2026-11-15: "world gen
should be continental with large oceans").

Writes kingdoms/src/main/resources/data:
- jaymoskingdoms/worldgen/noise/continents/{land,isles}.json: the continents' own noises (broad land, rare open-sea islands).
- jaymoskingdoms/worldgen/density_function/continents/shape.json: the home world's continentalness. A broad, slow noise
  (continents about ten thousand blocks across) with the game's own continentalness on top for ragged coasts, capes
  and coastal islets, then a spline that splits it into land and deep sea with only a narrow shelf between, so the
  sea between continents is open ocean, not shallows full of islands. Rare islands rise out on the open sea, and the
  land is lifted round the world's origin so the world's spawn stands on a continent.
  tools/gen_mohknee.py uses it as the home world's shape everywhere off Mohknee and its sea.
- jaymoskingdoms/worldgen/density_function/continents/{latitude,temperature,vegetation}.json: the home world's climate,
  a heat map over the continents (see the climate section below); gen_mohknee.py uses them off Mohknee.
- minecraft/worldgen/density_function/{overworld,overworld_large_biomes,overworld_amplified}/offset.json: the game's
  own land height, RAISE higher. The higher sea (seas/HighSea, +8 in new worlds) drowned every low plain and river flat
  into thousands of islets and pools (measured: 25% land in over 5,000 pieces); with the ground lifted as far as the
  sea, coasts, plains and beaches stand where the game meant them, and only the continents decide where the sea is.

Needs the 26.3 game jar (for the game's own offset files): pass its path, or it is looked for in the Gradle cache.
Run: python -I tools/gen_continents.py [minecraft-common.jar], then python -I tools/gen_mohknee.py
"""
import glob
import json
import os
import sys
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kingdoms', 'src', 'main', 'resources', 'data')

# ---- tuning ----
LAND_SCALE = 0.025         # the land noise's xz scale: its broadest wave is 256 / this blocks (about 10,000)
COAST_DETAIL = 0.12        # how much of the game's own continentalness rides on it (ragged coasts, capes, islets)
SHORE = 0.11               # the land noise's value at the shore: the larger, the less land (about 30% at 0.11)
ISLE_SCALE = 0.125         # the island noise's xz scale
ISLE_FROM = 0.55           # islands rise where the island noise passes this (rare: about one per few thousand blocks of sea)
ISLE_LIFT = 0.9            # and lift the ground this far (enough to break the surface of all but the deepest sea)
ISLE_STEEP = 6.0           # how sharply (the island's shore)
HOME_LIFT = 0.35           # the land lifted round the world's origin (so the spawn stands on a continent) ...
HOME_REACH = 3000          # ... fully within HOME_REACH - HOME_BAND blocks, fading out to HOME_REACH
HOME_BAND = 2500
# the land noise (after detail, islands and home lift) to continentalness: deep sea, a narrow shelf, the shore, then inland
SPLINE = [(-1.5, -0.75), (SHORE - 0.30, -0.72), (SHORE - 0.12, -0.5), (SHORE - 0.05, -0.3), (SHORE, -0.15), (SHORE + 0.03, -0.08),
          (SHORE + 0.10, 0.07), (SHORE + 0.25, 0.42), (SHORE + 0.45, 0.77), (1.5, 1.0)]
RAISE = 8                  # blocks the land is lifted: the higher sea's rise (HighSea.RISE)
OFFSET_BASE = -0.50375     # the game's own offset constant (asserted against the jar)
BLOCKS_PER_OFFSET = 128.0  # depth's y gradient: 3.0 over 384 blocks


def write(path, obj):
    path = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def add(a, b):
    return {'type': 'minecraft:add', 'left': a, 'right': b}


def mul(a, b):
    return {'type': 'minecraft:mul', 'left': a, 'right': b}


def clamp(x, lo, hi):
    return {'type': 'minecraft:clamp', 'input': x, 'min': lo, 'max': hi}


def noise(name, scale):
    return {'type': 'minecraft:noise', 'noise': name, 'shift_x': 'minecraft:shift_x', 'shift_z': 'minecraft:shift_z',
            'xz_scale': scale, 'y_scale': 0.0}


def distance(x, z):
    return {'type': 'minecraft:slice', 'axis': 'y', 'coordinate': 0,
            'input': {'type': 'minecraft:distance_to_point', 'point': [x, 0, z], 'metric': 'euclidean'}}


def within(d, radius, band):
    """1 nearer than radius - band, 0 past radius, a straight slope between."""
    return clamp(add(radius / band, mul(-1.0 / band, d)), 0.0, 1.0)


def spline(coordinate, points):
    out = []
    for i, (location, value) in enumerate(points):
        # the slope of the line through its neighbours: smooth, and never overshooting the next point by much
        slope = 0.0 if i in (0, len(points) - 1) else (points[i + 1][1] - points[i - 1][1]) / (points[i + 1][0] - points[i - 1][0])
        out.append({'derivative': round(slope, 4), 'location': round(location, 4), 'value': value})
    return {'type': 'minecraft:spline', 'spline': {'coordinate': coordinate, 'points': out}}


def game_jar():
    if len(sys.argv) > 1:
        return sys.argv[1]
    home = os.path.expanduser('~')
    for pattern in (os.path.join(home, '.gradle', 'caches', 'fabric-loom', '26.3', 'minecraft-common.jar'),
                    os.path.join(ROOT, '..', '..', '..', '..', '.gradle', 'loom-cache', 'minecraftMaven', 'net', 'minecraft', 'minecraft-common-*', '26.3', '*.jar')):
        found = sorted(glob.glob(pattern))
        if found:
            return found[0]
    sys.exit('No 26.3 game jar found: build kingdoms once, or pass the minecraft-common.jar path')


# ---- the continents ----
write('jaymoskingdoms/worldgen/noise/continents/land.json',
      {'base_octave': -8, 'octave_count': 5, 'amplitude_modifiers': [1.0, 0.7, 0.5, 0.35, 0.2]})
write('jaymoskingdoms/worldgen/noise/continents/isles.json',
      {'base_octave': -6, 'octave_count': 3, 'amplitude_modifiers': [1.0, 0.5, 0.25]})

land = add(noise('jaymoskingdoms:continents/land', LAND_SCALE), mul(COAST_DETAIL, noise('minecraft:continentalness', 0.25)))
isles = mul(ISLE_LIFT, clamp(mul(ISLE_STEEP, add(noise('jaymoskingdoms:continents/isles', ISLE_SCALE), -ISLE_FROM)), 0.0, 1.0))
home = mul(HOME_LIFT, within(distance(0, 0), HOME_REACH, HOME_BAND))
write('jaymoskingdoms/worldgen/density_function/continents/shape.json',
      {'type': 'minecraft:cache', 'input': spline(add(add(land, isles), home), SPLINE)})

# ---- the climate: a heat map laid over the continents ----
# Latitude: cold to the north (-z), hot to the south (+z), the world's origin between, in bands that repeat as on a
# globe (pole, temperate, equator, temperate, pole ...). Continent-sized wobbles of the game's own large temperature
# noise bend the bands, and the land's middle is harsher than its coasts (hotter in the south, colder in the north).
# Wet coasts and dry interiors, a dry belt (deserts) on the equator's side of the temperate band, a wet equator
# (jungle). Biomes follow these numbers, and the settlements, their buildings, dress, coin and blood follow the biomes.
LATITUDE = 6000            # blocks from the origin to a pole (north) or to the equator (south); a whole band is twice this
LATITUDE_HEAT = 0.8        # how hot the equator is and how cold a pole is (the game's temperature runs -1 to 1)
INLAND_EXTREMES = 0.35     # how much harsher the heat or cold is deep inland (by continentalness 0 to 1)
HEAT_WOBBLE = 0.5          # the large temperature noise (continent-sized) bending the bands
HEAT_LOCAL = 0.15          # the game's own temperature noise, for local variety
WET_WOBBLE = 0.5           # the large vegetation noise
WET_LOCAL = 0.15           # the game's own vegetation noise
# rain by continentalness (coasts wet, interiors dry) and by latitude (-1 pole, 0 the origin's band, 1 equator)
COAST_RAIN = [(-1.2, 0.15), (-0.11, 0.2), (0.1, 0.05), (0.35, -0.15), (0.7, -0.35), (1.0, -0.4)]
LATITUDE_RAIN = [(-1.0, -0.15), (-0.3, 0.12), (0.15, 0.0), (0.45, -0.35), (0.75, 0.0), (0.92, 0.55), (1.0, 0.6)]

SHAPE = 'jaymoskingdoms:continents/shape'
latitude = {'type': 'minecraft:gradient', 'axis': 'z', 'tiling': 'mirrored_repeat', 'from_coordinate': -LATITUDE,
            'to_coordinate': LATITUDE, 'from_value': -1.0, 'to_value': 1.0}
write('jaymoskingdoms/worldgen/density_function/continents/latitude.json', {'type': 'minecraft:cache', 'input': latitude})
LAT = 'jaymoskingdoms:continents/latitude'
heat = mul(LATITUDE_HEAT, mul(LAT, add(1.0, mul(INLAND_EXTREMES, clamp(SHAPE, 0.0, 1.0)))))
heat = add(add(heat, mul(HEAT_WOBBLE, noise('minecraft:temperature_large', 0.25))), mul(HEAT_LOCAL, noise('minecraft:temperature', 0.25)))
write('jaymoskingdoms/worldgen/density_function/continents/temperature.json', {'type': 'minecraft:cache', 'input': clamp(heat, -1.0, 1.0)})
wet = add(spline(SHAPE, COAST_RAIN), spline(LAT, LATITUDE_RAIN))
wet = add(add(wet, mul(WET_WOBBLE, noise('minecraft:vegetation_large', 0.25))), mul(WET_LOCAL, noise('minecraft:vegetation', 0.25)))
write('jaymoskingdoms/worldgen/density_function/continents/vegetation.json', {'type': 'minecraft:cache', 'input': clamp(wet, -1.0, 1.0)})

# ---- the land lifted with the sea ----
with open(os.path.join(ROOT, '..', '..', 'java', 'com', 'jaymoskingdoms', 'seas', 'HighSea.java')) as f:
    assert 'RISE = %d;' % RAISE in f.read(), 'HighSea.RISE disagrees with RAISE'
lift = RAISE / BLOCKS_PER_OFFSET
with zipfile.ZipFile(game_jar()) as jar:
    for world in ('overworld', 'overworld_large_biomes', 'overworld_amplified'):
        offset = json.loads(jar.read('data/minecraft/worldgen/density_function/%s/offset.json' % world))
        land_height = offset['input']['second']
        assert offset['input']['type'] == 'minecraft:lerp' and land_height['type'] == 'minecraft:add' and land_height['left'] == OFFSET_BASE, \
            world + ': the game\'s offset is not what this tool was written for'
        land_height['left'] = round(OFFSET_BASE + lift, 6)
        write('minecraft/worldgen/density_function/%s/offset.json' % world, offset)
print('continents: shore at', SHORE, 'land scale', LAND_SCALE, '; land lifted', RAISE, 'blocks (offset +%g)' % lift)
