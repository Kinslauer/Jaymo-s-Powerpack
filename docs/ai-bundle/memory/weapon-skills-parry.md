---
name: weapon-skills-parry
description: PR #1038: every melee weapon parries (WeaponMod katana/battleaxe/halberd/spear, patron weapons) and gets an RPG Series skill bar move
metadata:
  type: project
  modified: 2026-11-05T17:58:52.291Z
---
2026-11-05 Andy: battleaxe couldn't parry; battleaxe and ninjato had "no moves"; then "everything should have skill bar moves". Andy declined a separate Weapon Forms family for axes/maces/hammers (skill bar move is enough).
"Moves" = RPG Series weapon skills on Spell Engine's bar. Spell Engine's FallbackConfig assigns them by id regex and only to items with the vanilla WEAPON component, so many weapons had none. Kingdoms `forms/WeaponSkills` + `SpellAssignmentsMixin` (HEAD of SpellAssignments.encodeContent) fills every remaining melee weapon.
Parry: WeaponMod ItemMelee overrides use, so Guard.guardable skipped it. `KnifeGuard.guards` covers knife/katana/battleaxe/halberd/spear; sneak+use keeps halberd reach / spear throw. Deliberately unguarded: warhammer, flail, rapier, thrown, vanilla spear/trident, Arsenal relics, active patron weapons.
PR #1038 MERGED 2026-11-05 18:05Z (Powers 0.20.118, Kingdoms 0.7.601), not played. Related [[weapon-parry-guard]], [[weapon-forms]].
