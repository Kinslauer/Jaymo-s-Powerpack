---
name: villager-approaches
description: Kingdoms 0.5.39 (PR #67, pack 0.8.78): villagers/children/guards walk up to players and start interactions; right-click to answer
metadata:
  type: project
---
2026-10-12 Andy: "villagers should be able to confront you with questions, quests and initiate all kinds of interaction".
PR #67 (branch claude/project-thread-jclqaf, draft): `Approaches` (tuning at top), `TalkNet` packets, client `TalkScreen`.
13 topics: quest_offer, quest_check, accuse (Theft.commit -> Approaches.suspect), halt (guard vs stranger), question (8,
warm/plain/rude), hungry, bargain (per profession), gift, war, gossip, greet, child, warn. Paced 1.5-4 min per player,
same villager once a day. Ops test: /kingdom talk [topic]. Jar in /mnt/project-files/approaches/.
Skin-tone PR #64 holds 0.5.38/0.8.77 and adds origin talk; don't duplicate. See [[villager-speech]], [[kingdoms-quests]].
