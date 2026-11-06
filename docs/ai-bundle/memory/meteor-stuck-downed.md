---
name: meteor-stuck-downed
description: Players stuck at 0 health with no death (Jung after a gravity meteor, 2026-10-22); PR #453 (pack 0.15.130) adds a limbo catch; root cause still unknown
metadata:
  type: project
  modified: 2026-10-22T23:32:45.713Z
---
2026-10-22 Andy: after a gravity meteor Jung (MCA "Sora Stormbour'ne", user Jungxxv) lay stuck; giving up, /kill x2 and a relog did nothing. Video /mnt/project-files/MedalTVMinecraft20261022141750541.mp4; Andy = Jeksuis / Manigraphic78.

Diagnosis: health 0 but die() never ran. Fabric 0.164 ALLOW_DEATH only wraps isDeadOrDying in hurtServer, so a handler returning false without restoring health leaves the entity at 0, and every later hurtServer returns early. The server log (Andy uploaded it) had no exception and no death message. The meteor exploded at 14:16:39 right next to Jung. Every ALLOW_DEATH handler in Powers/Kingdoms sets health before refusing, so the culprit is unknown (maybe a third-party mod).

PR #453 MERGED 2026-10-22 23:3xZ (pack 0.15.130): DownedLimboMixin on LivingEntity.hurtServer RETURN plus a per-tick Downed.limbo watch. Downable players go down, everyone else dies properly. A saved DIED attachment (set in AFTER_DEATH) skips players who quit on the death screen. Each catch logs WARN "<name> was left at no health without dying (hit by <msgId>...)": grep the server log for it next time to find the cause. Built, not playtested.

2026-10-23 REGRESSION from #453 (Jung on 0.15.138, singleplayer): #453 checked LivingEntity.dead, but ServerPlayer.die never calls LivingEntity.die (dead is only set in handleKillingBlow), so every player death was treated as limbo. A downed player killed by a zombie got put back down with health, then the server ignored Respawn (handleClientCommand only respawns a player at health <= 0) and /kill did nothing. PR #465 MERGED 2026-10-23 14:11Z (pack 0.15.140) uses the DIED attachment only and clears DIED off anyone alive. Lesson: for players, "died" = Fabric AFTER_DEATH (ServerPlayer.die TAIL), never the dead flag.

Note 2026-10-22: in this thread the cloud gradle build worked WITHOUT the mirror (Maven Central didn't 429). See [[cloud-build]], [[downed-guards]], [[gravity-meteor-burst]].
