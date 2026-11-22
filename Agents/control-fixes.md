---
status: in-review
task: control-fixes
branch: claude/climb-parity, claude/afk-cam-fishing, claude/flintlock-release
with:
pr: 2008, 2009, 2011
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
- #2008 climb-parity (Powers): right-click grab and hold work from water; low banks climbed by the click; held right-click pulls up from a hang; with Climber, held right-click jumps/clings/climbs.
- #2009 afk-cam-fishing (Powers): AfkCinematicFishing keeps Ji AFK Cinematic's fishing cinematic off and counts a line out as activity (its fishing mode can't keep the bobber in every shot).
- #2011 flintlock-release (Powers + Kingdoms): Void flintlock, musketoon, arquebus, handgonne fire on release; WeaponMod's guns already did. No reload overlay / use bar for old guns; Q selects the next loaded gun on the hotbar.
- None played in game; CI builds only.
