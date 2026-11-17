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
