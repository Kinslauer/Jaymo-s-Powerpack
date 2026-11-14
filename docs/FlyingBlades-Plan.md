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
  earthboard. Asked Andy to confirm before patch 7.

## Keys

| Move | Arsenal | Void Arsenal |
| --- | --- | --- |
| Call / dismiss the blades | short hold Sneak + V | short hold Sneak + X |
| Change formation | tap Sneak + V | tap Sneak + X |
| Order wheel (patch 2) | keep holding the same key | same |
| Harry / steer (patch 2) | Sneak + B tap / hold | same |
| Whirl (patch 2) | on the wheel | on the wheel |
| Blade Storm (patch 3) | hold V (a tap is still Barrage) | on the wheel |
| Blade riding (patch 4) | double tap Jump | same |

The Void Arsenal differs because its Sneak + V already steps through gates.

## Patches (one PR each, in order; the thread branch holds one open PR at a time)

1. **Attendant Blades** (`power/arsenal/ArsenalWing`, client `ArsenalWingRenderer`): the wing, 2/4/6 blades by
   mastery, three formations, strike with my swing, stand guard, unmastered blades picking their own fights, HUD pips,
   rift tethers. Picks: wing, count, shapes, sortie, sentry, hud, trails (the tether half).
2. **Orders**: harry, steer by sight, whirl, ward (through `Guard`), spare mark, order wheel, hand a weapon to the wing,
   your own weapons can fly (never lost, like the Divine-born's `FlyingSword`).
3. **Blade Storm**: hold V, looping flight on `RiftBlade` (used by wing strikes too), each blade finds its own foe, the
   wing feeds the storm, echo blades, trail ribbon and slash flash. The holder is slowed while it runs.
4. **Blade riding**: double tap Jump, fight from the blade, step off and send it, riding with broken legs (`LimbUse`
   wobble). Until mastered the ridden blade can go home at random (with a short warning shake).
5. **Big moves**: Closing Ring, step to a blade, wing combos (Kingdoms' Weapon Forms engine through `PowersCompat`), the
   wing joining Hundred Blades and the Sword of Damocles.
6. **Look and who else**: the Void wing (corruption per strike, Endermen notice), guns on the wing, relic gifts carry
   over, working hands (tools dig), gifted villagers get two.
7. **Gravity's rocks**: the same orders for `GravityRocks` (formation, strike, harry, steer, whirl, ward, storm, ring),
   no riding. Waits on Andy confirming the reading above.

## Checks for each patch

- Compiles with Gradle (`compileClientJava`).
- Lang keys present for every new message; change note in `changes/powers/`.
- Lore: the placement paragraph in `Lore.md` covers the whole plan (blades the rift holds, still on loan).

## Open questions

- Which "rocks" Andy means (see above).
- Wing combos need Kingdoms; without Kingdoms loaded they are simply absent. Fine?
