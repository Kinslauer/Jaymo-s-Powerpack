---
name: two-handed-reach
description: Two-handed weapons reach 25% further and sweep a bigger box/arc for players and mobs; PR #262 MERGED (pack 0.11.75 / Kingdoms 0.7.67)
metadata:
  type: project
  modified: 2026-10-15T16:11:24.320Z
---
2026-10-15 Andy: "two handed weapon range and hurtbox shuld be larger". PR #262 MERGED 2026-10-15 16:2xZ as pack 0.11.75 / Kingdoms 0.7.67. Next free: 0.11.76 / 0.7.68.
- Any Better Combat weapon with `two_handed` (all mods): reach x1.25 (mixin on PlayerAttackHelper.getRangeForItem, used by client swing AND server range check), swing box width/height x1.35 and arc x1.25 (client TwoHandedSweepMixin on TargetFinder.findAttackTargetResult). Numbers in Powers core/TwoHanded.
- Mobs: Kingdoms MobCombat.TWO_HANDED_REACH / TWO_HANDED_ARC.
Test jars /mnt/project-files/two-handed-reach/. Not playtested. Related: [[accurate-hitboxes]], [[combat-animations]].
