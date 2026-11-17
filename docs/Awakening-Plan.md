# Plan: Awakening picks (2026-11-17)

Source: The Awakening (awakened 1.23.11), All Rights Reserved: ideas only, our own names, code and art. Pick page:
https://claude.ai/artifact/Cg7Qe6vj8RnoFt567b1LdK (db doc `picks/andy`). Andy ticked 50 of 58 rows.

Not picked: Dense (`aw-grav-dense`), Earthshaper (`aw-geo`), Limitless's other moves (`aw-limitless`), Catalysts
(`aw-catalyst`), Siege colossus (`aw-siege`), Standing challenge (`aw-duel`), Evoker's spire (`aw-spire`), Bosses shrug
off far shots (`aw-deflect`).

## Andy's notes (where each row goes)
- Wind the day: happens the first time a Chrono attunes, then again as the activation of Time Flow.
- Heavy and light: tie into weightlessness (Gravity's own weightless state).
- Airless dome: Smoke can do the same.
- Backdraft: Pyre (the Xenoform fire form) does it as well.
- Carry your water: water sorcerers too, and they can draw it from the moisture in the air.
- Stretching grab: Rubber-folk and the Verdant.
- Speedster: not a new power; it is what Chrono and Velox can do.
- Kinetic charger: wrapped into the Reactor.
- Reaper: added to the death spirit (the Kingdoms Reaper spirit).
- Soul summoner: added to Darktide.
- Regenerator: added to the vampire.
- Green power: added to the Druid.
- Size changer: the Druid's beast forms wrap into it.
- Sun power: the Crowned patron's moves, at first all Dawnborn; Clerics and Paladins can learn it.
- Rule writer: "Something should be working on how this works already." No vault task, thread or code for it was found
  on 2026-11-17, so it is built here from the row.
- Power trials: lore is that you have thoughts of how you can push your limits (inner thoughts, no screen lore).
- Trade powers: done through the fusion earrings.
- The Cordon: a faction of the Ash (the Ash Whisperer's followers) that forbids anything but blindseeing, blood and
  death magic.
- Threat tracker: sold at the Lookout.

## Patches (one branch, a commit per patch)
1. **This morning's calls, Gravity, Chrono, Wind**: `aw-ward`, `aw-decay`, `aw-grav-weight`, `aw-grav-planets`,
   `aw-chrono-day`, `aw-chrono-arrows`, `aw-wind-airless` (and Smoke), `aw-wind-ride`.
2. **Ember, Cryo, Darktide**: `aw-ember-heat`, `aw-ember-backdraft` (and Pyre), `aw-ember-shells`, `aw-cryo-frostbite`,
   `aw-cryo-rampart`, `aw-cryo-encase`, `aw-dark-light`, `aw-dark-pit`, `aw-dark-surf`, `aw-dark-vessels` (and water
   sorcerers).
3. **Web, Arsenal, Z Fighter, Rubber**: `aw-web-types`, `aw-arsenal-loop`, `aw-arsenal-surface`, `aw-arsenal-dome`,
   `aw-z-states`, `aw-z-counter`, `aw-z-rage`, `aw-rubber-reel` (and Verdant).
4. **Folded into what we have**: `aw-velocity` (Chrono and Velox), `aw-reaper` (Reaper spirit), `aw-summoner`
   (Darktide), `aw-regen` (vampire), `aw-green` and `aw-scaler` (Druid), `aw-sun` (Crowned patron, Dawnborn, Clerics,
   Paladins).
5. **New powers**: `aw-nuclear` Reactor with `aw-kinetic` in it, `aw-blinker`, `aw-rules`.
6. **How powers are earned**: `aw-brink`, `aw-trials`, `aw-power`, `aw-trade` (fusion earrings), `aw-relics`, `aw-bone`,
   `aw-combat`.
7. **Bosses and hunters**: `aw-cordon`, `aw-adapt`, `aw-strangers`, `aw-tracker` (Lookout), `aw-share`, `aw-teamtime`.

## As built

### Patch 1: this morning's calls, Gravity, Chrono, Wind
- `aw-ward` **Warding Hush** (Kingdoms `HushWard`). Blows and shots with a hand behind them stop short of you; each point
  of harm stopped costs a feather of stamina (Powers' stamina bar). What the feathers can't pay for gets through (the
  paid share is taken off the blow on the hurt path). Explosions, falls, fire, drowning, hunger and anything with no
  direct striker get through. Who has it: the Lantern Warden's sworn from the third tier (beside Lamp Against the
  Night), and any staff caster from a Scroll of the Warding Hush (a court wizard keeps one; 20 mana, 30 s, longer from a
  strong staff, 40 s wait). Without Powers it is paid in hunger.
- `aw-decay` **Ash rot** (Kingdoms `AshRot`). Decay stacks with no cap; each stack does 0.5 harm a second, all drop 8 s
  after the last new one. The Ash Whisperer's sworn: tier 1, every blow lays a stack; a new **Rot key** (default End,
  Patron keys category) holds the arts: tap at a foe = **Grip** (pins 2.5 s, stacks while held), sneak = **Spread** (a
  12-block strip of rotting ground for 6 s), tier 3 sprint = **Rush** (drives the foe till it meets a wall), tap at
  nothing = **Breach** (rots a 3x3 tunnel 6 deep that closes after 20 s), tier 4 hold 2 s = **Collapse** (a crater 9
  across and 6 deep that grows back over a minute, 5 min wait). The Unseeing's ritual-keepers (Powers
  `UnseeingRites`: blindfolded, gouged or zealous, never a follower who still has their eyes) lay rot with every blow
  (a short withering without Kingdoms), draw blood across the air to heal themselves, and lay death's touch (lingering
  withering).
- `aw-grav-weight` **Heft** (`GravityHeft`). Hold Z to step lighter, sneak+hold Z to step heavier (heavy, normal,
  light, weightless). Light: lower gravity, higher jump, half fall damage. Heavy: knockback resistance, faster fall,
  sinks in water. One step lighter than light is Gravity's own weightless state. Z taps keep their old jobs on release.
- `aw-grav-planets` **Planet hopping** (`GravityPlanets`). Sneak+G throws a small planet up to 50 blocks; you fall to
  it with no fall damage and keep some speed on arrival; throwing the next mid-fall keeps your speed (12-tick wait).
- `aw-chrono-day` **Wind the Day** (`ChronoWindDay`). The first time a Chrono attunes the day winds a full turn
  forward. After that, with Time Flow on, hold Z to sweep the day forward, faster the longer you hold, sneak to wind it
  back. On a server it needs the same share of the other overworld players as sleeping does (the gamerule): they
  agree by crouching or lying in bed; both sides see who is waiting on an overlay.
- `aw-chrono-arrows` **Arrows stop too**: already built. Time Flow's time stop opens a Stasis Field that parks every
  projectile in flight where it is and lets it carry on when time starts; the full time stop holds shots through the
  tick freeze. Nothing added.
- `aw-wind-airless` **Airless Dome** (`AirlessDome`, Wind and Smoke). At master rank with all four abilities: C (sprint+C
  when C is taken by an ascended/corrupted ultimate). A 7-block dome for 12 s: everyone but you loses air and drowns
  at 0 (armour or not; water-breathers are spared), creeper fuses run back, vanilla spellcasting illagers' spells are
  broken off, and Kingdoms casters inside can't cast (hushed). 90 s wait.
- `aw-wind-ride` **Ride your tornado** (`WindRide`). During Cyclone, a Sneak tap steps into the funnel: you ride it along
  your look, carried with whatever it has caught; tap again to step out.

Design calls: Rot arts on one new key so they don't take Patron boon keys; crater and tunnels grow back (no lasting
grief, they wait for anyone standing in them); Heft on Z hold so Z's taps stay; Planets on sneak+G (G alone still casts
the well); Wind the Day's consent is the sleeping rule; Airless Dome needs all four abilities and rank 3.

Lore (new, for Andy to approve in Lore.md):
- (new) **The Warding Hush** is the Lantern Warden's: the dark kept a hand's breadth from his own. Court wizards copied
  it onto scrolls; it costs the body's own breath to hold.
- (new) **Ash rot** is the Ash Whisperer's gift to his followers: what the Ash touches crumbles, earth and flesh both.
  The Unseeing's ritual-keepers learned the same rot, and blood and death rites with it.
- (new) Gravity's heft, Planet hopping, Wind the Day, Airless Dome and the ridden tornado are arts the powers grow
  into, no new people.

### Patch 2: Ember, Cryo, Darktide
- `aw-ember-heat` **Heat** (`EmberHeat`, for every fire user: Emberborn and Pyre). Every fire move cast (Kindle starting,
  the fireball, the fire kit's rays, ring, wall and column, the Flame Avatar's moves, the vent, Backdraft, the shells)
  adds 15 to a 100 gauge; at full the fire kit hits 30% harder (`EmberKindle.seasonal`, `FireKit.hurt`, the Judgement).
  At full, each cast burns a point of your own health (never under 4) to push past the cap, up to 30% more again. It
  cools 5 s after the last cast. Shown on the action bar.
- `aw-ember-backdraft` **Backdraft** (`EmberBackdraft`): sneak as you start Kindle (Emberborn), or sneak+G on the ground
  (Pyre). For 2 s the air and every fire within 12 blocks is drawn in (fire blocks, lit campfires, burning creatures go
  out) and foes are dragged in; then one blast, bigger and harder for every fire swallowed, and the fires it put out
  light again where they were. 20 s wait.
- `aw-ember-shells` **Cluster shells** (`EmberShells`), built for Ember (the row's first choice, not the Arsenal
  battery): let Kindle go at full heat while sneaking and the heat leaves as three lobbed shells; each bursts into six
  bomblets, and each bomblet leaves a patch of burning ground for 5 s (harms and sets alight; no fire blocks are lit).
- `aw-cryo-frostbite` **Frostbite** (`CryoFrostbite`): every ice hit (shards, breath, Flash Freeze, gaze, volley, ball,
  Blizzard, javelin, rampart, and a gifted villager's ice) and every breath of freezing fog (`SmokeField.chill`) lays a
  stack (up to 10). Each second the bitten take 0.25 freezing harm a stack and a slowness that deepens with the stacks;
  stacks hold 5 s after the last, then thaw one a second. What can't freeze is never bitten.
- `aw-cryo-rampart` **Glacier Rampart** (`CryoRampart`): sneak+X with every ability learned. A glacier of packed ice
  surges 20 blocks ahead (3 wide, a block higher every 3 so it's a walkable ramp), freezing every foe it reaches for 4 s
  (Flash Freeze's shell), stands 12 s and shatters. Shares the Ice Wall's cooldown (30 s).
- `aw-cryo-encase` **Encase and javelin**: Flash Freeze now holds 3 s, longer with each Mastery rank up to 5 s, and no
  longer refuses big targets: a boss is frozen through and pinned where it stands (no shell). Sprint+V throws an **Ice
  Javelin** (`CryoJavelin`) that flies dead straight 64 blocks, 9 harm and 3 Frostbite.
- `aw-dark-light` **Darkness feeds it** (`DarktideDark` + `DarktideDarkHurtMixin`): everything a Darktide or Smoke user
  hurts takes 25% more at light 3 and under and 20% less at light 12 and over (block light and the sky as the time of
  day dims it), sliding between. Smoke included (Andy's "if you like").
- `aw-dark-pit` (`DarktidePit`): sprint+B **Devouring Pit**, a pit of shadow on the ground up to 28 blocks out, drags
  foes in and swallows them (held, blind, 2 harm a second) for 4 s, 8 s where the ground lies in full dark; spat out
  when it closes. Sprint+V **Strangling Lift**, the foe you look at is lifted 2.5 blocks by a hand of shadow and choked
  for 3 s: drowning harm, and while it hangs it can't use powers (Powers silencer for players, Kingdoms `Will.hushed`
  for casters, an illager's spell falls apart).
- `aw-dark-surf` (`DarktideSurf`): sprint+G **Surf**, ride a crest of dark water along your look, 0.45 b/t over land,
  0.75 over water or in rain, knocking aside whoever's in the way (1 Flow a second); sprint+G again or Sneak steps off.
  Sprint+R **Geyser**, under the foe you look at (throws it up, 4 harm) or under yourself to ride up and land soft.
- `aw-dark-vessels` **Carry your water** (`DarktideVessels`, `compat/KitchenWaterskins`): Surf over dry land and the
  Geyser need water; near water or in rain it's free, otherwise it comes from what you carry: a flask (a bottle of
  Unpurified Water, 25), a canteen (a sip of a Kitchen waterskin, 15), a reservoir (a water bucket, 100). With none it is
  wrung out of Flow. In the rain or standing in water all Darktide harm is 25% stronger. **Water Sorcerers** (Kingdoms
  `Absorption`): hold J looking down into your hands to pour in what you carry (a sip, a bottle, a bucket), or with
  nothing carried to draw a little water out of the air (more in warm wet lands, none where it never rains).

Design calls: Heat is its own gauge (Running Hot stays the blows' gauge). Backdraft relights only the fires it put out.
New Darktide moves are on sprint+key (sneak+key is taken by the sea picks). The wet 25% applies to all of Darktide's
harm, since its moves mix water and dark. The water sorcerer's pour is on looking down so it never steals the blast
hold (which starts looking at nothing). Flask, canteen and reservoir are the bottle, Kitchen's waterskin and the bucket,
not new items.

Lore (new): no new people. Backdraft and the shells are the Emberborn's fire; the pit and the lift are the Drowned
Dark's; the water sorcerer's moisture-drawing is the water element's.

### Patch 3: Web, Arsenal, Z Fighter, Rubber
- `aw-web-types` **Web cartridges** (`WebKinds`, `WebShooters`, `WebBall`): a cartridge holds one kind of web fluid and
  the Web Shot (the tapped pellet and the charged shot) from that wrist is that kind. **Sticky** is the plain cartridge,
  the web as it was (each pellet hit stacks, five in a row webs them up). **Concussive** (brewed with gunpowder): no web,
  a thump that hurts 1.4x, throws back everything within 2.5 blocks and leaves them reeling (nausea, slowness).
  **Taser** (copper and redstone): no web, 3 harm on top, shocked still for 1.5 s, and the charge jumps to the nearest
  foe within 5 blocks. **Splitter** (flint): the charged shot leaves as three, fanned 8 degrees. A cartridge of another
  kind loads into the main hand's shooter, swapping out the old one with what's left in it. The bomb and the suit's
  mass stay plain web. Recipes take raw ingredients (2 string, a slime ball, a glass bottle, plus the kind's), never a
  used cartridge. Each kind has its own tint and name.
- `aw-arsenal-loop` **Endless Fall** (`ArsenalFall`), the Void Arsenal's sprint+V (the gates are the corrupted
  Arsenal's): a gate opens in the floor under the foe you look at (24 blocks) and another up to 7 blocks over it (lower
  under a roof, never under 2.5); it falls through and out of the top again 8 times, 2.5 harm each pass through the
  floor, its fall forgiven each pass. What no portal will take (the dragon, the wither) isn't taken. 25 s wait.
- `aw-arsenal-surface` **Surface** (`ArsenalSurface`), sprint+G: a rift straight up and you step out at the highest safe
  spot above you (sturdy footing, room to stand, no fire, lava, water, magma, powder snow, berry bush, wither rose or
  web). Under a closed roof (the Nether), the highest safe spot below the roof. 60 s wait.
- `aw-arsenal-dome` **Rift Seal** (`ArsenalSeal`, our own name and look: the world inside folded off into a pocket,
  walled in deep void with a pale violet rim): from the last rank of training (Paragon), C, or sprint+C once ascended
  (whose C stays the Hundred Blades). Hold C and the edge spreads from 9 to 20 blocks; let go and it closes. For up to
  60 s nobody inside can leave: at the wall they're thrown back, and whoever gets out anyway (a pearl, a gate, a portal)
  is pulled back in. Every player and creature inside but you has no powers: power keys refused (through the
  Eight-Sign Circle's silencer), Kingdoms casters hushed (`Will`, tag `jaymospowers_sealed`) and forms sealed, a gifted
  villager's power asleep, an illager's spell broken. It drains you every second: hunger, then 1 health a second once
  you're at 3 drumsticks. C again lets it go; it ends if you die or lose the power. 3 min wait.
- `aw-z-states` **Airborne, flat, pinned** (`ZFighterStates`, on the Z Fighter's hurt hook, every creature): a Z
  Fighter's harm, or a boxer's punch, on a body in the air (not one that flies of itself), flat (knocked out, lying,
  or swept: the leg sweep's trip) or pinned (staggered, held, shocked or webbed still) is 5% harder if the same move put
  it there and 10% if another did; each state-hit in a row within 2 s adds 2.5%, up to 15% (so 25% at most). A move is
  the boxer's punch, the hold combo's blow, or else the kind of harm and what carried it.
- `aw-z-counter` **Full Counter** (`ZFighterFullCounter`): raise the guard with Sneak held and for half a second
  whatever hits you, from any side, goes back and you take none: a blow at 2x on the striker, an arrow back at the
  archer (the bracers' send-back), any other shot its harm on whoever loosed it, a blast or spell or anything else half
  on whoever did it. Harm nobody did (falls, fire, drowning) isn't turned. 6 s wait. A counter is never countered.
- `aw-z-rage` **Battle fury** (`ZFighterFury`): every blow a Z Fighter lands adds 4% and every one he takes 3%; he grows
  with it, up to 35% bigger, 5 more hearts and 30% harder blows at full. Out of the fight 5 s and it cools, full to
  none in 30 s. At full fury his grab (Sneak + left click) is the Great Ape's: he takes hold of the foe and pounds it into
  the ground four times (each slam 0.6 of his fists, wears its poise, cracks the ground), and the fury is spent. The
  gauge shows on the action bar. Not as the Great Ape, who keeps his own rage (`ApeRage`). The old Rage (the pre-Super
  beating fury) is unchanged.
- `aw-rubber-reel` **Stretching grab** (`ZFighterReel`): the arm shoots out up to 8 blocks to the foe you look toward
  (or the nearest close round your look), bends over or round what's in the way, hooks it and yanks it to your feet:
  a drawn bow or raised shield lowered, a creeper's fuse undone, an illager's spell broken, its steps stopped, its poise
  worn, slowed a second. A Rubber-folk or Verdant Z Fighter: sprint+R. Any Kingdoms Rubber-folk or Verdant: the
  heritage key while sprinting (`Heritage.gift` calls it through `PowersCompat.reel`). Everyone sees the Verdant
  combo's stretched arm. 4 s wait.

Design calls: the Arsenal's new moves are on sprint+key, like Darktide's. Endless Fall stays on the Void Arsenal, as
the row names its gates. The Rift Seal holds its owner in too (they can let it go), and borrows the Eight-Sign Circle's
silencer path so Kingdoms forms are sealed with no new Kingdoms reflection. The fury is a new meter beside the old Rage,
which is a once-before-Super burst and stays as it was; the pound is the existing grab at full fury. The state bonus is
read at the moment of the hit (what state the body is in and which move last landed on it), not tracked as flags.

Gaps: the stretching grab for Kingdoms heritage players runs in Jaymo's Powers, so without Powers installed the
sprinting heritage key does what it always did. It cuts off vanilla windups, illager spells and item use, not a
Kingdoms folk caster's spell already being cast (Kingdoms keeps that state to itself). The Rift Seal pulls back a
player who leaves its dimension; a creature that leaves it is let go.

Lore (new): the web fluids are the Web Slinger's own brews. Endless Fall, Surface and the Rift Seal are the Pocket
Arsenal's rift turned on the world (the Seal is what a master of the pocket does: folds a piece of the world into one).
Battle fury is the Warrior-born's blood that the Great Ape is made of, showing in any Z Fighter pushed hard enough. The
stretching grab is the Rubber-folk's and the Verdant's stretch, as in the Verdant combo.
