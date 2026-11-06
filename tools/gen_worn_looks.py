"""Worn bauble looks: our own boxes and our own pixels (Andy's REA rule, docs/Worn-Looks-Spec.md).

Writes src/main/resources/assets/jaymospowers/worn_looks.json (the boxes, read by client/baubles/WornLooks) and one
texture per look in textures/entity/worn/. Nothing here is taken from another mod: shapes follow our own item icons,
colours are picked by eye from those icons, and every texel is painted below from flat colours, shading and noise.

Coordinates are the vanilla player model's: pixels, y down, the front of the body is -z. A part hangs on a bone (head,
body, right_arm, right_leg; the client mirrors right parts onto the left) at `pivot` (relative to the bone's pivot),
and its boxes are relative to the part's pivot. Run: python3 tools/gen_worn_looks.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(ROOT, 'src/main/resources/assets/jaymospowers')
OUT_TEX = os.path.join(RES, 'textures/entity/worn')

# ---- building blocks ---------------------------------------------------------------------------------------------


def box(frm, size, role, pattern='plain', inflate=0.0, fit=False, open_front=False):
    """A cube. `fit`: spans the whole width of an arm (narrowed by a pixel on slim arms). `open_front`: front face left
    clear but for a one-texel rim (a hood's face opening)."""
    return dict(frm=list(frm), size=list(size), role=role, pattern=pattern, inflate=inflate, fit=fit, open_front=open_front)


def part(bone, pivot, boxes, rot=(0, 0, 0), swing=None, sides='right', armour=None, children=()):
    """`swing`: (axis, amplitude, phase, kind); kind 'stride' rocks with each step, 'cape' also lifts as you speed up,
    'hang' rocks less and mostly at a walk. `sides`: 'right' (as given), 'left' (mirrored), 'both', or 'slot' (a ring:
    right hand for the first ring slot, left for the second). `armour`: the armour slot that, when worn, pushes this
    part out over it (head, chest, legs, feet), by scaling it about its pivot: so a part with `armour` sits on the bone's
    middle (the arm's middle for arms) and carries anything off-centre as children."""
    return dict(bone=bone, pivot=list(pivot), rot=list(rot), boxes=list(boxes),
                swing=None if swing is None else dict(axis=swing[0], amp=swing[1], phase=swing[2], kind=swing[3]),
                sides=sides, armour=armour, children=list(children))


LOOKS = {}


def look(item, palette, *parts, hide_cape=False):
    LOOKS[item] = dict(palette=palette, parts=list(parts), hide_cape=hide_cape)


# ---- head ----------------------------------------------------------------------------------------------------------
HEAD = dict(bone='head', armour='head')


def hat_part(boxes, children=(), rot=(0, 0, 0)):
    return part('head', (0, 0, 0), boxes, rot=rot, armour='head', children=children)


look('alewife_cap', dict(main='6b4a2e', band='5e8a3a', dark='3f2b1a'),
     hat_part([box((-5.5, -8.6, -5.5), (11, 1, 11), 'main', 'leather'),
               box((-4, -10.5, -4), (8, 2, 8), 'band', 'knit'),
               box((-3, -12.5, -3), (6, 2, 6), 'main', 'leather')],
              children=[part('head', (0, -12.5, 0), [box((-2, -2, -2), (4, 2, 4), 'main', 'leather'),
                                                      box((-1, -4, -1), (2, 2, 2), 'dark', 'leather')],
                             rot=(-0.25, 0, 0.1), swing=('z', 0.08, 0.0, 'hang'))]))

look('jester_cap', dict(red='b8332e', green='3f8a3c', gold='e0b23a'),
     hat_part([box((-4.5, -8.6, -4.5), (9, 2, 9), 'gold', 'studs'),
               box((-4, -10.5, -4), (4, 2, 8), 'red', 'knit'),
               box((0, -10.5, -4), (4, 2, 8), 'green', 'knit')],
              children=[part('head', (-2.5, -10, 0), [box((-1.5, -6, -1.5), (3, 6, 3), 'red', 'knit'),
                                                       box((-1, -7.5, -1), (2, 2, 2), 'gold', 'metal')],
                             rot=(0, 0, -1.9), swing=('z', 0.18, 0.0, 'stride')),
                        part('head', (2.5, -10, 0), [box((-1.5, -6, -1.5), (3, 6, 3), 'green', 'knit'),
                                                      box((-1, -7.5, -1), (2, 2, 2), 'gold', 'metal')],
                             rot=(0, 0, 1.9), swing=('z', 0.18, math.pi, 'stride'))]))

look('reed_breather', dict(reed='9a7a44', band='5a3b22', glass='9fc8cf'),
     hat_part([box((-4.5, -6.5, -4.5), (9, 1, 9), 'band', 'leather'),
               box((-3.5, -6.8, -4.9), (7, 2, 1), 'glass', 'glass'),
               box((-1, -3, -5.4), (6, 1, 1), 'reed', 'reed'),
               box((4.4, -12, -5.4), (1, 10, 1), 'reed', 'reed')]))

look('owl_mask', dict(feather='7a5a3c', light='c9a878', gold='e3b23c', beak='3a2a1c'),
     hat_part([box((-4.5, -8, -4.9), (9, 7, 1), 'feather', 'feather'),
               box((-3.8, -6.5, -5.3), (3, 3, 1), 'gold', 'eye'),
               box((0.8, -6.5, -5.3), (3, 3, 1), 'gold', 'eye'),
               box((-0.5, -4, -5.6), (1, 2, 1), 'beak', 'plain'),
               box((-4.5, -9.5, -4.9), (2, 2, 1), 'light', 'feather'),
               box((2.5, -9.5, -4.9), (2, 2, 1), 'light', 'feather')]))

look('villager_hat', dict(straw='d9b24c', band='8a5a2a'),
     hat_part([box((-6.5, -8.6, -6.5), (13, 1, 13), 'straw', 'straw'),
               box((-4, -11.5, -4), (8, 3, 8), 'straw', 'straw'),
               box((-4, -9.6, -4), (8, 1, 8), 'band', 'leather', inflate=0.1)]))

look('soothsayer_hood', dict(cloth='5b2f82', trim='e0b23a', dark='2e1844'),
     hat_part([box((-4, -8, -4), (8, 8, 8), 'cloth', 'cloth', inflate=0.6, open_front=True),
               box((-1, -9.6, 0), (2, 1, 4), 'cloth', 'cloth')]),
     part('body', (0, 0, 0), [], armour='chest',
          children=[part('body', (0, 0, 2.4), [box((-4, 0, 0), (8, 5, 1), 'cloth', 'cloth', inflate=0.3)],
                         rot=(0.08, 0, 0), swing=('x', 0.12, 0.0, 'cape'))]))

look('riders_hat', dict(felt='7a4e2c', band='3a2616', feather='c84a2e'),
     hat_part([box((-7, -8.6, -7), (14, 1, 14), 'felt', 'leather'),
               box((-4, -12, -4), (8, 3.5, 8), 'felt', 'leather'),
               box((-4, -9.6, -4), (8, 1, 8), 'band', 'leather', inflate=0.1)],
              children=[part('head', (3.8, -10, 1), [box((-0.5, -5, -0.5), (1, 5, 1), 'feather', 'feather')],
                             rot=(0.3, 0, 0.35), swing=('x', 0.1, 0.0, 'hang'))]))

look('fishers_hat', dict(felt='9a9488', band='5a5650', feather='c23a2a'),
     hat_part([box((-5.5, -8.6, -5.5), (11, 1, 11), 'felt', 'cloth'),
               box((-4, -11, -4), (8, 2.5, 8), 'felt', 'cloth'),
               box((-4, -9.6, -4), (8, 1, 8), 'band', 'cloth', inflate=0.1)],
              children=[part('head', (-3.8, -10, 0), [box((-0.5, -4, -0.5), (1, 4, 1), 'feather', 'feather')],
                             rot=(0.2, 0, -0.4), swing=('x', 0.1, 1.0, 'hang'))]))

look('sky_mask', dict(cloth='6a6a70', glass='8fd0dc', brass='b08a3c', mesh='3c3c42'),
     hat_part([box((-4.5, -6.5, -4.5), (9, 1, 9), 'cloth', 'cloth'),
               box((-3.6, -6.9, -4.9), (3, 2, 1), 'glass', 'glass'),
               box((0.6, -6.9, -4.9), (3, 2, 1), 'glass', 'glass'),
               box((-2, -3.5, -5.6), (4, 3, 2), 'mesh', 'mesh'),
               box((-2.5, -4, -5.0), (5, 4, 1), 'brass', 'metal')]))

look('divers_circlet', dict(gold='e3b33c', gem='4fd4c4'),
     hat_part([box((-4, -7.2, -4), (8, 1, 8), 'gold', 'metal', inflate=0.45),
               box((-0.5, -7.8, -4.9), (1, 1, 1), 'gem', 'gem')]))

# ---- neck: scarves and pendants ------------------------------------------------------------------------------------


def scarf(item, main, stripe):
    look(item, dict(main=main, stripe=stripe),
         part('body', (0, 0, 0), [box((-4.5, -0.6, -2.6), (9, 2, 5), 'main', 'knit', inflate=0.2)], armour='chest',
              children=[part('body', (2.0, 1.2, -2.6), [box((-1, 0, -1), (2, 7, 1), 'main', 'stripes')],
                             rot=(-0.1, 0, -0.08), swing=('x', 0.22, 0.0, 'stride')),
                        part('body', (-2.5, 1.2, 2.6), [box((-1, 0, 0), (2, 5, 1), 'main', 'stripes')],
                             rot=(0.15, 0, 0.1), swing=('x', 0.2, math.pi, 'cape'))]))


scarf('lucky_scarf', '4aa33e', '2f7a2a')
scarf('scarf_of_invisibility', '4a7fd0', '2f58a3')


def cords(role):
    return [part('body', (-2.8, 0, -2.25), [box((-0.5, 0, -0.25), (1, 5.6, 0.5), role, 'plain')], rot=(0, 0, -0.5)),
            part('body', (2.8, 0, -2.25), [box((-0.5, 0, -0.25), (1, 5.6, 0.5), role, 'plain')], rot=(0, 0, 0.5))]


def pendant(item, palette, boxes, cord='cord'):
    look(item, palette,
         part('body', (0, 0, 0), [], armour='chest',
              children=cords(cord) + [part('body', (0, 4.8, -2.5), boxes, swing=('x', 0.16, 0.0, 'stride'))]))


pendant('cross_necklace', dict(cord='d8c070', gold='e6b83a'),
        [box((-0.5, 0, -0.5), (1, 4, 1), 'gold', 'metal'), box((-1.5, 1, -0.5), (3, 1, 1), 'gold', 'metal')])
pendant('panic_necklace', dict(cord='d8d8d8', heart='d8282e'),
        [box((-1.5, 0, -0.5), (3, 2, 1), 'heart', 'gem'), box((-0.5, 2, -0.5), (1, 1, 1), 'heart', 'gem')])
pendant('shock_pendant', dict(cord='d8d8d8', gem='3aa8e8', cap='9a9aa0'),
        [box((-0.5, 0, -0.5), (1, 1, 1), 'cap', 'metal'), box((-1, 1, -0.5), (2, 2, 1), 'gem', 'gem')])
pendant('flame_pendant', dict(cord='d8d8d8', flame='f07a1e', cap='9a9aa0'),
        [box((-0.5, 0, -0.5), (1, 1, 1), 'cap', 'metal'), box((-1, 1, -0.5), (2, 2, 1), 'flame', 'gem')])
pendant('thorn_pendant', dict(cord='d8d8d8', thorn='3aa83a', cap='9a9aa0'),
        [box((-0.5, 0, -0.5), (1, 1, 1), 'cap', 'metal'), box((-0.5, 1, -0.5), (1, 2, 1), 'thorn', 'gem')])
pendant('heartstone_amulet', dict(cord='d6a83a', heart='e0485a', gold='e6b83a'),
        [box((-0.5, 0, -0.5), (1, 1, 1), 'gold', 'metal'), box((-1.5, 1, -0.6), (3, 2.5, 1.2), 'heart', 'gem'),
         box((-1.8, 0.8, -0.4), (3.6, 3, 0.8), 'gold', 'metal')])

# ---- charms: hung from the belt on the right of the front ----------------------------------------------------------


def charm(item, palette, boxes):
    look(item, palette,
         part('body', (0, 0, 0), [], armour='legs',
              children=[part('body', (-2.6, 10.2, -2.6), [box((-0.25, 0, -0.25), (0.5, 1.5, 0.5), 'cord', 'plain')],
                             swing=('x', 0.22, 0.4, 'stride'), children=[part('body', (0, 1.5, 0), boxes)])]))


charm('charm_of_sinking', dict(cord='5a4a3a', stone='3a5a8a', iron='7a7a80'),
      [box((-1, 0, -0.5), (2, 2.5, 1), 'stone', 'gem'), box((-1.25, -0.2, -0.6), (2.5, 0.6, 1.2), 'iron', 'metal')])
charm('charm_of_shrinking', dict(cord='3a2a4a', stone='7a3ab0', iron='9a9aa0'),
      [box((-0.75, 0, -0.5), (1.5, 2, 1), 'stone', 'gem'), box((-1, -0.2, -0.6), (2, 0.6, 1.2), 'iron', 'metal')])
charm('chorus_totem', dict(cord='3a2a4a', wood='8a6aa0', eye='4ad0b8'),
      [box((-1, 0, -0.75), (2, 3, 1.5), 'wood', 'carved'), box((-0.6, 0.6, -0.9), (1.2, 0.6, 0.2), 'eye', 'gem')])
charm('rabbit_foot_charm', dict(cord='c8b89a', fur='e8e0d0', claw='3a3028'),
      [box((-0.75, 0, -0.6), (1.5, 2.5, 1.2), 'fur', 'fur'), box((-0.6, 2.4, -0.8), (1.2, 0.5, 1), 'claw', 'plain')])

# ---- belts ---------------------------------------------------------------------------------------------------------


def belt_item(item, palette, boxes, extra=()):
    look(item, dict(strap='5a3a22', buckle='b0b0b8', **palette),
         part('body', (0, 0, 0), [box((-4, 9.6, -2), (8, 1.4, 4), 'strap', 'leather', inflate=0.55), box((-1, 9.4, -2.9), (2, 1.8, 0.6), 'buckle', 'metal')], armour='legs',
              children=[part('body', (-4.9, 10.6, 0), boxes, swing=('x', 0.2, 0.0, 'stride'))] + list(extra)))


look('brawlers_belt', dict(strap='6a4428', buckle='c8c8d0', plate='8a8a92'),
     part('body', (0, 0, 0), [box((-4, 9.2, -2), (8, 2.2, 4), 'strap', 'leather', inflate=0.55),
                              box((-1.5, 9.0, -3.0), (3, 2.6, 0.7), 'buckle', 'metal'),
                              box((-0.8, 9.6, -3.4), (1.6, 1.4, 0.4), 'plate', 'metal')], armour='legs'))
belt_item('lodestone_charm', dict(stone='5a5a62', cord='3a3a3a'),
          [box((-0.25, 0, -0.25), (0.5, 1.5, 0.5), 'cord', 'plain'), box((-1.25, 1.5, -1.25), (2.5, 2.5, 2.5), 'stone', 'speckle')])
belt_item('crystal_heart', dict(gem='d8243a', gold='e6b83a'),
          [box((-0.5, 0, -0.5), (1, 1, 1), 'gold', 'metal'), box((-1.25, 1, -0.75), (2.5, 2.2, 1.5), 'gem', 'gem')])
belt_item('wind_bladder', dict(skin='e8a8a0', tie='8a5a3a'),
          [box((-0.5, 0, -0.5), (1, 1, 1), 'tie', 'plain'), box((-1.75, 1, -1.75), (3.5, 3.5, 3.5), 'skin', 'skin')])
belt_item('ender_pouch', dict(cloth='3a2a52', clasp='3ad0a0'),
          [box((-1.5, 0, -1.25), (3, 3, 2.5), 'cloth', 'cloth'), box((-0.5, 0.2, -1.6), (1, 1, 0.4), 'clasp', 'gem')])

# ---- hands: gloves on both, gauntlets, bracers and rings on one ------------------------------------------------------
# right arm part pivot sits on the arm's middle (x -1 of the bone), so boxes are -2..2 across like a leg


def glove(item, palette, extra=(), sides='both', cuff='cuff'):
    look(item, palette,
         part('right_arm', (-1, 0, 0), [box((-2, 6.2, -2), (4, 3.8, 4), 'glove', 'leather', inflate=0.3, fit=True),
                                        box((-2, 5.4, -2), (4, 1, 4), cuff, 'leather', inflate=0.55, fit=True)] + list(extra),
              sides=sides, armour='chest'))


def claws(role):
    return [box((-1.6, 9.8, -1.6), (0.8, 2.2, 0.8), role, 'bone'), box((-0.4, 9.8, -1.6), (0.8, 2.6, 0.8), role, 'bone'),
            box((0.8, 9.8, -1.6), (0.8, 2.2, 0.8), role, 'bone')]


glove('digging_claws', dict(glove='2a4ab8', cuff='5a3a22', claw='c8c0b0'), claws('claw'))
glove('feral_claws', dict(glove='3a9a2e', cuff='5a3a22', claw='ece4d4'), claws('claw'))
glove('iron_knuckles', dict(glove='5a4a3e', cuff='3a2e26', iron='a8a8b0'),
      [box((-2, 7.0, -2.7), (4, 1.6, 0.8), 'iron', 'metal', fit=True), box((-2, 8.8, -2.6), (4, 0.8, 0.6), 'iron', 'metal', fit=True)])
glove('vampiric_glove', dict(glove='5a2a26', cuff='2a1614'), sides='right')
glove('fire_gauntlet', dict(glove='b83a1e', cuff='5a2a1a', flame='f8a83a'),
      [box((-2, 4.0, -2), (4, 1.6, 4), 'flame', 'flame', inflate=0.7, fit=True)], sides='right')
look('golden_hook', dict(cuff='5a3a22', gold='e6b83a'),
     part('right_arm', (-1, 0, 0), [box((-2, 6.4, -2), (4, 2.6, 4), 'cuff', 'leather', inflate=0.4, fit=True),
                                    box((-0.5, 9, -0.5), (1, 2.5, 1), 'gold', 'metal'),
                                    box((-0.5, 11.2, -2.2), (1, 1, 2), 'gold', 'metal'),
                                    box((-0.5, 10.2, -2.2), (1, 1, 1), 'gold', 'metal')], armour='chest'))


def bracer(item, palette, boxes, sides='left'):
    look(item, palette, part('right_arm', (-1, 0, 0), boxes, sides=sides, armour='chest'))


bracer('ram_bracer', dict(leather='7a5030', gold='e0b23a'),
       [box((-2, 4.2, -2), (4, 3, 4), 'leather', 'leather', inflate=0.45, fit=True),
        box((-1, 4.6, -2.75), (2, 2.2, 0.5), 'gold', 'metal')])
bracer('smelters_bracer', dict(iron='6a6a70', fire='f09030', dark='2a2a2e'),
       [box((-2, 4.0, -2), (4, 3.4, 4), 'iron', 'metal', inflate=0.45, fit=True),
        box((-1, 4.6, -2.75), (2, 2, 0.5), 'fire', 'flame')])
bracer('withered_bracelet', dict(bone='2a2626', glow='6a5a5a'),
       [box((-2, 7.2, -2), (4, 1, 4), 'bone', 'bone', inflate=0.35, fit=True)])


def ring(item, band, gem):
    look(item, dict(band=band, gem=gem),
         part('right_arm', (-1, 0, 0), [box((-2, 9.0, -2), (4, 0.7, 4), 'band', 'metal', inflate=0.12, fit=True),
                                        box((-0.5, 8.7, -2.5), (1, 1, 0.6), 'gem', 'gem')], sides='slot', armour='chest'))


ring('swiftfoot_ring', 'e6b83a', '6ae0e8')
ring('delvers_ring', 'c8783a', 'a050e0')
ring('gluttons_ring', 'e6b83a', 'e88aa0')

# ---- feet ------------------------------------------------------------------------------------------------------------


def boots(item, palette, height=3, extra=(), pattern='leather', sole=True):
    top = 12 - height
    boxes = [box((-2, top, -2), (4, height, 4), 'boot', pattern, inflate=0.35)]
    if sole:
        boxes.append(box((-2, 11.4, -2.4), (4, 0.8, 4.4), 'sole', 'plain', inflate=0.3))
    look(item, palette, part('right_leg', (0, 0, 0), boxes + list(extra), sides='both', armour='feet'))


boots('couriers_boots', dict(boot='7a5434', sole='3a2a1c', wing='f0f0f0'), height=5,
      extra=[box((2.2, 8, 0), (0.5, 2, 2.5), 'wing', 'feather')])
boots('waterstrider_boots', dict(boot='3a5a7a', sole='2a2a30', water='8ad0f0'), height=4,
      extra=[box((-2, 10.2, -2), (4, 0.8, 4), 'water', 'glass', inflate=0.45)])
boots('hare_boots', dict(boot='9a8270', sole='4a3a2e'), height=4, pattern='fur')
boots('kitty_slippers', dict(boot='e8e4dc', sole='6a6a6a', ear='3a3a3a', face='2a2a2a'), height=2,
      extra=[box((-1.6, 9.4, -2.3), (1, 1, 1), 'ear', 'plain'), box((0.6, 9.4, -2.3), (1, 1, 1), 'ear', 'plain'),
             box((-1, 10.6, -2.7), (2, 1, 0.4), 'face', 'plain')])
boots('steadfast_spikes', dict(boot='3a342e', sole='2a2622', iron='b8b8c0'), height=3,
      extra=[box((-2, 11.0, -3.0), (4, 1, 1), 'iron', 'metal'), box((-1.5, 12.0, -1.5), (1, 0.8, 1), 'iron', 'metal'),
             box((0.5, 12.0, 0.5), (1, 0.8, 1), 'iron', 'metal')])
boots('rooted_boots', dict(boot='5a3e26', sole='3a2a1a', root='6a8a3a'), height=4,
      extra=[box((-2.5, 9, -0.5), (0.6, 3.6, 0.6), 'root', 'bark'), box((1.9, 8.5, 0.6), (0.6, 4.0, 0.6), 'root', 'bark'),
             box((-0.3, 10, 2.0), (0.6, 2.6, 0.6), 'root', 'bark')])
boots('strider_shoes', dict(boot='8a2a2a', sole='3a1a1a', pad='d05a3a'), height=3,
      extra=[box((-2.5, 11.6, -2.8), (5, 1, 5.2), 'pad', 'skin')], sole=False)
look('snowshoes', dict(frame='8a6a3e', weave='c8b080', strap='5a3a22'),
     part('right_leg', (0, 0, 0), [box((-3, 11.6, -4.5), (6, 0.8, 9), 'weave', 'straw'),
                                   box((-3.2, 11.4, -4.7), (6.4, 0.6, 1), 'frame', 'plain'),
                                   box((-2, 10.4, -1), (4, 1, 2), 'strap', 'leather', inflate=0.4)], sides='both', armour='feet'))
look('frogskin_flippers', dict(skin='4a9a3a', dark='2f6a2a'),
     part('right_leg', (0, 0, 0), [box((-2, 10.6, -2), (4, 1.4, 4), 'dark', 'skin', inflate=0.35),
                                   box((-2.5, 11.4, -7), (5, 0.6, 5), 'skin', 'webbed')], sides='both', armour='feet'))

# ---- body ----------------------------------------------------------------------------------------------------------
look('featherfall_cloak', dict(cloth='e8e8f0', trim='c8c8d8', clasp='e6b83a'),
     part('body', (0, 0, 0), [box((-4.5, -0.4, -2.6), (9, 2, 5), 'cloth', 'feather', inflate=0.35),
                              box((-0.5, 0.2, -3.3), (1, 1, 0.5), 'clasp', 'metal')], armour='chest',
          children=[part('body', (0, 0.6, 2.4), [box((-4.5, 0, 0), (9, 14, 1), 'cloth', 'feather', inflate=0.2)],
                         rot=(0.06, 0, 0), swing=('x', 0.1, 0.0, 'cape'))]),
     hide_cape=True)

# ---- painting ------------------------------------------------------------------------------------------------------


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mul(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))


def dims(b):
    return [max(1, int(math.ceil(v - 1e-6))) for v in b['size']]


FACE_SHADE = dict(up=1.12, down=0.68, north=1.0, south=0.92, east=0.86, west=0.86)


def faces(u, v, w, h, d):
    """Vanilla cube UV layout: (name, x, y, width, height)."""
    return [('up', u + d, v, w, d), ('down', u + d + w, v, w, d),
            ('east', u, v + d, d, h), ('north', u + d, v + d, w, h),
            ('west', u + d + w, v + d, d, h), ('south', u + 2 * d + w, v + d, w, h)]


def texel(pattern, base, fx, fy, fw, fh, face, rnd):
    """One texel of a face, from its base colour and pattern."""
    n = 1.0 + (rnd.random() - 0.5) * 0.10
    c = mul(base, FACE_SHADE[face] * n)
    edge = fx == 0 or fy == 0 or fx == fw - 1 or fy == fh - 1
    if pattern == 'leather':
        if fy == 1 and fh >= 4 and fx % 2 == 0:
            c = mul(c, 1.18)  # stitching
    elif pattern in ('knit', 'stripes'):
        if pattern == 'knit' and (fx + fy) % 2 == 0:
            c = mul(c, 0.9)
        if pattern == 'stripes' and fy % 3 == 2:
            c = mul(c, 0.72)
    elif pattern == 'straw':
        if (fx + fy) % 3 == 0 or (fx - fy) % 4 == 0:
            c = mul(c, 0.84)
    elif pattern == 'cloth':
        if fy % 2 == 0:
            c = mul(c, 0.95)
    elif pattern == 'feather':
        if (fx + 2 * fy) % 4 == 0:
            c = mul(c, 0.82)
        elif fy % 3 == 0:
            c = mul(c, 1.08)
    elif pattern == 'fur':
        c = mul(c, 0.85 + rnd.random() * 0.3)
    elif pattern in ('metal', 'bone'):
        if fx == fy % max(1, fw) or (fw <= 2 and fy == 0):
            c = mul(c, 1.25)
    elif pattern == 'gem':
        c = mul(c, 1.25) if (fx, fy) == (0, 0) or (fw > 2 and (fx, fy) == (1, 1)) else mul(c, 0.95)
    elif pattern == 'glass':
        c = mul(c, 1.15) if fx == fy else c
    elif pattern == 'eye':
        if 0 < fx < fw - 1 and 0 < fy < fh - 1:
            c = (24, 18, 12)  # the pupil
    elif pattern == 'reed':
        if fy % 3 == 0:
            c = mul(c, 0.78)  # nodes
    elif pattern == 'mesh':
        if (fx + fy) % 2:
            c = mul(c, 0.6)
    elif pattern == 'flame':
        c = mix(c, (255, 220, 90), max(0.0, 1 - fy / max(1, fh)) * 0.6 * rnd.random())
    elif pattern == 'speckle':
        if rnd.random() < 0.18:
            c = mul(c, 1.4)
    elif pattern == 'skin':
        if rnd.random() < 0.12:
            c = mul(c, 0.88)
    elif pattern == 'webbed':
        if fx % 2 == 1 and face == 'up':
            c = mul(c, 0.75)
    elif pattern == 'carved':
        if fy % 2 == 1:
            c = mul(c, 0.8)
    elif pattern == 'bark':
        c = mul(c, 0.8 + 0.3 * ((fy * 7) % 3) / 2)
    elif pattern == 'studs':
        if fy == fh // 2 and fx % 2 == 0:
            c = mul(c, 1.3)
    if edge and fw >= 3 and fh >= 3 and pattern not in ('eye', 'gem'):
        c = mul(c, 0.86)
    return c


def walk(parts):
    for p in parts:
        yield p
        yield from walk(p['children'])


def pack(item, spec):
    """Gives every box a uv and returns the texture size; boxes are packed on shelves 64 wide."""
    boxes = [b for p in walk(spec['parts']) for b in p['boxes']]
    order = sorted(range(len(boxes)), key=lambda i: -(dims(boxes[i])[1] + dims(boxes[i])[2]))
    x = y = shelf = 0
    width = 64
    for i in order:
        w, h, d = dims(boxes[i])
        bw, bh = 2 * (w + d), h + d
        if bw > width:
            raise ValueError(f'{item}: a box is too wide to pack')
        if x + bw > width:
            x, y, shelf = 0, y + shelf, 0
        boxes[i]['uv'] = [x, y]
        x += bw
        shelf = max(shelf, bh)
    height = 16
    while height < y + shelf:
        height *= 2
    return width, height


def paint(item, spec, width, height):
    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(item)
    palette = {k: rgb(v) for k, v in spec['palette'].items()}
    for p in walk(spec['parts']):
        for b in p['boxes']:
            w, h, d = dims(b)
            base = palette[b['role']]
            for face, fx0, fy0, fw, fh in faces(b['uv'][0], b['uv'][1], w, h, d):
                for fy in range(fh):
                    for fx in range(fw):
                        if b['open_front'] and face == 'north' and 0 < fx < fw - 1 and 0 < fy:
                            continue  # the face opening
                        px[fx0 + fx, fy0 + fy] = texel(b['pattern'], base, fx, fy, fw, fh, face, rnd) + (255,)
    return img


def strip(parts):
    out = []
    for p in parts:
        q = {k: v for k, v in p.items() if k != 'children'}
        q['boxes'] = [dict(uv=b['uv'], frm=b['frm'], size=b['size'], inflate=b['inflate'], fit=b['fit']) for b in p['boxes']]
        q['children'] = strip(p['children'])
        out.append(q)
    return out


def main():
    os.makedirs(OUT_TEX, exist_ok=True)
    out = {}
    for item, spec in LOOKS.items():
        width, height = pack(item, spec)
        paint(item, spec, width, height).save(os.path.join(OUT_TEX, item + '.png'))
        out['jaymospowers:' + item] = dict(texture=f'jaymospowers:textures/entity/worn/{item}.png', width=width, height=height,
                                           hide_cape=spec['hide_cape'], parts=strip(spec['parts']))
    with open(os.path.join(RES, 'worn_looks.json'), 'w') as f:
        json.dump(out, f, indent=1)
        f.write('\n')
    print(len(out), 'looks')


if __name__ == '__main__':
    main()
