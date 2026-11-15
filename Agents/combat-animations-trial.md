---
status: blocked
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
- Blocked on Andy, three questions asked in the thread: who animates (a person in the tools, or keyframes by script as
  `tools/combat_animations.py` does today); whether the Blender route should replace blocky limbs with a smooth
  weighted body; whether longsword is the right trial weapon.
- This cloud session cannot build: no JDK 25 and Gradle cannot be downloaded here. Code goes up as a PR and the PR
  checks compile it. Nothing can be played from here.
- No new thing in the world, so no new lore. Movesets follow the schools' styles already in Lore.md (Still Water for
  the longsword trial).

## Done
Nothing built yet.
