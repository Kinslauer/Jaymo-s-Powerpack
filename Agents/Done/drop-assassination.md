---
status: done
task: drop-assassination
branch: claude/drop-assassination
with: 
pr: 2029
mods: powers, kingdoms
versions: Powers + Kingdoms (bumped at merge)
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/core/finisher/Finishers.java
  - src/client/java/com/jaymospowers/client/finisher/FinisherPose.java
  - src/client/java/com/jaymospowers/client/wounds/ExecutionReact.java
  - kingdoms/src/main/java/com/jaymoskingdoms/HiddenBlade.java
---
# Drop assassinations

**Task:** every player landing on a foe from 4+ blocks plays an animated drop attack (one per weapon kind); the hidden blade's is the refined clip move (D_HIDDEN_BLADE).

## Plan
- Read Finishers / FinisherPose / ExecutionReact / HiddenBlade, then plan the D_* kinds.

## Notes
- 

## Done
Everyone's drop (4+ blocks onto a foe) downs it, with a move per weapon; Stealth 5+ or the hidden blade kills. Bosses/big take a heavy blow. Built on the Deck (Powers + Kingdoms); nothing played, no pose seen in game. PR #2029.
- 2026-11-23 09:33 PR #2029 is merged into master; moved to Agents/Done.
