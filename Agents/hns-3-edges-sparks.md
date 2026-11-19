---
status: running
task: Hackers 'n Slashers patch 3 (edges and sparks)
branch: claude/hns-3-edges-sparks
with: hns-1 crits, hns-4 shove/procs agents (parallel)
pr: 
mods: powers, kingdoms
versions: 
started: 2026-11-19
owns:
  - src/main/java/com/jaymospowers/combat/Edge.java (new)
  - src/main/java/com/jaymospowers/combat/MetalHits.java
  - src/main/java/com/jaymospowers/combat/BladeSounds.java (hit sounds only)
  - tools/gen_blade_sounds.py (new functions only)
  - kingdoms/src/main/java/com/jaymoskingdoms/Whetting.java (new)
---
# Hackers 'n Slashers patch 3: edges and sparks

**Task:** hs_stone, hs_dull (+ smiths sharpening guards' blades), hs_kindle, hs_armour_ring.

## Plan
- Powers: Edge (dullness on the blade's custom data, damage mixin, grindstone sharpening), stone sparks, kindling, armour sounds.
- Kingdoms: Whetting (smith at grindstone sharpens a guard's blade).

## Notes
- 
