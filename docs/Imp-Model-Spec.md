# Imp model spec (REA method)

Andy (2026-11-11): "I want to add imp creatures similar to the pigmen design, but they fly around the nether, have
zombified versions ... They fly and lunge and attack from many angles. They charge a large fireball over 3s, that can be
detonated early killing them with a percisely timed attack." And: "Every part should animate the arms legs and wings,
and fireball, swoop attack, basic swing. All of it."

REA-Prompts (https://github.com/JoasASantos/REA-Prompts), section 4, applied to an entity model: study the references
as a black box, write down what is seen (facts with their source, guesses marked UNKNOWN), build only from this spec
with our own boxes and pixels, then list every difference left. No other model's boxes, UVs or pixels are copied.
Everything is made by `kingdoms/tools/gen_imp.py`.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Vanilla piglin and zombified piglin in game, looked at, not copied | the "pigman" make: wide head, snout, tusks, ears, hide and rot colours |
| R2 | The imp of folklore and old fantasy bestiaries (a small winged devil) | size, bat wings, horns, spade tail |
| R3 | Bat flight (real) | two-part wing, the tip trailing the arm on each beat, wings folded on the ground |
| R4 | Vanilla ghast fireball in game | the fireball's read: a fire charge three times the size, knocked back by a blow |
| R5 | Lore.md, "The Nether and the demons" | who they are: the demons' flying kin, at the sorcerer's battle |
| R6 | Our pack's style: cuboids, 1 texel = 1/16 block (docs/Vanilla-Style.md) | box sizes, texture density |

## 2. Facts (what the model must show)

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| F1 | A wide, flat-faced head with a snout pushed out of it, two tusks, ears out to the sides | R1 | head 8x7x7, snout 4x3x1.5, `tusk` and `ear` bones each side |
| F2 | Small: well under a man's height | R2 | 22 px to the crown (1.4 blocks), hitbox 0.7 x 1.4 |
| F3 | Two short horns | R2 | `horn` bones, two boxes each, tipped back and out |
| F4 | Bat wings from the shoulder blades, in two parts | R2, R3 | `wing` and `wing_tip` each side: a finger box with the membrane hung under it |
| F5 | A thin tail ending in a spade | R2 | `tail_0..2`, a flat spade box on the last |
| F6 | Arms and legs bend at elbow and knee | Andy's ask | `arm` + `forearm`, `leg` + `shin` each side |
| F7 | Zombified: the same body, hide gone grey-green, raw flesh and bone showing, dead eyes | R1 | second skin on the same layout (`zombified_imp.png`) |
| F8 | The fireball is gathered over the head, between raised hands, growing for 3 s | Andy's ask | `fireball` bone (6 px cube), scaled 0.05 to 1 by the `charge` animation |
| F9 | The last moments of the charge must read as the opening | Andy's ask ("precisely timed") | the ball throbs from 2.4 s to 3.0 s; the game adds a fuse hiss and lava sparks |

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: colour of a living imp. Chosen: ember red hide, paler belly and snout, soot-black wing fingers, dull red membrane.
- U2: clothing. Chosen: a scorched loincloth only.
- U3: eye colour. Chosen: yellow, glowing against the brow's shadow; the zombified's are pale and blank.
- U4: wingspan. Chosen: 16 px a side (2 blocks tip to tip, plus the body).
- U5: how long the opening is. Chosen: the last 12 ticks of the 60 (Imp.DETONATE_WINDOW), and the 5 ticks before the
  ball leaves the hands.

## 4. Skins (painted from nothing)

Living palette: hide (150,58,44), dark hide (112,40,34), belly (184,98,70), snout (196,112,86), wing finger (52,30,30),
membrane (124,36,40), tusk (226,214,180), horn (64,48,44), claw (40,30,28), hoof (48,36,32), loincloth (72,52,36),
fire (255,170,40) to core (255,240,170), eye (255,214,64).
Zombified palette: hide (122,136,108), dark hide (88,102,82), belly (150,150,118), membrane (92,96,78), raw flesh
(168,82,96), bare bone (212,204,170), eye (236,240,226).
Per texel: the material's colour with seeded noise and a light per face; a brow shadow and two-pixel eyes on the face;
nostrils on the snout; ribs and a ragged edge on the membranes; rot in 3x3 patches over the zombified hide.

## 5. Layout

128x64, one box-layout region per cube, packed in rows by the script in the order the cubes are made (26 bones, 39 cubes).

## 6. Animations (all in `imp.animation.json`)

| Name | Length | What moves |
| --- | --- | --- |
| idle | 3.0 s loop | wings folded, breathing, head looking about, ears flicking, tail swaying |
| walk | 0.6 s loop | legs and knees, arms and elbows opposite, folded wings bobbing, tail, hips |
| fly | 0.5 s loop | wingbeat with the tips trailing, legs tucked and paddling, arms, ears, tail, body bob |
| swing | 0.5 s | right claws drawn up and raked across, left arm back, body and head turned into it |
| swoop | 1.0 s | wings folded along the body, arms reaching, legs trailing, then the rake and wings thrown wide to brake |
| charge | 3.0 s, holds | arms up and apart, fireball swelling and turning, body arched, four heavy wingbeats, legs drawn up, tail lashing |
| throw | 0.6 s | arms drawn back and slammed forward, body whipped over, the ball leaving the hands |
| hurt | 0.35 s | wings jolted, arms wide, head snapped aside |
| death | 1.0 s, holds | wings fail and fold, limbs curl |

## 7. Differences left (validation)

Checked with `gen_imp.py --preview` renders (kingdoms/art/imp-render.png):
- Raised arms in the charge sit beside the big head and are hard to see from the front; they read from the side and back.
- Membranes are flat boxes: they don't stretch between the wing parts, so a gap opens at the wing's elbow on a deep fold.
- The swing and hurt key only the upper body, so the wings and legs keep the flight loop under them (by design).
- Cuboids can't round the snout or the horns.
- Not yet seen in game: the previews use the same maths as the bake, but GeckoLib's blending between loops and the
  timing of the lunge against the swoop animation need a look in play.
