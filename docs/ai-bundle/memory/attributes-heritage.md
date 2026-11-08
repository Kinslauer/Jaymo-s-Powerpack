---
name: attributes-heritage
description: PlayerEx + Origins remade in Kingdoms as K-screen Attributes and a Heritage character-creation step; PR #247 (pack 0.11.56 / Kingdoms 0.7.57)
metadata:
  type: project
  modified: 2026-10-15T13:34:49.153Z
---
2026-10-15 Andy asked for PlayerEx and Origins. Neither has a 26.3 build; both are MIT. PlayerEX (the 1.20/1.21 rewrite) is BML-licensed, so it can't be reused.
Andy said "build em both i want to see how the attributes integrate".
- `Stats`: character level = sum of the pillar levels; 1 point per 2 levels. Six attributes (Might, Agility, Finesse, Endurance, Grit, Wits), max 10. Points are stored in the Skills ranks as `stat_<name>`, so a respec covers them. Class favoured attribute +1 (`Stats.favoured`). No health: hearts stay with Hearty Meals.
- `Heritage`: forced step between MCA destiny and the class (`Classes.next`); 8 passive heritages. Verdant sets `Verdants.SHADE` on the player. Ops: `/kingdom heritage set|clear|get`.
- PR #247 MERGED 2026-10-15 ~13:48Z, not playtested. Jar: /mnt/project-files/attributes-heritage/. Related: [[skill-tree]], [[player-classes]].
