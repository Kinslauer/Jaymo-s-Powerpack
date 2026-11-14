# Flying Blades plan (Arsenal, then Gravity's rocks)

Andy, 2026-11-14: linked Yujian Craft and The Zenith, "arsenal should be able to do something like this". He picked 34
of 36 rows on the pick page (https://claude.ai/artifact/FdTQFbbhv8615nRy6t3QnC).

- **Yujian Craft** (forThousands, MIT, Forge 1.20.1 / NeoForge 1.21.1): six swords in formation, orders, sword riding,
  a sword array, combos. No Fabric 26.3 build. Anything adapted from its source keeps its MIT notice in the file.
- **The Zenith** (First_sight, All Rights Reserved, Terraria's sword): hold use for a stream of swords looping through
  the aim point. Ideas only.

## Andy's notes on the picks

- Attendant Blades "should be activated with a short hold".
- Blade riding "can disappear randomly until mastered".
- Blade Storm: "you should be slowed" while it runs (he did not pick "move freely").
- Not picked: the relic that storms by itself.
- "Anything the user can do with swords should be doable with the rocks as well. Except flying." Read as: a Gravity
  user's harnessed rocks (`GravityRocks`) take the same orders, and riding is left out because Gravity already has the
  earthboard. Andy chose "Both" (2026-11-14): Gravity's rocks get the orders, and an Arsenal user can also put loose
  rocks on the wing (patch 2) and storm with them (patch 3).

## Keys

| Move | Arsenal | Void Arsenal |
| --- | --- | --- |
| Call the blades | short hold Sneak + V | short hold Sneak + X |
| Change formation | tap Sneak + V | tap Sneak + X |
| Order wheel (formations, Whirl, spare, take a blade, send home) | short hold of the same key with the blades out | same |
| Harry / steer | Sneak + B tap / hold, with the blades out | same |
| Hand the wing what you hold | G with the blades out | same |
| Closing Ring, Step to a blade | on the order wheel | same |
| Blade Storm | hold V (a tap is still Barrage, on letting go); also on the wheel | on the wheel |
| Blade riding (step on, and step off and send it) | double tap Jump | same |
| Gravity: the rocks' order wheel (Harry, Steer, Stone storm, Closing ring) | hold B+G with the rocks out (a tap changes formation) | n/a |

The Void Arsenal differs because its Sneak + V already steps through gates.

## Patches (one PR each, in order; the thread branch holds one open PR at a time)

1. **Attendant Blades** (`power/arsenal/ArsenalWing`, client `ArsenalWingRenderer`): the wing, 2/4/6 blades by
   mastery, three formations, strike with my swing, stand guard, unmastered blades picking their own fights, HUD pips,
   rift tethers. Picks: wing, count, shapes, sortie, sentry, hud, trails (the tether half).
2. **Orders** (built): harry, steer by sight, whirl, ward (a guard raised, `Guard.raised`), spare mark, order wheel
   (`ArsenalWingScreen`), hand a weapon to the wing, your own weapons and loose rocks can fly (the wing is saved with the
   player, so they are never lost).
3. **Blade Storm** (built, `ArsenalStorm` + `ArsenalStormRenderer`): hold V. The loops are a shared formula
   (`ArsenalStorm.place`), not entities, so they follow the holder's aim. Each loop is spread to one side so a storm
   covers a crowd; the wing feeds the storm; own weapons and rocks on the wing take turns in it; echo blades ignore walls;
   ribbon and flash through `MagicFx`. The holder is slowed. Not done here: wing strikes still fly straight (`RiftBlade`).
4. **Blade riding** (built, `ArsenalRide` + client `ArsenalRideClient`, `ArsenalRideRenderer`): double tap Jump. The
   rider's client flies it the way they look; hands stay free, so they fight from the blade and the wing still takes its
   orders. Double tap Jump again steps off and sends the blade on as one heavy hit. Broken legs wobble it (`LimbMotion`).
   Until every ability is unlocked it can go home early, after a short warning shake. The stance is the earthboard's
   (`GravityBoardPose`) with the arms left free.
5. **Big moves** (built, `ArsenalWingMoves`): Closing Ring and Step to a blade are two more slices of the order wheel;
   wing combos are a five-hit chain counted in Powers by itself (Weapon Forms has no combo counter to share, so nothing
   here needs Kingdoms): the Fan's chain is thrust, thrust, cross, cross, every blade; the Halo's and Flank's is thrust,
   sweep, cross, sweep, every blade. The wing joins Hundred Blades and the Sword of Damocles (`ArsenalWingMoves.join`).
6. **Look and who else** (built, `ArsenalWingKit`): guns and bows on the wing fire from where they hang (a gun once,
   then home); tools on a steered wing dig the block looked at; a relic on a flying blade keeps its on-cut gift
   (`RelicWeapons.flyingCut`); Endermen near a Void wing turn on its holder (a Void holder is already fully corrupted,
   so there is no meter left to add to); gifted Arsenal villagers keep two blades at their shoulders in a fight, strike
   with them and ward with them (`ArsenalGift`). Not done: the Sword in the Stone's gift on the wing.
7. **Gravity's rocks** (built, `GravityRockOrders`): the harnessed rocks (`GravityRocks`) take the blades' orders, no
   riding. With the rocks out, B+G held opens their wheel: Harry (the rocks go at whoever you look at, one after
   another, until called off), Steer (they follow your eye and batter what they meet), Stone storm (they loop through
   your aim point, and you are slowed) and Closing ring (they ring a foe, hold, then crash in). A B+G tap still changes
   the formation, and with a guard raised a rock is spent to take a hit. Formation, strike and whirl were already the
   rocks' own ball, orbit and hurl.

## Checks for each patch

- Compiles with Gradle (`compileClientJava`).
- Lang keys present for every new message; change note in `changes/powers/`.
- Lore: the placement paragraph in `Lore.md` covers the whole plan (blades the rift holds, still on loan).

## Open questions

- (Settled in patch 5: wing combos do not need Kingdoms.)
