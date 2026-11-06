---
name: smoke-move-poses
description: Smoke poses for every move + Vapour Form as a sprint-triggered mode holding top speed; PR #218 MERGED (pack 0.11.17)
metadata:
  type: project
  modified: 2026-10-15T02:45:14.162Z
---
2026-10-15 Andy: "smoke needs animations on every move" (armour clipping and the Gravity earthboard pose went to other threads), then "vapor mode should be toggleable, activating when you enter sprint, and maintains it's extreme speed, but you should still be able to hover and slow down".
PR #218 draft, branch claude/project-thread-3mzmdx, MERGED 2026-10-15 as pack 0.11.17. Jar /mnt/project-files/smoke-animations/. Not playtested.
- SmokeMoves (server, synced-to-all `smoke_move`): kind + start; held kinds drop when the move stops, one-shots after length(). SmokeMovePose (client) poses the arms/head; it's in ModelPoses + PoseTakeover.posed + SmokeModelFeatureRendererMixin.
- Vapour: R toggles `smoke_vapour_armed`; server starts the form on isSprinting(); client takes off straight into a cruise; the cruise holds top speed, only S brakes to a hover.
- Andy 03:04Z follow-up: sprint keeps a torso; Shift (mastered) toggles wholly smoke, Shift no longer sinks (hover W follows look); no fall damage ever (SmokeMoves.LAND: legs smoke 1 s + spill); Jet hurts + throws back; all moves double smoke (SmokeField.MOVE_SMOKE).
**How to apply:** bring master in and take the next free version before merging; Andy merges only on "merge". Related: [[smoke-power]], [[smoke-vapour-flight]], [[pose-takeover-emf]].
