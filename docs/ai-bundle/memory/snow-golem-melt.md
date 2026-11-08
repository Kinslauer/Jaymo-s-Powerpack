---
name: snow-golem-melt
description: Snow golems melt instead of leaving corpses (PR #820, Kitchen 0.5.41 / pack 0.19.18), merged 2026-11-01
metadata:
  type: project
  modified: 2026-11-01T00:27:10.100Z
---
2026-11-01 Andy: "snow golems should melt and not leave corpses."
PR #820 (branch claude/project-thread-2c2slx), Kitchen 0.5.41 / pack 0.19.18, MERGED 2026-11-01 ~00:58Z on Andy's "merge"; built, not playtested.
Kitchen `corpse/SnowMelt` replaces the snow golem's tickDeath (50 ticks, snow particles, no smoke puff, leaves 2 snow layers); `client/corpse/SnowMeltPose` squashes it instead of the tip-over. snow_golem is in the `no_corpse` tag.
Test jar: /mnt/project-files/snow-golem-melt/. See [[loose-parts-ragdoll]], [[golem-corpses]].
