---
status: stalled
task: Hollow captains fix: captain keeps his WALKING place when due back; tuning numbers as static finals; 12 Hollow slots per player on their own ladders
branch: claude/hollow-captains-fix
with: Hephaestus (hermes-fixer)
pr:
mods: kingdoms
versions:
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HollowCaptains.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Captains.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Rivals.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Ladders.java
---
# Hollow captains fix

**Task:** Follows PR #2144 (squash 668d46a). Agent note: [[Agents/hollow-captains|hollow-captains]] (merged work). One PR in Kingdoms, owns HollowCaptains.java, Captains.java, Rivals.java, Ladders.java; no other task touches them.

1. **Bug:** `HollowCaptains.died()` takes a captain due back off `WALKING` without lowering `COUNT`; a roamer fills the gap and `comeBack()` adds his body without checking room, so 6 becomes 7. A captain due back keeps his place.
2. **Tuning:** move inline numbers into `static final` constants — the 10-tick path refresh (~625), the 2.0F mend threshold (~660/663), the retreat 8/3 and speed 1.1 (~690-692).
3. **12 per player, their own ladders:** each player has 12 Hollow slots on their own ladder (constant), replacing the world-wide `START = 6`. A Hollow becomes a captain only after it kills a player. Captains from different players' ladders can meet and fight together; one captain can be on more than one player's ladder. Decide offline behavior and say what was chosen in the PR.

## Plan
- `died()`: a captain due back stays on `WALKING`; `COUNT` drops only when he's gone for good. `comeBack()` returns him to his kept place.
- Add `static final` constants for the four inline numbers.
- Per-player ladder (12 slots) attachment; kill-only promotion via `Captains.slew`; intersecting ladders.
- Change note `changes/kingdoms/<branch>.md`; lore paragraph "The Hollow among the dead" as `(new)` in Lore.md for Andy; update this agent note.
- Build Kingdoms (`sh ./gradlew build` in kingdoms/), `python tools/pr_checks.py`, open PR, report what a player will see and what wasn't tested.

## Notes
- Open questions (ask Andy if they change the build): exact constant names/placement; whether world-wide 6 (`START`) stays alongside per-player ladders; what happens to a player's ladder while offline.
- Never touch `version=`, the MODLOGs or `docs/codemap/`.

## Done
What a player will see, and what wasn't tested.
- 2026-11-26 01:25 No sign of work for 30 min: marked stalled (whoever ran it stopped).
