---
name: villager-neighbours
description: Kingdoms 0.7.19 / pack 0.10.68 (PR #175 MERGED 2026-10-14 18:47Z): villager-to-villager regard drives fights/arguments to gifts/hugs/courting/MCA marriage
metadata:
  type: project
  modified: 2026-10-14T18:35:36.020Z
---
2026-10-14 Andy: villagers should interact with each other more, negative at low relationship, positive at high.
PR #175 (branch claude/project-thread-ffjmgj): `Neighbours` (tuning at top), attachment `neighbour_regard` (-100..100 per
pair, both sides). Starts from pair hash + MCA personality (McaBridge.temper) + partner/kin (McaBridge.kin). 15 meeting
kinds; fights are knockback-only scuffles with onlookers, guards break them up. MCA courting can end in marriage.
Routines.free is now package-private and excludes villagers in a meeting. Op test: /kingdom neighbours [kind].
Jar: /mnt/project-files/neighbours/. Unplaytested. See [[villager-days]], [[villager-speech]].
Follow-up 2026-10-14 Andy: "villagers will set each other up at low rep": grudge quests (`Grudges`, Quest.GRUDGE=13): a
beating (3 blows) or a lure (MCA Follow in the enemy's menu, per Andy; enemy follows you to the giver's hiding spot, giver ambushes). PR #177 MERGED 2026-10-14 19:12Z, Kingdoms 0.7.21 /
pack 0.10.70 (0.7.20/0.10.69 are languages PR #176's). Test: /kingdom neighbours grudge_beat|grudge_lure.
