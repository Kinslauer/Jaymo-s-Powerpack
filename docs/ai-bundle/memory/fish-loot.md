---
name: fish-loot
description: Fish corpses loot in one click and vanish (CorpseKind.FISH, fish_corpses tag), merged PR #75, Kitchen 0.4.29 / pack 0.8.89
metadata:
  type: project
  modified: 2026-10-13T01:10:54.677Z
---
2026-10-13 Andy: a beached Alex's Mobs flying fish dropped its fish but the corpse stayed; "looting fish should be instant".
Cause: too big to pocket, so it was searched like an OTHER corpse. Fix: `CorpseKind.FISH` (AbstractFish or tag
`jaymoskitchen:fish_corpses`, Alex's Mobs ids optional), any click drops loot and discards (`CorpseEntity.lootFish`).
PR #75 MERGED 2026-10-13 01:30Z (Kitchen 0.4.29, pack 0.8.89), built, not playtested. Jars: /mnt/project-files/fish-loot/.
See [[alexs-mobs]], [[golem-corpses]].
