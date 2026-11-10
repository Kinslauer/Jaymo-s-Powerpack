---
status: done
task: unique-starts
branch: claude/unique-starts
with:
pr:
mods: kingdoms
versions:
started: 2026-11-09
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java
---
# A start belongs to one player; if it's taken, find another

**Task:** Make life-story start places exclusive: two players mustn't wake in the same place (village, realm seat, camp, wreck...) — the start picks the nearest free one, bounded, with fallbacks.

## Plan
- Read LifeStory.spot and the start-picking flow; check how settlements/seats/wrecks are chosen for a start
- Make the wake-spot search skip places another player already started in, and fall back (next free thing, then a plain free spot)
- Keep the search bounded so a newcomer never waits on a long scan
- Single player is unaffected (no one to be taken by)
- Build, pr_checks, commit, push, open PR, set in-review

## Notes
- Tiv: it's the place that's exclusive, not the card. Two players may both be shipwrecked; they must not both wash up in the same place.
- A full world still needs an answer: fall back to the next free thing, then to a plain free spot, never leave a newcomer with no start.

## Done
What a player will see, and what wasn't tested.
