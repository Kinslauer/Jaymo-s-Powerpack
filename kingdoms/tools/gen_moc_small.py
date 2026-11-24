"""The small Mo' Creatures models Kingdoms keeps (Reference Mods patch 7, second half): the ent, the cricket, the snail, the
fly, the maggot, the roach, the mouse, the mole, the rays and the jellyfish (kingdoms/src/main/java/com/jaymoskingdoms/monsters/Ent.java,
Critter.java, SeaRay.java, Jellyfish.java).

For each model it reads the original's boxes and animation (MoCModelX.java of DrZhark's Mo' Creatures 1.10.2, GPL-3, see
licenses/mocreatures) through tools/mocreatures/port_model.py and writes a 26.3 model class beside the other beasts' in
src/client/java/com/jaymoskingdoms/client/monsters/, and copies the original textures under our names into
textures/entity/moc/ plus the loot of the ent. The animation is the original's, line by line, with the original's choices of
which boxes to draw (a flying cricket's wings, a hiding snail's shell) made by each part's visibility.

Run from kingdoms/: python3 tools/gen_moc_small.py"""
import json, os, re, shutil, sys
from PIL import Image

sys.path.insert(0, 'tools/mocreatures')
import port_model as pm
sys.path.insert(0, 'tools')
import gen_moc_beasts as g

SRC = '/home/user/src/moc/src/main/java/drzhark/mocreatures/client/model/'
TEX = '/home/user/src/moc/src/main/resources/assets/mocreatures/textures/models/'
MOC = 'tools/mocreatures/'
OUT = 'src/client/java/com/jaymoskingdoms/client/monsters/'
ENTITY = 'src/main/resources/assets/jaymoskingdoms/textures/entity/moc/'
DATA = 'src/main/resources/data/jaymoskingdoms/'

# original texture -> ours
TEXTURES = {'ent_oak': 'ent_oak', 'ent_birch': 'ent_birch', 'cricketa': 'cricket_a', 'cricketb': 'cricket_b', 'snaila': 'snail_a', 'snailb': 'snail_b',
            'snailc': 'snail_c', 'snaild': 'snail_d', 'snaile': 'snail_e', 'snailf': 'snail_f', 'fly': 'fly', 'maggot': 'maggot', 'roach': 'roach',
            'micew': 'mouse_white', 'miceb': 'mouse_brown', 'miceg': 'mouse_grey', 'mole': 'mole', 'mantray': 'manta_ray', 'stingray': 'stingray',
            'BCliger': 'liger', 'BCtiger': 'tigon'}
for _c in 'abcdefghijkl':
    TEXTURES['jellyfish' + _c] = 'jellyfish_' + _c

# model -> (class, doc, field decls, setup lines, rig override or None, parts shown only when 'on', parts shown only when 'off')
# 'on' and 'off' name the state flag: parts in ON are drawn when it is set, parts in OFF when it is not.
MODELS = {
    'Ent': dict(cls='EntModel', doc='the ent, from {@code MoCModelEnt}: a walking tree, its head turning with its neck, arms and legs swinging in turn',
                fields='', setup=''),
    'Cricket': dict(cls='CricketModel', doc='the cricket, from {@code MoCModelCricket}: its long hind legs folded on the ground, stretched in the air with the wings beating',
                    fields='private boolean isFlying;', setup='this.isFlying = state.airborne;',
                    on='ThighLeftB, ThighRightB, LegLeftB, LegRightB, LeftWing, RightWing', off='ThighLeft, ThighRight, LegLeft, LegRight, FoldedWings'),
    'Snail': dict(cls='SnailModel', doc='the snail, from {@code MoCModelSnail}: its tail working along, its shell pulled down over it when it hides',
                  fields='private boolean hiding;', setup='this.hiding = state.hiding;',
                  on='ShellDown', off='Head, Antenna, Body, ShellUp, Tail', flag='hiding'),
    'Fly': dict(cls='FlyModel', doc='the fly, from {@code MoCModelFly}: wings beating in the air and folded on the ground, legs walking',
                fields='private boolean isFlying;', setup='this.isFlying = state.airborne;', on='LeftWing, RightWing', off='FoldedWings',
                rig='''		float wingRot = Mth.cos(f2 * 3.0F) * 0.7F;
		this.RightWing.zRot = wingRot;
		this.LeftWing.zRot = -wingRot;
		float legMov;
		float legMovB;
		if (this.isFlying) {
			legMov = f1 * 1.5F;
			legMovB = legMov;
		} else {
			legMov = Mth.cos(f * 1.5F + 3.141593F) * 2.0F * f1;
			legMovB = Mth.cos(f * 1.5F) * 2.0F * f1;
		}
		this.FrontLegs.xRot = 0.1487144F + legMov;
		this.MidLegs.xRot = 0.5948578F + legMovB;
		this.RearLegs.xRot = 1.070744F + legMov;'''),
    'Maggot': dict(cls='MaggotModel', doc='the maggot, from {@code MoCModelMaggot}: its body stretching as it crawls',
                   fields='', setup='', rig='''		float stretch = 1.0F - Mth.cos(f * 3.0F) * f1 * 2.0F;
		this.Head.zScale = stretch;
		this.Body.zScale = stretch;
		this.Tail.zScale = stretch;
		this.Tailtip.zScale = stretch;'''),
    'Roach': dict(cls='RoachModel', doc='the roach, from {@code MoCModelRoach}: its shell closed on the ground, open with wings beating in the air, feelers and legs working',
                  fields='private boolean isFlying;', setup='this.isFlying = state.airborne;',
                  on='LShellOpen, RShellOpen, LeftWing, RightWing', off='LShellClosed, RShellClosed'),
    'Mouse': dict(cls='MouseModel', doc='the mouse, from {@code MoCModelMouse}: head, ears and whiskers turning together, four legs scurrying, the tail swaying',
                  fields='', setup=''),
    'Mole': dict(cls='MoleModel', doc='the mole, from {@code MoCModelMole}: its digging hands paddling beside it',
                 fields='', setup=''),
    'Ray': dict(cls='RayModel', doc='the manta ray and the stingray, from {@code MoCModelRay}: wings rippling in a wave, the stingray\'s tail raised to sting',
                fields='private boolean attacking;', setup='this.attacking = state.stinging;',
                on='Right, Left, RWingC, LWingC, RWingD, RWingE, RWingF, RWingG, LWingD, LWingE, LWingF, LWingG', off='REye, LEye',
                flag='manta'),
    'JellyFish': dict(cls='JellyfishModel', doc='the jellyfish, from {@code MoCModelJellyFish}: its tentacles trailing as it moves', fields='', setup=''),
}

HEADER = '''package com.jaymoskingdoms.client.monsters;

import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * %s
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */
'''


def translate_rig(path, names):
    body = pm.animation(path)
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    body = body.replace('this.radianF', 'RAD').replace('radianF', 'RAD')
    body = re.sub(r'\bf5\b', '0.0625F', body)
    body = body.replace('isHidden', 'visible')
    body = re.sub(r'float RAD = 57.29578F;\s*', '', body)
    body = body.replace('if (isFlying)', 'if (this.isFlying)').replace('this.attacking', 'this.attacking')
    return body


def make(name, spec):
    path = SRC + 'MoCModel%s.java' % name
    tex, parts, order = pm.parse(path)
    lines, look = pm.emit(tex, parts, order)
    names = list(order)
    out = [HEADER % spec['doc']]
    out.append('final class %s extends EntityModel<BeastState> {' % spec['cls'])
    out.append('\tprivate static final float RAD = 57.29578F;')
    out.append('\tprivate final ModelPart %s;' % ', '.join(names))
    if spec['fields']:
        out.append('\t' + spec['fields'])
    out.append('')
    out.append('\t%s(ModelPart root) {\n\t\tsuper(root);\n%s\n\t}\n' % (spec['cls'], '\n'.join(look)))
    out.append('\tstatic LayerDefinition createBodyLayer() {\n%s\n\t}\n' % '\n'.join(lines))
    out.append('\t@Override\n\tpublic void setupAnim(BeastState state) {\n\t\tsuper.setupAnim(state);')
    if spec['setup']:
        out.append('\t\t' + spec['setup'])
    flag = spec.get('flag')
    if 'on' in spec:
        var = {'hiding': 'this.hiding', 'manta': 'state.manta'}.get(flag, 'this.isFlying')
        on, off = spec['on'], spec['off']
        out.append('\t\tfor (ModelPart p : List.of(%s)) {\n\t\t\tp.visible = %s;\n\t\t}' % (on, var))
        out.append('\t\tfor (ModelPart p : List.of(%s)) {\n\t\t\tp.visible = !%s;\n\t\t}' % (off, var))
    out.append('\t\trig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);\n\t}\n')
    out.append('\tprivate void rig(float f, float f1, float f2, float f3, float f4) {')
    out.append(spec['rig'] if 'rig' in spec else translate_rig(path, names))
    out.append('\t}\n}')
    text = '\n'.join(out) + '\n'
    with open(OUT + spec['cls'] + '.java', 'w', newline='\n') as f:
        f.write(text)
    return tex


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', newline='\n') as f:
        json.dump(obj, f, indent='\t')
        f.write('\n')


if __name__ == '__main__':
    for name, spec in MODELS.items():
        make(name, spec)
    os.makedirs(ENTITY, exist_ok=True)
    for src, dst in TEXTURES.items():
        local = MOC + src + '.png'
        if not os.path.exists(local):
            shutil.copyfile(TEX + src + '.png', local)
        shutil.copyfile(local, ENTITY + dst + '.png')

    def drop(item, lo, hi):
        return {'type': 'minecraft:item', 'name': item, 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}
    # the ent's own: what a tree is made of (the sapling it carried is planted as it walks, so the loot is wood)
    write(DATA + 'loot_table/entities/ent.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': 1, 'entries': [drop('minecraft:oak_log', 6, 12)]}, {'rolls': 1, 'entries': [drop('minecraft:stick', 4, 10)]},
        {'rolls': 1, 'entries': [drop('minecraft:oak_sapling', 1, 3)]}]})
    loot = {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [drop('minecraft:leather', 1, 3)]}]}
    write(DATA + 'loot_table/entities/liger.json', loot)
    write(DATA + 'loot_table/entities/stingray.json', {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [drop('minecraft:bone_meal', 1, 3)]}]})

    # the spawn eggs' icons and the bone charm (a carved bone on a cord; the bound one holds a green glint)
    os.makedirs(g.ITEM, exist_ok=True)
    for i, (name, shell, spot) in enumerate((('ent', (96, 70, 40), (60, 120, 50)), ('liger', (214, 168, 96), (120, 80, 40)), ('manta_ray', (40, 52, 70), (230, 230, 235)),
                                             ('stingray', (150, 130, 96), (70, 60, 40)), ('jellyfish', (200, 150, 220), (120, 90, 170)), ('cricket', (70, 54, 30), (30, 24, 14)),
                                             ('snail', (170, 140, 90), (100, 70, 40)), ('fly', (50, 50, 56), (150, 170, 180)), ('maggot', (230, 224, 196), (190, 180, 140)),
                                             ('roach', (90, 40, 20), (50, 20, 10)), ('mouse', (170, 160, 150), (110, 100, 96)), ('mole', (60, 54, 56), (210, 150, 150)))):
        g.egg_icon(name + '_spawn_egg', shell, spot, 90 + i)
        g.item_files(name + '_spawn_egg')

    def charm(bound):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        px = img.load()
        for i in range(9):
            x, y = 3 + i, 11 - i
            for w, c in ((0, (238, 230, 205)), (1, (190, 176, 144))):
                px[x + w, y] = c + (255,)
        for (x, y) in ((2, 11), (3, 12), (2, 12), (3, 10), (11, 3), (12, 2), (12, 3), (11, 2)):
            px[x, y] = (238, 230, 205, 255)
        for (x, y) in ((7, 3), (6, 2), (5, 2), (4, 3), (4, 4), (5, 5)):
            px[x, y] = (110, 72, 40, 255)
        if bound:
            for (x, y) in ((7, 8), (8, 7), (7, 7), (8, 8)):
                px[x, y] = (110, 230, 130, 255)
        img.save(g.ITEM + ('bone_charm_bound' if bound else 'bone_charm') + '.png')
    charm(False)
    charm(True)
    g.item_files('bone_charm')
    g.item_files('bone_charm_bound')
