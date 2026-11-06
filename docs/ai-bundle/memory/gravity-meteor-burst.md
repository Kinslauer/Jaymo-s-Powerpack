---
name: gravity-meteor-burst
description: Gravity meteor: magnetic-region rock from build height, crashes through, embeds, bursts, sweep removes hanging debris (PR #423, pack 0.15.91); earlier burst FX #276
metadata:
  type: project
  modified: 2026-10-22T12:21:05.241Z
---
2026-10-16 Andy: meteor should embed and explode like the Spirit Bomb, big debris burst, lingering dust. PR #276 MERGED (pack 0.11.93). Vanilla block particles reset speed, so fast debris is client-side: GravityMeteorBurst payload -> client GravityMeteorFx.
- Board clip / second head fix (PR #278): any applyModel must be idempotent (absolute, never relative to current part rotation).

2026-10-22 Andy: "it should always contain blocks from the aether's magnetic region, animate falling ALL the way from build height, crash through and destroy anything on the way down, exploding after embedding... doesnt leave debris floating above the impact site." "Aether" = our own Aether port in Powers (com.jaymospowers.aether; magnetic biomes = tag is_aether_magnetic; ferrosite, ichorite, arkenium, corrobonite, gravitite). PR #423 MERGED 2026-10-22 12:4xZ (pack 0.15.99), jar /mnt/project-files/gravity-meteor/. Not playtested.
- Floating debris causes found: aimPoint returns an air point when aiming at sky (now dropped to ground), carve cap 600/tick ran out (now 6000), explosions leave tree crowns hanging (new GravityMeteorSweep after the shockwave, pre-impact floating clumps like Aether islets are spared).
- Entity tracking in 26.3 is horizontal-only, so a rock at build height still syncs; render distance comes from BlockCluster.SCALE (any SCALE set -> FAR_SEEN 384).
- Andy ruling 2026-10-22: rock, wreck lumps and burst debris are ONE set (GravityMeteor.rock): an Aether magnetic conglomerate (ferrosite, ichorite, ferrosite sand, arkenium, corrobonite, gravitite) plus magma ("rocks superheat entering the atmosphere"). No other vanilla stone.
