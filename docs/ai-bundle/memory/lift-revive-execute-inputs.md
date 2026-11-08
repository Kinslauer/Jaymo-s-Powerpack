---
name: lift-revive-execute-inputs
description: PR #538 (pack 0.17.7 / Kingdoms 0.7.222 / Kitchen 0.5.31): sneak+hold = lift, hold use = revive, hold left-click 1.5s = execute; big carcasses cut up in place
metadata:
  type: project
  modified: 2026-10-26T12:28:05.472Z
---
2026-10-26 Andy: pick-ups too sensitive, no lifting horses/cows (butcher them in place with a knife, meat/hide gathered slowly), revives and executions kept turning into pick-ups; suggested left-click hold 1.5 s for executions.
PR #538 (branch claude/project-thread-caswkn), built in the cloud, not playtested, MERGED 2026-10-26 13:4xZ (squash d48c26e):
- Sneak + hold right-click 0.6 s = lift (Carry.LIFT_TICKS; Kitchen CorpseEntity.LIFT_TICKS). Severed parts + Wind air grab stay a click. Client mixins swallow the same held click after a lift.
- Plain hold right-click 3 s = revive (Downed). Kingdoms disarm-on-tap still first.
- SNEAK + hold left-click 1.5 s (Andy 13:21Z: shift so Z Fighters aren't affected) = every execution incl. bare-hand neck snap (taps removed) and symbiote (Finishers.HOLD_TICKS 30, client FinisherClient sends Finishers.Execute). Z Fighter combos/race holds/guard stand aside over helpless bodies.
- Carry.tooBig: >1 wide or >2.2 tall; grown non-tag animals never liftable, even downed.
- Kitchen tooBigToLift = big_carcasses tag or animal bigger than a sheep: no lift/catch; knife/axe hold cuts a piece every 1.5 s (CUT_TICKS 30), meat first, full loot, then bones. Rig Arms can lift them (KitchenCompat.isTooBig).
Andy 2026-10-26 13:21Z added: dragging (sneak+hold on big carcass drags it; right-click butcher block within 3 blocks heaves it onto the table), crouch pose (client CrouchPose: low, arms out, sway; still 8 ticks = one knee) + server Kneel (any projectile from the knee steered 70% back onto aim at spawn: bows, crossbows, TaCZ, muskets; Andy 13:39Z).
Related: [[weapon-executions]], [[downed-guards]], [[block-carry]], [[butcher-table-pin]].
2026-10-26 Andy: lifted from too far. #546 merged (pack 0.17.15): lift needs <=1 block open air hitbox-to-hitbox (Carry.LIFT_GAP, server + client), cancels past 1.5 while filling. Parts/Wind snatch/Kitchen corpses unchanged.
