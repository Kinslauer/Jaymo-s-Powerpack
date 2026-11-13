"""Mohknee, the blossom folk's continent: the Overworld's land, sea and climate files (docs/BlossomLands-Plan.md, patch 1).

Writes kingdoms/src/main/resources/data: jaymoskingdoms/worldgen/density_function/mohknee/*.json and the five
minecraft/worldgen/density_function/overworld files that use them (continents, temperature, vegetation, erosion, ridges). Everything is
the game's own density functions (distance_to_point, slice, lerp, clamp): no code. The numbers here are the ones in
com.jaymoskingdoms.seas.Mohknee; change both together. Run: python -I tools/gen_mohknee.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kingdoms', 'src', 'main', 'resources', 'data')

# ---- tuning (mirrored in Mohknee.java) ----
CENTRE = (28000, 0)        # the land's middle (block x, z): due east of the world's origin
LAND = 3000                # the land's radius
COAST = 450                # the band its shore wanders in (inside LAND)
SEA = 16000                # open sea on every side, past the land
FADE = 1500                # the band past the sea where the home world's own shape comes back
WOBBLE = 450               # how far (blocks) the shore strays from a circle
CLIMATE_REACH = 900        # the land's weather reaches this far past its shore
# The Tideborn's islands out on the crossing (x, z, size): three on the way from the west, the rest for other headings.
ISLANDS = [(12500, -900, 240), (17000, 1100, 220), (21500, -500, 250), (28000, -9000, 230), (28000, 9500, 230),
           (20500, -8000, 220), (20000, 8500, 240), (38500, 600, 230)]
ISLAND_WOBBLE = 60         # an island's shore lies about three quarters of its size out: small enough for Offshore.island
ISLAND_SHORE = 0.4         # the share of its size its shore slopes over
DEEP = -0.62               # the crossing's continentalness (deep ocean; never the mushroom fields' -1.05)
SHORE_LAND = 0.30          # an island's middle: well inland, so its ground stands clear of the higher sea
ISLAND_CLIMATE = (0.05, -0.22, 0.10)   # an island's temperature, vegetation and erosion: mild, level plains
ISLAND_RIDGES = 0.5
# the land's climate bands: cool to mild (no desert, jungle or badlands), on the dry side (cherry groves, meadows, forest)
# Where the game puts cherry groves (OverworldBiomeBuilder, the plateau biomes): far inland (continentalness 0.3 up),
# erosion -0.375 to 0.05, mild (temperature -0.15 to 0.2) and dry (vegetation under -0.1), on the positive side of
# the ridges noise; the negative side of the same ground is meadow. So the land's heart is held far inland and high.
INLAND = (0.32, 0.55, 0.42, 0.20)         # lowest, highest, middle, swing: the heart's continentalness
TEMPERATURE = (-0.30, 0.18, 0.03, 0.20)   # the same four, of the land's own noise
VEGETATION = (-0.80, 0.05, -0.30, 0.50)
EROSION = (-0.60, 0.04, -0.25, 0.30)      # under -0.375 the ridges rise to snowy slopes and peaks


def ref(name):
    return 'jaymoskingdoms:mohknee/' + name


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


def lerp(alpha, first, second):
    return {'type': 'minecraft:lerp', 'alpha': alpha, 'first': first, 'second': second}


def within(distance, radius, band):
    """1 nearer than radius - band, 0 past radius, a straight slope between."""
    return clamp(add(radius / band, mul(-1.0 / band, distance)), 0.0, 1.0)


def distance(x, z):
    # sliced at y 0: the distance along the ground, the same at every height
    return {'type': 'minecraft:slice', 'axis': 'y', 'coordinate': 0,
            'input': {'type': 'minecraft:distance_to_point', 'point': [x, 0, z], 'metric': 'euclidean'}}


def noise(name, scale):
    return {'type': 'minecraft:noise', 'noise': name, 'shift_x': 'minecraft:shift_x', 'shift_z': 'minecraft:shift_z',
            'xz_scale': scale, 'y_scale': 0.0}


def mohknee(name, obj):
    write('jaymoskingdoms/worldgen/density_function/mohknee/' + name + '.json', obj)


# the home world's own shape, as these three files were before the far land
mohknee('home_continents', add(noise('minecraft:continentalness_large', 0.25), -0.06))
mohknee('home_temperature', noise('minecraft:temperature_large', 0.25))
mohknee('home_vegetation', noise('minecraft:vegetation_large', 0.25))
mohknee('home_erosion', noise('minecraft:erosion_large', 0.25))
mohknee('home_ridges', noise('minecraft:ridge', 0.25))   # the game's own: Kingdoms never changed it before

mohknee('wobble', {'type': 'minecraft:cache', 'input': noise('minecraft:continentalness', 0.5)})
mohknee('distance', {'type': 'minecraft:cache', 'input': add(distance(*CENTRE), mul(WOBBLE, ref('wobble')))})
mohknee('land', {'type': 'minecraft:cache', 'input': within(ref('distance'), LAND, COAST)})
mohknee('sea', {'type': 'minecraft:cache', 'input': within(ref('distance'), LAND + SEA + FADE, FADE)})
mohknee('climate', {'type': 'minecraft:cache', 'input': within(ref('distance'), LAND + CLIMATE_REACH, 600)})

islands = None
for x, z, size in ISLANDS:
    one = within(add(distance(x, z), mul(ISLAND_WOBBLE, ref('wobble'))), size, size * ISLAND_SHORE)
    islands = one if islands is None else {'type': 'minecraft:max', 'left': islands, 'right': one}
mohknee('islands', {'type': 'minecraft:cache', 'input': islands})
# an island's own weather: mild, level grassland whatever the sea round it is like, so a village can stand on it
mohknee('island_climate', {'type': 'minecraft:cache', 'input': clamp(mul(3.0, ref('islands')), 0.0, 1.0)})

home = ref('home_continents')
crossing = lerp(ref('sea'), home, add(DEEP, mul(0.08, home)))
crossing = lerp(ref('islands'), crossing, SHORE_LAND)
shore = lerp(ref('land'), crossing, clamp(add(INLAND[2], mul(INLAND[3], home)), INLAND[0], INLAND[1]))
write('minecraft/worldgen/density_function/overworld/continents.json', {'type': 'minecraft:cache', 'input': shore})


def band(home_name, own_noise, island, lo, hi, mid, swing):
    """The home world's value off the land; on it, a band of the land's own (finer) noise, so the land has variety of its own."""
    own = clamp(add(mid, mul(swing, noise(own_noise, 1.0))), lo, hi)
    return lerp(ref('island_climate'), lerp(ref('climate'), ref(home_name), own), island)


write('minecraft/worldgen/density_function/overworld/temperature.json',
      band('home_temperature', 'minecraft:temperature', ISLAND_CLIMATE[0], *TEMPERATURE))
write('minecraft/worldgen/density_function/overworld/vegetation.json',
      band('home_vegetation', 'minecraft:vegetation', ISLAND_CLIMATE[1], *VEGETATION))
write('minecraft/worldgen/density_function/overworld/erosion.json',
      {'type': 'minecraft:cache', 'input': band('home_erosion', 'minecraft:erosion', ISLAND_CLIMATE[2], *EROSION)})
# an island has no river valley through it (the higher sea would drown half of it): its ridges noise is held high
write('minecraft/worldgen/density_function/overworld/ridges.json',
      {'type': 'minecraft:cache', 'input': lerp(ref('island_climate'), ref('home_ridges'), ISLAND_RIDGES)})
# one village on each island, at its middle: the island structure with only_at, offered every chunk by its own set
# (SharedPools.OWN_PLACES keeps that set out of the shared village pool)
write('jaymoskingdoms/worldgen/structure/crossing_isle_village.json', {
    'type': 'jaymoskingdoms:island', 'biomes': '#jaymoskingdoms:has_structure/isle_village', 'step': 'surface_structures',
    'terrain_adaptation': 'beard_thin', 'spawn_overrides': {},
    'structures': ['minecraft:village_plains'], 'only_at': [[x, 64, z] for x, z, _ in ISLANDS]})
write('jaymoskingdoms/worldgen/structure_set/crossing_isle_villages.json', {
    'structures': [{'structure': 'jaymoskingdoms:crossing_isle_village', 'weight': 1}],
    'placement': {'type': 'minecraft:random_spread', 'spacing': 1, 'separation': 0, 'salt': 771203114}})
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'kingdoms', 'src', 'main', 'java', 'com', 'jaymoskingdoms', 'seas',
                       'Mohknee.java')) as f:
    java = f.read()
for x, z, size in ISLANDS:
    assert '{%d, %d, %d}' % (x, z, size) in java, 'Mohknee.ISLANDS is missing %s' % ((x, z, size),)
for need in ('CENTRE_X = %d, CENTRE_Z = %d' % CENTRE, 'LAND = %d, WOBBLE = %d' % (LAND, WOBBLE), 'SEA = %d, FADE = %d' % (SEA, FADE)):
    assert need in java, 'Mohknee.java disagrees: ' + need
print('mohknee: centre', CENTRE, 'land', LAND, 'sea', SEA, 'islands', len(ISLANDS))
