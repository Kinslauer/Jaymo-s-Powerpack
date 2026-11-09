---
status: running
task: startup-crash - the rat goal that killed the server
branch: claude/startup-crash
with:
pr:
mods: kitchen
versions:
started: 2026-11-09
owns:
  - kitchen/src/main/java/com/jaymoskitchen/rats/Rat.java
  - changes/kitchen/startup-crash.md
---

# Startup crash (bug-fixes thread)

**Task:** Fix the server crash Tiv reported in the Hotspot / parthenon bug-fixes thread. He sent the logs on 2026-11-09: two server crash reports and the matching console logs from the Steam Deck server.

## What the logs say

- `crash-2026-11-08_21.55.59-server.txt` (Powers 0.21.75 / Kingdoms 0.7.663 / Kitchen 0.5.95): the server died ~20 minutes in, ticking a rat. `Rat$RaidGoal.tick` (Rat.java:278) read a food target that had already been cleared — the goal clears `target` when it finishes eating or stealing, and the AI ticks a goal once more on an off tick without asking `canContinueToUse` first. Everybody is kicked and the world stops.
- `crash-2026-11-08_13.09.10-server.txt` (Powers 0.21.68 / Kingdoms 0.7.655 / Kitchen 0.5.92): a different, older crash, 13:09, ticking a named zombie. The NPE is inside vanilla `LivingEntity.refreshDirtyAttributes` (a fastutil set iterator with its backing array gone), no mod frame in the trace. Not fixed here: nothing points at one of our classes, and there is no console log for that session to go on. Worth its own task if it happens again.

## Done

`Rat$RaidGoal.tick` now stops itself when it has no target left instead of reading it. Nothing else changed.

Not tested in game: a rat eating, stealing and finishing a meal on a live server wasn't reproduced; the fix is compile-checked only.
