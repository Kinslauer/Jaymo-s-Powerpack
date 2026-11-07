---
name: power-fixes-0918
description: PR #123 MERGED (pack 0.9.19 / Kitchen 0.4.36): corpse catching, web tug jumps, one-web whip, flintlock pair drop, Damocles returns, random cannons, JEI when opped
metadata:
  type: project
  modified: 2026-10-13T17:49:19.326Z
---
2026-10-13 Andy's note -> PR #123 MERGED 2026-10-13 20:2xZ as pack 0.9.19 / Kitchen 0.4.36 (not playtested).
- Falling corpses: empty-hand right-click catches a tumbling body into your arms; `CorpseEntity.CATCH_RADIUS` 2.0 pick radius while airborne; stuck-in-block bodies eased out, settle after `STUCK_TICKS` 40.
- Web: each stuck web gives one tug on a fresh Jump press in midair (`WebSwing.TUG_LIFT` 0.6, `TUG_PULL` 0.3).
- Web Whip with both webs on one catch draws as one web in both hands (`WebArms.twoHanded`) — applied to all whip kinds, not only the lasso/overhead spin.
- Arsenal: Q on a summoned flintlock drops the off-hand one too (`ServerPlayerDropMixin`); Damocles sword hauled back up into its rift (rise phase); cannons `GROW_TICKS` 16, random grid cells behind you seeded by battery start.
- JEI cheat mode also on when opped (`JeiCheats`, COMMANDS_GAMEMASTER).
Jars: /mnt/project-files/power-fixes/. Retake free versions before merging.
- Follow-up in same PR: R/G swing webs stick to creatures/players/corpses within 2 blocks of the crosshair out to 32 (`WebAnchors.entityTarget`, gold diamond crosshair, `WebPower.tickEntityWeb` drags light ones); precise hanging (midair, slow, no WASD: Space/Shift reel `HANG_REEL` 0.07; slingshot brace then only on ground/with movement keys); free limbs ragdoll (`WebArms.LIMB_*`).
