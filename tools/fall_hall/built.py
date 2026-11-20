import sys, json, re, numpy as np
from PIL import Image
base = sys.argv[1]
arr = np.load(base + '.npy'); states = json.load(open(base + '.states.json'))
NAT = re.compile(r'^minecraft:(air|cave_air|grass_block|dirt|coarse_dirt|rooted_dirt|podzol|mud|clay|stone|deepslate|granite|diorite|andesite|tuff|calcite|gravel|sand|sandstone|red_sand|water|lava|bedrock|.*_ore|raw_.*|.*leaves|.*_log|short_grass|tall_grass|fern|large_fern|.*grass|bush|leaf_litter|.*shrub|dandelion|poppy|.*tulip|.*orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|.*_mushroom|mushroom_stem|kelp|kelp_plant|seagrass|tall_seagrass|glow_lichen|moss_block|moss_carpet|vine|sugar_cane|lily_pad|pointed_dripstone|dripstone_block|amethyst.*|budding_amethyst|.*_amethyst_bud|smooth_basalt|copper_ore|sweet_berry_bush|cave_vines.*|hanging_roots|spore_blossom|big_dripleaf.*|small_dripleaf|azalea|flowering_azalea|pumpkin|wildflowers|firefly_bush|dry_.*grass|cactus.*|snow|ice|powder_snow|packed_ice|.*_sapling|sculk.*|pink_petals|bubble_column|infested_.*|cobweb|spawner|fire|magma_block|obsidian|soul_.*sand|soul_soil)$')
natural = np.array([bool(NAT.match(s.split('[')[0])) for s in states])
built = ~natural[arr]
col = built.sum(axis=0)  # z, x
print('built columns', (col > 0).sum())
img = np.clip(col * 6, 0, 255).astype(np.uint8)
Image.fromarray(img).resize((img.shape[1]*3, img.shape[0]*3), Image.NEAREST).save(base + '_built.png')
np.save(base + '.built.npy', built)
from collections import Counter
ids = arr[built]
print(Counter(states[i].split('[')[0] for i in ids).most_common(60))
