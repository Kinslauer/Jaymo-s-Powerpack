---
name: smoke-armour
description: Smoke Vapour Form drops everything worn on going up in smoke (PR #286 MERGED, 0.11.108); nothing goes on while smoke
metadata:
  type: project
  modified: 2026-10-16T07:42:00.214Z
---
2026-10-14 Andy: smoke can't put armour on when immaterial (PR #196 MERGED, 0.10.102: SmokeArmourMixin, SmokeEquipMixin, hand-back in SmokeVapour.tick).
2026-10-16 Andy: "going immaterial as smoke should drop everything you are wearing, including backpack." PR #286 MERGED 2026-10-16 08:5xZ (pack 0.11.108; next free 0.11.109): SmokeVapour.shed on start drops armour, costume (Costume.strip), Traveler Tool Belt (compat/ToolBeltCompat, worn = first belt in inventory) at feet; backpack set down as block (Backpack.dropWorn, shared with death). Backpack/costume can't go on in Vapour Form. Jar /mnt/project-files/smoke-shed/. Not playtested.
**How to apply:** bring master in and take the next free version before merging; Andy merges only on "merge". Related: [[smoke-power]], [[backpack]].
