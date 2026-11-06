---
name: beg-pardon
description: Kingdoms 0.6.2 / pack 0.9.2 (PR #110 merged 2026-10-13 14:02Z): right-click an NPC who approached you to beg their pardon or ask for more
metadata:
  type: project
---
2026-10-13 Andy: "sometimes, npcs will run to you and you miss what they say, give the ability to beg their pardon, or ask them to elaborate".
PR #110 (branch claude/project-thread-3n0tgw): `Approaches` keeps the last unanswered approach (RECENT, PARDON_TICKS 45 s);
right-click (not sneaking) opens a TalkNet.Ask screen: Beg your pardon? / Could you tell me more? / Never mind. A walked-off
question is re-asked after a pardon. Every opening has a `talk.jaymoskingdoms.<topic>.<n>.more` line; new openings need one too.
Jar in /mnt/project-files/pardon/. See [[villager-approaches]], [[villager-speech]].
Same PR, Andy's follow-up: guards halted too often (now HALT_TICKS 3 days, GUARD_GAP 8 min) and got 4 temper-shaped
encounters (search, toll, bout, loyalty; tables at top of Approaches, `guardsBusiness`); `Theft.arrest` = 1-min wanted.
