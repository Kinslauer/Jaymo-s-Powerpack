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
  nothing = **Breach** (rots a 3x3 tunnel 6 deep that closes after 20 s), tier 4 hold 2 s = **Collapse** (a crater 19
  across, radius 9 and 6 deep that grows back over a minute, 5 min wait). The Unseeing's ritual-keepers (Powers
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

### Patch 4: folded into what we have
Seven picked rows that Andy said to fold into things already in the pack. Nothing here is a new power. All of it builds;
none of it has been played.

**Keys.** The four Kingdoms rows that had no key left share one new scheme, **the art keys** (`ArtKeys`, client
`ArtKeysClient`; their own heading "Jaymo's Arts" in Controls, rebindable): **Soul** (numpad 1), **Claw** (numpad 2),
**Wild** (numpad 3), **Sun** (numpad 4). Each is read the way the Rot key is: the server gets the press and the release
and reads a **tap**, a **sneak + tap**, a **sprint + tap**, a **hold** (let go after half a second) or a **sneak + hold**.
No existing key was taken. The Powers rows use what the power already had (the wheel, use, Z, C).

- `aw-velocity` **Speedster → Chrono's Overclock and Velox's Velocity** (`ChronoMomentum`, `ChronoTunnel`,
  `ChronoShotPassMixin`; both speedsters get all of it, since both already run the same code).
  **Momentum in gears**: the wheel no longer puts you in a gear, it sets your **top gear**; a run starts in gear 1 and
  sprinting at speed carries you up a gear every 0.4 s to the top gear, and you lose one every half second off speed. The
  chip underlines the top gear. **Phase through arrows**: every shot passes through a speedster in gear 5 at speed (a
  "blur"), and through a Chrono holding the phase. Phasing through walls is the held phase Chrono already had.
  **Hurl static**: running charges you (faster in higher gears, a violet bar under the chip); use with an empty hand and
  nothing to catch or snatch throws all of it as a bolt down your sight (10 harm at a full charge, 24 blocks, a daze),
  arcing on to two more creatures within 5 blocks at half. **Slow the world while you keep your speed** was already
  built (`ChronoSlowTime`, both speedsters); nothing was added. **The tunnel**: a blur that runs at a wall (a real wall,
  not a step) opens a straight tunnel to the first clear spot past it, at most 10 blocks on; it stays open 8 s and
  anyone standing in its line walks through. No block is touched: the phase's own collision rule lets a player in the
  line through, floor and unbreakable blocks excepted. Step out of the line inside the wall, or be in it when it closes,
  and you are put at the nearer mouth.
  My calls: momentum climbs to the gear the wheel sets, not always to 5, so a player who wants gear 3 still gets gear 3
  (the price is a short ramp from a standing start, 0.8 s to gear 3). Velox opens tunnels too although it has no held
  phase, so the row is shared. Gaps: only **players** can follow through a tunnel (the collision rule is for players;
  a creature would need its own); a tunnel never goes through bedrock or barriers.
- `aw-reaper` **Reaper → the Kingdoms Reaper spirit** (`SoulArts`, `SoulDriftMixin`; Soul key).
  **Souls** are banked for good: 1 a kill, 10 for a great foe (100 health or more), twice that with the scythe in hand;
  a keeper who had already paid Toll starts with 5 souls for each point. **The tier** runs from 0 to 1000 souls: 5
  hearts and half damage at none, 20 hearts and six times the damage at full, on every blow the keeper lands.
  **Wisps** (tap): three homing wisps, 3 harm each. **Brand** (sneak + tap): the branded takes a quarter more from you
  for 30 s, is worth a soul more, and when it dies the brand jumps to the nearest foe within 10 blocks. **Chain**
  (sprint + tap): 2 harm and 1 healing a second for 5 s, on a steady beat. **Drift** (hold the key down): you drift
  through walls for up to 4 s, the floor still holding; ending in stone puts you at the nearest clear spot. **The Soul
  Scythe** (sneak + hold, empty hand): needs 500 souls banked; its blows reap half the harm to every foe within 3
  blocks, and its kills bank twice.
  My calls: souls are never spent, since the row calls them a permanent tier, so the scythe needs 500 banked rather than
  costing 500. The scythe is Jaymo's Powers' netherite war scythe where that mod is in the pack (its art), else a
  netherite hoe; no new art. **Balance, for Andy**: a new keeper is weaker than a keeper was before this patch (5
  hearts, half damage), and a full one is far stronger; that is the row as written. The numbers are at the top of
  `SoulArts`.
- `aw-summoner` **Soul summoner → Darktide's Necromancy** (`DarktideSouls`, `DarktideTide`, client
  `DarktideSoulScreen`). **Kills give souls**: while corrupted, what you or your risen shadows kill gives its shadow up
  at once; the corpse it leaves is marked so the same shadow is not taken from it again. **Stamina to keep**: every
  risen shadow wears at your feathers; run dry and the newest sinks back among your shadows. **Gone for good if they
  die**: a shadow that melts because the corruption lifted, you left, or you ran dry now goes back into your list
  (before this patch it was lost); only one that dies is gone. **The soul menu** (sprint + Z): the shadows you hold;
  click up to three as your **wave**, and a tap of C raises all of the wave that is not up yet (no wave set: the last
  shadow taken, as before). **Possess** (Z at a risen shadow of yours): your body is left standing as a mannequin in
  your skin and you ride in the shadow for up to 20 s: you see from it, forward and back walk it, left and right turn
  it, jump jumps, sprint runs, and it strikes what stands in front of it. Sneak puts you back in your body. **Warp**:
  sneak with sprint held and your body comes to the shadow instead. A blow to the body throws you back into it and
  lands on you.
  My calls and gaps: villagers and anything with 100 health or more still have to be taken from the body by hand (a
  villager's shadow is the villager itself, which only the body holds). While riding, **the mouse does not look**: the
  game's own camera on another body ignores it, so the shadow is steered with the move keys, and it strikes by itself
  rather than on a click. Riding uses the game's spectator camera (your game mode is put back when it ends, and at the
  next login if the server died in between). No stamina is worn while riding (20 s at most, then a 30 s wait).
- `aw-regen` **Regenerator → the vampire** (`ClawArts`; Claw key; vampires and dhampirs). **You never stop healing**:
  half a heart every 2 s, four times slower when the sun or a holy thing has you weak, twice as fast in a frenzy.
  **Claws bleed, and the bleeding heals you**: an empty-handed blow lays a wound (up to 8; half a point a second each,
  on a steady beat; they close 8 s after the last), and half of what bleeds out heals you within 24 blocks. Skeletons
  and golems do not bleed. **Rake** (tap): three slashes, twice the harm on a pinned foe. **Rend** (sneak + tap): 7
  harm and two wounds; a foe at a quarter health or less is killed outright (never one with 100 health or more).
  **Pounce** (sprint + tap): you fly at a foe up to 14 blocks off and **pin** it under you for 2.5 s. **Roar of frenzy**
  (hold): everything within 8 blocks is frightened, and for 12 s you are stronger, faster, heal twice as fast and your
  claws lay two wounds. 90 s wait.
- `aw-green` **Green power → the Druid** (`GreenArts`; Wild key, out of a shape; two tree perks). **Greenwarden** (level
  3): tap at a foe, **ivy** roots it 4 s; tap at a friend, a **vine gives** them your health; tap at a block, the vine
  **grapples** you to it; sneak + tap at a foe, the vine **drains** it into you. **Daylight heals you** (under the open
  sky by day) and **fire hurts half again as much**. **Bloom and Root** (level 8): sneak + tap with no foe in sight, a
  **patch of sunflowers** heals your side for 20 s (twice as fast in the day's sun); sprint + tap at a friend (or no one,
  for yourself), a **cocoon of roots**: 5 s in which nothing harms them, they cannot move, and they mend.
  Gap: the sunflower patch is drawn (a pool of light and sunflower petals), not real sunflower blocks: real ones would
  be free flowers and dye from every cast, and would have to be planted in other people's land.
- `aw-scaler` **Size changer → the Druid's beast forms** (`SizeArts`; Wild key, in a shape; two tree perks). **Seven
  Sizes** (level 5, 3 ranks): in a shape, tap grows the beast a size and sneak + tap shrinks it, speck (a tenth) to
  colossus (four times); each rank opens one more size each way. Health, blows, step and reach go with the size. **Small**
  (the two smallest): use a creature at least twice your height to **ride it**; what hunted you loses you. **Big**:
  sprint + tap **stomps**, hold **grabs** a smaller creature into your hand and hold again throws it. **Wax and Wane**
  (level 12), out of a shape: hold the key at a creature and a **ray shrinks** it a size, sneak + hold **grows** it, for
  a minute (never a creature with 100 health or more). **The jar**: a glass bottle used on a creature shrunk as far as
  it goes holds it; use the jar on a block to let it out.
  My calls: the sizes are worn on the wild shape (Andy: "wrap druid into this with his beast forms"), so a Druid out of
  a shape has the green arts on the same key and the sizes in one. No new beast models: the Druid's own stand-in beast
  is scaled. People are not put in jars (no players, no villagers), nor another's pet. Gap: the ray gives others five
  sizes, not seven (a quarter to two and three quarter times).
- `aw-sun` **Sun power → the Crowned Sun's arts** (`SunArts`; Sun key). Every **Dawnborn** has them; a **Cleric** or a
  **Paladin** learns them in the tree (level 10, 3 points). **Stronger the higher the sun**: under the open sky the arts
  run from full strength at the horizon to half again at noon, and your own blows up to a fifth harder; with no sun the
  arts are at 0.7 and your blows are only your own. **Ascension**: blows landed and taken (and standing in the sun) fill
  you with heat; each 100 is a step, **four** of them, and at each you grow a tenth, hit harder and your arts burn
  hotter; it ebbs 10 s after the fighting stops. **Sun orb** (tap), **sunfire pillar** (sneak + tap at a spot: a warning,
  then four beats of fire), **pin and pummel** (sprint + tap at a foe within 5 blocks: held fast, five blows, the last
  throws it), **a sun of your own** (hold: a small sun overhead for 2 minutes, light to fight by, and it counts as a
  sun where the real one cannot be seen). **The Zenith Axe** (sneak + hold with an axe in hand and a nether star in the
  other): the star is forged into the axe; with it in hand the ascension has a **fifth** step.
  My calls: any Cleric or Paladin can learn them whatever their patron, **except the Ash Whisperer's sworn**, who are
  refused (Dawnborn too): the Sun is her rival. Say if it should need the Sun's own oath. The personal sun's light is
  the game's invisible light block, moved with you and taken away when it goes out.

**Class trees go over 30 points.** New perks: Druid +9 points (Greenwarden 2, Bloom and Root 2, Seven Sizes 3 ranks at
2, Wax and Wane 2), Cleric +3 and Paladin +3 (the Sun's Arts). They sit in the two outer columns of each tree. Nobody can
buy everything in those trees now; say if the level-ups should give more points or the perks should cost less.

Lore (new): no new people or gods. **The Reaper's souls**: death keeps count, and what its keeper sends it stays sent;
the keeper is thin as a shade when the count is low and terrible when it is high, and the scythe is the Reaper's own,
lent to one who has filled it. **The Darktide's shadows** are the Drowned Dark's: what its servant kills goes down into
the dark water at once, and the servant can go down after it and look out through its eyes, leaving the body on the
shore. **The vampire's claws**: the blood that is their curse is also what mends them, and it answers to any blood
they spill. **The green arts and the sizes** are the groves': a Druid who can wear a beast can ask the green to hold,
give and take, and can wear the beast at any size the grove has ever grown one. **The Sun's arts** are the Crowned
Sun's own, in the blood of the Dawnborn, its bound people, and taught by its priests and its sworn swords; the Ash
Whisperer's people cannot be taught them. **The speedsters**: Chrono's static is his own time rubbing against the
world's, and the tunnel is the stretch of the world he has already run through, a moment ahead of everyone else.

### Patch 5: new powers (Reactor Heart, Blinker, Lawgiver)
- `aw-nuclear` with `aw-kinetic` folded in: **Reactor Heart** (`power/reactor`, lang `reactor.json`). One charge
  (0-100) fills from running (0.15 a block), landing from 3+ blocks (1.5 a block fallen) and being hurt (3 a point of
  harm). Full, it **detonates** round you (5 blocks, 12 harm at the middle) and you take none of it; none of your own
  blasts hurt you and none breaks a block. **Z, the dial**: each move pours in a quarter, a half or all of the charge (never
  under its own least), and hits harder for it (0.5 + spent / 40). **R Cone Burst** (the starter, at half strength): a
  punched wave of air and heat in a 6-block cone. **G Blast Dash** along the look; **Sneak + G Recoil**: blast in front,
  thrown backwards. **V Shell Beam**: 48 blocks, bursting where it hits; **Sneak + V Charged Throw**: one of the held
  stack thrown to burst where it lands. **B (hold) the Drill**: stone, deepslate, netherrack, dirt, sand, gravel, clay,
  sandstone and end stone come out as their items (as an iron pickaxe would), 1 charge a block; never an ore, never a
  made block, never where you may not build. **Sneak + B Ground Crack**: an 8-block split that harms and pops foes up.
  **C (ascended, hold 5 s) the Nuke**: all the charge in a 14-block blast (up to 30 harm), then 30 s of fallout 10
  blocks wide (1 harm a second and poison to everyone but you). Calling: live through a blast of 6+ harm (a day off, once
  a day; in Chrono's Future at once).
- `aw-blinker` **Blinker** (`power/blinker`, lang `blinker.json`). **R Blink** 12 blocks (the starter: 6), short of
  walls, untouchable for half a second (all harm refused but what bypasses invulnerability). **G Blink Strike**: behind
  a foe up to 18 blocks off, 1.5x fists + 2. **V Vanish**: blinding smoke 5 blocks round (blindness 4 s, mobs lose you),
  invisible 5 s, blink up to 5 blocks back. **B Sky Drop**: a foe within 6 blocks and you blink 14 up, held a moment,
  then you blink home while it falls (not what no portal takes; refused under a roof). **C (ascended) Flicker**: 2 s, a
  blow every 4 ticks (five a second) on a random foe within 12, untouchable throughout. Calling: land 16 ender pearls
  (an End-born, 8). Awakens at once.
- `aw-rules` **Lawgiver** (`power/rules`, lang `rules.json`, the rulebook screen `RuleBookScreen`). Up to five rules,
  each a sentence of four parts picked in the rulebook: **who** (I, my allies near me, the foes near me, the one I look
  at, everyone near me; near is 12 blocks), **what** (mend, wither, swift, slowed, strong, weak, hardened, float, burn,
  have no powers, glow, blind), **strength** 1-5 (held to 1 + abilities learned) and **length** (5, 15, 30, 60 s).
  Enacting costs **ink** (what x strength x (seconds / 5)^0.6 x who: 1 self or one, 1.5 allies, 2 foes, 2.5 everyone;
  150 ink most, 1 back a second, 2 with no rule running). One rule at a time; three at mastery 9 (abilities learned +
  rank = 9). Area rules find who they're about each second. "Have no powers" is the Eight-Sign silencer (players),
  Kingdoms `Will` (tag `jaymospowers_ruled_mute`) and forms, and a gifted villager's power. Keys: **R** enact the rule
  picked (the starter's first rulebook: "I mend, strength 1, 15 s"), **G** the rulebook, **V** pick (Sneak: back),
  **B** repeal (half the unspent ink back). Calling: lay a book you wrote and signed on a lectern (a day off, once a day).

Design calls: "Reactor" is already a Z Fighter android form, so the power is the Reactor Heart. The kinetic charger's
fill (moving, landing, being hit) is the Reactor's heat: one gauge, not two. Its blasts are hand-rolled so the maker
takes none and no block breaks (a nuke that levels builds would be griefing). The drill's "never builds" is held by
what it takes (the natural ground kinds); a stone block a player placed looks the same and is taken. The Lawgiver's
"mastery 9" is abilities learned (4) plus rank (5). "Have no powers" written about yourself silences you too, until
it runs out. Each new power is fated (fate can hand it out) and has its own calling.

Lore (new): the **Reactor Heart** is the ruined future's (Chrono's Future): the reactors that powered its cities and
burned them down, one beating in a person. The **Blinker** is the End's own step, the endermen's; the End-born wake to
it soonest. The **Lawgiver** is the Judge's gift (the afterlife's Judge, who rules on the dead): a living hand that
writes law, and for a while the world keeps it.

### Patch 6: how powers are earned
- `aw-brink` **The brink** (`core/Callings`). A powerless survival player brought to 4 health or less by any harm, and
  still alive, takes a day off the awakening wait, once a game day, never below 5 minutes (6000 ticks): it shortens the
  3-5 day wait without skipping it and claims no power (any calling still decides which one). An italic overlay says
  something in them woke a little.
- `aw-trials` **Power trials** (`core/trials/PowerTrials`, the H menu's **Trials** tab and a pinned line on the HUD,
  `client/trials/TrialsScreen`, lang `trials.json`). Every power has the same 16-rung ladder in its own words: use each of
  its four abilities (by their names: 10, 10, 15, 15 times), land 25 blows, fell 10 and 40 hostile creatures, fell 10 and
  30 with the power itself, fell a great foe (100+ health or a Kingdoms boss), take 30 harm and live, go below Y -40,
  above Y 200, into the Nether and the End, and travel 3000 blocks. Each finished trial is 25 mastery points (all 16: 400,
  nearly the 420 that unlocks every ability), beside mastery by use. Three are open at once in ladder order; one whose
  ability isn't learned yet waits and opens the moment it is. Pin one (it shows on the top-left rail), or swap one to the
  back of the ladder for one of 3 swaps; finishing any quest (a Kingdoms quest, by reflection, or a Z Fighter master's job)
  gives a swap back. A new power starts a new ladder. The prompts are the holder's own thoughts, as italic overlays ("I
  could push further... Fell a great foe"), never chat.
- `aw-power` **Ability Power** (Kingdoms `Stats.Stat.POWER` on the K screen's Attributes tab, shown to those with a Powers
  power; Powers `core/AbilityPower`). A ninth attribute, 0-100 like the rest (the world tier's cap, the same points). It
  works on what a power does while it acts (its keys and its own tick): +0.3% harm a point (100: +30%) from the power's
  moves to anyone but the maker, +0.2% reach a point (+20%) where a move picks its target by aim (`PickKit.aimed`), +0.25%
  longer a point (+25%) on effects a move lays on someone else. Health and stamina stay where they were (hearts are
  Hearty Meals', stamina the Feathers), so only Ability Power is new.
- `aw-trade` **Trading powers** through the Bonding Earrings (`core/PowerTrade`). Two players with a power and an earring in
  hand, within 5 blocks, facing: sneak and use the earring at the other. Each must say yes twice (the offer and its answer,
  then once more to be sure), each yes within 15 s of the last. Then both earrings crack and the powers change hands;
  mastery starts again from the first ability, fate (ascended, corrupted) starts fresh, and a new trial ladder begins. Any
  two powers, not only Z Fighters; the earrings' fusion (plain use) is unchanged.
- `aw-relics` **The relic belt** (`relics/Relics`, `RelicBeltItem`, `RelicItem`, lang `relics.json`). A belt-slot bauble
  (leather, string and a gold ingot) with 4 pouches, loaded like a bundle. Seven relics, levels 1-3: **Heartseed** +2 most
  health a level, **Wind Knot** +1 stamina feather, **Focus Stone** +5% harm from your power's moves (on top of Ability
  Power), always; **Hale Coin** +1 attack while at 80% health or more; **Last Ember** +3 armour at 30% health or less; **Tide
  Scale** +20% swimming speed in water; **Stride Spur** +5% speed while sprinting. Click a relic onto its twin (same
  level) to merge them a level up. Every boss leaves its own relic when it falls: the Warden a Heartseed, the Wither a
  Last Ember, the Ender Dragon a Focus Stone, the Elder Guardian a Tide Scale, and each Kingdoms boss-bar mob the same kind
  every time (by its type).
- `aw-bone` **Boneiron** (`bonebond/Boneiron`, `BondingVatBlock`, `BoneironPlateItem`, lang `bonebond.json`). A rare ore in
  deepslate between Y -64 and -40 (veins of up to 3 in about one chunk in three; iron pickaxe), smelted to boneiron
  ingots. The **Bonding Vat** (a cauldron, two ingots and a bone block) takes up to 6 ingots in its brine. A fast healer
  climbs in and settles (sneaking) for 10 s, taking 1 harm a second, and their bones take the next tier (tier n costs n+1
  ingots), up to 5. Each tier is +2 armour, +1 toughness, +1 attack and +0.2 knockback, kept through death. Fast healers:
  Kingdoms' Verdant, vampires and werewolves (`Heritage.healsFast`), and a Z Fighter of the Verdant line; anyone else is
  refused. Or plate armour: a **boneiron plate** (three ingots in a row) clicked onto any piece of armour, up to 3 a piece,
  each worn plating +0.5 armour, +0.25 toughness, +0.25 attack and +0.05 knockback.
- `aw-combat` **Combat mode** (Kingdoms `FormsClient`, key F4 under Combat Arts). Our R G V B keys and the class key already
  fire at once, and Spell Engine's skill bar fires on its own keys; the one thing that only picks is the Weapon Forms belt
  (Ctrl and scroll pick a slot, the Combat Art key fires it). With combat mode on, in stance, the number keys 1-5 fire
  that belt slot's form at once instead of changing the hotbar slot; the belt strip edges every slot red while it is on.
  It stays until toggled off, on that client.

Design calls and gaps: "Ability Power raises every ability in its own way" is three levers that reach every power's moves
(harm, aimed reach, effect length), not a hand-tuned rule per ability: a move's size or number of targets is its own code and isn't read from a shared place, so those two aren't raised. Harm from a power's projectile or a lingering field
counts only when it lands inside the power's own tick or key (most do; a vanilla arrow or snowball it threw does not).
Without Kingdoms there is no Ability Power (only the Focus Stone). The trials are one shared ladder shape filled with each
power's own ability names, not 16 hand-written feats per power. The bonding is done crouched in the vat, not lying down
(the game has no lying pose outside a bed). Plating is done by clicking a plate onto armour, not at the Tinkering forge:
the forge works only its own fixed set of molten metals, and adding boneiron to it would mean a new molten metal through
the smeltery, casts and forging tables. Relics need no bond (they're worn in the belt, not as baubles of their own).

Lore (new): the **trials** are nobody's but the holder's: thoughts that come to someone with a power of how far they could
push it, and the push itself is the training. **The brink**: whatever sleeps in the powerless stirs when death comes
close, and comes a little sooner after. The **Bonding Earrings** bind two souls for the fusion; held out without the dance,
the bond carries what woke in each across to the other, and cracks. A **relic** is what a great creature's death leaves
hardened behind, a knot of the strength that kept it up; worn close, a little of it goes into the wearer. **Boneiron**:
in the deepest deepslate, where the world's oldest bones lie pressed into the stone, the iron has taken something of
them; it takes to living bone the way other metals take to fire, if the body can mend round it fast enough (the Verdant,
the vampires, the wolfblooded).

### Patch 7: bosses and hunters
- `aw-cordon` **The Cordon** (Kingdoms `Cordon`, `CordonFolk`, client `CordonRenderer`, lang `awakening.json`; skins and the
  summons drawn by `tools/gen_awakening_assets.py`). A sect of the Ash Whisperer's faithful who hunt the awakened. They hunt
  anyone holding a Jaymo's Powers power, except a Resonance holder who can't see (eyes gone or a blindfold on: blindseeing),
  and anyone working sanctioned magic: a Mage, Cleric, Druid or Paladin, a Warlock of any patron but the Reaper, a Sorcerer
  of any element but death and blood. Blood and death mages, blindseers and the artless are left alone. Every 2 minutes
  each such player in the Overworld's wild (never in a realm's land) has a 12% chance (+4% a world tier, no oftener than
  every 10 minutes) of a patrol arriving 26-40 blocks off with a bell and a shout: 2 sentinels (+1 a tier, up to 5); from
  the Borderlands a **stalker** (invisible until within 6 blocks of its quarry or struck; its knife lays 2 Rot), from the
  Wildlands an **enforcer** (mail; each blow binds the struck one's power and forms for 5 s, through Powers' seal,
  `EightSignCircle.sealFor`), from the Dread an **Overseer** at its head (once at half health it rallies the Cordon within
  16 blocks: Strength for 20 s). **Sentinels** carry spears. The Overseer always carries a **Cordon summons**: read (used)
  in the Overworld, it becomes a filled map (biome preview, a target X) to the **Court of Judgment** 400-700 blocks off. The
  court rises when a player comes within 48 blocks: a round floor of polished blackstone bricks (radius 11) with the air
  cleared above, eight basalt pillars with soul lanterns, the judge's dais on the north side, the Arbiter on it and two
  sentinels at its feet. Courts are kept on the Overworld (marked, raised, judged).
- `aw-adapt` **The Arbiter** (`CordonFolk`, the ARBITER rank; a boss bar through `BossBars`, a head taller, 320 health). It
  learns the harm dealt it by kind (fire, shot, blunt, blade, magic and powers, beasts: the same kinds a Bane learns,
  `CaptainWays.cause`): each kind is taken less and less as more of it lands, down to a quarter once 60 of it has been
  dealt, and it says so when it has a kind ("Your blade is known to me."). What it soaks builds up; at 80 it **overloads**:
  a burst of binding red (8 harm and a throw back to everything within 6 blocks), then 5 s staggered (it can't strike, it
  barely moves, it takes double harm) and it forgets all it had learned. Its blows bind the power for 3 s and lay a Rot.
  Changing what you hit it with keeps it from learning; keeping to one thing makes it overload sooner.
- `aw-strangers` **Awakened strangers** (Kingdoms `AwakenedStrangers`). Every minute each player with a power in the
  Overworld's wild has a 5% chance (no oftener than once a game day) of a stranger walking up from 22-34 blocks off: an MCA
  villager (a guard, so armed; a plain villager without MCA) given a random giftable power as an open gift of Jaymo's
  Powers' gifted folk, fighting boldly, named with their mastery's title: "<name>, Adept of <power>" or "<name>, Master of
  <power>" (a master 15% of the time, +10% a world tier, with the gifted masters' faster moves, signature and ultimate).
  Within 14 blocks they call the player out (a speech bubble), then fight: their power's moves through the gifted folk's
  AI, and blows by hand. At a quarter of their health they yield ("I yield. I've more road to walk yet.") and walk off; so
  they do when the quarry is gone or dead, or after 5 minutes; gone a little later.
- `aw-tracker` **The threat tracker** (Powers `tracker/ThreatTracker`). Sold by the Keeper on the Lookout for 30 silver: in
  his talk with MCA, or (as the old trader) by sneaking and clicking him empty-handed. A brass case whose needle turns to
  the nearest elite or boss within 96 blocks (blighted creatures, Kingdoms' marked monsters with a bar, the vanilla four
  and Kingdoms' boss-bar mobs), sixteen needle turns drawn on our own case, picked by the game's own compass angle to the
  item's lodestone tracker (set every second, untracked, so no lodestone is needed); with nothing near it spins. Held, the
  action bar says what it points at and how far.
- `aw-share` **Who did what** (Powers `core/BossShare`, client `client/boss/BossShareCard`). The harm each player deals a
  boss (the vanilla four and Kingdoms' boss-bar mobs: by hand, shot or power, whatever names them; the killing blow counts
  only what was left) is tallied; when it falls, everyone who fought it gets a card on the top-left rail for 10 s: the
  boss's name, then up to six fighters, most harm first, each with their share and a bar (yours in gold, and always on the
  card).
- `aw-teamtime` **Your side gets caught too** (Powers `core/TeamTime`, `TeamTimeMixin`, `TeamTimeFrozenMixin`, Chrono's
  `StasisField`). When a power's move, while it acts, lays Slowness or Freezing on an enemy player, or sets frost on them,
  every one of the caster's teammates within 8 blocks of that player gets the same. Teammates: one Kingdoms party
  (`Parties.allied`, by reflection) or one scoreboard team. An enemy player caught in a Chrono's Stasis Field pulls the
  Chrono's teammates within 8 blocks of the field's edge into it too. Time Stop (every player but the Chrono) and Slow Time
  (every player near) already caught the Chrono's own side.

Design calls and gaps: the Cordon hunts players only, not the gifted villagers who carry powers (they'd need a whole war
between NPCs); the court is built by code where the map says, not a structure file, and is raised once (an Arbiter that
has fallen doesn't come back; a second summons marks a second court). The adapting construct is the Arbiter, the Cordon's
boss, not a remnant construct. The strangers' "mastery title" is two ranks (Adept and Master of their power), not a title
per power, and they come only to players who hold a power (they seek their own kind). The tracker's elites are the two
kinds of elite there are (Powers' blighted creatures and Kingdoms' marked monsters); with MCA the Keeper sells it in his
talk, without it on a sneaking click. The share card credits harm that names a player (a tamed wolf's or a summoned
creature's bites don't). Team time covers players' powers only (a gifted villager's slow isn't caught), and only what is
laid while the power acts (its keys and its own tick), as Ability Power does.

Lore (new): **The Cordon** are a sect among the Ash Whisperer's faithful. Her people keep secrets, and the awakening is a
light shone on everything: a power is seen from far off, and what is seen can be watched. So the Cordon draw a line round
the arts and hunt whoever steps over it: only what is worked in the dark is let be, the blind who see by sound, and blood
and death magic, the arts every crown hangs men for and so the arts that keep their mouths shut. They wear ash-grey, and
the black band of the blindfold over their eyes, the devout's rite turned to a uniform; they lay her rot. Their
**enforcers** bind a gift with a blood-knot that holds a while. Their **Overseers** carry the summons: a letter sealed in
binding red that, read, shows the way to a **Court of Judgment**, where **the Arbiter** sits, the sect's judge, bound in
blood magic to learn every blow struck at it; it judges by learning, until it has learned more than the binding can
hold. **Awakened strangers** are gifted folk who left home to see what their gift is worth, and test it on the others
they meet who carry one; they take a title on the road, Adept or Master, and most of them yield before it costs them
their life. The **threat tracker** is the Keeper's: "the wind up here carries everything, and a little of it is in that
needle", and it turns to whatever great thing is near.

### After the row check (independent audit of every built row against the code)
All 43 rows of patches 1-3 and 5-7 have code a player can reach; none of the unpicked rows is built.

Fixed: an awakened stranger with no MCA (a plain villager) crashed the server on its first blow (no attack attribute);
it now strikes with a fist. The four silencing marks (Airless Dome, Drowning Pit, Rift Seal, a Lawgiver's rule) were
saved with the entity and never came off after a logout, an unload or a crash; anything entering the world now enters
unmarked (`core/StaleTags`). Rot and Frostbite timed their hurt from the last stack, so a Grip or fast hits kept
pushing it back and they never hurt: each now has a steady beat. Rotted holes grow back through water, lava or grass
that came in (only a block someone put there is left). The boss share card never counted the felling blow, and showed
nothing for a boss felled in one: the last blow now counts what was left. The first day-winding asked the other players
for ever; it waits a minute and is then let go. The Court of Judgment is never marked in a realm's land and is not
raised over ground with a chest, bed, furnace or the like on it. Heft is dropped on death. The kill-with-your-power
trial counts only your own power.

Not as the rows say, or worth Andy's eye:
- **`aw-grav-planets`**: each planet thrown costs a little hunger (0.6 exhaustion) and none can be thrown at 1 food
  bar or less, like the other Gravity moves. Arriving keeps 35% of your speed; a throw mid-fall keeps all of it.
- **`aw-trials`**: one shared 16-step ladder with each power's own ability names, not a hand-written ladder per power.
  The trials screen has one how-to line ("Your own thoughts on how to push your limits...").
- **`aw-power`**: Ability Power raises harm, aimed reach and effect length; not size or number of targets.
- **`aw-bone`**: bonding is crouching in the vat; plating is a plate clicked onto armour, not at the Tinkering forge.
- **`aw-combat`**: combat mode covers the Weapon Forms belt only.
- **`aw-relics`**: the four vanilla bosses have fixed relics; other bosses give one of the seven by kind of boss. Wind
  Knot, Hale Coin and Stride Spur only come from those other bosses.
- **`aw-cordon`**: a court whose spot turns out to be built on is not raised, so that summons' map leads to nothing.
- **`aw-decay`**: a crash while a rotted hole is open leaves it open (they are closed on a normal stop).
