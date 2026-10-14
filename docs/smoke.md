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
  - **fade**: 0.4% + a flat 0.004 a step, +0.01 under the sky, +0.035 in the rain; past 24000 cells it fades 3x.
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
  world's light, sorted (`entityTranslucent`). Your own smoke is drawn at 30%. Your head in someone else's smoke
  greys the screen (full at 1.6).
- `SmokePowerClient`: R G V B chips right of the hotbar; a bar over the crosshair for pour / smother / vapour.

## Smoke Jet (0.10.67)
Hold X (`power/smoke/SmokeJet`): a jet of smoke along your look, 14 blocks, widening 0.14 a block. Each tick it lays
0.6 of gas along the way (more toward the end) and blows the smoke in its way on; it gives what it catches a gentle
shove; 0.025 hunger a tick, stops at 6 food. While it runs the player carries the tag `jaymospowers_jet:smoke`, which
Kingdoms' ships read to fill their sails (half a Wind user's Gale Stream). Drawn by `client/power/smoke/SmokeJetClient`.

## Stage 2 (proposed, not built)
Z Smoke Sight, X Smoke Step, C Pyroclasm (ascended), corrupted Smog (toxic smoke), cure at Y 200+ in clean air,
Vapour Form through 1-block gaps.
