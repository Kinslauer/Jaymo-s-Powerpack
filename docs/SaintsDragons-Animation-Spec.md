# Saint's Dragons animation spec (REA method)

Andy (2026-11-06): "saint's dragons?" then "i really just want animations". The models stay as they are. This spec
covers how our four lesser dragons move. It follows REA-Prompts (https://github.com/JoasASantos/REA-Prompts),
section 4 "Software & feature cloning": study the reference from the outside, write down what is seen (facts with their
source, guesses marked UNKNOWN), build only from this spec, then list what still differs.

No Saint's Dragons animation file is used or converted. Its `.animation.json` files are all rights reserved
(`docs/SaintsDragons-Plan.md`). We read only their names and lengths, which tell us what a dragon does and roughly how
long it takes. Everything else is drawn procedurally in our models, as the rest of the pack's beasts are.

## 1. References

| Ref | What | Used for |
| --- | --- | --- |
| R1 | Saint's Dragons 0.9.88: the list of animation names and lengths per dragon (from the extracted zips Andy sent) | which motions each dragon needs, rough timing |
| R2 | Real crocodiles: high walk, gallop, basking with the jaws open, death roll, tail sweep | Varasuchus |
| R3 | Seals and sea lions crutching on land; snakes' lateral undulation, where the curves stay fixed to the ground | Atroxiia |
| R4 | Stegosaurs and big grazers (heavy plod, graze, head toss) | Stegonaut (done in 0.7.531, unchanged here) |
| R5 | eanncrod's Kintsugi wyvern (CC BY 4.0), the model Ignivorus is drawn on, and its 28 animations | Ignivorus, Eurus, Aeolus |

## 2. What the reference does (R1), and where we stand

Lengths are in seconds as listed in R1. "Ours before" is the pack at Kingdoms 0.7.611.

### Varasuchus

| R1 motion (length) | Ours before | Now (0.7.612) |
| --- | --- | --- |
| idle, idle2 (5) | breathing, a slow jaw gape | kept, plus the idle fidgets below |
| walk, walk2 (2.5, 2) | high diagonal walk | kept |
| run, run2 (1.67, 1.33) | the walk sped up | a gallop: front pair and hind pair each together, the body rocking (R2) |
| bite, bite2 (0.83) | none (the base bite landed with no motion) | drawn back with the jaws wide, then snapped forward and shut, 16 ticks |
| claw_left/right, run_and_claw (0.54) | claw move | kept |
| horn_gore (0.83) | horn move | kept |
| tail_attack_left/right (1.46), tail_swipe_left (2.33) | none for the base sweep | the tail swung round to its left and back, rolling down it a beat per segment, the body twisting in, 26 ticks |
| slash_barrage (4.62) | barrage | kept |
| phase1 / phase2 (1.04), dash_left/right (1.33) | rise onto hind legs, dash | kept |
| tailguard, _hold, _cancel, _parry | tailguard and parry | kept |
| hurt (2) | none | a flinch: jerked aside, head tossed back, jaw open, then righted |
| die (2.79) | none | legs locked out stiff, jaw slack, head lolled, tail limp (with the game's sideways fall) |
| grumble1-3 (6.7-7.5), flex 1-3, investigating (3.5) | none | every 15 s stood still: a hiss (jaw wide, head low), a head shake, a tail swish, or a slow sniff along the ground, in turn |
| sit, down/up (5, 1.25) | settles back | kept |
| fall_asleep, sleep, wake_up | none | sitting, it basks with its jaws propped open, breathing slowly (R2) |
| swim_idle (5), swim_move | legs folded back, tail sculling | floating still, the legs tread slowly; under way the strokes quicken |
| bucking (4.46), enter_grooming, eat, jump | none | not taken (no matching behaviour in our dragon) |

### Atroxiia

Andy, 2026-11-04: "atroxiia has no walk animation". It was pulled (0.7.535) because it slid along: the arms swung only
0.45 rad, the chest bobbed under a pixel, the tail barely moved, and a heavy beast's walk speed reads low, so at a crawl
almost nothing showed.

| R1 motion (length) | Ours before | Now (0.7.612) |
| --- | --- | --- |
| walk (2.5), slither, slither3 (3.5) | faint arm swing | a seal's crutch (R3): each hand lifted high, swung far forward and planted, the chest heaving up over the planted arm and rolling toward it; the serpent body throws two S-curves down its length keyed to the ground covered, so the curves hold still as it slides through them (R3). Shows at full from a slow crawl up. |
| run (1.25) | the walk sped up | bounds: both arms reach and pull together, the chest rocking fore and aft, the body wave flatter |
| idle (5), grumble1-3 (1-1.5) | breathing | every 16 s stood still: a grumble with the head tilted and jaw parted |
| taunt (2.92), flex (3.33) | none | chest raised, arms flung out wide, jaw open |
| investigating (3.5) | none | head down to the ground, sniffing side to side |
| underwater_bite (1.12) | none (the base bite had no motion) | the plain bite on land and in water: neck drawn back, jaws wide, lunged and shut, 16 ticks |
| swipe_left/right, slam_left/right, precise_strike, gungnir_stab, devastating_sweep, helheim_quake1/2 | all six moves | kept |
| (base tail sweep) | none | a lash: a flick of the tail to its right, rolling down it, 24 ticks |
| hurt (0.75), stunned (2.62) | none | a flinch: rocked back and aside, jaw open |
| die (1.96) | none | the arms give way and splay, head and tail slack |
| sit, sleep, fall_asleep, wake_up | arms folded | sitting it dozes: head laid down on its arms, tail curled round |
| swim, swim_idle (2.5) | arms swept back, body undulating | floating still the arms scull slowly; under way faster |
| eat, jump | none | not taken |

With its walk drawn, Atroxiia is back in the game (`Dragons.ATROXIIA_ON = true`): it spawns again and `/kingdom dragon
atroxiia` works.

### Ignivorus (on the Kintsugi wyvern)

R1 lists 55 motions for Ignivorus (fire breath in three parts, fireballs at three charge levels on the ground and in the
air, stomps, leap and impact, wing swipes, body slam, skyfall, bulldozer, phase two, roar, sleep, death). Our Ignivorus
is drawn on the Kintsugi wyvern (R5), which brings 28 animations of its own. Before, its model used four of them
(idle, hover, flight, knockdown), so its fireballs and magma pillars came with no motion at all.

| R1 motion | Ours before | Now (0.7.612), from R5's set |
| --- | --- | --- |
| fireball_level1-3_charge/shoots (_ground) | none | the fireball volley plays `wyvern_fire` on the ground, `wyvern_hover_fire` in the air (60 ticks) |
| ignivorus_stomp_left/right, ignivorus_impact | none | the magma pillars' stamp plays `wyvern_slam` (50 ticks) |
| bite (1.42) | none | `wyvern_bite` (32 ticks) |
| wing_swipe, body_slam | none for the base sweep | the base tail sweep plays `wyvern_tail_sweep_left` (42 ticks) |
| hurt (1.75), stunned | none | `wyvern_flinch` at each blow |
| die (4) | the game's sideways fall | `wyvern_death`, without the sideways fall |
| sleep, fall_asleep | legs folded | sitting, `wyvern_sleep` |
| roar (3.62), grumble1-3 | none | stood idle, `wyvern_roar` about every 26 s |
| fire_breath_* | jaw drops | kept |
| skyfall, bulldozer, phase2_*, leap, drinking | none | not taken (no matching behaviour yet; the plan's "still to come" list) |

Each move fades in over its first tenth and out over its last fifth, played through at its move's pace, with the idle
(or the wingbeat) giving way under it. The flinch, death, sleep and roar are not Ignivorus's own moves, so Eurus and
Aeolus (the same wyvern) get them too.

### Stegonaut

Already mapped to its reference set in 0.7.531 (bite, chin slam, ground slam, the ground eating in three parts, roar,
graze, grumbles, flinch, gallop, swim stroke, doze, death). Nothing changed here.

## 3. UNKNOWN / guesses (decided here, change freely)

- U1: R1's exact keyframes. Only names and lengths were read, so every pose here is ours.
- U2: move lengths. Ours are shorter than R1's where our moves are quicker (bite 16 ticks against R1's 0.83 s = 17;
  Ignivorus's wyvern anims are played faster than authored to fit its moves: fire 4.08 s in 3 s, slam 3.5 s in 2.5 s).
- U3: how often idle fidgets come. Chosen: Varasuchus every 15 s, Atroxiia every 16 s, Ignivorus a roar every 26 s.
- U4: Atroxiia's stride. Chosen: 0.42 rad of gait per walk distance, two curves of body wave, sway 0.1 rad at its
  root growing to 0.32 at its tip.
- U5: whether sitting means sleeping. Ours have no separate sleep state, so a sitting dragon dozes (Varasuchus basks,
  Atroxiia lays its head down, the wyvern curls up).

## 4. Built where

- `WindDragon.swept()`: a hook when the base tail sweep lands (beside 0.7.531's `bitten()`).
- `VarasuchusDragon`: `BITE_MOVE` (16 ticks) and `SWEEP_MOVE` (26) from `bitten()`/`swept()`.
- `AtroxiiaDragon`: `BITE_MOVE` (16) and `LASH_MOVE` (24).
- `IgnivorusDragon`: a synced action of its own: `VOLLEY_MOVE`, `STAMP_MOVE`, `BITE_MOVE`, `SWEEP_MOVE`.
- `VarasuchusDragonModel`, `AtroxiiaDragonModel`: the motions above, every one an eased envelope that starts and ends at
  rest (Andy: "all the joints should move and blend smoothly").
- `KintsugiDragonModel(true)`: Ignivorus's move mapping; `WindDragonRenderer.ownDeath()` turns off the sideways fall for
  the wyverns.

## 5. Differences left (validation)

Checked with Gradle (`compileClientJava`) and a walk/run preview of Atroxiia rendered from its body plan
(`art-overhaul/lesser-dragons/atroxiia-walk.gif`, `atroxiia-run.gif`). Not played.
- No eat, jump, bucking, grooming or wake-up motions: our dragons have no behaviour for them.
- Ignivorus has no stomp left/right, leap, skyfall, bulldozer or phase two, since it doesn't do them yet.
- The walkers still fall onto their side as they die (the game's own death), with their limbs posed over it.
- Sitting and sleeping are one state.
