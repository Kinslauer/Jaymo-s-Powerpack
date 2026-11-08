---
name: grim-camps-056
description: Kingdoms 0.5.6 (draft PR #26, 2026-10-11): barbarians settle beside Grim ruins and take wild ones; how to playtest Grim in dev
metadata:
  type: project
  modified: 2026-10-11T22:04:59.132Z
---
2026-10-11 Andy asked that barbarians "spawn in and inhabit" Grim Kingdoms ruins (it already existed in 0.5.0 but only within 600 blocks of a kingdom, and raiders bunched at the ruin's middle). Draft PR #26 (branch claude/project-thread-8bq9ox, Kingdoms 0.5.6): camp pitched beside the ruin (`CampBuildings.settle`), raiders spread, wild ruins taken with 160-block spacing. Seen working in game.

Grim playtest recipe: drop the Modrinth jar (version Hn8zuckE) into kingdoms/run/mods; seed 8675309 has tor_tower at -752,16, glacierfall_keep_plains at -640,-768 (both within 600 of a kingdom founded at -700,-320) and stonyfell_outpost_plains at -800,928. Grim sites are sparse (most 2,500+ blocks apart). See [[cloud-playtest-setup]], [[kingdoms-living-economy]].
