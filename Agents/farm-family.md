---
status: in-review
task: Family farmhand's MCA family - wary of zombies, hide at night, last-resort defence, farm work
branch: claude/musing-tharp-a4b281
with: Claude Code session (side session of the coordinator)
pr: 1127
mods: Powers, Kingdoms
versions: Powers 0.21.59, Kingdoms 0.7.648 (PR #1127)
started: 2026-11-07
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/FarmFamily.java (new)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/mixin/mca/McaFarmWorkButtonMixin.java (new)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/farm-family.json (new)
  - small hooks in FarmhandHome, Workers.step, InteractActions.offered, McaCompat/McaBridge (choring)
---
# Farm family

**Task:** Andy: "for the farmhand start, those MCA should be far more wary of zombies and try to hide at night, as a very last resort they will have to swing back and defend themselves. they are just deadweight and they make life harder for the player. they need some kind of utility or function as of now."

## Notes
- Andy picked all four jobs (2026-11-07): tend crops, keep animals, unlock MCA chores, child gathers.
