---
name: mob-executions
description: PR #819 (pack 0.19.20 / Kingdoms 0.7.459): hostile mobs/illagers walk up and execute downed players with a letterboxed UltimateFilm scene
metadata:
  type: project
  modified: 2026-11-01T00:22:33.566Z
---
2026-11-01 Andy: "enemy mobs and illagers should be able to execute you, with a letterboxed scene".
PR #819 (branch claude/project-thread-2gmkkm), pack 0.19.20 / Kingdoms 0.7.459, built in the cloud, not playtested. MERGED 2026-11-01 01:08Z on Andy's "merge".
- core/wounds/MobExecutions: 5 s grace after going down (GRACE_TICKS), nearest Enemy within 16 blocks with line of sight; its AI is set aside via DownedMobMixin -> MobExecutions.step.
- Film: UltimateFilm DUTCH on the executioner for the victim (bars-only for players within 24 blocks), then afterDeath CUTS round the killer. Kingdoms captain's scene replaces it.
- Moves: axe beheads, blade stabs, bow/crossbow point-blank, evoker fangs, witch potion, zombie/spider feed, ravager/warden crush, bare hands stamp.
- Hitting the executioner before the blow cancels it (15 s cooldown). No creepers, fliers, slimes, bosses.
- Andy 2026-11-01 00:51Z "everyone": any mob fighting the victim also executes (target, brain ATTACK_TARGET, or hit by them within GRUDGE_TICKS 400): bandits, barbarians, wolves.
- Andy 00:57Z: guards "prioritize arresting you because unless they are of a certain temperament they try to be righteous and just": MobExecutions.SPARE veto; Kingdoms PowersCompat.spareRighteousGuards spares Downed.isGuard guards (unless HOTHEADED/SPITEFUL Temperament) and village iron golems.
Related: [[downed-guards]], [[downed-self-rise]], [[weapon-executions]].

2026-11-05 PR #961 (Powers 0.20.51 / Kingdoms 0.7.548 / Kitchen 0.5.63, MERGED 2026-11-05 00:30Z; also skeletons: Subtle Effects bone sprites off via compat.SubtleEffectsCompat, BladeBlood.splinters bone chips): MobExecutions.FINISHES executes downed MOBS too (no film); Kingdoms guards/archers finish downed monsters. Rearm skips MCA villagers (MCA gives guards a weapon each fight and stows it between, which caused "My weapon!" loops). Same PR: blood scales with health (BladeBlood.hurtShare 0.3x full health to 1.2x none), low-health drip; pixel-scatter drop/fleck sprites removed (Andy: redstone-like, never blood); Kitchen uses KitchenBlood.

2026-11-05 Andy (clip: accidental rock hit, flung ~14 blocks, executed by a rallied MCA townsman): "if that many guards are there they will force one another to do the just thing". PR #997 MERGED 2026-11-05 09:35Z (Kingdoms 0.7.570, hint 107, `HeldBack`; Andy: realms with cells jail you 2 min instead of a fine): every townsperson (guards, sworn, villagers, settled folk) spares downed players; only a lone HOTHEADED/SPITEFUL one (no townsperson within 16) executes, and only an outlaw/hostile/war enemy. Downed within 2 min of an assault near a guard = taken in: fine 6 emeralds or a warning. NPC blows' throw capped per tick (0.6 flat / 0.4 up); exact fling source not pinned (likely stacked SchooledGuards pushes).
2026-11-05 PR #1001 MERGED 10:0xZ (Kingdoms 0.7.575, hint 112; Andy chose "cells with a stair up"): every cell exit opens the iron door and you walk out via Speakeasy.passage (corridor + ladder to a spruce hatch at seat level), no teleport; escape = wanted HUNT_TICKS*3 not outlaw; LifeAims.betray can't push rep below hostile; seat respawn dropped on escape; HeldBack.standDown stops townsfolk attacking downed players they'd spare.
