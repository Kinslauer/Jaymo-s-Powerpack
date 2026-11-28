---
status: running
task: Frostborn troopers of the future as MCA villagers of the Frostborn race
branch: frostborn-troopers-mca
with: claude
pr: 
mods: powers, kingdoms
versions: 
started: 2026-11-28
owns:
  - src/main/java/com/jaymospowers/power/chrono/future/FutureTrooper*.java
  - src/main/java/com/jaymospowers/power/chrono/future/FutureMachines.java
  - src/main/java/com/jaymospowers/power/chrono/future/FutureRig.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ZFighterFolk.java
---
# Frostborn troopers as MCA people

**Task:** Andy (clip "Frostborn zombie bug"): "this frostborn is a zombie instead of being an actual MCA frostborn from the future". Every trooper (squads, Rig pilots) is an MCA villager of the Frostborn heritage, keeping the Rig, palm blast, thruster fall, hostility, squad alert, three-sided war and Rig Salvage.

## Plan
- Kingdoms: ZFighterFolk.trooper spawns an MCA villager (hurtable, Frostborn, Powers drives it).
- Powers: FutureTroopers drives MCA troopers by tick; every trooper check goes through one predicate; old FutureTrooper stays as the body without MCA/Kingdoms.

## Notes
- No T3 (Codex/Gemini) tools in this session; Claude wrote it.

## Done
