---
name: medicine-tree
description: Survival's Medicine page (Steady Hands..Lifesaver) + Stealth XP from sneaking past monsters; PR #377 MERGED 2026-10-21 13:0xZ (Kingdoms 0.7.136 / pack 0.15.49)
metadata:
  type: project
  modified: 2026-10-21T12:52:36.435Z
---
2026-10-21 Andy: "sneaking near enemies undetected should passively grow your stealth skill" and "a medicine tree in the same vein as shadow and free running but for survival, that way every skill has at least one sub path".
PR #377 (branch claude/project-thread-34f75e) MERGED on Andy's "merge", built, not playtested; jars /mnt/project-files/medicine-tree/.
- Senses.sneakPast: sneaking+moving, unaware Enemy in sight <12 blocks: 1 Stealth XP / 10 s (x2 within 6); spotted/searched resets.
- Survival max level 25 (practice capped at 15). Perks tiers 4-6: STEADY_HANDS, FIELD_DRESSER, BONESETTER, PLAGUE_DOCTOR (Nightfall.infect/Atrophy; only their leeches draw out plague+poison, Andy), LIFESAVER, SELF_REVIVE ("Second Wind", tier 7: eat while downed to rise, once/day, Powers core/wounds/SelfRevive; old page-1 SECOND_WIND key kept, shown as "Rally" - flagged to Andy).
- Powers core/wounds/MedicineSkill reads ranks by reflection; Treatment/Downed revive hooks; tag jaymospowers_treated -> 2 Survival XP (10 s cap).
- Strength already had its sub-path (Combat Arts page) before this.
Related: [[skill-tree]], [[combat-arts]], [[nightfall-plague]], [[downed-guards]], [[accurate-hitboxes]].
