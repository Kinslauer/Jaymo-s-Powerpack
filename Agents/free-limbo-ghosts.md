---
status: done
task: Free limbo ghosts after being eaten
branch: claude/project-thread-c686cq
with: cloud thread
pr: 1506
mods: powers, kingdoms
versions: 
started: 2026-11-14
owns:
  - src/main/java/com/jaymospowers/core/wounds/Limbo.java
  - src/client/java/com/jaymospowers/client/wounds/LimboHud.java
---
# Free limbo ghosts after being eaten

**Task:** Andy was eaten while in Limbo and stayed tethered to nothing. A Limbo ghost with no body left roams free until Backspace.

## Plan
- Limbo: no corpse of theirs for 3 looks (6 s) sets FREE, tether off; Kingdoms graves count as a body (Limbo.RESTING).

## Notes
- Lore.md: "(new) The unbodied" for Andy to approve.

## Done
- 
- Merged as PR #1506 (2026-11-14). A Limbo ghost whose body is gone roams free until Backspace; a buried body keeps its ghost. Compiled, not played.
