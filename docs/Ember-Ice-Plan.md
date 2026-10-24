# Emberborn fire and the Ice power (Powers 0.14 / Kingdoms 0.8)

Andy (2026-10-17): "expand on emberborn, with a left click hold ability, and synergies merging smoke power into fire
somehow" and an ice power whose synergy is with "smoke instead of fire". Built as two parallel tracks; every number
is a `static final` at the top of its class. Open questions are at the end.

## Track A: Emberborn fire (heritage, anyone of that blood)
- **Kindle (hold left mouse, bare-handed, not at a block):** flame gushes from the palm in a short cone (5 blocks;
  starts at 3 and reaches 5 over the first second). Whatever it touches burns (set alight, plus a little fire damage
  each half second); it lights campfires, candles and TNT, melts snow and ice it hits, but never sets blocks alight,
  so nobody's village burns down by accident. It costs hunger while it runs. Like the Frost-born's ice prison it goes
  through `RaceMoves`/`RaceHoldClient`: with a power (other than Smoke, below) it moves to sneak + left hold.
- **Heat:** holding builds heat (a bar over the crosshair, 0 to full in 3 s); it cools when you let go. At full heat
  the flame burns white-hot (more damage). **Let go at full heat** and the gathered heat is thrown as a **fireball**
  that bursts on impact (fire damage and burning in a 2.5-block radius, no block damage; 5 s cooldown).
- **Weather:** chilled (the heritage's cold weakness) the cone is half as long and heat builds half as fast; in water
  or rain the flame only hisses steam.
- **Everyone sees it:** flame and spark particles from the hand, the arm held out (pose), a roar of fire sound.

### Smoke merged into fire (an Emberborn with the Smoke power)
- **Their own body is a heat source** for `SmokeHeat`: their smoke always pours 25% more and sootier.
- **Kindle pours smoke:** the flame leaves sooty smoke behind it, so a long burn fills the room.
- **Flashover:** Kindle (or a fireball) hitting their **own** smoke ignites the whole cloud: a wave of flame races
  through every connected cell of it (fast, a few ticks per block), setting everything inside alight and burning the
  smoke away into a puff of black soot. Smokescreen or a Smoke Bomb, then a flick of flame, is a fire trap.
- **For them, the plain left hold stays Kindle** even though they have a power (Smoke has no left-click hold).

## 0.15.22 (Track A, built)
Built as planned, in `power/ember` (`EmberKindle`, `EmberFireball`, `EmberFlashover`) and
`client/power/ember/EmberKindleClient`; lang `lang/parts/ember.json`. Compiled on Andy's PC. Review fixes: an
Emberborn's flame smoke and flashover ash never take a block of someone else's smoke (so a flashover only burns its
maker's own cloud); if the server puts the flame out while it's held (too hungry), the client stands aside so the click
digs again.
- **Emberborn on the client:** a synced `emberborn` attachment, set from Kingdoms' `jaymoskingdoms_heritage_emberborn`
  tag once a second (anyone, power or not). Kindle's state (`ember_kindle`: running + heat in twentieths) is synced to
  all for the bar and the pose.
- **Input:** through `RaceHoldClient` like the Frost-born's prison: the flame starts after 4 ticks held (a quick click
  stays a punch), the client says "still held" every 5 ticks and the server stops it after 40 without word (a lag spike shouldn't eat the fireball); let go and
  it stops at once. With a power other than Smoke it's sneak + left hold; Smoke keeps the plain hold. Once the flame
  runs it keeps the click at blocks too (digging is held off, `ZFighterAttackMixin`), so you can aim at a campfire.
  A Z Fighter Emberborn: as a Frost-born, Kindle only when nothing is in plain reach (else the hold combo).
- **The flame:** cone 3 → 5 blocks over 1 s (half chilled), stopped by walls, 0.25 wide at the palm + 0.22 a block.
  Every 0.5 s what's in it (in sight of the palm) is set alight 3 s via `core/Flames` and takes 1 fire damage
  (white-hot: 5 s, 2). Every 5 ticks the block it plays on: campfire/candle/candle cake lit, TNT primed (as theirs),
  snow (layer, block, powder) melted away, ice/frosted ice to water. Nothing else, never fire blocks. 0.03 hunger a
  tick, stops at 6 food. Rain or water: steam and a hiss only, no heat. Chilled = cold biome (base temperature
  <= 0.2) or freezing, read by Powers itself (it can't call Kingdoms' `Heritage.chilled`).
- **Heat:** 0 → full in 3 s (6 s chilled), cools in 2 s. White-hot adds white sparks. Let go at full heat: a fireball
  (1.1 b/t, 40 ticks, flies straight), bursting on a creature or block: 2.5-block burst, 6 damage at the middle (3 at
  the edge), burning 5 s, lights/melts the block it hits, fizzles in water. 5 s cooldown (a release in cooldown just
  cools).
- **Smoke merged in:** an Emberborn counts as one heat source of their own in `SmokeHeat` (+25%, sootier); Kindle
  puffs 0.15 (x the power's boost) of soot >= 0.7 smoke every 4 ticks just above the flame's base; the flame (1.5+
  blocks out) or a fireball (in flight or its burst) meeting their own smoke of 1.0+ starts a **flashover**: a ring a
  block further every 2 ticks through connected cells of theirs at 0.3+, up to 400 cells, each burnt cell setting what's
  in it alight 6 s (4 damage once a wave; never the owner), the smoke cleared and 15% of it going up as a black soot
  puff with no owner (so it can't chain). 1 s between flashovers. `SmokeField` only gained three methods at its end.
- **Look:** right arm held out along the look for everyone (`PoseTakeover` counts it); a heat bar over the crosshair
  (above Smoke's bar when they have Smoke): orange "Heat", white "White-hot: let go to throw".

**Check first in game:** (1) as an Emberborn with no power, hold left mouse at air: flame, heat bar, arm out; let go at
full heat for the fireball. (2) Aim at an unlit campfire, a candle, TNT, snow and ice. (3) Another player watching sees
the arm and the flame. (4) With Smoke: Smokescreen, then Kindle into it: the flashover wave and the soot. With a Wind
or Web power: Kindle is sneak + hold. (5) In a snowy biome the cone is short and heat slow; in rain only steam.
(6) A long burn indoors with Smoke fills the ceiling with soot, which can flash over on its own when you aim up into it
(by design: a real flashover; say if it should not).

## Track B: Cryokinesis, the ice power
Its own package `power/cryo` (+ client), lang `lang/parts/cryo.json`, doc `docs/cryo.md`. Keys as every power:
- **R, Ice Slide (the starter):** freeze a lane of ice under and ahead of you and skate on it (sprint to speed up,
  inertia, hunger like Wind's flight; the lane melts behind you after 10 s; water freezes into a bridge).
- **G (hold), Frost Breath:** a cone of cold (8 blocks) that slows, builds freezing (powder-snow freeze) and turns
  water to ice; long enough in it and a creature is frozen stiff.
- **V, Ice Shards:** a fan of five shards that hit and slow.
- **B, Flash Freeze:** what you look at (16 blocks) is shut in ice for 3 s (shorter and far more often than a
  Frost-born's once-a-day prison).
- **X, Ice Wall:** a wall of packed ice (5 wide, 3 high) where you look; it melts after 15 s.
- **Z, Snow mode (toggle):** Frost Breath and Ice Shards turn to snow: they slow and blind but never hurt (for friends
  and villagers).
- **C, Blizzard (the ultimate):** a 20 s whiteout round you (16 blocks): snow falls and lies, everything but you
  slowly freezes, and the white hides you.
- **Frost-born:** freezing never hurts them, the calling comes sooner, and the power is a little stronger in the cold.
- The way in (a calling, `core/Callings`): stand in powder snow or a snowstorm, freezing, for a minute.

### Ice into smoke: freezing fog
- Frost Breath, Ice Shards, Flash Freeze and the Blizzard turn any smoke they touch (anyone's) into **freezing fog**:
  it stops drifting and hangs, glitters pale blue, and everything standing in it slows and freezes; frost (snow
  layers) settles on the ground beneath it. It thaws back into plain smoke after 20 s, or at once if fire reaches it.
- So a Smoke user and an Ice user together make a frozen killing fog; and an Emberborn's flame thaws it.

## Open questions for Andy
1. Should Kindle set blocks on fire too (a real flamethrower), or stay creature-only as above?
2. Name: "Cryokinesis" (beside Chronokinesis), or plain "Ice"?
3. Should the Ice power have fates later (corrupted: a killing cold; ascended: winter's calm), like Smoke's stages?

## 0.15.151: the Flame Avatar (Andy, 2026-10-24)
"An emberborn variant that triggers whenever an emberborn spends too many of its years in the nether, they become
consumed by the flame already within them, turning into an avatar of flame, capable of immense destruction. It should
be seen as a problem for emberborn to control their fire, and this form is the end result of one failing to control."
Built in `power/ember/FlameAvatar` + `AvatarMoves` (+ `mixin/EmberLivingEntityMixin`), client
`client/power/ember/FlameAvatarClient`, lang in `lang/parts/ember.json`. Every number is a constant at the top.
- **The way in:** time an Emberborn spends in the Nether is counted (saved, kept through death); outside it cools at a
  quarter of the rate, so it's long stays that do it. At 7 in-game days (about 2 h 20 min in the Nether) the fire takes
  them: a burst of flame and fires round them, the title "Consumed". Vague warnings at 50%, 75% and 90%; past 75%
  flames lick off them. Ops: `/power avatar <targets> on|off|progress`. Only while they're still Emberborn.
- **Undone (Andy):** going down in water. On the damage tick that would drop them in water (after what the flame
  covers) they revert to plain Emberborn at once (steam, a message); the hit still lands, and the Nether count starts
  over.
- **Flaring body (passive):** 3 hearts at most, whatever adds health. A 20-point flame bar takes hits first (a blow,
  a shot or a blast; not falls, drowning, hunger, the void or fire); a hit flares the body and sets the attacker alight
  if they're within 4 blocks. What the flame can't cover goes to the hearts. Refills: lava 0.5 a tick, fire/lit
  campfire/magma 0.25 a tick, 1.5 a food point eaten; 60% in the cold. Rain drains 0.02 a tick, water 0.25 a tick, and
  in water with the flame out the avatar takes 1 damage a second.
- **R, Fire Fist:** a giant fireball (1.4 b/t) bursting in a 4.5-block blast: 10 damage at the middle (5 at the edge),
  burning 6 s, thrown back, up to 20 fires round it. 4 flame (Andy), 4 s cooldown.
- **G, Flaming Pillar:** tap: a 3.5-block pillar round you; hold (8+ ticks) marks the spot under the cursor (only you
  see it, out to 32 blocks) and letting go raises a 2.5-block pillar there. 6 damage, burning 5 s, thrown up and away,
  up to 10 fires; half the damage dealt comes back as flame. 5 s cooldown.
- **B, Ignition Switch:** tap: everything burnable in an 8-block cone in front is lit: campfires, candles, other
  mods' unlit torches/lanterns/braziers, flammable blocks (up to 12 fires), creatures. 3 flame, 1 s cooldown. Hold:
  everything burning within 16 blocks and in sight (burning creatures, fires; marked for you while held) goes off in a
  small blast (2 blocks, 4 damage, a shove; the fire is spent), 1 flame each, up to 16. 6 s cooldown.
- **Keys:** with no power, plain R G B; beside a power, sneak + R/G/B (V stays the power's). Hush and being downed
  stop them like a power's.
- **Look:** char over the skin and clothes (the face a little thinner), emissive lava veins, a molten heart, burning
  crown and hands, wreathed in vanilla's fire (not round your own first-person camera, not in water); embers and
  smoke, guttering as the flame runs low. Preview: project files `art-overhaul/flame-avatar/flame-avatar-preview.png`.
- **HUD:** right of the hotbar in the chip style (above a power's chips): the flame bar, then R G B with cooldowns.
- No block is ever broken by any of it (fires only).
- **Avatars in the Nether (Andy; Kingdoms 0.7.187, `nether/AvatarCamps`):** MCA villager avatars live in camps of
  their own (scorched ground, a great fire, basalt pillars, a cache) and march in bands of 3 on Hornkin forts and
  piglin camps. They won't talk to anyone who isn't Emberborn, and only turn on players near their own camp (24
  blocks). Same look as the player form.

**Open questions for Andy:** (1) your sentence "the destructive capabilities are limited to the..." was cut off: built
as "limited to these three moves, no block breaking"; (2) is 7 in-game days in the Nether right, and should anything
ever undo it (answered: going down in water); (3) should Fire Fist cost flame too (answered: yes); (4) should the
blasts break blocks.

**Check first in game:** `/power avatar @s on` as an Emberborn; take a hit (flame bar drops, body flares); step into
water (steam, bar drains); R at a wall; G tap among mobs, G hold at a far spot; B tap at an unlit campfire and wood;
set mobs alight, then hold B.
