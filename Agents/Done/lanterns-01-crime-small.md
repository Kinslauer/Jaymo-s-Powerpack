---
status: done
task: lanterns-01-crime-small
branch: claude/lanterns-01-crime-small
with: Claude Code (PC)
pr: 1751
mods: kingdoms
versions: 
started: 2026-11-18
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Theft.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Suspicion.java
  - kingdoms/src/main/java/com/jaymoskingdoms/ChestNoise.java (wakeToHear)
  - kingdoms/src/main/java/com/jaymoskingdoms/Approaches.java (suspected made package-visible)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/crime-watch.json
  - changes/kingdoms/lanterns-01-crime-small.md
---
# lanterns-01-crime-small

**Task:** Lanterns patch 1: a witness wakes a sleeping housemate and tells them; a suspect who isn't wanted yet gets a guard or golem watching them from a distance.

## Plan
- Theft#witnesses: witness wakes nearest sleeper of the faction and passes it on.
- Suspicion guard watch: spread from vampires/werewolves to crime hearsay and light crimes.

## Notes
- No PC build (memory: PC runs only the game); CI is the compile check.

## Done
Guards and villagers who see a crime wake the nearest sleeper of their faction, who comes over and remembers it as hearsay. Guards who know of a recent crime you aren't wanted for watch you, and one follows at a distance (a golem does it if no guard is near). CI build passes; not played.
- 2026-11-18 19:07 PR #1751 is merged into master; moved to Agents/Done.
