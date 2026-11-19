---
status: done
task: lanterns-03-patrol-small
branch: claude/lanterns-03-patrol-small
with: Claude Code (PC)
pr: 1754
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrolRelief.java (new)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPatrol.java (menu + init lines)
  - src/main/java/com/jaymospowers/JaymosPowers.java (attachment list line)
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_patrol_relief.json
  - changes/powers/lanterns-03-patrol-small.md
---
# lanterns-03-patrol-small

**Task:** Lanterns patch 3: Patrol relief runs (herbs from a far world to a town's healer) and your Patrol file.

## Notes
- Lore: "Relief runs" (new) paragraph added to Lore.md after The Galactic Patrol, for Andy to approve.
- Lanterns 2 and 3 each add one name to JaymosPowers.registerAttachments, on lines far apart (no conflict).

## Done
Patrolmen give relief runs (6 dried limphium leaves to any town's cleric, for silver and TP). "Reporting in" reads out your Patrol file. Compiled on the PC, CI passes; not played.
- 2026-11-18 19:07 PR #1754 is merged into master; moved to Agents/Done.
