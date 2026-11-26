# Wither model spec

Andy (2026-11-18): our own Wither model, animations and sounds in the pack's style (the wr_look pick from the Fire and
Wither list). His direction after the first tries (2026-11-19): "it should look more hulking like the large suit and
broader shoulders for the heads to sit on, the two side heads becoming pauldron like. and four ribcage like arms
sticking out"; on the third try: charred black like the Wither, not bone white, the centre skull sat on the body, the
four rib arms plain to see. 2026-11-24: "finish the wither model".

Made by `tools/gen_wither.py` (shape, skin, glow, pale skin, keyframes; bakes with `tools/bbmodel_bake.py`), editable in
Blockbench at `kingdoms/art/wither.bbmodel`. Sounds by `tools/gen_wither_sounds.py`. Drawn by
`client/wither/WitherRenderer` (in place of vanilla's), which plays the move `wither/WitherAct` tells it.

## Shape (Blockbench px, 16 a block; drawn at 0.85, about 4 blocks tall and 3.9 across the shoulders)

| Bone | What |
| --- | --- |
| body | a 26 px wide ribcage: four rib bands round a dark core (violet seams glow between the ribs), breastbone and backbone |
| shoulders | a 54 px yoke across the top and a hump the centre skull sits in |
| head_c | the big centre skull (16 px), its jaw in front of the hump, two short horns |
| pauldron_r/l, head_r/l | flared plates at the yoke's ends, a 12 px skull on each: the side heads as pauldrons |
| arm_ur/ul/dr/dl (+ _fore, _claw) | four rib arms: a ridged upper rib, a crooked forearm, three claws |
| spine, tail, tail_tip | vertebrae with floating ribs, a tail tapering to a spike |

Skin: soot black with ash on the upper edges, cracks, sockets with cold violet-white pupils (glow), teeth. 256 px sheet.

## Animations (seconds)

| Name | Plays when | Length |
| --- | --- | --- |
| idle | hanging still | 3.0 loop |
| orbit / orbit_fast | circling (by how fast it moves); fast in its last third | 2.0 / 1.2 loop |
| ravage | each skull thrown at a house | 0.8 |
| beam_tell, beam_fire | the beam's 55-tick tell, then the shot | 2.75 hold, 0.7 |
| charge_tell, charge_rush | the charge's 28-tick tell, then the rush | 1.4 hold, 0.6 loop |
| stun | dazed after a shield stops the charge | 2.0 loop |
| climb | rising before the skull rain, and through it | 2.0 hold |
| throw_blues | flinging blue skulls (one every 14 ticks) | 0.7 loop |
| spawn | its 220 ticks of build-up (also smaller, flickering pale) | 11.0 hold |
| death | its last second | 1.0 hold |

The three skulls take vanilla's head looks on top of any animation.

## Sounds

Synthesised, nothing sampled: ambient_1..3, hurt_1..3, death, spawn, shoot_1..2 (in place of vanilla's Wither sounds,
assets/minecraft/sounds.json) and beam_charge, beam_fire, charge_roar, charge_rush, stun (jaymoskingdoms:wither.*).
