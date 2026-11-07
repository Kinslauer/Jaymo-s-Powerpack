---
name: weapon-forms
description: Weapon Forms (Kingdoms 0.7.30, PR #200): full Better Combat combos are a Strength perk; mobs get 2 plain swings unless experienced
metadata:
  type: project
  modified: 2026-10-15T00:39:48.799Z
---
2026-10-15 Andy: "weapon combo animations are unlocked within the strength skill tree. simple one or two attacks otherwise. like most mobs. some mobs will be more experienced like storied guards or kings."
Built as Skills.Perk.WEAPON_FORMS (Strength root, tier 0 col 0, lvl 2, 1 pt) + WeaponForms class + mixin/bettercombat/AttackSelectionMixin (trims PlayerAttackHelper.selectAttack's attack array; runs both sides). Basic = first 2 attacks without jaymospowers: flourish animations. MobCombat.pick uses the same.
Experienced (thread's pick): leaders, ruler's escort, assassins, Javelins.elite realms' guards, folk grown 12+ days, illager captains.
PR #200 MERGED 2026-10-15 (pack 0.10.108 / Kingdoms 0.7.30). Not playtested; jars /mnt/project-files/weapon-forms/. Related: [[combat-animations]], [[combat-arts]].
