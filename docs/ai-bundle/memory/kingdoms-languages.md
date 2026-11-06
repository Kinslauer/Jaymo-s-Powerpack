---
name: kingdoms-languages
description: Kingdoms language system (PR #176 MERGED 2026-10-14 18:54Z, Kingdoms 0.7.20 / pack 0.10.69): tongues by climate, only simple dealings with foreign folk
metadata:
  type: project
  modified: 2026-10-14T18:36:44.522Z
---
2026-10-14 Andy asked for a language system that keeps you to simple dealings with kingdoms outside the region you spawned in.
PR #176 (branch claude/project-thread-8yx0q1). It uses 5 tongues keyed to a settlement seat's Climate: Common (plains), Northern (taiga/snow), Southern (desert/badlands/savanna), Wildwood (jungle/swamp) and Blossom (cherry). A player's home tongue comes from the climate at the world spawn.
`Languages` decides who understands whom. `Speech.say` garbles lines per listener. McaMessageMixin garbles MCA chat. A foreigner keeps trade, gifts, hug/kiss, rock-paper-scissors, follow/stay and simple approach topics. Quests, chat, hiring, court wizard respec and the tail explanation are blocked.
You learn a tongue by spending time near its speakers (1 point a minute) and trading (2 points a trade); 100 points makes you fluent. MCA family and your sworn realm always understand you.
Any new "NPC says X" should go through Speech.say so it respects tongues. PR #175 (villager relationships, Neighbours) merged first as 0.7.19 and was merged into #176; its villager-to-villager lines go through Speech.say, so they are garbled for foreigners. See [[villager-speech]], [[villager-approaches]], [[zfighter]].
Accent: learning goes past FLUENT (100) to NATIVE (200); from 50 points locals remark on your accent (tiers halting/broken/thick/slight, then native), via Speech.sayTo (to you only, not garbled). Andy asked for this 2026-10-14. Next free versions: pack 0.10.70 / Kingdoms 0.7.21.
