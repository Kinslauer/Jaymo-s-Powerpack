---
status: done
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-b
pr: 1165
suggested_owns: [kitchen/src/main/java/com/jaymoskitchen/climate/Thirst.java]
writer: hermes-writer
text_pass: 
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
- 2026-11-08 20:26 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-08 22:56 hermes-b's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-09 00:35 Review (head fe33cd3): changes needed. 1) ParasiteEffect.java:25 TICKS = one minute: the parasite wears off on its own before you could brew hot chocolate, so the cure Andy asked for never matters. Make it last long enough that treating it is worth it (e.g. several in-game days, still slow and survivable). 2) No effect icon: add kitchen/src/main/resources/assets/jaymoskitchen/textures/mob_effect/parasite.png (comfort.png and nourishment.png are there), or the HUD shows a missing texture. 3) Version numbers in comments, which CLAUDE.md forbids: ParasiteEffect.java:19 '(0.5.94)' and Thirst.java:244 '(0.5.x)'. 4) Thirst.java:81 leaves an orphan javadoc where SICK_NAUSEA/SICK_HUNGER were removed; delete it. Thirst.java:248 the closing brace of consumed() is indented two tabs; fix it. 5) The PR should say which existing drink cures it and why; hot chocolate is allowed by the task, but say it in the PR body (it does) and in the change note (it does): fine. The rest is right: dirty water now gives the parasite instead of nausea, it never deals the killing blow, and hot chocolate reaches consumed() through StomachConsumableMixin.
- 2026-11-09 01:40 Coordinator merging: fixed review items 1-4 (two-day parasite, HUD icon, version comments gone, orphan javadoc + brace indent fixed). Building.
- 2026-11-09 01:52 PR #1165 is merged into master; moved to Inbox/Done.
