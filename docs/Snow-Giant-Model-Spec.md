# Snow giant model spec (REA method)

Andy (2026-11-12, about the Snow Giant first drawn as a white grizzly at twice the size): "add it to the giants being
worked on, as its own upright model."

REA-Prompts (https://github.com/JoasASantos/REA-Prompts), section 4, applied to an entity model: write down what the
model must show (facts with their source, guesses marked UNKNOWN), build only from this spec with our own boxes and
pixels, then list every difference left. No other model's boxes, UVs or pixels are copied. Everything is made by
`kingdoms/tools/gen_snow_giant.py`.

## 1. References studied

| Ref | What | Used for |
| --- | --- | --- |
| R1 | `wild/SnowGiant` and `wild/Grizzly` (our own code) | what it does and for how long: rear and slam, thrown ice, swipes, charge, maul, roar, stagger |
| R2 | Lore.md, "Four more great ones": "a white, shaggy great beast" of the cold plains, the Frostborn's neighbour | coat, bearing (not a monster's face) |
| R3 | The wild man of the snows in mountain folklore (an upright, long-armed, shaggy giant) | build: upright, arms to the shins, small head sunk forward |
| R4 | Real cold-country animals: muskox coat (long guard hair over the shoulders), bare dark skin on a polar bear's nose and pads | the mantle, the slate face, paws and feet |
| R5 | Our pack's style: cuboids, 1 texel = 1/16 block | box sizes, texture density |

## 2. Facts (what the model must show)

| # | Fact | Source | In the model |
| --- | --- | --- | --- |
| F1 | Upright, its own body, not the grizzly's | Andy | two jointed legs, a chest, two jointed arms; 15 bones |
| F2 | White and shaggy | R2 | white coat, a longer greyer mantle over shoulders and back |
| F3 | A giant, twice the size it was first built (Andy, 2026-11-12: "make it twice as large") | Andy | 55 px to the crown, drawn 2x: 6.9 blocks tall, 6 across the arms; hitbox 4.0 x 6.8 |
| F4 | It rears, lifts both arms and brings its weight down | R1 | `rear`: up by 12 ticks, down on its fists at 26 |
| F5 | It throws blocks of ice from over its head | R1 | in `rear` both paws are over the head at tick 18, when the ice leaves them |
| F6 | It swipes with either paw | R1 | `swipe_left`, `swipe_right`: long arms, the blow at tick 7 |
| F7 | It mauls with its jaws | R1 | a `jaw` bone; `maul` bites at ticks 10, 22 and 34 |
| F8 | It roars when enraged | R1 | `roar`: arms wide, head up and shaken, jaw open |
| F9 | Bare skin where a cold-country beast has it | R4 | slate face mask, paws and feet |

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: horns. Chosen: two short horns the grey of old ice.
- U2: beard. Chosen: three icicles under the jaw.
- U3: eye colour. Chosen: pale ice blue.
- U4: claws. Chosen: dark, short, on paws and feet.
- U5: its leap has no action of its own in the code, so it has no pose; it leaps in whatever it was doing.

## 4. Skin (painted from nothing)

Coat (224,231,236), mantle (190,202,212), chest (238,240,236), skin (88,106,126) with frost freckles (150,176,198),
horn (142,162,180), ice (150,206,236), claw (52,62,76), eye (150,226,255), mouth (44,52,70).
Per texel: the material's colour with seeded noise and a light per face; streaks in the coat and mantle; the face mask,
brow shadow and two-pixel eyes on the head's front; teeth along the inside of the jaw.

## 5. Layout

256x256, one box-layout region per cube, packed in rows by the script in the order the cubes are made (15 bones, 34 cubes).

## 6. Animations (in `bbmodels/snow_giant.json`, baked from `kingdoms/art/snow_giant.bbmodel`)

Named for the grizzly's actions and as long as each lasts in the code, so the game plays them with no change to the beast.

| Name | Length | What moves |
| --- | --- | --- |
| idle | 4.0 s loop | breathing, arms hanging and swaying, head turning |
| walk | 1.6 s loop | two strides, knees, arms against the legs, shoulders rolling |
| run | 1.0 s loop | leaning in, long strides, arms driving |
| rear | 1.7 s | up, arms overhead (ice leaves at 0.9 s), down on its fists at 1.3 s |
| swipe_left, swipe_right | 0.7 s | a paw wound out wide and swept across at 0.35 s, the shoulders with it |
| charge | 1.5 s | head down behind the right shoulder, running |
| maul | 2.3 s | bent over, paws down, biting at 0.5, 1.1 and 1.7 s |
| roar | 2.0 s | arms wide, chest out, head up and shaken, jaw wide |
| stagger | 1.2 s | knocked back onto a heel, arms up, head aside |
| death | 1.0 s, holds | to its knees, then onto its face |

## 7. Differences left (validation)

Checked with `gen_snow_giant.py --preview` renders (kingdoms/art/snow_giant-render.png):
- The coat is flat boxes with a noisy skin; it has no hanging tufts.
- Bent over in the slam and the maul the chest and mantle hide most of the head from the front.
- With a barrage of thrown ice the `rear` animation ends (arms down) while later blocks still leave from over its head.
- The hitbox is 4.0 wide (its trunk; the arms hang outside it, so its blows still start at the old distances) and 6.8 tall;
  the horn tips stand 0.8 above it. Drawn 2x, a texel is 1/8 block.
- Not yet seen in game.
