---
name: golem-burning
description: Merged PR #81 (Kingdoms 0.5.48 / Kitchen 0.4.31 / pack 0.8.94): every iron golem carries corpses to a fire and drops them in
metadata:
  type: project
  modified: 2026-10-13T02:35:30.406Z
---
2026-10-13 Andy: "iron golems should pick up and drop the corpses into flame". The 0.5.23 Undertakers code already let village-faction golems throw monster corpses into a fire within 16 blocks, which rarely fired.
MERGED 2026-10-13 on Andy's "merge", PR #81 as Kingdoms 0.5.48 / Kitchen 0.4.31 / pack 0.8.94 (built, not playtested): `Undertakers.golems` sends any iron golem anywhere; monsters at once, other non-player corpses after 5 min untouched (SEEN map), never heavy corpses; fire within 24 blocks; drop from 1.8 blocks. Firefighting skips fires on infiniburn blocks and calls carriers away (`calledAway`). Kitchen `carryPosition` scales with the carrier's size.
Kitchen 0.4.30 is still held by the unmerged butchers thread. Test jars: /mnt/project-files/golem-burning/. See [[corpse-despawn]], [[golems-quiet-firefighters]], [[golem-corpses]].
