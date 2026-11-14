# Sand worm and the thing under the wreck: model spec (REA method)

Andy (2026-11-14), asked what porting Bosses'Rise should still add: "Remodel our five bosses". The dragon (Kintsugi's
model) and the snow giant (docs/Snow-Giant-Model-Spec.md) already have full bodies; these two were plain towers of
boxes. Bosses'Rise is All Rights Reserved: nothing of its models, textures or animations was opened or copied.

REA-Prompts (https://github.com/JoasASantos/REA-Prompts), section 4, applied to entity models: what the model must
show (facts with their source, guesses marked UNKNOWN), built only from this spec with our own boxes and pixels, then
the differences left. Everything is made by `kingdoms/tools/gen_lair_beasts.py`.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | `beasts/SandWorm`, `beasts/WreckThing` (our own code) | what each does and for how long |
| R2 | Lore.md, "Four more great ones" | the worm is an old Stoneborn legend under the desert's ruins; the thing is the Deep Mother's own, takes hold of ships, its teeth "hooked" |
| R3 | Real animals: bristle worms (rings, bristles, jaws that evert), giant squid (mantle, fins, eight arms and two long feeding tentacles with hooked clubs, a beak in the ring of arms, level pupils) | body plans |
| R4 | Our pack's style: cuboids, 1 texel = 1/16 block | box sizes, texture density |

## 2. Facts (what the models must show)

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| W1 | It stands about nine blocks out of the sand, in a 3 x 8 box | R1 | 88 px of rings and 28 of head, drawn 1.6x |
| W2 | A bend runs up the whole body | R1 | eight rings, each a bone riding the one under it |
| W3 | It comes up through the sand in 10 ticks and goes under in 20 | R1 | `rise`, `sink`; the renderer moves it through the ground |
| W4 | Its bite rears back, lands flat along a line at tick 16 of 30 | R1 | `bite`: back by 0.65 s, down at 0.8 s, jaws shut at 0.82 s |
| W5 | Sand-coloured, of the desert | R2 | sandstone hide, darker plate, pale belly |
| T1 | It lies under a wreck and reaches up for ships | R1, R2 | arms standing up from the body, the beak on top among them |
| T2 | Eight arms that open and clap shut (lash, landing at tick 14 of 26) | R1 | `lash`: open at 0.55 s, shut at 0.7 s |
| T3 | It holds what it grabs over itself and squeezes every 15 ticks | R1 | `grab`: curled from 0.75 s, tightening every 0.75 s |
| T4 | It darts off behind ink | R1 | `ink`: arms trailed, mantle pumping |
| T5 | Hooked teeth | R2 | hooks on the feeders' clubs, small teeth round the beak |

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: the worm's mouth. Chosen: four plated jaws that open like a flower, four hooks between them.
- U2: the worm's armour. Chosen: a darker plate band on every ring with a spine behind and a bristle each side.
- U3: the thing's colour. Kept from its first skin: deep wine red, pale inside the arms.
- U4: the thing's eyes. Chosen: gold lamps with level slit pupils, facing forward under brows.
- U5: feeders. Chosen: two, a third longer than the arms.

## 4. Animations

Named for the beasts' actions and as long as each lasts in the code.

| Model | Name | Length | What moves |
| --- | --- | --- | --- |
| worm | idle, warn, up | 4 s loops | a wave up the rings, jaws breathing, head turning |
| worm | rise | 0.5 s | jaws burst open, arched back |
| worm | bite | 1.5 s | reared back and spread, thrown down, jaws shut, back up |
| worm | sink | 1.0 s | jaws shut, the body turning as it goes |
| worm | death | 1.0 s, holds | down along its length, jaws fallen open |
| thing | idle | 6 s loop | arms waving out of step, mantle breathing, fins, beak |
| thing | swim | 2 s loop | arms opened and clapped together, fins beating |
| thing | lash | 1.3 s | opened wide, clapped shut, eased back |
| thing | grab | 4.5 s, holds | opened, closed, kept curled and tightened |
| thing | ink | 1.8 s | arms trailed, mantle pumping |
| thing | death | 1.0 s, holds | arms slack and splayed, mantle sagging, beak open |

## 5. Differences left (validation)

Checked in `gen_lair_beasts.py --preview` renders (project files `art-overhaul/boss-remodels/`):
- The worm's rings are square in section, not round.
- The worm shows nothing during `warn` (it is under the sand; the server's particles are the tell).
- The thing's dead pose reads close to its opened pose from far off.
- The thing's mantle and feeders reach outside its 3.2 x 3.0 hitbox.
- Neither has been seen in game.
