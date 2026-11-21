"""Read blocks in a box into numpy (ids into a global state list) plus block entities and entities."""
import sys, os, glob, json, pickle, numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from anvil import chunks, section_blocks, state_str
world = sys.argv[1]; out = sys.argv[2]
x0, y0, z0, x1, y1, z1 = map(int, sys.argv[3:9])
reg = os.path.join(world, 'dimensions/minecraft/overworld/region')
W, H, D = x1-x0, y1-y0, z1-z0
arr = np.zeros((H, D, W), dtype=np.int32)  # y, z, x
states = ['minecraft:air']; sid = {'minecraft:air': 0}
bes = {}
for path in glob.glob(os.path.join(reg, 'r.*.mca')):
    _, rx, rz, _ = os.path.basename(path).split('.'); rx, rz = int(rx), int(rz)
    if (rx+1)*512 <= x0 or rx*512 >= x1 or (rz+1)*512 <= z0 or rz*512 >= z1: continue
    for c in chunks(path):
        cx, cz = int(c['xPos']), int(c['zPos'])
        if (cx+1)*16 <= x0 or cx*16 >= x1 or (cz+1)*16 <= z0 or cz*16 >= z1: continue
        for s in c['sections']:
            sy = int(s['Y'])*16
            if sy+16 <= y0 or sy >= y1: continue
            pal, idx = section_blocks(s)
            if pal is None: continue
            ids = []
            for p in pal:
                k = state_str(p)
                if k not in sid: sid[k] = len(states); states.append(k)
                ids.append(sid[k])
            idx = np.array(idx, dtype=np.int32).reshape(16, 16, 16)  # y z x
            g = np.array(ids, dtype=np.int32)[idx]
            ya, yb = max(sy, y0), min(sy+16, y1)
            za, zb = max(cz*16, z0), min(cz*16+16, z1)
            xa, xb = max(cx*16, x0), min(cx*16+16, x1)
            arr[ya-y0:yb-y0, za-z0:zb-z0, xa-x0:xb-x0] = g[ya-sy:yb-sy, za-cz*16:zb-cz*16, xa-cx*16:xb-cx*16]
        for be in c.get('block_entities', []):
            x, y, z = int(be['x']), int(be['y']), int(be['z'])
            if x0 <= x < x1 and y0 <= y < y1 and z0 <= z < z1:
                bes[(x, y, z)] = be
ents = []
edir = os.path.join(world, 'dimensions/minecraft/overworld/entities')
import nbtlib
for path in glob.glob(os.path.join(edir, 'r.*.mca')):
    for c in chunks(path):
        for e in c.get('Entities', []):
            p = [float(v) for v in e['Pos']]
            if x0 <= p[0] < x1 and y0 <= p[1] < y1 and z0 <= p[2] < z1:
                ents.append(e)
np.save(out + '.npy', arr)
json.dump(states, open(out + '.states.json', 'w'))
nbtlib.File({'bes': nbtlib.List[nbtlib.Compound](list(bes.values())), 'ents': nbtlib.List[nbtlib.Compound](ents), 'origin': nbtlib.IntArray([x0, y0, z0])}).save(out + '.extra.nbt')
print('blocks', arr.shape, 'states', len(states), 'block entities', len(bes), 'entities', len(ents))
