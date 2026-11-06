---
name: weapon-parry-guard
description: Why weapon parry broke (MCA chat on right click, Tool Station weapons unguarded) and PR #987 fix (Powers 0.20.76)
metadata:
  type: project
  modified: 2026-11-05T07:24:28.111Z
---
2026-11-05 Andy: "parry doesnt seem possible with weapons anymore, is that because of the weapon arts?" Not the arts: Spell Engine's skill bar gives the use key to any item whose use animation isn't NONE (BLOCKS_ATTACKS makes it BLOCK), so guarding weapons keep right click.
Real causes: (1) MCA villagers (orcs, guards, bandits) override `interact` and open their chat on right click, so the guard never went up against them. (2) Tool Station weapons (`TinkerToolItem` overrides use) never got the guard, and ToolStats removed BLOCKS_ATTACKS.
PR #987 (Powers 0.20.76, MERGED 2026-11-05 07:29Z, not played): `GuardOverTalkMixin` on Player.interactOn passes when `Guard.guardsAgainst` (a guarding weapon, a foe = Enemy / isAggressive / locked on, not sneaking). Tinker SWORD/WEAPON/TOOL now guard; an off-hand dual-wield dagger still swings. Jar: /mnt/project-files/weapon-parry/.
**Hard-won:** a Fabric UseEntityCallback returning FAIL cancels the whole startUseItem (no item use either), so it can't be used to fall through to an item; make the entity side return PASS instead. Related [[combat-arts]], [[weapon-forms]].
