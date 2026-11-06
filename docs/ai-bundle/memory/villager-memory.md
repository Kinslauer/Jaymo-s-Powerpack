---
name: villager-memory
description: Recollections (per-villager memory of each player) + MCA chat AI realm context (ChatPrompts); PR #347 MERGED 2026-10-21, Kingdoms 0.7.109 / pack 0.15.10
metadata:
  type: project
---
2026-10-20 Andy: "i want this worked in" (MCA chat AI) + "Villagers should remember you and your specific interactions".
PR #347 (branch claude/project-thread-yz2q2h) MERGED 2026-10-21 02:4xZ as Kingdoms 0.7.109 / pack 0.15.10, not playtested. `Recollections` attachment on Villager/Folk:
player (or costume stand-in) -> Person(name, last, memories); 30 kinds with weight/lifetime; remarks via Approaches
GREET, MCA chat.topic, gossip about other players, Neighbours share; leaning <= -10 refuses MCA social answers.
Lines: `recall.jaymoskingdoms.<kind>.<0-2>`, `.heard.<kind>.<0-1>`, `.about.<kind>.<0-1>`.
MCA chat AI stays OFF by default; op enables with `/mca chatAI default` (MCA's own command; free server, hourly quota,
chats may be used for training). `ChatPrompts.append` is injected at TAIL of MCA ChatAIContext.appendPrompts.
New "NPC remembers X" features should call Recollections.remember. See [[villager-approaches]], [[deeds-captives]], [[mca-in-pack]].
