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

## 0.14.0 (Track A, built)
Built as planned, in `power/ember` (`EmberKindle`, `EmberFireball`, `EmberFlashover`) and
`client/power/ember/EmberKindleClient`; lang `lang/parts/ember.json`. Not compiled in the cloud (no Fabric maven).
- **Emberborn on the client:** a synced `emberborn` attachment, set from Kingdoms' `jaymoskingdoms_heritage_emberborn`
  tag once a second (anyone, power or not). Kindle's state (`ember_kindle`: running + heat in twentieths) is synced to
  all for the bar and the pose.
- **Input:** through `RaceHoldClient` like the Frost-born's prison: the flame starts after 4 ticks held (a quick click
  stays a punch), the client says "still held" every 5 ticks and the server stops it after 15 without word; let go and
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
