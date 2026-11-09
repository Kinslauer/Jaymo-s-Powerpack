---
status: open
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by:
pr:
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java]
---

# Bad water gives parasites, and a tea cures them

Kitchen, thirst: dirty water should cause a slow parasite instead of vanilla poison, and something existing should cure it.

## What's wanted

From the pick-list, row **Bad water gives a slow parasite instead of poison**: "Dirty water ticks damage at random rather than a straight potion hit; a thirsty debuff replaces nausea."

Andy: "parasites and treatment methods should be added too", then "the treatment can be added to any of the existing items, maybe one of the teas."

## Notes

- **No new items.** Andy's ruling: the cure goes on an existing item. The pack's teas are Powers' `willow_bark_tea` and `chamomile_tea`; a Kitchen drink works too. Pick one and say which in the PR.
- The parasite effect itself has to be ours (a Kitchen mob effect or a body component), since ours replaces poison rather than borrowing it.
- Parasites should be slow and survivable — a reason to boil water, not a death sentence.
