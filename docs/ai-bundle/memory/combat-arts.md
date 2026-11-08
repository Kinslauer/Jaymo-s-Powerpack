---
name: combat-arts
description: Strength tree "Combat Arts" second page (combos, finishers, Charge/War Cry on Y, Bladestorm), PR #116 MERGED, Kingdoms 0.6.5 / pack 0.9.10
metadata:
  type: project
  modified: 2026-10-13T15:09:00.159Z
---
2026-10-13 Andy: "give the strength skill tree access to even more combat combos and skills". Built as `CombatArts` in Kingdoms (PR #116, MERGED 2026-10-13 15:11Z on Andy's "merge"): Strength levels to 25 (others 15; alignment caps xp at level 15), 8 perks on a second page of the K screen: Momentum, Cleave, Earthsplitter/Rend/Executioner finishers every 4th full-strength link, Charge (Y) / War Cry (sneak+Y), Bladestorm every 8th link. Area blows only hit foes (Enemy or mobs targeting you).
Our own chain counter, not Better Combat's combo index (BC isn't a Kingdoms dependency); swing charge read in PlayerAttackMixin so BC swings count. Not playtested; jar in /mnt/project-files/combat-arts/. Related: [[skill-tree]], [[combat-animations]].
2026-10-22 Andy: Combat Arts must open right after Weapon Forms, not need Titan's Grip. PR #413 MERGED 2026-10-22 04:16Z (Kingdoms 0.7.163 / pack 0.15.87): Momentum/Cleave require WEAPON_FORMS (set in Perk's static block, forward ref) at level 2; deeper perks keep 18/21/24. Related: [[weapon-forms]].
