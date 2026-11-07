---
name: downed-self-rise
description: Downed rework 2026-10-29 PR #693 MERGED (pack 0.18.5 / Kingdoms 0.7.358): 6+ dmg kills the downed, sneak self-rise if no limb red, 2-hit interrupt, Will node Unflinching
metadata:
  type: project
  modified: 2026-10-29T22:10:15.758Z
---
2026-10-29 Andy: downed + any attack >= 3 hearts kills; downed without red-or-worse limbs stand up with shift (longer, animated rise, interrupted after 2 hits); too wounded, shift gives up as before; Will node to withstand more hits during charged interactions.

PR #693 MERGED 2026-10-29 as pack 0.18.5 / Kingdoms 0.7.358 (branch claude/project-thread-tkttix), built, not playtested. Readings I chose:
- "Crippled" = off feet but not downed (Breaks.crawlOnly, Darktide crawl); untouched.
- "Attack" = DamageSource with an entity/direct entity or explosion (not fire/falls). Raw amount, pre-armour. Applies to downed mobs and players.
- "Red" = wounds screen BAD colour, limb fraction <= 0.25 (arms/legs/feet only; head/body always hurt when downed), or broken/severed. Snapshotted as they fall (Downed.TOO_HURT), because the downed player's 20 hp refills parts to the bruise line; also checked live.
- Rise: players only, 8 s, 30% health, battered; not while passed out, carried or in water. Able players can't give up until passed out.
- 2-hit rule also added to revives and Second Wind eating (my call; the "charged interactions" plural). Executions untouched.
- Unflinching: Kingdoms perk, 2 ranks, +1 hit each, tier 12 col 1, needs No Getting Up.
See [[downed-guards]], [[will-tree]].
