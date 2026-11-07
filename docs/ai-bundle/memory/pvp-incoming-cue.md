---
name: pvp-incoming-cue
description: PvP/foe attack indicator (IncomingBlows): star+bell to the swing's target, closing arc round crosshair, gold = perfect parry; PR #1039 merged (Powers 0.21.1)
metadata:
  type: project
  modified: 2026-11-05T18:01:26.338Z
---
2026-11-05 Andy: "is it possible to add an indicator for pvp? as of right now it is hard to parry or defend at all".
PR #1039 MERGED 2026-11-05 18:09Z as Powers 0.21.1 / Kingdoms 0.7.603 (built, not played):
combat/IncomingBlows + compat/mixin/IncomingSwingMixin (Better Combat ServerNetwork.handleAttackAnimation HEAD) warn the
player a swing is aimed at (lock-on target, or in reach+1.5 within 50 degrees of the swinger's look) with the star and bell, sent only to them.
client/combat/IncomingBlowsClient draws 5 dots round the crosshair on the attacker's side, closing in over the upswing.
White = parry, red = heavy, gold = last PERFECT_TICKS (raise guard then for a perfect parry). The arc closes earlier by the player's own ping.
Mobs are covered through AttackTells.windUp, and Kingdoms MobCombat through PowersCompat.tellIncoming.
Related [[weapon-parry-guard]].
