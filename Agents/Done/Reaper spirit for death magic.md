---
status: done
task: 
branch: claude/project-thread-elf776
with: Claude (cloud thread "Add the Reaper spirit for death magic")
pr: 1526
mods: kingdoms
versions: 
started: 2026-11-14
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/Reaper.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/ReaperClient.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/McaReaper.java
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/reaper.json
---
# Reaper spirit for death magic

**Task:** Andy: death magic users, and warlocks who pact with it, gain the Reaper, an evil spirit of the Grue's own mana that grows in stages, is seen by anyone near death, and grows stronger with corpses near.

## Plan
- Rules in `Reaper` (Toll, five stages, corpse strength, hunger, moves on the spirit key, Grue immunity).
- Drawn by `ReaperClient` with MCA's Grim Reaper model (`McaReaper`).
- "reaper" as a Warlock pact option.

## Notes
- Small hooks in Spirits, SpiritSickness, SpiritKey, Necromancy, ForbiddenArts, Mana, Warlocks, Classes, ClassCasters, GraveGhost and the hurt and target mixins.

## Done
Built with Gradle; not played. Not built: NPC necromancers carrying one, a clash film against a mana spirit, smoke threads from each corpse.
- 2026-11-14 09:13 PR #1526 is merged into master; moved to Agents/Done.
