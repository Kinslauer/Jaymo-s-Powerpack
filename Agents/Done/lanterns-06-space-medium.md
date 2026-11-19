---
status: done
task: lanterns-06-space-medium
branch: claude/lanterns-06-space-medium
with: Claude Code (PC)
pr: 1766
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/power/zfighter/KiVoyage.java (new)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterPodTrip.java (Flight.ki, Hollow stretch, init/damage lines)
  - src/main/java/com/jaymospowers/power/zfighter/ZFighterSpace.java (skirtsHollow, indexOf)
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterFlightScreen.java
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterSpaceArt.java (hollow)
  - src/client/java/com/jaymospowers/client/power/zfighter/ZFighterStarMapScreen.java (Hollow lines)
  - src/main/resources/assets/jaymospowers/lang/parts/zfighter_hollow.json
  - changes/powers/lanterns-06-space-medium.md
---
# lanterns-06-space-medium

**Task:** Lanterns patch 6: a black hole past the worlds (the Hollow), and flying to a near world on your own ki.

## Notes
- Stacked on lanterns-02 (#1753, star map).
- Question for Andy: the ki-crossing power gate (now 50,000) and ki cost (80% of max), KiVoyage.POWER_GATE / KI_SHARE.
- Lore: "The Hollow" (new) paragraph added before The giant's ring.

## Done
The Hollow sits on the star map; trips past it run long and the flight screen bends the stars round it. A 50,000-power Z Fighter can fly past the top of the world and cross to the Pale Moon and back on ki. Compiled on the PC, CI passes; not played.
- 2026-11-19 01:59 PR #1766 is merged into master; moved to Agents/Done.
