# Jaymo's Kingdoms: the wind dragons

Andy's dragons for Kingdoms (2026-10-21). Our own design: Berk's Dragon Academy is All Rights Reserved, so nothing of
its code or art is used, only the idea of winning a dragon's trust and riding it. The approved preview was the
"Kingdoms Dragons" artifact (its "cinder" species became Notus). Built in `kingdoms/` (`dragons/`, client
`client/dragons/`, art `tools/gen_dragons.py`); the log is `kingdoms/MODLOG.md`.

## The five
Andy: "Names : Boreas, Zephyrus, Notus, Eurus, and Aeolus."

| Dragon | Wind | Home | Breath | Nature | Favourite | Bond |
|---|---|---|---|---|---|---|
| **Notus** | south | badlands, basalt deltas; perches on spires | a cone of fire: creatures and blocks catch | heavy, slow to turn, hits hardest, tough hide; tail club, back spikes | cooked mutton | Emberborn tame it twice as fast; Kindle doesn't scare it |
| **Boreas** | north | frozen and jagged peaks, ice spikes | frost: slows, freezes, turns water to ice for a while | a long glider that barely flaps, a silent diver | raw salmon | Frost-born and Cryokinesis users; its frost freezes smoke into fog |
| **Zephyrus** | west | swamps and mangroves, half sunk in the water | a gob of acid that poisons and leaves a toxic puddle | swims as well as it flies, ambushes from the water | tropical fish | Verdant and Tideborn |
| **Eurus** | east | disguised as a villager, anywhere | a crackling bolt that chains between foes | the fastest flier, the weakest hide, flies in storms | cooked rabbit | Stormborn; Wind users can ride it in a gale |
| **Aeolus** | keeper of the winds | disguised as a villager, anywhere | all four in turn: fire, frost, storm, spores | the boss: about three times their size, three phases (ground, air, a gale that throws everything) | | |

## The two in disguise
Andy: "Eurus and Aeolus are actually MCA villagers, they can spawn anywhere and will transform after questioning you if
you have already beaten two other dragons." "If you haven't killed the past three they simply shoo you off unless you
have wind control, a tamed dragon, an unlocked heritage, arsenal, chrono or darktide."
- Eurus drops the disguise once you've beaten two dragons; Aeolus once you've beaten three.
- **Only kills count as beating a dragon** (Andy's decision, 2026-10-21; taming one doesn't).
- Short of that they shoo you off, unless you have Wind Control, a tamed dragon, an earned heritage, Arsenal, Chrono or
  Darktide: then they reveal themselves anyway.
- Beating Aeolus calms every wild dragon near for a day and leaves eggs; an egg hatches into a young dragon that grows
  up already bonded to you.

## What every dragon shares
- A miniboss: a boss bar for whoever hits it (`BossBars`), in the `c:bosses` entity tag (`Senses`: caught unaware like
  other bosses), an id with "dragon" in it (slaying or taming one wakes Dragonblood, `Heritage.dragon`).
- **Taming:** worn below a third of its health it crashes and is grounded for a while. Crouch near it with empty hands
  (or its favourite food); a trust meter (your HUD, only yours) fills; offering its favourite fills it faster, the
  species' heritage twice as fast. A drawn weapon, sprinting at it or anyone hurting it drains it and makes it lash
  out; empty, it bolts, heals and flies off. Full: tamed to that player.
- **Riding:** a saddle (the horse slot; `can_equip_saddle`), free-rein flight with momentum (Space take off and climb,
  W fly where you look, S brake and hover, Shift dive and land), its own stamina (hard flight and breath drain it, meat
  refills it; exhausted, it lands), R breathes where you look, a second player rides behind (Andy: "you can have a
  piggyback rider").
- Andy: "fire burns blocks": a wild dragon's breath follows the mobGriefing gamerule, a ridden one's always burns, and
  never touches its riders, its owner, the owner's tamed animals or teammates.
- **Each one its own** (Andy: "can you slightly differentiate every dragon visually like the heritages?"):
  `DragonLook`, rolled at birth and saved: one of the species' hide shades, long, short or twin horns, now and then a
  lost back spike, and one of the species' glowing marking patterns (an emissive layer with the eyes, as the heritages'
  marks). A species brings its own hides and markings (`textures/entity/dragon/<species>/hide_<n>.png`,
  `marks_<n>.png`, painted by `tools/gen_dragons.py`); the model can reuse Notus's body plan with its own parts.

## Build order
1. **Notus, end to end** (Kingdoms 0.7.137): built, below.
2. **Boreas and Zephyrus** (also 0.7.137): the same body, riding and taming, each with its own shape, breath, home
   and personality. Built, below.
3. **Eurus and Aeolus**: the villagers in disguise (MCA), their questions and reveal, Eurus as a dragon, Aeolus as the
   boss, then eggs and hatchlings.

## What's built (0.7.137, not yet seen in game)
- `dragons/WindDragon` holds what every wind dragon shares (all of the below but the breath, food, bond, home and
  body); a species overrides its hooks (`favourite`, `breathe`, `bondFactor`, `seat`, `cruiseSpeed`, `perchable`...).
  `BoreasDragon` + `DragonFrost` (frost cone; peaks), `ZephyrusDragon` + `DragonAcid` (acid gobs and puddles; perches in
  water, swims). Client: one `WindDragonRenderer`, a model per species, art from `tools/gen_<species>.py`.
- `dragons/NotusDragon` (extends `AbstractHorse`, so it's an `OwnableEntity` with the horse's saddle slot and inventory
  screen, as `WarElephant`), `DragonBreath` (the cone, shared), `DragonLook` (the variation, shared), `Dragons`
  (registration, the rider's `Breathe` packet, the Nether's deltas, `/kingdom dragon [tame]` for ops, the spawn egg).
- Wild: a rare `Encounters` entry (weight 2) in the badlands, and a Nether check of its own for the basalt deltas
  (encounters only run in the Overworld). It circles high round its home, perches on the tallest spot near, and goes
  for players within 22 blocks: swoops, hovers and breathes, or lands to bite and tail-sweep.
- Taming as above (Notus: cooked mutton, Emberborn). Its trust is a value of its own, not Equestrian's `Bond`: Bond is
  built round horses (hunger, thirst, hooves, disciplines) and only kept for the mounts Equestrian covers.
- Tamed: follows, sits (sneak-use, empty hands), defends its owner; riding, flight, stamina and breath as above.
- Client: `NotusDragonModel` (hand-written `ModelPart`s, procedural animation for flying, gliding, hovering to breathe,
  landing, walking, perched, crashed, sitting), `NotusDragonRenderer` with the glowing layer, `DragonsClient` (flight,
  the breath key, the stamina and trust HUD), `DragonDiveInputMixin` (Shift in the air dives instead of dismounting).

## Left for later
- Notus: Kindle not scaring it (a Powers tie-in), whistling it to you, barrel rolls (A A), armour for it, its own
  sounds, eggs.
- Eurus, Aeolus, eggs and hatchlings (phase 3). Boreas: its frost freezing Smoke's smoke into fog (a Powers tie-in).
