---
status: running
task: Rig patch 6: senses, tech override and nova - Active camouflage, tech override, ore callouts, hex visor, nova
branch: claude/rig-beetle-6-senses-nova
with:
pr:
mods: powers
versions:
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/rig/RigCloak.java (new)
  - src/main/java/com/jaymospowers/rig/RigOverride.java (new)
  - src/main/java/com/jaymospowers/rig/RigNova.java (new)
  - changes/powers/rig-beetle-6-senses-nova.md
---
# Rig patch 6: senses, tech override and nova

**Task:** Implement five new Rig actives as menu entries: Active camouflage (se-camo), Tech override (se-override), Ore callouts (se-ore), Hex visor (se-visor), Nova (hl-nova). Each with charge cost, poses, and sounds.

## Plan
- Create RigCloak.java for active camouflage (builds on RigVisibility)
- Create RigOverride.java for tech override (golems, doors, androids/mechs)
- Create RigNova.java for nova panic button
- Add ore callouts to RigScan (passive ping while digging)
- Add hex visor effect to RigHelmView (closing wipe + hex grid)
- Add new actions to RigNet, RigPicks, RigMenuScreen
- Register new classes in RigPicks.init()
- Write lore paragraph for tech override
- Build and test

## Notes
- Camo: timed cloak, shimmers, mobs lose you, first hit is ambush (more damage)
- Tech override: iron/clockwork golems freeze, redstone doors open, Frostborn androids/mechs stutter (needs lore)
- Ore callouts: passive quiet ping when digging near ore, extends RigScan
- Hex visor: short closing wipe + faint hex grid at edges in RigHelmView
- Nova: all charge spent, everything near thrown back, breaks grab

## Done
What a player will see, and what wasn't tested.