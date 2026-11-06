---
name: strays-indoors
description: Villagers shoo stray animals out of houses/speakeasy (Kingdoms Strays), PR #992, Kingdoms 0.7.566, hint 104
metadata:
  type: project
  modified: 2026-11-05T08:09:55.399Z
---
2026-11-05 Andy: "villagers should react to animals being somewhere they shouldnt, like deer in the speakeasy". (The attached video actually showed an orc fight, not a deer.)
PR #992 MERGED 2026-11-05 08:1xZ (branch claude/project-thread-pb1lr5), Kingdoms 0.7.566, hint 104, lang part strays.json, built, not played.
`Strays`: wild Animal indoors (built floor + non-tree roof, or in Speakeasy.room) -> seer's bubble, nearest guard/worker/seer shoos with swats to a reachable open-sky spot (opens+closes a wooden door). 30 s timeout: guard/den hand/butcher kills it, others call a guard.
Skips: Works stables/barns/pens, ridden/leashed/tamed/named/owned, tag jaymoskingdoms:house_animals.
Defaults chosen by Claude, not Andy: culling stuck animals, 30 s, ranges. See [[herd-killings]], [[underworld-speakeasies]].
