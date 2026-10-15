# Smoke

Andy asked (2026-10-14) for a Smoke power whose logic is built on a smoke mod from AsianHalfSquat's
"Top 10 Minecraft Mods (26.2)" video (youtube.com/watch?v=suH-0zIiLU4); he then named the mod: **Advanced Chimneys**
(EnderLanky, CC BY-ND 4.0: no derivatives, so none of its code or art is used). It ships unchanged in the pack (with
ForgeEndertech), and the Smoke power's gas is our own after its rules: smoke that collides, follows the wind, grows with
heat sources, changes with what it passes through, and rides its chimneys (`ChimneyCompat`, block ids only).

## The gas (`power/smoke/SmokeField`)
- Server side, per dimension: a sparse map of block → density (1 = can't see through it; cap 3) and who made it.
  Not saved. Stepped every 2 ticks into fresh maps:
  - **rise**: 20% climbs into the block above (so it pools under ceilings and fills rooms top-down);
  - **spread**: 10% of the difference flows to each thinner side neighbour, 3.5% downward;
  - **leak**: full blocks stop it, fluids kill it, part blocks (doors, slabs, fences, bars) pass 25%, leaves 50%;
  - **drift**: under the open sky a wandering wind (0.3 clear, 0.7 rain, 1.0 storm) carries up to 12% downwind;
  - **fade**: 0.3% + a flat 0.004 a step, +0.007 under the sky, +0.035 in the rain; past 24000 cells it fades 3x.
- **path**: through fire it comes out 15% thicker and sootier, through leaves cleaner; each block keeps a soot
  value (0 pale grey to 1 black) the client colours by. Inside Advanced Chimneys' chimneys, ducts, pipes and pumps
  60% is drawn up each step and it only moves along them or out of a vent.
- **heat** (`SmokeHeat`): a Smoke user with fires, lava, magma, lit campfires or furnaces within 3 blocks pours 25%
  more per source (up to 2x), and sootier.
- `puff` adds smoke and spills what a block can't hold outward (breadth first), so a bomb is a cloud at once.
- `blow` pushes smoke; Wind users flying faster than 0.6 b/t scatter it.
- Sight: `blocksSight` samples the line between two points every half block; 1.6 in all (or the far end buried in
  1.0+) blocks it, never within 3 blocks. `SmokeTargetMixin` (Mob.setTarget) refuses hidden targets and every
  10 ticks mobs drop targets they can't see.
- Sync: every 4 ticks each player gets the cells within 48 blocks (5 bytes a cell, at most 6000), one empty packet
  when there's none left.

## The client (`client/power/smoke`)
- `SmokeFieldClient` eases each block between the last two updates and draws 1-2 camera-facing puffs per block
  from `textures/entity/smoke/puff.png` (made by `tools/gen_smoke.py`), slowly turning and bobbing, lit by the
  world's light, sorted (`entityTranslucent`). Since 0.10.81 it's drawn much thicker (Andy: "way thicker so it is
  visible more easily"): 1-3 puffs a block (2 from 0.6, 3 from 1.8), alpha 0.22 + 0.35 a unit (up to 0.85), bigger
  puffs, and your own smoke at 50%. Your head in someone else's smoke greys the screen (full at 1.6).
- `SmokePowerClient`: R G V B chips right of the hotbar; a bar over the crosshair for pour / smother / cruise speed.

## Thicker smoke (0.10.81)
Every ability pours more: Smokescreen 2.0 a tick (was 0.9), Smoke Bomb 130 / 190 mastered (was 60 / 90), Smother
1.1 a tick on the target (was 0.5), Smoke Jet 1.5 a tick (was 0.6), and the gas fades a little slower.

## Vapour Form flight (0.10.81)
Andy: "smoke's G vapour form should turn everything below your waist into smoke, giving you a powerful flight mode
similar to wind's", and it moved from G to R so it's the first ability (Smokescreen moved to G). R toggles
(`SmokeVapour`); the synced `smoke_vapour` attachment is the form. The client flies it (`SmokeFlightClient`, built like
Z Fighter's: hover, Sprint cruise to 2.2 b/t (1.3 starter), dive speed-up to 1.25x, S brake, speed-limited turns)
and takes off when the form starts. The server ends it on R, Sneak on the ground, 30 ticks grounded, 6 food or
`WindFlight.canFly` failing; shots pass through, falls don't hurt. `SmokeFlightPose` hides the legs (and pants,
through `Dismembered.hidePart`, so a shared model is restored once drawn) and the leggings/boots, pours the smoke tail
for everyone, and makes WindPose lay a fast flier out flat. The old 4 s invisibility, Speed II, Slow Falling and "mobs
lose you" are gone (the trail hides you instead).

## No armour in Vapour Form (0.10.102)
Andy: "smoke cannot wear any armor when immaterial. it stays equipped until you do." Read as: in Vapour Form you
can't put armour on; what you already wear stays on until you take it off yourself. `SmokeArmourMixin` makes
`LivingEntity.isEquippableInSlot` refuse armour slots (inventory clicks, shift-clicks, hotbar keys) and
`canEquipWithDispenser` refuse armour; `SmokeEquipMixin` refuses right-click equipping (`Equippable.swapWithEquipmentSlot`).
As a catch-all (creative inventory, other mods) `SmokeVapour.tick` hands back anything that lands in an armour slot
that was empty the tick before. Each refusal shows "Armour won't go on smoke." Vapour Form is the only immaterial state.

## Smoke Jet (0.10.67)
Hold X (`power/smoke/SmokeJet`): a jet of smoke along your look, 14 blocks, widening 0.14 a block. Each tick it lays
0.6 of gas along the way (more toward the end) and blows the smoke in its way on; it gives what it catches a gentle
shove; 0.025 hunger a tick, stops at 6 food. While it runs the player carries the tag `jaymospowers_jet:smoke`, which
Kingdoms' ships read to fill their sails (half a Wind user's Wind Whistle). Drawn by `client/power/smoke/SmokeJetClient`.

## The rest of the power
Plan: `/mnt/project-files/smoke/Smoke-Plan.md` (Andy, 2026-10-15): base kit, Smog (corrupted), Cloudshaper (ascended:
harness clouds, stop tornadoes building and weaken them), Ash Storm (both: kill or create a tornado).

## Stage 1: the base kit finished (0.10.110)
- **Smoke Sight, Z** (`SmokeSight`, client `SmokeSightClient` + `SmokeSightGlowMixin`/`SmokeSightOutlineMixin`): a
  toggle (`smoke_sight` attachment, synced to the user) for anyone past the starter. Creatures within 32 blocks with
  0.5+ smoke (anyone's) at their feet or eyes are outlined through walls on the user's screen only, pale grey, near
  black for `Enemy`. Your own smoke is drawn at 15% instead of 50%. 0.004 hunger a tick.
- **Smoke Step, Sneak + X** (`SmokeStep`; the jet's Hold packet with Sneak held): along your look (32 blocks, through
  walls) the first block holding 1.0+ of your own smoke; you come out there (or a block below/above, wherever you fit).
  Puffs at both ends. Cooldown 6 s (4 s mastered), its own `SmokeState.STEP` slot.
- **Wholly smoke** (Andy: "unlocked at max mastery, vapour flight makes you completely intangible while sprint flying,
  meaning your whole body becomes smoke"): fully mastered, cruising in Vapour Form tells the server (`SmokeVapour.Whole`
  packet) and it sets `smoke_wholly` (synced to all). While it holds: all damage refused but `bypasses_invulnerability`;
  no attacks (`AttackEntityCallback`) and no G/V/B/X/Step; `SmokeIntangibleMixin` (shots pass), `SmokePushMixin` (no
  pushing either way), `SmokePoseMixin` (swimming-pose box, so 1-block holes fit), `SmokeSeepMixin` (both sides: any
  block whose collision isn't a full cube has none for them: doors, fences, bars, panes, trapdoors, slabs). The body,
  armour, held items, cape and parrots aren't drawn; a body-sized cloud streams instead and the trail is thicker.
  Stopping the cruise brings the body back.
- **Bank** (mastered Smokescreen): while pouring and 20 s after, the user's smoke (by owner) ignores the wind and fades
  3x slower (`SmokeField.bank`).
