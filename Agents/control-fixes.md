---
status: running
task: control-fixes
branch: claude/climb-parity, claude/flintlock-release, (afk-cam: TBD)
with:
pr:
mods: powers, kingdoms
versions:
started: 2026-11-22
owns:
  - src/main/java/com/jaymospowers/core/Ledge.java
  - src/client/java/com/jaymospowers/client/LedgeClient.java
  - src/client/java/com/jaymospowers/client/freerun/FreerunClient.java
  - flintlock / musket / early gun classes (Powers armory, Arsenal flintlock, Kingdoms Musketoon/EarlyGuns)
---
# Control fixes (2026-11-22)

**Task:** Andy's control reports: idle camera (Ji AFK Cinematic) throws you off your bobber; can't right-click pull up onto shore from water; right-click hold with empty hand should climb (parity); flintlocks fire on release, next hold reloads (interruptible), no loading bar, Q swaps to the other loaded one.

## Plan
- PR 1 climb-parity: Ledge.canHang water variant, held right-click pulls up / climbs (Climber).
- PR 2 flintlocks: release-fire, hold-reload, no bar, Q swap.
- PR 3 AFK cam while fishing: pack config or compat.

## Notes
- Builds on CI (PC runs only the game).

## Done
