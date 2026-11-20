"""Cut Fall Hall's manor out of the extracted area into a 26.3 structure template (gzipped NBT).

Kept: every block in the castle's footprint (its columns with built blocks from the main floor up, widened by MARGIN),
from the plinth (BOTTOM) to the spire. Left out (structure void, so the world's own blocks stay): everything outside
that footprint. Taken out: trees whose leaves and logs stand mostly outside the castle, loose mobs, container contents
and loot tables, spawners and other dungeon blocks. Kept: paintings and cushions."""
import sys, json, re, numpy as np, nbtlib
from nbtlib import Compound, List, Int, Double, String, File
from scipy import ndimage  # noqa
base, out = sys.argv[1], sys.argv[2]
X0, Y0, Z0 = 2130, -64, -300                      # the area's origin
XA, XB, ZA, ZB = 2246, 2291, -176, -133           # the manor's box (x, z)
BOTTOM, TOP, FLOOR = 100, 159, 107                # plinth, above the spire, the main floor
MARGIN = 2
arr = np.load(base + '.npy'); states = json.load(open(base + '.states.json')); built = np.load(base + '.built.npy')
sub = arr[BOTTOM-Y0:TOP-Y0, ZA-Z0:ZB-Z0, XA-X0:XB-X0].copy()
bsub = built[BOTTOM-Y0:TOP-Y0, ZA-Z0:ZB-Z0, XA-X0:XB-X0]
H, D, W = sub.shape
name = lambda i: states[i].split('[')[0]
foot = bsub[FLOOR-BOTTOM:].any(axis=0)
wide = ndimage.binary_dilation(foot, iterations=MARGIN)
# trees: leaves/logs components mostly outside the footprint
tree = np.array([('leaves' in s or re.search(r'(?<!stripped_)\w*_log\b', s) is not None) and 'stripped' not in s for s in states])
tmask = tree[sub]
lab, n = ndimage.label(tmask)
air = states.index('minecraft:air')
removed_trees = 0
for k in range(1, n + 1):
    m = lab == k
    zs, xs = np.where(m.any(axis=0))
    inside = foot[zs, xs].mean()
    if inside < 0.5:
        sub[m] = air; removed_trees += m.sum()
DROP = {'minecraft:spawner', 'minecraft:trial_spawner', 'minecraft:vault', 'minecraft:structure_block', 'minecraft:jigsaw',
        'minecraft:command_block', 'minecraft:barrier', 'minecraft:light', 'minecraft:sculk_shrieker', 'minecraft:sculk_sensor',
        'minecraft:calibrated_sculk_sensor', 'minecraft:sculk_catalyst', 'minecraft:suspicious_sand', 'minecraft:suspicious_gravel'}
extra = nbtlib.load(base + '.extra.nbt')
bes = {(int(b['x']), int(b['y']), int(b['z'])): b for b in extra['bes']}
STRIP = ('Items', 'LootTable', 'LootTableSeed', 'item', 'bees', 'x', 'y', 'z', 'keepPacked')
EMPTIED = {'minecraft:chest', 'minecraft:trapped_chest', 'minecraft:barrel', 'minecraft:furnace', 'minecraft:smoker',
           'minecraft:blast_furnace', 'minecraft:campfire', 'minecraft:soul_campfire', 'minecraft:hopper', 'minecraft:dispenser',
           'minecraft:dropper', 'minecraft:brewing_stand', 'minecraft:beehive', 'minecraft:bee_nest'}
KEEP_ITEMS = {'minecraft:chiseled_bookshelf', 'minecraft:shelf', 'minecraft:lectern'}
pal, pid, blocks = [], {}, []
def state_tag(s):
    m = re.match(r'([^\[]+)(?:\[(.*)\])?$', s)
    t = Compound({'id': String(m.group(1))})
    if m.group(2):
        t['properties'] = Compound({k: String(v) for k, v in (kv.split('=') for kv in m.group(2).split(','))})
    return t
dropped = {}
for y in range(H):
    for z in range(D):
        for x in range(W):
            if not wide[z, x]:
                continue
            i = int(sub[y, z, x]); s = states[i]
            if name(i) in DROP:
                dropped[name(i)] = dropped.get(name(i), 0) + 1; s = 'minecraft:air'
            if s not in pid:
                pid[s] = len(pal); pal.append(state_tag(s))
            b = Compound({'pos': List[Int]([Int(x), Int(y), Int(z)]), 'state': Int(pid[s])})
            be = bes.get((XA + x, BOTTOM + y, ZA + z))
            if be is not None and s != 'minecraft:air':
                tag = Compound(be)
                for k in STRIP:
                    if k in ('Items', 'item') and str(be['id']) in KEEP_ITEMS:
                        continue
                    tag.pop(k, None)
                # an emptied container or hive keeps no data at all: the game makes it fresh, and it goes down with the
                # walls (a block with data is placed after everything else, so a rail resting on a stove would drop)
                if str(be['id']) in EMPTIED and 'CustomName' not in tag:
                    tag = None
                if tag is not None:
                    b['nbt'] = tag
            blocks.append(b)
ents = []
LIVING_SKIP = {'minecraft:creeper', 'minecraft:skeleton', 'minecraft:zombie', 'minecraft:villager'}
KEEP_ENTS = {'minecraft:painting', 'minecraft:cushion', 'minecraft:item_frame', 'minecraft:glow_item_frame', 'minecraft:armor_stand',
             'minecraft:leash_knot', 'minecraft:block_display', 'minecraft:item_display', 'minecraft:text_display'}
skipped = {}
for e in extra['ents']:
    p = [float(v) for v in e['Pos']]
    if not (XA <= p[0] < XB and BOTTOM <= p[1] < TOP and ZA <= p[2] < ZB):
        continue
    eid = str(e['id'])
    if eid not in KEEP_ENTS or not wide[int(p[2]) - ZA, int(p[0]) - XA]:
        skipped[eid] = skipped.get(eid, 0) + 1; continue
    tag = Compound(e); tag.pop('UUID', None)
    rel = [p[0] - XA, p[1] - BOTTOM, p[2] - ZA]
    if 'block_pos' in e:
        # (a painting hangs from its block, not from where its middle is: as the game saves one)
        bp = [int(v) for v in e['block_pos']]
        bpos = [bp[0] - XA, bp[1] - BOTTOM, bp[2] - ZA]
        # and is put back by its block's middle: placing it snaps it to the block its position falls in, so a tall
        # one's own middle (half a block up) would hang it a block too high, off its wall
        rel = [v + 0.5 for v in bpos]
    else:
        bpos = [int(np.floor(v)) for v in rel]
    ents.append(Compound({'pos': List[Double]([Double(v) for v in rel]),
                          'blockPos': List[Int]([Int(v) for v in bpos]), 'nbt': tag}))
root = Compound({'DataVersion': Int(5023), 'size': List[Int]([Int(W), Int(H), Int(D)]),
                 'palette': List[Compound](pal), 'blocks': List[Compound](blocks), 'entities': List[Compound](ents)})
File(root, gzipped=True).save(out)
print('size', W, H, D, 'footprint', int(foot.sum()), 'wide', int(wide.sum()), 'blocks', len(blocks), 'palette', len(pal),
      'entities', len(ents), 'trees removed', int(removed_trees), 'dropped', dropped, 'skipped', skipped)
