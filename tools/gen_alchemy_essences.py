"""The hand-set essence table (track A): what vanilla things are made of, as data files.

Writes src/main/resources/data/jaymospowers/alchemy/essences/*.json. Everything not listed here is worked out from
crafting and smelting recipes when a server starts (see alchemy/essence/Essences.java), so only the raw materials and
the things recipes can't explain are set by hand. Keys are item ids, or "#tag" for an item tag; for creatures, entity
type ids or "#entity_type_tag". An id beats a tag; among tags the first listed wins.

Amounts are 1-20 for ordinary things. Early, everyday things carry the seven primals (so a player's first scans find
them), the compounds sit on rarer and more worked things.

Run from the repository root: python3 -I tools/gen_alchemy_essences.py
"""
import json
import os

OUT = "src/main/resources/data/jaymospowers/alchemy/essences/"
PRIMALS = {"fire", "water", "earth", "air", "salt", "sulphur", "mercury"}
ALL = PRIMALS | {"life", "light", "metal", "frost", "motion", "spark", "rot", "venom", "crystal", "spirit", "plant", "beast",
                 "death", "mind", "shadow", "arcana", "wealth", "craft", "war", "undeath", "flux"}


def parse(spec):
    words = spec.split()
    assert len(words) % 2 == 0, spec
    out = {}
    for i in range(0, len(words), 2):
        assert words[i] in ALL, "unknown essence " + words[i]
        out[words[i]] = int(words[i + 1])
    return out


def mc(key):
    if key.startswith("#"):
        return "#" + (key[1:] if ":" in key else "minecraft:" + key[1:])
    return key if ":" in key else "minecraft:" + key


def table(rows):
    out = {}
    for line in rows.strip().splitlines():
        line = line.strip()
        if not line or line.startswith("//"):
            continue
        key, spec = line.split(":", 1)
        out[mc(key.strip())] = parse(spec)
    return out


BLOCKS = table("""
// earth and stone
dirt: earth 5 water 1
coarse_dirt: earth 5 air 1
rooted_dirt: earth 5 plant 2
podzol: earth 5 plant 2 rot 1
mycelium: earth 5 rot 2 plant 1
grass_block: earth 5 plant 2
mud: earth 4 water 4
clay: earth 4 water 3 salt 1
clay_ball: earth 1 water 1
sand: earth 3 salt 2
red_sand: earth 3 salt 2 fire 1
gravel: earth 4 air 1
stone: earth 6 salt 2
cobblestone: earth 5 salt 1
deepslate: earth 7 salt 2
cobbled_deepslate: earth 6 salt 1
granite: earth 5 salt 2 fire 1
diorite: earth 5 salt 3
andesite: earth 5 salt 2 air 1
tuff: earth 5 air 1 salt 1
calcite: salt 5 earth 2
dripstone_block: earth 5 water 2 salt 1
pointed_dripstone: earth 2 water 1
netherrack: earth 4 fire 3
basalt: earth 6 fire 2
blackstone: earth 6 salt 1 shadow 1
soul_sand: earth 4 spirit 3
soul_soil: earth 4 spirit 2
end_stone: earth 6 air 3 mercury 1
obsidian: earth 8 fire 4 salt 3
crying_obsidian: earth 8 fire 4 salt 3 spirit 4
magma_block: earth 5 fire 6 sulphur 2
sandstone: earth 5 salt 3
red_sandstone: earth 5 salt 3 fire 1
terracotta: earth 5 fire 2 water 1
bricks: earth 5 fire 2 water 1
brick: earth 1 fire 1
snow_block: water 3 air 2 frost 3
snow: water 1 air 1 frost 1
snowball: water 1 frost 1
ice: water 3 salt 2 frost 4
packed_ice: water 4 salt 3 frost 8
blue_ice: water 5 salt 4 frost 14
sponge: water 8 air 3 life 2
wet_sponge: water 14 air 3 life 2
moss_block: earth 3 water 2 plant 4
moss_carpet: earth 1 water 1 plant 2
sculk: shadow 3 mind 2 rot 2
sculk_catalyst: shadow 6 mind 4 death 4 rot 3
sculk_sensor: shadow 4 mind 3 spark 3
sculk_shrieker: shadow 6 mind 4 undeath 4
amethyst_block: crystal 8 salt 4 arcana 2
budding_amethyst: crystal 10 arcana 6 life 3
amethyst_cluster: crystal 10 arcana 3
small_amethyst_bud: crystal 3 arcana 1
medium_amethyst_bud: crystal 5 arcana 1
large_amethyst_bud: crystal 8 arcana 2
glass: crystal 2 salt 1 air 1
tinted_glass: crystal 3 shadow 2 salt 1
glowstone: light 8 fire 4 crystal 2
sea_lantern: light 6 water 4 crystal 4
shroomlight: light 4 plant 3 fire 2
honey_block: life 4 water 4 earth 2
honeycomb_block: life 6 earth 4 plant 2
slime_block: water 12 mercury 8 life 4
dragon_egg: arcana 20 beast 16 shadow 12 mercury 12
bone_block: death 6 salt 12 earth 3
cobweb: air 3 venom 2 beast 1
// ores and metal blocks
coal_ore: earth 6 fire 4 sulphur 2
deepslate_coal_ore: earth 7 fire 4 sulphur 2
iron_ore: earth 8 metal 5 salt 1
deepslate_iron_ore: earth 9 metal 5 salt 1
copper_ore: earth 8 metal 4 spark 1
deepslate_copper_ore: earth 9 metal 4 spark 1
gold_ore: earth 8 metal 4 wealth 3
deepslate_gold_ore: earth 9 metal 4 wealth 3
nether_gold_ore: earth 6 fire 3 metal 3 wealth 3
redstone_ore: earth 7 mercury 5 spark 2
deepslate_redstone_ore: earth 8 mercury 5 spark 2
lapis_ore: earth 7 salt 3 crystal 3 water 1
deepslate_lapis_ore: earth 8 salt 3 crystal 3 water 1
diamond_ore: earth 8 crystal 6 wealth 2
deepslate_diamond_ore: earth 9 crystal 6 wealth 2
emerald_ore: earth 8 wealth 4 crystal 3
deepslate_emerald_ore: earth 9 wealth 4 crystal 3
nether_quartz_ore: earth 6 fire 3 crystal 4 salt 2
ancient_debris: earth 10 fire 6 metal 12 wealth 4
iron_block: metal 40 earth 10
gold_block: metal 30 wealth 30
copper_block: metal 36 spark 10 earth 6
diamond_block: crystal 60 light 15 wealth 20 earth 10
emerald_block: wealth 36 crystal 28 life 10
lapis_block: salt 20 crystal 18 water 12 arcana 5
redstone_block: mercury 20 spark 8 earth 6
coal_block: fire 28 earth 18 sulphur 14
netherite_block: metal 80 fire 30 earth 30 wealth 20
raw_iron_block: metal 30 earth 20
raw_copper_block: metal 28 earth 20 spark 6
raw_gold_block: metal 24 wealth 20 earth 20
// plants
#logs: earth 3 water 2 plant 4
#planks: earth 1 plant 2
#leaves: air 2 water 1 plant 2
#saplings: earth 2 water 1 plant 4 life 2
#flowers: air 1 plant 4 life 1
#wool: air 3 beast 2 earth 1
#coals: fire 4 earth 3 sulphur 2
#fishes: water 5 life 3 beast 1
short_grass: air 1 earth 1 plant 1
tall_grass: air 2 earth 1 plant 2
fern: air 1 earth 1 plant 2
large_fern: air 2 earth 1 plant 3
dead_bush: earth 1 air 1 death 1
vine: air 1 water 1 plant 3
glow_lichen: light 2 plant 2 earth 1
lily_pad: water 2 air 1 plant 3
seagrass: water 2 plant 2
kelp: water 3 plant 2
dried_kelp: air 2 plant 2 fire 1
dried_kelp_block: air 14 plant 12 fire 4
sugar_cane: water 2 air 1 plant 3
bamboo: air 2 earth 1 plant 2
cactus: water 3 earth 2 plant 2 venom 1
pumpkin: water 3 earth 3 plant 4
carved_pumpkin: water 3 earth 3 plant 4 spirit 1
melon: water 6 earth 2 plant 3
hay_block: earth 8 air 6 plant 9
brown_mushroom: earth 2 rot 2 plant 1
red_mushroom: earth 2 venom 2 plant 1
mushroom_stem: earth 3 air 1 plant 2
brown_mushroom_block: earth 3 air 1 rot 2
red_mushroom_block: earth 3 air 1 venom 2
crimson_fungus: earth 2 fire 2 rot 2 plant 1
warped_fungus: earth 2 air 2 rot 2 plant 1
crimson_stem: earth 3 fire 2 plant 4
warped_stem: earth 3 air 2 plant 4
nether_wart: earth 2 fire 2 plant 3
nether_wart_block: earth 3 fire 3 plant 5
warped_wart_block: earth 3 air 3 plant 5
weeping_vines: earth 1 fire 1 plant 2
twisting_vines: earth 1 air 1 plant 2
chorus_flower: air 3 mercury 3 plant 2 shadow 1
chorus_fruit: air 2 mercury 2 plant 2
cherry_leaves: air 2 water 1 plant 3
azalea: earth 2 water 1 plant 5 life 1
big_dripleaf: water 3 air 2 plant 3
small_dripleaf: water 2 air 1 plant 2
spore_blossom: air 2 plant 3 venom 1 life 1
""")

ITEMS = table("""
// metals and gems
coal: fire 4 earth 3 sulphur 2
charcoal: fire 4 earth 1 sulphur 1 plant 1
raw_iron: earth 4 metal 6
raw_copper: earth 4 metal 5 spark 1
raw_gold: earth 4 metal 5 wealth 3
iron_ingot: metal 8 earth 2
iron_nugget: metal 1
copper_ingot: metal 6 spark 2 earth 1
gold_ingot: metal 6 wealth 6
gold_nugget: metal 1 wealth 1
netherite_scrap: metal 6 fire 4 earth 4
netherite_ingot: metal 16 fire 6 earth 6 wealth 4
diamond: crystal 10 light 3 wealth 4 earth 3
emerald: wealth 6 crystal 5 life 2
lapis_lazuli: salt 3 crystal 3 water 2 arcana 1
quartz: crystal 4 salt 3 fire 1
amethyst_shard: crystal 5 arcana 2 spirit 1
redstone: mercury 4 spark 2 earth 1
glowstone_dust: light 3 fire 2 air 1
prismarine_shard: water 3 crystal 3 light 1
prismarine_crystals: light 3 crystal 4 water 2
flint: earth 3 sulphur 2 salt 1
echo_shard: shadow 6 spirit 4 crystal 4 mind 2
// things of fire, powder and the Nether
gunpowder: sulphur 4 fire 2 salt 1
blaze_powder: fire 5 sulphur 2 spark 1
blaze_rod: fire 8 sulphur 3 spark 2
magma_cream: fire 5 mercury 3 sulphur 2
ghast_tear: water 4 mercury 3 spirit 4
nether_star: arcana 16 light 12 spirit 6 fire 6
fire_charge: fire 5 sulphur 3 earth 1
dragon_breath: fire 6 shadow 6 arcana 8 air 4
// things of the body
bone: salt 4 death 2 earth 1
bone_meal: salt 2 earth 1
rotten_flesh: rot 3 death 2 beast 1
leather: beast 3 earth 1 water 1
rabbit_hide: beast 2 earth 1
rabbit_foot: motion 4 beast 2 wealth 1
feather: air 5 motion 1
egg: life 3 water 2 earth 1
turtle_scute: beast 3 water 2 salt 2
armadillo_scute: beast 3 earth 2 salt 2
phantom_membrane: shadow 4 air 4 undeath 2
spider_eye: venom 3 beast 1 sulphur 1
string: air 2 beast 1 plant 1
slime_ball: water 3 mercury 3 life 2
ink_sac: water 3 shadow 2 mercury 1
glow_ink_sac: water 3 light 3 mercury 1
honeycomb: life 3 earth 2 plant 1
honey_bottle: life 3 water 2 earth 1
nautilus_shell: water 4 salt 3 beast 2 spirit 1
heart_of_the_sea: water 12 spirit 6 wealth 6 arcana 4
shulker_shell: earth 4 mercury 4 shadow 3 crystal 2
ender_pearl: mercury 6 shadow 3 motion 3
ender_eye: mercury 8 shadow 4 motion 3 fire 4 mind 2
wither_skeleton_skull: death 12 undeath 8 shadow 4
skeleton_skull: death 6 undeath 4 salt 2
zombie_head: death 5 undeath 4 rot 3
creeper_head: spark 5 sulphur 4 fire 3
piglin_head: beast 4 wealth 3 war 2
player_head: mind 6 life 4 beast 2
dragon_head: arcana 10 beast 8 fire 6 shadow 4
totem_of_undying: spirit 12 death 6 wealth 6 life 6
elytra: air 14 motion 10 beast 4 arcana 3
trident: metal 8 water 8 war 4 spirit 2
experience_bottle: mind 6 spirit 6 life 4 crystal 1
nether_brick: earth 2 fire 2 shadow 1
resin_clump: plant 2 earth 1 fire 1
// food and farm
apple: plant 3 life 2 water 1
golden_apple: life 8 wealth 10 metal 6 plant 3
enchanted_golden_apple: life 14 wealth 20 metal 12 arcana 10 plant 3
wheat: earth 2 air 1 plant 2
wheat_seeds: earth 1 plant 1
beetroot_seeds: earth 1 plant 1
melon_seeds: earth 1 water 1 plant 1
pumpkin_seeds: earth 1 plant 1
torchflower_seeds: earth 1 light 1 plant 1
pitcher_pod: earth 1 water 1 plant 2
carrot: earth 2 water 1 plant 2
potato: earth 3 water 1 plant 2
poisonous_potato: earth 3 venom 2 plant 2
beetroot: earth 2 water 1 plant 2
sweet_berries: water 2 plant 2 life 1
glow_berries: water 2 light 2 plant 2
cocoa_beans: earth 1 plant 2 fire 1
sugar: water 1 air 1 plant 1
bread: earth 2 air 1 fire 1 plant 2
cookie: earth 1 fire 1 plant 1
cake: life 4 water 3 earth 2 plant 3
pumpkin_pie: life 2 water 2 earth 2 plant 3
beef: beast 3 life 2 water 1
porkchop: beast 3 life 2 water 1
mutton: beast 3 life 2 water 1
chicken: beast 3 life 2 air 1
rabbit: beast 3 life 1 motion 1
cod: water 4 life 2 beast 1
salmon: water 4 life 2 beast 1
tropical_fish: water 3 life 2 light 1
pufferfish: water 3 venom 4 life 1
cooked_beef: beast 3 life 2 fire 2
cooked_porkchop: beast 3 life 2 fire 2
cooked_mutton: beast 3 life 2 fire 2
cooked_chicken: beast 3 life 2 fire 2
cooked_rabbit: beast 3 life 1 fire 2
cooked_cod: water 2 life 2 fire 2
cooked_salmon: water 2 life 2 fire 2
baked_potato: earth 3 fire 2 plant 1
milk_bucket: life 5 water 8 metal 6
water_bucket: water 12 metal 6
lava_bucket: fire 14 earth 4 metal 6
powder_snow_bucket: frost 4 water 3 air 2 metal 6
// glass and potion things
glass_bottle: crystal 1 air 1
potion: water 4 crystal 1
splash_potion: water 4 crystal 1 sulphur 2
""")

CREATURES = table("""
// the undead and the dead
zombie: undeath 6 death 4 beast 2 rot 2
husk: undeath 6 death 4 beast 2 earth 3
drowned: undeath 6 death 4 water 5 beast 2
zombie_villager: undeath 6 death 4 mind 3 beast 1
zombified_piglin: undeath 6 death 3 fire 3 war 2
skeleton: undeath 6 death 5 salt 4 war 2
stray: undeath 6 death 5 frost 5 war 2
bogged: undeath 6 death 5 venom 4 war 2
wither_skeleton: undeath 8 death 8 shadow 4 war 4
zoglin: undeath 6 death 4 beast 6 fire 2
phantom: shadow 6 undeath 4 air 6 beast 2
wither: death 16 undeath 12 shadow 8 arcana 8
ender_dragon: arcana 20 beast 16 shadow 12 mercury 12 war 8
warden: shadow 14 beast 12 war 8 spirit 6
// beasts of the dark and the deep
creeper: spark 6 sulphur 5 fire 4
spider: venom 5 beast 4 air 1
cave_spider: venom 6 beast 3
enderman: shadow 6 mercury 6 motion 4 mind 2
endermite: mercury 3 shadow 2 beast 1
silverfish: earth 3 beast 2 mercury 1
slime: water 6 mercury 4 life 3
magma_cube: fire 6 mercury 4 life 2 earth 2
blaze: fire 12 spark 6 sulphur 4
ghast: spirit 8 air 6 fire 4 death 3
breeze: air 10 motion 6 spark 3
shulker: earth 4 mercury 4 shadow 3 crystal 3
guardian: water 8 crystal 4 spirit 3 war 2
elder_guardian: water 12 crystal 8 spirit 6 war 4 arcana 3
vex: spirit 6 air 4 war 3
allay: spirit 8 motion 4 mind 3 light 2
// folk and the magical
villager: mind 8 life 5 wealth 3 craft 2
wandering_trader: mind 8 life 5 wealth 6 motion 2
witch: arcana 6 mind 5 venom 3 beast 1
pillager: war 6 beast 3 mind 2
vindicator: war 7 beast 3 metal 2
evoker: arcana 8 mind 5 war 3 spirit 2
illusioner: arcana 8 mind 6 shadow 3
ravager: war 8 beast 8 earth 3
player: mind 10 life 8 beast 4 spirit 2
piglin: wealth 4 beast 4 war 2 fire 1
piglin_brute: war 6 beast 5 wealth 3 fire 1
hoglin: beast 8 life 4 fire 2
strider: fire 8 life 3 beast 2
iron_golem: metal 14 earth 6 craft 4 war 3
snow_golem: frost 8 water 4 plant 2
copper_golem: metal 8 spark 4 craft 3
// farm and wild animals
cow: beast 5 life 4 water 2
mooshroom: beast 5 life 4 plant 3 rot 1
pig: beast 5 life 4 earth 1
sheep: beast 5 life 4 air 2
goat: beast 5 life 3 motion 2 earth 1
chicken: beast 3 life 3 air 3
horse: beast 6 life 4 motion 4
donkey: beast 6 life 4 earth 2
mule: beast 6 life 4 earth 2 motion 1
skeleton_horse: undeath 6 death 5 motion 4
zombie_horse: undeath 6 death 4 motion 4
camel: beast 6 life 4 earth 3 water 1
llama: beast 6 life 4 air 1
trader_llama: beast 6 life 4 wealth 2
wolf: beast 8 life 3 war 2 motion 2
cat: beast 6 life 3 motion 3 shadow 1
ocelot: beast 6 life 3 motion 4
fox: beast 6 life 3 motion 3
rabbit: beast 4 life 3 motion 4
bee: plant 3 motion 4 beast 2 life 1
bat: shadow 3 motion 4 beast 2 air 2
parrot: beast 4 air 5 motion 3 life 1
polar_bear: beast 9 life 4 frost 5 war 2
panda: beast 8 life 5 plant 3
turtle: beast 4 water 4 salt 3
frog: beast 3 water 4 life 3 motion 2
tadpole: water 3 life 3 beast 1
armadillo: beast 4 earth 3 salt 2
sniffer: beast 8 earth 5 plant 5 life 4
squid: water 8 life 4 shadow 1
glow_squid: water 8 life 4 light 4
dolphin: water 8 life 4 motion 4 mind 2
cod: water 5 life 3 beast 1
salmon: water 5 life 3 beast 1
pufferfish: water 5 venom 4 life 2
tropical_fish: water 5 life 3 light 1
axolotl: water 6 life 6 beast 2 spirit 1
nautilus: water 6 beast 3 salt 3
// tags catch the rest (an id above always wins)
#undead: undeath 5 death 3 rot 1
#skeletons: undeath 6 death 5 salt 4
#raiders: war 6 beast 3 mind 2
#arthropod: beast 4 earth 2 venom 1
#aquatic: water 6 life 3 beast 1
""")


def write(name, items, entities):
    os.makedirs(OUT, exist_ok=True)
    doc = {}
    if items:
        doc["items"] = items
    if entities:
        doc["entities"] = entities
    with open(OUT + name, "w") as f:
        json.dump(doc, f, indent=1)
        f.write("\n")


write("vanilla_blocks.json", BLOCKS, {})
write("vanilla_items.json", ITEMS, {})
write("vanilla_creatures.json", {}, CREATURES)
print(len(BLOCKS), "block/plant entries,", len(ITEMS), "item entries,", len(CREATURES), "creature entries")
