#!/usr/bin/env python3
"""Skeleton models for Kitchen's bone stands (butchery/Skeletons): one GeckoLib model and its poses per animal.

Writes kitchen/.../geckolib/models/block/skeleton_<animal>.geo.json and geckolib/animations/block/skeleton_<animal>
.animation.json for cow, pig, sheep, horse, wolf, deer and chicken, and the shared bone texture
(textures/block/skeleton.png). Open any of them in Blockbench (GeckoLib plugin, Block/Item model) to hand-tidy.

Coordinates are pixels. The skeleton stands on the stand's 4-pixel plinth, an iron rod up into its belly, its head
toward -z (north, which GeckoLib turns to the stand's facing). Bedrock's rotation sign: +x tips a part's -z end down (and a tail's +z end up).

Run from the repo root: python3 tools/gen_skeleton_models.py [--preview DIR]
"""
import json
import os
import sys

from PIL import Image, ImageDraw

ROOT = 'kitchen/src/main/resources/assets/jaymoskitchen'
MODELS = ROOT + '/geckolib/models/block'
ANIMS = ROOT + '/geckolib/animations/block'
TEXTURE = ROOT + '/textures/block/skeleton.png'
FLOOR = 4  # the plinth's top


class Model:
    def __init__(self, name):
        self.name = name
        self.bones = []
        self.by_name = {}

    def bone(self, name, parent, pivot, rotation=None):
        b = {'name': name, 'pivot': [round(v, 3) for v in pivot], 'cubes': []}
        if parent:
            b['parent'] = parent
        if rotation:
            b['rotation'] = rotation
        self.bones.append(b)
        self.by_name[name] = b
        return name

    def cube(self, bone, x0, y0, z0, sx, sy, sz):
        """A cube from its low corner (x0, y0, z0), of size (sx, sy, sz); all faces take the bone texture."""
        uv = {}
        for face, (w, h) in {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}.items():
            uv[face] = {'uv': [0, 0], 'uv_size': [min(16, max(1, round(w))), min(16, max(1, round(h)))]}
        self.by_name[bone]['cubes'].append({'origin': [round(x0, 3), round(y0, 3), round(z0, 3)],
                                            'size': [round(sx, 3), round(sy, 3), round(sz, 3)], 'uv': uv})

    def sym(self, bone, x0, y0, z0, sx, sy, sz):
        """A cube and its mirror across x = 0."""
        self.cube(bone, x0, y0, z0, sx, sy, sz)
        self.cube(bone, -x0 - sx, y0, z0, sx, sy, sz)

    def geo(self):
        for b in self.bones:
            if not b['cubes']:
                del b['cubes']
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.skeleton_' + self.name, 'texture_width': 16, 'texture_height': 16,
                            'visible_bounds_width': 5, 'visible_bounds_height': 5, 'visible_bounds_offset': [0, 1.5, 0]},
            'bones': self.bones}]}


def quadruped(name, leg, length, width, depth, neck, neck_angle, skull, snout, tail, tail_angle, extras=None):
    """A four-legged skeleton: legs `leg` tall, a spine `length` long, a ribcage `width` wide and `depth` deep."""
    m = Model(name)
    spine_y = FLOOR + leg + depth
    front, back = -length / 2, length / 2
    m.bone('root', None, [0, 0, 0])
    m.bone('rod', 'root', [0, FLOOR, 0])
    m.cube('rod', -0.5, FLOOR, -0.5, 1, spine_y - depth - FLOOR + 1, 1)

    # the spine, hinged at the hips, so it rears
    m.bone('spine', 'root', [0, spine_y, back - 3])
    m.cube('spine', -0.5, spine_y - 1, front, 1, 1.5, length)
    for i, z in enumerate(range(int(front) + 1, int(back) - 1, 2)):
        tall = 2.5 if z < front + length * 0.35 else 1.2  # the tall spines over the shoulders
        m.cube('spine', -0.5, spine_y + 0.5, z, 1, tall, 1)
    # ribs: true ribs deep at the front, shallowing toward the loins; the sternum under the front ones
    rib_end = back - length * 0.3
    z = front + 2
    while z < rib_end:
        t = (z - front) / (rib_end - front)
        d = depth * (1.0 - 0.45 * t)
        half = width / 2 * (0.85 + 0.15 * (1 - abs(t - 0.4)))
        m.cube('spine', -half, spine_y - 1.5, z, half * 2, 1, 1)
        m.sym('spine', half - 1, spine_y - d, z, 1, d - 0.5, 1)
        if t < 0.55:
            m.cube('spine', -half + 1, spine_y - d, z, half * 2 - 2, 1, 1)
        z += 2
    m.cube('spine', -0.75, spine_y - depth, front + 1, 1.5, 1, length * 0.4)
    # the pelvis
    m.sym('spine', width / 2 - 2, spine_y - 4, back - 6, 1, 4, 5)
    m.cube('spine', -width / 2 + 2, spine_y - 1.5, back - 6, width - 4, 1, 5)

    # the neck, out of the shoulders, and the skull at its end
    m.bone('neck', 'spine', [0, spine_y, front], [neck_angle, 0, 0])
    for k in range(int(neck)):
        m.cube('neck', -0.75, spine_y - 1, front - k - 1, 1.5, 1.5, 0.8)
        m.cube('neck', -0.5, spine_y + 0.5, front - k - 1, 1, 0.8, 0.6)
    head_z = front - neck
    sw, sh, sl = skull
    nw, nh, nl = snout
    m.bone('head', 'neck', [0, spine_y, head_z], [-neck_angle * 0.8, 0, 0])
    # cranium: a shell of plates round the eye sockets
    m.cube('head', -sw / 2, spine_y - sh / 2 + 1, head_z - sl, sw, 1, sl)              # brow and crown
    m.sym('head', sw / 2 - 1, spine_y - sh / 2, head_z - sl * 0.6, 1, sh - 1, sl * 0.6)  # cheeks behind the eyes
    m.cube('head', -sw / 2 + 1, spine_y - sh / 2, head_z - 1, sw - 2, sh - 1, 1)      # the back of the skull
    # the face: nasal bones on top, the upper jaw, the teeth
    top = spine_y - sh / 2 + 1
    m.cube('head', -nw / 2, top - 1, head_z - sl - nl, nw, 1, nl)
    m.sym('head', nw / 2 - 1, top - nh, head_z - sl - nl, 1, nh - 1, nl)
    m.cube('head', -nw / 2, top - nh, head_z - sl - nl, nw, 0.6, 1)
    # the lower jaw, its own bone so it can open
    m.bone('jaw', 'head', [0, top - nh, head_z - 1])
    m.sym('jaw', nw / 2 - 1, top - nh - 1, head_z - sl - nl + 0.5, 1, 1, sl + nl - 1.5)
    m.cube('jaw', -nw / 2, top - nh - 1, head_z - sl - nl + 0.5, nw, 1, 1.2)

    # legs: shoulder blade, upper leg, lower leg, hoof or paw; the hind legs bend back at the hock
    hoof = extras.get('hoof', 'hoof') if extras else 'hoof'
    for side, sx in (('left', 1), ('right', -1)):
        x = sx * (width / 2 - 1.5)
        fz = front + 2.5
        m.bone('front_' + side, 'spine', [x, spine_y - 2, fz])
        m.cube('front_' + side, x - 0.5, spine_y - 6, fz - 1.5, 1, 5, 3)            # scapula
        upper = leg * 0.45
        m.cube('front_' + side, x - 0.75, FLOOR + leg - upper + depth - 4, fz - 0.75, 1.5, upper, 1.5)
        m.cube('front_' + side, x - 1, FLOOR + leg - upper + depth - 5, fz - 1, 2, 1.5, 2)  # elbow
        low = leg - upper + depth - 5 - 1
        m.cube('front_' + side, x - 0.5, FLOOR + 1, fz - 0.5, 1, low, 1)
        foot(m, 'front_' + side, x, fz, hoof)

        hz = back - 3
        m.bone('hind_' + side, 'root', [x, spine_y - 3, hz])
        thigh = leg * 0.45
        m.cube('hind_' + side, x - 0.75, spine_y - 3 - thigh, hz - 0.75, 1.5, thigh, 1.5)
        knee = spine_y - 3 - thigh
        m.cube('hind_' + side, x - 1, knee - 1, hz - 1.5, 2, 1.5, 2)
        hock = FLOOR + leg * 0.35
        m.cube('hind_' + side, x - 0.5, hock, hz + 0.5, 1, knee - hock - 1, 1)
        m.cube('hind_' + side, x - 0.75, hock - 1, hz + 0.25, 1.5, 1.5, 1.5)
        m.cube('hind_' + side, x - 0.5, FLOOR + 1, hz + 0.5, 1, hock - FLOOR - 1, 1)
        foot(m, 'hind_' + side, x, hz + 1, hoof)

    # the tail: a run of small vertebrae off the pelvis
    m.bone('tail', 'spine', [0, spine_y - 1, back], [tail_angle, 0, 0])
    for k in range(int(tail)):
        s = max(0.5, 1.0 - k * 0.05)
        m.cube('tail', -s / 2, spine_y - 1, back + k, s, s, 0.8)

    if extras:
        for kind in extras.get('head', []):
            kind(m, spine_y, head_z, skull)
    return m


def foot(m, bone, x, z, kind):
    if kind == 'paw':
        m.cube(bone, x - 1, FLOOR, z - 2, 2, 1, 2.5)
        m.cube(bone, x - 1, FLOOR, z - 2.5, 0.5, 0.5, 0.5)
        m.cube(bone, x + 0.5, FLOOR, z - 2.5, 0.5, 0.5, 0.5)
    elif kind == 'trotter':
        m.cube(bone, x - 1, FLOOR, z - 1.5, 0.8, 1, 2)
        m.cube(bone, x + 0.2, FLOOR, z - 1.5, 0.8, 1, 2)
    else:
        m.cube(bone, x - 1, FLOOR, z - 1.5, 2, 1, 2.5)


def horns(m, spine_y, head_z, skull):
    sw, sh, sl = skull
    y = spine_y + sh / 2 - 1
    m.sym('head', sw / 2, y, head_z - 2, 2, 1, 1)
    m.sym('head', sw / 2 + 1.5, y + 1, head_z - 2, 1, 2, 1)


def antlers(m, spine_y, head_z, skull):
    sw, sh, sl = skull
    y = spine_y + sh / 2 - 1
    for sx in (1, -1):
        bx = sx * (sw / 2 - 1)
        def c(x0, y0, z0, a, b, d):
            m.cube('head', x0 if sx > 0 else -x0 - a, y0, z0, a, b, d)
        c(bx - 0.5 if sx > 0 else -bx - 0.5, y, head_z - 2, 1, 4, 1)
        c(abs(bx), y + 3, head_z - 2, 3, 1, 1)
        c(abs(bx) + 2, y + 3, head_z - 2, 1, 4, 1)
        c(abs(bx) + 1, y + 5, head_z - 4, 1, 1, 2)
        c(abs(bx) + 2, y + 6, head_z - 2, 2, 1, 1)
        c(abs(bx) + 3, y + 6, head_z - 2, 1, 3, 1)


def pig_disc(m, spine_y, head_z, skull):
    sw, sh, sl = skull
    m.cube('head', -1.5, spine_y - sh / 2, head_z - sl - 4.5, 3, 2, 0.5)


def tusks(m, spine_y, head_z, skull):
    m.sym('jaw', 1.2, spine_y - 4, head_z - 8, 0.5, 2, 0.5)


def chicken():
    m = Model('chicken')
    leg = 5
    hip_y = FLOOR + leg
    m.bone('root', None, [0, 0, 0])
    m.bone('rod', 'root', [0, FLOOR, 0])
    m.cube('rod', -0.5, FLOOR, -0.5, 1, hip_y - FLOOR - 1, 1)
    m.bone('spine', 'root', [0, hip_y + 2, 1])
    m.cube('spine', -0.5, hip_y + 3, -3, 1, 1, 6)                       # the back
    for z in (-3, -1, 1):
        m.cube('spine', -2, hip_y + 3, z, 4, 0.7, 0.7)
        m.sym('spine', 1.5, hip_y, z, 0.6, 3, 0.7)
    m.cube('spine', -0.4, hip_y - 0.5, -3.5, 0.8, 3, 5)                 # the keel
    m.sym('spine', 1, hip_y + 1, 1, 0.8, 2, 3)                          # the pelvis
    m.cube('spine', -1, hip_y + 3.5, 3, 2, 0.6, 2)                      # the tail's ploughshare
    # neck up out of the shoulders, the skull and beak
    m.bone('neck', 'spine', [0, hip_y + 3.5, -3])
    for k in range(4):
        m.cube('neck', -0.5, hip_y + 3.5 + k, -3.5 - k * 0.25, 1, 0.9, 1)
    hy = hip_y + 7.5
    m.bone('head', 'neck', [0, hy, -4])
    m.cube('head', -1.5, hy, -6, 3, 2.5, 3)
    m.cube('head', -0.5, hy + 0.5, -8, 1, 1, 2)                         # the beak
    m.bone('jaw', 'head', [0, hy, -6])
    m.cube('jaw', -0.4, hy - 0.3, -7.5, 0.8, 0.5, 1.6)
    # wings folded along the sides
    for side, sx in (('left', 1), ('right', -1)):
        m.bone('wing_' + side, 'spine', [sx * 2, hip_y + 3, -2])
        x = sx * 2 if sx > 0 else -2.6
        m.cube('wing_' + side, x, hip_y + 2.6, -2.5, 0.6, 0.6, 4)
        m.cube('wing_' + side, x, hip_y + 1.6, 1, 0.6, 1, 0.6)
        m.cube('wing_' + side, x, hip_y + 1.6, 1, 0.6, 0.6, 3)
        m.bone('leg_' + side, 'root', [sx * 1.2, hip_y + 1, 0.5])
        lx = sx * 1.2 - 0.4
        m.cube('leg_' + side, lx, hip_y - 1.5, 0, 0.8, 2.5, 0.8)
        m.cube('leg_' + side, lx, FLOOR + 0.5, 0.3, 0.6, hip_y - 1.5 - FLOOR - 0.5, 0.6)
        m.cube('leg_' + side, lx - 0.6, FLOOR, -1.5, 0.4, 0.4, 2)
        m.cube('leg_' + side, lx + 0.2, FLOOR, -1.8, 0.4, 0.4, 2.3)
        m.cube('leg_' + side, lx + 1.0, FLOOR, -1.5, 0.4, 0.4, 2)
        m.cube('leg_' + side, lx + 0.2, FLOOR, 0.8, 0.4, 0.4, 1)
    return m


# animal: (model, its poses: name -> {bone: rotation or (rotation, position)})
def quad_poses(rear=False, graze=0, low=False, root_head=False, howl=False, sit=False, alert=False):
    poses = {'standing': {}}
    if graze:
        poses['grazing'] = {'neck': [graze, 0, 0], 'head': [graze * 0.4, 0, 0]}
    if low:
        poses['lowing'] = {'neck': [-12, 0, 0], 'head': [-25, 0, 0], 'jaw': [22, 0, 0]}
    if root_head:
        poses['rooting'] = {'neck': [30, 0, 0], 'head': [25, 0, 0], 'front_left': [-8, 0, 0], 'front_right': [-8, 0, 0]}
    if rear:
        poses['rearing'] = {'spine': [-38, 0, 0], 'front_left': [62, 0, 0], 'front_right': [45, 0, 0],
                            'hind_left': [-12, 0, 0], 'hind_right': [-12, 0, 0], 'neck': [10, 0, 0], 'tail': [15, 0, 0]}
    if howl:
        poses['howling'] = {'neck': [-35, 0, 0], 'head': [-30, 0, 0], 'jaw': [18, 0, 0]}
    if sit:
        poses['sitting'] = {'spine': ([-32, 0, 0], [0, -3, 0]), 'front_left': [32, 0, 0], 'front_right': [32, 0, 0],
                            'hind_left': ([-70, 0, 0], [0, -3, 1]), 'hind_right': ([-70, 0, 0], [0, -3, 1]), 'neck': [15, 0, 0],
                            'tail': [-25, 0, 0]}
    if alert:
        poses['alert'] = {'neck': [-20, 0, 0], 'head': [12, 0, 0], 'tail': [60, 0, 0]}
    return poses


ANIMALS = {
    'cow': (lambda: quadruped('cow', 12, 18, 11, 9, 6, -18, (6, 6, 5), (4, 4, 5), 10, -70, {'head': [horns]}),
            quad_poses(graze=42, low=True)),
    'pig': (lambda: quadruped('pig', 6, 16, 9, 8, 3, 0, (6, 6, 5), (4, 4, 4), 3, -30, {'hoof': 'trotter', 'head': [pig_disc, tusks]}),
            quad_poses(root_head=True)),
    'sheep': (lambda: quadruped('sheep', 11, 14, 8, 7, 4, -25, (5, 5, 4), (3, 3, 4), 4, -60),
              quad_poses(graze=40)),
    'horse': (lambda: quadruped('horse', 15, 22, 10, 10, 11, -50, (5, 6, 5), (4, 4, 8), 12, -55),
              quad_poses(graze=62, rear=True)),
    'wolf': (lambda: quadruped('wolf', 8, 12, 6, 6, 3, -20, (5, 5, 4), (3, 3, 4), 8, -30, {'hoof': 'paw'}),
             quad_poses(howl=True, sit=True)),
    'deer': (lambda: quadruped('deer', 16, 16, 8, 8, 9, -55, (4, 5, 4), (3, 3, 5), 3, -40, {'head': [antlers]}),
             quad_poses(graze=65, alert=True)),
}
CHICKEN_POSES = {
    'standing': {},
    'pecking': {'spine': [12, 0, 0], 'neck': [55, 0, 0], 'head': [20, 0, 0]},
    'flapping': {'wing_left': [0, 0, -70], 'wing_right': [0, 0, 70], 'neck': [-10, 0, 0], 'jaw': [15, 0, 0]},
}


def animations(name, poses):
    out = {}
    for pose, bones in poses.items():
        entry = {}
        for bone, value in bones.items():
            if isinstance(value, tuple):
                entry[bone] = {'rotation': value[0], 'position': value[1]}
            else:
                entry[bone] = {'rotation': value}
        if not entry:
            entry = {'root': {'rotation': [0, 0, 0]}}
        out[pose] = {'loop': True, 'animation_length': 1.0, 'bones': entry}
    return {'format_version': '1.8.0', 'animations': out}


def bone_texture():
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            # bone: a warm off-white with a faint porous grain and darker edges
            grain = ((x * 7 + y * 13) % 9) - 4
            edge = 12 if x in (0, 15) or y in (0, 15) else 0
            v = 232 + grain - edge
            img.putpixel((x, y), (v, v - 6, v - 22, 255))
    img.save(TEXTURE)


def preview(model, path):
    """A flat side view of the rest pose (no bone rotations), to check proportions."""
    s = 8
    img = Image.new('RGB', (64 * s, 48 * s), (40, 44, 52))
    d = ImageDraw.Draw(img)
    for b in model.bones:
        for c in b.get('cubes', []):
            x0, y0, z0 = c['origin']
            sx, sy, sz = c['size']
            u0, u1 = (z0 + 32) * s, (z0 + sz + 32) * s
            v0, v1 = (48 - y0 - sy) * s, (48 - y0) * s
            d.rectangle([u0, v0, u1, v1], outline=(236, 228, 206))
    d.line([0, (48 - FLOOR) * s, 64 * s, (48 - FLOOR) * s], fill=(120, 90, 60))
    img.save(path)


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(ANIMS, exist_ok=True)
    prev = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    built = {name: (make(), poses) for name, (make, poses) in ANIMALS.items()}
    built['chicken'] = (chicken(), CHICKEN_POSES)
    for name, (model, poses) in built.items():
        if prev:
            preview(model, os.path.join(prev, 'skeleton_' + name + '.png'))
        with open(f'{MODELS}/skeleton_{name}.geo.json', 'w') as f:
            json.dump(model.geo(), f, indent=1)
        with open(f'{ANIMS}/skeleton_{name}.animation.json', 'w') as f:
            json.dump(animations(name, poses), f, indent=1)
    bone_texture()


if __name__ == '__main__':
    main()
