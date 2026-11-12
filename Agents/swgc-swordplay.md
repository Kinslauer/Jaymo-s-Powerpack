---
status: in-review
task: SWGC swordplay picks (23 rows), patch 1 of 6: the armed body
branch: claude/project-thread-4d9bz2
with: Claude (cloud)
pr: 1341
mods: powers
versions: 
started: 2026-11-12
owns:
  - src/client/java/com/jaymospowers/client/combat/ArmedPose.java
  - src/client/java/com/jaymospowers/client/combat/ParryPose.java
  - src/main/java/com/jaymospowers/combat/Armed.java
---
# SWGC swordplay

**Task:** Andy linked SWGC (Star Wars: Galaxy in a Cube, ARR, 1.20.1) to improve our swordplay with its animations, and
ticked all 23 rows of the picklist (https://claude.ai/artifact/3oaLD4gmgTxVPeUt1X5juG). Ideas only: our own keyframes,
names and sounds.

## Plan
- Patch 1 (Powers): armed stance/walk/run, draw flourish, armed crouch, mid-air dodge flip, parry poses.
- Patch 2: Wheel Cut, Figure Eight, blade throw, swatting shots.
- Patch 3 (Kingdoms): seven blade styles, styled guards and duelists.
- Patch 4: two-blade set, Twinblade (Warrior-born design, schematic in the Stormborn Tomb), Mill Charge, guards at attention.
- Patch 5: cut marks that are real wounds (bandages heal them), swing and clash sounds.
- Patch 6: reel and tear for Lift (grabs anything), orders screen, great-helm view, title splashes, energy blade.

## Notes
- Andy's row notes are in Decisions.md (2026-11-12).

## Done
Patch 1 (PR #1341): armed stance, draw, armed crouch, mid-air flip, parry lines. Compiled, not played; pose angles are a
first pass. Patches 2-6 not started.

- 2026-11-12: patch 2 pushed to the same branch/PR (#1341): `combat/Swat`, `combat/BladeThrow`, `Armed.CUT`, cut poses
  in `ArmedPose`, Darktide swat; Kingdoms `forms/SpecialCuts` (wheel_cut, figure_eight, hurled_blade), bridge methods in
  `PowersCompat`, blade school arts + `ART_STANDING`. Flip lift gated by `Dodge.flipLift`. Compiled, not played.
