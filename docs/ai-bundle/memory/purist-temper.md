---
name: purist-temper
description: Purist ruler temper (6th Tempers.Temper, Purists class): heritage-proud king, PR #371 MERGED 2026-10-21 as Kingdoms 0.7.130 / pack 0.15.38
metadata:
  type: project
  modified: 2026-10-21T09:42:22.767Z
---
2026-10-20 Andy: "Add a new king temper, Purist, they insist on a high population of its own heritage. Ridiculing and stepping over those who are not. Very prideful ruler".
Built in thread "Purist king temper" (branch claude/project-thread-2smll2), PR #371 MERGED 2026-10-21 09:5xZ (Kingdoms 0.7.130 / pack 0.15.38). Not playtested.
Design: PURIST appended as ordinal 5, drawn on its own (1 in 6) so old rulers keep their temper. Blood from leader UUID (Purists.blood). Approaches has two columns purist / purist_kin (PLAIN is now index 7). Any new temper-indexed array must get a PURIST entry (Tempers, Retinue.RULES, QuestBoard.REGICIDE_CHANCE, Assassins.TEMPER_CHANCE, Arrests.TEMPER_LEAN).
Tempers.describe now takes (temper, faction).
Related: [[kingdoms-war-tempers]], [[paranoid-guards]].
