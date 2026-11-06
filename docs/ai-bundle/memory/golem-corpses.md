---
name: golem-corpses
description: Heavy iron golem corpses (PR #32): only Gravity lifts them, no butchering or burning, crumble after looting
metadata:
  type: project
  modified: 2026-10-12T04:44:55.076Z
---
2026-10-12 Andy asked: iron golem corpses are heavy, only Gravity can lift them, and they can't be butchered.
PR #32 (branch claude/heavy-golem-corpses-du93wn), MERGED 2026-10-12 as Powers 0.8.54 + Kitchen 0.4.27 (built, not playtested). Andy 2026-10-12: golem corpses must not burn; searched, they crumble (synced CRUMBLING, 50 ticks) and vanish; unsearched 10 min, same. Kitchen tag `jaymoskitchen:heavy_corpses`
+ `CorpseEntity.isHeavy`; Powers `KitchenCompat.isHeavy` blocks tendril grabs, symbiote carry/thrash, and golem hunting.
Gravity's Lift goes through Kitchen's `takeBy`, which stays open.
Test jars: /mnt/project-files/golem-corpses/. See [[gravity-0825]].
