---
name: rarer-quests
description: Andy wanted quests rarer; merged PR #83 (Kingdoms 0.5.49 / pack 0.8.96) makes 1 in 20 villagers givers, kings seldom, reputation helps
metadata:
  type: project
  modified: 2026-10-13T02:46:41.168Z
---

Andy (2026-10-13): "quests should be a bit more rare, only 1 in 20 villagers and rarely will a king offer you a quest... or make it relate to conversation and reputation". PR #83: villagers are givers by a fixed UUID lot (GIVER_SHARE 0.05 + up to 0.05 more by reputation), givers have work on OFFER_CHANCE 0.5 of days; kings KING_CHANCE 0.08 + up to 0.12 by reputation. Tuning at the top of Quests.java.

Same PR (Andy's follow-up): giving up costs 10 rep (40 for a king's); Traveller's Journal item (book + map, shapeless) opens on M or use, only while carried: Quests / Map (client-drawn from loaded chunks, zoom 1-32) / Reputation pages (Journal, JournalClient, JournalScreen).

**Why:** keep future quest work from making them common again.
**How to apply:** new quest sources (approaches, stablehands) go through Quests.hasWork. Related: [[kingdoms-quests]], [[villager-approaches]].
