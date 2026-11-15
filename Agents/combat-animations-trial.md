---
status: working
task: Andy (Claude app, 2026-11-15): Epic Fight style combat animations for players, humanoid mobs and bosses
branch: claude/combat-animations-trial
with: 
pr: 
mods: Powers, Kingdoms
versions: 
started: 2026-11-15
owns:
  - docs/CombatAnimations-Plan.md
---
# combat-animations-trial

**Task:** Andy: "I want you to work on it here, with fable. And eventually i want all humanoid mobs and bosses and
players, im not sure if blender or block bench give better results so we will do a trial of both, and i definitely want
as much visible in first person as possible." Follows the research in `epic-fight-animation-research` (hermes-b's note,
and the fuller write-up from source in the Claude project: "Epic Fight animation research").

## Plan
- Patch 1, the trial: the same three longsword moves (a guard stance, a first cut, a heavy overhead) made two ways and
  switchable in game, so Andy can judge them side by side in first and third person.
  - Blockbench route: keyframes on the blocky player rig with bending elbows and knees, played through Player
    Animation Library on players and `PalMobAnimator` on mobs.
  - Blender route: a rigged, weighted body of our own drawn in place of the blocky one, the way Epic Fight does it
    (our own code and art; Epic Fight is GPL-3 code with all-rights-reserved assets, ideas only).
- Later patches, after Andy picks a route: weapon stances (idle, walk, run per weapon), hit reactions, dodge and guard,
  every humanoid mob, the bosses, first-person body and camera.
- Timing is already ours (`MobCombat`, `AttackTells`, `HitStop`, `Duellists`, the parry): this work is the motion on
  top of it, not a second combat system.

## Notes
- Andy answered (2026-11-15): Claude animates, with Blender run headless by script; keep the blocky body (no weighted
  mesh) on both routes; longsword. So the Blender route is the blocky rig keyed in Blender and exported, not a new body.
- The new cloud session builds: JDK 25 (Temurin, /opt/jdk25) and `./gradlew build` pass; Blender 5.3 at /opt/blender-5.3.0-linux-x64.
  Nothing can be played from here: Andy judges the two routes in game.
- No new thing in the world, so no new lore. Movesets follow the schools' styles already in Lore.md (Still Water for
  the longsword trial).

## Done
Nothing built yet.
