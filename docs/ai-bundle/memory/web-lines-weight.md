---
name: web-lines-weight
description: Advanced Hook Launchers + Enchanted Fishing Line asks (2026-10-14): remade; rope-sim web lines (synced), weight/rip-outs/fire; PR #166 MERGED, pack 0.10.53
metadata:
  type: project
  modified: 2026-10-14T16:29:57.605Z
---
Andy linked Advanced Hook Launchers 2026-10-14 asking to adapt its line and behaviours for Web. Licence CC BY-ND 4.0, no source: nothing ported, own remake.
PR #166 MERGED 2026-10-14 16:39Z (pack 0.10.53; 0.10.52 was reserved by the immersion-mods thread), not playtested. Jar in /mnt/project-files/web-lines/.
- Client `WebRopeSim`: stuck lines are verlet ropes (drape over edges, rest on ground; visual only), after Andy also linked Enchanted Fishing Line (ARR). `WebLineShape`: snake while shooting, twang on stick, stretch hum + thin. Rope length synced (WebNet.Rope -> WebPower.ROPE/ROPE_OFF) so everyone sees ropes hang.
- Server `WebHeft`: weight = bbox volume vs strength (4, suit x1.5, armour x2, helpers on same Yank catch add up), recoil + drag via new additive `WebNet.Shove`; Yank plucks riders; B rips torches/lanterns/bells; lines burn in fire/lava/campfire.
- Skipped AHL's "webs don't stick to sand" (would nerf deserts); offered to Andy.
Related: [[web-airbrake]], [[web-strength]].
2026-10-14 fix pass, PR #193 MERGED 2026-10-14 23:26Z (pack 0.10.99): lead let-go spends one line only after break/loss (WebSpentLines.LEAD_WAIT); taut lines drawn straight (WebRopeSim.TAUT_SLACK); lines on moving catches must pass moving=true to WebGrowth or they restart each frame (whips vanished); local player's off hand reads the synced whip line or the lasso grips one-handed; suits must be in PoseTakeover.posed or Fresh Animations misaligns them; barrage 1 stab / 10 ticks.
