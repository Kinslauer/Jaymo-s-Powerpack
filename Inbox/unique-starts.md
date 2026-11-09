---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java]
---

# A start belongs to one player; if it's taken, find another

Two players should never open with the same beginning in the same place.

## What's wanted

Tiv, in the Tasks thread: "Every player should have their own start, it should search for another if they are already taken."

## Notes

- The start is the last page of the life story (`LifeStory.Start`, thirty-odd of them: the capital, a village, banished, shipwrecked, chained, greenskin, the other worlds and the rest), and `LifeStory.spot` is what settles where you wake once it's chosen.
- Read one way: the *place* is exclusive — a start that puts you in a village, a realm's seat or a camp needs a free one, so it looks for another settlement/spot when someone's already been born there. This is the reading "search for another" suggests.
- Read the other way: the *start itself* is exclusive — once a player has had "shipwrecked", no one else in that world gets it.
- Confirm which Tiv means before building; the first reading is the better story ("our own start" = our own corner of the world) and doesn't run out of starts on a big server.
- A full world still needs an answer: fall back to the next free thing, then to a plain free spot, and never leave a newcomer with no start.
- Single player is unaffected — there's no one to be taken by.
