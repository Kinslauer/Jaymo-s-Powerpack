---
name: skill-tree
description: Skill tree (Survival/Stealth/Strength, K key) in Kingdoms with respec and alignment/gossip; PR #84 merged PR #84, Kingdoms 0.5.52 / pack 0.8.103
metadata:
  type: project
---
2026-10-13 Andy: stealth that improves the more you steal, as a skill tree with pillars Survival, Stealth, Strength. Then (03:06Z) build Survival and Strength as drafted ("we can fix later"); respec at a court wizard or by drinking a bottle o' enchanting; alignment shown to other players and spread by villager gossip.
Lives in Kingdoms (`Skills`, `SurvivalSkill`, `StrengthSkill`, `Renown`; client `SkillScreen`, `SkillTitles`; key K), separate from powers/mastery.
Andy's rule: Hardy Landing must not affect powers that negate fall damage. It is FALL_DAMAGE_MULTIPLIER ADD_MULTIPLIED_TOTAL -0.3, so zero stays zero.
Stealth perks act on the held pickpocket (#80, merged): they shorten Pickpocket.holdTicks and widen BEHIND_COS/TURN_COS.
PR #84 MERGED 2026-10-13 04:00Z on Andy's "merge" as Kingdoms 0.5.52 / pack 0.8.103. Not playtested. Jars in /mnt/project-files/skill-tree/.
Packhorse lightens hunger (carry-slowness lives in Powers/Kitchen); flagged to Andy. Related: [[pickpocketing]].
Forager (first Survival perk) makes you experienced with butchering via KitchenCompat.skills -> Kitchen Butchers.setExperienced (Andy 03:12Z, see [[butcher-villagers]]).
