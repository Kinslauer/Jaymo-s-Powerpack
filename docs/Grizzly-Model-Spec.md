# Grizzly model spec (REA method)

Andy (2026-11-05): "Can you use rea for models". REA-Prompts (https://github.com/JoasASantos/REA-Prompts), section 4
"Software & feature cloning", applied to an entity model: study the references as a black box, write down what is seen
(facts with their source, guesses marked UNKNOWN), build only from this spec with our own boxes and pixels, then list
every difference left. No other mod's boxes, UVs or pixels are copied.

The first grizzly (0.7.610) reused Bear Minimum's brown bear boxes and recoloured its texture (Apache-2.0, allowed).
0.7.611 replaces both with the model and coat below.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Real grizzly (North American brown bear) anatomy, from field-guide knowledge | shape, proportions, coat |
| R2 | Bear Minimum's brown bear in game (Apache-2.0), looked at, not copied | the pack's bear scale next to ours |
| R3 | Vanilla polar bear's rear-up (warning) behaviour | timing of the stand |
| R4 | Animal Garden spectacled bear page (all rights reserved) | ideas only: diurnal forager, defends cubs |
| R5 | Our pack's style: vanilla-style cuboids, 1 texel = 1/16 block, vanilla-like shade ramps (docs/Vanilla-Style.md) | box sizes, texture density |

## 2. Facts (what the model must show)

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| F1 | The shoulder hump (digging muscle) is the highest point of a grizzly on all fours | R1 | `hump` box on the chest, top at y 0, 2 px above the rump |
| F2 | The rump sits lower than the shoulders | R1 | rump top y 6, chest top y 3 |
| F3 | The face is dished: a step from brow down to a long muzzle | R1 | skull top -5, muzzle top -1.5 (3.5 px step) |
| F4 | Small, round ears set wide | R1 | 3x3x1 ears at the skull's outer corners |
| F5 | Very long front claws, pale horn coloured; hind claws short | R1 | `claws` box (5x1x2) on each front paw only, pale |
| F6 | Coat brown with silver/blond tips over the hump, back and crown ("grizzled"); legs darker; muzzle paler | R1 | painted (section 4) |
| F7 | Short stubby tail | R1 | 3x3x2 tail |
| F8 | Rears onto its hind legs to see and warn, pivoting at the hips | R1, R3 | body pivots at the hip (0, 14, 8); head and forelegs ride the body |
| F9 | Opens its jaw wide when roaring and biting | R1 | separate `jaw` part, opened by the roar and the maul's bites |
| F10 | Bigger than the pack's brown bear (a mini-boss) | R2, Andy's ask | 32 px long, 24 px to the hump, drawn 1.15x (about 2.3 blocks long) |

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: exact shoulder height in blocks. Chosen: about 1.7 blocks to the hump at 1.15x (a real big male is ~1.0-1.5 m).
- U2: eye placement and colour. Chosen: 1 px dark eyes high on the skull's front, close to the brow.
- U3: scars. Not a grizzly fact, a mini-boss tell: two pale raked lines over the left of the face.
- U4: how far the forelegs reach out when reared. Chosen: 0.6 rad forward of the body.

## 4. Coat (painted from nothing by `tools/gen_grizzly.py`)

Palette (ours): base brown (82,56,36), dark brown (58,39,26), warm mid (108,74,46), silver tip (182,162,124), leg dark
(46,31,21), muzzle tan (140,106,72), nose (28,22,20), claw horn (204,190,160), mouth (120,52,50), teeth (224,218,200).
Per texel: base lightened/darkened by a seeded noise, short vertical fur streaks, frost toward the silver tip on the
top faces of hump, chest and rump and the upper third of their sides, thinning down the flanks; legs fade darker to the
paw; jaw and muzzle tan; nose on the muzzle's front; mouth roof under the skull and inside of the jaw so an open jaw
shows a mouth with a row of teeth.

## 5. Layout (128x64)

| Box | Size (w,h,d) | UV |
| --- | --- | --- |
| chest | 15,14,12 | 0,0 |
| rump | 13,12,12 | 0,26 |
| hump | 10,4,9 | 54,0 |
| skull | 10,9,8 | 54,13 |
| muzzle | 6,4,5 | 92,0 |
| jaw | 5,2,5 | 92,9 |
| ears | 3,3,1 | 114,0 |
| tail | 3,3,2 | 114,4 |
| front leg | 5,10,6 | 54,30 |
| hind leg | 5,10,7 | 76,30 |
| claws | 5,1,2 | 100,30 |

## 6. Differences left (validation)

Checked with `tools/gen_grizzly.py --preview` renders (rest, reared, swipe, charge, maul, roar):
- The hump reads only from the side and three-quarter views; from straight ahead it is hidden by the head (as in life).
- Cuboids can't round the belly or the dished face; the dish is one step.
- Legs are single boxes (no elbow/knee bend); the rear-up lifts the forelegs as a whole.
- No cubs, no foraging: the grizzly's behaviour is ours (Grizzly.java), not Bear Minimum's or Animal Garden's.
