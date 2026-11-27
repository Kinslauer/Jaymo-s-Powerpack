---
status: in-review
task: hollow-captains-fix
branch: claude/hollow-captains-fix
with:
pr: 2245
mods: kingdoms
versions:
started: 2026-11-26
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/HollowCaptains.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Captains.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Rivals.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Ladders.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/LadderScreen.java
---
# Hollow captains: keep the count, 12 per player, own ladders

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
- Per-player design: each player gets a COUNT (12) and WALKING list tracked by player UUID. When a Hollow captain kills a player, they're promoted and added to that player's ladder. Captains can be on multiple ladders (intersect). When a player is offline, their ladder slots are frozen - no roaming/horde filling for them; their captains don't walk for them until they log back in. When they return, their ladder refills from available captains.

## Done
What a player will see, and what wasn't tested.
- 2026-11-26 10:00 No sign of work for 30 min: marked stalled (whoever ran it stopped).
- 2026-11-27 04:30 Fixed all review issues from 2026-11-27 02:59:
  1. `playerRoom()` now counts due-back captains on the player's ladder as reserved slots
  2. Plain Hollows now track their owner via `HOLLOW_OWNER` entity attachment; `died()` and `ENTITY_UNLOAD` use this to clean up `PLAYER_WALKING`
  3. Added `ServerPlayerEvents.JOIN` handler to check for due-back captains when a player logs in
  4. `Ladders.board()` now shows per-player Hollow captain ladders in the ladder screen
  5. World-wide `COUNT`/`WALKING` kept as `@Deprecated` fallback only
  6. Lore.md paragraph "The Hollow among the dead" already exists (marked new, 2026-11-26)
  7. Fixed `MEND_THRESHOLD` javadoc to describe it as a health margin, not a frequency
- Build passes (`sh ./gradlew build`), PR checks pass (`python tools/pr_checks.py`)
- Not tested in game.
