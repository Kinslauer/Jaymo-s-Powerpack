---
name: heritage-life-story
description: Life story screen reworded per heritage (story.jaymoskingdoms.as.<heritage>.*), heritage-only raisers; PR #805 = pack 0.19.0 / Kingdoms 0.7.448
metadata:
  type: project
---
2026-10-31 Andy: the life story screen should change with the heritage picked ("bio built wouldnt have anyone who raised them").
PR #805 (open at 2026-10-31 ~21:05Z): LifeStoryScreen.story() prefers lang `story.jaymoskingdoms.as.<heritage>.<q>...`; Answer.onlyBorn / notBorn hide answers per birth heritage (server checks too). 13 heritage-only raisers (tinkerer, vatkin, acrobats, ringmaster, keepers, grovekeepers, elders, hornkin, wolves, barrows, chieftain, master, sunpriests). Nobles hidden for Bio-built, Warrior-born, Verdant.
Andy wants this release to be pack **0.19.0** (Kingdoms 0.7.448) and "cap all the work today"; he may bugtest before.
Narrator clips for the `as.*` lines don't exist yet: run tools/voices/make_voices.py narrator, then --sounds (registered only once clips exist).
