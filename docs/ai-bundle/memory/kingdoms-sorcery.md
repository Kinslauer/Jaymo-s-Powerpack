---
name: kingdoms-sorcery
description: Kingdoms 0.5.37 sorcery (PR #60 merged, pack 0.8.74): our own Iron's Spells take - camp shamans/necromancers, court wizards, thralls, scrolls
metadata:
  type: project
  modified: 2026-10-12T19:08:31.157Z
---
2026-10-12: Andy wanted Iron's Spells 'n Spellbooks to fuel necromancy and wizards for kingdoms and (more so) barbarians.
Iron's Spells is Forge/NeoForge 1.21.1 only and all rights reserved (no distributed ports, no asset reuse), so it was
reimplemented originally in Kingdoms: `Sorcery` (roles 40 necromancer, 41 shaman, 42 wizard), `Thralls` (risen undead,
attachment `thrall`, `ThrallTargetMixin` on LivingEntity.canAttack), `Arcana` (staffs + 5 scrolls), client `SorcerySkins`
(wizard hat model), art `tools/gen_sorcery.py`. Tyrant kings get a court necromancer. Andy: not every kingdom believes in magic (60% do, `Sorcery.believesInMagic`, fixed by id); the rest keep no court mage and prosecute magic (reading a scroll there = `Theft.Crime.SORCERY`, medium, ruler exempt). Op cmd `/kingdom caster <kind>`.
PR #60 merged 2026-10-12 as Kingdoms 0.5.37 / pack 0.8.74. Seen working in a cloud playtest.
Test jar + screenshots: /mnt/project-files/irons-spells/.
Not done yet: necromancer reviving Equestrian horses from mane locks (needs #58 merged), casters joining raids/war parties.
Related: [[equestrian-plan]], [[grim-camps-056]].
