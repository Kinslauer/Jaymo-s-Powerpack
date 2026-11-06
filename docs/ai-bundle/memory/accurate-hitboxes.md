---
name: accurate-hitboxes
description: Accurate Hitboxes not added; own per-limb hitboxes + blunt weapons break bones (core/Fractures) in PR #170 (pack 0.10.64)
metadata:
  type: project
  modified: 2026-10-14T16:45:16.265Z
---
2026-10-14 Andy asked about Countered's Accurate Hitboxes (MIT, 26.3 Fabric 2.0.2). Not added: client-only, wraps
only LocalPlayer.pick, falls back to the vanilla box, re-runs setupAnim for every mob each frame, and Better Combat
weapons do their own targeting.
Andy said "build this" for our own version: PR #170 MERGED 2026-10-14 17:11Z as pack 0.10.64 (branch claude/project-thread-uem9qe).
- core/LimbBoxes: head/torso/arms/legs laid out from height, body/head yaw, pose (stand/crouch, SWIMMING/FALL_FLYING
  horizontal, passenger legs forward), villager/witch folded arms, zombies arms out, lost parts skipped, SLACK 0.1.
  Off (whole box) when asleep, carried by a player, Xenoform form, or Great Ape.
- client/compat/mixin/LimbSwingMixin (optional config jaymospowers.bettercombat.client.mixins.json) filters
  BC TargetFinder$CollisionFilter.filter; client/mixin/LimbPickMixin wraps LocalPlayer.pick's getEntityHitResult.
- Dismember.aimedPart -> LimbBoxes.aimedPart; a torso hit severs the nearest limb (keeps sever rates).
- 2026-10-14 Andy: blunt weapons don't sever (only Dismember.blade); asked for breaks: core/Fractures (mace/hammer/club/flail/cudgel/staff words + #jaymospowers:crushing): 8% +1%/dmg over 4 (max 30%), players 5%; arm drops and can't grip, leg limps, head dazes, skeletons shatter (Dismember.shatter); mends over 6000 ticks (up to 3x faster at full health); limbs drawn limp (client Dismembered.limp). Same PR.
- 2026-10-14 Andy's healing rules: breaks mend faster when healthy; instantly via Hermit Bean, Verdant heal (and own body), respawn, or a cleric priest (right-click with 2 emeralds). Chrono Bloom / Wind Zephyr's Breath START healing: whole 2 min / 5 min later (Fractures.startMend, MENDED_BY). Players' LOST limbs never regrow unless Verdant (one per 30 s), Hermit Bean (all), or respawn (Dismember.regrow).
Test jar /mnt/project-files/limb-hitboxes/. Not playtested.
