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
