---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-b
pr:
suggested_owns: [kingdoms/src/main/java/com/jaymoskingdoms/LifeStory.java]
---

# A start belongs to one player; if it's taken, find another

Two players should never open with the same beginning in the same place.

## What's wanted

Tiv, in the Tasks thread: "Every player should have their own start, it should search for another if they are already taken."

## Notes

- The start is the last page of the life story (`LifeStory.Start`, thirty-odd of them: the capital, a village, banished, shipwrecked, chained, greenskin, the other worlds and the rest), and `LifeStory.spot` is what settles where you wake once it's chosen.
- Settled with Tiv: it's the *place* that's exclusive, not the card. A start that puts you in a village, a realm's seat, a camp or a wreck needs one nobody has been born in, so it looks for another free one. Two players may both be shipwrecked; they must not both wash up in the same place.
- So the work is in `spot` and whatever picks the settlement/seat/wreck: ask for the nearest free one, remember who started where, and keep the search bounded so a newcomer never waits on a long scan.
- A full world still needs an answer: fall back to the next free thing, then to a plain free spot, and never leave a newcomer with no start.
- Single player is unaffected — there's no one to be taken by.
- 2026-11-09 17:50 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
