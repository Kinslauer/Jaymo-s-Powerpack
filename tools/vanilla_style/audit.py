"""Style audit: measure every modded item texture against vanilla's own distribution."""
import sys, os, json, colorsys, glob
from PIL import Image
import numpy as np

def frame(p):
    im = Image.open(p).convert('RGBA')
    if im.height > im.width: im = im.crop((0, 0, im.width, im.width))
    return im

def metrics(p):
    im = frame(p); a = np.asarray(im).astype(float); W = im.width
    al = a[..., 3]; op = al > 0
    n = op.sum()
    if n < 4: return None
    rgb = a[..., :3]
    lum = (0.299*rgb[..., 0] + 0.587*rgb[..., 1] + 0.114*rgb[..., 2]) / 255
    pad = np.pad(op, 1)
    edge = op & ~(pad[:-2,1:-1] & pad[2:,1:-1] & pad[1:-1,:-2] & pad[1:-1,2:])
    mx = rgb.max(-1)
    black_edge = (edge & (mx < 40)).sum() / max(edge.sum(), 1)
    semi = ((al > 0) & (al < 255)).sum() / n
    cols = len({tuple(c) for c in a[op].astype(int)})
    hsv = np.array([colorsys.rgb_to_hsv(*(c/255)) for c in rgb[op]])
    sat = hsv[:, 1].mean()
    # local noise: mean |dLum| between horizontally/vertically adjacent interior pixels
    inner = op & ~edge
    dh = np.abs(lum[:, 1:] - lum[:, :-1])[inner[:, 1:] & inner[:, :-1]]
    dv = np.abs(lum[1:] - lum[:-1])[inner[1:] & inner[:-1]]
    d = np.concatenate([dh, dv]); noise = d.mean() if len(d) else 0
    yy, xx = np.mgrid[0:W, 0:W]
    diag = (xx + yy)[op]; L = lum[op]
    light = np.corrcoef(diag, L)[0, 1] if L.std() > 0 and diag.std() > 0 else 0  # negative = lit from top-left
    return dict(res=W, black_edge=black_edge, semi=semi, colors=cols / (W/16)**2,
                sat=sat, noise=noise, light=-light, cover=n / W**2)

KEYS = ['black_edge', 'semi', 'colors', 'sat', 'noise', 'light', 'cover']

def collect(root):
    out = {}
    for p in glob.glob(os.path.join(root, '**/textures/item*/**/*.png'), recursive=True) + \
             glob.glob(os.path.join(root, 'textures/item/*.png')):
        try:
            m = metrics(p)
        except Exception as e:
            continue
        if m: out[p] = m
    return out

if __name__ == '__main__':
    vanilla = collect(sys.argv[1]); targets = {}
    for r in sys.argv[3:]: targets.update(collect(r))
    V = {k: np.array([m[k] for m in vanilla.values()]) for k in KEYS}
    lo = {k: np.percentile(V[k], 5) for k in KEYS}; hi = {k: np.percentile(V[k], 95) for k in KEYS}
    rows = []
    for p, m in targets.items():
        flags = []
        if m['res'] != 16: flags.append(f"{m['res']}px")
        for k in KEYS:
            if m[k] > hi[k]: flags.append(k + '+')
            elif m[k] < lo[k]: flags.append(k + '-')
        rows.append(dict(path=p, flags=flags, **{k: round(float(m[k]), 3) for k in KEYS}, res=m['res']))
    json.dump(dict(vanilla_n=len(vanilla), lo=lo, hi=hi, rows=rows), open(sys.argv[2], 'w'), indent=1, default=float)
    print('vanilla', len(vanilla), 'targets', len(rows))
    for k in KEYS: print(f"{k:11s} vanilla 5-95%: {lo[k]:.3f} .. {hi[k]:.3f}  median {np.median(V[k]):.3f}")
